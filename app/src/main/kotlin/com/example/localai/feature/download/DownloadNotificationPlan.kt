package com.example.localai.feature.download

import android.content.Context
import com.example.localai.R
import com.example.localai.common.Fmt
import com.example.localai.data.room.DownloadState

/**
 * 下载通知内容规划（纯计算，便于单测）。
 * 输入为 DownloadRepository 的易失任务快照，可在任意线程调用；无活动任务时返回 null，
 * 调用方据此结束前台服务。
 */
internal object DownloadNotificationPlan {

    /** 通知内容快照。signature 用于节流：状态/进度未变时不重复刷通知。 */
    class Plan(
        @JvmField val title: String,
        @JvmField val text: String,
        @JvmField val percent: Int,
        @JvmField val indeterminate: Boolean,
        @JvmField val signature: String
    )

    fun of(context: Context, tasks: List<DownloadRepository.TaskView>): Plan? {
        val active = ArrayList<DownloadRepository.TaskView>()
        for (task in tasks) {
            if (DownloadState.isActive(task.entity.state)) {
                active.add(task)
            }
        }
        if (active.isEmpty()) {
            return null
        }
        if (active.size > 1) {
            val percent = aggregatePercent(active)
            return Plan(
                context.getString(R.string.dl_notif_multi_title_fmt, active.size),
                context.getString(R.string.dl_notif_multi_text_fmt, percent),
                percent, false, signatureOf(active))
        }
        val task = active[0]
        val entity = task.entity
        val text = when (entity.state) {
            DownloadState.DOWNLOADING -> {
                val remaining = entity.totalBytes - entity.bytesDownloaded
                val eta = (remaining / maxOf(1.0, task.speedBps)).toLong()
                context.getString(R.string.dl_state_downloading_fmt,
                    Fmt.humanBytes(task.speedBps.toLong()), Fmt.humanEta(eta))
            }
            DownloadState.QUEUED -> context.getString(R.string.dl_state_queued)
            DownloadState.VERIFYING -> context.getString(R.string.dl_state_verifying)
            else -> context.getString(R.string.dl_state_installing)
        }
        return Plan(entity.displayName ?: entity.modelId, text, entity.percent(),
            DownloadState.DOWNLOADING != entity.state, signatureOf(active))
    }

    /** 多任务时按字节数加权聚合总进度，避免小文件拉高百分比。 */
    private fun aggregatePercent(active: List<DownloadRepository.TaskView>): Int {
        var done = 0L
        var total = 0L
        for (task in active) {
            done += task.entity.bytesDownloaded
            total += task.entity.totalBytes
        }
        if (total <= 0) {
            return 0
        }
        return (done * 100 / total).coerceIn(0L, 100L).toInt()
    }

    private fun signatureOf(active: List<DownloadRepository.TaskView>): String {
        val sb = StringBuilder()
        for (task in active) {
            sb.append(task.entity.taskId).append(':').append(task.entity.state)
                .append(':').append(task.entity.percent()).append(';')
        }
        return sb.toString()
    }
}