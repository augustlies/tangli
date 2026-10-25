package com.ecut.campus;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * 登录统一门户后的一次性上手引导。
 * 第一次检测到已登录会话时自动弹出；之后可在「关于」页重新查看。
 */
public final class LoginGuide {

    private static final String PREFS = "onboarding";
    private static final String KEY_SHOWN = "login_guide_shown";

    static final String TEXT =
            "① 同步课表\n点下面的按钮，或在课表页右上角「⋯ → 同步课表」。以后想更新再点一次就行。\n\n"
            + "② 看课表\n左右滑动切换周，点课程块看教室和老师。每次打开都会回到本周。\n\n"
            + "③ 查成绩\n设置 → 学业成绩 → 同步成绩。在「通用」里打开「出新成绩时提醒」，出分会自动通知你。\n\n"
            + "④ 去广告\n底部「拦截」页点开启，闪动校园等 App 的开屏广告会被挡掉。\n\n"
            + "⑤ 和同学约时间\n设置 → 同学，扫码互加后能看到你们都没课的时间。\n\n"
            + "登录过一段时间会失效，到时 App 会提醒你重新登录一次。";

    private LoginGuide() {}

    /** 已登录且还没看过引导时弹出；onSync 为「立即同步课表」按钮的动作。 */
    public static void maybeShow(Activity a, Runnable onSync) {
        if (a.isFinishing() || !Prefs.hasPortalSession(a)) return;
        if (a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)) return;
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply();
        show(a, onSync);
    }

    public static void show(Activity a, Runnable onSync) {
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(onSync != null ? "登录好了，这样用" : "使用指南")
                .setMessage(TEXT);
        if (onSync != null) {
            b.setNegativeButton("稍后", null)
             .setPositiveButton("立即同步课表", (d, w) -> onSync.run());
        } else {
            b.setPositiveButton("知道了", null);
        }
        b.show();
    }
}
