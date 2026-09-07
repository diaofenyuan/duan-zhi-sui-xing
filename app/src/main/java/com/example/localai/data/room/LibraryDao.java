package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LibraryDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt, id") List<WorkspaceEntity> workspaces();
    @Insert long insertWorkspace(WorkspaceEntity entity);
    @Update void updateWorkspace(WorkspaceEntity entity);
    @Query("SELECT * FROM library_sources WHERE workspaceId = :workspaceId ORDER BY createdAt DESC") List<SourceEntity> sources(long workspaceId);
    @Query("SELECT * FROM library_sources WHERE id = :id") SourceEntity source(long id);
    @Query("SELECT * FROM library_sources WHERE id IN (:ids)") List<SourceEntity> selectedSources(long[] ids);
    @Insert long insertSource(SourceEntity entity);
    @Query("DELETE FROM library_sources WHERE id = :id") void deleteSource(long id);
    @Query("SELECT * FROM task_results WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC") List<TaskResultEntity> results(long workspaceId);
    @Query("SELECT * FROM task_results WHERE id = :id") TaskResultEntity result(long id);
    @Insert long insertResult(TaskResultEntity entity);
    @Update void updateResult(TaskResultEntity entity);
    @Query("DELETE FROM task_results WHERE id = :id") void deleteResult(long id);
    @Query("DELETE FROM workspaces WHERE id = :id") void deleteWorkspace(long id);
}
