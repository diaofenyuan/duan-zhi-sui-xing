package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert
    long insert(MessageEntity entity);

    @Update
    void update(MessageEntity entity);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :lastKeptId")
    void deleteAfter(long conversationId, long lastKeptId);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    List<MessageEntity> messagesFor(long conversationId);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void deleteForConversation(long conversationId);

    @Query("DELETE FROM messages")
    void clear();
}
