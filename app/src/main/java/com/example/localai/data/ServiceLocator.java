package com.example.localai.data;

import android.content.Context;

import com.example.localai.data.network.CatalogClient;
import com.example.localai.data.network.CatalogConfig;
import com.example.localai.data.network.TrustedKeys;
import com.example.localai.data.room.AppDatabase;
import com.example.localai.data.storage.ModelStorageManager;
import com.example.localai.feature.chat.ChatRepository;
import com.example.localai.feature.download.DownloadCoordinator;
import com.example.localai.feature.download.DownloadRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** 应用级组合根：构建 Room/网络/存储/下载协调器并对外暴露唯一实例。 */
public final class ServiceLocator {

    private static volatile ServiceLocator instance;

    private final DownloadRepository downloadRepository;
    private final DownloadCoordinator downloadCoordinator;
    private final ExecutorService downloadWorker;
    private final ExecutorService controlWorker;
    private final ChatRepository chatRepository;

    private ServiceLocator(Context appContext) {
        AppDatabase database = AppDatabase.build(appContext);
        ModelStorageManager storage = new ModelStorageManager(appContext.getFilesDir());

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        CatalogClient catalogClient = new CatalogClient(CatalogConfig.BASE_URL, httpClient, TrustedKeys.get());

        downloadWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "localai-download");
            thread.setDaemon(true);
            return thread;
        });
        controlWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "localai-dl-control");
            thread.setDaemon(true);
            return thread;
        });
        downloadCoordinator = new DownloadCoordinator(
                database.downloadDao(), database.modelDao(), catalogClient,
                storage, httpClient, downloadWorker, controlWorker, null);
        downloadRepository = new DownloadRepository(
                database.downloadDao(), database.modelDao(), storage,
                catalogClient, downloadCoordinator);
        downloadCoordinator.attachListener(downloadRepository::onCoordinatorChanged);
        chatRepository = new ChatRepository(database.conversationDao(), database.messageDao());
    }

    public static synchronized void init(Context appContext) {
        if (instance == null) {
            instance = new ServiceLocator(appContext.getApplicationContext());
            instance.downloadRepository.loadInitial();
        }
    }

    public static DownloadRepository downloads() {
        return instance == null ? null : instance.downloadRepository;
    }

    public static ChatRepository chat() {
        return instance == null ? null : instance.chatRepository;
    }

    static DownloadCoordinator coordinator() {
        return instance == null ? null : instance.downloadCoordinator;
    }
}
