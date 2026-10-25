package com.ecut.campus;

import android.content.Context;
import android.content.SharedPreferences;

import com.ecut.campus.data.SemesterMetaPolicy;

/** 简单偏好设置封装 */
public final class Prefs {

    private static final String FILE = "ecut_campus";
    private static final String KEY_BLOCK_OBSERVE = "block_observe";
    private static final String KEY_PORTAL_HOST = "portal_host";
    private static final String KEY_PORTAL_SESSION = "portal_session_ready";
    private static final String KEY_TARGETS_JSON = "targets_json";
    private static final String KEY_CURRENT_WEEK = "current_week";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_DISPLAY_DAYS = "display_days";
    private static final String KEY_SHOW_INACTIVE = "show_inactive_courses";
    private static final String KEY_SEMESTER_START = "semester_start_date";
    private static final String KEY_TOTAL_WEEKS = "semester_total_weeks";
    private static final String KEY_GRADES_DEMO = "grades_demo";

    private Prefs() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 是否同时拦截统计/埋点类（observe）域名，默认关闭以求稳 */
    // ===== 1.0：检查更新 =====
    public static boolean updateAutoCheck(Context ctx) {
        return sp(ctx).getBoolean("update_auto", true);
    }

    public static void setUpdateAutoCheck(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean("update_auto", on).apply();
    }

    public static long updateCheckedAt(Context ctx) {
        return sp(ctx).getLong("update_checked_at", 0L);
    }

    public static void setUpdateCheckedAt(Context ctx, long t) {
        sp(ctx).edit().putLong("update_checked_at", t).apply();
    }

    public static String updateSkippedTag(Context ctx) {
        return sp(ctx).getString("update_skipped", "");
    }

    public static void setUpdateSkippedTag(Context ctx, String tag) {
        sp(ctx).edit().putString("update_skipped", tag).apply();
    }

    public static boolean blockObserve(Context ctx) {
        return sp(ctx).getBoolean(KEY_BLOCK_OBSERVE, false);
    }

    public static void setBlockObserve(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean(KEY_BLOCK_OBSERVE, v).apply();
    }

    /** 缓存的教务代理主机（运行时从页面抓取后保存） */
    public static String portalHost(Context ctx) {
        return sp(ctx).getString(KEY_PORTAL_HOST, null);
    }

    public static void setPortalHost(Context ctx, String host) {
        sp(ctx).edit().putString(KEY_PORTAL_HOST, host).apply();
    }

    public static void clearPortalHost(Context ctx) {
        sp(ctx).edit().remove(KEY_PORTAL_HOST).apply();
    }

    /** 是否已经在设置页完成统一门户/办事大厅会话初始化 */
    public static boolean hasPortalSession(Context ctx) {
        return sp(ctx).getBoolean(KEY_PORTAL_SESSION, false);
    }

    public static void setPortalSession(Context ctx, boolean ready) {
        sp(ctx).edit().putBoolean(KEY_PORTAL_SESSION, ready).apply();
    }

    /** 直达目标配置表（JSON 数组字符串） */
    public static String targetsJson(Context ctx) {
        return sp(ctx).getString(KEY_TARGETS_JSON, null);
    }

    public static void setTargetsJson(Context ctx, String json) {
        sp(ctx).edit().putString(KEY_TARGETS_JSON, json).apply();
    }

    /** 用户当前查看/锚定的教学周 */
    public static int currentWeek(Context ctx) {
        return sp(ctx).getInt(KEY_CURRENT_WEEK, 1);
    }

    public static boolean hasCurrentWeek(Context ctx) {
        return sp(ctx).contains(KEY_CURRENT_WEEK);
    }

    public static void setCurrentWeek(Context ctx, int week) {
        sp(ctx).edit().putInt(KEY_CURRENT_WEEK,
                SemesterMetaPolicy.clampWeeks(week)).apply();
    }

    /** 上次同步成功的时间戳文本 */
    public static String lastSyncTime(Context ctx) {
        return sp(ctx).getString(KEY_LAST_SYNC_TIME, "");
    }

    public static void setLastSyncTime(Context ctx, String text) {
        sp(ctx).edit().putString(KEY_LAST_SYNC_TIME, text == null ? "" : text).apply();
    }

    /** 课表显示天数：7 或 5，默认七天 */
    public static int displayDays(Context ctx) {
        return sp(ctx).getInt(KEY_DISPLAY_DAYS, 7) == 5 ? 5 : 7;
    }

    public static void setDisplayDays(Context ctx, int days) {
        sp(ctx).edit().putInt(KEY_DISPLAY_DAYS, days == 5 ? 5 : 7).apply();
    }

