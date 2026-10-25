package com.ecut.campus;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ecut.campus.data.Grades;

import java.util.List;

/** 新成绩通知。 */
public final class Notify {
    private Notify() {}

    private static final String CH_GRADES = "grades";

    public static void requestPermissionIfNeeded(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(a,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
    }

    public static void newGrades(Context ctx, List<Grades.CourseGrade> fresh) {
        if (!Prefs.gradeNotify(ctx) || fresh.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CH_GRADES, "新成绩",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < fresh.size() && i < 5; i++) {
            Grades.CourseGrade g = fresh.get(i);
            if (body.length() > 0) body.append("\n");
            body.append(g.name).append("  ").append(g.score);
        }
        if (fresh.size() > 5) body.append("\n…还有 ").append(fresh.size() - 5).append(" 门");
        Intent open = new Intent(ctx, GradesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = fresh.size() == 1 ? "出成绩了：" + fresh.get(0).name : "出了 " + fresh.size() + " 门新成绩";
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_GRADES)
                .setSmallIcon(R.drawable.ic_nav_grades)
                .setContentTitle(title)
                .setContentText(fresh.size() == 1 ? fresh.get(0).score : body.toString().replace("\n", " · "))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(0xFFFF4A1C)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(ctx).notify(2001, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
