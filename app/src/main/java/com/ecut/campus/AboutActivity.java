package com.ecut.campus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 二级页：关于。项目主页、检查更新、问题反馈、许可。 */
public class AboutActivity extends AppCompatActivity {
    private TextView updateTrail;
    private TextView updateSub;
    private boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        render();
        check(false);
    }

    private void render() {
        LinearLayout col = Ui.page(this, "设置", "烫梨", "v" + BuildConfig.VERSION_NAME
                + " · 东华理工大学非官方校园工具");

        Ui.label(this, col, "PROJECT · 项目");
        LinearLayout c1 = Ui.card(this, col);
        Ui.row(this, c1, "01", "GitHub 项目主页", "觉得好用就点个 Star，让更多同学找到它", "★ STAR",
                v -> Updater.open(this, Updater.REPO_URL));
        View upd = Ui.row(this, c1, "02", "检查更新", "正在检查…", "…", v -> check(true));
        bindUpdateRow(upd);
        Ui.row(this, c1, "03", "全部版本与更新日志", "历史版本都在这里下载", "↗ WEB",
                v -> Updater.open(this, Updater.RELEASES_URL));
        Ui.row(this, c1, "04", "自动检查更新", "每天打开时最多检查一次",
                Prefs.updateAutoCheck(this) ? "ON" : "OFF", v -> {
                    boolean on = !Prefs.updateAutoCheck(this);
                    Prefs.setUpdateAutoCheck(this, on);
                    recreate();
                });

        Ui.row(this, c1, "05", "使用指南", "登录后怎么同步课表、查成绩、去广告", "?",
                v -> LoginGuide.show(this, null));

        Ui.label(this, col, "FEEDBACK · 问题反馈");
        LinearLayout c2 = Ui.card(this, col);
        Ui.row(this, c2, "01", "发邮件反馈", Updater.FEEDBACK_EMAIL, "MAIL", v -> mail());
        Ui.row(this, c2, "02", "在 GitHub 提 Issue", "需要 GitHub 账号，方便追踪进度", "↗ WEB",
                v -> Updater.open(this, Updater.ISSUES_URL));
        Ui.row(this, c2, "03", "复制邮箱地址", "邮件打不开时用", "COPY", v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("email", Updater.FEEDBACK_EMAIL));
            Toast.makeText(this, "已复制 " + Updater.FEEDBACK_EMAIL, Toast.LENGTH_SHORT).show();
        });
        Ui.row(this, c2, "04", "导出诊断日志", "课表不对、同步失败时导出，连同截图一起发来", "EXPORT",
                v -> DiagLog.export(this));
        TextView tip = Ui.text(this, col,
                "反馈时说清楚哪个页面、做了什么、看到了什么，最好附一张截图。邮件里会自动带上版本号和机型。",
                12f, G.MUTE);
        tip.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), 0);

        Ui.label(this, col, "LICENSE · 许可");
        LinearLayout c3 = Ui.card(this, col);
        Ui.row(this, c3, "01", "GPL-3.0 开源许可", "可自由使用和修改，改版须同样开源 · © August Lies", "↗ WEB",
                v -> Updater.open(this, Updater.REPO_URL + "/blob/main/LICENSE"));
        Ui.row(this, c3, "02", "广告规则", "部分来自 anti-AD（MIT）", "↗ WEB",
                v -> Updater.open(this, "https://github.com/privacy-protection-tools/anti-AD"));
        TextView note = Ui.text(this, col,
                "非官方工具，与东华理工大学无关。教务数据以学校系统为准，成绩计算仅供参考。烫梨不保存任何账号密码。",
                12f, G.MUTE);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), 0);

        // 连点 7 次版本号进入调试台
        TextView ver = Ui.text(this, col, "烫梨 v" + BuildConfig.VERSION_NAME + " · build " + BuildConfig.VERSION_CODE,
                11f, G.MUTE);
        ver.setTypeface(android.graphics.Typeface.MONOSPACE);
        ver.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 8));
        ver.setOnClickListener(v -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastVerTap > 1500) verTaps = 0;
            lastVerTap = now;
            verTaps++;
            if (verTaps >= 7) {
                verTaps = 0;
                startActivity(new Intent(this, DebugActivity.class));
            } else if (verTaps >= 4) {
                Toast.makeText(this, "再点 " + (7 - verTaps) + " 次进入调试台", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int verTaps;
    private long lastVerTap;

    /** Ui.row 返回的行：[序号, 文字列, 右侧]；拿到副标题和右侧标记，用来显示检查结果。 */
    private void bindUpdateRow(View row) {
        LinearLayout r = (LinearLayout) row;
        LinearLayout texts = (LinearLayout) r.getChildAt(1);
        updateSub = texts.getChildCount() > 1 ? (TextView) texts.getChildAt(1) : null;
        updateTrail = (TextView) r.getChildAt(2);
    }

    private void setUpdate(String sub, String trail, int color) {
        if (updateSub != null) updateSub.setText(sub);
        if (updateTrail != null) {
            updateTrail.setText(trail);
            updateTrail.setTextColor(color);
        }
    }

    private void check(boolean userTap) {
        if (checking) return;
        checking = true;
        setUpdate("正在检查…", "…", G.MUTE);
        Updater.fetchLatest(r -> {
            checking = false;
            if (isFinishing()) return;
            if (r == null) {
                setUpdate("连不上 GitHub，稍后再试或点「全部版本」", "重试", G.MUTE);
                return;
            }
            Prefs.setUpdateCheckedAt(this, System.currentTimeMillis());
            if (Updater.isNewer(r)) {
                setUpdate("新版本 " + r.tag + " 可以下载", "● NEW", G.ACCENT);
                if (userTap) Updater.showDialog(this, r, false);
            } else {
                setUpdate("已是最新版本", "✓", G.MUTE);
                if (userTap) Toast.makeText(this, "已是最新版本", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mail() {
        String subject = "[烫梨反馈] v" + BuildConfig.VERSION_NAME;
        String body = "\n\n\n————\n版本：v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\n机型：" + Build.MANUFACTURER + " " + Build.MODEL
                + "\n系统：Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{Updater.FEEDBACK_EMAIL});
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(Intent.createChooser(i, "选择邮件应用"));
        } catch (Exception e) {
            Toast.makeText(this, "没有邮件应用，邮箱已复制", Toast.LENGTH_SHORT).show();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("email", Updater.FEEDBACK_EMAIL));
        }
    }
}
