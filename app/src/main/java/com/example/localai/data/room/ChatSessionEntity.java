package com.example.localai.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 当前编辑中的会话；正文沿用 messages 表，避免在系统状态 Bundle 中复制长对话。 */
@Entity(tableName = "chat_session")
public class ChatSessionEntity {
    @PrimaryKey public int slot = 1;
    @NonNull public String token = "";
    public long conversationId;
    @NonNull public String modelId = "";
    @NonNull public String draft = "";
}
