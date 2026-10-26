package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 金智 ehall「我的课表」：识别、位图周次、合并。样本为虚构数据。 */
public class WiseduSourceTest {

    private static String row(String name, String teacher, String room, int day, int s, int e, String skzc) {
        return "{\"KCM\":\"" + name + "\",\"SKJS\":\"" + teacher + "\",\"JASMC\":\"" + room
                + "\",\"SKXQ\":" + day + ",\"KSJC\":" + s + ",\"JSJC\":" + e + ",\"SKZC\":\"" + skzc
                + "\",\"ZCMC\":\"\",\"XF\":\"3\"}";
    }

    private static final String RAW = "{\"term\":\"2026-2027-1\",\"rows\":["
            + row("数据结构", "张老师", "仙Ⅱ-101", 1, 1, 2, "1111111111111111") + ","
            + row("数据结构", "张老师", "仙Ⅱ-101", 3, 3, 4, "1010101010101010") + ","
            + row("体育", "李老师", "体育馆", 5, 7, 8, "0111100000000000") + "]}";

    @Test
    public void detectsEhall() {
        WiseduSource w = new WiseduSource();
        assertTrue(w.detect("https://ehallapp.nju.edu.cn/jwapp/sys/wdkb/*default/index.do", null) >= 80);
        assertTrue(w.detect("https://ehall.nju.edu.cn/portal/html/select_role.html?appId=1", null)
                >= TimetableSources.MIN_CONFIDENCE);
        assertSame(w.getClass(), TimetableSources.detect(
                "https://ehallapp.nju.edu.cn/jwapp/sys/wdkb/*default/index.do", null).getClass());
        assertFalse(TimetableSources.detect("https://jw.x.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html", null)
                instanceof WiseduSource);
        assertFalse(TimetableSources.detect("https://jw.x.edu.cn/jsxsd/xskb/xskb_list.do", null)
                instanceof WiseduSource);
    }

    @Test
    public void parsesRows() {
        JwglxtFetcher.Result r = new WiseduSource().parse(RAW);
        assertEquals(2, r.courseCount);
        assertEquals("2026-2027-1", r.timetable.semester.id);
        Timetable.Course ds = r.timetable.courses.get(0);
        assertEquals("数据结构", ds.name);
        assertEquals(2, ds.sessions.size());
        assertEquals(16, ds.sessions.get(0).weeks.size());
        assertEquals(8, ds.sessions.get(1).weeks.size());
        assertEquals(Integer.valueOf(1), ds.sessions.get(1).weeks.get(0));
        Timetable.Course pe = r.timetable.courses.get(1);
        assertEquals(5, pe.sessions.get(0).dayOfWeek);
        assertEquals(7, pe.sessions.get(0).startSection);
        assertEquals(8, pe.sessions.get(0).endSection);
        assertEquals("[2, 3, 4, 5]", pe.sessions.get(0).weeks.toString());
    }

    @Test
    public void portalGivesHint() {
        JwglxtFetcher.Result r = new WiseduSource().parse("NOAPP|ehall.nju.edu.cn/portal/html/select_role.html");
        assertEquals(0, r.courseCount);
        assertTrue(r.warnings.get(0).contains("我的课表"));
    }
    @Test
    public void parsesListViewText() {
        java.util.List<WiseduSource.Slot> sl = WiseduSource.parseListSlots(
                "周三 2-4节 1-3周,10-13周 仙Ⅱ-304,周五 5-8节 2-18周(双) 仙1-216");
        assertEquals(2, sl.size());
        assertEquals(3, sl.get(0).day);
        assertEquals(2, sl.get(0).start);
        assertEquals(4, sl.get(0).end);
        assertEquals("[1, 2, 3, 10, 11, 12, 13]", sl.get(0).weeks.toString());
        assertEquals("仙Ⅱ-304", sl.get(0).location);
        assertEquals(5, sl.get(1).day);
        assertTrue(sl.get(1).weeks.contains(18));
        assertFalse(sl.get(1).weeks.contains(3));
        assertTrue(WiseduSource.parseListSlots("自由时间 1-16周").isEmpty());
    }

    @Test
    public void parsesGradText() {
        java.util.List<WiseduSource.Slot> sl = WiseduSource.parseGradSlots(
                "5-9单周,13-17单周 星期五[3-4节]仙Ⅰ-319;4-18周 星期一[3-4节]");
        assertEquals(2, sl.size());
        assertEquals("[5, 7, 9, 13, 15, 17]", sl.get(0).weeks.toString());
        assertEquals("仙Ⅰ-319", sl.get(0).location);
        assertEquals(1, sl.get(1).day);
        assertEquals(15, sl.get(1).weeks.size());
    }

    @Test
    public void importsDomAndGradPayloads() {
        String dom = "{\"term\":\"\",\"termName\":\"2026-2027学年 第1学期\",\"dom\":["
                + "{\"name\":\"高等数学\",\"teacher\":\"王老师\",\"tl\":\"周一 1-2节 1-16周 仙Ⅰ-101\"}]}";
        JwglxtFetcher.Result r = new WiseduSource().parse(dom);
        assertEquals(1, r.courseCount);
        assertEquals("2026-2027-1", r.timetable.semester.id);
        String grad = "{\"term\":\"20261\",\"termName\":\"2026-2027学年第一学期\",\"grad\":["
                + "{\"KCMC\":\"矩阵分析\",\"RKJS\":\"赵老师\",\"PKSJDD\":\"1-16周 星期二[5-6节]仙Ⅱ-201\"}]}";
        r = new WiseduSource().parse(grad);
        assertEquals(1, r.courseCount);
        assertEquals(2, r.timetable.courses.get(0).sessions.get(0).dayOfWeek);
        assertTrue(new WiseduSource().detect("https://ehallapp.nju.edu.cn/gsapp/sys/wdkbapp/*default/index.do", null)
                >= TimetableSources.MIN_CONFIDENCE);
    }
}