    /** 是否显示非本周课程，默认关闭 */
    public static boolean showInactiveCourses(Context ctx) {
        return sp(ctx).getBoolean(KEY_SHOW_INACTIVE, false);
    }

    public static void setShowInactiveCourses(Context ctx, boolean show) {
        sp(ctx).edit().putBoolean(KEY_SHOW_INACTIVE, show).apply();
    }

    /**
     * 兼容缓存：主源是 timetable_semesters.json。
     * 只允许 SemesterArchive 刷新，业务代码应读写归档。
     */
    public static String semesterStartDate(Context ctx) {
        return sp(ctx).getString(KEY_SEMESTER_START, "");
    }

    /** 仅供 SemesterArchive 镜像同步；业务代码不要调用。 */
    public static void setSemesterStartDate(Context ctx, String date) {
        sp(ctx).edit().putString(KEY_SEMESTER_START, date == null ? "" : date).apply();
    }

    /**
     * 界面上可用的周跨度。不再让用户设定「学期总周数」：
     * 只看开学日期，周次一直往后排（见 WeekMath.openWeekSpan）。
     */
    public static int totalWeeks(Context ctx) {
        return WeekMath.openWeekSpan(WeekMath.normalizeToMonday(semesterStartDate(ctx)));
    }

    /** 归档里存的旧周数字段，只用于原样回写，不参与界面。 */
    public static int storedTotalWeeks(Context ctx) {
        return SemesterMetaPolicy.clampWeeks(
                sp(ctx).getInt(KEY_TOTAL_WEEKS, SemesterMetaPolicy.DEFAULT_WEEKS));
    }

    /** 仅供 SemesterArchive 镜像同步；业务代码不要调用。 */
    public static void setTotalWeeks(Context ctx, int weeks) {
        sp(ctx).edit().putInt(KEY_TOTAL_WEEKS,
                SemesterMetaPolicy.clampWeeks(weeks)).apply();
    }

    /** 当前成绩是否为主动载入的演示数据 */
    public static boolean isGradesDemo(Context ctx) {
        return sp(ctx).getBoolean(KEY_GRADES_DEMO, false);
    }

    public static void setGradesDemo(Context ctx, boolean demo) {
        sp(ctx).edit().putBoolean(KEY_GRADES_DEMO, demo).apply();
    }

    // ===== 0.4：外观、同步状态、新成绩 =====
    /** 一屏显示全部 10 节（压缩课块高度）。 */
    public static boolean fitAllSections(Context ctx) { return sp(ctx).getBoolean("fit_all_sections", false); }
    public static void setFitAllSections(Context ctx, boolean v) { sp(ctx).edit().putBoolean("fit_all_sections", v).apply(); }

    public static int nightMode(Context ctx) { return sp(ctx).getInt("night_mode", 0); }
    public static void setNightMode(Context ctx, int m) { sp(ctx).edit().putInt("night_mode", m).apply(); }

    /** 上次成功同步课表的时间戳（毫秒）。 */
    public static long timetableSyncedAt(Context ctx) { return sp(ctx).getLong("tt_synced_at", 0L); }
    public static void setTimetableSyncedAt(Context ctx, long t) { sp(ctx).edit().putLong("tt_synced_at", t).apply(); }

    /** 门户/零信任登录是否已失效（同步时检测到跳回登录页）。 */
    public static boolean sessionExpired(Context ctx) { return sp(ctx).getBoolean("session_expired", false); }
    public static void setSessionExpired(Context ctx, boolean v) { sp(ctx).edit().putBoolean("session_expired", v).apply(); }

    /** 新出、尚未查看的成绩 key（GradeDiff.key），用 \n 分隔。 */
    public static java.util.Set<String> newGradeKeys(Context ctx) {
        String raw = sp(ctx).getString("new_grade_keys", "");
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String k : raw.split("\n")) if (!k.isEmpty()) set.add(k);
        return set;
    }
    public static void setNewGradeKeys(Context ctx, java.util.Collection<String> keys) {
        sp(ctx).edit().putString("new_grade_keys", String.join("\n", keys)).apply();
    }

    public static boolean gradeNotify(Context ctx) { return sp(ctx).getBoolean("grade_notify", true); }
    public static void setGradeNotify(Context ctx, boolean v) { sp(ctx).edit().putBoolean("grade_notify", v).apply(); }

    public static String calendarImageUrl(Context ctx) { return sp(ctx).getString("calendar_img", ""); }
    public static void setCalendarImageUrl(Context ctx, String u) { sp(ctx).edit().putString("calendar_img", u).apply(); }

    public static String shareName(Context ctx) { return sp(ctx).getString("share_name", ""); }
    public static void setShareName(Context ctx, String n) { sp(ctx).edit().putString("share_name", n).apply(); }
}
