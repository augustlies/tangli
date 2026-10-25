package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DemoGradesTest {
    private static Grades.CourseGrade c(String code, String name) {
        Grades.CourseGrade g = new Grades.CourseGrade();
        g.code = code;
        g.name = name;
        g.score = "90";
        g.scoreValue = 90.0;
        g.credit = 2.0;
        g.gradePoint = 4.0;
        return g;
    }

    @Test
    public void stripsOnlyDemoCourses() {
        Grades g = new Grades();
        Grades.SemesterGrades demo = new Grades.SemesterGrades();
        demo.id = "2025-2026-2";
        demo.courses.add(c("DK2104ZB", "构造地质学"));
        demo.courses.add(c("DK2108ZB", "结晶学与矿物学"));
        Grades.SemesterGrades real = new Grades.SemesterGrades();
        real.id = "2024-2025-1";
        real.courses.add(c("DK7002ZW", "普通地质学"));
        real.courses.add(c("DK7002ZW", "普通地质学(实验)"));
        real.courses.add(c("XX0001", "大学英语"));
        g.semesters.add(demo);
        g.semesters.add(real);

        assertEquals(3, DemoGrades.strip(g));
        assertEquals(1, g.semesters.size());
        assertEquals(2, g.semesters.get(0).courses.size());
        assertEquals(0, DemoGrades.strip(g));
    }
}
