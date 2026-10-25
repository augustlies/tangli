package com.ecut.campus.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 正方教务的周次表达式解析。
 *
 * 真实样本（本机）：{@code 5-12周}、{@code 10-13周}、{@code 9-16周}
 * 必须兼容但本机未出现：
 *   {@code 1-5周,7-12周}   多区间
 *   {@code 1-16周(单)} / {@code 1-16周(双)}   单双周
 *   {@code 1,3,5,7周}      离散周（[ODD]）
 *   {@code 3周}            单周
 *   {@code 1-16周(单周)} / {@code 1-16周(双周)}
 *   {@code 1-16}           无「周」字
 *
 * 解析失败不抛异常：返回 [ODD] 表示「全周都上」是错的，宁可返回空表示「无法判断」，
 * 由上层决定降级策略（保底按全周显示，避免课程凭空消失）。
 */
public final class WeekRange {

    private WeekRange() {}

    /** 解析结果 */
    public static final class Parsed {
        /** 周次列表，升序去重。解析失败时为空 */
        public final List<Integer> weeks;
        /** 原文是否含「单」/「双」标记 */
        public final boolean hasParity;
        /** 单周 true / 双周 false / 无标记 null */
        public final Boolean oddOnly;
        /** 原始表达式 */
        public final String raw;

        Parsed(List<Integer> weeks, boolean hasParity, Boolean oddOnly, String raw) {
            this.weeks = weeks;
            this.hasParity = hasParity;
            this.oddOnly = oddOnly;
            this.raw = raw;
        }

        public boolean ok() {
            return !weeks.isEmpty();
        }

        public boolean contains(int week) {
            return weeks.contains(week);
        }

        public int min() {
            return weeks.isEmpty() ? 0 : weeks.get(0);
        }

        public int max() {
            return weeks.isEmpty() ? 0 : weeks.get(weeks.size() - 1);
        }
    }

    /**
     * 解析周次表达式。
     *
     * @param expr 如 {@code 5-12周}、{@code 1-5周,7-12周}、{@code 1-16周(单)}
     * @return 永不为 null；解析失败时 {@link Parsed#ok()} 为 false
     */
    public static Parsed parse(String expr) {
        String raw = expr == null ? "" : expr.trim();
        if (raw.isEmpty()) return new Parsed(new ArrayList<>(), false, null, raw);

        // 1. 检测单双周标记（必须在剥离中文数字前做）
        Boolean oddOnly = null;
        boolean hasParity = false;
        if (raw.contains("单")) {
            hasParity = true;
            oddOnly = Boolean.TRUE;
        } else if (raw.contains("双")) {
            hasParity = true;
            oddOnly = Boolean.FALSE;
        }

        // 2. 剥离所有非数字/非分隔符字符
        //    保留：数字、'-'（区间连接）、','（多区间分隔）
        //    统一：全角逗号/顿号 → ','
        //    丢弃：周、()、（）、单、双、空格
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                sb.append(c);
            } else if (c == ',' || c == '，' || c == '、') {
                sb.append(',');
            }
            // 其余字符（周、括号、单、双、空格）全部丢弃
        }

        String cleaned = sb.toString();
        if (cleaned.isEmpty()) return new Parsed(new ArrayList<>(), hasParity, oddOnly, raw);

        // 3. 按逗号切段，每段可能是 "a-b" 或 "a"
        List<Integer> result = new ArrayList<>();
        String[] parts = cleaned.split(",");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            if (dash > 0 && dash < part.length() - 1) {
                // 区间 a-b
                Integer a = toInt(part.substring(0, dash));
                Integer b = toInt(part.substring(dash + 1));
                if (a == null || b == null) continue;
                if (a > b) { int t = a; a = b; b = t; } // 容错：写反了也认
                if (b - a > 60) continue;              // 异常区间，丢弃
                for (int w = a; w <= b; w++) addDistinct(result, w);
            } else {
                // 单值 a，或形如 "5-" / "-5" 的残缺写法
                Integer a = toInt(part.replace("-", ""));
                if (a != null && a > 0 && a <= 60) addDistinct(result, a);
            }
        }

        // 4. 应用单双周过滤
        if (oddOnly != null) {
            List<Integer> filtered = new ArrayList<>();
            for (int w : result) {
                boolean isOdd = (w % 2 == 1);
                if (oddOnly ? isOdd : !isOdd) filtered.add(w);
            }
            result = filtered;
        }

        // 5. 排序去重
        result = sortedDistinct(result);
        return new Parsed(result, hasParity, oddOnly, raw);
    }

    /** 便捷方法：判断某周是否上课。解析失败时返回 true（保底显示，避免课程消失） */
    public static boolean isActive(String expr, int week) {
        Parsed p = parse(expr);
        if (!p.ok()) return true;
        return p.contains(week);
    }

    private static Integer toInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void addDistinct(List<Integer> list, int v) {
        if (v <= 0 || v > 60) return;
        if (!list.contains(v)) list.add(v);
    }

    private static List<Integer> sortedDistinct(List<Integer> in) {
        List<Integer> out = new ArrayList<>();
        for (int v : in) if (!out.contains(v)) out.add(v);
        out.sort(Integer::compareTo);
        return out;
    }

    /** 把周次列表压回区间表达式，用于导入导出时的紧凑表示 */
    public static String compress(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) return "";
        List<Integer> s = sortedDistinct(weeks);
        StringBuilder sb = new StringBuilder();
        int runStart = s.get(0);
        int prev = runStart;
        for (int i = 1; i <= s.size(); i++) {
            int cur = (i < s.size()) ? s.get(i) : Integer.MIN_VALUE;
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            // 收尾一个区间
            if (sb.length() > 0) sb.append(',');
            if (runStart == prev) sb.append(runStart);
            else sb.append(runStart).append('-').append(prev);
            if (cur != Integer.MIN_VALUE) {
                runStart = cur;
                prev = cur;
            }
        }
        return sb.toString();
    }
}
