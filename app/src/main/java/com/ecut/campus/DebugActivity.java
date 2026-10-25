package com.ecut.campus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.AppSetting;
import com.ecut.campus.data.Dates;
import com.ecut.campus.data.TangliCommand;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 调试台（关于页连点版本号 7 次进入）：直接输入烫梨指令执行、查看当前状态、翻历史。
 * 所有指令照样走预览确认，不绕过任何检查。
 */
public class DebugActivity extends AppCompatActivity {

    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 执行完指令回来，状态要是新的
        if (input != null) {
            String keep = input.getText().toString();
            render();
            input.setText(keep);
        }
    }

    private void render() {
        LinearLayout col = Ui.page(this, "关于", "调试台", "直接执行烫梨指令 · 每条仍需确认");

        Ui.label(this, col, "CONSOLE · 指令");
        LinearLayout c1 = Ui.card(this, col);
        input = new EditText(this);
        input.setHint("同步 课表\n设置 主题=深色\n作息 第1节 08:10-08:55\n打开 校园邮箱");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13f);
        input.setTextColor(G.INK);
        input.setHintTextColor(G.MUTE);
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(null);
        int p = Ui.dp(this, 16);
        input.setPadding(p, p, p, p);
        c1.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp2.leftMargin = Ui.dp(this, 10);
        btns.addView(Ui.button(this, "预览并执行", true, v -> run()), bp);
        btns.addView(Ui.button(this, "复制为链接", false, v -> copyLink()), bp2);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 10);
        col.addView(btns, bl);

        Ui.label(this, col, "STATE · 当前状态");
        LinearLayout c2 = Ui.card(this, col);
        String state = state();
        TextView st = new TextView(this);
        st.setText(state);
        st.setTypeface(Typeface.MONOSPACE);
        st.setTextSize(11.5f);
        st.setTextColor(G.INK);
        st.setLineSpacing(0, 1.2f);
        st.setTextIsSelectable(true);
        st.setPadding(p, p, p, p);
        c2.addView(st);
        Ui.row(this, c2, "01", "复制状态", "反馈问题时一起发过来", "COPY", v -> {
            copy("烫梨状态", state);
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });

        Ui.label(this, col, "TOOLS · 工具");
        LinearLayout c3 = Ui.card(this, col);
        Ui.row(this, c3, "01", "导出全部为指令", "课表、停课调课、备注、设置、作息，换手机时一次还原", "COPY",
                v -> CommandUi.exportAll(this));
        Ui.row(this, c3, "02", "复制给 AI 的说明", "粘给任意 AI，说出你想怎么改", "COPY",
                v -> CommandUi.copyAiGuide(this));
        Ui.row(this, c3, "03", "导出诊断日志", "不含 Cookie、学号、姓名", "EXPORT", v -> DiagLog.export(this));

        List<String> history = CommandUi.history(this);
        Ui.label(this, col, "HISTORY · 最近执行");
        LinearLayout c4 = Ui.card(this, col);
        if (history.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("还没有执行过指令");
            none.setTextSize(12.5f);
            none.setTextColor(G.MUTE);
            none.setPadding(p, p, p, p);
            c4.addView(none);
        } else {
            int i = 1;
            for (String h : history) {
                if (i > 10) break;
                String body = stripHeader(h);
                String first = body.split("\n")[0];
                int lines = body.split("\n").length;
                Ui.row(this, c4, String.format(Locale.ROOT, "%02d", i++), first,
                        lines > 1 ? "共 " + lines + " 行 · 点一下放回输入框" : "点一下放回输入框", "↺",
                        v -> {
                            input.setText(body);
                            input.requestFocus();
                        });
            }
        }
    }

    private static String stripHeader(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("```")) continue;
            if (l.contains(TangliCommand.HEADER) && !l.contains("整表")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(l);
        }
        return sb.toString();
    }

    private String commandText() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return "";
        return TangliCommand.looksLike(text) ? text : TangliCommand.HEADER + " v1\n" + text;
    }

    private void run() {
        String text = commandText();
        if (text.isEmpty()) {
            Toast.makeText(this, "先写一条指令", Toast.LENGTH_SHORT).show();
            return;
        }
        CommandUi.showPreview(this, text, () -> {
            String keep = input.getText().toString();
            render();
            input.setText(keep);
        });
    }

    private void copyLink() {
        String text = commandText();
        if (text.isEmpty()) {
            Toast.makeText(this, "先写一条指令", Toast.LENGTH_SHORT).show();
            return;
        }
        copy("烫梨链接", CommandUi.toLink(text));
        Toast.makeText(this, "已复制链接，在浏览器或备忘录里点它就能打开烫梨", Toast.LENGTH_LONG).show();
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private String state() {
        Timetable t = TimetableStore.load(this);
        TangliCommand.Env env = CommandUi.env(this, t);
        StringBuilder sb = new StringBuilder();
        sb.append("版本      v").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("今天      ").append(env.today);
        if (Dates.valid(env.today)) sb.append(' ').append(Dates.cnDay(Dates.dayOfWeek(env.today)));
        sb.append('\n');
        if (Dates.valid(env.start)) {
            sb.append("第1周周一 ").append(env.start).append(Prefs.semesterStartDate(this).isEmpty() ? "（按校历推算）" : "").append('\n');
            sb.append("本周      第 ").append(Dates.weekOf(env.start, env.today)).append(" 周\n");
        } else {
            sb.append("第1周周一 未设置\n");
        }
        sb.append("学期      ").append(t.semester.id.isEmpty() ? "—" : t.semester.id).append('\n');
        int sessions = 0, notes = 0, custom = 0;
        for (Timetable.Course c : t.courses) {
            sessions += c.sessions.size();
            if (c.note != null && !c.note.isEmpty()) notes++;
            if (!Timetable.SOURCE_JWGLXT.equals(c.source)) custom++;
        }
        sb.append("课程      ").append(t.courses.size()).append(" 门 / ").append(sessions).append(" 个时段（手动 ")
                .append(custom).append("）\n");
        sb.append("临时安排  ").append(t.adjusts.size()).append(" 条 · 备注 ").append(notes).append(" 条\n");
        long synced = Prefs.timetableSyncedAt(this);
        sb.append("上次同步  ").append(synced > 0
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(synced)) : "—").append('\n');
        sb.append("登录状态  ").append(Prefs.sessionExpired(this) ? "已失效" : Prefs.hasPortalSession(this) ? "正常" : "未登录").append('\n');
        sb.append('\n');
        for (Map.Entry<String, String> e : env.settings.entrySet()) {
            String v = AppSetting.display(e.getKey(), e.getValue());
            if (AppSetting.SHARE_NAME.equals(e.getKey()) && v.isEmpty()) v = "—";
            sb.append(pad(AppSetting.label(e.getKey()))).append(v).append('\n');
        }
        sb.append('\n');
        int n = env.sectionStart.length - 1;
        for (int i = 1; i <= n; i++) {
            sb.append(String.format(Locale.ROOT, "第%-2d节  %02d:%02d-%02d:%02d\n", i,
                    env.sectionStart[i] / 60, env.sectionStart[i] % 60, env.sectionEnd[i] / 60, env.sectionEnd[i] % 60));
        }
        return sb.toString().trim();
    }

    private static String pad(String label) {
        StringBuilder sb = new StringBuilder(label);
        // 中文按两格算，对齐到 10 格
        int w = 0;
        for (char c : label.toCharArray()) w += c > 0x2E80 ? 2 : 1;
        while (w++ < 16) sb.append(' ');
        return sb.toString();
    }
}
