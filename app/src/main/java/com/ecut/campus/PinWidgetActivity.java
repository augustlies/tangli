package com.ecut.campus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.PinLauncher;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 添加桌面组件的小卡片：长按图标的「添加今日课程组件」/「添加下一节课组件」和设置 → 通用 → 桌面组件 都打开这里。
 * 页面在前台时用 Activity 自己去请桌面放置（打开时自动请一次），等桌面回调「放好了」再显示「已添加」并关闭。
 * vivo / 小米等桌面没开「桌面快捷方式」权限时会悄悄不弹窗：约 3 秒没动静就换成帮助状态（去开权限 / 再试 / 手动步骤）。
 * 已知开了权限也不弹的桌面（vivo / iQOO OriginOS，见 data/PinLauncher）：不自动请求，直接给大号手动步骤。
 */
public final class PinWidgetActivity extends AppCompatActivity {

    static final String EXTRA_WIDGET = "widget";
    private static final long WAIT_MS = 3000;
    private static final long AFTER_RETURN_MS = 1500;

    /** 正在前台的卡片，桌面回调（ClassWidget 收到 ACTION_PINNED）时交给它。 */
    private static WeakReference<PinWidgetActivity> live = new WeakReference<>(null);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean manualLauncher;
    private boolean autoAsked, asking, pausedWhileAsking, done, fromSettings;
    private int countBefore;

    private TextView status, steps;
    private Button primary, secondary;

    static String manual() {
        return "手动添加：长按桌面空白处 → 小组件 → 搜「烫梨」 → 今日课程";
    }

    static Intent intent(Context c) {
        return new Intent(c, PinWidgetActivity.class);
    }

    /** 设置里的按钮：直接打开「今日课程」的添加卡片（只剩这一种小组件，拉成 2×2 就是方块）。 */
    static void choose(android.app.Activity a) {
        a.startActivity(intent(a));
    }

