package com.example.localai.feature.chat

import android.icu.text.BreakIterator
import java.util.Locale

/** 标题按可见字符截取，避免拆开生僻汉字、组合音标和表情序列；正文保持原样。 */
internal object ConversationText {
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun title(text: String): String = prefix(text.trim().replace(whitespace, " "), 24)

    fun initial(title: String): String = prefix(title, 1)

    private fun prefix(text: String, count: Int): String {
        val boundaries = BreakIterator.getCharacterInstance(Locale.CHINA)
        boundaries.setText(text)
        var end = boundaries.first()
        repeat(count) {
            val next = boundaries.next()
            if (next == BreakIterator.DONE) return text
            end = next
        }
        return text.substring(0, end)
    }
}
