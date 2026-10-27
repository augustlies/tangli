package com.ecut.campus;

import android.content.Context;

import com.ecut.campus.data.DayPlan;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 读本地课表 + 作息 + 开学日，给小组件和课前提醒算「某天上哪些课」。 */
final class ClassPlans {
    private ClassPlans() {}

    static final class Ctx {
        Timetable t;
        String start = "";
        int[] secStart, secEnd;
        int totalWeeks;
    }

    static Ctx load(Context c) {
        Ctx x = new Ctx();
        x.t = TimetableStore.load(c);
        String id = x.t != null && x.t.semester != null ? x.t.semester.id : "";
        x.start = WeekMath.effectiveStart(Prefs.semesterStartDate(c), id, Schools.current(c).isEcut());
        ScheduleSettings ss = ScheduleSettingsStore.load(c);
        int n = ScheduleSettings.MAX_SECTIONS;
        x.secStart = new int[n + 1];
        x.secEnd = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            x.secStart[i] = ss.startMinutes(i);
            x.secEnd[i] = ss.endMinutes(i);
        }
        x.totalWeeks = Prefs.totalWeeks(c);
        return x;
    }

    static List<DayPlan.Item> day(Ctx x, String ymd) {
        return DayPlan.of(x.t, x.start, ymd, x.secStart, x.secEnd);
    }

    static String ymd(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    /** 某天 0 点的毫秒数（本地时区）。 */
    static long midnight(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static long at(long dayMidnight, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayMidnight);
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60);
        cal.set(Calendar.MINUTE, minutes % 60);
        return cal.getTimeInMillis();
    }

    static long addDays(long dayMidnight, int n) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayMidnight);
        cal.add(Calendar.DAY_OF_MONTH, n);
        return cal.getTimeInMillis();
    }

    static int nowMinutes(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /** 「明天」「周三」「10/28 周三」 */
    static String dayName(long todayMid, long dayMid, String ymd) {
        int diff = (int) Math.round((dayMid - todayMid) / 86_400_000.0);
        String wd = com.ecut.campus.data.Dates.cnDay(com.ecut.campus.data.Dates.dayOfWeek(ymd));
        if (diff == 0) return "今天";
        if (diff == 1) return "明天";
        if (diff < 7) return wd;
        return com.ecut.campus.data.Dates.md(ymd) + " " + wd;
    }
}
