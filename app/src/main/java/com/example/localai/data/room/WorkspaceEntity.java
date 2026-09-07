package com.example.localai.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "workspaces")
public class WorkspaceEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String title = "";
    public long createdAt;
}
