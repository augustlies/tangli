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
    private final EditText[] durationInputs = new EditText[ScheduleSettings.SECTION_COUNT];
    private final EditText[] breakInputs = new EditText[ScheduleSettings.SECTION_COUNT];
    private final TextView[] previewViews = new TextView[ScheduleSettings.SECTION_COUNT];
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
                        .setMessage("将第 1-10 节的时长与课间休息全部恢复为默认值。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("恢复", (d, w) -> {
                            settings.resetToDefaults();
                            etFirstStart.setText(settings.firstClassStart);
                            rebuildRows();
                        })
                        .show());

        etFirstStart.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyFirstStart();
        });

        rebuildRows();
    }

    private void rebuildRows() {
        container.removeAllViews();
        for (int i = 0; i < ScheduleSettings.SECTION_COUNT; i++) {
            addSectionRow(i);
        }
        refreshPreview();
    }

    private void addSectionRow(int index) {
        ScheduleSettings.Section sec = settings.section(index + 1);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(G.CARD);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setStrokeColor(G.LINE);
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
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                syncInputsToModel();
                refreshPreview();
            }
        });
        return et;
    }

    private void syncInputsToModel() {
        String first = etFirstStart.getText().toString().trim();
        if (ScheduleSettings.parseHm(first) >= 0) settings.firstClassStart = first;

        for (int i = 0; i < ScheduleSettings.SECTION_COUNT; i++) {
            ScheduleSettings.Section sec = settings.section(i + 1);
            sec.durationMinutes = parseOr(trimmed(durationInputs[i]), sec.durationMinutes, 1, 300);
            sec.breakAfterMinutes = parseOr(trimmed(breakInputs[i]), sec.breakAfterMinutes, 0, 600);
        }
    }

    private void refreshPreview() {
        syncInputsToModel();
        for (int i = 0; i < ScheduleSettings.SECTION_COUNT; i++) {
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
