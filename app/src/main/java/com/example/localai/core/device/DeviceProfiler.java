package com.example.localai.core.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.StatFs;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 设备画像采集（P4，真实取值，无个人标识：不含序列号/IMEI/账号）。
 * 热状态与电量仅做系统级信号；页大小经 libcore Os.sysconf 读取，读取失败记录未知。
 * 真机与模拟器共用同一条采集路径（S042 复用本接口校准）。
 */
public final class DeviceProfiler {

    /** 采集结果快照（字段对齐 S023 设备画像）。 */
    public static final class Profile {
        public String manufacturer;
        public String model;
        public int sdkInt;
        public String androidVersion;
        public List<String> abis;
        public int cores;
        public long ramTotalMb;
        public long ramAvailMb;
        public int memoryClassMb;
        public long storageFreeMb;
        public int pageSizeKb;          // 0 = 未知
        public int batteryPercent;      // -1 = 未知
        public boolean charging;
        public int thermalStatusCode;   // -1 = 未知/不可用
        public boolean hasNeon;

        public String modelLabel() {
            if (manufacturer == null || manufacturer.trim().isEmpty()) {
                return model == null ? "未知设备" : model;
            }
            return manufacturer + " " + model;
        }

        public String abiLabel() {
            if (abis == null || abis.isEmpty()) {
                return "unknown";
            }
            return Arrays.toString(abis.toArray(new String[0]));
        }

        public String ramLabel() {
            long gb = ramTotalMb / 1024;
            if (gb >= 1 && ramTotalMb % 1024 < 96) {
                return gb + " GB";
            }
            return ramTotalMb + " MB";
        }

        public String storageLabel() {
            double gb = storageFreeMb / 1024.0;
            if (gb >= 10) {
                return String.format("%.1f GB", gb);
            }
            return String.format("%.0f MB", (double) storageFreeMb);
        }
    }

    private DeviceProfiler() {
    }

    public static Profile collect(Context context) {
        Profile p = new Profile();
        p.manufacturer = Build.MANUFACTURER;
        p.model = Build.MODEL;
        p.sdkInt = Build.VERSION.SDK_INT;
        p.androidVersion = Build.VERSION.RELEASE;
        p.abis = Build.SUPPORTED_ABIS == null ? Arrays.asList("arm64-v8a") : Arrays.asList(Build.SUPPORTED_ABIS);
        p.cores = Runtime.getRuntime().availableProcessors();
        p.hasNeon = p.abis.contains("arm64-v8a") || p.abis.contains("armeabi-v7a");

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            p.ramTotalMb = info.totalMem / (1024 * 1024);
            p.ramAvailMb = info.availMem / (1024 * 1024);
            p.memoryClassMb = am.getMemoryClass();
        }

        File data = context.getFilesDir();
        if (data != null) {
            try {
                StatFs fs = new StatFs(data.getAbsolutePath());
                p.storageFreeMb = (fs.getAvailableBytes() + fs.getFreeBytes()) / 2 / (1024 * 1024);
            } catch (RuntimeException ignored) {
                p.storageFreeMb = 0;
            }
        }

        try {
            long pages = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
            p.pageSizeKb = (int) (pages / 1024);
        } catch (Throwable t) {
            p.pageSizeKb = 0;
        }

        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                p.batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } else {
                p.batteryPercent = -1;
            }
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                p.charging = plugged != 0;
            }
        } catch (Throwable t) {
            p.batteryPercent = -1;
        }

        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= 29) {
                p.thermalStatusCode = pm.getCurrentThermalStatus();
            } else {
                p.thermalStatusCode = -1;
            }
        } catch (Throwable t) {
            p.thermalStatusCode = -1;
        }
        return p;
    }
}
