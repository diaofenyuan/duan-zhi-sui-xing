package com.example.localai.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.util.Arrays

/**
 * 设备画像采集（P4，真实取值，无个人标识：不含序列号/IMEI/账号）。
 * 热状态与电量仅做系统级信号；页大小经 libcore Os.sysconf 读取，读取失败记录未知。
 * 真机与模拟器共用同一条采集路径（S042 复用本接口校准）。
 */
object DeviceProfiler {

    /** 采集结果快照（字段对齐 S023 设备画像）。可变 public 字段保持 Java 侧 getter/setter 兼容。 */
    class Profile {
        @JvmField var manufacturer: String? = null
        @JvmField var model: String? = null
        @JvmField var sdkInt: Int = 0
        @JvmField var androidVersion: String? = null
        @JvmField var abis: List<String>? = null
        @JvmField var cores: Int = 0
        @JvmField var ramTotalMb: Long = 0
        @JvmField var ramAvailMb: Long = 0
        @JvmField var memoryClassMb: Int = 0
        @JvmField var storageFreeMb: Long = 0
        @JvmField var pageSizeKb: Int = 0          // 0 = 未知
        @JvmField var batteryPercent: Int = -1     // -1 = 未知
        @JvmField var charging: Boolean = false
        @JvmField var thermalStatusCode: Int = -1  // -1 = 未知/不可用
        @JvmField var hasNeon: Boolean = false

        fun modelLabel(): String {
            val mfr = manufacturer
            if (mfr == null || mfr.trim().isEmpty()) {
                return model ?: "未知设备"
            }
            return mfr + " " + model
        }

        fun abiLabel(): String {
            val a = abis
            if (a == null || a.isEmpty()) {
                return "unknown"
            }
            return Arrays.toString(a.toTypedArray())
        }

        fun ramLabel(): String {
            val gb = ramTotalMb / 1024
            return if (gb >= 1 && ramTotalMb % 1024 < 96) {
                "$gb GB"
            } else {
                "$ramTotalMb MB"
            }
        }

        fun storageLabel(): String {
            val gb = storageFreeMb / 1024.0
            return if (gb >= 10) {
                String.format("%.1f GB", gb)
            } else {
                String.format("%.0f MB", storageFreeMb.toDouble())
            }
        }
    }

    @JvmStatic
    fun collect(context: Context): Profile {
        val p = Profile()
        p.manufacturer = Build.MANUFACTURER
        p.model = Build.MODEL
        p.sdkInt = Build.VERSION.SDK_INT
        p.androidVersion = Build.VERSION.RELEASE
        val supportedAbis: List<String> = if (Build.SUPPORTED_ABIS == null) {
            Arrays.asList("arm64-v8a")
        } else {
            Arrays.asList(*Build.SUPPORTED_ABIS)
        }
        p.abis = supportedAbis
        p.cores = Runtime.getRuntime().availableProcessors()
        p.hasNeon = supportedAbis.contains("arm64-v8a") || supportedAbis.contains("armeabi-v7a")

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        if (am != null) {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            p.ramTotalMb = info.totalMem / (1024 * 1024)
            p.ramAvailMb = info.availMem / (1024 * 1024)
            p.memoryClassMb = am.memoryClass
        }

        val data = context.filesDir
        if (data != null) {
            try {
                val fs = StatFs(data.absolutePath)
                p.storageFreeMb = (fs.availableBytes + fs.freeBytes) / 2 / (1024 * 1024)
            } catch (ignored: RuntimeException) {
                p.storageFreeMb = 0
            }
        }

        try {
            val pages = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
            p.pageSizeKb = (pages / 1024).toInt()
        } catch (t: Throwable) {
            p.pageSizeKb = 0
        }

        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
            if (bm != null) {
                p.batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                p.batteryPercent = -1
            }
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                p.charging = plugged != 0
            }
        } catch (t: Throwable) {
            p.batteryPercent = -1
        }

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
            if (pm != null && Build.VERSION.SDK_INT >= 29) {
                p.thermalStatusCode = pm.currentThermalStatus
            } else {
                p.thermalStatusCode = -1
            }
        } catch (t: Throwable) {
            p.thermalStatusCode = -1
        }
        return p
    }
}
