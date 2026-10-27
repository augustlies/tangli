package com.ecut.campus.fun;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.ecut.campus.QrBitmap;
import com.ecut.campus.data.PairStats;
import com.ecut.campus.data.SemesterPersona;
import com.ecut.campus.data.Timetable;

import java.util.List;

/**
 * 分享卡片的绘制（Canvas → Bitmap）：双人课表海报、学期人设卡。
 * 导出的图片固定用「纸墨」浅色（纸 #F5F4F0、墨 #111），不随 App 主题/深色模式变，发出去谁看都一样。
 * 朱砂只点一个小圆点。
 */
public final class CardPainter {
    private CardPainter() {}

    /** MUTE 在 PAPER 上约 4.7:1，打印/投屏时小字也看得清。 */
    static final int PAPER = 0xFFF5F4F0, INK = 0xFF111111, SUB = 0xFF55554F, MUTE = 0xFF6E6D67,
            LINE = 0xFFDEDCD5, ACCENT = 0xFFFF4A1C;
    public static final String SITE = "tangli.sueexin.dev";
    static final String SITE_URL = "https://tangli.sueexin.dev";
    static final int W = 1080, M = 72;
    static final String[] DAY = {"", "一", "二", "三", "四", "五", "六", "日"};

    // ------------------------------------------------------------ 双人课表海报

    public static final class PairInput {
        public String meName = "我", themName = "同学";
        public Timetable me, them;
        public PairStats.Result stats;
        public int days = 5, sections = 10;
    }

    public static Bitmap pairPoster(PairInput in) {
        int rowH = 66, headH = 56, rail = 52;
        int gridH = headH + in.sections * rowH;
        int top = 620;
        int h = top + gridH + 70 + 300;
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(PAPER);
        PairStats.Result r = in.stats;

        float y = 110;
        text(c, "TANGLI · 双人课表 · 第 " + two(r.week) + " 周", M, y, 26, MUTE, true, false);
        dot(c, W - M - 10, y - 9);
        y += 128;
        text(c, r.title, M, y, fit(r.title, 104, W - 2 * M, false, true), INK, false, true);
        y += 58;
        text(c, r.subtitle, M, y, 34, SUB, false, false);
        y += 64;
        String names = ellipsize(in.meName, 10) + "  ×  " + ellipsize(in.themName, 10);
        text(c, names, M, y, fit(names, 40, W - 2 * M, false, true), INK, false, true);
        y += 36;
        hair(c, M, y, W - M);

        // 三个读数
        float colW = (W - 2 * M) / 3f;
        String[] vals = {r.overlapPct + "%", String.valueOf(r.sameBuildingSections), r.lunchDays.size() + "天"};
        String[] labels = {"课表重合", "同时段同一栋楼（节）", "能一起吃午饭"};
        for (int i = 0; i < 3; i++) {
            float x = M + i * colW;
            text(c, vals[i], x, y + 96, 72, INK, true, true);
            text(c, labels[i], x, y + 136, 24, SUB, false, false);
        }
        y += 172;
        String extra = mealLine(r);
        text(c, extra, M, y, fit(extra, 26, W - 2 * M, false, false), SUB, false, false);

        // 叠在一起的一周
        float gx = M, gy = top, gw = W - 2 * M;
        float colWidth = (gw - rail) / in.days;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int d = 1; d <= in.days; d++) {
            text(c, "周" + DAY[d], gx + rail + (d - 1) * colWidth + colWidth / 2f, gy + 36, 24, SUB, false, false,
                    Paint.Align.CENTER);
        }
        for (int s = 1; s <= in.sections; s++) {
            float ry = gy + headH + (s - 1) * rowH;
            hair(c, gx, ry, gx + gw);
            text(c, two(s), gx, ry + 40, 22, MUTE, true, false);
        }
        hair(c, gx, gy + gridH, gx + gw);
        drawLane(c, p, in.me, r.week, in, gx + rail, gy + headH, colWidth, rowH, 0);
        drawLane(c, p, in.them, r.week, in, gx + rail, gy + headH, colWidth, rowH, 1);

        // 图例
        float ly = gy + gridH + 44;
        p.setStyle(Paint.Style.FILL);
        p.setColor(INK);
        c.drawRect(gx, ly - 20, gx + 24, ly + 4, p);
        text(c, ellipsize(in.meName, 8), gx + 36, ly, 24, SUB, false, false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawRect(gx + 300, ly - 19, gx + 323, ly + 3, p);
        text(c, ellipsize(in.themName, 8), gx + 336, ly, 24, SUB, false, false);
        text(c, "每格左半是" + ellipsize(in.meName, 4) + "，右半是" + ellipsize(in.themName, 4), W - M, ly, 22, MUTE,
                false, false, Paint.Align.RIGHT);

        footer(c, h);
        return bmp;
    }

