package com.ecut.campus.data;

/** 比较 "v1.2.3" / "1.2.3-gauge" 这类版本号。只看数字段，后缀忽略。 */
public final class VersionCompare {
    private VersionCompare() {}

    public static int[] parse(String v) {
        if (v == null) return new int[]{0, 0, 0};
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isDigit(c) || c == '.')) { cut = i; break; }
        }
        String[] parts = s.substring(0, cut).split("\\.");
        int[] out = new int[]{0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** @return 正数表示 a 比 b 新 */
    public static int compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        return 0;
    }

    public static boolean isNewer(String remote, String local) {
        return compare(remote, local) > 0;
    }
}
