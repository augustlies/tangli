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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课表本地存储。
 *
 * {@code filesDir/timetable_semesters.json} 是多学期主源；
 * {@code filesDir/timetable.json} 只是当前学期镜像，供旧调用方读取。
 * save() 先写主源，成功后才刷新镜像，镜像不会反向覆盖主源。
 *
 * 同步合并规则（见 docs/DATA-SCHEMA.md）：
 *   - 教务有、本地无 → 新增
     *   - 教务有、本地有 → 覆盖课程内容，保留用户的 colorIndex 与 note；
     *     开学日期保留本地值（教务接口不下发开学日期，remote 恒为空）
 *   - 教务无、本地是 jwglxt 来源 → 删除（教务已取消）
 *   - 教务无、本地是 custom 来源 → 保留（手动添加的实验课永不删除）
 */
public final class TimetableStore {

    private static final String TAG = "TimetableStore";
    private static final String FILE_NAME = "timetable.json";
    private static final String TMP_NAME = "timetable.json.tmp";

    private TimetableStore() {}

    // ---------- 读写 ----------

    /** 读取课表。文件不存在或损坏时返回空课表，不抛异常 */
    public static Timetable load(Context ctx) {
        Timetable mirror = readMirror(ctx);
        SemesterArchive.importLegacyMirror(ctx, mirror);
        String activeId = SemesterArchive.activeSemesterId(ctx);
        Timetable archived = activeId.isEmpty() ? null : SemesterArchive.loadSemester(ctx, activeId);
        if (archived != null) {
            CourseColorAssigner.assign(archived);
            return archived;
        }
        if (mirror != null) {
            CourseColorAssigner.assign(mirror);
            return mirror;
        }
        return new Timetable();
    }

    /** 先写归档主源，成功后再刷新 timetable.json 镜像。 */
    public static boolean save(Context ctx, Timetable t) {
        return SemesterArchive.saveCurrentSemester(ctx, t);
    }

    /** 归档主源刷新镜像；包内可见，避免 UI 绕开主源。 */
    static boolean writeMirrorFromArchive(Context ctx, Timetable t) {
        return writeMirror(ctx, t);
    }

    /** 读取当前学期镜像，仅供主源一致性校验。 */
    static Timetable readActiveMirror(Context ctx) {
        return readMirror(ctx);
    }

    private static boolean writeMirror(Context ctx, Timetable t) {
        File dir = ctx.getFilesDir();
        File tmp = new File(dir, TMP_NAME);
        File dst = new File(dir, FILE_NAME);
        try {
            String json = t.toJson().toString();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.getFD().sync();
            }
            if (dst.exists() && !dst.delete()) {
                Log.w(TAG, "旧文件删除失败，继续尝试覆盖");
            }
            if (!tmp.renameTo(dst)) {
                Log.e(TAG, "rename 失败");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存课表失败", e);
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
        SemesterArchive.clear(ctx);
    }

    // ---------- 同步合并 ----------

    /**
     * 把新抓到的教务课表合并进本地课表。
     *
     * @param local  本地现有课表
     * @param remote 刚从教务抓到的课表
     * @return 合并后的课表（新对象，不修改入参）
     */
    public static Timetable merge(Timetable local, Timetable remote) {
        Timetable out = new Timetable();
        out.semester = remote.semester.id.isEmpty() ? local.semester : remote.semester;
        // 同一学期的元数据由归档主源持有；教务结果只补空字段，
        // 不能把用户设置的日期或总周数洗回默认值。
        if (local != null && sameSemester(local, out)) {
            boolean localHasStart = local.semester.startDate != null
                    && !local.semester.startDate.isEmpty();
            if (localHasStart || local.semester.startDateTouched) {
                out.semester.startDate = local.semester.startDate == null
                        ? "" : local.semester.startDate;
                out.semester.startDateTouched = true;
            }
            out.semester.totalWeeks = local.semester.totalWeeks;
            out.semester.sortOrder = local.semester.sortOrder;
        }
        // 停课/调课/放假补课是用户自己的安排，教务不下发，同学期同步时原样保留
        if (local != null && (sameSemester(local, out) || remote.semester.id.isEmpty())) {
            out.adjusts.addAll(local.adjusts);
        }

        Map<String, Timetable.Course> localByKey = new LinkedHashMap<>();
        if (local != null) {
            for (Timetable.Course c : local.courses) localByKey.put(c.key, c);
        }

        // 1. 教务返回的课程：覆盖内容，保留用户的 note。colorIndex 不回填，
        // 教务课程统一由 CourseColorAssigner 按课程名重新分配。
        for (Timetable.Course rc : remote.courses) {
            Timetable.Course existing = localByKey.remove(rc.key);
            if (existing != null) {
                rc.note = existing.note;
                if (existing.colorTouched) {
                    rc.colorIndex = existing.colorIndex;
                    rc.colorTouched = true;
                }
            }
            out.courses.add(rc);
        }

        // 2. 本地剩下的：custom / import 保留，jwglxt 删除
        for (Timetable.Course leftover : localByKey.values()) {
            if (Timetable.SOURCE_JWGLXT.equals(leftover.source)) {
                Log.i(TAG, "教务已取消，删除课程: " + leftover.name);
            } else {
                out.courses.add(leftover);
            }
        }

        return out;
    }

    private static boolean sameSemester(Timetable a, Timetable b) {
        return a != null && b != null && a.semester != null && b.semester != null
                && a.semester.id != null && a.semester.id.equals(b.semester.id);
    }

    // ---------- 导入导出 ----------

    /** 导出为 JSON 字符串（用于写文件/分享） */
    public static String exportJson(Timetable t) throws JSONException {
        return t.toJson().toString(2);
    }

    /** 从 JSON 字符串导入。版本过高时抛 JSONException */
    public static Timetable importJson(String json) throws JSONException {
        return Timetable.fromJson(json);
    }

    // ---------- 内部 ----------

    private static Timetable readMirror(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return null;
        try {
            return Timetable.fromJson(readAll(new FileInputStream(f)));
        } catch (Exception e) {
            Log.e(TAG, "timetable.json 镜像损坏，忽略", e);
            return null;
        }
    }

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    /** 便捷：按周+星期筛选，供 UI 层调用 */
    public static List<Timetable.Course> coursesAt(Timetable t, int dayOfWeek, int week) {
        if (t == null) return new ArrayList<>();
        return t.coursesOn(dayOfWeek, week);
    }
}
