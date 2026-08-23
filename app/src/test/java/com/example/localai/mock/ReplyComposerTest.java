package com.example.localai.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 流式回复生成器测试。 */
public class ReplyComposerTest {

    @Test
    public void greetingReply() {
        String reply = ReplyComposer.build("你好，你是谁");
        assertTrue(reply.contains("本地"));
    }

    @Test
    public void cacheReplyExplains() {
        assertTrue(ReplyComposer.build("什么是 KV Cache？").contains("草稿"));
    }

    @Test
    public void poemReply() {
        assertTrue(ReplyComposer.build("写一首关于离线 AI 的短诗").contains("\n"));
    }

    @Test
    public void unknownPromptFallsBack() {
        String reply = ReplyComposer.build("随便说点什么");
        assertTrue(!reply.isEmpty());
        assertNotEquals(ReplyComposer.build("你好"), reply);
    }

    @Test
    public void chunksReconstructOriginal() {
        String reply = "一二三四五六七八九十ABC";
        StringBuilder rebuilt = new StringBuilder();
        for (String part : ReplyComposer.chunk(reply)) {
            rebuilt.append(part);
        }
        assertEquals(reply, rebuilt.toString());
    }
}
