package com.example.localai.feature.settings

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.inference.ApprovedModels
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {
    @Test fun manualParametersValidatePersistAndResetThroughSettingsDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(InferencePolicy.PREFS, android.content.Context.MODE_PRIVATE)
        val oldContext = prefs.getInt(InferencePolicy.KEY_CONTEXT, 0)
        val oldGpu = prefs.getInt(InferencePolicy.KEY_GPU_LAYERS, 0)
        fun input(view: View): EditText? = if (view is EditText) view else
            if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { input(view.getChildAt(it)) } else null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_settings)
                    activity.findViewById<View>(R.id.row_context_length).performClick()
                }
                scenario.onActivity {
                    val dialog = WindowInspector.getGlobalWindowViews().first { it.findViewById<View>(android.R.id.button1)?.isShown == true }
                    val edit = input(dialog)!!
                    edit.setText("999999999999")
                    dialog.findViewById<View>(android.R.id.button1).performClick()
                    assertNotNull(edit.error)
                    edit.setText("8192")
                    dialog.findViewById<View>(android.R.id.button1).performClick()
                }
                scenario.onActivity { it.findViewById<View>(R.id.row_gpu_layers).performClick() }
                scenario.onActivity {
                    val dialog = WindowInspector.getGlobalWindowViews().first { it.findViewById<View>(android.R.id.button1)?.isShown == true }
                    input(dialog)!!.setText("12")
                    dialog.findViewById<View>(android.R.id.button1).performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val params = InferencePolicy.current(activity, ApprovedModels.QWEN_05B)
                    assertEquals(8192, params.contextLength)
                    assertEquals(12, params.gpuLayers)
                    activity.openTab(R.id.nav_settings)
                    activity.findViewById<View>(R.id.row_gpu_layers).performClick()
                }
                scenario.onActivity {
                    WindowInspector.getGlobalWindowViews().mapNotNull { it.findViewById<View>(android.R.id.button3) }.first { it.isShown }.performClick()
                }
                scenario.onActivity {
                    assertEquals(0, prefs.getInt(InferencePolicy.KEY_GPU_LAYERS, 0))
                }
            }
        } finally {
            prefs.edit().putInt(InferencePolicy.KEY_CONTEXT, oldContext).putInt(InferencePolicy.KEY_GPU_LAYERS, oldGpu).commit()
        }
    }

    @Test fun radioSelectionChangesEffectiveInferenceLimits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(InferencePolicy.PREFS, android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString(InferencePolicy.KEY_MODE, null)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_settings)
                    activity.findViewById<View>(R.id.radio_balanced).performClick()
                    activity.findViewById<View>(R.id.radio_saver).performClick()
                    assertEquals("saver", prefs.getString(InferencePolicy.KEY_MODE, null))
                    val parameters = InferencePolicy.current(activity, ApprovedModels.QWEN_05B)
                    assertEquals(1024, parameters.contextLength)
                    assertTrue(parameters.threads <= 2)
                    assertEquals(128, parameters.maxNewTokens)
                }
            }
        } finally {
            if (previous == null) prefs.edit().remove(InferencePolicy.KEY_MODE).commit()
            else prefs.edit().putString(InferencePolicy.KEY_MODE, previous).commit()
        }
    }

    @Test fun clearCacheButtonRemovesTemporaryFileAndKeepsInstalledModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "settings-cleanup-regression.tmp").apply { writeBytes(ByteArray(128 * 1024)) }
        val model = ApprovedModels.QWEN_05B
        assertTrue(ApprovedModels.isInstalled(context, model.modelId))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_settings)
                activity.findViewById<View>(R.id.row_clear_cache).performClick()
            }
            scenario.onActivity {
                val confirm = WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<View>(android.R.id.button1) }.first { it.isShown }
                confirm.performClick()
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (fixture.exists() && System.nanoTime() < deadline) Thread.sleep(25)
            assertFalse("设置按钮没有执行真实清理", fixture.exists())
            assertTrue("清理缓存不得删除模型", ApprovedModels.isInstalled(context, model.modelId))
        }
    }
}
