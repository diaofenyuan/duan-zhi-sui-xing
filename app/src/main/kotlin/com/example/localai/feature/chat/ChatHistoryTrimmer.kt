package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage
import java.util.ArrayList

/**
 * 上下文裁剪（纯逻辑，可单测）：
 * 把完整对话历史压缩到 contextLength 预算内，规则可解释：
 *  1. 预算 = ctxLen * 0.55（其余留给回复本身）；
 *  2. 从最新一条开始保留，逐条累计占用（用户+助手成对计入），直到预算;
 *  3. 最新一条用户消息始终保留（超预算时对其文本截断）；
 *  4. 被丢弃的历史不会静默消失——返回 droppedCount，UI/引擎可记录。
 */
object ChatHistoryTrimmer {

    private const val HISTORY_BUDGET = 0.55

    class Trimmed(
        @JvmField val kept: List<ChatMessage>,
        @JvmField val droppedCount: Int
    )

    @JvmStatic
    fun truncate(history: List<ChatMessage>?, contextLength: Int): Trimmed {
        if (history == null || history.isEmpty()) {
            return Trimmed(ArrayList(), 0)
        }
        val budgetTokens = (maxOf(1, contextLength) * HISTORY_BUDGET).toInt()
        val kept = ArrayList<ChatMessage>()
        var used = 0
        var dropped = 0

        var i = history.size - 1
        while (i >= 0) {
            val m = history[i]
            if (m.role != ChatMessage.ROLE_USER && m.role != ChatMessage.ROLE_BOT) {
                dropped++
                i--
                continue
            }
            var pairStart = i
            if (m.role == ChatMessage.ROLE_BOT) {
                var j = i - 1
                while (j >= 0 && history[j].role == ChatMessage.ROLE_BOT) {
                    j--
                }
                if (j >= 0 && history[j].role == ChatMessage.ROLE_USER) {
                    pairStart = j
                }
            }
            val pair = history.subList(pairStart, i + 1)
            var tokens = 0
            for (pm in pair) {
                tokens += approxTokens(pm.text)
            }
            val remaining = budgetTokens - used
            if (tokens <= remaining) {
                for (k in 0 until pair.size) {
                    kept.add(k, pair[k])
                }
                used += tokens
                if (pairStart == 0) {
                    break
                }
                i = pairStart - 1
                continue
            }
            // 这一对放不下：
            // 1) 若它包含最新一条用户消息（i == size-1 或 pair 末项是 user），裁剪该消息并收尾；
            // 2) 否则整对丢弃，继续向更早的历史回退（预算不足时较早的更小，直接丢弃并继续）
            val lastPair = pair[pair.size - 1]
            if (lastPair.role == ChatMessage.ROLE_USER && lastPair === history[history.size - 1]) {
                val clipped = ChatMessage(ChatMessage.ROLE_USER,
                    truncateChars(lastPair.text, maxOf(8, remaining)))
                kept.add(0, clipped)
                dropped += history.size - 1 - kept.size
                break
            }
            dropped += pair.size
            i = pairStart - 1
        }
        if (kept.isEmpty()) {
            // 兜底：至少要能带上最新一条用户消息
            val last = history[history.size - 1]
            if (last.role == ChatMessage.ROLE_USER) {
                kept.add(ChatMessage(ChatMessage.ROLE_USER,
                    truncateChars(last.text, maxOf(8, budgetTokens))))
            } else {
                kept.add(history[history.size - 1])
            }
        }
        return Trimmed(kept, dropped)
    }

    @JvmStatic
    fun approxTokens(text: String?): Int {
        if (text == null || text.isEmpty()) {
            return 0
        }
        val chars = text.length
        // 中文/全角字符约 1.5 字符一个 token，其余约 4 字符一个 token；
        // 混合输入保守取 /2。
        return maxOf(1, (chars + 1) / 2)
    }

    private fun truncateChars(text: String?, budgetTokens: Int): String {
        if (text == null || text.isEmpty()) {
            return ""
        }
        val budgetChars = maxOf(0, budgetTokens * 2)
        if (text.length <= budgetChars) {
            return text
        }
        return text.substring(0, budgetChars) + "\n……（较早内容已按上下文上限裁剪）"
    }
}
