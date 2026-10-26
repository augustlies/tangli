package com.ecut.campus.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校徽小图标：从学校官网首页找图标地址。纯逻辑，不碰网络和 Android。
 *
 * 教务系统自己的图标多半是正方/强智的厂商标志，所以从教务域名退回到学校主域名
 * （jwxt.xxx.edu.cn、172-20-130-13.atrust.ecut.edu.cn → xxx.edu.cn / ecut.edu.cn），
 * 再去官网首页读 &lt;link rel="icon"&gt;。安装包里不带任何校徽。
 */
public final class SchoolBadge {

    private SchoolBadge() {}

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    /** 主机名 → 学校主域名；IP、空值返回空串。 */
    public static String rootDomain(String host) {
        if (host == null) return "";
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("*.")) h = h.substring(2);
        int colon = h.indexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty() || IPV4.matcher(h).matches() || !h.contains(".")) return "";
        String[] parts = h.split("\\.");
        int keep = 2;
        int n = parts.length;
        if (n >= 3 && (parts[n - 1].equals("cn") || parts[n - 1].equals("hk") || parts[n - 1].equals("tw"))
                && (parts[n - 2].equals("edu") || parts[n - 2].equals("com")
                || parts[n - 2].equals("org") || parts[n - 2].equals("ac"))) {
            keep = 3;
        }
        if (n < keep) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = n - keep; i < n; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** 从学校配置里挑出最可信的主域名：host 规则、教务地址、登录地址里出现最多的那个。 */
    public static String schoolDomain(SchoolProfile p) {
        if (p == null) return "";
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (String h : p.hosts) vote(votes, rootDomain(h));
        vote(votes, rootDomain(hostOf(p.jwBase)));
        vote(votes, rootDomain(hostOf(p.loginUrl)));
        String best = "";
        int bestN = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestN) {
                best = e.getKey();
                bestN = e.getValue();
            }
        }
        return best;
    }

    private static void vote(Map<String, Integer> votes, String d) {
        if (d == null || d.isEmpty()) return;
        Integer n = votes.get(d);
        votes.put(d, n == null ? 1 : n + 1);
    }

    private static String hostOf(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    /** 官网首页候选地址，按顺序试。 */
    public static List<String> homepageCandidates(String domain) {
        List<String> out = new ArrayList<>();
        if (domain == null || domain.isEmpty()) return out;
        out.add("https://www." + domain + "/");
        out.add("https://" + domain + "/");
        out.add("http://www." + domain + "/");
        return out;
    }

    /**
     * 从首页 HTML 里挑图标地址，按清晰度排序：apple-touch-icon → 标了尺寸的 icon（大的在前）
     * → 其余 icon → 站点根目录 /favicon.ico。返回的都是绝对地址。
     */
    public static List<String> iconCandidates(String html, String pageUrl) {
        List<String[]> found = new ArrayList<>(); // {url, score}
        if (html != null) {
            Matcher m = LINK.matcher(html);
            while (m.find()) {
                Map<String, String> attrs = attrs(m.group());
                String rel = attrs.getOrDefault("rel", "").toLowerCase(Locale.ROOT);
                String href = attrs.getOrDefault("href", "").trim();
                if (href.isEmpty() || href.startsWith("data:") || !rel.contains("icon")) continue;
                if (rel.contains("mask-icon")) continue; // 单色 svg 轮廓
                if (href.toLowerCase(Locale.ROOT).endsWith(".svg")) continue; // BitmapFactory 解不了
                int score;
                if (rel.contains("apple-touch-icon")) score = 1000;
                else score = 100 + maxSize(attrs.getOrDefault("sizes", ""));
                String abs = resolve(pageUrl, href);
                if (abs.isEmpty()) continue;
                found.add(new String[]{abs, String.valueOf(score)});
            }
        }
        found.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        List<String> out = new ArrayList<>();
        for (String[] f : found) if (!out.contains(f[0])) out.add(f[0]);
        String root = resolve(pageUrl, "/favicon.ico");
        if (!root.isEmpty() && !out.contains(root)) out.add(root);
        return out;
    }

    private static int maxSize(String sizes) {
        int best = 0;
        for (String s : sizes.toLowerCase(Locale.ROOT).split("\\s+")) {
            int x = s.indexOf('x');
            if (x <= 0) continue;
            try {
                best = Math.max(best, Integer.parseInt(s.substring(0, x)));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(best, 512);
    }

    private static Map<String, String> attrs(String tag) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(tag);
        while (m.find()) {
            String v = m.group(3) != null ? m.group(3) : (m.group(4) != null ? m.group(4) : m.group(5));
            out.put(m.group(1).toLowerCase(Locale.ROOT), v.replace("&amp;", "&"));
        }
        return out;
    }

    static String resolve(String base, String href) {
        try {
            URI u = URI.create(base).resolve(href.replace(" ", "%20"));
            String s = u.getScheme();
            if (!"http".equals(s) && !"https".equals(s)) return "";
            return u.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
