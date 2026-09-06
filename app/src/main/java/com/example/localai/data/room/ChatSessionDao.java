package com.example.localai.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ChatSessionDao {
    @Query("SELECT * FROM chat_session WHERE slot = 1")
    ChatSessionEntity current();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(ChatSessionEntity session);

    @Query("DELETE FROM chat_session WHERE conversationId = :id")
    void clearForConversation(long id);

    @Query("DELETE FROM chat_session")
    void clear();
}
