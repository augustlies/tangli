package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 学期人设卡：只看本地课表（和成绩），按固定规则挑一个人设 + 三行数据。纯 Java，不联网、不用 AI。
 *
 * 规则按优先级从上往下，命中第一个就停：
 *  空白课表观察员  这周一节课都没有
 *  满绩卷王预备役  平均绩点 ≥ 3.7
 *  早八渡劫散仙    有早八（9:00 前开始的课）的天数 ≥ 3
 *  夜行听课侠      有晚课（18:00 后开始）的天数 ≥ 2
 *  连堂铁人        某天最长连上 ≥ 6 节（中间隔 ≥ 60 分钟就算断开，午休不算连上）
 *  课表苦行僧      一周 ≥ 26 节
 *  周X空课神仙     周一到周五至少一天没课（X = 第一个空课日）
 *  学霸稳健派      平均绩点 ≥ 3.2
 *  佛系选课人      一周 ≤ 12 节
 *  逆风翻盘候选人  平均绩点 < 2.0
 *  均衡发展型选手  以上都不是
 */
public final class SemesterPersona {
    private SemesterPersona() {}

    static final String[] DAY = {"", "一", "二", "三", "四", "五", "六", "日"};

    public static final class Stats {
        public int classDays, earlyDays, eveningDays, maxRun, weeklySections, courses;
        public List<Integer> freeWeekdays = new ArrayList<>();
        public Double gpa;
    }

    public static final class Card {
        public String title = "";
        public String tagline = "";
        public List<String> lines = new ArrayList<>();
        public Stats stats;
    }

    static final int EARLY_BEFORE = 9 * 60, EVENING_FROM = 18 * 60, BREAK_MIN = 60;

    /** 平均绩点；一门带绩点的成绩都没有（只有通过/合格之类）返回 null。全挂就是 0.0，不是 null。 */
    public static Double gpa(Grades g) {
        if (g == null) return null;
        for (Grades.SemesterGrades sem : g.semesters)
            for (Grades.CourseGrade c : sem.courses)
                if (c.gradePoint != null && c.credit > 0) return g.summary.gpa;
        return null;
    }

    /**
     * @param secStart secStart[n] / secEnd[n] = 第 n 节开始/结束的分钟数（作息），长度决定节数
     * @param gpa      平均绩点，没有成绩传 null
     */
    public static Stats stats(Timetable t, int week, int[] secStart, int[] secEnd, Double gpa) {
        int sections = Math.max(0, Math.min(secStart.length, secEnd.length) - 1);
        Stats st = new Stats();
        st.gpa = gpa;
        boolean[][] busy = new boolean[8][sections + 2];
        if (t != null && t.courses != null) {
            for (Timetable.Course c : t.courses) {
                boolean has = false;
                for (Timetable.Session s : c.sessions) {
                    if (s.dayOfWeek < 1 || s.dayOfWeek > 7 || !s.isActive(week)) continue;
                    has = true;
                    for (int k = Math.max(1, s.startSection); k <= Math.min(sections, s.endSection); k++) {
                        busy[s.dayOfWeek][k] = true;
                    }
                }
                if (has) st.courses++;
            }
        }
        for (int d = 1; d <= 7; d++) {
            int run = 0, n = 0;
            boolean evening = false, early = false;
            for (int k = 1; k <= sections; k++) {
                if (busy[d][k]) {
                    n++;
                    boolean gap = k > 1 && busy[d][k - 1] && secStart[k] - secEnd[k - 1] >= BREAK_MIN;
                    run = gap ? 1 : run + 1;
                    st.maxRun = Math.max(st.maxRun, run);
                    if (secStart[k] >= EVENING_FROM) evening = true;
                    if (secStart[k] < EARLY_BEFORE) early = true;
                } else {
                    run = 0;
                }
            }
            st.weeklySections += n;
            if (n > 0) st.classDays++;
            if (early) st.earlyDays++;
            if (evening) st.eveningDays++;
            if (n == 0 && d <= 5) st.freeWeekdays.add(d);
        }
        return st;
    }

    public static Card pick(Stats s) {
        Card c = new Card();
        c.stats = s;
        String first;
        if (s.weeklySections == 0) {
            c.title = "空白课表观察员";
            c.tagline = "课表还是一张白纸，先去同步一下吧";
            first = "本周 0 节课";
        } else if (s.gpa != null && s.gpa >= 3.7) {
            c.title = "满绩卷王预备役";
            c.tagline = "绩点在发光，别忘了也睡个好觉";
            first = gpaLine(s);
        } else if (s.earlyDays >= 3) {
            c.title = "早八渡劫散仙";
            c.tagline = "每一个早八，都是一次小型飞升";
            first = earlyLine(s);
        } else if (s.eveningDays >= 2) {
            c.title = "夜行听课侠";
            c.tagline = "月亮出来了，你还在教室里";
            first = "晚课 " + s.eveningDays + " 天";
        } else if (s.maxRun >= 6) {
            c.title = "连堂铁人";
            c.tagline = "一坐就是半天，意志力拉满";
            first = runLine(s);
        } else if (s.weeklySections >= 26) {
            c.title = "课表苦行僧";
            c.tagline = "课多不怕，怕的是没带水杯";
            first = weeklyLine(s);
        } else if (!s.freeWeekdays.isEmpty()) {
            c.title = "周" + DAY[s.freeWeekdays.get(0)] + "空课神仙";
            c.tagline = "别人上课，你在人间游荡";
            first = freeLine(s);
        } else if (s.gpa != null && s.gpa >= 3.2) {
            c.title = "学霸稳健派";
            c.tagline = "不声不响，稳稳上分";
            first = gpaLine(s);
        } else if (s.weeklySections <= 12) {
            c.title = "佛系选课人";
            c.tagline = "课少心宽，时间都是自己的";
            first = weeklyLine(s);
        } else if (s.gpa != null && s.gpa < 2.0) {
            c.title = "逆风翻盘候选人";
            c.tagline = "低开才好高走，这学期就看你了";
            first = gpaLine(s);
        } else {
            c.title = "均衡发展型选手";
            c.tagline = "不偏科不熬夜，稳得像个闹钟";
            first = weeklyLine(s);
        }
        c.lines.add(first);
        if (s.weeklySections == 0) return c; // 白纸就别再补「连上 0 节」之类，看着像算错了
        String[] more = {s.earlyDays > 0 ? earlyLine(s) : null, s.gpa != null ? gpaLine(s) : null,
                runLine(s), freeLine(s), weeklyLine(s)};
        for (String m : more) {
            if (c.lines.size() >= 3) break;
            if (m != null && !c.lines.contains(m)) c.lines.add(m);
        }
        return c;
    }

    static String earlyLine(Stats s) {
        return "早八 " + s.earlyDays + "/" + s.classDays + " 天";
    }

    static String runLine(Stats s) {
        return "最长连上 " + s.maxRun + " 节";
    }

    static String freeLine(Stats s) {
        if (s.freeWeekdays.isEmpty()) return "工作日天天有课";
        StringBuilder sb = new StringBuilder("空课日 ");
        for (int i = 0; i < s.freeWeekdays.size(); i++) {
            if (i > 0) sb.append('、');
            sb.append('周').append(DAY[s.freeWeekdays.get(i)]);
        }
        return sb.toString();
    }

    static String gpaLine(Stats s) {
        return s.gpa == null ? "还没有绩点" : String.format(Locale.US, "平均绩点 %.2f", s.gpa);
    }

    static String weeklyLine(Stats s) {
        return "每周 " + s.weeklySections + " 节 · " + s.courses + " 门课";
    }
}
