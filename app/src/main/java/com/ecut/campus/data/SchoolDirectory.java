package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 公开整理的学校 → 教务入口目录（assets/school-directory.json，来源见 NOTICE）。
 * 只用来帮同学找到登录页：网址没逐一核实，可能过时，所以选中后仍当作「自定义学校」，
 * 真正的系统类型由登录后的页面识别。
 */
public final class SchoolDirectory {

    public static final class Entry {
        public final String name;
        /** 原始类型：zhengfang_new / qiangzhi / kingosoft_new / wisedu / chaoxing / urp ... */
        public final String type;
        public final String url;

        public Entry(String name, String type, String url) {
            this.name = name;
            this.type = type == null ? "" : type;
            this.url = url;
        }

        /** 给人看的系统名。 */
        public String typeLabel() {
            return SchoolDirectory.typeLabel(type);
        }

        /** 烫梨能不能导入这一类系统的课表。 */
        public boolean importable() {
            SchoolProfile.Vendor v = vendorOf(type);
            return v == SchoolProfile.Vendor.ZHENGFANG || v == SchoolProfile.Vendor.QIANGZHI
                    || (type != null && type.toLowerCase(Locale.ROOT).startsWith("wisedu"));
        }
    }

    private final List<Entry> entries;

    private SchoolDirectory(List<Entry> entries) {
        this.entries = entries;
    }

    public static SchoolDirectory parse(String json) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("schools");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray row = arr.optJSONArray(i);
                    if (row == null || row.length() < 3) continue;
                    String name = row.optString(0, "").trim();
                    String url = row.optString(2, "").trim();
                    if (name.isEmpty() || !url.matches("(?i)^https?://.+")) continue;
                    list.add(new Entry(name, row.optString(1, ""), url));
                }
            }
        } catch (Exception ignored) {
        }
        return new SchoolDirectory(Collections.unmodifiableList(list));
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> all() {
        return entries;
    }

    /**
     * 按校名搜索：完整包含优先，其次「去掉大学/学院后」包含，再其次按字逐个出现（如「华理」→ 华东理工）。
     * 空查询返回空列表。
     */
    public List<Entry> search(String query, int limit) {
        List<Entry> out = new ArrayList<>();
        String q = norm(query);
        if (q.isEmpty()) return out;
        List<Entry> starts = new ArrayList<>();
        List<Entry> contains = new ArrayList<>();
        List<Entry> fuzzy = new ArrayList<>();
        for (Entry e : entries) {
            String n = norm(e.name);
            if (n.startsWith(q)) starts.add(e);
            else if (n.contains(q)) contains.add(e);
            else if (q.length() >= 2 && subsequence(q, n)) fuzzy.add(e);
        }
        for (List<Entry> part : java.util.Arrays.asList(starts, contains, fuzzy)) {
            for (Entry e : part) {
                if (out.size() >= limit) return out;
                out.add(e);
            }
        }
        return out;
    }

    /** 选中目录里的学校 → 自定义学校。只有网址本身能看出系统类型时才信目录给的类型。 */
    public static SchoolProfile toProfile(Entry e) {
        SchoolProfile.Vendor fromUrl = SchoolProfile.guessVendor(e.url);
        SchoolProfile.Vendor listed = vendorOf(e.type);
        SchoolProfile.Vendor v = fromUrl == listed ? listed : SchoolProfile.Vendor.OTHER;
        SchoolProfile p = SchoolProfile.custom(e.url, v);
        if (p == null) return null;
        p.name = e.name;
        return p;
    }

    public static SchoolProfile.Vendor vendorOf(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.startsWith("zhengfang")) return SchoolProfile.Vendor.ZHENGFANG;
        if (t.startsWith("qiangzhi")) return SchoolProfile.Vendor.QIANGZHI;
        return SchoolProfile.Vendor.OTHER;
    }

    public static String typeLabel(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.startsWith("zhengfang")) return "正方";
        if (t.startsWith("qiangzhi")) return "强智";
        if (t.startsWith("kingosoft")) return "青果";
        if (t.startsWith("wisedu")) return "金智";
        if (t.startsWith("chaoxing")) return "超星";
        if (t.startsWith("urp")) return "URP";
        if (t.startsWith("south_soft")) return "南软";
        return "其他";
    }

    static String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s（）()·\\-—]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean subsequence(String q, String n) {
        int j = 0;
        for (int i = 0; i < n.length() && j < q.length(); i++) {
            if (n.charAt(i) == q.charAt(j)) j++;
        }
        return j == q.length();
    }
}
