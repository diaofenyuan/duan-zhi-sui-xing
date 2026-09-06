package com.example.localai.core.inference

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 使用真实 AIDL Stub 控制时序，不加载 JNI；验证连接和轮次之间的隔离。 */
@RunWith(RobolectricTestRunner::class)
class InferenceClientLifecycleTest {
    class BindingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val connections = CopyOnWriteArrayList<ServiceConnection>()
        val unbound = CopyOnWriteArrayList<ServiceConnection>()
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            connections.add(connection)
            return true
        }
        override fun unbindService(connection: ServiceConnection) { unbound.add(connection) }
    }

    class FakeService : IInferenceService.Stub() {
        val requests = CopyOnWriteArrayList<InferenceRequest>()
        val callbacks = CopyOnWriteArrayList<IInferenceCallback>()
        val loadEntered = CountDownLatch(1)
        var loadGate: CountDownLatch? = null
        var loadCode = NativeSession.OK
        @Volatile var releases = 0
        @Volatile var releasedOnMain = false
        override fun load(request: InferenceRequest?): Int {
            if (request != null) requests.add(request)
            loadEntered.countDown()
            check(loadGate?.await(5, TimeUnit.SECONDS) != false)
            return loadCode
        }
        override fun start(prompt: String, cb: IInferenceCallback): Int {
            callbacks.add(cb)
            return NativeSession.OK
        }
        override fun stop(): Int = NativeSession.OK
        override fun releaseSession() {
            releasedOnMain = Looper.myLooper() == Looper.getMainLooper()
            releases++
        }
        override fun getPid(): Int = 123
        override fun getStats(): InferenceStats? = null
    }

    class Recorder : InferenceClient.Events {
        val states = mutableListOf<Int>()
        val tokens = mutableListOf<String>()
        val errors = mutableListOf<Int>()
        var finishes = 0
        override fun onStateChanged(state: Int) { states.add(state) }
        override fun onToken(batch: String) { tokens.add(batch) }
        override fun onError(code: Int, message: String) { errors.add(code) }
        override fun onFinished(reason: Int) { finishes++ }
    }

    private lateinit var context: BindingContext
    private lateinit var client: InferenceClient
    private val services = mutableListOf<FakeService>()
    private val name = ComponentName("com.example.localai", "InferenceService")
    private val request = InferenceRequest("test", "m", "1", "/model.gguf", 512, 2, 0.7f, 0.9f, 32)

    @Before fun setUp() {
        context = BindingContext()
        client = InferenceClient(context)
    }

    @After fun tearDown() {
        services.forEach { it.loadGate?.countDown() }
        client.release()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("推理事件超时", condition())
    }

    private fun connect(recorder: Recorder, service: FakeService = FakeService()): FakeService {
        services.add(service)
        client.connect(request, recorder)
        context.connections.last().onServiceConnected(name, service)
        await { client.getState() == InferenceClient.STATE_READY || recorder.errors.isNotEmpty() }
        return service
    }

    @Test fun releaseDoesNotCallSlowBinderOnMainThread() {
        val service = connect(Recorder())
        client.release()
        await { service.releases == 1 }
        assertFalse("释放 Native 会话不能阻塞 UI", service.releasedOnMain)
    }

    @Test fun lateGenerationEventsCannotReviveReleasedClient() {
        val events = Recorder()
        val service = connect(events)
        client.start("hello")
        await { service.callbacks.size == 1 }
        client.release()
        service.callbacks[0].onToken("late")
        service.callbacks[0].onFinished(NativeSession.FINISH_STOPPED)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(InferenceClient.STATE_RELEASED, client.getState())
        assertTrue(events.tokens.isEmpty())
        assertEquals(0, events.finishes)
    }

    @Test fun oldRoundCannotWriteIntoNextRound() {
        val first = Recorder()
        val service = connect(first)
        client.start("one")
        await { service.callbacks.size == 1 }
        service.callbacks[0].onFinished(NativeSession.FINISH_STOPPED)
        await { first.finishes == 1 }
        val second = Recorder()
        client.setEvents(second)
        client.start("two")
        await { service.callbacks.size == 2 }
        service.callbacks[0].onToken("old")
        service.callbacks[0].onError(NativeSession.ERR_INTERNAL, "old error")
        service.callbacks[1].onToken("new")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("new"), second.tokens)
        assertTrue(second.errors.isEmpty())
        assertEquals(InferenceClient.STATE_RUNNING, client.getState())
    }

    @Test fun repeatedDisconnectReportsOnlyOneCrash() {
        val events = Recorder()
        connect(events)
        val connection = context.connections.last()
        connection.onServiceDisconnected(name)
        connection.onServiceDisconnected(name)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(InferenceClient.ERR_ENGINE_CRASHED), events.errors)
    }

    @Test fun failedLoadUnbindsBeforeRetry() {
        val events = Recorder()
        connect(events, FakeService().apply { loadCode = NativeSession.ERR_MODEL_LOAD_FAILED })
        await { context.unbound.size == 1 }
        assertEquals(listOf(NativeSession.ERR_MODEL_LOAD_FAILED), events.errors)
        connect(Recorder())
        assertEquals(2, context.connections.size)
    }

    @Test fun releasedBindingCannotLoadAfterReconnect() {
        client.connect(request, Recorder())
        val old = context.connections.last()
        client.release()
        val newEvents = Recorder()
        connect(newEvents)
        old.onServiceConnected(name, FakeService())
        old.onServiceDisconnected(name)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(newEvents.errors.isEmpty())
        assertEquals(InferenceClient.STATE_READY, client.getState())
    }
}
