package com.ecut.campus.data;

import java.util.List;

/** 某一周的「谁有课」矩阵，以及多人共同空闲。 */
public final class FreeTime {

    private FreeTime() {}

    /** busy[day-1][section-1]：该周该节是否有课。 */
    public static boolean[][] busy(Timetable t, int week, int sections) {
        boolean[][] b = new boolean[7][sections];
        if (t == null) return b;
        for (Timetable.Course c : t.courses) {
            for (Timetable.Session s : c.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > 7 || !s.isActive(week)) continue;
                for (int sec = Math.max(1, s.startSection); sec <= Math.min(sections, s.endSection); sec++) {
                    b[s.dayOfWeek - 1][sec - 1] = true;
                }
            }
        }
        return b;
    }

    /** 所有人都没课的格子。 */
    public static boolean[][] commonFree(List<Timetable> people, int week, int sections) {
        boolean[][] free = new boolean[7][sections];
        for (int d = 0; d < 7; d++) for (int s = 0; s < sections; s++) free[d][s] = true;
        for (Timetable t : people) {
            boolean[][] b = busy(t, week, sections);
            for (int d = 0; d < 7; d++) for (int s = 0; s < sections; s++) if (b[d][s]) free[d][s] = false;
        }
        return free;
    }
}
