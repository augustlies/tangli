package com.ecut.campus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

/**
 * 小组件 / 通知栏「下一节课」的上下课闹钟。
 * 以前用 setWindow：手机放着不动进入省电休眠（Doze）后，这种闹钟会被系统一直往后压，
 * 直到亮屏甚至打开 App 才响，表现就是「课上完了小组件还停在那节，点进去才刷新」。
 * 现在用 AllowWhileIdle：休眠中也照样叫醒；系统允许精确闹钟时按点叫，不允许就尽量准时。
 */
final class Wake {
    private Wake() {
    }

    static void at(Context c, long when, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            // 个别系统收回了精确闹钟权限：退回到不精确但休眠中也能响的
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}
