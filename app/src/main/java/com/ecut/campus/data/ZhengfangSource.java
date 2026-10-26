package com.ecut.campus.data;

import java.util.Calendar;
import java.util.Locale;

/**
 * 正方教务（新版 V9，/jwglxt/）。课表走 kbList JSON 接口，解析交给 {@link JwglxtFetcher}。
 *
 * 东华理工的同步逻辑仍在 CampusSyncClient 里原样保留；这里服务自定义的正方学校和「导入此页课表」。
 */
public final class ZhengfangSource implements TimetableSource {

    public static final String TIMETABLE_PATH =
            "kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default";
    public static final String GRADES_PATH =
            "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default";

    @Override
    public SchoolProfile.Vendor vendor() {
        return SchoolProfile.Vendor.ZHENGFANG;
    }

    @Override
    public int detect(String url, String html) {
        int score = 0;
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains("/jwglxt/") || u.endsWith("/jwglxt")) score += 50;
        if (u.contains("xskbcx") || u.contains("login_slogin") || u.contains("/xtgl/")) score += 20;
        if (html != null) {
            if (html.contains("kbgrid_table") || html.contains("xskbcx_cx")) score += 30;
            if (html.contains("/jwglxt/")) score += 10;
            if (html.contains("ajaxForm") && html.contains("xnm") && html.contains("xqm")) score += 10;
            if (html.contains("zftal") || html.contains("ZFTAL") || html.contains("正方软件")) score += 10;
        }
        return Math.min(100, score);
    }

    @Override
    public String timetableUrl(SchoolProfile p) {
        if (p == null || p.jwBase == null || p.jwBase.isEmpty()) return null;
        return p.jwBase + TIMETABLE_PATH;
    }

    public static String gradesUrl(SchoolProfile p) {
        if (p == null || p.jwBase == null || p.jwBase.isEmpty()) return null;
        return p.jwBase + GRADES_PATH;
    }

    @Override
    public boolean isLoginPage(String url) {
        if (url == null) return false;
        return url.contains("login_slogin")
                || (url.contains("/jwglxt/") && url.contains("/login"));
    }

    @Override
    public boolean isLoggedInPage(String url) {
        if (url == null || isLoginPage(url)) return false;
        return url.contains("/jwglxt/") || url.contains("/xtgl/")
                || url.contains("/kbcx/") || url.contains("/cjcx/");
    }

    public static boolean isTimetablePage(String url) {
        return url != null && (url.contains("xskbcx") || url.contains("/kbcx/"))
                && !url.contains("login_slogin");
    }

    public static boolean isGradesPage(String url) {
        return url != null && url.contains("cjcx") && !url.contains("login_slogin");
    }

    @Override
    public String extractionJs(SchoolProfile p, String xnm, String xqm) {
        String[] def = defaultTerm(Calendar.getInstance());
        String xnmExpr = xnm != null && !xnm.isEmpty()
                ? "'" + xnm.replaceAll("[^0-9]", "") + "'"
                : "((yEl&&yEl.value)?yEl.value:'" + def[0] + "')";
        String xqmExpr = xqm != null && !xqm.isEmpty()
                ? "'" + xqm.replaceAll("[^0-9]", "") + "'"
                : "((tEl&&tEl.value)?tEl.value:'" + def[1] + "')";
        // 学校可能部署在 /jwglxt/ 下，也可能直接在根目录：从当前页面路径推出接口前缀。
        return "(function(){" +
                "try{" +
                "var p=location.pathname;var base='/jwglxt/';" +
                "var i=p.indexOf('/jwglxt/');" +
                "if(i>=0){base=p.substring(0,i+8);}else{" +
                "var ks=['/kbcx/','/xtgl/','/cjcx/'];" +
                "for(var k=0;k<ks.length;k++){var j=p.indexOf(ks[k]);if(j>=0){base=p.substring(0,j+1);break;}}}" +
                "var yEl=document.getElementById('xnm');" +
                "var tEl=document.getElementById('xqm');" +
                "var xnm=" + xnmExpr + ";" +
                "var xqm=" + xqmExpr + ";" +
                "var body='xnm='+encodeURIComponent(xnm)+'&xqm='+encodeURIComponent(xqm)+'&kzlx=ck&xsdm=';" +
                "function post(path){var x=new XMLHttpRequest();x.open('POST',base+path,false);" +
                "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
                "x.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "x.send(body);return x.responseText||'';}" +
                "var t='';try{t=post('kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151');}catch(e){}" +
                // 有的学校只开放 cxXsgrkb 接口
                "if(t.indexOf('kbList')<0){try{var t2=post('kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151');" +
                "if(t2.indexOf('kbList')>=0)t=t2;}catch(e){}}" +
                "return t;" +
                "}catch(e){return 'ERR:'+e;}" +
                "})()";
    }

    @Override
    public JwglxtFetcher.Result parse(String raw) {
        return JwglxtFetcher.parse(raw);
    }

    /**
     * 页面上取不到学年学期时的默认值：8 月及以后算当年第一学期，
     * 1 月算上一年第一学期，2-7 月算上一年第二学期。
     *
     * @return {xnm, xqm}，xqm 为正方学期码 3/12
     */
    public static String[] defaultTerm(Calendar now) {
        int y = now.get(Calendar.YEAR);
        int m = now.get(Calendar.MONTH) + 1;
        if (m >= 8) return new String[]{String.valueOf(y), "3"};
        if (m == 1) return new String[]{String.valueOf(y - 1), "3"};
        return new String[]{String.valueOf(y - 1), "12"};
    }
}
