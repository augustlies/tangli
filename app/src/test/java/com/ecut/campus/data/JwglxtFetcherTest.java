package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表解析与序列化测试。
 *
 * 输入是 2026-10-23 真机抓取的正方课表样本（已脱敏，存于
 * app/src/test/resources/fixtures/jwglxt_kb_sample.json）。
 */
public class JwglxtFetcherTest {

    /** 真机样本：14 条记录 → 6 门课 */
    private static final String REAL_SAMPLE = "{"
            + "\"xsxx\":{"
            + "\"XNM\":\"2026\",\"XQM\":\"3\",\"XNMC\":\"2026-2027\",\"XQMMC\":\"1\","
            + "\"XM\":\"张三\",\"XH\":\"2024000000\",\"ZYMC\":\"资源勘查工程\""
            + "},"
            + "\"kbList\":["
            + rec("地学信息三维可视化", "1", "1-2", "10-13周", "3-114", "教师A", "2.0", "JXB001")
            + "," + rec("地球化学", "1", "3-4", "5-12周", "4-305", "教师B", "3.0", "JXB002")
            + "," + rec("数字地质学", "1", "5-6", "6-11周", "4-424", "教师C", "2.0", "JXB003")
            + "," + rec("矿床学", "2", "1-2", "7-13周", "4-620", "教师D", "3.5", "JXB004")
            + "," + rec("文献检索与科技论文写作", "2", "3-4", "5-8周", "4-620", "教师E", "1.5", "JXB005")
            + "," + rec("成矿构造学", "2", "3-4", "9-16周", "3-420", "教师F", "2.0", "JXB006")
            + "," + rec("地球化学", "3", "3-4", "5-12周", "4-422", "教师B", "3.0", "JXB002")
            + "," + rec("数字地质学", "3", "5-6", "6-10周", "4-422", "教师C", "2.0", "JXB003")
            + "," + rec("成矿构造学", "4", "1-2", "9-16周", "3-105", "教师F", "2.0", "JXB006")
            + "," + rec("矿床学", "4", "3-4", "7-13周", "4-622", "教师D", "3.5", "JXB004")
            + "," + rec("文献检索与科技论文写作", "4", "5-6", "5-8周", "4-322", "教师E", "1.5", "JXB005")
            + "," + rec("地学信息三维可视化", "4", "5-6", "10-13周", "3-217", "教师A", "2.0", "JXB001")
            + "," + rec("地球化学", "5", "1-2", "5-12周", "4-422", "教师B", "3.0", "JXB002")
            + "," + rec("矿床学", "5", "5-6", "7-13周", "4-421", "教师D", "3.5", "JXB004")
            + "]"
            + "}";

    private static String rec(String kcmc, String xqj, String jcor, String zcd,
                              String cdmc, String xm, String xf, String jxb) {
        return "{\"kcmc\":\"" + kcmc + "\",\"xqj\":\"" + xqj + "\",\"jcor\":\"" + jcor
                + "\",\"jc\":\"" + jcor + "节\",\"zcd\":\"" + zcd + "\",\"cdmc\":\"" + cdmc
                + "\",\"xm\":\"" + xm + "\",\"xf\":\"" + xf + "\",\"jxb_id\":\"" + jxb
                + "\",\"xqmc\":\"广兰校园\",\"kclbmc\":\"多媒体教室\",\"kcbj\":\"主修\""
                + ",\"khfsmc\":\"未安排\"}";
    }

    // ---------- 基础解析 ----------

