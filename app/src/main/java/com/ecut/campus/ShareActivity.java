package com.ecut.campus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableShare;
import com.ecut.campus.data.TimetableStore;


/** 三级页：我的课表二维码 + 分享码。 */
public class ShareActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        LinearLayout col = Ui.page(this, "同学", "分享我的课表", "只包含课程名、地点、时间、周次和作息");

        Timetable t = TimetableStore.load(this);
        if (t.courses.isEmpty()) {
            Ui.label(this, col, "EMPTY");
            Ui.text(this, col, "还没有课表。先在课表页同步或导入。", 14f, G.SUB);
            return;
        }
        String code;
        try {
            code = TimetableShare.encode(t, Prefs.shareName(this),
                    ScheduleSettingsStore.load(this).toShare()); // 1.2.6：带上作息，同学扫码可一键沿用
        } catch (Exception e) {
            Ui.text(this, col, "生成失败：" + e.getMessage(), 14f, G.ACCENT);
            return;
        }

        // 二维码放在纸白方块里，深色模式下也保证能被扫到
        FrameLayout qrBox = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 8));
        bg.setColor(0xFFFBFAF7);
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), G.LINE);
        qrBox.setBackground(bg);
        int pad = Ui.dp(this, 16);
        qrBox.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        Bitmap qr = QrBitmap.make(code, 900, 0xFF111111, 0xFFFBFAF7);
        if (qr != null) iv.setImageBitmap(qr);
        qrBox.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ql.topMargin = Ui.dp(this, 20);
        col.addView(qrBox, ql);

        TextView meta = new TextView(this);
        meta.setText(t.courses.size() + " 门课  ·  " + code.length() + " 字符" + (qr == null ? "  ·  太长，请用分享码" : ""));
        meta.setTypeface(Typeface.MONOSPACE);
        meta.setTextSize(11f);
        meta.setTextColor(G.MUTE);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        String message = (Prefs.shareName(this).isEmpty() ? "我" : Prefs.shareName(this))
                + "的课表，用「烫梨」→ 设置 → 同学 → 粘贴分享码 导入：\n" + code;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView send = Ui.button(this, "发给同学", true, v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(i, "发给同学"));
        });
        TextView copy = Ui.button(this, "复制分享码", false, v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("烫梨课表", message));
            Toast.makeText(this, "已复制，去微信粘贴给同学", Toast.LENGTH_SHORT).show();
        });
        row.addView(send, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        cl.leftMargin = Ui.dp(this, 10);
        row.addView(copy, cl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 16);
        col.addView(row, rl);

        TextView tip = new TextView(this);
        tip.setText("面对面：对方在「同学 → 扫同学的二维码」扫这张图。\n不在身边：发分享码，对方复制整段消息后点「粘贴分享码」。");
        tip.setTextSize(12f);
        tip.setTextColor(G.SUB);
        tip.setLineSpacing(0, 1.3f);
        tip.setPadding(0, Ui.dp(this, 16), 0, 0);
        col.addView(tip);
    }
}
