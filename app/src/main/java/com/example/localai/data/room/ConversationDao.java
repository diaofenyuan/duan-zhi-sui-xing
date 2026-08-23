package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ConversationDao {

    @Insert
    long insert(ConversationEntity entity);

    @Update
    void update(ConversationEntity entity);

    @Delete
    void delete(ConversationEntity entity);

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    List<ConversationEntity> all();

    @Query("SELECT * FROM conversations WHERE id = :id")
    ConversationEntity getById(long id);

    @Query("DELETE FROM conversations WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM conversations")
    void clear();
}
