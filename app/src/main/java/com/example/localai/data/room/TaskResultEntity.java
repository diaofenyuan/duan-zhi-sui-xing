package com.example.localai.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "task_results", foreignKeys = @ForeignKey(entity = WorkspaceEntity.class,
        parentColumns = "id", childColumns = "workspaceId", onDelete = ForeignKey.CASCADE),
        indices = @Index("workspaceId"))
public class TaskResultEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long workspaceId;
    @NonNull public String kind = "summary";
    @NonNull public String title = "";
    @NonNull public String input = "";
    @NonNull public String output = "";
    @NonNull public String originalOutput = "";
    @NonNull public String checklistJson = "[]";
    @NonNull public String citationsJson = "[]";
    @NonNull public String sourceIdsJson = "[]";
    @NonNull public String modelId = "";
    @NonNull public String status = "draft";
    public long createdAt;
    public long updatedAt;
}
