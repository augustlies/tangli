package com.ecut.campus.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 强智 xskb_list.do 表格解析（fixture 已脱敏）。 */
public class QiangzhiSourceTest {

    static String fixture(String path) throws Exception {
        try (InputStream in = QiangzhiSourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull("缺少 fixture " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Timetable.Course course(Timetable t, String name) {
        for (Timetable.Course c : t.courses) if (c.name.equals(name)) return c;
        throw new AssertionError("没有课程 " + name + "，实际: " + t.courses.size());
    }

    private static Timetable.Session session(Timetable.Course c, int day) {
        for (Timetable.Session s : c.sessions) if (s.dayOfWeek == day) return s;
        throw new AssertionError(c.name + " 没有周" + day + " 的课");
    }

    @Test
    public void parsesStandardTable() throws Exception {
        JwglxtFetcher.Result r = QiangzhiSource.parseHtml(fixture("fixtures/qiangzhi/xskb_list-sample.html"));
        assertTrue(r.warnings.toString(), r.ok());
        assertEquals("2025-2026-1", r.timetable.semester.id);
        assertEquals("2025-2026 学年第1学期", r.timetable.semester.name);
        assertEquals(5, r.courseCount);
        assertEquals(0, r.failedWeekExpr);

        Timetable.Course math = course(r.timetable, "高等数学A");
        assertEquals("张三", math.teacher);
        assertEquals(Timetable.SOURCE_JWGLXT, math.source);
        assertEquals(2, math.sessions.size());
        Timetable.Session mon = session(math, 1);
        assertEquals(1, mon.startSection);
        assertEquals(2, mon.endSection);
        assertEquals(16, mon.weeks.size());
        assertEquals("A101", mon.location);
        assertEquals("1-16(周)", mon.weeksRaw);
        Timetable.Session thu = session(math, 4);
        assertEquals(3, thu.startSection);
        assertEquals(4, thu.endSection);
        assertEquals(Arrays.asList(1, 3, 5, 7, 9, 11, 13, 15), thu.weeks);
        assertEquals("A102", thu.location);
    }

    @Test
    public void splitsMultipleCoursesInOneCellAndIgnoresBriefCopy() throws Exception {
        JwglxtFetcher.Result r = QiangzhiSource.parseHtml(fixture("fixtures/qiangzhi/xskb_list-sample.html"));
        Timetable.Course en = course(r.timetable, "大学英语");
        Timetable.Course la = course(r.timetable, "线性代数");
        assertEquals(1, en.sessions.size());
        assertEquals(1, la.sessions.size());
        assertEquals(3, en.sessions.get(0).dayOfWeek);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), en.sessions.get(0).weeks);
        assertEquals("B202", en.sessions.get(0).location);
        assertEquals("王五", la.teacher);
        assertEquals(9, (int) la.sessions.get(0).weeks.get(0));
        assertEquals("C303", la.sessions.get(0).location);
    }

    @Test
    public void handlesEvenWeeksAndFourSectionBlocks() throws Exception {
        JwglxtFetcher.Result r = QiangzhiSource.parseHtml(fixture("fixtures/qiangzhi/xskb_list-sample.html"));
        Timetable.Session phy = session(course(r.timetable, "大学物理"), 2);
        assertEquals(Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16), phy.weeks);
        Timetable.Session lab = session(course(r.timetable, "程序设计实验"), 5);
        assertEquals(5, lab.startSection);
        assertEquals(8, lab.endSection);
        assertEquals(Arrays.asList(3, 4, 5, 6, 8, 9, 10, 11, 12), lab.weeks);
        assertEquals("实验楼E505", lab.location);
    }

    @Test
    public void usesHeaderForDaysAndRowLabelForSections() throws Exception {
        JwglxtFetcher.Result r = QiangzhiSource.parseHtml(fixture("fixtures/qiangzhi/xskb_list-sunday-first.html"));
        assertEquals(3, r.courseCount);
        assertEquals("", r.timetable.semester.id);
        Timetable.Course pol = course(r.timetable, "思想政治理论");
        assertEquals("孙八", pol.teacher);
        Timetable.Session s = session(pol, 1);
        assertEquals(1, s.startSection);
        assertEquals(2, s.endSection);
        assertEquals("F606", s.location);
        assertEquals(6, course(r.timetable, "周末讲座").sessions.get(0).dayOfWeek);
        Timetable.Session vol = course(r.timetable, "志愿服务").sessions.get(0);
        assertEquals(7, vol.dayOfWeek);
        assertEquals(3, vol.startSection);
        assertEquals(4, vol.endSection);
    }

    @Test
    public void emptyOrErrorInputIsNotOk() {
        assertFalse(QiangzhiSource.parseHtml("").ok());
        assertFalse(QiangzhiSource.parseHtml(null).ok());
        assertFalse(QiangzhiSource.parseHtml("NOTABLE").ok());
        assertFalse(QiangzhiSource.parseHtml("<html><body>请先登录</body></html>").ok());
    }

    @Test
    public void helpers() {
        assertArrayEquals(new int[]{1, 4}, QiangzhiSource.sectionsFrom("01-02-03-04节"));
        assertArrayEquals(new int[]{9, 11}, QiangzhiSource.sectionsFrom("9-11节"));
        assertArrayEquals(new int[]{1, 2}, QiangzhiSource.rowLabelSections("第一大节 0102", 1));
        assertArrayEquals(new int[]{3, 4}, QiangzhiSource.rowLabelSections("第二大节(03,04小节)", 2));
        assertArrayEquals(new int[]{5, 6}, QiangzhiSource.rowLabelSections("第三大节", 9));
        assertArrayEquals(new int[]{7, 8}, QiangzhiSource.rowLabelSections("", 4));
        assertEquals(3, QiangzhiSource.dayFromId("8A1F3C-3-2"));
        assertEquals(0, QiangzhiSource.dayFromId("kb"));
        assertEquals(7, QiangzhiSource.dayFromText("星期日"));
        assertEquals(1, QiangzhiSource.dayFromText("周一"));
    }

    @Test
    public void extractionJsTargetsHistoricTerm() {
        String js = new QiangzhiSource().extractionJs(null, "2024", "12");
        assertTrue(js.contains("2024-2025-2"));
        assertTrue(js.contains("xskb/xskb_list.do"));
    }
}
