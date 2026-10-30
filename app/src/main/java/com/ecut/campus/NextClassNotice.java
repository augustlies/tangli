package com.ecut.campus;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ecut.campus.data.DayPlan;
import com.ecut.campus.data.Dates;
import com.ecut.campus.data.NextClassText;
import com.ecut.campus.data.WidgetDay;

import java.util.List;

/**
 * 通知栏常驻「下一节课」（默认关）：一条低优先级、静音、不能滑掉的通知，写着正在上 / 下一节 / 明天第一节的课名、时间、教室。
 * 不用前台服务：只在上课、下课、上课前 60 分钟、零点这些时刻用不精确闹钟（RTC，不唤醒）刷新一次，
 * 另外开机、改时间、升级、课表/作息变了（ClassWidget.updateAll 会顺手调这里）也刷新。倒计时由系统 Chronometer 自己走。
 */
public final class NextClassNotice {
    private NextClassNotice() {}

    static final String CH = "next_class";
    static final int NOTIF_ID = 2102;
    private static final int REQ_TICK = 62, REQ_OPEN = 10;
    static final String ACTION_TICK = "com.ecut.campus.NEXT_CLASS_TICK";

    /** 算一遍要显示什么、发/撤通知、排下一次刷新。任何一步出错都不往外抛。 */
    public static void update(Context c) {
        try {
            if (!Prefs.nextClassNotice(c)) {
                cancel(c);
                return;
            }
            long now = System.currentTimeMillis();
            NextClassText.Notice n = notice(c, now);
            if (n.kind == NextClassText.NONE || !ClassReminder.canNotify(c)) {
                NotificationManagerCompat.from(c).cancel(NOTIF_ID);
            } else {
                post(c, n, now);
            }
            schedule(c, now);
        } catch (Throwable ignored) {
        }
    }

    /** 关掉开关：撤通知、取消闹钟。 */
    static void cancel(Context c) {
        try {
            NotificationManagerCompat.from(c).cancel(NOTIF_ID);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tick(c));
        } catch (Throwable ignored) {
        }
    }

    static NextClassText.Notice notice(Context c, long now) {
        ClassPlans.Ctx x = ClassPlans.load(c);
        String ymd = ClassPlans.ymd(now);
        if (!semesterOk(x, ymd)) return new NextClassText.Notice();
        List<DayPlan.Item> today = ClassPlans.day(x, ymd);
        String tmr = Dates.addDays(ymd, 1);
        List<DayPlan.Item> tomorrow = semesterOk(x, tmr) ? ClassPlans.day(x, tmr) : null;
        return NextClassText.build(today, tomorrow, ClassPlans.nowMinutes(now));
    }

    private static boolean semesterOk(ClassPlans.Ctx x, String ymd) {
        if (x.t == null || x.t.courses.isEmpty() || !Dates.valid(x.start)) return false;
        int week = Dates.weekOf(x.start, ymd);
        return week >= 1 && (x.totalWeeks <= 0 || week <= x.totalWeeks);
    }

    private static void post(Context c, NextClassText.Notice n, long now) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CH, "下一节课", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("通知栏常驻显示下一节课（静音，不弹出）");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AppActions.EXTRA_ACTION, "open")
                .putExtra(AppActions.EXTRA_VALUE, "timetable");
        PendingIntent pi = PendingIntent.getActivity(c, REQ_OPEN, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_nav_timetable)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(n.text))
                .setColor(0xFFFF4A1C)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setContentIntent(pi);
        if (n.countdownMin >= 0) {
            long target = ClassPlans.at(ClassPlans.midnight(now), n.countdownMin);
            if (target > now) {
                // 「还剩 mm:ss」/「mm:ss 后上课」由系统倒计时控件走秒，到点闹钟再翻页
                b.setWhen(target).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
                b.setSubText(n.kind == NextClassText.CURRENT ? "下课倒计时" : "上课倒计时");
            } else {
                b.setShowWhen(false);
            }
        } else {
            b.setShowWhen(false);
        }
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }

    /** 下一次刷新：今天最近的上课 / 下课 / 上课前 60 分钟，没有就明早 0:01。窗口 1 分钟（不精确就够）。 */
    private static void schedule(Context c, long now) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long today = ClassPlans.midnight(now);
        long next = ClassPlans.addDays(today, 1) + 60_000L;
        try {
            ClassPlans.Ctx x = ClassPlans.load(c);
            int b = WidgetDay.nextRefreshMin(ClassPlans.day(x, ClassPlans.ymd(now)), ClassPlans.nowMinutes(now));
            if (b >= 0) next = Math.min(next, ClassPlans.at(today, b));
        } catch (Exception ignored) {
        }
        am.setWindow(AlarmManager.RTC_WAKEUP, Math.max(now + 30_000L, next + 2_000L), 60_000L, tick(c));
    }

    private static PendingIntent tick(Context c) {
        Intent i = new Intent(c, Receiver.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, REQ_TICK, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 自己的刷新闹钟 + 开机 / 改时间 / 换时区 / 跨天 / 升级。开关关着时只是空跑一下。 */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            // 顺带刷新桌面小组件（updateAll 里会先刷通知）：两边互为备份，开机 / 改时间 / 跨天 / 升级后小组件也跟着翻页
            ClassWidget.updateAll(c);
            if (ClassWidget.placedCount(c) > 0) WidgetRefreshWorker.schedule(c);
        }
    }
}
