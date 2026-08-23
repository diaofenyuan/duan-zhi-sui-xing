package com.example.localai.data.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** 本地数据库：下载任务、已安装模型、会话、消息。schema 导出至 app/schemas（迁移审计用）。 */
@Database(entities = {
        DownloadEntity.class,
        ModelEntity.class,
        ConversationEntity.class,
        MessageEntity.class
}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DownloadDao downloadDao();

    public abstract ModelDao modelDao();

    public abstract ConversationDao conversationDao();

    public abstract MessageDao messageDao();

    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "localai.db")
                .build();
    }
}
