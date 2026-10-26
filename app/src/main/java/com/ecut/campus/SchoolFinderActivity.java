package com.ecut.campus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.campus.data.SchoolProfile;

/**
 * 「帮我找」：在网上搜「XX大学 教务系统」，同学点进自己学校的教务登录页后，
 * 按底部「就用这个网址」把当前网址带回去。这里只浏览，不登录、不读页面内容。
 */
public class SchoolFinderActivity extends AppCompatActivity {
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_URL = "url";

    private WebView web;
    private TextView urlTv;
    private ProgressBar bar;

    /** 搜索词 → 必应搜索页（国内可直接打开，广告比别家少）。 */
    static String searchUrl(String school) {
        String q = (school == null ? "" : school.trim()) + " 教务系统 登录";
        return "https://cn.bing.com/search?q=" + Uri.encode(q.trim());
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        String query = getIntent().getStringExtra(EXTRA_QUERY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(G.PAPER);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        TextView close = new TextView(this);
        close.setText("‹ 返回");
        close.setTextSize(13f);
        close.setTypeface(Typeface.MONOSPACE);
        close.setTextColor(G.SUB);
        close.setGravity(Gravity.CENTER_VERTICAL);
        close.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        close.setBackgroundResource(Ui.selectable(this, true));
        close.setOnClickListener(v -> finish());
        head.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("找到你们学校的教务登录页");
        title.setTextSize(15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(G.INK);
        titles.addView(title);
        urlTv = new TextView(this);
        urlTv.setTextSize(11f);
        urlTv.setTypeface(Typeface.MONOSPACE);
        urlTv.setTextColor(G.MUTE);
        urlTv.setSingleLine(true);
        urlTv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titles.addView(urlTv);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        foot.setBackgroundColor(G.CARD);
        TextView tip = new TextView(this);
        tip.setText("点进学校的教务系统，看到输入学号密码的登录页时，按下面的按钮。不用在这里登录。");
        tip.setTextSize(12f);
        tip.setTextColor(G.SUB);
        foot.addView(tip);
        TextView use = Ui.button(this, "就用这个网址", true, v -> confirm());
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        ul.topMargin = Ui.dp(this, 8);
        foot.addView(use, ul);
        root.addView(foot);

        setContentView(root);
        getWindow().setStatusBarColor(G.PAPER);
        getWindow().setNavigationBarColor(G.CARD);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                // 只在这里浏览网页，拦掉唤起其他 App 的链接（baiduboxapp:// 之类）
                return scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                urlTv.setText(url);
                bar.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                urlTv.setText(url);
                bar.setVisibility(android.view.View.GONE);
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        web.loadUrl(searchUrl(query));
    }

    private void confirm() {
        String url = web.getUrl();
        if (url == null || isSearchPage(url)) {
            Toast.makeText(this, "先点进你们学校的教务网站，再按这个按钮", Toast.LENGTH_LONG).show();
            return;
        }
        SchoolProfile guess = SchoolProfile.custom(url, null);
        String kind = guess == null ? "" : guess.vendor == SchoolProfile.Vendor.OTHER
                ? "暂时看不出是哪家的系统，登录后烫梨会再认一次。"
                : "看起来是" + guess.vendor.label + "教务。";
        new AlertDialog.Builder(this)
                .setTitle("用这个网址？")
                .setMessage(url + "\n\n" + kind)
                .setNegativeButton("再找找", null)
                .setPositiveButton("就用它", (d, w) -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_URL, url));
                    finish();
                })
                .show();
    }

    /** 还停在搜索结果页（没点进学校网站）。 */
    static boolean isSearchPage(String url) {
        String h = SchoolProfile.hostOf(url).toLowerCase(java.util.Locale.ROOT);
        return h.endsWith("bing.com") || h.endsWith("baidu.com") || h.endsWith("sogou.com")
                || h.endsWith("so.com") || h.endsWith("google.com");
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
