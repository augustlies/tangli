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
 * 金智教务（网上办事大厅 ehall 里的「我的课表」应用，路径 {@code /jwapp/sys/wdkb/}）。
 *
 * 课表接口 {@code POST jwapp/sys/wdkb/modules/xskcb/xskcb.do}（参数 XNXQDM=2025-2026-1）回 JSON，
 * {@code datas.xskcb.rows[]} 每条带 KCM 课程名、SKJS 老师、JASMC 教室、SKXQ 星期、KSJC/JSJC 起止节、
 * SKZC 周次位图（第 i 位为 1 = 第 i+1 周上课）。当前学期取自 {@code modules/jshkcb/dqxnxq.do}。
 *
 * ehall 的应用要先在大厅里点开（会话才会激活），而且常常在另一个子域（如 ehallapp.*），
 * 所以只支持「打开我的课表 → 导入此页课表」，不做后台同步，也不碰账号密码。
 * 接口字段参考公开资料整理；代码为烫梨独立实现。
 */
public final class WiseduSource implements TimetableSource {

    public static final String LABEL = "金智";
    private static final Pattern TERM_ID = Pattern.compile("(\\d{4})-(\\d{4})-(\\d)");

    @Override
    public SchoolProfile.Vendor vendor() {
        // 没有后台同步：仍算「其他」。
        return SchoolProfile.Vendor.OTHER;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public int detect(String url, String html) {
        int score = 0;
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains("/jwapp/sys/") || u.contains("/gsapp/sys/")) score += 55;
        if (u.contains("wdkb")) score += 25;
        if (u.contains("ehall")) score += 20;
        if (u.contains("/portal/html/select_role") || u.contains("/new/index.html")) score += 20;
        if (html != null) {
            if (html.contains("jwapp/sys")) score += 25;
            if (html.contains("ehall") || html.contains("网上办事")) score += 10;
            if (html.contains("kbtable") || html.contains("kbcontent") || html.contains("jwglxt")) score -= 40;
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
        return OPEN_APP_HINT;
    }

    static final String OPEN_APP_HINT =
            "这是金智网上办事大厅：请先选「学生」身份，在大厅里搜索并打开「我的课表」，等课表显示出来后再点「导入此页课表」。";

    @Override
    public String extractionJs(SchoolProfile p, String xnm, String xqm) {
        String want = "";
        if (xnm != null && xnm.matches("\\d{4}")) {
            int y = Integer.parseInt(xnm);
            int n = "12".equals(xqm) ? 2 : ("16".equals(xqm) ? 3 : 1);
            want = y + "-" + (y + 1) + "-" + n;
        }
        return "(function(){try{" +
                "var p=location.pathname;" +
                "function post(u,b){var x=new XMLHttpRequest();x.open('POST',u,false);" +
                "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded;charset=UTF-8');" +
                "x.send(b||'');return {s:x.status,t:x.responseText||''};}" +
                "function js(t){try{return JSON.parse(t);}catch(e){return null;}}" +
                // 研究生「我的课表」（如南京大学 gsapp/sys/wdkbapp）：学期 kfdxnxqcx，课表 xsjxrwcx，时间地点是一段文字 PKSJDD
                "var g=p.indexOf('/gsapp/sys/');" +
                "if(g>=0){var gb=location.origin+p.substring(0,g)+'/gsapp/sys/'+p.substring(g+11).split('/')[0]+'/modules/xskcb/';" +
                "var tq=js(post(gb+'kfdxnxqcx.do').t),terms=[];try{terms=tq.datas.kfdxnxqcx.rows;}catch(e){}" +
                "if(!terms.length)return 'NODATA|gsapp kfdxnxqcx';" +
                "var cur=null;for(var t=0;t<terms.length;t++){if(terms[t].SFDQXQ==='1'||terms[t].SFDQXQ===1){cur=terms[t];}}" +
                "if(!cur){terms.sort(function(a,b){return (a.PX||0)-(b.PX||0);});cur=terms[0];}" +
                "var gr=js(post(gb+'xsjxrwcx.do','XNXQDM='+encodeURIComponent(cur.XNXQDM)).t),grows=null;" +
                "try{grows=gr.datas.xsjxrwcx.rows;}catch(e){}" +
                "if(!grows)return 'NODATA|gsapp xsjxrwcx';" +
                "return JSON.stringify({term:String(cur.XNXQDM),termName:cur.XNXQDM_DISPLAY||'',grad:grows});}" +
                "var i=p.indexOf('/jwapp/sys/');" +
                "if(i<0)return 'NOAPP|'+location.host+p;" +
                "var root=location.origin+p.substring(0,i)+'/jwapp/sys/';" +
                // 当前应用名（wdkb / 其他课表应用），优先用它，再退回 wdkb
                "var app=p.substring(i+11).split('/')[0];var apps=[app];if(app!=='wdkb')apps.push('wdkb');" +
                "var diag=[];" +
                "for(var a=0;a<apps.length;a++){var base=root+apps[a]+'/modules/';" +
                "var term='" + want + "';" +
                "if(!term){var q=post(base+'jshkcb/dqxnxq.do');var o=js(q.t);" +
                "try{term=o.datas.dqxnxq.rows[0].DM;}catch(e){}diag.push(apps[a]+' dqxnxq '+q.s);}" +
                "if(!term){var d=new Date(),y=d.getFullYear(),m=d.getMonth()+1;" +
                "term=(m>=8?y:y-1)+'-'+(m>=8?y+1:y)+'-'+(m>=2&&m<8?2:1);}" +
                "var eps=['xskcb/xskcb.do','xskcb/cxxszhxqkb.do'];" +
                "for(var e=0;e<eps.length;e++){var r=post(base+eps[e],'XNXQDM='+encodeURIComponent(term));" +
                "var j=js(r.t);diag.push(apps[a]+' '+eps[e]+' '+r.s+' '+r.t.length+'B');" +
                "if(j&&j.datas){var rows=null;for(var k in j.datas){if(j.datas[k]&&j.datas[k].rows){rows=j.datas[k].rows;break;}}" +
                "if(rows)return JSON.stringify({term:term,rows:rows});}}}" +
                // 接口不回数据时读页面上的列表视图（南京大学本科「我的课表」→ 列表）
                "var rowsEl=document.querySelectorAll('table tbody tr'),dom=[];" +
                "for(var r2=0;r2<rowsEl.length;r2++){var td=rowsEl[r2].querySelectorAll('td');" +
                "if(td.length>6&&/节/.test(td[6].textContent))dom.push({name:td[2].textContent.trim(),teacher:td[4].textContent.trim(),tl:td[6].textContent.trim()});}" +
                "if(dom.length){var tn=document.querySelector('#dqxnxqkclb');" +
                "return JSON.stringify({term:'',termName:tn?tn.textContent.trim():'',dom:dom});}" +
                "return 'NODATA|'+diag.join(' ; ');" +
                "}catch(e){return 'ERR:'+e;}})()";
    }

    @Override
    public JwglxtFetcher.Result parse(String raw) {
        JwglxtFetcher.Result r = new JwglxtFetcher.Result();
        if (raw == null || raw.trim().isEmpty() || raw.startsWith("ERR:")) {
            r.warnings.add("没读到课表，请确认已经登录并打开了「我的课表」。");
            return r;
        }
        if (raw.startsWith("NOAPP")) {
            r.warnings.add(OPEN_APP_HINT);
            return r;
        }
        if (raw.startsWith("NODATA")) {
            r.warnings.add("打开了金智应用，但课表接口没回数据：请在「我的课表」里等课表显示出来再导入。");
            if (raw.length() > 7) {
                String d = raw.substring(7);
                r.warnings.add("试过: " + (d.length() > 400 ? d.substring(0, 400) : d));
            }
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
        Matcher m = TERM_ID.matcher(o.optString("term", ""));
        if (m.find()) {
            sem.id = m.group(1) + "-" + m.group(2) + "-" + m.group(3);
            sem.name = m.group(1) + "-" + m.group(2) + " 学年第" + m.group(3) + "学期";
        }
        if (!m.find(0) && !o.optString("termName", "").isEmpty()) sem.name = o.optString("termName", "");
        if (sem.id == null || sem.id.isEmpty()) {
            Matcher t2 = Pattern.compile("(\\d{4})-(\\d{4})\\D{0,6}([12一二])").matcher(sem.name == null ? "" : sem.name);
            if (t2.find()) sem.id = t2.group(1) + "-" + t2.group(2) + "-" + ("1一".contains(t2.group(3)) ? "1" : "2");
        }
        if (o.has("grad") || o.has("dom")) return parseText(r, o);
        JSONArray rows = o.optJSONArray("rows");
        int maxWeek = 0;
        Map<String, Timetable.Course> grouped = new LinkedHashMap<>();
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject it = rows.optJSONObject(i);
            if (it == null) continue;
            String name = str(it, "KCM", "KCMC");
            int day = parseInt(str(it, "SKXQ", "XQ"), 0);
            int start = parseInt(str(it, "KSJC", "KSJCDM"), 0);
            int end = parseInt(str(it, "JSJC", "JSJCDM"), start);
            if (name.isEmpty() || day < 1 || day > 7 || start <= 0) continue;
            if (end < start) end = start;
            r.rawRecords++;
            TreeSet<Integer> weeks = bitmapWeeks(str(it, "SKZC"));
            String weeksRaw = str(it, "ZCMC");
            if (weeks.isEmpty() && !weeksRaw.isEmpty()) {
                WeekRange.Parsed pw = WeekRange.parse(weeksRaw);
                if (pw.ok()) weeks.addAll(pw.weeks);
            }
            if (weeks.isEmpty()) {
                r.failedWeekExpr++;
                continue;
            }
            if (weeks.last() > maxWeek) maxWeek = weeks.last();
            String teacher = str(it, "SKJS", "JSXM");
            String location = str(it, "JASMC", "JASDM");
            String key = "wisedu_" + name + "_" + teacher;
            Timetable.Course c = grouped.get(key);
            if (c == null) {
                c = new Timetable.Course();
                c.key = key;
                c.name = name;
                c.teacher = teacher;
                c.credit = parseDouble(str(it, "XF"));
                c.source = Timetable.SOURCE_JWGLXT;
                grouped.put(key, c);
            }
            Timetable.Session s = new Timetable.Session();
            s.dayOfWeek = day;
            s.startSection = start;
            s.endSection = end;
            s.weeks.addAll(weeks);
            s.weeksRaw = WeekRange.compress(s.weeks);
            s.location = location;
            s.teacher = teacher;
            c.sessions.add(s);
        }
        sem.totalWeeks = Math.max(20, maxWeek);
        List<Timetable.Course> courses = new ArrayList<>(grouped.values());
        r.mergedDuplicates = JwglxtFetcher.mergeOverlaps(courses);
        r.timetable.courses.addAll(courses);
        CourseColorAssigner.assign(r.timetable);
        r.courseCount = r.timetable.courses.size();
        if (r.courseCount == 0) r.warnings.add("这学期的课表是空的；如果不对，请在「我的课表」里切到本学期再导入。");
        return r;
    }

    /** 研究生 PKSJDD 文字 / 本科列表视图文字 → 课程。 */
    private static JwglxtFetcher.Result parseText(JwglxtFetcher.Result r, JSONObject o) {
        Timetable.Semester sem = r.timetable.semester;
        boolean grad = o.has("grad");
        JSONArray rows = grad ? o.optJSONArray("grad") : o.optJSONArray("dom");
        int maxWeek = 0;
        Map<String, Timetable.Course> grouped = new LinkedHashMap<>();
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject it = rows.optJSONObject(i);
            if (it == null) continue;
            String name = grad ? str(it, "KCMC", "KCM") : it.optString("name", "").trim();
            String teacher = grad ? str(it, "RKJS", "SKJS") : it.optString("teacher", "").trim();
            String text = grad ? str(it, "PKSJDD") : it.optString("tl", "").trim();
            if (name.isEmpty() || text.isEmpty()) continue;
            List<Slot> slots = grad ? parseGradSlots(text) : parseListSlots(text);
            if (slots.isEmpty()) {
                if (!text.contains("自由")) r.failedWeekExpr++;
                continue;
            }
            String key = "wisedu_" + name + "_" + teacher;
            Timetable.Course c = grouped.get(key);
            if (c == null) {
                c = new Timetable.Course();
                c.key = key;
                c.name = name;
                c.teacher = teacher;
                c.source = Timetable.SOURCE_JWGLXT;
                grouped.put(key, c);
            }
            for (Slot sl : slots) {
                r.rawRecords++;
                if (sl.weeks.last() > maxWeek) maxWeek = sl.weeks.last();
                Timetable.Session s = new Timetable.Session();
                s.dayOfWeek = sl.day;
                s.startSection = sl.start;
                s.endSection = sl.end;
                s.weeks.addAll(sl.weeks);
                s.weeksRaw = WeekRange.compress(s.weeks);
                s.location = sl.location;
                s.teacher = teacher;
                c.sessions.add(s);
            }
        }
        sem.totalWeeks = Math.max(20, maxWeek);
        List<Timetable.Course> courses = new ArrayList<>(grouped.values());
        r.mergedDuplicates = JwglxtFetcher.mergeOverlaps(courses);
        r.timetable.courses.addAll(courses);
        CourseColorAssigner.assign(r.timetable);
        r.courseCount = r.timetable.courses.size();
        if (r.courseCount == 0) r.warnings.add("这学期的课表是空的；如果不对，请在「我的课表」里切到本学期再导入。");
        return r;
    }

