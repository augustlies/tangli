package com.ecut.campus;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.data.SchoolProfile;

import java.util.ArrayList;
import java.util.List;

/** 选学校（从 MainActivity 拆出）：当前学校 + 搜索（内置学校与目录） + 「帮我找」 + 手动填网址。 */
final class SchoolPicker {

    static final int REQ_FIND_SCHOOL = 2002;

    private final MainActivity a;
    private String pendingFinderName;

    SchoolPicker(MainActivity a) {
        this.a = a;
    }

    void show() {
        List<SchoolProfile> builtins = Schools.builtin(a);
        SchoolProfile cur = Schools.current(a);
        SchoolProfile custom = Schools.custom(a);
        // 选过学校，或已经登录 / 同步过（老用户没选过学校但一直在用东华理工）才算「有当前学校」
        boolean hasCur = Prefs.hasChosenSchool(a) || Prefs.hasPortalSession(a) || Prefs.timetableSyncedAt(a) > 0;
        com.ecut.campus.data.SchoolDirectory dir = Schools.directory(a);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.EditText search = new android.widget.EditText(a);
        search.setHint("搜学校名，如 华东理工");
        search.setSingleLine(true);
        box.addView(search);
        android.widget.ListView list = new android.widget.ListView(a);
        list.setDivider(null);
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(320 * a.getResources().getDisplayMetrics().density)));

        // 每一行：名字 + 小字说明；行为由 actions 决定
        List<String[]> rows = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        AlertDialog[] holder = new AlertDialog[1];
        android.widget.ArrayAdapter<String[]> adapter = new android.widget.ArrayAdapter<String[]>(
                a, android.R.layout.simple_list_item_2, android.R.id.text1, rows) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = super.getView(pos, convert, parent);
                String[] r = getItem(pos);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                t1.setText(r[0]);
                t1.setTextSize(15);
                t2.setText(r[1]);
                t2.setTextSize(12);
                t2.setVisibility(r[1].isEmpty() ? View.GONE : View.VISIBLE);
                return v;
            }
        };
        list.setAdapter(adapter);
        Runnable refill = () -> {
            String q = search.getText().toString().trim();
            rows.clear();
            actions.clear();
            if (q.isEmpty()) {
                // 默认列表不置顶任何学校：只放用户自己选中的那一所（如果有），然后是搜索和手动填
                if (hasCur) {
                    rows.add(new String[]{cur.name + "  ✓", cur.isCustom() ? "自定义学校 · 点此修改网址" : "当前学校"});
                    actions.add(() -> {
                        holder[0].dismiss();
                        if (cur.isCustom()) showCustomSchoolDialog(custom);
                    });
                }
            } else {
                // 内置学校只作为普通搜索结果出现（用自己的适配），不再默认置顶
                for (SchoolProfile p : builtins) {
                    if (!p.name.contains(q) && !(q.length() >= 2 && p.aliasMatch(q))) continue;
                    boolean on = hasCur && p.id.equals(cur.id);
                    rows.add(new String[]{p.name + (on ? "  ✓" : ""), on ? "当前学校 · 已适配" : "已适配"});
                    actions.add(() -> {
                        holder[0].dismiss();
                        if (on) return;
                        Schools.select(a, p);
                        a.refreshSettingsTab();
                        Toast.makeText(a, "已切换到 " + p.name, Toast.LENGTH_SHORT).show();
                    });
                }
                if (custom != null && custom.name.contains(q)) {
                    rows.add(new String[]{custom.name + (cur.isCustom() ? "  ✓" : ""),
                            "自定义学校 · 点此修改网址"});
                    actions.add(() -> { holder[0].dismiss(); showCustomSchoolDialog(custom); });
                }
            }
            if (q.isEmpty()) {
                rows.add(new String[]{"输入校名搜索", "收录了 " + dir.size() + " 所学校的教务入口"});
                actions.add(() -> {});
            } else {
                for (com.ecut.campus.data.SchoolDirectory.Entry e : dir.search(q, 40)) {
                    rows.add(new String[]{e.name, e.typeLabel() + (e.importable() ? "" : " · 暂不能导入课表")
                            + " · " + SchoolProfile.hostOf(e.url)});
                    actions.add(() -> { holder[0].dismiss(); confirmDirectorySchool(e); });
                }
                rows.add(new String[]{"没找到 / 网址不对？帮我找", "在网上搜「" + q + " 教务系统」，点进登录页后一键填入"});
                actions.add(() -> { holder[0].dismiss(); openSchoolFinder(q); });
            }
            rows.add(new String[]{"手动输入教务网址", "知道网址就直接填"});
            actions.add(() -> { holder[0].dismiss(); showCustomSchoolDialog(custom); });
            adapter.notifyDataSetChanged();
        };
        list.setOnItemClickListener((parent, v, pos, id) -> {
            if (pos >= 0 && pos < actions.size()) actions.get(pos).run();
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) { refill.run(); }
        });
        refill.run();
        holder[0] = new AlertDialog.Builder(a)
                .setTitle("选择学校")
                .setView(box)
                .setNegativeButton("取消", null)
                .show();
    }

    /** 目录里的学校：先说清楚是什么系统、网址可能过时，再去登录。 */
    private void confirmDirectorySchool(com.ecut.campus.data.SchoolDirectory.Entry e) {
        SchoolProfile p = com.ecut.campus.data.SchoolDirectory.toProfile(e);
        if (p == null) {
            Toast.makeText(a, "这个网址不对，换「帮我找」试试", Toast.LENGTH_LONG).show();
            openSchoolFinder(e.name);
            return;
        }
        String msg = "教务系统：" + e.typeLabel()
                + (e.importable() ? "（烫梨能导入课表）" : "（烫梨暂时读不了这类课表，可以先登录看看）")
                + ("金智".equals(e.typeLabel()) ? "\n登录后选「学生」，在大厅里打开「我的课表」，课表显示出来再点「导入此页课表」。" : "")
                + "\n入口：" + e.url
                + "\n\n这个网址来自公开整理，可能已经换了。打开后要是不是你们的教务登录页，回来点「帮我找」。";
        new AlertDialog.Builder(a)
                .setTitle(e.name)
                .setMessage(msg)
                .setNeutralButton("帮我找", (d, w) -> openSchoolFinder(e.name))
                .setNegativeButton("取消", null)
                .setPositiveButton("去登录", (d, w) -> {
                    Schools.select(a, p);
                    a.refreshSettingsTab();
                    a.openWeb("portal");
                })
                .show();
    }

    private void openSchoolFinder(String school) {
        pendingFinderName = school;
        Intent i = new Intent(a, SchoolFinderActivity.class);
        i.putExtra(SchoolFinderActivity.EXTRA_QUERY, school);
        a.startActivityForResult(i, REQ_FIND_SCHOOL);
    }

    private void showCustomSchoolDialog(SchoolProfile existing) {
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.EditText name = new android.widget.EditText(a);
        name.setHint("学校名称（可不填）");
        name.setSingleLine(true);
        android.widget.EditText url = new android.widget.EditText(a);
        url.setHint("教务网址，如 jw.xxx.edu.cn/jwglxt");
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (existing != null) {
            if (!"自定义学校".equals(existing.name)) name.setText(existing.name);
            url.setText(existing.loginUrl);
        }
        TextView label = new TextView(a);
        label.setText("教务系统类型");
        label.setPadding(0, pad / 2, 0, 0);
        android.widget.RadioGroup group = new android.widget.RadioGroup(a);
        String[] vendors = {"自动识别", "正方", "强智", "其他 / 不知道"};
        for (int i = 0; i < vendors.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(a);
            rb.setText(vendors[i]);
            rb.setId(View.generateViewId());
            rb.setTag(i);
            group.addView(rb);
            if (i == 0) group.check(rb.getId());
        }
        TextView tip = new TextView(a);
        tip.setText("打开教务登录页后，把浏览器地址栏里的网址复制过来就行。烫梨不会保存你的账号密码，登录在网页里完成。");
        tip.setTextSize(12);
        tip.setPadding(0, pad / 2, 0, 0);
        box.addView(name);
        box.addView(url);
        box.addView(label);
        box.addView(group);
        box.addView(tip);
        new AlertDialog.Builder(a)
                .setTitle("自定义学校")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    View checked = group.findViewById(group.getCheckedRadioButtonId());
                    int idx = checked == null ? 0 : (int) checked.getTag();
                    SchoolProfile.Vendor vendor = idx == 1 ? SchoolProfile.Vendor.ZHENGFANG
                            : idx == 2 ? SchoolProfile.Vendor.QIANGZHI
                            : idx == 3 ? SchoolProfile.Vendor.OTHER : null;
                    SchoolProfile p = SchoolProfile.custom(url.getText().toString(), vendor);
                    if (p == null) {
                        Toast.makeText(a, "网址不对，请检查一下", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = name.getText().toString().trim();
                    if (!n.isEmpty()) p.name = n;
                    Schools.select(a, p);
                    a.refreshSettingsTab();
                    boolean h5 = p.vendor == SchoolProfile.Vendor.OTHER
                            && new com.ecut.campus.data.QiangzhiH5Source().detect(p.loginUrl, null)
                            >= com.ecut.campus.data.TimetableSources.MIN_CONFIDENCE;
                    boolean wisedu = p.vendor == SchoolProfile.Vendor.OTHER && !h5
                            && new com.ecut.campus.data.WiseduSource().detect(p.loginUrl, null)
                            >= com.ecut.campus.data.TimetableSources.MIN_CONFIDENCE;
                    String msg = wisedu
                            ? "识别为金智网上办事大厅。登录后选「学生」，在大厅里打开「我的课表」，等课表出来再点底部的「导入此页课表」；这类系统不能后台同步。"
                            : h5
                            ? "识别为强智移动版教务。登录后在首页点底部的「导入此页课表」就行；这类系统不能后台同步，课表有变化时再打开网页导一次。"
                            : p.vendor == SchoolProfile.Vendor.OTHER
                            ? "没认出教务系统类型。登录后进入课表页，点底部的「导入此页课表」试试。"
                            : "识别为" + p.vendor.label + "教务。先登录一次教务系统，之后就能同步课表"
                            + (p.supportsGrades() ? "和成绩" : "") + "。";
                    new AlertDialog.Builder(a)
                            .setTitle("已保存")
                            .setMessage(msg)
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("去登录", (d2, w2) -> a.openWeb("portal"))
                            .show();
                })
                .show();
    }

    /** 「帮我找」页面返回：带回登录页网址，预填到自定义学校对话框。 */
    void onFinderResult(int resultCode, Intent data) {
        String url = resultCode == android.app.Activity.RESULT_OK && data != null
                ? data.getStringExtra(SchoolFinderActivity.EXTRA_URL) : null;
        if (url == null) return;
        SchoolProfile found = SchoolProfile.custom(url, null);
        if (found == null) return;
        if (pendingFinderName != null && !pendingFinderName.trim().isEmpty()) {
            found.name = pendingFinderName.trim();
        }
        showCustomSchoolDialog(found);
    }
}
