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
import com.example.localai.mock.MockStore;
import com.example.localai.model.BenchEntry;
import com.example.localai.model.DeviceProfile;
import com.example.localai.model.ModelInfo;

import java.util.List;
import java.util.Locale;

/** 设备诊断页：设备画像、实时状态、短测基准与兼容性总览（演示数据）。 */
public class DiagnosticsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diagnostics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.spec_soc)).setText(DeviceProfile.SOC);
        ((TextView) view.findViewById(R.id.spec_ram)).setText(DeviceProfile.RAM);
        ((TextView) view.findViewById(R.id.spec_os)).setText(DeviceProfile.OS);
        ((TextView) view.findViewById(R.id.spec_abi)).setText(DeviceProfile.ABI);
        ((TextView) view.findViewById(R.id.spec_storage)).setText(DeviceProfile.STORAGE);
        ((TextView) view.findViewById(R.id.spec_page)).setText(DeviceProfile.PAGE);

        ((TextView) view.findViewById(R.id.chip_battery)).setText(
                getString(R.string.chip_battery_fmt, 86));

        LinearLayout benchList = view.findViewById(R.id.bench_list);
        List<BenchEntry> benches = MockStore.benchmarks();
        double maxTps = 1.0;
        for (BenchEntry b : benches) {
            maxTps = Math.max(maxTps, b.tps);
        }
        for (BenchEntry b : benches) {
            benchList.addView(buildBenchRow(b, maxTps));
        }

        // 兼容性图例
        int recommended = 0;
        int runnable = 0;
        int high = 0;
        int unsupported = 0;
        for (ModelInfo m : MockStore.MODELS) {
            switch (m.compat) {
                case ModelInfo.COMPAT_RUNNABLE:
                    runnable++;
                    break;
                case ModelInfo.COMPAT_HIGH_LOAD:
                    high++;
                    break;
                case ModelInfo.COMPAT_UNSUPPORTED:
                    unsupported++;
                    break;
                default:
                    recommended++;
                    break;
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

        // 不支持原因列表
        LinearLayout reasonList = view.findViewById(R.id.reason_list);
        boolean anyReason = false;
        for (ModelInfo m : MockStore.MODELS) {
            if (!m.compatReason.isEmpty()
                    && !com.example.localai.model.ModelInfo.COMPAT_RECOMMENDED.equals(m.compat)) {
                reasonList.addView(buildReasonRow(m.name + "：" + m.compatReason));
                anyReason = true;
            }
        }
        if (!anyReason && benches.isEmpty()) {
            reasonList.addView(buildReasonRow("暂无已安装模型，安装后可查看实测结论。"));
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

    /** 基准行：名称 + 速度条；右侧 TPS 大数字与 TTFT/内存小字。 */
    private View buildBenchRow(BenchEntry entry, double maxTps) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        row.setPadding(0, pad / 2, 0, pad / 2);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setTextSize(13);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setText(entry.modelName);
        top.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tps = new TextView(requireContext());
        tps.setTextSize(14);
        tps.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tps.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_primary));
        tps.setText(String.format(Locale.US, "%.1f tok/s", entry.tps));
        top.addView(tps, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = new TextView(requireContext());
        meta.setTextSize(11);
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary));
        meta.setText(String.format(Locale.US, "首字 %d ms · 峰值 %.1f GB 内存",
                entry.ttftMs, entry.memGb));
        meta.setPadding(0, dp(3), 0, 0);
        row.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        com.google.android.material.progressindicator.LinearProgressIndicator bar =
                new com.google.android.material.progressindicator.LinearProgressIndicator(requireContext());
        bar.setProgress((int) Math.min(100, Math.round(entry.tps * 100.0 / maxTps)));
        bar.setIndicatorColor(ContextCompat.getColor(requireContext(),
                entry.tps >= 12 ? R.color.status_success : R.color.status_warn));
        bar.setTrackCornerRadius(dp2());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        blp.topMargin = dp(6);
        row.addView(bar, blp);
        return row;
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

    private int dp2() {
        return dp(2);
    }
}
