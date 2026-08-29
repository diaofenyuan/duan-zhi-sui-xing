package com.example.localai.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 持久化测试（Robolectric）：下载任务/模型/会话/消息 CRUD、
 * 级联删除与"进程重启"（关闭数据库后重开仍可读）验证。
 */
@RunWith(RobolectricTestRunner::class)
class RoomPersistenceTest {

    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun downloadEntity_roundtrip() {
        val db = db!!
        val entity = DownloadEntity("t1", "m1", "1.0", "model.gguf",
            "Model One", "pub", "Q4_K_M", "Apache-2.0", 1000L,
            4096L, "abc123", "http://x/model.gguf")
        entity.state = DownloadState.DOWNLOADING
        entity.bytesDownloaded = 1024L
        entity.etag = "\"e1\""
        db.downloadDao().insert(entity)

        val loaded = db.downloadDao().getById("t1")
        assertNotNull(loaded)
        assertEquals("m1", loaded!!.modelId)
        assertEquals(1024L, loaded.bytesDownloaded)
        assertEquals("\"e1\"", loaded.etag)
        assertEquals(DownloadState.DOWNLOADING, loaded.state)
        assertEquals(25, loaded.percent())
    }

    @Test
    fun downloadState_transitions() {
        assertTrue(DownloadState.canTransition(DownloadState.QUEUED, DownloadState.DOWNLOADING))
        assertTrue(DownloadState.canTransition(DownloadState.DOWNLOADING, DownloadState.PAUSED))
        assertTrue(DownloadState.canTransition(DownloadState.PAUSED, DownloadState.DOWNLOADING))
        assertTrue(DownloadState.canTransition(DownloadState.DOWNLOADING, DownloadState.VERIFYING))
        assertTrue(DownloadState.canTransition(DownloadState.VERIFYING, DownloadState.INSTALLING))
        assertTrue(DownloadState.canTransition(DownloadState.INSTALLING, DownloadState.READY))
        assertTrue(DownloadState.canTransition(DownloadState.FAILED, DownloadState.DOWNLOADING))
        assertTrue(!DownloadState.canTransition(DownloadState.READY, DownloadState.DOWNLOADING))
        assertTrue(!DownloadState.canTransition(DownloadState.PAUSED, DownloadState.VERIFYING))
        assertTrue(!DownloadState.canTransition(DownloadState.FAILED, DownloadState.PAUSED))

        val entity = DownloadEntity("t2", "m1", "1.0", "f", "n", "p", "q", "l",
            1L, 10L, "s", "u")
        assertTrue(!entity.transition(DownloadState.PAUSED)) // QUEUED -> PAUSED 非法
        assertEquals(DownloadState.QUEUED, entity.state)
        assertTrue(entity.transition(DownloadState.DOWNLOADING))
        assertEquals(DownloadState.DOWNLOADING, entity.state)
    }

    @Test
    fun recoverableQuery_excludesTerminal() {
        val db = db!!
        val dao = db.downloadDao()
        dao.insert(task("t1", DownloadState.DOWNLOADING))
        dao.insert(task("t2", DownloadState.PAUSED))
        dao.insert(task("t3", DownloadState.FAILED))
        dao.insert(task("t4", DownloadState.READY))

        val recoverable = dao.recoverable()
        assertEquals(2, recoverable.size)

        val visible = dao.visible()
        assertEquals(3, visible.size) // FAILED 仍可见供重试，READY 不可见
    }

    private fun task(id: String, state: String): DownloadEntity {
        val entity = DownloadEntity(id, "m1", "1.0", "f", "n", "p", "q", "l",
            1L, 100L, "s", "u")
        entity.state = state
        return entity
    }

    @Test
    fun conversationAndMessages_crudAndCascade() {
        val db = db!!
        val convDao = db.conversationDao()
        val msgDao = db.messageDao()

        val convId = convDao.insert(ConversationEntity("标题", "m1"))
        msgDao.insert(MessageEntity(convId, MessageEntity.ROLE_USER, "问题"))
        msgDao.insert(MessageEntity(convId, MessageEntity.ROLE_BOT, "回答"))

        assertEquals(2, msgDao.messagesFor(convId).size)
        assertEquals(MessageEntity.ROLE_USER, msgDao.messagesFor(convId)[0].role)

        // 级联删除：会话删除后消息不残留
        convDao.deleteById(convId)
        assertTrue(msgDao.messagesFor(convId).isEmpty())
    }

    @Test
    fun persistence_survivesDbReopen() {
        // 注意：进程重启语义必须用文件库验证（内存库随连接关闭清空）
        val context: Context = ApplicationProvider.getApplicationContext()
        val dbName = "persist-test.db"
        val fileDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        fileDb.downloadDao().insert(task("t-persist", DownloadState.PAUSED))
        val model = ModelEntity("m1", "1.0", "Model One", "pub", "Q4_K_M",
            "Apache-2.0", "model.gguf", 4096L, 1000L)
        fileDb.modelDao().insert(model)
        fileDb.close()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(DownloadState.PAUSED, reopened.downloadDao().getById("t-persist")!!.state)
            assertNotNull(reopened.modelDao().get("m1", "1.0"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun modelDao_replaceAndDelete() {
        val db = db!!
        val dao = db.modelDao()
        val v1 = ModelEntity("m1", "1.0", "Model One", "pub", "Q4_K_M",
            "Apache-2.0", "model.gguf", 4096L, 1000L)
        dao.insert(v1)
        dao.insert(ModelEntity("m1", "2.0", "Model One", "pub", "Q4_K_M",
            "Apache-2.0", "model.gguf", 4096L, 1000L))
        assertEquals(2, dao.count())
        assertNotNull(dao.getByModelId("m1"))
        dao.deleteById("m1", "1.0")
        assertEquals(1, dao.count())
        assertNull(dao.get("m1", "1.0"))
    }
}
