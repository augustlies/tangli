package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

/** vc33 小玩意的纯逻辑：翻牌批语、每节课价格、缺勤上限、双人海报统计、学期人设。 */
public class FunStatsTest {

    static Timetable.Session ses(int day, int start, int end, int fromWeek, int toWeek, String loc) {
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = day;
        s.startSection = start;
        s.endSection = end;
        for (int w = fromWeek; w <= toWeek; w++) s.weeks.add(w);
        s.location = loc;
        return s;
    }

    static Timetable.Course course(String name, Timetable.Session... ss) {
        Timetable.Course c = new Timetable.Course();
        c.name = name;
        c.key = "k_" + name + "_" + ss.length;
        c.sessions.addAll(Arrays.asList(ss));
        return c;
    }

    /** 8:00 起每节 45 分钟；第 5 节下午 14:00，第 9 节晚上 19:00。 */
    static int[][] schedule(int n) {
        int[] st = new int[n + 1], en = new int[n + 1];
        int[] starts = {0, 480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195, 1250, 1305};
        for (int i = 1; i <= n; i++) {
            st[i] = starts[i];
            en[i] = starts[i] + 45;
        }
        return new int[][]{st, en};
    }

    // ---------- GradeVerdict ----------
    @Test
    public void verdictBandsNumeric() {
        assertEquals(GradeVerdict.Band.TOP, GradeVerdict.band("95", 95.0, true));
        assertEquals(GradeVerdict.Band.TOP, GradeVerdict.band("90", null, true));
        assertEquals(GradeVerdict.Band.GOOD, GradeVerdict.band("89.5", 89.5, true));
        assertEquals(GradeVerdict.Band.MID, GradeVerdict.band("70", 70.0, true));
        assertEquals(GradeVerdict.Band.PASS, GradeVerdict.band("60", 60.0, true));
        assertEquals(GradeVerdict.Band.FAIL, GradeVerdict.band("59", 59.0, true));
        assertEquals(GradeVerdict.Band.FAIL, GradeVerdict.band("75", 75.0, false));
        assertEquals(GradeVerdict.Band.GOOD, GradeVerdict.band("", 85.0, true));
    }

    @Test
    public void verdictBandsLevelsAndPassFail() {
        assertEquals(GradeVerdict.Band.TOP, GradeVerdict.band("优秀", 95.0, true));
        assertEquals(GradeVerdict.Band.GOOD, GradeVerdict.band("良好", null, true));
        assertEquals(GradeVerdict.Band.MID, GradeVerdict.band("中等", null, true));
        assertEquals(GradeVerdict.Band.PASS, GradeVerdict.band("及格", null, true));
        assertEquals(GradeVerdict.Band.FAIL, GradeVerdict.band("不及格", null, true));
        assertEquals(GradeVerdict.Band.PASSED, GradeVerdict.band("合格", null, true));
        assertEquals(GradeVerdict.Band.PASSED, GradeVerdict.band("通过", 85.0, true));
        assertEquals(GradeVerdict.Band.FAIL, GradeVerdict.band("不合格", null, false));
        assertEquals(GradeVerdict.Band.GOOD, GradeVerdict.band("b+", null, true));
        assertNull(GradeVerdict.band("", null, true));
        assertNull(GradeVerdict.band("缓考", 0.0, true));
        assertNull(GradeVerdict.band("", null, false));
    }

    @Test
    public void verdictPickIsFromBandAndEveryBandHasSeveral() {
        for (GradeVerdict.Band b : GradeVerdict.Band.values()) {
            String[] lines = GradeVerdict.lines(b);
            assertTrue(lines.length >= 3);
            String p = GradeVerdict.pick(b, 7);
            assertTrue(Arrays.asList(lines).contains(p));
            assertTrue(Arrays.asList(lines).contains(GradeVerdict.pick(b, -123456789L)));
            for (String l : lines) assertTrue(l, l.length() <= 14);
        }
        assertEquals("", GradeVerdict.pick(null, 1));
    }

