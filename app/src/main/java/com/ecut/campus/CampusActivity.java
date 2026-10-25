package com.ecut.campus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.AcademicCalendar;

/** 二级页：校园。校历、地图（三级）+ 图书馆、邮箱（外链）。 */
public class CampusActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        render();
    }

    private void render() {
        LinearLayout col = Ui.page(this, "设置", "校园", "离线可看的放前面，需要联网的在后面");
        Ui.label(this, col, "OFFLINE · 离线");
        LinearLayout c1 = Ui.card(this, col);
        Ui.row(this, c1, "01", "校历", AcademicCalendar.statusLine(WeekMath.todayDateLabel()), "›",
                v -> startActivity(new Intent(this, CalendarActivity.class)));
        Ui.row(this, c1, "02", "校园地图", "南昌校区 · 卫星图 · 双指缩放", "›",
                v -> startActivity(new Intent(this, MapActivity.class)));

        Ui.label(this, col, "ONLINE · 在线");
        LinearLayout c2 = Ui.card(this, col);
        Ui.row(this, c2, "01", "预约图书馆", "座位 / 研讨间预约", "↗ WEB",
                v -> CampusLinks.openLibrary(this));
        Ui.row(this, c2, "02", "校园邮箱", "需校园网 · 统一认证后开通", "↗ WEB",
                v -> CampusLinks.openMail(this));
    }
}
