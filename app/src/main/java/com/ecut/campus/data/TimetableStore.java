package com.ecut.campus.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课表本地存储。
 *
 * 学校目录内的 {@code timetable_semesters.json} 是多学期主源；
 * {@code timetable.json} 只是当前学期镜像，供旧调用方读取。
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
    /** Import parser limits: large enough for multi-semester-sized personal schedules, bounded for hostile files. */
    public static final int MAX_IMPORT_CHARS = 2 * 1024 * 1024;
    public static final int MAX_IMPORT_COURSES = 500;
    public static final int MAX_IMPORT_SESSIONS_PER_COURSE = 100;
    public static final int MAX_IMPORT_SESSIONS = 10_000;
    public static final int MAX_IMPORT_ADJUSTS = 1_000;
    private static final int MAX_IMPORT_JSON_DEPTH = 64;
    private static final int MAX_IMPORT_STRING_CHARS = 4096;
    private static final int MAX_IMPORT_COURSE_NAME_CHARS = 256;
    private static final int MAX_IMPORT_SESSION_TEXT_CHARS = 512;

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
        if (mirror != null && !SemesterArchive.isInitialized(ctx)) {
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
        try {
            AtomicFileStore.write(com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME), t.toJson().toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存课表失败", e);
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

        // 1. 教务返回的课程：覆盖内容，保留用户的 note 和颜色。
        // 颜色按课程沿用（对不上 key 就按课程名），CourseColorAssigner 只给新课和撞色的课换色，
        // 所以同步一次颜色不会整体挪位。
        Map<String, Integer> colorByName = new java.util.HashMap<>();
        if (local != null) {
            for (Timetable.Course c : local.courses) {
                colorByName.putIfAbsent(CourseColorAssigner.normalizeName(c.name), c.colorIndex);
            }
        }
        for (Timetable.Course rc : remote.courses) {
            Timetable.Course existing = localByKey.remove(rc.key);
            if (existing != null) {
                rc.note = existing.note;
                rc.colorIndex = existing.colorIndex;
                if (existing.colorTouched) rc.colorTouched = true;
            } else {
                Integer known = colorByName.get(CourseColorAssigner.normalizeName(rc.name));
                if (known != null) rc.colorIndex = known;
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

    /** 文件导入严格校验；磁盘旧数据读取仍使用兼容解析器。 */
    public static Timetable importJson(String json) throws JSONException {
        if (json == null || json.length() > MAX_IMPORT_CHARS) {
            throw new JSONException("课表文件超过大小限制");
        }
        ensureBoundedJsonNesting(json);
        JSONTokener tokener = new JSONTokener(json);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0) {
            throw new JSONException("课表必须是单个 JSON 对象");
        }
        JSONObject root = (JSONObject) value;
        if (integer(root, "schemaVersion", 1, Timetable.SCHEMA_VERSION) != Timetable.SCHEMA_VERSION) {
            throw new JSONException("不支持的课表版本");
        }
        JSONObject semester = root.getJSONObject("semester");
        requiredString(semester, "id");
        fields(semester, "id name startDate", "totalWeeks sortOrder", "startDateTouched");
        if (semester.has("totalWeeks")) integer(semester, "totalWeeks", 1, 30);
        JSONArray courses = root.getJSONArray("courses");
        if (courses.length() > MAX_IMPORT_COURSES) throw new JSONException("课程数量超过限制");
        java.util.Set<String> keys = new java.util.HashSet<>();
        int sessionCount = 0;
        for (int i = 0; i < courses.length(); i++) {
            JSONObject c = courses.getJSONObject(i);
            String key = requiredString(c, "key");
            if (!keys.add(key)) throw new JSONException("重复的课程 key：" + key);
            String courseName = requiredString(c, "name");
            if (courseName.length() > MAX_IMPORT_COURSE_NAME_CHARS) {
                throw new JSONException("课程名称过长");
            }
            fields(c, "key name teacher category examType source note", "colorIndex", "colorTouched");
            if (c.has("credit") && !(c.get("credit") instanceof Number)) {
                throw new JSONException("credit 必须是数字");
            }
            if (c.has("source") && !java.util.Arrays.asList(Timetable.SOURCE_JWGLXT,
                    Timetable.SOURCE_CUSTOM, Timetable.SOURCE_IMPORT).contains(c.getString("source"))) {
                throw new JSONException("未知的课程来源");
            }
            JSONArray sessions = c.getJSONArray("sessions");
            if (sessions.length() > MAX_IMPORT_SESSIONS_PER_COURSE
                    || sessionCount > MAX_IMPORT_SESSIONS - sessions.length()) {
                throw new JSONException("课时时段数量超过限制");
            }
            sessionCount += sessions.length();
            for (int j = 0; j < sessions.length(); j++) {
                JSONObject s = sessions.getJSONObject(j);
                integer(s, "dayOfWeek", 1, 7);
                int start = integer(s, "startSection", 1, 99);
                integer(s, "endSection", start, 99);
                fields(s, "weeksRaw location campus roomType teacher timeStart timeEnd", "", "");
                JSONArray weeks = s.getJSONArray("weeks");
                if (weeks.length() > WeekRange.MAX_WEEK) throw new JSONException("周次数量超过限制");
                for (int w = 0; w < weeks.length(); w++) {
                    Object week = weeks.get(w);
                    if (!(week instanceof Number) || ((Number) week).doubleValue() < 1
                            || ((Number) week).doubleValue() > 99
                            || ((Number) week).doubleValue() != ((Number) week).intValue()) {
                        throw new JSONException("周次必须是 1-99 的整数");
                    }
                }
                if (s.has("timeStart") || s.has("timeEnd")) {
                    if (Timetable.Session.minutesOf(s.optString("timeStart")) < 0
                            || Timetable.Session.minutesOf(s.optString("timeEnd"))
                            <= Timetable.Session.minutesOf(s.optString("timeStart"))) {
                        throw new JSONException("自定义时间无效");
                    }
                }
                if (s.has("weekTeachers")) {
                    JSONObject teachers = s.getJSONObject("weekTeachers");
                    if (teachers.length() > WeekRange.MAX_WEEK) throw new JSONException("教师周次数量超过限制");
                    java.util.Iterator<String> it = teachers.keys();
                    while (it.hasNext()) {
                        String week = it.next();
                        try {
                            int n = Integer.parseInt(week);
                            if (n < 1 || n > 99) throw new NumberFormatException();
                        } catch (NumberFormatException e) { throw new JSONException("教师周次无效"); }
                        Object teacher = teachers.get(week);
                        if (!(teacher instanceof String)
                                || ((String) teacher).length() > MAX_IMPORT_SESSION_TEXT_CHARS) {
                            throw new JSONException("教师必须是长度受限的文本");
                        }
                    }
                }
            }
        }
        if (root.has("adjusts")) {
            JSONArray adjusts = root.getJSONArray("adjusts");
            if (adjusts.length() > MAX_IMPORT_ADJUSTS) throw new JSONException("调课数量超过限制");
            for (int i = 0; i < adjusts.length(); i++) {
                JSONObject a = adjusts.getJSONObject(i);
                requiredString(a, "id");
                String type = requiredString(a, "type");
                if (!java.util.Arrays.asList("cancel", "move", "off", "makeup").contains(type)) {
                    throw new JSONException("未知的调课类型");
                }
                fields(a, "id type name toTimeStart toTimeEnd toLocation date fromDate note",
                        "week day start end toWeek toDay toStart toEnd", "");
            }
        }
        return Timetable.fromJson(root);
    }

    /** Check nesting before JSONObject/JSONTokener recurse into caller-controlled input. */
    static void ensureBoundedJsonNesting(String json) throws JSONException {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (++depth > MAX_IMPORT_JSON_DEPTH) throw new JSONException("课表 JSON 嵌套过深");
            } else if (c == '}' || c == ']') {
                if (--depth < 0) throw new JSONException("课表 JSON 结构无效");
            }
        }
    }

    /** 文件备份是增量合并，不具有教务全量同步的删除权限；跨学期拒绝。 */
    public static Timetable mergeImport(Timetable local, Timetable imported) throws JSONException {
        Timetable out = Timetable.fromJson(local.toJson());
        Timetable incoming = Timetable.fromJson(imported.toJson());
        if (out.semester.id.isEmpty()) out.semester = incoming.semester;
        if (!out.semester.id.equals(incoming.semester.id)) {
            throw new JSONException("请先切换到备份对应的学期再导入");
        }
        Map<String, Timetable.Course> byKey = new LinkedHashMap<>();
        for (Timetable.Course c : out.courses) byKey.put(c.key, c);
        for (Timetable.Course c : incoming.courses) {
            Timetable.Course existing = byKey.get(c.key);
            c.source = existing == null ? Timetable.SOURCE_IMPORT : existing.source;
            if (existing != null) {
                c.note = existing.note;
                c.colorIndex = existing.colorIndex;
                c.colorTouched = existing.colorTouched;
            }
            byKey.put(c.key, c);
        }
        out.courses = new ArrayList<>(byKey.values());
        for (Timetable.Adjust a : incoming.adjusts) {
            boolean exists = false;
            for (Timetable.Adjust old : out.adjusts) {
                if (old.id.equals(a.id) || old.sameAs(a)) { exists = true; break; }
            }
            if (!exists) out.adjusts.add(a);
        }
        return out;
    }

    private static String requiredString(JSONObject o, String key) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()
                || ((String) value).length() > MAX_IMPORT_STRING_CHARS) {
            throw new JSONException(key + " 必须是非空文本");
        }
        return (String) value;
    }

    private static int integer(JSONObject o, String key, int min, int max) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue()
                || ((Number) value).doubleValue() < min || ((Number) value).doubleValue() > max) {
            throw new JSONException(key + " 必须是范围内的整数");
        }
        return ((Number) value).intValue();
    }

    private static void fields(JSONObject o, String strings, String ints, String bools) throws JSONException {
        for (String key : strings.split(" ")) {
            if (o.has(key)) {
                Object value = o.get(key);
                if (!(value instanceof String)) throw new JSONException(key + " 必须是文本");
                int limit = ("location".equals(key) || "campus".equals(key) || "roomType".equals(key)
                        || "teacher".equals(key)) ? MAX_IMPORT_SESSION_TEXT_CHARS : MAX_IMPORT_STRING_CHARS;
                if (((String) value).length() > limit) throw new JSONException(key + " 长度超过限制");
            }
        }
        for (String key : ints.split(" ")) {
            if (o.has(key)) integer(o, key, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        for (String key : bools.split(" ")) {
            if (o.has(key) && !(o.get(key) instanceof Boolean)) throw new JSONException(key + " 必须是布尔值");
        }
    }

    // ---------- 内部 ----------

    private static Timetable readMirror(Context ctx) {
        File f = com.ecut.campus.SchoolFiles.file(ctx, FILE_NAME);
        try {
            String json = AtomicFileStore.read(f);
            return json == null ? null : Timetable.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "timetable.json 镜像损坏，忽略", e);
            return null;
        }
    }

    /** 便捷：按周+星期筛选，供 UI 层调用 */
    public static List<Timetable.Course> coursesAt(Timetable t, int dayOfWeek, int week) {
        if (t == null) return new ArrayList<>();
        return t.coursesOn(dayOfWeek, week);
    }
}
