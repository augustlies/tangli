package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强智教务（/jsxsd/）。课表页 {@code jsxsd/xskb/xskb_list.do} 是一张 HTML 表格 {@code #kbtable}：
 * 每个格子里有 {@code div.kbcontent}（详细）和 {@code div.kbcontent1}（简略）两份，只解析详细那份；
 * 一个格子里多门课用一行 {@code -----} 隔开；字段放在 {@code <font title="老师|周次(节次)|教室">} 里，
 * 周次节次形如 {@code 1-16(周)[01-02节]}。
 *
 * 星期优先按表头（星期一…星期日）定位，表头认不出时按 div id 末尾的 {@code -星期-2}，再不行按列序；
 * 节次优先取 {@code [01-02节]}，没有时按行表头（如 0102）或「第 N 大节」推算。
 *
 * 思路参考拾光课程表适配仓库（MIT）中对强智 kbcontent 的说明；代码为烫梨独立实现。
 */
public final class QiangzhiSource implements TimetableSource {

    public static final String TIMETABLE_PATH = "xskb/xskb_list.do";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern TR = Pattern.compile("<tr\\b[^>]*>(.*?)(?=<tr\\b|</table>|$)", FLAGS);
    private static final Pattern CELL = Pattern.compile("<(td|th)\\b([^>]*)>(.*?)(?=<td\\b|<th\\b|</tr>|$)", FLAGS);
    private static final Pattern DIV = Pattern.compile("<div\\b([^>]*)>(.*?)</div>", FLAGS);
    private static final Pattern FONT = Pattern.compile("<font\\b([^>]*)>(.*?)</font>", FLAGS);
    private static final Pattern TITLE_ATTR = Pattern.compile("\\btitle\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", FLAGS);
    private static final Pattern CLASS_ATTR = Pattern.compile("\\bclass\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", FLAGS);
    private static final Pattern ID_ATTR = Pattern.compile("\\bid\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", FLAGS);
    private static final Pattern COLSPAN = Pattern.compile("\\bcolspan\\s*=\\s*[\"']?(\\d+)", FLAGS);
    private static final Pattern SEPARATOR = Pattern.compile("(?:<br\\s*/?>\\s*)?-{5,}\\s*(?:<br\\s*/?>)?", FLAGS);
    private static final Pattern BRACKET_SECTIONS = Pattern.compile("[\\[［【]([^\\]］】]*)[\\]］】]");
    private static final Pattern SELECT_TERM = Pattern.compile(
            "<select\\b[^>]*(?:id|name)\\s*=\\s*[\"']?xnxq01id[\"']?[^>]*>(.*?)</select>", FLAGS);
    private static final Pattern OPTION = Pattern.compile("<option\\b([^>]*)>", FLAGS);
    private static final Pattern VALUE_ATTR = Pattern.compile("\\bvalue\\s*=\\s*[\"']?([^\"'\\s>]+)", FLAGS);
    private static final Pattern TERM_ID = Pattern.compile("(\\d{4})-(\\d{4})-(\\d)");
    private static final String[] CN_NUM = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    @Override
    public SchoolProfile.Vendor vendor() {
        return SchoolProfile.Vendor.QIANGZHI;
    }

    @Override
    public int detect(String url, String html) {
        int score = 0;
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains("/jsxsd/") || u.endsWith("/jsxsd")) score += 50;
        if (u.contains("xskb")) score += 20;
        if (html != null) {
            if (html.contains("kbtable")) score += 25;
            if (html.contains("kbcontent")) score += 20;
            if (html.contains("/jsxsd/")) score += 10;
            if (html.contains("xnxq01id")) score += 5;
        }
        return Math.min(100, score);
    }

    @Override
    public String timetableUrl(SchoolProfile p) {
        if (p == null || p.jwBase == null || p.jwBase.isEmpty()) return null;
        return p.jwBase + TIMETABLE_PATH;
    }

    @Override
    public boolean isLoginPage(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("/jsxsd/") && (u.contains("logintoxk") || u.contains("login")
                || u.matches(".*/jsxsd/?(\\?.*)?$"));
    }

    @Override
    public boolean isLoggedInPage(String url) {
        if (url == null || isLoginPage(url)) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("/jsxsd/") && (u.contains("framework") || u.contains("xskb")
                || u.contains("xsmain") || u.contains("/grxx/") || u.contains("/kscj/"));
    }

    public static boolean isTimetablePage(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("xskb");
    }

    /**
     * 先在当前页面和同源 frame 里找 #kbtable；找不到就同步请求 xskb_list.do。
     * 只把课表表格和学期下拉框交回 Java，不带页面其余内容。
     */
    @Override
    public String extractionJs(SchoolProfile p, String xnm, String xqm) {
        String term = "";
        if (xnm != null && xnm.matches("\\d{4}")) {
            int y = Integer.parseInt(xnm);
            int n = "12".equals(xqm) ? 2 : ("16".equals(xqm) ? 3 : 1);
            term = y + "-" + (y + 1) + "-" + n;
        }
        return "(function(){" +
                "try{" +
                "function pick(doc){var t=doc.getElementById('kbtable');if(!t)return '';" +
                "var s=doc.getElementById('xnxq01id');" +
                "return (s?s.outerHTML:'')+t.outerHTML;}" +
                "var want='" + term + "';" +
                "if(!want){var r=pick(document);if(r)return r;" +
                "for(var f=0;f<window.frames.length;f++){try{var r2=pick(window.frames[f].document);" +
                "if(r2)return r2;}catch(e){}}}" +
                "var p=location.pathname;var i=p.indexOf('/jsxsd/');" +
                "var base=i>=0?p.substring(0,i+7):'/jsxsd/';" +
                "var x=new XMLHttpRequest();" +
                "x.open('GET',base+'xskb/xskb_list.do'+(want?'?xnxq01id='+encodeURIComponent(want):''),false);" +
                "x.send();var txt=x.responseText||'';" +
                "try{var d=new DOMParser().parseFromString(txt,'text/html');var r3=pick(d);if(r3)return r3;}catch(e){}" +
                "return txt.indexOf('kbtable')>=0?txt:'NOTABLE';" +
                "}catch(e){return 'ERR:'+e;}" +
                "})()";
    }

    @Override
    public JwglxtFetcher.Result parse(String raw) {
        return parseHtml(raw);
    }

    /** 解析 xskb_list.do 的 HTML（整页或只含 #kbtable 都行）。 */
    public static JwglxtFetcher.Result parseHtml(String html) {
        JwglxtFetcher.Result r = new JwglxtFetcher.Result();
        if (html == null || html.trim().isEmpty() || html.startsWith("ERR:") || html.startsWith("NOTABLE")) {
            r.warnings.add("页面里没有课表表格");
            return r;
        }
        parseSemester(html, r.timetable.semester);
        r.timetable.semester.totalWeeks = 20;

        String table = html;
        int k = indexOfIgnoreCase(html, "kbtable");
        if (k >= 0) {
            int start = html.lastIndexOf('<', k);
            int end = indexOfIgnoreCase(html, "</table>", k);
            table = html.substring(Math.max(0, start), end < 0 ? html.length() : end + 8);
        } else {
            r.warnings.add("没有 #kbtable，按整页解析");
        }

        Map<String, Timetable.Course> grouped = new LinkedHashMap<>();
        int[] headerDays = null;
        int dataRow = 0;
        Matcher tr = TR.matcher(table);
        while (tr.find()) {
            String row = tr.group(1);
            List<String[]> cells = cells(row);
            if (cells.isEmpty()) continue;
            if (headerDays == null && looksLikeHeader(cells)) {
                headerDays = headerDays(cells);
                continue;
            }
            boolean hasTd = false;
            for (String[] c : cells) if ("td".equals(c[0])) { hasTd = true; break; }
            if (!hasTd) continue;
            dataRow++;

            int[] rowSections = null;
            int col = 0;
            for (String[] c : cells) {
                boolean isLabel = "th".equals(c[0])
                        || (col == 0 && !c[2].contains("kbcontent") && looksLikeRowLabel(c[2]));
                if (isLabel) {
                    if (rowSections == null) rowSections = rowLabelSections(text(c[2]), dataRow);
                    continue;
                }
                int colIndex = col;
                col += colspan(c[1]);
                if (!c[2].contains("kbcontent")) continue;
                if (rowSections == null) rowSections = new int[]{dataRow * 2 - 1, dataRow * 2};

                int headerDay = headerDays != null && colIndex < headerDays.length ? headerDays[colIndex] : 0;
                for (String[] div : contentDivs(c[2])) {
                    int day = headerDay > 0 ? headerDay : dayFromId(div[0]);
                    if (day <= 0) day = colIndex + 1;
                    if (day < 1 || day > 7) continue;
                    for (String block : SEPARATOR.split(div[1])) {
                        addBlock(block, day, rowSections, grouped, r);
                    }
                }
            }
        }

        List<Timetable.Course> courses = new ArrayList<>(grouped.values());
        r.mergedDuplicates = JwglxtFetcher.mergeOverlaps(courses);
        r.timetable.courses.addAll(courses);
        CourseColorAssigner.assign(r.timetable);
        r.courseCount = r.timetable.courses.size();
        if (r.courseCount == 0) r.warnings.add("课表表格里没有课程");
        return r;
    }

    // ---------- 单门课 ----------

    private static void addBlock(String block, int day, int[] rowSections,
                                 Map<String, Timetable.Course> grouped, JwglxtFetcher.Result r) {
        String teacher = "", weeksField = "", location = "";
        List<String> untitled = new ArrayList<>();
        StringBuilder rest = new StringBuilder();
        int last = 0;
        Matcher fm = FONT.matcher(block);
        while (fm.find()) {
            rest.append(block, last, fm.start()).append("<br>");
            last = fm.end();
            String title = attr(TITLE_ATTR, fm.group(1));
            String value = text(fm.group(2));
            if (value.isEmpty()) continue;
            if (title.contains("老师") || title.contains("教师")) {
                teacher = teacher.isEmpty() ? value : teacher;
            } else if (title.contains("周次") || title.contains("节次")) {
                weeksField = weeksField.isEmpty() ? value : weeksField;
            } else if (title.contains("教室") || title.contains("地点") || title.contains("场地")) {
                location = location.isEmpty() ? value : location;
            } else if (title.isEmpty()) {
                untitled.add(value);
            }
        }
        rest.append(block.substring(last));

        String name = "";
        for (String line : lines(rest.toString())) {
            if (line.isEmpty() || line.matches("[-\\s]+")) continue;
            name = line;
            break;
        }
        if (name.isEmpty() && !untitled.isEmpty()) name = untitled.remove(0);
        name = name.replaceAll("^[●★○◆■\\s]+", "").trim();
        if (name.isEmpty()) return;

        if (weeksField.isEmpty()) {
            for (String u : untitled) if (u.contains("周")) { weeksField = u; break; }
        }

        int start = rowSections[0], end = rowSections[1];
        String weeksExpr = weeksField;
        Matcher bm = BRACKET_SECTIONS.matcher(weeksField);
        int[] fallback = null;
        int fallbackAt = -1;
        while (bm.find()) {
            // 节次括号：「[01-02节]」「[01-02]节」；「1-16[周]」「[1-16]周」里的括号是周次，不能当节次
            String inner = bm.group(1);
            String after = weeksField.substring(bm.end()).trim();
            int[] s = sectionsFrom(inner);
            if (s == null) continue;
            if (inner.contains("节") || after.startsWith("节")) {
                start = s[0]; end = s[1];
                weeksExpr = weeksField.substring(0, bm.start()).trim();
                fallback = null;
                break;
            }
            if (fallback == null && !inner.contains("周") && !after.startsWith("周")) {
                fallback = s;
                fallbackAt = bm.start();
            }
        }
        if (fallback != null) {
            start = fallback[0]; end = fallback[1];
            weeksExpr = weeksField.substring(0, fallbackAt).trim();
        }

        String key = "qiangzhi_" + name + "_" + teacher;
        Timetable.Course course = grouped.get(key);
        if (course == null) {
            course = new Timetable.Course();
            course.key = key;
            course.name = name;
            course.teacher = teacher;
            course.source = Timetable.SOURCE_JWGLXT;
            grouped.put(key, course);
        }
        r.rawRecords++;

        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = day;
        s.startSection = start;
        s.endSection = end;
        s.weeksRaw = weeksExpr;
        WeekRange.Parsed wr = WeekRange.parse(weeksExpr);
        s.weeks.addAll(wr.weeks);
        if (!wr.ok()) {
            r.failedWeekExpr++;
            r.warnings.add("周次解析失败: '" + weeksExpr + "' (" + name + ")");
        }
        s.location = location;
        s.teacher = teacher;
        for (Timetable.Session x : course.sessions) {
            if (x.dayOfWeek == s.dayOfWeek && x.startSection == s.startSection
                    && x.endSection == s.endSection && x.weeksRaw.equals(s.weeksRaw)) {
                return;
            }
        }
        course.sessions.add(s);
    }

    // ---------- 表格结构 ----------

    /** 每个元素 {标签, 属性, 内容}。 */
    private static List<String[]> cells(String row) {
        List<String[]> out = new ArrayList<>();
        Matcher m = CELL.matcher(row);
        while (m.find()) {
            String body = m.group(3);
            int close = lastIndexOfIgnoreCase(body, "</" + m.group(1).toLowerCase(Locale.ROOT) + ">");
            if (close >= 0) body = body.substring(0, close);
            out.add(new String[]{m.group(1).toLowerCase(Locale.ROOT), m.group(2), body});
        }
        return out;
    }

    private static boolean looksLikeHeader(List<String[]> cells) {
        int hits = 0;
        for (String[] c : cells) if (dayFromText(text(c[2])) > 0) hits++;
        return hits >= 5;
    }

    /** 表头列（不含行表头格子）→ 星期。 */
    private static int[] headerDays(List<String[]> cells) {
        List<Integer> days = new ArrayList<>();
        boolean first = true;
        for (String[] c : cells) {
            int d = dayFromText(text(c[2]));
            if (first && d == 0) { first = false; continue; } // 左上角空格
            first = false;
            int span = colspan(c[1]);
            for (int i = 0; i < span; i++) days.add(d);
        }
        int[] out = new int[days.size()];
        for (int i = 0; i < out.length; i++) out[i] = days.get(i);
        return out;
    }

    static int dayFromText(String t) {
        if (t == null) return 0;
        String s = t.replaceAll("\\s+", "");
        String[] names = {"一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < names.length; i++) {
            if (s.contains("星期" + names[i]) || s.equals("周" + names[i])
                    || s.startsWith("周" + names[i])) return i + 1;
        }
        if (s.contains("星期日") || s.contains("星期天") || s.startsWith("周日") || s.startsWith("周天")) return 7;
        return 0;
    }

    private static boolean looksLikeRowLabel(String html) {
        String t = text(html);
        return t.contains("大节") || t.contains("小节") || t.matches("^第?[0-9一二三四五六七八九十]+节.*");
    }

    /** 行表头 → 节次：「0102」「01,02」「1-2节」优先，其次「第三大节」，都没有按行号。 */
    static int[] rowLabelSections(String label, int dataRow) {
        String t = label == null ? "" : label.replaceAll("\\s+", "");
        Matcher pairs = Pattern.compile("(?<![0-9])(\\d{2})(\\d{2})(?![0-9])").matcher(t);
        if (pairs.find()) {
            int a = Integer.parseInt(pairs.group(1)), b = Integer.parseInt(pairs.group(2));
            if (a > 0 && b >= a && b - a < 6) return new int[]{a, b};
        }
        Matcher nums = Pattern.compile("\\d+").matcher(t.replaceAll("^第[一二三四五六七八九十]+大节", ""));
        List<Integer> list = new ArrayList<>();
        while (nums.find()) list.add(Integer.parseInt(nums.group()));
        if (list.size() >= 2) {
            int a = list.get(0), b = list.get(list.size() - 1);
            if (a > 0 && b >= a && b - a < 6) return new int[]{a, b};
        }
        for (int i = CN_NUM.length - 1; i >= 0; i--) {
            if (t.contains("第" + CN_NUM[i] + "大节")) return new int[]{i * 2 + 1, i * 2 + 2};
        }
        int n = Math.max(1, dataRow);
        return new int[]{n * 2 - 1, n * 2};
    }

    /** 「01-02节」「01-02-03-04节」「9-11节」→ [起, 止]。 */
    static int[] sectionsFrom(String s) {
        Matcher m = Pattern.compile("\\d+").matcher(s);
        int min = Integer.MAX_VALUE, max = -1;
        while (m.find()) {
            int v = Integer.parseInt(m.group());
            if (v <= 0 || v > 20) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max < 0 ? null : new int[]{min, max};
    }

    /** 格子里的详细课程 div：{id, 内容}。没有 kbcontent 时退回 kbcontent1。 */
    private static List<String[]> contentDivs(String cell) {
        List<String[]> detailed = new ArrayList<>(), brief = new ArrayList<>();
        Matcher m = DIV.matcher(cell);
        while (m.find()) {
            String cls = attr(CLASS_ATTR, m.group(1));
            String[] item = {attr(ID_ATTR, m.group(1)), m.group(2)};
            if (cls.matches("(^|.*\\s)kbcontent(\\s.*|$)")) detailed.add(item);
            else if (cls.matches("(^|.*\\s)kbcontent1(\\s.*|$)")) brief.add(item);
        }
        return detailed.isEmpty() ? brief : detailed;
    }

    /** div id 形如 {@code 4F2A…-3-2}：倒数第二段是星期。 */
    static int dayFromId(String id) {
        if (id == null) return 0;
        Matcher m = Pattern.compile("-(\\d)-\\d$").matcher(id.trim());
        if (!m.find()) return 0;
        int d = Integer.parseInt(m.group(1));
        return d >= 1 && d <= 7 ? d : 0;
    }

    private static void parseSemester(String html, Timetable.Semester sem) {
        Matcher sel = SELECT_TERM.matcher(html);
        if (!sel.find()) return;
        String value = "";
        Matcher opt = OPTION.matcher(sel.group(1));
        while (opt.find()) {
            String attrs = opt.group(1);
            if (attrs.toLowerCase(Locale.ROOT).matches(".*\\bselected\\b.*")) {
                value = attr(VALUE_ATTR, attrs);
                break;
            }
        }
        Matcher t = TERM_ID.matcher(value);
        if (!t.find()) return;
        sem.id = t.group(1) + "-" + t.group(2) + "-" + t.group(3);
        sem.name = t.group(1) + "-" + t.group(2) + " 学年第" + t.group(3) + "学期";
    }

    // ---------- 文本工具 ----------

    private static int colspan(String attrs) {
        Matcher m = COLSPAN.matcher(attrs == null ? "" : attrs);
        if (!m.find()) return 1;
        try {
            return Math.max(1, Math.min(14, Integer.parseInt(m.group(1))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String attr(Pattern p, String attrs) {
        if (attrs == null) return "";
        Matcher m = p.matcher(attrs);
        if (!m.find()) return "";
        for (int g = 2; g <= m.groupCount(); g++) if (m.group(g) != null) return m.group(g).trim();
        return m.group(1) == null ? "" : m.group(1).trim();
    }

    private static List<String> lines(String html) {
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?is)<[^>]+>", "");
        List<String> out = new ArrayList<>();
        for (String line : unescape(s).split("\n")) out.add(line.trim());
        return out;
    }

    static String text(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("(?is)<[^>]+>", "");
        return unescape(s).replaceAll("\\s+", " ").trim();
    }

    private static String unescape(String s) {
        return JwglxtFetcher.clean(s.replace("&nbsp;", " ").replace("&#160;", " "))
                .replace("&ensp;", " ").replace("&emsp;", " ");
    }

    private static int indexOfIgnoreCase(String s, String key) {
        return indexOfIgnoreCase(s, key, 0);
    }

    private static int indexOfIgnoreCase(String s, String key, int from) {
        return s.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT), from);
    }

    private static int lastIndexOfIgnoreCase(String s, String key) {
        return s.toLowerCase(Locale.ROOT).lastIndexOf(key.toLowerCase(Locale.ROOT));
    }
}
