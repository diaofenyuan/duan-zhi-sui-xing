package com.example.localai;

import android.app.Application;
import android.os.Build;

import com.example.localai.data.ServiceLocator;

import java.lang.reflect.Method;

/**
 * 应用入口：初始化组合根（Room/网络/存储/下载协调器）。
 * P3 进程隔离：:inference 进程只承载推理服务，跳过全部网络/数据/UI 初始化，
 * 保证"推理进程不访问网络"的架构边界（网络权限本身是应用级，隔离以代码路径保证）。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (isInferenceProcess()) {
            return;
        }
        ServiceLocator.init(this);
    }

    /** 当前进程是否为独立推理进程。 */
    private static boolean isInferenceProcess() {
        String name = currentProcessName();
        return name != null && name.endsWith(":inference");
    }

    private static String currentProcessName() {
        if (Build.VERSION.SDK_INT >= 28) {
            return Application.getProcessName();
        }
        try {
            Class<?> clazz = Class.forName("android.app.ActivityThread");
            Method method = clazz.getDeclaredMethod("currentProcessName");
            method.setAccessible(true);
            return (String) method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
