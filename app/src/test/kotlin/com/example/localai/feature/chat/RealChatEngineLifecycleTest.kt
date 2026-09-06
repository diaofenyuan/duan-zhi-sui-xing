package com.example.localai.feature.chat

import android.content.ComponentName
import android.os.Looper
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.core.inference.InferenceClientLifecycleTest.BindingContext
import com.example.localai.core.inference.InferenceClientLifecycleTest.FakeService
import com.example.localai.core.inference.NativeSession
import com.example.localai.model.ChatMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RealChatEngineLifecycleTest {
    private lateinit var context: BindingContext
    private lateinit var engine: RealChatEngine
    private val services = mutableListOf<FakeService>()
    private val name = ComponentName("com.example.localai", "InferenceService")
    private val history = listOf(ChatMessage(ChatMessage.ROLE_USER, "hello"))

    private class Recorder : ChatEngine.StreamListener {
        var thinking = 0
        val tokens = mutableListOf<String>()
        val finishes = mutableListOf<Boolean>()
        val errors = mutableListOf<Int>()
        override fun onThinking() { thinking++ }
        override fun onDelta(delta: String) { tokens.add(delta) }
        override fun onFinished(stopped: Boolean) { finishes.add(stopped) }
        override fun onError(code: Int, message: String) { errors.add(code) }
    }

    @Before fun setUp() {
        context = BindingContext()
        engine = RealChatEngine(context, ApprovedModels.SMOLLM_135M)
    }
    @After fun tearDown() {
        services.forEach { it.loadGate?.countDown() }
        services.forEach { it.releaseGate?.countDown() }
        engine.release()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("等待推理状态超时", condition())
    }
    private fun bind(service: FakeService = FakeService()): FakeService {
        services.add(service)
        context.connections.last().onServiceConnected(name, service)
        return service
    }

    @Test fun modelStateWaitsForNativeReleaseAndObservesIdleCrash() {
        val states = mutableListOf<ChatEngine.ModelState>()
        engine.setModelStateListener { states.add(engine.modelState()) }
        assertEquals(ChatEngine.ModelState.UNLOADED, engine.modelState())
        engine.start(history, Recorder())
        assertEquals(ChatEngine.ModelState.LOADING, engine.modelState())
        val service = bind()
        await { service.callbacks.size == 1 }
        assertEquals(ChatEngine.ModelState.GENERATING, engine.modelState())
        service.callbacks.single().onFinished(NativeSession.FINISH_END)
        await { !engine.isRunning() }
        assertEquals(ChatEngine.ModelState.LOADED, engine.modelState())
        val gate = CountDownLatch(1)
        service.releaseGate = gate
        engine.release()
        await { service.releaseEntered.count == 0L }
        assertEquals(ChatEngine.ModelState.RELEASING, engine.modelState())
        assertEquals(ChatEngine.ModelState.RELEASING, states.last())
        gate.countDown()
        await { engine.modelState() == ChatEngine.ModelState.UNLOADED }
        assertEquals(1, service.releases)
        assertFalse(service.releasedOnMain)

        engine.start(history, Recorder())
        val next = bind()
        await { next.callbacks.size == 1 }
        next.callbacks.single().onFinished(NativeSession.FINISH_END)
        await { !engine.isRunning() }
        context.connections.last().onServiceDisconnected(name)
        await { engine.modelState() == ChatEngine.ModelState.ERROR }
        assertEquals(ChatEngine.ModelState.ERROR, states.last())
    }

    @Test fun oldReleaseConfirmationDoesNotOverrideNewLoadingState() {
        engine.start(history, Recorder())
        val service = bind()
        await { service.callbacks.size == 1 }
        service.callbacks.single().onFinished(NativeSession.FINISH_END)
        await { !engine.isRunning() }
        service.releaseGate = CountDownLatch(1)
        engine.release()
        await { service.releaseEntered.count == 0L }
        engine.start(history, Recorder())
        val next = bind(FakeService().apply { loadGate = CountDownLatch(1) })
        service.releaseGate!!.countDown()
        await { next.loadEntered.count == 0L }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ChatEngine.ModelState.LOADING, engine.modelState())
        next.loadGate!!.countDown()
        await { next.callbacks.size == 1 }
        assertEquals(ChatEngine.ModelState.GENERATING, engine.modelState())
    }

    @Test fun stopBeforeBindingFinishesImmediatelyAndNeverGenerates() {
        val events = Recorder()
        engine.start(history, events)
        val binding = context.connections.last()
        engine.stop()
        assertFalse(engine.isRunning())
        assertEquals(listOf(true), events.finishes)
        val service = FakeService()
        binding.onServiceConnected(name, service)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1L, service.loadEntered.count)
        assertTrue(service.callbacks.isEmpty())
    }

    @Test fun changingModeReloadsNativeRequestBeforeNextRound() {
        val prefs = context.getSharedPreferences("localai_settings", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("mode", null)
        try {
            prefs.edit().putString("mode", "balanced").commit()
            engine.start(history, Recorder())
            val first = bind()
            await { first.callbacks.size == 1 }
            assertEquals(2048, first.requests.single().contextLength)
            first.callbacks.single().onFinished(NativeSession.FINISH_END)
            await { !engine.isRunning() }
            prefs.edit().putString("mode", "saver").commit()
            engine.start(history, Recorder())
            assertEquals("模式改变应重新绑定并加载参数", 2, context.connections.size)
            val second = bind()
            await { second.callbacks.size == 1 }
            assertEquals(1024, second.requests.single().contextLength)
            assertTrue(second.requests.single().threadCount <= 2)
            assertEquals(128, second.requests.single().maxNewTokens)
        } finally {
            if (previous == null) prefs.edit().remove("mode").commit()
            else prefs.edit().putString("mode", previous).commit()
        }
    }

    @Test fun stopDuringLoadDoesNotRestartWhenLoadCompletes() {
        val events = Recorder()
        engine.start(history, events)
        val gate = CountDownLatch(1)
        val service = bind(FakeService().apply { loadGate = gate })
        await { service.loadEntered.count == 0L }
        engine.stop()
        assertFalse(engine.isRunning())
        assertEquals(listOf(true), events.finishes)
        gate.countDown()
        await { service.releases == 1 }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.callbacks.isEmpty())
        assertTrue(events.errors.isEmpty())
    }

    @Test fun nextRoundUsesItsOwnListenerAndIgnoresReleasedCallbacks() {
        val first = Recorder()
        engine.start(history, first)
        val service = bind()
        await { service.callbacks.size == 1 }
        service.callbacks[0].onFinished(NativeSession.FINISH_STOPPED)
        await { !engine.isRunning() }
        val second = Recorder()
        engine.start(history, second)
        await { service.callbacks.size == 2 }
        service.callbacks[1].onToken("second")
        await { second.tokens.isNotEmpty() }
        engine.release()
        service.callbacks[1].onError(NativeSession.ERR_INTERNAL, "late")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(first.tokens.isEmpty())
        assertEquals(listOf("second"), second.tokens)
        assertTrue(second.errors.isEmpty())
        assertFalse(engine.isRunning())
    }

    @Test fun loadFailureCanBeRetriedWithoutOldPrompt() {
        val first = Recorder()
        engine.start(history, first)
        bind(FakeService().apply { loadCode = NativeSession.ERR_MODEL_LOAD_FAILED })
        await { first.errors.isNotEmpty() }
        assertFalse(engine.isRunning())
        val second = Recorder()
        engine.start(history, second)
        val service = bind()
        await { service.callbacks.size == 1 }
        service.callbacks[0].onToken("retried")
        await { second.tokens.isNotEmpty() }
        assertEquals(listOf("retried"), second.tokens)
        assertTrue(first.tokens.isEmpty())
    }
}
