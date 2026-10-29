package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断日志：同步过程、解析结果、崩溃堆栈写到 App 私有目录，
 * 用户在「关于 → 导出诊断日志」一键导出成 txt 发给开发者。
 *
 * 隐私：不记录 Cookie、学号、姓名、URL 参数；课表原始数据只保留课程相关字段。
 */
public final class DiagLog {

    private static final String DIR = "diag";
    private static final String LOG = "log.txt";
    private static final String KB = "kblist.json";
    private static final String PAGE_SAMPLE = "page_sample.json";
    private static final long MAX_BYTES = 256 * 1024;

    private DiagLog() {}

    private static File dir(Context ctx) {
        File d = new File(ctx.getApplicationContext().getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 追加一行日志。任何异常都吞掉，日志不能影响正常功能。 */
    public static synchronized void log(Context ctx, String tag, String msg) {
        if (ctx == null) return;
        try {
            File f = new File(dir(ctx), LOG);
            if (f.length() > MAX_BYTES) trim(f);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " [" + tag + "] " + scrub(msg) + "\n";
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void error(Context ctx, String tag, String msg, Throwable e) {
        StringWriter sw = new StringWriter();
        if (e != null) e.printStackTrace(new PrintWriter(sw));
        log(ctx, tag, msg + (e == null ? "" : "\n" + sw));
    }

    /** 只保留后一半，避免文件无限增长。 */
    private static void trim(File f) throws Exception {
        byte[] all = readBytes(f);
        int from = all.length / 2;
        while (from < all.length && all[from] != '\n') from++;
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.setLength(0);
            raf.write(all, Math.min(from + 1, all.length), Math.max(0, all.length - from - 1));
        }
    }

    /** 去掉 URL 查询参数（可能带 ticket/token）和像学号的长数字。 */
    static String scrub(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i);jsessionid=[^\\s\"'?#/]*", ";…")
                .replaceAll("\\?[^\\s\"']*", "?…")
                .replaceAll("(?<![0-9])[0-9]{9,}(?![0-9])", "#########");
    }

    /** 保存最近一次教务课表接口返回的 kbList，只留排课相关字段（不含学生信息）。 */
    public static void saveKbList(Context ctx, String json) {
        if (ctx == null || json == null) return;
        try {
            JSONArray src = new JSONObject(json).optJSONArray("kbList");
            if (src == null) return;
            String[] keep = {"kcmc", "xm", "jxb_id", "jxbmc", "xqj", "jcor", "jc", "zcd",
                    "cdmc", "xqmc", "kcxz", "khfsmc"};
            JSONArray out = new JSONArray();
            for (int i = 0; i < src.length(); i++) {
                JSONObject r = src.optJSONObject(i);
                if (r == null) continue;
                JSONObject o = new JSONObject();
                for (String k : keep) if (r.has(k)) o.put(k, r.optString(k, ""));
                out.put(o);
            }
            try (FileOutputStream fos = new FileOutputStream(new File(dir(ctx), KB))) {
                fos.write(out.toString(1).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            log(ctx, "diag", "saveKbList failed: " + e);
        }
    }

    /** 保存「导入此页课表」失败时的页面结构样本（调用方已用 PageSample 脱敏）。 */
    public static void savePageSample(Context ctx, String json) {
        if (ctx == null || json == null) return;
        try (FileOutputStream fos = new FileOutputStream(new File(dir(ctx), PAGE_SAMPLE))) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            log(ctx, "diag", "savePageSample failed: " + e);
        }
    }

    /** 崩溃时先写日志，再交给系统默认处理。 */
    public static void installCrashHandler(Context ctx) {
        Context app = ctx.getApplicationContext();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            error(app, "CRASH", "thread " + t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private static String ts(long ms) {
        if (ms <= 0) return "(无)";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }

    /** 每一节单独 try：某一块读失败不影响整份报告。 */
    private interface Section { void write(StringBuilder sb) throws Throwable; }

    private static void section(StringBuilder sb, String title, Section s) {
        sb.append("== ").append(title).append(" ==\n");
        try {
            s.write(sb);
        } catch (Throwable e) {
            sb.append("(读取失败: ").append(e).append(")\n");
        }
        sb.append('\n');
    }

    private static String yn(boolean b) { return b ? "是" : "否"; }

    /** 拼一份全局诊断报告：版本设备、权限、登录同步、设置、课表、成绩、指令、运行日志。 */
    public static String buildReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("烫梨诊断日志（全局）\n");
        sb.append("生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append('\n');
        sb.append("版本: ").append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n\n");

        section(sb, "运行环境", x -> {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            x.append("首次安装: ").append(ts(pi.firstInstallTime))
                    .append(" · 本次更新: ").append(ts(pi.lastUpdateTime)).append('\n');
            android.content.res.Configuration cfg = ctx.getResources().getConfiguration();
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            x.append("语言: ").append(Locale.getDefault()).append(" · 时区: ")
                    .append(java.util.TimeZone.getDefault().getID()).append('\n');
            x.append("屏幕: ").append(Math.round(dm.widthPixels / dm.density)).append('x')
                    .append(Math.round(dm.heightPixels / dm.density)).append("dp · 密度 ")
                    .append(dm.density).append(" · 字体缩放 ").append(cfg.fontScale)
                    .append(" · 系统深色 ").append(yn((cfg.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                            == android.content.res.Configuration.UI_MODE_NIGHT_YES)).append('\n');
            String wv = "(未知)";
            if (Build.VERSION.SDK_INT >= 26) {
                android.content.pm.PackageInfo w = android.webkit.WebView.getCurrentWebViewPackage();
                if (w != null) wv = w.packageName + " " + w.versionName;
            }
            x.append("WebView: ").append(wv).append('\n');
            String net = "无网络";
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (nc != null) {
                    StringBuilder n = new StringBuilder();
                    if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) n.append("WiFi ");
                    if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) n.append("移动数据 ");
                    if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) n.append("VPN ");
                    net = n.length() == 0 ? "其他" : n.toString().trim();
                }
            }
            x.append("网络: ").append(net).append('\n');
        });

        section(sb, "权限与后台", x -> {
            boolean notif = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled();
            x.append("通知: ").append(yn(notif));
            if (Build.VERSION.SDK_INT >= 33) {
                x.append(" · 通知权限 ").append(yn(ctx.checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED));
            }
            x.append('\n');
            x.append("相机权限: ").append(yn(Build.VERSION.SDK_INT < 23 || ctx.checkSelfPermission(
                    android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)).append('\n');
            x.append("VPN 已授权: ").append(yn(android.net.VpnService.prepare(ctx) == null))
                    .append(" · 拦截运行中: ").append(yn(DnsFilterService.isRunning)).append('\n');
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= 23) {
                x.append("忽略电池优化: ").append(yn(pm.isIgnoringBatteryOptimizations(ctx.getPackageName()))).append('\n');
            }
        });

        section(sb, "学校", x -> {
            com.ecut.campus.data.SchoolProfile p = Schools.current(ctx);
            x.append("学校: ").append(p.name).append(" · id=").append(p.id)
                    .append(" · 教务: ").append(p.vendor.label).append('(').append(p.vendor.key()).append(')')
                    .append(" · 访问: ").append(p.access.key()).append('\n');
            x.append("教务主机: ").append(p.jwHost().isEmpty() ? "-" : p.jwHost())
                    .append(" · 已验证: ").append(yn(p.verified))
                    .append(" · 后台同步: ").append(yn(p.supportsBackgroundSync()))
                    .append(" · 成绩: ").append(yn(p.supportsGrades())).append('\n');
        });

        section(sb, "登录与同步", x -> {
            x.append("门户会话: ").append(yn(Prefs.hasPortalSession(ctx)))
                    .append(" · 登录过期: ").append(yn(Prefs.sessionExpired(ctx))).append('\n');
            String host = Prefs.portalHost(ctx);
            boolean cookie = false;
            try {
                String h = host == null || host.isEmpty() ? "" : (host.startsWith("http") ? host : "https://" + host);
                cookie = !h.isEmpty() && android.webkit.CookieManager.getInstance().getCookie(h) != null;
            } catch (Throwable ignored) {
            }
            x.append("门户地址: ").append(host == null || host.isEmpty() ? "(默认)" : host)
                    .append(" · 有 Cookie: ").append(yn(cookie)).append('\n');
            x.append("上次同步: ").append(Prefs.lastSyncTime(ctx))
                    .append(" · 课表同步于: ").append(ts(Prefs.timetableSyncedAt(ctx))).append('\n');
        });

        Timetable t = null;
        try { t = TimetableStore.load(ctx); } catch (Throwable ignored) {}
        final Timetable tt = t;
        final String semId = t != null && t.semester != null ? t.semester.id : "";

        section(sb, "周次", x -> {
            String rawStart = Prefs.semesterStartDate(ctx);
            String eff = WeekMath.effectiveStart(rawStart, semId, Schools.current(ctx).isEcut());
            x.append("今天: ").append(WeekMath.todayDateLabel()).append('\n');
            x.append("设置的开学日期: ").append(rawStart.isEmpty() ? "(未设置)" : rawStart).append('\n');
            x.append("实际使用的第1周周一: ").append(eff.isEmpty() ? "(无)" : eff).append('\n');
            x.append("算出的本周: ").append(eff.isEmpty() ? "-" : String.valueOf(
                    WeekMath.currentWeek(eff, WeekMath.OPEN_WEEKS_MAX))).append('\n');
        });

        section(sb, "设置", x -> {
            for (java.util.Map.Entry<String, String> e : AppActions.currentSettings(ctx).entrySet()) {
                if (com.ecut.campus.data.AppSetting.SHARE_NAME.equals(e.getKey())) continue; // 昵称不导出
                x.append(com.ecut.campus.data.AppSetting.label(e.getKey())).append(" = ")
                        .append(e.getValue()).append('\n');
            }
            x.append("更新: 上次检查 ").append(ts(Prefs.updateCheckedAt(ctx)))
                    .append(" · 跳过版本 ").append(Prefs.updateSkippedTag(ctx).isEmpty() ? "(无)" : Prefs.updateSkippedTag(ctx)).append('\n');
            ScheduleSettings ss = ScheduleSettingsStore.load(ctx);
            StringBuilder r = new StringBuilder();
            for (int i = 1; i <= ss.sectionCount(); i++) {
                if (r.length() > 0) r.append("  ");
                r.append(i).append(':').append(ss.startTime(i));
            }
            x.append("作息: ").append(r).append('\n');
        });

        section(sb, "学期", x -> {
            String active = com.ecut.campus.data.SemesterArchive.activeSemesterId(ctx);
            for (com.ecut.campus.data.SemesterArchive.Entry e : com.ecut.campus.data.SemesterArchive.listSemesters(ctx)) {
                x.append(e.id.equals(active) ? "* " : "- ").append(e.displayName())
                        .append(" · 开学 ").append(e.startDate.isEmpty() ? "(未设)" : e.startDate)
                        .append(" · ").append(e.totalWeeks).append("周 · ").append(e.courseCount).append("门")
                        .append(e.custom ? " · 自建" : "").append('\n');
            }
        });

        section(sb, "本地课表", x -> {
            if (tt == null || tt.courses == null) {
                x.append("(无)\n");
                return;
            }
            x.append("学期: ").append(semId).append(' ').append(tt.semester.name)
                    .append(" · startDate=").append(tt.semester.startDate)
                    .append(" touched=").append(tt.semester.startDateTouched).append('\n');
            int notes = 0;
            for (Timetable.Course c : tt.courses) if (c.note != null && !c.note.isEmpty()) notes++;
            x.append("课程数: ").append(tt.courses.size()).append(" · 有备注: ").append(notes)
                    .append(" · 停课/调课/放假: ").append(tt.adjusts == null ? 0 : tt.adjusts.size()).append('\n');
            for (Timetable.Course c : tt.courses) {
                x.append("- ").append(c.name).append(" | ").append(c.teacher)
                        .append(" | ").append(c.source).append('\n');
                if (c.sessions == null) continue;
                for (Timetable.Session s : c.sessions) {
                    x.append("    周").append(s.dayOfWeek).append(' ')
                            .append(s.startSection).append('-').append(s.endSection).append("节 ")
                            .append(s.weeksRaw).append(" → ").append(s.weeks)
                            .append(" @").append(s.location).append('\n');
                }
            }
            if (tt.adjusts != null) {
                for (Timetable.Adjust a : tt.adjusts) {
                    x.append("  调整 ").append(a.type).append(' ').append(a.name)
                            .append(a.date.isEmpty() ? " 第" + a.week + "周 周" + a.day + " " + a.start + "-" + a.end + "节" : " " + a.date)
                            .append(Timetable.Adjust.MOVE.equals(a.type)
                                    ? " → 第" + a.toWeek + "周 周" + a.toDay + " " + a.toStart + "-" + a.toEnd + "节" : "")
                            .append('\n');
                }
            }
        });

        section(sb, "成绩（只记门数，不含分数）", x -> {
            com.ecut.campus.data.Grades g = com.ecut.campus.data.GradesStore.load(ctx);
            int demo = 0;
            for (com.ecut.campus.data.Grades.SemesterGrades s : g.semesters) {
                for (com.ecut.campus.data.Grades.CourseGrade c : s.courses) {
                    if (com.ecut.campus.data.DemoGrades.isDemo(c)) demo++;
                }
                x.append("- ").append(s.id).append(": ").append(s.courses.size()).append("门\n");
            }
            x.append("合计: ").append(g.totalCourseCount()).append("门 · 更新于 ")
                    .append(g.updatedAt == null || g.updatedAt.isEmpty() ? "(从未同步)" : g.updatedAt).append('\n');
            x.append("演示标记: ").append(yn(Prefs.isGradesDemo(ctx))).append(" · 残留样本: ").append(demo)
                    .append(" · 未读新成绩: ").append(Prefs.newGradeKeys(ctx).size()).append('\n');
        });

        section(sb, "同学与指令", x -> {
            x.append("已添加同学: ").append(FriendStore.load(ctx).size()).append('\n');
            java.util.List<String> h = CommandUi.history(ctx);
            x.append("指令历史: ").append(h.size()).append("条\n");
            for (int i = 0; i < Math.min(5, h.size()); i++) {
                String[] lines = h.get(i).split("\n");
                String first = lines.length > 1 ? lines[1] : lines[0];
                x.append("  ").append(lines.length).append("行 · ")
                        .append(scrub(first.length() > 40 ? first.substring(0, 40) + "…" : first)).append('\n');
            }
        });

        sb.append("== 最近一次教务原始课表（已去除学生信息）==\n");
        sb.append(readText(new File(dir(ctx), KB), "(还没有同步过)")).append("\n\n");
        File sample = new File(dir(ctx), PAGE_SAMPLE);
        if (sample.exists()) {
            sb.append("== 页面结构样本（只含标签和字段名）==\n");
            sb.append(readText(sample, "(空)")).append("\n\n");
        }
        sb.append("== 运行日志（同步过程、指令、崩溃）==\n");
        sb.append(readText(new File(dir(ctx), LOG), "(空)")).append('\n');
        return sb.toString();
    }

    private static byte[] readBytes(File f) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String readText(File f, String empty) {
        try {
            if (!f.exists() || f.length() == 0) return empty;
            return new String(readBytes(f), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "(读取失败: " + e + ")";
        }
    }

    /**
     * 出错时的统一弹窗：写明原因，带「发给开发者」（一键上传）和「导出文件」两个按钮。
     * 用在导入失败、同步失败这类用户自己解决不了、需要发给开发者看的地方。
     */
    public static void failDialog(Activity a, String title, String message) {
        if (a == null || a.isFinishing()) return;
        log(a, "fail", title + ": " + message);
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message + "\n\n弄不好的话，点「发给开发者」一键上传错误日志（不含账号密码，姓名学号已去掉）。")
                .setNegativeButton("知道了", null)
                .setNeutralButton("导出文件", (d, w) -> export(a))
                .setPositiveButton("发给开发者", (d, w) -> upload(a))
                .show();
    }

    /** 上传地址：官网的 /api/report，转发到开发者的 Telegram。 */
    static final String REPORT_URL = "https://tangli.sueexin.dev/api/report";

    /**
     * 一键把诊断日志发给开发者：后台上传，成功后弹出报告码（让用户发帖/私信时报上），
     * 失败就退回到系统分享，让用户自己发。
     */
    public static void upload(Activity a) {
        if (a == null) return;
        final String report;
        try {
            report = buildReport(a);
        } catch (Throwable e) {
            export(a);
            return;
        }
        Toast.makeText(a, "正在发送错误日志…", Toast.LENGTH_SHORT).show();
        final android.content.Context app = a.getApplicationContext();
        new Thread(() -> {
            String id = null, err = null;
            java.net.HttpURLConnection c = null;
            try {
                byte[] body = report.getBytes(StandardCharsets.UTF_8);
                c = (java.net.HttpURLConnection) new java.net.URL(REPORT_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                c.setRequestProperty("X-Tangli-Version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
                try (java.io.OutputStream os = c.getOutputStream()) { os.write(body); }
                int code = c.getResponseCode();
                java.io.InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                String text = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
                JSONObject o = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                if (code == 200 && o.optBoolean("ok")) id = o.optString("id", "");
                else err = code == 429 ? "发得太频繁了，过一会儿再试" : "服务器没收下（" + code + "）";
            } catch (Throwable e) {
                err = "网络不通";
            } finally {
                if (c != null) c.disconnect();
            }
            final String fid = id, ferr = err;
            log(app, "fail", fid != null ? "日志已上传 #" + fid : "日志上传失败: " + ferr);
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (fid != null) {
                    new androidx.appcompat.app.AlertDialog.Builder(a)
                            .setTitle("已发给开发者")
                            .setMessage("报告码 #" + fid + "\n\n反馈时报上这个码，开发者就能对上你的日志。")
                            .setPositiveButton("复制报告码", (d, w) -> {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                        a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("tangli", "烫梨报告码 #" + fid));
                                Toast.makeText(a, "已复制", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("好", null)
                            .show();
                } else {
                    Toast.makeText(a, "没发出去：" + ferr + "，改用分享发送", Toast.LENGTH_LONG).show();
                    export(a);
                }
            });
        }, "diag-upload").start();
    }

    private static byte[] readAll(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /** 写成 txt 并弹出系统分享（微信 / QQ / 邮件都行）。 */
    public static void export(Activity a) {
        try {
            String name = "tangli-diag-" + new SimpleDateFormat("MMdd-HHmm", Locale.US)
                    .format(new Date()) + ".txt";
            File out = ShareFiles.exportFile(a, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(buildReport(a).getBytes(StandardCharsets.UTF_8));
            }
            ShareFiles.send(a, out, "text/plain", "烫梨诊断日志 v" + BuildConfig.VERSION_NAME, "发送诊断日志");
        } catch (Throwable e) {
            Toast.makeText(a, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
