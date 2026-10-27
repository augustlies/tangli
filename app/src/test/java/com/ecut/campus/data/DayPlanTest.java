package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class DayPlanTest {

    private static final int[] S = new int[17], E = new int[17];
    static {
        for (int i = 1; i <= 16; i++) { S[i] = 8 * 60 + (i - 1) * 55; E[i] = S[i] + 45; }
    }

    private static Timetable.Session ses(int day, int a, int b, String loc, int... weeks) {
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = day; s.startSection = a; s.endSection = b; s.location = loc;
        s.weeks = TimetableShare.list(weeks);
        return s;
    }

    private static Timetable sample() {
        Timetable t = new Timetable();
        t.semester.startDate = "2026-09-07";
        Timetable.Course a = new Timetable.Course();
        a.name = "构造地质学";
        a.sessions.add(ses(2, 3, 4, "A201", 1, 2, 3));
        Timetable.Course b = new Timetable.Course();
        b.name = "大学物理";
        b.sessions.add(ses(2, 1, 2, "B105", 1, 2, 3));
        Timetable.Session exam = ses(2, 0, 0, "3-402", 2);
        exam.timeStart = "19:00"; exam.timeEnd = "21:00";
        b.sessions.add(exam);
        t.courses.add(a);
        t.courses.add(b);
        return t;
    }

    @Test
    public void sortsByTimeAndUsesCustomTimes() {
        // 2026-09-15 是第 2 周周二
        List<DayPlan.Item> l = DayPlan.of(sample(), "2026-09-07", "2026-09-15", S, E);
        assertEquals(3, l.size());
        assertEquals("大学物理", l.get(0).name);
        assertEquals("1-2节", l.get(0).sections);
        assertEquals("构造地质学", l.get(1).name);
        assertEquals(19 * 60, l.get(2).startMin);
        assertEquals("", l.get(2).sections);
        assertEquals("19:00-21:00", l.get(2).timeLabel());
    }

    @Test
    public void respectsWeeksAndCancel() {
        Timetable t = sample();
        Timetable.Adjust c = new Timetable.Adjust();
        c.type = Timetable.Adjust.CANCEL; c.name = "构造地质学"; c.week = 3; c.day = 2; c.start = 3; c.end = 4;
        t.adjusts.add(c);
        List<DayPlan.Item> l = DayPlan.of(t, "2026-09-07", "2026-09-22", S, E); // 第 3 周周二
        assertEquals(1, l.size());
        assertEquals("大学物理", l.get(0).name);
        assertTrue(DayPlan.of(t, "2026-09-07", "2026-09-29", S, E).isEmpty()); // 第 4 周没课
    }

    @Test
    public void movedClassShowsOnNewDay() {
        Timetable t = sample();
        Timetable.Adjust m = new Timetable.Adjust();
        m.type = Timetable.Adjust.MOVE; m.name = "构造地质学"; m.week = 1; m.day = 2; m.start = 3; m.end = 4;
        m.toWeek = 1; m.toDay = 4; m.toStart = 5; m.toEnd = 6; m.toLocation = "C301";
        t.adjusts.add(m);
        List<DayPlan.Item> thu = DayPlan.of(t, "2026-09-07", "2026-09-10", S, E);
        assertEquals(1, thu.size());
        assertEquals("C301", thu.get(0).location);
        assertEquals("调课", thu.get(0).tag);
        assertEquals(S[5], thu.get(0).startMin);
    }

    @Test
    public void currentNextAndDone() {
        List<DayPlan.Item> l = DayPlan.of(sample(), "2026-09-07", "2026-09-15", S, E);
        assertEquals("大学物理", DayPlan.current(l, 8 * 60 + 10).name);
        assertEquals("构造地质学", DayPlan.next(l, 8 * 60 + 10).name);
        assertNull(DayPlan.current(l, 12 * 60));
        assertFalse(DayPlan.doneFor(l, 20 * 60));
        assertTrue(DayPlan.doneFor(l, 21 * 60));
        assertTrue(DayPlan.of(sample(), "", "2026-09-15", S, E).isEmpty());
        assertTrue(DayPlan.of(sample(), "2026-09-07", "2026-09-01", S, E).isEmpty()); // 开学前
    }
}
