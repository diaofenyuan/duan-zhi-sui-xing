package com.example.localai.feature.download

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.common.Fmt
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.ArrayList

/** 进行中/失败下载任务适配器（P2：真实任务状态，来自 Room + 协调器进度）。 */
class DownloadAdapter(private val actions: Actions) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    interface Actions {
        fun onPauseResume(task: DownloadEntity)

        fun onCancel(task: DownloadEntity)

        fun onRetry(task: DownloadEntity)
    }

    private val tasks = ArrayList<DownloadRepository.TaskView>()

    fun submit(list: List<DownloadRepository.TaskView>) {
        tasks.clear()
        tasks.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val view = tasks[position]
        val task = view.entity

        h.textName.text = if (task.displayName == null) task.modelId else task.displayName
        h.textPercent.text = task.percent().toString() + "%"
        h.bar.progress = task.percent()

        val state = task.state
        val failed = DownloadState.FAILED == state
        h.textError.visibility = if (failed) View.VISIBLE else View.GONE
        h.btnCancel.setOnClickListener { actions.onCancel(task) }

        if (failed) {
            h.textError.text = getString(h, R.string.dl_state_failed_fmt,
                if (task.lastError == null) "未知错误" else task.lastError)
            h.iconState.setImageResource(R.drawable.ic_warning)
            h.iconState.imageTintList = colorList(h, R.color.status_danger)
            h.btnPrimary.setImageResource(R.drawable.ic_refresh)
            h.btnPrimary.imageTintList = colorList(h, R.color.md_primary)
            h.btnPrimary.contentDescription = getString(h, R.string.action_retry)
            h.btnPrimary.setOnClickListener { actions.onRetry(task) }
            h.textState.setText(R.string.dl_state_wait_retry)
            h.textPercent.setTextColor(color(h, R.color.status_danger))
            h.bar.setIndicatorColor(color(h, R.color.status_danger))
            h.bar.visibility = View.INVISIBLE
            return
        }
        h.bar.setIndicatorColor(color(h, R.color.md_primary))
        h.textPercent.setTextColor(color(h, R.color.md_primary))
        h.iconState.setImageResource(R.drawable.ic_download)
        h.bar.visibility = if (DownloadState.DOWNLOADING == state) View.VISIBLE else View.INVISIBLE

        when (state) {
            DownloadState.QUEUED -> {
                h.textState.setText(R.string.dl_state_queued)
                h.btnPrimary.setImageResource(R.drawable.ic_pause)
                h.btnPrimary.isEnabled = false
                h.btnPrimary.alpha = 0.4f
            }
            DownloadState.PAUSED -> {
                h.textState.setText(R.string.dl_state_paused)
                h.btnPrimary.setImageResource(R.drawable.ic_play)
                h.btnPrimary.isEnabled = true
                h.btnPrimary.alpha = 1f
            }
            DownloadState.VERIFYING -> {
                h.textState.setText(R.string.dl_state_verifying)
                h.btnPrimary.setImageResource(R.drawable.ic_pause)
                h.btnPrimary.isEnabled = false
                h.btnPrimary.alpha = 0.4f
            }
            DownloadState.INSTALLING -> {
                h.textState.setText(R.string.dl_state_installing)
                h.btnPrimary.setImageResource(R.drawable.ic_pause)
                h.btnPrimary.isEnabled = false
                h.btnPrimary.alpha = 0.4f
            }
            else -> {
                val remaining = maxOf(0L, task.totalBytes - task.bytesDownloaded)
                val etaSecs = if (remaining <= 0) 0L
                else (remaining / maxOf(1.0, view.speedBps)).toLong()
                h.textState.text = getString(h, R.string.dl_state_downloading_fmt,
                    Fmt.humanBytes(view.speedBps.toLong()), Fmt.humanEta(etaSecs))
                h.btnPrimary.setImageResource(R.drawable.ic_pause)
                h.btnPrimary.isEnabled = true
                h.btnPrimary.alpha = 1f
            }
        }
        h.btnPrimary.contentDescription = getString(h,
            if (DownloadState.PAUSED == state) R.string.action_resume else R.string.action_pause)
        h.btnPrimary.setOnClickListener {
            if (DownloadState.PAUSED == task.state) {
                actions.onPauseResume(task) // resume
            } else if (DownloadState.DOWNLOADING == task.state) {
                actions.onPauseResume(task) // pause
            }
        }
    }

    private fun getString(h: VH, resId: Int, vararg args: Any): String {
        return h.itemView.context.getString(resId, *args)
    }

    private fun color(h: VH, resId: Int): Int {
        return ContextCompat.getColor(h.itemView.context, resId)
    }

    private fun colorList(h: VH, resId: Int): ColorStateList {
        return ColorStateList.valueOf(color(h, resId))
    }

    override fun getItemCount(): Int = tasks.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconState: ImageView = itemView.findViewById(R.id.icon_state)
        val textName: TextView = itemView.findViewById(R.id.text_name)
        val textState: TextView = itemView.findViewById(R.id.text_state)
        val textPercent: TextView = itemView.findViewById(R.id.text_percent)
        val textError: TextView = itemView.findViewById(R.id.text_error)
        val bar: LinearProgressIndicator = itemView.findViewById(R.id.bar)
        val btnPrimary: ImageButton = itemView.findViewById(R.id.btn_primary)
        val btnCancel: ImageButton = itemView.findViewById(R.id.btn_cancel)
    }
}
