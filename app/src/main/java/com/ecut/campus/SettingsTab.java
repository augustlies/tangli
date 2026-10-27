package com.ecut.campus;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.SchoolProfile;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.util.Locale;

/** 「设置」页签（从 MainActivity 拆出）：一级入口、两个二级页、学校/账号状态、外观与通知。 */
final class SettingsTab {

    private final MainActivity a;
    private final View settingsFragment;

    SettingsTab(MainActivity a, View root) {
        this.a = a;
        this.settingsFragment = root;
    }

    void bind() {
        TextView versionTv = settingsFragment.findViewById(R.id.tv_about_version);
        if (versionTv != null) {
            versionTv.setText("烫梨 · v" + BuildConfig.VERSION_NAME);
        }
        View aboutCard = settingsFragment.findViewById(R.id.card_about);
        if (aboutCard != null) aboutCard.setOnClickListener(v ->
                a.startActivity(new Intent(a, AboutActivity.class)));
        View starTv = settingsFragment.findViewById(R.id.tv_about_star);
        if (starTv != null) starTv.setOnClickListener(v -> Updater.open(a, Updater.REPO_URL));

        // ===== 0.4：一级只放入口，二级页在 fragment 内切换 =====
        View campusRow = settingsFragment.findViewById(R.id.row_settings_campus);
        if (campusRow != null) campusRow.setOnClickListener(v ->
                a.startActivity(new Intent(a, CampusActivity.class)));
        View schoolRow = settingsFragment.findViewById(R.id.row_settings_school);
        if (schoolRow != null) schoolRow.setOnClickListener(v -> a.schoolPicker.show());
        View mateRow = settingsFragment.findViewById(R.id.row_settings_classmates);
        if (mateRow != null) mateRow.setOnClickListener(v ->
                a.startActivity(new Intent(a, ClassmatesActivity.class)));
        View ttRow = settingsFragment.findViewById(R.id.row_settings_timetable_page);
        if (ttRow != null) ttRow.setOnClickListener(v -> showPage(R.id.settings_page_timetable));
        View funRow = settingsFragment.findViewById(R.id.row_settings_fun);
        if (funRow != null) funRow.setOnClickListener(v ->
                a.startActivity(new Intent(a, com.ecut.campus.fun.FunActivity.class)));
        View genRow = settingsFragment.findViewById(R.id.row_settings_general_page);
        if (genRow != null) genRow.setOnClickListener(v -> showPage(R.id.settings_page_general));
        View backTt = settingsFragment.findViewById(R.id.btn_settings_back_tt);
        if (backTt != null) backTt.setOnClickListener(v -> showPage(R.id.settings_page_root));
        View backGen = settingsFragment.findViewById(R.id.btn_settings_back_general);
        if (backGen != null) backGen.setOnClickListener(v -> showPage(R.id.settings_page_root));
        View icsRow = settingsFragment.findViewById(R.id.row_export_ics);
        if (icsRow != null) icsRow.setOnClickListener(v -> a.exportIcs());
        bindClassRemind();
        com.google.android.material.materialswitch.MaterialSwitch gradeSw =
                settingsFragment.findViewById(R.id.switch_grade_notify);
        if (gradeSw != null) {
            gradeSw.setChecked(Prefs.gradeNotify(a));
            gradeSw.setOnCheckedChangeListener((b, on) -> {
                Prefs.setGradeNotify(a, on);
                GradeCheckWorker.schedule(a);
                if (on) Notify.requestPermissionIfNeeded(a);
            });
        }
        renderThemeSegment();
        renderNightSegment();
        bindWidget();

        View gradesCard = settingsFragment.findViewById(R.id.card_settings_grades);
        if (gradesCard != null) {
            gradesCard.setOnClickListener(v ->
                    a.startActivity(new Intent(a, GradesActivity.class)));
        }

        View scheduleCard = settingsFragment.findViewById(R.id.card_settings_schedule);
        if (scheduleCard != null) {
            scheduleCard.setOnClickListener(v ->
                    a.startActivity(new Intent(a, ScheduleSettingsActivity.class)));
        }

        View timetableCard = settingsFragment.findViewById(R.id.card_settings_timetable);
        if (timetableCard != null) {
            timetableCard.setOnClickListener(v ->
                    a.startActivity(new Intent(a, TimetableSettingsActivity.class)));
        }


        View syncNowBtn = settingsFragment.findViewById(R.id.btn_settings_sync_now);
        if (syncNowBtn != null) syncNowBtn.setOnClickListener(v -> a.syncTimetable());

        View openPortalBtn = settingsFragment.findViewById(R.id.btn_settings_open_portal);
        if (openPortalBtn != null) openPortalBtn.setOnClickListener(v -> a.openWeb("portal"));

        View exportBtn = settingsFragment.findViewById(R.id.btn_export_timetable);
        if (exportBtn != null) exportBtn.setOnClickListener(v -> TimetableFiles.exportJson(a));

        View importBtn = settingsFragment.findViewById(R.id.btn_import_timetable);
        if (importBtn != null) importBtn.setOnClickListener(v -> TimetableFiles.pickImport(a));

        View clearDataBtn = settingsFragment.findViewById(R.id.btn_clear_local_data);
        if (clearDataBtn != null) {
            clearDataBtn.setOnClickListener(v -> new AlertDialog.Builder(a)
                    .setTitle("清空本地数据")
                    .setMessage("将清空课表、成绩与作息配置，且不可恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空", (d, w) -> {
                        TimetableStore.clear(a);
                        GradesStore.clear(a);
                        ScheduleSettingsStore.clear(a);
                        Prefs.setLastSyncTime(a, "");
                        Prefs.setGradesDemo(a, false);
                        refresh();
                        Toast.makeText(a, "本地数据已清空", Toast.LENGTH_SHORT).show();
                    })
                    .show());
        }

        View resetBtn = settingsFragment.findViewById(R.id.btn_reset_targets);
        if (resetBtn != null) {
            resetBtn.setOnClickListener(v -> {
                TargetStore.resetToDefaults(a);
                Prefs.clearPortalHost(a);
                refresh();
                Toast.makeText(a, "已重置代理节点与默认路径配置", Toast.LENGTH_SHORT).show();
            });
        }

        View editBtn = settingsFragment.findViewById(R.id.btn_edit_targets);
        if (editBtn != null) {
            editBtn.setOnClickListener(v ->
                    a.startActivity(new Intent(a, TargetEditActivity.class)));
        }
    }

