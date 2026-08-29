package com.example.localai.feature.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.common.Fmt
import com.google.android.material.button.MaterialButton
import java.util.ArrayList

/** 模型目录条目（P2：来自签名验证通过的目录数据，非写死状态）。 */
class CatalogAdapter(private val actions: Actions) : RecyclerView.Adapter<CatalogAdapter.VH>() {

    fun interface Actions {
        fun onInstall(item: DownloadRepository.CatalogItem)
    }

    private val items = ArrayList<DownloadRepository.CatalogItem>()

    fun submit(list: List<DownloadRepository.CatalogItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog, parent, false))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.textName.text = item.displayName
        val meta = Fmt.humanBytes(item.sizeBytes) + " · " + item.quantization + " · " +
                (if (item.licenseSpdx == null) "未知许可" else item.licenseSpdx)
        h.textMeta.text = meta
        if (item.description != null && item.description.isNotEmpty()) {
            h.textDesc.text = item.description
            h.textDesc.visibility = View.VISIBLE
        } else {
            h.textDesc.visibility = View.GONE
        }
        if (item.installed) {
            h.btn.isEnabled = false
            h.btn.alpha = 0.55f
            h.btn.setText(R.string.catalog_installed)
        } else {
            h.btn.isEnabled = true
            h.btn.alpha = 1f
            h.btn.setText(R.string.catalog_install)
            h.btn.setOnClickListener { actions.onInstall(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.text_name)
        val textMeta: TextView = itemView.findViewById(R.id.text_meta)
        val textDesc: TextView = itemView.findViewById(R.id.text_desc)
        val btn: MaterialButton = itemView.findViewById(R.id.btn_install)
    }
}
