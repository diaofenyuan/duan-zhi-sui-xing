package com.example.localai.feature.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localai.data.room.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryMigrationTest {
    @Test fun upgradePreservesHistoryAndCascadesOnlyWithinWorkspace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "library-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val schema = JsonParser.parseString(File("schemas/com.example.localai.data.room.AppDatabase/2.json").readText()).asJsonObject["database"].asJsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            schema["entities"].asJsonArray.forEach { raw ->
                val entity = raw.asJsonObject
                val table = entity["tableName"].asString
                db.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                entity["indices"]?.asJsonArray?.forEach { index -> db.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"].asJsonArray.forEach { db.execSQL(it.asString) }
            db.execSQL("INSERT INTO conversations (id,title,modelId,createdAt,updatedAt) VALUES (8,'保留会话','model',1,2)")
            db.version = 2
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertEquals("保留会话", database.conversationDao().all().first().title)
            val dao = database.libraryDao()
            val workspace = dao.insertWorkspace(WorkspaceEntity().apply { title = "项目" })
            val other = dao.insertWorkspace(WorkspaceEntity().apply { title = "旅行" })
            val source = dao.insertSource(SourceEntity().apply { workspaceId = workspace; this.name = "计划" })
            val task = TaskResultEntity().apply { workspaceId = workspace; title = "摘要"; output = "编辑结果"; citationsJson = "[{\"excerpt\":\"原文\"}]" }
            task.id = dao.insertResult(task)
            dao.deleteSource(source)
            assertEquals(task.citationsJson, dao.result(task.id).citationsJson)
            task.output = "再次编辑"; dao.updateResult(task)
            assertEquals("再次编辑", dao.result(task.id).output)
            dao.insertSource(SourceEntity().apply { workspaceId = other; this.name = "行程" })
            dao.deleteWorkspace(workspace)
            assertTrue(dao.results(workspace).isEmpty()); assertEquals(1, dao.sources(other).size)
            assertEquals(1, database.conversationDao().all().size)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
