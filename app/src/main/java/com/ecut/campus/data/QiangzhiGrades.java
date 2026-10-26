package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强智教务成绩：kscj/cjcx_list 的 #dataList 表格。
 *
 * 各校列数和顺序不一样，所以只按表头文字找列（开课学期、课程编号、课程名称、成绩、学分、绩点……），
 * 找不到「课程名称」和「成绩」两列就当作解析失败。纯 Java，无 Android 依赖。
 */
public final class QiangzhiGrades {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern TABLE = Pattern.compile(
            "<table\\b[^>]*\\bid\\s*=\\s*[\"']?dataList[\"']?[^>]*>(.*?)</table>", FLAGS);
    private static final Pattern TR = Pattern.compile("<tr\\b[^>]*>(.*?)(?=<tr\\b|</table>|$)", FLAGS);
    private static final Pattern CELL = Pattern.compile("<(td|th)\\b[^>]*>(.*?)(?=<td\\b|<th\\b|</tr>|$)", FLAGS);
    private static final Pattern TERM_ID = Pattern.compile("(\\d{4})-(\\d{4})-(\\d)");

    private QiangzhiGrades() {}

    public static String gradesUrl(SchoolProfile p) {
        if (p == null || p.jwBase == null || p.jwBase.isEmpty()) return null;
        return p.jwBase + "kscj/cjcx_list";
    }

    public static boolean isGradesPage(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("/kscj/");
    }

    /**
     * 当前页面有 #dataList 就直接用；否则同步 POST 查全部学期成绩。
     * 只把成绩表格交回 Java；学校提示没权限时返回 __NOPERM__，没找到表返回 NOTABLE。
     */
    public static String extractionJs() {
        return "(function(){" +
                "try{" +
                "function pick(doc){var t=doc.getElementById('dataList');return t?t.outerHTML:'';}" +
                "var r=pick(document);if(r&&location.search.indexOf('kksj=')<0&&document.body" +
                "&&/cjcx_list/.test(location.pathname))return r;" +
                "var p=location.pathname;var i=p.indexOf('/jsxsd/');" +
                "var base=i>=0?p.substring(0,i+7):'/jsxsd/';" +
                "var x=new XMLHttpRequest();" +
                "x.open('POST',base+'kscj/cjcx_list',false);" +
                "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
                "x.send('kksj=&kcxz=&kcmc=&xsfs=all');" +
                "var txt=x.responseText||'';" +
                "try{var d=new DOMParser().parseFromString(txt,'text/html');var r2=pick(d);if(r2)return r2;" +
                "var b=(d.body&&d.body.textContent)||'';if(/无(操作)?权限|未开放|评教/.test(b)&&b.length<2000)return '__NOPERM__';}catch(e){}" +
                "if(r)return r;" +
                "return 'NOTABLE';" +
                "}catch(e){return 'ERR:'+e;}" +
                "})()";
    }

