package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DownloadEntity entity);

    @Update
    void update(DownloadEntity entity);

    @Delete
    void delete(DownloadEntity entity);

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    DownloadEntity getById(String taskId);

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    List<DownloadEntity> all();

    @Query("SELECT * FROM download_tasks WHERE state IN ('QUEUED','DOWNLOADING','PAUSED','VERIFYING','INSTALLING') ORDER BY createdAt ASC")
    List<DownloadEntity> recoverable();

    @Query("SELECT * FROM download_tasks WHERE state IN ('QUEUED','DOWNLOADING','VERIFYING','INSTALLING','PAUSED','FAILED') ORDER BY createdAt DESC")
    List<DownloadEntity> visible();

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    void deleteById(String taskId);

    @Query("SELECT * FROM download_tasks WHERE modelId = :modelId AND state NOT IN ('READY','CANCELED')")
    List<DownloadEntity> activeForModel(String modelId);

    @Query("DELETE FROM download_tasks")
    void clear();
}