    static final class Slot {
        int day, start, end;
        String location = "";
        TreeSet<Integer> weeks = new TreeSet<>();
    }

    private static final Pattern LIST_SEG = Pattern.compile(
            "([一二三四五六日天])?\\s*(\\d+)(?:-(\\d+))?节\\s*([\\d\\-,，周]+(?:[(（][单双][)）])?)\\s*(.*)");
    private static final Pattern GRAD_SEG = Pattern.compile(
            "(.+?周)\\s*星期([一二三四五六日天])\\s*\\[(\\d+)(?:-(\\d+))?节\\](.*)");

    /** 「周三 2-4节 1-3周,10-13周 仙Ⅱ-304,周五 5-6节 2-18周(双) 仙1-216」 */
    static List<Slot> parseListSlots(String text) {
        List<Slot> out = new ArrayList<>();
        for (String seg : text.split(",周|，周|;|；")) {
            seg = seg.trim();
            if (seg.startsWith("周")) seg = seg.substring(1);
            Matcher m = LIST_SEG.matcher(seg);
            if (!m.find() || m.group(1) == null) continue;
            Slot s = new Slot();
            s.day = dayOf(m.group(1));
            s.start = Integer.parseInt(m.group(2));
            s.end = m.group(3) == null ? s.start : Integer.parseInt(m.group(3));
            s.weeks = textWeeks(m.group(4));
            s.location = m.group(5).trim();
            if (s.day > 0 && s.end >= s.start && !s.weeks.isEmpty()) out.add(s);
        }
        return out;
    }

