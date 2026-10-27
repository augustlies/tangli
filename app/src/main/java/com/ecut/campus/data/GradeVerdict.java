package com.ecut.campus.data;

import java.util.Locale;

/**
 * 翻牌批语：新成绩翻开前先给一句话（按分数段），再点一下才看数字。
 * 纯 Java；百分制、五级制（优良中及格不及格）、字母制、两级制（通过/合格）都认。
 */
public final class GradeVerdict {
    private GradeVerdict() {}

    public enum Band { TOP, GOOD, MID, PASS, FAIL, PASSED }

    private static final String[][] LINES = {
            // TOP ≥90 / 优秀 / A
            {"这门你是来砸场子的", "老师看了都想鼓掌", "这分数，可以裱起来", "学神附体，收下膝盖", "实力派，不解释"},
            // GOOD 80–89 / 良好 / B
            {"很能打，离满分就差一口气", "稳稳的，值得一顿好饭", "这门课被你拿捏了", "优等生的从容，你有了"},
            // MID 70–79 / 中等 / C
            {"中规中矩，稳中有进", "不惊不险，刚刚好", "及格线在你身后很远", "平稳落地，下次再冲"},
            // PASS 60–69 / 及格 / D
            {"惊险过关，心跳也算学分", "过了就是胜利！", "六十分万岁，一分不浪费", "压线也是线，稳稳拿下"},
            // FAIL <60 / 不及格 / F
            {"这次没站稳，补考见真章", "先别慌，还有翻盘的机会", "摔一跤而已，不算输", "抱抱你，下次一起拿下"},
            // PASSED 通过 / 合格（两级制）
            {"通过！这门课给你盖章了", "合格到手，轻松翻篇", "过了，下一门"},
    };

    /** 这些不是分数，是考试状态：不给批语，免得缓考被说成挂科。 */
    private static final String[] STATUS = {"缓考", "缺考", "旷考", "作弊", "违纪", "取消", "免考", "待定"};

    /**
     * 分数段。认不出（空、缓考/缺考之类）返回 null，调用方直接跳过批语。
     * 其余 isPassed=false 一律算 FAIL（教务说没过就是没过）。
     */
    public static Band band(String score, Double scoreValue, boolean isPassed) {
        String s = score == null ? "" : score.trim().toUpperCase(Locale.ROOT);
        for (String w : STATUS) if (s.contains(w)) return null;
        if (!isPassed) return s.isEmpty() && scoreValue == null ? null : Band.FAIL;
        if (s.contains("不及格") || s.contains("不合格") || s.contains("不通过") || s.contains("未通过")) return Band.FAIL;
        if (s.startsWith("优")) return Band.TOP;
        if (s.startsWith("良")) return Band.GOOD;
        if (s.startsWith("中")) return Band.MID;
        if (s.startsWith("及格")) return Band.PASS;
        if (s.equals("合格") || s.equals("通过") || s.equals("P") || s.equals("PASS")) return Band.PASSED;
        if (s.matches("[A-F][+-]?")) {
            switch (s.charAt(0)) {
                case 'A': return Band.TOP;
                case 'B': return Band.GOOD;
                case 'C': return Band.MID;
                case 'D': return Band.PASS;
                default: return Band.FAIL;
            }
        }
        Double v = null;
        if (s.matches("[0-9]+(\\.[0-9]+)?")) {
            try { v = Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        if (v == null && s.isEmpty()) v = scoreValue;
        if (v == null || v.isNaN() || v < 0) return null;
        if (v >= 90) return Band.TOP;
        if (v >= 80) return Band.GOOD;
        if (v >= 70) return Band.MID;
        if (v >= 60) return Band.PASS;
        return Band.FAIL;
    }

    public static String[] lines(Band b) {
        return LINES[b.ordinal()].clone();
    }

    /** 按 seed 挑一句（同一门成绩传同一个 seed，每次看到同一句）；band 为 null 返回空串。 */
    public static String pick(Band b, long seed) {
        if (b == null) return "";
        String[] l = LINES[b.ordinal()];
        return l[(int) Math.floorMod(seed, (long) l.length)];
    }
}
