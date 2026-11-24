package com.ecut.campus;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ecut.campus.data.DayPlan;
import com.ecut.campus.data.Dates;
import com.ecut.campus.data.ReminderSchedule;

import java.util.List;

/**
 * 课前提醒（默认关）：每次只排下一节课的一个闹钟，响了发通知再排下一个。
 * 用不精确闹钟（不申请精确闹钟权限），省电模式下可能晚一两分钟；通知里的倒计时按真实上课时间走。
 */
public final class ClassReminder {
    private ClassReminder() {}

    static final String CH = "class_remind";
    private static final int NOTIF_ID = 2101;
    private static final int REQ = 61;
    private static final String EX_START = "start";
    private static final String EX_END = "end";
    private static final String EX_TITLE = "title";
    private static final String EX_TEXT = "text";
    private static final String EX_MULTI = "multiple";
    private static final String EX_RECHECK = "recheck";
    private static final String EX_FIRE_AT = "fire_at";
    static final int LOOKAHEAD_DAYS = ReminderSchedule.LOOKAHEAD_DAYS;

    /** 打开/关闭、改提前分钟、课表或作息变了、开机、改时间后都调一次。 */
    public static void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent old = pending(c, null);
        am.cancel(old);
        if (!Prefs.classRemind(c)) return;
        long now = System.currentTimeMillis();
        Intent next = findNext(c, now);
        if (next == null) return;
        boolean recheck = next.getBooleanExtra(EX_RECHECK, false);
        long fire = recheck ? next.getLongExtra(EX_FIRE_AT, 0)
                : next.getLongExtra(EX_START, 0) - Prefs.classRemindLead(c) * 60_000L;
        fire = Math.max(fire, now + 1000);
        PendingIntent pi = pending(c, next);
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, pi);
        else am.set(AlarmManager.RTC_WAKEUP, fire, pi);
    }

    /** 往后 8 天里第一节「提醒时间还没过」的课。 */
    static Intent findNext(Context c, long now) {
        ClassPlans.Ctx x = ClassPlans.load(c);
        long lead = Prefs.classRemindLead(c) * 60_000L;
        long today = ClassPlans.midnight(now);
        for (int d = 0; d < LOOKAHEAD_DAYS; d++) {
            long mid = ClassPlans.addDays(today, d);
            String ymd = ClassPlans.ymd(mid);
            List<DayPlan.Item> items = ClassPlans.day(x, ymd);
            for (DayPlan.Item it : items) {
                long start = ClassPlans.at(mid, it.startMin);
                if (start - lead <= now) continue;
                List<DayPlan.Item> simultaneous = ReminderSchedule.sameStart(items, it.startMin);
                return alarmIntent(c, mid, start, simultaneous);
            }
        }
        if (ReminderSchedule.hasPotentialFutureClass(x.t, x.start,
                Dates.addDays(ClassPlans.ymd(today), LOOKAHEAD_DAYS))) {
            Intent retry = new Intent(c, Receiver.class);
            retry.putExtra(EX_RECHECK, true);
            retry.putExtra(EX_FIRE_AT, ReminderSchedule.nextSearchAt(today));
            return retry;
        }
        return null;
    }

    private static Intent alarmIntent(Context c, long midnight, long start, List<DayPlan.Item> items) {
        Intent i = new Intent(c, Receiver.class);
        long end = start;
        String time = com.ecut.campus.data.Timetable.Session.formatHm(items.get(0).startMin);
        StringBuilder names = new StringBuilder();
        StringBuilder details = new StringBuilder();
        for (DayPlan.Item item : items) {
            if (names.length() > 0) names.append("、");
            names.append(item.name);
            end = Math.max(end, ClassPlans.at(midnight, item.endMin));
            if (details.length() > 0) details.append("；");
            details.append(item.name);
            if (!item.sections.isEmpty()) details.append(" ").append(item.sections);
            if (!item.location.isEmpty()) details.append(" · ").append(item.location);
            if (!item.tag.isEmpty()) details.append(" · ").append(item.tag);
        }
        if (items.size() == 1) {
            DayPlan.Item item = items.get(0);
            details.setLength(0);
            if (!item.location.isEmpty()) details.append(item.location);
            if (!item.sections.isEmpty()) details.append(details.length() > 0 ? " · " : "").append(item.sections);
            if (!item.tag.isEmpty()) details.append(details.length() > 0 ? " · " : "").append(item.tag);
        }
        i.putExtra(EX_START, start);
        i.putExtra(EX_END, end);
        i.putExtra(EX_TITLE, time + "  " + names);
        i.putExtra(EX_TEXT, details.toString());
        i.putExtra(EX_MULTI, items.size() > 1);
        return i;
    }

    private static PendingIntent pending(Context c, Intent extras) {
        Intent i = extras != null ? extras : new Intent(c, Receiver.class);
        return PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    static void post(Context c, Intent data) {
        if (!Prefs.classRemind(c) || !canNotify(c)) return;
        long start = data.getLongExtra(EX_START, 0), end = data.getLongExtra(EX_END, 0);
        long now = System.currentTimeMillis();
        if (start <= 0 || now > end) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CH, "上课提醒", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("上课前提醒课程和教室");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 9, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = data.getStringExtra(EX_TEXT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_nav_timetable)
                .setContentTitle(data.getStringExtra(EX_TITLE))
                .setContentText(text == null || text.isEmpty() ? "快上课了" : text)
                .setColor(0xFFFF4A1C)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setTimeoutAfter(Math.max(60_000L, end - now));
        if (data.getBooleanExtra(EX_MULTI, false)) {
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        }
        if (start > now) {
            // 通知里显示「还有 xx:xx」实时倒计时，到点后自动变成正计时
            b.setWhen(start).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
        }
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }

    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            String a = intent.getAction();
            if (a == null && !intent.getBooleanExtra(EX_RECHECK, false)) post(c, intent); // 自己排的闹钟
            reschedule(c);
            ClassWidget.updateAll(c);
        }
    }
}
