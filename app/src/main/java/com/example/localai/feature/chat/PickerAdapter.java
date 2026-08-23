package com.example.localai.feature.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.R;
import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.List;

/** 模型切换底部弹层列表（仅展示已安装模型）。 */
public class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.VH> {

    public interface OnPick {
        void onPick(ModelInfo model);
    }

    private final List<ModelInfo> models = new ArrayList<>();
    private final String selectedId;
    private final OnPick onPick;

    public PickerAdapter(List<ModelInfo> models, String selectedId, OnPick onPick) {
        this.models.addAll(models);
        this.selectedId = selectedId;
        this.onPick = onPick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_picker_model, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final ModelInfo model = models.get(position);
        h.iconBg.setBackgroundResource(grad(model.gradIndex));
        h.textIcon.setText(String.valueOf(model.letter()));
        h.textName.setText(model.name);
        h.textMeta.setText(model.paramsLabel + " · " + model.quant + " · " + model.sizeLabel);
        h.imgSelected.setVisibility(
                model.id.equals(selectedId) ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> onPick.onPick(model));
    }

    static int grad(int index) {
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

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        final View iconBg;
        final TextView textIcon;
        final TextView textName;
        final TextView textMeta;
        final ImageView imgSelected;

        VH(@NonNull View itemView) {
            super(itemView);
            iconBg = itemView.findViewById(R.id.icon_bg);
            textIcon = itemView.findViewById(R.id.text_icon);
            textName = itemView.findViewById(R.id.text_name);
            textMeta = itemView.findViewById(R.id.text_meta);
            imgSelected = itemView.findViewById(R.id.img_selected);
        }
    }
}
