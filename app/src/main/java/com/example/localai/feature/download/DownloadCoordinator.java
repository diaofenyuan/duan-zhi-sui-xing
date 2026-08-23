package com.example.localai.feature.download;

import com.example.localai.data.network.Catalog;
import com.example.localai.data.network.CatalogClient;
import com.example.localai.data.network.CatalogException;
import com.example.localai.data.network.ModelManifest;
import com.example.localai.data.room.DownloadDao;
import com.example.localai.data.room.DownloadEntity;
import com.example.localai.data.room.DownloadState;
import com.example.localai.data.room.ModelDao;
import com.example.localai.data.room.ModelEntity;
import com.example.localai.data.storage.ModelStorageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 下载协调器（S015/S016/S017 核心）：
 * 目录解析 -> 断点续传（Range/If-Range，200/206/416/ETag 变化安全处理）->
 * SHA-256 + GGUF 校验 -> 原子安装。全部操作在单线程 worker 执行；
 * 进程重启后由 recoverPending() 按 Room 持久化状态恢复。
 * 失败语义：网络中断等可恢复错误置 FAILED 并保留 .part（用户重试即续传）；
 * 哈希/GGUF 错误删除 .part（非法资产不落盘）；非法 Manifest 直接拒绝创建任务。
 */
public final class DownloadCoordinator {

    public interface Listener {
        void onChanged();
    }

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private static final int BUFFER_SIZE = 32 * 1024;
    private static final long PROGRESS_MIN_INTERVAL_MS = 250;
    private static final long PROGRESS_MIN_BYTES = 256 * 1024;

    private final DownloadDao downloadDao;
    private final ModelDao modelDao;
    private final CatalogClient catalogClient;
    private final ModelStorageManager storage;
    private final OkHttpClient httpClient;
    private final ExecutorService worker;
    private final ExecutorService control;

    private final Map<String, Boolean> pauseFlags = new ConcurrentHashMap<>();
    private final Map<String, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, Double> speeds = new ConcurrentHashMap<>();
    private final Map<String, Long> lastProgressBytes = new HashMap<>();
    private final Map<String, Long> lastProgressTime = new HashMap<>();
    private volatile Listener listener;

    /**
     * @param worker  下载/校验/安装单线程执行器（阻塞 IO 不阻塞控制面）
     * @param control 控制面单线程执行器（入队/暂停/恢复/重试/取消/恢复扫描）
     */
    public DownloadCoordinator(DownloadDao downloadDao, ModelDao modelDao, CatalogClient catalogClient,
                               ModelStorageManager storage, OkHttpClient httpClient,
                               ExecutorService worker, ExecutorService control, Listener listener) {
        this.downloadDao = downloadDao;
        this.modelDao = modelDao;
        this.catalogClient = catalogClient;
        this.storage = storage;
        this.httpClient = httpClient;
        this.worker = worker;
        this.control = control;
        this.listener = listener;
    }

    public double speedOf(String taskId) {
        Double v = speeds.get(taskId);
        return v == null ? 0 : v;
    }

    /** 在 worker 线程串行执行（下载类工作）。 */
    public void post(Runnable runnable) {
        worker.execute(runnable);
    }

    /** 在控制线程串行执行（控制面工作，不被下载阻塞）。 */
    public void postControl(Runnable runnable) {
        control.execute(runnable);
    }

    /** 加入下载队列（目录/Manifest 解析与签名验证在控制线程完成，随后投递下载到 worker）。 */
    public void start(String modelId, Callback callback) {
        control.execute(() -> {
            try {
                if (!downloadDao.activeForModel(modelId).isEmpty()) {
                    callback.onResult(false, "该模型已有进行中的任务");
                    return;
                }
                if (modelDao.getByModelId(modelId) != null) {
                    callback.onResult(false, "该模型已安装");
                    return;
                }
                Catalog catalog = catalogClient.fetchCatalog();
                Catalog.Entry entry = findEntry(catalog, modelId);
                if (entry == null) {
                    callback.onResult(false, "目录中未找到该模型（未签名或不存在的资产被拒绝）");
                    return;
                }
                CatalogClient.ManifestBundle bundle = catalogClient.fetchManifestBundle(entry.modelId, entry.version);
                ModelManifest manifest = bundle.manifest;
                ModelManifest.FileEntry file = manifest.primaryFile();
                if (file == null) {
                    callback.onResult(false, "Manifest 文件列表为空");
                    return;
                }
                String url = catalogClient.resolveUrl(file.urls.get(0));
                DownloadEntity entity = new DownloadEntity(
                        "t-" + modelId + "-" + System.currentTimeMillis(),
                        modelId, entry.version, file.name,
                        manifest.displayName == null ? modelId : manifest.displayName,
                        manifest.source == null ? null : manifest.source.publisher,
                        manifest.quantization,
                        manifest.license == null ? null : manifest.license.spdx,
                        manifest.parameterCount,
                        file.sizeBytes, file.sha256, url);
                entity.state = DownloadState.QUEUED;
                try {
                    storage.persistManifest(entity.taskId, bundle.json, bundle.sig);
                } catch (IOException e) {
                    callback.onResult(false, "本地写入 Manifest 失败：" + shortMessage(e));
                    return;
                }
                downloadDao.insert(entity);
                notifyChanged();
                callback.onResult(true, null);
                worker.execute(() -> runTask(entity.taskId));
            } catch (CatalogException e) {
                callback.onResult(false, describe(e));
            } catch (RuntimeException e) {
                callback.onResult(false, "入队失败：" + e.getMessage());
            }
        });
    }

