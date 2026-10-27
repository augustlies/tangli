package com.ecut.campus;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 二级/三级页的 Gauge 骨架：返回 + 大标题 + 分组卡片 + 行。全部代码构建，颜色取 G。 */
public final class Ui {
    private Ui() {}

    public static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics()));
    }

    /** 建一个可滚动页面，返回放内容的竖向容器。 */
    public static LinearLayout page(Activity a, String backLabel, String title, String sub) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(G.BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 40));
        sv.addView(col);

        TextView back = new TextView(a);
        back.setText("‹ " + backLabel);
        back.setTypeface(Typeface.MONOSPACE);
        back.setTextSize(13f);
        back.setTextColor(G.SUB);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(a, 6), 0, dp(a, 6), 0);
        back.setBackgroundResource(selectable(a, true));
        back.setOnClickListener(v -> a.finish());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 40));
        bl.leftMargin = -dp(a, 6);
        col.addView(back, bl);

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(24f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(G.INK);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(a, 4);
        col.addView(t, tl);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(a);
            s.setText(sub);
            s.setTextSize(12.5f);
            s.setTextColor(G.SUB);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = dp(a, 2);
            col.addView(s, sl);
        }
        a.setContentView(sv);
        if (!G.GLASS) {
            a.getWindow().setStatusBarColor(G.PAPER);
            a.getWindow().setNavigationBarColor(G.PAPER);
        }
        return col;
    }

    public static TextView label(Activity a, LinearLayout parent, String text) {
        TextView l = new TextView(a);
        l.setText(text);
        l.setTypeface(Typeface.MONOSPACE);
        l.setLetterSpacing(0.08f);
        l.setTextSize(11f);
        l.setTextColor(G.MUTE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 22);
        lp.bottomMargin = dp(a, 8);
        parent.addView(l, lp);
        return l;
    }

    /** 纸面卡片（发丝边框），返回卡片内的竖向容器。 */
    public static LinearLayout card(Activity a, LinearLayout parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, G.CARD_RADIUS));
        bg.setColor(G.CARD);
        bg.setStroke(Math.max(1, dp(a, 1)), G.CARD_STROKE);
        c.setBackground(bg);
        c.setClipToOutline(true);
        parent.addView(c, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    /** 一行：序号 + 标题/副标题 + 右侧（› 或等宽标记）。在卡内第二行起自动加发丝线。 */
    public static View row(Activity a, LinearLayout card, String index, String title, String sub,
                           String trailing, View.OnClickListener click) {
        if (card.getChildCount() > 0) {
            View hair = new View(a);
            hair.setBackgroundColor(G.LINE);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            hp.leftMargin = dp(a, 48);
            card.addView(hair, hp);
        }
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(a, 60));
        r.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 14));
        if (click != null) {
            r.setBackgroundResource(selectable(a, false));
            r.setOnClickListener(click);
        }
        TextView idx = new TextView(a);
        idx.setText(index);
        idx.setTypeface(Typeface.MONOSPACE);
        idx.setTextSize(11f);
        idx.setTextColor(G.MUTE);
        r.addView(idx, new LinearLayout.LayoutParams(dp(a, 30), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(15.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(G.INK);
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(a);
            s.setText(sub);
            s.setTextSize(12.5f);
            s.setTextColor(G.SUB);
            s.setPadding(0, dp(a, 3), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tr = new TextView(a);
        if (trailing == null || trailing.equals("›")) {
            tr.setText("›");
            tr.setTextSize(22f);
        } else {
            tr.setText(trailing);
            tr.setTypeface(Typeface.MONOSPACE);
            tr.setTextSize(11f);
        }
        tr.setTextColor(G.MUTE);
        r.addView(tr);
        card.addView(r, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    public static TextView text(Activity a, LinearLayout parent, String s, float size, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        parent.addView(t);
        return t;
    }

    /** 实心墨色按钮 / 描边按钮（TextView 实现，避免主题差异）。 */
    public static TextView button(Activity a, String s, boolean filled, View.OnClickListener l) {
        TextView b = new TextView(a);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(13.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 6));
        if (filled) {
            bg.setColor(G.INK);
            b.setTextColor(G.ON_INK);
        } else {
            bg.setColor(G.CLEAR);
            bg.setStroke(Math.max(1, dp(a, 1)), G.STROKE);
            b.setTextColor(G.INK);
        }
        b.setBackground(bg);
        b.setForeground(a.getDrawable(selectable(a, false)));
        b.setOnClickListener(l);
        b.setMinHeight(dp(a, 44));
        return b;
    }

    public static int selectable(Activity a, boolean borderless) {
        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(borderless
                ? android.R.attr.selectableItemBackgroundBorderless
                : android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    /** 弹窗里放一个输入框：左右 22dp、上 8dp 留白。height 用 px 或 -2（自适应）。 */
    public static LinearLayout dialogBox(Activity a, View child, int height) {
        int p = dp(a, 22);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(p, dp(a, 8), p, 0);
        box.addView(child, new LinearLayout.LayoutParams(-1, height));
        return box;
    }

    public static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
