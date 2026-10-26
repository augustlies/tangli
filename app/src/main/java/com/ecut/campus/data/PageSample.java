package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * 「暂不支持这个教务系统」时导出的页面结构样本：只留标签、id、class、表单字段名、表格行列数，
 * 不留页面文字。这里再做一遍脱敏：去掉中文（姓名多为中文）、长数字（学号）、邮箱、查询参数，
 * 只保留少量教务常见字段词（周次、节次、教室……）方便判断厂商。
 */
public final class PageSample {

    public static final int MAX_CHARS = 24 * 1024;

    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff\\uf900-\\ufaff]+");
    private static final Pattern LONG_DIGITS = Pattern.compile("[0-9]{5,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
    private static final Pattern QUERY = Pattern.compile("[?#].*$");
    /** 路径参数，如 ;jsessionid=…（会话凭据）。 */
    private static final Pattern PATH_PARAM = Pattern.compile("(?i);jsessionid=[^/?#\\s]*");
    /** 带空格或连字符的手机号：138 1234 5678、138-1234-5678。 */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])1[0-9]{2}[\\s-][0-9]{4}[\\s-][0-9]{4}(?![0-9])");
    private static final String[] KEEP_WORDS = {
            "星期", "周次", "节次", "教室", "老师", "教师", "地点", "课程", "学分", "大节", "小节", "上课"
    };

    private PageSample() {}

    /** 对 JS 采集的结构 JSON 脱敏；输入不是 JSON 时返回空串。 */
    public static String sanitize(String json) {
        if (json == null || json.trim().isEmpty()) return "";
        try {
            Object v = clean(new JSONObject(json), 0);
            String out = ((JSONObject) v).toString();
            if (out.length() > MAX_CHARS) out = out.substring(0, MAX_CHARS) + "…(截断)";
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static Object clean(Object v, int depth) throws Exception {
        if (depth > 8) return "…";
        if (v instanceof JSONObject) {
            JSONObject in = (JSONObject) v, out = new JSONObject();
            Iterator<String> keys = in.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String ck = scrub(k);
                if (ck.isEmpty()) continue;
                out.put(ck, clean(in.get(k), depth + 1));
            }
            return out;
        }
        if (v instanceof JSONArray) {
            JSONArray in = (JSONArray) v, out = new JSONArray();
            for (int i = 0; i < in.length() && i < 300; i++) out.put(clean(in.get(i), depth + 1));
            return out;
        }
        if (v instanceof String) return scrub((String) v);
        return v;
    }

    /** 单个字符串脱敏。 */
    public static String scrub(String s) {
        if (s == null) return "";
        String out = QUERY.matcher(s).replaceAll("");
        out = PATH_PARAM.matcher(out).replaceAll("");
        out = PHONE.matcher(out).replaceAll("#");
        out = EMAIL.matcher(out).replaceAll("@");
        out = LONG_DIGITS.matcher(out).replaceAll("#");
        java.util.regex.Matcher m = CJK.matcher(out);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(out, last, m.start());
            String word = m.group();
            String kept = "";
            for (String w : KEEP_WORDS) if (word.contains(w)) { kept = w; break; }
            sb.append(kept);
            last = m.end();
        }
        sb.append(out.substring(last));
        out = sb.toString().trim();
        if (out.length() > 80) out = out.substring(0, 80);
        return out;
    }

    /** 在 WebView 里运行的采集脚本：只读结构，不读文字。 */
    public static String collectJs() {
        return "(function(){try{" +
                "var out={path:location.pathname,frames:window.frames.length,crossOriginFrames:0," +
                "tags:{},ids:[],classes:{},fields:[],labels:{},tables:[]};" +
                "function walk(doc){" +
                "var all=doc.getElementsByTagName('*');" +
                "for(var i=0;i<all.length&&i<4000;i++){var e=all[i];var t=e.tagName.toLowerCase();" +
                "out.tags[t]=(out.tags[t]||0)+1;" +
                "if(e.id&&out.ids.length<200)out.ids.push(t+'#'+e.id);" +
                "var c=(typeof e.className==='string')?e.className:'';" +
                "var cs=c.split(/\\s+/);for(var j=0;j<cs.length;j++){if(cs[j])out.classes[cs[j]]=(out.classes[cs[j]]||0)+1;}" +
                "if((t==='input'||t==='select'||t==='textarea')&&out.fields.length<120){" +
                "var n=e.getAttribute('name')||e.id||'';if(n)out.fields.push(t+'['+n+']'+(e.type?':'+e.type:''));}" +
                "if(t==='font'||t==='span'){var ti=e.getAttribute('title');if(ti)out.labels[ti]=(out.labels[ti]||0)+1;}}" +
                "var ts=doc.getElementsByTagName('table');" +
                "for(var k=0;k<ts.length&&k<40;k++){var tb=ts[k];var mc=0;" +
                "for(var r=0;r<tb.rows.length;r++)mc=Math.max(mc,tb.rows[r].cells.length);" +
                "out.tables.push({id:tb.id||'',cls:(typeof tb.className==='string'?tb.className:''),rows:tb.rows.length,maxCols:mc," +
                "rowspan:tb.querySelectorAll('[rowspan]').length});}}" +
                "walk(document);" +
                "for(var f=0;f<window.frames.length;f++){try{walk(window.frames[f].document);}catch(e){out.crossOriginFrames++;}}" +
                "return JSON.stringify(out);}catch(e){return '';}})()";
    }
}
