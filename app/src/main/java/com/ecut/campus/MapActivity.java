package com.ecut.campus;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 三级页：图片查看（校园地图 / 校历原图）。用 WebView 自带的双指缩放，
 * 不引第三方库。EXTRA_URL 为空时显示内置的校园地图。
 */
public class MapActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        G.init(this);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || url.isEmpty()) url = "file:///android_asset/campus_map.webp";
        if (title == null) title = "校园地图 · 南昌校区";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(G.PAPER);
        WebView web = new WebView(this);
        web.setBackgroundColor(G.PAPER);
        WebSettings s = web.getSettings();
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        String bg = String.format("#%06X", G.PAPER & 0xFFFFFF);
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1,"
                + "minimum-scale=1,maximum-scale=8,user-scalable=yes'><style>html,body{margin:0;background:" + bg
                + ";}img{width:100%;height:auto;display:block;padding-top:56px;}</style></head>"
                + "<body><img src='" + url + "'></body></html>";
        web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
        root.addView(web, Ui.match());

        TextView bar = new TextView(this);
        bar.setText("‹  " + title);
        bar.setTypeface(Typeface.MONOSPACE);
        bar.setTextSize(13f);
        bar.setTextColor(G.INK);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        bar.setBackgroundColor(G.alpha(G.PAPER, 0xEE));
        bar.setOnClickListener(v -> finish());
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        TextView hint = new TextView(this);
        hint.setText("双指缩放 · 拖动查看");
        hint.setTypeface(Typeface.MONOSPACE);
        hint.setTextSize(11f);
        hint.setTextColor(G.MUTE);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28), Gravity.BOTTOM);
        hint.setBackgroundColor(G.alpha(G.PAPER, 0xDD));
        root.addView(hint, hl);
        hint.postDelayed(() -> hint.animate().alpha(0f).setDuration(400).start(), 2500);

        setContentView(root);
        getWindow().setStatusBarColor(G.PAPER);
        getWindow().setNavigationBarColor(G.PAPER);
    }
}
