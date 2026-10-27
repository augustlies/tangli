package com.ecut.campus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.SchoolProfile;
import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;
import com.google.android.material.color.DynamicColors;


public class MainActivity extends AppCompatActivity implements AppActions.Host {

    static final int REQ_VPN = 1001;
    static final int TAB_TIMETABLE = 0;
    static final int TAB_ADBLOCK = 1;
    static final int TAB_SETTINGS = 2;

    private View campusFragment;
    private View adblockFragment;
    private View settingsFragment;
    private TimetableView timetableView;
    private Timetable currentTimetable = new Timetable();
    private ScheduleSettings scheduleSettings = ScheduleSettings.createDefault();

    private CapsuleDock textDock;
    private DockBlurView dockBlur;
    private AdblockTab adblock;
    private SettingsTab settings;
    private ViewGroup syncHost;
    CampusSyncClient activeSync;
    int currentTab = TAB_TIMETABLE;

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
            if (tab == TAB_SETTINGS && page > 0 && page != R.id.settings_page_root) settings.showPage(page);
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
            else if ("history".equals(value)) historySync.start();
            else syncTimetable();
        } else if ("export".equals(action) && "ics".equals(value)) {
            exportIcs();
        } else if ("open".equals(action)) {
            if ("timetable".equals(value)) showTab(TAB_TIMETABLE);
            else if ("settings".equals(value)) showTab(TAB_SETTINGS);
            else if ("adblock".equals(value)) showTab(TAB_ADBLOCK);
        } else if ("dns".equals(action) && "start".equals(value)) {
            // 快捷开关 / 暂停结束通知：没授权的话在这里补授权
            showTab(TAB_ADBLOCK);
            if (!DnsFilterService.isRunning) adblock.prepareAndStartVpn();
        }
    }

    /** 烫梨指令应用（或撤销）之后：课表重画；设置可能变了，拦截页和设置页下次打开时重建。 */
    void onCommandApplied() {
        refreshTimetableTab();
        boolean rebuild = false;
        if (adblockFragment != null) {
            contentContainer.removeView(adblockFragment);
            adblockFragment = null;
            adblock = null;
            rebuild = true;
        }
        if (settingsFragment != null) {
            contentContainer.removeView(settingsFragment);
            settingsFragment = null;
            settings = null;
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
        adblock = new AdblockTab(this, adblockFragment);
        adblock.bind();
    }

    private void ensureSettingsTab() {
        if (settingsFragment != null) return;
        settingsFragment = getLayoutInflater().inflate(R.layout.fragment_settings, contentContainer, false);
        contentContainer.addView(settingsFragment);
        settings = new SettingsTab(this, settingsFragment);
        settings.bind();
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
            backToCurrent.setOnClickListener(v -> jumpToWeek(actualCurrentWeek()));
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
                    else if ("切换学期".equals(title)) semesterPicker.show();
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

    final SemesterPicker semesterPicker = new SemesterPicker(this);
    final SchoolPicker schoolPicker = new SchoolPicker(this);
    private final HistorySync historySync = new HistorySync(this);

    /** 点击左上角周数：打开旋钮选周。 */
    private void showWeekMenu(View anchor) {
        WeekPickerDialog.show(this, currentViewedWeek(), Prefs.totalWeeks(this), actualCurrentWeek());
    }

    /** 跳到某周并重画（选周旋钮、回本周按钮共用）。 */
    void jumpToWeek(int week) {
        Prefs.setCurrentWeek(this, week);
        refreshTimetableTab();
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

    private void refreshStatus() {
        if (adblock != null) adblock.refreshStatus();
    }

    void refreshSettingsTab() {
        if (settings != null) settings.refresh();
    }

    void refreshTimetableTab() {
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
            timetableView.setWarnCourses(com.ecut.campus.fun.FunPrefs.absenceWarnings(this, currentTimetable, total));
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


    void openWeb(String targetKey) {
        Intent i = new Intent(this, WebShellActivity.class);
        i.putExtra(WebShellActivity.EXTRA_TARGET_KEY, targetKey);
        startActivity(i);
    }

    void exportIcs() {
        TimetableFiles.exportIcs(this, currentTimetable, scheduleSettings);
    }


    void syncTimetable() {
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


    WebView newWebViewForSync() {
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
        if (requestCode == SchoolPicker.REQ_FIND_SCHOOL) {
            schoolPicker.onFinderResult(resultCode, data);
        } else if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                if (adblock != null) adblock.onVpnGranted();
                else AdblockTab.startService(this);
            } else if (adblock != null) {
                adblock.onVpnDenied();
            }
        } else if (requestCode == TimetableFiles.REQ_IMPORT_JSON && resultCode == Activity.RESULT_OK && data != null) {
            if (TimetableFiles.importFrom(this, data.getData())) {
                refreshTimetableTab();
                refreshSettingsTab();
            }
        } else if (requestCode == WidgetBg.REQ_PICK && resultCode == Activity.RESULT_OK && data != null) {
            android.net.Uri picked = data.getData();
            if (settings != null) settings.onWidgetImagePicked(picked);
            else new Thread(() -> {
                if (WidgetBg.importFrom(getApplicationContext(), picked)) ClassWidget.updateAll(getApplicationContext());
            }, "widget-bg").start();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab == TAB_SETTINGS && settings != null && settings.backToRoot()) return;
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android 10+ 只有拿到焦点后才能读剪贴板：复制了 AI 给的烫梨指令，回来就弹预览
        if (hasFocus) CommandUi.checkClipboard(this, this::onCommandApplied);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 离开 App 时（课表/作息可能刚改过）顺手重排课前提醒、刷新桌面小组件
        ClassReminder.reschedule(this);
        ClassWidget.updateAll(this);
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
