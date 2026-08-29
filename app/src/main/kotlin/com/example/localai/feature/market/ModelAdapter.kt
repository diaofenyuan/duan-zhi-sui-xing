package com.example.localai.feature.market

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.model.ModelInfo
import java.util.ArrayList

/** 市场列表适配器。 */
class ModelAdapter(private val listener: OnModelClickListener?) :
    RecyclerView.Adapter<ModelAdapter.VH>() {

    fun interface OnModelClickListener {
        fun onModelClick(model: ModelInfo)
    }

    private val items = ArrayList<ModelInfo>()

    fun submit(models: List<ModelInfo>) {
        items.clear()
        items.addAll(models)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val model = items[position]

        h.iconBg.setBackgroundResource(gradRes(model.gradIndex))
        h.textIcon.text = model.letter().toString()
        h.textName.text = model.name

        bindCompatBadge(h.badgeCompat, model.compat)

        h.textMeta.text = model.publisher + " · " + model.paramsLabel
        h.tagCtx.text = "上下文 " + model.contextLabel
        h.tagSize.text = model.quant
        h.textSize.text = model.sizeLabel

        h.itemView.setOnClickListener {
            listener?.onModelClick(model)
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconBg: View = itemView.findViewById(R.id.icon_bg)
        val textIcon: TextView = itemView.findViewById(R.id.text_icon)
        val textName: TextView = itemView.findViewById(R.id.text_name)
        val badgeCompat: TextView = itemView.findViewById(R.id.badge_compat)
        val textMeta: TextView = itemView.findViewById(R.id.text_meta)
        val tagCtx: TextView = itemView.findViewById(R.id.tag_ctx)
        val tagSize: TextView = itemView.findViewById(R.id.tag_size)
        val textSize: TextView = itemView.findViewById(R.id.text_size)
    }

    companion object {
        @JvmStatic
        fun gradRes(index: Int): Int {
            return when (index % 4) {
                1 -> R.drawable.grad_b
                2 -> R.drawable.grad_c
                3 -> R.drawable.grad_d
                else -> R.drawable.grad_a
            }
        }

        /** 兼容徽标：按状态设置容器底色与文字色，返回标签文案。 */
        @JvmStatic
        fun compatLabel(compat: String?): String {
            if (ModelInfo.COMPAT_RUNNABLE == compat) {
                return "可运行"
            }
            if (ModelInfo.COMPAT_HIGH_LOAD == compat) {
                return "高负载"
            }
            if (ModelInfo.COMPAT_UNSUPPORTED == compat) {
                return "不支持"
            }
            return "推荐"
        }

        @JvmStatic
        fun compatBgRes(compat: String?): Int {
            if (ModelInfo.COMPAT_RUNNABLE == compat) {
                return R.color.status_info_container
            }
            if (ModelInfo.COMPAT_HIGH_LOAD == compat) {
                return R.color.status_warn_container
            }
            if (ModelInfo.COMPAT_UNSUPPORTED == compat) {
                return R.color.status_danger_container
            }
            return R.color.status_success_container
        }

        @JvmStatic
        fun compatTextRes(compat: String?): Int {
            if (ModelInfo.COMPAT_RUNNABLE == compat) {
                return R.color.status_info
            }
            if (ModelInfo.COMPAT_HIGH_LOAD == compat) {
                return R.color.status_warn
            }
            if (ModelInfo.COMPAT_UNSUPPORTED == compat) {
                return R.color.status_danger
            }
            return R.color.status_success
        }

        @JvmStatic
        fun bindCompatBadge(badge: TextView, compat: String?) {
            badge.text = compatLabel(compat)
            badge.setBackgroundResource(compatBgRes(compat))
            badge.setTextColor(ContextCompat.getColor(badge.context, compatTextRes(compat)))
        }
    }
}
