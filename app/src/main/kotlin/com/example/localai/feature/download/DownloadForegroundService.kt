package com.example.localai.feature.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.DownloadState

/**
 * 下载保活前台服务（dataSync）：
 * 下载/校验/安装期间保持前台状态，防止应用进入后台后被系统冻结或回收导致传输中断
 * （实测：无前台服务时切后台约 10 秒即冻结，回前台任务已失败）；
 * 传输期间持有 PARTIAL_WAKE_LOCK，避免息屏后 CPU 挂起让 socket 停走。
 *
 * 生命周期由任务快照驱动：有活动任务才启动，任务清空立即停止；通知为前台服务的常驻载体，
 * 内容随状态与进度刷新（节流 1 秒/次）。进程被杀后的恢复仍由 Room 持久化 + recoverPending 负责。
 */
class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastSignature: String? = null
    @Volatile private var lastNotifiedAt = 0L

    private val repositoryListener = object : DownloadRepository.Listener {
        override fun onDownloadsChanged() {
            refreshNotification()
        }

        override fun onCatalogChanged() {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceLocator.downloads()?.register(repositoryListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCleanly()
            return START_NOT_STICKY
        }
        running = true
        val plan = currentPlan()
        if (plan == null) {
            // 启动指令与任务结束的竞态：先履行 startForeground 约定，再立即退出
            enterForeground(buildNotification(fallbackPlan()))
            stopCleanly()
            return START_NOT_STICKY
        }
        lastSignature = plan.signature
        lastNotifiedAt = SystemClock.elapsedRealtime()
        enterForeground(buildNotification(plan))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServiceLocator.downloads()?.unregister(repositoryListener)
        releaseWakeLock()
        running = false
        super.onDestroy()
    }

    /**
     * Android 15+ 对 dataSync 前台服务有累计 6 小时上限；到时转为用户可见的「已暂停」并退出前台，
     * 避免被系统按超时强制结束。
     */
    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        val repository = ServiceLocator.downloads()
        if (repository != null) {
            for (task in repository.tasks()) {
                if (DownloadState.DOWNLOADING == task.entity.state) {
                    repository.pause(task.entity.taskId)
                }
            }
        }
        stopCleanly()
    }

    /** 任务快照变化（主线程回调）：刷新通知；活动任务清空则结束前台服务。 */
    private fun refreshNotification() {
        val plan = currentPlan()
        if (plan == null) {
            stopCleanly()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (plan.signature == lastSignature) {
            return
        }
        if (now - lastNotifiedAt < MIN_UPDATE_INTERVAL_MS) {
            // 进度变化远快于 1 秒一次，节流避免高频刷通知
            return
        }
        lastSignature = plan.signature
        lastNotifiedAt = now
        // Android 13+ 未授权时跳过刷新：前台服务仍保活，只是通知不可见
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(plan))
        }
    }

    private fun currentPlan(): DownloadNotificationPlan.Plan? =
        DownloadNotificationPlan.of(this, ServiceLocator.downloads()?.tasks().orEmpty())

    private fun fallbackPlan(): DownloadNotificationPlan.Plan =
        DownloadNotificationPlan.Plan(getString(R.string.app_name),
            getString(R.string.dl_notif_preparing), 0, true, "fallback")

    private fun enterForeground(notification: Notification) {
        acquireWakeLock()
        // API 29 起必须声明类型；manifest 已声明 dataSync 并申请对应权限
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun stopCleanly() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(plan: DownloadNotificationPlan.Plan): Notification {
        val contentIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(plan.title)
            .setContentText(plan.text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, plan.percent, plan.indeterminate)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.dl_notif_channel),
            NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.dl_notif_channel_desc)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    /** 不设超时：下载时长不可预期，释放点固定为任务清空或服务销毁（stopCleanly/onDestroy）。 */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) {
            return
        }
        val power = getSystemService(PowerManager::class.java) ?: return
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "localai:download")
        lock.setReferenceCounted(false)
        lock.acquire()
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        /** 通知点击入口：直接打开下载页。 */
        const val ACTION_OPEN_DOWNLOADS = "com.example.localai.action.OPEN_DOWNLOADS"
        private const val ACTION_SYNC = "com.example.localai.action.DOWNLOAD_SYNC"
        private const val ACTION_STOP = "com.example.localai.action.DOWNLOAD_STOP"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_UPDATE_INTERVAL_MS = 1000L

        @Volatile
        private var running = false

        /** 按当前任务快照同步保活状态：有活动任务确保前台服务运行，否则停止。 */
        @JvmStatic
        fun sync(context: Context) {
            val repository = ServiceLocator.downloads() ?: return
            val active = repository.tasks().any { DownloadState.isActive(it.entity.state) }
            if (active) {
                ensureStarted(context)
            } else {
                stopIfIdle(context)
            }
        }

        private fun ensureStarted(context: Context) {
            if (running) {
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).setAction(ACTION_SYNC)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (ignored: IllegalStateException) {
                // Android 12+ 限制后台启动前台服务（如恢复任务恰好发生在后台）：
                // 忽略本次，回到前台时 MainActivity.onStart 会再次 sync
            }
        }

        private fun stopIfIdle(context: Context) {
            if (!running) {
                return
            }
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }

        /**
         * Android 13+ 通知权限：未授权时前台服务仍可保活，只是进度通知不可见；
         * 因此在发起下载时按需申请，拒绝也不阻断下载。
         */
        @JvmStatic
        fun ensureNotificationPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT < 33) {
                return
            }
            val granted = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }
}