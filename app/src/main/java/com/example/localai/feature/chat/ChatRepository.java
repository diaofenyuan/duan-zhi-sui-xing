package com.example.localai.feature.chat;

import android.os.Handler;
import android.os.Looper;

import com.example.localai.data.room.ConversationDao;
import com.example.localai.data.room.ConversationEntity;
import com.example.localai.data.room.MessageDao;
import com.example.localai.data.room.MessageEntity;
import com.example.localai.model.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话/消息持久化门面（P4）：UI 永远只在主线程调用快照方法；
 * DAO 访问全部在单线程 executor 上执行，完成后主线程回调。
 * 保存语义为「全量覆盖当前会话」（标题 + 消息列表），空白会话不落库。
 */
public final class ChatRepository {

    public interface Listener {
        void onConversationsChanged();
    }

    private final ConversationDao conversationDao;
    private final MessageDao messageDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "localai-chat");
        t.setDaemon(true);
        return t;
    });
    private final List<Listener> listeners = new ArrayList<>();
    private volatile List<ConversationEntity> conversationsCache = Collections.emptyList();

    public ChatRepository(ConversationDao conversationDao, MessageDao messageDao) {
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
    }

    public void register(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
        runOnMain(listener::onConversationsChanged);
    }

    public void unregister(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void refresh() {
        executor.execute(() -> {
            conversationsCache = conversationDao.all();
            runOnMain(() -> {
                for (Listener l : snapshotListeners()) {
                    l.onConversationsChanged();
                }
            });
        });
    }

    public List<ConversationEntity> conversations() {
        return conversationsCache;
    }

    /**
     * 保存会话：conversationId &lt;= 0 时自动创建新会话并回调新 id；
     * 标题仅在非空时更新；消息全量替换（先删后插，无残留）。
     */
    public void saveConversation(final long conversationId, final String modelId,
                                 final String title, final List<ChatMessage> messages,
                                 final ConversationSavedCallback callback) {
        if (messages == null || messages.isEmpty()) {
            if (callback != null) {
                callback.onSaved(conversationId);
            }
            return;
        }
        executor.execute(() -> {
            long cid = conversationId;
            try {
                ConversationEntity conversation;
                if (cid > 0) {
                    conversation = conversationDao.getById(cid);
                    if (conversation == null) {
                        cid = -1;
                    }
                } else {
                    conversation = null;
                }
                if (conversation == null) {
                    conversation = new ConversationEntity("", modelId);
                    cid = conversationDao.insert(conversation);
                }
                if (title != null && !title.trim().isEmpty()) {
                    conversation.title = title.trim();
                }
                conversation.updatedAt = System.currentTimeMillis();
                conversationDao.update(conversation);
                messageDao.deleteForConversation(cid);
                for (ChatMessage m : messages) {
                    messageDao.insert(new MessageEntity(cid,
                            m.role == ChatMessage.ROLE_USER
                                    ? MessageEntity.ROLE_USER : MessageEntity.ROLE_BOT,
                            m.text));
                }
            } catch (RuntimeException e) {
                conversationsCache = conversationDao.all();
                if (callback != null) {
                    runOnMain(() -> callback.onError(e.getMessage()));
                }
                return;
            }
            final long savedCid = cid;
            conversationsCache = conversationDao.all();
            runOnMain(() -> {
                for (Listener l : snapshotListeners()) {
                    l.onConversationsChanged();
                }
                if (callback != null) {
                    callback.onSaved(savedCid);
                }
            });
        });
    }

    public void loadMessages(final long conversationId, final MessagesCallback callback) {
        executor.execute(() -> {
            try {
                List<ChatMessage> result = new ArrayList<>();
                for (MessageEntity m : messageDao.messagesFor(conversationId)) {
                    result.add(new ChatMessage(
                            MessageEntity.ROLE_USER.equals(m.role)
                                    ? ChatMessage.ROLE_USER : ChatMessage.ROLE_BOT,
                            m.content));
                }
                runOnMain(() -> callback.onResult(result, null));
            } catch (RuntimeException e) {
                runOnMain(() -> callback.onResult(null, e.getMessage()));
            }
        });
    }

    public void deleteConversation(final long conversationId) {
        executor.execute(() -> {
            conversationDao.deleteById(conversationId);
            conversationsCache = conversationDao.all();
            runOnMain(() -> {
                for (Listener l : snapshotListeners()) {
                    l.onConversationsChanged();
                }
            });
        });
    }

    public void clearAll() {
        executor.execute(() -> {
            messageDao.clear();
            conversationDao.clear();
            conversationsCache = conversationDao.all();
            runOnMain(() -> {
                for (Listener l : snapshotListeners()) {
                    l.onConversationsChanged();
                }
            });
        });
    }

    public interface ConversationSavedCallback {
        void onSaved(long conversationId);

        void onError(String message);
    }

    public interface MessagesCallback {
        void onResult(List<ChatMessage> messages, String error);
    }

    private List<Listener> snapshotListeners() {
        synchronized (listeners) {
            return new ArrayList<>(listeners);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
