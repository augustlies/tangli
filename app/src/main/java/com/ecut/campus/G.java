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

    public static void init(Context ctx) {
        PAPER = c(ctx, R.color.g_paper);
        CARD = c(ctx, R.color.g_card);
        INK = c(ctx, R.color.g_ink);
        SUB = c(ctx, R.color.g_sub);
        SUB2 = c(ctx, R.color.g_sub2);
        MUTE = c(ctx, R.color.g_mute);
        LINE = c(ctx, R.color.g_line);
        CHIP = c(ctx, R.color.g_chip);
        ACCENT = c(ctx, R.color.g_accent);
        HAIR = c(ctx, R.color.g_hair);
        STROKE = c(ctx, R.color.g_stroke);
        ON_INK = c(ctx, R.color.g_on_ink);
        WARN = c(ctx, R.color.g_warn);
        OK = c(ctx, R.color.g_ok);
        NIGHT = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        TimetableView.applyPalette(NIGHT);
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