    public void pause(String taskId) {
        control.execute(() -> {
            // 先置标志并取消在途连接（立刻生效，不被阻塞下载挡住），再落库状态
            pauseFlags.put(taskId, Boolean.TRUE);
            cancelCall(taskId);
            DownloadEntity entity = downloadDao.getById(taskId);
            if (entity == null || !DownloadState.DOWNLOADING.equals(entity.state)) {
                pauseFlags.remove(taskId);
                return;
            }
            if (entity.transition(DownloadState.PAUSED)) {
                downloadDao.update(entity);
                notifyChanged();
            }
        });
    }

    public void resume(String taskId) {
        control.execute(() -> {
            DownloadEntity entity = downloadDao.getById(taskId);
            if (entity == null || !DownloadState.PAUSED.equals(entity.state)) {
                return;
            }
            pauseFlags.remove(taskId);
            if (entity.transition(DownloadState.DOWNLOADING)) {
                downloadDao.update(entity);
                notifyChanged();
            }
            worker.execute(() -> runTask(taskId));
        });
    }

    public void retry(String taskId) {
        control.execute(() -> {
            DownloadEntity entity = downloadDao.getById(taskId);
            if (entity == null || !DownloadState.FAILED.equals(entity.state)) {
                return;
            }
            pauseFlags.remove(taskId);
            entity.lastError = null;
            if (entity.transition(DownloadState.DOWNLOADING)) {
                downloadDao.update(entity);
                notifyChanged();
            }
            worker.execute(() -> runTask(taskId));
        });
    }

    public void cancel(String taskId) {
        control.execute(() -> {
            DownloadEntity entity = downloadDao.getById(taskId);
            if (entity == null) {
                return;
            }
            pauseFlags.put(taskId, Boolean.TRUE);
            cancelCall(taskId);
            downloadDao.delete(entity);
            storage.removeDownloadDir(taskId);
            speeds.remove(taskId);
            notifyChanged();
        });
    }

    /** 进程重启恢复：继续非终态任务（VERIFYING/INSTALLING 重新执行；PAUSED 保持）。 */
    public void recoverPending() {
        control.execute(() -> {
            storage.cleanup();
            List<DownloadEntity> pending = downloadDao.recoverable();
            for (DownloadEntity entity : pending) {
                String state = entity.state;
                if (DownloadState.PAUSED.equals(state)) {
                    continue;
                }
                if (DownloadState.VERIFYING.equals(state) || DownloadState.INSTALLING.equals(state)) {
                    worker.execute(() -> verifyAndInstall(entity.taskId));
                    continue;
                }
                worker.execute(() -> runTask(entity.taskId));
            }
            notifyChanged();
        });
    }

    public void shutdown() {
        for (Call call : activeCalls.values()) {
            call.cancel();
        }
        worker.shutdownNow();
        control.shutdownNow();
    }

    /** 由组合根注入事件回调（构造后再接线，避免循环依赖）。 */
    public void attachListener(Listener l) {
        this.listener = l;
    }

    // ---------- 内部实现 ----------

