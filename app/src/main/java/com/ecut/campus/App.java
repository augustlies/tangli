package com.ecut.campus;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyNightMode(Prefs.nightMode(this));
        G.init(this);
        // 冷启动只做必须的事：规则预解析、后台任务登记都放到低优先级线程，
        // 并让出前 1.2 秒给首屏，避免和课表绘制抢 CPU。
        Thread warm = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                Thread.sleep(DnsFilterService.isRunning ? 0 : 1200);
            } catch (InterruptedException ignored) {
            }
            try {
                GradeCheckWorker.schedule(this);
            } catch (Throwable ignored) {
            }
            // 规则 10 万+ 条，提前在后台解析，避免拦截服务启动时卡主线程
            DomainRules.get(this);
        }, "startup-warm");
        warm.start();
    }

    /** 0 跟随系统，1 浅色，2 深色。 */
    public static void applyNightMode(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
