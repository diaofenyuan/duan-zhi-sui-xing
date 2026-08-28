package com.example.localai.feature.download;

import android.os.Handler;
import android.os.Looper;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 下载域门面：向 UI 提供主线程安全的快照（任务/已安装/目录），
 * 后台 worker 上完成目录刷新、任务操作与状态回传。
 * 任务状态不再由 UI 写死：全部来自 Room + 协调器真实进度。
 */
public final class DownloadRepository {

    public interface Listener {
        void onDownloadsChanged();

        void onCatalogChanged();
    }

    /** 进行中任务视图（entity + 实时速度）。 */
    public static final class TaskView {
        public final DownloadEntity entity;
        public final double speedBps;

        TaskView(DownloadEntity entity, double speedBps) {
            this.entity = entity;
            this.speedBps = speedBps;
        }
    }

    /** 目录条目（只展示 Manifest 签名验证通过的模型）。 */
    public static final class CatalogItem {
        public final String modelId;
        public final String version;
        public final String displayName;
        public final String description;
        public final String publisher;
        public final String licenseSpdx;
        public final String licenseUrl;
        public final String sourceUrl;
        public final long sizeBytes;
        public final String quantization;
        public final long parameterCount;
        public final long contextLength;
        public final List<String> tasks;
        public final List<String> languages;
        /** approved=正式权重（真实 GGUF）；demo=演示载荷（不可推理）。 */
        public final String weightStatus;
        public final String chatTemplate;
        public final String updatedAt;
        public final int minAndroidApi;
        public final List<String> abis;
        public final boolean installed;

        CatalogItem(String modelId, String version, String displayName, String description,
                    String publisher, String licenseSpdx, String licenseUrl, String sourceUrl,
                    long sizeBytes, String quantization, long parameterCount, long contextLength,
                    List<String> tasks, List<String> languages, String weightStatus,
                    String chatTemplate, String updatedAt, int minAndroidApi, List<String> abis,
                    boolean installed) {
            this.modelId = modelId;
            this.version = version;
            this.displayName = displayName;
            this.description = description;
            this.publisher = publisher;
            this.licenseSpdx = licenseSpdx;
            this.licenseUrl = licenseUrl;
            this.sourceUrl = sourceUrl;
            this.sizeBytes = sizeBytes;
            this.quantization = quantization;
            this.parameterCount = parameterCount;
            this.contextLength = contextLength;
            this.tasks = tasks;
            this.languages = languages;
            this.weightStatus = weightStatus;
            this.chatTemplate = chatTemplate;
            this.updatedAt = updatedAt;
            this.minAndroidApi = minAndroidApi;
            this.abis = abis;
            this.installed = installed;
        }

        public boolean isApproved() {
            return "approved".equals(weightStatus);
        }

        public boolean isDemo() {
            return !isApproved();
        }
    }

    public static final class CatalogView {
        public enum State { LOADING, READY, ERROR }

        public State state = State.LOADING;
        public List<CatalogItem> models = Collections.emptyList();
        public String error;

        public boolean isError() {
            return state == State.ERROR;
        }

        public boolean isLoading() {
            return state == State.LOADING;
        }

        public boolean isReady() {
            return state == State.READY;
        }
    }

    private final DownloadCoordinator coordinator;
    private final DownloadDao downloadDao;
    private final ModelDao modelDao;
    private final ModelStorageManager storage;
    private final CatalogClient catalogClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private volatile List<TaskView> taskCache = Collections.emptyList();
    private volatile List<ModelEntity> installedCache = Collections.emptyList();
    private volatile CatalogView catalogCache = new CatalogView();

    public DownloadRepository(DownloadDao downloadDao, ModelDao modelDao,
                              ModelStorageManager storage, CatalogClient catalogClient,
                              DownloadCoordinator coordinator) {
        this.downloadDao = downloadDao;
        this.modelDao = modelDao;
        this.storage = storage;
        this.catalogClient = catalogClient;
        this.coordinator = coordinator;
    }

    public void register(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
        // 注册即推送当前快照，避免错过注册前的加载事件
        runOnMain(() -> {
            listener.onDownloadsChanged();
            listener.onCatalogChanged();
        });
    }