    private int settingsPage = R.id.settings_page_root;

    void showPage(int pageId) {
        int[] pages = {R.id.settings_page_root, R.id.settings_page_timetable, R.id.settings_page_general};
        boolean forward = pageId != R.id.settings_page_root;
        for (int id : pages) {
            View p = settingsFragment.findViewById(id);
            if (p == null) continue;
            if (id == pageId) {
                p.setVisibility(View.VISIBLE);
                p.setAlpha(0f);
                p.setTranslationX((forward ? 1 : -1) * 36 * a.getResources().getDisplayMetrics().density);
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
        renderSegment(R.id.seg_theme, new String[]{"纸墨", "磨砂玻璃"}, Prefs.theme(a), (v, theme) -> {
            if (theme == Prefs.theme(a)) return;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            ThemeReveal.capture(a, v, a.currentTab, settingsPage);
            Prefs.setTheme(a, theme);
            a.recreate();
        });
    }

    /** 深色模式三段：跟随系统 / 浅色 / 深色。 */
    private void renderNightSegment() {
        renderSegment(R.id.seg_night_mode, new String[]{"跟随系统", "浅色", "深色"}, Prefs.nightMode(a), (v, mode) -> {
            if (mode == Prefs.nightMode(a)) return;
            ThemeReveal.capture(a, v, a.currentTab, settingsPage);
            Prefs.setNightMode(a, mode);
            ClassWidget.updateAll(a.getApplicationContext()); // 桌面小组件跟着换深浅色
            App.applyNightMode(mode); // 会重建 Activity
        });
    }

    private interface SegmentPick { void pick(View v, int index); }

    /** 分段选择器：外框（纸墨发丝描边 / 玻璃浅底）+ 选中段墨色实底。主题、深色模式共用。 */
    private void renderSegment(int segId, String[] labels, int cur, SegmentPick onPick) {
        LinearLayout seg = settingsFragment.findViewById(segId);
        if (seg == null) return;
        seg.removeAllViews();
        float d = a.getResources().getDisplayMetrics().density;
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius((G.GLASS ? 19 : 6) * d);
        if (G.GLASS) box.setColor(G.CHIP); else box.setStroke(Math.max(1, Math.round(d)), G.STROKE);
        seg.setBackground(box);
        seg.setPadding(Math.round(3 * d), Math.round(3 * d), Math.round(3 * d), Math.round(3 * d));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tv = new TextView(a);
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
            tv.setOnClickListener(v -> onPick.pick(v, index));
            seg.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    /** 返回键：在二级页时先回一级页，返回 true 表示已处理。 */
    boolean backToRoot() {
        if (settingsPage == R.id.settings_page_root) return false;
        showPage(R.id.settings_page_root);
        return true;
    }

    void refresh() {
        if (settingsFragment == null) return;
        TextView gradeSummary = settingsFragment.findViewById(R.id.tv_settings_grade_summary);
        if (gradeSummary != null) {
            Grades grades = GradesStore.load(a);
            if (grades.totalCourseCount() == 0) {
                gradeSummary.setText("尚未同步成绩");
            } else {
                gradeSummary.setText(String.format(Locale.US, "GPA %.2f  ·  加权 %.1f%s",
                        grades.summary.gpa, grades.summary.weightedScore,
                        Prefs.isGradesDemo(a) ? "  ·  演示数据" : ""));
            }
        }

        TextView statusLine = settingsFragment.findViewById(R.id.tv_settings_status_line);
        if (statusLine != null) {
            statusLine.setText(Schools.isEcut(a)
                    ? com.ecut.campus.data.AcademicCalendar.statusLine(WeekMath.todayDateLabel())
                    : Schools.current(a).name);
        }
        TextView gradeNew = settingsFragment.findViewById(R.id.tv_settings_grade_new);
        if (gradeNew != null) {
            int n = Prefs.newGradeKeys(a).size();
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
            ScheduleSettings s = ScheduleSettingsStore.load(a);
            int n = s.sectionCount();
            scheduleSummary.setText("每天 " + n + " 节  ·  第 1 节 " + s.firstClassStart + " 开始  ·  第 " + n + " 节 "
                    + s.rangeLabel(n));
        }

        TextView timetableSummary = settingsFragment.findViewById(R.id.tv_settings_timetable_summary);
        if (timetableSummary != null) {
            String start = Prefs.semesterStartDate(a);
            timetableSummary.setText((Prefs.displayDays(a) == 5 ? "五天模式" : "七天模式")
                    + (start.isEmpty() ? "  ·  未设开学日期" : ""));
        }

        SchoolProfile school = Schools.current(a);
        bindSchoolRow(school);
        TextView hostTv = settingsFragment.findViewById(R.id.host_status_text);
        TextView badgeTv = settingsFragment.findViewById(R.id.tv_settings_node_badge);
        String host = Prefs.portalHost(a);
        boolean hasSession = Prefs.hasPortalSession(a);
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
                if (Prefs.sessionExpired(a)) {
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
            Timetable t = TimetableStore.load(a);
            Grades g = GradesStore.load(a);
            String sync = Prefs.lastSyncTime(a);
            summaryTv.setText("本地课表：" + t.courses.size() + " 门  ·  本地成绩："
                    + g.totalCourseCount() + " 门"
                    + (sync.isEmpty() ? "" : ("  ·  更新于 " + sync)));
        }
    }

    // ==================== 学校 ====================

    /** 设置页「学校」行与东华理工专属入口的显隐。 */
    /** 「00」位置：有缓存的校徽就显示原色校徽，没有就后台去学校官网抓一次。 */
    private void bindSchoolBadge(SchoolProfile school) {
        if (settingsFragment == null) return;
        android.widget.ImageView badge = settingsFragment.findViewById(R.id.iv_settings_school_badge);
        TextView index = settingsFragment.findViewById(R.id.tv_settings_school_index);
        if (badge == null || index == null) return;
        android.graphics.Bitmap bmp = SchoolBadgeLoader.cached(a, school);
        if (bmp != null) {
            badge.setImageBitmap(bmp);
            badge.setVisibility(View.VISIBLE);
            index.setVisibility(View.INVISIBLE);
            return;
        }
        badge.setVisibility(View.GONE);
        index.setVisibility(View.VISIBLE);
        final String schoolId = school.id;
        SchoolBadgeLoader.ensure(a, school, b -> {
            if (a.isFinishing() || settingsFragment == null) return;
            if (!schoolId.equals(Schools.current(a).id)) return;
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
                    : (school.vendor == SchoolProfile.Vendor.OTHER
                        && school.loginUrl != null && school.loginUrl.contains("wdkb")
                        ? "金智" : school.vendor.label) + "教务"
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
        if (portalBtn != null) portalBtn.setText("登录" + Schools.loginLabel(a));
        TextView gradeSummary = settingsFragment.findViewById(R.id.tv_settings_grade_summary);
        if (gradeSummary != null && !school.supportsGrades()) gradeSummary.setText("这个学校暂不支持成绩");
    }

    private void bindOtherSchoolAccount(SchoolProfile school, TextView hostTv, TextView badgeTv,
                                        boolean hasSession) {
        String how = school.supportsBackgroundSync()
                ? "课表可在后台同步" : "登录后在课表页点「导入此页课表」";
        if (hasSession && Prefs.sessionExpired(a)) {
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

    private static final int[] REMIND_LEADS = {5, 10, 15, 20};

    // ===== 桌面组件：一键添加 + 自选背景 =====
    private void bindWidget() {
        View pin = settingsFragment.findViewById(R.id.row_widget_pin);
        if (pin != null) pin.setOnClickListener(v -> PinWidgetActivity.choose(a));
        View reset = settingsFragment.findViewById(R.id.btn_widget_bg_reset);
        if (reset != null) reset.setOnClickListener(v -> {
            WidgetBg.clear(a, true);
            ClassWidget.updateAll(a.getApplicationContext());
            renderWidgetBg();
            Toast.makeText(a, "组件背景已恢复默认", Toast.LENGTH_SHORT).show();
        });
        android.widget.SeekBar seek = settingsFragment.findViewById(R.id.seek_widget_mask);
        if (seek != null) {
            seek.setProgress(Prefs.widgetMask(a));
            seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar b, int p, boolean user) {
                    TextView tv = settingsFragment.findViewById(R.id.tv_widget_mask);
                    if (tv != null) tv.setText(p + "%");
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar b) {
                    Prefs.setWidgetMask(a, b.getProgress());
                    ClassWidget.updateAll(a.getApplicationContext()); // 松手就刷新桌面上的小组件
                    renderWidgetBg();
                }
            });
        }
        renderWidgetBg();
    }

    /** 组件背景一栏：分段（默认 / 选一张图片）、遮罩滑杆、按图亮度给的提示。 */
    void renderWidgetBg() {
        boolean img = WidgetBg.active(a);
        int mask = Prefs.widgetMask(a);
        renderSegment(R.id.seg_widget_bg, new String[]{"默认", "选一张图片"}, img ? 1 : 0, (v, i) -> {
            if (i == 1) {
                WidgetBg.pick(a); // 已经有图时再点也能换一张
            } else if (WidgetBg.active(a)) {
                WidgetBg.clear(a, false);
                ClassWidget.updateAll(a.getApplicationContext());
                renderWidgetBg();
            }
        });
        View box = settingsFragment.findViewById(R.id.box_widget_mask);
        if (box != null) box.setVisibility(img ? View.VISIBLE : View.GONE);
        android.widget.SeekBar seek = settingsFragment.findViewById(R.id.seek_widget_mask);
        if (seek != null && seek.getProgress() != mask) seek.setProgress(mask);
        TextView mtv = settingsFragment.findViewById(R.id.tv_widget_mask);
        if (mtv != null) mtv.setText(mask + "%");
        TextView sub = settingsFragment.findViewById(R.id.tv_widget_bg_sub);
        if (sub != null) sub.setText(img ? "自选图片 · 遮罩 " + mask + "%" : "默认纸色，跟着浅色 / 深色走");
        TextView hint = settingsFragment.findViewById(R.id.tv_widget_bg_hint);
        if (hint != null && img) {
            double g = WidgetBg.headerGray(a);
            boolean night = (a.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            double mg = com.ecut.campus.data.WidgetLook.gray(WidgetBg.maskColor(night));
            int want = g < 0 ? mask : com.ecut.campus.data.WidgetLook.suggestedMask(g, mg, mask);
            hint.setText(want > mask
                    ? "这张图标题那块明暗混杂，遮罩调到 " + want + "% 左右字会更清楚"
                    : "遮罩越高，字越清楚；字的深浅会跟着图自动换");
        }
        TextView pinSub = settingsFragment.findViewById(R.id.tv_widget_pin_sub);
        if (pinSub != null) {
            int n = ClassWidget.placedCount(a);
            pinSub.setText(n > 0 ? "已放了 " + n + " 个 · 今日课程，4×2 或拉成 2×2"
                    : "今日课程，4×2 或拉成 2×2，放到桌面一眼就知道");
        }
    }

    /** 选完图片回来：后台复制缩小，好了刷新小组件和这一栏。 */
    void onWidgetImagePicked(android.net.Uri uri) {
        Toast.makeText(a, "正在处理图片…", Toast.LENGTH_SHORT).show();
        android.content.Context app = a.getApplicationContext();
        new Thread(() -> {
            boolean ok = WidgetBg.importFrom(app, uri);
            if (ok) ClassWidget.updateAll(app);
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                renderWidgetBg();
                Toast.makeText(a, ok ? "组件背景换好了" : "这张图读不了，换一张试试", Toast.LENGTH_SHORT).show();
            });
        }, "widget-bg").start();
    }

    private void bindClassRemind() {
        com.google.android.material.materialswitch.MaterialSwitch sw =
                settingsFragment.findViewById(R.id.switch_class_remind);
        TextView sub = settingsFragment.findViewById(R.id.tv_class_remind_sub);
        View row = settingsFragment.findViewById(R.id.row_class_remind);
        if (sw == null) return;
        Runnable label = () -> {
            if (sub == null) return;
            String t = "提前 " + Prefs.classRemindLead(a) + " 分钟通知课程和教室";
            if (Prefs.classRemind(a) && !ClassReminder.canNotify(a)) t += " · 通知被关了，收不到";
            else if (Prefs.classRemind(a)) t += " · 点这里改时间";
            sub.setText(t);
        };
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(Prefs.classRemind(a));
        label.run();
        sw.setOnCheckedChangeListener((b, on) -> {
            Prefs.setClassRemind(a, on);
            if (on) Notify.requestPermissionIfNeeded(a);
            ClassReminder.reschedule(a);
            label.run();
        });
        if (row != null) row.setOnClickListener(v -> {
            if (!Prefs.classRemind(a)) { sw.setChecked(true); return; }
            String[] items = new String[REMIND_LEADS.length];
            int checked = 1;
            for (int i = 0; i < REMIND_LEADS.length; i++) {
                items[i] = "提前 " + REMIND_LEADS[i] + " 分钟";
                if (REMIND_LEADS[i] == Prefs.classRemindLead(a)) checked = i;
            }
            new AlertDialog.Builder(a).setTitle("上课前多久提醒")
                    .setSingleChoiceItems(items, checked, (d, w) -> {
                        Prefs.setClassRemindLead(a, REMIND_LEADS[w]);
                        ClassReminder.reschedule(a);
                        label.run();
                        d.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }
}
