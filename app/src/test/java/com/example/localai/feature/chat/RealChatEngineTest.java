package com.example.localai.feature.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.localai.model.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 真实推理引擎的提示组织与模式标识（纯 JVM）。 */
public class RealChatEngineTest {

    @Test
    public void buildPrompt_formatsChatmlHistory() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(ChatMessage.ROLE_USER, "hello"));
        history.add(new ChatMessage(ChatMessage.ROLE_BOT, "hi there"));

        String prompt = RealChatEngine.buildPrompt(history);

        String expected = "<|im_start|>user\nhello<|im_end|>\n"
                + "<|im_start|>assistant\nhi there<|im_end|>\n"
                + "<|im_start|>assistant\n";
        assertEquals(expected, prompt);
    }

    @Test
    public void buildPrompt_endsWithAssistantMarker() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(ChatMessage.ROLE_USER, "q"));
        String prompt = RealChatEngine.buildPrompt(history);
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"));
        assertTrue(prompt.contains("q"));
    }

    @Test
    public void buildPrompt_skipsTypingSentinels() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(ChatMessage.ROLE_USER, "a"));
        String prompt = RealChatEngine.buildPrompt(history);
        assertFalse(prompt.contains("typing"));
        assertEquals("<|im_start|>user\na<|im_end|>\n<|im_start|>assistant\n", prompt);
    }
}
