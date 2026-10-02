package com.example.localai.feature.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.localai.R
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 下载通知内容规划测试：标题/正文/进度与节流签名（前台服务保活的展示口径）。 */
@RunWith(RobolectricTestRunner::class)
class DownloadNotificationPlanTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun task(state: String, downloaded: Long, total: Long,
                     taskId: String = "t-1",
                     name: String = "Qwen2.5-1.5B-Instruct · Q8_0",
                     speed: Double = 4_000_000.0): DownloadRepository.TaskView {
        val entity = DownloadEntity(taskId, "m", "1.0", "f.gguf", name, "p", "Q8_0", "l",
            1L, total, "s", "u")
        entity.state = state
        entity.bytesDownloaded = downloaded
        return DownloadRepository.TaskView(entity, speed)
    }

    @Test
    fun downloadingTask_showsNameProgressSpeedAndEta() {
        val plan = DownloadNotificationPlan.of(context,
            listOf(task(DownloadState.DOWNLOADING, 810_000_000, 1_800_000_000)))!!

        assertEquals("Qwen2.5-1.5B-Instruct · Q8_0", plan.title)
        assertEquals(45, plan.percent)
        assertFalse(plan.indeterminate)
        assertTrue(plan.text.contains("4 MB/s"))
        assertTrue(plan.text.contains("剩余"))
    }

    @Test
    fun verifyingTask_isIndeterminate() {
        val plan = DownloadNotificationPlan.of(context,
            listOf(task(DownloadState.VERIFYING, 1_800_000_000, 1_800_000_000)))!!

        assertTrue(plan.indeterminate)
        assertEquals(context.getString(R.string.dl_state_verifying), plan.text)
    }

    @Test
    fun multipleTasks_aggregatePercentByBytes() {
        val first = task(DownloadState.DOWNLOADING, 500_000_000, 1_000_000_000, taskId = "t-a")
        val second = task(DownloadState.DOWNLOADING, 0, 1_000_000_000, taskId = "t-b")

        val plan = DownloadNotificationPlan.of(context, listOf(first, second))!!

        assertEquals(context.getString(R.string.dl_notif_multi_title_fmt, 2), plan.title)
        assertEquals(25, plan.percent)
        assertEquals(context.getString(R.string.dl_notif_multi_text_fmt, 25), plan.text)
    }

    @Test
    fun noActiveTask_returnsNull() {
        assertNull(DownloadNotificationPlan.of(context, listOf(task(DownloadState.PAUSED, 1L, 100L))))
        assertNull(DownloadNotificationPlan.of(context, emptyList()))
    }

    @Test
    fun signatureChangesWithProgress() {
        val first = DownloadNotificationPlan.of(context,
            listOf(task(DownloadState.DOWNLOADING, 810_000_000, 1_800_000_000)))!!
        val second = DownloadNotificationPlan.of(context,
            listOf(task(DownloadState.DOWNLOADING, 828_000_000, 1_800_000_000)))!!

        assertNotEquals(first.signature, second.signature)
    }
}