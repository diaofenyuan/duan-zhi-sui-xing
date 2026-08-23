package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ModelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ModelEntity entity);

    @Update
    void update(ModelEntity entity);

    @Delete
    void delete(ModelEntity entity);

    @Query("SELECT * FROM installed_models WHERE modelId = :modelId AND version = :version")
    ModelEntity get(String modelId, String version);

    @Query("SELECT * FROM installed_models WHERE modelId = :modelId ORDER BY installedAt DESC LIMIT 1")
    ModelEntity getByModelId(String modelId);

    @Query("SELECT * FROM installed_models ORDER BY installedAt DESC")
    List<ModelEntity> all();

    @Query("DELETE FROM installed_models WHERE modelId = :modelId AND version = :version")
    void deleteById(String modelId, String version);

    @Query("SELECT COUNT(*) FROM installed_models")
    int count();

    @Query("DELETE FROM installed_models")
    void clear();
}
