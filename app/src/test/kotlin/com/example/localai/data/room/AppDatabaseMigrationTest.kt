package com.example.localai.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    @Test fun upgradeFromPublishedSchemaKeepsHistoryModelsAndTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JsonParser.parseString(File("schemas/com.example.localai.data.room.AppDatabase/1.json").readText())
            .asJsonObject.getAsJsonObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            for (element in schema.getAsJsonArray("entities")) {
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                old.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                for (index in entity.getAsJsonArray("indices") ?: com.google.gson.JsonArray()) {
                    old.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
            }
            old.execSQL("INSERT INTO conversations(id,title,modelId,createdAt,updatedAt) VALUES(42,'历史标题','old-model',1,1)")
            old.execSQL("INSERT INTO messages(id,conversationId,role,content,createdAt) VALUES(7,42,'user','历史正文',1)")
            old.execSQL("INSERT INTO installed_models(modelId,version,sizeBytes,parameterCount,installedAt) VALUES('old-model','1',123,10,1)")
            old.execSQL("INSERT INTO download_tasks(taskId,parameterCount,bytesDownloaded,totalBytes,retryCount,createdAt,updatedAt) VALUES('old-task',10,5,20,0,1,1)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            assertEquals("历史标题", db.conversationDao().getById(42).title)
            assertEquals("历史正文", db.messageDao().messagesFor(42).single().content)
            assertEquals(123, db.modelDao().getByModelId("old-model").sizeBytes)
            assertEquals(5, db.downloadDao().getById("old-task").bytesDownloaded)
            assertNull(db.chatSessionDao().current())
            db.chatSessionDao().save(ChatSessionEntity().apply { draft = "升级后的草稿" })
            assertEquals("升级后的草稿", db.chatSessionDao().current().draft)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
