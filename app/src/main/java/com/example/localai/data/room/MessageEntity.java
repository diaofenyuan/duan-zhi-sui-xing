package com.example.localai.data.room;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 会话消息；随会话删除级联清理（不残留消息）。 */
@Entity(tableName = "messages",
        foreignKeys = @ForeignKey(entity = ConversationEntity.class,
                parentColumns = "id",
                childColumns = "conversationId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("conversationId")})
public class MessageEntity {

    public static final String ROLE_USER = "user";
    public static final String ROLE_BOT = "bot";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long conversationId;
    public String role;
    public String content;
    public long createdAt;

    public MessageEntity() {
    }

    @Ignore
    public MessageEntity(long conversationId, String role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }
}
