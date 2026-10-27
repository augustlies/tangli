package com.ecut.campus;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;
import com.ecut.campus.data.WeekRange;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 课程新增/编辑页。
 *
 * 只允许五组双节槽；周次默认只勾选进入时正在查看的那一周。
 */
public class CourseEditActivity extends AppCompatActivity {

    public static final String EXTRA_COURSE_KEY = "course_key";
    public static final String EXTRA_SESSION_INDEX = "session_index";
    public static final String EXTRA_DEFAULT_DAY = "default_day";
    public static final String EXTRA_DEFAULT_SECTION = "default_section";
    /** 在课表空白处拖出来的结束节；没有时按起始节默认。 */
    public static final String EXTRA_DEFAULT_END_SECTION = "default_end_section";
    public static final String EXTRA_CURRENT_WEEK = "current_week";

    private static final String[] DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] WEEK_MODE_LABELS = {"全部周", "连续", "单周", "双周", "离散"};

    private Timetable timetable;
    private Timetable.Course editingCourse;
    private final List<Timetable.Session> draftSessions = new ArrayList<>();
    private int selectedSessionIndex = 0;
    private int currentWeek = 1;

    private EditText etName;
    private EditText etTeacher;
    private EditText etNote;
    private EditText etLocation;
    private EditText etWeeks;
    private TextView tvWeeksPreview;
    private LinearLayout colorRow;
    private LinearLayout sessionChipRow;
    private LinearLayout daySelectorRow;
    private LinearLayout sectionSelectorRow;
    private LinearLayout weekModeRow;
    private LinearLayout weekChipContainer;

    private int selectedDay = 1;
    private int selectedStart = 1;
    private int selectedEnd = 2;
    private boolean customTime = false;
    private String timeStart = "";
    private String timeEnd = "";
    private ScheduleSettings sched;
    private int selectedColor = 0;
    private int weekMode = 0;
    private Integer continuousStart = null;
    private final Set<Integer> selectedWeeks = new LinkedHashSet<>();
    private boolean loadingForm;
    private boolean dirty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_course_edit);

        timetable = TimetableStore.load(this);
        currentWeek = getIntent().getIntExtra(EXTRA_CURRENT_WEEK,
                Prefs.hasCurrentWeek(this) ? Prefs.currentWeek(this) : 1);

        String courseKey = getIntent().getStringExtra(EXTRA_COURSE_KEY);
        int sessionIndex = getIntent().getIntExtra(EXTRA_SESSION_INDEX, -1);
        if (courseKey != null) {
            for (Timetable.Course c : timetable.courses) {
                if (courseKey.equals(c.key)) {
                    editingCourse = c;
                    break;
                }
            }
        }

        bindViews();
        prepareDraft(sessionIndex);
        renderAll();
        com.ecut.campus.fun.CourseExtras.attach(this, findViewById(R.id.course_extras),
                timetable, editingCourse, sessionIndex);

        findViewById(R.id.btn_course_back).setOnClickListener(v -> confirmExit());
        findViewById(R.id.btn_course_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_add_session).setOnClickListener(v -> addSession());
        findViewById(R.id.btn_delete_session).setOnClickListener(v -> deleteCurrentSession());
        View deleteCourseBtn = findViewById(R.id.btn_delete_course);
        deleteCourseBtn.setVisibility(editingCourse == null ? View.GONE : View.VISIBLE);
        deleteCourseBtn.setOnClickListener(v -> confirmDeleteCourse());
    }

    private void bindViews() {
        etName = findViewById(R.id.et_course_name);
        etTeacher = findViewById(R.id.et_course_teacher);
        etNote = findViewById(R.id.et_course_note);
        etLocation = findViewById(R.id.et_session_location);
        etWeeks = findViewById(R.id.et_weeks_value);
        tvWeeksPreview = findViewById(R.id.tv_weeks_preview);
        colorRow = findViewById(R.id.course_color_row);
        sessionChipRow = findViewById(R.id.session_chip_container);
        daySelectorRow = findViewById(R.id.day_selector_row);
        sectionSelectorRow = findViewById(R.id.section_selector_row);
        sectionSelectorRow.setOrientation(LinearLayout.VERTICAL);
        android.view.ViewGroup.LayoutParams slp = sectionSelectorRow.getLayoutParams();
        slp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        sectionSelectorRow.setLayoutParams(slp);
        sched = ScheduleSettingsStore.load(this);
        weekModeRow = findViewById(R.id.week_mode_row);
        weekChipContainer = findViewById(R.id.week_chip_container);

        TextView title = findViewById(R.id.tv_course_edit_title);
        title.setText(editingCourse == null ? "添加课程" : "编辑课程");

        etName.addTextChangedListener(simpleWatcher());
        etTeacher.addTextChangedListener(simpleWatcher());
        etNote.addTextChangedListener(simpleWatcher());
        etLocation.addTextChangedListener(simpleWatcher());
        etWeeks.addTextChangedListener(simpleWatcher());
    }

    private TextWatcher simpleWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!loadingForm) dirty = true;
                if (s == etWeeks.getEditableText()) updateWeeksPreview();
            }
        };
    }

    private void prepareDraft(int highlightSessionIndex) {
        if (editingCourse != null) {
            etName.setText(editingCourse.name);
            etTeacher.setText(editingCourse.teacher);
            etNote.setText(editingCourse.note);
            selectedColor = Math.abs(editingCourse.colorIndex) % TimetableView.PALETTE_BG.length;
            for (Timetable.Session s : editingCourse.sessions) draftSessions.add(copySession(s));
            if (highlightSessionIndex >= 0 && highlightSessionIndex < draftSessions.size()) {
                selectedSessionIndex = highlightSessionIndex;
            }
        } else {
            selectedColor = com.ecut.campus.data.CourseColorAssigner.firstFree(timetable);
            Timetable.Session s = new Timetable.Session();
            s.dayOfWeek = getIntent().getIntExtra(EXTRA_DEFAULT_DAY, WeekMath.todayDayOfWeek());
            int defStart = getIntent().getIntExtra(EXTRA_DEFAULT_SECTION, 1);
            s.startSection = clamp(defStart, 1, maxSec());
            // 点在单数节默认两节，点在双数节默认只占这一节；编辑页里可以逐节加减
            s.endSection = (s.startSection % 2 == 1) ? Math.min(maxSec(), s.startSection + 1) : s.startSection;
            int drawnEnd = getIntent().getIntExtra(EXTRA_DEFAULT_END_SECTION, -1);
            if (drawnEnd >= s.startSection) s.endSection = clamp(drawnEnd, s.startSection, maxSec());
            s.weeks.add(currentWeek);
            s.weeksRaw = String.valueOf(currentWeek);
            draftSessions.add(s);
        }
        if (draftSessions.isEmpty()) draftSessions.add(new Timetable.Session());
        selectedSessionIndex = Math.max(0, Math.min(draftSessions.size() - 1, selectedSessionIndex));
    }

    private void renderAll() {
        renderColorRow();
        renderSessionChips();
        loadSessionIntoForm();
        dirty = false;
    }

    private void renderColorRow() {
        colorRow.removeAllViews();
        colorRow.setOrientation(LinearLayout.VERTICAL);
        int n = TimetableView.PALETTE_BG.length;
        int perRow = 8;
        LinearLayout line = null;
        for (int i = 0; i < n; i++) {
            if (i % perRow == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) ll.topMargin = dp(8);
                colorRow.addView(line, ll);
            }
            final int idx = i;
            int fill = G.GLASS ? TimetableView.PALETTE_GLASS[idx] : TimetableView.PALETTE_BG[idx];
            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setText(idx == selectedColor ? "✓" : "");
            dot.setTextColor(TimetableView.PALETTE_FG[idx]);
            dot.setTypeface(Typeface.DEFAULT_BOLD);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(fill);
            circle.setStroke(dp(idx == selectedColor ? 2 : 1), TimetableView.PALETTE_FG[idx]);
            dot.setBackground(circle);

            // 8 个一行均分宽度，圆点本身固定 30dp
            android.widget.FrameLayout cell = new android.widget.FrameLayout(this);
            android.widget.FrameLayout.LayoutParams dl = new android.widget.FrameLayout.LayoutParams(dp(30), dp(30));
            dl.gravity = Gravity.CENTER;
            cell.addView(dot, dl);
            line.addView(cell, new LinearLayout.LayoutParams(0, dp(34), 1f));
            dot.setOnClickListener(v -> {
                selectedColor = idx;
                dirty = true;
                renderColorRow();
            });
        }
    }

    private void renderSessionChips() {
        sessionChipRow.removeAllViews();
        for (int i = 0; i < draftSessions.size(); i++) {
            final int idx = i;
            Timetable.Session s = draftSessions.get(i);
            TextView chip = new TextView(this);
            chip.setText("周" + DAY_LABELS[clamp(s.dayOfWeek, 1, 7) - 1]
                    + " " + (s.hasCustomTime() ? s.timeStart
                    : (s.startSection == s.endSection ? s.startSection + "节"
                    : s.startSection + "-" + s.endSection)));
            chip.setTextSize(12.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            bg.setColor(idx == selectedSessionIndex ? G.INK : G.PAPER);
            chip.setBackground(bg);
            chip.setTextColor(idx == selectedSessionIndex ? G.CARD : G.SUB);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                saveFormIntoSession();
                selectedSessionIndex = idx;
                renderSessionChips();
                loadSessionIntoForm();
            });
            sessionChipRow.addView(chip);
        }
    }

    private void loadSessionIntoForm() {
        Timetable.Session s = currentSession();
        loadingForm = true;
        selectedDay = clamp(s.dayOfWeek, 1, 7);
        selectedStart = clamp(s.startSection, 1, maxSec());
        selectedEnd = clamp(Math.max(s.startSection, s.endSection), selectedStart, maxSec());
        customTime = s.hasCustomTime();
        timeStart = customTime ? s.timeStart : "";
        timeEnd = customTime ? s.timeEnd : "";
        etLocation.setText(s.location);
        weekMode = detectWeekMode(s);
        selectedWeeks.clear();
        if (s.weeks.isEmpty()) {
            int total = Prefs.storedTotalWeeks(this);
            for (int w = 1; w <= total; w++) selectedWeeks.add(w);
        } else {
            selectedWeeks.addAll(s.weeks);
        }
        continuousStart = null;
        writeWeeksText();

        renderDaySelector();
        renderSectionSelector();
        renderWeekModeSelector();
        renderWeekChips();
        updateWeeksPreview();
        loadingForm = false;
    }

    private void renderDaySelector() {
        daySelectorRow.removeAllViews();
        for (int d = 1; d <= 7; d++) {
            final int day = d;
            TextView tv = segment("周" + DAY_LABELS[d - 1], selectedDay == d);
            tv.setOnClickListener(v -> {
                selectedDay = day;
                dirty = true;
                renderDaySelector();
            });
            daySelectorRow.addView(tv);
        }
    }

    /**
     * 节次选择：「按节次」可逐节加减（单节、三节都行）；
     * 「自定义时间」按真实起止时间画（比如考试 14:30–16:30），不用对齐节次。
     */
    private void renderSectionSelector() {
        sectionSelectorRow.removeAllViews();

        LinearLayout mode = hRow();
        TextView bySection = segment("按节次", !customTime);
        bySection.setOnClickListener(v -> { customTime = false; dirty = true; renderSectionSelector(); });
        TextView byTime = segment("自定义时间", customTime);
        byTime.setOnClickListener(v -> {
            if (!customTime) {
                customTime = true;
                if (Timetable.Session.minutesOf(timeStart) < 0) timeStart = sched.startTime(selectedStart);
                if (Timetable.Session.minutesOf(timeEnd) < 0) timeEnd = sched.endTime(selectedEnd);
                dirty = true;
                renderSectionSelector();
            }
        });
        mode.addView(bySection);
        mode.addView(byTime);
        sectionSelectorRow.addView(mode);

        LinearLayout row = hRow();
        ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = dp(6);
        if (!customTime) {
            row.addView(stepper("从第 " + selectedStart + " 节", () -> {
                if (selectedStart > 1) selectedStart--;
            }, () -> {
                if (selectedStart < maxSec()) selectedStart++;
                if (selectedEnd < selectedStart) selectedEnd = selectedStart;
            }));
            row.addView(stepper("到第 " + selectedEnd + " 节", () -> {
                if (selectedEnd > selectedStart) selectedEnd--;
            }, () -> {
                if (selectedEnd < maxSec()) selectedEnd++;
            }));
        } else {
            TextView a = segment("开始 " + timeStart, false);
            a.setOnClickListener(v -> pickTime(true));
            TextView b = segment("结束 " + timeEnd, false);
            b.setOnClickListener(v -> pickTime(false));
            row.addView(a);
            row.addView(b);
        }
        sectionSelectorRow.addView(row);

        TextView hint = new TextView(this);
        hint.setTextSize(11.5f);
        hint.setTextColor(G.MUTE);
        hint.setPadding(dp(2), dp(6), 0, 0);
        hint.setText(customTime
                ? "按真实时间显示在课表上，不用和其他课对齐（适合考试、讲座）"
                : sched.startTime(selectedStart) + " – " + sched.endTime(selectedEnd)
                        + " · 共 " + (selectedEnd - selectedStart + 1) + " 节");
        sectionSelectorRow.addView(hint);
    }

    private LinearLayout hRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
        return row;
    }

    /** [−] 文字 [+]，占半行。 */
    private View stepper(String label, Runnable minus, Runnable plus) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.setMarginEnd(dp(4));
        box.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(G.PAPER);
        box.setBackground(bg);

        TextView m = stepBtn("−", minus);
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(G.INK);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView p = stepBtn("+", plus);
        box.addView(m);
        box.addView(t);
        box.addView(p);
        return box;
    }

    private TextView stepBtn(String s, Runnable action) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(16f);
        b.setTextColor(G.INK);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        b.setOnClickListener(v -> {
            action.run();
            dirty = true;
            renderSectionSelector();
        });
        return b;
    }

    private void pickTime(boolean start) {
        int cur = Timetable.Session.minutesOf(start ? timeStart : timeEnd);
        if (cur < 0) cur = 8 * 60;
        GaugePickers.time(this, start ? "开始时间" : "结束时间", cur / 60, cur % 60, (h, m) -> {
            String v = Timetable.Session.formatHm(h * 60 + m);
            if (start) timeStart = v; else timeEnd = v;
            if (Timetable.Session.minutesOf(timeEnd) <= Timetable.Session.minutesOf(timeStart)) {
                Toast.makeText(this, "结束时间要晚于开始时间", Toast.LENGTH_SHORT).show();
            }
            dirty = true;
            renderSectionSelector();
        });
    }

    /** 写入节次/时间。自定义时间同时换算出覆盖的节次，给空闲时间、分享等按节计算的功能用。 */
    /** 当前作息每天几节（8–16）。 */
    private int maxSec() {
        return sched == null ? ScheduleSettings.DEFAULT_SECTION_COUNT : sched.sectionCount();
    }

    private void applySectionsTo(Timetable.Session s) {
        if (customTime && Timetable.Session.minutesOf(timeStart) >= 0
                && Timetable.Session.minutesOf(timeEnd) > Timetable.Session.minutesOf(timeStart)) {
            int a = Timetable.Session.minutesOf(timeStart), b = Timetable.Session.minutesOf(timeEnd);
            int first = maxSec(), last = 1;
            for (int sec = 1; sec <= maxSec(); sec++) {
                if (sched.endMinutes(sec) > a && sched.startMinutes(sec) < b) {
                    first = Math.min(first, sec);
                    last = Math.max(last, sec);
                }
            }
            if (first > last) { first = selectedStart; last = selectedEnd; }
            s.startSection = first;
            s.endSection = last;
            s.timeStart = timeStart;
            s.timeEnd = timeEnd;
        } else {
            s.startSection = selectedStart;
            s.endSection = Math.max(selectedStart, selectedEnd);
            s.timeStart = "";
            s.timeEnd = "";
        }
    }

    private void renderWeekModeSelector() {
        weekModeRow.removeAllViews();
        for (int i = 0; i < WEEK_MODE_LABELS.length; i++) {
            final int mode = i;
            TextView tv = segment(WEEK_MODE_LABELS[i], weekMode == i);
            tv.setOnClickListener(v -> {
                weekMode = mode;
                dirty = true;
                applyWeekMode();
                renderWeekModeSelector();
                renderWeekChips();
                updateWeeksPreview();
            });
            weekModeRow.addView(tv);
        }
    }

    private TextView segment(String text, boolean selected) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(12f);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setTextColor(selected ? G.INK : G.SUB);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(selected ? G.CHIP : G.PAPER);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.setMarginEnd(dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void applyWeekMode() {
        int total = Prefs.storedTotalWeeks(this);
        continuousStart = null;
        selectedWeeks.clear();
        switch (weekMode) {
            case 0:
                for (int w = 1; w <= total; w++) selectedWeeks.add(w);
                break;
            case 1:
                selectedWeeks.add(Math.min(total, currentWeek));
                break;
            case 2:
                for (int w = 1; w <= total; w += 2) selectedWeeks.add(w);
                break;
            case 3:
                for (int w = 2; w <= total; w += 2) selectedWeeks.add(w);
                break;
            default:
                selectedWeeks.add(Math.min(total, currentWeek));
                break;
        }
        writeWeeksText();
    }

    private void renderWeekChips() {
        if (weekChipContainer == null) return;
        weekChipContainer.removeAllViews();
        int total = Prefs.totalWeeks(this);

        LinearLayout row = null;
        for (int w = 1; w <= total; w++) {
            if ((w - 1) % 7 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = dp(4);
                row.setLayoutParams(rowLp);
                weekChipContainer.addView(row);
            }

            final int week = w;
            boolean selected = selectedWeeks.contains(w);
            TextView chip = new TextView(this);
            chip.setText(String.valueOf(w));
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(12f);
            chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(selected ? G.INK : G.MUTE);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            bg.setColor(selected ? G.CHIP : G.PAPER);
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            lp.setMarginEnd(dp(3));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> onWeekChipClick(week));
            if (row != null) row.addView(chip);
        }
    }

    private void onWeekChipClick(int week) {
        int total = Prefs.totalWeeks(this);
        if (weekMode == 0) {
            weekMode = 4;
            selectedWeeks.clear();
            selectedWeeks.add(week);
        } else if (weekMode == 2 || weekMode == 3) {
            weekMode = 4;
            selectedWeeks.add(week);
        } else if (weekMode == 1) {
            if (continuousStart == null) {
                continuousStart = week;
                selectedWeeks.clear();
                selectedWeeks.add(week);
            } else {
                int from = Math.min(continuousStart, week);
                int to = Math.min(total, Math.max(continuousStart, week));
                selectedWeeks.clear();
                for (int w = from; w <= to; w++) selectedWeeks.add(w);
                continuousStart = null;
            }
        } else {
            if (selectedWeeks.contains(week) && selectedWeeks.size() > 1) {
                selectedWeeks.remove(week);
            } else {
                selectedWeeks.add(week);
            }
        }
        dirty = true;
        writeWeeksText();
        renderWeekModeSelector();
        renderWeekChips();
        updateWeeksPreview();
    }

    private void writeWeeksText() {
        int total = Prefs.storedTotalWeeks(this);
        if (selectedWeeks.isEmpty()) {
            etWeeks.setText("");
            return;
        }
        if (weekMode == 0 && selectedWeeks.size() >= total) {
            etWeeks.setText("1-" + total);
        } else if (weekMode == 2) {
            etWeeks.setText("1-" + total + "周(单)");
        } else if (weekMode == 3) {
            etWeeks.setText("1-" + total + "周(双)");
        } else {
            List<Integer> sorted = new ArrayList<>(selectedWeeks);
            sorted.sort(Integer::compareTo);
            etWeeks.setText(WeekRange.compress(sorted) + "周");
        }
    }

    private int detectWeekMode(Timetable.Session s) {
        String raw = s.weeksRaw == null ? "" : s.weeksRaw;
        int total = Prefs.storedTotalWeeks(this);
        if (raw.contains("单")) return 2;
        if (raw.contains("双")) return 3;
        if (raw.equals("1-" + total) || raw.equals("1-" + total + "周")) return 0;
        if (raw.contains(",") || raw.contains("，")) return 4;
        if (raw.contains("-")) return 1;
        return 4;
    }

    private void updateWeeksPreview() {
        String raw = etWeeks.getText().toString().trim();
        WeekRange.Parsed parsed = WeekRange.parse(raw);
        if (parsed.ok()) {
            tvWeeksPreview.setText("共 " + parsed.weeks.size() + " 周 · "
                    + WeekRange.compress(parsed.weeks) + " 周");
        } else if (!selectedWeeks.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(selectedWeeks);
            sorted.sort(Integer::compareTo);
            tvWeeksPreview.setText("已选 " + sorted.size() + " 周 · " + WeekRange.compress(sorted));
        } else {
            tvWeeksPreview.setText("未识别周次，保存后将按全周显示");
        }
    }

    private void addSession() {
        saveFormIntoSession();
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = selectedDay;
        applySectionsTo(s);
        s.weeks.add(currentWeek);
        s.weeksRaw = String.valueOf(currentWeek);
        draftSessions.add(s);
        selectedSessionIndex = draftSessions.size() - 1;
        dirty = true;
        renderSessionChips();
        loadSessionIntoForm();
    }

    private void deleteCurrentSession() {
        if (draftSessions.size() <= 1) {
            Toast.makeText(this, "至少保留一个时段", Toast.LENGTH_SHORT).show();
            return;
        }
        draftSessions.remove(selectedSessionIndex);
        selectedSessionIndex = Math.max(0, selectedSessionIndex - 1);
        dirty = true;
        renderSessionChips();
        loadSessionIntoForm();
    }

    private void saveFormIntoSession() {
        if (draftSessions.isEmpty()) return;
        Timetable.Session s = currentSession();
        s.dayOfWeek = selectedDay;
        applySectionsTo(s);
        s.location = etLocation.getText().toString().trim();
        List<Integer> sorted = new ArrayList<>(selectedWeeks);
        sorted.sort(Integer::compareTo);
        int total = Prefs.storedTotalWeeks(this);
        if (weekMode == 2) {
            s.weeksRaw = "1-" + total + "周(单)";
        } else if (weekMode == 3) {
            s.weeksRaw = "1-" + total + "周(双)";
        } else {
            s.weeksRaw = WeekRange.compress(sorted);
            if (!s.weeksRaw.isEmpty()) s.weeksRaw += "周";
        }
        s.weeks.clear();
        s.weeks.addAll(sorted);
    }

    private Timetable.Session currentSession() {
        return draftSessions.get(Math.max(0, Math.min(draftSessions.size() - 1, selectedSessionIndex)));
    }

    private void save() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请填写课程名称", Toast.LENGTH_SHORT).show();
            return;
        }
        saveFormIntoSession();

        Timetable.Course target = editingCourse;
        if (target == null) {
            target = new Timetable.Course();
            target.key = "custom_" + UUID.randomUUID().toString().substring(0, 8);
            target.source = Timetable.SOURCE_CUSTOM;
            timetable.courses.add(target);
        }
        String oldName = editingCourse == null ? null : editingCourse.name;
        target.name = name;
        target.teacher = etTeacher.getText().toString().trim();
        target.note = etNote.getText().toString().trim();
        target.colorIndex = selectedColor;
        target.colorTouched = true;
        target.sessions.clear();
        for (Timetable.Session s : draftSessions) target.sessions.add(copySession(s));

        if (TimetableStore.save(this, timetable)) {
            if (oldName != null) com.ecut.campus.fun.FunPrefs.renameAbsence(this, timetable, oldName, name);
            dirty = false;
            Toast.makeText(this, "已保存课程：" + name, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmExit() {
        if (!dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("放弃未保存的修改？")
                .setMessage("返回后本次修改将不会保存。")
                .setNegativeButton("继续编辑", null)
                .setPositiveButton("放弃", (d, w) -> finish())
                .show();
    }

    private void confirmDeleteCourse() {
        if (editingCourse == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除整门课程")
                .setMessage("确定从本地课表中移除「" + editingCourse.name + "」及其全部时段吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    timetable.courses.remove(editingCourse);
                    if (TimetableStore.save(this, timetable)) {
                        dirty = false;
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private static Timetable.Session copySession(Timetable.Session src) {
        return src.copy();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
