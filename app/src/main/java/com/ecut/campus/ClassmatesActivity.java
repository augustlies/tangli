package com.ecut.campus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

/** 二级页：同学。分享我的课表、添加同学、同学列表 → 共同空闲（三级）。 */
public class ClassmatesActivity extends AppCompatActivity {

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) importText(result.getContents());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        LinearLayout col = Ui.page(this, "设置", "同学", "互相导入课表，看谁什么时候有空");

        Ui.label(this, col, "ME · 我的课表");
        LinearLayout me = Ui.card(this, col);
        String name = Prefs.shareName(this);
        Ui.row(this, me, "01", "分享我的课表", "二维码 / 分享码，只含课程、地点和时间", "›",
                v -> startActivity(new Intent(this, ShareActivity.class)));
        Ui.row(this, me, "02", "分享时显示的名字", name.isEmpty() ? "未设置（同学那边会显示「同学」）" : name, "改",
                v -> editName());

        Ui.label(this, col, "ADD · 添加同学");
        LinearLayout add = Ui.card(this, col);
        Ui.row(this, add, "01", "扫同学的二维码", "对方在「分享我的课表」里打开二维码", "SCAN", v -> scan());
        Ui.row(this, add, "02", "粘贴分享码", "微信里复制整段消息就行，会自动识别", "PASTE", v -> paste());

        List<FriendStore.Friend> friends = FriendStore.load(this);
        Ui.label(this, col, "FRIENDS · 同学 " + friends.size());
        LinearLayout list = Ui.card(this, col);
        if (friends.isEmpty()) {
            Ui.row(this, list, "--", "还没有同学", "加上之后，就能看你们的共同空闲", "", null);
        } else {
            if (friends.size() >= 2) {
                Ui.row(this, list, "ALL", "大家的共同空闲", friends.size() + 1 + " 人一起，约自习、约饭、排小组作业", "›",
                        v -> startActivity(new Intent(this, FreeTimeActivity.class)
                                .putExtra(FreeTimeActivity.EXTRA_ALL, true)));
            }
            int i = 1;
            for (FriendStore.Friend f : friends) {
                int n = f.shared.timetable.courses.size();
                android.view.View r = Ui.row(this, list, String.format("%02d", i++), f.name,
                        "和我的共同空闲 · 对方 " + n + " 门课 · 长按管理", "›",
                        v -> startActivity(new Intent(this, FreeTimeActivity.class)
                                .putExtra(FreeTimeActivity.EXTRA_FRIEND, f.id)));
                r.setOnLongClickListener(v -> {
                    manage(f, v);
                    return true;
                });
            }
        }
    }

    private void editName() {
        EditText et = new EditText(this);
        et.setText(Prefs.shareName(this));
        et.setSingleLine(true);
        et.setHint("例如：小董");
        et.setBackgroundResource(R.drawable.bg_gauge_input);
        int p = Ui.dp(this, 22);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
        new AlertDialog.Builder(this).setTitle("分享时显示的名字").setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setShareName(this, et.getText().toString().trim());
                    render();
                }).show();
    }

    private void scan() {
        ScanOptions o = new ScanOptions();
        o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        o.setPrompt("对准同学的课表二维码");
        o.setBeepEnabled(false);
        o.setOrientationLocked(true);
        scanner.launch(o);
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        String text = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null
                ? clip.getItemAt(0).getText().toString() : "";
        if (text.contains(com.ecut.campus.data.TimetableShare.PREFIX)) {
            importText(text);
            return;
        }
        EditText et = new EditText(this);
        et.setHint("把同学发来的整段消息粘贴到这里");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        et.setBackgroundResource(R.drawable.bg_gauge_input);
        int p = Ui.dp(this, 22);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("粘贴分享码").setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", (d, w) -> importText(et.getText().toString()))
                .show();
    }

    private void importText(String text) {
        FriendStore.Friend f = FriendStore.add(this, text, null);
        if (f == null) {
            Toast.makeText(this, "没认出课表分享码", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "已添加「" + f.name + "」的课表 · " + f.shared.timetable.courses.size() + " 门",
                Toast.LENGTH_SHORT).show();
        render();
    }

    private void manage(FriendStore.Friend f, android.view.View anchor) {
        new GaugeMenu(this).add("改名").divider().add("删除")
                .show(anchor, t -> {
                    if ("删除".equals(t)) {
                        new AlertDialog.Builder(this).setTitle("删除「" + f.name + "」？")
                                .setMessage("只会删掉你手机上的这份课表。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (d, w) -> {
                                    FriendStore.remove(this, f.id);
                                    render();
                                }).show();
                    } else {
                        EditText et = new EditText(this);
                        et.setText(f.name);
                        et.setSingleLine(true);
                        et.setBackgroundResource(R.drawable.bg_gauge_input);
                        int p = Ui.dp(this, 22);
                        LinearLayout box = new LinearLayout(this);
                        box.setPadding(p, Ui.dp(this, 8), p, 0);
                        box.addView(et, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
                        new AlertDialog.Builder(this).setTitle("改名").setView(box)
                                .setNegativeButton("取消", null)
                                .setPositiveButton("保存", (d, w) -> {
                                    String n = et.getText().toString().trim();
                                    if (!n.isEmpty()) FriendStore.rename(this, f.id, n);
                                    render();
                                }).show();
                    }
                });
    }
}
