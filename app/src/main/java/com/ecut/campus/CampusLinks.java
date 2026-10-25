package com.ecut.campus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** 校园外部入口：邮箱开通页、微信公众号。 */
public final class CampusLinks {

    /** 校园邮箱开通/登录：先走统一身份认证，再落到邮箱自助页。 */
    public static final String MAIL_REGISTER_URL = "https://email.ecut.edu.cn/HP/HPHome";

    /** 图书馆空间预约 H5（座位/研讨间）。 */
    public static final String LIBRARY_BOOKING_URL = "https://libspace.ecut.edu.cn/h5/index.html#/mo/home";

    public static final String MAIL_HINT = "邮箱开通页需要校园网环境。打不开的话，连上校园 Wi-Fi 或开启 aTrust 后点右上角刷新。";

    /** 打开图书馆预约页（应用内）。 */
    public static void openLibrary(Context ctx) {
        Intent i = new Intent(ctx, WebShellActivity.class);
        i.putExtra(WebShellActivity.EXTRA_RAW_PATH, LIBRARY_BOOKING_URL);
        i.putExtra(WebShellActivity.EXTRA_TITLE, "预约图书馆");
        ctx.startActivity(i);
    }

    /** 打开校园邮箱开通页，底部浮窗提示校园网要求。 */
    public static void openMail(Context ctx) {
        Intent i = new Intent(ctx, WebShellActivity.class);
        i.putExtra(WebShellActivity.EXTRA_RAW_PATH, MAIL_REGISTER_URL);
        i.putExtra(WebShellActivity.EXTRA_TITLE, "校园邮箱");
        i.putExtra(WebShellActivity.EXTRA_HINT, MAIL_HINT);
        ctx.startActivity(i);
    }

    /** 图书馆服务号名称，用于在微信内搜索（备用）。 */
    public static final String LIBRARY_ACCOUNT = "东华理工大学图书馆";

    private static final String WECHAT_PKG = "com.tencent.mm";

    private CampusLinks() {}

    /**
     * 第三方 App 不能直接打开指定公众号主页（需要微信开放平台 AppID 审核）。
     * 退而求其次：复制公众号名 → 拉起微信，用户在搜索框粘贴即到。
     */
    public static void openLibraryInWechat(Context ctx) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("library", LIBRARY_ACCOUNT));

        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(WECHAT_PKG);
        if (launch == null) {
            Toast.makeText(ctx, "未安装微信，公众号名已复制", Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(launch);
        Toast.makeText(ctx, "已复制「" + LIBRARY_ACCOUNT + "」，在微信搜索里粘贴即可",
                Toast.LENGTH_LONG).show();
    }
}
