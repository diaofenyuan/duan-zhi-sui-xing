package com.example.localai.feature.diagnostics

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.ModelEntity
import com.example.localai.feature.download.DownloadRepository
import com.example.localai.feature.settings.InferencePolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiagnosticsInstrumentedTest {
    private fun fragment(activity: MainActivity) = activity.supportFragmentManager.findFragmentByTag("diag") as DiagnosticsFragment
    private fun text(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
        else -> ""
    }
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        var passed = false
        while (!passed && System.nanoTime() < deadline) {
            scenario.onActivity { passed = condition(it) }
            if (!passed) Thread.sleep(50)
        }
        assertTrue("诊断页未及时刷新", passed)
    }

    @Test fun visibleDiagnosticsRefreshesAndReportsActualDeviceState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openTab(R.id.nav_diag) }
            await(scenario) { fragment(it).requireView().findViewById<TextView>(R.id.spec_soc).text.isNotEmpty() }
            scenario.onActivity { activity ->
                val root = fragment(activity).requireView()
                val profile = DeviceProfiler.collect(activity)
                assertEquals("${profile.cores} 个可用逻辑核心", root.findViewById<TextView>(R.id.spec_soc).text.toString())
                val expectedBattery = if (profile.batteryPercent in 0..100)
                    activity.getString(R.string.chip_battery_fmt, profile.batteryPercent, if (profile.charging) "已接电源" else "未接电源")
                    else activity.getString(R.string.chip_battery_unknown)
                assertEquals(expectedBattery, root.findViewById<TextView>(R.id.chip_battery).text.toString())
                val displayed = text(root)
                assertTrue(displayed.contains("模型运行内存估算"))
                assertFalse(displayed.contains("P50") || displayed.contains("演示数据") || displayed.contains("NEON"))
                root.findViewById<TextView>(R.id.spec_soc).text = "stale"
                activity.openTab(R.id.nav_settings)
                activity.openTab(R.id.nav_diag)
            }
            await(scenario) { fragment(it).requireView().findViewById<TextView>(R.id.spec_soc).text.toString() != "stale" }
            scenario.onActivity { fragment(it).requireView().findViewById<TextView>(R.id.chip_battery).text = "pending" }
            await(scenario) { fragment(it).requireView().findViewById<TextView>(R.id.chip_battery).text.toString() != "pending" }
        }
    }

    @Test fun installedSnapshotChangesRefreshVisibleRowsWithoutTouchingModels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openTab(R.id.nav_diag) }
            await(scenario) { text(fragment(it).requireView().findViewById(R.id.bench_list)).contains("Qwen") }
            val repository = ServiceLocator.downloads()!!
            val cache = repository.javaClass.getDeclaredField("installedCache").apply { isAccessible = true }
            val original = repository.installed()
            try {
                scenario.onActivity { activity ->
                    val diag = fragment(activity)
                    val params = InferencePolicy.current(activity, ApprovedModels.QWEN_05B)
                    assertTrue(text(diag.requireView().findViewById(R.id.bench_list)).contains("上下文 ${params.contextLength}"))
                    // 只替换内存快照模拟异步通知，不删除数据库记录或用户模型文件。
                    cache.set(repository, emptyList<ModelEntity>())
                    val listener = diag.javaClass.getDeclaredField("repositoryListener").apply { isAccessible = true }.get(diag) as DownloadRepository.Listener
                    listener.onDownloadsChanged()
                }
                await(scenario) { text(fragment(it).requireView().findViewById(R.id.bench_list)).contains("暂无已安装模型") }
            } finally {
                scenario.onActivity { activity ->
                    cache.set(repository, original)
                    val diag = fragment(activity)
                    val listener = diag.javaClass.getDeclaredField("repositoryListener").apply { isAccessible = true }.get(diag) as DownloadRepository.Listener
                    listener.onDownloadsChanged()
                    assertTrue(ApprovedModels.isInstalled(activity, ApprovedModels.QWEN_05B.modelId))
                }
            }
            await(scenario) { text(fragment(it).requireView().findViewById(R.id.bench_list)).contains("Qwen") }
        }
    }
}
