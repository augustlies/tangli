package com.ecut.campus.fun;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.G;
import com.ecut.campus.Prefs;
import com.ecut.campus.R;
import com.ecut.campus.Ui;
import com.ecut.campus.data.ClassPrice;
import com.ecut.campus.data.TimetableStore;

/** 二级页「小玩意」：翻牌批语开关、学费（这节课值多少钱）、学期人设卡、双人课表海报。全部本地。 */
public class FunActivity extends AppCompatActivity {

    /** 从课程详情点「填一下学费」进来：直接弹学费框。 */
    public static final String EXTRA_EDIT_TUITION = "edit_tuition";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_EDIT_TUITION, false)) {
            getWindow().getDecorView().post(this::editTuition);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        LinearLayout col = Ui.page(this, "设置", "小玩意", "都在你手机上算，不联网、不上传");

        Ui.label(this, col, "GRADES · 成绩");
        LinearLayout grades = Ui.card(this, col);
        boolean on = FunPrefs.verdictOn(this);
        Ui.row(this, grades, "01", "翻牌批语", "新成绩先翻出一句评语，再点一下才看分数", on ? "开" : "关", v -> {
            FunPrefs.setVerdictOn(this, !FunPrefs.verdictOn(this));
            render();
        });

        Ui.label(this, col, "TIMETABLE · 课表");
        LinearLayout tt = Ui.card(this, col);
        double tuition = FunPrefs.tuition(this);
        String priceSub;
        if (tuition <= 0) {
            priceSub = "填一次每学期学费，课程详情里显示「这节 ¥xx」；不填就不显示";
        } else {
            int total = ClassPrice.totalSections(TimetableStore.load(this), Prefs.totalWeeks(this));
            double per = ClassPrice.perSection(tuition, total);
            priceSub = "每学期 ¥" + ClassPrice.yuan(tuition)
                    + (per > 0 ? " · 本学期 " + total + " 节 · 每节 ¥" + ClassPrice.yuan(per) : " · 课表还是空的");
        }
        Ui.row(this, tt, "01", "这节课值多少钱", priceSub, tuition > 0 ? "改" : "填", v -> editTuition());
        Ui.row(this, tt, "02", "缺勤安全气囊", "点开课表上的课记请假 / 缺勤 / 被点名；快到 1/3 时课块描红边", "", null);

        Ui.label(this, col, "SHARE · 分享卡片");
        LinearLayout share = Ui.card(this, col);
        Ui.row(this, share, "01", "学期人设卡", "按你的课表和成绩挑一个人设，生成一张图", "›",
                v -> FunCards.showPersona(this));
        Ui.row(this, share, "02", "双人课表海报", "选一个同学，两人课表叠成一张长图", "›",
                v -> FunCards.pickFriend(this));
    }

    private void editTuition() {
        EditText et = new EditText(this);
        et.setText(FunPrefs.tuitionRaw(this));
        et.setSingleLine(true);
        et.setHint("例如 5500");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setBackgroundResource(R.drawable.bg_gauge_input);
        LinearLayout box = Ui.dialogBox(this, et, Ui.dp(this, 46));
        new AlertDialog.Builder(this).setTitle("每学期学费（元）").setView(box)
                .setMessage("只用来算「这节课值多少钱」，只存在本机。清空就不再显示。")
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", (d, w) -> {
                    FunPrefs.setTuitionRaw(this, "");
                    render();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String raw = et.getText().toString().trim();
                    if (!raw.isEmpty() && ClassPrice.parseTuition(raw) <= 0) {
                        Toast.makeText(this, "没看懂这个数，填个整数就行", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FunPrefs.setTuitionRaw(this, raw);
                    render();
                }).show();
    }
}
