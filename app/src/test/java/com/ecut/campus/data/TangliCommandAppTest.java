package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Map;

/** 1.2.0 新增：备注 / 设置 / 作息 / 同步 / 导出 / 打开。 */
public class TangliCommandAppTest {

    private static TangliCommand.Env env() {
        TangliCommand.Env e = new TangliCommand.Env();
        e.start = "2026-09-07";
        e.today = "2026-10-25";
        e.sectionStart = new int[]{0, 480, 535, 600, 655};
        e.sectionEnd = new int[]{0, 525, 580, 645, 700};
        e.settings.put(AppSetting.THEME, "0");
        e.settings.put(AppSetting.DAYS, "7");
        e.settings.put(AppSetting.GRADE_NOTIFY, "1");
        return e;
    }

    private static Timetable base() {
        Timetable t = new Timetable();
        Timetable.Course c = new Timetable.Course();
        c.key = "jx1"; c.name = "大学英语"; c.source = Timetable.SOURCE_JWGLXT;
        Timetable.Session s = new Timetable.Session();
        s.dayOfWeek = 5; s.startSection = 1; s.endSection = 2;
        s.weeks.add(1);
        c.sessions.add(s);
        t.courses.add(c);
        return t;
    }

    @Test
    public void noteSetsAndClears() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n备注 大学英语 | 交第三章作业", base(), env());
        assertEquals(1, p.changeCount());
        assertTrue(p.timetableChanged());
        assertEquals("交第三章作业", p.result.findByName("大学英语").note);
        TangliCommand.Plan again = TangliCommand.plan("烫梨指令 v1\n备注 大学英语 | 交第三章作业", p.result, env());
        assertEquals(0, again.changeCount());
        TangliCommand.Plan clear = TangliCommand.plan("烫梨指令 v1\n备注 大学英语 | 清空", p.result, env());
        assertEquals("", clear.result.findByName("大学英语").note);
        assertEquals(1, TangliCommand.plan("烫梨指令 v1\n备注 没这门课 | x", base(), env()).errorCount());
    }

    @Test
    public void setParsesAliasesAndSkipsSame() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n设置 主题=深色 ｜ 显示周末=否, 成绩提醒 开", base(), env());
        assertEquals(0, p.errorCount());
        assertEquals(1, p.changeCount());
        assertFalse(p.timetableChanged());
        Map<String, String> m = p.settings();
        assertEquals("2", m.get(AppSetting.THEME));
        assertEquals("5", m.get(AppSetting.DAYS));
        assertEquals("1", m.get(AppSetting.GRADE_NOTIFY));
        TangliCommand.Plan same = TangliCommand.plan("烫梨指令 v1\n设置 主题=跟随系统", base(), env());
        assertEquals(0, same.changeCount());
        assertEquals(1, TangliCommand.plan("烫梨指令 v1\n设置 字体=大", base(), env()).errorCount());
        assertEquals(1, TangliCommand.plan("烫梨指令 v1\n设置 深色模式=也许", base(), env()).errorCount());
        assertEquals("1", TangliCommand.plan("烫梨指令 v1\n设置 深色模式=关", base(), env()).settings().get(AppSetting.THEME));
    }

    @Test
    public void sectionShiftsLaterSections() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n作息 第1节 08:10-08:55", base(), env());
        assertEquals(0, p.errorCount());
        assertTrue(p.sectionsChanged);
        assertEquals(490, p.sectionStart[1]);
        assertEquals(535, p.sectionEnd[1]);
        assertEquals(545, p.sectionStart[2]); // 原课间 10 分钟保留，整体顺延
        assertEquals(710, p.sectionEnd[4]);
        TangliCommand.Plan two = TangliCommand.plan("烫梨指令 v1\n作息 第1节 08:00-08:40 | 第2节 08:50-09:30", base(), env());
        assertEquals(530, two.sectionStart[2]);
        assertEquals(570, two.sectionEnd[2]);
        assertEquals(1, TangliCommand.plan("烫梨指令 v1\n作息 第2节 08:30-09:00", base(), env()).errorCount());
        assertEquals(0, TangliCommand.plan("烫梨指令 v1\n作息 第1节 08:00-08:45", base(), env()).changeCount());
    }

    @Test
    public void actionsAreRecognisedLoosely() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n同步课表\n导出 诊断日志\n打开 校园邮箱\n打开 不存在", base(), env());
        assertEquals(3, p.changeCount());
        assertEquals(1, p.errorCount());
        assertEquals("timetable", p.actions("sync").get(0).value);
        assertEquals("diag", p.actions("export").get(0).value);
        assertEquals("mail", p.actions("open").get(0).value);
        assertFalse(p.timetableChanged());
        // AI 的解释长句不当成指令
        TangliCommand.Plan chatter = TangliCommand.plan("烫梨指令 v1\n打开烫梨后它会自动识别这段文字并弹出预览", base(), env());
        assertEquals(0, chatter.items.size());
    }

    @Test
    public void exportAllRoundTrips() {
        Timetable t = TangliCommand.plan("烫梨指令 v1\n备注 大学英语 | 带字典", base(), env()).result;
        TangliCommand.Env e = env();
        e.settings.put(AppSetting.THEME, "2");
        String all = TangliCommand.exportAll(t, e);
        assertTrue(all.startsWith("烫梨指令 v1 整表"));
        assertTrue(all.contains("备注 大学英语 | 带字典"));
        assertTrue(all.contains("主题=深色"));
        assertTrue(all.contains("作息 第1节 08:00-08:45"));
        TangliCommand.Env fresh = env();
        TangliCommand.Plan back = TangliCommand.plan(all, new Timetable(), fresh);
        assertEquals(0, back.errorCount());
        assertEquals("带字典", back.result.findByName("大学英语").note);
        assertEquals("2", back.settings().get(AppSetting.THEME));
        assertFalse(back.sectionsChanged);
    }

    @Test
    public void fitAllSetting() {
        TangliCommand.Plan p = TangliCommand.plan("烫梨指令 v1\n设置 一屏显示10节=开", base(), env());
        assertEquals(0, p.errorCount());
        assertEquals("1", p.settings().get(AppSetting.FIT_ALL));
    }
}
