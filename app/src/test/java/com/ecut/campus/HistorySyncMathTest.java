package com.ecut.campus;

import static org.junit.Assert.assertEquals;

import com.ecut.campus.data.Grades;

import org.junit.Test;

public class HistorySyncMathTest {

    private static Grades info() { return new Grades(); }

    @Test
    public void descendingListTakesLastElement() {
        Grades g = info();
        g.semesters.add(sem("2025-2026-1"));
        g.semesters.add(sem("2025-2026-2"));
        g.semesters.add(sem("2024-2025-1"));
        g.semesters.add(sem("2024-2025-2"));
        assertEquals(2024, HistorySync.earliestGradeYear(g));
    }

    @Test
    public void ascendingListTakesLastElementToo() {
        Grades g = info();
        g.semesters.add(sem("2024-2025-1"));
        g.semesters.add(sem("2025-2026-1"));
        assertEquals(2025, HistorySync.earliestGradeYear(g));
    }

    @Test
    public void singleEntryList() {
        Grades g = info();
        g.semesters.add(sem("2026-2027-1"));
        assertEquals(2026, HistorySync.earliestGradeYear(g));
    }

    @Test
    public void emptyOrInvalidReturnsMinusOne() {
        assertEquals(-1, HistorySync.earliestGradeYear(info()));
        assertEquals(-1, HistorySync.earliestGradeYear(null));
        Grades bad = info();
        bad.semesters.add(sem("garbage"));
        assertEquals(-1, HistorySync.earliestGradeYear(bad));
    }

    @Test
    public void busiestWeekPicksWeekWithMostSessions() {
        com.ecut.campus.data.Timetable t = new com.ecut.campus.data.Timetable();
        t.courses.add(courseWithWeeks("1-16周", 2));
        t.courses.add(courseWithWeeks("1-16周", 2));
        t.courses.add(courseWithWeeks("3-18周", 1));
        assertEquals(3, com.ecut.campus.data.TimetableStats.busiestWeek(t));
    }

    @Test
    public void busiestWeekTiePrefersEarlier() {
        com.ecut.campus.data.Timetable t = new com.ecut.campus.data.Timetable();
        t.courses.add(courseWithWeeks("5-8周", 1));
        t.courses.add(courseWithWeeks("9-12周", 1));
        assertEquals(5, com.ecut.campus.data.TimetableStats.busiestWeek(t));
    }

    @Test
    public void busiestWeekEmptyTimetableReturnsOne() {
        assertEquals(1, com.ecut.campus.data.TimetableStats.busiestWeek(new com.ecut.campus.data.Timetable()));
        assertEquals(1, com.ecut.campus.data.TimetableStats.busiestWeek(null));
    }

    private static com.ecut.campus.data.Timetable.Course courseWithWeeks(
            String weeksRaw, int sessions) {
        com.ecut.campus.data.Timetable.Course c = new com.ecut.campus.data.Timetable.Course();
        for (int i = 0; i < sessions; i++) {
            com.ecut.campus.data.Timetable.Session s = new com.ecut.campus.data.Timetable.Session();
            s.dayOfWeek = 1;
            s.startSection = 1;
            s.endSection = 2;
            s.weeksRaw = weeksRaw;
            s.weeks.addAll(com.ecut.campus.data.WeekRange.parse(weeksRaw).weeks);
            c.sessions.add(s);
        }
        return c;
    }

    private static Grades.SemesterGrades sem(String id) {
        Grades.SemesterGrades s = new Grades.SemesterGrades();
        s.id = id;
        return s;
    }
}
