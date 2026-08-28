package com.example.localai.feature.download;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.R;
import com.example.localai.common.Fmt;
import com.example.localai.data.room.DownloadEntity;
import com.example.localai.data.room.DownloadState;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** 进行中/失败下载任务适配器（P2：真实任务状态，来自 Room + 协调器进度）。 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.VH> {

    public interface Actions {
        void onPauseResume(DownloadEntity task);

        void onCancel(DownloadEntity task);

        void onRetry(DownloadEntity task);
    }

    private final List<DownloadRepository.TaskView> tasks = new ArrayList<>();
    private final Actions actions;

    public DownloadAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<DownloadRepository.TaskView> list) {
        tasks.clear();
        tasks.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final DownloadRepository.TaskView view = tasks.get(position);
        final DownloadEntity task = view.entity;

        h.textName.setText(task.displayName == null ? task.modelId : task.displayName);
        h.textPercent.setText(task.percent() + "%");
        h.bar.setProgress(task.percent());

        String state = task.state;
        boolean failed = DownloadState.FAILED.equals(state);
        h.textError.setVisibility(failed ? View.VISIBLE : View.GONE);
        h.btnCancel.setOnClickListener(v -> actions.onCancel(task));

        if (failed) {
            h.textError.setText(getString(h, R.string.dl_state_failed_fmt,
                    task.lastError == null ? "未知错误" : task.lastError));
            h.iconState.setImageResource(R.drawable.ic_warning);
            h.iconState.setImageTintList(colorList(h, R.color.status_danger));
            h.btnPrimary.setImageResource(R.drawable.ic_refresh);
            h.btnPrimary.setImageTintList(colorList(h, R.color.md_primary));
            h.btnPrimary.setContentDescription(getString(h, R.string.action_retry));
            h.btnPrimary.setOnClickListener(v -> actions.onRetry(task));
            h.textState.setText(R.string.dl_state_wait_retry);
            h.textPercent.setTextColor(color(h, R.color.status_danger));
            h.bar.setIndicatorColor(color(h, R.color.status_danger));
            h.bar.setVisibility(View.INVISIBLE);
            return;
        }
        h.bar.setIndicatorColor(color(h, R.color.md_primary));
        h.textPercent.setTextColor(color(h, R.color.md_primary));
        h.iconState.setImageResource(R.drawable.ic_download);
        h.bar.setVisibility(DownloadState.DOWNLOADING.equals(state) ? View.VISIBLE : View.INVISIBLE);

        switch (state) {
            case DownloadState.QUEUED:
                h.textState.setText(R.string.dl_state_queued);
                h.btnPrimary.setImageResource(R.drawable.ic_pause);
                h.btnPrimary.setEnabled(false);
                h.btnPrimary.setAlpha(0.4f);
                break;
            case DownloadState.PAUSED:
                h.textState.setText(R.string.dl_state_paused);
                h.btnPrimary.setImageResource(R.drawable.ic_play);
                h.btnPrimary.setEnabled(true);
                h.btnPrimary.setAlpha(1f);
                break;
            case DownloadState.VERIFYING:
                h.textState.setText(R.string.dl_state_verifying);
                h.btnPrimary.setImageResource(R.drawable.ic_pause);
                h.btnPrimary.setEnabled(false);
                h.btnPrimary.setAlpha(0.4f);
                break;
            case DownloadState.INSTALLING:
                h.textState.setText(R.string.dl_state_installing);
                h.btnPrimary.setImageResource(R.drawable.ic_pause);
                h.btnPrimary.setEnabled(false);
                h.btnPrimary.setAlpha(0.4f);
                break;
            default:
                long remaining = Math.max(0L, task.totalBytes - task.bytesDownloaded);
                long etaSecs = remaining <= 0 ? 0 : (long) (remaining / Math.max(1.0, view.speedBps));
                h.textState.setText(getString(h, R.string.dl_state_downloading_fmt,
                        Fmt.humanBytes((long) view.speedBps), Fmt.humanEta(etaSecs)));
                h.btnPrimary.setImageResource(R.drawable.ic_pause);
                h.btnPrimary.setEnabled(true);
                h.btnPrimary.setAlpha(1f);
                break;
        }
        h.btnPrimary.setContentDescription(getString(h,
                DownloadState.PAUSED.equals(state) ? R.string.action_resume : R.string.action_pause));
        h.btnPrimary.setOnClickListener(v -> {
            if (DownloadState.PAUSED.equals(task.state)) {
                actions.onPauseResume(task); // resume
            } else if (DownloadState.DOWNLOADING.equals(task.state)) {
                actions.onPauseResume(task); // pause
            }
        });
    }

    private String getString(VH h, int resId, Object... args) {
        return h.itemView.getContext().getString(resId, args);
    }

    private int color(VH h, int resId) {
        return ContextCompat.getColor(h.itemView.getContext(), resId);
    }

    private ColorStateList colorList(VH h, int resId) {
        return ColorStateList.valueOf(color(h, resId));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        final ImageView iconState;
        final TextView textName;
        final TextView textState;
        final TextView textPercent;
        final TextView textError;
        final LinearProgressIndicator bar;
        final ImageButton btnPrimary;
        final ImageButton btnCancel;

        VH(@NonNull View itemView) {
            super(itemView);
            iconState = itemView.findViewById(R.id.icon_state);
            textName = itemView.findViewById(R.id.text_name);
            textState = itemView.findViewById(R.id.text_state);
            textPercent = itemView.findViewById(R.id.text_percent);
            textError = itemView.findViewById(R.id.text_error);
            bar = itemView.findViewById(R.id.bar);
            btnPrimary = itemView.findViewById(R.id.btn_primary);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
        }
    }
}
