package com.ecut.campus.data;

import android.content.Context;
import android.util.Log;

import com.ecut.campus.Prefs;
import com.ecut.campus.WeekMath;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多学期课表归档：filesDir/timetable_semesters.json。
 *
 * 结构：
 *   { schemaVersion, activeSemesterId, timetables: [Timetable...] }
 *
 * 这个文件是开学日期/总周数/多学期课表的唯一可写主源。
 * timetable.json 是由这里刷新的当前学期只读镜像，Prefs 是兼容缓存。
 */
public final class SemesterArchive {

    public static final int SCHEMA_VERSION = 1;
    private static final String TAG = "SemesterArchive";
    private static final String FILE_NAME = "timetable_semesters.json";
    private static final String TMP_NAME = "timetable_semesters.json.tmp";
    private static final String KEY_ACTIVE = "activeSemesterId";

    private SemesterArchive() {}

    // ---------- 归档条目 ----------

    public static final class Entry {
        public String id = "";
        public String name = "";
        public String startDate = "";
        public int totalWeeks = 20;
        public boolean custom;
        public int courseCount;
        public int sortOrder = 0;

        public String displayName() {
            return (name == null || name.isEmpty()) ? id : name;
        }
    }

    // ---------- 读取 ----------

    public static List<Entry> listSemesters(Context ctx) {
        List<Entry> out = new ArrayList<>();
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Timetable t;
            try { t = Timetable.fromJson(o); } catch (JSONException e) { continue; }
            Entry e = new Entry();
            e.id = t.semester.id;
            e.name = t.semester.name;
            e.startDate = t.semester.startDate;
            e.totalWeeks = t.semester.totalWeeks;
            e.custom = t.semester.id.startsWith("custom_");
            e.courseCount = t.courses.size();
            e.sortOrder = t.semester.sortOrder;
            out.add(e);
        }
        sortEntries(out);
        return out;
    }

    /**
     * 显示顺序：用户拖过序（sortOrder>0）按序在前；未排过序的学期（0）
     * 视为最大排最后，同序时按 id 倒序；全为 0（老数据）退回默认倒序。
     */
    static void sortEntries(List<Entry> list) {
        boolean anyOrder = false;
        for (Entry e : list) if (e.sortOrder != 0) { anyOrder = true; break; }
        if (anyOrder) {
            list.sort((a, b) -> {
                int sa = a.sortOrder == 0 ? Integer.MAX_VALUE : a.sortOrder;
                int sb = b.sortOrder == 0 ? Integer.MAX_VALUE : b.sortOrder;
                if (sa != sb) return Integer.compare(sa, sb);
                return b.id.compareTo(a.id);
            });
        } else {
            sortSemesters(list);
        }
    }

    /** 持久化用户拖动后的学期顺序（按传入顺序写 sortOrder，其余字段不动）。 */
    public static void reorderSave(Context ctx, List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optJSONObject("semester") == null) continue;
            Timetable t;
            try { t = Timetable.fromJson(o); } catch (JSONException e) { continue; }
            int idx = orderedIds.indexOf(t.semester.id);
            if (idx >= 0) {
                t.semester.sortOrder = idx + 1;
                try { arr.put(i, t.toJson()); } catch (JSONException ignored) {}
            }
        }
        writeRoot(ctx, root);
    }

    public static Timetable loadSemester(Context ctx, String semesterId) {
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                try {
                    Timetable t = Timetable.fromJson(o);
                    if (t.semester.id.equals(semesterId)) return t;
                } catch (JSONException ignored) {}
            }
        }
        return null;
    }

    public static String activeSemesterId(Context ctx) {
        return readRoot(ctx).optString(KEY_ACTIVE, "");
    }

    public static boolean contains(Context ctx, String semesterId) {
        return loadSemester(ctx, semesterId) != null;
    }

    // ---------- 写入 ----------

    /**
     * 写入/更新一个学期。makeActive=true 时同时切换 active 指针。
     * makeActive=false 只更新归档已有学期；空归档由 saveCurrentSemester 初始化。
     */
    public static boolean saveSemester(Context ctx, Timetable t, boolean makeActive) {
        if (t == null) return false;
        if (t.semester.id == null || t.semester.id.isEmpty()) {
            Log.w(TAG, "semester.id 为空，跳过归档");
            return false;
        }
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) arr = new JSONArray();
        if (!makeActive && arr.length() == 0) {
            Log.w(TAG, "空归档没有当前学期，拒绝静默写入历史数据");
            return false;
        }

        List<Timetable> all = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try { all.add(Timetable.fromJson(o)); } catch (JSONException ignored) {}
        }
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).semester.id.equals(t.semester.id)) {
                all.set(i, t);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(t);

        JSONArray out = new JSONArray();
        for (Timetable item : all) {
            try { out.put(item.toJson()); } catch (JSONException e) {
                Log.e(TAG, "归档条目序列化失败", e);
                return false;
            }
        }
        try {
            root.put("timetables", out);
            if (makeActive) {
                root.put(KEY_ACTIVE, t.semester.id);
            }
        } catch (JSONException e) {
            Log.e(TAG, "归档序列化失败", e);
            return false;
        }
        boolean saved = writeRoot(ctx, root);
        if (!saved) return false;
        if (root.optString(KEY_ACTIVE, "").equals(t.semester.id)) {
            syncActiveMirrors(ctx, t);
        }
        return true;
    }

    /**
     * 保存当前学期。首次安装且归档为空时，用这份课表初始化主源；
     * 历史学期同步仍必须调用 saveSemester(..., false)，不能抢占 active。
     */
    public static boolean saveCurrentSemester(Context ctx, Timetable t) {
        if (t == null) return false;
        if (t.semester.id == null || t.semester.id.isEmpty()) {
            Log.w(TAG, "semester.id 为空，跳过归档");
            return false;
        }
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        String activeId = root.optString(KEY_ACTIVE, "");
        boolean hasValidActive = arr != null && contains(arr, activeId);
        if (!hasValidActive) return saveSemester(ctx, t, true);
        if (activeId.equals(t.semester.id)) return saveSemester(ctx, t, true);
        Log.w(TAG, "保存对象不是当前学期，拒绝覆盖当前镜像");
        return false;
    }

    public static void setActiveSemester(Context ctx, String semesterId) {
        JSONObject root = readRoot(ctx);
        try { root.put(KEY_ACTIVE, semesterId == null ? "" : semesterId); } catch (JSONException ignored) {}
        writeRoot(ctx, root);
    }

    /** 新建一个空的自定义学期并设为当前。 */
    public static Timetable createCustomSemester(Context ctx, String name) {
        Timetable t = new Timetable();
        t.semester.id = "custom_" + System.currentTimeMillis();
        t.semester.name = (name == null || name.trim().isEmpty())
                ? "自定义学期" : name.trim();
        t.semester.startDate = todayDate();
        t.semester.totalWeeks = SemesterMetaPolicy.DEFAULT_WEEKS;
        t.semester.startDateTouched = true;
        saveSemester(ctx, t, true);
        return t;
    }

    public static boolean deleteSemester(Context ctx, String semesterId) {
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return false;
        JSONArray out = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Timetable t;
            try { t = Timetable.fromJson(o); } catch (JSONException e) { continue; }
            if (t.semester.id.equals(semesterId)) { removed = true; continue; }
            out.put(o);
        }
        if (!removed) return false;
        try {
            root.put("timetables", out);
            if (semesterId != null && semesterId.equals(root.optString(KEY_ACTIVE, ""))) {
                String fallback = "";
                if (out.length() > 0) {
                    JSONObject first = out.optJSONObject(0);
                    if (first != null) {
                        fallback = first.optJSONObject("semester") == null
                                ? "" : first.optJSONObject("semester").optString("id", "");
                    }
                }
                root.put(KEY_ACTIVE, fallback);
            }
        } catch (JSONException e) {
            Log.e(TAG, "删除学期序列化失败", e);
            return false;
        }
        return writeRoot(ctx, root);
    }

    /**
     * 用户显式保存「开学日期 + 总周数」。
     * 只能由设置页调用；读取界面不得调用，否则镜像值会反写主源。
     */
    public static boolean updateActiveSemesterMeta(
            Context ctx, String startDate, int totalWeeks) {
        String activeId = activeSemesterId(ctx);
        if (activeId == null || activeId.isEmpty()) return false;
        Timetable t = loadSemester(ctx, activeId);
        if (t == null) return false;
        String normalized = WeekMath.normalizeToMonday(startDate);
        if (normalized.isEmpty()) return false;
        t.semester.startDate = normalized;
        t.semester.totalWeeks = SemesterMetaPolicy.clampWeeks(totalWeeks);
        t.semester.startDateTouched = true;
        return saveSemester(ctx, t, true);
    }

    /**
     * 主源写入后镜像刷新失败时，从主源再刷新一次；
     * 返回最终镜像是否与主源一致。
     */
    public static boolean ensureActiveMirror(Context ctx) {
        String activeId = activeSemesterId(ctx);
        if (activeId == null || activeId.isEmpty()) return false;
        Timetable t = loadSemester(ctx, activeId);
        if (t == null) return false;
        syncActiveMirrors(ctx, t);
        Timetable mirror = TimetableStore.readActiveMirror(ctx);
        if (mirror == null || !activeId.equals(mirror.semester.id)) return false;
        String expectedStart = SemesterMetaPolicy.resolveStartDate(t.semester.startDate);
        int expectedWeeks = SemesterMetaPolicy.resolveTotalWeeks(t.semester.totalWeeks);
        return expectedStart.equals(SemesterMetaPolicy.resolveStartDate(
                        mirror.semester.startDate))
                && expectedWeeks == SemesterMetaPolicy.resolveTotalWeeks(
                        mirror.semester.totalWeeks)
                && expectedStart.equals(Prefs.semesterStartDate(ctx))
                && expectedWeeks == Prefs.totalWeeks(ctx);
    }

    /**
     * 清理历史污染：未手动设置日期的教务学期 startDate 全部清空，
     * 让各学期回到「未设置 → 显示第 1 周」的状态，等用户按需设置。
     */
    public static void clearUnsetStartDates(Context ctx) {
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return;
        String activeId = root.optString(KEY_ACTIVE, "");
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject sem = arr.optJSONObject(i) == null
                    ? null : arr.optJSONObject(i).optJSONObject("semester");
            if (sem == null) continue;
            String id = sem.optString("id", "");
            String start = sem.optString("startDate", "");
            boolean touched = sem.has("startDateTouched");
            // 活动学期的日期是用户在设置页设置的，即使没有标记也保留
            boolean isActive = id.equals(activeId);
            if (!touched && !isActive && !id.startsWith("custom_") && !start.isEmpty()) {
                sem.remove("startDate");
                try { sem.put("startDateTouched", false); } catch (JSONException ignored) {}
                changed = true;
            }
        }
        if (changed) writeRoot(ctx, root);
    }

    /**
     * 一次性迁移：修复旧版本把「当前学期 2026-09-07」扩散到全部历史学期的问题。
     * 只有 2026-2027-1 的 9/7 是用户手动设置的真实日期，其余教务学期的同日期
     * 一律是污染值，清空后回到「未设置 → 第 1 周」。
     */
    public static void migratePollutedStartDates(Context ctx) {
        android.content.SharedPreferences guard = ctx.getSharedPreferences(
                "semester_archive_meta", Context.MODE_PRIVATE);
        if (guard.getBoolean("pollution_fixed_v2", false)) return;
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return;
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject sem = arr.optJSONObject(i) == null
                    ? null : arr.optJSONObject(i).optJSONObject("semester");
            if (sem == null) continue;
            String id = sem.optString("id", "");
            if (id.isEmpty() || id.startsWith("custom_")) continue;
            if (sem.optBoolean("startDateTouched", false)) continue;
            if ("2026-2027-1".equals(id)) {
                try {
                    sem.put("startDate", "2026-09-07");
                    sem.put("startDateTouched", true);
                    changed = true;
                } catch (JSONException ignored) {}
            } else if ("2026-09-07".equals(sem.optString("startDate", ""))) {
                sem.remove("startDate");
                try { sem.put("startDateTouched", false); } catch (JSONException ignored) {}
                changed = true;
            }
        }
        if (changed) writeRoot(ctx, root);
        guard.edit().putBoolean("pollution_fixed_v2", true).apply();
    }

    /**
     * 以归档为唯一事实来源刷新镜像和 Prefs 兼容缓存。
     * 此方法只读主源、只写镜像，绝不反向写归档。
     */
    public static void reconcileActiveMeta(Context ctx) {
        String activeId = activeSemesterId(ctx);
        if (activeId == null || activeId.isEmpty()) return;
        Timetable t = loadSemester(ctx, activeId);
        if (t == null) return;
        Prefs.setCurrentWeek(ctx, t.semester.startDate == null || t.semester.startDate.isEmpty()
                ? 1 : WeekMath.currentWeek(t.semester.startDate,
                        SemesterMetaPolicy.clampWeeks(t.semester.totalWeeks)));
        syncActiveMirrors(ctx, t);
    }

    public static void clear(Context ctx) {
        new File(ctx.getFilesDir(), FILE_NAME).delete();
        new File(ctx.getFilesDir(), TMP_NAME).delete();
    }

    // ---------- timetable.json 镜像同步 ----------

    /** 活动学期发生变化时，把归档中该学期写回 timetable.json 与 Prefs 镜像。 */
    public static void syncActiveMirror(Context ctx, String semesterId) {
        Timetable t = loadSemester(ctx, semesterId);
        if (t != null) syncActiveMirrors(ctx, t);
    }

    /** 主源 → 只读镜像。除这里和 TimetableStore 内部外，不应有其他写入路径。 */
    private static void syncActiveMirrors(Context ctx, Timetable t) {
        TimetableStore.writeMirrorFromArchive(ctx, t);
        Prefs.setSemesterStartDate(ctx, SemesterMetaPolicy.resolveStartDate(t.semester.startDate));
        Prefs.setTotalWeeks(ctx, SemesterMetaPolicy.resolveTotalWeeks(t.semester.totalWeeks));
    }

    /**
     * 首次升级到多学期主源时，把旧 timetable.json 的课程与元数据并入归档。
     * 只补缺失字段，不覆盖归档已有的日期/周数。
     */
    public static void importLegacyMirror(Context ctx, Timetable mirror) {
        if (mirror == null || mirror.semester == null
                || mirror.semester.id == null || mirror.semester.id.isEmpty()) return;
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) arr = new JSONArray();
        Timetable existing = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                Timetable t = Timetable.fromJson(o);
                if (mirror.semester.id.equals(t.semester.id)) {
                    existing = t;
                    break;
                }
            } catch (JSONException ignored) {}
        }
        if (existing == null) {
            // 老用户首次启动：镜像就是当前学期，直接建主源条目。
            if (mirror.semester.startDate != null
                    && !mirror.semester.startDate.isEmpty()) {
                mirror.semester.startDateTouched = true;
            }
            if (arr.length() == 0) {
                saveSemesterAsSource(ctx, mirror);
            } else {
                // 归档已有其他学期时只追加，不覆盖、不改 active。
                saveSemester(ctx, mirror, false);
            }
            return;
        }
        JSONObject existingJson = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            JSONObject sem = o.optJSONObject("semester");
            if (sem != null && mirror.semester.id.equals(sem.optString("id", ""))) {
                existingJson = sem;
                break;
            }
        }
        boolean changed = false;
        if (SemesterMetaPolicy.shouldSeedStartDate(existing.semester.id,
                mirror.semester.id, existing.semester.startDate,
                mirror.semester.startDate)) {
            existing.semester.startDate = mirror.semester.startDate == null
                    ? "" : mirror.semester.startDate;
            existing.semester.startDateTouched = true;
            changed = true;
        }
        boolean archiveHasTotalWeeks = existingJson != null && existingJson.has("totalWeeks");
        if (SemesterMetaPolicy.shouldSeedTotalWeeks(existing.semester.id,
                mirror.semester.id, archiveHasTotalWeeks, mirror.semester.totalWeeks)) {
            existing.semester.totalWeeks = SemesterMetaPolicy.clampWeeks(
                    mirror.semester.totalWeeks);
            changed = true;
        }
        if (existing.courses.isEmpty() && !mirror.courses.isEmpty()) {
            existing.courses.addAll(mirror.courses);
            changed = true;
        }
        if (changed) saveSemester(ctx, existing, false);
    }

    /**
     * 把已知的课程配色映射应用到归档内所有教务学期，保证「同名同色」跨学期。
     */
    public static void propagateColors(Context ctx, Map<String, Integer> knownColors) {
        if (knownColors == null || knownColors.isEmpty()) return;
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return;
        boolean changed = false;
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                Timetable t = Timetable.fromJson(o);
                CourseColorAssigner.applyKnown(t, knownColors);
                out.put(t.toJson());
                changed = true;
            } catch (JSONException e) {
                Log.w(TAG, "配色传播跳过一个学期", e);
            }
        }
        if (changed) {
            try { root.put("timetables", out); } catch (JSONException ignored) {}
            writeRoot(ctx, root);
            String activeId = root.optString(KEY_ACTIVE, "");
            if (!activeId.isEmpty()) syncActiveMirror(ctx, activeId);
        }
    }

    /**
     * 跨所有学期统一配色：同名课程同色，不同课程名尽量不同色。
     * 手动课程（custom/import）不参与自动分配。
     */
    public static void unifyColors(Context ctx) {
        JSONObject root = readRoot(ctx);
        JSONArray arr = root.optJSONArray("timetables");
        if (arr == null) return;

        // 第一遍：收集全部教务课程名，按首次出现顺序建映射
        Map<String, Integer> knownColors = new LinkedHashMap<>();
        int paletteSize = CourseColorAssigner.PALETTE_SIZE;
        List<Timetable> all = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                Timetable t = Timetable.fromJson(o);
                all.add(t);
                for (Timetable.Course c : t.courses) {
                    if (!Timetable.SOURCE_JWGLXT.equals(c.source)) continue;
                    String key = CourseColorAssigner.normalizeName(c.name);
                    if (!key.isEmpty() && !knownColors.containsKey(key)) {
                        knownColors.put(key, 0); // 占位，下一步统一分配
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "unifyColors 读取失败跳过一个学期", e);
            }
        }

        // 统一分配：第 N 个新名字拿第 N 个未占用色
        boolean[] used = new boolean[paletteSize];
        int cursor = 0;
        for (String key : knownColors.keySet()) {
            int pick = cursor % paletteSize;
            if (used[pick]) {
                for (int j = 0; j < paletteSize; j++) {
                    if (!used[j]) { pick = j; break; }
                }
            }
            used[pick] = true;
            knownColors.put(key, pick);
            cursor++;
        }

        // 第二遍：应用并写回
        boolean changed = false;
        JSONArray out = new JSONArray();
        for (Timetable t : all) {
            CourseColorAssigner.applyKnown(t, knownColors);
            try { out.put(t.toJson()); } catch (JSONException e) {
                Log.w(TAG, "unifyColors 序列化失败跳过一个学期", e);
                continue;
            }
            changed = true;
        }
        if (changed) {
            try { root.put("timetables", out); } catch (JSONException ignored) {}
            writeRoot(ctx, root);
            // 当前学期镜像也要刷新
            String activeId = root.optString(KEY_ACTIVE, "");
            if (!activeId.isEmpty()) syncActiveMirror(ctx, activeId);
        }
    }

    // ---------- 读写 ----------

    private static JSONObject readRoot(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return new JSONObject();
        try (InputStream is = new FileInputStream(f)) {
            String json = readAll(is);
            return new JSONObject(json);
        } catch (Exception e) {
            Log.e(TAG, "timetable_semesters.json 读取失败，视为空归档", e);
            return new JSONObject();
        }
    }

    /** 只有课表列表为空时，才允许以当前课表初始化主源。 */
    private static boolean saveSemesterAsSource(Context ctx, Timetable t) {
        JSONObject root = readRoot(ctx);
        try {
            root.put(KEY_ACTIVE, t.semester.id);
            root.put("timetables", new JSONArray().put(t.toJson()));
        } catch (JSONException e) {
            Log.e(TAG, "初始化归档主源失败", e);
            return false;
        }
        if (!writeRoot(ctx, root)) return false;
        syncActiveMirrors(ctx, t);
        return true;
    }

    private static boolean contains(JSONArray arr, String semesterId) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject sem = arr.optJSONObject(i) == null
                    ? null : arr.optJSONObject(i).optJSONObject("semester");
            if (sem != null && semesterId.equals(sem.optString("id", ""))) return true;
        }
        return false;
    }

    private static boolean writeRoot(Context ctx, JSONObject root) {
        File dir = ctx.getFilesDir();
        File tmp = new File(dir, TMP_NAME);
        File dst = new File(dir, FILE_NAME);
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.getFD().sync();
            }
            if (dst.exists() && !dst.delete()) {
                Log.w(TAG, "旧归档删除失败，继续覆盖");
            }
            if (!tmp.renameTo(dst)) {
                Log.e(TAG, "归档 rename 失败");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存归档失败", e);
            if (tmp.exists()) tmp.delete();
            return false;
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

    /** 学期按 id 倒序（新学期在前），id 无法排序时按名称。 */
    private static void sortSemesters(List<Entry> list) {
        list.sort((a, b) -> {
            if (a.custom != b.custom) return a.custom ? 1 : -1;
            return b.id.compareTo(a.id);
        });
    }

    private static String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
