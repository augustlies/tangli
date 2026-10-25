package com.ecut.campus.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析官网校历栏目：列表页取最新一篇，文章页取校历大图（或 PDF）。 */
public final class CalendarSource {

    private CalendarSource() {}

    public static final String BASE = "https://www.ecut.edu.cn";

    private static final Pattern LIST_ITEM = Pattern.compile(
            "href=['\"]([^'\"]+?page\\.htm)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_IMG = Pattern.compile(
            "wp_articlecontent[\\s\\S]*?<img[^>]+?src=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_PDF = Pattern.compile(
            "pdfsrc=['\"]([^'\"]+\\.pdf)['\"]", Pattern.CASE_INSENSITIVE);

    /** 列表页里第一篇标题含「校历」的文章（列表按发布时间倒序）。找不到返回 null。 */
    public static String latestArticleUrl(String listHtml) {
        if (listHtml == null) return null;
        Matcher m = LIST_ITEM.matcher(listHtml);
        while (m.find()) {
            // 标题可能在 title 属性里，也可能嵌在后面几层标签里：取链接后一小段去掉标签再看
            int end = Math.min(listHtml.length(), m.end() + 600);
            int next = listHtml.indexOf("page.htm", m.end());
            if (next > 0 && next < end) end = next;
            String tail = listHtml.substring(m.end(), end).replaceAll("<[^>]*>", "");
            if (tail.contains("校历")) return absolute(m.group(1));
        }
        return null;
    }

    /** 文章页里的校历图片或 PDF 地址。找不到返回 null。 */
    public static String imageUrl(String articleHtml) {
        if (articleHtml == null) return null;
        Matcher m = CONTENT_IMG.matcher(articleHtml);
        if (m.find()) return absolute(m.group(1));
        Matcher p = CONTENT_PDF.matcher(articleHtml);
        if (p.find()) return absolute(p.group(1));
        return null;
    }

    static String absolute(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        return BASE + (href.startsWith("/") ? "" : "/") + href;
    }
}
