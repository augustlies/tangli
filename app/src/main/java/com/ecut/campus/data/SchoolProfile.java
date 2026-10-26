package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 一所学校的教务配置：教务系统厂商、地址、访问方式、允许打开的域名。
 *
 * 内置学校来自 assets/schools.json；用户也可以填一个教务网址建「自定义学校」。
 * 纯 Java，无 Android 依赖，便于 JVM 单测。
 */
public final class SchoolProfile {

    public static final String ECUT_ID = "ecut";
    public static final String CUSTOM_ID = "custom";

    /** 教务系统厂商。只区分烫梨能解析的几家，其余都是 OTHER。 */
    public enum Vendor {
        ZHENGFANG("正方"), QIANGZHI("强智"), OTHER("其他");

        public final String label;

        Vendor(String label) {
            this.label = label;
        }

        public static Vendor parse(String s) {
            if (s == null) return OTHER;
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "zhengfang":
                case "zf":
                case "zf_new":
                    return ZHENGFANG;
                case "qiangzhi":
                case "qz":
                    return QIANGZHI;
                default:
                    return OTHER;
            }
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 访问方式：直连、零信任（aTrust）、WebVPN。 */
    public enum Access {
        DIRECT, ATRUST, WEBVPN;

        public static Access parse(String s) {
            if (s == null) return DIRECT;
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "atrust":
                    return ATRUST;
                case "webvpn":
                    return WEBVPN;
                default:
                    return DIRECT;
            }
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public String id = "";
    public String name = "";
    public Vendor vendor = Vendor.OTHER;
    /** 教务应用根地址，正方以 /jwglxt/ 结尾，强智以 /jsxsd/ 结尾，统一带结尾斜杠。 */
    public String jwBase = "";
    public Access access = Access.DIRECT;
    /** 用户手动登录时打开的页面。 */
    public String loginUrl = "";
    /** 允许打开的域名。{@code *.example.edu.cn} 同时匹配 example.edu.cn 本身。 */
    public List<String> hosts = new ArrayList<>();
    public List<String> aliases = new ArrayList<>();
    public boolean verified;
    /** 校历、校园地图、办事大厅等东华理工专属功能。 */
    public boolean ecutFeatures;

    /** 搜索词是否命中别名（如「南大」「NJU」）。 */
    public boolean aliasMatch(String q) {
        String lq = q.toLowerCase(Locale.ROOT);
        for (String a : aliases) {
            String la = a.toLowerCase(Locale.ROOT);
            if (la.contains(lq) || lq.contains(la)) return true;
        }
        return false;
    }

    public boolean isEcut() {
        return ECUT_ID.equals(id);
    }

    public boolean isCustom() {
        return CUSTOM_ID.equals(id);
    }

    /** 成绩支持正方（jqGrid tabGrid）和强智（kscj/cjcx_list #dataList）。 */
    public boolean supportsGrades() {
        return (vendor == Vendor.ZHENGFANG || vendor == Vendor.QIANGZHI)
                && jwBase != null && !jwBase.isEmpty();
    }

    /** 能否不打开网页、在后台静默同步课表。 */
    public boolean supportsBackgroundSync() {
        return (vendor == Vendor.ZHENGFANG || vendor == Vendor.QIANGZHI)
                && jwBase != null && !jwBase.isEmpty();
    }

