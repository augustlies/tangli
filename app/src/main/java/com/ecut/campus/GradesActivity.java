package com.ecut.campus;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 独立成绩页，从设置首页进入。 */
public class GradesActivity extends AppCompatActivity {

    private Grades currentGrades = new Grades();
    private String selectedSemesterFilter = "ALL";
    private CampusSyncClient activeSync;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_grades);

        findViewById(R.id.btn_grades_back).setOnClickListener(v -> finish());

        View syncBtn = findViewById(R.id.btn_sync_grades);
        if (syncBtn != null) syncBtn.setOnClickListener(v -> syncGrades());
        View emptySyncBtn = findViewById(R.id.btn_empty_sync_grades);
        if (emptySyncBtn != null) emptySyncBtn.setOnClickListener(v -> syncGrades());

        View demoBtn = findViewById(R.id.btn_load_demo_grades);
        if (demoBtn != null) {
            demoBtn.setOnClickListener(v -> {
                DemoData.seedGradesOnly(this);
                refresh();
                Toast.makeText(this, "已载入演示成绩（已标记）", Toast.LENGTH_SHORT).show();
            });
        }

        refresh();
    }

    /** 本次打开时的新成绩，离开页面后才清掉，方便用户看清是哪几门。 */
    private final java.util.Set<String> newIds = new java.util.HashSet<>();

    @Override
    protected void onResume() {
        super.onResume();
        newIds.clear();
        for (String k : Prefs.newGradeKeys(this)) {
            int bar = k.indexOf('|');
            newIds.add(bar >= 0 ? k.substring(bar + 1) : k);
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.setNewGradeKeys(this, new java.util.ArrayList<>());
        ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2001);
    }

    private void refresh() {
        currentGrades = GradesStore.load(this);
        currentGrades.recomputeAll();

        LinearLayout pillBox = findViewById(R.id.semester_pill_container);
        if (pillBox != null) {
            pillBox.removeAllViews();
            addSemesterPill(pillBox, "ALL", "全部学期");
            for (Grades.SemesterGrades sem : currentGrades.semesters) {
                addSemesterPill(pillBox, sem.id, formatShortSemester(sem.id));
            }
        }

        TextView tvGpa = findViewById(R.id.tv_gpa_value);
        TextView tvWeighted = findViewById(R.id.tv_weighted_value);
        TextView tvCreditSummary = findViewById(R.id.tv_grades_credit_summary);
        TextView tvScopeTag = findViewById(R.id.tv_grades_scope_tag);
        TextView tvSubtitle = findViewById(R.id.tv_grades_subtitle);

        if (tvSubtitle != null) {
            tvSubtitle.setText(Prefs.isGradesDemo(this)
                    ? "演示数据 · 仅用于预览界面，不代表真实成绩"
                    : currentGrades.totalCourseCount() + " 门课程 · "
                    + currentGrades.semesters.size() + " 个学期 · 本地秒开");
        }

        List<Grades.CourseGrade> displayCourses = new ArrayList<>();
        if ("ALL".equals(selectedSemesterFilter)) {
            for (Grades.SemesterGrades s : currentGrades.semesters) {
                displayCourses.addAll(s.courses);
            }
            if (tvGpa != null) {
                tvGpa.setText(String.format(Locale.US, "%.2f", currentGrades.summary.gpa));
            }
            if (tvWeighted != null) {
                tvWeighted.setText(String.format(Locale.US, "%.1f", currentGrades.summary.weightedScore));
            }
            if (tvCreditSummary != null) {
                tvCreditSummary.setText(String.format(Locale.US, "%.1f 学分 · %d 门通过",
                        currentGrades.summary.totalCredit, currentGrades.summary.passedCount));
            }
            if (tvScopeTag != null) tvScopeTag.setText("全部学期");
        } else {
            Grades.SemesterGrades matched = null;
            for (Grades.SemesterGrades s : currentGrades.semesters) {
                if (selectedSemesterFilter.equals(s.id)) {
                    matched = s;
                    break;
                }
            }
            if (matched != null) {
                matched.recompute();
                displayCourses.addAll(matched.courses);
                if (tvGpa != null) tvGpa.setText(String.format(Locale.US, "%.2f", matched.gpa));
                if (tvWeighted != null) {
                    tvWeighted.setText(String.format(Locale.US, "%.1f", matched.weightedScore));
                }
                if (tvCreditSummary != null) {
                    tvCreditSummary.setText(String.format(Locale.US, "%.1f 学分 · %d 门课程",
                            matched.totalCredit, matched.courses.size()));
                }
                if (tvScopeTag != null) tvScopeTag.setText(formatShortSemester(matched.id));
            } else {
                selectedSemesterFilter = "ALL";
            }
        }

        View emptyCard = findViewById(R.id.empty_grades_card);
        if (emptyCard != null) {
            emptyCard.setVisibility(displayCourses.isEmpty() ? View.VISIBLE : View.GONE);
        }

        LinearLayout listBox = findViewById(R.id.grades_list_container);
        if (listBox == null) return;
        listBox.removeAllViews();

        LayoutInflater inf = LayoutInflater.from(this);
        int idx = 0;
        for (Grades.CourseGrade cg : displayCourses) {
            idx++;
            View row = inf.inflate(R.layout.item_grade_card, listBox, false);
            View bar = row.findViewById(R.id.grade_color_bar);
            TextView tvIdx = row.findViewById(R.id.tv_grade_index);
            TextView tvName = row.findViewById(R.id.tv_grade_course_name);
            TextView tvMeta = row.findViewById(R.id.tv_grade_course_meta);
            TextView tvScore = row.findViewById(R.id.tv_grade_score_badge);

            tvIdx.setText(String.format(Locale.US, "%02d", idx));
            String cid = cg.code == null || cg.code.isEmpty() ? cg.name : cg.code;
            if (newIds.contains(cid)) {
                android.text.SpannableString sp = new android.text.SpannableString("● " + cg.name);
                sp.setSpan(new android.text.style.ForegroundColorSpan(G.ACCENT), 0, 1, 0);
                tvName.setText(sp);
            } else {
                tvName.setText(cg.name);
            }
            String gpText = cg.gradePoint != null ? String.format(Locale.US, "%.2f", cg.gradePoint) : "—";
            String nature = (cg.nature != null && !cg.nature.isEmpty()) ? cg.nature : "必修";
            tvMeta.setText(nature + " · " + cg.credit + " 学分 · 绩点 " + gpText);

            double v = cg.scoreValue != null ? cg.scoreValue : (cg.isPassed ? 85.0 : 50.0);
            boolean failed = !cg.isPassed || v < 60.0;
            int ink = failed ? G.ACCENT : G.INK;
            tvScore.setText(cg.score);
            tvScore.setTextColor(ink);
            // 非数字成绩（优秀/合格）字号收小，保持右侧刻度对齐
            boolean numeric = cg.score != null && cg.score.matches("[0-9.]+");
            tvScore.setTextSize(numeric ? 22f : 14f);

            bar.setBackgroundColor(ink);
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.width = Math.round(dp(56) * (float) Math.max(0.0, Math.min(100.0, v)) / 100f);
            bar.setLayoutParams(lp);

            listBox.addView(row);
        }
    }

    /** 学期刻度标签：选中为墨色粗字 + 2dp 墨线，未选为弱文字，无胶囊。 */
    private void addSemesterPill(LinearLayout container, String semId, String label) {
        boolean selected = semId.equals(selectedSemesterFilter);
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setPadding(0, 0, dp(18), 0);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTypeface(selected ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) : Typeface.MONOSPACE);
        tv.setTextColor(selected ? G.INK : G.MUTE);
        tv.setPadding(0, dp(4), 0, dp(10));
        tv.setSingleLine(true);
        tab.addView(tv);

        View line = new View(this);
        line.setBackgroundColor(selected ? G.INK : 0x00000000);
        tab.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        tab.setOnClickListener(v -> {
            selectedSemesterFilter = semId;
            refresh();
        });
        container.addView(tab);
    }

    private String formatShortSemester(String id) {
        if (id == null || id.isEmpty()) return "未知学期";
        String[] p = id.split("-");
        if (p.length >= 3 && p[0].length() == 4 && p[1].length() == 4) {
            return p[0].substring(2) + "-" + p[1].substring(2) + "(" + p[2] + ")";
        }
        return id;
    }

    private void openWeb(String targetKey) {
        Intent i = new Intent(this, WebShellActivity.class);
        i.putExtra(WebShellActivity.EXTRA_TARGET_KEY, targetKey);
        startActivity(i);
    }

    private void syncGrades() {
        if (activeSync != null && activeSync.isRunning()) {
            Toast.makeText(this, "正在同步，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Prefs.hasPortalSession(this)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("请先登录统一门户")
                    .setMessage("登录一次后，课表与成绩都能在后台静默更新，不再跳转网页。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去登录", (d, w) -> openWeb("portal"))
                    .show();
            return;
        }

        ViewGroup host = findViewById(R.id.grades_sync_host);
        WebView view = new WebView(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        view.setAlpha(0f);
        host.addView(view);

        Toast.makeText(this, "正在后台同步成绩…", Toast.LENGTH_SHORT).show();
        activeSync = CampusSyncClient.start(view, CampusSyncClient.Kind.GRADES,
                new CampusSyncClient.Callback() {
                    @Override
                    public void onSuccess(int count) {
                        activeSync = null;
                        refresh();
                        Toast.makeText(GradesActivity.this,
                                "成绩已更新：" + count + " 门", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String message) {
                        activeSync = null;
                        Toast.makeText(GradesActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (activeSync != null) {
            activeSync.cancel();
            activeSync = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
