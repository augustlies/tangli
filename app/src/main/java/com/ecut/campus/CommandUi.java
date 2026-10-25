package com.ecut.campus;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ecut.campus.data.AiGuide;
import com.ecut.campus.data.Dates;
import com.ecut.campus.data.TangliCommand;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;
import com.google.android.material.snackbar.Snackbar;

/**
 * 「烫梨指令」相关界面：剪贴板识别、预览确认、撤销、复制给 AI 的说明、
 * 长按课块停课/调课、点击带标记的块撤销。
 */
final class CommandUi {

    private CommandUi() {}

    private static final String PREFS = "tangli_cmd";
    private static final String KEY_LAST_HASH = "last_clip_hash";
    private static boolean previewShowing;

    // ------------------------------------------------------------ 环境

    static TangliCommand.Env env(Context ctx, Timetable t) {
        TangliCommand.Env e = new TangliCommand.Env();
        String start = WeekMath.effectiveStart(Prefs.semesterStartDate(ctx), t == null ? "" : t.semester.id);
        e.start = start == null || start.isEmpty() ? "" : WeekMath.normalizeToMonday(start);
        e.today = WeekMath.todayDateLabel();
        e.totalWeeks = Prefs.storedTotalWeeks(ctx);
        ScheduleSettings ss = ScheduleSettingsStore.load(ctx);
        int n = ScheduleSettings.SECTION_COUNT;
        e.sectionStart = new int[n + 1];
        e.sectionEnd = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            e.sectionStart[i] = ss.startMinutes(i);
            e.sectionEnd[i] = ss.endMinutes(i);
        }
        return e;
    }

    private static String sectionTimes(Context ctx) {
        ScheduleSettings ss = ScheduleSettingsStore.load(ctx);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= ScheduleSettings.SECTION_COUNT; i++) {
            sb.append("第").append(i).append("节 ").append(ss.startTime(i)).append('-').append(ss.endTime(i)).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 剪贴板

    private static void copy(Context ctx, String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        markHandled(ctx, text); // 自己复制出去的不再当成指令弹窗
    }

    private static void markHandled(Context ctx, String text) {
        prefs(ctx).edit().putInt(KEY_LAST_HASH, text == null ? 0 : text.trim().hashCode()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clipText(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                    || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
            return cs == null ? "" : cs.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static boolean isGuide(String text) {
        return text.contains("你是「烫梨」课表 App 的助手");
    }

    /** 回到前台（拿到焦点）时调用：剪贴板里有新的烫梨指令就弹预览，同一段只提示一次。 */
    static void checkClipboard(Activity a, Runnable refresh) {
        if (previewShowing) return;
        String text = clipText(a);
        if (!TangliCommand.looksLike(text) || isGuide(text)) return;
        int hash = text.trim().hashCode();
        if (prefs(a).getInt(KEY_LAST_HASH, 0) == hash) return;
        markHandled(a, text);
        showPreview(a, text, refresh);
    }

    // ------------------------------------------------------------ 预览 + 应用 + 撤销

    static void showPreview(Activity a, String text, Runnable refresh) {
        Timetable before = TimetableStore.load(a);
        TangliCommand.Plan plan = TangliCommand.plan(text, before, env(a, before));
        int changes = plan.changeCount();
        int errors = plan.errorCount();

        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * d);
        list.setPadding(pad, Math.round(6 * d), pad, Math.round(6 * d));
        for (TangliCommand.Item it : plan.items) {
            TextView tv = new TextView(a);
            tv.setTextSize(13.5f);
            tv.setLineSpacing(0, 1.15f);
            tv.setPadding(0, Math.round(5 * d), 0, Math.round(5 * d));
            if (!it.ok) {
                tv.setText("✕ " + it.desc + (it.line.isEmpty() ? "" : "\n   " + it.line));
                tv.setTextColor(G.MUTE);
            } else if (it.warn) {
                tv.setText("! " + it.desc);
                tv.setTextColor(G.ACCENT);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else if (it.changed) {
                tv.setText("· " + it.desc);
                tv.setTextColor(G.INK);
            } else {
                tv.setText("· " + it.desc);
                tv.setTextColor(G.MUTE);
            }
            list.addView(tv);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(list);

        String title = changes > 0 ? "烫梨指令 · " + changes + " 处改动" : "烫梨指令";
        if (errors > 0) title += "（" + errors + " 行没看懂）";
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(title).setView(sv);
        if (changes > 0) {
            b.setNegativeButton("取消", null);
            b.setPositiveButton("应用", (dlg, w) -> {
                if (!TimetableStore.save(a, plan.result)) {
                    Toast.makeText(a, "保存失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (refresh != null) refresh.run();
                undoBar(a, "已应用 " + changes + " 处改动", before, refresh);
            });
        } else {
            b.setPositiveButton("知道了", null);
        }
        previewShowing = true;
        AlertDialog dialog = b.create();
        dialog.setOnDismissListener(x -> previewShowing = false);
        dialog.show();
    }

    private static void undoBar(Activity a, String msg, Timetable before, Runnable refresh) {
        View root = a.findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar.make(root, msg, 6000)
                .setAction("撤销", v -> {
                    TimetableStore.save(a, before);
                    if (refresh != null) refresh.run();
                })
                .show();
    }

    // ------------------------------------------------------------ 菜单入口

    static void copyAiGuide(Activity a) {
        Timetable t = TimetableStore.load(a);
        copy(a, "烫梨 · 给 AI 的说明", AiGuide.build(t, env(a, t), sectionTimes(a)));
        new AlertDialog.Builder(a)
                .setTitle("已复制给 AI 的说明")
                .setMessage("粘贴给任意 AI（豆包、DeepSeek、Kimi、ChatGPT…），在末尾说你想怎么改，"
                        + "比如「下周三下午加一场地质学考试，14:30 到 16:30，在 3-402」，也可以附上课表截图。\n\n"
                        + "把 AI 回复的指令复制下来，回到烫梨就会自动弹出预览，确认后才生效。")
                .setPositiveButton("知道了", null)
                .show();
    }

    static void showPasteDialog(Activity a, Runnable refresh) {
        float d = a.getResources().getDisplayMetrics().density;
        EditText et = new EditText(a);
        et.setHint("烫梨指令 v1\n+ 课程名 | 周三 | 3-4节 | 1-16周 | 地点");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(5);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setTextSize(13.5f);
        String clip = clipText(a);
        if (TangliCommand.looksLike(clip) && !isGuide(clip)) et.setText(clip);
        LinearLayout wrap = new LinearLayout(a);
        int pad = Math.round(20 * d);
        wrap.setPadding(pad, Math.round(8 * d), pad, 0);
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(a)
                .setTitle("粘贴烫梨指令")
                .setView(wrap)
                .setNegativeButton("取消", null)
                .setPositiveButton("预览", (dlg, w) -> {
                    String text = et.getText().toString();
                    if (!TangliCommand.looksLike(text)) text = TangliCommand.HEADER + " v1\n" + text;
                    markHandled(a, text);
                    showPreview(a, text, refresh);
                })
                .show();
    }

    static void exportForShare(Activity a) {
        Timetable t = TimetableStore.load(a);
        if (t.courses.isEmpty()) {
            Toast.makeText(a, "课表是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = TangliCommand.exportForShare(t);
        copy(a, "烫梨指令", text);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            a.startActivity(Intent.createChooser(send, "已复制，也可以直接发给同学"));
        } catch (RuntimeException e) {
            Toast.makeText(a, "已复制烫梨指令，对方复制后打开烫梨即可导入", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ 长按 / 点标记

    interface Editor { void open(Timetable.Course c, Timetable.Session s); }

    static void onLongPress(Activity a, Timetable.Course c, Timetable.Session s, boolean active, String label,
                            String adjustId, boolean extra, int week, Runnable refresh, Editor editor) {
        if (label != null) {
            onMarked(a, c, s, label, adjustId, extra, refresh, editor);
            return;
        }
        if (!active) {
            editor.open(c, s);
            return;
        }
        String head = c.name + " · 第" + week + "周" + Dates.cnDay(s.dayOfWeek) + " "
                + (s.hasCustomTime() ? s.timeStart + "-" + s.timeEnd : s.startSection + "-" + s.endSection + "节");
        new AlertDialog.Builder(a)
                .setTitle(head)
                .setItems(new String[]{"仅本周停课", "调到其他时间…", "编辑课程"}, (dlg, which) -> {
                    if (which == 0) cancelOnce(a, c, s, week, refresh);
                    else if (which == 1) showMoveDialog(a, c, s, week, refresh);
                    else editor.open(c, s);
                })
                .show();
    }

    private static Timetable.Adjust sourceOf(String type, Timetable.Course c, Timetable.Session s, int week) {
        Timetable.Adjust adj = new Timetable.Adjust();
        adj.type = type;
        adj.name = c.name;
        adj.week = week;
        adj.day = s.dayOfWeek;
        adj.start = s.startSection;
        adj.end = s.endSection;
        return adj;
    }

    private static void cancelOnce(Activity a, Timetable.Course c, Timetable.Session s, int week, Runnable refresh) {
        Timetable before = TimetableStore.load(a);
        Timetable t = TimetableStore.load(a);
        TangliCommand.addAdjust(t, sourceOf(Timetable.Adjust.CANCEL, c, s, week));
        TimetableStore.save(a, t);
        if (refresh != null) refresh.run();
        undoBar(a, "第" + week + "周" + Dates.cnDay(s.dayOfWeek) + "「" + c.name + "」停一次", before, refresh);
    }

    private static NumberPicker picker(Activity a, int min, int max, int value, String[] labels) {
        NumberPicker p = new NumberPicker(a);
        p.setMinValue(min);
        p.setMaxValue(max);
        if (labels != null) p.setDisplayedValues(labels);
        p.setValue(Math.max(min, Math.min(max, value)));
        p.setWrapSelectorWheel(false);
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return p;
    }

    private static TextView caption(Activity a, String s) {
        TextView tv = new TextView(a);
        tv.setText(s);
        tv.setTextColor(G.MUTE);
        tv.setTextSize(12f);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private static void showMoveDialog(Activity a, Timetable.Course c, Timetable.Session s, int week, Runnable refresh) {
        float d = a.getResources().getDisplayMetrics().density;
        int maxWeek = Math.max(week, Prefs.totalWeeks(a));
        String[] wl = new String[maxWeek];
        for (int i = 0; i < maxWeek; i++) wl[i] = "第" + (i + 1) + "周";
        String[] dl = new String[7];
        for (int i = 0; i < 7; i++) dl[i] = Dates.cnDay(i + 1);
        String[] sl = new String[ScheduleSettings.SECTION_COUNT];
        for (int i = 0; i < sl.length; i++) sl[i] = "第" + (i + 1) + "节";

        NumberPicker pw = picker(a, 1, maxWeek, week, wl);
        NumberPicker pd = picker(a, 1, 7, s.dayOfWeek, dl);
        NumberPicker ps = picker(a, 1, sl.length, s.startSection, sl);
        NumberPicker pe = picker(a, 1, sl.length, s.endSection, sl);
        ps.setOnValueChangedListener((p, o, n) -> { if (pe.getValue() < n) pe.setValue(n); });
        pe.setOnValueChangedListener((p, o, n) -> { if (ps.getValue() > n) ps.setValue(n); });

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        for (NumberPicker p : new NumberPicker[]{pw, pd, ps, pe}) row.addView(p, lp);
        LinearLayout caps = new LinearLayout(a);
        caps.setOrientation(LinearLayout.HORIZONTAL);
        for (String cap : new String[]{"周次", "星期", "从", "到"}) caps.addView(caption(a, cap), lp);

        EditText loc = new EditText(a);
        loc.setHint("新地点（不变就留空）");
        loc.setSingleLine(true);
        loc.setTextSize(14f);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * d);
        box.setPadding(pad, Math.round(8 * d), pad, 0);
        box.addView(caps);
        box.addView(row);
        box.addView(loc);

        new AlertDialog.Builder(a)
                .setTitle("把这次「" + c.name + "」调到")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("调过去", (dlg, w) -> {
                    Timetable before = TimetableStore.load(a);
                    Timetable t = TimetableStore.load(a);
                    Timetable.Adjust adj = sourceOf(Timetable.Adjust.MOVE, c, s, week);
                    adj.toWeek = pw.getValue();
                    adj.toDay = pd.getValue();
                    adj.toStart = ps.getValue();
                    adj.toEnd = pe.getValue();
                    adj.toLocation = loc.getText().toString().trim();
                    if (adj.toWeek == week && adj.toDay == s.dayOfWeek && adj.toStart == s.startSection
                            && adj.toEnd == s.endSection && adj.toLocation.isEmpty()) return;
                    TangliCommand.addAdjust(t, adj);
                    TimetableStore.save(a, t);
                    if (refresh != null) refresh.run();
                    undoBar(a, "已调到第" + adj.toWeek + "周" + Dates.cnDay(adj.toDay)
                            + " " + adj.toStart + "-" + adj.toEnd + "节", before, refresh);
                })
                .show();
    }

    static void onMarked(Activity a, Timetable.Course c, Timetable.Session s, String label, String adjustId,
                         boolean extra, Runnable refresh, Editor editor) {
        String time = s.hasCustomTime() ? s.timeStart + "-" + s.timeEnd : s.startSection + "-" + s.endSection + "节";
        String msg;
        switch (label) {
            case "放假": msg = "这天放假，课自动置灰。"; break;
            case "停课": msg = "这一周停一次，其它周照常。"; break;
            case "已调走": msg = "这次课调到别的时间了，那边会显示「调课」。"; break;
            case "补课": msg = "调休补课日，上的是被换掉那天的课。"; break;
            default: msg = "这次课是从别的时间调过来的。"; break;
        }
        msg = Dates.cnDay(s.dayOfWeek) + " " + time + (s.location.isEmpty() ? "" : " · " + s.location) + "\n\n" + msg;
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(c.name + " · " + label).setMessage(msg);
        if (adjustId != null && !adjustId.isEmpty()) {
            boolean dayRule = "放假".equals(label) || "补课".equals(label);
            b.setPositiveButton(dayRule ? "取消这天的安排" : "恢复原样", (dlg, w) -> {
                Timetable before = TimetableStore.load(a);
                Timetable t = TimetableStore.load(a);
                t.adjusts.removeIf(x -> adjustId.equals(x.id));
                TimetableStore.save(a, t);
                if (refresh != null) refresh.run();
                undoBar(a, "已恢复", before, refresh);
            });
            b.setNegativeButton("关闭", null);
        } else {
            b.setPositiveButton("知道了", null);
        }
        if (!extra) b.setNeutralButton("编辑课程", (dlg, w) -> editor.open(c, s));
        b.show();
    }
}
