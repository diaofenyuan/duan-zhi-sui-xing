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
import com.example.localai.core.compatibility.CompatibilityEngine;
import com.example.localai.core.device.DeviceProfiler;
import com.example.localai.data.ServiceLocator;
import com.example.localai.feature.download.DownloadRepository;
import com.example.localai.model.ModelInfo;
import com.google.android.material.snackbar.Snackbar;

/** 模型详情页：真实目录数据（签名 Manifest）+ 兼容性结论 + 下载/开始对话 + 已批准/演示标识。 */
public class ModelDetailFragment extends Fragment {

    private static final String ARG_MODEL_ID = "model_id";

    private String modelId;
    private DownloadRepository.CatalogItem currentItem;
    private final DownloadRepository.Listener repositoryListener = new DownloadRepository.Listener() {
        @Override
        public void onDownloadsChanged() {
            refreshFromRepository();
        }

        @Override
        public void onCatalogChanged() {
            refreshFromRepository();
        }
    };

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
        modelId = getArguments() != null ? getArguments().getString(ARG_MODEL_ID) : null;
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getOnBackPressedDispatcher().onBackPressed();
            }
        });

        DownloadRepository repository = ServiceLocator.downloads();
        if (repository != null) {
            repository.register(repositoryListener);
        }
        refreshFromRepository();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository != null) {
            repository.unregister(repositoryListener);
        }
    }

    private void refreshFromRepository() {
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository == null) {
            showUnavailable(getView(), "下载服务未就绪");
            return;
        }
        DownloadRepository.CatalogView view = repository.catalogView();
        DownloadRepository.CatalogItem item = null;
        if (view != null && view.models != null) {
            for (DownloadRepository.CatalogItem candidate : view.models) {
                if (candidate.modelId.equals(modelId)) {
                    item = candidate;
                    break;
                }
            }
        }
        if (item == null) {
            showUnavailable(getView(), "目录尚未加载该模型（刷新后重试）");
            return;
        }
        currentItem = item;
        bind(item);
    }

    private void showUnavailable(View view, String message) {
        if (view == null || !isAdded()) {
            return;
        }
        TextView compatReason = view.findViewById(R.id.compat_reason);
        compatReason.setText(message);
    }

    private void bind(DownloadRepository.CatalogItem item) {
        if (getView() == null || !isAdded()) {
            return;
        }
        View view = getView();

        // 头图
        boolean codeTask = item.tasks != null && item.tasks.contains(ModelInfo.TASK_CODE);
        TextView tag = view.findViewById(R.id.hero_tag);
        tag.setText(codeTask ? "代码助手" : "文本对话");
        TextView status = view.findViewById(R.id.hero_status);
        status.setText(item.isApproved() ? "已批准权重" : "演示载荷");
        status.setVisibility(View.VISIBLE);
        int statusBg = item.isApproved() ? R.color.status_success_container : R.color.status_warn_container;
        int statusText = item.isApproved() ? R.color.status_success : R.color.status_warn;
        status.setBackgroundResource(statusBg);
        status.setTextColor(ContextCompat.getColor(requireContext(), statusText));

        ((TextView) view.findViewById(R.id.hero_name)).setText(item.displayName);
        ((TextView) view.findViewById(R.id.hero_publisher)).setText(
                (item.publisher == null ? "" : item.publisher) + " · " + item.updatedAt + " 更新");
        ((TextView) view.findViewById(R.id.hero_param))
                .setText(MarketModels.paramsLabel(item.parameterCount) + " 参数");
        ((TextView) view.findViewById(R.id.hero_quant)).setText(item.quantization);
        ((TextView) view.findViewById(R.id.hero_ctx))
                .setText("上下文 " + MarketModels.contextLabel(item.contextLength));

        // 三格数据（性能未实测：显示待真机基线）
        ((TextView) view.findViewById(R.id.val_size)).setText(MarketModels.sizeLabel(item.sizeBytes));
        ((TextView) view.findViewById(R.id.val_speed)).setText("待测");
        ((TextView) view.findViewById(R.id.val_ttft)).setText("待测");

        // 适配结论（CompatibilityEngine，估算）
        CompatibilityEngine.ModelConstraints constraints = new CompatibilityEngine.ModelConstraints(
                item.minAndroidApi, item.abis, item.sizeBytes, item.contextLength, item.parameterCount);
        CompatibilityEngine.DeviceSnapshot snapshot = deviceSnapshot();
        CompatibilityEngine.Result result = CompatibilityEngine.evaluate(snapshot, constraints);

        TextView compatTitle = view.findViewById(R.id.compat_title);
        TextView compatReason = view.findViewById(R.id.compat_reason);
        android.widget.ImageView compatIcon = view.findViewById(R.id.compat_icon);
        compatTitle.setText(ModelAdapter.compatLabel(result.level)
                + (result.isRecommended() ? " · 本机可流畅运行" : ""));
        compatTitle.setTextColor(ContextCompat.getColor(requireContext(),
                ModelAdapter.compatTextRes(result.level)));
        compatIcon.setBackgroundResource(ModelAdapter.compatBgRes(result.level));
        String reason = result.reasons.isEmpty()
                ? "硬约束满足，短基准测试通过，内存与温控余量充足（估算值）。"
                : String.join("；", result.reasons);
        compatReason.setText(reason);

        // 简介
        ((TextView) view.findViewById(R.id.text_desc)).setText(item.description);

        // 详细信息
        LinearLayout meta = view.findViewById(R.id.meta_container);
        meta.removeAllViews();
        addMetaRow(meta, getString(R.string.meta_license), item.licenseSpdx, true);
        addMetaRow(meta, getString(R.string.meta_quant),
                item.quantization + " · GGUF", false);
        addMetaRow(meta, getString(R.string.meta_template),
                item.chatTemplate == null ? "chatml" : item.chatTemplate, false);
        addMetaRow(meta, getString(R.string.meta_source),
                item.sourceUrl == null ? "" : item.sourceUrl, false);
        addMetaRow(meta, getString(R.string.meta_updated), item.updatedAt, false);

        // 示例指令 → 带模型打开聊天并预填
        View.OnClickListener toChat = v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.setChatPrefill(((TextView) v).getText().toString());
                activity.openChatWithModel(item.modelId);
            }
        };
        view.findViewById(R.id.sample_1).setOnClickListener(toChat);
        view.findViewById(R.id.sample_2).setOnClickListener(toChat);
        view.findViewById(R.id.sample_3).setOnClickListener(toChat);

        // 主操作按钮
        TextView note = view.findViewById(R.id.btn_note);
        com.google.android.material.button.MaterialButton action =
                view.findViewById(R.id.btn_action);
        action.setEnabled(true);
        action.setAlpha(1f);
        if (item.installed) {
            action.setText(R.string.btn_open_chat);
            note.setText(item.isApproved()
                    ? "已安装 · 点击开始本地对话"
                    : "已安装 · 演示载荷，对话为演示模式");
            action.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openChatWithModel(item.modelId);
                }
            });
        } else if (result.isUnsupported()) {
            action.setText(R.string.btn_unsupported);
            action.setEnabled(false);
            action.setAlpha(0.5f);
            note.setText(reason);
        } else {
            action.setText(getString(R.string.btn_download_fmt, MarketModels.sizeLabel(item.sizeBytes)));
            note.setText(getString(R.string.download_note_fmt));
            action.setOnClickListener(v -> {
                DownloadRepository repository = ServiceLocator.downloads();
                if (repository == null) {
                    Snackbar.make(getView(), "下载服务未就绪", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                repository.enqueue(item.modelId, (ok, message) -> {
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

    private CompatibilityEngine.DeviceSnapshot deviceSnapshot() {
        DeviceProfiler.Profile p = DeviceProfiler.collect(requireContext());
        return new CompatibilityEngine.DeviceSnapshot(
                p.sdkInt,
                p.abis.isEmpty() ? "" : p.abis.get(0),
                p.storageFreeMb * 1024 * 1024,
                p.ramTotalMb * 1024 * 1024);
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
        valueView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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
