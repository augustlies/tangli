package com.ecut.campus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.FreeTime;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.util.ArrayList;
import java.util.List;

/** 三级页：共同空闲。和一位同学，或和所有同学。 */
public class FreeTimeActivity extends AppCompatActivity {
    public static final String EXTRA_FRIEND = "friend";
    public static final String EXTRA_ALL = "all";

    private static final String[] DAYS = {"一", "二", "三", "四", "五", "六", "日"};

    private int week;
    private final List<String> names = new ArrayList<>();
    private final List<Timetable> tables = new ArrayList<>();
    private ScheduleSettings schedule;
    private String myStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        schedule = ScheduleSettingsStore.load(this);
        myStart = WeekMath.normalizeToMonday(Prefs.semesterStartDate(this));
        week = Prefs.hasCurrentWeek(this) ? Prefs.currentWeek(this)
                : WeekMath.currentWeek(myStart, WeekMath.openWeekSpan(myStart));
        names.add("我");
        tables.add(TimetableStore.load(this));
        String fid = getIntent().getStringExtra(EXTRA_FRIEND);
        boolean all = getIntent().getBooleanExtra(EXTRA_ALL, false);
        for (FriendStore.Friend f : FriendStore.load(this)) {
            if (all || f.id.equals(fid)) {
                names.add(f.name);
                tables.add(f.shared.timetable);
            }
        }
        render();
    }

    /** 对方开学日期不同的话，按同一个自然周对齐。 */
    private int weekFor(Timetable t) {
        String start = WeekMath.normalizeToMonday(t.semester.startDate);
        if (myStart == null || myStart.isEmpty() || start == null || start.isEmpty() || start.equals(myStart))
            return week;
        String monday = WeekMath.weekRangeStart(myStart, week);
        int w = WeekMath.weekOfDate(start, monday);
        return w < 1 ? week : w;
    }

    private void render() {
        String who = names.size() == 2 ? "我和" + names.get(1) : names.size() + " 人";
        LinearLayout col = Ui.page(this, "同学", "共同空闲", who + " · 都没课的时段");

        // 周切换
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = navBtn("‹", v -> { if (week > 1) { week--; render(); } });
        TextView title = new TextView(this);
        title.setText("第 " + (week < 10 ? "0" + week : week) + " 周");
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(20f);
        title.setTextColor(G.INK);
        title.setGravity(Gravity.CENTER);
        TextView next = navBtn("›", v -> { week++; render(); });
        nav.addView(prev, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        nav.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 14);
        col.addView(nav, nl);

        int sections = ScheduleSettings.SECTION_COUNT;
        int days = Prefs.displayDays(this) == 5 ? 5 : 7;
        int[][] busyCount = new int[7][sections];
        for (Timetable t : tables) {
            boolean[][] b = FreeTime.busy(t, weekFor(t), sections);
            for (int d = 0; d < 7; d++) for (int s = 0; s < sections; s++) if (b[d][s]) busyCount[d][s]++;
        }

        Grid grid = new Grid(this, busyCount, days, sections, tables.size());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 34 + sections * 44));
        gl.topMargin = Ui.dp(this, 8);
        col.addView(grid, gl);

        // 文字汇总：每天连续的共同空闲
        Ui.label(this, col, "FREE · 都有空");
        LinearLayout card = Ui.card(this, col);
        int idx = 1;
        for (int d = 0; d < days; d++) {
            StringBuilder sb = new StringBuilder();
            int s = 0;
            while (s < sections) {
                if (busyCount[d][s] == 0) {
                    int e = s;
                    while (e + 1 < sections && busyCount[d][e + 1] == 0) e++;
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(s + 1).append("–").append(e + 1).append(" 节  ")
                            .append(schedule.startTime(s + 1)).append("–").append(schedule.endTime(e + 1));
                    s = e + 1;
                } else s++;
            }
            if (sb.length() == 0) continue;
            Ui.row(this, card, String.format("%02d", idx++), "周" + DAYS[d], sb.toString(), "", null);
        }
        if (idx == 1) Ui.row(this, card, "--", "这周没有大家都空的时段", "换一周看看", "", null);

        TextView legend = new TextView(this);
        legend.setText(tables.size() > 2 ? "深色格子 = 所有人都有空；数字 = 这节有几个人有课" : "深色格子 = 你们都有空");
        legend.setTextSize(11.5f);
        legend.setTextColor(G.MUTE);
        legend.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(legend);
    }

    private TextView navBtn(String s, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(22f);
        b.setTextColor(G.INK);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(Ui.selectable(this, true));
        b.setOnClickListener(l);
        return b;
    }

    /** 刻度风格网格：发丝线 + 等宽节次；全员空闲格用墨色淡底 + 左侧墨条。 */
    static final class Grid extends View {
        private final int[][] busy;
        private final int days, sections, people;
        private final Paint line = new Paint(), fill = new Paint(), bar = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        Grid(Context c, int[][] busy, int days, int sections, int people) {
            super(c);
            this.busy = busy; this.days = days; this.sections = sections; this.people = people;
            d = c.getResources().getDisplayMetrics().density;
            line.setColor(G.LINE);
            line.setStrokeWidth(Math.max(1f, d * 0.6f));
            fill.setColor(G.alpha(G.INK, G.NIGHT ? 0x2E : 0x1A));
            bar.setColor(G.INK);
            text.setTypeface(Typeface.MONOSPACE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float left = 26 * d, top = 26 * d;
            float cw = (getWidth() - left) / days;
            float ch = (getHeight() - top) / sections;
            text.setTextSize(11 * d);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(G.MUTE);
            for (int x = 0; x < days; x++) {
                c.drawText(DAYS[x], left + cw * x + cw / 2, top - 9 * d, text);
            }
            for (int y = 0; y < sections; y++) {
                text.setColor(G.MUTE);
                c.drawText(String.format("%02d", y + 1), left / 2, top + ch * y + ch / 2 + 4 * d, text);
                c.drawLine(left, top + ch * y, getWidth(), top + ch * y, line);
                for (int x = 0; x < days; x++) {
                    RectF r = new RectF(left + cw * x + 2 * d, top + ch * y + 2 * d,
                            left + cw * (x + 1) - 2 * d, top + ch * (y + 1) - 2 * d);
                    int b = busy[x][y];
                    if (b == 0) {
                        c.drawRoundRect(r, 3 * d, 3 * d, fill);
                        c.drawRect(r.left, r.top, r.left + 2 * d, r.bottom, bar);
                        text.setColor(G.INK);
                        c.drawText("空", r.centerX(), r.centerY() + 4 * d, text);
                    } else if (people > 2) {
                        text.setColor(G.MUTE);
                        c.drawText(b + "/" + people, r.centerX(), r.centerY() + 4 * d, text);
                    }
                }
            }
            c.drawLine(left, top + ch * sections, getWidth(), top + ch * sections, line);
        }
    }
}
