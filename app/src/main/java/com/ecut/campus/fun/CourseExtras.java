package com.ecut.campus.fun;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ecut.campus.G;
import com.ecut.campus.Prefs;
import com.ecut.campus.Ui;
import com.ecut.campus.data.AbsenceBook;
import com.ecut.campus.data.ClassPrice;
import com.ecut.campus.data.Timetable;

/**
 * 课程详情（编辑页）顶上的一小块：「这节 ¥19.8」+ 早八毒舌一句，和缺勤安全气囊（请假/缺勤/被点名计数 + 细条）。
 * 只对已有课程显示；计数改了立刻存本机（底部给「撤销」），和课程编辑的「保存」无关。
 */
public final class CourseExtras {

    private final Activity a;
    private final Timetable t;
    private final Timetable.Course course;
    private final int totalWeeks;
    private AbsenceBook book;
    private TextView[] values;
    private View barFill;
    private TextView caption;

    private CourseExtras(Activity a, Timetable t, Timetable.Course course) {
        this.a = a;
        this.t = t;
        this.course = course;
        this.totalWeeks = Prefs.totalWeeks(a);
    }

    /** 往 host 里填内容并显示；course 为空（新建课程）时保持隐藏。 */
    public static void attach(Activity a, LinearLayout host, Timetable t, Timetable.Course course,
                              int sessionIndex) {
        if (host == null || course == null || t == null) return;
        host.removeAllViews();
        CourseExtras x = new CourseExtras(a, t, course);
        Timetable.Session s = sessionIndex >= 0 && sessionIndex < course.sessions.size()
                ? course.sessions.get(sessionIndex)
                : (course.sessions.isEmpty() ? null : course.sessions.get(0));
        View price = x.priceBlock(s);
        if (price != null) host.addView(price, block(a));
        host.addView(x.absenceBlock(), block(a));
        host.setVisibility(View.VISIBLE);
    }

