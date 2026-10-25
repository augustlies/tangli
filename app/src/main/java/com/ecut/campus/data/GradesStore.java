package com.ecut.campus.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成绩本地存储（filesDir/grades.json）。
 * 原子写入，支持增量合并不同学期查询出的成绩。
 */
public final class GradesStore {

    private static final String TAG = "GradesStore";
    private static final String FILE_NAME = "grades.json";
    private static final String TMP_NAME = "grades.json.tmp";

    private GradesStore() {}

    public static Grades load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return new Grades();
        try {
            String json = readAll(new FileInputStream(f));
            return Grades.fromJson(json);
        } catch (JSONException e) {
            Log.e(TAG, "grades.json 损坏，返回空成绩", e);
            return new Grades();
        } catch (Exception e) {
            Log.e(TAG, "读取成绩失败", e);
            return new Grades();
        }
    }

    public static boolean save(Context ctx, Grades g) {
        File dir = ctx.getFilesDir();
        File tmp = new File(dir, TMP_NAME);
        File dst = new File(dir, FILE_NAME);
        try {
            String json = g.toJson().toString();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.getFD().sync();
            }
            if (dst.exists() && !dst.delete()) {
                Log.w(TAG, "旧成绩文件删除失败，继续覆盖");
            }
            if (!tmp.renameTo(dst)) {
                Log.e(TAG, "rename 失败");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存成绩失败", e);
            if (tmp.exists()) tmp.delete();
            return false;
        }
    }

    public static boolean exists(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME).exists();
    }

    public static void clear(Context ctx) {
        File dir = ctx.getFilesDir();
        new File(dir, FILE_NAME).delete();
        new File(dir, TMP_NAME).delete();
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

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