    @Test
    public void verdictSkipsExamStatusAndIsStable() {
        // 缓考/缺考没给通过标记，也不能被说成挂科
        assertNull(GradeVerdict.band("缓考", null, false));
        assertNull(GradeVerdict.band("缺考", 0.0, false));
        assertNull(GradeVerdict.band("旷考", null, false));
        assertNull(GradeVerdict.band("作弊", 0.0, false));
        assertEquals(GradeVerdict.Band.FAIL, GradeVerdict.band("45", 45.0, false));
        long seed = java.util.Objects.hash("高等数学", "92");
        assertEquals(GradeVerdict.pick(GradeVerdict.Band.TOP, seed), GradeVerdict.pick(GradeVerdict.Band.TOP, seed));
    }

    // ---------- ClassPrice ----------
    @Test
    public void totalSectionsCountsRealOccurrences() {
        Timetable t = new Timetable();
        t.courses.add(course("高数", ses(1, 1, 2, 1, 16, "A101"), ses(3, 3, 4, 1, 8, "A101")));
        t.courses.add(course("实验", ses(5, 5, 7, 2, 4, "B2")));
        assertEquals(2 * 16 + 2 * 8 + 3 * 3, ClassPrice.totalSections(t, 20));
        assertEquals(2 * 4 + 2 * 4 + 3 * 3, ClassPrice.totalSections(t, 4));
        assertEquals(0, ClassPrice.totalSections(new Timetable(), 20));
        assertEquals(0, ClassPrice.totalSections(null, 20));
        // 同一时段重叠的课（口令留下的两条、手动重复加的）只算一次
        t.courses.add(course("高数重复", ses(1, 2, 3, 1, 16, "A101")));
        assertEquals(2 * 16 + 16 + 2 * 8 + 3 * 3, ClassPrice.totalSections(t, 20));
    }

    @Test
    public void perSectionAndFormatting() {
        assertEquals(10.0, ClassPrice.perSection(570, 57), 1e-9);
        assertEquals(0, ClassPrice.perSection(0, 57), 1e-9);
        assertEquals(0, ClassPrice.perSection(5000, 0), 1e-9);
        assertEquals("19.8", ClassPrice.yuan(19.84));
        assertEquals("128", ClassPrice.yuan(127.6));
        assertEquals(5500, ClassPrice.parseTuition(" ¥5,500元 "), 1e-9);
        assertEquals(0, ClassPrice.parseTuition("五千"), 1e-9);
        assertEquals(0, ClassPrice.parseTuition(""), 1e-9);
    }

    @Test
    public void earlyEightTease() {
        assertTrue(ClassPrice.isEarlyEight(ses(1, 1, 2, 1, 1, "")));
        assertFalse(ClassPrice.isEarlyEight(ses(1, 3, 4, 1, 1, "")));
        assertTrue(ClassPrice.teaseLine(16, 0).contains("一杯奶茶"));
        assertTrue(ClassPrice.teaseLine(90, 1).contains("2张电影票"));
        assertTrue(ClassPrice.teaseLine(1, 2).contains("一瓶矿泉水"));
        assertEquals("", ClassPrice.teaseLine(0, 0));
        assertEquals(ClassPrice.teaseLine(20, "高数".hashCode()), ClassPrice.teaseLine(20, "高数".hashCode()));
    }

    // ---------- AbsenceBook ----------
    @Test
    public void absenceLimitIsOneThirdOfSessionsAcrossSameNamedCourses() {
        Timetable t = new Timetable();
        t.courses.add(course("大学英语", ses(1, 1, 2, 1, 16, "")));
        t.courses.add(course("大学 英语", ses(3, 1, 2, 1, 8, ""))); // 另一个 key、同名（带空格）
        assertEquals(24, AbsenceBook.sessionCount(t, "大学英语", 20));
        assertEquals(8, AbsenceBook.limit(24));
        assertEquals(0, AbsenceBook.limit(2));
        assertEquals(0, AbsenceBook.sessionCount(t, "高数", 20));
    }

