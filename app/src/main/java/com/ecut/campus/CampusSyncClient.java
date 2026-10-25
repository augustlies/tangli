package com.ecut.campus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ecut.campus.data.Grades;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.JwglxtFetcher;
import com.ecut.campus.data.SemesterArchive;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStore;
import com.ecut.campus.data.SyncDiff;

import org.json.JSONTokener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用与登录页共享 Cookie 的不可见 WebView，完成办事大厅内的教务同步。
 *
 * 学校把教务入口挂在办事大厅 SPA 后面，裸 /jwglxt/ 地址会落到正方自己的
 * 登录页；因此后台同步必须先回到 eHall，再点击对应应用卡片。
 */
public final class CampusSyncClient {

    public enum Kind {
        TIMETABLE("学生课表查询", "课表"),
        GRADES("学生成绩查询", "成绩");

        final String cardText;
        final String label;

        Kind(String cardText, String label) {
            this.cardText = cardText;
            this.label = label;
        }
    }

    /** 最近一次当前学期课表同步的变化摘要（UI 取走后清空）。 */
    public static volatile String lastTimetableDiff = "";

    public interface Callback {
        void onSuccess(int count);
        void onFailure(String message);
    }

    private static final String TAG = "CampusSync";
    private static final String EHALL_DIRECT_HOME =
            "https://ehall.ecut.edu.cn/new/index.html";
    private static final String EHALL_PROXY_HOME =
            "https://ehall-ecut-edu-cn-s.atrust.ecut.edu.cn/new/index.html";
    private static final String TIMETABLE_PATH =
            "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default";
    private static final String GRADES_PATH =
            "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default";
    private static final long NAVIGATION_TIMEOUT_MS = 30_000L;
    private static final long DATA_TIMEOUT_MS = 13_000L;
    private static final Pattern PROXY_HOST = Pattern.compile(
            "([0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}\\.atrust\\.ecut\\.edu\\.cn)");

    private final Context appContext;
    private final WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Kind kind;
    private final Callback callback;
    private final String targetXnm;
    private final String targetXqm;

    private boolean finished;
    private boolean cardClicked;
    private boolean dataRequested;
    private boolean directAttempted;
    private boolean fallbackStarted;
    private boolean openedAppFrameLoaded;
    private int pollAttempts;
    private boolean running;
    private int ehallLoginChecks;
    private int gradeReadAttempts;

