package com.example.localai.core.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import java.util.concurrent.Executors

/** UI 侧推理连接：每次绑定、每轮生成独立识别，过期回调不能影响当前会话。 */
class InferenceClient(context: Context) {
    interface Events {
        fun onStateChanged(state: Int)
        fun onToken(batch: String)
        fun onFinished(reason: Int)
        fun onError(code: Int, message: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var state = STATE_IDLE
    @Volatile private var connection: Connection? = null
    @Volatile private var round = 0L
    private var events: Events? = null

    private inner class Connection(val request: InferenceRequest?) : ServiceConnection {
        @Volatile var service: IInferenceService? = null
        var binder: IBinder? = null
        var bound = false
        val death = IBinder.DeathRecipient { postFor(this) { crash(this) } }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            postFor(this) {
                if (binder == null) {
                    crash(this)
                    return@postFor
                }
                try {
                    binder.linkToDeath(death, 0)
                } catch (_: RemoteException) {
                    crash(this)
                    return@postFor
                }
                this.binder = binder
                val connected = IInferenceService.Stub.asInterface(binder)
                service = connected
                val request = request
                if (request == null) {
                    setState(this, STATE_READY)
                    return@postFor
                }
                setState(this, STATE_LOADING)
                binderIo.execute {
                    if (connection !== this) return@execute
                    try {
                        val code = connected.load(request)
                        postFor(this) {
                            if (code == NativeSession.OK) {
                                setState(this, STATE_READY)
                            } else {
                                val receiver = events
                                retire(this)
                                state = STATE_IDLE
                                receiver?.onStateChanged(STATE_IDLE)
                                receiver?.onError(code, describeLoadError(code))
                            }
                        }
                    } catch (_: RemoteException) {
                        postFor(this) { crash(this) }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            postFor(this) { crash(this) }
        }

        override fun onBindingDied(name: ComponentName?) {
            postFor(this) { crash(this) }
        }

        override fun onNullBinding(name: ComponentName?) {
            postFor(this) { crash(this) }
        }
    }

    @Synchronized
    fun setEvents(events: Events) { this.events = events }

    @Synchronized
    fun connect(request: InferenceRequest?, events: Events) {
        this.events = events
        if (connection != null) return
        val next = Connection(request)
        connection = next
        state = STATE_BINDING
        postFor(next) { events.onStateChanged(STATE_BINDING) }
        try {
            next.bound = appContext.bindService(
                Intent(appContext, InferenceService::class.java), next, Context.BIND_AUTO_CREATE)
        } catch (_: RuntimeException) {
            postFor(next) { crash(next) }
            return
        }
        if (!next.bound) postFor(next) { crash(next) }
    }

    @Synchronized
    fun restart(request: InferenceRequest?, events: Events) {
        release()
        connect(request, events)
    }

    @Synchronized
    fun start(prompt: String) {
        val current = connection
        val service = current?.service
        if (current == null || service == null || state != STATE_READY) {
            val receiver = events
            val generation = round
            main.post {
                synchronized(this) {
                    if (events === receiver && round == generation && state != STATE_RELEASED) {
                        receiver?.onError(NativeSession.ERR_WRONG_STATE, "推理服务尚未就绪")
                    }
                }
            }
            return
        }
        val generation = ++round
        val receiver = events
        state = STATE_RUNNING
        postFor(current) { if (round == generation) receiver?.onStateChanged(STATE_RUNNING) }
        val callback = object : IInferenceCallback.Stub() {
            private fun deliver(terminal: Boolean, action: () -> Unit) {
                postFor(current) {
                    if (round != generation || state != STATE_RUNNING) return@postFor
                    if (terminal) {
                        round++
                        state = STATE_READY
                    }
                    action()
                }
            }
            override fun onToken(batch: String) = deliver(false) { receiver?.onToken(batch) }
            override fun onFinished(reason: Int) = deliver(true) { receiver?.onFinished(reason) }
            override fun onError(code: Int, message: String) = deliver(true) { receiver?.onError(code, message) }
        }
        binderIo.execute {
            if (connection !== current || round != generation) return@execute
            try {
                val code = service.start(prompt, callback)
                if (code != NativeSession.OK) callback.onError(code, describeLoadError(code))
            } catch (_: RemoteException) {
                postFor(current) { crash(current) }
            }
        }
    }

    @Synchronized
    fun stop() {
        val current = connection ?: return
        val service = current.service ?: return
        val generation = round
        if (state != STATE_RUNNING) return
        binderIo.execute {
            if (connection !== current || round != generation) return@execute
            try {
                val code = service.stop()
                if (code != NativeSession.OK && code != NativeSession.ERR_WRONG_STATE) {
                    postFor(current) {
                        if (round == generation) {
                            round++
                            state = STATE_READY
                            events?.onError(code, describeLoadError(code))
                        }
                    }
                }
            } catch (_: RemoteException) {
                postFor(current) { crash(current) }
            }
        }
    }

    /** 同步诊断接口，仅供后台调用；业务 UI 不在主线程查询 Binder。 */
    fun getStats(): InferenceStats? {
        val current = connection ?: return null
        return try {
            current.service?.getStats()
        } catch (_: RemoteException) {
            postFor(current) { crash(current) }
            null
        }
    }

    @Synchronized
    fun release() {
        events = null
        connection?.let { retire(it) }
        state = STATE_RELEASED
    }

    fun getState(): Int = state

    private fun retire(current: Connection) {
        if (connection !== current) return
        connection = null
        round++
        // 与所有客户端的 load 共用串行队列，保证旧模型释放不会晚于新模型加载。
        // 解绑延后到释放完成，避免 Native 仍在退出时服务被立即销毁。
        binderIo.execute {
            try {
                current.service?.releaseSession()
            } catch (_: RemoteException) {
                // 进程已死亡时，解绑仍需执行。
            } finally {
                try {
                    current.binder?.unlinkToDeath(current.death, 0)
                } catch (_: java.util.NoSuchElementException) {
                    // Binder 已移除死亡监听。
                }
                if (current.bound) {
                    try {
                        appContext.unbindService(current)
                    } catch (_: IllegalArgumentException) {
                        // 系统已撤销绑定。
                    }
                }
            }
        }
    }

    private fun crash(current: Connection) {
        val receiver = events
        retire(current)
        state = STATE_CRASHED
        receiver?.onStateChanged(STATE_CRASHED)
        receiver?.onError(ERR_ENGINE_CRASHED, "推理进程已退出，请重试")
    }

    private fun setState(current: Connection, next: Int) {
        if (connection === current && state != next) {
            state = next
            events?.onStateChanged(next)
        }
    }

    private fun postFor(current: Connection, action: () -> Unit) {
        main.post {
            synchronized(this) {
                if (connection === current) action()
            }
        }
    }

    companion object {
        // 应用只有一个 Native 会话；共享线程还避免反复切换模型泄漏客户端线程。
        private val binderIo = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "localai-binder").apply { isDaemon = true }
        }
        @JvmField val ERR_ENGINE_CRASHED = 1199
        @JvmField val STATE_IDLE = 0
        @JvmField val STATE_BINDING = 1
        @JvmField val STATE_LOADING = 2
        @JvmField val STATE_READY = 3
        @JvmField val STATE_RUNNING = 4
        @JvmField val STATE_CRASHED = 5
        @JvmField val STATE_RELEASED = 6

        private fun describeLoadError(code: Int): String = when (code) {
            NativeSession.ERR_MODEL_LOAD_FAILED -> "模型文件加载失败（文件无效或不兼容）"
            NativeSession.ERR_CONTEXT_CREATE_FAILED -> "推理上下文创建失败（内存不足）"
            NativeSession.ERR_TOKENIZE_FAILED -> "输入文本分词失败"
            NativeSession.ERR_WRONG_STATE -> "推理服务状态异常"
            NativeSession.ERR_ILLEGAL_ARGUMENT -> "模型路径不合法"
            else -> "推理失败（code=$code）"
        }
    }
}
