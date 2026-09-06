package com.example.localai.feature.settings

import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.feature.diagnostics.DiagnosticsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class PrivacyInstrumentedTest {
    @Test fun privacyCanBeReadRecreatedAndExitedWithoutLosingNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_settings)
                activity.findViewById<View>(R.id.row_privacy).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.primaryNavigationFragment is PrivacyFragment)
                val body = activity.findViewById<TextView>(R.id.privacy_body).text.toString()
                assertTrue(body.contains("hf-mirror.com") && body.contains("huggingface.co"))
                assertTrue(body.contains("剪贴板") && body.contains("卸载") && body.contains("清空全部会话"))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var previousScroll = 0
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.privacy_scroll)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                previousScroll = scroll.scrollY
                assertTrue("完整说明应可滚动阅读", previousScroll > 0)
            }
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.supportFragmentManager.primaryNavigationFragment is PrivacyFragment)
                assertEquals(previousScroll, activity.findViewById<ScrollView>(R.id.privacy_scroll).scrollY)
                activity.findViewById<View>(R.id.btn_back).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.primaryNavigationFragment is SettingsFragment)
                assertEquals(R.id.nav_settings, activity.findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId)
                activity.findViewById<View>(R.id.row_privacy).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                activity.openTab(R.id.nav_diag)
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                assertTrue(activity.supportFragmentManager.primaryNavigationFragment is DiagnosticsFragment)
            }
        }
    }

    @Test fun installedApkExcludesPrivateDataFromCloudBackupAndDeviceTransfer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val androidNs = "http://schemas.android.com/apk/res/android"
        var rulesId = 0
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    assertFalse(parser.getAttributeBooleanValue(androidNs, "allowBackup", true))
                    assertFalse(parser.getAttributeBooleanValue(androidNs, "fullBackupContent", true))
                    rulesId = parser.getAttributeResourceValue(androidNs, "dataExtractionRules", 0)
                }
            }
        }
        assertTrue("安装包必须引用排除规则", rulesId != 0)
        val exclusions = mutableMapOf<String, MutableSet<String>>()
        context.resources.getXml(rulesId).use { parser ->
            var section = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "cloud-backup", "device-transfer" -> section = parser.name
                    "include" -> fail("不能重新包含用户数据")
                    "exclude" -> {
                        assertEquals(".", parser.getAttributeValue(null, "path"))
                        exclusions.getOrPut(section) { mutableSetOf() }.add(parser.getAttributeValue(null, "domain"))
                    }
                }
            }
        }
        val domains = setOf("root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref")
        assertEquals(setOf("cloud-backup", "device-transfer"), exclusions.keys)
        exclusions.values.forEach { assertEquals(domains, it) }
    }
}
