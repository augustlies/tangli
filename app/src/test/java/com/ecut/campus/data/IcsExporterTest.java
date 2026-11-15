package com.ecut.campus.data;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ecut.campus.ScheduleSettings;

import org.junit.Test;

public class IcsExporterTest {

    private static Timetable timetableWithOneCourse() {
        Timetable t = new Timetable();
        t.semester.id = "2026-2027-1";
        t.semester.name = "2026-2027 学年第一学期";
        t.semester.startDate = "2026-09-07"; // 周一
        t.semester.totalWeeks = 20;
        Timetable.Course c = new Timetable.Course();
        c.name = "矿床学";
        c.teacher = "戴朝成";
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 2; // 周二
        s.startSection = 1;
        s.endSection = 2;
        s.weeksRaw = "1-16周";
        s.weeks.addAll(com.ecut.campus.data.WeekRange.parse("1-16周").weeks);
        s.location = "4-620";
        c.sessions.add(s);
        t.courses.add(c);
        return t;
    }

    @Test
    public void generatesValidIcsWithWeeklyEvents() {
        String ics = IcsExporter.export(timetableWithOneCourse(),
                ScheduleSettings.createDefault(), "测试学期");
        assertNotNull(ics);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"));
        assertTrue(ics.trim().endsWith("END:VCALENDAR"));
        // 1-16 周 → 16 个事件
        int events = ics.split("BEGIN:VEVENT", -1).length - 1;
        assertTrue(events == 16);
        // 第 1 周周二 = 2026-09-08，双节课 08:00 → 第 2 节下课 09:40
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260908T080000"));
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260908T094000"));
        assertTrue(ics.contains("SUMMARY:矿床学"));
        assertTrue(ics.contains("LOCATION:4-620"));
        assertTrue(ics.contains("DESCRIPTION:戴朝成"));
    }

    @Test
    public void skipsWeeksOutsideRange() {
        Timetable t = timetableWithOneCourse();
        t.courses.get(0).sessions.get(0).weeks.clear();
        t.courses.get(0).sessions.get(0).weeksRaw = "5-8周";
        t.courses.get(0).sessions.get(0).weeks.addAll(
                WeekRange.parse("5-8周").weeks);
        String ics = IcsExporter.export(t, ScheduleSettings.createDefault(), "x");
        assertNotNull(ics);
        assertTrue(ics.split("BEGIN:VEVENT", -1).length - 1 == 4);
    }

    @Test
    public void nullWhenNoStartDate() {
        Timetable t = timetableWithOneCourse();
        t.semester.startDate = "";
        assertNull(IcsExporter.export(t, ScheduleSettings.createDefault(), "x"));
    }

    @Test
    public void appliesCancelAndMoveOverlay() {
        Timetable t = timetableWithOneCourse();
        t.semester.startDate = "2026-03-02"; // 周一，春季无内置调休
        t.courses.get(0).sessions.get(0).weeks.clear();
        t.courses.get(0).sessions.get(0).weeksRaw = "1-4周";
        t.courses.get(0).sessions.get(0).weeks.addAll(WeekRange.parse("1-4周").weeks);

        // 第 2 周周二（2026-03-10）停课
        Timetable.Adjust cancel = new Timetable.Adjust();
        cancel.id = "c1";
        cancel.type = Timetable.Adjust.CANCEL;
        cancel.name = "矿床学";
        cancel.week = 2;
        cancel.day = 2;
        cancel.start = 1;
        cancel.end = 2;
        t.adjusts.add(cancel);

        // 第 3 周周二（2026-03-17）调到第 4 周周六（2026-03-28）19:00-20:30 @ 3-201
        Timetable.Adjust move = new Timetable.Adjust();
        move.id = "m1";
        move.type = Timetable.Adjust.MOVE;
        move.name = "矿床学";
        move.week = 3;
        move.day = 2;
        move.start = 1;
        move.end = 2;
        move.toWeek = 4;
        move.toDay = 6;
        move.toTimeStart = "19:00";
        move.toTimeEnd = "20:30";
        move.toLocation = "3-201";
        t.adjusts.add(move);

        String ics = IcsExporter.export(t, ScheduleSettings.createDefault(), "测试");
        assertNotNull(ics);
        // 原 4 节 - 1 停课 - 1 调走 + 1 调来 = 3 个事件
        assertTrue(ics.split("BEGIN:VEVENT", -1).length - 1 == 3);
        assertTrue(!ics.contains("20260310T080000"));
        assertTrue(!ics.contains("20260317T080000"));
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260328T190000"));
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260328T203000"));
        assertTrue(ics.contains("LOCATION:3-201"));
    }
}
