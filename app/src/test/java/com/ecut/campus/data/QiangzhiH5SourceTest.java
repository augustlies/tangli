package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** 强智移动教务（/sjd/ + njwhd 接口）：识别、逐周合并、开学日。样本为虚构数据。 */
public class QiangzhiH5SourceTest {

    private static String item(String name, String teacher, String loc, String time, String weeks) {
        return "{\"courseName\":\"" + name + "\",\"teacherName\":\"" + teacher + "\",\"location\":\"" + loc
                + "\",\"classTime\":\"" + time + "\",\"classWeek\":\"1-2\",\"classWeekDetails\":\"" + weeks
                + "\",\"startTime\":\"08:00\",\"endTIme\":\"09:40\"}";
    }

    private static final String RAW = "{\"term\":{\"semesterId\":\"2026-2027-1\"},\"weeks\":{"
            + "\"1\":{\"date\":[{\"xqmc\":\"一\",\"mxrq\":\"2026-08-31\",\"zc\":\"1\",\"xqid\":\"1\"},"
            + "{\"xqmc\":\"二\",\"mxrq\":\"2026-09-01\",\"zc\":\"1\",\"xqid\":\"2\"}],\"item\":["
            + item("操作系统", "张老师", "A401", "10102", ",1,2,3,") + ","
            + item("操作系统", "张老师", "A401", "30304", ",1,3,") + ","
            + item("大学英语", "李老师", "B306", "2050607", ",1,") + "]},"
            + "\"2\":{\"date\":[],\"item\":["
            + item("操作系统", "张老师", "A401", "10102", ",1,2,3,") + ","
            + item("实验课", "王老师", "实验楼", "50910", "") + "]}"
            + "}}";

    @Test
    public void detectsMobilePortalOnly() {
        QiangzhiH5Source s = new QiangzhiH5Source();
        assertTrue(s.detect("http://jw.example.edu.cn:8080/sjd/#/new/Home", null) >= TimetableSources.MIN_CONFIDENCE);
        assertTrue(s.detect("https://jw.example.edu.cn/app/", "<title>移动教务</title><script src=static/config.js></script><script src=static/icons.js>")
                >= TimetableSources.MIN_CONFIDENCE);
        assertTrue(s.detect("https://jw.example.edu.cn/jsxsd/xskb/xskb_list.do", "<table id=kbtable>")
                < TimetableSources.MIN_CONFIDENCE);
        assertSame(s.getClass(), TimetableSources.detect("http://jw.example.edu.cn:8080/sjd/#/new/Home",
                "<title>移动教务</title>").getClass());
        assertEquals(SchoolProfile.Vendor.OTHER, s.vendor());
        assertEquals("强智移动版", s.label());
    }

    @Test
    public void classTimeCodes() {
        assertTrue(Arrays.equals(new int[]{1, 1, 2}, QiangzhiH5Source.classTime("10102")));
        assertTrue(Arrays.equals(new int[]{2, 5, 7}, QiangzhiH5Source.classTime("2050607")));
        assertTrue(Arrays.equals(new int[]{5, 9, 10}, QiangzhiH5Source.classTime("50910")));
        assertEquals(null, QiangzhiH5Source.classTime("9"));
        assertEquals(null, QiangzhiH5Source.classTime(""));
        assertEquals(Arrays.asList(1, 2, 3), QiangzhiH5Source.weekList(",1,2,3,"));
    }

    @Test
    public void mergesWeeksAndReadsStart() {
        JwglxtFetcher.Result r = new QiangzhiH5Source().parse(RAW);
        assertTrue(r.ok());
        assertEquals("2026-2027-1", r.timetable.semester.id);
        assertEquals("2026-08-31", r.timetable.semester.startDate);
        assertEquals(3, r.courseCount);
        Timetable.Course os = null, lab = null;
        for (Timetable.Course c : r.timetable.courses) {
            if (c.name.equals("操作系统")) os = c;
            if (c.name.equals("实验课")) lab = c;
        }
        assertEquals(2, os.sessions.size());
        Timetable.Session mon = os.sessions.get(0).dayOfWeek == 1 ? os.sessions.get(0) : os.sessions.get(1);
        assertEquals(Arrays.asList(1, 2, 3), mon.weeks);
        assertEquals("A401", mon.location);
        // 没有 classWeekDetails 时按 classWeek
        assertEquals(Arrays.asList(1, 2), lab.sessions.get(0).weeks);
        assertEquals(9, lab.sessions.get(0).startSection);
        assertEquals(10, lab.sessions.get(0).endSection);
    }

    @Test
    public void explainsFailures() {
        QiangzhiH5Source s = new QiangzhiH5Source();
        JwglxtFetcher.Result r = s.parse("NOTOKEN");
        assertFalse(r.ok());
        assertTrue(s.emptyHint(r).contains("登录"));
        assertTrue(s.emptyHint(s.parse("NOAPI")).contains("重新登录"));
        assertFalse(s.parse("garbage").ok());
    }
}