    @Test
    public void absenceWarnAtEightyPercentAndSurvivesJson() {
        AbsenceBook b = new AbsenceBook();
        b.bump("大学英语", 0, 3);
        b.bump("大学英语", 1, 3);
        b.bump("大学英语", 2, 5);
        b.bump("大学英语", 1, -10); // 不会变负
        AbsenceBook.Counts c = b.get("大学　英语");
        assertEquals(3, c.leave);
        assertEquals(0, c.absent);
        assertEquals(5, c.called);
        assertFalse(AbsenceBook.warn(c, 8));   // 3 < 6.4
        b.bump("大学英语", 1, 4);                // 7 ≥ 6.4
        assertTrue(AbsenceBook.warn(b.get("大学英语"), 8));
        assertFalse(AbsenceBook.warn(new AbsenceBook.Counts(), 0));

        AbsenceBook r = AbsenceBook.fromJson(b.toJson());
        assertEquals(7, r.get("大学英语").used());
        assertEquals(5, r.get("大学英语").called);
        assertEquals(0, AbsenceBook.fromJson("not json").get("x").used());

        // 重新导入后课程 key 变了：按课名仍能对上
        Timetable t = new Timetable();
        t.courses.add(course("大学英语", ses(1, 1, 2, 1, 16, ""), ses(3, 1, 2, 1, 8, "")));
        t.courses.add(course("高数", ses(2, 1, 2, 1, 16, "")));
        Set<String> warn = r.warnKeys(t, 20);
        assertTrue(warn.contains("大学英语"));
        assertEquals(1, warn.size());
    }

    @Test
    public void absenceFollowsRename() {
        AbsenceBook b = new AbsenceBook();
        b.bump("高等数学", 1, 2);
        b.bump("高等数学A", 0, 1);
        Timetable t = new Timetable();
        t.courses.add(course("高等数学A", ses(1, 1, 2, 1, 16, "")));
        assertTrue(b.rename("高等数学", "高等数学A", t));
        assertEquals(3, b.get("高等数学A").used());
        assertEquals(0, b.get("高等数学").used());
        assertFalse(AbsenceBook.fromJson(b.toJson()).toJson().contains("高等数学\""));
        // 课表里还有别的课叫旧名：计数留给它
        b.bump("英语", 1, 1);
        t.courses.add(course("英语", ses(2, 1, 2, 1, 16, "")));
        assertFalse(b.rename("英语", "英语听说", t));
        assertEquals(1, b.get("英语").used());
        assertFalse(b.rename("不存在", "x", t));
    }

    // ---------- PairStats ----------
    @Test
    public void buildingNames() {
        assertEquals("N1", PairStats.building("N1-305"));
        assertEquals("3", PairStats.building("3-201"));
        assertEquals("三教A", PairStats.building("三教A201"));
        assertEquals("致远楼", PairStats.building("致远楼305"));
        assertEquals("理科楼", PairStats.building("（本部）理科楼204"));
        assertEquals("第一教学楼", PairStats.building("第一教学楼-201"));
        assertEquals("", PairStats.building("201"));
        assertEquals("", PairStats.building(null));
    }

