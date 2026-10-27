package com.ecut.campus.data;

/** 课表统计的小工具（纯 Java）。 */
public final class TimetableStats {
    private TimetableStats() {}

    /** 课程时段最集中的一个周（并列取更小的），没有课时返回 1。 */
    public static int busiestWeek(Timetable t) {
        if (t == null || t.courses == null) return 1;
        int best = 1, bestCount = -1;
        for (int week = 1; week <= 30; week++) {
            int count = 0;
            for (Timetable.Course c : t.courses) {
                for (Timetable.Session s : c.sessions) if (s.isActive(week)) count++;
            }
            if (count > bestCount) { bestCount = count; best = week; }
        }
        return bestCount <= 0 ? 1 : best;
    }
}
