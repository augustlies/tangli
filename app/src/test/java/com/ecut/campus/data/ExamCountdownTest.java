package com.ecut.campus.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ExamCountdownTest {
    @Test public void recognizesExamNamesButNotGenericExamType() {
        for (String s : Arrays.asList("期中考试", "随堂测验", "实验检测", "CET-4", "大学英语四六级", "全国计算机等级考试",
                "计算机二级", "大学英语四级考试", "考研英语", "研究生入学考试", "国考", "国家公务员考试", "普通话水平测试"))
            assertTrue(s, ExamCountdown.isExam(s));
        assertFalse(ExamCountdown.isExam("考试"));
        assertFalse(ExamCountdown.isExam("高等数学"));
    }
    @Test public void findsUpcomingSessionByWeekAndDayAndHonorsSemesterEnd() {
        Timetable t = new Timetable(); t.semester.startDate = "2026-08-31"; t.semester.totalWeeks = 3;
        Timetable.Course c = new Timetable.Course(); c.name = "期末考试";
        Timetable.Session s = new Timetable.Session(); s.dayOfWeek = 3; s.weeks.addAll(Arrays.asList(1, 3)); c.sessions.add(s); t.courses.add(c);
        assertEquals("2026-09-16", ExamCountdown.nextCourseDate(t,c,"2026-08-31",3,"2026-09-10"));
        assertEquals("2026-09-16", ExamCountdown.nextCourseDate(t,c,"2026-08-31",3,"2026-09-16"));
        assertEquals("", ExamCountdown.nextCourseDate(t,c,"2026-08-31",2,"2026-09-17"));
        assertEquals(0, ExamCountdown.remainingDays("2026-09-16","2026-09-16"));
    }
    @Test public void filtersPastAndSortsEventsWithNarrowCapacity() {
        java.util.List<ExamCountdown.Event> all = Arrays.asList(new ExamCountdown.Event("later","2026-12-03"),
                new ExamCountdown.Event("past","2026-11-01"), new ExamCountdown.Event("soon","2026-11-25"),
                new ExamCountdown.Event("today","2026-11-24"));
        java.util.List<ExamCountdown.Event> shown = ExamCountdown.upcoming(all,"2026-11-24",ExamCountdown.capacity(WidgetDay.NARROW));
        assertEquals(1,shown.size()); assertEquals("today",shown.get(0).name);
        assertEquals(2,ExamCountdown.upcoming(all,"2026-11-24",2).size());
        assertEquals("soon",ExamCountdown.upcoming(all,"2026-11-24",2).get(1).name);
        assertEquals(1,ExamCountdown.capacity(WidgetDay.NARROW));
        assertEquals(1,ExamCountdown.capacity(WidgetDay.MID));
        assertEquals(2,ExamCountdown.capacity(WidgetDay.WIDE));
    }
}
