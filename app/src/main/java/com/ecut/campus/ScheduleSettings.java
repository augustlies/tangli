package com.ecut.campus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全局作息配置：第 1 节开始时间、每节时长、每节课后休息。
 *
 * 纯 Java 模型，不依赖 Android，便于 JVM 单测。时间按顺序顺延计算：
 * 第 N 节开始 = 第 1 节开始 + 前面所有节的时长与课间休息。
 */
public final class ScheduleSettings {

    public static final int SCHEMA_VERSION = 1;
    /** 每天最多几节：不同学校从 8 节到 16 节不等，作息表按最大值存，sectionCount 决定显示几节。 */
    public static final int MAX_SECTIONS = 16;
    public static final int MIN_SECTIONS = 8;
    public static final int DEFAULT_SECTION_COUNT = 10;
    public static final String DEFAULT_FIRST_CLASS_START = "08:00";

    private static final int[] DEFAULT_DURATIONS = {
            45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45
    };
    private static final int[] DEFAULT_BREAKS = {
            10, 20, 10, 140, 10, 20, 10, 80, 10, 10, 10, 10, 10, 10, 10, 0
    };

    public static final class Section {
        public int section;
        public int durationMinutes = 45;
        public int breakAfterMinutes = 0;

        public Section() {}

        public Section(int section, int durationMinutes, int breakAfterMinutes) {
            this.section = section;
            this.durationMinutes = durationMinutes;
            this.breakAfterMinutes = breakAfterMinutes;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("section", section);
            o.put("durationMinutes", durationMinutes);
            o.put("breakAfterMinutes", breakAfterMinutes);
            return o;
        }
    }

    public String firstClassStart = DEFAULT_FIRST_CLASS_START;
    /** 每天显示几节（MIN_SECTIONS..MAX_SECTIONS）。 */
    public int sectionCount = DEFAULT_SECTION_COUNT;
    public List<Section> sections = new ArrayList<>();

    public ScheduleSettings() {
        resetToDefaults();
    }

    public static ScheduleSettings createDefault() {
        return new ScheduleSettings();
    }

    public void resetToDefaults() {
        firstClassStart = DEFAULT_FIRST_CLASS_START;
        sectionCount = DEFAULT_SECTION_COUNT;
        sections.clear();
        for (int i = 0; i < MAX_SECTIONS; i++) {
            sections.add(new Section(i + 1, DEFAULT_DURATIONS[i], DEFAULT_BREAKS[i]));
        }
    }

    public Section section(int sectionNumber) {
        int idx = Math.max(0, Math.min(MAX_SECTIONS - 1, sectionNumber - 1));
        if (sections.size() < MAX_SECTIONS) normalizeSections();
        return sections.get(idx);
    }

    public int startMinutes(int sectionNumber) {
        int first = parseHm(firstClassStart);
        if (first < 0) first = parseHm(DEFAULT_FIRST_CLASS_START);
        int minutes = first;
        for (int i = 1; i < sectionNumber; i++) {
            Section s = section(i);
            minutes += Math.max(1, s.durationMinutes);
            minutes += Math.max(0, s.breakAfterMinutes);
        }
        return minutes;
    }

    public int endMinutes(int sectionNumber) {
        return startMinutes(sectionNumber) + Math.max(1, section(sectionNumber).durationMinutes);
    }

    public String startTime(int sectionNumber) {
        return formatHm(startMinutes(sectionNumber));
    }

    public String endTime(int sectionNumber) {
        return formatHm(endMinutes(sectionNumber));
    }

    /** 按每节起止（分钟，下标 = 节次）回写成「第 1 节开始 + 时长 + 课间」。 */
    public void applyRanges(int[] start, int[] end) {
        normalizeSections();
        int n = Math.min(MAX_SECTIONS, Math.min(start.length, end.length) - 1);
        if (n < 1) return;
        if (n > sectionCount) sectionCount = clamp(n, MIN_SECTIONS, MAX_SECTIONS);
        firstClassStart = formatHm(start[1]);
        for (int i = 1; i <= n; i++) {
            Section s = section(i);
            s.durationMinutes = Math.max(1, end[i] - start[i]);
            if (i < n) s.breakAfterMinutes = Math.max(0, start[i + 1] - end[i]);
        }
    }

    public int sectionCount() {
        return clamp(sectionCount, MIN_SECTIONS, MAX_SECTIONS);
    }

    public void setSectionCount(int n) {
        sectionCount = clamp(n, MIN_SECTIONS, MAX_SECTIONS);
    }

    /**
     * 导入的课表用到了更靠后的节次（比如第 12 节）时自动扩到那一节；只会变多不会变少。
     * 返回是否改动。
     */
    public boolean ensureCovers(int maxSection) {
        int want = clamp(maxSection, MIN_SECTIONS, MAX_SECTIONS);
        if (want <= sectionCount()) return false;
        normalizeSections();
        // 原来最后一节后面没留课间的话，补一个默认 10 分钟，免得新加的节次贴着上一节
        for (int i = sectionCount(); i < want; i++) {
            Section prev = section(i);
            if (prev.breakAfterMinutes == 0) prev.breakAfterMinutes = 10;
        }
        sectionCount = want;
        return true;
    }

