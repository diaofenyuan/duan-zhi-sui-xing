package com.example.localai.data.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** 本地数据库：下载任务、已安装模型、会话、消息。schema 导出至 app/schemas（迁移审计用）。 */
@Database(entities = {
        DownloadEntity.class,
        ModelEntity.class,
        ConversationEntity.class,
        MessageEntity.class,
        ChatSessionEntity.class
}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DownloadDao downloadDao();

    public abstract ModelDao modelDao();

    public abstract ConversationDao conversationDao();

    public abstract MessageDao messageDao();

    public abstract ChatSessionDao chatSessionDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            // 仅增加当前编辑状态，升级时保留已有模型、任务和聊天历史。
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_session (slot INTEGER NOT NULL, token TEXT NOT NULL, conversationId INTEGER NOT NULL, modelId TEXT NOT NULL, draft TEXT NOT NULL, PRIMARY KEY(slot))");
        }
    };

    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "localai.db")
                .addMigrations(MIGRATION_1_2)
                .build();
    }
}
