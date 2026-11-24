package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 课表分享码：只带课程名、地点、时间和周次（不带教师、学号等），1.2.6 起可附带作息时间，
 * 压缩后 base64url，前缀「烫梨课表1:」。可做成二维码或直接发文字。
 */
public final class TimetableShare {

    private TimetableShare() {}

    private static final int MAX_SHARE_TEXT_CHARS = 3 * 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = TimetableStore.MAX_IMPORT_CHARS;
    private static final int MAX_SHARED_COURSE_NAME_CHARS = 256;
    private static final int MAX_SHARED_FIELD_CHARS = 512;

    public static final String PREFIX = "TLKB1:";

    public static final class Shared {
        public String owner = "";
        public String semesterId = "";
        public String startDate = "";
        public Timetable timetable;
        /** 分享者的作息（紧凑格式 {f, n, d[], b[]}），旧版分享码没有则为 null。 */
        public JSONObject schedule;
    }

    public static String encode(Timetable t, String owner) throws JSONException {
        return encode(t, owner, null);
    }

    /** @param schedule 作息（紧凑格式），可空；旧版烫梨解析时会忽略这个字段 */
    public static String encode(Timetable t, String owner, JSONObject schedule) throws JSONException {
        JSONObject o = new JSONObject();
        if (schedule != null) o.put("w", schedule);
        o.put("o", owner == null ? "" : owner);
        o.put("s", t.semester.id);
        o.put("d", t.semester.startDate);
        JSONArray cs = new JSONArray();
        for (Timetable.Course c : t.courses) {
            JSONObject co = new JSONObject();
            co.put("n", c.name);
            JSONArray ss = new JSONArray();
            for (Timetable.Session s : c.sessions) {
                JSONArray a = new JSONArray();
                a.put(s.dayOfWeek).put(s.startSection).put(s.endSection)
                        .put(WeekRange.compress(s.weeks)).put(s.location == null ? "" : s.location);
                if (s.hasCustomTime()) a.put(s.timeStart).put(s.timeEnd); // 1.1.0 起：自定义时间（旧版忽略）
                ss.put(a);
            }
            co.put("t", ss);
            cs.put(co);
        }
        o.put("c", cs);
        byte[] raw = o.toString().getBytes(StandardCharsets.UTF_8);
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
        def.setInput(raw);
        def.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!def.finished()) bos.write(buf, 0, def.deflate(buf));
        def.end();
        return PREFIX + B64.encode(bos.toByteArray());
    }

    /** 从任意文本里找出分享码并解析（允许前后夹着微信里的其他文字）。失败返回 null。 */
    public static Shared decode(String text) {
        if (text == null || text.length() > MAX_SHARE_TEXT_CHARS) return null;
        int i = text.indexOf(PREFIX);
        if (i < 0) return null;
        int j = i + PREFIX.length();
        int k = j;
        while (k < text.length() && B64.isChar(text.charAt(k))) k++;
        if (k - j > ((MAX_UNCOMPRESSED_BYTES + 2) / 3) * 4) return null;
        try {
            byte[] packed = B64.decode(text.substring(j, k));
            if (packed.length > MAX_UNCOMPRESSED_BYTES) return null;
            Inflater inf = new Inflater(true);
            inf.setInput(packed);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int guard = 0;
            boolean complete;
            try {
                while (!inf.finished() && guard++ < 10_000) {
                    int n = inf.inflate(buf);
                    if (n == 0) break;
                    if (n > MAX_UNCOMPRESSED_BYTES - bos.size()) {
                        throw new DataFormatException("share exceeds size limit");
                    }
                    bos.write(buf, 0, n);
                }
                complete = inf.finished();
            } finally {
                inf.end();
            }
            if (!complete) return null;
            String json = bos.toString("UTF-8");
            TimetableStore.ensureBoundedJsonNesting(json);
            JSONObject o = new JSONObject(json);
            Shared sh = new Shared();
            sh.owner = o.optString("o", "");
            sh.semesterId = o.optString("s", "");
            sh.startDate = o.optString("d", "");
            sh.schedule = o.optJSONObject("w");
            if (sh.owner.length() > 4096 || sh.semesterId.length() > 4096 || sh.startDate.length() > 4096
                    || !validScheduleSize(sh.schedule)) return null;
            Timetable t = new Timetable();
            t.semester.id = sh.semesterId;
            t.semester.startDate = sh.startDate;
            JSONArray cs = o.getJSONArray("c");
            if (cs.length() > TimetableStore.MAX_IMPORT_COURSES) return null;
            int totalSessions = 0;
            for (int a = 0; a < cs.length(); a++) {
                JSONObject co = cs.getJSONObject(a);
                Timetable.Course c = new Timetable.Course();
                c.name = co.optString("n", "");
                if (c.name.length() > MAX_SHARED_COURSE_NAME_CHARS) return null;
                c.key = "shared_" + a;
                JSONArray ss = co.getJSONArray("t");
                if (ss.length() > TimetableStore.MAX_IMPORT_SESSIONS_PER_COURSE
                        || totalSessions > TimetableStore.MAX_IMPORT_SESSIONS - ss.length()) return null;
                totalSessions += ss.length();
                for (int b = 0; b < ss.length(); b++) {
                    JSONArray x = ss.getJSONArray(b);
                    if (x.length() < 5 || x.length() > 7) return null;
                    Timetable.Session s = new Timetable.Session();
                    s.dayOfWeek = x.getInt(0);
                    s.startSection = x.getInt(1);
                    s.endSection = x.getInt(2);
                    if (s.dayOfWeek < 1 || s.dayOfWeek > 7 || s.startSection < 1
                            || s.startSection > 99 || s.endSection < s.startSection || s.endSection > 99) return null;
                    s.weeksRaw = x.getString(3);
                    if (s.weeksRaw.length() > 4096) return null;
                    WeekRange.Parsed p = WeekRange.parse(s.weeksRaw);
                    if (p.weeks.size() > WeekRange.MAX_WEEK) return null;
                    s.weeks = p.ok() ? new ArrayList<>(p.weeks) : new ArrayList<>();
                    s.location = x.optString(4, "");
                    if (s.location.length() > MAX_SHARED_FIELD_CHARS) return null;
                    s.timeStart = x.optString(5, "");
                    s.timeEnd = x.optString(6, "");
                    if (s.timeStart.length() > MAX_SHARED_FIELD_CHARS
                            || s.timeEnd.length() > MAX_SHARED_FIELD_CHARS) return null;
                    c.sessions.add(s);
                }
                t.courses.add(c);
            }
            sh.timetable = t;
            return sh;
        } catch (JSONException | DataFormatException | java.io.UnsupportedEncodingException
                 | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean validScheduleSize(JSONObject schedule) {
        if (schedule == null) return true;
        JSONArray durations = schedule.optJSONArray("d");
        JSONArray breaks = schedule.optJSONArray("b");
        return (durations == null || durations.length() <= 16)
                && (breaks == null || breaks.length() <= 16);
    }

    /** 不依赖 java.util.Base64（API 26+）的 base64url。 */
    static final class B64 {
        private static final char[] ALPHA =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

        static boolean isChar(char ch) {
            return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_';
        }

        static String encode(byte[] in) {
            StringBuilder sb = new StringBuilder((in.length + 2) / 3 * 4);
            for (int i = 0; i < in.length; i += 3) {
                int b = (in[i] & 0xFF) << 16;
                if (i + 1 < in.length) b |= (in[i + 1] & 0xFF) << 8;
                if (i + 2 < in.length) b |= (in[i + 2] & 0xFF);
                sb.append(ALPHA[(b >> 18) & 63]).append(ALPHA[(b >> 12) & 63]);
                if (i + 1 < in.length) sb.append(ALPHA[(b >> 6) & 63]);
                if (i + 2 < in.length) sb.append(ALPHA[b & 63]);
            }
            return sb.toString();
        }

        static byte[] decode(String s) {
            int[] rev = new int[128];
            java.util.Arrays.fill(rev, -1);
            for (int i = 0; i < ALPHA.length; i++) rev[ALPHA[i]] = i;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int buf = 0, bits = 0;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch >= 128 || rev[ch] < 0) throw new IllegalArgumentException("bad char");
                buf = (buf << 6) | rev[ch];
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    out.write((buf >> bits) & 0xFF);
                }
            }
            return out.toByteArray();
        }
    }

    static List<Integer> list(int... xs) {
        List<Integer> l = new ArrayList<>();
        for (int x : xs) l.add(x);
        return l;
    }
}
