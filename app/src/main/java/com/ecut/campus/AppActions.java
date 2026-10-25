package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.ecut.campus.data.AppSetting;
import com.ecut.campus.data.TangliCommand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 烫梨指令里「设置 / 同步 / 导出 / 打开」在 App 里的落地。
 * 课表本身的改动在 {@link TangliCommand}；这里只管 Android 相关的部分。
 */
final class AppActions {

    private AppActions() {}

    static final String EXTRA_ACTION = "tangli_action";
    static final String EXTRA_VALUE = "tangli_value";

    /** 需要主界面才能做的事（同步、导出 ICS、切换底部页签）。 */
    interface Host {
        void runHostAction(String action, String value);
    }

    // ------------------------------------------------------------ 设置

    static Map<String, String> currentSettings(Context ctx) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(AppSetting.THEME, String.valueOf(Prefs.nightMode(ctx)));
        m.put(AppSetting.DAYS, String.valueOf(Prefs.displayDays(ctx) == 5 ? 5 : 7));
        m.put(AppSetting.INACTIVE, Prefs.showInactiveCourses(ctx) ? "1" : "0");
        m.put(AppSetting.FIT_ALL, Prefs.fitAllSections(ctx) ? "1" : "0");
        m.put(AppSetting.GRADE_NOTIFY, Prefs.gradeNotify(ctx) ? "1" : "0");
        m.put(AppSetting.UPDATE_CHECK, Prefs.updateAutoCheck(ctx) ? "1" : "0");
        m.put(AppSetting.OBSERVE, Prefs.blockObserve(ctx) ? "1" : "0");
        m.put(AppSetting.SHARE_NAME, Prefs.shareName(ctx));
        return m;
    }

    /** 写入设置。主题变化时返回 true（调用方最后再切主题，切主题会重建界面）。 */
    static boolean applySettings(Context ctx, Map<String, String> m) {
        boolean themeChanged = false;
        for (Map.Entry<String, String> e : m.entrySet()) {
            String v = e.getValue();
            boolean on = "1".equals(v);
            switch (e.getKey()) {
                case AppSetting.THEME: {
                    int mode = parse(v, 0);
                    if (mode != Prefs.nightMode(ctx)) {
                        Prefs.setNightMode(ctx, mode);
                        themeChanged = true;
                    }
                    break;
                }
                case AppSetting.DAYS:
                    Prefs.setDisplayDays(ctx, parse(v, 7) == 5 ? 5 : 7);
                    break;
                case AppSetting.INACTIVE:
                    Prefs.setShowInactiveCourses(ctx, on);
                    break;
                case AppSetting.FIT_ALL:
                    Prefs.setFitAllSections(ctx, on);
                    break;
                case AppSetting.GRADE_NOTIFY:
                    if (on != Prefs.gradeNotify(ctx)) {
                        Prefs.setGradeNotify(ctx, on);
                        GradeCheckWorker.schedule(ctx);
                    }
                    break;
                case AppSetting.UPDATE_CHECK:
                    Prefs.setUpdateAutoCheck(ctx, on);
                    break;
                case AppSetting.OBSERVE:
                    Prefs.setBlockObserve(ctx, on);
                    break;
                case AppSetting.SHARE_NAME:
                    Prefs.setShareName(ctx, v == null ? "" : v);
                    break;
                default:
                    break;
            }
        }
        return themeChanged;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return def;
        }
    }

    // ------------------------------------------------------------ 作息

    static String scheduleJson(Context ctx) {
        try {
            return ScheduleSettingsStore.load(ctx).toJson().toString();
        } catch (Exception e) {
            return "";
        }
    }

    static boolean applySections(Context ctx, int[] start, int[] end) {
        ScheduleSettings ss = ScheduleSettingsStore.load(ctx);
        ss.applyRanges(start, end);
        return ScheduleSettingsStore.save(ctx, ss);
    }

    static void restoreSchedule(Context ctx, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            ScheduleSettingsStore.save(ctx, ScheduleSettings.fromJson(json));
        } catch (Exception ignored) {
            // 撤销失败就保留当前作息
        }
    }

    // ------------------------------------------------------------ 动作

    static void run(Activity a, TangliCommand.Item it) {
        run(a, it.action, it.value);
    }

    static void run(Activity a, String action, String value) {
        switch (action) {
            case "sync":
                toHost(a, action, value);
                break;
            case "export":
                if ("diag".equals(value)) DiagLog.export(a);
                else if ("share".equals(value)) CommandUi.exportForShare(a);
                else if ("all".equals(value)) CommandUi.exportAll(a);
                else toHost(a, action, value);
                break;
            case "open":
                open(a, value);
                break;
            default:
                break;
        }
    }

    private static void open(Activity a, String what) {
        Class<?> target = null;
        switch (what) {
            case "mail": CampusLinks.openMail(a); return;
            case "library": CampusLinks.openLibrary(a); return;
            case "grades": target = GradesActivity.class; break;
            case "calendar": target = CalendarActivity.class; break;
            case "map": target = MapActivity.class; break;
            case "classmates": target = ClassmatesActivity.class; break;
            case "share": target = ShareActivity.class; break;
            case "about": target = AboutActivity.class; break;
            case "schedule": target = ScheduleSettingsActivity.class; break;
            case "ttsettings": target = TimetableSettingsActivity.class; break;
            case "debug": target = DebugActivity.class; break;
            case "settings":
            case "adblock":
                toHost(a, "open", what);
                return;
            default:
                break;
        }
        if (target == null) return;
        try {
            a.startActivity(new Intent(a, target));
        } catch (RuntimeException e) {
            Toast.makeText(a, "打不开：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void toHost(Activity a, String action, String value) {
        if (a instanceof Host) {
            ((Host) a).runHostAction(action, value);
            return;
        }
        Intent i = new Intent(a, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(EXTRA_ACTION, action);
        i.putExtra(EXTRA_VALUE, value);
        a.startActivity(i);
    }
}
