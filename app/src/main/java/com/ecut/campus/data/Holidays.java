package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置法定节假日与调休（国务院办公厅公布）。
 * 补课日上哪天的课按国务院调休对应关系推定；学校另有通知时用「烫梨指令」覆盖。
 * 2027 年安排公布后再补。
 */
public final class Holidays {

    private Holidays() {}

    public static final class Day {
        public final String date, label, fromDate; // fromDate 仅补课日
        public final boolean off;

        Day(String date, String label, boolean off, String fromDate) {
            this.date = date; this.label = label; this.off = off; this.fromDate = fromDate;
        }
    }

    public static final List<Day> DAYS = new ArrayList<>();

    static {
        off("2026-09-25", "2026-09-27", "中秋");
        off("2026-10-01", "2026-10-07", "国庆");
        DAYS.add(new Day("2026-09-20", "国庆调休", false, "2026-10-06"));
        DAYS.add(new Day("2026-10-10", "国庆调休", false, "2026-10-07"));
        off("2027-01-01", "2027-01-01", "元旦");
    }

    private static void off(String from, String to, String label) {
        for (String d = from; d.compareTo(to) <= 0; d = Dates.addDays(d, 1)) {
            DAYS.add(new Day(d, label, true, ""));
        }
    }

    /** 内置 + 课表里的用户安排（用户的优先）。返回 null 表示普通日子。 */
    public static Day lookup(Timetable t, String ymd) {
        if (ymd == null || ymd.isEmpty()) return null;
        if (t != null) {
            for (int i = t.adjusts.size() - 1; i >= 0; i--) {
                Timetable.Adjust a = t.adjusts.get(i);
                if (!ymd.equals(a.date)) continue;
                if (Timetable.Adjust.OFF.equals(a.type)) {
                    return new Day(ymd, a.note.isEmpty() ? "放假" : a.note, true, "");
                }
                if (Timetable.Adjust.MAKEUP.equals(a.type)) {
                    return new Day(ymd, a.note.isEmpty() ? "补课" : a.note, false, a.fromDate);
                }
            }
        }
        for (Day d : DAYS) if (d.date.equals(ymd)) return d;
        return null;
    }
}