    public void unregister(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /** 应用启动：加载缓存 -> 刷新目录 -> 恢复未完成任务（全部异步，绝不主线程触库）。 */
    public void loadInitial() {
        coordinator.postControl(() -> {
            reloadCachesAndNotifyDownloads();
            refreshCatalog();
            coordinator.recoverPending();
        });
    }

    public void refreshCatalog() {
        setCatalogLoading();
        coordinator.post(() -> {
            try {
                Catalog catalog = catalogClient.fetchCatalog();
                List<CatalogItem> items = new ArrayList<>();
                for (Catalog.Entry entry : catalog.models) {
                    try {
                        ModelManifest manifest = catalogClient.fetchManifest(entry.modelId, entry.version);
                        ModelManifest.FileEntry file = manifest.primaryFile();
                        boolean installed = modelDao.getByModelId(entry.modelId) != null;
                        items.add(new CatalogItem(entry.modelId, entry.version,
                                entry.displayName == null ? entry.modelId : entry.displayName,
                                entry.description == null ? manifest.description : entry.description,
                                manifest.source == null ? null : manifest.source.publisher,
                                manifest.license == null ? null : manifest.license.spdx,
                                manifest.license == null ? null : manifest.license.url,
                                manifest.source == null ? null : manifest.source.url,
                                file == null ? 0 : file.sizeBytes,
                                manifest.quantization,
                                manifest.parameterCount,
                                manifest.contextLength,
                                manifest.tasks == null ? Collections.<String>emptyList() : manifest.tasks,
                                manifest.languages == null ? Collections.<String>emptyList() : manifest.languages,
                                "approved".equals(manifest.weightStatus) ? "approved" : "demo",
                                manifest.chatTemplate,
                                manifest.updatedAt,
                                manifest.runtime == null ? 0 : manifest.runtime.minAndroidApi,
                                manifest.runtime == null ? Collections.<String>emptyList() : manifest.runtime.abis,
                                installed));
                    } catch (CatalogException e) {
                        // 单模型 Manifest 签名/结构失败：不进入列表（只显示签名通过的模型）
                        android.util.Log.w("localai-catalog",
                                "manifest rejected " + entry.modelId + ": "
                                        + e.code() + " " + e.getMessage());
                    }
                }
                setCatalogReady(items);
            } catch (CatalogException e) {
                setCatalogError(describe(e));
            }
        });
    }

    public void enqueue(String modelId, DownloadCoordinator.Callback callback) {
        final DownloadCoordinator.Callback wrapped = new DownloadCoordinator.Callback() {
            @Override
            public void onResult(boolean ok, String message) {
                runOnMain(() -> callback.onResult(ok, message));
            }
        };
        coordinator.post(() -> coordinator.start(modelId, wrapped));
    }

    public void pause(String taskId) {
        coordinator.post(() -> coordinator.pause(taskId));
    }

    public void resume(String taskId) {
        coordinator.post(() -> coordinator.resume(taskId));
    }

    public void retry(String taskId) {
        coordinator.post(() -> coordinator.retry(taskId));
    }

    public void cancel(String taskId) {
        coordinator.post(() -> coordinator.cancel(taskId));
    }

    public void deleteModel(String modelId, String version, DownloadCoordinator.Callback callback) {
        coordinator.postControl(() -> {
            try {
                storage.delete(modelId, version);
                modelDao.deleteById(modelId, version);
                reloadCachesAndNotifyDownloads();
                refreshCatalog();
                runOnMain(() -> callback.onResult(true, null));
            } catch (RuntimeException e) {
                runOnMain(() -> callback.onResult(false, "删除失败：" + e.getMessage()));
            }
        });
    }

    /** 主线程安全快照。 */
    public List<TaskView> tasks() {
        return taskCache;
    }

    public List<ModelEntity> installed() {
        return installedCache;
    }

    public CatalogView catalogView() {
        return catalogCache;
    }

    public long installedBytes() {
        long total = 0;
        for (ModelEntity m : installedCache) {
            total += m.sizeBytes;
        }
        return total;
    }

    /** 协调器事件（worker 线程）：重载缓存并转发到主线程。内部由组合根接线。 */
    public void onCoordinatorChanged() {
        reloadCaches();
        runOnMain(() -> {
            for (Listener l : snapshotListeners()) {
                l.onDownloadsChanged();
            }
        });
    }

    private void reloadCachesAndNotifyDownloads() {
        reloadCaches();
        runOnMain(() -> {
            for (Listener l : snapshotListeners()) {
                l.onDownloadsChanged();
            }
        });
    }

    private void reloadCaches() {
        List<DownloadEntity> entities = downloadDao.visible();
        List<TaskView> views = new ArrayList<>();
        for (DownloadEntity e : entities) {
            views.add(new TaskView(e, coordinator.speedOf(e.taskId)));
        }
        taskCache = views;
        installedCache = modelDao.all();
        // 目录行的 installed 标记同步
        CatalogView view = catalogCache;
        if (view.isReady()) {
            Set<String> installedIds = new HashSet<>();
            for (ModelEntity m : installedCache) {
                installedIds.add(m.modelId);
            }
            List<CatalogItem> items = new ArrayList<>();
            for (CatalogItem item : view.models) {
                items.add(new CatalogItem(item.modelId, item.version, item.displayName,
                        item.description, item.publisher, item.licenseSpdx, item.licenseUrl,
                        item.sourceUrl, item.sizeBytes, item.quantization, item.parameterCount,
                        item.contextLength, item.tasks, item.languages, item.weightStatus,
                        item.chatTemplate, item.updatedAt, item.minAndroidApi, item.abis,
                        installedIds.contains(item.modelId)));
            }
            view.models = items;
        }
    }

    private void setCatalogLoading() {
        CatalogView view = new CatalogView();
        view.state = CatalogView.State.LOADING;
        catalogCache = view;
        notifyCatalogChanged();
    }

    private void setCatalogReady(List<CatalogItem> items) {
        CatalogView view = new CatalogView();
        view.state = CatalogView.State.READY;
        view.models = items;
        catalogCache = view;
        notifyCatalogChanged();
    }

    private void setCatalogError(String error) {
        CatalogView view = new CatalogView();
        view.state = CatalogView.State.ERROR;
        view.error = error;
        catalogCache = view;
        notifyCatalogChanged();
    }

    private void notifyCatalogChanged() {
        runOnMain(() -> {
            for (Listener l : snapshotListeners()) {
                l.onCatalogChanged();
            }
        });
    }

    private List<Listener> snapshotListeners() {
        synchronized (listeners) {
            return new ArrayList<>(listeners);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private static String describe(CatalogException e) {
        switch (e.code()) {
            case BAD_SIGNATURE:
                return "目录签名验证失败：" + e.getMessage();
            case NOT_FOUND:
                return "目录资源不存在";
            case HTTP:
                return "服务器错误：" + e.getMessage();
            default:
                return "网络不可用：" + e.getMessage();
        }
    }
}
