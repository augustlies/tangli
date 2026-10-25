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
    public static final int SECTION_COUNT = 10;
    public static final String DEFAULT_FIRST_CLASS_START = "08:00";

    private static final int[] DEFAULT_DURATIONS = {
            45, 45, 45, 45, 45, 45, 45, 45, 45, 45
    };
    private static final int[] DEFAULT_BREAKS = {
            10, 20, 10, 140, 10, 20, 10, 80, 10, 0
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
    public List<Section> sections = new ArrayList<>();

    public ScheduleSettings() {
        resetToDefaults();
    }

    public static ScheduleSettings createDefault() {
        return new ScheduleSettings();
    }

    public void resetToDefaults() {
        firstClassStart = DEFAULT_FIRST_CLASS_START;
        sections.clear();
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections.add(new Section(i + 1, DEFAULT_DURATIONS[i], DEFAULT_BREAKS[i]));
        }
    }

    public Section section(int sectionNumber) {
        int idx = Math.max(0, Math.min(SECTION_COUNT - 1, sectionNumber - 1));
        if (sections.size() < SECTION_COUNT) normalizeSections();
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
        int n = Math.min(SECTION_COUNT, Math.min(start.length, end.length) - 1);
        if (n < 1) return;
        firstClassStart = formatHm(start[1]);
        for (int i = 1; i <= n; i++) {
            Section s = section(i);
            s.durationMinutes = Math.max(1, end[i] - start[i]);
            if (i < n) s.breakAfterMinutes = Math.max(0, start[i + 1] - end[i]);
        }
    }

    public String rangeLabel(int sectionNumber) {
        return startTime(sectionNumber) + "-" + endTime(sectionNumber);
    }

    public JSONObject toJson() throws JSONException {
        normalizeSections();
        JSONObject o = new JSONObject();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("firstClassStart", firstClassStart);
        JSONArray arr = new JSONArray();
        for (Section s : sections) arr.put(s.toJson());
        o.put("sections", arr);
        return o;
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

        JSONArray arr = o.optJSONArray("sections");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject so = arr.optJSONObject(i);
                if (so == null) continue;
                int number = so.optInt("section", i + 1);
                if (number < 1 || number > SECTION_COUNT) continue;
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
        while (sections.size() < SECTION_COUNT) {
            int i = sections.size();
            sections.add(new Section(i + 1, DEFAULT_DURATIONS[i], DEFAULT_BREAKS[i]));
        }
        if (sections.size() > SECTION_COUNT) {
            sections = new ArrayList<>(sections.subList(0, SECTION_COUNT));
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
