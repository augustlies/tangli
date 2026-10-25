package com.ecut.campus;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 后台约每 6 小时用已登录的门户 Cookie 静默同步一次成绩；
 * 新成绩由 CampusSyncClient 里的 GradeDiff 发现并通知。不碰账号密码。
 */
public class GradeCheckWorker extends Worker {
    private static final String NAME = "grade_check";

    public GradeCheckWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    public static void schedule(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx);
        if (!Prefs.gradeNotify(ctx)) {
            wm.cancelUniqueWork(NAME);
            return;
        }
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(GradeCheckWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .build();
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!Prefs.gradeNotify(ctx) || !Prefs.hasPortalSession(ctx) || Prefs.sessionExpired(ctx)
                || Prefs.isGradesDemo(ctx)) {
            return Result.success();
        }
        CountDownLatch done = new CountDownLatch(1);
        final WebView[] holder = new WebView[1];
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            try {
                WebView view = new WebView(ctx);
                holder[0] = view;
                CampusSyncClient.start(view, CampusSyncClient.Kind.GRADES, new CampusSyncClient.Callback() {
                    @Override public void onSuccess(int count) { done.countDown(); }
                    @Override public void onFailure(String message) { done.countDown(); }
                });
            } catch (Throwable t) {
                done.countDown();
            }
        });
        try {
            done.await(90, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return Result.success();
    }
}
