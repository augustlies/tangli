package com.ecut.campus;

import java.net.URI;
import java.util.Locale;

/**
 * 校园 URL 的纯 Java 策略，便于在 JVM 单测中验证，不依赖 Android Uri。
 */
final class CampusUrlPolicy {

    private static final String BARE_HOST = "172.20.130.13";
    private static final String PROXY_HOST = "172-20-130-13.atrust.ecut.edu.cn";
    private static final String EHALL_DIRECT_HOST = "ehall.ecut.edu.cn";
    private static final String EHALL_PROXY_HOST =
            "ehall-ecut-edu-cn-s.atrust.ecut.edu.cn";

    private CampusUrlPolicy() {}

    static String rewriteBareCampusHost(String url) {
        if (url == null || url.isEmpty()) return url;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return url;
        }
        if (!BARE_HOST.equals(uri.getHost())) return url;

        StringBuilder out = new StringBuilder("https://").append(PROXY_HOST);
        out.append(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) out.append('?').append(uri.getRawQuery());
        if (uri.getRawFragment() != null) out.append('#').append(uri.getRawFragment());
        return out.toString();
    }

    static boolean isAllowedCampusUrl(String url) {
        if (url == null) return false;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.endsWith(".ecut.edu.cn")
                || h.equals("ecut.edu.cn")
                || h.equals(BARE_HOST);
    }

    static boolean isEhallUrl(String url) {
        if (url == null) return false;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.equals(EHALL_DIRECT_HOST) || h.equals(EHALL_PROXY_HOST);
    }
}
