package com.ecut.campus;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.view.WeekDialView;

/** 旋钮选周（从 MainActivity 拆出）：竖向拖动刻度盘，中央指针所指周即选中；底部按钮确认。 */
final class WeekPickerDialog {
    private WeekPickerDialog() {}

    static void show(MainActivity a, int current, int total, int actual) {
        AlertDialog[] dialog = new AlertDialog[1];
        Runnable dismiss = () -> { if (dialog[0] != null && dialog[0].isShowing()) dialog[0].dismiss(); };
        AlertDialog.Builder builder = new AlertDialog.Builder(a);
        float d = a.getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * d);

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad);

        WeekDialView dial = new WeekDialView(a);
        dial.configure(current, total, actual);
        dial.setOnWeekPicked(w -> {
            dismiss.run();
            a.jumpToWeek(w);
        });
        content.addView(dial, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(240 * d)));

        TextView ok = new TextView(a);
        ok.setText("跳转到第 " + current + " 周");
        ok.setTextSize(13.5f);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setTextColor(G.ON_INK);
        ok.setGravity(android.view.Gravity.CENTER);
        GradientDrawable okBg = new GradientDrawable();
        okBg.setCornerRadius(G.SMALL_RADIUS * d);
        okBg.setColor(G.INK);
        ok.setBackground(okBg);
        ok.setOnClickListener(v -> {
            dismiss.run();
            a.jumpToWeek(dial.week());
        });
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(40 * d));
        okLp.topMargin = Math.round(12 * d);
        ok.setLayoutParams(okLp);
        dial.setOnWeekChanged(w -> ok.setText("跳转到第 " + w + " 周"));
        content.addView(ok);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        builder.setView(scroll);
        builder.setNegativeButton("取消", null);
        dialog[0] = builder.create();
        dialog[0].show();
    }
}
