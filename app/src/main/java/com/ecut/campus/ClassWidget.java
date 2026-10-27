package com.ecut.campus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.ecut.campus.data.DayPlan;
import com.ecut.campus.data.Dates;
import com.ecut.campus.data.WidgetDay;
import com.ecut.campus.data.WidgetLook;

import java.util.List;

/**
 * 桌面小组件「今日课程」：「今日周二，还有 N 门课要上」+ 日期/周次 + 当天还没下课的课（课程色圆角条）。
 * 当天上完 / 没课就显示明天第一节。正在上的那条「还剩 mm:ss」、下一门 60 分钟内「mm:ss 后上课」由 Chronometer 自己走秒；
 * 小组件本身只在上课、下课、上课前 60 分钟、零点这些时刻刷新（外加系统每 30 分钟兜底）。拉成 2×2 时标题和课程条收紧。
 * 类名和 provider 不变，老用户桌面上的小组件升级后原地换新样子。
 */
public final class ClassWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.ecut.campus.WIDGET_TICK";

    /** 课程色板：资源里分日/夜两套（values / values-night）。 */
    static final int[] PIG = {
            R.color.w_pig0, R.color.w_pig1, R.color.w_pig2, R.color.w_pig3, R.color.w_pig4, R.color.w_pig5,
            R.color.w_pig6, R.color.w_pig7, R.color.w_pig8, R.color.w_pig9, R.color.w_pig10, R.color.w_pig11,
            R.color.w_pig12, R.color.w_pig13, R.color.w_pig14, R.color.w_pig15, R.color.w_pig16,
            R.color.w_pig17, R.color.w_pig18, R.color.w_pig19, R.color.w_pig20, R.color.w_pig21,
            R.color.w_pig22, R.color.w_pig23
    };
    static final int[] CHIP = {
            R.drawable.w_chip_0, R.drawable.w_chip_1, R.drawable.w_chip_2, R.drawable.w_chip_3,
            R.drawable.w_chip_4, R.drawable.w_chip_5, R.drawable.w_chip_6, R.drawable.w_chip_7,
            R.drawable.w_chip_8, R.drawable.w_chip_9, R.drawable.w_chip_10, R.drawable.w_chip_11,
            R.drawable.w_chip_12, R.drawable.w_chip_13, R.drawable.w_chip_14, R.drawable.w_chip_15,
            R.drawable.w_chip_16, R.drawable.w_chip_17, R.drawable.w_chip_18, R.drawable.w_chip_19,
            R.drawable.w_chip_20, R.drawable.w_chip_21, R.drawable.w_chip_22, R.drawable.w_chip_23
    };

    /** 跟随系统时颜色交给资源（桌面切深浅色立刻生效）；App 里强制浅/深色时由代码上色。 */
    static final int THEME_SYSTEM = 0, THEME_DAY = 1, THEME_NIGHT = 2;

    /** 自选背景图上的标题配色：图偏亮用深字，偏暗用浅字（由 data/WidgetLook 按图 + 遮罩的亮度决定）。 */
    static final int INK_ON_LIGHT = 0xFF111111, SUB_ON_LIGHT = 0xFF3D3C38, ACCENT_ON_LIGHT = 0xFFD83A10;
    static final int INK_ON_DARK = 0xFFF4F3EE, SUB_ON_DARK = 0xFFE2E0D8, ACCENT_ON_DARK = 0xFFFF8A5E;

    static final String ACTION_PINNED = "com.ecut.campus.WIDGET_PINNED";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
        scheduleTick(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle newOptions) {
        render(c, m, new int[]{id}); // 拉高/压扁后重新算能放几条
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_TICK.equals(intent.getAction())) updateAll(c);
        else if (ACTION_PINNED.equals(intent.getAction())) {
            // 添加卡片还开着就由它显示「已添加」，否则弹个 toast
            if (!PinWidgetActivity.onPinned())
                android.widget.Toast.makeText(c, "已放到桌面", android.widget.Toast.LENGTH_SHORT).show();
            updateAll(c);
        }
    }

    @Override
    public void onDisabled(Context c) {
        cancelTickIfIdle(c);
    }

    static int[] ids(Context c, AppWidgetManager m, Class<?> provider) {
        try {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, provider));
            return ids == null ? new int[0] : ids;
        } catch (Exception e) {
            return new int[0];
        }
    }

    /** 桌面上放了几个「今日课程」。 */
    static int placedCount(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        return m == null ? 0 : ids(c, m, ClassWidget.class).length;
    }

    /** 课表、作息、主题、背景变了也调一下；没放时什么都不做。 */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] a = ids(c, m, ClassWidget.class);
        if (a.length == 0) return;
        render(c, m, a);
        scheduleTick(c);
    }

    /** 下一次上课/下课/上课前 60 分钟（或明早 0:01）叫醒一次。窗口 1 分钟，免得倒计时走到负数还没翻页。 */
    static void scheduleTick(Context c) {
        try {
            long now = System.currentTimeMillis();
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.setWindow(AlarmManager.RTC, Math.max(now + 30_000L, nextRefresh(c, now) + 2_000L),
                    60_000L, tick(c));
        } catch (Throwable ignored) {
            // 排不上闹钟不影响这次显示；系统每 30 分钟还会兜底刷新
        }
    }

    /** 小组件都被移走了才取消闹钟。 */
    static void cancelTickIfIdle(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m != null && ids(c, m, ClassWidget.class).length > 0) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tick(c));
    }

    private static PendingIntent tick(Context c) {
        Intent i = new Intent(c, ClassWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 71, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static int theme(Context c) {
        int m = Prefs.nightMode(c);
        return m == 1 ? THEME_DAY : m == 2 ? THEME_NIGHT : THEME_SYSTEM;
    }

    /** 强制浅/深色时，按那套配置读资源颜色（和资源里同一份色值）。 */
    static Context themed(Context c, int theme) {
        if (theme == THEME_SYSTEM) return c;
        Configuration cfg = new Configuration(c.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (theme == THEME_NIGHT ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return c.createConfigurationContext(cfg);
    }

    /**
     * 按小组件高度估能放几条（标题区 ≈ 28dp + 38dp×字号，每条 ≈ 15dp + 35dp×字号）。
     * 窄（2×2）不显示日期行、标题 16sp：标题区 ≈ 27dp + 22dp×字号。
     */
    static int capacity(Context c, AppWidgetManager m, int id, int level) {
        int h = 0;
        try {
            Bundle o = m.getAppWidgetOptions(id);
            if (o != null) h = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        } catch (Exception ignored) {
        }
        if (h <= 0) h = 165;
        float fs = Math.max(0.85f, c.getResources().getConfiguration().fontScale);
        float head = level == WidgetDay.NARROW ? 27 + 22 * fs : 28 + 38 * fs;
        return Math.max(1, (int) ((h - head + 5) / (15 + 35 * fs)));
    }

    /** 小组件竖屏宽度（dp），拿不到按 4×2 常见的 250。 */
    static int widthDp(AppWidgetManager m, int id) {
        return WidgetBg.sizeDp(m, id)[0];
    }

    /** 宽度档位（data/WidgetDay.WIDE / MID / NARROW）。 */
    static int level(Context c, AppWidgetManager m, int id) {
        return WidgetDay.widthLevel(widthDp(m, id), c.getResources().getConfiguration().fontScale);
    }

    /** 读本地课表，算出此刻小组件要显示的内容。 */
    static WidgetDay.Card card(Context c, long now, int cap) {
        return card(c, now, cap, WidgetDay.WIDE);
    }

    static WidgetDay.Card card(Context c, long now, int cap, int level) {
        String ymd = ClassPlans.ymd(now);
        int nowMin = ClassPlans.nowMinutes(now);
        ClassPlans.Ctx x = ClassPlans.load(c);
        int week = Dates.valid(x.start) ? Dates.weekOf(x.start, ymd) : 0;
        int state = state(x, week);
        List<DayPlan.Item> today = state == WidgetDay.OK ? ClassPlans.day(x, ymd) : null;
        List<DayPlan.Item> tomorrow = state == WidgetDay.OK ? ClassPlans.day(x, Dates.addDays(ymd, 1)) : null;
        return WidgetDay.build(state, ymd, week, x.start, today, tomorrow, nowMin, cap, level);
    }

    /** 下一次该刷新的时刻：最近的上课/下课/上课前 60 分钟，没有就明早 0:01。 */
    static long nextRefresh(Context c, long now) {
        long today = ClassPlans.midnight(now);
        long next = ClassPlans.addDays(today, 1) + 60_000L;
        try {
            ClassPlans.Ctx x = ClassPlans.load(c);
            int b = WidgetDay.nextRefreshMin(ClassPlans.day(x, ClassPlans.ymd(now)), ClassPlans.nowMinutes(now));
            if (b >= 0) next = Math.min(next, ClassPlans.at(today, b));
        } catch (Exception ignored) {
        }
        return next;
    }

    static PendingIntent openTimetable(Context c, int req, boolean mutable) {
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AppActions.EXTRA_ACTION, "open")
                .putExtra(AppActions.EXTRA_VALUE, "timetable");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (mutable && Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        else flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, req, open, flags);
    }

    /** 一张小组件的配色。itemTheme：课程色按哪套走（有图时固定成此刻的深浅色）。 */
    static final class Palette {
        int itemTheme, ink, sub, accent;
        /** 需要代码给文字上色（强制浅/深色或有图）；跟随系统且没图时交给资源。 */
        boolean explicit;
    }

    /** 跟随系统 → 资源色；强制浅/深 → 那套资源色；有图 → 按图的明暗选深字/浅字。 */
    static Palette palette(Context c, int theme, boolean night, WidgetBg.Look look) {
        Palette p = new Palette();
        // 有图时整张卡都由代码上色（图是按此刻深浅色合成的），课程色也按此刻的深浅色走
        p.itemTheme = look != null ? (night ? THEME_NIGHT : THEME_DAY) : theme;
        p.explicit = look != null || theme != THEME_SYSTEM;
        Context tc = themed(c, p.itemTheme);
        if (look != null) {
            p.ink = look.darkText ? INK_ON_LIGHT : INK_ON_DARK;
            p.sub = look.darkText ? SUB_ON_LIGHT : SUB_ON_DARK;
            p.accent = look.darkText ? ACCENT_ON_LIGHT : ACCENT_ON_DARK;
        } else {
            p.ink = ContextCompat.getColor(tc, R.color.w_ink);
            p.sub = ContextCompat.getColor(tc, R.color.w_sub);
            p.accent = ContextCompat.getColor(tc, R.color.w_accent);
        }
        return p;
    }

    /**
     * 有背景图时课程条的不透明度（0..255）：课程色约 72%，让（不模糊的）原图透出来；
     * 遮罩低、图花或字不够清楚时自动加高（data/WidgetLook.chipAlpha）。没图 = 255，和以前一样。
     */
    static int chipAlpha(Context c, WidgetBg.Look look, int itemTheme, boolean night) {
        if (look == null || look.chipGray < 0) return 255;
        Context tc = themed(c, itemTheme);
        double[] pig = new double[PIG.length];
        for (int i = 0; i < PIG.length; i++) pig[i] = WidgetLook.gray(ContextCompat.getColor(tc, PIG[i]));
        double a = WidgetLook.chipAlpha(look.chipGray, look.chipSpread, WidgetLook.gray(WidgetBg.maskColor(night)),
                Prefs.widgetMask(c), WidgetLook.gray(ContextCompat.getColor(tc, R.color.w_ink)),
                WidgetLook.gray(ContextCompat.getColor(tc, R.color.w_chip_sub)), pig);
        return WidgetLook.alpha255(a);
    }

    static boolean isNight(Context c, int theme) {
        return theme == THEME_NIGHT || (theme == THEME_SYSTEM
                && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }

    private static int state(ClassPlans.Ctx x, int week) {
        if (x.t == null || x.t.courses.isEmpty()) return WidgetDay.NO_TIMETABLE;
        if (!Dates.valid(x.start)) return WidgetDay.NO_START;
        if (week < 1) return WidgetDay.BEFORE;
        if (x.totalWeeks > 0 && week > x.totalWeeks) return WidgetDay.AFTER;
        return WidgetDay.OK;
    }

    /** 一个小组件的整张 RemoteViews。photoLong > 0 时用自选背景图（长边像素上限）。 */
    private static RemoteViews view(Context c, AppWidgetManager m, int id, WidgetDay.Card card, int cap, int level,
                                    int theme, boolean night, int photoLong) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_classes);
        WidgetBg.Look look = photoLong > 0
                ? WidgetBg.compose(c, WidgetBg.sizeDp(m, id), night, Prefs.widgetMask(c), photoLong,
                card.chips.isEmpty() ? null : WidgetBg.CHIPS_WIDE)
                : null;
        Palette pal = palette(c, theme, night, look);
        int ca = chipAlpha(c, look, pal.itemTheme, night);
        int itemTheme = pal.itemTheme, ink = pal.ink, sub = pal.sub, accent = pal.accent;
        CharSequence title = card.title;
        if (card.numStart >= 0 && card.numEnd <= card.title.length()) {
            SpannableString sp = new SpannableString(card.title);
            sp.setSpan(new ForegroundColorSpan(accent), card.numStart, card.numEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title = sp;
        }
        v.setTextViewText(R.id.w_title, title);
        // 拉成 2×2：标题已缩成「周二 · 还有 N 门」，字也小一号
        v.setTextViewTextSize(R.id.w_title, TypedValue.COMPLEX_UNIT_SP, level == WidgetDay.NARROW ? 16f : 18f);
        v.setTextViewText(R.id.w_sub, card.sub);
        v.setViewVisibility(R.id.w_sub, level == WidgetDay.NARROW ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.w_note, card.note);
        v.setViewVisibility(R.id.w_note, card.chips.isEmpty() ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_list, card.chips.isEmpty() ? View.GONE : View.VISIBLE);
        if (look != null) {
            v.setImageViewBitmap(R.id.w_bg, look.bitmap);
        } else if (theme != THEME_SYSTEM) {
            v.setImageViewResource(R.id.w_bg, night ? R.drawable.w_card_night : R.drawable.w_card_day);
        }
        if (pal.explicit) {
            v.setTextColor(R.id.w_title, ink);
            v.setTextColor(R.id.w_sub, sub);
            v.setTextColor(R.id.w_note, sub);
        }
        // 列表：每个小组件一份（高度不同能放的条数不同），主题和背景版本也写进去，变了会换一份新的
        Intent svc = new Intent(c, ClassWidgetService.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("tangli-widget://classes/" + id + "?cap=" + cap + "&w=" + level + "&theme=" + itemTheme
                        + (look != null ? "&bg=" + Prefs.widgetBgVer(c) + "&ca=" + ca : "")));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setPendingIntentTemplate(R.id.w_list, openTimetable(c, 73, true));
        v.setOnClickPendingIntent(android.R.id.background, openTimetable(c, 72, false));
        return v;
    }

    /** 兜底：任何一步出错也推一个能显示的占位（「烫梨 · 今日课程」），不让桌面停在空白方块。 */
    static RemoteViews fallback(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_classes_loading);
        try {
            v.setOnClickPendingIntent(android.R.id.background, openTimetable(c, 72, false));
        } catch (Throwable ignored) {
        }
        return v;
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        if (m == null || ids == null) return;
        long now = System.currentTimeMillis();
        int theme = THEME_SYSTEM;
        boolean night = false, photo = false;
        try {
            theme = theme(c);
            night = isNight(c, theme);
            // 自选背景图：每个小组件按自己的真实像素从存图裁切合成；失败就按默认纸色画
            photo = WidgetBg.active(c);
        } catch (Throwable ignored) {
        }
        for (int id : ids) {
            boolean done = false;
            try {
                int level = level(c, m, id);
                int cap = capacity(c, m, id, level);
                WidgetDay.Card card;
                try {
                    card = card(c, now, cap, level);
                } catch (Exception e) {
                    card = new WidgetDay.Card();
                    card.title = "今日课程";
                    card.note = "打开烫梨看看";
                }
                // 位图太大被系统拒收（个别老系统）或 OOM 时先缩一半再试，再不行就不用图
                int[] tries = photo ? new int[]{WidgetBg.FULL, 480, 0} : new int[]{0};
                for (int t = 0; t < tries.length && !done; t++) {
                    try { // 位图每个小组件现解码、用完即弃
                        m.updateAppWidget(id, view(c, m, id, card, cap, level, theme, night, tries[t]));
                        done = true;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!done) {
                try {
                    m.updateAppWidget(id, fallback(c));
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
        } catch (Throwable ignored) {
        }
    }
}
