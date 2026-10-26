package com.ecut.campus;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;
import java.util.Locale;

/**
 * 跟主题走的日期 / 时间选择器，替换系统 DatePickerDialog / TimePickerDialog
 * （系统那套在磨砂玻璃下是灰头、直角，和整体不搭）。
 */
public final class GaugePickers {
    private GaugePickers() {}

    public interface OnDate {
        void picked(int year, int month0, int day);
    }

    public interface OnTime {
        void picked(int hour, int minute);
    }

    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};

    /** 日期选择。wholeWeek=true 时按「周」高亮（选学期开始日期：点哪天都选中那一周）。 */
    public static void date(Activity a, int year, int month0, int day, boolean wholeWeek, OnDate cb) {
        final int[] sel = {year, month0, day};
        final int[] view = {year, month0};
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 16), dp(a, 18), dp(a, 16), 0);

        TextView big = new TextView(a);
        big.setTextSize(24f);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setTextColor(G.INK);
        big.setPadding(dp(a, 6), 0, 0, 0);
        root.addView(big);

        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = navBtn(a, "‹");
        TextView month = new TextView(a);
        month.setGravity(Gravity.CENTER);
        month.setTextSize(15f);
        month.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        month.setTextColor(G.INK);
        TextView next = navBtn(a, "›");
        TextView todayBtn = navBtn(a, "今天");
        todayBtn.setTextSize(13f);
        nav.addView(prev, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        nav.addView(month, new LinearLayout.LayoutParams(0, dp(a, 44), 1f));
        nav.addView(next, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        nav.addView(todayBtn, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 44)));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = dp(a, 6);
        root.addView(nav, nl);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        for (String w : WEEK) {
            TextView t = new TextView(a);
            t.setText(w);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12f);
            t.setTextColor(G.MUTE);
            head.addView(t, new LinearLayout.LayoutParams(0, dp(a, 28), 1f));
        }
        root.addView(head);

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(sel[0], sel[1], sel[2], 12, 0, 0);
            big.setText(String.format(Locale.CHINA, "%d月%d日 周%s", sel[1] + 1, sel[2],
                    WEEK[(picked.get(Calendar.DAY_OF_WEEK) + 5) % 7]));
            month.setText(String.format(Locale.US, "%04d · %02d", view[0], view[1] + 1));
            grid.removeAllViews();
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(view[0], view[1], 1, 12, 0, 0);
            int lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 周一开头
            int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            int rows = (lead + days + 6) / 7;
            Calendar today = Calendar.getInstance();
            int selWeekKey = weekKey(sel[0], sel[1], sel[2]);
            for (int row = 0; row < 6; row++) {
                LinearLayout line = new LinearLayout(a);
                line.setOrientation(LinearLayout.HORIZONTAL);
                boolean weekRow = false;
                for (int col = 0; col < 7 && row < rows; col++) {
                    int d = row * 7 + col - lead + 1;
                    if (d >= 1 && d <= days && wholeWeek && weekKey(view[0], view[1], d) == selWeekKey) weekRow = true;
                }
                if (weekRow) {
                    GradientDrawable band = new GradientDrawable();
                    band.setColor(G.alpha(G.INK, 0x14));
                    band.setCornerRadius(dp(a, 19));
                    line.setBackground(band);
                }
                for (int col = 0; col < 7; col++) {
                    int d = row * 7 + col - lead + 1;
                    TextView cell = new TextView(a);
                    cell.setGravity(Gravity.CENTER);
                    cell.setTextSize(14f);
                    if (row < rows && d >= 1 && d <= days) {
                        final int dd = d;
                        cell.setText(String.valueOf(d));
                        boolean isSel = sel[0] == view[0] && sel[1] == view[1] && sel[2] == d;
                        boolean isToday = today.get(Calendar.YEAR) == view[0]
                                && today.get(Calendar.MONTH) == view[1] && today.get(Calendar.DAY_OF_MONTH) == d;
                        if (isSel) {
                            GradientDrawable dot = new GradientDrawable();
                            dot.setShape(GradientDrawable.OVAL);
                            dot.setColor(G.INK);
                            cell.setBackground(dot);
                            cell.setTextColor(G.ON_INK);
                            cell.setTypeface(Typeface.DEFAULT_BOLD);
                        } else {
                            cell.setTextColor(isToday ? (G.GLASS ? G.INK : G.ACCENT) : G.INK);
                            if (isToday) cell.setTypeface(Typeface.DEFAULT_BOLD);
                        }
                        cell.setOnClickListener(v -> {
                            sel[0] = view[0];
                            sel[1] = view[1];
                            sel[2] = dd;
                            render[0].run();
                        });
                    }
                    FrameLayout holder = new FrameLayout(a);
                    FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(dp(a, 38), dp(a, 38));
                    hl.gravity = Gravity.CENTER;
                    holder.addView(cell, hl);
                    line.addView(holder, new LinearLayout.LayoutParams(0, dp(a, 38), 1f));
                }
                // 固定 6 行高度，翻月时弹窗不跳
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(a, 38));
                rl.topMargin = dp(a, 4);
                grid.addView(line, rl);
            }
        };
        prev.setOnClickListener(v -> {
            view[1]--;
            if (view[1] < 0) { view[1] = 11; view[0]--; }
            render[0].run();
        });
        next.setOnClickListener(v -> {
            view[1]++;
            if (view[1] > 11) { view[1] = 0; view[0]++; }
            render[0].run();
        });
        todayBtn.setOnClickListener(v -> {
            Calendar t = Calendar.getInstance();
            sel[0] = view[0] = t.get(Calendar.YEAR);
            sel[1] = view[1] = t.get(Calendar.MONTH);
            sel[2] = t.get(Calendar.DAY_OF_MONTH);
            render[0].run();
        });
        render[0].run();

        new AlertDialog.Builder(a)
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> cb.picked(sel[0], sel[1], sel[2]))
                .show();
    }

    /** 时间选择：时 / 分两个滚轮。 */
    public static void time(Activity a, String title, int hour, int minute, OnTime cb) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(a, 8), 0, 0);

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.US, "%02d", i);
        // 分钟 5 分一格（作息基本都是 5 的倍数），滚得快；原来的值不是 5 的倍数就把它也放进去
        int[] minVals = minuteSteps(minute);
        String[] mins = new String[minVals.length];
        int minIdx = 0;
        for (int i = 0; i < minVals.length; i++) {
            mins[i] = String.format(Locale.US, "%02d", minVals[i]);
            if (minVals[i] == minute) minIdx = i;
        }
        NumberPicker ph = wheel(a, 0, 23, hour, hours);
        NumberPicker pm = wheel(a, 0, minVals.length - 1, minIdx, mins);

        TextView colon = new TextView(a);
        colon.setText(":");
        colon.setTextSize(28f);
        colon.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        colon.setTextColor(G.INK);
        colon.setPadding(dp(a, 14), 0, dp(a, 14), 0);

        row.addView(ph, new LinearLayout.LayoutParams(dp(a, 72), -2));
        row.addView(colon);
        row.addView(pm, new LinearLayout.LayoutParams(dp(a, 72), -2));

        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(row)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> cb.picked(ph.getValue(), minVals[pm.getValue()]))
                .show();
    }

    private static NumberPicker wheel(Activity a, int min, int max, int value, String[] labels) {
        NumberPicker p = new NumberPicker(a);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setDisplayedValues(labels);
        p.setValue(Math.max(min, Math.min(max, value)));
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            p.setTextColor(G.INK);
            p.setTextSize(dp(a, 22));
            p.setSelectionDividerHeight(Math.max(1, dp(a, 1)));
        }
        return p;
    }

    private static TextView navBtn(Activity a, String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(22f);
        t.setTextColor(G.INK);
        t.setBackgroundResource(Ui.selectable(a, true));
        return t;
    }

    /** 0,5,10…55，外加 extra（不是 5 的倍数时插在对应位置）。 */
    static int[] minuteSteps(int extra) {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        for (int i = 0; i < 60; i += 5) set.add(i);
        if (extra >= 0 && extra < 60) set.add(extra);
        int[] out = new int[set.size()];
        int i = 0;
        for (int v : set) out[i++] = v;
        return out;
    }

    /** 用「那一周的周一」当 key，判断两天是否同一周。 */
    static int weekKey(int y, int m0, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m0, d, 12, 0, 0);
        int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_YEAR, -back);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
