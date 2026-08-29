package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 上下文裁剪测试（P4）：预算内完整、超预算成对保留、最新消息兜底。 */
class ChatHistoryTrimmerTest {

    private fun user(text: String) = ChatMessage(ChatMessage.ROLE_USER, text)

    private fun bot(text: String) = ChatMessage(ChatMessage.ROLE_BOT, text)

    @Test
    fun shortHistoryKeptIntact() {
        val history = ArrayList<ChatMessage>()
        history.add(user("你好"))
        history.add(bot("你好，有什么可以帮你？"))
        val t = ChatHistoryTrimmer.truncate(history, 2048)
        assertEquals(2, t.kept.size)
        assertEquals(0, t.droppedCount)
    }

    @Test
    fun longHistoryTrimmedFromTheFront() {
        val history = ArrayList<ChatMessage>()
        // 30 轮，每轮约 100 字符 -> 远超 512 token 预算
        for (i in 0 until 30) {
            val sb = StringBuilder()
            for (j in 0 until 20) {
                sb.append("字")
            }
            val text = "第" + i + "轮：" + sb.toString()
            history.add(user(text))
            history.add(bot(text + "的回复内容"))
        }
        val t = ChatHistoryTrimmer.truncate(history, 512)
        assertTrue(t.droppedCount > 0)
        assertTrue(t.kept.size < history.size)
        // 最后一轮必须保留
        assertTrue(t.kept[t.kept.size - 1] === history[history.size - 1])
    }

    @Test
    fun latestUserMessageAlwaysKept() {
        val history = ArrayList<ChatMessage>()
        for (i in 0 until 10) {
            history.add(user("这是一条很长的消息" + i))
        }
        val t = ChatHistoryTrimmer.truncate(history, 64)
        assertTrue(t.kept.size >= 1)
        assertTrue(t.kept[t.kept.size - 1].role == ChatMessage.ROLE_USER)
    }

    @Test
    fun emptyHistoryReturnsEmpty() {
        val t = ChatHistoryTrimmer.truncate(ArrayList(), 1024)
        assertTrue(t.kept.isEmpty())
    }

    @Test
    fun approxTokensPositive() {
        assertTrue(ChatHistoryTrimmer.approxTokens("hello world") >= 1)
        assertTrue(ChatHistoryTrimmer.approxTokens("你好世界") >= 2)
    }
}
