package com.example.localai.feature.diagnostics;

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

import com.example.localai.R;
import com.example.localai.core.compatibility.CompatibilityEngine;
import com.example.localai.core.device.DeviceProfiler;
import com.example.localai.data.ServiceLocator;
import com.example.localai.data.room.ModelEntity;
import com.example.localai.feature.download.DownloadRepository;

import java.util.Locale;

/** 设备诊断页：真实设备画像（DeviceProfiler）+ 兼容性估算总览（CompatibilityEngine）+ 已安装模型估算基准。 */
public class DiagnosticsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diagnostics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        DeviceProfiler.Profile p = DeviceProfiler.collect(requireContext());

        TextView deviceName = view.findViewById(R.id.diag_device_name);
        if (deviceName != null) {
            deviceName.setText(p.modelLabel());
        }

        ((TextView) view.findViewById(R.id.spec_soc)).setText(p.modelLabel());
        ((TextView) view.findViewById(R.id.spec_ram)).setText(p.ramLabel() + " · 可用 "
                + p.ramAvailMb + " MB");
        ((TextView) view.findViewById(R.id.spec_os)).setText(
                "Android " + p.androidVersion + " · API " + p.sdkInt);
        ((TextView) view.findViewById(R.id.spec_abi)).setText(p.abiLabel());
        ((TextView) view.findViewById(R.id.spec_storage)).setText(
                "可用 " + p.storageLabel() + " · 内存档 "
                + p.memoryClassMb + " MB");
        ((TextView) view.findViewById(R.id.spec_page)).setText(
                p.pageSizeKb > 0 ? p.pageSizeKb + " KB" : "未知");

        ((TextView) view.findViewById(R.id.chip_thermal)).setText(thermalLabel(p.thermalStatusCode));
        ((TextView) view.findViewById(R.id.chip_battery)).setText(
                getString(R.string.chip_battery_fmt, p.batteryPercent < 0 ? 0 : p.batteryPercent));

        // 估算基准：已安装模型（真实清单 + 峰值估算；实测数值 P6 真机采集）
        LinearLayout benchList = view.findViewById(R.id.bench_list);
        benchList.removeAllViews();
        CompatibilityEngine.DeviceSnapshot snapshot = snapshot(p);
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository != null && !repository.installed().isEmpty()) {
            for (ModelEntity entity : repository.installed()) {
                benchList.addView(buildEstRow(entity, snapshot));
            }
        } else {
            benchList.addView(buildReasonRow("暂无已安装模型。安装后此处显示估算结论，实测性能在真机阶段（P6）采集。"));
        }

        // 兼容性总览：目录模型分档统计（估算）
        int recommended = 0;
        int runnable = 0;
        int high = 0;
        int unsupported = 0;
        if (repository != null && repository.catalogView().isReady()) {
            for (DownloadRepository.CatalogItem item : repository.catalogView().models) {
                CompatibilityEngine.Result r = CompatibilityEngine.evaluate(snapshot, constraints(item));
                switch (r.level) {
                    case CompatibilityEngine.LEVEL_RUNNABLE:
                        runnable++;
                        break;
                    case CompatibilityEngine.LEVEL_HIGH_LOAD:
                        high++;
                        break;
                    case CompatibilityEngine.LEVEL_UNSUPPORTED:
                        unsupported++;
                        break;
                    default:
                        recommended++;
                        break;
                }
            }
        }
        setLegend(view, R.id.legend_recommended,
                legend(getString(R.string.compat_recommended), recommended),
                R.color.status_success_container, R.color.status_success);
        setLegend(view, R.id.legend_runnable,
                legend(getString(R.string.compat_runnable), runnable),
                R.color.status_info_container, R.color.status_info);
        setLegend(view, R.id.legend_high,
                legend(getString(R.string.compat_high), high),
                R.color.status_warn_container, R.color.status_warn);
        setLegend(view, R.id.legend_unsupported,
                legend(getString(R.string.compat_unsupported), unsupported),
                R.color.status_danger_container, R.color.status_danger);

        // 原因列表（仅非推荐项，估算）
        LinearLayout reasonList = view.findViewById(R.id.reason_list);
        reasonList.removeAllViews();
        boolean anyReason = false;
        if (repository != null && repository.catalogView().isReady()) {
            for (DownloadRepository.CatalogItem item : repository.catalogView().models) {
                CompatibilityEngine.Result r = CompatibilityEngine.evaluate(snapshot, constraints(item));
                if (!r.reasons.isEmpty() && !r.isRecommended()) {
                    reasonList.addView(buildReasonRow(item.displayName + "：" + String.join("；", r.reasons)));
                    anyReason = true;
                }
            }
        }
        if (!anyReason) {
            reasonList.addView(buildReasonRow("未加载目录或所有模型均为推荐档（估算）。"));
        }
    }

    private CompatibilityEngine.DeviceSnapshot snapshot(DeviceProfiler.Profile p) {
        return new CompatibilityEngine.DeviceSnapshot(
                p.sdkInt,
                p.abis.isEmpty() ? "" : p.abis.get(0),
                p.storageFreeMb * 1024 * 1024,
                p.ramTotalMb * 1024 * 1024);
    }

    private CompatibilityEngine.ModelConstraints constraints(DownloadRepository.CatalogItem item) {
        return new CompatibilityEngine.ModelConstraints(
                item.minAndroidApi, item.abis, item.sizeBytes,
                item.contextLength, item.parameterCount);
    }

    private String thermalLabel(int status) {
        switch (status) {
            case -1:
                return "温控状态未知";
            case 0:
                return "温控正常";
            case 1:
                return "轻度升温";
            case 2:
                return "中度升温";
            case 3:
                return "严重升温";
            default:
                return "过热降频中";
        }
    }

    private View buildEstRow(ModelEntity entity, CompatibilityEngine.DeviceSnapshot snapshot) {
        CompatibilityEngine.ModelConstraints constraints = new CompatibilityEngine.ModelConstraints(
                26, null, entity.sizeBytes, 2048, Math.max(1, entity.parameterCount));
        CompatibilityEngine.Result r = CompatibilityEngine.evaluate(snapshot, constraints);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(0, pad / 2, 0, pad / 2);

        TextView name = new TextView(requireContext());
        name.setTextSize(13);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setText(entity.displayName == null ? entity.modelId : entity.displayName);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView meta = new TextView(requireContext());
        meta.setTextSize(11);
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary));
        meta.setText(String.format(Locale.US, "估算峰值 %d MB",
                r.estimatedPeakBytes / (1024 * 1024)));
        row.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(requireContext());
        status.setTextSize(12);
        status.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int textRes;
        if (r.isRecommended()) {
            textRes = R.color.status_success;
        } else if (CompatibilityEngine.LEVEL_RUNNABLE.equals(r.level)) {
            textRes = R.color.status_info;
        } else if (CompatibilityEngine.LEVEL_HIGH_LOAD.equals(r.level)) {
            textRes = R.color.status_warn;
        } else {
            textRes = R.color.status_danger;
        }
        status.setTextColor(ContextCompat.getColor(requireContext(), textRes));
        status.setText(levelLabel(r.level));
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.leftMargin = dp(10);
        row.addView(status, stp);
        return row;
    }

    private String levelLabel(String level) {
        switch (level) {
            case CompatibilityEngine.LEVEL_RUNNABLE:
                return "可运行";
            case CompatibilityEngine.LEVEL_HIGH_LOAD:
                return "高负载";
            case CompatibilityEngine.LEVEL_UNSUPPORTED:
                return "不支持";
            default:
                return "推荐";
        }
    }

    private String legend(String label, int count) {
        return label + " " + count;
    }

    private void setLegend(View root, int id, String text, int bgRes, int textRes) {
        TextView tv = root.findViewById(id);
        tv.setText(text);
        tv.setBackgroundResource(bgRes);
        tv.setTextColor(ContextCompat.getColor(requireContext(), textRes));
    }

    private View buildReasonRow(String text) {
        TextView tv = new TextView(requireContext());
        tv.setTextSize(12);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tv.setLineSpacing(dp(2), 1f);
        tv.setText(text);
        int pad = dp(5);
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