    @Test
    public void parse_realSample_ok() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        assertTrue("应解析成功", r.ok());
        assertEquals("原始记录 14 条", 14, r.rawRecords);
        assertEquals("聚合后 6 门课", 6, r.courseCount);
        assertEquals("周次全部可解析", 0, r.failedWeekExpr);
    }

    @Test
    public void parse_semester() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        assertEquals("2026-2027-1", r.timetable.semester.id);
        assertTrue(r.timetable.semester.name.contains("2026-2027"));
        assertTrue(r.timetable.semester.name.contains("第1学期"));
    }

    // ---------- 聚合正确性 ----------

    @Test
    public void aggregate_multiSessionCourse() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course geochem = r.timetable.findByName("地球化学");
        assertNotNull("地球化学应存在", geochem);
        assertEquals("地球化学有 3 个时段", 3, geochem.sessions.size());
    }

    @Test
    public void aggregate_sameCourseDifferentRooms() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course geochem = r.timetable.findByName("地球化学");

        List<String> rooms = new ArrayList<>();
        for (Timetable.Session s : geochem.sessions) rooms.add(s.location);
        assertTrue("周一教室 4-305", rooms.contains("4-305"));
        assertTrue("周三/周五教室 4-422", rooms.contains("4-422"));
    }

    @Test
    public void aggregate_twoSessionsCourse() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course viz = r.timetable.findByName("地学信息三维可视化");
        assertEquals(2, viz.sessions.size());
        assertEquals(2.0, viz.credit, 0.01);
    }

    // ---------- 字段映射 ----------

    @Test
    public void field_sections() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course viz = r.timetable.findByName("地学信息三维可视化");
        Timetable.Session s = viz.sessions.get(0);
        assertEquals(1, s.startSection);
        assertEquals(2, s.endSection);
        assertEquals(1, s.dayOfWeek);
    }

    @Test
    public void field_weeks() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course viz = r.timetable.findByName("地学信息三维可视化");
        Timetable.Session s = viz.sessions.get(0);
        assertEquals("10-13周", s.weeksRaw);
        assertEquals(4, s.weeks.size());
        assertTrue(s.isActive(10));
        assertTrue(s.isActive(13));
        assertFalse(s.isActive(9));
    }

    @Test
    public void field_teacherAndCampus() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        Timetable.Course ore = r.timetable.findByName("矿床学");
        assertEquals("教师D", ore.teacher);
        assertEquals(3.5, ore.credit, 0.01);
        assertEquals("广兰校园", ore.sessions.get(0).campus);
    }

    // ---------- 查询辅助 ----------

    @Test
    public void query_coursesOnMonday() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        // 第 10 周周一：地学信息三维可视化(10-13) + 地球化学(5-12) + 数字地质学(6-11)
        List<Timetable.Course> monday = r.timetable.coursesOn(1, 10);
        assertEquals(3, monday.size());
    }

    @Test
    public void query_weekOutsideRange() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        // 第 1 周：所有课都还没开始（最早 5-8周）
        List<Timetable.Course> week1 = r.timetable.coursesOn(1, 1);
        assertEquals("第 1 周周一无课", 0, week1.size());
    }

    @Test
    public void query_week16() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        // 第 16 周周四：成矿构造学(9-16)
        List<Timetable.Course> thu = r.timetable.coursesOn(4, 16);
        assertEquals(1, thu.size());
        assertEquals("成矿构造学", thu.get(0).name);
    }

    // ---------- 序列化往返 ----------

    @Test
    public void jsonRoundTrip() throws Exception {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(REAL_SAMPLE);
        String json = r.timetable.toJson().toString();

        Timetable restored = Timetable.fromJson(json);
        assertEquals(r.timetable.courses.size(), restored.courses.size());
        assertEquals(r.timetable.semester.id, restored.semester.id);

        Timetable.Course orig = r.timetable.findByName("地球化学");
        Timetable.Course back = restored.findByName("地球化学");
        assertNotNull(back);
        assertEquals(orig.sessions.size(), back.sessions.size());
        assertEquals(orig.sessions.get(0).weeksRaw, back.sessions.get(0).weeksRaw);
        assertEquals(orig.sessions.get(0).location, back.sessions.get(0).location);
    }

    @Test
    public void schemaVersion_tooNew() {
        String future = "{\"schemaVersion\":99,\"semester\":{},\"courses\":[]}";
        try {
            Timetable.fromJson(future);
            assertTrue("版本过高应抛异常", false);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("版本"));
        }
    }

    // ---------- 异常输入 ----------

    @Test
    public void parse_emptyInput() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse("");
        assertFalse(r.ok());
        assertTrue(r.warnings.size() > 0);
    }

    @Test
    public void parse_nullInput() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse(null);
        assertFalse(r.ok());
    }

    @Test
    public void parse_invalidJson() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse("{not json");
        assertFalse(r.ok());
    }

    @Test
    public void parse_noKbList() {
        JwglxtFetcher.Result r = JwglxtFetcher.parse("{\"xsxx\":{}}");
        assertFalse(r.ok());
    }

    @Test
    public void parse_badWeekExpr_warnsButKeepsCourse() {
        String json = "{\"xsxx\":{},\"kbList\":[{\"kcmc\":\"怪课\",\"xqj\":\"1\","
                + "\"jcor\":\"1-2\",\"zcd\":\"待定\",\"jxb_id\":\"X1\"}]}";
        JwglxtFetcher.Result r = JwglxtFetcher.parse(json);
        assertEquals("课程仍保留", 1, r.courseCount);
        assertEquals("记录 1 条解析失败", 1, r.failedWeekExpr);
        assertTrue("有警告", r.warnings.size() > 0);
        // 保底：周次为空时 isActive 返回 true，课程不会消失
        assertTrue(r.timetable.courses.get(0).isActive(5));
    }

    // ---------- 字段清洗 ----------

    @Test
    public void clean_htmlEntities() {
        assertEquals("A & B", JwglxtFetcher.clean("A &amp; B"));
        assertEquals("a b", JwglxtFetcher.clean("a&nbsp;b"));
        assertEquals("x", JwglxtFetcher.clean("  x  "));
    }

    @Test
    public void clean_fullWidthSpace() {
        assertEquals("A B", JwglxtFetcher.clean("A\u3000B"));
    }

    @Test
    public void parseSections_formats() {
        int[] a = JwglxtFetcher.parseSections("3-4");
        assertEquals(3, a[0]);
        assertEquals(4, a[1]);

        int[] b = JwglxtFetcher.parseSections("5-6节");
        assertEquals(5, b[0]);
        assertEquals(6, b[1]);

        int[] c = JwglxtFetcher.parseSections("7");
        assertEquals(7, c[0]);
        assertEquals(7, c[1]);

        int[] d = JwglxtFetcher.parseSections("");
        assertEquals(1, d[0]);
    }

    @Test
    public void buildSemesterId_terms() {
        assertEquals("2026-2027-1", JwglxtFetcher.buildSemesterId("2026", "3"));
        assertEquals("2026-2027-2", JwglxtFetcher.buildSemesterId("2026", "12"));
        assertEquals("2025-2026-1", JwglxtFetcher.buildSemesterId("2025", "3"));
    }

    // ---------- 同名同时段去重（形势与政策被拆成多个教学班） ----------

    @Test
    public void parse_sameNameSameSlot_mergedIntoOneSession() {
        String json = "{\"xsxx\":{\"XNM\":\"2026\",\"XQM\":\"3\"},\"kbList\":["
                + rec("形势与政策(III)", "1", "3-4", "11周", "3-402", "教师A", "0.5", "JXB_A")
                + "," + rec("形势与政策(III)", "1", "3-4", "11-12周", "3-402", "教师B", "0.5", "JXB_B")
                + "," + rec("马克思主义基本原理", "1", "3-4", "1-10周", "3-104", "教师C", "3", "JXB_C")
                + "]}";
        JwglxtFetcher.Result r = JwglxtFetcher.parse(json);
        assertEquals(1, r.mergedDuplicates);
        int xs = 0;
        for (Timetable.Course c : r.timetable.courses) {
            if (!c.name.startsWith("形势与政策")) continue;
            xs++;
            assertEquals(1, c.sessions.size());
            assertTrue(c.sessions.get(0).weeks.contains(11));
            assertTrue(c.sessions.get(0).weeks.contains(12));
            // 窄的（只第 11 周）那条更具体：第 11 周显示教师A，第 12 周显示教师B
            assertEquals("教师A", c.sessions.get(0).teacherFor(11, c.teacher));
            assertEquals("教师B", c.sessions.get(0).teacherFor(12, c.teacher));
            assertEquals("3-402", c.sessions.get(0).location);
        }
        assertEquals(1, xs);
        // 第 11 周周一 3-4 节只剩一门课
        assertEquals(1, r.timetable.coursesOn(1, 11).size());
    }

    @Test
    public void parse_differentNamesSameSlot_keptAsConflict() {
        String json = "{\"xsxx\":{\"XNM\":\"2026\",\"XQM\":\"3\"},\"kbList\":["
                + rec("课程甲", "2", "1-2", "1-8周", "1-101", "教师A", "2", "J1")
                + "," + rec("课程乙", "2", "1-2", "5-8周", "1-102", "教师B", "2", "J2")
                + "]}";
        JwglxtFetcher.Result r = JwglxtFetcher.parse(json);
        assertEquals(0, r.mergedDuplicates);
        assertEquals(2, r.timetable.courses.size());
    }

    /** 形势与政策场景：同一教学班，按周换主讲老师。 */
    @Test
    public void parse_perWeekLecturers() {
        String json = "{\"xsxx\":{\"XNM\":\"2026\",\"XQM\":\"3\"},\"kbList\":["
                + rec("形势与政策（III）", "1", "3-4", "11-14周", "3-402", "教师甲", "0.5", "J")
                + "," + rec("形势与政策（III）", "1", "3-4", "第11周", "3-402", "教师乙", "0.5", "J")
                + "," + rec("形势与政策（III）", "1", "3-4", "第12周", "3-402", "教师丙", "0.5", "J")
                + "," + rec("形势与政策（III）", "1", "3-4", "第13周", "3-402", "教师丁", "0.5", "J")
                + "," + rec("形势与政策（III）", "1", "3-4", "第14周", "3-402", "教师戊", "0.5", "J")
                + "]}";
        JwglxtFetcher.Result r = JwglxtFetcher.parse(json);
        assertEquals(1, r.timetable.courses.size());
        Timetable.Course c = r.timetable.courses.get(0);
        assertEquals(1, c.sessions.size());
        Timetable.Session s = c.sessions.get(0);
        assertEquals("11-14周", s.weeksRaw);
        assertEquals("教师乙", s.teacherFor(11, c.teacher));
        assertEquals("教师丙", s.teacherFor(12, c.teacher));
        assertEquals("教师丁", s.teacherFor(13, c.teacher));
        assertEquals("教师戊", s.teacherFor(14, c.teacher));
    }

    @Test
    public void session_customTimeAndWeekTeachers_roundTrip() throws Exception {
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 3;
        s.startSection = 5;
        s.endSection = 6;
        s.timeStart = "14:30";
        s.timeEnd = "16:30";
        s.weekTeachers.put(11, "教师乙");
        Timetable.Session back = Timetable.Session.fromJson(s.toJson());
        assertTrue(back.hasCustomTime());
        assertEquals("14:30", back.timeStart);
        assertEquals("16:30", back.timeEnd);
        assertEquals("教师乙", back.teacherFor(11, "x"));
        assertEquals("x", back.teacherFor(12, "x"));
        Timetable.Session bad = new Timetable.Session();
        bad.timeStart = "16:00";
        bad.timeEnd = "15:00";
        assertFalse(bad.hasCustomTime());
    }
}
