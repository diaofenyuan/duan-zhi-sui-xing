package com.example.localai.feature.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.localai.data.room.AppDatabase;
import com.example.localai.model.ChatMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** ChatRepository 会话持久化测试（Room 内存库 + Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class ChatRepositoryTest {

    private AppDatabase database;
    private ChatRepository repository;

    /** 执行主线程队列中的 pending runnable，直到 latch 归零或超时。 */
    static void idleUntil(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
        assertTrue("timeout waiting for callback; count=" + latch.getCount(),
                latch.getCount() == 0);
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new ChatRepository(database.conversationDao(), database.messageDao());
    }

    @After
    public void tearDown() {
        database.close();
    }

    private List<ChatMessage> sampleMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, "什么是 KV Cache"));
        messages.add(new ChatMessage(ChatMessage.ROLE_BOT, "这是回复"));
        return messages;
    }

    @Test
    public void saveCreatesConversationAndMessages() throws Exception {
        final long[] savedId = {-1};
        CountDownLatch latch = new CountDownLatch(1);
        repository.saveConversation(0, "smollm-135m-instruct", "什么是 KV Cache",
                sampleMessages(), new ChatRepository.ConversationSavedCallback() {
                    @Override
                    public void onSaved(long conversationId) {
                        savedId[0] = conversationId;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        latch.countDown();
                    }
                });
        idleUntil(latch);
        assertTrue(savedId[0] > 0);

        MessagesCallbackHolder holder = new MessagesCallbackHolder();
        repository.loadMessages(savedId[0], holder.callback());
        idleUntil(holder.latch);
        assertNotNull(holder.messages);
        assertEquals(2, holder.messages.size());
        assertEquals(1, database.conversationDao().all().size());
    }

    @Test
    public void updateConversationOverwritesMessages() throws Exception {
        final long[] savedId = {-1};
        CountDownLatch latch = new CountDownLatch(1);
        repository.saveConversation(0, "smollm-135m-instruct", "标题一",
                sampleMessages(), new ChatRepository.ConversationSavedCallback() {
                    @Override
                    public void onSaved(long conversationId) {
                        savedId[0] = conversationId;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        latch.countDown();
                    }
                });
        idleUntil(latch);

        List<ChatMessage> updated = new ArrayList<>();
        updated.add(new ChatMessage(ChatMessage.ROLE_USER, "下一轮"));
        CountDownLatch second = new CountDownLatch(1);
        repository.saveConversation(savedId[0], "smollm-135m-instruct", "新的标题",
                updated, new ChatRepository.ConversationSavedCallback() {
                    @Override
                    public void onSaved(long conversationId) {
                        second.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        second.countDown();
                    }
                });
        idleUntil(second);

        MessagesCallbackHolder holder = new MessagesCallbackHolder();
        repository.loadMessages(savedId[0], holder.callback());
        idleUntil(holder.latch);
        assertEquals(1, holder.messages.size());
        assertEquals("新的标题", database.conversationDao().all().get(0).title);
    }

    @Test
    public void deleteConversationCascadesMessages() throws Exception {
        final long[] savedId = {-1};
        CountDownLatch latch = new CountDownLatch(1);
        repository.saveConversation(0, "m", "标题", sampleMessages(),
                new ChatRepository.ConversationSavedCallback() {
                    @Override
                    public void onSaved(long conversationId) {
                        savedId[0] = conversationId;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        latch.countDown();
                    }
                });
        idleUntil(latch);

        repository.deleteConversation(savedId[0]);
        Thread.sleep(500);
        assertEquals(0, database.conversationDao().all().size());
        assertEquals(0, database.messageDao().messagesFor(savedId[0]).size());
    }

    static class MessagesCallbackHolder implements ChatRepository.MessagesCallback {
        List<ChatMessage> messages;
        String error;
        CountDownLatch latch = new CountDownLatch(1);

        ChatRepository.MessagesCallback callback() {
            return this;
        }

        @Override
        public void onResult(List<ChatMessage> messages, String error) {
            this.messages = messages;
            this.error = error;
            latch.countDown();
        }
    }
}
