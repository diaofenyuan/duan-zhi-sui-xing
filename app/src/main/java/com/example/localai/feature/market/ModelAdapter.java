package com.example.localai.feature.market;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.R;
import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.List;

/** 市场列表适配器。 */
public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.VH> {

    public interface OnModelClickListener {
        void onModelClick(ModelInfo model);
    }

    private final List<ModelInfo> items = new ArrayList<>();
    private final OnModelClickListener listener;

    public ModelAdapter(OnModelClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ModelInfo> models) {
        items.clear();
        items.addAll(models);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final ModelInfo model = items.get(position);

        h.iconBg.setBackgroundResource(gradRes(model.gradIndex));
        h.textIcon.setText(String.valueOf(model.letter()));
        h.textName.setText(model.name);

        bindCompatBadge(h.badgeCompat, model.compat);

        h.textMeta.setText(model.publisher + " · " + model.paramsLabel);
        h.tagCtx.setText("上下文 " + model.contextLabel);
        h.tagSize.setText(model.quant);
        h.textSize.setText(model.sizeLabel);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onModelClick(model);
            }
        });
    }

    static int gradRes(int index) {
        switch (index % 4) {
            case 1:
                return R.drawable.grad_b;
            case 2:
                return R.drawable.grad_c;
            case 3:
                return R.drawable.grad_d;
            default:
                return R.drawable.grad_a;
        }
    }

    /** 兼容徽标：按状态设置容器底色与文字色，返回标签文案。 */
    static String compatLabel(String compat) {
        if (ModelInfo.COMPAT_RUNNABLE.equals(compat)) {
            return "可运行";
        }
        if (ModelInfo.COMPAT_HIGH_LOAD.equals(compat)) {
            return "高负载";
        }
        if (ModelInfo.COMPAT_UNSUPPORTED.equals(compat)) {
            return "不支持";
        }
        return "推荐";
    }

    static int compatBgRes(String compat) {
        if (ModelInfo.COMPAT_RUNNABLE.equals(compat)) {
            return R.color.status_info_container;
        }
        if (ModelInfo.COMPAT_HIGH_LOAD.equals(compat)) {
            return R.color.status_warn_container;
        }
        if (ModelInfo.COMPAT_UNSUPPORTED.equals(compat)) {
            return R.color.status_danger_container;
        }
        return R.color.status_success_container;
    }

    static int compatTextRes(String compat) {
        if (ModelInfo.COMPAT_RUNNABLE.equals(compat)) {
            return R.color.status_info;
        }
        if (ModelInfo.COMPAT_HIGH_LOAD.equals(compat)) {
            return R.color.status_warn;
        }
        if (ModelInfo.COMPAT_UNSUPPORTED.equals(compat)) {
            return R.color.status_danger;
        }
        return R.color.status_success;
    }

    static void bindCompatBadge(TextView badge, String compat) {
        badge.setText(compatLabel(compat));
        badge.setBackgroundResource(compatBgRes(compat));
        badge.setTextColor(ContextCompat.getColor(badge.getContext(), compatTextRes(compat)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        final View iconBg;
        final TextView textIcon;
        final TextView textName;
        final TextView badgeCompat;
        final TextView textMeta;
        final TextView tagCtx;
        final TextView tagSize;
        final TextView textSize;

        VH(@NonNull View itemView) {
            super(itemView);
            iconBg = itemView.findViewById(R.id.icon_bg);
            textIcon = itemView.findViewById(R.id.text_icon);
            textName = itemView.findViewById(R.id.text_name);
            badgeCompat = itemView.findViewById(R.id.badge_compat);
            textMeta = itemView.findViewById(R.id.text_meta);
            tagCtx = itemView.findViewById(R.id.tag_ctx);
            tagSize = itemView.findViewById(R.id.tag_size);
            textSize = itemView.findViewById(R.id.text_size);
        }
    }
}
