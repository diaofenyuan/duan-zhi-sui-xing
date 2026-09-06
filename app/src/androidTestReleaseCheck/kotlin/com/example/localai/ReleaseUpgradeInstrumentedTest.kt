package com.example.localai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.data.ServiceLocator
import com.example.localai.data.network.CatalogConfig
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.room.ModelEntity
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.feature.chat.ChatFragment
import com.example.localai.feature.chat.ChatRepository
import com.example.localai.feature.chat.MessageAdapter
import com.example.localai.feature.download.ModelVerifier
import com.example.localai.feature.settings.InferencePolicy
import com.example.localai.model.ChatMessage
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 两次独立安装之间运行；仅编译进 releaseCheck 测试 APK，绝不操作原应用的数据。 */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeInstrumentedTest {
    @Test fun releaseInstallAndUpgradePreserveData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("com.example.localai.releasecheck", context.packageName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val phase = InstrumentationRegistry.getArguments().getString("upgradePhase")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val expectedVersion = InstrumentationRegistry.getArguments().getString("expectedVersionCode")?.toLong()
            ?: if (phase == "prepare") 1L else 2L
        assertEquals(expectedVersion, version)
        val model = ApprovedModels.QWEN_05B
        val policy = context.getSharedPreferences(InferencePolicy.PREFS, Context.MODE_PRIVATE)
        val bundle = CatalogConfig.create(context, OkHttpClient()).fetchManifestBundle(model.modelId, model.version)
        val digest = bundle.manifest.files!!.single().sha256
        if (phase == "prepare") {
            val storage = ModelStorageManager(context.filesDir)
            if (!ApprovedModels.isInstalled(context, model.modelId)) {
                val part = storage.partFile("release-upgrade-fixture")
                // 复用已验证的官方权重副本，模型下载网络链路已有单独验收。
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "cat /data/local/tmp/localai-release-model.gguf")).use { input ->
                    part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                assertEquals(model.sizeBytes, part.length())
                assertTrue(ModelVerifier.sha256Matches(part, digest))
                storage.install(model.modelId, model.version, part, bundle.json, bundle.sig, model.fileName)
                storage.removeDownloadDir("release-upgrade-fixture")
            }
            withDatabase(context) { db ->
                db.modelDao().insert(ModelEntity(model.modelId, model.version, model.displayName,
                    model.publisher, "Q4_K_M", model.license, model.fileName, model.sizeBytes, model.parameterCount))
            }
            assertTrue(policy.edit().putString(InferencePolicy.KEY_MODE, "saver")
                .putBoolean(InferencePolicy.KEY_KEEP_SCREEN, true).commit())
            val saved = CountDownLatch(1)
            var error: String? = null
            ServiceLocator.chat()!!.saveSession("release-upgrade", 0, model.modelId, "升级后保留的草稿",
                "发布升级验收", listOf(ChatMessage(ChatMessage.ROLE_USER, "升级前的问题"),
                    ChatMessage(ChatMessage.ROLE_BOT, "升级前保存的测试正文")), object : ChatRepository.ConversationSavedCallback {
                    override fun onSaved(conversationId: Long) { saved.countDown() }
                    override fun onError(message: String?) { error = message ?: "保存失败"; saved.countDown() }
                })
            assertTrue(saved.await(15, TimeUnit.SECONDS))
            assertNull(error)
        } else {
            assertEquals("verify", phase)
            assertEquals("saver", policy.getString(InferencePolicy.KEY_MODE, null))
            assertTrue(policy.getBoolean(InferencePolicy.KEY_KEEP_SCREEN, false))
            assertTrue(ApprovedModels.isInstalled(context, model.modelId))
            assertTrue(ModelVerifier.sha256Matches(ApprovedModels.modelFile(context, model), digest))
            withDatabase(context) { db ->
                assertNotNull(db.modelDao().get(model.modelId, model.version))
                val session = db.chatSessionDao().current()!!
                assertEquals("release-upgrade", session.token)
                assertEquals("升级后保留的草稿", session.draft)
                val messages = db.messageDao().messagesFor(session.conversationId)
                assertEquals(listOf("升级前的问题", "升级前保存的测试正文"), messages.map { it.content })
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.openTab(R.id.nav_chat) }
                await(scenario) { it.findViewById<EditText>(R.id.input).text.toString() == "升级后保留的草稿" }
                scenario.onActivity {
                    it.findViewById<EditText>(R.id.input).setText("请只用一句中文向我问好。")
                    it.findViewById<android.view.View>(R.id.btn_send).performClick()
                }
                await(scenario) { activity ->
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    val running = chat.javaClass.getDeclaredField("generating").apply { isAccessible = true }.getBoolean(chat)
                    val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                    val messages = adapter.items().filterIsInstance<ChatMessage>()
                    assertFalse(messages.any { it.text.startsWith("生成失败：") })
                    !running && messages.size == 4 && messages.last().text.any { it in '\u4e00'..'\u9fff' }
                }
            }
        }
    }

    private fun withDatabase(context: Context, action: (AppDatabase) -> Unit) {
        val db = AppDatabase.build(context)
        try { action(db) } finally { db.close() }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        var done = false
        while (!done && System.nanoTime() < deadline) {
            scenario.onActivity { done = condition(it) }
            if (!done) Thread.sleep(100)
        }
        assertTrue("发布验收页面未在期限内完成", done)
    }
}