    /** 课表里用到的最大节次（没有课返回 0）。 */
    public static int maxSectionUsed(com.ecut.campus.data.Timetable t) {
        int max = 0;
        if (t == null || t.courses == null) return 0;
        for (com.ecut.campus.data.Timetable.Course c : t.courses) {
            if (c.sessions == null) continue;
            for (com.ecut.campus.data.Timetable.Session s : c.sessions) {
                max = Math.max(max, Math.max(s.startSection, s.endSection));
            }
        }
        return Math.min(MAX_SECTIONS, max);
    }

    public String rangeLabel(int sectionNumber) {
        return startTime(sectionNumber) + "-" + endTime(sectionNumber);
    }

    public JSONObject toJson() throws JSONException {
        normalizeSections();
        JSONObject o = new JSONObject();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("firstClassStart", firstClassStart);
        o.put("sectionCount", sectionCount());
        JSONArray arr = new JSONArray();
        for (Section s : sections) arr.put(s.toJson());
        o.put("sections", arr);
        return o;
    }

    /** 分享码里用的紧凑作息：f=第 1 节开始，n=每天几节，d=每节时长，b=课后休息（只写前 n 节）。 */
    public JSONObject toShare() throws JSONException {
        normalizeSections();
        JSONObject o = new JSONObject();
        o.put("f", firstClassStart);
        int n = sectionCount();
        o.put("n", n);
        JSONArray d = new JSONArray(), b = new JSONArray();
        for (int i = 1; i <= n; i++) {
            d.put(section(i).durationMinutes);
            b.put(section(i).breakAfterMinutes);
        }
        o.put("d", d);
        o.put("b", b);
        return o;
    }

    /** 解析分享码里的作息；格式不对返回 null。没写到的节次沿用默认值。 */
    public static ScheduleSettings fromShare(JSONObject o) {
        if (o == null || parseHm(o.optString("f", "")) < 0) return null;
        JSONArray d = o.optJSONArray("d"), b = o.optJSONArray("b");
        if (d == null || b == null || d.length() == 0) return null;
        ScheduleSettings s = createDefault();
        s.firstClassStart = o.optString("f");
        s.sectionCount = clamp(o.optInt("n", d.length()), MIN_SECTIONS, MAX_SECTIONS);
        for (int i = 0; i < Math.min(MAX_SECTIONS, d.length()); i++) {
            Section x = s.sections.get(i);
            x.durationMinutes = clamp(d.optInt(i, x.durationMinutes), 1, 300);
            if (i < b.length()) x.breakAfterMinutes = clamp(b.optInt(i, x.breakAfterMinutes), 0, 600);
        }
        return s;
    }

    /** 两份作息在前 max(两者节数) 节上的上下课时间是否完全一样。 */
    public boolean sameTimesAs(ScheduleSettings o) {
        if (o == null) return false;
        int n = Math.max(sectionCount(), o.sectionCount());
        for (int i = 1; i <= n; i++) {
            if (startMinutes(i) != o.startMinutes(i) || endMinutes(i) != o.endMinutes(i)) return false;
        }
        return true;
    }

    public static ScheduleSettings fromJson(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public static ScheduleSettings fromJson(JSONObject o) throws JSONException {
        int ver = o == null ? 0 : o.optInt("schemaVersion", 0);
        if (ver > SCHEMA_VERSION) {
            throw new JSONException("作息配置版本 " + ver + " 高于当前支持版本 " + SCHEMA_VERSION);
        }

        ScheduleSettings out = createDefault();
        if (o == null) return out;

        String first = o.optString("firstClassStart", DEFAULT_FIRST_CLASS_START);
        if (parseHm(first) >= 0) out.firstClassStart = first;
        out.sectionCount = clamp(o.optInt("sectionCount", DEFAULT_SECTION_COUNT), MIN_SECTIONS, MAX_SECTIONS);

        JSONArray arr = o.optJSONArray("sections");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject so = arr.optJSONObject(i);
                if (so == null) continue;
                int number = so.optInt("section", i + 1);
                if (number < 1 || number > MAX_SECTIONS) continue;
                Section s = out.sections.get(number - 1);
                s.durationMinutes = clamp(so.optInt("durationMinutes", s.durationMinutes), 1, 300);
                s.breakAfterMinutes = clamp(so.optInt("breakAfterMinutes", s.breakAfterMinutes), 0, 600);
            }
        }
        return out;
    }

    public static int parseHm(String hm) {
        if (hm == null) return -1;
        String[] p = hm.trim().split(":");
        if (p.length != 2) return -1;
        try {
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatHm(int minutes) {
        int day = 24 * 60;
        int m = ((minutes % day) + day) % day;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    private void normalizeSections() {
        if (sections == null) sections = new ArrayList<>();
        while (sections.size() < MAX_SECTIONS) {
            int i = sections.size();
            sections.add(new Section(i + 1, DEFAULT_DURATIONS[i], DEFAULT_BREAKS[i]));
        }
        if (sections.size() > MAX_SECTIONS) {
            sections = new ArrayList<>(sections.subList(0, MAX_SECTIONS));
        }
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (s == null) {
                s = new Section(i + 1, DEFAULT_DURATIONS[i], DEFAULT_BREAKS[i]);
                sections.set(i, s);
            }
            s.section = i + 1;
            s.durationMinutes = clamp(s.durationMinutes, 1, 300);
            s.breakAfterMinutes = clamp(s.breakAfterMinutes, 0, 600);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
