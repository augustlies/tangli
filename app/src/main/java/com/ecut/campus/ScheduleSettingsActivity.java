package com.ecut.campus;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/** 作息时间编辑：第 1 节开始时间、每节时长、课后休息，实时预览。 */
public class ScheduleSettingsActivity extends AppCompatActivity {

    private ScheduleSettings settings;
    private EditText etFirstStart;
    private final EditText[] durationInputs = new EditText[ScheduleSettings.MAX_SECTIONS];
    private final EditText[] breakInputs = new EditText[ScheduleSettings.MAX_SECTIONS];
    private final TextView[] previewViews = new TextView[ScheduleSettings.MAX_SECTIONS];
    private LinearLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_schedule_settings);

        settings = ScheduleSettingsStore.load(this);
        container = findViewById(R.id.section_settings_container);
        etFirstStart = findViewById(R.id.et_first_class_start);
        etFirstStart.setText(settings.firstClassStart);

        findViewById(R.id.btn_schedule_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_schedule_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_schedule_reset).setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("恢复默认作息")
                        .setMessage("将每天节数恢复为 10 节，时长与课间休息全部恢复为默认值。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("恢复", (d, w) -> {
                            settings.resetToDefaults();
                            etFirstStart.setText(settings.firstClassStart);
                            rebuildRows();
                        })
                        .show());

        // 第 1 节开始时间也改成滚轮选择，不弹键盘
        etFirstStart.setFocusable(false);
        etFirstStart.setCursorVisible(false);
        etFirstStart.setOnClickListener(v -> {
            int cur = ScheduleSettings.parseHm(etFirstStart.getText().toString().trim());
            if (cur < 0) cur = 8 * 60;
            GaugePickers.time(this, "第 1 节开始", cur / 60, cur % 60, (h, m) -> {
                etFirstStart.setText(ScheduleSettings.formatHm(h * 60 + m));
                applyFirstStart();
            });
        });

        rebuildRows();
    }

    private void rebuildRows() {
        container.removeAllViews();
        java.util.Arrays.fill(durationInputs, null);
        java.util.Arrays.fill(breakInputs, null);
        java.util.Arrays.fill(previewViews, null);
        addCountRow();
        for (int i = 0; i < settings.sectionCount(); i++) {
            addSectionRow(i);
        }
        refreshPreview();
    }

    /**
     * 改每天节数。调少时如果课表里还有更后面节次的课，先把这些课列出来让用户选，
     * 不悄悄把选择改回去。
     */
    private void applySectionCount(int want) {
        com.ecut.campus.data.Timetable tt = com.ecut.campus.data.TimetableStore.load(this);
        int used = ScheduleSettings.maxSectionUsed(tt);
        if (want < used) {
            StringBuilder list = new StringBuilder();
            int shown = 0, more = 0;
            for (com.ecut.campus.data.Timetable.Course c : tt.courses) {
                for (com.ecut.campus.data.Timetable.Session se : c.sessions) {
                    if (se.endSection <= want) continue;
                    if (shown < 6) {
                        list.append("· ").append(c.name).append("  ").append(com.ecut.campus.data.Dates.cnDay(se.dayOfWeek))
                                .append(" 第 ").append(se.startSection).append(se.endSection > se.startSection ? "-" + se.endSection : "")
                                .append(" 节\n");
                        shown++;
                    } else more++;
                }
            }
            if (more > 0) list.append("…还有 ").append(more).append(" 个时段\n");
            final int keep = used;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("还有课排在第 " + (want + 1) + " 节以后")
                    .setMessage(list.toString().trim() + "\n\n调成 " + want + " 节的话这些课会看不见。先把它们删掉或改到前面的节次，再来调少；或者先按 " + keep + " 节保存。")
                    .setNegativeButton("不改了", null)
                    .setPositiveButton("按 " + keep + " 节保存", (d, w) -> {
                        setCount(keep);
                    })
                    .show();
            return;
        }
        setCount(want);
    }

    private void setCount(int n) {
        if (n == settings.sectionCount()) return;
        if (n > settings.sectionCount()) settings.ensureCovers(n);
        else settings.setSectionCount(n);
        rebuildRows();
    }

    /** 每天几节：8–16 节，不同学校不一样。点开刻度拨盘选。 */
    private void addCountRow() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(14);
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(G.CARD);
        card.setRadius(dp(Math.round(G.CARD_RADIUS)));
        card.setCardElevation(0);
        card.setStrokeColor(G.CARD_STROKE);
        card.setStrokeWidth(dp(1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundResource(Ui.selectable(this, false));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("每天几节课");
        title.setTextSize(14f);
        title.setTextColor(G.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText("按你们学校的作息来，8–16 节；导入的课用到更后面的节次会自动加上（删课后不会自动缩回）");
        sub.setTextSize(12f);
        sub.setTextColor(G.SUB);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText(settings.sectionCount() + " 节 ›");
        value.setTextSize(15f);
        value.setTextColor(G.INK);
        value.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vl.setMarginStart(dp(12));
        row.addView(value, vl);

        row.setOnClickListener(v -> {
            syncInputsToModel();
            com.ecut.campus.view.RulerDialView dial = new com.ecut.campus.view.RulerDialView(this);
            dial.configure(ScheduleSettings.MIN_SECTIONS, ScheduleSettings.MAX_SECTIONS, settings.sectionCount(), "节");
            LinearLayout box = new LinearLayout(this);
            box.setPadding(dp(8), dp(4), dp(8), 0);
            box.addView(dial, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("每天几节课")
                    .setView(box)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (d, w) -> {
                        applySectionCount(dial.value());
                    })
                    .show();
        });
        card.addView(row);
        container.addView(card);
    }

    private void addSectionRow(int index) {
        ScheduleSettings.Section sec = settings.section(index + 1);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(G.CARD);
        card.setRadius(dp(Math.round(G.CARD_RADIUS)));
        card.setCardElevation(0);
        card.setStrokeColor(G.CARD_STROKE);
        card.setStrokeWidth(dp(1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView number = new TextView(this);
        number.setText("第 " + (index + 1) + " 节");
        number.setTextSize(14f);
        number.setTextColor(G.INK);
        number.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        number.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(number);

        previewViews[index] = new TextView(this);
        previewViews[index].setTextSize(12.5f);
        previewViews[index].setTextColor(G.INK);
        previewViews[index].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(previewViews[index]);
        row.addView(top);

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        inputs.setPadding(0, dp(10), 0, 0);

        inputs.addView(inputLabel("时长"));
        durationInputs[index] = numberInput(String.valueOf(sec.durationMinutes), index, true);
        inputs.addView(durationInputs[index]);

        inputs.addView(inputLabel("课后休息"));
        breakInputs[index] = numberInput(String.valueOf(sec.breakAfterMinutes), index, false);
        inputs.addView(breakInputs[index]);

        row.addView(inputs);
        card.addView(row);
        container.addView(card);
    }

    private TextView inputLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(G.MUTE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        lp.setMarginStart(dp(6));
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText numberInput(String value, int index, boolean duration) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextSize(13.5f);
        et.setTextColor(G.INK);
        et.setGravity(Gravity.CENTER);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_gauge_input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(66), dp(40));
        lp.setMarginEnd(dp(4));
        et.setLayoutParams(lp);
        // 不再弹键盘：点一下弹出刻度拨盘，左右拨动选分钟
        et.setFocusable(false);
        et.setCursorVisible(false);
        et.setOnClickListener(v -> showDial(et, duration, index));
        return et;
    }

    private void showDial(EditText target, boolean duration, int index) {
        int cur;
        try { cur = Integer.parseInt(target.getText().toString().trim()); } catch (Exception e) { cur = duration ? 45 : 10; }
        com.ecut.campus.view.RulerDialView dial = new com.ecut.campus.view.RulerDialView(this);
        dial.configure(duration ? 5 : 0, duration ? 180 : 120, cur, "分钟");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        box.addView(dial, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("第 " + (index + 1) + " 节 · " + (duration ? "上课时长" : "课后休息"))
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> {
                    target.setText(String.valueOf(dial.value()));
                    syncInputsToModel();
                    refreshPreview();
                })
                .show();
    }

    private void syncInputsToModel() {
        String first = etFirstStart.getText().toString().trim();
        if (ScheduleSettings.parseHm(first) >= 0) settings.firstClassStart = first;

        for (int i = 0; i < settings.sectionCount(); i++) {
            if (durationInputs[i] == null || breakInputs[i] == null) continue;
            ScheduleSettings.Section sec = settings.section(i + 1);
            sec.durationMinutes = parseOr(trimmed(durationInputs[i]), sec.durationMinutes, 1, 300);
            sec.breakAfterMinutes = parseOr(trimmed(breakInputs[i]), sec.breakAfterMinutes, 0, 600);
        }
    }

    private void refreshPreview() {
        syncInputsToModel();
        for (int i = 0; i < settings.sectionCount(); i++) {
            if (previewViews[i] != null) {
                previewViews[i].setText(settings.rangeLabel(i + 1));
            }
        }
    }

    private void applyFirstStart() {
        String first = etFirstStart.getText().toString().trim();
        if (ScheduleSettings.parseHm(first) >= 0) {
            settings.firstClassStart = first;
            refreshPreview();
        } else {
            etFirstStart.setText(settings.firstClassStart);
            Toast.makeText(this, "时间格式应为 HH:mm", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        applyFirstStart();
        syncInputsToModel();
        if (ScheduleSettingsStore.save(this, settings)) {
            Toast.makeText(this, "作息时间已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private static String trimmed(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }

    private static int parseOr(String text, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(text)));
        } catch (Exception e) {
            return fallback;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
