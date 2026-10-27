package com.ecut.campus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.ecut.campus.data.IcsExporter;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 课表文件进出（从 MainActivity 拆出）：JSON 备份 / 从备份导入 / 导出 ICS。 */
final class TimetableFiles {
    private TimetableFiles() {}

    static final int REQ_IMPORT_JSON = 2001;

    static void exportJson(Activity a) {
        Timetable t = TimetableStore.load(a);
        if (t.courses.isEmpty()) {
            Toast.makeText(a, "当前本地课表为空，请先同步或添加课程", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String json = TimetableStore.exportJson(t);
            String date = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
            String fileName = "timetable_" + date + ".json";

            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "Tangli");
            if (!dir.exists() && !dir.mkdirs()) dir = a.getExternalFilesDir(null);
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Toast.makeText(a, "已导出至：" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    static void pickImport(Activity a) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            a.startActivityForResult(intent, REQ_IMPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(a, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    /** 读选中的 JSON 备份并合并进当前课表。成功返回 true（已提示）。 */
    static boolean importFrom(Activity a, Uri uri) {
        if (uri == null) return false;
        try (InputStream is = a.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (is != null) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            }
            String json = bos.toString("UTF-8");
            Timetable imported = TimetableStore.importJson(json);
            Timetable merged = TimetableStore.merge(TimetableStore.load(a), imported);
            TimetableStore.save(a, merged);
            Toast.makeText(a, "导入成功：共 " + merged.courses.size() + " 门课程",
                    Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(a, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** 导出当前学期课表为 ICS 并弹出系统分享。 */
    static void exportIcs(Activity a, Timetable t, ScheduleSettings schedule) {
        String start = t.semester.startDate;
        if (start == null || start.isEmpty()) {
            Toast.makeText(a, "先在「课表设置」里设置第一周日期，才能导出日历", Toast.LENGTH_LONG).show();
            return;
        }
        String label = t.semester.name + " · 烫梨课表";
        String ics = IcsExporter.export(t, schedule, label);
        if (ics == null || ics.isEmpty()) {
            Toast.makeText(a, "导出失败：课表为空或日期无效", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String safeName = t.semester.id.replaceAll("[^A-Za-z0-9_-]", "_");
            File out = ShareFiles.exportFile(a, "tangli_" + safeName + ".ics");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(ics.getBytes(StandardCharsets.UTF_8));
            }
            ShareFiles.send(a, out, "text/calendar", null, "分享课表日历");
        } catch (Exception e) {
            Toast.makeText(a, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
