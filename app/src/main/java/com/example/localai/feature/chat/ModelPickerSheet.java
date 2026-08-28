package com.example.localai.feature.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.localai.R;
import com.example.localai.model.ModelInfo;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** 已安装模型切换底部弹层。 */
public class ModelPickerSheet extends BottomSheetDialogFragment {

    public interface Callback {
        void onModelPicked(ModelInfo model);
    }

    private final List<ModelInfo> models;
    private final String selectedId;
    private final Callback callback;

    public ModelPickerSheet(List<ModelInfo> models, String selectedId, Callback callback) {
        this.models = models;
        this.selectedId = selectedId;
        this.callback = callback;
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
        android.app.Dialog dialog = super.onCreateDialog(savedInstanceState);
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_model_picker, null, false);

        androidx.recyclerview.widget.RecyclerView list =
                view.findViewById(R.id.list_picker);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        if (models.isEmpty()) {
            Snackbar.make(view, R.string.picker_empty, Snackbar.LENGTH_SHORT).show();
        }
        list.setAdapter(new PickerAdapter(models, selectedId, model -> {
            if (callback != null) {
                callback.onModelPicked(model);
            }
            dismissAllowingStateLoss();
        }));

        com.google.android.material.bottomsheet.BottomSheetDialog sheetDialog =
                (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
        sheetDialog.setContentView(view);
        return dialog;
    }
}
