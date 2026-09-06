package com.example.localai.feature.diagnostics

import android.content.res.Configuration
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 在当前模拟器的实际字体/方向配置下验证，不修改系统配置或模型数据。 */
@RunWith(AndroidJUnit4::class)
class ReadabilityInstrumentedTest {
    @Test fun diagnosticsKeepCompleteValuesAtCurrentFontSize() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openTab(R.id.nav_diag) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val systemScale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale
                assertEquals("应用应遵循系统字号并限制在 150%", minOf(systemScale, 1.5f), activity.resources.configuration.fontScale, 0.001f)
                val ids = listOf(R.id.diag_device_name, R.id.spec_soc, R.id.spec_ram, R.id.spec_os,
                    R.id.spec_abi, R.id.spec_storage, R.id.spec_page, R.id.chip_thermal, R.id.chip_battery)
                for (id in ids) {
                    val text = activity.findViewById<TextView>(id)
                    val layout = text.layout
                    val name = activity.resources.getResourceEntryName(id)
                    assertNotNull("$name 未完成排版", layout)
                    assertTrue("$name 不应为空", text.text.isNotEmpty())
                    assertTrue("$name 被省略", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                    assertEquals("$name 丢失末尾文字", text.text.length, layout.getLineEnd(layout.lineCount - 1))
                    assertTrue("$name 高度裁切", layout.height <= text.height - text.totalPaddingTop - text.totalPaddingBottom)
                }
            }
        }
    }

    @Test fun secondaryMetadataHasReadableContrastInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val themed = context.createConfigurationContext(config)
            val foreground = ContextCompat.getColor(themed, R.color.text_tertiary)
            for (backgroundId in listOf(R.color.app_background, R.color.surface, R.color.surface_variant)) {
                val contrast = ColorUtils.calculateContrast(foreground, ContextCompat.getColor(themed, backgroundId))
                assertTrue("辅助文字对比度不足：$contrast", contrast >= 4.5)
            }
        }
    }
}
