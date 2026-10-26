package com.ecut.campus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.JwglxtFetcher;
import com.ecut.campus.data.PageSample;
import com.ecut.campus.data.SchoolProfile;
import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.SyncDiff;
import com.ecut.campus.data.TermStartProbe;
import com.ecut.campus.data.QiangzhiGrades;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableSource;
import com.ecut.campus.data.TimetableSources;
import com.ecut.campus.data.TimetableStore;
import com.ecut.campus.data.ZhengfangSource;

import org.json.JSONTokener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教务 WebView 壳（带正方教务移动端自适应 CSS 注入与外链净化）。
 */
public class WebShellActivity extends AppCompatActivity {

    private static final String TAG = "WebShell";
    public static final String EXTRA_TARGET_KEY = "target_key";
    public static final String EXTRA_RAW_PATH = "raw_path";
    public static final String EXTRA_TITLE = "title";
    /** 可选：进入页面后在底部浮窗显示的提示。 */
    public static final String EXTRA_HINT = "hint";

    private static final String DEFAULT_PORTAL =
            "https://atrust1.ecut.edu.cn/portal/#/app_center";
    private static final String EHALL_HOME =
            "https://ehall.ecut.edu.cn/new/index.html";

    private WebView webView;
    private ProgressBar progress;
    private TextView webTitle;
    private MaterialButton toggleFitBtn;

    private String title = "教务";
    private String targetPath;
    private boolean autoOpenTimetable = false;
    private boolean mobileFitEnabled = true;

    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_FILE = 3001;
    private int retryCount = 0;
    private boolean pendingJump = false;
    private String pendingDirectUrl;
    private boolean syncedTimetableOnce = false;
    private boolean syncedGradesOnce = false;
    private boolean openingEhallTarget = false;
    /** 当前学校。东华理工保持原有的门户 / 办事大厅流程。 */
    private SchoolProfile school;
    private boolean importing = false;