    /** 「5-9单周,13-17单周 星期五[3-4节]仙Ⅰ-319;4-18周 星期一[3-4节]」 */
    static List<Slot> parseGradSlots(String text) {
        List<Slot> out = new ArrayList<>();
        for (String seg : text.split(";|；")) {
            Matcher m = GRAD_SEG.matcher(seg.trim());
            if (!m.find()) continue;
            Slot s = new Slot();
            s.day = dayOf(m.group(2));
            s.start = Integer.parseInt(m.group(3));
            s.end = m.group(4) == null ? s.start : Integer.parseInt(m.group(4));
            s.weeks = textWeeks(m.group(1));
            s.location = m.group(5).trim();
            if (s.day > 0 && s.end >= s.start && !s.weeks.isEmpty()) out.add(s);
        }
        return out;
    }

    private static int dayOf(String d) {
        int i = "一二三四五六日".indexOf(d);
        if (i < 0 && "天".equals(d)) i = 6;
        return i + 1;
    }

    /** 「1-3周,10-13周」「2-18周(双)」「5-9单周,13-17单周」「7周」 */
    static TreeSet<Integer> textWeeks(String s) {
        TreeSet<Integer> out = new TreeSet<>();
        if (s == null) return out;
        Matcher m = Pattern.compile("(\\d+)(?:-(\\d+))?\\s*(单|双)?\\s*周?\\s*(?:[(（](单|双)[)）])?").matcher(s);
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = m.group(2) == null ? a : Integer.parseInt(m.group(2));
            String f = m.group(3) != null ? m.group(3) : m.group(4);
            if (b < a || b > 60) continue;
            for (int w = a; w <= b; w++) {
                if ("单".equals(f) && w % 2 == 0) continue;
                if ("双".equals(f) && w % 2 == 1) continue;
                out.add(w);
            }
        }
        return out;
    }

    /** "0111100000" → {2,3,4,5}（第 i 位为 1 = 第 i+1 周）。 */
    static TreeSet<Integer> bitmapWeeks(String s) {
        TreeSet<Integer> out = new TreeSet<>();
        if (s == null || !s.matches("[01]+")) return out;
        for (int i = 0; i < s.length() && i < 60; i++) if (s.charAt(i) == '1') out.add(i + 1);
        return out;
    }

    private static String str(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equals(s)) return s;
            }
        }
        return "";
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
