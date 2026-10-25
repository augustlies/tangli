package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
        return s.replaceAll("\\?[^\\s\"']*", "?…")
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

    /** 崩溃时先写日志，再交给系统默认处理。 */
    public static void installCrashHandler(Context ctx) {
        Context app = ctx.getApplicationContext();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            error(app, "CRASH", "thread " + t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** 拼一份可读的诊断报告。 */
    public static String buildReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("烫梨诊断日志\n");
        sb.append("生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append('\n');
        sb.append("版本: ").append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n\n");

        Timetable t = null;
        try { t = TimetableStore.load(ctx); } catch (Throwable ignored) {}
        String semId = t != null && t.semester != null ? t.semester.id : "";
        String rawStart = Prefs.semesterStartDate(ctx);
        String eff = WeekMath.effectiveStart(rawStart, semId);
        sb.append("== 周次 ==\n");
        sb.append("今天: ").append(WeekMath.todayDateLabel()).append('\n');
        sb.append("设置的开学日期: ").append(rawStart.isEmpty() ? "(未设置)" : rawStart).append('\n');
        sb.append("实际使用的第1周周一: ").append(eff.isEmpty() ? "(无)" : eff).append('\n');
        sb.append("算出的本周: ").append(eff.isEmpty() ? "-" : String.valueOf(
                WeekMath.currentWeek(eff, WeekMath.OPEN_WEEKS_MAX))).append('\n');
        sb.append("显示天数: ").append(Prefs.displayDays(ctx))
                .append(" · 显示非本周: ").append(Prefs.showInactiveCourses(ctx)).append('\n');
        sb.append("上次同步: ").append(Prefs.lastSyncTime(ctx))
                .append(" · 登录过期: ").append(Prefs.sessionExpired(ctx)).append("\n\n");

        sb.append("== 本地课表 ==\n");
        if (t == null || t.courses == null) {
            sb.append("(无)\n");
        } else {
            sb.append("学期: ").append(semId).append(' ').append(t.semester.name)
                    .append(" · startDate=").append(t.semester.startDate)
                    .append(" touched=").append(t.semester.startDateTouched).append('\n');
            sb.append("课程数: ").append(t.courses.size()).append('\n');
            for (Timetable.Course c : t.courses) {
                sb.append("- ").append(c.name).append(" | ").append(c.teacher)
                        .append(" | ").append(c.source).append('\n');
                if (c.sessions == null) continue;
                for (Timetable.Session s : c.sessions) {
                    sb.append("    周").append(s.dayOfWeek).append(' ')
                            .append(s.startSection).append('-').append(s.endSection).append("节 ")
                            .append(s.weeksRaw).append(" → ").append(s.weeks)
                            .append(" @").append(s.location).append('\n');
                }
            }
        }
        sb.append('\n');

        sb.append("== 最近一次教务原始课表（已去除学生信息）==\n");
        sb.append(readText(new File(dir(ctx), KB), "(还没有同步过)")).append("\n\n");
        sb.append("== 运行日志 ==\n");
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

    /** 写成 txt 并弹出系统分享（微信 / QQ / 邮件都行）。 */
    public static void export(Activity a) {
        try {
            File d = new File(a.getExternalFilesDir(null), "export");
            if (!d.exists()) d.mkdirs();
            String name = "tangli-diag-" + new SimpleDateFormat("MMdd-HHmm", Locale.US)
                    .format(new Date()) + ".txt";
            File out = new File(d, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(buildReport(a).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    a, a.getPackageName() + ".fileprovider", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "烫梨诊断日志 v" + BuildConfig.VERSION_NAME);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(share, "发送诊断日志"));
        } catch (Throwable e) {
            Toast.makeText(a, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