    /** 匹配 aTrust 代理主机：172-20-130-13.atrust.ecut.edu.cn */
    private static final Pattern PROXY_HOST = Pattern.compile(
            "([0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}\\.atrust\\.ecut\\.edu\\.cn)");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_web);

        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        webTitle = findViewById(R.id.web_title);
        toggleFitBtn = findViewById(R.id.btn_toggle_fit);
        school = Schools.current(this);

        resolveTarget();
        if (webTitle != null) webTitle.setText(title);
        if (getIntent().hasExtra(EXTRA_TITLE) && toggleFitBtn != null) {
            toggleFitBtn.setVisibility(View.GONE); // 非教务页面不需要手机适配开关
        }
        String hint = getIntent().getStringExtra(EXTRA_HINT);
        View hintBox = findViewById(R.id.web_hint);
        if (hint != null && !hint.isEmpty() && hintBox != null) {
            ((android.widget.TextView) findViewById(R.id.web_hint_text)).setText(hint);
            hintBox.setVisibility(View.VISIBLE);
            hintBox.setTranslationY(dpPx(120));
            hintBox.animate().translationY(0f).setStartDelay(400).setDuration(320)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)).start();
            findViewById(R.id.web_hint_close).setOnClickListener(v ->
                    hintBox.animate().translationY(hintBox.getHeight() + dpPx(32)).alpha(0f)
                            .setStartDelay(0).setDuration(220)
                            .withEndAction(() -> hintBox.setVisibility(View.GONE)).start());
        }

        View backBtn = findViewById(R.id.btn_web_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
        View refreshBtn = findViewById(R.id.btn_web_refresh);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> {
                if (webView != null) webView.reload();
            });
        }
        if (toggleFitBtn != null) {
            toggleFitBtn.setOnClickListener(v -> {
                mobileFitEnabled = !mobileFitEnabled;
                toggleFitBtn.setText(mobileFitEnabled ? "手机适配" : "原版网页");
                if (webView != null) webView.reload();
            });
        }

        View importBtn = findViewById(R.id.btn_import_page);
        if (importBtn != null) {
            // 其他学校没有静默同步：教务页底部常驻「导入此页课表」
            boolean show = !school.isEcut() && !getIntent().hasExtra(EXTRA_TITLE);
            importBtn.setVisibility(show ? View.VISIBLE : View.GONE);
            importBtn.setOnClickListener(v -> importThisPage());
        }

        configureWebView();
        route();
    }

    private void resolveTarget() {
        String rawPath = getIntent().getStringExtra(EXTRA_RAW_PATH);
        if (rawPath != null && !rawPath.trim().isEmpty()) {
            targetPath = rawPath.trim();
            String t = getIntent().getStringExtra(EXTRA_TITLE);
            title = (t != null && !t.isEmpty()) ? t : "路径测试";
            return;
        }
        String key = getIntent().getStringExtra(EXTRA_TARGET_KEY);
        if (!school.isEcut()) {
            resolveOtherSchoolTarget(key);
            return;
        }
        autoOpenTimetable = "timetable".equals(key);
        List<TargetStore.Target> all = TargetStore.load(this);
        TargetStore.Target found = null;
        for (TargetStore.Target t : all) {
            if (t.key.equals(key)) { found = t; break; }
        }
        if (found == null && !all.isEmpty()) found = all.get(0);
        if (found != null) {
            targetPath = found.path;
            title = found.title;
        } else {
            targetPath = DEFAULT_PORTAL;
            title = "应用中心";
        }
    }

    /** 其他学校：课表 / 成绩直达本校教务，其余入口打开登录页。 */
    private void resolveOtherSchoolTarget(String key) {
        autoOpenTimetable = false;
        title = school.name == null || school.name.isEmpty() ? "教务" : school.name;
        TimetableSource src = Schools.source(school);
        String url = null;
        if ("timetable".equals(key) && src != null) url = src.timetableUrl(school);
        if ("grades".equals(key) && school.supportsGrades()) {
            url = school.vendor == SchoolProfile.Vendor.QIANGZHI
                    ? QiangzhiGrades.gradesUrl(school) : ZhengfangSource.gradesUrl(school);
        }
        if (url == null || !Prefs.hasPortalSession(this)) url = school.loginUrl;
        if (url == null || url.isEmpty()) url = school.jwBase;
        targetPath = url;
    }

    private boolean isJwglxtLoginPage(String url) {
        return url != null
                && url.contains("/jwglxt/")
                && (url.contains("login_slogin") || url.contains("/login"));
    }

    private boolean isJwglxtPage(String url) {
        return url != null && url.contains("/jwglxt/") && !isJwglxtLoginPage(url);
    }

    /**
     * 办事大厅的卡片可能下发裸校内 IP（http://172.20.130.13/...）。
     * 该地址在当前网络不可直连，但 aTrust 已为同一节点提供代理域名。
     * 保留原始 path/query，只替换 scheme + host，避免丢失 SSO 参数。
     */
    static String rewriteBareCampusHost(String url) {
        return CampusUrlPolicy.rewriteBareCampusHost(url);
    }

    private boolean isAbsoluteTarget() {
        return targetPath != null
                && (targetPath.startsWith("http://") || targetPath.startsWith("https://"));
    }

    /** 只允许校内门户与正方教务相关域名，阻断无关外链 */
    private boolean isAllowedCampusUrl(String url) {
        return CampusUrlPolicy.isAllowedFor(school, url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(0);
                if (!school.isEcut()) return;
                maybeCaptureHost(url);
                if (isJwglxtLoginPage(url)) {
                    Prefs.setPortalSession(WebShellActivity.this, false);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                Log.i(TAG, "PAGE URL: " + url);
                if (!school.isEcut()) {
                    onOtherSchoolPageFinished(view, url);
                    return;
                }
                if (isJwglxtPage(url)) {
                    applyMobileAdaptation(view);
                    rememberPortalSession(url);
                    if (url.contains("xskbcx") || url.contains("kbcx")) {
                        syncTimetableFromSession(view);
                    } else if (url.contains("cjcx")) {
                        syncGradesFromDom(view);
                    }
                }
                if (isJwglxtLoginPage(url)) {
                    Log.w(TAG, "jwglxt login page opened, session is missing");
                    Prefs.setPortalSession(WebShellActivity.this, false);
                    if (autoOpenTimetable) {
                        Toast.makeText(WebShellActivity.this,
                                "统一身份会话已失效，正在返回办事大厅", Toast.LENGTH_LONG).show();
                        Prefs.clearPortalHost(WebShellActivity.this);
                        pendingJump = false;
                        pendingDirectUrl = null;
                        webView.postDelayed(() -> webView.loadUrl(EHALL_HOME), 500);
                    }
                }
                if (pendingDirectUrl != null && url != null) {
                    pendingDirectUrl = null;
                    if (isJwglxtPage(url)) {
                        Log.i(TAG, "direct ok: " + url);
                    } else {
                        Log.w(TAG, "direct bounced, clearing host: " + url);
                        Prefs.clearPortalHost(WebShellActivity.this);
                        webView.loadUrl(DEFAULT_PORTAL);
                        pendingJump = true;
                        return;
                    }
                }
                if (url != null && url.contains("/portal/")) {
                    scrapeAppCenterUrls();
                }
                if (url != null && url.contains("ehall.ecut.edu.cn")
                        && !url.contains("/jwglxt/")) {
                    rememberEhallSession(view, autoOpenTimetable);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = rewriteBareCampusHost(request.getUrl().toString());
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return true; // 屏蔽第三方 App 唤起协议
                }
                if (!isAllowedCampusUrl(url)) {
                    Toast.makeText(WebShellActivity.this, "已拦截非教务外链", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (school.isEcut()) maybeCaptureHost(url);
                if (!url.equals(request.getUrl().toString())) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    Log.w(TAG, "main frame error: " + error.getDescription());
                    retryOnce();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                if (newProgress >= 100) progress.setVisibility(View.GONE);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                // 在同一 WebView 内打开新窗口链接，保证返回键与手机适配生效
                WebView temp = new WebView(WebShellActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        String u = req.getUrl().toString();
                        if (isAllowedCampusUrl(u)) {
                            maybeCaptureHost(u);
                            webView.loadUrl(u);
                        }
                        v.destroy();
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String u, Bitmap f) {
                        if (isAllowedCampusUrl(u)) {
                            maybeCaptureHost(u);
                            webView.loadUrl(u);
                        }
                        v.destroy();
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * 从正方教务成绩页（jqGrid tabGrid DOM）提取成绩并落盘。
     * 先将学年/学期设为空（全部）且每页条数调大，触发查询后提取全部 DOM 行，
     * 同时保留页面内查询按钮监听，用户切换学年再次点击查询时也会自动增量合并到本地。
     */
    private void syncGradesFromDom(WebView view) {
        String trigger = "(function(){" +
                "try{" +
                "window.__ecutReadGrades=function(){};" +
                "var y=document.getElementById('xnm');var t=document.getElementById('xqm');" +
                "if(y && !window.__ecutGradeInited){y.value='';}" +
                "if(t && !window.__ecutGradeInited){t.value='';}" +
                "var pg=document.querySelector('select.ui-pg-selbox');" +
                "if(pg){pg.value='500';}" +
                "var btn=document.getElementById('search_go');" +
                "if(btn && !window.__ecutGradeInited){" +
                "window.__ecutGradeInited=true;" +
                "btn.click();return 'CLICKED';}" +
                "}catch(e){return 'ERR:'+e;}" +
                "return 'READY';})()";
        view.evaluateJavascript(trigger, r -> {
            Log.i(TAG, "GRADE_TRIGGER: " + r);
            view.postDelayed(() -> readGradeDomOnce(view), 3200);
            view.postDelayed(() -> readGradeDomOnce(view), 6500);
        });
    }

    private void readGradeDomOnce(WebView view) {
        String js = "(function(){" +
                "var t=document.getElementById('tabGrid');" +
                "if(!t||!t.rows||t.rows.length<=1)return '';" +
                "var rows=[];" +
                "for(var i=0;i<t.rows.length;i++){" +
                "var row=[];var cs=t.rows[i].cells;" +
                "for(var c=0;c<cs.length;c++){row.push((cs[c].innerText||'').trim());}" +
                "rows.push(row);}" +
                "return JSON.stringify({rows:rows});})()";
        view.evaluateJavascript(js, raw -> {
            String decoded = unwrapJsString(raw);
            if (decoded == null || decoded.isEmpty()) return;
            Grades parsed = Grades.parseFromDomRows(decoded);
            int count = parsed.totalCourseCount();
            if (count > 0) {
                Grades local = GradesStore.load(WebShellActivity.this);
                Grades merged = GradesStore.merge(local, parsed);
                if (GradesStore.save(WebShellActivity.this, merged)) {
                    Prefs.setGradesDemo(WebShellActivity.this, false);
                    String now = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
                    Prefs.setLastSyncTime(WebShellActivity.this, now);
                    Log.i(TAG, "Grades saved: " + merged.totalCourseCount() + " courses");
                    if (!syncedGradesOnce) {
                        syncedGradesOnce = true;
                        Toast.makeText(WebShellActivity.this,
                                "已同步 " + merged.totalCourseCount() + " 门成绩到本地，离线可秒开",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    /**
     * 在 WebView 会话内请求正方课表 JSON 接口（自动带 aTrust 与 JSESSIONID Cookie），
     * 解析 kbList 并合并写入本地 timetable.json。
     */
    private void syncTimetableFromSession(WebView view) {
        String js = "(function(){" +
                "try{" +
                "var yEl=document.getElementById('xnm');" +
                "var tEl=document.getElementById('xqm');" +
                "var xnm=(yEl&&yEl.value)?yEl.value:'2026';" +
                "var xqm=(tEl&&tEl.value)?tEl.value:'3';" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('POST','/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151',false);" +
                "xhr.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
                "xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "xhr.send('xnm='+encodeURIComponent(xnm)+'&xqm='+encodeURIComponent(xqm)+'&kzlx=ck');" +
                "return xhr.responseText||'';" +
                "}catch(e){return '';}" +
                "})()";
        view.evaluateJavascript(js, raw -> {
            String json = unwrapJsString(raw);
            if (json == null || json.isEmpty()) return;
            JwglxtFetcher.Result res = JwglxtFetcher.parse(json);
            DiagLog.saveKbList(WebShellActivity.this, json);
            DiagLog.log(WebShellActivity.this, "webshell", "课表解析: 学期=" + res.timetable.semester.id
                    + " 原始=" + res.rawRecords + " 课程=" + res.courseCount
                    + " 合并重复=" + res.mergedDuplicates
                    + (res.warnings.isEmpty() ? "" : " " + res.warnings));
            if (res.ok()) {
                Timetable local = TimetableStore.load(WebShellActivity.this);
                Timetable merged = TimetableStore.merge(local, res.timetable);
                if (TimetableStore.save(WebShellActivity.this, merged)) {
                    String now = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
                    Prefs.setLastSyncTime(WebShellActivity.this, now);
                    Log.i(TAG, "Timetable saved: " + merged.courses.size() + " courses (" + res.rawRecords + " records)");
                    if (!syncedTimetableOnce) {
                        syncedTimetableOnce = true;
                        Toast.makeText(WebShellActivity.this,
                                "课表同步成功：共 " + merged.courses.size() + " 门课已保存至本地",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.w(TAG, "Timetable parse empty or warning: " + res.warnings);
            }
        });
    }

    // ==================== 其他学校：导入此页课表 ====================

    private void onOtherSchoolPageFinished(WebView view, String url) {
        TimetableSource src = Schools.source(school);
        if (src == null || url == null) return;
        if (src.isLoggedInPage(url)) {
            Prefs.setPortalSession(this, true);
            Prefs.setSessionExpired(this, false);
            CookieManager.getInstance().flush();
            if (school.vendor == SchoolProfile.Vendor.ZHENGFANG) applyMobileAdaptation(view);
        }
    }

    /** 认出当前页面是哪家教务 → 取课表 → 预览确认后写入。 */
    private void importThisPage() {
        if (webView == null || importing) return;
        importing = true;
        String js = "(function(){try{" +
                "var h=document.documentElement?document.documentElement.outerHTML:'';h=h.substring(0,200000);" +
                "for(var f=0;f<window.frames.length;f++){try{var d=window.frames[f].document;" +
                "h+='\\n'+d.documentElement.outerHTML.substring(0,200000);}catch(e){}}" +
                "return h;}catch(e){return '';}})()";
        webView.evaluateJavascript(js, raw -> {
            if (webView == null) { importing = false; return; }
            String html = unwrapJsString(raw);
            String url = webView.getUrl();
            TimetableSource src = TimetableSources.detect(url, html);
            if (src == null && school.allowsUrl(url)) src = Schools.source(school);
            DiagLog.log(this, "import", "导入此页: 识别=" + (src == null ? "无" : src.vendor().key())
                    + " · " + school.diagSummary() + " · path=" + pathOnly(url));
            if (src == null) {
                importing = false;
                showUnsupported(null);
                return;
            }
            runImport(src, url);
        });
    }

    private void runImport(TimetableSource src, String pageUrl) {
        Toast.makeText(this, "正在读取" + src.vendor().label + "教务的课表…", Toast.LENGTH_SHORT).show();
        webView.evaluateJavascript(src.extractionJs(school, null, null), raw -> {
            importing = false;
            if (webView == null || isFinishing()) return;
            String text = unwrapJsString(raw);
            JwglxtFetcher.Result res = (text == null || text.isEmpty() || text.startsWith("ERR:"))
                    ? new JwglxtFetcher.Result() : src.parse(text);
            if (src.vendor() == SchoolProfile.Vendor.ZHENGFANG && res.ok()) {
                DiagLog.saveKbList(this, text);
            }
            DiagLog.log(this, "import", "解析(" + src.vendor().key() + "): 学期=" + res.timetable.semester.id
                    + " 原始=" + res.rawRecords + " 课程=" + res.courseCount
                    + " 周次失败=" + res.failedWeekExpr
                    + (res.warnings.isEmpty() ? "" : " " + res.warnings));
            if (!res.ok()) {
                showUnsupported("认出这是" + src.vendor().label + "教务，但这一页没有读到课表。\n\n"
                        + "请先在教务里打开「我的课表」（学期课表）页面，再点「导入此页课表」。");
                return;
            }
            probeTermStart(src, res, () -> showImportPreview(src, res, pageUrl));
        });
    }

    /** 非东华理工、还没开学日期时，从教务推出第 1 周周一（强智教学周历 / 页面「当前第 N 周」）。 */
    private void probeTermStart(TimetableSource src, JwglxtFetcher.Result res, Runnable next) {
        String have = res.timetable.semester.startDate;
        if (school.isEcut() || (have != null && !have.isEmpty())) { next.run(); return; }
        webView.evaluateJavascript(TermStartProbe.probeJs(src.vendor(), res.timetable.semester.id), raw -> {
            if (webView == null || isFinishing()) return;
            String monday = TermStartProbe.detect(unwrapJsString(raw), WeekMath.todayDateLabel());
            DiagLog.log(this, "import", "开学日探测: " + (monday.isEmpty() ? "没找到" : monday));
            if (!monday.isEmpty()) res.timetable.semester.startDate = monday;
            next.run();
        });
    }

    private void showImportPreview(TimetableSource src, JwglxtFetcher.Result res, String pageUrl) {
        Timetable current = TimetableStore.load(this);
        String semId = res.timetable.semester.id;
        // 页面上是别的学期（不是当前学期）：合并进归档里的那个学期，不能写进当前学期。
        boolean otherSemester = semId != null && !semId.isEmpty()
                && current.semester.id != null && !current.semester.id.isEmpty()
                && !semId.equals(current.semester.id);
        Timetable archived = otherSemester ? SemesterArchive.loadSemester(this, semId) : null;
        Timetable before = otherSemester ? (archived != null ? archived : new Timetable()) : current;
        Timetable merged = TimetableStore.merge(before, res.timetable);
        String diff = SyncDiff.summarize(before, merged);
        StringBuilder msg = new StringBuilder();
        msg.append("识别为：").append(src.vendor().label).append("教务\n");
        String semName = res.timetable.semester.name;
        msg.append("学期：").append(semName == null || semName.isEmpty() ? "没认出来（沿用当前学期）" : semName).append('\n');
        msg.append("共 ").append(res.courseCount).append(" 门课：");
        int shown = 0;
        for (Timetable.Course c : res.timetable.courses) {
            if (shown == 12) { msg.append(" 等"); break; }
            if (shown > 0) msg.append("、");
            msg.append(c.name);
            shown++;
        }
        if (res.failedWeekExpr > 0) {
            msg.append("\n\n有 ").append(res.failedWeekExpr).append(" 处上课周没认出来，导入后请核对。");
        }
        if (diff != null && !diff.isEmpty()) msg.append("\n\n和现在的课表相比：\n").append(diff);
        String start = merged.semester.startDate;
        if (!school.isEcut()) {
            msg.append("\n\n第 1 周周一：").append(start == null || start.isEmpty()
                    ? "没从教务读到，导入后请在课表设置里选一下" : start);
        }
        if (otherSemester) msg.append("\n\n这不是当前学期，会存到历史学期里，当前课表不变。");
        msg.append("\n\n导入会替换之前从教务导入的课程，你自己添加的课程会保留。");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("导入这份课表？")
                .setMessage(msg.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", (d, w) -> {
                    boolean saved = otherSemester
                            ? SemesterArchive.saveSemester(this, merged, false)
                            : TimetableStore.save(this, merged);
                    if (!saved) {
                        Toast.makeText(this, "课表写入本地失败", Toast.LENGTH_LONG).show();
                        return;
                    }
                    SemesterArchive.unifyColors(this);
                    Prefs.setLastSyncTime(this, new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date()));
                    Prefs.setTimetableSyncedAt(this, System.currentTimeMillis());
                    Prefs.setPortalSession(this, true);
                    Prefs.setSessionExpired(this, false);
                    rememberDetectedVendor(src, pageUrl);
                    DiagLog.log(this, "import", "已导入 " + merged.courses.size() + " 门");
                    Toast.makeText(this, "已导入 " + res.courseCount + " 门课", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 自定义学校原本没认出厂商：导入成功后记下来，以后就能后台同步。 */
    private void rememberDetectedVendor(TimetableSource src, String pageUrl) {
        if (!school.isCustom() || school.vendor != SchoolProfile.Vendor.OTHER) return;
        SchoolProfile upgraded = SchoolProfile.custom(pageUrl, src.vendor());
        if (upgraded == null) return;
        upgraded.loginUrl = school.loginUrl;
        upgraded.name = school.name;
        for (String h : school.hosts) if (!upgraded.hosts.contains(h)) upgraded.hosts.add(h);
        Prefs.setCustomSchoolJson(this, upgraded.toJson().toString());
        school = upgraded;
        DiagLog.log(this, "school", "自定义学校识别为 " + upgraded.diagSummary());
    }

    private void showUnsupported(String detail) {
        if (isFinishing()) return;
        String msg = (detail != null ? detail + "\n\n" : "烫梨还读不了这个页面的课表。\n\n")
                + "可以导出一份「页面结构样本」帮忙适配你的学校：只记网页用了哪些标签和字段名，"
                + "不含姓名、学号等文字，会附在诊断日志里一起分享。";
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(detail != null ? "没读到课表" : "暂不支持这个教务系统")
                .setMessage(msg)
                .setNegativeButton("关闭", null)
                .setPositiveButton("导出结构样本", (d, w) -> exportPageSample())
                .show();
    }

    private void exportPageSample() {
        if (webView == null) return;
        webView.evaluateJavascript(PageSample.collectJs(), raw -> {
            String sample = PageSample.sanitize(unwrapJsString(raw));
            if (sample.isEmpty()) {
                Toast.makeText(this, "没能读取页面结构", Toast.LENGTH_SHORT).show();
                return;
            }
            DiagLog.savePageSample(this, sample);
            DiagLog.log(this, "import", "已保存页面结构样本 " + sample.length() + " 字 · " + school.diagSummary());
            DiagLog.export(this);
        });
    }

    /** 只留路径，不带查询参数（可能有 ticket）。 */
    static String pathOnly(String url) {
        if (url == null) return "";
        try {
            String p = java.net.URI.create(url).getRawPath();
            return p == null ? "" : p;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 将 evaluateJavascript 返回的 JSON 编码字符串还原为原始字符串 */
    static String unwrapJsString(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "null".equals(trimmed) || "\"\"".equals(trimmed)) return "";
        try {
            Object val = new JSONTokener(trimmed).nextValue();
            return val != null ? val.toString() : "";
        } catch (Exception e) {
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                return trimmed.substring(1, trimmed.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
            return trimmed;
        }
    }

    /** 诊断：先触发查询，延迟后 dump 课表 DOM */
    private void dumpTimetableDom(WebView view) {
        String trigger = "(function(){" +
                "var btns=document.querySelectorAll('button,a,input[type=button]');" +
                "for(var i=0;i<btns.length;i++){var t=(btns[i].innerText||btns[i].value||'').trim();" +
                "if(t==='查询'||t==='查 询'){btns[i].click();return 'CLICKED:'+t;}}" +
                "return 'NO_BUTTON';})()";
        view.evaluateJavascript(trigger, r -> {
            Log.i(TAG, "QUERY_TRIGGER: " + r);
            view.postDelayed(() -> {
                String dump = "(function(){" +
                        "var out={};" +
                        "var names=['ylkbTable','table1','table2'];" +
                        "for(var ni=0;ni<names.length;ni++){" +
                        "var el=document.getElementById(names[ni]);" +
                        "if(!el){out[names[ni]]='NOT_FOUND';continue;}" +
                        "var o={tag:el.tagName,cls:(el.className||'').substring(0,50)," +
                        "childCount:el.children.length,textLen:(el.innerText||'').length};" +
                        "var tags={};var kids=el.querySelectorAll('*');" +
                        "for(var i=0;i<kids.length&&i<600;i++){var t=kids[i].tagName;tags[t]=(tags[t]||0)+1;}" +
                        "o.tagHistogram=tags;" +
                        "var rs=el.querySelectorAll('td[rowspan],div[rowspan]');" +
                        "o.rowspanCount=rs.length;" +
                        "if(rs.length>0){var b=rs[0];" +
                        "o.firstRowspan={tag:b.tagName,cls:(b.className||'').substring(0,50)," +
                        "rowspan:b.rowSpan,colspan:b.colSpan," +
                        "innerHTML:(b.innerHTML||'').substring(0,700)};}" +
                        "var td=el.querySelector('td');" +
                        "if(td){o.firstTd={cls:(td.className||'').substring(0,50)," +
                        "innerHTML:(td.innerHTML||'').substring(0,500)};}" +
                        "out[names[ni]]=o;}" +
                        "return JSON.stringify(out);})()";
                view.evaluateJavascript(dump, v -> Log.i(TAG, "DOM_DUMP: " + v));
            }, 6000);
        });
    }

    /**
     * 针对正方教务（/jwglxt/）注入移动端自适应 CSS 与外链/冗余栏清理脚本。
     */
    private void applyMobileAdaptation(WebView view) {
        if (!mobileFitEnabled) return;
        String js = "(function(){" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "m.content='width=device-width,initial-scale=1.0,maximum-scale=3.0,user-scalable=yes';" +
                "if(document.getElementById('ecut-mobile-css'))return;" +
                "var s=document.createElement('style');s.id='ecut-mobile-css';" +
                "s.textContent=`" +
                "body{background:#F5F4F0 !important;font-family:sans-serif !important;overflow-x:hidden !important;}" +
                ".navbar-inverse,header.navbar,#footerID,.footer,#btn_dy,#shuchuPDF,#btn_sort{display:none !important;}" +
                ".container,.container-fluid,.row,#yhgnPage,.panel,.panel-body{width:100% !important;min-width:0 !important;max-width:100vw !important;margin:0 !important;padding:6px !important;box-sizing:border-box !important;}" +
                ".sl_all_form,.search_box,.form-horizontal{background:#FBFAF7 !important;border-radius:16px !important;padding:10px 12px !important;margin-bottom:10px !important;border:1px solid #E3E1DB !important;box-shadow:none !important;}" +
                ".form-group{margin-bottom:8px !important;}" +
                ".form-control,select{border-radius:10px !important;border:1px solid #D2D0CA !important;height:36px !important;font-size:13px !important;}" +
                ".btn-primary,.btn-default{border-radius:10px !important;font-size:13px !important;padding:6px 14px !important;}" +
                ".btn-primary{background:#111111 !important;border-color:#111111 !important;color:#FFF !important;}" +
                "h4,.timetable_title,caption{font-size:16px !important;line-height:1.3 !important;text-align:center !important;white-space:normal !important;}" +
                ".pull-right{float:none !important;display:block !important;text-align:center !important;font-size:12px !important;}" +
                "#kbgrid_table_0,.timetable{width:100% !important;table-layout:fixed !important;border-collapse:collapse !important;background:#FFF !important;border-radius:12px !important;overflow:hidden !important;}" +
                "#kbgrid_table_0 th,#kbgrid_table_0 td,.timetable th,.timetable td{padding:3px 2px !important;font-size:11px !important;line-height:1.25 !important;word-break:break-word !important;overflow-wrap:anywhere !important;vertical-align:top !important;}" +
                "#kbgrid_table_0 th:nth-child(1),#kbgrid_table_0 td:nth-child(1),#kbgrid_table_0 th:nth-child(2),#kbgrid_table_0 td:nth-child(2){width:22px !important;text-align:center !important;vertical-align:middle !important;font-size:10px !important;}" +
                ".timetable_con{padding:4px 3px !important;margin:2px 0 !important;border-radius:4px !important;background:#FBFAF7 !important;border-left:2px solid #111111 !important;color:#111111 !important;text-align:left !important;font-size:10.5px !important;line-height:1.25 !important;}" +
                ".ui-jqgrid,.ui-jqgrid-view,.ui-jqgrid-hdiv,.ui-jqgrid-bdiv{width:100% !important;max-width:100vw !important;overflow-x:auto !important;-webkit-overflow-scrolling:touch !important;}" +
                "`;" +
                "document.head.appendChild(s);" +
                "})()";
        view.evaluateJavascript(js, null);
    }

    private void retryOnce() {
        if (retryCount >= 2) return;
        retryCount++;
        webView.postDelayed(() -> {
            String u = webView.getUrl();
            if (u != null) webView.loadUrl(u); else route();
        }, 800);
    }

    private void route() {
        if (isAbsoluteTarget()) {
            webView.loadUrl(targetPath);
            return;
        }
        if (autoOpenTimetable) {
            Log.i(TAG, "timetable sync starts from ehall");
            pendingJump = false;
            pendingDirectUrl = null;
            webView.loadUrl(EHALL_HOME);
            return;
        }
        String host = Prefs.portalHost(this);
        if (host == null) {
            webView.loadUrl(DEFAULT_PORTAL);
            pendingJump = true;
            return;
        }
        navigateToTarget(host);
    }

    private void navigateToTarget(String host) {
        String direct = "https://" + host + targetPath;
        pendingDirectUrl = direct;
        Log.i(TAG, "load direct -> " + direct);
        webView.loadUrl(direct);
    }

    private void maybeCaptureHost(String url) {
        if (url == null) return;
        Matcher m = PROXY_HOST.matcher(url);
        if (m.find()) {
            String host = m.group(1);
            if (!host.equals(Prefs.portalHost(this))) {
                Log.i(TAG, "captured proxy host: " + host);
                Prefs.setPortalHost(this, host);
            }
            if (pendingJump && !url.contains("/jwglxt/")) {
                pendingJump = false;
                Log.i(TAG, "host captured while pending, continue to target");
                navigateToTarget(host);
            } else if (pendingJump && url.contains("/jwglxt/")) {
                pendingJump = false;
            }
        }
    }

    private void rememberPortalSession(String url) {
        if (url == null || url.contains("login_slogin")) return;
        if (url.contains("/jwglxt/") && isJwglxtPage(url)) {
            Prefs.setPortalSession(this, true);
            CookieManager.getInstance().flush();
        }
    }

    private void scrapeAppCenterUrls() {
        String js = "(function(){var out=[];var as=document.querySelectorAll('a[href]');" +
                "for(var i=0;i<as.length;i++){out.push(as[i].href);}" +
                "return JSON.stringify(out);})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null) return;
            Matcher m = PROXY_HOST.matcher(value);
            String last = null;
            while (m.find()) last = m.group(1);
            if (last != null) {
                Prefs.setPortalHost(this, last);
                if (pendingJump) {
                    pendingJump = false;
                    navigateToTarget(last);
                }
            }
        });
    }

    /**
     * 办事大厅是异步渲染的 SPA，首次打开时卡片往往还没出现。
     * 轮询查找“学生课表查询(新)”并点击其可交互父节点，让办事大厅自己完成
     * 统一身份认证与 aTrust 代理跳转，避免绕过统一登录直接打开教务登录页。
     */
    private void openTimetableFromEhall(WebView view) {
        openEhallTarget(view, "学生课表查询(新)");
    }

    private void rememberEhallSession(WebView view, boolean openTimetable) {
        String js = "(function(){" +
                "var id=document.getElementById('analysisUserId');" +
                "var name=document.getElementById('ampHeaderToolUserName');" +
                "var tool=document.getElementById('ampHasLoginTool');" +
                "var uid=id?(id.value||''):'';" +
                "var text=name?((name.innerText||name.textContent||'').trim()):'';" +
                "var logged=(uid&&uid!=='guest')||(text&&text.length>0)||" +
                "(tool&&getComputedStyle(tool).display==='block');" +
                "return logged?'LOGIN':'GUEST';" +
                "})()";
        view.evaluateJavascript(js, raw -> {
            String state = unwrapJsString(raw);
            if (!"LOGIN".equals(state)) return;
            Prefs.setPortalSession(this, true);
            CookieManager.getInstance().flush();
            if (openTimetable && !openingEhallTarget) {
                openingEhallTarget = true;
                openTimetableFromEhall(view);
            }
        });
    }

    private void openEhallTarget(WebView view, String target) {
        String js = "(function(){" +
                "if(window.__tangliTimetableOpening)return 'ALREADY';" +
                "var target=" + jsQuote(target) + ";" +
                "function pick(){" +
                "var all=document.querySelectorAll('body *');" +
                "for(var i=0;i<all.length;i++){" +
                "var el=all[i];" +
                "if(el.children.length>0)continue;" +
                "var text=(el.innerText||el.textContent||'').replace(/\\s+/g,'').trim();" +
                "if(text!==target)continue;" +
                "var node=el;" +
                "for(var n=0;n<7&&node;n++,node=node.parentElement){" +
                "if(node.matches&&node.matches('a,button,[onclick],[role=button],.amp-app-item,.amp-app-card')){" +
                "window.__tangliTimetableOpening=true;node.click();return 'CLICKED';" +
                "}" +
                "var style=getComputedStyle(node);" +
                "if(style&&style.cursor==='pointer'){window.__tangliTimetableOpening=true;node.click();return 'CLICKED_POINTER';}" +
                "}" +
                "}" +
                "return 'WAIT';" +
                "}" +
                "var result=pick();" +
                "if(result==='WAIT'){" +
                "var tries=0;" +
                "var timer=setInterval(function(){" +
                "tries++;" +
                "var r=pick();" +
                "if(r!=='WAIT'||tries>=32)clearInterval(timer);" +
                "},250);" +
                "}" +
                "return result;" +
                "})()";
        view.evaluateJavascript(js, result -> Log.i(TAG, "EHALL_TARGET: " + result));
    }

    private static String jsQuote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        CookieManager.getInstance().flush();
        if (webView != null) {
            webView.loadUrl("about:blank");
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private float dpPx(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
