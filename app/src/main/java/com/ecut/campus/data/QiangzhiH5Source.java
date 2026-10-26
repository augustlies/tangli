package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强智「移动教务」手机网页版（常见路径 {@code /sjd/#/...}，接口根如 {@code /bzb_njwhd}）。
 *
 * 页面同目录下的 {@code serverconfig.json} 写着接口根 ApiUrl；登录后 token 只放在 sessionStorage「Token」，
 * 关掉网页就没了，所以只支持「打开网页登录 → 导入此页课表」，不做后台同步，也不碰账号密码。
 *
 * 课表接口 {@code POST /student/curriculum?week=N&kbjcmsid=…} 每次只回一周：逐周取（周数来自 /teachingWeek），
 * 每条课带 {@code classTime}（x0a0b = 星期 x 第 a、b 节）和 {@code classWeekDetails}（",1,2,3,"），
 * 按 课程名+星期+节次+教室+老师 合并各周。第 1 周的 date 列表给出第 1 周周一。
 *
 * 接口字段参考公开的「湖南科技大学强智教务系统 API 文档」整理；代码为烫梨独立实现。
 */
public final class QiangzhiH5Source implements TimetableSource {

    public static final String LABEL = "强智移动版";
    private static final Pattern TERM_ID = Pattern.compile("(\\d{4})-(\\d{4})-(\\d)");
    private static final int MAX_WEEKS = 30;

    @Override
    public SchoolProfile.Vendor vendor() {
        // 没有后台同步：仍算「其他」，学校配置不会被改成强智（/jsxsd/）。
        return SchoolProfile.Vendor.OTHER;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public int detect(String url, String html) {
        int score = 0;
        String path = SchoolProfile.pathOf(url == null ? "" : url).toLowerCase(Locale.ROOT);
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (path.contains("/sjd/") || path.endsWith("/sjd")) score += 45;
        if (u.contains("/njwhd") || u.contains("_njwhd")) score += 45;
        if (html != null) {
            if (html.contains("移动教务")) score += 25;
            if (html.contains("static/config.js") && html.contains("static/icons.js")) score += 20;
            if (html.contains("njwhd")) score += 20;
            if (html.contains("kbtable") || html.contains("kbcontent")) score -= 40; // 电脑版强智课表页
        }
        return Math.max(0, Math.min(100, score));
    }

    @Override
    public String timetableUrl(SchoolProfile p) {
        return null;
    }

    @Override
    public boolean isLoggedInPage(String url) {
        return false;
    }

    @Override
    public boolean isLoginPage(String url) {
        return false;
    }

    @Override
    public String emptyHint(JwglxtFetcher.Result r) {
        if (r != null && !r.warnings.isEmpty()) return r.warnings.get(0);
        return "请先在这个网页里登录，再点「导入此页课表」。";
    }

    /** 同步 XHR 依次取 serverconfig.json → 节次模式 → 学期 → 周数 → 逐周课表，打包成一段 JSON 交回 Java。 */
    @Override
    public String extractionJs(SchoolProfile p, String xnm, String xqm) {
        return "(function(){try{" +
                "var tok=sessionStorage.getItem('Token');if(!tok)return 'NOTOKEN';" +
                "function req(m,u,q){var x=new XMLHttpRequest();x.open(m,u+(q?'?'+q:''),false);" +
                "if(m==='POST')x.setRequestHeader('token',tok);x.send();return x.status==200?x.responseText:'';}" +
                "function js(t){try{return JSON.parse(String(t).replace(/^\\uFEFF/,''));}catch(e){return null;}}" +
                "var dir=location.pathname.substring(0,location.pathname.lastIndexOf('/')+1);" +
                "var cfg=js(req('GET',dir+'serverconfig.json'))||{};" +
                "var api=cfg.ApiUrl||'';" +
                "if(api){try{api=location.origin+new URL(api,location.href).pathname;}catch(e){}}" +
                "var cands=api?[api]:[location.origin+'/bzb_njwhd',location.origin+'/njwhd'];" +
                "var base='',weeks=null;" +
                "for(var i=0;i<cands.length&&!weeks;i++){var w=js(req('POST',cands[i].replace(/\\/$/,'')+'/teachingWeek'));" +
                "if(w&&w.data&&w.data.length){base=cands[i].replace(/\\/$/,'');weeks=w;}}" +
                "if(!weeks)return 'NOAPI';" +
                "var ms=js(req('POST',base+'/Get_sjkbms'));var mid='';" +
                "if(ms&&ms.data&&ms.data.length){mid=ms.data[0].kbjcmsid||'';" +
                "for(var j=0;j<ms.data.length;j++)if(String(ms.data[j].mrms)==='1'){mid=ms.data[j].kbjcmsid||mid;break;}}" +
                "var term=js(req('POST',base+'/currentTerm'));" +
                "var out={term:term&&term.data&&term.data[0]?term.data[0]:null,weeks:{}};" +
                "for(var k=0;k<weeks.data.length&&k<" + MAX_WEEKS + ";k++){var wk=String(weeks.data[k].week);" +
                "var r=js(req('POST',base+'/student/curriculum','week='+encodeURIComponent(wk)+'&kbjcmsid='+encodeURIComponent(mid)));" +
                "if(r&&r.data&&r.data[0])out.weeks[wk]={item:r.data[0].item||[],date:r.data[0].date||[]};}" +
                "return JSON.stringify(out);" +
                "}catch(e){return 'ERR:'+e;}})()";
    }

    @Override
    public JwglxtFetcher.Result parse(String raw) {
        JwglxtFetcher.Result r = new JwglxtFetcher.Result();
        if (raw == null || raw.trim().isEmpty() || raw.startsWith("ERR:")) {
            r.warnings.add("没读到课表，请确认网页已经登录后再试。");
            return r;
        }
        if (raw.startsWith("NOTOKEN")) {
            r.warnings.add("还没登录：请先在这个网页里登录，进到首页后再点「导入此页课表」。");
            return r;
        }
        if (raw.startsWith("NOAPI")) {
            r.warnings.add("登录状态可能过期了：请在网页里重新登录一次，再点「导入此页课表」。");
            return r;
        }
        JSONObject o;
        try {
            o = new JSONObject(raw);
        } catch (Exception e) {
            r.warnings.add("课表数据格式不对");
            return r;
        }
        Timetable.Semester sem = r.timetable.semester;
        JSONObject term = o.optJSONObject("term");
        if (term != null) {
            Matcher m = TERM_ID.matcher(term.optString("semesterId", term.optString("semesterName", "")));
            if (m.find()) {
                sem.id = m.group(1) + "-" + m.group(2) + "-" + m.group(3);
                sem.name = m.group(1) + "-" + m.group(2) + " 学年第" + m.group(3) + "学期";
            }
        }
        JSONObject weeks = o.optJSONObject("weeks");
        int maxWeek = 0;
        Map<String, Slot> slots = new LinkedHashMap<>();
        if (weeks != null) {
            JSONArray names = weeks.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String wk = names.optString(i);
                int weekNo = parseInt(wk, 0);
                if (weekNo > maxWeek) maxWeek = weekNo;
                JSONObject w = weeks.optJSONObject(wk);
                if (w == null) continue;
                if (weekNo == 1 && sem.startDate.isEmpty()) sem.startDate = mondayOf(w.optJSONArray("date"));
                JSONArray items = w.optJSONArray("item");
                for (int k = 0; items != null && k < items.length(); k++) {
                    JSONObject it = items.optJSONObject(k);
                    if (it != null) addItem(it, weekNo, slots, r);
                }
            }
        }
        sem.totalWeeks = Math.max(20, maxWeek);

        Map<String, Timetable.Course> grouped = new LinkedHashMap<>();
        for (Slot s : slots.values()) {
            String key = "qiangzhi_" + s.name + "_" + s.teacher;
            Timetable.Course c = grouped.get(key);
            if (c == null) {
                c = new Timetable.Course();
                c.key = key;
                c.name = s.name;
                c.teacher = s.teacher;
                c.source = Timetable.SOURCE_JWGLXT;
                grouped.put(key, c);
            }
            Timetable.Session ss = new Timetable.Session();
            ss.dayOfWeek = s.day;
            ss.startSection = s.start;
            ss.endSection = s.end;
            ss.weeks.addAll(s.weeks);
            ss.weeksRaw = s.weeksRaw.isEmpty() ? WeekRange.compress(ss.weeks) : s.weeksRaw;
            ss.location = s.location;
            ss.teacher = s.teacher;
            ss.timeStart = s.timeStart;
            c.sessions.add(ss);
        }
        List<Timetable.Course> courses = new ArrayList<>(grouped.values());
        r.mergedDuplicates = JwglxtFetcher.mergeOverlaps(courses);
        r.timetable.courses.addAll(courses);
        CourseColorAssigner.assign(r.timetable);
        r.courseCount = r.timetable.courses.size();
        if (r.courseCount == 0) r.warnings.add("这学期的课表是空的；如果不对，请在网页里重新登录后再导入。");
        return r;
    }

    private static final class Slot {
        String name, teacher, location, weeksRaw, timeStart;
        int day, start, end;
        TreeSet<Integer> weeks = new TreeSet<>();
    }

    private static void addItem(JSONObject it, int weekNo, Map<String, Slot> slots, JwglxtFetcher.Result r) {
        String name = it.optString("courseName", "").trim();
        int[] t = classTime(it.optString("classTime", ""));
        if (name.isEmpty() || t == null) return;
        r.rawRecords++;
        String teacher = it.optString("teacherName", "").trim();
        String location = it.optString("location", "").trim();
        String key = name + "|" + t[0] + "|" + t[1] + "|" + t[2] + "|" + location + "|" + teacher;
        Slot s = slots.get(key);
        if (s == null) {
            s = new Slot();
            s.name = name;
            s.teacher = teacher;
            s.location = location;
            s.day = t[0];
            s.start = t[1];
            s.end = t[2];
            s.weeksRaw = "";
            s.timeStart = it.optString("startTime", "").trim();
            slots.put(key, s);
        }
        List<Integer> ws = weekList(it.optString("classWeekDetails", ""));
        if (ws.isEmpty()) {
            String expr = it.optString("classWeek", "").trim();
            WeekRange.Parsed p = WeekRange.parse(expr);
            if (p.ok()) {
                ws = p.weeks;
                if (s.weeksRaw.isEmpty()) s.weeksRaw = expr;
            }
        }
        if (ws.isEmpty() && weekNo > 0) ws = java.util.Collections.singletonList(weekNo);
        s.weeks.addAll(ws);
    }

    /** "10102" → {星期 1, 第 1 节, 第 2 节}；"5050607" → {5, 5, 7}。认不出返回 null。 */
    static int[] classTime(String s) {
        if (s == null) return null;
        s = s.trim();
        if (!s.matches("[1-7](\\d{2})+")) return null;
        int day = s.charAt(0) - '0';
        int lo = Integer.MAX_VALUE, hi = 0;
        for (int i = 1; i + 2 <= s.length(); i += 2) {
            int n = Integer.parseInt(s.substring(i, i + 2));
            if (n <= 0) continue;
            lo = Math.min(lo, n);
            hi = Math.max(hi, n);
        }
        if (hi == 0) return null;
        return new int[]{day, lo, hi};
    }

    /** ",1,2,3," → [1,2,3]。 */
    static List<Integer> weekList(String s) {
        List<Integer> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("[,，\\s]+")) {
            int n = parseInt(part.trim(), 0);
            if (n > 0 && n <= 60 && !out.contains(n)) out.add(n);
        }
        return out;
    }

    private static String mondayOf(JSONArray dates) {
        if (dates == null) return "";
        for (int i = 0; i < dates.length(); i++) {
            JSONObject d = dates.optJSONObject(i);
            if (d == null) continue;
            String rq = d.optString("mxrq", "");
            if (("1".equals(d.optString("xqid")) || "一".equals(d.optString("xqmc")))
                    && rq.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return rq;
            }
        }
        return "";
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
}
