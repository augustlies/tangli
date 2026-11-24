package com.ecut.campus.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成绩本地存储（学校目录内的 grades.json）。
 * 原子写入，支持增量合并不同学期查询出的成绩。
 */
public final class GradesStore {

    private static final String TAG = "GradesStore";
    private static final String FILE_NAME = "grades.json";

    private GradesStore() {}

    public static Grades load(Context ctx) {
        File f = com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME);
        try {
            String json = AtomicFileStore.read(f);
            return json == null ? new Grades() : Grades.fromJson(json);
        } catch (JSONException e) {
            Log.e(TAG, "grades.json 损坏，返回空成绩", e);
            return new Grades();
        } catch (Exception e) {
            Log.e(TAG, "读取成绩失败", e);
            return new Grades();
        }
    }

    public static boolean save(Context ctx, Grades g) {
        try {
            AtomicFileStore.write(com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME), g.toJson().toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存成绩失败", e);
            return false;
        }
    }

    public static boolean exists(Context ctx) {
        return AtomicFileStore.exists(com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME));
    }

    public static void clear(Context ctx) {
        SyncGeneration.invalidate();
        try {
            AtomicFileStore.clear(com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME));
        } catch (Exception e) {
            Log.e(TAG, "清理本地文件失败", e);
        }
    }

    /**
     * 合并本地成绩与新抓取的成绩（按学期 ID + 课程代码/名称去重覆盖）。
     */
    public static Grades merge(Grades local, Grades remote) {
        Grades out = new Grades();
        Map<String, Grades.SemesterGrades> semMap = new LinkedHashMap<>();

        if (local != null) {
            for (Grades.SemesterGrades s : local.semesters) {
                Grades.SemesterGrades copy = new Grades.SemesterGrades();
                copy.id = s.id;
                copy.name = s.name;
                copy.courses.addAll(s.courses);
                semMap.put(copy.id, copy);
            }
        }

        if (remote != null) {
            for (Grades.SemesterGrades rs : remote.semesters) {
                Grades.SemesterGrades target = semMap.get(rs.id);
                if (target == null) {
                    target = new Grades.SemesterGrades();
                    target.id = rs.id;
                    target.name = rs.name;
                    semMap.put(rs.id, target);
                }
                Map<String, Grades.CourseGrade> courseMap = new LinkedHashMap<>();
                for (Grades.CourseGrade c : target.courses) {
                    String key = (c.code != null && !c.code.isEmpty()) ? c.code : c.name;
                    courseMap.put(key, c);
                }
                for (Grades.CourseGrade rc : rs.courses) {
                    String key = (rc.code != null && !rc.code.isEmpty()) ? rc.code : rc.name;
                    courseMap.put(key, rc);
                }
                target.courses.clear();
                target.courses.addAll(courseMap.values());
            }
        }

        out.semesters.addAll(semMap.values());
        out.sortSemestersDescending();
        out.recomputeAll();
        return out;
    }

    public static String exportJson(Grades g) throws JSONException {
        return g.toJson().toString(2);
    }

    public static Grades importJson(String json) throws JSONException {
        return Grades.fromJson(json);
    }

}
