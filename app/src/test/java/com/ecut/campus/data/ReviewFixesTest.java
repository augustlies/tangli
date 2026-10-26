package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.2.4 自查修复的回归测试。 */
public class ReviewFixesTest {

    @Test
    public void vendorGuessIgnoresHostAndQuery() {
        // 主机名叫 jwglxt 但跑的是强智
        assertEquals(SchoolProfile.Vendor.QIANGZHI,
                SchoolProfile.guessVendor("https://jwglxt.example.edu.cn/jsxsd/"));
        SchoolProfile p = SchoolProfile.custom("jwglxt.example.edu.cn/jsxsd/xk/LoginToXk", null);
        assertEquals(SchoolProfile.Vendor.QIANGZHI, p.vendor);
        assertEquals("https://jwglxt.example.edu.cn/jsxsd/", p.jwBase);
        // 统一认证网址的 service 参数里带 /jwglxt 不算正方
        assertEquals(SchoolProfile.Vendor.OTHER, SchoolProfile.guessVendor(
                "https://authserver.example.edu.cn/authserver/login?service=https%3A%2F%2Fjw.example.edu.cn/jwglxt/"));
        assertEquals(SchoolProfile.Vendor.OTHER, SchoolProfile.guessVendor(
                "https://authserver.example.edu.cn/authserver/login?service=https://jw.example.edu.cn/jwglxt/"));
        // 原有行为保留
        assertEquals(SchoolProfile.Vendor.ZHENGFANG,
                SchoolProfile.guessVendor("https://jw.example.edu.cn/jwglxt/xtgl/login_slogin.html"));
        assertEquals(SchoolProfile.Vendor.ZHENGFANG,
                SchoolProfile.guessVendor("https://jwglxt.example.edu.cn/xtgl/login_slogin.html"));
        assertEquals(SchoolProfile.Vendor.QIANGZHI,
                SchoolProfile.guessVendor("http://10.0.0.8:8080/jsxsd"));
    }

    @Test
    public void parentDomainKeepsCountrySecondLevel() {
        assertEquals("example.ac.cn", SchoolProfile.parentDomain("jw.example.ac.cn"));
        assertEquals("example.gov.cn", SchoolProfile.parentDomain("jw.example.gov.cn"));
        assertEquals("example.edu.hk", SchoolProfile.parentDomain("sis.example.edu.hk"));
        assertEquals("example.edu.cn", SchoolProfile.parentDomain("jw.example.edu.cn"));
        assertEquals("example.com.cn", SchoolProfile.parentDomain("jw.example.com.cn"));
        assertEquals("example.com", SchoolProfile.parentDomain("jw.example.com"));
        assertEquals("example.cn", SchoolProfile.parentDomain("jw.example.cn"));
        assertNull(SchoolProfile.parentDomain("example.ac.cn"));
        assertNull(SchoolProfile.parentDomain("10.1.2.3"));
        SchoolProfile p = SchoolProfile.custom("jw.example.ac.cn/jsxsd/", null);
        assertTrue(p.allowsUrl("https://cas.example.ac.cn/login"));
        assertFalse(p.allowsUrl("https://other.ac.cn/"));
    }

    @Test
    public void sameSchool() {
        SchoolProfile a = SchoolProfile.custom("jw.a.edu.cn/jwglxt", null);
        SchoolProfile a2 = SchoolProfile.custom("https://jw.a.edu.cn/jwglxt/xtgl/login_slogin.html", null);
        SchoolProfile b = SchoolProfile.custom("jw.b.edu.cn/jsxsd", null);
        SchoolProfile ecut = new SchoolProfile();
        ecut.id = SchoolProfile.ECUT_ID;
        assertTrue(SchoolProfile.sameSchool(a, a2));
        assertFalse(SchoolProfile.sameSchool(a, b));
        assertFalse(SchoolProfile.sameSchool(ecut, a));
        assertTrue(SchoolProfile.sameSchool(ecut, ecut));
        assertFalse(SchoolProfile.sameSchool(null, ecut));
    }

    private static String cell(String weeks) {
        return "<table id=\"kbtable\"><tr><th></th><th>星期一</th><th>星期二</th><th>星期三</th>"
                + "<th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>"
                + "<tr><th>第一大节</th><td><div class=\"kbcontent\" id=\"X-1-2\">测试课程<br>"
                + "<font title=\"老师\">教师甲</font><br><font title=\"周次(节次)\">" + weeks + "</font>"
                + "<br><font title=\"教室\">B201</font></div></td>"
                + "<td></td><td></td><td></td><td></td><td></td><td></td></tr></table>";
    }

    @Test
    public void qiangzhiWeekBracketIsNotSections() {
        Timetable.Session s = QiangzhiSource.parseHtml(cell("1-16[周][03-04节]"))
                .timetable.courses.get(0).sessions.get(0);
        assertEquals(3, s.startSection);
        assertEquals(4, s.endSection);
        assertEquals(16, s.weeks.size());

        s = QiangzhiSource.parseHtml(cell("[2-9]周[05-06]节")).timetable.courses.get(0).sessions.get(0);
        assertEquals(5, s.startSection);
        assertEquals(6, s.endSection);
        assertEquals(8, s.weeks.size());

        s = QiangzhiSource.parseHtml(cell("1-8,10-16(周)[01-02-03-04节]")).timetable.courses.get(0).sessions.get(0);
        assertEquals(1, s.startSection);
        assertEquals(4, s.endSection);
        assertEquals(15, s.weeks.size());
        assertEquals("1-8,10-16(周)", s.weeksRaw);

        // 没有「节」字的老写法照旧
        s = QiangzhiSource.parseHtml(cell("1-15(单周)[07-08]")).timetable.courses.get(0).sessions.get(0);
        assertEquals(7, s.startSection);
        assertEquals(8, s.endSection);
        assertEquals(8, s.weeks.size());

        // 只有周次括号：节次按行表头
        s = QiangzhiSource.parseHtml(cell("1-16[周]")).timetable.courses.get(0).sessions.get(0);
        assertEquals(1, s.startSection);
        assertEquals(2, s.endSection);
        assertEquals(16, s.weeks.size());
    }

    @Test
    public void pageSampleStripsSessionIdAndSpacedPhone() {
        String out = PageSample.sanitize("{\"path\":\"/jsxsd/framework/xsMain.jsp;jsessionid=AB12CD34EF\","
                + "\"labels\":{\"138 1234 5678\":1,\"139-8765-4321\":1}}");
        assertFalse(out, out.contains("AB12CD34EF"));
        assertFalse(out, out.contains("1234"));
        assertFalse(out, out.contains("8765"));
        assertTrue(out, out.contains("/jsxsd/framework/xsMain.jsp"));
    }
}
