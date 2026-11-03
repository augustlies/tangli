package com.ecut.campus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Locale;

/**
 * 「让小组件自己翻页」引导：放行后台运行。
 * vivo / OPPO / 小米 / 华为等手机会把后台的 App 冻住，闹钟和兜底刷新都被压着，
 * 小组件就只能等打开 App 才刷新。代码里能做的（休眠中也叫醒、15 分钟兜底）都做了，
 * 剩下这一步只能用户在系统里点一下：不受电池优化限制 + 手机管家里的自启动 / 后台运行。
 */
final class BgAllow {
    private BgAllow() {
    }

    /** 已经「不受电池优化限制」。Android 6 以下没有这回事，算放行。 */
    static boolean ignoring(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable e) {
            return false;
        }
    }

    /** 放了小组件、还没放行、也没主动弹过：打开 App 时主动弹一次。 */
    static boolean shouldAutoShow(Context c) {
        return !Prefs.bgGuideShown(c) && ClassWidget.placedCount(c) > 0 && !ignoring(c);
    }

    static void autoShow(Activity a) {
        Prefs.setBgGuideShown(a);
        show(a);
    }

    /** 设置里那一行的副标题。 */
    static String status(Context c) {
        return ignoring(c) ? "已允许后台运行 · 上完课会自己翻页"
                : "没放行的话，有的手机要打开烫梨才刷新，点这里设置";
    }

    static void show(Activity a) {
        if (a == null || a.isFinishing()) return;
        boolean ok = ignoring(a);
        String vendor = vendorName();
        String msg = "有的手机为了省电会把后台的烫梨冻住，小组件就得等你打开烫梨才翻到下一节。放行一次就好：\n\n"
                + (ok ? "1. 允许后台运行 —— 已经允许了 ✓\n" : "1. 点「允许后台运行」，在弹窗里选「允许」\n")
                + "2. 点「去开自启动」，找到烫梨，打开「自启动」"
                + ("vivo".equals(vendor) || "iQOO".equals(vendor) ? "和「允许后台高耗电」" : "和「后台运行」")
                + "\n\n只用来按上下课时间刷新卡片，一天就几次，基本不耗电。";
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("让小组件自己翻页")
                .setMessage(msg)
                .setNeutralButton("以后再说", null)
                .setNegativeButton("去开自启动", (d, w) -> openVendor(a));
        if (!ok) b.setPositiveButton("允许后台运行", (d, w) -> requestIgnore(a));
        b.show();
    }

    static void requestIgnore(Activity a) {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + a.getPackageName()));
        if (start(a, i)) return;
        if (start(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return;
        openDetails(a);
    }

    /** 各家手机管家里的「自启动 / 后台」页；打不开就退到系统的应用详情页。 */
    static void openVendor(Activity a) {
        for (String[] cn : vendorPages()) {
            Intent i = new Intent().setComponent(new ComponentName(cn[0], cn[1]))
                    .putExtra("packagename", a.getPackageName())
                    .putExtra("package_name", a.getPackageName());
            if (start(a, i)) return;
        }
        openDetails(a);
        Toast.makeText(a, "在「电池 / 耗电」或「权限」里把烫梨设为允许后台运行", Toast.LENGTH_LONG).show();
    }

    private static void openDetails(Activity a) {
        start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + a.getPackageName())));
    }

    private static boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (Throwable e) { // 没这个页面 / 没导出 / 被系统拦
            return false;
        }
    }

    private static String brand() {
        return (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
    }

    static String vendorName() {
        String b = brand();
        if (b.contains("iqoo")) return "iQOO";
        if (b.contains("vivo")) return "vivo";
        if (b.contains("xiaomi") || b.contains("redmi")) return "小米";
        if (b.contains("oppo") || b.contains("oneplus") || b.contains("realme")) return "手机管家";
        if (b.contains("honor")) return "荣耀";
        if (b.contains("huawei")) return "华为";
        return "系统";
    }

    private static String[][] vendorPages() {
        String b = brand();
        if (b.contains("vivo") || b.contains("iqoo")) return new String[][]{
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"}};
        if (b.contains("xiaomi") || b.contains("redmi")) return new String[][]{
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"}};
        if (b.contains("oppo") || b.contains("oneplus") || b.contains("realme")) return new String[][]{
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"}};
        if (b.contains("honor")) return new String[][]{
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}};
        if (b.contains("huawei")) return new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"}};
        return new String[0][];
    }
}