    /** 解析 #dataList 表格（整页或只含表格都行）。永不抛异常；失败返回空 Grades。 */
    public static Grades parseHtml(String html) {
        Grades out = new Grades();
        if (html == null || html.isEmpty()) return out;
        try {
            Matcher tm = TABLE.matcher(html);
            String table = tm.find() ? tm.group(1) : html;
            Map<String, Grades.SemesterGrades> bySem = new LinkedHashMap<>();
            int[] col = null;
            Matcher rm = TR.matcher(table);
            while (rm.find()) {
                List<String> cells = cells(rm.group(1));
                if (cells.isEmpty()) continue;
                if (col == null) {
                    col = header(cells);
                    continue;
                }
                String name = get(cells, col[C_NAME]);
                if (name.isEmpty() || "课程名称".equals(name)) continue;
                String score = get(cells, col[C_SCORE]);

                String termRaw = get(cells, col[C_TERM]);
                Matcher term = TERM_ID.matcher(termRaw);
                String semId;
                String semName;
                if (term.find()) {
                    semId = term.group(1) + "-" + term.group(2) + "-" + term.group(3);
                    semName = term.group(1) + "-" + term.group(2) + " 学年第" + term.group(3) + "学期";
                } else {
                    semId = termRaw.isEmpty() ? "未知学期" : termRaw;
                    semName = semId;
                }
                Grades.SemesterGrades sem = bySem.get(semId);
                if (sem == null) {
                    sem = new Grades.SemesterGrades();
                    sem.id = semId;
                    sem.name = semName;
                    bySem.put(semId, sem);
                }

                Grades.CourseGrade cg = new Grades.CourseGrade();
                cg.code = get(cells, col[C_CODE]);
                cg.name = name;
                cg.score = score;
                cg.scoreValue = Grades.tryParseDouble(score);
                cg.credit = JwglxtFetcher.parseDouble(get(cells, col[C_CREDIT]));
                cg.gradePoint = Grades.tryParseDouble(get(cells, col[C_GP]));
                if (cg.gradePoint == null) cg.gradePoint = levelPoint(score, cg.scoreValue);
                cg.nature = get(cells, col[C_NATURE]);
                cg.category = get(cells, col[C_ATTR]);
                cg.examType = get(cells, col[C_EXAM]);
                cg.college = get(cells, col[C_COLLEGE]);
                cg.teacher = get(cells, col[C_TEACHER]);
                cg.isPassed = Grades.evaluatePassed(score, cg.scoreValue);
                sem.courses.add(cg);
            }
            out.semesters.addAll(bySem.values());
            out.sortSemestersDescending();
            out.recomputeAll();
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static final int C_TERM = 0, C_CODE = 1, C_NAME = 2, C_SCORE = 3, C_CREDIT = 4, C_GP = 5,
            C_NATURE = 6, C_ATTR = 7, C_EXAM = 8, C_COLLEGE = 9, C_TEACHER = 10;

    /** 表头 → 列号；没有的列是 -1。课程名称或成绩缺失时整表视为无效（返回全 -1，行全被跳过）。 */
    static int[] header(List<String> cells) {
        int[] c = new int[11];
        java.util.Arrays.fill(c, -1);
        for (int i = 0; i < cells.size(); i++) {
            String h = cells.get(i).replace(" ", "");
            if (c[C_TERM] < 0 && (h.contains("开课学期") || h.equals("学年学期") || h.equals("学期"))) c[C_TERM] = i;
            else if (c[C_CODE] < 0 && (h.contains("课程编号") || h.contains("课程代码") || h.equals("课程号"))) c[C_CODE] = i;
            else if (c[C_NAME] < 0 && h.contains("课程名称")) c[C_NAME] = i;
            else if (c[C_SCORE] < 0 && (h.equals("成绩") || h.equals("总成绩") || h.equals("总评成绩")
                    || h.equals("最终成绩") || h.equals("总评"))) c[C_SCORE] = i;
            else if (c[C_CREDIT] < 0 && h.equals("学分")) c[C_CREDIT] = i;
            else if (c[C_GP] < 0 && (h.equals("绩点") || h.equals("成绩绩点") || h.equals("学分绩点"))) c[C_GP] = i;
            else if (c[C_NATURE] < 0 && h.contains("课程性质")) c[C_NATURE] = i;
            else if (c[C_ATTR] < 0 && (h.contains("课程属性") || h.contains("课程类别"))) c[C_ATTR] = i;
            else if (c[C_EXAM] < 0 && (h.contains("考试性质") || h.contains("考核方式") || h.contains("成绩标识"))) c[C_EXAM] = i;
            else if (c[C_COLLEGE] < 0 && (h.contains("开课单位") || h.contains("开课学院"))) c[C_COLLEGE] = i;
            else if (c[C_TEACHER] < 0 && (h.contains("教师") || h.contains("任课老师"))) c[C_TEACHER] = i;
        }
        if (c[C_NAME] < 0 || c[C_SCORE] < 0) java.util.Arrays.fill(c, -1);
        return c;
    }

    /** 等级制成绩没有绩点列时按常见五级制估：优秀 4.5/良好 3.5/中等 2.5/及格 1.5/不及格 0。 */
    static Double levelPoint(String score, Double value) {
        if (value != null) return value < 60 ? 0.0 : Grades.round2(Math.min(5.0, Math.max(1.0, (value - 50) / 10)));
        if (score == null) return null;
        String s = score.trim();
        if (s.contains("不及格") || s.contains("不合格")) return 0.0;
        if (s.contains("优")) return 4.5;
        if (s.contains("良")) return 3.5;
        if (s.contains("中")) return 2.5;
        if (s.contains("及格") || s.contains("合格")) return 1.5;
        return null;
    }

    private static String get(List<String> cells, int i) {
        return i >= 0 && i < cells.size() ? cells.get(i) : "";
    }

    private static List<String> cells(String row) {
        List<String> out = new ArrayList<>();
        Matcher m = CELL.matcher(row);
        while (m.find()) out.add(QiangzhiSource.text(m.group(2)));
        return out;
    }
}