    private static LinearLayout.LayoutParams block(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 12);
        return lp;
    }

    private LinearLayout box() {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 18);
        b.setPadding(p, Ui.dp(a, 14), p, Ui.dp(a, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(G.CARD);
        bg.setCornerRadius(Ui.dp(a, G.CARD_RADIUS));
        bg.setStroke(Math.max(1, Ui.dp(a, 1)), G.CARD_STROKE);
        b.setBackground(bg);
        return b;
    }

    private TextView mono(String s, float size, int color) {
        TextView tv = new TextView(a);
        tv.setText(s);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(size);
        tv.setTextColor(color);
        return tv;
    }

    // ------------------------------------------------------------ 这节课值多少钱

    private View priceBlock(Timetable.Session s) {
        double tuition = FunPrefs.tuition(a);
        int total = ClassPrice.totalSections(t, totalWeeks);
        double per = ClassPrice.perSection(tuition, total);
        if (tuition <= 0) return tuitionHint();
        if (per <= 0) return null;
        LinearLayout b = box();
        b.addView(mono("PRICE · 学费折算", 11f, G.MUTE));

        int span = s == null ? 1 : Math.max(1, s.endSection - s.startSection + 1);
        SpannableStringBuilder sb = new SpannableStringBuilder("这节 ");
        int x = sb.length();
        sb.append("¥").append(ClassPrice.yuan(per));
        float sd = a.getResources().getDisplayMetrics().scaledDensity;
        sb.setSpan(new AbsoluteSizeSpan(Math.round(26 * sd)), x, sb.length(), 0);
        sb.setSpan(new TypefaceSpan("monospace"), x, sb.length(), 0);
        sb.setSpan(new StyleSpan(Typeface.BOLD), x, sb.length(), 0);
        sb.setSpan(new ForegroundColorSpan(G.INK), x, sb.length(), 0);
        if (span > 1) {
            int y = sb.length();
            sb.append("  · 连上 ").append(String.valueOf(span)).append(" 节 ¥").append(ClassPrice.yuan(per * span));
            sb.setSpan(new TypefaceSpan("monospace"), y, sb.length(), 0);
            sb.setSpan(new AbsoluteSizeSpan(Math.round(12 * sd)), y, sb.length(), 0);
        }
        TextView big = new TextView(a);
        big.setText(sb);
        big.setTextSize(13f);
        big.setTextColor(G.SUB);
        big.setPadding(0, Ui.dp(a, 6), 0, 0);
        b.addView(big);

        if (ClassPrice.isEarlyEight(s)) {
            TextView tease = new TextView(a);
            tease.setText(ClassPrice.teaseLine(per * span, course.name.hashCode()));
            tease.setTextSize(12.5f);
            tease.setTextColor(G.SUB);
            tease.setPadding(0, Ui.dp(a, 6), 0, 0);
            b.addView(tease);
        }
        TextView basis = mono("¥" + ClassPrice.yuan(tuition) + " ÷ 本学期 " + total + " 节（同一时段只算一次）", 10.5f, G.MUTE);
        basis.setPadding(0, Ui.dp(a, 6), 0, 0);
        b.addView(basis);
        return b;
    }

    /** 还没填学费：只留一行，点了去「小玩意」填。 */
    private View tuitionHint() {
        TextView tv = new TextView(a);
        tv.setText("填一下学费，这里会显示这节课值多少钱 ›");
        tv.setTextSize(12f);
        tv.setTextColor(G.SUB);
        tv.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), 0, Ui.dp(a, 2));
        tv.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, FunActivity.class)
                .putExtra(FunActivity.EXTRA_EDIT_TUITION, true)));
        return tv;
    }

    // ------------------------------------------------------------ 缺勤安全气囊

    private View absenceBlock() {
        book = FunPrefs.absence(a, t);
        LinearLayout b = box();
        b.addView(mono("ABSENCE · 缺勤安全气囊", 11f, G.MUTE));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(a, 10), 0, 0);
        String[] labels = {"请假", "缺勤", "被点名"};
        values = new TextView[3];
        for (int i = 0; i < 3; i++) {
            row.addView(stepper(labels[i], i), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        b.addView(row);

        FrameLayout track = new FrameLayout(a);
        track.setBackgroundColor(G.HAIR);
        barFill = new View(a);
        track.addView(barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 3));
        tl.topMargin = Ui.dp(a, 12);
        b.addView(track, tl);

        caption = mono("", 11f, G.SUB);
        caption.setPadding(0, Ui.dp(a, 6), 0, 0);
        b.addView(caption);
        TextView note = new TextView(a);
        note.setText("请假和缺勤计入上限（本学期课次的 1/3），只记在你手机上");
        note.setTextSize(11f);
        note.setTextColor(G.MUTE);
        note.setPadding(0, Ui.dp(a, 4), 0, 0);
        b.addView(note);
        render();
        track.post(this::render);
        return b;
    }

    private View stepper(String label, int which) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView l = new TextView(a);
        l.setText(label);
        l.setTextSize(12f);
        l.setTextColor(G.SUB);
        col.addView(l);

        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(stepButton("−", which, -1), new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
        TextView v = mono("0", 18f, G.INK);
        v.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        v.setGravity(Gravity.CENTER);
        values[which] = v;
        line.addView(v, new LinearLayout.LayoutParams(Ui.dp(a, 34), ViewGroup.LayoutParams.WRAP_CONTENT));
        line.addView(stepButton("+", which, 1), new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = Ui.dp(a, 4);
        col.addView(line, ll);
        return col;
    }

    private TextView stepButton(String s, int which, int delta) {
        TextView btn = new TextView(a);
        btn.setText(s);
        btn.setTextSize(16f);
        btn.setTextColor(G.INK);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(a, G.SMALL_RADIUS));
        bg.setStroke(Math.max(1, Ui.dp(a, 1)), G.STROKE);
        btn.setBackground(bg);
        btn.setContentDescription(s.equals("+") ? "加一" : "减一");
        btn.setOnClickListener(v -> {
            if (book.get(course.name).count(which) + delta < 0) return;
            step(which, delta);
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            View root = a.findViewById(android.R.id.content);
            if (root != null) {
                String[] what = {"请假", "缺勤", "被点名"};
                com.google.android.material.snackbar.Snackbar
                        .make(root, what[which] + (delta > 0 ? " +1，已记下" : " −1，已记下"), 4000)
                        .setAction("撤销", u -> step(which, -delta)).show();
            }
        });
        return btn;
    }

    private void step(int which, int delta) {
        book.bump(course.name, which, delta);
        FunPrefs.saveAbsence(a, t, book);
        render();
    }

    private void render() {
        AbsenceBook.Counts c = book.get(course.name);
        values[0].setText(String.valueOf(c.leave));
        values[1].setText(String.valueOf(c.absent));
        values[2].setText(String.valueOf(c.called));
        int sessions = AbsenceBook.sessionCount(t, course.name, totalWeeks);
        int limit = AbsenceBook.limit(sessions);
        boolean warn = AbsenceBook.warn(c, limit);
        int color = warn ? G.alarm() : G.INK;
        View track = (View) barFill.getParent();
        float ratio = limit <= 0 ? (c.used() > 0 ? 1f : 0f) : Math.min(1f, c.used() / (float) limit);
        ViewGroup.LayoutParams lp = barFill.getLayoutParams();
        lp.width = Math.round(track.getWidth() * ratio);
        barFill.setLayoutParams(lp);
        barFill.setBackgroundColor(color);
        String text = sessions == 0 ? "本学期课表里没排这门课，算不了上限"
                : limit == 0 ? "共 " + sessions + " 次课，缺 1 次就超过 1/3 · 已记 " + c.used()
                : "请假+缺勤 " + c.used() + " / 上限 " + limit + " · 共 " + sessions + " 次课";
        if (sessions > 0 && c.used() > limit) text += "\n已超过 1/3，可能影响考试资格";
        else if (warn) text += "\n快到上限了，悠着点";
        caption.setText(text);
        caption.setTextColor(warn ? G.alarm() : G.SUB);
    }
}
