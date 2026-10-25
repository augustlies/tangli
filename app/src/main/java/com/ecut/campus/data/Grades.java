package com.ecut.campus.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 成绩数据模型。
 *
 * 结构严格遵循 docs/DATA-SCHEMA.md 的 grades.json 规范，
 * 同时兼容真机 jqGrid tabGrid DOM 抓取（39 列索引映射）。
 */
public final class Grades {

    public static final int SCHEMA_VERSION = 1;

    public static final class Summary {
        public double gpa = 0.0;
        public double weightedScore = 0.0;
        public double totalCredit = 0.0;
        public int passedCount = 0;
        public int failedCount = 0;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("gpa", round2(gpa));
            o.put("weightedScore", round2(weightedScore));
            o.put("totalCredit", round2(totalCredit));
            o.put("passedCount", passedCount);
            o.put("failedCount", failedCount);
            return o;
        }

        public static Summary fromJson(JSONObject o) {
            Summary s = new Summary();
            if (o == null) return s;
            s.gpa = o.optDouble("gpa", 0.0);
            s.weightedScore = o.optDouble("weightedScore", 0.0);
            s.totalCredit = o.optDouble("totalCredit", 0.0);
            s.passedCount = o.optInt("passedCount", 0);
            s.failedCount = o.optInt("failedCount", 0);
            return s;
        }
    }

    public static final class CourseGrade {
        public String code = "";
        public String name = "";
        public String score = "";
        public Double scoreValue = null;
        public double credit = 0.0;
        public Double gradePoint = null;
        public String nature = "";
        public String examType = "";
        public String teacher = "";
        public String college = "";
        public String category = "";
        public boolean isPassed = true;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("code", code);
            o.put("name", name);
            o.put("score", score);
            if (scoreValue != null) o.put("scoreValue", round2(scoreValue));
            else o.put("scoreValue", JSONObject.NULL);
            o.put("credit", round2(credit));
            if (gradePoint != null) o.put("gradePoint", round2(gradePoint));
            else o.put("gradePoint", JSONObject.NULL);
            o.put("nature", nature);
            o.put("examType", examType);
            o.put("teacher", teacher);
            o.put("college", college);
            o.put("category", category);
            o.put("isPassed", isPassed);
            return o;
        }

        public static CourseGrade fromJson(JSONObject o) {
            CourseGrade c = new CourseGrade();
            if (o == null) return c;
            c.code = o.optString("code", "").trim();
            c.name = o.optString("name", "");
            c.score = o.optString("score", "");
            if (!o.isNull("scoreValue") && o.has("scoreValue")) {
                double v = o.optDouble("scoreValue", Double.NaN);
                if (!Double.isNaN(v)) c.scoreValue = v;
            }
            c.credit = o.optDouble("credit", 0.0);
            if (!o.isNull("gradePoint") && o.has("gradePoint")) {
                double g = o.optDouble("gradePoint", Double.NaN);
                if (!Double.isNaN(g)) c.gradePoint = g;
            }
            c.nature = o.optString("nature", "");
            c.examType = o.optString("examType", "");
            c.teacher = o.optString("teacher", "");
            c.college = o.optString("college", "");
            c.category = o.optString("category", "");
            c.isPassed = o.optBoolean("isPassed", evaluatePassed(c.score, c.scoreValue));
            return c;
        }
    }

    public static final class SemesterGrades {
        public String id = "";
        public String name = "";
        public double gpa = 0.0;
        public double weightedScore = 0.0;
        public double totalCredit = 0.0;
        public List<CourseGrade> courses = new ArrayList<>();

        public void recompute() {
            double gpWeightSum = 0.0;
            double gpCreditSum = 0.0;
            double scoreWeightSum = 0.0;
            double scoreCreditSum = 0.0;
            double creditSum = 0.0;
            for (CourseGrade c : courses) {
                double cr = Math.max(0.0, c.credit);
                if (c.isPassed) {
                    creditSum += cr;
                }
                if (c.gradePoint != null && cr > 0) {
                    gpWeightSum += c.gradePoint * cr;
                    gpCreditSum += cr;
                }
                if (c.scoreValue != null && cr > 0) {
                    scoreWeightSum += c.scoreValue * cr;
                    scoreCreditSum += cr;
                }
            }
            this.totalCredit = round2(creditSum);
            this.gpa = gpCreditSum > 0 ? round2(gpWeightSum / gpCreditSum) : 0.0;
            this.weightedScore = scoreCreditSum > 0 ? round2(scoreWeightSum / scoreCreditSum) : 0.0;
        }

        public JSONObject toJson() throws JSONException {
            recompute();
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("gpa", round2(gpa));
            o.put("weightedScore", round2(weightedScore));
            o.put("totalCredit", round2(totalCredit));
            JSONArray arr = new JSONArray();
            for (CourseGrade c : courses) arr.put(c.toJson());
            o.put("courses", arr);
            return o;
        }

        public static SemesterGrades fromJson(JSONObject o) {
            SemesterGrades s = new SemesterGrades();
            if (o == null) return s;
            s.id = o.optString("id", "");
            s.name = o.optString("name", "");
            s.gpa = o.optDouble("gpa", 0.0);
            s.weightedScore = o.optDouble("weightedScore", 0.0);
            s.totalCredit = o.optDouble("totalCredit", 0.0);
            JSONArray arr = o.optJSONArray("courses");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject co = arr.optJSONObject(i);
                    if (co != null) s.courses.add(CourseGrade.fromJson(co));
                }
            }
            s.recompute();
            return s;
        }
    }

    public String updatedAt = "";
    public Summary summary = new Summary();
    public List<SemesterGrades> semesters = new ArrayList<>();

    public Grades() {}

    public void sortSemestersDescending() {
        semesters.sort((a, b) -> {
            String ia = a.id == null ? "" : a.id;
            String ib = b.id == null ? "" : b.id;
            return ib.compareTo(ia);
        });
    }

    public void recomputeAll() {
        double gpWeightSum = 0.0;
        double gpCreditSum = 0.0;
        double scoreWeightSum = 0.0;
        double scoreCreditSum = 0.0;
        double totalCredit = 0.0;
        int passed = 0;
        int failed = 0;

        for (SemesterGrades sem : semesters) {
            sem.recompute();
            for (CourseGrade c : sem.courses) {
                double cr = Math.max(0.0, c.credit);
                if (c.isPassed) {
                    passed++;
                    totalCredit += cr;
                } else {
                    failed++;
                }
                if (c.gradePoint != null && cr > 0) {
                    gpWeightSum += c.gradePoint * cr;
                    gpCreditSum += cr;
                }
                if (c.scoreValue != null && cr > 0) {
                    scoreWeightSum += c.scoreValue * cr;
                    scoreCreditSum += cr;
                }
            }
        }
        summary.totalCredit = round2(totalCredit);
        summary.passedCount = passed;
        summary.failedCount = failed;
        summary.gpa = gpCreditSum > 0 ? round2(gpWeightSum / gpCreditSum) : 0.0;
        summary.weightedScore = scoreCreditSum > 0 ? round2(scoreWeightSum / scoreCreditSum) : 0.0;
        if (updatedAt == null || updatedAt.isEmpty()) {
            updatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
        }
    }

    public int totalCourseCount() {
        int n = 0;
        for (SemesterGrades s : semesters) n += s.courses.size();
        return n;
    }

    public JSONObject toJson() throws JSONException {
        recomputeAll();
        JSONObject o = new JSONObject();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("updatedAt", updatedAt);
        o.put("summary", summary.toJson());
        JSONArray sa = new JSONArray();
        for (SemesterGrades s : semesters) sa.put(s.toJson());
        o.put("semesters", sa);
        return o;
    }

    public static Grades fromJson(JSONObject o) throws JSONException {
        int ver = o.optInt("schemaVersion", 0);
        if (ver > SCHEMA_VERSION) {
            throw new JSONException("成绩数据版本 " + ver + " 高于当前支持版本 " + SCHEMA_VERSION + "，请升级 App");
        }
        Grades g = new Grades();
        g.updatedAt = o.optString("updatedAt", "");
        JSONObject sum = o.optJSONObject("summary");
        if (sum != null) g.summary = Summary.fromJson(sum);
        JSONArray sa = o.optJSONArray("semesters");
        if (sa != null) {
            for (int i = 0; i < sa.length(); i++) {
                JSONObject so = sa.optJSONObject(i);
                if (so != null) g.semesters.add(SemesterGrades.fromJson(so));
            }
        }
        g.recomputeAll();
        return g;
    }

    public static Grades fromJson(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    /**
     * 从正方教务 tabGrid DOM 抓取的二维字符串数组解析成绩。
     * 列索引映射见 docs/DATA-SCHEMA.md 0 节：
     *   [1] 学年 (2024-2025)
     *   [2] 学期 (1 / 2)
     *   [3] 课程代码
     *   [4] 课程名称
     *   [5] 课程性质
     *   [6] 学分
     *   [8] 绩点
     *   [9] 成绩性质 (正常考试)
     *   [11] 开课学院
     *   [12] 课程标记 (主修)
     *   [16/17] 教师
     *   [22] 成绩（或当列偏移时回退检测）
     */
    public static Grades parseFromDomRows(String rowsJson) {
        Grades out = new Grades();
        if (rowsJson == null || rowsJson.trim().isEmpty()) return out;
        try {
            JSONArray rows;
            String trimmed = rowsJson.trim();
            if (trimmed.startsWith("{")) {
                JSONObject wrapper = new JSONObject(trimmed);
                rows = wrapper.optJSONArray("rows");
                if (rows == null) rows = wrapper.optJSONArray("domSample");
            } else {
                rows = new JSONArray(trimmed);
            }
            if (rows == null) return out;

            Map<String, SemesterGrades> bySem = new LinkedHashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONArray r = rows.optJSONArray(i);
                if (r == null || r.length() < 23) continue;

                String name = JwglxtFetcher.clean(r.optString(4, ""));
                if (name.isEmpty() || "课程名称".equals(name)) continue;

                String year = JwglxtFetcher.clean(r.optString(1, ""));
                String term = JwglxtFetcher.clean(r.optString(2, ""));
                if (year.isEmpty()) year = "未知学年";
                if (term.isEmpty()) term = "1";

                String semId = year + "-" + term;
                SemesterGrades sem = bySem.get(semId);
                if (sem == null) {
                    sem = new SemesterGrades();
                    sem.id = semId;
                    sem.name = year + " 学年第" + term + "学期";
                    bySem.put(semId, sem);
                }

                CourseGrade cg = new CourseGrade();
                cg.code = JwglxtFetcher.clean(r.optString(3, ""));
                cg.name = name;
                cg.nature = JwglxtFetcher.clean(r.optString(5, ""));
                cg.credit = JwglxtFetcher.parseDouble(r.optString(6, "0"));
                cg.gradePoint = tryParseDouble(r.optString(8, ""));
                cg.examType = JwglxtFetcher.clean(r.optString(9, ""));
                cg.college = JwglxtFetcher.clean(r.optString(11, ""));
                cg.category = JwglxtFetcher.clean(r.optString(12, ""));

                String c17 = JwglxtFetcher.clean(r.optString(17, ""));
                String c16 = JwglxtFetcher.clean(r.optString(16, ""));
                cg.teacher = !c17.isEmpty() ? c17 : (!c16.contains("-") ? c16 : "");

                String scoreStr = JwglxtFetcher.clean(r.optString(22, ""));
                if (scoreStr.isEmpty()) {
                    scoreStr = JwglxtFetcher.clean(r.optString(21, ""));
                }
                cg.score = scoreStr;
                cg.scoreValue = tryParseDouble(scoreStr);
                if (cg.gradePoint == null && cg.scoreValue != null) {
                    cg.gradePoint = estimateGradePoint(cg.scoreValue);
                }
                cg.isPassed = evaluatePassed(cg.score, cg.scoreValue);
                sem.courses.add(cg);
            }

            out.semesters.addAll(bySem.values());
            out.sortSemestersDescending();
            out.recomputeAll();
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean evaluatePassed(String scoreText, Double scoreVal) {
        if (scoreVal != null) {
            return scoreVal >= 60.0;
        }
        if (scoreText == null) return true;
        String s = scoreText.trim();
        if (s.contains("不及格") || s.contains("不合格") || s.contains("缺考") || s.contains("作弊") || s.contains("未通过")) {
            return false;
        }
        return true;
    }

    public static Double tryParseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double estimateGradePoint(double score) {
        if (score < 60.0) return 0.0;
        return round2(Math.min(5.0, Math.max(1.0, (score - 50.0) / 10.0)));
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
