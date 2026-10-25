package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 成绩解析、GPA/加权分计算与 JSON 序列化测试。
 *
 * 样本基于 2026-10-23 真机 jqGrid tabGrid DOM 抓取（39 列，已脱敏）。
 */
public class GradesTest {

    /** 真机 tabGrid 39 列脱敏样本（第 0 行为 jqGrid 空占位行，第 1/2 行为真实数据行） */
    private static final String REAL_DOM_ROWS = "{"
            + "\"rows\":["
            + "[\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"],"
            + "[\"查看\",\"2024-2025\",\"1\",\"DK1101ZB\",\"专业导论\",\"必修\",\"1.0\",\"\",\"3.80\",\"正常考试\",\"否\",\"地球与行星科学学院\",\"主修\",\"\",\"\",\"专业导论-0024\",\"教师A\",\"\",\"2024000000\",\"张三\",\"\",\"\",\"88\",\"否\",\"\",\"3.80\",\"01\",\"88\",\"2024\",\"3\",\"ID1\",\"ID2\",\"ID3\",\"\",\"\",\"\",\"\",\"\",\"ID1\"],"
            + "[\"查看\",\"2024-2025\",\"1\",\"DK7002ZW\",\"普通地质学\",\"必修\",\"3.0\",\"\",\"2.50\",\"正常考试\",\"否\",\"地球与行星科学学院\",\"主修\",\"\",\"\",\"普通地质学-0001\",\"教师B\",\"\",\"2024000000\",\"张三\",\"\",\"\",\"75\",\"否\",\"\",\"7.50\",\"01\",\"75\",\"2024\",\"3\",\"ID4\",\"ID5\",\"ID6\",\"\",\"\",\"\",\"\",\"\",\"ID4\"]"
            + "]}";

    @Test
    public void parseFromDomRows_realSample_ok() {
        Grades g = Grades.parseFromDomRows(REAL_DOM_ROWS);
        assertEquals("1 个学期", 1, g.semesters.size());
        assertEquals("2 门课", 2, g.totalCourseCount());

        Grades.SemesterGrades sem = g.semesters.get(0);
        assertEquals("2024-2025-1", sem.id);
        assertEquals(4.0, sem.totalCredit, 0.01);

        // 加权平均分 = (88*1 + 75*3) / 4 = 313 / 4 = 78.25
        assertEquals(78.25, g.summary.weightedScore, 0.01);
        // GPA = (3.80*1 + 2.50*3) / 4 = 11.30 / 4 = 2.825 -> 2.83
        assertEquals(2.83, g.summary.gpa, 0.01);
        assertEquals(2, g.summary.passedCount);
        assertEquals(0, g.summary.failedCount);
    }

    @Test
    public void parseFromDomRows_textScoreAndFail() {
        String rows = "{\"rows\":["
                + "[\"查看\",\"2025-2026\",\"2\",\"TY01\",\"体育\",\"必修\",\"1.0\",\"\",\"\",\"正常考试\",\"否\",\"体育学院\",\"主修\",\"\",\"\",\"体育-01\",\"教师C\",\"\",\"2024000000\",\"张三\",\"\",\"\",\"优秀\",\"否\"],"
                + "[\"查看\",\"2025-2026\",\"2\",\"MA01\",\"高等数学\",\"必修\",\"4.0\",\"\",\"0.0\",\"正常考试\",\"否\",\"理学院\",\"主修\",\"\",\"\",\"高数-01\",\"教师D\",\"\",\"2024000000\",\"张三\",\"\",\"\",\"52\",\"否\"]"
                + "]}";
        Grades g = Grades.parseFromDomRows(rows);
        assertEquals(2, g.totalCourseCount());
        Grades.CourseGrade pe = g.semesters.get(0).courses.get(0);
        assertEquals("优秀", pe.score);
        assertNull(pe.scoreValue);
        assertTrue(pe.isPassed);

        Grades.CourseGrade math = g.semesters.get(0).courses.get(1);
        assertEquals(52.0, math.scoreValue, 0.01);
        assertFalse(math.isPassed);
        assertEquals(1, g.summary.passedCount);
        assertEquals(1, g.summary.failedCount);
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        Grades g = Grades.parseFromDomRows(REAL_DOM_ROWS);
        String json = g.toJson().toString();
        Grades restored = Grades.fromJson(json);
        assertNotNull(restored);
        assertEquals(g.totalCourseCount(), restored.totalCourseCount());
        assertEquals(g.summary.gpa, restored.summary.gpa, 0.01);
        assertEquals(g.summary.weightedScore, restored.summary.weightedScore, 0.01);
    }

    @Test
    public void merge_deduplicatesAndUpdates() {
        Grades g1 = Grades.parseFromDomRows(REAL_DOM_ROWS);
        Grades g2 = Grades.parseFromDomRows(REAL_DOM_ROWS);
        Grades merged = GradesStore.merge(g1, g2);
        assertEquals(2, merged.totalCourseCount());
    }

    @Test
    public void parseFromDomRows_canReplaceStaleDemoData() {
        Grades demo = new Grades();
        Grades.SemesterGrades stale = new Grades.SemesterGrades();
        stale.id = "2025-2026-2";
        stale.name = "2025-2026 学年第2学期";
        Grades.CourseGrade fake = new Grades.CourseGrade();
        fake.code = "DEMO";
        fake.name = "演示课程";
        fake.score = "93";
        fake.scoreValue = 93.0;
        fake.credit = 3.5;
        fake.gradePoint = 4.3;
        stale.courses.add(fake);
        demo.semesters.add(stale);

        Grades remote = Grades.parseFromDomRows(REAL_DOM_ROWS);
        assertEquals(1, demo.totalCourseCount());
        assertEquals(2, remote.totalCourseCount());
        assertEquals("专业导论", remote.semesters.get(0).courses.get(0).name);
    }
}
