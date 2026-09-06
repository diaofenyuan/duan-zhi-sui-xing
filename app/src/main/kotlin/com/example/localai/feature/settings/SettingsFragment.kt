package com.example.localai.feature.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.BuildConfig
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.common.Fmt
import com.example.localai.data.ServiceLocator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/** 设置页：运行模式、推理行为、存储与数据、隐私、关于；配置持久化到 SharedPreferences。 */
class SettingsFragment : Fragment() {
    private val storageWorker = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "localai-cache").apply { isDaemon = true }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var clearingCache = false

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
        radioAuto.setOnClickListener(selectAuto)
        radioBalanced.setOnClickListener(selectBalanced)
        radioSaver.setOnClickListener(selectSaver)

        when (prefs().getString(KEY_MODE, "auto")) {
            "balanced" -> setMode("balanced", radioAuto, radioBalanced, radioSaver)
            "saver" -> setMode("saver", radioAuto, radioBalanced, radioSaver)
            else -> setMode("auto", radioAuto, radioBalanced, radioSaver)
        }

        val swKeepScreen = view.findViewById<MaterialSwitch>(R.id.sw_keep_screen)

        swKeepScreen.isChecked = prefs().getBoolean(KEY_KEEP_SCREEN, false)
        prefs().edit().remove("bg_gen").remove("metrics").apply()

        swKeepScreen.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(KEY_KEEP_SCREEN, checked).apply()
        }
        refreshInferenceSettings()
        view.findViewById<View>(R.id.row_context_length).setOnClickListener { editInferenceSetting(false) }
        view.findViewById<View>(R.id.row_gpu_layers).setOnClickListener { editInferenceSetting(true) }

        refreshCacheSize()
        view.findViewById<View>(R.id.row_clear_cache).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.row_clear_cache)
                .setMessage(R.string.clear_cache_explanation)
                .setPositiveButton(R.string.action_ok) { _, _ -> clearTemporaryCache() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        view.findViewById<View>(R.id.row_clear_sessions).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_sessions_title)
                .setMessage(R.string.dialog_clear_sessions_msg)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    val app = requireContext().applicationContext
                    ServiceLocator.chat()?.clearAll { error ->
                        android.widget.Toast.makeText(app,
                            error?.let { "清空失败：$it" } ?: app.getString(R.string.toast_sessions_cleared),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        view.findViewById<View>(R.id.row_privacy).setOnClickListener {
            (activity as? MainActivity)?.push(PrivacyFragment())
        }

        // 关于：开源许可证
        view.findViewById<View>(R.id.row_license).setOnClickListener {
            (activity as? MainActivity)?.push(LicensesFragment())
        }
    }

    private fun refreshInferenceSettings() {
        val target = view ?: return
        val context = prefs().getInt(InferencePolicy.KEY_CONTEXT, 0)
        target.findViewById<TextView>(R.id.text_context_length).text = if (context == 0)
            getString(R.string.context_auto) else getString(R.string.context_value, context)
        val layers = prefs().getInt(InferencePolicy.KEY_GPU_LAYERS, 0)
        target.findViewById<TextView>(R.id.text_gpu_layers).text = when (layers) {
            -1 -> getString(R.string.gpu_all)
            0 -> getString(R.string.gpu_cpu)
            else -> getString(R.string.gpu_value, layers)
        }
    }

    private fun editInferenceSetting(gpu: Boolean) {
        val key = if (gpu) InferencePolicy.KEY_GPU_LAYERS else InferencePolicy.KEY_CONTEXT
        val padding = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or if (gpu) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            setSingleLine(true)
            setText(prefs().getInt(key, 0).toString())
            selectAll()
            hint = getString(if (gpu) R.string.gpu_input_hint else R.string.context_input_hint)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(TextView(requireContext()).apply {
                setText(if (gpu) R.string.gpu_explanation else R.string.context_explanation)
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (gpu) R.string.row_gpu_layers else R.string.row_context_length)
            .setView(content)
            .setPositiveButton(R.string.action_ok, null)
            .setNeutralButton(R.string.inference_reset) { _, _ ->
                prefs().edit().remove(key).apply()
                refreshInferenceSettings()
            }
            .setNegativeButton(R.string.action_cancel, null).create()
        dialog.setOnShowListener {
            // 非法输入保留在对话框内，避免用户以为已经保存。
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = input.text.toString().trim().toIntOrNull()
                val valid = number != null && if (gpu) number in -1..256
                    else number == 0 || number in InferencePolicy.MIN_CONTEXT..InferencePolicy.MAX_CONTEXT
                if (!valid) {
                    input.error = getString(if (gpu) R.string.gpu_input_hint else R.string.context_input_hint)
                } else {
                    prefs().edit().putInt(key, number!!).apply()
                    refreshInferenceSettings()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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

    override fun onResume() {
        super.onResume()
        refreshCacheSize()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshCacheSize()
    }

    override fun onDestroy() {
        storageWorker.shutdown()
        super.onDestroy()
    }

    private fun refreshCacheSize() {
        val target = view ?: return
        if (clearingCache) return
        val cache = TemporaryCache(requireContext().cacheDir)
        target.findViewById<TextView>(R.id.text_cache_size).setText(R.string.cache_measuring)
        storageWorker.execute {
            val result = cache.measure()
            mainHandler.post {
                if (view !== target || clearingCache) return@post
                target.findViewById<TextView>(R.id.text_cache_size).text =
                    if (result.failures == 0) getString(R.string.row_clear_cache_val_fmt, Fmt.humanBytes(result.bytes))
                    else getString(R.string.cache_measure_failed)
            }
        }
    }

    private fun clearTemporaryCache() {
        if (clearingCache) return
        val app = requireContext().applicationContext
        val target = view ?: return
        val row = target.findViewById<View>(R.id.row_clear_cache)
        clearingCache = true
        row.isEnabled = false
        storageWorker.execute {
            val result = TemporaryCache(app.cacheDir).clear()
            mainHandler.post {
                clearingCache = false
                val message = app.getString(R.string.cache_cleaned_fmt, Fmt.humanBytes(result.bytes)) +
                    if (result.failures == 0) "" else app.getString(R.string.cache_cleanup_partial)
                android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
                if (view === target) {
                    row.isEnabled = true
                    refreshCacheSize()
                }
            }
        }
    }

    private fun prefs(): SharedPreferences =
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = InferencePolicy.PREFS
        private const val KEY_MODE = InferencePolicy.KEY_MODE
        private const val KEY_KEEP_SCREEN = InferencePolicy.KEY_KEEP_SCREEN
    }
}
