package com.ecut.campus;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 刻度风格弹出菜单：纸面 + 发丝描边 + 4dp 圆角，左侧等宽序号，分组之间发丝分隔。
 * 用来替代系统 PopupMenu 的大白板。
 */
public final class GaugeMenu {

    public interface OnPick { void onPick(String title); }

    private final Context ctx;
    private final List<List<String>> groups = new ArrayList<>();

    public GaugeMenu(Context ctx) {
        this.ctx = ctx;
        groups.add(new ArrayList<>());
    }

    public GaugeMenu add(String title) {
        groups.get(groups.size() - 1).add(title);
        return this;
    }

    public GaugeMenu divider() {
        if (!groups.get(groups.size() - 1).isEmpty()) groups.add(new ArrayList<>());
        return this;
    }

    public void show(View anchor, OnPick pick) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_gauge_menu);
        box.setPadding(0, dp(6), 0, dp(6));

        PopupWindow pw = new PopupWindow(box, dp(212), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(10));

        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);

        int n = 0;
        for (int g = 0; g < groups.size(); g++) {
            if (groups.get(g).isEmpty()) continue;
            if (g > 0) {
                View line = new View(ctx);
                line.setBackgroundColor(G.HAIR);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
                lp.setMargins(dp(16), dp(5), dp(16), dp(5));
                box.addView(line, lp);
            }
            for (String title : groups.get(g)) {
                n++;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), 0, dp(16), 0);
                row.setBackgroundResource(ripple.resourceId);

                TextView num = new TextView(ctx);
                num.setText(String.format(Locale.US, "%02d", n));
                num.setTypeface(Typeface.MONOSPACE);
                num.setTextSize(10.5f);
                num.setTextColor(G.MUTE);
                row.addView(num, new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView tv = new TextView(ctx);
                tv.setText(title);
                tv.setTextSize(14f);
                tv.setTextColor(G.INK);
                tv.setSingleLine(true);
                row.addView(tv);

                row.setOnClickListener(v -> {
                    pw.dismiss();
                    pick.onPick(title);
                });
                box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            }
        }
        pw.showAsDropDown(anchor, -dp(212) + anchor.getWidth() - dp(4), dp(2), Gravity.NO_GRAVITY);
    }

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
