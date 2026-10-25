package com.ecut.campus.data;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** 纯 Java 日期小工具（yyyy-MM-dd），按 UTC 算整天，避免时区/夏令时误差。 */
public final class Dates {

    private Dates() {}

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** yyyy-MM-dd → 自 1970-01-01 的天数；格式不对返回 Long.MIN_VALUE。 */
    public static long epochDay(String ymd) {
        if (ymd == null) return Long.MIN_VALUE;
        String[] p = ymd.trim().split("-");
        if (p.length != 3) return Long.MIN_VALUE;
        try {
            int y = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]), d = Integer.parseInt(p[2]);
            if (m < 1 || m > 12 || d < 1 || d > 31) return Long.MIN_VALUE;
            Calendar c = new GregorianCalendar(UTC);
            c.clear();
            c.set(y, m - 1, d);
            if (c.get(Calendar.DAY_OF_MONTH) != d) return Long.MIN_VALUE;
            return Math.floorDiv(c.getTimeInMillis(), 86_400_000L);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    public static String ofEpochDay(long day) {
        Calendar c = new GregorianCalendar(UTC);
        c.setTimeInMillis(day * 86_400_000L);
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean valid(String ymd) {
        return epochDay(ymd) != Long.MIN_VALUE;
    }

    /** 1=周一 … 7=周日 */
    public static int dayOfWeek(String ymd) {
        long d = epochDay(ymd);
        if (d == Long.MIN_VALUE) return 0;
        // 1970-01-01 是周四
        return (int) Math.floorMod(d + 3, 7L) + 1;
    }

    /** 第 week 周星期 day 的日期；开学日期无效返回空串。 */
    public static String dateOf(String start, int week, int day) {
        long s = epochDay(start);
        if (s == Long.MIN_VALUE || week == 0 || day < 1 || day > 7) return "";
        long monday = s - (dayOfWeek(start) - 1);
        return ofEpochDay(monday + (long) (week - 1) * 7 + (day - 1));
    }

    /** 日期是第几周（开学前 ≤0）；无效返回 0。 */
    public static int weekOf(String start, String ymd) {
        long s = epochDay(start), d = epochDay(ymd);
        if (s == Long.MIN_VALUE || d == Long.MIN_VALUE) return 0;
        long monday = s - (dayOfWeek(start) - 1);
        return (int) Math.floorDiv(d - monday, 7L) + 1;
    }

    public static String addDays(String ymd, int n) {
        long d = epochDay(ymd);
        return d == Long.MIN_VALUE ? "" : ofEpochDay(d + n);
    }

    private static final String[] CN = {"", "一", "二", "三", "四", "五", "六", "日"};

    public static String cnDay(int day) {
        return day >= 1 && day <= 7 ? "周" + CN[day] : "";
    }

    /** 2026-10-10 → 10月10日 */
    public static String md(String ymd) {
        String[] p = ymd == null ? new String[0] : ymd.split("-");
        if (p.length != 3) return ymd == null ? "" : ymd;
        return Integer.parseInt(p[1]) + "月" + Integer.parseInt(p[2]) + "日";
    }
}
