package com.ecut.campus.data;

/**
 * 一家教务系统的课表来源：怎么认出它、课表在哪、在已登录 WebView 里跑什么 JS 取原始数据、
 * 怎么把原始数据解析成 {@link Timetable}。
 *
 * 抓取和解析分开：JS 只取原始文本（尽量走接口），解析全部在 Java 里，可单测、可离线重跑。
 * 纯 Java，无 Android 依赖。
 */
public interface TimetableSource {

    SchoolProfile.Vendor vendor();

    /** 当前页面是这家教务的把握，0-100。纯函数；html 可以为 null（只看网址）。 */
    int detect(String url, String html);

    /** 后台同步时要打开的课表页；不支持时返回 null。 */
    String timetableUrl(SchoolProfile p);

    /** 当前网址是不是已登录的教务页面（用来判断同步能否继续、登录是否有效）。 */
    boolean isLoggedInPage(String url);

    /** 当前网址是不是这家教务的登录页。 */
    boolean isLoginPage(String url);

    /**
     * 在已登录的教务 WebView 里运行的 JS，返回原始课表文本。
     *
     * @param p   当前学校；通用导入时可能为 null
     * @param xnm 学年（如 2025），null 跟随页面当前学期
     * @param xqm 正方学期码（3/12/16），null 跟随页面当前学期
     */
    String extractionJs(SchoolProfile p, String xnm, String xqm);

    /** 原始文本 → 课表。永不抛异常；失败时 courseCount=0 并带 warnings。 */
    JwglxtFetcher.Result parse(String raw);
}
