package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert
    long insert(MessageEntity entity);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    List<MessageEntity> messagesFor(long conversationId);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void deleteForConversation(long conversationId);

    @Query("DELETE FROM messages")
    void clear();
}
