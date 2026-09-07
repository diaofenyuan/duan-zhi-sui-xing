package com.example.localai.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "library_sources", foreignKeys = @ForeignKey(entity = WorkspaceEntity.class,
        parentColumns = "id", childColumns = "workspaceId", onDelete = ForeignKey.CASCADE),
        indices = @Index("workspaceId"))
public class SourceEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long workspaceId;
    @NonNull public String name = "";
    @NonNull public String type = "text";
    // 按页保留提取原文；引用始终从这里取值，不采用模型输出的所谓原文。
    @NonNull public String pagesJson = "[]";
    public int charCount;
    public long createdAt;
}
