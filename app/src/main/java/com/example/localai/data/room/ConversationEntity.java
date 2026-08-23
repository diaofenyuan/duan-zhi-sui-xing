package com.example.localai.data.room;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 会话（P2 只建数据层，UI 接驳在 P4）。 */
@Entity(tableName = "conversations")
public class ConversationEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String modelId;
    public long createdAt;
    public long updatedAt;

    public ConversationEntity() {
    }

    @Ignore
    public ConversationEntity(String title, String modelId) {
        this.title = title;
        this.modelId = modelId;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
}
