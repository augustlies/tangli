package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 强智成绩表 + 开学日探测。样本按强智常见结构手写，已匿名。 */
public class QiangzhiGradesAndStartTest {

    private static final String GRADES = "<html><body><table id=\"dataList\" class=\"Nsb_r_list Nsb_table\">"
            + "<tr><th>序号</th><th>开课学期</th><th>课程编号</th><th>课程名称</th><th>成绩</th>"
            + "<th>学分</th><th>总学时</th><th>绩点</th><th>考核方式</th><th>课程属性</th><th>课程性质</th></tr>"
            + "<tr><td>1</td><td>2025-2026-1</td><td>0000001</td><td>高等数学A</td>"
            + "<td><a href=\"javascript:JsMod('/jsxsd/kscj/pscj_list.do?xs0101id=x')\">88</a></td>"
            + "<td>5</td><td>80</td><td>3.8</td><td>考试</td><td>必修</td><td>公共基础课</td></tr>"
            + "<tr><td>2</td><td>2025-2026-1</td><td>0000002</td><td>体育</td><td>良好</td>"
            + "<td>1</td><td>32</td><td></td><td>考查</td><td>必修</td><td>公共基础课</td></tr>"
            + "<tr><td>3</td><td>2024-2025-2</td><td>0000003</td><td>大学物理</td><td>52</td>"
            + "<td>3</td><td>48</td><td>0</td><td>考试</td><td>必修</td><td>学科基础课</td></tr>"
            + "</table></body></html>";

    @Test
    public void parsesGradesByHeader() {
        Grades g = QiangzhiGrades.parseHtml(GRADES);
        assertEquals(3, g.totalCourseCount());
        assertEquals(2, g.semesters.size());
        Grades.SemesterGrades first = g.semesters.get(0);
        assertEquals("2025-2026-1", first.id);
        assertEquals("2025-2026 学年第1学期", first.name);
        Grades.CourseGrade math = first.courses.get(0);
        assertEquals("高等数学A", math.name);
        assertEquals("0000001", math.code);
        assertEquals("88", math.score);
        assertEquals(5.0, math.credit, 0.001);
        assertEquals(3.8, math.gradePoint, 0.001);
        assertEquals("考试", math.examType);
        Grades.CourseGrade pe = first.courses.get(1);
        assertNull(pe.scoreValue);
        assertEquals(3.5, pe.gradePoint, 0.001);
        assertTrue(pe.isPassed);
        assertFalse(g.semesters.get(1).courses.get(0).isPassed);
    }

    @Test
    public void tableWithoutNameOrScoreIsEmpty() {
        String html = "<table id='dataList'><tr><th>序号</th><th>考试科目</th></tr><tr><td>1</td><td>x</td></tr></table>";
        assertEquals(0, QiangzhiGrades.parseHtml(html).totalCourseCount());
        assertEquals(0, QiangzhiGrades.parseHtml(null).totalCourseCount());
    }

    @Test
    public void gradesUrlAndPage() {
        SchoolProfile p = SchoolProfile.custom("http://10.0.0.8:8080/jsxsd/", null);
        assertEquals("http://10.0.0.8:8080/jsxsd/kscj/cjcx_list", QiangzhiGrades.gradesUrl(p));
        assertTrue(QiangzhiGrades.isGradesPage("http://10.0.0.8:8080/jsxsd/kscj/cjcx_list"));
        assertFalse(QiangzhiGrades.isGradesPage("http://10.0.0.8:8080/jsxsd/xskb/xskb_list.do"));
    }

    @Test
    public void startFromQiangzhiCalendar() {
        String html = "<table id='kbtable'><tr><th>周次</th><th>星期一</th><th>星期二</th></tr>"
                + "<tr><td>1</td><td title='2025年09月08日'>8</td><td title='2025年09月09日'>9</td></tr>"
                + "<tr><td>2</td><td title='2025年09月15日'>15</td></tr></table>";
        assertEquals("2025-09-08", TermStartProbe.fromQiangzhiCalendar(html, "2025-10-26"));
        // 日期在周中也收敛到周一
        String mid = "<tr><td>第1周</td><td>2025-09-10</td></tr>";
        assertEquals("2025-09-08", TermStartProbe.fromQiangzhiCalendar(mid, "2025-10-26"));
        // 年份离今天太远的不要
        assertEquals("", TermStartProbe.fromQiangzhiCalendar(html, "2027-10-26"));
    }

    @Test
    public void startFromWeekText() {
        // 2026-10-26 是周一；当前第 8 周 → 往回 7 周
        assertEquals("2026-09-07", TermStartProbe.fromWeekText("欢迎 · 当前第8周 · 星期一", "2026-10-26"));
        assertEquals("2026-09-07", TermStartProbe.fromWeekText("本周是第 8 教学周", "2026-10-28"));
        assertEquals("", TermStartProbe.fromWeekText("第8周有考试", "2026-10-26"));
        assertEquals("", TermStartProbe.fromWeekText("当前第40周", "2026-10-26"));
    }

    @Test
    public void detectPrefersCalendarThenText() {
        String json = "{\"jxzl\":\"<tr><td>1</td><td>2026-09-01</td></tr>\",\"text\":\"当前第8周\"}";
        assertEquals("2026-08-31", TermStartProbe.detect(json, "2026-10-26"));
        assertEquals("2026-09-07", TermStartProbe.detect("{\"jxzl\":\"\",\"text\":\"当前第8周\"}", "2026-10-26"));
        assertEquals("", TermStartProbe.detect("{}", "2026-10-26"));
    }
}
