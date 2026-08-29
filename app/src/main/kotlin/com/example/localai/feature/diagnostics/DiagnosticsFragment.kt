package com.example.localai.feature.diagnostics

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.R
import com.example.localai.core.compatibility.CompatibilityEngine
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.ModelEntity
import com.example.localai.feature.download.DownloadRepository
import java.util.Locale

/** 设备诊断页：真实设备画像（DeviceProfiler）+ 兼容性估算总览（CompatibilityEngine）+ 已安装模型估算基准。 */
class DiagnosticsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_diagnostics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val p = DeviceProfiler.collect(requireContext())

        val deviceName = view.findViewById<TextView>(R.id.diag_device_name)
        deviceName.text = p.modelLabel()

        view.findViewById<TextView>(R.id.spec_soc).text = p.modelLabel()
        view.findViewById<TextView>(R.id.spec_ram).text = p.ramLabel() + " · 可用 " +
                p.ramAvailMb + " MB"
        view.findViewById<TextView>(R.id.spec_os).text =
            "Android " + p.androidVersion + " · API " + p.sdkInt
        view.findViewById<TextView>(R.id.spec_abi).text = p.abiLabel()
        view.findViewById<TextView>(R.id.spec_storage).text =
            "可用 " + p.storageLabel() + " · 内存档 " + p.memoryClassMb + " MB"
        view.findViewById<TextView>(R.id.spec_page).text =
            if (p.pageSizeKb > 0) p.pageSizeKb.toString() + " KB" else "未知"

        view.findViewById<TextView>(R.id.chip_thermal).text = thermalLabel(p.thermalStatusCode)
        view.findViewById<TextView>(R.id.chip_battery).text =
            getString(R.string.chip_battery_fmt, if (p.batteryPercent < 0) 0 else p.batteryPercent)

        // 估算基准：已安装模型（真实清单 + 峰值估算；实测数值 P6 真机采集）
        val benchList = view.findViewById<LinearLayout>(R.id.bench_list)
        benchList.removeAllViews()
        val snapshot = snapshot(p)
        val repository = ServiceLocator.downloads()
        if (repository != null && repository.installed().isNotEmpty()) {
            for (entity in repository.installed()) {
                benchList.addView(buildEstRow(entity, snapshot))
            }
        } else {
            benchList.addView(buildReasonRow("暂无已安装模型。安装后此处显示估算结论，实测性能在真机阶段（P6）采集。"))
        }

        // 兼容性总览：目录模型分档统计（估算）
        var recommended = 0
        var runnable = 0
        var high = 0
        var unsupported = 0
        if (repository != null && repository.catalogView().isReady()) {
            for (item in repository.catalogView().models) {
                val r = CompatibilityEngine.evaluate(snapshot, constraints(item))
                when (r.level) {
                    CompatibilityEngine.LEVEL_RUNNABLE -> runnable++
                    CompatibilityEngine.LEVEL_HIGH_LOAD -> high++
                    CompatibilityEngine.LEVEL_UNSUPPORTED -> unsupported++
                    else -> recommended++
                }
            }
        }
        setLegend(view, R.id.legend_recommended,
            legend(getString(R.string.compat_recommended), recommended),
            R.color.status_success_container, R.color.status_success)
        setLegend(view, R.id.legend_runnable,
            legend(getString(R.string.compat_runnable), runnable),
            R.color.status_info_container, R.color.status_info)
        setLegend(view, R.id.legend_high,
            legend(getString(R.string.compat_high), high),
            R.color.status_warn_container, R.color.status_warn)
        setLegend(view, R.id.legend_unsupported,
            legend(getString(R.string.compat_unsupported), unsupported),
            R.color.status_danger_container, R.color.status_danger)

        // 原因列表（仅非推荐项，估算）
        val reasonList = view.findViewById<LinearLayout>(R.id.reason_list)
        reasonList.removeAllViews()
        var anyReason = false
        if (repository != null && repository.catalogView().isReady()) {
            for (item in repository.catalogView().models) {
                val r = CompatibilityEngine.evaluate(snapshot, constraints(item))
                if (r.reasons.isNotEmpty() && !r.isRecommended()) {
                    reasonList.addView(buildReasonRow(item.displayName + "：" + r.reasons.joinToString("；")))
                    anyReason = true
                }
            }
        }
        if (!anyReason) {
            reasonList.addView(buildReasonRow("未加载目录或所有模型均为推荐档（估算）。"))
        }
    }

    private fun snapshot(p: DeviceProfiler.Profile): CompatibilityEngine.DeviceSnapshot {
        val abis = p.abis
        return CompatibilityEngine.DeviceSnapshot(
            p.sdkInt,
            if (abis == null || abis.isEmpty()) "" else abis[0],
            p.storageFreeMb * 1024 * 1024,
            p.ramTotalMb * 1024 * 1024)
    }

    private fun constraints(item: DownloadRepository.CatalogItem): CompatibilityEngine.ModelConstraints {
        return CompatibilityEngine.ModelConstraints(
            item.minAndroidApi, item.abis, item.sizeBytes,
            item.contextLength, item.parameterCount)
    }

    private fun thermalLabel(status: Int): String {
        return when (status) {
            -1 -> "温控状态未知"
            0 -> "温控正常"
            1 -> "轻度升温"
            2 -> "中度升温"
            3 -> "严重升温"
            else -> "过热降频中"
        }
    }

    private fun buildEstRow(entity: ModelEntity, snapshot: CompatibilityEngine.DeviceSnapshot): View {
        val constraints = CompatibilityEngine.ModelConstraints(
            26, null, entity.sizeBytes, 2048L, maxOf(1L, entity.parameterCount))
        val r = CompatibilityEngine.evaluate(snapshot, constraints)

        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val pad = dp(10f)
        row.setPadding(0, pad / 2, 0, pad / 2)

        val name = TextView(requireContext())
        name.textSize = 13f
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        name.text = if (entity.displayName == null) entity.modelId else entity.displayName
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val meta = TextView(requireContext())
        meta.textSize = 11f
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
        meta.text = String.format(Locale.US, "估算峰值 %d MB",
            r.estimatedPeakBytes / (1024 * 1024))
        row.addView(meta, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val status = TextView(requireContext())
        status.textSize = 12f
        status.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        val textRes = if (r.isRecommended()) {
            R.color.status_success
        } else if (CompatibilityEngine.LEVEL_RUNNABLE == r.level) {
            R.color.status_info
        } else if (CompatibilityEngine.LEVEL_HIGH_LOAD == r.level) {
            R.color.status_warn
        } else {
            R.color.status_danger
        }
        status.setTextColor(ContextCompat.getColor(requireContext(), textRes))
        status.text = levelLabel(r.level)
        val stp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        stp.leftMargin = dp(10f)
        row.addView(status, stp)
        return row
    }

    private fun levelLabel(level: String): String {
        return when (level) {
            CompatibilityEngine.LEVEL_RUNNABLE -> "可运行"
            CompatibilityEngine.LEVEL_HIGH_LOAD -> "高负载"
            CompatibilityEngine.LEVEL_UNSUPPORTED -> "不支持"
            else -> "推荐"
        }
    }

    private fun legend(label: String, count: Int): String = label + " " + count

    private fun setLegend(root: View, id: Int, text: String, bgRes: Int, textRes: Int) {
        val tv = root.findViewById<TextView>(id)
        tv.text = text
        tv.setBackgroundResource(bgRes)
        tv.setTextColor(ContextCompat.getColor(requireContext(), textRes))
    }

    private fun buildReasonRow(text: String): View {
        val tv = TextView(requireContext())
        tv.textSize = 12f
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        tv.setLineSpacing(dp(2f).toFloat(), 1f)
        tv.text = text
        val pad = dp(5f)
        tv.setPadding(pad, pad, pad, pad)
        return tv
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
