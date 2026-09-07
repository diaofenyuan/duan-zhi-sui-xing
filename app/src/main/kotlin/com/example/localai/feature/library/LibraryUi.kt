package com.example.localai.feature.library

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.R
import com.google.android.material.button.MaterialButton

/** 原生资料页共用的排版，遵循现有配色和字号缩放。 */
abstract class LibraryUi : Fragment() {
    protected lateinit var body: LinearLayout
    protected lateinit var pageTitle: TextView
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun page(title: String): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        }
        val header = LinearLayout(requireContext()).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(24), dp(4))
        }
        header.addView(button("返回") { parentFragmentManager.popBackStack() }, LinearLayout.LayoutParams(dp(72), dp(48)))
        pageTitle = label(title, 24f)
        header.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        root.addView(ScrollView(requireContext()).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
    protected fun label(text: String, size: Float = 15f, secondary: Boolean = false) = TextView(requireContext()).apply {
        this.text = text; textSize = size
        setTextColor(ContextCompat.getColor(context, if (secondary) R.color.text_secondary else R.color.text_primary))
        setPadding(0, dp(8), 0, dp(8))
    }
    protected fun button(text: String, action: () -> Unit) = MaterialButton(requireContext(), null,
        com.google.android.material.R.attr.borderlessButtonStyle).apply {
        this.text = text; isAllCaps = false; minHeight = dp(48)
        setOnClickListener { action() }
    }
    protected fun field(hint: String, minLines: Int = 1) = EditText(requireContext()).apply {
        this.hint = hint; textSize = 15f; this.minLines = minLines
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        isSaveEnabled = false
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.surface_variant))
            cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) }
    }
    protected fun divider() = View(requireContext()).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.outline))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) }
    }
    protected fun entry(title: String, subtitle: String, action: () -> Unit) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = dp(68); setPadding(dp(8), dp(6), dp(8), dp(6))
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        setBackgroundResource(value.resourceId)
        isClickable = true; isFocusable = true; contentDescription = "$title，$subtitle"
        addView(label(title, 16f).apply { setPadding(0, dp(4), 0, dp(2)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        addView(label(subtitle, 12f, true).apply { setPadding(0, 0, 0, dp(4)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        setOnClickListener { action() }
    }
    protected fun message(text: String) { context?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() } }
    protected fun failure(error: Throwable) = message(error.message?.take(180) ?: "操作失败，请重试")
}
