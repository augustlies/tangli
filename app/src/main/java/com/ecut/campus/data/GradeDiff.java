package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 对比两次成绩同步，找出新出的成绩。 */
public final class GradeDiff {

    private GradeDiff() {}

    public static String key(Grades.SemesterGrades s, Grades.CourseGrade c) {
        String id = c.code == null || c.code.isEmpty() ? c.name : c.code;
        return s.id + "|" + id;
    }

    /** remote 里有、local 里没有的课程。local 为空（首次同步）时返回空列表，避免一次推几十条。 */
    public static List<Grades.CourseGrade> newGrades(Grades local, Grades remote) {
        List<Grades.CourseGrade> out = new ArrayList<>();
        if (local == null || remote == null || local.semesters.isEmpty()) return out;
        Set<String> known = keys(local);
        for (Grades.SemesterGrades s : remote.semesters) {
            for (Grades.CourseGrade c : s.courses) {
                if (!known.contains(key(s, c))) out.add(c);
            }
        }
        return out;
    }

    public static List<String> newKeys(Grades local, Grades remote) {
        List<String> out = new ArrayList<>();
        if (local == null || remote == null || local.semesters.isEmpty()) return out;
        Set<String> known = keys(local);
        for (Grades.SemesterGrades s : remote.semesters)
            for (Grades.CourseGrade c : s.courses)
                if (!known.contains(key(s, c))) out.add(key(s, c));
        return out;
    }

    static Set<String> keys(Grades g) {
        Set<String> set = new HashSet<>();
        for (Grades.SemesterGrades s : g.semesters)
            for (Grades.CourseGrade c : s.courses) set.add(key(s, c));
        return set;
    }
}
