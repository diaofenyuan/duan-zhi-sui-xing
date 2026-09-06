package com.example.localai.feature.download

import com.example.localai.data.network.Catalog
import com.example.localai.data.network.CatalogClient
import com.example.localai.data.network.CatalogException
import com.example.localai.data.network.ModelManifest
import com.example.localai.data.room.DownloadDao
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.example.localai.data.room.ModelDao
import com.example.localai.data.room.ModelEntity
import com.example.localai.data.storage.ModelStorageManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 下载协调器（S015/S016/S017 核心）：
 * 目录解析 -> 断点续传（Range/If-Range，200/206/416/ETag 变化安全处理）->
 * SHA-256 + GGUF 校验 -> 原子安装。传输在 worker 执行，控制线程可打断连接；
 * 进程重启后由 recoverPending() 按 Room 持久化状态恢复。
 * 失败语义：网络中断等可恢复错误置 FAILED 并保留 .part（用户重试即续传）；
 * 哈希/GGUF 错误删除 .part（非法资产不落盘）；非法 Manifest 直接拒绝创建任务。
 */
class DownloadCoordinator(
    private val downloadDao: DownloadDao,
    private val modelDao: ModelDao,
    private val catalogClient: CatalogClient,
    private val storage: ModelStorageManager,
    private val httpClient: OkHttpClient,
    private val worker: ExecutorService,
    private val control: ExecutorService,
    listener: Listener?
) {
    // 状态提交互斥，网络和哈希计算不持锁；恢复操作排在旧请求退出之后。
    private val stateLock = Any()
    @Volatile private var closed = false

    fun interface Listener {
        fun onChanged()
    }

    fun interface Callback {
        fun onResult(ok: Boolean, message: String?)
    }

    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val speeds = ConcurrentHashMap<String, Double>()
    private val lastProgressBytes = HashMap<String, Long>()
    private val lastProgressTime = HashMap<String, Long>()

    @Volatile
    private var listener: Listener? = listener

    fun speedOf(taskId: String): Double {
        val v = speeds[taskId]
        return v ?: 0.0
    }

    /** 在 worker 线程串行执行（下载类工作）。 */
    fun post(runnable: Runnable) {
        worker.execute(runnable)
    }

    /** 在控制线程串行执行（控制面工作，不被下载阻塞）。 */
    fun postControl(runnable: Runnable) {
        control.execute(runnable)
    }

    /** 加入下载队列（目录/Manifest 解析与签名验证在控制线程完成，随后投递下载到 worker）。 */
    fun start(modelId: String, callback: Callback) {
        control.execute {
            try {
                if (downloadDao.activeForModel(modelId).isNotEmpty()) {
                    callback.onResult(false, "该模型已有进行中的任务")
                    return@execute
                }
                val installed = modelDao.getByModelId(modelId)?.let { entity ->
                    storage.isInstalled(entity.modelId, entity.version, entity.fileName, entity.sizeBytes)
                } ?: false
                if (installed) {
                    callback.onResult(false, "该模型已安装")
                    return@execute
                }
                val catalog = catalogClient.fetchCatalog()
                val entry = findEntry(catalog, modelId)
                if (entry == null) {
                    callback.onResult(false, "目录中未找到该模型（未签名或不存在的资产被拒绝）")
                    return@execute
                }
                val bundle = catalogClient.fetchManifestBundle(entry.modelId!!, entry.version!!)
                val manifest = bundle.manifest
                val file = manifest.primaryFile()
                if (file == null) {
                    callback.onResult(false, "Manifest 文件列表为空")
                    return@execute
                }
                val url = catalogClient.resolveUrl(file.urls!![0])
                val entity = DownloadEntity(
                    "t-" + modelId + "-" + System.currentTimeMillis(),
                    modelId, entry.version, file.name,
                    if (manifest.displayName == null) modelId else manifest.displayName,
                    manifest.source?.publisher,
                    manifest.quantization,
                    manifest.license?.spdx,
                    manifest.parameterCount,
                    file.sizeBytes, file.sha256, url)
                entity.state = DownloadState.QUEUED
                try {
                    storage.persistManifest(entity.taskId, bundle.json, bundle.sig)
                } catch (e: IOException) {
                    callback.onResult(false, "本地写入 Manifest 失败：" + shortMessage(e))
                    return@execute
                }
                downloadDao.insert(entity)
                notifyChanged()
                callback.onResult(true, null)
                worker.execute { runTask(entity.taskId) }
            } catch (e: CatalogException) {
                callback.onResult(false, describe(e))
            } catch (e: RuntimeException) {
                callback.onResult(false, "入队失败：" + e.message)
            }
        }
    }

    fun pause(taskId: String) {
        control.execute {
            synchronized(stateLock) {
                val entity = downloadDao.getById(taskId) ?: return@execute
                if (!entity.transition(DownloadState.PAUSED)) return@execute
                pauseFlags[taskId] = true
                cancelCall(taskId)
                downloadDao.update(entity)
                notifyChanged()
            }
        }
    }

    fun resume(taskId: String) {
        control.execute {
            worker.execute resume@{
                synchronized(stateLock) {
                    val entity = downloadDao.getById(taskId) ?: return@resume
                    if (entity.state != DownloadState.PAUSED || closed) return@resume
                    pauseFlags.remove(taskId)
                    entity.transition(DownloadState.DOWNLOADING)
                    downloadDao.update(entity)
                    notifyChanged()
                }
                runTask(taskId)
            }
        }
    }

    fun retry(taskId: String) {
        control.execute {
            worker.execute retry@{
                val entity = downloadDao.getById(taskId)
                if (entity == null || entity.state != DownloadState.FAILED || closed) {
                    return@retry
                }
                try {
                    // 重试重新验证元数据，也补回校验失败时已删除的本地 Manifest。
                    val bundle = catalogClient.fetchManifestBundle(entity.modelId, entity.version)
                    val file = bundle.manifest.primaryFile()!!
                    check(file.name == entity.fileName && file.sizeBytes == entity.totalBytes &&
                        file.sha256.equals(entity.sha256, ignoreCase = true)) { "模型版本内容已变更，请取消后重新下载" }
                    storage.persistManifest(taskId, bundle.json, bundle.sig)
                    val urls = file.urls!!.map { catalogClient.resolveUrl(it)!! }
                    entity.url = urls[(urls.indexOf(entity.url) + 1) % urls.size]
                    entity.etag = null
                } catch (e: Exception) {
                    synchronized(stateLock) {
                        if (downloadDao.getById(taskId)?.state == DownloadState.FAILED) {
                            entity.lastError = "重试失败：" + shortMessage(e)
                            downloadDao.update(entity)
                            notifyChanged()
                        }
                    }
                    return@retry
                }
                synchronized(stateLock) {
                    if (downloadDao.getById(taskId)?.state != DownloadState.FAILED || closed) return@retry
                    pauseFlags.remove(taskId)
                    entity.lastError = null
                    entity.transition(DownloadState.DOWNLOADING)
                    downloadDao.update(entity)
                    notifyChanged()
                }
                runTask(taskId)
            }
        }
    }

    fun cancel(taskId: String) {
        control.execute {
            synchronized(stateLock) {
                val entity = downloadDao.getById(taskId) ?: return@execute
                pauseFlags[taskId] = true
                cancelCall(taskId)
                downloadDao.delete(entity)
                speeds.remove(taskId)
                notifyChanged()
            }
            worker.execute {
                storage.removeDownloadDir(taskId)
                pauseFlags.remove(taskId)
                lastProgressBytes.remove(taskId)
                lastProgressTime.remove(taskId)
            }
        }
    }

    /** 进程重启恢复：继续非终态任务（VERIFYING/INSTALLING 重新执行；PAUSED 保持）。 */
    fun recoverPending() {
        control.execute {
            worker.execute { storage.cleanup() }
            val pending = downloadDao.recoverable()
            for (entity in pending) {
                val state = entity.state
                if (DownloadState.PAUSED == state) {
                    continue
                }
                if (DownloadState.VERIFYING == state || DownloadState.INSTALLING == state) {
                    worker.execute { verifyAndInstall(entity.taskId) }
                    continue
                }
                worker.execute { runTask(entity.taskId) }
            }
            notifyChanged()
        }
    }

    fun shutdown() {
        closed = true
        for (call in activeCalls.values) {
            call.cancel()
        }
        worker.shutdownNow()
        control.shutdownNow()
    }

    /** 由组合根注入事件回调（构造后再接线，避免循环依赖）。 */
    fun attachListener(l: Listener) {
        this.listener = l
    }

    // ---------- 内部实现 ----------

    private fun runTask(taskId: String) {
        synchronized(stateLock) {
            val entity = downloadDao.getById(taskId) ?: return
            if (isStopped(taskId)) return
            if (DownloadState.QUEUED == entity.state) {
                entity.transition(DownloadState.DOWNLOADING)
                downloadDao.update(entity)
                notifyChanged()
            }
        }
        val part = storage.partFile(taskId)
        try {
            // 只允许一次脏偏移重置，异常响应不能造成无限自动请求。
            repeat(2) {
                if (isStopped(taskId)) {
                    return
                }
                val entity = downloadDao.getById(taskId)
                if (entity == null || !(DownloadState.DOWNLOADING == entity.state)) {
                    return
                }
                val complete = downloadOnce(entity, part)
                if (complete) {
                    verifyAndInstall(taskId)
                    return
                }
            }
            downloadDao.getById(taskId)?.let { fail(it, "服务器无法完成下载，请重试或切换下载地址") }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 单次下载尝试。返回 true 表示文件已完整（进入校验阶段）。
     * 抛 InterruptedException 表示外部取消（暂停/取消/关闭），其余 IO 错误置 FAILED。
     */
    @Throws(InterruptedException::class)
    private fun downloadOnce(entity: DownloadEntity, part: File): Boolean {
        val offset = part.length()
        val builder = Request.Builder().url(entity.url).get()
        if (offset > 0) {
            builder.header("Range", "bytes=$offset-")
            if (entity.etag != null && entity.etag!!.isNotEmpty()) {
                builder.header("If-Range", entity.etag)
            }
        }
        val call = httpClient.newCall(builder.build())
        synchronized(stateLock) {
            if (isStopped(entity.taskId)) return false
            activeCalls[entity.taskId] = call
        }
        try {
            call.execute().use { response ->
                val code = response.code
                if (code == 416) {
                    if (offset == 0L) {
                        fail(entity, "服务器拒绝从头下载（416），请重试切换地址")
                        return false
                    }
                    // 本地偏移超出服务器内容：安全重启（截断重下，不拼接）
                    truncate(part)
                    syncDownloaded(entity, 0)
                    return false
                }
                if (code == 404) {
                    fail(entity, "服务器资源不存在（404）")
                    return false
                }
                if (code != 200 && code != 206) {
                    fail(entity, "服务器返回 HTTP $code")
                    return false
                }
                val total = response.body?.contentLength() ?: -1
                if (code == 206) {
                    val range = parseContentRange(response.header("Content-Range"))
                    if (range == null || range[0] != offset || range[1] != entity.totalBytes - 1 ||
                        range[2] != entity.totalBytes || (total >= 0 && total != range[1] - range[0] + 1)) {
                        fail(entity, "服务器续传范围或文件总长度与 Manifest 不一致")
                        return false
                    }
                } else {
                    if (total >= 0 && entity.totalBytes > 0 && total != entity.totalBytes) {
                        fail(entity, "服务器文件大小与 Manifest 不一致")
                        return false
                    }
                    if (offset > 0) {
                        // 200 = If-Range/ETag 不匹配或服务器不支持续传：内容可能已变，截断重下，杜绝拼接新旧内容
                        truncate(part)
                        syncDownloaded(entity, 0)
                    }
                }
                val etag = response.header("ETag")
                if (etag != null && etag.isNotEmpty()) {
                    synchronized(stateLock) {
                        val fresh = downloadDao.getById(entity.taskId)
                        if (fresh?.state == DownloadState.DOWNLOADING && !isStopped(entity.taskId)) {
                            fresh.etag = etag
                            downloadDao.update(fresh)
                        }
                    }
                }
                if (Thread.interrupted()) {
                    throw InterruptedException()
                }
                if (!streamToFile(response, entity, part)) {
                    return false
                }
                if (part.length() != entity.totalBytes) {
                    fail(entity, "下载提前结束，文件长度不足，请重试续传")
                    return false
                }
                return true
            }
        } catch (e: IOException) {
            if (isStopped(entity.taskId)) {
                return false // 暂停/取消路径：状态已由 pause/cancel 置位
            }
            fail(entity, "网络中断：" + shortMessage(e))
            return false
        } finally {
            activeCalls.remove(entity.taskId)
        }
    }

    /** 流式写盘；进度节流更新 Room 与速度。返回 false 表示中断（状态已置 FAILED）。 */
    private fun streamToFile(response: okhttp3.Response, entity: DownloadEntity, part: File): Boolean {
        try {
            response.body!!.byteStream().use { inp ->
                FileOutputStream(part, true).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastBytes = lastProgressBytes.getOrDefault(entity.taskId, part.length())
                    var lastTime = lastProgressTime.getOrDefault(entity.taskId, System.currentTimeMillis())
                    var n = inp.read(buffer)
                    while (n > 0) {
                        if (isStopped(entity.taskId)) return false
                        if (n.toLong() > entity.totalBytes - part.length()) {
                            fail(entity, "下载内容超出 Manifest 大小，已停止接收")
                            return false
                        }
                        out.write(buffer, 0, n)
                        val now = System.currentTimeMillis()
                        val written = part.length()
                        if (written - lastBytes >= PROGRESS_MIN_BYTES || now - lastTime >= PROGRESS_MIN_INTERVAL_MS) {
                            val speed = if (now > lastTime) (written - lastBytes) * 1000.0 / (now - lastTime) else 0.0
                            speeds[entity.taskId] = speed
                            lastProgressBytes[entity.taskId] = written
                            lastProgressTime[entity.taskId] = now
                            lastBytes = written
                            lastTime = now
                            syncDownloaded(entity, written)
                        }
                        n = inp.read(buffer)
                    }
                    syncDownloaded(entity, part.length())
                }
            }
            return true
        } catch (e: IOException) {
            if (isStopped(entity.taskId)) {
                return false
            }
            fail(entity, "下载中断：" + shortMessage(e))
            return false
        }
    }

    private fun verifyAndInstall(taskId: String) {
        val entity = synchronized(stateLock) {
            val fresh = downloadDao.getById(taskId) ?: return
            if (isStopped(taskId)) return
            if (fresh.state != DownloadState.VERIFYING && fresh.state != DownloadState.INSTALLING) {
                if (!fresh.transition(DownloadState.VERIFYING)) return
                downloadDao.update(fresh)
                notifyChanged()
            }
            fresh
        }
        try {
            val bundle = catalogClient.verifyManifestBundle(entity.modelId, entity.version,
                storage.readManifest(taskId), storage.readManifestSig(taskId))
            val file = bundle.manifest.primaryFile()!!
            if (file.name != entity.fileName || file.sizeBytes != entity.totalBytes ||
                !file.sha256.equals(entity.sha256, ignoreCase = true)) {
                fail(entity, "下载记录与已签名 Manifest 不一致，请取消后重新下载")
                return
            }
            val installed = storage.modelFile(entity.modelId, entity.version, entity.fileName)
            val committed = entity.state == DownloadState.INSTALLING &&
                storage.isInstalled(entity.modelId, entity.version) && installed.isFile
            val part = if (committed) installed else storage.partFile(taskId)
            if (part.length() != entity.totalBytes || !ModelVerifier.sha256Matches(part, entity.sha256)) {
                if (!committed) storage.removeDownloadDir(taskId)
                fail(entity, "SHA-256 校验失败（文件损坏或服务器内容变更）")
                return
            }
            val probe = ModelVerifier.probeGguf(part)
            if (!probe.ok) {
                if (!committed) storage.removeDownloadDir(taskId)
                fail(entity, probe.reason ?: "")
                return
            }
            synchronized(stateLock) {
                val fresh = downloadDao.getById(taskId) ?: return
                if (isStopped(taskId)) return
                if (fresh.state != DownloadState.INSTALLING) {
                    if (!fresh.transition(DownloadState.INSTALLING)) return
                    downloadDao.update(fresh)
                    notifyChanged()
                }
                // 文件发布和数据库提交与取消互斥；昂贵的哈希校验已在锁外完成。
                if (!committed) storage.install(fresh.modelId, fresh.version, part,
                    bundle.json, bundle.sig, fresh.fileName)
                completeInstall(fresh)
            }
        } catch (e: Exception) {
            fail(entity, "校验或安装失败：" + shortMessage(e))
        }
    }

    private fun completeInstall(entity: DownloadEntity) {
        val model = ModelEntity(entity.modelId, entity.version,
            if (entity.displayName == null) entity.modelId else entity.displayName,
            entity.publisher, entity.quantization, entity.licenseSpdx,
            entity.fileName, entity.totalBytes, entity.parameterCount)
        modelDao.insert(model)
        downloadDao.deleteById(entity.taskId)
        storage.removeDownloadDir(entity.taskId)
        speeds.remove(entity.taskId)
        lastProgressBytes.remove(entity.taskId)
        lastProgressTime.remove(entity.taskId)
        notifyChanged()
    }

    private fun fail(entity: DownloadEntity, reason: String) {
        synchronized(stateLock) {
            if (isStopped(entity.taskId)) return
            val fresh = downloadDao.getById(entity.taskId)
            if (fresh == null) {
                return
            }
            fresh.retryCount++
            fresh.lastError = reason
            if (fresh.transition(DownloadState.FAILED)) {
                downloadDao.update(fresh)
                notifyChanged()
            }
        }
    }

    private fun syncDownloaded(entity: DownloadEntity, bytes: Long) {
        synchronized(stateLock) {
            val fresh = downloadDao.getById(entity.taskId)
            if (fresh == null || !(DownloadState.DOWNLOADING == fresh.state)) {
                return
            }
            fresh.bytesDownloaded = bytes
            fresh.updatedAt = System.currentTimeMillis()
            downloadDao.update(fresh)
            notifyChanged()
        }
    }

    private fun isStopped(taskId: String) =
        closed || Thread.currentThread().isInterrupted || pauseFlags.containsKey(taskId)

    private fun cancelCall(taskId: String) {
        val call = activeCalls[taskId]
        call?.cancel()
    }

    private fun truncate(file: File) {
        try {
            FileOutputStream(file, false).use {
                // 截断为空
            }
        } catch (ignored: IOException) {
            file.delete()
        }
    }

    private fun parseContentRange(contentRange: String?): LongArray? {
        val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(contentRange ?: "") ?: return null
        val values = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        if (values[0] > values[1] || values[1] >= values[2]) return null
        return values.toLongArray()
    }

    private fun findEntry(catalog: Catalog?, modelId: String): Catalog.Entry? {
        if (catalog == null || catalog.models == null) {
            return null
        }
        for (entry in catalog.models!!) {
            if (entry.modelId != null && entry.modelId == modelId) {
                return entry
            }
        }
        return null
    }

    private fun describe(e: CatalogException): String {
        return when (e.code()) {
            CatalogException.Code.BAD_SIGNATURE -> "签名验证失败，资产被拒绝：" + e.message
            CatalogException.Code.SCHEMA_INVALID -> "Manifest 非法：" + e.message
            CatalogException.Code.NOT_FOUND -> "资源不存在：" + e.message
            CatalogException.Code.HTTP -> "服务器错误：" + e.message
            else -> "网络错误：" + e.message
        }
    }

    private fun shortMessage(e: Exception): String {
        val msg = e.message
        if (msg == null || msg.isEmpty()) {
            return e.javaClass.simpleName
        }
        return if (msg.length > 120) msg.substring(0, 120) else msg
    }

    private fun notifyChanged() {
        listener?.onChanged()
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROGRESS_MIN_INTERVAL_MS = 250L
        private const val PROGRESS_MIN_BYTES = 256L * 1024
    }
}
