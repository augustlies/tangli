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
 * 课表分享码：只带课程名、地点、时间和周次（不带教师、学号等），
 * 压缩后 base64url，前缀「烫梨课表1:」。可做成二维码或直接发文字。
 */
public final class TimetableShare {

    private TimetableShare() {}

    public static final String PREFIX = "TLKB1:";

    public static final class Shared {
        public String owner = "";
        public String semesterId = "";
        public String startDate = "";
        public Timetable timetable;
    }

    public static String encode(Timetable t, String owner) throws JSONException {
        JSONObject o = new JSONObject();
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
        if (text == null) return null;
        int i = text.indexOf(PREFIX);
        if (i < 0) return null;
        int j = i + PREFIX.length();
        int k = j;
        while (k < text.length() && B64.isChar(text.charAt(k))) k++;
        try {
            byte[] packed = B64.decode(text.substring(j, k));
            Inflater inf = new Inflater(true);
            inf.setInput(packed);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int guard = 0;
            while (!inf.finished() && guard++ < 10_000) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                bos.write(buf, 0, n);
            }
            inf.end();
            JSONObject o = new JSONObject(bos.toString("UTF-8"));
            Shared sh = new Shared();
            sh.owner = o.optString("o", "");
            sh.semesterId = o.optString("s", "");
            sh.startDate = o.optString("d", "");
            Timetable t = new Timetable();
            t.semester.id = sh.semesterId;
            t.semester.startDate = sh.startDate;
            JSONArray cs = o.getJSONArray("c");
            for (int a = 0; a < cs.length(); a++) {
                JSONObject co = cs.getJSONObject(a);
                Timetable.Course c = new Timetable.Course();
                c.name = co.optString("n", "");
                c.key = "shared_" + a;
                JSONArray ss = co.getJSONArray("t");
                for (int b = 0; b < ss.length(); b++) {
                    JSONArray x = ss.getJSONArray(b);
                    Timetable.Session s = new Timetable.Session();
                    s.dayOfWeek = x.getInt(0);
                    s.startSection = x.getInt(1);
                    s.endSection = x.getInt(2);
                    s.weeksRaw = x.getString(3);
                    WeekRange.Parsed p = WeekRange.parse(s.weeksRaw);
                    s.weeks = p.ok() ? new ArrayList<>(p.weeks) : new ArrayList<>();
                    s.location = x.optString(4, "");
                    s.timeStart = x.optString(5, "");
                    s.timeEnd = x.optString(6, "");
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
