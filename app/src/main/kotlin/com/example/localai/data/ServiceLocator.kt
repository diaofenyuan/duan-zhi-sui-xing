package com.example.localai.data

import android.content.Context
import com.example.localai.data.network.CatalogConfig
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.feature.chat.ChatRepository
import com.example.localai.feature.download.DownloadCoordinator
import com.example.localai.feature.download.DownloadRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** 应用级组合根：构建 Room/网络/存储/下载协调器并对外暴露唯一实例。 */
class ServiceLocator private constructor(appContext: Context) {

    private val downloadRepository: DownloadRepository
    private val downloadCoordinator: DownloadCoordinator
    private val downloadWorker: ExecutorService
    private val controlWorker: ExecutorService
    private val chatRepository: ChatRepository
    private val libraryRepository: com.example.localai.feature.library.LibraryRepository

    init {
        val database = AppDatabase.build(appContext)
        val storage = ModelStorageManager(appContext.filesDir)

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val catalogClient = CatalogConfig.create(appContext, httpClient)

        downloadWorker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "localai-download").apply { isDaemon = true }
        }
        controlWorker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "localai-dl-control").apply { isDaemon = true }
        }
        downloadCoordinator = DownloadCoordinator(
            database.downloadDao(), database.modelDao(), catalogClient,
            storage, httpClient, downloadWorker, controlWorker, null)
        downloadRepository = DownloadRepository(
            database.downloadDao(), database.modelDao(), storage,
            catalogClient, downloadCoordinator)
        downloadCoordinator.attachListener { downloadRepository.onCoordinatorChanged() }
        chatRepository = ChatRepository(database)
        libraryRepository = com.example.localai.feature.library.LibraryRepository(appContext, database)
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        @JvmStatic
        @Synchronized
        fun init(appContext: Context) {
            if (instance == null) {
                val locator = ServiceLocator(appContext.applicationContext)
                instance = locator
                locator.downloadRepository.loadInitial()
            }
        }

        @JvmStatic
        fun downloads(): DownloadRepository? = instance?.downloadRepository

        @JvmStatic
        fun chat(): ChatRepository? = instance?.chatRepository

        @JvmStatic
        fun library(): com.example.localai.feature.library.LibraryRepository? = instance?.libraryRepository

        @JvmStatic
        fun coordinator(): DownloadCoordinator? = instance?.downloadCoordinator
    }
}
