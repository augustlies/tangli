package com.ecut.campus;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStats;
import com.ecut.campus.view.FlowLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 学期切换弹窗（从 MainActivity 拆出）：点击切换、长按拖动排序、长按不动松手删除、新建学期。 */
final class SemesterPicker {

    private final MainActivity a;

    SemesterPicker(MainActivity a) {
        this.a = a;
    }

    private AlertDialog dialog;

    private FlowLayout semesterFlow;
    private TextView pickerHint;
    private List<SemesterArchive.Entry> semesterEntries;
    private String draggingSemesterId;
    private boolean dragMoved;
    private boolean semesterOrderChanged;

    private static final String HINT_DEFAULT = "点击切换 · 长按拖动排序 · 长按不动松手删除";

    /** 学期切换：半透明胶囊，点击切换、长按拖动排序、长按不动松手删除。 */
    void show() {
        if (dialog != null && dialog.isShowing()) return;
        semesterEntries = SemesterArchive.listSemesters(a);
        semesterOrderChanged = false;
        draggingSemesterId = null;
        dragMoved = false;

        AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle("选择学期");

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        float d = a.getResources().getDisplayMetrics().density;
        int pad = Math.round(18 * d);
        content.setPadding(pad, pad / 2, pad, pad);

        TextView hint = new TextView(a);
        hint.setText(HINT_DEFAULT);
        hint.setTextSize(11.5f);
        hint.setTextColor(G.MUTE);
        content.addView(hint);
        pickerHint = hint;

        semesterFlow = new FlowLayout(a);
        content.addView(semesterFlow);
        rebuildSemesterChips();

        TextView createChip = newCapsule("＋ 新建学期课表", false, G.INK);
        createChip.setOnClickListener(v -> {
            dismissSemesterPicker();
            promptCreateSemester();
        });
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = Math.round(12 * d);
        createChip.setLayoutParams(createLp);
        content.addView(createChip);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        builder.setView(scroll);
        builder.setNegativeButton("取消", null);
        dialog = builder.create();
        dialog.show();
    }


