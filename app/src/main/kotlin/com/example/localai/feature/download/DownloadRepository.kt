package com.example.localai.feature.download

import android.os.Handler
import android.os.Looper
import com.example.localai.data.network.Catalog
import com.example.localai.data.network.CatalogClient
import com.example.localai.data.network.CatalogException
import com.example.localai.data.network.ModelManifest
import com.example.localai.data.room.DownloadDao
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.ModelDao
import com.example.localai.data.room.ModelEntity
import com.example.localai.data.storage.ModelStorageManager
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/** 下载仓库：协调器 + Room + 目录缓存的统一门面，主线程安全快照 + 事件转发。 */
class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val modelDao: ModelDao,
    private val storage: ModelStorageManager,
    private val catalogClient: CatalogClient,
    private val coordinator: DownloadCoordinator
) {

    interface Listener {
        fun onDownloadsChanged()

        fun onCatalogChanged()
    }

    class TaskView(
        @JvmField val entity: DownloadEntity,
        @JvmField val speedBps: Double
    )

    /** 目录条目（只展示 Manifest 签名验证通过的模型）。 */
    class CatalogItem(
        @JvmField val modelId: String?,
        @JvmField val version: String?,
        @JvmField val displayName: String?,
        @JvmField val description: String?,
        @JvmField val publisher: String?,
        @JvmField val licenseSpdx: String?,
        @JvmField val licenseUrl: String?,
        @JvmField val sourceUrl: String?,
        @JvmField val sizeBytes: Long,
        @JvmField val quantization: String?,
        @JvmField val parameterCount: Long,
        @JvmField val contextLength: Long,
        @JvmField val tasks: List<String>?,
        @JvmField val languages: List<String>?,
        @JvmField val weightStatus: String?,
        @JvmField val chatTemplate: String?,
        @JvmField val updatedAt: String?,
        @JvmField val minAndroidApi: Int,
        @JvmField val abis: List<String>?,
        @JvmField val installed: Boolean
    ) {
        fun isApproved(): Boolean = "approved" == weightStatus

        fun isDemo(): Boolean = !isApproved()
    }

    class CatalogView {
        enum class State { LOADING, READY, ERROR }

        @JvmField var state: State = State.LOADING
        @JvmField var models: List<CatalogItem> = Collections.emptyList()
        @JvmField var error: String? = null

        fun isError(): Boolean = state == State.ERROR

        fun isLoading(): Boolean = state == State.LOADING

        fun isReady(): Boolean = state == State.READY
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<Listener>()

    @Volatile
    private var taskCache: List<TaskView> = Collections.emptyList()

    @Volatile
    private var installedCache: List<ModelEntity> = Collections.emptyList()

    @Volatile
    private var catalogCache = CatalogView()

    fun register(listener: Listener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
        // 注册即推送当前快照，避免错过注册前的加载事件
        runOnMain {
            listener.onDownloadsChanged()
            listener.onCatalogChanged()
        }
    }

    fun unregister(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /** 应用启动：加载缓存 -> 刷新目录 -> 恢复未完成任务（全部异步，绝不主线程触库）。 */
    fun loadInitial() {
        coordinator.postControl {
            reloadCachesAndNotifyDownloads()
            refreshCatalog()
            coordinator.recoverPending()
        }
    }

    fun refreshCatalog() {
        setCatalogLoading()
        coordinator.post {
            try {
                val catalog = catalogClient.fetchCatalog()
                val items = ArrayList<CatalogItem>()
                for (entry in catalog.models!!) {
                    try {
                        val manifest = catalogClient.fetchManifest(entry.modelId!!, entry.version!!)
                        val file = manifest.primaryFile()
                        val entity = modelDao.getByModelId(entry.modelId)
                        val installed = entity != null &&
                                storage.modelFile(entity.modelId, entity.version,
                                    entity.fileName ?: "").isFile
                        items.add(CatalogItem(entry.modelId, entry.version,
                            if (entry.displayName == null) entry.modelId else entry.displayName,
                            if (entry.description == null) manifest.description else entry.description,
                            manifest.source?.publisher,
                            manifest.license?.spdx,
                            manifest.license?.url,
                            manifest.source?.url,
                            file?.sizeBytes ?: 0,
                            manifest.quantization,
                            manifest.parameterCount,
                            manifest.contextLength,
                            manifest.tasks ?: Collections.emptyList(),
                            manifest.languages ?: Collections.emptyList(),
                            if ("approved" == manifest.weightStatus) "approved" else "demo",
                            manifest.chatTemplate,
                            manifest.updatedAt,
                            manifest.runtime?.minAndroidApi ?: 0,
                            manifest.runtime?.abis ?: Collections.emptyList(),
                            installed))
                    } catch (e: CatalogException) {
                        android.util.Log.w("localai-catalog",
                            "manifest rejected " + entry.modelId + ": " + e.code() + " " + e.message)
                    }
                }
                setCatalogReady(items)
            } catch (e: CatalogException) {
                setCatalogError(describe(e))
            }
        }
    }

    fun enqueue(modelId: String, callback: DownloadCoordinator.Callback) {
        val wrapped = DownloadCoordinator.Callback { ok, message ->
            runOnMain { callback.onResult(ok, message) }
        }
        coordinator.post { coordinator.start(modelId, wrapped) }
    }

    fun pause(taskId: String) {
        coordinator.post { coordinator.pause(taskId) }
    }

    fun resume(taskId: String) {
        coordinator.post { coordinator.resume(taskId) }
    }

    fun retry(taskId: String) {
        coordinator.post { coordinator.retry(taskId) }
    }

    fun cancel(taskId: String) {
        coordinator.post { coordinator.cancel(taskId) }
    }

    fun deleteModel(modelId: String, version: String, callback: DownloadCoordinator.Callback) {
        coordinator.postControl {
            try {
                storage.delete(modelId, version)
                modelDao.deleteById(modelId, version)
                reloadCachesAndNotifyDownloads()
                refreshCatalog()
                runOnMain { callback.onResult(true, null) }
            } catch (e: RuntimeException) {
                runOnMain { callback.onResult(false, "删除失败：" + e.message) }
            }
        }
    }

    /** 主线程安全快照。 */
    fun tasks(): List<TaskView> = taskCache

    fun installed(): List<ModelEntity> = installedCache

    fun catalogView(): CatalogView = catalogCache

    fun installedBytes(): Long {
        var total = 0L
        for (m in installedCache) {
            total += m.sizeBytes
        }
        return total
    }

    /** 协调器事件（worker 线程）：重载缓存并转发到主线程。内部由组合根接线。 */
    fun onCoordinatorChanged() {
        reloadCaches()
        runOnMain {
            for (l in snapshotListeners()) {
                l.onDownloadsChanged()
            }
        }
    }

    private fun reloadCachesAndNotifyDownloads() {
        reloadCaches()
        runOnMain {
            for (l in snapshotListeners()) {
                l.onDownloadsChanged()
            }
        }
    }

    private fun reloadCaches() {
        val entities = downloadDao.visible()
        val views = ArrayList<TaskView>()
        for (e in entities) {
            views.add(TaskView(e, coordinator.speedOf(e.taskId)))
        }
        taskCache = views
        installedCache = modelDao.all()
        // 目录行的 installed 标记同步
        val view = catalogCache
        if (view.isReady()) {
            val installedIds = HashSet<String>()
            for (m in installedCache) {
                installedIds.add(m.modelId)
            }
            val items = ArrayList<CatalogItem>()
            for (item in view.models) {
                items.add(CatalogItem(item.modelId, item.version, item.displayName,
                    item.description, item.publisher, item.licenseSpdx, item.licenseUrl,
                    item.sourceUrl, item.sizeBytes, item.quantization, item.parameterCount,
                    item.contextLength, item.tasks, item.languages, item.weightStatus,
                    item.chatTemplate, item.updatedAt, item.minAndroidApi, item.abis,
                    installedIds.contains(item.modelId)))
            }
            view.models = items
        }
    }

    private fun setCatalogLoading() {
        val view = CatalogView()
        view.state = CatalogView.State.LOADING
        catalogCache = view
        notifyCatalogChanged()
    }

    private fun setCatalogReady(items: List<CatalogItem>) {
        val view = CatalogView()
        view.state = CatalogView.State.READY
        view.models = items
        catalogCache = view
        notifyCatalogChanged()
    }

    private fun setCatalogError(error: String) {
        val view = CatalogView()
        view.state = CatalogView.State.ERROR
        view.error = error
        catalogCache = view
        notifyCatalogChanged()
    }

    private fun notifyCatalogChanged() {
        runOnMain {
            for (l in snapshotListeners()) {
                l.onCatalogChanged()
            }
        }
    }

    private fun snapshotListeners(): List<Listener> {
        synchronized(listeners) {
            return ArrayList(listeners)
        }
    }

    private fun runOnMain(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private fun describe(e: CatalogException): String {
        return when (e.code()) {
            CatalogException.Code.BAD_SIGNATURE -> "目录签名验证失败：" + e.message
            CatalogException.Code.NOT_FOUND -> "目录资源不存在"
            CatalogException.Code.HTTP -> "服务器错误：" + e.message
            else -> "网络不可用：" + e.message
        }
    }
}
