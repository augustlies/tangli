package com.ecut.campus;

import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.SemesterMetaPolicy;
import com.ecut.campus.data.TimetableStore;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;

/** 课表设置：学期日期、总周数、七天/五天、非本周课程开关。 */
public class TimetableSettingsActivity extends AppCompatActivity {

    private TextView tvStartValue;
    private TextView tvPreview;
    private TextView tvFirstCourse;
    private TextView btnSeven;
    private TextView btnFive;
    private EditText etTotalWeeks;
    private MaterialSwitch switchInactive;

    private String startDate;
    private int displayDays;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_timetable_settings);

        String storedStart = Prefs.semesterStartDate(this);
        startDate = WeekMath.normalizeToMonday(storedStart);
        displayDays = Prefs.displayDays(this);

        tvStartValue = findViewById(R.id.tv_semester_start_value);
        tvPreview = findViewById(R.id.tv_current_week_preview);
        tvFirstCourse = findViewById(R.id.tv_first_course_preview);
        btnSeven = findViewById(R.id.btn_days_seven);
        btnFive = findViewById(R.id.btn_days_five);
        etTotalWeeks = findViewById(R.id.et_total_weeks);
        switchInactive = findViewById(R.id.switch_show_inactive);

        // 不再让用户设学期总周数：只看开学日期，周次往后排
        etTotalWeeks.setVisibility(android.view.View.GONE);
        android.view.View totalLabel = findViewById(R.id.tv_total_weeks_label);
        if (totalLabel != null) totalLabel.setVisibility(android.view.View.GONE);
        switchInactive.setChecked(Prefs.showInactiveCourses(this));
        switchInactive.setOnCheckedChangeListener((btn, checked) ->
                Prefs.setShowInactiveCourses(this, checked));

        findViewById(R.id.btn_timetable_settings_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_timetable_settings_save).setOnClickListener(v -> save());
        tvStartValue.setOnClickListener(v -> showDatePicker());
        btnSeven.setOnClickListener(v -> selectDays(7));
        btnFive.setOnClickListener(v -> selectDays(5));

        renderStartDate();
        selectDays(displayDays);
        refreshPreview();
    }

    private void showDatePicker() {
        Calendar cal = WeekMath.parseDate(startDate);
        if (cal == null) cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String picked = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    year, month + 1, dayOfMonth);
            startDate = WeekMath.normalizeToMonday(picked);
            renderStartDate();
            refreshPreview();
            if (!picked.equals(startDate)) {
                Toast.makeText(this, "已调整为当周周一：" + startDate, Toast.LENGTH_SHORT).show();
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void renderStartDate() {
        tvStartValue.setText(startDate == null || startDate.isEmpty()
                ? "点击选择日期"
                : startDate + "（周一）");
    }

    private void selectDays(int days) {
        displayDays = days == 5 ? 5 : 7;
        styleSegment(btnSeven, displayDays == 7);
        styleSegment(btnFive, displayDays == 5);
        Prefs.setDisplayDays(this, displayDays);
    }

    private void styleSegment(TextView tv, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(selected ? G.CARD : G.CLEAR);
        tv.setBackground(bg);
        tv.setTextColor(selected ? G.INK : G.MUTE);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void refreshPreview() {
        if (startDate == null || startDate.isEmpty()) {
            tvPreview.setText("请先设置学期第一周周一日期");
            return;
        }
        int current = Prefs.hasCurrentWeek(this)
                ? Math.max(1, Math.min(WeekMath.openWeekSpan(startDate), Prefs.currentWeek(this)))
                : WeekMath.currentWeek(startDate, WeekMath.openWeekSpan(startDate));
        tvPreview.setText("第 " + current + " 周 · " + WeekMath.weekRangeLabel(startDate, current));
        Timetable timetable = TimetableStore.load(this);
        if (tvFirstCourse != null) {
            tvFirstCourse.setText("首节课：" + WeekMath.firstCourseLabel(startDate, timetable));
        }
    }

    /** 归档字段原样保留，界面不再编辑它。 */
    private int parseTotalWeeks() {
        return Prefs.storedTotalWeeks(this);
    }

    private void save() {
        String normalizedStart = WeekMath.normalizeToMonday(startDate);
        if (normalizedStart.isEmpty()) {
            Toast.makeText(this, "请先设置学期第一周周一日期", Toast.LENGTH_SHORT).show();
            return;
        }
        startDate = normalizedStart;
        int total = parseTotalWeeks();
        Prefs.setDisplayDays(this, displayDays);
        Prefs.setShowInactiveCourses(this, switchInactive.isChecked());
        if (!SemesterArchive.updateActiveSemesterMeta(this, startDate, total)) {
            Toast.makeText(this, "保存失败：找不到当前学期，请先同步课表", Toast.LENGTH_LONG).show();
            return;
        }
        if (!SemesterArchive.ensureActiveMirror(this)) {
            Toast.makeText(this, "日期已写入主源，但本地镜像刷新失败，请重试", Toast.LENGTH_LONG).show();
            return;
        }
        // 设置页只写归档主源；归档会同步刷新 timetable.json 与 Prefs。
        Toast.makeText(this, "课表设置已保存", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
