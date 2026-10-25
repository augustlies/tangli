package com.ecut.campus.data;

import java.util.Iterator;

/**
 * 旧版本「载入样本体验」写进来的演示成绩。演示数据曾经和真实成绩合并保存，
 * 所以这里按「课程代码 + 课程名」精确识别并剔除，真实成绩不受影响。
 */
public final class DemoGrades {
    private DemoGrades() {}

    private static final String[][] DEMO = {
            {"DK2104ZB", "构造地质学"},
            {"DK2108ZB", "结晶学与矿物学"},
            {"DK2112ZB", "岩石学（岩浆岩与变质岩）"},
            {"DK2119ZW", "古生物学与地史学"},
            {"DK1101ZB", "专业导论"},
            {"DK7002ZW", "普通地质学"},
            {"GG1002ZB", "高等数学 A(一)"},
            {"TY1001ZB", "大学体育（太极拳）"},
    };

    public static boolean isDemo(Grades.CourseGrade c) {
        if (c == null || c.code == null || c.name == null) return false;
        for (String[] d : DEMO) {
            if (d[0].equals(c.code) && d[1].equals(c.name)) return true;
        }
        return false;
    }

    /** 剔除演示成绩，返回删掉的门数；删空的学期一并去掉。 */
    public static int strip(Grades g) {
        if (g == null || g.semesters == null) return 0;
        int removed = 0;
        Iterator<Grades.SemesterGrades> si = g.semesters.iterator();
        while (si.hasNext()) {
            Grades.SemesterGrades s = si.next();
            if (s.courses == null) continue;
            Iterator<Grades.CourseGrade> ci = s.courses.iterator();
            while (ci.hasNext()) {
                if (isDemo(ci.next())) {
                    ci.remove();
                    removed++;
                }
            }
            if (s.courses.isEmpty()) si.remove();
        }
        if (removed > 0) g.recomputeAll();
        return removed;
    }
}
