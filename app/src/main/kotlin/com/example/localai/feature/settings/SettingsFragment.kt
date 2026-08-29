package com.example.localai.feature.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.BuildConfig
import com.example.localai.R
import com.example.localai.common.Fmt
import com.example.localai.data.ServiceLocator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/** 设置页：运行模式、推理行为、存储与数据、隐私、关于；配置持久化到 SharedPreferences。 */
class SettingsFragment : Fragment() {

    private lateinit var cardAuto: MaterialCardView
    private lateinit var cardBalanced: MaterialCardView
    private lateinit var cardSaver: MaterialCardView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.text_version).text =
            getString(R.string.settings_ver_fmt, BuildConfig.VERSION_NAME)

        cardAuto = view.findViewById(R.id.mode_auto)
        cardBalanced = view.findViewById(R.id.mode_balanced)
        cardSaver = view.findViewById(R.id.mode_saver)

        val radioAuto = view.findViewById<RadioButton>(R.id.radio_auto)
        val radioBalanced = view.findViewById<RadioButton>(R.id.radio_balanced)
        val radioSaver = view.findViewById<RadioButton>(R.id.radio_saver)

        val selectAuto = View.OnClickListener { setMode("auto", radioAuto, radioBalanced, radioSaver) }
        val selectBalanced = View.OnClickListener { setMode("balanced", radioAuto, radioBalanced, radioSaver) }
        val selectSaver = View.OnClickListener { setMode("saver", radioAuto, radioBalanced, radioSaver) }
        cardAuto.setOnClickListener(selectAuto)
        cardBalanced.setOnClickListener(selectBalanced)
        cardSaver.setOnClickListener(selectSaver)

        when (prefs().getString(KEY_MODE, "auto")) {
            "balanced" -> setMode("balanced", radioAuto, radioBalanced, radioSaver)
            "saver" -> setMode("saver", radioAuto, radioBalanced, radioSaver)
            else -> setMode("auto", radioAuto, radioBalanced, radioSaver)
        }

        val swKeepScreen = view.findViewById<MaterialSwitch>(R.id.sw_keep_screen)
        val swBgGen = view.findViewById<MaterialSwitch>(R.id.sw_bg_gen)
        val swMetrics = view.findViewById<MaterialSwitch>(R.id.sw_metrics)

        swKeepScreen.isChecked = prefs().getBoolean(KEY_KEEP_SCREEN, false)
        swBgGen.isChecked = prefs().getBoolean(KEY_BG_GEN, false)
        swMetrics.isChecked = prefs().getBoolean(KEY_METRICS, false)

        swKeepScreen.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(KEY_KEEP_SCREEN, checked).apply()
        }
        swBgGen.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(KEY_BG_GEN, checked).apply()
        }
        swMetrics.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(KEY_METRICS, checked).apply()
        }

        // 存储与数据：真实已安装模型体积
        val cacheSize = view.findViewById<TextView>(R.id.text_cache_size)
        cacheSize.text = getString(R.string.row_clear_cache_val_fmt, cacheLabel())
        view.findViewById<View>(R.id.row_clear_cache).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.row_clear_cache)
                .setMessage(getString(R.string.row_clear_cache_val_fmt, cacheLabel()))
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.cache_cleaned_fmt, cacheLabel()),
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        view.findViewById<View>(R.id.row_clear_sessions).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_sessions_title)
                .setMessage(R.string.dialog_clear_sessions_msg)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    ServiceLocator.chat()?.clearAll()
                    android.widget.Toast.makeText(requireContext(),
                        R.string.toast_sessions_cleared,
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        // 关于：开源许可证
        view.findViewById<View>(R.id.row_license).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.license_dialog_title)
                .setMessage(licenseSummary())
                .setPositiveButton(R.string.action_close, null)
                .show()
        }
    }

    private fun setMode(mode: String, auto: RadioButton, balanced: RadioButton, saver: RadioButton) {
        prefs().edit().putString(KEY_MODE, mode).apply()
        val isAuto = "auto" == mode
        val isBalanced = "balanced" == mode
        auto.isChecked = isAuto
        balanced.isChecked = isBalanced
        saver.isChecked = !isAuto && !isBalanced
        highlight(cardAuto, isAuto)
        highlight(cardBalanced, isBalanced)
        highlight(cardSaver, !isAuto && !isBalanced)
    }

    private fun highlight(card: MaterialCardView, selected: Boolean) {
        val density = resources.displayMetrics.density
        card.strokeWidth = if (selected) (2 * density).toInt() else (1 * density).toInt()
        card.strokeColor = ContextCompat.getColor(requireContext(),
            if (selected) R.color.md_primary else R.color.outline)
    }

    /** 已安装模型真实体积（Room installed_models 汇总）。 */
    private fun cacheLabel(): String {
        val downloads = ServiceLocator.downloads() ?: return "0 MB"
        return Fmt.humanBytes(downloads.installedBytes())
    }

    private fun licenseSummary(): String {
        return "端智随行 P4 版\n\n" +
                "· Material Components — Apache-2.0\n" +
                "· AndroidX（AppCompat / RecyclerView / Core / Room / Work）— Apache-2.0\n" +
                "· llama.cpp / ggml — MIT\n" +
                "· OkHttp — Apache-2.0\n" +
                "· Gson — Apache-2.0\n" +
                "· JUnit 4 — EPL-1.0（仅测试）\n\n" +
                "完整清单见 docs/license-policy.md 与 NOTICE。"
    }

    private fun prefs(): SharedPreferences =
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "localai_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_KEEP_SCREEN = "keep_screen"
        private const val KEY_BG_GEN = "bg_gen"
        private const val KEY_METRICS = "metrics"
    }
}