    private static String mealLine(PairStats.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.lunchDays.isEmpty() ? "午饭时间凑不到一起" : "午饭 " + days(r.lunchDays));
        sb.append("   ·   ");
        sb.append(r.dinnerDays.isEmpty() ? "晚饭凑不到" : "晚饭 " + days(r.dinnerDays));
        if (!r.topBuilding.isEmpty()) sb.append("   ·   常在 ").append(r.topBuilding).append(" 碰面");
        return sb.toString();
    }

    private static String days(List<Integer> ds) {
        StringBuilder sb = new StringBuilder();
        for (int d : ds) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('周').append(DAY[d]);
        }
        return sb.toString();
    }

    /** lane 0 = 我（左半，墨色实块），lane 1 = 同学（右半，墨色描边）。 */
    private static void drawLane(Canvas c, Paint p, Timetable t, int week, PairInput in,
                                 float x0, float y0, float colW, float rowH, int lane) {
        if (t == null || t.courses == null) return;
        float pad = 4, laneW = colW / 2f;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(20);
        tp.setColor(lane == 0 ? PAPER : INK);
        for (Timetable.Course course : t.courses) {
            for (Timetable.Session s : course.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > in.days || !s.isActive(week)) continue;
                int a = Math.max(1, s.startSection), b = Math.min(in.sections, s.endSection);
                if (b < a) continue;
                float left = x0 + (s.dayOfWeek - 1) * colW + lane * laneW + pad;
                RectF rect = new RectF(left, y0 + (a - 1) * rowH + pad, left + laneW - 2 * pad, y0 + b * rowH - pad);
                p.setStyle(Paint.Style.FILL);
                p.setColor(lane == 0 ? INK : PAPER);
                c.drawRoundRect(rect, 6, 6, p);
                if (lane == 1) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3);
                    p.setColor(INK);
                    c.drawRoundRect(rect, 6, 6, p);
                }
                String name = course.name == null ? "" : course.name;
                int w = Math.max(10, Math.round(rect.width() - 10));
                int lines = Math.max(1, (int) ((rect.height() - 8) / 24));
                StaticLayout l = StaticLayout.Builder.obtain(name, 0, name.length(), tp, w)
                        .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
                        .setIncludePad(false).build();
                c.save();
                c.clipRect(rect);
                c.translate(rect.left + 5, rect.top + 5);
                l.draw(c);
                c.restore();
            }
        }
    }

    // ------------------------------------------------------------ 学期人设卡

    public static Bitmap personaCard(SemesterPersona.Card card, String semesterName) {
        int h = 1420;
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(PAPER);
        float y = 110;
        String head = "TANGLI · 学期人设卡" + (semesterName == null || semesterName.isEmpty() ? "" : " · " + semesterName);
        text(c, head, M, y, fit(head, 26, W - 2 * M - 40, true, false), MUTE, true, false);
        dot(c, W - M - 10, y - 9);
        // 刻度尺装饰
        y += 50;
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setColor(INK);
        tick.setStrokeWidth(2);
        for (int i = 0; i <= 40; i++) {
            float x = M + i * (W - 2f * M) / 40f;
            c.drawLine(x, y, x, y + (i % 10 == 0 ? 28 : i % 5 == 0 ? 18 : 10), tick);
        }
        y += 120;
        text(c, "这学期的你是", M, y, 34, SUB, false, false);
        y += 150;
        text(c, card.title, M, y, fit(card.title, 120, W - 2 * M, false, true), INK, false, true);
        y += 70;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(36);
        tp.setColor(SUB);
        StaticLayout tag = StaticLayout.Builder.obtain(card.tagline, 0, card.tagline.length(), tp, W - 2 * M)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(8, 1f).build();
        c.save();
        c.translate(M, y - 30);
        tag.draw(c);
        c.restore();
        y += tag.getHeight() + 60;
        hair(c, M, y, W - M);
        for (int i = 0; i < card.lines.size(); i++) {
            float ry = y + 120 * (i + 1);
            text(c, two(i + 1), M, ry - 42, 26, MUTE, true, false);
            String line = card.lines.get(i);
            text(c, line, M + 80, ry - 38, fit(line, 52, W - 2 * M - 80, false, true), INK, false, true);
            hair(c, M, ry, W - M);
        }
        footer(c, h);
        return bmp;
    }

    // ------------------------------------------------------------ 共用

    private static void footer(Canvas c, int h) {
        float fy = h - 300;
        hair(c, M, fy, W - M);
        text(c, "烫梨", M, fy + 96, 52, INK, false, true);
        text(c, SITE, M, fy + 148, 28, SUB, true, false);
        text(c, "扫码下载 · 卡片在手机上本地生成", M, fy + 196, 24, MUTE, false, false);
        Bitmap qr = QrBitmap.make(SITE_URL, 200, INK, PAPER);
        if (qr != null) c.drawBitmap(qr, W - M - qr.getWidth(), fy + 40, null);
    }

    private static void hair(Canvas c, float x0, float y, float x1) {
        Paint p = new Paint();
        p.setColor(LINE);
        p.setStrokeWidth(2);
        c.drawLine(x0, y, x1, y, p);
    }

    private static void dot(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ACCENT);
        c.drawCircle(x, y, 9, p);
    }

    private static void text(Canvas c, String s, float x, float y, float size, int color, boolean mono, boolean bold) {
        text(c, s, x, y, size, color, mono, bold, Paint.Align.LEFT);
    }

    private static void text(Canvas c, String s, float x, float y, float size, int color, boolean mono, boolean bold,
                             Paint.Align align) {
        Paint p = paint(size, mono, bold);
        p.setColor(color);
        p.setTextAlign(align);
        c.drawText(s, x, y, p);
    }

    private static Paint paint(float size, boolean mono, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(Typeface.create(mono ? Typeface.MONOSPACE : Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    /** 字号从 size 往下缩，直到一行放得下 maxW。 */
    static float fit(String s, float size, float maxW, boolean mono, boolean bold) {
        Paint p = paint(size, mono, bold);
        while (size > 12 && p.measureText(s) > maxW) {
            size -= 2;
            p.setTextSize(size);
        }
        return size;
    }

    static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
