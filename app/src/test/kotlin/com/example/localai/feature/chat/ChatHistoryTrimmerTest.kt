package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

/** 使用可控分词器验证预算边界，真实词表另由当前模拟器覆盖。 */
class ChatHistoryTrimmerTest {
    private fun user(text: String) = ChatMessage(ChatMessage.ROLE_USER, text)
    private fun bot(text: String) = ChatMessage(ChatMessage.ROLE_BOT, text)
    private fun count(prompt: String) = prompt.length

    @Test fun shortHistoryKeptIntactIncludingTemplateBudget() {
        val history = listOf(user("你好"), bot("你好"), user("继续"))
        val exact = RealChatEngine.buildPrompt(history).length
        val result = ChatHistoryTrimmer.truncate(history, exact, ::count)
        assertEquals(history, result.kept)
        assertEquals(0, result.droppedCount)
        assertFalse(result.inputTooLong)
        assertTrue(ChatHistoryTrimmer.truncate(history, exact - 1, ::count).droppedCount > 0)
    }

    @Test fun longHistoryKeepsContiguousRecentTurns() {
        val history = listOf(user("旧问题"), bot("旧答复"), user("字".repeat(200)), bot("长答复"), user("最新问题"))
        val budget = RealChatEngine.buildPrompt(listOf(history.last())).length + 100
        val result = ChatHistoryTrimmer.truncate(history, budget, ::count)
        assertEquals(listOf(history.last()), result.kept)
        assertEquals(4, result.droppedCount)
        assertEquals(5, history.size)
    }

    @Test fun latestQuestionIsNeverSilentlyClipped() {
        val text = "原文😀".repeat(100)
        val result = ChatHistoryTrimmer.truncate(listOf(user(text)), 100, ::count)
        assertTrue(result.inputTooLong)
        assertTrue(result.kept.isEmpty())
        assertEquals(0, result.droppedCount)
    }

    @Test fun oversizedTransportInputDoesNotReachBinder() {
        var called = false
        val result = ChatHistoryTrimmer.truncate(listOf(user("字".repeat(70_000))), 2048) {
            called = true
            1
        }
        assertTrue(result.inputTooLong)
        assertFalse(called)
    }

    @Test fun orphanAssistantIsNotUsedWithoutItsQuestion() {
        val history = listOf(bot("已删除问题的答复"), user("新的问题"))
        val result = ChatHistoryTrimmer.truncate(history, 1024, ::count)
        assertEquals(listOf(history.last()), result.kept)
        assertEquals(1, result.droppedCount)
    }

    @Test fun emptyHistoryReturnsEmpty() {
        val result = ChatHistoryTrimmer.truncate(emptyList(), 1024, ::count)
        assertTrue(result.kept.isEmpty())
        assertEquals(0, result.droppedCount)
    }
}
