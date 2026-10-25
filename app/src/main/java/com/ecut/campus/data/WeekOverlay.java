package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 某一周的「临时变化」：哪些课这周不上（放假/停课/已调走），哪些课是额外加进来的（补课/调来）。
 * 课程本身不动，TimetableView 按这个叠加绘制。
 */
public final class WeekOverlay {

    public static final class Mark {
        public final String label;     // 放假 / 停课 / 已调走
        public final String adjustId;  // 可撤销的用户安排；内置节假日为空

        Mark(String label, String adjustId) { this.label = label; this.adjustId = adjustId; }
    }

    public static final class Extra {
        public final Timetable.Course course;
        public final Timetable.Session session; // 新造的时段，只在本周显示
        public final String label;              // 补课 / 调课
        public final String adjustId;

        Extra(Timetable.Course c, Timetable.Session s, String label, String adjustId) {
            course = c; session = s; this.label = label; this.adjustId = adjustId;
        }
    }

    public final int week;
    public final Map<Timetable.Session, Mark> cancelled = new IdentityHashMap<>();
    public final List<Extra> extras = new ArrayList<>();
    /** 星期 → 当天标签（「国庆」「补 10/7 周三」），表头用 */
    public final String[] dayLabels = new String[8];
    public final boolean[] dayOff = new boolean[8];

    private WeekOverlay(int week) { this.week = week; }

    public static WeekOverlay empty(int week) { return new WeekOverlay(week); }

    public boolean isEmpty() {
        if (!cancelled.isEmpty() || !extras.isEmpty()) return false;
        for (int d = 1; d <= 7; d++) if (dayLabels[d] != null) return false;
        return true;
    }

    public static WeekOverlay build(Timetable t, String start, int week) {
        WeekOverlay o = new WeekOverlay(week);
        if (t == null) return o;
        boolean hasStart = Dates.valid(start);

        // 1. 放假 / 补课日
        if (hasStart) {
            for (int d = 1; d <= 7; d++) {
                String ymd = Dates.dateOf(start, week, d);
                Holidays.Day h = Holidays.lookup(t, ymd);
                if (h == null) continue;
                if (h.off) {
                    o.dayOff[d] = true;
                    o.dayLabels[d] = h.label;
                    String offId = userAdjustId(t, ymd);
                    for (Timetable.Course c : t.courses) {
                        for (Timetable.Session s : c.sessions) {
                            if (s.dayOfWeek == d && s.isActive(week)) o.cancelled.put(s, new Mark("放假", offId));
                        }
                    }
                } else if (Dates.valid(h.fromDate)) {
                    int fw = Dates.weekOf(start, h.fromDate);
                    int fd = Dates.dayOfWeek(h.fromDate);
                    o.dayLabels[d] = "补" + Dates.cnDay(fd);
                    String aid = userAdjustId(t, ymd);
                    for (Timetable.Course c : t.courses) {
                        for (Timetable.Session s : c.sessions) {
                            if (s.dayOfWeek != fd || !s.isActive(fw)) continue;
                            if (findCancel(t, c, s, fw) != null) continue;
                            Timetable.Session n = s.copy();
                            n.dayOfWeek = d;
                            n.weeks.clear();
                            n.weeks.add(week);
                            n.weeksRaw = week + "周";
                            String wt = s.weekTeachers.get(fw);
                            n.weekTeachers.clear();
                            if (wt != null) n.weekTeachers.put(week, wt);
                            o.extras.add(new Extra(c, n, "补课", aid));
                        }
                    }
                }
            }
        }

        // 2. 停课 / 调课
        for (Timetable.Adjust a : t.adjusts) {
            boolean cancel = Timetable.Adjust.CANCEL.equals(a.type);
            boolean move = Timetable.Adjust.MOVE.equals(a.type);
            if (!cancel && !move) continue;
            if (a.week == week) {
                for (Timetable.Course c : t.courses) {
                    if (!c.name.equals(a.name)) continue;
                    for (Timetable.Session s : c.sessions) {
                        if (matches(a, s, week)) {
                            o.cancelled.put(s, new Mark(cancel ? "停课" : "已调走", a.id));
                        }
                    }
                }
            }
            if (move && targetWeek(a) == week) {
                for (Timetable.Course c : t.courses) {
                    if (!c.name.equals(a.name)) continue;
                    for (Timetable.Session s : c.sessions) {
                        if (!matches(a, s, a.week)) continue;
                        Timetable.Session n = s.copy();
                        n.dayOfWeek = a.toDay > 0 ? a.toDay : s.dayOfWeek;
                        if (a.hasToTime()) {
                            n.timeStart = a.toTimeStart;
                            n.timeEnd = a.toTimeEnd;
                        } else if (a.toStart > 0) {
                            int len = s.endSection - s.startSection;
                            n.startSection = a.toStart;
                            n.endSection = a.toEnd >= a.toStart ? a.toEnd : a.toStart + len;
                            n.timeStart = "";
                            n.timeEnd = "";
                        }
                        if (!a.toLocation.isEmpty()) n.location = a.toLocation;
                        n.weeks.clear();
                        n.weeks.add(week);
                        n.weeksRaw = week + "周";
                        String wt = s.weekTeachers.get(a.week);
                        n.weekTeachers.clear();
                        if (wt != null) n.weekTeachers.put(week, wt);
                        o.extras.add(new Extra(c, n, "调课", a.id));
                        break; // 一条调课只搬一个时段
                    }
                }
            }
        }
        return o;
    }

    public static int targetWeek(Timetable.Adjust a) {
        return a.toWeek > 0 ? a.toWeek : a.week;
    }

    /** 调整是否命中这个时段（这周确实有这节课，且星期/节次对得上）。 */
    public static boolean matches(Timetable.Adjust a, Timetable.Session s, int week) {
        if (!s.isActive(week)) return false;
        if (a.day > 0 && s.dayOfWeek != a.day) return false;
        if (a.start > 0) {
            int end = a.end >= a.start ? a.end : a.start;
            if (s.endSection < a.start || s.startSection > end) return false;
        }
        return true;
    }

    private static Timetable.Adjust findCancel(Timetable t, Timetable.Course c, Timetable.Session s, int week) {
        for (Timetable.Adjust a : t.adjusts) {
            if ((Timetable.Adjust.CANCEL.equals(a.type) || Timetable.Adjust.MOVE.equals(a.type))
                    && a.week == week && c.name.equals(a.name) && matches(a, s, week)) return a;
        }
        return null;
    }

    private static String userAdjustId(Timetable t, String ymd) {
        for (Timetable.Adjust a : t.adjusts) if (ymd.equals(a.date)) return a.id;
        return "";
    }
}
