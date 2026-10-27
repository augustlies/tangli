package com.ecut.campus.fun;

import android.content.Context;
import android.content.SharedPreferences;

import com.ecut.campus.data.AbsenceBook;
import com.ecut.campus.data.ClassPrice;
import com.ecut.campus.data.Timetable;

import java.util.Set;

/**
 * 「小玩意」的本地设置与私人记录，单独一个 prefs 文件，不往 Prefs 里堆：
 * 翻牌批语开关、每学期学费、各学期的缺勤记录（按学期 id 分开）。全部只在本机。
 */
public final class FunPrefs {
    private FunPrefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("fun", Context.MODE_PRIVATE);
    }

    public static boolean verdictOn(Context c) {
        return sp(c).getBoolean("grade_verdict", true);
    }

    public static void setVerdictOn(Context c, boolean on) {
        sp(c).edit().putBoolean("grade_verdict", on).apply();
    }

    /** 用户填的每学期学费原文；空 = 不显示「这节课值多少钱」。 */
    public static String tuitionRaw(Context c) {
        return sp(c).getString("tuition", "");
    }

    public static void setTuitionRaw(Context c, String raw) {
        sp(c).edit().putString("tuition", raw == null ? "" : raw.trim()).apply();
    }

    public static double tuition(Context c) {
        return ClassPrice.parseTuition(tuitionRaw(c));
    }

    private static String absenceKey(Timetable t) {
        String id = t != null && t.semester != null && t.semester.id != null ? t.semester.id : "";
        return "absence_" + id;
    }

    public static AbsenceBook absence(Context c, Timetable t) {
        return AbsenceBook.fromJson(sp(c).getString(absenceKey(t), ""));
    }

    public static void saveAbsence(Context c, Timetable t, AbsenceBook b) {
        sp(c).edit().putString(absenceKey(t), b.toJson()).apply();
    }

    /** 课程编辑页改了课名：缺勤计数跟着搬过去。t 是改名后的课表。 */
    public static void renameAbsence(Context c, Timetable t, String oldName, String newName) {
        AbsenceBook b = absence(c, t);
        if (b.rename(oldName, newName, t)) saveAbsence(c, t, b);
    }

    /** 课表上要描朱砂边的课（规范化课名）。 */
    public static Set<String> absenceWarnings(Context c, Timetable t, int totalWeeks) {
        return absence(c, t).warnKeys(t, totalWeeks);
    }
}
