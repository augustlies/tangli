package com.ecut.campus;

import android.content.Context;
import android.content.res.Configuration;

import androidx.core.content.ContextCompat;

/**
 * Gauge 色板的运行时取值。浅色/深色由 values(-night)/colors.xml 决定，
 * 每个 Activity 在 setContentView 之前调用 {@link #init(Context)}。
 */
public final class G {
    private G() {}

    public static int PAPER = 0xFFF5F4F0, CARD = 0xFFFBFAF7, INK = 0xFF111111, SUB = 0xFF55554F,
            SUB2 = 0xFF45453F, MUTE = 0xFF9A9993, LINE = 0xFFE9E7E1, CHIP = 0xFFECEAE4,
            ACCENT = 0xFFFF4A1C, HAIR = 0x17000000, STROKE = 0x33000000, ON_INK = 0xFFFBFAF7,
            WARN = 0xFFB4540A, OK = 0xFF2F5D3A, CLEAR = 0x00000000;
    public static boolean NIGHT = false;
    /** 当前是否磨砂玻璃主题。 */
    public static boolean GLASS = false;
    /** 页面底色（玻璃主题下透明，露出柔光底）。 */
    public static int BG = 0xFFF5F4F0;
    /** 卡片描边、弹窗底色。 */
    public static int CARD_STROKE = 0xFFE9E7E1, DIALOG = 0xFFFBFAF7;
    /** 卡片 / 小控件圆角（dp）。 */
    public static float CARD_RADIUS = 8f, SMALL_RADIUS = 4f;

    public static final int THEME_INK = 0, THEME_GLASS = 1;

    /**
     * 在 setContentView 之前调用：按设置叠加主题，再把色板读进静态字段。
     * Application 也调用一次，保证用 Application Context 画的东西颜色一致。
     */
    public static void init(Context ctx) {
        GLASS = Prefs.theme(ctx) == THEME_GLASS;
        if (GLASS) ctx.getTheme().applyStyle(R.style.ThemeOverlay_Tangli_Glass, true);
        BG = a(ctx, R.attr.gBg, PAPER);
        PAPER = a(ctx, R.attr.gPaper, c(ctx, R.color.g_paper));
        CARD = a(ctx, R.attr.gCard, c(ctx, R.color.g_card));
        CARD_STROKE = a(ctx, R.attr.gCardStroke, c(ctx, R.color.g_line));
        DIALOG = a(ctx, R.attr.gDialog, c(ctx, R.color.g_card));
        INK = a(ctx, R.attr.gInk, c(ctx, R.color.g_ink));
        SUB = a(ctx, R.attr.gSub, c(ctx, R.color.g_sub));
        SUB2 = a(ctx, R.attr.gSub2, c(ctx, R.color.g_sub2));
        MUTE = a(ctx, R.attr.gMute, c(ctx, R.color.g_mute));
        LINE = a(ctx, R.attr.gLine, c(ctx, R.color.g_line));
        CHIP = a(ctx, R.attr.gChip, c(ctx, R.color.g_chip));
        ACCENT = a(ctx, R.attr.gAccent, c(ctx, R.color.g_accent));
        HAIR = a(ctx, R.attr.gHair, c(ctx, R.color.g_hair));
        STROKE = a(ctx, R.attr.gStroke, c(ctx, R.color.g_stroke));
        ON_INK = a(ctx, R.attr.gOnInk, c(ctx, R.color.g_on_ink));
        WARN = a(ctx, R.attr.gWarn, c(ctx, R.color.g_warn));
        OK = a(ctx, R.attr.gOk, c(ctx, R.color.g_ok));
        CARD_RADIUS = GLASS ? 20f : 8f;
        SMALL_RADIUS = GLASS ? 14f : 4f;
        NIGHT = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        TimetableView.applyPalette(NIGHT);
    }

    /** 成绩不及格等「必须醒目」的红：纸墨用朱砂，玻璃主题（强调色是墨）另给一个砖红。 */
    public static int alarm() {
        return GLASS ? (NIGHT ? 0xFFFF7A59 : 0xFFD9480F) : ACCENT;
    }

    private static int a(Context ctx, int attr, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (!ctx.getTheme().resolveAttribute(attr, tv, true)) return fallback;
        if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
        if (tv.resourceId != 0) {
            try { return ContextCompat.getColor(ctx, tv.resourceId); } catch (Exception ignored) { }
        }
        return fallback;
    }

    /** 给颜色换透明度（0-255）。 */
    public static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** 线性混色，t=0 取 a，t=1 取 b。 */
    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static int c(Context ctx, int id) {
        return ContextCompat.getColor(ctx, id);
    }
}
