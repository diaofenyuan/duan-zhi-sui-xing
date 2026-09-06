package com.example.localai.feature.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.MainActivity
import com.example.localai.R
import org.json.JSONArray

/** 只读取随包声明，不打开远端网页；条目参数和滚动位置随返回栈恢复。 */
class LicensesFragment : Fragment(R.layout.fragment_licenses) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        render(view)
    }

    private fun readAsset(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9_.-]+")))
        return requireContext().assets.open("licenses/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun render(root: View) {
        val content = root.findViewById<LinearLayout>(R.id.license_content)
        val title = root.findViewById<TextView>(R.id.license_title)
        content.removeAllViews()
        try {
            val entries = JSONArray(readAsset("index.json"))
            val index = arguments?.getInt("entry", -1) ?: -1
            if (index == -1) {
                title.setText(R.string.row_license)
                content.addView(body(getString(R.string.licenses_intro)))
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val row = layoutInflater.inflate(R.layout.item_license, content, false)
                    row.findViewById<TextView>(R.id.license_name).text = entry.getString("title")
                    row.findViewById<TextView>(R.id.license_summary).text = entry.getString("summary")
                    row.setOnClickListener {
                        (activity as? MainActivity)?.push(LicensesFragment().apply {
                            arguments = Bundle().apply { putInt("entry", i) }
                        })
                    }
                    content.addView(row)
                }
            } else {
                val entry = entries.getJSONObject(index)
                title.text = entry.getString("title")
                val text = buildString {
                    append(entry.getString("summary")).append("\n\n")
                    append(entry.getString("notice")).append("\n\n")
                    val components = entry.getJSONArray("components")
                    for (i in 0 until components.length()) append(components.getString(i)).append('\n')
                    val files = entry.getJSONArray("texts")
                    for (i in 0 until files.length()) {
                        val name = files.getString(i)
                        append("\n\n").append(name).append("\n\n").append(readAsset(name))
                    }
                }
                content.addView(body(text).apply { id = R.id.license_text; setTextIsSelectable(true) })
            }
        } catch (_: Exception) {
            // 安装包损坏或条目缺失时给出可恢复提示，避免点击关于页直接崩溃。
            content.removeAllViews()
            content.addView(body(getString(R.string.licenses_load_failed)))
            content.addView(Button(requireContext()).apply {
                setText(R.string.action_retry)
                setOnClickListener { render(root) }
            })
        }
    }

    private fun body(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        val pad = resources.getDimensionPixelSize(R.dimen.pad_screen)
        setPadding(pad, pad, pad, pad)
        setLineSpacing(3 * resources.displayMetrics.density, 1f)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
}
