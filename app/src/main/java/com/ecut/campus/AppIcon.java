package com.ecut.campus;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.ecut.campus.data.AppIconOption;

/**
 * 切换桌面图标：AndroidManifest 里三个 activity-alias（.LauncherPear 默认开、.LauncherArt / 彩蛋 .LauncherBenben 默认关），
 * 同一时间只开一个。用 DONT_KILL_APP，不杀进程；桌面多久刷新新图标看桌面自己（几秒到需要重启桌面不等）。
 * 包名带 .debug 后缀时 alias 类名不变，所以 ComponentName 用 (getPackageName(), 完整类名)。
 */
final class AppIcon {
    private AppIcon() {}

    static AppIconOption current(Context c) {
        return AppIconOption.fromKey(Prefs.appIcon(c));
    }

    /** 选了新图标：先开新的再关旧的，中间任何时刻桌面上都至少有一个入口。 */
    static boolean apply(Context c, AppIconOption want) {
        Prefs.setAppIcon(c, want.key);
        return sync(c);
    }

    /**
     * 让组件状态和存的选择一致（备份恢复到新手机、上次切到一半被杀之类）。只在不一致时才动，
     * 正常启动什么都不做。返回 true 表示改了组件状态。
     */
    static boolean sync(Context c) {
        AppIconOption want = current(c);
        PackageManager pm = c.getPackageManager();
        boolean changed = false;
        try {
            ComponentName on = comp(c, want);
            if (!enabled(pm, on, want == AppIconOption.DEFAULT)) {
                pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                changed = true;
            }
            for (AppIconOption o : AppIconOption.values()) {
                if (o == want) continue;
                ComponentName off = comp(c, o);
                if (enabled(pm, off, o == AppIconOption.DEFAULT)) {
                    pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                    changed = true;
                }
            }
        } catch (Exception ignored) {
            // 个别 ROM 不让改自己的组件：保持现状，桌面还是原来的图标
        }
        return changed;
    }

    static ComponentName comp(Context c, AppIconOption o) {
        return new ComponentName(c.getPackageName(), o.alias);
    }

    /** DEFAULT 状态 = 清单里写的 enabled 值（默认图标为 true，其它为 false）。 */
    private static boolean enabled(PackageManager pm, ComponentName cn, boolean manifestDefault) {
        int s = pm.getComponentEnabledSetting(cn);
        if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
        if (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return manifestDefault;
        return false;
    }
}