    /** 教务根地址的路径部分，如 {@code /jwglxt/}；取不到时返回 "/"。 */
    public String jwPathPrefix() {
        try {
            String p = URI.create(jwBase).getRawPath();
            if (p == null || p.isEmpty()) return "/";
            return p.endsWith("/") ? p : p + "/";
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    public String jwHost() {
        return hostOf(jwBase);
    }

    /** host 是否在白名单里（不区分大小写）。 */
    public boolean allowsHost(String host) {
        if (host == null || host.isEmpty() || hosts == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        for (String rule : hosts) {
            if (rule == null || rule.isEmpty()) continue;
            String r = rule.trim().toLowerCase(Locale.ROOT);
            if (r.startsWith("*.")) {
                String suffix = r.substring(2);
                if (h.equals(suffix) || h.endsWith("." + suffix)) return true;
            } else if (h.equals(r)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
            return allowsHost(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 诊断日志用的一行摘要：不含完整地址和查询参数，只留主机名。 */
    public String diagSummary() {
        return "school=" + id + " vendor=" + vendor.key() + " access=" + access.key()
                + " host=" + (jwHost().isEmpty() ? "-" : jwHost())
                + (verified ? " verified" : "");
    }

    // ---------- JSON ----------

    public static SchoolProfile fromJson(JSONObject o) {
        SchoolProfile p = new SchoolProfile();
        if (o == null) return p;
        p.id = o.optString("id", "").trim();
        p.name = o.optString("name", "").trim();
        p.vendor = Vendor.parse(o.optString("vendor", ""));
        p.jwBase = withSlash(o.optString("jwBase", "").trim());
        p.access = Access.parse(o.optString("access", ""));
        p.loginUrl = o.optString("loginUrl", "").trim();
        JSONArray hs = o.optJSONArray("hosts");
        if (hs != null) {
            for (int i = 0; i < hs.length(); i++) {
                String h = hs.optString(i, "").trim();
                if (!h.isEmpty()) p.hosts.add(h);
            }
        }
        JSONArray al = o.optJSONArray("aliases");
        for (int i = 0; al != null && i < al.length(); i++) {
            String a = al.optString(i, "").trim();
            if (!a.isEmpty()) p.aliases.add(a);
        }
        p.verified = o.optBoolean("verified", false);
        p.ecutFeatures = o.optBoolean("ecutFeatures", false);
        if (p.hosts.isEmpty()) {
            String h = hostOf(p.jwBase);
            if (!h.isEmpty()) p.hosts.add(h);
            String lh = hostOf(p.loginUrl);
            if (!lh.isEmpty() && !p.hosts.contains(lh)) p.hosts.add(lh);
        }
        if (p.loginUrl.isEmpty()) p.loginUrl = p.jwBase;
        return p;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("vendor", vendor.key());
            o.put("jwBase", jwBase);
            o.put("access", access.key());
            o.put("loginUrl", loginUrl);
            o.put("hosts", new JSONArray(hosts));
            o.put("verified", verified);
            o.put("ecutFeatures", ecutFeatures);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** 解析 schools.json。格式坏掉的条目跳过，整份坏掉返回空列表，不抛异常。 */
    public static List<SchoolProfile> parseList(String json) {
        List<SchoolProfile> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("schools");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                SchoolProfile p = fromJson(o);
                if (p.id.isEmpty() || p.name.isEmpty()) continue;
                out.add(p);
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return out;
    }

    /** 是不是同一所学校：id 相同，自定义学校还要教务主机相同。用来决定切换时要不要清掉上一所的成绩。 */
    public static boolean sameSchool(SchoolProfile a, SchoolProfile b) {
        if (a == null || b == null || a.id == null || !a.id.equals(b.id)) return false;
        return !a.isCustom() || a.jwHost().equalsIgnoreCase(b.jwHost());
    }

    public static SchoolProfile find(List<SchoolProfile> list, String id) {
        if (list == null || id == null) return null;
        for (SchoolProfile p : list) if (id.equals(p.id)) return p;
        return null;
    }

    // ---------- 自定义学校 ----------

    /**
     * 用户填的教务网址 → 自定义学校。
     *
     * @param input  用户输入，可以不带 https://，可以是登录页或课表页
     * @param vendor 用户选的厂商；null 表示按网址自动识别
     * @return 网址无效时返回 null
     */
    public static SchoolProfile custom(String input, Vendor vendor) {
        String url = normalizeUrl(input);
        if (url == null) return null;
        String host = hostOf(url);
        if (host.isEmpty()) return null;
        String origin = originOf(url);
        Vendor v = vendor != null ? vendor : guessVendor(url);

        SchoolProfile p = new SchoolProfile();
        p.id = CUSTOM_ID;
        p.name = "自定义学校";
        p.vendor = v;
        p.loginUrl = url;
        p.access = host.toLowerCase(Locale.ROOT).contains("webvpn") ? Access.WEBVPN
                : (host.toLowerCase(Locale.ROOT).contains("atrust") ? Access.ATRUST : Access.DIRECT);
        String path = pathOf(url);
        String lower = path.toLowerCase(Locale.ROOT);
        if (v == Vendor.ZHENGFANG) {
            int i = lower.indexOf("/jwglxt/");
            if (i >= 0) {
                p.jwBase = origin + path.substring(0, i + "/jwglxt/".length());
            } else if (lower.endsWith("/jwglxt")) {
                p.jwBase = origin + path + "/";
            } else {
                int k = indexOfAny(lower, "/xtgl/", "/kbcx/", "/cjcx/");
                p.jwBase = k >= 0 ? origin + path.substring(0, k + 1) : origin + "/jwglxt/";
            }
        } else if (v == Vendor.QIANGZHI) {
            int i = lower.indexOf("/jsxsd/");
            if (i >= 0) {
                p.jwBase = origin + path.substring(0, i + "/jsxsd/".length());
            } else if (lower.endsWith("/jsxsd")) {
                p.jwBase = origin + path + "/";
            } else {
                p.jwBase = origin + "/jsxsd/";
            }
        } else {
            p.jwBase = origin + "/";
        }
        p.hosts.add(host.toLowerCase(Locale.ROOT));
        String parent = parentDomain(host);
        if (parent != null) p.hosts.add("*." + parent);
        p.verified = false;
        p.ecutFeatures = false;
        return p;
    }

    /** 仅凭网址猜厂商：/jwglxt → 正方，/jsxsd → 强智。 */
    public static Vendor guessVendor(String url) {
        if (url == null) return Vendor.OTHER;
        // 只看路径：主机名 jwglxt.xxx.edu.cn 上跑强智、或统一认证网址的 service 参数里带 /jwglxt，都不能算正方。
        String path = pathOf(url).toLowerCase(Locale.ROOT);
        if (path.contains("/jsxsd")) return Vendor.QIANGZHI;
        if (path.contains("/jwglxt")) return Vendor.ZHENGFANG;
        // 根目录部署的正方常用 jwglxt.xxx.edu.cn 这样的主机名
        if (hostOf(url).toLowerCase(Locale.ROOT).startsWith("jwglxt.")) return Vendor.ZHENGFANG;
        return Vendor.OTHER;
    }

    /** 补全 https://、去掉空白；不是 http(s) 网址时返回 null。 */
    public static String normalizeUrl(String input) {
        if (input == null) return null;
        String s = input.trim().replace(" ", "");
        if (s.isEmpty()) return null;
        if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
        try {
            URI uri = URI.create(s);
            if (uri.getHost() == null || !uri.getHost().contains(".")) return null;
            return s;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static String originOf(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme + "://" + uri.getRawAuthority();
    }

    static String pathOf(String url) {
        try {
            String p = URI.create(url).getRawPath();
            return p == null ? "" : p;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * 学校主域名，用于放行统一认证等兄弟子域：jw.abc.edu.cn → abc.edu.cn，
     * jw.abc.com → abc.com。IP 地址或只有两段的域名返回 null（只放行它自己）。
     */
    static String parentDomain(String host) {
        if (host == null) return null;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.matches("[0-9.]+")) return null;
        String[] parts = h.split("\\.");
        // 国家顶级域下的二级公共后缀（edu.cn、ac.cn、gov.cn、edu.hk、com.tw…）要多留一段，
        // 否则会放行整个 *.ac.cn。
        int keep = h.matches(".*\\.(edu|com|org|net|gov|ac)\\.[a-z]{2}") ? 3 : 2;
        if (parts.length <= keep) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - keep; i < parts.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static int indexOfAny(String s, String... keys) {
        for (String k : keys) {
            int i = s.indexOf(k);
            if (i >= 0) return i;
        }
        return -1;
    }

    private static String withSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s : s + "/";
    }
}
