package com.ecut.campus;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * 小组件兜底刷新：约每 15 分钟一次（系统允许的最短间隔），不联网、只重画一下。
 * 有的手机（vivo / OPPO / 小米等）会把烫梨的上下课闹钟压住或丢掉，
 * 这时课上完了卡片还停在那节；有这个兜底，最多十几分钟就会自己翻过去。
 * 桌面上没放小组件时自己取消。
 */
public class WidgetRefreshWorker extends Worker {
    private static final String NAME = "widget_refresh";

    public WidgetRefreshWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    static void schedule(Context c) {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    WidgetRefreshWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (Throwable ignored) {
        }
    }

    static void cancel(Context c) {
        try {
            WorkManager.getInstance(c).cancelUniqueWork(NAME);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (ClassWidget.placedCount(c) == 0) {
            cancel(c);
            return Result.success();
        }
        try {
            ClassWidget.updateAll(c);
        } catch (Throwable ignored) {
        }
        return Result.success();
    }
}
