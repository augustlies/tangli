package com.ecut.campus.data;

import com.ecut.campus.WeekMath;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 非东华理工学校：从教务系统推出第 1 周周一，免得用东华理工的校历。
 *
 * 两条线索，按可靠程度：
 * 1. 强智「教学周历」jxzl/jxzl_query：第 1 周那一行里的完整日期；
 * 2. 页面文字里的「当前第 N 周 / 本周是第 N 周」：本周一往回推 N-1 周。
 * 都找不到就返回空串，由用户在课表设置里手动选。纯 Java，无 Android 依赖。
 */
public final class TermStartProbe {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern TR = Pattern.compile("<tr\\b[^>]*>(.*?)(?=<tr\\b|</table>|$)", FLAGS);
    private static final Pattern FIRST_CELL = Pattern.compile("<t[dh]\\b[^>]*>(.*?)(?=<t[dh]\\b|</tr>|$)", FLAGS);
    private static final Pattern FULL_DATE = Pattern.compile(
            "(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})");
    private static final Pattern WEEK_TEXT = Pattern.compile(
            "(当前|本周|今天|现在|目前)[^第\\n]{0,6}第\\s*(\\d{1,2})\\s*(?:教学)?周");

    private TermStartProbe() {}

    /**
     * 在已登录教务页面里跑：强智取教学周历整页，其他取当前页文字。
     * 返回 JSON {jxzl: html, text: 页面文字}，各自最多 60k 字符。
     */
    public static String probeJs(SchoolProfile.Vendor vendor, String termId) {
        String term = termId != null && termId.matches("\\d{4}-\\d{4}-\\d") ? termId : "";
        boolean qz = vendor == SchoolProfile.Vendor.QIANGZHI;
        return "(function(){" +
                "var o={jxzl:'',text:''};" +
                "try{o.text=((document.body&&document.body.innerText)||'').substring(0,60000);}catch(e){}" +
                (qz ? "try{var p=location.pathname;var i=p.indexOf('/jsxsd/');" +
                        "var base=i>=0?p.substring(0,i+7):'/jsxsd/';" +
                        "var x=new XMLHttpRequest();x.open('GET',base+'jxzl/jxzl_query" +
                        (term.isEmpty() ? "" : "?xnxq01id=" + term) + "',false);x.send();" +
                        "o.jxzl=(x.responseText||'').substring(0,60000);}catch(e){}" : "") +
                "return JSON.stringify(o);})()";
    }

    /** 解析 probeJs 的结果。todayYmd 用于「第 N 周」推算和合理性检查。 */
    public static String detect(String probeJson, String todayYmd) {
        if (probeJson == null || probeJson.isEmpty()) return "";
        String jxzl = "";
        String text = "";
        try {
            JSONObject o = new JSONObject(probeJson);
            jxzl = o.optString("jxzl", "");
            text = o.optString("text", "");
        } catch (Exception e) {
            text = probeJson;
        }
        String s = fromQiangzhiCalendar(jxzl, todayYmd);
        if (!s.isEmpty()) return s;
        return fromWeekText(text, todayYmd);
    }

    /** 强智教学周历：第一列是 1（或「第1周」）的那一行里第一个完整日期所在周的周一。 */
    public static String fromQiangzhiCalendar(String html, String todayYmd) {
        if (html == null || html.isEmpty()) return "";
        Matcher rm = TR.matcher(html);
        while (rm.find()) {
            String row = rm.group(1);
            Matcher c = FIRST_CELL.matcher(row);
            if (!c.find()) continue;
            String first = QiangzhiSource.text(c.group(1)).replace(" ", "");
            if (!first.matches("(第)?0?1(周)?")) continue;
            Matcher d = FULL_DATE.matcher(row);
            if (!d.find()) continue;
            String monday = WeekMath.normalizeToMonday(String.format(Locale.US, "%s-%02d-%02d",
                    d.group(1), Integer.parseInt(d.group(2)), Integer.parseInt(d.group(3))));
            if (plausible(monday, todayYmd)) return monday;
        }
        return "";
    }

    /** 「当前第 8 周」→ 本周一往回推 7 周。 */
    public static String fromWeekText(String text, String todayYmd) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = WEEK_TEXT.matcher(text);
        if (!m.find()) return "";
        int n = Integer.parseInt(m.group(2));
        if (n < 1 || n > 25) return "";
        Calendar cal = parse(WeekMath.normalizeToMonday(todayYmd));
        if (cal == null) return "";
        cal.add(Calendar.DAY_OF_MONTH, -7 * (n - 1));
        return format(cal.getTime());
    }

    /** 开学日要在今天前后约 10 个月以内，排除教学周历里别的年份。 */
    static boolean plausible(String monday, String todayYmd) {
        Calendar a = parse(monday);
        Calendar b = parse(todayYmd);
        if (a == null || b == null) return false;
        long days = Math.abs(a.getTimeInMillis() - b.getTimeInMillis()) / 86_400_000L;
        return days <= 300;
    }

    private static Calendar parse(String ymd) {
        if (ymd == null || ymd.isEmpty()) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setLenient(false);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(ymd));
            return c;
        } catch (ParseException e) {
            return null;
        }
    }

    private static String format(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }
}