    private void runTask(String taskId) {
        DownloadEntity entity = downloadDao.getById(taskId);
        if (entity == null || pauseFlags.containsKey(taskId)) {
            return;
        }
        if (DownloadState.QUEUED.equals(entity.state)) {
            entity.transition(DownloadState.DOWNLOADING);
            downloadDao.update(entity);
            notifyChanged();
        }
        File part = storage.partFile(taskId);
        try {
            while (true) {
                if (pauseFlags.containsKey(taskId)) {
                    return;
                }
                entity = downloadDao.getById(taskId);
                if (entity == null || !DownloadState.DOWNLOADING.equals(entity.state)) {
                    return;
                }
                boolean complete = downloadOnce(entity, part);
                if (complete) {
                    verifyAndInstall(taskId);
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单次下载尝试。返回 true 表示文件已完整（进入校验阶段）。
     * 抛 InterruptedException 表示外部取消（暂停/取消/关闭），其余 IO 错误置 FAILED。
     */
    private boolean downloadOnce(DownloadEntity entity, File part)
            throws InterruptedException {
        long offset = part.length();
        Request.Builder builder = new Request.Builder().url(entity.url).get();
        if (offset > 0) {
            builder.header("Range", "bytes=" + offset + "-");
            if (entity.etag != null && !entity.etag.isEmpty()) {
                builder.header("If-Range", entity.etag);
            }
        }
        Call call = httpClient.newCall(builder.build());
        activeCalls.put(entity.taskId, call);
        try (Response response = call.execute()) {
            int code = response.code();
            if (code == 416) {
                // 本地偏移超出服务器内容：安全重启（截断重下，不拼接）
                truncate(part);
                syncDownloaded(entity, 0);
                return false;
            }
            if (code == 404) {
                fail(entity, "服务器资源不存在（404）");
                return false;
            }
            if (code != 200 && code != 206) {
                fail(entity, "服务器返回 HTTP " + code);
                return false;
            }
            long total = response.body() == null ? -1 : response.body().contentLength();
            if (code == 206) {
                long start = parseContentRangeStart(response.header("Content-Range"));
                if (start != offset) {
                    // 服务器未按请求偏移续传（异常）：截断重下
                    truncate(part);
                    syncDownloaded(entity, 0);
                    return false;
                }
                if (total >= 0 && entity.totalBytes > 0 && start + total != entity.totalBytes) {
                    fail(entity, "服务器文件总长度与 Manifest 不一致");
                    return false;
                }
            } else {
                if (total >= 0 && entity.totalBytes > 0 && total != entity.totalBytes) {
                    fail(entity, "服务器文件大小与 Manifest 不一致");
                    return false;
                }
                if (offset > 0) {
                    // 200 = If-Range/ETag 不匹配或服务器不支持续传：内容可能已变，截断重下，杜绝拼接新旧内容
                    truncate(part);
                    syncDownloaded(entity, 0);
                }
            }
            String etag = response.header("ETag");
            if (etag != null && !etag.isEmpty()) {
                entity.etag = etag;
                downloadDao.update(entity);
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (!streamToFile(response, entity, part)) {
                return false;
            }
            return part.length() == entity.totalBytes;
        } catch (IOException e) {
            if (pauseFlags.containsKey(entity.taskId)) {
                return false; // 暂停/取消路径：状态已由 pause/cancel 置位
            }
            fail(entity, "网络中断：" + shortMessage(e));
            return false;
        } finally {
            activeCalls.remove(entity.taskId);
        }
    }

    /** 流式写盘；进度节流更新 Room 与速度。返回 false 表示中断（状态已置 FAILED）。 */
    private boolean streamToFile(Response response, DownloadEntity entity, File part) {
        try (InputStream in = response.body().byteStream();
             FileOutputStream out = new FileOutputStream(part, true)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long lastBytes = lastProgressBytes.getOrDefault(entity.taskId, part.length());
            long lastTime = lastProgressTime.getOrDefault(entity.taskId, System.currentTimeMillis());
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                long now = System.currentTimeMillis();
                long written = part.length();
                if (written - lastBytes >= PROGRESS_MIN_BYTES || now - lastTime >= PROGRESS_MIN_INTERVAL_MS) {
                    double speed = now > lastTime ? (written - lastBytes) * 1000.0 / (now - lastTime) : 0;
                    speeds.put(entity.taskId, speed);
                    lastProgressBytes.put(entity.taskId, written);
                    lastProgressTime.put(entity.taskId, now);
                    lastBytes = written;
                    lastTime = now;
                    syncDownloaded(entity, written);
                }
            }
            syncDownloaded(entity, part.length());
            return true;
        } catch (IOException e) {
            if (pauseFlags.containsKey(entity.taskId)) {
                return false;
            }
            fail(entity, "下载中断：" + shortMessage(e));
            return false;
        }
    }

    private void verifyAndInstall(String taskId) {
        DownloadEntity entity = downloadDao.getById(taskId);
        if (entity == null) {
            return;
        }
        File part = storage.partFile(taskId);
        if (!DownloadState.VERIFYING.equals(entity.state) && !DownloadState.INSTALLING.equals(entity.state)) {
            if (!entity.transition(DownloadState.VERIFYING)) {
                fail(entity, "状态异常，无法进入校验");
                return;
            }
            downloadDao.update(entity);
            notifyChanged();
        }
        try {
            if (!ModelVerifier.sha256Matches(part, entity.sha256)) {
                storage.removeDownloadDir(taskId);
                fail(entity, "SHA-256 校验失败（文件损坏或服务器内容变更）");
                return;
            }
            ModelVerifier.GgufProbe probe = ModelVerifier.probeGguf(part);
            if (!probe.ok) {
                storage.removeDownloadDir(taskId);
                fail(entity, probe.reason);
                return;
            }
            if (!entity.transition(DownloadState.INSTALLING)) {
                fail(entity, "状态异常，无法进入安装");
                return;
            }
            downloadDao.update(entity);
            notifyChanged();
            installFromPart(entity, part);
        } catch (IOException e) {
            fail(entity, "校验失败：" + shortMessage(e));
        }
    }

    private void installFromPart(DownloadEntity entity, File part) {
        byte[] manifestBytes;
        byte[] sigBytes;
        try {
            manifestBytes = storage.readManifest(entity.taskId);
            sigBytes = storage.readManifestSig(entity.taskId);
        } catch (IOException e) {
            fail(entity, "读取本地 Manifest 失败：" + shortMessage(e));
            return;
        }
        try {
            storage.install(entity.modelId, entity.version, part, manifestBytes, sigBytes, entity.fileName);
            ModelEntity model = new ModelEntity(entity.modelId, entity.version,
                    entity.displayName == null ? entity.modelId : entity.displayName,
                    entity.publisher, entity.quantization, entity.licenseSpdx,
                    entity.fileName, entity.totalBytes, entity.parameterCount);
            modelDao.insert(model);
            downloadDao.deleteById(entity.taskId);
            storage.removeDownloadDir(entity.taskId);
            speeds.remove(entity.taskId);
            notifyChanged();
        } catch (IOException e) {
            fail(entity, "安装失败：" + shortMessage(e));
        }
    }

    private void fail(DownloadEntity entity, String reason) {
        DownloadEntity fresh = downloadDao.getById(entity.taskId);
        if (fresh == null) {
            return;
        }
        fresh.retryCount++;
        fresh.lastError = reason;
        if (fresh.transition(DownloadState.FAILED)) {
            downloadDao.update(fresh);
            notifyChanged();
        }
    }

    private void syncDownloaded(DownloadEntity entity, long bytes) {
        DownloadEntity fresh = downloadDao.getById(entity.taskId);
        if (fresh == null || !DownloadState.DOWNLOADING.equals(fresh.state)) {
            return;
        }
        fresh.bytesDownloaded = bytes;
        fresh.updatedAt = System.currentTimeMillis();
        downloadDao.update(fresh);
        notifyChanged();
    }

    private void cancelCall(String taskId) {
        Call call = activeCalls.get(taskId);
        if (call != null) {
            call.cancel();
        }
    }

    private static void truncate(File file) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            // 截断为空
        } catch (IOException ignored) {
            file.delete();
        }
    }

    private static long parseContentRangeStart(String contentRange) {
        if (contentRange == null || !contentRange.startsWith("bytes ")) {
            return -1;
        }
        try {
            String spec = contentRange.substring("bytes ".length());
            int dash = spec.indexOf('-');
            return Long.parseLong(spec.substring(0, dash));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static Catalog.Entry findEntry(Catalog catalog, String modelId) {
        if (catalog == null || catalog.models == null) {
            return null;
        }
        for (Catalog.Entry entry : catalog.models) {
            if (entry.modelId != null && entry.modelId.equals(modelId)) {
                return entry;
            }
        }
        return null;
    }

    private static String describe(CatalogException e) {
        switch (e.code()) {
            case BAD_SIGNATURE:
                return "签名验证失败，资产被拒绝：" + e.getMessage();
            case SCHEMA_INVALID:
                return "Manifest 非法：" + e.getMessage();
            case NOT_FOUND:
                return "资源不存在：" + e.getMessage();
            case HTTP:
                return "服务器错误：" + e.getMessage();
            default:
                return "网络错误：" + e.getMessage();
        }
    }

    private static String shortMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onChanged();
        }
    }
}
