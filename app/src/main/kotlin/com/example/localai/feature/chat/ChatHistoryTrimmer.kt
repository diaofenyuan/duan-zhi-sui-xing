package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage

/** 按真实分词保留连续的最近完整轮次，预算包含模板并为回复预留空间。 */
object ChatHistoryTrimmer {
    // 限制单次 Binder 文本大小；过长问题明确拒绝，旧轮次则整轮排除。
    const val MAX_PROMPT_CHARS = 64 * 1024

    class Trimmed(
        val kept: List<ChatMessage>,
        val droppedCount: Int,
        val inputTooLong: Boolean = false
    )

    fun truncate(history: List<ChatMessage>, budgetTokens: Int, countTokens: (String) -> Int): Trimmed {
        val starts = history.indices.filter { history[it].role == ChatMessage.ROLE_USER }
        if (starts.isEmpty()) return Trimmed(emptyList(), history.size)
        var kept = emptyList<ChatMessage>()
        var keptStart = history.size
        for (start in starts.asReversed()) {
            val candidate = history.subList(start, history.size)
            val prompt = RealChatEngine.buildPrompt(candidate)
            val fits = prompt.length <= MAX_PROMPT_CHARS && countTokens(prompt) <= budgetTokens
            if (!fits) {
                if (kept.isEmpty()) return Trimmed(emptyList(), 0, inputTooLong = true)
                // 不越过放不下的一轮拼接更早的小片段，避免改变对话的前后关系。
                break
            }
            kept = candidate
            keptStart = start
        }
        return Trimmed(kept.toList(), keptStart)
    }
}
