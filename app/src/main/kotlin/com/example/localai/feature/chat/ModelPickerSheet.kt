package com.example.localai.feature.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.model.ModelInfo
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar

/** 已安装模型切换底部弹层。 */
class ModelPickerSheet(
    private val models: List<ModelInfo>,
    private val selectedId: String?,
    private val callback: Callback?
) : BottomSheetDialogFragment() {

    fun interface Callback {
        fun onModelPicked(model: ModelInfo)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_model_picker, null, false)

        val list = view.findViewById<RecyclerView>(R.id.list_picker)
        list.layoutManager = LinearLayoutManager(requireContext())
        if (models.isEmpty()) {
            Snackbar.make(view, R.string.picker_empty, Snackbar.LENGTH_SHORT).show()
        }
        list.adapter = PickerAdapter(models, selectedId) { model ->
            callback?.onModelPicked(model)
            dismissAllowingStateLoss()
        }

        val sheetDialog = dialog as BottomSheetDialog
        sheetDialog.setContentView(view)
        return dialog
    }
}
