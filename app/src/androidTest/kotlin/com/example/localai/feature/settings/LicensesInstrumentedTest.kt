package com.example.localai.feature.settings

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun index() = JSONArray(context.assets.open("licenses/index.json").bufferedReader().use { it.readText() })

    @Test fun allBundledNoticesAreReadableAndCoverTransitiveNativeAndModelLicenses() {
        val entries = index()
        val coordinates = mutableSetOf<String>()
        val titles = mutableListOf<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            titles.add(entry.getString("title"))
            val modules = entry.getJSONArray("components")
            for (j in 0 until modules.length()) coordinates.add(modules.getString(j))
            val files = entry.getJSONArray("texts")
            for (j in 0 until files.length()) {
                val text = context.assets.open("licenses/" + files.getString(j)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                assertTrue(text.length > 500)
                assertFalse("许可文本不能有解码损坏", text.contains('\uFFFD'))
            }
        }
        assertTrue(coordinates.contains("com.squareup.okio:okio-jvm:3.6.0"))
        assertTrue(coordinates.contains("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1"))
        assertFalse(coordinates.any { it.startsWith("junit:") || it.startsWith("androidx.work:") })
        assertTrue(titles.contains("Public Suffix List"))
        assertTrue(titles.any { it.contains("libc++") })
        assertTrue(titles.contains("Qwen2.5-0.5B-Instruct"))
    }

    @Test fun licenseEntryAndScrollSurviveRecreationAndInvalidEntryIsRecoverable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_settings)
                activity.findViewById<View>(R.id.row_license).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                val content = activity.findViewById<android.widget.LinearLayout>(R.id.license_content)
                assertEquals(index().length() + 1, content.childCount)
                // 第一条为实际组件组，用真实入口进入许可证正文。
                content.getChildAt(1).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                val text = activity.findViewById<TextView>(R.id.license_text).text.toString()
                assertTrue(text.contains("androidx.room:room-runtime-android:2.8.4"))
                assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
                assertFalse(text.contains("docs/license-policy.md"))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var position = 0
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.license_scroll)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                position = scroll.scrollY
                assertTrue(position > 0)
            }
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(position, activity.findViewById<ScrollView>(R.id.license_scroll).scrollY)
                activity.findViewById<View>(R.id.btn_back).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                assertEquals(activity.getString(R.string.row_license), activity.findViewById<TextView>(R.id.license_title).text)
                val entries = index()
                val llvm = (0 until entries.length()).first { entries.getJSONObject(it).getString("title").contains("libc++") }
                activity.findViewById<android.widget.LinearLayout>(R.id.license_content).getChildAt(llvm + 1).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                val runtimeText = activity.findViewById<TextView>(R.id.license_text).text.toString()
                assertTrue(runtimeText.length > 100_000)
                assertTrue(runtimeText.contains("LLVM Exceptions"))
                activity.findViewById<View>(R.id.btn_back).performClick()
                activity.supportFragmentManager.executePendingTransactions()
                activity.push(LicensesFragment().apply { arguments = Bundle().apply { putInt("entry", Int.MAX_VALUE) } })
                activity.supportFragmentManager.executePendingTransactions()
                val content = activity.findViewById<android.widget.LinearLayout>(R.id.license_content)
                assertEquals(activity.getString(R.string.licenses_load_failed), (content.getChildAt(0) as TextView).text)
                content.getChildAt(1).performClick()
                assertEquals(2, content.childCount)
                activity.openTab(R.id.nav_settings)
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                assertTrue(activity.supportFragmentManager.primaryNavigationFragment is SettingsFragment)
            }
        }
    }
}
