package com.example.localai.data.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Room 持久化测试（Robolectric）：下载任务/模型/会话/消息 CRUD、
 * 级联删除与"进程重启"（关闭数据库后重开仍可读）验证。
 */
@RunWith(RobolectricTestRunner.class)
public class RoomPersistenceTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void downloadEntity_roundtrip() {
        DownloadEntity entity = new DownloadEntity("t1", "m1", "1.0", "model.gguf",
                "Model One", "pub", "Q4_K_M", "Apache-2.0", 1000L,
                4096, "abc123", "http://x/model.gguf");
        entity.state = DownloadState.DOWNLOADING;
        entity.bytesDownloaded = 1024;
        entity.etag = "\"e1\"";
        db.downloadDao().insert(entity);

        DownloadEntity loaded = db.downloadDao().getById("t1");
        assertNotNull(loaded);
        assertEquals("m1", loaded.modelId);
        assertEquals(1024, loaded.bytesDownloaded);
        assertEquals("\"e1\"", loaded.etag);
        assertEquals(DownloadState.DOWNLOADING, loaded.state);
        assertEquals(25, loaded.percent());
    }

    @Test
    public void downloadState_transitions() {
        assertTrue(DownloadState.canTransition(DownloadState.QUEUED, DownloadState.DOWNLOADING));
        assertTrue(DownloadState.canTransition(DownloadState.DOWNLOADING, DownloadState.PAUSED));
        assertTrue(DownloadState.canTransition(DownloadState.PAUSED, DownloadState.DOWNLOADING));
        assertTrue(DownloadState.canTransition(DownloadState.DOWNLOADING, DownloadState.VERIFYING));
        assertTrue(DownloadState.canTransition(DownloadState.VERIFYING, DownloadState.INSTALLING));
        assertTrue(DownloadState.canTransition(DownloadState.INSTALLING, DownloadState.READY));
        assertTrue(DownloadState.canTransition(DownloadState.FAILED, DownloadState.DOWNLOADING));
        assertTrue(!DownloadState.canTransition(DownloadState.READY, DownloadState.DOWNLOADING));
        assertTrue(!DownloadState.canTransition(DownloadState.PAUSED, DownloadState.VERIFYING));
        assertTrue(!DownloadState.canTransition(DownloadState.FAILED, DownloadState.PAUSED));

        DownloadEntity entity = new DownloadEntity("t2", "m1", "1.0", "f", "n", "p", "q", "l",
                1L, 10, "s", "u");
        assertTrue(!entity.transition(DownloadState.PAUSED)); // QUEUED -> PAUSED 非法
        assertEquals(DownloadState.QUEUED, entity.state);
        assertTrue(entity.transition(DownloadState.DOWNLOADING));
        assertEquals(DownloadState.DOWNLOADING, entity.state);
    }

    @Test
    public void recoverableQuery_excludesTerminal() {
        DownloadDao dao = db.downloadDao();
        dao.insert(task("t1", DownloadState.DOWNLOADING));
        dao.insert(task("t2", DownloadState.PAUSED));
        dao.insert(task("t3", DownloadState.FAILED));
        dao.insert(task("t4", DownloadState.READY));

        List<DownloadEntity> recoverable = dao.recoverable();
        assertEquals(2, recoverable.size());

        List<DownloadEntity> visible = dao.visible();
        assertEquals(3, visible.size()); // FAILED 仍可见供重试，READY 不可见
    }

    private static DownloadEntity task(String id, String state) {
        DownloadEntity entity = new DownloadEntity(id, "m1", "1.0", "f", "n", "p", "q", "l",
                1L, 100, "s", "u");
        entity.state = state;
        return entity;
    }

    @Test
    public void conversationAndMessages_crudAndCascade() {
        ConversationDao convDao = db.conversationDao();
        MessageDao msgDao = db.messageDao();

        long convId = convDao.insert(new ConversationEntity("标题", "m1"));
        msgDao.insert(new MessageEntity(convId, MessageEntity.ROLE_USER, "问题"));
        msgDao.insert(new MessageEntity(convId, MessageEntity.ROLE_BOT, "回答"));

        assertEquals(2, msgDao.messagesFor(convId).size());
        assertEquals(MessageEntity.ROLE_USER, msgDao.messagesFor(convId).get(0).role);

        // 级联删除：会话删除后消息不残留
        convDao.deleteById(convId);
        assertTrue(msgDao.messagesFor(convId).isEmpty());
    }

    @Test
    public void persistence_survivesDbReopen() {
        // 注意：进程重启语义必须用文件库验证（内存库随连接关闭清空）
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "persist-test.db";
        AppDatabase fileDb = Room.databaseBuilder(context, AppDatabase.class, dbName)
                .allowMainThreadQueries()
                .build();
        fileDb.downloadDao().insert(task("t-persist", DownloadState.PAUSED));
        ModelEntity model = new ModelEntity("m1", "1.0", "Model One", "pub", "Q4_K_M",
                "Apache-2.0", "model.gguf", 4096, 1000);
        fileDb.modelDao().insert(model);
        fileDb.close();

        AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, dbName)
                .allowMainThreadQueries()
                .build();
        try {
            assertEquals(DownloadState.PAUSED, reopened.downloadDao().getById("t-persist").state);
            assertNotNull(reopened.modelDao().get("m1", "1.0"));
        } finally {
            reopened.close();
        }
    }

    @Test
    public void modelDao_replaceAndDelete() {
        ModelDao dao = db.modelDao();
        ModelEntity v1 = new ModelEntity("m1", "1.0", "Model One", "pub", "Q4_K_M",
                "Apache-2.0", "model.gguf", 4096, 1000);
        dao.insert(v1);
        dao.insert(new ModelEntity("m1", "2.0", "Model One", "pub", "Q4_K_M",
                "Apache-2.0", "model.gguf", 4096, 1000));
        assertEquals(2, dao.count());
        assertNotNull(dao.getByModelId("m1"));
        dao.deleteById("m1", "1.0");
        assertEquals(1, dao.count());
        assertNull(dao.get("m1", "1.0"));
    }
}
