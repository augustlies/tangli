package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class PageTextForAiTest {

    @Test
    public void masksPersonalInfoButKeepsTimetable() {
        String raw = "欢迎您，张小明同学\n姓名：张小明  学号：202412345678\n\n"
                + "[表格]\n | 周一 | 周二\n第1-2节 | 大学物理 / 1-16周 / B105 / 李老师 | \n"
                + "第1-2节 | 大学物理 / 1-16周 / B105 / 李老师 | \n"
                + "电话 13912345678 身份证 11010519900307123X 邮箱 a.b@163.com\n"
                + "课程代码 (2026-2027-1)-B0012345-01";
        String out = PageTextForAi.clean(raw);
        assertFalse(out.contains("张小明"));
        assertFalse(out.contains("202412345678"));
        assertFalse(out.contains("13912345678"));
        assertFalse(out.contains("11010519900307123X"));
        assertFalse(out.contains("a.b@163.com"));
        assertTrue(out.contains("大学物理 / 1-16周 / B105 / 李老师"));
        assertTrue(out.contains("(2026-2027-1)-B0012345-01"));
        // 连续重复行只留一行，空行去掉
        assertEquals(out.indexOf("第1-2节"), out.lastIndexOf("第1-2节"));
        assertFalse(out.contains("\n\n"));
    }

    @Test
    public void truncatesVeryLongPages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("第").append(i).append("行 课程\n");
        String out = PageTextForAi.clean(sb.toString());
        assertTrue(out.length() <= PageTextForAi.MAX_CHARS + 40);
        assertTrue(out.endsWith("省略了）"));
        assertEquals("", PageTextForAi.clean(null));
    }

    @Test
    public void guideFromPageAsksForFullRebuild() {
        Timetable t = new Timetable();
        TangliCommand.Env env = new TangliCommand.Env();
        String g = AiGuide.buildFromPage(t, env, "第1节 08:00-08:45", "大学物理 周一 1-2节");
        assertTrue(g.startsWith("你是「烫梨」课表 App 的助手"));
        assertTrue(g.contains("烫梨指令 v1 整表"));
        assertTrue(g.trim().endsWith("大学物理 周一 1-2节"));
    }
}