    /** ClassWidget 收到桌面的「放好了」回调时调用；有卡片在就由卡片显示，返回 false 则由调用方弹 toast。 */
    static boolean onPinned() {
        PinWidgetActivity a = live.get();
        if (a == null || a.isFinishing()) return false;
        a.ui.post(a::showDone);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        G.init(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
        }
        setContentView(R.layout.activity_pin_widget);
        if (savedInstanceState != null) autoAsked = savedInstanceState.getBoolean("asked");

        View card = findViewById(R.id.pin_card);
        GradientDrawable bg = new GradientDrawable();
        float d = getResources().getDisplayMetrics().density;
        bg.setCornerRadius((G.GLASS ? 26f : G.CARD_RADIUS) * d);
        bg.setColor(G.GLASS ? getColor(R.color.glass_dialog_sheet) : G.DIALOG);
        bg.setStroke(Math.max(1, (int) (d * (G.GLASS ? 1f : 0.5f))),
                G.GLASS ? getColor(R.color.glass_dialog_stroke) : G.CARD_STROKE);
        card.setBackground(bg);

        status = findViewById(R.id.pin_status);
        steps = findViewById(R.id.pin_steps);
        primary = findViewById(R.id.btn_pin_primary);
        secondary = findViewById(R.id.btn_pin_secondary);
        findViewById(R.id.pin_scrim).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pin_close).setOnClickListener(v -> finish());
        manualLauncher = ignoresPin();
        bindWidget();
        showStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("asked", autoAsked);
    }

    @Override
    protected void onResume() {
        super.onResume();
        live = new WeakReference<>(this);
        if (done) return;
        if (asking && pausedWhileAsking) {
            // 桌面的确认窗口关掉回来了：可能刚放好（回调稍后才到），也可能点了取消
            ui.removeCallbacks(timeout);
            ui.postDelayed(afterReturn, AFTER_RETURN_MS);
        } else if (fromSettings) {
            fromSettings = false;
            ui.postDelayed(this::ask, 400); // 从权限页回来，自动再试一次
        } else if (!autoAsked && !manualLauncher) {
            autoAsked = true;
            ui.postDelayed(this::ask, 350); // 等页面真正显示出来再请，桌面才认这是前台请求
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (asking) pausedWhileAsking = true; // 桌面弹出了确认窗口，盖住了这里
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (live.get() == this) live = new WeakReference<>(null);
        super.onDestroy();
    }

    private void bindWidget() {
        ((ImageView) findViewById(R.id.pin_preview)).setImageResource(
                R.drawable.widget_classes_preview);
        ((TextView) findViewById(R.id.pin_name)).setText("今日课程 · 4×2，可拉成 2×2");
    }

    /** 默认桌面会忽略放置请求吗（包名在名单里，或厂商是 vivo / iQOO）。 */
    private boolean ignoresPin() {
        String home = null;
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager()
                    .resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) home = ri.activityInfo.packageName;
        } catch (Exception ignored) {
            // 解析不到就只看厂商
        }
        return PinLauncher.ignoresPinRequest(home, Build.MANUFACTURER);
    }

    private void showStart() {
        if (manualLauncher) showManual();
        else showReady();
    }

    /** 不弹窗的桌面：一打开就给大号手动步骤，一键添加降为小字按钮。 */
    private void showManual() {
        asking = false;
        pausedWhileAsking = false;
        ui.removeCallbacks(timeout);
        ui.removeCallbacks(afterReturn);
        status.setText("这台手机的桌面不会弹出添加窗口，按下面几步手动加，十秒就好：");
        steps.setText(PinLauncher.originSteps());
        steps.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        steps.setTextColor(G.INK);
        steps.setLineSpacing(10 * getResources().getDisplayMetrics().density, 1f);
        steps.setVisibility(View.VISIBLE);
        primary.setText("知道了");
        primary.setEnabled(true);
        primary.setOnClickListener(v -> finish());
        secondary.setVisibility(View.GONE);
        TextView tryAnyway = findViewById(R.id.btn_pin_try);
        tryAnyway.setVisibility(View.VISIBLE);
        tryAnyway.setOnClickListener(v -> tryAnyway());
    }

    /** 「仍然试试一键添加」：只发一次请求，不等、不跳权限页；桌面真放好了回调照样会到。 */
    private void tryAnyway() {
        if (done || isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                AppWidgetManager m = AppWidgetManager.getInstance(this);
                if (m != null && m.isRequestPinAppWidgetSupported()) {
                    countBefore = placed();
                    m.requestPinAppWidget(new ComponentName(this, ClassWidget.class),
                            null, pinCallback());
                    android.widget.Toast.makeText(this, "已经请过桌面了；没弹窗的话就按上面几步手动加",
                            android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception ignored) {
                // 落到下面的提示
            }
        }
        android.widget.Toast.makeText(this, "这个桌面不支持一键添加，按上面几步手动加", android.widget.Toast.LENGTH_LONG).show();
    }

    private PendingIntent pinCallback() {
        Intent cb = new Intent(this, ClassWidget.class).setAction(ClassWidget.ACTION_PINNED)
                .putExtra(EXTRA_WIDGET, "class");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(this, 74, cb, flags);
    }

    private void showReady() {
        status.setText("今天剩下的课一门一条，正在上的有倒计时；拉成 2×2 也能看。");
        steps.setVisibility(View.GONE);
        primary.setText("添加到桌面");
        primary.setEnabled(true);
        primary.setOnClickListener(v -> ask());
        secondary.setVisibility(View.GONE);
    }

    private void showWaiting() {
        status.setText("正在请桌面放置…在弹出的窗口里点「添加」就好。");
        steps.setVisibility(View.GONE);
        primary.setText("等待桌面…");
        primary.setEnabled(false);
        secondary.setVisibility(View.GONE);
    }

    /** 帮助状态：noPopup = 桌面根本没弹窗；否则是弹过窗口但没放上。 */
    private void showHelp(boolean noPopup) {
        asking = false;
        pausedWhileAsking = false;
        status.setText(noPopup
                ? "系统没弹出添加窗口。vivo、小米、OPPO、华为等手机要先给烫梨开「桌面快捷方式」权限，开好回来会自动再试。"
                : "还没添加上。如果是点了取消，再试一次就好；窗口没出现的话，先去开「桌面快捷方式」权限。");
        steps.setText("也可以手动添加：\n1. 长按桌面空白处\n2. 点「小组件」（vivo 叫「原子组件」）\n3. 搜「烫梨」\n4. 选「今日课程」拖到桌面");
        steps.setVisibility(View.VISIBLE);
        primary.setText("去开启权限");
        primary.setEnabled(true);
        primary.setOnClickListener(v -> openPermission());
        secondary.setText("再试一次");
        secondary.setVisibility(View.VISIBLE);
        secondary.setOnClickListener(v -> ask());
    }

    private void showUnsupported() {
        asking = false;
        status.setText("这个桌面不支持一键添加，手动加一下：");
        steps.setText("1. 长按桌面空白处\n2. 点「小组件」（vivo 叫「原子组件」）\n3. 搜「烫梨」\n4. 选「今日课程」拖到桌面");
        steps.setVisibility(View.VISIBLE);
        primary.setText("知道了");
        primary.setEnabled(true);
        primary.setOnClickListener(v -> finish());
        secondary.setText("去开启权限");
        secondary.setVisibility(View.VISIBLE);
        secondary.setOnClickListener(v -> openPermission());
    }

    private void showDone() {
        if (done || isFinishing()) return;
        done = true;
        asking = false;
        ui.removeCallbacks(timeout);
        ui.removeCallbacks(afterReturn);
        status.setText("已添加，回桌面看看吧。");
        steps.setVisibility(View.GONE);
        findViewById(R.id.btn_pin_try).setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
        primary.setText("已添加");
        primary.setEnabled(false);
        ClassWidget.updateAll(this);
        ui.postDelayed(this::finish, 1100);
    }

    private final Runnable timeout = () -> {
        if (done || !asking) return;
        if (placedMore()) { showDone(); return; }
        if (!pausedWhileAsking) showHelp(true); // 3 秒内既没回调、页面也没被盖住 → 桌面没弹窗
    };

    private final Runnable afterReturn = () -> {
        if (done) return;
        if (placedMore()) showDone();
        else showHelp(false);
    };

    private int placed() {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            return ClassWidget.ids(this, m, ClassWidget.class).length;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 有的桌面放好了却不回调：数一下这种组件是不是多了一个。 */
    private boolean placedMore() {
        return placed() > countBefore;
    }

    /** 用 Activity 自己（前台）请桌面放置。 */
    private void ask() {
        if (done || isFinishing()) return;
        if (manualLauncher) { showManual(); return; }
        ui.removeCallbacks(timeout);
        ui.removeCallbacks(afterReturn);
        boolean sent = false;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                AppWidgetManager m = AppWidgetManager.getInstance(this);
                if (m != null && m.isRequestPinAppWidgetSupported()) {
                    countBefore = placed();
                    PendingIntent ok = pinCallback();
                    Class<?> provider = ClassWidget.class;
                    asking = true;
                    pausedWhileAsking = false;
                    sent = m.requestPinAppWidget(new ComponentName(this, provider), null, ok);
                }
            } catch (Exception e) {
                sent = false;
            }
        }
        if (!sent) {
            asking = false;
            if (Build.VERSION.SDK_INT >= 26) showHelp(true);
            else showUnsupported();
            return;
        }
        showWaiting();
        ui.postDelayed(timeout, WAIT_MS);
    }

    /** 打开各家系统的「桌面快捷方式」权限页，都打不开就去应用详情页。 */
    private void openPermission() {
        String pkg = getPackageName();
        String brand = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(java.util.Locale.ROOT);
        List<Intent> tries = new ArrayList<>();
        if (brand.contains("vivo") || brand.contains("iqoo")) {
            tries.add(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    .setAction("secure.intent.action.softPermissionDetail").putExtra("packagename", pkg));
            tries.add(comp("com.iqoo.secure", "com.iqoo.secure.safeguard.SoftPermissionDetailActivity").putExtra("packagename", pkg));
            tries.add(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity").putExtra("packagename", pkg));
        } else if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            tries.add(comp("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .setAction("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg));
            tries.add(comp("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                    .setAction("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg));
            tries.add(new Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg));
        } else if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")) {
            tries.add(comp("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionManagerActivity").putExtra("packageName", pkg));
            tries.add(comp("com.oplus.safecenter", "com.oplus.safecenter.permission.PermissionManagerActivity").putExtra("packageName", pkg));
            tries.add(comp("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"));
        } else if (brand.contains("huawei") || brand.contains("honor")) {
            tries.add(comp("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.SingleAppActivity").putExtra("packageName", pkg));
            tries.add(comp("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity").putExtra("packageName", pkg));
            tries.add(comp("com.hihonor.systemmanager", "com.hihonor.permissionmanager.ui.MainActivity").putExtra("packageName", pkg));
        }
        tries.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
        for (Intent i : tries) {
            try {
                startActivity(i);
                fromSettings = true;
                android.widget.Toast.makeText(this, "找到「桌面快捷方式」打开，再回到烫梨", android.widget.Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {
                // 这家没有这个页面，或不让打开，试下一个
            }
        }
        android.widget.Toast.makeText(this, "打不开权限页，去 设置 → 应用 → 烫梨 → 权限 里找「桌面快捷方式」", android.widget.Toast.LENGTH_LONG).show();
    }

    private static Intent comp(String pkg, String cls) {
        return new Intent().setComponent(new ComponentName(pkg, cls));
    }
}
