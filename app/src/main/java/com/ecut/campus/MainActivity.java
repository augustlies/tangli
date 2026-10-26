package com.ecut.campus;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.IcsExporter;
import com.ecut.campus.data.SchoolProfile;
import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;
import com.ecut.campus.view.FlowLayout;
import com.ecut.campus.view.WeekDialView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AppActions.Host {

    private static final int REQ_VPN = 1001;
    private static final int REQ_IMPORT_JSON = 2001;
    private static final int TAB_TIMETABLE = 0;
    private static final int TAB_ADBLOCK = 1;
    private static final int TAB_SETTINGS = 2;

    private View campusFragment;
    private View adblockFragment;
    private View settingsFragment;
    private TimetableView timetableView;
    private Timetable currentTimetable = new Timetable();
    private ScheduleSettings scheduleSettings = ScheduleSettings.createDefault();

    private CapsuleDock textDock;
    private DockBlurView dockBlur;
    private TextView statusText;
    private TextView statusSubtext;
    private TextView shieldBadge;
    private com.ecut.campus.view.ShieldRingView toggleBtn;
    private MaterialCardView adblockHeroCard;
    private ViewGroup syncHost;
    private CampusSyncClient activeSync;
    private int currentTab = TAB_TIMETABLE;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_main);

        ViewGroup container = findViewById(R.id.content_container);
        syncHost = findViewById(R.id.sync_host);
        LayoutInflater inflater = getLayoutInflater();
        contentContainer = container;
        // 冷启动只建课表页；拦截、设置两页第一次点开时才创建（秒开）
        campusFragment = inflater.inflate(R.layout.fragment_campus, container, false);
        container.addView(campusFragment);

        textDock = findViewById(R.id.text_dock);
        dockBlur = findViewById(R.id.dock_blur);
        if (dockBlur != null) {
            // 纸墨的胶囊是实心墨色，用不着模糊层
            if (G.GLASS) dockBlur.setSource(container);
            else dockBlur.setVisibility(View.GONE);
        }
        textDock.setOnTabSelectedListener(this::showTab);
        // 在课表页再点一次「课表」：回到本周（看别的周看迷路时最顺手的出口）
        textDock.setOnTabReselectedListener(tab -> {
            if (tab != TAB_TIMETABLE) return;
            int actual = actualCurrentWeek();
            if (Prefs.currentWeek(this) == actual) return;
            Prefs.setCurrentWeek(this, actual);
            refreshTimetableTab();
            Toast.makeText(this, "已回到本周（第 " + actual + " 周）", Toast.LENGTH_SHORT).show();
        });

        bindTimetableTab();
        runStartupMigrationsOnce();

        textDock.post(() -> {
            if (timetableView != null) timetableView.setBottomInset(dockInset());
        });

        // 冷启动直接定位到现实中的本周，onResume 就不必再刷一遍
        String today = WeekMath.todayDateLabel();
        if (!today.equals(lastShownDay)) {
            lastShownDay = today;
            Prefs.setCurrentWeek(this, actualCurrentWeek());
        }
        showTab(TAB_TIMETABLE);
        skipNextResumeRefresh = true;
        if (ThemeReveal.restoreTab >= 0) {
            int tab = ThemeReveal.restoreTab, page = ThemeReveal.restorePage;
            ThemeReveal.restoreTab = -1;
            ThemeReveal.restorePage = -1;
            showTab(tab);
            if (tab == TAB_SETTINGS && page > 0 && page != R.id.settings_page_root) showSettingsPage(page);
        }
        ThemeReveal.play(this);
        // 首帧画完再去查更新（联网 + 可能弹窗），不挡冷启动
        container.postDelayed(() -> {
            if (!isFinishing()) Updater.autoCheck(this);
        }, 1500);
        // 从 tangli:// 链接冷启动
        Intent launch = getIntent();
        if (launch != null && (launch.getData() != null || launch.hasExtra(AppActions.EXTRA_ACTION))) {
            container.post(() -> handleExternalIntent(launch));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    /** tangli:// 链接（弹预览）或其它页面转过来的动作（同步、切页签）。 */
    private void handleExternalIntent(Intent intent) {
        if (intent == null || isFinishing()) return;
        String action = intent.getStringExtra(AppActions.EXTRA_ACTION);
        if (action != null) {
            String value = intent.getStringExtra(AppActions.EXTRA_VALUE);
            intent.removeExtra(AppActions.EXTRA_ACTION);
            runHostAction(action, value == null ? "" : value);
            return;
        }
        Uri data = intent.getData();
        if (data != null && CommandUi.SCHEME.equalsIgnoreCase(data.getScheme())) {
            intent.setData(null);
            showTab(TAB_TIMETABLE);
            CommandUi.openLink(this, data, this::onCommandApplied);
        }
    }

    @Override
    public void runHostAction(String action, String value) {
        if ("sync".equals(action)) {
            showTab(TAB_TIMETABLE);
            if ("grades".equals(value)) syncGrades();
            else if ("history".equals(value)) syncHistorySemesters();
            else syncTimetable();
        } else if ("export".equals(action) && "ics".equals(value)) {
            exportIcs();
        } else if ("open".equals(action)) {
            if ("settings".equals(value)) showTab(TAB_SETTINGS);
            else if ("adblock".equals(value)) showTab(TAB_ADBLOCK);
        } else if ("dns".equals(action) && "start".equals(value)) {
            // 快捷开关 / 暂停结束通知：没授权的话在这里补授权
            showTab(TAB_ADBLOCK);
            if (!DnsFilterService.isRunning) prepareAndStartVpn();
        }
    }

    /** 烫梨指令应用（或撤销）之后：课表重画；设置可能变了，拦截页和设置页下次打开时重建。 */
    void onCommandApplied() {
        refreshTimetableTab();
        boolean rebuild = false;
        if (adblockFragment != null) {
            contentContainer.removeView(adblockFragment);
            adblockFragment = null;
            rebuild = true;
        }
        if (settingsFragment != null) {
            contentContainer.removeView(settingsFragment);
            settingsFragment = null;
            rebuild = true;
        }
        if (rebuild && currentTab != TAB_TIMETABLE) showTab(currentTab);
    }

    private ViewGroup contentContainer;
    private boolean skipNextResumeRefresh;
    private static final String STARTUP_PREFS = "startup_meta";

    /**
     * 学期数据的清理 / 迁移 / 镜像对齐原来每次冷启动都读写 JSON；
     * 它们都是一次性修复，按版本号只跑一次（装新版本后会再跑一遍）。
     */
    private void runStartupMigrationsOnce() {
        android.content.SharedPreferences sp = getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE);
        if (sp.getInt("migrated_vc", 0) == BuildConfig.VERSION_CODE) return;
        // 一次性清理历史学期被全局日期污染的 startDate（有 startDateTouched 标记的不动）
        SemesterArchive.clearUnsetStartDates(this);
        // 一次性迁移：恢复 26-27-1 的真实 9/7，清掉其余学期被扩散的 9/7
        SemesterArchive.migratePollutedStartDates(this);
        // 迁移后以归档为准刷新镜像，防止 Prefs/镜像里的旧日期复活
        SemesterArchive.reconcileActiveMeta(this);
        sp.edit().putInt("migrated_vc", BuildConfig.VERSION_CODE).apply();
    }

    private void ensureAdblockTab() {
        if (adblockFragment != null) return;
        adblockFragment = getLayoutInflater().inflate(R.layout.fragment_adblock, contentContainer, false);
        contentContainer.addView(adblockFragment);
        bindAdBlock();
    }

    private void ensureSettingsTab() {
        if (settingsFragment != null) return;
        settingsFragment = getLayoutInflater().inflate(R.layout.fragment_settings, contentContainer, false);
        contentContainer.addView(settingsFragment);
        bindSettings();
    }

    /** 第一次看到有课的课表时，提示一次「长按空白处画课」。 */
    private void maybeShowDrawTip(com.ecut.campus.data.Timetable t) {
        if (t == null || t.courses.isEmpty()) return;
        android.content.SharedPreferences sp = getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE);
        if (sp.getBoolean("tip_draw_course", false)) return;
        sp.edit().putBoolean("tip_draw_course", true).apply();
        Toast.makeText(this, "小技巧：长按空白格子再上下拖，可以直接画一节课", Toast.LENGTH_LONG).show();
    }

    private void showTab(int tab) {
        currentTab = tab;
        if (tab == TAB_ADBLOCK) ensureAdblockTab();
        if (tab == TAB_SETTINGS) ensureSettingsTab();
        campusFragment.setVisibility(tab == TAB_TIMETABLE ? View.VISIBLE : View.GONE);
        if (adblockFragment != null) adblockFragment.setVisibility(tab == TAB_ADBLOCK ? View.VISIBLE : View.GONE);
        if (settingsFragment != null) settingsFragment.setVisibility(tab == TAB_SETTINGS ? View.VISIBLE : View.GONE);

        if (textDock != null && textDock.selectedTab() != tab) textDock.setSelected(tab, true);
        if (dockBlur != null) dockBlur.markDirty();

        if (tab == TAB_TIMETABLE) refreshTimetableTab();
        else if (tab == TAB_ADBLOCK) refreshStatus();
        else refreshSettingsTab();
    }

    /** 悬浮 Dock 占掉的底部高度（胶囊高 + 离底边距 + 一点呼吸空间）。 */
    private int dockInset() {
        if (textDock == null || textDock.getHeight() == 0) return 0;
        android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) textDock.getLayoutParams();
        return textDock.getHeight() + lp.bottomMargin + Math.round(6 * getResources().getDisplayMetrics().density);
    }

    // ==================== 课表 ====================

    private void bindTimetableTab() {
        timetableView = campusFragment.findViewById(R.id.timetable_view);
        if (timetableView != null) {
            timetableView.setOnSessionClickListener(new TimetableView.OnSessionClickListener() {
                @Override
                public void onSessionClick(Timetable.Course course, Timetable.Session session,
                                           boolean activeThisWeek) {
                    openCourseEditor(course, session);
                }

                @Override
                public void onEmptySlotClick(int dayOfWeek, int startSection) {
                    openCourseEditor(null, dayOfWeek, startSection);
                }

                @Override
                public void onEmptyRangeDrawn(int dayOfWeek, int startSection, int endSection) {
                    Intent i = new Intent(MainActivity.this, CourseEditActivity.class);
                    i.putExtra(CourseEditActivity.EXTRA_DEFAULT_DAY, dayOfWeek);
                    i.putExtra(CourseEditActivity.EXTRA_DEFAULT_SECTION, startSection);
                    i.putExtra(CourseEditActivity.EXTRA_DEFAULT_END_SECTION, endSection);
                    i.putExtra(CourseEditActivity.EXTRA_CURRENT_WEEK, currentViewedWeek());
                    startActivity(i);
                }

                @Override
                public void onHint(String message) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onWeekSwipe(int delta) {
                    changeWeek(delta);
                }

                @Override
                public void onMarkedClick(Timetable.Course course, Timetable.Session session,
                                          String label, String adjustId, boolean extra) {
                    CommandUi.onMarked(MainActivity.this, course, session, label, adjustId, extra,
                            MainActivity.this::onCommandApplied, MainActivity.this::openCourseEditor);
                }

                @Override
                public void onSessionLongPress(Timetable.Course course, Timetable.Session session,
                                               boolean active, String label, String adjustId,
                                               boolean extra, int week) {
                    CommandUi.onLongPress(MainActivity.this, course, session, active, label, adjustId,
                            extra, week, MainActivity.this::onCommandApplied,
                            MainActivity.this::openCourseEditor);
                }

                @Override
                public void onWeekSettling(int delta) {
                    // 手指一松就先翻标题，不等吸附动画结束
                    int total = Prefs.totalWeeks(MainActivity.this);
                    int next = Math.max(1, Math.min(total, currentViewedWeek() + delta));
                    TextView title = campusFragment.findViewById(R.id.tv_week_title);
                    if (title != null) title.setText(gaugeWeekTitle(next, total));
                }
            });
            timetableView.setOnVerticalScrollListener(() -> {
                if (dockBlur != null) dockBlur.markDirty();
            });
        }

        TextView scrubTitle = campusFragment.findViewById(R.id.tv_week_title);
        if (scrubTitle != null) {
            scrubTitle.setOnTouchListener(new WeekScrubber(scrubTitle, new WeekScrubber.Host() {
                @Override public int viewedWeek() { return currentViewedWeek(); }
                @Override public int actualWeek() { return actualCurrentWeek(); }
                @Override public int totalWeeks() { return Prefs.totalWeeks(MainActivity.this); }
                @Override public void previewWeek(int week) {
                    scrubTitle.setText(gaugeWeekTitle(week, Prefs.totalWeeks(MainActivity.this)));
                    if (timetableView != null) timetableView.setCurrentWeek(week);
                }
                @Override public void commitWeek(int week) {
                    Prefs.setCurrentWeek(MainActivity.this, week);
                    refreshTimetableTab();
                }
                @Override public void onTitleClick(View v) { showWeekMenu(v); }
            }));
        }

        View backToCurrent = campusFragment.findViewById(R.id.btn_back_to_current_week);
        if (backToCurrent != null) {
            backToCurrent.setOnClickListener(v -> {
                int actual = actualCurrentWeek();
                Prefs.setCurrentWeek(this, actual);
                refreshTimetableTab();
            });
        }

        View addBtn = campusFragment.findViewById(R.id.btn_add_course);
        if (addBtn != null) addBtn.setOnClickListener(this::showEditMenu);

        View moreBtn = campusFragment.findViewById(R.id.btn_more);
        if (moreBtn != null) moreBtn.setOnClickListener(this::showTimetableMenu);
    }

    /** 「＋」：改课表的三种方式。 */
    private void showEditMenu(View anchor) {
        new GaugeMenu(this)
                .add("手动添加课程")
                .divider()
                .add("用 AI 改课表")
                .add("粘贴烫梨指令")
                .show(anchor, title -> {
                    if ("用 AI 改课表".equals(title)) CommandUi.copyAiGuide(this);
                    else if ("粘贴烫梨指令".equals(title)) CommandUi.showPasteDialog(this, this::onCommandApplied);
                    else {
                        int day = WeekMath.todayDayOfWeek();
                        if (Prefs.displayDays(this) == 5) day = Math.min(5, day);
                        openCourseEditor(null, day, 1);
                    }
                });
    }

    /** 「⋯」：只留常用的四项，其余收进分享和课表设置。 */
    private void showTimetableMenu(View anchor) {
        new GaugeMenu(this)
                .add("同步课表")
                .add("切换学期")
                .divider()
                .add("分享课表")
                .add("课表设置")
                .show(anchor, title -> {
                    if ("同步课表".equals(title)) syncTimetable();
                    else if ("切换学期".equals(title)) showSemesterPicker();
                    else if ("分享课表".equals(title)) showShareMenu(anchor);
                    else startActivity(new Intent(this, TimetableSettingsActivity.class));
                });
    }

    private void showShareMenu(View anchor) {
        anchor.post(() -> new GaugeMenu(this)
                .add("二维码")
                .add("烫梨指令（发给同学）")
                .divider()
                .add("导出到日历 (ICS)")
                .show(anchor, title -> {
                    if ("二维码".equals(title)) startActivity(new Intent(this, ShareActivity.class));
                    else if (title.startsWith("烫梨指令")) CommandUi.exportForShare(this);
                    else exportIcs();
                }));
    }

    private androidx.appcompat.app.AlertDialog dialog;

    private FlowLayout semesterFlow;
    private TextView pickerHint;
    private List<SemesterArchive.Entry> semesterEntries;
    private String draggingSemesterId;
    private boolean dragMoved;
    private boolean semesterOrderChanged;

    private static final String HINT_DEFAULT = "点击切换 · 长按拖动排序 · 长按不动松手删除";

    /** 学期切换：半透明胶囊，点击切换、长按拖动排序、长按不动松手删除。 */
    private void showSemesterPicker() {
        if (dialog != null && dialog.isShowing()) return;
        semesterEntries = SemesterArchive.listSemesters(this);
        semesterOrderChanged = false;
        draggingSemesterId = null;
        dragMoved = false;

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("选择学期");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(18 * d);
        content.setPadding(pad, pad / 2, pad, pad);

        TextView hint = new TextView(this);
        hint.setText(HINT_DEFAULT);
        hint.setTextSize(11.5f);
        hint.setTextColor(G.MUTE);
        content.addView(hint);
        pickerHint = hint;

        semesterFlow = new FlowLayout(this);
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

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        builder.setView(scroll);
        builder.setNegativeButton("取消", null);
        dialog = builder.create();
        dialog.show();
    }

    /** 点击左上角周数：打开旋钮选周。 */
    private void showWeekMenu(View anchor) {
        showWeekPicker(currentViewedWeek(), Prefs.totalWeeks(this));
    }

    /** 旋钮选周：竖向拖动刻度盘，中央指针所指周即选中；底部按钮确认或回本周。 */
    private void showWeekPicker(int current, int total) {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * d);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad);

        WeekDialView dial = new WeekDialView(this);
        dial.configure(current, total, actualCurrentWeek());
        dial.setOnWeekPicked(w -> {
            dismissSemesterPicker();
            Prefs.setCurrentWeek(this, w);
            refreshTimetableTab();
        });
        content.addView(dial, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(240 * d)));

        TextView ok = new TextView(this);
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
            dismissSemesterPicker();
            Prefs.setCurrentWeek(this, dial.week());
            refreshTimetableTab();
        });
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(40 * d));
        okLp.topMargin = Math.round(12 * d);
        ok.setLayoutParams(okLp);
        dial.setOnWeekChanged(w -> ok.setText("跳转到第 " + w + " 周"));
        content.addView(ok);

        ScrollView scroll = new ScrollView(this);
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
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13.5f);
        tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setTextColor(textColor);
        float d = getResources().getDisplayMetrics().density;
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
        String activeId = SemesterArchive.activeSemesterId(this);
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
        String activeId = SemesterArchive.activeSemesterId(this);
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
        SemesterArchive.reorderSave(this, ids);
    }

    private void confirmDeleteSemester(SemesterArchive.Entry entry) {
        String name = entry.displayName() + (entry.custom ? "（自定义）" : "");
        new AlertDialog.Builder(this)
                .setTitle("删除学期")
                .setMessage("删除「" + name + "」及其 " + entry.courseCount + " 门课程？不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d2, w2) -> {
                    boolean wasActive = entry.id.equals(SemesterArchive.activeSemesterId(this));
                    // 回退目标按显示顺序取删除后剩下的第一个学期
                    String fallback = "";
                    for (SemesterArchive.Entry e : semesterEntries) {
                        if (!e.id.equals(entry.id)) { fallback = e.id; break; }
                    }
                    if (!SemesterArchive.deleteSemester(this, entry.id)) {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (wasActive && !fallback.isEmpty()) switchToSemester(fallback);
                    refreshTimetableTab();
                    Toast.makeText(this, "已删除：" + name, Toast.LENGTH_SHORT).show();
                    dismissSemesterPicker();
                    showSemesterPicker();
                })
                .show();
    }

    private void switchToSemester(String semesterId) {
        Timetable target = SemesterArchive.loadSemester(this, semesterId);
        if (target == null) return;
        SemesterArchive.setActiveSemester(this, semesterId);
        SemesterArchive.syncActiveMirror(this, semesterId);

        // 归档是唯一主源；切换只恢复该学期的日期和周数，不回写 Prefs。
        String start = WeekMath.normalizeToMonday(target.semester.startDate);
        if (!start.isEmpty()) {
            Prefs.setCurrentWeek(this, WeekMath.currentWeek(start, WeekMath.openWeekSpan(start)));
        } else {
            // 历史学期没有真实开学日期，定位到课程最集中的一个周，避免落进空白周
            Prefs.setCurrentWeek(this, busiestWeek(target));
        }

        refreshTimetableTab();
        Toast.makeText(this, "已切换到 " + target.semester.name, Toast.LENGTH_SHORT).show();
    }

    private void promptCreateSemester() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("如 2025-2026 学年第1学期");
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("新建学期课表")
                .setMessage("学期第一周默认为今天，可稍后在「课表设置」修改。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    Timetable t = SemesterArchive.createCustomSemester(this, name);
                    Prefs.setCurrentWeek(this, 1);
                    refreshTimetableTab();
                    Toast.makeText(this, "已创建并切换：" + t.semester.name,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * 历史学期同步：按成绩页覆盖的学年范围，把该生所有学期课表拉到归档。
     * 当前学期保持 active 不变，历史学期只进归档。
     */
    private void syncHistorySemesters() {
        Grades grades = GradesStore.load(this);
        if (grades.semesters.isEmpty()) {
            Toast.makeText(this, "先同步一次成绩，才知道要拉取哪些学年", Toast.LENGTH_SHORT).show();
            return;
        }
        int startYear = earliestGradeYear(grades);
        if (startYear < 0) {
            Toast.makeText(this, "无法识别学年范围", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int thisYear = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        int currentFallYear = month >= 7 ? thisYear : thisYear - 1;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("同步历史学期课表")
                .setMessage("将按成绩记录从 " + startYear + " 年级第一学期拉到当前学期，"
                        + "每个学期单独请求教务接口并写入归档，当前学期不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", (d, w) -> {
                    // 串行发起：每个学期一次隐藏 WebView 请求
                    syncSemesterRange(startYear, currentFallYear, 1);
                })
                .show();
    }

    private int historyCursorYear;
    private int historyCursorTerm;
    private int historyEndYear;

    /**
     * 成绩学期列表按 id 倒序排列（新学期在前），
     * 最早学年取列表末尾的学期 id 首段。
     */
    static int earliestGradeYear(Grades grades) {
        if (grades == null || grades.semesters.isEmpty()) return -1;
        Grades.SemesterGrades last = grades.semesters.get(grades.semesters.size() - 1);
        String[] parts = (last.id == null ? "" : last.id).split("-");
        if (parts.length < 1 || parts[0].isEmpty()) return -1;
        try {
            return Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 课程时段最集中的一个周（并列取更小的），没有课时返回 1。 */
    static int busiestWeek(Timetable t) {
        if (t == null || t.courses == null) return 1;
        int best = 1, bestCount = -1;
        for (int week = 1; week <= 30; week++) {
            int count = 0;
            for (Timetable.Course c : t.courses) {
                for (Timetable.Session s : c.sessions) if (s.isActive(week)) count++;
            }
            if (count > bestCount) { bestCount = count; best = week; }
        }
        return bestCount <= 0 ? 1 : best;
    }

    private void syncSemesterRange(int fromYear, int toYear, int fromTerm) {
        historyCursorYear = fromYear;
        historyCursorTerm = fromTerm;
        historyEndYear = toYear;
        syncNextHistorySemester();
    }

    private void syncNextHistorySemester() {
        if (historyCursorYear > historyEndYear) {
            Toast.makeText(this, "历史学期同步完成", Toast.LENGTH_SHORT).show();
            refreshTimetableTab();
            refreshSettingsTab();
            return;
        }
        String xnm = String.valueOf(historyCursorYear);
        String xqm = historyCursorTerm == 1 ? "3" : (historyCursorTerm == 2 ? "12" : "16");
        Toast.makeText(this, "同步 " + historyCursorYear + "-"
                + (historyCursorYear + 1) + " 第" + historyCursorTerm + "学期…",
                Toast.LENGTH_SHORT).show();
        activeSync = CampusSyncClient.start(newWebViewForSync(),
                CampusSyncClient.Kind.TIMETABLE, xnm, xqm,
                new CampusSyncClient.Callback() {
                    @Override
                    public void onSuccess(int count) {
                        activeSync = null;
                        advanceHistoryCursor();
                    }

                    @Override
                    public void onFailure(String message) {
                        activeSync = null;
                        boolean needRelogin = message != null
                                && (message.contains("重新登录") || message.contains("登录已失效"));
                        if (needRelogin) {
                            // 会话失效：后续学期没有意义，整体中止，引导重新登录。
                            historyCursorYear = historyEndYear + 1;
                            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle(Schools.loginLabel(MainActivity.this) + "登录已失效")
                                    .setMessage("历史学期同步已中止。请到「设置 → 教务账号」"
                                            + "重新登录后，再点「同步历史学期」。")
                                    .setPositiveButton("去登录", (d2, w2) -> openWeb("portal"))
                                    .setNegativeButton("稍后", null)
                                    .show();
                            return;
                        }
                        // 单个学期失败不中断，继续下一个
                        Toast.makeText(MainActivity.this,
                                "第 " + historyCursorTerm + " 学期："
                                        + message + "，继续下一个",
                                Toast.LENGTH_SHORT).show();
                        advanceHistoryCursor();
                    }
                });
    }

    private void advanceHistoryCursor() {
        if (historyCursorTerm >= 3) {
            historyCursorYear++;
            historyCursorTerm = 1;
        } else {
            historyCursorTerm++;
        }
        // 没开课的学期教务会返回空课表，失败时也照常推进，
        // 最终归档里只会留下有课的学期。
        syncNextHistorySemester();
    }

    private void changeWeek(int delta) {
        int total = Prefs.totalWeeks(this);
        int current = currentViewedWeek();
        int next = Math.max(1, Math.min(total, current + delta));
        if (next == current) return;
        Prefs.setCurrentWeek(this, next);
        refreshTimetableTab();
    }

    private int currentViewedWeek() {
        if (!Prefs.hasCurrentWeek(this)) {
            int actual = actualCurrentWeek();
            Prefs.setCurrentWeek(this, actual);
            return actual;
        }
        return Prefs.currentWeek(this);
    }

    /** 本进程最后一次「对齐到本周」的日期；冷启动为空，所以一打开就对齐。 */
    private static String lastShownDay = "";

    private int actualCurrentWeek() {
        // 没设开学日期时用官方校历兜底（标题、日期、当前周必须用同一个起点）
        String start = effectiveStart();
        if (start.isEmpty()) return 1;
        return WeekMath.currentWeek(start, Prefs.totalWeeks(this));
    }

    private String effectiveStart() {
        String id = currentTimetable != null && currentTimetable.semester != null
                ? currentTimetable.semester.id : "";
        return WeekMath.effectiveStart(Prefs.semesterStartDate(this), id, Schools.current(this).isEcut());
    }

    /** 刻度标题：小字「第」+ 巨大等宽周数 + 「周」。不显示总周数。 */
    private CharSequence gaugeWeekTitle(int week, int total) {
        String num = week < 10 ? "0" + week : String.valueOf(week);
        String head = "第 ";
        String tail = " 周";
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(head);
        int a = sb.length();
        sb.append(num);
        int b = sb.length();
        sb.append(tail);
        float d = getResources().getDisplayMetrics().scaledDensity;
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(13 * d)), 0, a, 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(G.MUTE), 0, a, 0);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(46 * d)), a, b, 0);
        sb.setSpan(new android.text.style.TypefaceSpan("monospace"), a, b, 0);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), a, b, 0);
        sb.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(13 * d)), b, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(G.MUTE), b, sb.length(), 0);
        sb.setSpan(new android.text.style.TypefaceSpan("monospace"), b, sb.length(), 0);
        return sb;
    }

    /** 课表超过 3 天没同步时，在副标题后提示一句。 */
    private String staleHint() {
        if (Prefs.sessionExpired(this)) return "  ·  登录已过期";
        long at = Prefs.timetableSyncedAt(this);
        if (at <= 0) return "";
        long days = (System.currentTimeMillis() - at) / 86_400_000L;
        return days >= 3 ? "  ·  " + days + " 天前同步" : "";
    }

    private void refreshTimetableTab() {
        currentTimetable = TimetableStore.load(this);
        maybeShowDrawTip(currentTimetable);
        scheduleSettings = ScheduleSettingsStore.load(this);
        // 别的学校一天 11、12 节甚至更多：导入的课用到了后面的节次就自动扩展，只增不减
        int usedMax = ScheduleSettings.maxSectionUsed(currentTimetable);
        if (scheduleSettings.ensureCovers(usedMax) && ScheduleSettingsStore.save(this, scheduleSettings)) {
            Toast.makeText(this, "课表用到了第 " + usedMax + " 节，已改成每天 " + usedMax + " 节", Toast.LENGTH_LONG).show();
        }
        int week = currentViewedWeek();
        int total = Prefs.totalWeeks(this);
        String start = effectiveStart();
        boolean startFromCalendar = Prefs.semesterStartDate(this).isEmpty() && !start.isEmpty();
        if (week > total) {
            week = total;
            Prefs.setCurrentWeek(this, week);
        }

        TextView weekTitle = campusFragment.findViewById(R.id.tv_week_title);
        if (weekTitle != null) {
            weekTitle.setText(gaugeWeekTitle(week, total));
            weekTitle.setOnClickListener(this::showWeekMenu);
        }

        TextView subtitle = campusFragment.findViewById(R.id.tv_semester_subtitle);
        if (subtitle != null) {
            int until = WeekMath.daysUntilStart(start);
            boolean needStart = (start == null || start.isEmpty()) && !Schools.current(this).isEcut();
            subtitle.setOnClickListener(needStart
                    ? v -> startActivity(new Intent(this, TimetableSettingsActivity.class)) : null);
            subtitle.setClickable(needStart);
            subtitle.setText((start == null || start.isEmpty())
                    ? (needStart ? "还没设第 1 周周一 · 点这里设置" : "")
                    : (until > 0 ? "距开学 " + until + " 天 · "
                            : (week != actualCurrentWeek() ? "本周是第 " + actualCurrentWeek() + " 周 · " : ""))
                            + WeekMath.weekRangeLabel(start, week)
                            + (startFromCalendar ? "  ·  按校历" : "")
                            + staleHint());
        }

        if (timetableView != null) {
            timetableView.setSchedule(scheduleSettings);
            timetableView.setSemesterStartDate(start);
            timetableView.setDisplayDays(Prefs.displayDays(this));
            timetableView.setTotalWeeks(total);
            timetableView.setShowInactiveCourses(Prefs.showInactiveCourses(this));
            timetableView.setFitAllSections(Prefs.fitAllSections(this));
            timetableView.setTimetable(currentTimetable, week);
            if (textDock != null && textDock.getHeight() > 0) {
                timetableView.setBottomInset(dockInset());
            }
        }
        if (dockBlur != null) dockBlur.markDirty();
    }

    private void openCourseEditor(@Nullable Timetable.Course course, Timetable.Session session) {
        Intent i = new Intent(this, CourseEditActivity.class);
        if (course != null) {
            i.putExtra(CourseEditActivity.EXTRA_COURSE_KEY, course.key);
            i.putExtra(CourseEditActivity.EXTRA_SESSION_INDEX, course.sessions.indexOf(session));
        }
        i.putExtra(CourseEditActivity.EXTRA_CURRENT_WEEK, currentViewedWeek());
        startActivity(i);
    }

    private void openCourseEditor(@Nullable Timetable.Course course, int day, int startSection) {
        Intent i = new Intent(this, CourseEditActivity.class);
        if (course != null) {
            i.putExtra(CourseEditActivity.EXTRA_COURSE_KEY, course.key);
        } else {
            i.putExtra(CourseEditActivity.EXTRA_DEFAULT_DAY, day);
            i.putExtra(CourseEditActivity.EXTRA_DEFAULT_SECTION, startSection);
        }
        i.putExtra(CourseEditActivity.EXTRA_CURRENT_WEEK, currentViewedWeek());
        startActivity(i);
    }

    // ==================== 拦截 ====================

    private void bindAdBlock() {
        adblockHeroCard = adblockFragment.findViewById(R.id.adblock_hero_card);
        statusText = adblockFragment.findViewById(R.id.status_text);
        statusSubtext = adblockFragment.findViewById(R.id.status_subtext);
        shieldBadge = adblockFragment.findViewById(R.id.tv_shield_badge);
        toggleBtn = adblockFragment.findViewById(R.id.btn_toggle);
        MaterialSwitch swObserve = adblockFragment.findViewById(R.id.switch_block_observe);

        TextView tvBlock = adblockFragment.findViewById(R.id.stat_block_count);
        TextView tvObserve = adblockFragment.findViewById(R.id.stat_observe_count);
        TextView tvAllow = adblockFragment.findViewById(R.id.stat_allow_count);
        // 10 万条规则解析要一两秒，绝不能在主线程等：先显示占位，算完再填
        for (TextView tv : new TextView[]{tvBlock, tvObserve, tvAllow}) {
            if (tv != null) tv.setText("—");
        }
        new Thread(() -> {
            DomainRules rules = DomainRules.get(getApplicationContext());
            int b = rules.blockCount(), o = rules.observeCount(), a = rules.allowCount();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (tvBlock != null) tvBlock.setText(String.valueOf(b));
                if (tvObserve != null) tvObserve.setText(String.valueOf(o));
                if (tvAllow != null) tvAllow.setText(String.valueOf(a));
            });
        }, "rules-count").start();

        if (swObserve != null) {
            swObserve.setChecked(Prefs.blockObserve(this));
            swObserve.setOnCheckedChangeListener((btn, checked) -> {
                Prefs.setBlockObserve(this, checked);
                if (DnsFilterService.isRunning) {
                    Toast.makeText(this, "重启拦截后对统计域名生效", Toast.LENGTH_SHORT).show();
                }
            });
        }

        toggleBtn.setOnClickListener(v -> {
            DiagLog.log(this, "dns", DnsFilterService.isRunning ? "用户关闭拦截" : "用户开启拦截");
            if (DnsFilterService.isRunning) {
                DnsControl.stop(this);
                statusText.postDelayed(this::refreshStatus, 300);
            } else {
                prepareAndStartVpn();
            }
        });

        View pauseBtn = adblockFragment.findViewById(R.id.btn_dns_pause);
        if (pauseBtn != null) pauseBtn.setOnClickListener(v -> {
            if ((DnsControl.pausedUntil(this) > 0 || DnsControl.resumeOverdue(this)) && !DnsFilterService.isRunning) {
                // 正在暂停：这个按钮变成「现在恢复」
                prepareAndStartVpn();
                return;
            }
            if (!DnsFilterService.isRunning) {
                Toast.makeText(this, "护盾没开，不用暂停", Toast.LENGTH_SHORT).show();
                return;
            }
            Runnable doPause = () -> {
                DiagLog.log(this, "dns", "用户暂停拦截 30 分钟");
                DnsControl.pause(this, 30);
                statusText.postDelayed(this::refreshStatus, 300);
            };
            if (!DnsControl.canNotify(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("通知被关了")
                        .setMessage("30 分钟后烫梨会试着自动恢复；如果系统不让，要靠一条通知提醒你点一下。现在通知是关的，到点没恢复的话需要你回来手动打开。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("照样暂停", (d, w) -> doPause.run())
                        .show();
                return;
            }
            doPause.run();
        });

        TextView testResult = adblockFragment.findViewById(R.id.tv_dns_test_result);
        View testBtn = adblockFragment.findViewById(R.id.btn_dns_test);
        if (testBtn != null) testBtn.setOnClickListener(v -> {
            testBtn.setEnabled(false);
            if (testResult != null) {
                testResult.setVisibility(View.VISIBLE);
                testResult.setTextColor(G.SUB);
                testResult.setText("正在解析一个广告域名…");
            }
            new Thread(() -> {
                DnsControl.Verdict r = DnsControl.selfTest(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    testBtn.setEnabled(true);
                    if (testResult == null) return;
                    testResult.setTextColor(r.good ? G.INK : G.alarm());
                    testResult.setText((r.good ? "✓ " : "✕ ") + r.title + " · " + r.detail);
                });
            }, "dns-selftest").start();
        });

        android.widget.EditText lookupInput = adblockFragment.findViewById(R.id.et_dns_lookup);
        View lookupBtn = adblockFragment.findViewById(R.id.btn_dns_lookup);
        TextView lookupTitle = adblockFragment.findViewById(R.id.tv_dns_lookup_title);
        TextView lookupDetail = adblockFragment.findViewById(R.id.tv_dns_lookup_detail);
        Runnable doLookup = () -> {
            if (lookupInput == null) return;
            String q = lookupInput.getText().toString();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(lookupInput.getWindowToken(), 0);
            new Thread(() -> {
                DnsControl.Verdict r = DnsControl.lookup(getApplicationContext(), q);
                runOnUiThread(() -> {
                    if (isFinishing() || lookupTitle == null || lookupDetail == null) return;
                    lookupTitle.setVisibility(View.VISIBLE);
                    lookupDetail.setVisibility(View.VISIBLE);
                    lookupTitle.setTextColor(r.good ? G.INK : G.SUB);
                    lookupTitle.setText((r.good ? "● " : "○ ") + r.title);
                    lookupDetail.setText(r.detail);
                });
            }, "dns-lookup").start();
        };
        if (lookupBtn != null) lookupBtn.setOnClickListener(v -> doLookup.run());
        if (lookupInput != null) lookupInput.setOnEditorActionListener((tv, id, ev) -> {
            doLookup.run();
            return true;
        });
    }

    /** 开着/暂停中每分钟刷一次文案。只在拦截页可见时跑。 */
    private final Runnable uptimeTick = new Runnable() {
        @Override
        public void run() {
            if (currentTab != TAB_ADBLOCK || statusText == null || isFinishing()) return;
            refreshStatus();
        }
    };

    private void prepareAndStartVpn() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) startActivityForResult(prep, REQ_VPN);
        else doStartVpn();
    }

    private void doStartVpn() {
        Intent svc = new Intent(this, DnsFilterService.class)
                .setAction(DnsFilterService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        DnsControl.markStarted(this);
        if (statusText != null) statusText.postDelayed(this::refreshStatus, 500);
    }

    private void refreshStatus() {
        if (statusText == null) return;
        statusText.removeCallbacks(uptimeTick);
        boolean on = DnsFilterService.isRunning;
        long pausedUntil = DnsControl.pausedUntil(this);
        boolean overdue = DnsControl.resumeOverdue(this);
        if (toggleBtn != null) toggleBtn.setOn(on);
        if (adblockHeroCard != null) adblockHeroCard.setCardBackgroundColor(on ? G.CARD : G.CHIP);
        if (on) {
            statusText.setText(R.string.adblock_on);
            if (statusSubtext != null) statusSubtext.setText(DnsControl.uptimeLabel(DnsControl.uptimeMs(this)));
            if (shieldBadge != null) {
                shieldBadge.setText("● ACTIVE");
                shieldBadge.setTextColor(G.GLASS ? G.INK : G.ACCENT);
            }
            statusText.postDelayed(uptimeTick, 60_000L);
        } else {
            statusText.setText(overdue ? "暂停已结束，护盾还没开" : pausedUntil > 0 ? "护盾暂停中" : getString(R.string.adblock_off));
            if (statusSubtext != null) {
                statusSubtext.setText(overdue ? "系统没让后台自动恢复，点一下护盾或「现在恢复」"
                        : pausedUntil > 0 ? DnsControl.pauseLabel(pausedUntil, System.currentTimeMillis())
                        : "点一下护盾开启本地 DNS 拦截");
            }
            if (shieldBadge != null) {
                shieldBadge.setText(overdue ? "! NOT RESUMED" : pausedUntil > 0 ? "Ⅱ PAUSED" : "○ STANDBY");
                shieldBadge.setTextColor(overdue ? G.alarm() : G.INK);
            }
            if (pausedUntil > 0) statusText.postDelayed(uptimeTick, 60_000L);
        }
        if (adblockFragment != null) {
            TextView pause = adblockFragment.findViewById(R.id.btn_dns_pause);
            if (pause != null) pause.setText((pausedUntil > 0 || overdue) && !on ? "现在恢复" : "暂停 30 分钟");
        }
    }

    // ==================== 设置 ====================

    private void bindSettings() {
        TextView versionTv = settingsFragment.findViewById(R.id.tv_about_version);
        if (versionTv != null) {
            versionTv.setText("烫梨 · v" + BuildConfig.VERSION_NAME);
        }
        View aboutCard = settingsFragment.findViewById(R.id.card_about);
        if (aboutCard != null) aboutCard.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        View starTv = settingsFragment.findViewById(R.id.tv_about_star);
        if (starTv != null) starTv.setOnClickListener(v -> Updater.open(this, Updater.REPO_URL));

        // ===== 0.4：一级只放入口，二级页在 fragment 内切换 =====
        View campusRow = settingsFragment.findViewById(R.id.row_settings_campus);
        if (campusRow != null) campusRow.setOnClickListener(v ->
                startActivity(new Intent(this, CampusActivity.class)));
        View schoolRow = settingsFragment.findViewById(R.id.row_settings_school);
        if (schoolRow != null) schoolRow.setOnClickListener(v -> showSchoolPicker());
        View mateRow = settingsFragment.findViewById(R.id.row_settings_classmates);
        if (mateRow != null) mateRow.setOnClickListener(v ->
                startActivity(new Intent(this, ClassmatesActivity.class)));
        View ttRow = settingsFragment.findViewById(R.id.row_settings_timetable_page);
        if (ttRow != null) ttRow.setOnClickListener(v -> showSettingsPage(R.id.settings_page_timetable));
        View genRow = settingsFragment.findViewById(R.id.row_settings_general_page);
        if (genRow != null) genRow.setOnClickListener(v -> showSettingsPage(R.id.settings_page_general));
        View backTt = settingsFragment.findViewById(R.id.btn_settings_back_tt);
        if (backTt != null) backTt.setOnClickListener(v -> showSettingsPage(R.id.settings_page_root));
        View backGen = settingsFragment.findViewById(R.id.btn_settings_back_general);
        if (backGen != null) backGen.setOnClickListener(v -> showSettingsPage(R.id.settings_page_root));
        View icsRow = settingsFragment.findViewById(R.id.row_export_ics);
        if (icsRow != null) icsRow.setOnClickListener(v -> exportIcs());
        com.google.android.material.materialswitch.MaterialSwitch gradeSw =
                settingsFragment.findViewById(R.id.switch_grade_notify);
        if (gradeSw != null) {
            gradeSw.setChecked(Prefs.gradeNotify(this));
            gradeSw.setOnCheckedChangeListener((b, on) -> {
                Prefs.setGradeNotify(this, on);
                GradeCheckWorker.schedule(this);
                if (on) Notify.requestPermissionIfNeeded(this);
            });
        }
        renderThemeSegment();
        renderNightSegment();

        View gradesCard = settingsFragment.findViewById(R.id.card_settings_grades);
        if (gradesCard != null) {
            gradesCard.setOnClickListener(v ->
                    startActivity(new Intent(this, GradesActivity.class)));
        }

        View scheduleCard = settingsFragment.findViewById(R.id.card_settings_schedule);
        if (scheduleCard != null) {
            scheduleCard.setOnClickListener(v ->
                    startActivity(new Intent(this, ScheduleSettingsActivity.class)));
        }

        View timetableCard = settingsFragment.findViewById(R.id.card_settings_timetable);
        if (timetableCard != null) {
            timetableCard.setOnClickListener(v ->
                    startActivity(new Intent(this, TimetableSettingsActivity.class)));
        }


        View syncNowBtn = settingsFragment.findViewById(R.id.btn_settings_sync_now);
        if (syncNowBtn != null) syncNowBtn.setOnClickListener(v -> syncTimetable());

        View openPortalBtn = settingsFragment.findViewById(R.id.btn_settings_open_portal);
        if (openPortalBtn != null) openPortalBtn.setOnClickListener(v -> openWeb("portal"));

        View exportBtn = settingsFragment.findViewById(R.id.btn_export_timetable);
        if (exportBtn != null) exportBtn.setOnClickListener(v -> exportTimetableToDownloads());

        View importBtn = settingsFragment.findViewById(R.id.btn_import_timetable);
        if (importBtn != null) importBtn.setOnClickListener(v -> launchImportFilePicker());

        View seedBtn = settingsFragment.findViewById(R.id.btn_seed_demo_all);
        if (seedBtn != null) {
            seedBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("载入演示数据")
                    .setMessage("将写入脱敏的课表与成绩样本，成绩页会明确标注「演示数据」。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("载入", (d, w) -> {
                        DemoData.seedAll(this);
                        refreshSettingsTab();
                        Toast.makeText(this, "演示数据已载入", Toast.LENGTH_SHORT).show();
                    })
                    .show());
        }

        View clearDataBtn = settingsFragment.findViewById(R.id.btn_clear_local_data);
        if (clearDataBtn != null) {
            clearDataBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("清空本地数据")
                    .setMessage("将清空课表、成绩与作息配置，且不可恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空", (d, w) -> {
                        TimetableStore.clear(this);
                        GradesStore.clear(this);
                        ScheduleSettingsStore.clear(this);
                        Prefs.setLastSyncTime(this, "");
                        Prefs.setGradesDemo(this, false);
                        refreshSettingsTab();
                        Toast.makeText(this, "本地数据已清空", Toast.LENGTH_SHORT).show();
                    })
                    .show());
        }

        View resetBtn = settingsFragment.findViewById(R.id.btn_reset_targets);
        if (resetBtn != null) {
            resetBtn.setOnClickListener(v -> {
                TargetStore.resetToDefaults(this);
                Prefs.clearPortalHost(this);
                refreshSettingsTab();
                Toast.makeText(this, "已重置代理节点与默认路径配置", Toast.LENGTH_SHORT).show();
            });
        }

        View editBtn = settingsFragment.findViewById(R.id.btn_edit_targets);
        if (editBtn != null) {
            editBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, TargetEditActivity.class)));
        }
    }

    private int settingsPage = R.id.settings_page_root;

    private void showSettingsPage(int pageId) {
        int[] pages = {R.id.settings_page_root, R.id.settings_page_timetable, R.id.settings_page_general};
        boolean forward = pageId != R.id.settings_page_root;
        for (int id : pages) {
            View p = settingsFragment.findViewById(id);
            if (p == null) continue;
            if (id == pageId) {
                p.setVisibility(View.VISIBLE);
                p.setAlpha(0f);
                p.setTranslationX((forward ? 1 : -1) * 36 * getResources().getDisplayMetrics().density);
                p.animate().alpha(1f).translationX(0f).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            } else {
                p.setVisibility(View.GONE);
            }
        }
        settingsPage = pageId;
        View scroll = settingsFragment.findViewById(R.id.settings_scroll);
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    /** 主题两段：纸墨 / 磨砂玻璃。切换时从点的位置圆形扩散。 */
    private void renderThemeSegment() {
        LinearLayout seg = settingsFragment.findViewById(R.id.seg_theme);
        if (seg == null) return;
        seg.removeAllViews();
        String[] labels = {"纸墨", "磨砂玻璃"};
        int cur = Prefs.theme(this);
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius((G.GLASS ? 19 : 6) * d);
        if (G.GLASS) box.setColor(G.CHIP); else box.setStroke(Math.max(1, Math.round(d)), G.STROKE);
        seg.setBackground(box);
        seg.setPadding(Math.round(3 * d), Math.round(3 * d), Math.round(3 * d), Math.round(3 * d));
        for (int i = 0; i < labels.length; i++) {
            final int theme = i;
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(12.5f);
            boolean on = i == cur;
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setTextColor(on ? G.ON_INK : G.SUB);
            if (on) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius((G.GLASS ? 16 : 4) * d);
                bg.setColor(G.INK);
                tv.setBackground(bg);
            }
            tv.setOnClickListener(v -> {
                if (theme == Prefs.theme(this)) return;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                ThemeReveal.capture(this, v, currentTab, settingsPage);
                Prefs.setTheme(this, theme);
                recreate();
            });
            seg.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    /** 深色模式三段：跟随系统 / 浅色 / 深色。 */
    private void renderNightSegment() {
        LinearLayout seg = settingsFragment.findViewById(R.id.seg_night_mode);
        if (seg == null) return;
        seg.removeAllViews();
        String[] labels = {"跟随系统", "浅色", "深色"};
        int cur = Prefs.nightMode(this);
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius((G.GLASS ? 19 : 6) * d);
        if (G.GLASS) box.setColor(G.CHIP); else box.setStroke(Math.max(1, Math.round(d)), G.STROKE);
        seg.setBackground(box);
        seg.setPadding(Math.round(3 * d), Math.round(3 * d), Math.round(3 * d), Math.round(3 * d));
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(12.5f);
            boolean on = i == cur;
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setTextColor(on ? G.ON_INK : G.SUB);
            if (on) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius((G.GLASS ? 16 : 4) * d);
                bg.setColor(G.INK);
                tv.setBackground(bg);
            }
            tv.setOnClickListener(v -> {
                if (mode == Prefs.nightMode(this)) return;
                ThemeReveal.capture(this, v, currentTab, settingsPage);
                Prefs.setNightMode(this, mode);
                App.applyNightMode(mode); // 会重建 Activity
            });
            seg.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab == TAB_SETTINGS && settingsFragment != null && settingsPage != R.id.settings_page_root) {
            showSettingsPage(R.id.settings_page_root);
            return;
        }
        super.onBackPressed();
    }

    private void refreshSettingsTab() {
        if (settingsFragment == null) return;
        TextView gradeSummary = settingsFragment.findViewById(R.id.tv_settings_grade_summary);
        if (gradeSummary != null) {
            Grades grades = GradesStore.load(this);
            if (grades.totalCourseCount() == 0) {
                gradeSummary.setText("尚未同步成绩");
            } else {
                gradeSummary.setText(String.format(Locale.US, "GPA %.2f  ·  加权 %.1f%s",
                        grades.summary.gpa, grades.summary.weightedScore,
                        Prefs.isGradesDemo(this) ? "  ·  演示数据" : ""));
            }
        }

        TextView statusLine = settingsFragment.findViewById(R.id.tv_settings_status_line);
        if (statusLine != null) {
            statusLine.setText(Schools.isEcut(this)
                    ? com.ecut.campus.data.AcademicCalendar.statusLine(WeekMath.todayDateLabel())
                    : Schools.current(this).name);
        }
        TextView gradeNew = settingsFragment.findViewById(R.id.tv_settings_grade_new);
        if (gradeNew != null) {
            int n = Prefs.newGradeKeys(this).size();
            if (n > 0) {
                gradeNew.setText("● " + n + " 门新成绩");
                gradeNew.setTextSize(11f);
                gradeNew.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                gradeNew.setTextColor(G.ACCENT);
            } else {
                gradeNew.setText("›");
                gradeNew.setTextSize(22f);
                gradeNew.setTypeface(Typeface.DEFAULT);
                gradeNew.setTextColor(G.MUTE);
            }
        }

        TextView scheduleSummary = settingsFragment.findViewById(R.id.tv_settings_schedule_summary);
        if (scheduleSummary != null) {
            ScheduleSettings s = ScheduleSettingsStore.load(this);
            int n = s.sectionCount();
            scheduleSummary.setText("每天 " + n + " 节  ·  第 1 节 " + s.firstClassStart + " 开始  ·  第 " + n + " 节 "
                    + s.rangeLabel(n));
        }

        TextView timetableSummary = settingsFragment.findViewById(R.id.tv_settings_timetable_summary);
        if (timetableSummary != null) {
            String start = Prefs.semesterStartDate(this);
            timetableSummary.setText((Prefs.displayDays(this) == 5 ? "五天模式" : "七天模式")
                    + (start.isEmpty() ? "  ·  未设开学日期" : ""));
        }

        SchoolProfile school = Schools.current(this);
        bindSchoolRow(school);
        TextView hostTv = settingsFragment.findViewById(R.id.host_status_text);
        TextView badgeTv = settingsFragment.findViewById(R.id.tv_settings_node_badge);
        String host = Prefs.portalHost(this);
        boolean hasSession = Prefs.hasPortalSession(this);
        if (hostTv != null && !school.isEcut()) {
            bindOtherSchoolAccount(school, hostTv, badgeTv, hasSession);
        } else if (hostTv != null) {
            if (hasSession) {
                hostTv.setText(host != null && !host.isEmpty()
                        ? "统一门户已登录 · 教务节点 " + host
                        : "统一门户已登录 · 课表与成绩可静默更新");
                if (badgeTv != null) {
                    badgeTv.setText("● 已登录");
                    badgeTv.setTextColor(G.INK);
                    badgeTv.setBackgroundColor(0x00000000);
                }
                if (Prefs.sessionExpired(this)) {
                    hostTv.setText("登录已过期：点「登录统一门户」重新登录一次，课表和成绩才能继续更新");
                    if (badgeTv != null) {
                        badgeTv.setText("● 登录已过期");
                        badgeTv.setTextColor(G.ACCENT);
                    }
                }
            } else {
                hostTv.setText("首次使用请点击「登录统一门户」；登录一次后课表与成绩可静默更新");
                if (badgeTv != null) {
                    badgeTv.setText("○ 待连接");
                    badgeTv.setTextColor(G.MUTE);
                    badgeTv.setBackgroundColor(0x00000000);
                }
            }
        }

        TextView summaryTv = settingsFragment.findViewById(R.id.tv_settings_data_summary);
        if (summaryTv != null) {
            Timetable t = TimetableStore.load(this);
            Grades g = GradesStore.load(this);
            String sync = Prefs.lastSyncTime(this);
            summaryTv.setText("本地课表：" + t.courses.size() + " 门  ·  本地成绩："
                    + g.totalCourseCount() + " 门"
                    + (sync.isEmpty() ? "" : ("  ·  更新于 " + sync)));
        }
    }

    private void exportTimetableToDownloads() {
        Timetable t = TimetableStore.load(this);
        if (t.courses.isEmpty()) {
            Toast.makeText(this, "当前本地课表为空，请先同步或添加课程", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String json = TimetableStore.exportJson(t);
            String date = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
            String fileName = "timetable_" + date + ".json";

            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "Tangli");
            if (!dir.exists() && !dir.mkdirs()) dir = getExternalFilesDir(null);
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Toast.makeText(this, "已导出至：" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchImportFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWeb(String targetKey) {
        Intent i = new Intent(this, WebShellActivity.class);
        i.putExtra(WebShellActivity.EXTRA_TARGET_KEY, targetKey);
        startActivity(i);
    }

    /** 导出当前学期课表为 ICS 并弹出系统分享。 */
    private void exportIcs() {
        String start = currentTimetable.semester.startDate;
        if (start == null || start.isEmpty()) {
            Toast.makeText(this, "先在「课表设置」里设置第一周日期，才能导出日历", Toast.LENGTH_LONG).show();
            return;
        }
        String label = currentTimetable.semester.name + " · 烫梨课表";
        String ics = IcsExporter.export(currentTimetable, scheduleSettings, label);
        if (ics == null || ics.isEmpty()) {
            Toast.makeText(this, "导出失败：课表为空或日期无效", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null), "export");
            if (!dir.exists()) dir.mkdirs();
            String safeName = currentTimetable.semester.id.replaceAll("[^A-Za-z0-9_-]", "_");
            File out = new File(dir, "tangli_" + safeName + ".ics");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(ics.getBytes(StandardCharsets.UTF_8));
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/calendar");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享课表日历"));
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void syncTimetable() {
        startSilentSync(CampusSyncClient.Kind.TIMETABLE);
    }

    private void syncGrades() {
        startSilentSync(CampusSyncClient.Kind.GRADES);
    }

    private void startSilentSync(CampusSyncClient.Kind kind) {
        if (activeSync != null && activeSync.isRunning()) {
            Toast.makeText(this, "正在同步，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        SchoolProfile school = Schools.current(this);
        if (kind == CampusSyncClient.Kind.GRADES && !school.supportsGrades()) {
            Toast.makeText(this, "这个学校暂不支持成绩", Toast.LENGTH_LONG).show();
            return;
        }
        if (kind == CampusSyncClient.Kind.TIMETABLE && !school.supportsBackgroundSync()) {
            new AlertDialog.Builder(this)
                    .setTitle("这个学校还不能后台同步")
                    .setMessage("打开教务网页，登录后进入「我的课表」页面，点底部的「导入此页课表」就能把课表导进来。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("打开教务", (d, w) -> openWeb("timetable"))
                    .show();
            return;
        }
        if (!Prefs.hasPortalSession(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("请先登录" + Schools.loginLabel(this))
                    .setMessage("登录一次后，课表与成绩都能在后台静默更新，不再跳转网页。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去登录", (d, w) -> openWeb("portal"))
                    .show();
            return;
        }

        Toast.makeText(this, "正在后台同步" + kind.label + "…", Toast.LENGTH_SHORT).show();
        activeSync = CampusSyncClient.start(newWebViewForSync(), kind,
                new CampusSyncClient.Callback() {
                    @Override
                    public void onSuccess(int count) {
                        activeSync = null;
                        refreshTimetableTab();
                        refreshSettingsTab();
                        String diff = kind == CampusSyncClient.Kind.TIMETABLE
                                ? CampusSyncClient.lastTimetableDiff : "";
                        CampusSyncClient.lastTimetableDiff = "";
                        if (diff != null && !diff.isEmpty()) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("课表有变化")
                                    .setMessage(diff)
                                    .setPositiveButton("知道了", null)
                                    .show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    kind.label + "已更新：" + count + " 门",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        activeSync = null;
                        refreshSettingsTab();
                        refreshTimetableTab();
                        if (Prefs.sessionExpired(MainActivity.this)) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("登录已过期")
                                    .setMessage(Schools.loginLabel(MainActivity.this)
                                            + "的登录失效了。重新登录一次，课表和成绩就能继续静默更新。")
                                    .setNegativeButton("稍后", null)
                                    .setPositiveButton("去登录", (d, w) -> openWeb("portal"))
                                    .show();
                        } else {
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ==================== 学校 ====================

    /** 设置页「学校」行与东华理工专属入口的显隐。 */
    /** 「00」位置：有缓存的校徽就显示原色校徽，没有就后台去学校官网抓一次。 */
    private void bindSchoolBadge(SchoolProfile school) {
        if (settingsFragment == null) return;
        android.widget.ImageView badge = settingsFragment.findViewById(R.id.iv_settings_school_badge);
        TextView index = settingsFragment.findViewById(R.id.tv_settings_school_index);
        if (badge == null || index == null) return;
        android.graphics.Bitmap bmp = SchoolBadgeLoader.cached(this, school);
        if (bmp != null) {
            badge.setImageBitmap(bmp);
            badge.setVisibility(View.VISIBLE);
            index.setVisibility(View.INVISIBLE);
            return;
        }
        badge.setVisibility(View.GONE);
        index.setVisibility(View.VISIBLE);
        final String schoolId = school.id;
        SchoolBadgeLoader.ensure(this, school, b -> {
            if (isFinishing() || settingsFragment == null) return;
            if (!schoolId.equals(Schools.current(this).id)) return;
            android.widget.ImageView iv = settingsFragment.findViewById(R.id.iv_settings_school_badge);
            TextView tv = settingsFragment.findViewById(R.id.tv_settings_school_index);
            if (iv == null || tv == null) return;
            iv.setImageBitmap(b);
            iv.setAlpha(0f);
            iv.setVisibility(View.VISIBLE);
            iv.animate().alpha(1f).setDuration(220).start();
            tv.setVisibility(View.INVISIBLE);
        });
    }

    private void bindSchoolRow(SchoolProfile school) {
        bindSchoolBadge(school);
        TextView sub = settingsFragment.findViewById(R.id.tv_settings_school_sub);
        if (sub != null) {
            String detail = school.isEcut() ? "已适配 · 课表、成绩、校历、地图"
                    : school.vendor.label + "教务"
                    + (school.supportsBackgroundSync() ? " · 可后台同步课表" : " · 在教务页「导入此页课表」")
                    + (school.supportsGrades() ? " · 成绩" : "");
            sub.setText(school.name + " · " + detail);
        }
        int vis = school.ecutFeatures ? View.VISIBLE : View.GONE;
        View campusRow = settingsFragment.findViewById(R.id.row_settings_campus);
        if (campusRow != null) campusRow.setVisibility(vis);
        View campusHair = settingsFragment.findViewById(R.id.hair_settings_campus);
        if (campusHair != null) campusHair.setVisibility(vis);
        TextView portalBtn = settingsFragment.findViewById(R.id.btn_settings_open_portal);
        if (portalBtn != null) portalBtn.setText("登录" + Schools.loginLabel(this));
        TextView gradeSummary = settingsFragment.findViewById(R.id.tv_settings_grade_summary);
        if (gradeSummary != null && !school.supportsGrades()) gradeSummary.setText("这个学校暂不支持成绩");
    }

    private void bindOtherSchoolAccount(SchoolProfile school, TextView hostTv, TextView badgeTv,
                                        boolean hasSession) {
        String how = school.supportsBackgroundSync()
                ? "课表可在后台同步" : "登录后在课表页点「导入此页课表」";
        if (hasSession && Prefs.sessionExpired(this)) {
            hostTv.setText("登录已过期：点「登录教务系统」重新登录一次");
            if (badgeTv != null) {
                badgeTv.setText("● 登录已过期");
                badgeTv.setTextColor(G.ACCENT);
            }
        } else if (hasSession) {
            hostTv.setText("教务系统已登录 · " + how);
            if (badgeTv != null) {
                badgeTv.setText("● 已登录");
                badgeTv.setTextColor(G.INK);
                badgeTv.setBackgroundColor(0x00000000);
            }
        } else {
            hostTv.setText("首次使用请点「登录教务系统」，在网页里登录你的教务账号；" + how);
            if (badgeTv != null) {
                badgeTv.setText("○ 待连接");
                badgeTv.setTextColor(G.MUTE);
                badgeTv.setBackgroundColor(0x00000000);
            }
        }
    }

    private void showSchoolPicker() {
        List<SchoolProfile> builtins = Schools.builtin(this);
        SchoolProfile cur = Schools.current(this);
        SchoolProfile custom = Schools.custom(this);
        String[] items = new String[builtins.size() + 1];
        for (int i = 0; i < builtins.size(); i++) {
            SchoolProfile p = builtins.get(i);
            items[i] = p.name + (p.id.equals(cur.id) ? "  ✓" : "");
        }
        items[builtins.size()] = custom == null ? "自定义学校（输入教务网址）"
                : "自定义学校：" + custom.name + (cur.isCustom() ? "  ✓" : "") + "（点此修改）";
        new AlertDialog.Builder(this)
                .setTitle("选择学校")
                .setItems(items, (d, which) -> {
                    if (which < builtins.size()) {
                        SchoolProfile p = builtins.get(which);
                        if (p.id.equals(cur.id)) return;
                        Schools.select(this, p);
                        refreshSettingsTab();
                        Toast.makeText(this, "已切换到 " + p.name, Toast.LENGTH_SHORT).show();
                    } else {
                        showCustomSchoolDialog(custom);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomSchoolDialog(SchoolProfile existing) {
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("学校名称（可不填）");
        name.setSingleLine(true);
        android.widget.EditText url = new android.widget.EditText(this);
        url.setHint("教务网址，如 jw.xxx.edu.cn/jwglxt");
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (existing != null) {
            if (!"自定义学校".equals(existing.name)) name.setText(existing.name);
            url.setText(existing.loginUrl);
        }
        TextView label = new TextView(this);
        label.setText("教务系统类型");
        label.setPadding(0, pad / 2, 0, 0);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        String[] vendors = {"自动识别", "正方", "强智", "其他 / 不知道"};
        for (int i = 0; i < vendors.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(vendors[i]);
            rb.setId(View.generateViewId());
            rb.setTag(i);
            group.addView(rb);
            if (i == 0) group.check(rb.getId());
        }
        TextView tip = new TextView(this);
        tip.setText("打开教务登录页后，把浏览器地址栏里的网址复制过来就行。烫梨不会保存你的账号密码，登录在网页里完成。");
        tip.setTextSize(12);
        tip.setPadding(0, pad / 2, 0, 0);
        box.addView(name);
        box.addView(url);
        box.addView(label);
        box.addView(group);
        box.addView(tip);
        new AlertDialog.Builder(this)
                .setTitle("自定义学校")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    View checked = group.findViewById(group.getCheckedRadioButtonId());
                    int idx = checked == null ? 0 : (int) checked.getTag();
                    SchoolProfile.Vendor vendor = idx == 1 ? SchoolProfile.Vendor.ZHENGFANG
                            : idx == 2 ? SchoolProfile.Vendor.QIANGZHI
                            : idx == 3 ? SchoolProfile.Vendor.OTHER : null;
                    SchoolProfile p = SchoolProfile.custom(url.getText().toString(), vendor);
                    if (p == null) {
                        Toast.makeText(this, "网址不对，请检查一下", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = name.getText().toString().trim();
                    if (!n.isEmpty()) p.name = n;
                    Schools.select(this, p);
                    refreshSettingsTab();
                    String msg = p.vendor == SchoolProfile.Vendor.OTHER
                            ? "没认出教务系统类型。登录后进入课表页，点底部的「导入此页课表」试试。"
                            : "识别为" + p.vendor.label + "教务。先登录一次教务系统，之后就能同步课表"
                            + (p.supportsGrades() ? "和成绩" : "") + "。";
                    new AlertDialog.Builder(this)
                            .setTitle("已保存")
                            .setMessage(msg)
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("去登录", (d2, w2) -> openWeb("portal"))
                            .show();
                })
                .show();
    }

    private WebView newWebViewForSync() {
        WebView view = new WebView(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        // 保持 VISIBLE 但完全透明：INVISIBLE 可能让 WebView 暂停渲染与定时器。
        view.setAlpha(0f);
        syncHost.addView(view);
        return view;
    }

    @Override
    protected void onDestroy() {
        if (activeSync != null) {
            activeSync.cancel();
            activeSync = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) doStartVpn();
            else if (statusText != null) statusText.setText("VPN 授权被拒绝，无法开启拦截");
        } else if (requestCode == REQ_IMPORT_JSON && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                if (is != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                }
                String json = bos.toString("UTF-8");
                Timetable imported = TimetableStore.importJson(json);
                Timetable merged = TimetableStore.merge(TimetableStore.load(this), imported);
                TimetableStore.save(this, merged);
                refreshTimetableTab();
                refreshSettingsTab();
                Toast.makeText(this, "导入成功：共 " + merged.courses.size() + " 门课程",
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android 10+ 只有拿到焦点后才能读剪贴板：复制了 AI 给的烫梨指令，回来就弹预览
        if (hasFocus) CommandUi.checkClipboard(this, this::onCommandApplied);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次进 App（或跨天回来）都先回到现实中的本周，不停在上次翻到的周
        String today = WeekMath.todayDateLabel();
        boolean dayChanged = !today.equals(lastShownDay);
        if (dayChanged) {
            lastShownDay = today;
            Prefs.setCurrentWeek(this, actualCurrentWeek());
        }
        // 刚从统一门户登录回来：第一次弹出上手引导
        LoginGuide.maybeShow(this, this::syncTimetable);
        boolean justCreated = skipNextResumeRefresh;
        skipNextResumeRefresh = false;
        // onCreate 刚刷新过一遍，冷启动不重复读盘重绘
        if (justCreated && !dayChanged) return;
        if (currentTab == TAB_TIMETABLE) refreshTimetableTab();
        else if (currentTab == TAB_ADBLOCK) refreshStatus();
        else refreshSettingsTab();
    }
}