    private final Runnable timeout = () -> {
        if (!finished) fail("同步超时，请确认统一门户登录仍有效");
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            if (pollAttempts++ >= 40 || cardClicked) return;
            String url = webView.getUrl();
            if (!isEhallUrl(url)) {
                handler.postDelayed(this, 500L);
                return;
            }
            clickEhallCard();
            handler.postDelayed(this, 500L);
        }
    };

    private CampusSyncClient(WebView view, Kind kind, String xnm, String xqm, Callback callback) {
        this.webView = view;
        this.appContext = view.getContext().getApplicationContext();
        this.kind = kind;
        this.callback = callback;
        this.targetXnm = xnm;
        this.targetXqm = xqm;
    }

    /** 必须在主线程调用，并先把 WebView 附着到当前 Activity 的不可见容器。 */
    public static CampusSyncClient start(WebView view, Kind kind, Callback callback) {
        return start(view, kind, null, null, callback);
    }

    /**
     * 指定学年学期同步。xnm/xqm 为教务参数（如 2025 / 3），
     * 传 null 时跟随教务页面当前选中的学期。
     */
    public static CampusSyncClient start(WebView view, Kind kind,
                                         String xnm, String xqm, Callback callback) {
        CampusSyncClient client = new CampusSyncClient(view, kind, xnm, xqm, callback);
        client.running = true;
        client.configure();
        client.load();
        return client;
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i(TAG, kind.label + " page: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, kind.label + " finished: " + url);
                if (finished) return;
                if (url == null) return;
                capturePortalHost(url);

                if (isLoginPage(url)) {
                    if (directAttempted && !fallbackStarted) {
                        startEhallFallback("教务直达需要重新认证");
                    } else if (fallbackStarted && cardClicked) {
                        // 办事大厅已点击卡片、直达重试尚未发出时忽略；
                        // 5 秒后的直达重试若仍失败会走超时兜底。
                        Log.i(TAG, kind.label + " login page after card click, waiting for retry");
                    } else {
                        Prefs.setPortalSession(appContext, false);
                        fail("统一门户登录已失效，请先在设置页重新登录");
                    }
                    return;
                }
                if (isTrustLoginWall(url)) {
                    // 零信任网关会话有效时也会先经过 shortcut.html#!/login
                    // 快速握手再自动跳转，不能立刻判失败；等 6 秒复核，
                    // 还停在登录墙才认为真的过期。
                    handler.postDelayed(() -> {
                        String current = webView.getUrl();
                        if (finished || current == null || !isTrustLoginWall(current)) return;
                        Prefs.setPortalSession(appContext, false);
                        fail("零信任网关登录已失效，请先在设置页重新登录");
                    }, 6_000L);
                    return;
                }
                if (kind == Kind.TIMETABLE && isTimetablePage(url)) {
                    requestData();
                    return;
                }
                if (kind == Kind.GRADES && isGradesPage(url)) {
                    requestData();
                    return;
                }
                if (isEhallUrl(url)) {
                    checkEhallLogin();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    Log.w(TAG, kind.label + " page error: "
                            + error.getDescription() + " @ " + request.getUrl());
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String raw = request.getUrl().toString();
                String url = WebShellActivity.rewriteBareCampusHost(raw);
                if (!isAllowed(url)) return true;
                if (!url.equals(raw)) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });

        // 办事大厅的卡片可能通过 window.open 打开教务，必须在同一隐藏会话里接住。
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(view.getContext());
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openPopupUrl(v, request.getUrl().toString());
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        openPopupUrl(v, url);
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }

    private void load() {
        handler.postDelayed(timeout, NAVIGATION_TIMEOUT_MS);
        String host = Prefs.portalHost(appContext);
        if (host != null && !host.trim().isEmpty()) {
            directAttempted = true;
            String url = targetUrl(host, kind);
            Log.i(TAG, kind.label + " direct: " + url);
            webView.loadUrl(url);
        } else {
            startEhallFallback("没有缓存的教务节点");
        }
    }

    private void clickEhallCard() {
        String js = "(function(){" +
                "var wanted=" + jsString(kind.cardText) + ";" +
                "function norm(s){return (s||'').replace(/\\s+/g,'').trim();}" +
                "function isWanted(t){" +
                "t=norm(t);return t===wanted||t===wanted+'(新)'||t.indexOf(wanted+'(')===0;" +
                "}" +
                "var titles=document.querySelectorAll('.appTitleFlag');" +
                "for(var i=0;i<titles.length;i++){" +
                "var title=titles[i];" +
                "if(!isWanted(title.innerText||title.textContent))continue;" +
                "var card=title.closest('.appFlag');" +
                "if(!card)continue;" +
                "var appId=card.getAttribute('data-appid')||'';" +
                "try{card.click();}catch(e){}" +
                "if(window.jQuery){try{window.jQuery(card).trigger('click');}catch(e){}}" +
                "return 'CLICKED:'+appId+':'+(card.className||'');" +
                "}" +
                "return 'WAIT';" +
                "})()";
        webView.evaluateJavascript(js, r -> {
            String value = WebShellActivity.unwrapJsString(r);
            Log.i(TAG, kind.label + " card: " + value);
            if (value.startsWith("CLICKED")) {
                cardClicked = true;
                handler.removeCallbacks(poll);
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, NAVIGATION_TIMEOUT_MS);
                handler.postDelayed(this::openEhallAppFrame, 800L);
                handler.postDelayed(this::openEhallAppFrame, 2_000L);
                handler.postDelayed(this::openEhallAppFrame, 4_000L);
                // 手机版门户没有 #iframeList，点击卡片只种下教务会话；
                // 5 秒后重载直达地址，此时教务不再要求登录。
                handler.postDelayed(this::retryDirectAfterEhallHop, 5_000L);
            }
        });
    }

    private void retryDirectAfterEhallHop() {
        if (finished || dataRequested) return;
        String host = Prefs.portalHost(appContext);
        if (host == null || host.trim().isEmpty()) return;
        String url = targetUrl(host, kind);
        Log.i(TAG, kind.label + " retry direct after ehall hop: " + url);
        webView.loadUrl(url);
    }

    /**
     * 办事大厅在桌面端会把应用加载进 #iframeList。隐藏 WebView 只需取出该
     * iframe 的最终 URL 并在主 WebView 打开，onPageFinished 就能继续抓数据。
     */
    private void openEhallAppFrame() {
        if (finished || openedAppFrameLoaded) return;
        String js = "(function(){" +
                "var list=document.querySelectorAll('#iframeList iframe');" +
                "for(var i=0;i<list.length;i++){" +
                "var src=list[i].getAttribute('src')||'';" +
                "if(src&&src!=='about:blank')return src;" +
                "}" +
                "return '';})()";
        webView.evaluateJavascript(js, raw -> {
            if (finished || openedAppFrameLoaded) return;
            String src = WebShellActivity.unwrapJsString(raw);
            if (src == null || src.isEmpty() || "about:blank".equals(src)) return;
            openedAppFrameLoaded = true;
            String url = resolveEhallUrl(src);
            Log.i(TAG, kind.label + " app frame: " + url);
            if (isAllowed(url)) webView.loadUrl(url);
        });
    }

    private void startEhallFallback(String reason) {
        if (finished || fallbackStarted) return;
        fallbackStarted = true;
        cardClicked = false;
        dataRequested = false;
        pollAttempts = 0;
        ehallLoginChecks = 0;
        Log.i(TAG, kind.label + " fallback: " + reason);
        DiagLog.log(appContext, "sync", kind.label + " fallback: " + reason);
        webView.stopLoading();
        webView.loadUrl(EHALL_PROXY_HOME);
        handler.postDelayed(poll, 1_500L);
    }

    private void requestData() {
        if (dataRequested) return;
        dataRequested = true;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(timeout, DATA_TIMEOUT_MS);

        if (kind == Kind.TIMETABLE) {
            extractTimetable();
        } else {
            extractGrades();
        }
    }

    private void extractTimetable() {
        String xnmExpr = targetXnm != null && !targetXnm.isEmpty()
                ? "'" + targetXnm.replace("'", "") + "'"
                : "((yEl&&yEl.value)?yEl.value:'2026')";
        String xqmExpr = targetXqm != null && !targetXqm.isEmpty()
                ? "'" + targetXqm.replace("'", "") + "'"
                : "((tEl&&tEl.value)?tEl.value:'3')";
        String js = "(function(){" +
                "try{" +
                "var yEl=document.getElementById('xnm');" +
                "var tEl=document.getElementById('xqm');" +
                "var xnm=" + xnmExpr + ";" +
                "var xqm=" + xqmExpr + ";" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('POST','/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151',false);" +
                "xhr.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
                "xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "xhr.send('xnm='+encodeURIComponent(xnm)+'&xqm='+encodeURIComponent(xqm)+'&kzlx=ck');" +
                "return xhr.responseText||'';" +
                "}catch(e){return 'ERR:'+e;}" +
                "})()";
        webView.evaluateJavascript(js, raw -> {
            String json = WebShellActivity.unwrapJsString(raw);
            if (json == null || json.isEmpty() || json.startsWith("ERR:")) {
                fail("课表接口没有返回数据");
                return;
            }
            JwglxtFetcher.Result result = JwglxtFetcher.parse(json);
            DiagLog.saveKbList(appContext, json);
            DiagLog.log(appContext, "sync", "课表解析: 学期=" + result.timetable.semester.id
                    + " 原始=" + result.rawRecords + " 课程=" + result.courseCount
                    + " 周次失败=" + result.failedWeekExpr + " 合并重复=" + result.mergedDuplicates
                    + (result.warnings.isEmpty() ? "" : " " + result.warnings));
            if (!result.ok()) {
                fail("课表解析为空，请稍后重试");
                return;
            }
            Timetable merged;
            if (targetXnm != null && !targetXnm.isEmpty()) {
                // 历史学期：合并进归档里该学期的既有数据，不动当前学期。
                Timetable existing = SemesterArchive.loadSemester(
                        appContext, result.timetable.semester.id);
                merged = TimetableStore.merge(
                        existing != null ? existing : new Timetable(), result.timetable);
                if (!SemesterArchive.saveSemester(appContext, merged, false)) {
                    fail("历史课表写入归档失败");
                    return;
                }
            } else {
                Timetable before = TimetableStore.load(appContext);
                merged = TimetableStore.merge(before, result.timetable);
                lastTimetableDiff = SyncDiff.summarize(before, merged);
                if (!TimetableStore.save(appContext, merged)) {
                    fail("课表写入本地失败");
                    return;
                }
            }
            SemesterArchive.unifyColors(appContext);
            Prefs.setLastSyncTime(appContext, now());
            Log.i(TAG, "Timetable saved: " + merged.courses.size()
                    + " courses (" + result.rawRecords + " records)");
            Prefs.setPortalSession(appContext, true);
            finishSuccess(merged.courses.size());
        });
    }

    private void extractGrades() {
        String trigger = "(function(){" +
                "try{" +
                "window.__ecutReadGrades=function(){};" +
                "var y=document.getElementById('xnm');var t=document.getElementById('xqm');" +
                "if(y&&!window.__ecutGradeInited){y.value='';}" +
                "if(t&&!window.__ecutGradeInited){t.value='';}" +
                "if(window.jQuery){" +
                "try{var g=window.jQuery('#tabGrid');" +
                "if(g.length&&g.jqGrid){g.jqGrid('setGridParam',{rowNum:500,page:1});" +
                "g.trigger('reloadGrid');}}" +
                "catch(e){}" +
                "}" +
                "var pg=document.querySelector('select.ui-pg-selbox');" +
                "if(pg){pg.value='500';" +
                "pg.dispatchEvent(new Event('change',{bubbles:true}));" +
                "if(window.jQuery)window.jQuery(pg).trigger('change');}" +
                "var btn=document.getElementById('search_go');" +
                "if(btn&&!window.__ecutGradeInited){" +
                "window.__ecutGradeInited=true;btn.click();return 'CLICKED';}" +
                "}catch(e){return 'ERR:'+e;}" +
                "return 'READY';})()";
        webView.evaluateJavascript(trigger, r -> {
            Log.i(TAG, "GRADE_TRIGGER: " + r);
            logGradePageDiagnostics();
            handler.postDelayed(this::readGradeRows, 3_200L);
            handler.postDelayed(this::readGradeRows, 6_500L);
        });
    }

    private void logGradePageDiagnostics() {
        String js = "(function(){" +
                "function opts(id){var e=document.getElementById(id);if(!e)return null;" +
                "var out=[];for(var i=0;i<e.options.length;i++)out.push({v:e.options[i].value,t:e.options[i].text});" +
                "return {value:e.value,options:out};}" +
                "function pgopts(){var e=document.querySelector('select.ui-pg-selbox');if(!e)return null;" +
                "var out=[];for(var i=0;i<e.options.length;i++)out.push(e.options[i].value);" +
                "return {value:e.value,options:out};}" +
                "var t=document.getElementById('tabGrid');" +
                "return JSON.stringify({xnm:opts('xnm'),xqm:opts('xqm'),pg:pgopts()," +
                "rows:t&&t.rows?t.rows.length:-1});" +
                "})()";
        webView.evaluateJavascript(js, raw -> Log.i(TAG, "GRADE_DIAG: "
                + WebShellActivity.unwrapJsString(raw)));
    }

    private void readGradeRows() {
        if (finished) return;
        gradeReadAttempts++;
        String js = "(function(){" +
                "var t=document.getElementById('tabGrid');" +
                "if(!t||!t.rows||t.rows.length<=1)return '';" +
                "var rows=[];" +
                "for(var i=0;i<t.rows.length;i++){" +
                "var row=[];var cs=t.rows[i].cells;" +
                "for(var c=0;c<cs.length;c++){row.push((cs[c].innerText||'').trim());}" +
                "rows.push(row);}" +
                "return JSON.stringify({rows:rows});})()";
        webView.evaluateJavascript(js, raw -> {
            String decoded = WebShellActivity.unwrapJsString(raw);
            if (decoded == null || decoded.isEmpty()) {
                if (gradeReadAttempts >= 2) fail("成绩表格没有返回数据");
                return;
            }
            Grades parsed = Grades.parseFromDomRows(decoded);
            int count = parsed.totalCourseCount();
            StringBuilder semesters = new StringBuilder();
            for (Grades.SemesterGrades sem : parsed.semesters) {
                if (semesters.length() > 0) semesters.append(", ");
                semesters.append(sem.id).append('=').append(sem.courses.size());
            }
            Log.i(TAG, "GRADE_ROWS: " + decoded.length()
                    + " chars, courses=" + count + ", semesters=[" + semesters + "]");
            if (count <= 0) {
                if (gradeReadAttempts >= 2) fail("成绩解析为空，请稍后重试");
                return;
            }
            // 教务成绩是全量权威数据。直接覆盖而不是合并，避免旧演示数据
            // 或已撤销课程永久残留。
            parsed.updatedAt = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
            parsed.recomputeAll();
            // 0.4：和上一次对比，记下新出的成绩（首次同步不算）
            try {
                Grades before = GradesStore.load(appContext);
                if (!Prefs.isGradesDemo(appContext)) {
                    java.util.List<Grades.CourseGrade> fresh =
                            com.ecut.campus.data.GradeDiff.newGrades(before, parsed);
                    if (!fresh.isEmpty()) {
                        java.util.Set<String> keys = Prefs.newGradeKeys(appContext);
                        keys.addAll(com.ecut.campus.data.GradeDiff.newKeys(before, parsed));
                        Prefs.setNewGradeKeys(appContext, keys);
                        Notify.newGrades(appContext, fresh);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "grade diff failed", e);
            }
            if (!GradesStore.save(appContext, parsed)) {
                fail("成绩写入本地失败");
                return;
            }
            Prefs.setGradesDemo(appContext, false);
            Prefs.setLastSyncTime(appContext, now());
            Log.i(TAG, "Grades replaced: " + parsed.totalCourseCount()
                    + " courses in " + parsed.semesters.size() + " semesters");
            Prefs.setPortalSession(appContext, true);
            finishSuccess(parsed.totalCourseCount());
        });
    }

    private void checkEhallLogin() {
        if (finished || dataRequested || cardClicked) return;
        String js = "(function(){" +
                "var id=document.getElementById('analysisUserId');" +
                "var name=document.getElementById('ampHeaderToolUserName');" +
                "var tool=document.getElementById('ampHasLoginTool');" +
                "var uid=id?(id.value||''):'';" +
                "var text=name?((name.innerText||name.textContent||'').trim()):'';" +
                "if(uid&&uid!=='guest')return 'LOGIN';" +
                "if(text&&text.length>0)return 'LOGIN';" +
                "if(tool&&getComputedStyle(tool).display==='block')return 'LOGIN';" +
                "return 'GUEST';" +
                "})()";
        webView.evaluateJavascript(js, raw -> {
            if (finished || dataRequested || cardClicked) return;
            String state = WebShellActivity.unwrapJsString(raw);
            if ("LOGIN".equals(state)) {
                Prefs.setPortalSession(appContext, true);
                CookieManager.getInstance().flush();
                return;
            }
            if (ehallLoginChecks++ < 5) {
                handler.postDelayed(this::checkEhallLogin, 1_200L);
            } else {
                fail("请先在设置页登录统一门户");
            }
        });
    }

    private void openPopupUrl(WebView popup, String rawUrl) {
        if (popup == null || finished) return;
        if (rawUrl == null || rawUrl.isEmpty() || "about:blank".equals(rawUrl)) return;
        capturePortalHost(rawUrl);
        String url = WebShellActivity.rewriteBareCampusHost(rawUrl);
        if (isAllowed(url)) {
            webView.loadUrl(url);
            // window.open 的临时 WebView 只负责把 URL 交回主隐藏会话。
            popup.stopLoading();
        }
        popup.destroy();
    }

    private void capturePortalHost(String url) {
        if (url == null) return;
        Matcher m = PROXY_HOST.matcher(url);
        if (m.find() && !m.group(1).equals(Prefs.portalHost(appContext))) {
            Prefs.setPortalHost(appContext, m.group(1));
        }
    }

    private boolean isTimetablePage(String url) {
        return url != null && url.contains("/jwglxt/")
                && (url.contains("xskbcx") || url.contains("/kbcx/"))
                && !isLoginPage(url);
    }

    private boolean isGradesPage(String url) {
        return url != null && url.contains("/jwglxt/")
                && url.contains("cjcx") && !isLoginPage(url);
    }

    static boolean isEhallUrl(String url) {
        return CampusUrlPolicy.isEhallUrl(url);
    }

    static String targetUrl(String host, Kind kind) {
        String path = kind == Kind.TIMETABLE ? TIMETABLE_PATH : GRADES_PATH;
        return "https://" + host + path;
    }

    static boolean isLoginPage(String url) {
        return url != null && url.contains("/jwglxt/")
                && (url.contains("login_slogin") || url.contains("/login"));
    }

    /** atrust 零信任通道的登录墙：shortcut.html 的 #!/login 或 authserver 登录页 */
    static boolean isTrustLoginWall(String url) {
        if (url == null) return false;
        return url.contains("authserver/login")
                || (url.contains("portal/shortcut.html")
                        && url.contains("%23%21%2Flogin"));
    }

    private static boolean isAllowed(String url) {
        return CampusUrlPolicy.isAllowedCampusUrl(url);
    }

    private void finishSuccess(int count) {
        if (finished) return;
        finished = true;
        running = false;
        Prefs.setSessionExpired(appContext, false);
        if (kind == Kind.TIMETABLE) Prefs.setTimetableSyncedAt(appContext, System.currentTimeMillis());
        cleanup();
        callback.onSuccess(count);
    }

    private void fail(String message) {
        if (finished) return;
        finished = true;
        running = false;
        Log.w(TAG, kind.label + " failed: " + message);
        DiagLog.log(appContext, "sync", kind.label + " 失败: " + message);
        if (message != null && message.contains("登录已失效")) Prefs.setSessionExpired(appContext, true);
        cleanup();
        callback.onFailure(message);
    }

    private String resolveEhallUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        if (raw.startsWith("//")) return "https:" + raw;
        String current = webView.getUrl();
        if (current == null) return raw;
        try {
            return java.net.URI.create(current).resolve(raw).toString();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        CookieManager.getInstance().flush();
        if (webView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }
        webView.loadUrl("about:blank");
        webView.destroy();
    }

    /** Activity 销毁时静默取消，不触发失败回调，避免向已销毁页面弹 Toast。 */
    public void cancel() {
        if (finished) return;
        finished = true;
        running = false;
        cleanup();
    }

    private static String jsString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String now() {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
    }
}
