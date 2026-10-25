package com.ecut.campus;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.AcademicCalendar;
import com.ecut.campus.data.CalendarSource;
import com.ecut.campus.data.SemesterArchive;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** 三级页：校历。内置关键日期 + 一键设开学日期 + 官网原图。 */
public class CalendarActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        render();
    }

    private void render() {
        String today = WeekMath.todayDateLabel();
        LinearLayout col = Ui.page(this, "校园", "校历", "数据来自学校官网「校园生活 → 校历」");

        // 大号状态读数
        TextView big = new TextView(this);
        big.setText(AcademicCalendar.statusLine(today));
        big.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        big.setTextSize(15f);
        big.setTextColor(G.INK);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 18);
        col.addView(big, bl);

        List<AcademicCalendar.Event> nowEv = AcademicCalendar.eventsOn(today);
        if (!nowEv.isEmpty()) {
            TextView n = new TextView(this);
            n.setText("● 今天：" + nowEv.get(0).label);
            n.setTypeface(Typeface.MONOSPACE);
            n.setTextSize(12f);
            n.setTextColor(G.ACCENT);
            n.setPadding(0, Ui.dp(this, 6), 0, 0);
            col.addView(n);
        }

        AcademicCalendar.Term term = AcademicCalendar.termFor(today);
        if (term != null) {
            Ui.label(this, col, "TERM · " + term.name);
            LinearLayout c = Ui.card(this, col);
            Ui.row(this, c, "01", "第 1 周周一", AcademicCalendar.shortDate(term.classStart) + " 上课", null, null);
            Ui.row(this, c, "02", "教学周", term.teachingWeeks + " 周", null, null);
            Ui.row(this, c, "03", "期末考试周", AcademicCalendar.shortDate(term.examStart) + " – "
                    + AcademicCalendar.shortDate(term.examEnd), null, null);
            Ui.row(this, c, "04", term.breakName, AcademicCalendar.shortDate(term.breakStart) + " – "
                    + AcademicCalendar.shortDate(term.breakEnd), null, null);
            removeTrailing(c);

            String curStart = WeekMath.normalizeToMonday(Prefs.semesterStartDate(this));
            if (!term.classStart.equals(curStart)) {
                TextView set = Ui.button(this, "把课表开学日期设为 " + AcademicCalendar.shortDate(term.classStart),
                        true, v -> applyStart(term));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
                sl.topMargin = Ui.dp(this, 12);
                col.addView(set, sl);
            }
        }

        Ui.label(this, col, "NEXT · 接下来");
        LinearLayout ev = Ui.card(this, col);
        int i = 1;
        for (AcademicCalendar.Event e : AcademicCalendar.upcoming(today, 8)) {
            String when = e.start.equals(e.end) ? AcademicCalendar.shortDate(e.start)
                    : AcademicCalendar.shortDate(e.start) + "–" + AcademicCalendar.shortDate(e.end);
            long days = AcademicCalendar.daysBetween(today, e.start);
            String sub = days > 0 ? when + " · " + days + " 天后" : when + " · 进行中";
            Ui.row(this, ev, String.format("%02d", i++), e.label, sub, e.holiday ? "假" : "校", null);
        }

        Ui.label(this, col, "SOURCE · 原图");
        LinearLayout src = Ui.card(this, col);
        Ui.row(this, src, "01", "查看官网校历原图", "联网获取最新一年，可缩放", "↗", v -> openOriginal());

        TextView note = new TextView(this);
        note.setText("节假日调休以国务院公布为准。学校每年 6 月发布新校历。");
        note.setTextSize(11.5f);
        note.setTextColor(G.MUTE);
        note.setPadding(0, Ui.dp(this, 14), 0, 0);
        col.addView(note);
    }

    /** 信息行没有点击，去掉右侧的 ›。 */
    private void removeTrailing(LinearLayout card) {
        for (int k = 0; k < card.getChildCount(); k++) {
            if (card.getChildAt(k) instanceof LinearLayout) {
                LinearLayout r = (LinearLayout) card.getChildAt(k);
                if (r.getChildCount() == 3) r.getChildAt(2).setVisibility(android.view.View.GONE);
            }
        }
    }

    private void applyStart(AcademicCalendar.Term term) {
        new AlertDialog.Builder(this)
                .setTitle("设置开学日期")
                .setMessage("把当前学期课表的第 1 周周一设为 " + term.classStart + "（" + term.name + "）？")
                .setNegativeButton("取消", null)
                .setPositiveButton("设置", (d, w) -> {
                    boolean ok = SemesterArchive.updateActiveSemesterMeta(this, term.classStart,
                            Prefs.storedTotalWeeks(this)) && SemesterArchive.ensureActiveMirror(this);
                    if (ok) {
                        Prefs.setCurrentWeek(this, WeekMath.currentWeek(term.classStart,
                                WeekMath.openWeekSpan(term.classStart)));
                        Toast.makeText(this, "已设置，课表周次已更新", Toast.LENGTH_SHORT).show();
                        render();
                    } else {
                        Toast.makeText(this, "设置失败：请先同步或导入课表", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void openOriginal() {
        String cached = Prefs.calendarImageUrl(this);
        Toast.makeText(this, "正在获取官网校历…", Toast.LENGTH_SHORT).show();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String img = null;
            try {
                String article = CalendarSource.latestArticleUrl(get(AcademicCalendar.SOURCE_LIST_URL));
                if (article != null) img = CalendarSource.imageUrl(get(article));
            } catch (Exception ignored) {
            }
            final String found = img != null ? img : (cached.isEmpty() ? null : cached);
            main.post(() -> {
                if (isFinishing()) return;
                if (found == null) {
                    Toast.makeText(this, "官网暂时打不开，稍后再试", Toast.LENGTH_LONG).show();
                    return;
                }
                Prefs.setCalendarImageUrl(this, found);
                if (found.endsWith(".pdf")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(found)));
                } else {
                    startActivity(new Intent(this, MapActivity.class)
                            .putExtra(MapActivity.EXTRA_URL, found)
                            .putExtra(MapActivity.EXTRA_TITLE, "校历原图"));
                }
            });
        }).start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TangLi");
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
