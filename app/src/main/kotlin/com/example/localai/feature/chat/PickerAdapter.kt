package com.example.localai.feature.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.model.ModelInfo
import java.util.ArrayList

/** 模型切换底部弹层列表（仅展示已安装模型）。 */
class PickerAdapter(
    models: List<ModelInfo>,
    private val selectedId: String?,
    private val onPick: OnPick
) : RecyclerView.Adapter<PickerAdapter.VH>() {

    fun interface OnPick {
        fun onPick(model: ModelInfo)
    }

    private val models = ArrayList<ModelInfo>()

    init {
        this.models.addAll(models)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picker_model, parent, false))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val model = models[position]
        h.iconBg.setBackgroundResource(grad(model.gradIndex))
        h.textIcon.text = model.letter().toString()
        h.textName.text = model.name
        h.textMeta.text = model.paramsLabel + " · " + model.quant + " · " + model.sizeLabel
        h.imgSelected.visibility = if (model.id == selectedId) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onPick.onPick(model) }
    }

    override fun getItemCount(): Int = models.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconBg: View = itemView.findViewById(R.id.icon_bg)
        val textIcon: TextView = itemView.findViewById(R.id.text_icon)
        val textName: TextView = itemView.findViewById(R.id.text_name)
        val textMeta: TextView = itemView.findViewById(R.id.text_meta)
        val imgSelected: ImageView = itemView.findViewById(R.id.img_selected)
    }

    companion object {
        @JvmStatic
        fun grad(index: Int): Int {
            return when (index % 4) {
                1 -> R.drawable.grad_b
                2 -> R.drawable.grad_c
                3 -> R.drawable.grad_d
                else -> R.drawable.grad_a
            }
        }
    }
}
