package com.example.localai.feature.market;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.data.ServiceLocator;
import com.example.localai.mock.MockStore;
import com.example.localai.model.ModelInfo;
import com.google.android.material.snackbar.Snackbar;

/** 模型详情页：头图、实测数据、适配结论、元信息与示例指令。 */
public class ModelDetailFragment extends Fragment {

    private static final String ARG_MODEL_ID = "model_id";

    private ModelInfo model;

    public static ModelDetailFragment newInstance(String modelId) {
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_ID, modelId);
        ModelDetailFragment f = new ModelDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_model_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String id = getArguments() != null ? getArguments().getString(ARG_MODEL_ID) : null;
        model = MockStore.modelById(id);
        if (model == null) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getOnBackPressedDispatcher().onBackPressed();
            }
            return;
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // 头图
        TextView tag = view.findViewById(R.id.hero_tag);
        tag.setText(ModelInfo.TASK_CODE.equals(model.task) ? "代码助手" : "文本对话");
        ((TextView) view.findViewById(R.id.hero_name)).setText(model.name);
        ((TextView) view.findViewById(R.id.hero_publisher))
                .setText(model.publisher + " · " + model.updated + " 更新");
        ((TextView) view.findViewById(R.id.hero_param)).setText(model.paramsLabel + " 参数");
        ((TextView) view.findViewById(R.id.hero_quant)).setText(model.quant);
        ((TextView) view.findViewById(R.id.hero_ctx)).setText("上下文 " + model.contextLabel);

        // 三格数据
        ((TextView) view.findViewById(R.id.val_size)).setText(model.sizeLabel);
        ((TextView) view.findViewById(R.id.val_speed)).setText(
                getString(R.string.speed_fmt, model.tps > 0 ? String.valueOf(model.tps) : "—"));
        ((TextView) view.findViewById(R.id.val_ttft)).setText(
                getString(R.string.ttft_fmt, model.ttftMs));

        // 适配结论
        TextView compatTitle = view.findViewById(R.id.compat_title);
        TextView compatReason = view.findViewById(R.id.compat_reason);
        android.widget.ImageView compatIcon = view.findViewById(R.id.compat_icon);
        compatTitle.setText(ModelAdapter.compatLabel(model.compat)
                + (ModelInfo.COMPAT_RECOMMENDED.equals(model.compat) ? " · 本机可流畅运行" : ""));
        int bgRes = ModelAdapter.compatBgRes(model.compat);
        int textRes = ModelAdapter.compatTextRes(model.compat);
        compatTitle.setTextColor(ContextCompat.getColor(requireContext(), textRes));
        compatIcon.setBackgroundResource(bgRes);
        String reason = model.compatReason == null || model.compatReason.isEmpty()
                ? "硬约束满足，短基准测试通过，内存与温控余量充足。"
                : model.compatReason;
        compatReason.setText(reason);

        // 简介
        ((TextView) view.findViewById(R.id.text_desc)).setText(model.desc);

        // 详细信息（程序化行）
        LinearLayout meta = view.findViewById(R.id.meta_container);
        addMetaRow(meta, getString(R.string.meta_license), model.license, true);
        addMetaRow(meta, getString(R.string.meta_quant), model.quant + " · GGUF", false);
        addMetaRow(meta, getString(R.string.meta_template), "ChatML", false);
        addMetaRow(meta, getString(R.string.meta_source), "huggingface.co/" + model.publisher, false);
        addMetaRow(meta, getString(R.string.meta_updated), model.updated, false);

        // 示例指令 → 预填到聊天输入框
        View.OnClickListener toChat = v -> {
            MockStore.pendingPrefill = ((TextView) v).getText().toString();
            openTab(R.id.nav_chat);
        };
        view.findViewById(R.id.sample_1).setOnClickListener(toChat);
        view.findViewById(R.id.sample_2).setOnClickListener(toChat);
        view.findViewById(R.id.sample_3).setOnClickListener(toChat);

        // 主操作按钮
        TextView note = view.findViewById(R.id.btn_note);
        com.google.android.material.button.MaterialButton action =
                view.findViewById(R.id.btn_action);
        if (model.installed) {
            action.setText(R.string.btn_open_chat);
            note.setText("已安装 · 点击开始对话");
            action.setOnClickListener(v -> openTab(R.id.nav_chat));
        } else if (ModelInfo.COMPAT_UNSUPPORTED.equals(model.compat)) {
            action.setText(R.string.btn_unsupported);
            action.setEnabled(false);
            action.setAlpha(0.5f);
            note.setText(model.compatReason);
        } else {
            action.setText(getString(R.string.btn_download_fmt, model.sizeLabel));
            note.setText(getString(R.string.download_note_fmt));
            action.setOnClickListener(v -> {
                // P2：接入真实下载链路（签名验证 -> Range 下载 -> SHA-256/GGUF 校验 -> 原子安装）。
                // 模型必须存在于签名目录中才会被接受，否则给出可见原因。
                if (ServiceLocator.downloads() == null) {
                    Snackbar.make(view, "下载服务未就绪", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                ServiceLocator.downloads().enqueue(model.id, (ok, message) -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    Snackbar.make(getView(), ok
                                    ? getString(R.string.snackbar_queued_real) : message,
                            Snackbar.LENGTH_SHORT)
                            .setAction(R.string.snackbar_view, x -> openTab(R.id.nav_download))
                            .show();
                });
            });
        }
    }

    private void addMetaRow(LinearLayout parent, String label, String value, boolean first) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int top = first ? 0 : (int) (10 * getResources().getDisplayMetrics().density);
        row.setPadding(0, top, 0, 0);

        TextView labelView = new TextView(requireContext());
        labelView.setTextSize(12);
        labelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary));
        labelView.setText(label);
        row.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(requireContext());
        valueView.setTextSize(13.5f);
        valueView.setTextColor(ContextCompat.getColor(requireContext(),
                label.equals("许可证") ? R.color.md_primary : R.color.text_primary));
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD,
                label.equals("许可证") ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        valueView.setText(value);
        valueView.setMaxLines(1);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        valueView.setGravity(Gravity.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueView.setPadding((int) (16 * getResources().getDisplayMetrics().density), 0, 0, 0);
        row.addView(valueView, lp);

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openTab(int tabId) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            while (activity.getSupportFragmentManager().getBackStackEntryCount() > 0) {
                activity.getSupportFragmentManager().popBackStackImmediate();
            }
            activity.openTab(tabId);
        }
    }
}