    private void dismissSemesterPicker() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private TextView newCapsule(String text, boolean active, int textColor) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextSize(13.5f);
        tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setTextColor(textColor);
        float d = a.getResources().getDisplayMetrics().density;
        int hPad = Math.round(16 * d), vPad = Math.round(9 * d);
        tv.setPadding(hPad, vPad, hPad, vPad);
        // Gauge 标签：6dp 小圆角；选中为墨色实底，未选为发丝描边
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6 * d);
        if (active) {
            bg.setColor(G.INK);
            tv.setTextColor(G.CARD);
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(Math.max(1, Math.round(d)), G.STROKE);
        }
        tv.setBackground(bg);
        return tv;
    }

    private void rebuildSemesterChips() {
        semesterFlow.removeAllViews();
        String activeId = SemesterArchive.activeSemesterId(a);
        for (int i = 0; i < semesterEntries.size(); i++) {
            semesterFlow.addView(buildSemesterChip(semesterEntries.get(i), activeId));
        }
    }

    private View buildSemesterChip(SemesterArchive.Entry entry, String activeId) {
        boolean isActive = entry.id.equals(activeId);
        TextView chip = newCapsule(entry.displayName() + (entry.custom ? "（自定义）" : "")
                + " · " + entry.courseCount + " 门", isActive,
                isActive ? G.INK : G.SUB2);
        if (entry.id.equals(draggingSemesterId)) chip.setAlpha(0.55f);
        chip.setOnClickListener(v -> {
            if (draggingSemesterId != null) return;
            int idx = semesterFlow.indexOfChild(v);
            if (idx < 0 || idx >= semesterEntries.size()) return;
            dismissSemesterPicker();
            switchToSemester(semesterEntries.get(idx).id);
        });
        chip.setOnLongClickListener(v -> {
            if (draggingSemesterId != null) return false;
            int idx = semesterFlow.indexOfChild(v);
            if (idx < 0 || idx >= semesterEntries.size()) return false;
            draggingSemesterId = semesterEntries.get(idx).id;
            dragMoved = false;
            chip.setAlpha(0.55f);
            chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            SemesterArchive.Entry grabbed = semesterById(draggingSemesterId);
            if (grabbed != null && pickerHint != null) {
                pickerHint.setText("已抓起「" + grabbed.displayName() + "」：拖到其他胶囊交换位置，原地松手删除");
                pickerHint.setTextColor(G.INK);
            }
            return true;
        });
        chip.setOnTouchListener((v, ev) -> {
            if (draggingSemesterId == null) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    int cur = semesterIndexOf(draggingSemesterId);
                    int target = capsuleIndexUnder(ev.getRawX(), ev.getRawY());
                    if (cur >= 0 && target >= 0 && target != cur) {
                        Collections.swap(semesterEntries, cur, target);
                        dragMoved = true;
                        semesterOrderChanged = true;
                        refreshChipVisualsInPlace();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean moved = dragMoved;
                    String dragId = draggingSemesterId;
                    draggingSemesterId = null;
                    dragMoved = false;
                    v.setAlpha(1f);
                    if (pickerHint != null) {
                        pickerHint.setText(HINT_DEFAULT);
                        pickerHint.setTextColor(G.MUTE);
                    }
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                        if (moved) {
                            persistSemesterOrder();
                            rebuildSemesterChips();
                        } else {
                            SemesterArchive.Entry e = dragId == null ? null : semesterById(dragId);
                            if (e != null) confirmDeleteSemester(e);
                        }
                    } else {
                        rebuildSemesterChips();
                    }
                    return true;
                }
            }
            return false;
        });
        return chip;
    }

    /** 拖动换位时原地刷新胶囊内容，不重建 view，保证触摸流不中断。 */
    private void refreshChipVisualsInPlace() {
        String activeId = SemesterArchive.activeSemesterId(a);
        for (int i = 0; i < semesterFlow.getChildCount() && i < semesterEntries.size(); i++) {
            TextView chip = (TextView) semesterFlow.getChildAt(i);
            SemesterArchive.Entry e = semesterEntries.get(i);
            boolean isActive = e.id.equals(activeId);
            chip.setText(e.displayName() + (e.custom ? "（自定义）" : "")
                    + " · " + e.courseCount + " 门");
            chip.setTypeface(isActive ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(isActive ? G.CARD : G.SUB2);
            chip.setAlpha(e.id.equals(draggingSemesterId) ? 0.55f : 1f);
        }
    }

    private int semesterIndexOf(String id) {
        for (int i = 0; i < semesterEntries.size(); i++)
            if (semesterEntries.get(i).id.equals(id)) return i;
        return -1;
    }

    private SemesterArchive.Entry semesterById(String id) {
        for (SemesterArchive.Entry e : semesterEntries)
            if (e.id.equals(id)) return e;
        return null;
    }

    private int capsuleIndexUnder(float rawX, float rawY) {
        for (int i = 0; i < semesterFlow.getChildCount(); i++) {
            View c = semesterFlow.getChildAt(i);
            int[] loc = new int[2];
            c.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX < loc[0] + c.getWidth()
                    && rawY >= loc[1] && rawY < loc[1] + c.getHeight()) return i;
        }
        return -1;
    }

    private void persistSemesterOrder() {
        List<String> ids = new ArrayList<>();
        for (SemesterArchive.Entry e : semesterEntries) ids.add(e.id);
        SemesterArchive.reorderSave(a, ids);
    }

    private void confirmDeleteSemester(SemesterArchive.Entry entry) {
        String name = entry.displayName() + (entry.custom ? "（自定义）" : "");
        new AlertDialog.Builder(a)
                .setTitle("删除学期")
                .setMessage("删除「" + name + "」及其 " + entry.courseCount + " 门课程？不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d2, w2) -> {
                    boolean wasActive = entry.id.equals(SemesterArchive.activeSemesterId(a));
                    // 回退目标按显示顺序取删除后剩下的第一个学期
                    String fallback = "";
                    for (SemesterArchive.Entry e : semesterEntries) {
                        if (!e.id.equals(entry.id)) { fallback = e.id; break; }
                    }
                    if (!SemesterArchive.deleteSemester(a, entry.id)) {
                        Toast.makeText(a, "删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (wasActive && !fallback.isEmpty()) switchToSemester(fallback);
                    a.refreshTimetableTab();
                    Toast.makeText(a, "已删除：" + name, Toast.LENGTH_SHORT).show();
                    dismissSemesterPicker();
                    show();
                })
                .show();
    }

    private void switchToSemester(String semesterId) {
        Timetable target = SemesterArchive.loadSemester(a, semesterId);
        if (target == null) return;
        SemesterArchive.setActiveSemester(a, semesterId);
        SemesterArchive.syncActiveMirror(a, semesterId);

        // 归档是唯一主源；切换只恢复该学期的日期和周数，不回写 Prefs。
        String start = WeekMath.normalizeToMonday(target.semester.startDate);
        if (!start.isEmpty()) {
            Prefs.setCurrentWeek(a, WeekMath.currentWeek(start, WeekMath.openWeekSpan(start)));
        } else {
            // 历史学期没有真实开学日期，定位到课程最集中的一个周，避免落进空白周
            Prefs.setCurrentWeek(a, TimetableStats.busiestWeek(target));
        }

        a.refreshTimetableTab();
        Toast.makeText(a, "已切换到 " + target.semester.name, Toast.LENGTH_SHORT).show();
    }

    private void promptCreateSemester() {
        android.widget.EditText input = new android.widget.EditText(a);
        input.setHint("如 2025-2026 学年第1学期");
        int pad = Math.round(24 * a.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(a)
                .setTitle("新建学期课表")
                .setMessage("学期第一周默认为今天，可稍后在「课表设置」修改。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    Timetable t = SemesterArchive.createCustomSemester(a, name);
                    Prefs.setCurrentWeek(a, 1);
                    a.refreshTimetableTab();
                    Toast.makeText(a, "已创建并切换：" + t.semester.name,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
