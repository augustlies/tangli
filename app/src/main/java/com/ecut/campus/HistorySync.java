package com.ecut.campus;

import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;

/** 历史学期课表同步（从 MainActivity 拆出）：按成绩覆盖的学年逐个学期串行拉取，写入归档。 */
final class HistorySync {

    private final MainActivity a;

    HistorySync(MainActivity a) {
        this.a = a;
    }


    /**
     * 历史学期同步：按成绩页覆盖的学年范围，把该生所有学期课表拉到归档。
     * 当前学期保持 active 不变，历史学期只进归档。
     */
    void start() {
        Grades grades = GradesStore.load(a);
        if (grades.semesters.isEmpty()) {
            Toast.makeText(a, "先同步一次成绩，才知道要拉取哪些学年", Toast.LENGTH_SHORT).show();
            return;
        }
        int startYear = earliestGradeYear(grades);
        if (startYear < 0) {
            Toast.makeText(a, "无法识别学年范围", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int thisYear = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        int currentFallYear = month >= 7 ? thisYear : thisYear - 1;

        new AlertDialog.Builder(a)
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

    private void syncSemesterRange(int fromYear, int toYear, int fromTerm) {
        historyCursorYear = fromYear;
        historyCursorTerm = fromTerm;
        historyEndYear = toYear;
        syncNextHistorySemester();
    }

    private void syncNextHistorySemester() {
        if (historyCursorYear > historyEndYear) {
            Toast.makeText(a, "历史学期同步完成", Toast.LENGTH_SHORT).show();
            a.refreshTimetableTab();
            a.refreshSettingsTab();
            return;
        }
        String xnm = String.valueOf(historyCursorYear);
        String xqm = historyCursorTerm == 1 ? "3" : (historyCursorTerm == 2 ? "12" : "16");
        Toast.makeText(a, "同步 " + historyCursorYear + "-"
                + (historyCursorYear + 1) + " 第" + historyCursorTerm + "学期…",
                Toast.LENGTH_SHORT).show();
        a.activeSync = CampusSyncClient.start(a.newWebViewForSync(),
                CampusSyncClient.Kind.TIMETABLE, xnm, xqm,
                new CampusSyncClient.Callback() {
                    @Override
                    public void onSuccess(int count) {
                        a.activeSync = null;
                        advanceHistoryCursor();
                    }

                    @Override
                    public void onFailure(String message) {
                        a.activeSync = null;
                        boolean needRelogin = message != null
                                && (message.contains("重新登录") || message.contains("登录已失效"));
                        if (needRelogin) {
                            // 会话失效：后续学期没有意义，整体中止，引导重新登录。
                            historyCursorYear = historyEndYear + 1;
                            new AlertDialog.Builder(a)
                                    .setTitle(Schools.loginLabel(a) + "登录已失效")
                                    .setMessage("历史学期同步已中止。请到「设置 → 教务账号」"
                                            + "重新登录后，再点「同步历史学期」。")
                                    .setPositiveButton("去登录", (d2, w2) -> a.openWeb("portal"))
                                    .setNegativeButton("稍后", null)
                                    .show();
                            return;
                        }
                        // 单个学期失败不中断，继续下一个
                        Toast.makeText(a,
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
}
