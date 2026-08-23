package com.example.localai.mock;

import android.os.Handler;
import android.os.Looper;

import com.example.localai.model.DownloadTask;
import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 模拟下载引擎：驱动纯状态机 DownloadTask 的时间部分，仅用于 P1 前端演示。 */
public final class MockDownloadEngine {

    public interface Listener {
        void onDownloadsChanged();
    }

    private static MockDownloadEngine instance;

    public static synchronized MockDownloadEngine get() {
        if (instance == null) {
            instance = new MockDownloadEngine();
        }
        return instance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean ticking = false;

    private static final long TICK_MS = 150;

    private MockDownloadEngine() {
    }

    public List<DownloadTask> tasks() {
        return MockStore.TASKS;
    }

    public void register(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        ensureTicking();
    }

    public void unregister(Listener listener) {
        listeners.remove(listener);
    }

    public void startDownload(ModelInfo model) {
        for (DownloadTask t : tasks()) {
            if (t.modelId.equals(model.id)) {
                return;
            }
        }
        DownloadTask task = new DownloadTask(
                "t-" + model.id + "-" + System.currentTimeMillis(),
                model.id, model.name, model.sizeBytes, 0L,
                DownloadTask.State.DOWNLOADING);
        tasks().add(task);
        notifyChanged();
        ensureTicking();
    }

    public void pause(DownloadTask task) {
        if (task.transition(DownloadTask.Action.PAUSE)) {
            notifyChanged();
        }
    }

    public void resume(DownloadTask task) {
        if (task.transition(DownloadTask.Action.RESUME)) {
            ensureTicking();
            notifyChanged();
        }
    }

    public void retry(DownloadTask task) {
        task.failReason = null;
        task.downloadedBytes = 0;
        task.state = DownloadTask.State.DOWNLOADING;
        ensureTicking();
        notifyChanged();
    }

    public void cancel(DownloadTask task) {
        tasks().remove(task);
        notifyChanged();
    }

    private void ensureTicking() {
        if (ticking) {
            return;
        }
        boolean hasActive = false;
        for (DownloadTask t : tasks()) {
            if (t.state == DownloadTask.State.DOWNLOADING) {
                hasActive = true;
                break;
            }
        }
        if (!hasActive) {
            return;
        }
        ticking = true;
        handler.postDelayed(tickRunnable, TICK_MS);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            List<DownloadTask> snapshot = new ArrayList<>(tasks());
            boolean anyActive = false;
            for (DownloadTask task : snapshot) {
                if (task.state != DownloadTask.State.DOWNLOADING) {
                    continue;
                }
                anyActive = true;
                double jitter = 0.75 + random.nextDouble() * 0.5;
                task.speedBps = (4.5 + random.nextDouble() * 3.0) * 1024 * 1024;
                task.downloadedBytes += (long) (task.speedBps * jitter * TICK_MS / 1000.0);
                if (task.downloadedBytes >= task.totalBytes) {
                    task.downloadedBytes = task.totalBytes;
                    task.transition(DownloadTask.Action.VERIFY);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            completeVerification(task);
                        }
                    }, 1600 + random.nextInt(800));
                }
            }
            notifyChanged();
            if (anyActive) {
                handler.postDelayed(this, TICK_MS);
            } else {
                ticking = false;
            }
        }
    };

    private void completeVerification(DownloadTask task) {
        if (task.state != DownloadTask.State.VERIFYING) {
            return;
        }
        task.transition(DownloadTask.Action.COMPLETE);
        ModelInfo model = MockStore.modelById(task.modelId);
        if (model != null) {
            model.installed = true;
        }
        tasks().remove(task);
        notifyChanged();
    }

    private void notifyChanged() {
        List<Listener> snapshot = new ArrayList<>(listeners);
        for (Listener l : snapshot) {
            l.onDownloadsChanged();
        }
    }
}
