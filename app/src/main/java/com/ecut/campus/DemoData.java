package com.ecut.campus;

import android.content.Context;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.JwglxtFetcher;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 脱敏演示数据。只在用户主动点击「载入演示数据」时写入，首次启动不再自动预置。
 */
public final class DemoData {

    private DemoData() {}

    public static void seedAll(Context ctx) {
        seedTimetable(ctx);
        seedGradesOnly(ctx);
        Prefs.setLastSyncTime(ctx, new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date()));
    }

    public static void seedTimetable(Context ctx) {
        String kbSample = "{"
                + "\"xsxx\":{\"XNM\":\"2026\",\"XQM\":\"3\",\"XNMC\":\"2026-2027\",\"XQMMC\":\"1\"},"
                + "\"kbList\":["
                + "{\"kcmc\":\"地学信息三维可视化\",\"xqj\":\"1\",\"jcor\":\"1-2\",\"zcd\":\"10-13周\",\"cdmc\":\"3-114\",\"xm\":\"教师A\",\"xf\":\"2.0\",\"jxb_id\":\"JXB001\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"地球化学\",\"xqj\":\"1\",\"jcor\":\"3-4\",\"zcd\":\"5-12周\",\"cdmc\":\"4-305\",\"xm\":\"教师B\",\"xf\":\"3.0\",\"jxb_id\":\"JXB002\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"数字地质学\",\"xqj\":\"1\",\"jcor\":\"5-6\",\"zcd\":\"6-11周\",\"cdmc\":\"4-424\",\"xm\":\"教师C\",\"xf\":\"2.0\",\"jxb_id\":\"JXB003\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"矿床学\",\"xqj\":\"2\",\"jcor\":\"1-2\",\"zcd\":\"7-13周\",\"cdmc\":\"4-620\",\"xm\":\"教师D\",\"xf\":\"3.5\",\"jxb_id\":\"JXB004\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"文献检索与科技论文写作\",\"xqj\":\"2\",\"jcor\":\"3-4\",\"zcd\":\"5-8周\",\"cdmc\":\"4-620\",\"xm\":\"教师E\",\"xf\":\"1.5\",\"jxb_id\":\"JXB005\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"成矿构造学\",\"xqj\":\"2\",\"jcor\":\"3-4\",\"zcd\":\"9-16周\",\"cdmc\":\"3-420\",\"xm\":\"教师F\",\"xf\":\"2.0\",\"jxb_id\":\"JXB006\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"地球化学\",\"xqj\":\"3\",\"jcor\":\"3-4\",\"zcd\":\"5-12周\",\"cdmc\":\"4-422\",\"xm\":\"教师B\",\"xf\":\"3.0\",\"jxb_id\":\"JXB002\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"数字地质学\",\"xqj\":\"3\",\"jcor\":\"5-6\",\"zcd\":\"6-10周\",\"cdmc\":\"4-422\",\"xm\":\"教师C\",\"xf\":\"2.0\",\"jxb_id\":\"JXB003\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"成矿构造学\",\"xqj\":\"4\",\"jcor\":\"1-2\",\"zcd\":\"9-16周\",\"cdmc\":\"3-105\",\"xm\":\"教师F\",\"xf\":\"2.0\",\"jxb_id\":\"JXB006\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"矿床学\",\"xqj\":\"4\",\"jcor\":\"3-4\",\"zcd\":\"7-13周\",\"cdmc\":\"4-622\",\"xm\":\"教师D\",\"xf\":\"3.5\",\"jxb_id\":\"JXB004\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"文献检索与科技论文写作\",\"xqj\":\"4\",\"jcor\":\"5-6\",\"zcd\":\"5-8周\",\"cdmc\":\"4-322\",\"xm\":\"教师E\",\"xf\":\"1.5\",\"jxb_id\":\"JXB005\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"地学信息三维可视化\",\"xqj\":\"4\",\"jcor\":\"5-6\",\"zcd\":\"10-13周\",\"cdmc\":\"3-217\",\"xm\":\"教师A\",\"xf\":\"2.0\",\"jxb_id\":\"JXB001\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"地球化学\",\"xqj\":\"5\",\"jcor\":\"1-2\",\"zcd\":\"5-12周\",\"cdmc\":\"4-422\",\"xm\":\"教师B\",\"xf\":\"3.0\",\"jxb_id\":\"JXB002\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"},"
                + "{\"kcmc\":\"矿床学\",\"xqj\":\"5\",\"jcor\":\"5-6\",\"zcd\":\"7-13周\",\"cdmc\":\"4-421\",\"xm\":\"教师D\",\"xf\":\"3.5\",\"jxb_id\":\"JXB004\",\"xqmc\":\"广兰校园\",\"kcbj\":\"主修\"}"
                + "]}";
        JwglxtFetcher.Result res = JwglxtFetcher.parse(kbSample);
        if (!res.ok()) return;
        for (int i = 0; i < res.timetable.courses.size(); i++) {
            res.timetable.courses.get(i).colorIndex = i % TimetableView.PALETTE_BG.length;
        }
        Timetable merged = TimetableStore.merge(TimetableStore.load(ctx), res.timetable);
        TimetableStore.save(ctx, merged);
        Prefs.setCurrentWeek(ctx, 10);
    }

    public static void seedGradesOnly(Context ctx) {
        Grades g = new Grades();
        Grades.SemesterGrades s1 = new Grades.SemesterGrades();
        s1.id = "2025-2026-2";
        s1.name = "2025-2026 学年第2学期";
        s1.courses.add(grade("DK2104ZB", "构造地质学", "93", 93.0, 3.5, 4.30, "必修"));
        s1.courses.add(grade("DK2108ZB", "结晶学与矿物学", "91", 91.0, 4.0, 4.10, "必修"));
        s1.courses.add(grade("DK2112ZB", "岩石学（岩浆岩与变质岩）", "86", 86.0, 3.5, 3.60, "必修"));
        s1.courses.add(grade("DK2119ZW", "古生物学与地史学", "84", 84.0, 3.0, 3.40, "必修"));

        Grades.SemesterGrades s2 = new Grades.SemesterGrades();
        s2.id = "2024-2025-1";
        s2.name = "2024-2025 学年第1学期";
        s2.courses.add(grade("DK1101ZB", "专业导论", "88", 88.0, 1.0, 3.80, "必修"));
        s2.courses.add(grade("DK7002ZW", "普通地质学", "75", 75.0, 3.0, 2.50, "必修"));
        s2.courses.add(grade("GG1002ZB", "高等数学 A(一)", "90", 90.0, 5.0, 4.00, "必修"));
        s2.courses.add(grade("TY1001ZB", "大学体育（太极拳）", "优秀", 95.0, 1.0, 4.50, "必修"));

        g.semesters.add(s1);
        g.semesters.add(s2);
        g.recomputeAll();
        GradesStore.save(ctx, GradesStore.merge(GradesStore.load(ctx), g));
        Prefs.setGradesDemo(ctx, true);
    }

    private static Grades.CourseGrade grade(String code, String name, String score,
                                            Double value, double credit, Double gp, String nature) {
        Grades.CourseGrade c = new Grades.CourseGrade();
        c.code = code;
        c.name = name;
        c.score = score;
        c.scoreValue = value;
        c.credit = credit;
        c.gradePoint = gp;
        c.nature = nature;
        c.examType = "正常考试";
        c.isPassed = true;
        return c;
    }
}
