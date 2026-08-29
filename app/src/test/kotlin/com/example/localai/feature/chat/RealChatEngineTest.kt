package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实推理引擎的提示组织与模式标识（纯 JVM）。 */
class RealChatEngineTest {

    @Test
    fun buildPrompt_formatsChatmlHistory() {
        val history = ArrayList<ChatMessage>()
        history.add(ChatMessage(ChatMessage.ROLE_USER, "hello"))
        history.add(ChatMessage(ChatMessage.ROLE_BOT, "hi there"))

        val prompt = RealChatEngine.buildPrompt(history)

        val expected = "<|im_start|>user\nhello<|im_end|>\n" +
                "<|im_start|>assistant\nhi there<|im_end|>\n" +
                "<|im_start|>assistant\n"
        assertEquals(expected, prompt)
    }

    @Test
    fun buildPrompt_endsWithAssistantMarker() {
        val history = ArrayList<ChatMessage>()
        history.add(ChatMessage(ChatMessage.ROLE_USER, "q"))
        val prompt = RealChatEngine.buildPrompt(history)
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
        assertTrue(prompt.contains("q"))
    }

    @Test
    fun buildPrompt_skipsTypingSentinels() {
        val history = ArrayList<ChatMessage>()
        history.add(ChatMessage(ChatMessage.ROLE_USER, "a"))
        val prompt = RealChatEngine.buildPrompt(history)
        assertFalse(prompt.contains("typing"))
        assertEquals("<|im_start|>user\na<|im_end|>\n<|im_start|>assistant\n", prompt)
    }
}
