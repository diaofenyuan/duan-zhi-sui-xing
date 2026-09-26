package com.example.localai.feature.library

import android.content.Context
import android.net.ConnectivityManager
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/** 复用真实功能断言，在同一进程内验证连续使用；结果仅代表运行测试的设备。 */
@RunWith(AndroidJUnit4::class)
class OfflineWorkloadInstrumentedTest {
    @Test fun repeatsOfflineWorkflowsInOneProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // 本类为离线专项验收：qa/build/run_emulator_acceptance.py 会先开飞行模式再单跑本类。
        // 联网的整包回归下跳过；一旦开始执行，下方第 33 行仍强制校验全程无网络。
        Assume.assumeTrue("请先断开虚拟机网络后再运行本用例", connectivity.activeNetwork == null)
        val flow = LibraryFlowInstrumentedTest()
        val ocr = OfflineOcrInstrumentedTest()
        flow.importsPdfPageTextWithoutNetworkAndRejectsBlankScan()
        flow.interruptedChecklistRestoresStreamCheckpoint()
        repeat(3) { round ->
            val started = SystemClock.elapsedRealtime()
            val before = context.getSharedPreferences("localai_measurements", 0).getLong("time", 0)
            flow.offlineQuestionCitationAndEditableChecklistSurviveReopen()
            val taskMs = SystemClock.elapsedRealtime() - started
            ocr.recognizesChineseReportAndNumbersLocally()
            ocr.blankImageDoesNotFabricateTextAndCancelledRunStops()
            assertNull("运行期间恢复了网络，不能作为离线验收", connectivity.activeNetwork)
            val stats = context.getSharedPreferences("localai_measurements", 0)
            assertTrue("本轮没有记录真实推理数据", stats.getLong("time", 0) > before)
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            // PSS 是轮次结束时主进程的快照；独立推理进程和采样峰值由 ADB 另行采集。
            Log.i("OfflineWorkloadQA", "round=${round + 1}, taskMs=$taskMs, " +
                "lastTtftMs=${stats.getLong("ttft", 0)}, lastElapsedMs=${stats.getLong("elapsed", 0)}, " +
                "lastTokens=${stats.getLong("tokens", 0)}, mainPssKb=${memory.totalPss}")
        }
    }
}
