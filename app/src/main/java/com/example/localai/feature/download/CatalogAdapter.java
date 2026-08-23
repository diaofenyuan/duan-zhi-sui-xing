package com.example.localai.feature.download;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.R;
import com.example.localai.common.Fmt;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** 模型目录条目（P2：来自签名验证通过的目录数据，非写死状态）。 */
public class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.VH> {

    public interface Actions {
        void onInstall(DownloadRepository.CatalogItem item);
    }

    private final List<DownloadRepository.CatalogItem> items = new ArrayList<>();
    private final Actions actions;

    public CatalogAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<DownloadRepository.CatalogItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_catalog, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final DownloadRepository.CatalogItem item = items.get(position);
        h.textName.setText(item.displayName);
        String meta = Fmt.humanBytes(item.sizeBytes) + " · " + item.quantization + " · "
                + (item.licenseSpdx == null ? "未知许可" : item.licenseSpdx);
        h.textMeta.setText(meta);
        if (item.description != null && !item.description.isEmpty()) {
            h.textDesc.setText(item.description);
            h.textDesc.setVisibility(View.VISIBLE);
        } else {
            h.textDesc.setVisibility(View.GONE);
        }
        if (item.installed) {
            h.btn.setEnabled(false);
            h.btn.setAlpha(0.55f);
            h.btn.setText(R.string.catalog_installed);
        } else {
            h.btn.setEnabled(true);
            h.btn.setAlpha(1f);
            h.btn.setText(R.string.catalog_install);
            h.btn.setOnClickListener(v -> actions.onInstall(item));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        final TextView textName;
        final TextView textMeta;
        final TextView textDesc;
        final MaterialButton btn;

        VH(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_name);
            textMeta = itemView.findViewById(R.id.text_meta);
            textDesc = itemView.findViewById(R.id.text_desc);
            btn = itemView.findViewById(R.id.btn_install);
        }
    }
}
