package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

public class NewFeaturesDataTest {

    private static Timetable sample() {
        Timetable t = new Timetable();
        t.semester.id = "2026-2027-1";
        t.semester.startDate = "2026-09-07";
        Timetable.Course c = new Timetable.Course();
        c.name = "构造地质学";
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 2; s.startSection = 3; s.endSection = 4;
        s.weeks = TimetableShare.list(1, 2, 3, 4, 5, 6, 7, 8);
        s.location = "地学楼 A201";
        c.sessions.add(s);
        t.courses.add(c);
        return t;
    }

    @Test
    public void shareCodeRoundTrips() throws Exception {
        String code = TimetableShare.encode(sample(), "小董");
        assertTrue(code.startsWith(TimetableShare.PREFIX));
        TimetableShare.Shared sh = TimetableShare.decode("快导入我的课表 " + code + " ✓");
        assertNotNull(sh);
        assertEquals("小董", sh.owner);
        assertEquals("2026-09-07", sh.startDate);
        Timetable.Session s = sh.timetable.courses.get(0).sessions.get(0);
        assertEquals("构造地质学", sh.timetable.courses.get(0).name);
        assertEquals(2, s.dayOfWeek);
        assertEquals(4, s.endSection);
        assertEquals(8, s.weeks.size());
        assertEquals("地学楼 A201", s.location);
    }

    @Test
    public void garbageShareCodeIsNull() {
        assertNull(TimetableShare.decode("hello"));
        assertNull(TimetableShare.decode(TimetableShare.PREFIX + "@@@"));
    }

    @Test
    public void base64RoundTrip() {
        for (int n = 0; n < 40; n++) {
            byte[] b = new byte[n];
            for (int i = 0; i < n; i++) b[i] = (byte) (i * 37 + 11);
            assertArrayEquals(b, TimetableShare.B64.decode(TimetableShare.B64.encode(b)));
        }
    }

    @Test
    public void commonFreeExcludesAnyonesClass() {
        Timetable a = sample();
        Timetable b = new Timetable();
        boolean[][] free = FreeTime.commonFree(Arrays.asList(a, b), 3, 10);
        assertFalse(free[1][2]);
        assertFalse(free[1][3]);
        assertTrue(free[1][4]);
        boolean[][] week9 = FreeTime.commonFree(Arrays.asList(a, b), 9, 10);
        assertTrue(week9[1][2]);
    }

    @Test
    public void calendarStatus() {
        assertEquals(8, AcademicCalendar.weekOf(AcademicCalendar.termFor("2026-10-26"), "2026-10-26"));
        assertTrue(AcademicCalendar.statusLine("2026-10-26").startsWith("第一学期 · 第 8 周"));
        assertTrue(AcademicCalendar.statusLine("2027-01-30").startsWith("寒假中"));
        assertEquals("2026-2027-2", AcademicCalendar.termFor("2027-02-27").id);
        assertTrue(AcademicCalendar.statusLine("2026-09-01").contains("距开学 6 天"));
        assertEquals("国庆节", AcademicCalendar.eventsOn("2026-10-03").get(0).label);
        assertNull(AcademicCalendar.termFor("2027-09-02"));
    }

    @Test
    public void calendarSourceParsing() {
        String list = "<li><a href='/26/c3/c7595a140995/page.htm' target='_blank' title='东华理工大学2026-2027学年校历'>东华理工大学2026-2027学年校历</a></li>"
                + "<li><a href='/02/c5/c7595a131781/page.htm' title='东华理工大学2025-2026学年校历'>x</a></li>";
        assertEquals("https://www.ecut.edu.cn/26/c3/c7595a140995/page.htm", CalendarSource.latestArticleUrl(list));
        String real = "<a class=\"news-row\" href=\"/26/c3/c7595a140995/page.htm\" target=\"_blank\"><span class=\"news-row-date\"><strong>22</strong><small>2026.06</small></span><span class=\"news-row-copy\"><strong class=\"line2\">东华理工大学2026-2027学年校历</strong></span></a>";
        assertEquals("https://www.ecut.edu.cn/26/c3/c7595a140995/page.htm", CalendarSource.latestArticleUrl(real));
        String art = "<div class='wp_articlecontent'><p><img data-layer=\"photo\" src=\"/_upload/article/images/a.jpg\" /></p></div>";
        assertEquals("https://www.ecut.edu.cn/_upload/article/images/a.jpg", CalendarSource.imageUrl(art));
        String pdf = "<div class='wp_articlecontent'><div pdfsrc=\"/_upload/article/files/x.pdf\"></div></div>";
        assertEquals("https://www.ecut.edu.cn/_upload/article/files/x.pdf", CalendarSource.imageUrl(pdf));
    }

    @Test
    public void gradeDiffFindsOnlyNew() {
        Grades local = new Grades();
        Grades.SemesterGrades s = new Grades.SemesterGrades();
        s.id = "2025-2026-2";
        Grades.CourseGrade a = new Grades.CourseGrade(); a.code = "A1"; a.name = "高数";
        s.courses.add(a);
        local.semesters.add(s);
        Grades remote = new Grades();
        Grades.SemesterGrades s2 = new Grades.SemesterGrades();
        s2.id = "2025-2026-2";
        Grades.CourseGrade a2 = new Grades.CourseGrade(); a2.code = "A1"; a2.name = "高数";
        Grades.CourseGrade b2 = new Grades.CourseGrade(); b2.code = "B2"; b2.name = "矿床学";
        s2.courses.add(a2); s2.courses.add(b2);
        remote.semesters.add(s2);
        assertEquals(1, GradeDiff.newGrades(local, remote).size());
        assertEquals("矿床学", GradeDiff.newGrades(local, remote).get(0).name);
        assertTrue(GradeDiff.newGrades(new Grades(), remote).isEmpty());
    }
}