    @Test
    public void pairOverlapBuildingAndMeals() {
        int[][] sc = schedule(10);
        Timetable me = new Timetable(), you = new Timetable();
        me.courses.add(course("高数", ses(1, 1, 2, 1, 16, "三教A201")));
        me.courses.add(course("英语", ses(2, 3, 4, 1, 16, "致远楼101")));
        you.courses.add(course("线代", ses(1, 1, 2, 1, 16, "三教A305")));
        you.courses.add(course("体育", ses(3, 5, 6, 1, 16, "操场")));
        PairStats.Result r = PairStats.compute(me, you, 3, 5, sc[0], sc[1]);
        assertEquals(4, r.mySections);
        assertEquals(4, r.theirSections);
        assertEquals(2, r.both);
        assertEquals(6, r.either);
        assertEquals(33, r.overlapPct);
        assertEquals(2, r.sameBuildingSections);
        assertEquals("三教A", r.topBuilding);
        // 周四周五两人都没课，整段都空，也算能一起吃
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), r.lunchDays);
        assertEquals("干饭搭子认证", r.title);
        assertEquals("一半的课一起熬，饭也一起吃", r.subtitle);
        PairStats.Result r3 = PairStats.compute(me, you, 3, 3, sc[0], sc[1]);
        assertEquals("半个同桌", r3.title);
    }

    @Test
    public void pairLunchGapAndEmptyTitle() {
        int[][] sc = schedule(10);
        Timetable me = new Timetable(), you = new Timetable();
        // 第 4-5 节（10:55-11:40、14:00-14:45）中间留出 11:40-14:00，照样能一起吃午饭
        me.courses.add(course("跨午", ses(1, 4, 5, 1, 16, "")));
        PairStats.Result r = PairStats.compute(me, you, 1, 5, sc[0], sc[1]);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), r.lunchDays);
        assertTrue(r.dinnerDays.contains(1));
        PairStats.Result empty = PairStats.compute(new Timetable(), new Timetable(), 1, 5, sc[0], sc[1]);
        assertEquals("两张白纸", empty.title);
        assertEquals(0, empty.overlapPct);
        PairStats.Result oneSide = PairStats.compute(me, new Timetable(), 1, 5, sc[0], sc[1]);
        assertEquals("等 TA 交课表", oneSide.title);
        assertEquals("先交你的课表", PairStats.compute(new Timetable(), me, 1, 5, sc[0], sc[1]).title);
    }

    @Test
    public void mealWindowNeedsForty() {
        int[] st = {0, 660, 760};
        int[] en = {0, 760, 840};
        String[] a = {null, "x", null};
        String[] b = {null, null, "y"};
        assertEquals(0, PairStats.commonFree(a, b, st, en, PairStats.LUNCH_FROM, PairStats.LUNCH_TO));
        String[] c = {null, null, null};
        assertEquals(180, PairStats.commonFree(c, c, st, en, PairStats.LUNCH_FROM, PairStats.LUNCH_TO));
    }

    // ---------- SemesterPersona ----------
    @Test
    public void personaEarlyEight() {
        int[][] sc = schedule(10);
        Timetable t = new Timetable();
        for (int d = 1; d <= 4; d++) t.courses.add(course("早课" + d, ses(d, 1, 2, 1, 16, "")));
        t.courses.add(course("周五", ses(5, 3, 4, 1, 16, "")));
        SemesterPersona.Card c = SemesterPersona.pick(SemesterPersona.stats(t, 1, sc[0], sc[1], null));
        assertEquals("早八渡劫散仙", c.title);
        assertEquals("早八 4/5 天", c.lines.get(0));
        assertEquals(3, c.lines.size());
    }

    @Test
    public void personaFreeDayGpaAndEmpty() {
        int[][] sc = schedule(10);
        Timetable t = new Timetable();
        t.courses.add(course("A", ses(1, 3, 4, 1, 16, "")));
        t.courses.add(course("B", ses(2, 3, 4, 1, 16, "")));
        t.courses.add(course("C", ses(4, 3, 4, 1, 16, "")));
        t.courses.add(course("D", ses(5, 3, 4, 1, 16, "")));
        SemesterPersona.Stats s = SemesterPersona.stats(t, 1, sc[0], sc[1], null);
        assertEquals(Arrays.asList(3), s.freeWeekdays);
        assertEquals("周三空课神仙", SemesterPersona.pick(s).title);
        assertEquals("满绩卷王预备役", SemesterPersona.pick(SemesterPersona.stats(t, 1, sc[0], sc[1], 3.85)).title);
        assertEquals("空白课表观察员", SemesterPersona.pick(SemesterPersona.stats(new Timetable(), 1, sc[0], sc[1], null)).title);
        assertEquals("空白课表观察员", SemesterPersona.pick(SemesterPersona.stats(null, 1, sc[0], sc[1], 3.9)).title);
    }

    @Test
    public void personaEveningMarathonAndDefault() {
        int[][] sc = schedule(10);
        Timetable t = new Timetable();
        t.courses.add(course("晚1", ses(1, 9, 10, 1, 16, "")));
        t.courses.add(course("晚2", ses(3, 9, 10, 1, 16, "")));
        for (int d = 1; d <= 5; d++) t.courses.add(course("课" + d, ses(d, 3, 4, 1, 16, "")));
        assertEquals("夜行听课侠", SemesterPersona.pick(SemesterPersona.stats(t, 1, sc[0], sc[1], null)).title);

        Timetable m = new Timetable();
        m.courses.add(course("连堂", ses(2, 3, 8, 1, 16, "")));
        for (int d = 1; d <= 5; d++) if (d != 2) m.courses.add(course("x" + d, ses(d, 3, 4, 1, 16, "")));
        // 默认作息 3-4 节和 5-8 节中间隔着午休：不算连上 6 节
        SemesterPersona.Stats ms = SemesterPersona.stats(m, 1, sc[0], sc[1], null);
        assertEquals(4, ms.maxRun);
        assertFalse("连堂铁人".equals(SemesterPersona.pick(ms).title));
        // 课间都只有 10 分钟的作息：同样 6 节就是连堂
        int[] st = new int[11], en = new int[11];
        for (int i = 1; i <= 10; i++) { st[i] = 480 + (i - 1) * 55; en[i] = st[i] + 45; }
        SemesterPersona.Stats tight = SemesterPersona.stats(m, 1, st, en, null);
        assertEquals(6, tight.maxRun);
        assertEquals("连堂铁人", SemesterPersona.pick(tight).title);

        Timetable d = new Timetable();
        for (int k = 1; k <= 5; k++) {
            d.courses.add(course("m" + k, ses(k, 3, 4, 1, 16, "")));
            d.courses.add(course("n" + k, ses(k, 5, 6, 1, 16, "")));
        }
        SemesterPersona.Card dc = SemesterPersona.pick(SemesterPersona.stats(d, 1, sc[0], sc[1], 2.8));
        assertEquals("均衡发展型选手", dc.title);
        assertEquals("每周 20 节 · 10 门课", dc.lines.get(0));
        assertTrue(dc.lines.contains("平均绩点 2.80"));
    }

    @Test
    public void personaReviewFixes() {
        int[][] sc = schedule(10);
        // 白纸只留一行，不补「连上 0 节」
        SemesterPersona.Card blank = SemesterPersona.pick(SemesterPersona.stats(new Timetable(), 1, sc[0], sc[1], null));
        assertEquals(Arrays.asList("本周 0 节课"), blank.lines);
        // 第 1 节 10:00 开始的作息不算早八
        Timetable t = new Timetable();
        for (int d = 1; d <= 4; d++) t.courses.add(course("早课" + d, ses(d, 1, 2, 1, 16, "")));
        int[] st = sc[0].clone(), en = sc[1].clone();
        st[1] = 600; en[1] = 645; st[2] = 655; en[2] = 700;
        assertEquals(0, SemesterPersona.stats(t, 1, st, en, null).earlyDays);
        // 课多（≥26 节）但周五空着：苦行僧优先
        Timetable busy = new Timetable();
        for (int d = 1; d <= 4; d++) {
            busy.courses.add(course("a" + d, ses(d, 3, 4, 1, 16, "")));
            busy.courses.add(course("b" + d, ses(d, 5, 8, 1, 16, "")));
            busy.courses.add(course("c" + d, ses(d, 9, 10, 1, 16, "")));
        }
        SemesterPersona.Stats bs = SemesterPersona.stats(busy, 1, sc[0], sc[1], 2.5);
        bs.eveningDays = 0; // 只看优先级
        assertEquals("课表苦行僧", SemesterPersona.pick(bs).title);
        // 绩点 0（全挂）≠ 没成绩
        Grades g = new Grades();
        Grades.SemesterGrades sem = new Grades.SemesterGrades();
        Grades.CourseGrade c = new Grades.CourseGrade();
        c.credit = 3;
        c.gradePoint = 0.0;
        c.isPassed = false;
        sem.courses.add(c);
        g.semesters.add(sem);
        g.recomputeAll();
        assertEquals(0.0, SemesterPersona.gpa(g), 1e-9);
        Grades passOnly = new Grades();
        Grades.SemesterGrades s2 = new Grades.SemesterGrades();
        Grades.CourseGrade p = new Grades.CourseGrade();
        p.credit = 2;
        s2.courses.add(p);
        passOnly.semesters.add(s2);
        assertNull(SemesterPersona.gpa(passOnly));
        assertNull(SemesterPersona.gpa(null));
        Timetable d = new Timetable();
        for (int k = 1; k <= 5; k++) {
            d.courses.add(course("m" + k, ses(k, 3, 4, 1, 16, "")));
            d.courses.add(course("n" + k, ses(k, 5, 6, 1, 16, "")));
        }
        assertEquals("逆风翻盘候选人", SemesterPersona.pick(SemesterPersona.stats(d, 1, sc[0], sc[1], 0.0)).title);
    }
}
