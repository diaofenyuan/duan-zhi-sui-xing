package com.example.localai.feature.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.localai.model.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 上下文裁剪测试（P4）：预算内完整、超预算成对保留、最新消息兜底。 */
public class ChatHistoryTrimmerTest {

    private static ChatMessage user(String text) {
        return new ChatMessage(ChatMessage.ROLE_USER, text);
    }

    private static ChatMessage bot(String text) {
        return new ChatMessage(ChatMessage.ROLE_BOT, text);
    }

    @Test
    public void shortHistoryKeptIntact() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(user("你好"));
        history.add(bot("你好，有什么可以帮你？"));
        ChatHistoryTrimmer.Trimmed t = ChatHistoryTrimmer.truncate(history, 2048);
        assertEquals(2, t.kept.size());
        assertEquals(0, t.droppedCount);
    }

    @Test
    public void longHistoryTrimmedFromTheFront() {
        List<ChatMessage> history = new ArrayList<>();
        // 30 轮，每轮约 100 字符 -> 远超 512 token 预算
        for (int i = 0; i < 30; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 20; j++) {
                sb.append("字");
            }
            String text = "第" + i + "轮：" + sb.toString();
            history.add(user(text));
            history.add(bot(text + "的回复内容"));
        }
        ChatHistoryTrimmer.Trimmed t = ChatHistoryTrimmer.truncate(history, 512);
        assertTrue(t.droppedCount > 0);
        assertTrue(t.kept.size() < history.size());
        // 最后一轮必须保留
        assertTrue(t.kept.get(t.kept.size() - 1) == history.get(history.size() - 1));
    }

    @Test
    public void latestUserMessageAlwaysKept() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(user("这是一条很长的消息" + i));
        }
        ChatHistoryTrimmer.Trimmed t = ChatHistoryTrimmer.truncate(history, 64);
        assertTrue(t.kept.size() >= 1);
        assertTrue(t.kept.get(t.kept.size() - 1).role == ChatMessage.ROLE_USER);
    }

    @Test
    public void emptyHistoryReturnsEmpty() {
        ChatHistoryTrimmer.Trimmed t = ChatHistoryTrimmer.truncate(new ArrayList<>(), 1024);
        assertTrue(t.kept.isEmpty());
    }

    @Test
    public void approxTokensPositive() {
        assertTrue(ChatHistoryTrimmer.approxTokens("hello world") >= 1);
        assertTrue(ChatHistoryTrimmer.approxTokens("你好世界") >= 2);
    }
}
