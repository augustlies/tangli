package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

/** 1.2.7：「应用图标」选项和存的值、清单里的 activity-alias 对得上。 */
public class AppIconOptionTest {

    @Test
    public void defaultIsPear() {
        assertEquals(AppIconOption.PEAR, AppIconOption.DEFAULT);
        assertEquals("刻度梨", AppIconOption.DEFAULT.label);
    }

    @Test
    public void unknownOrEmptyKeyFallsBackToDefault() {
        assertEquals(AppIconOption.PEAR, AppIconOption.fromKey(""));
        assertEquals(AppIconOption.PEAR, AppIconOption.fromKey(null));
        assertEquals(AppIconOption.PEAR, AppIconOption.fromKey("crest"));
        assertEquals(AppIconOption.PEAR, AppIconOption.fromKey("ART"));
    }

    @Test
    public void keysRoundTrip() {
        for (AppIconOption o : AppIconOption.values()) assertSame(o, AppIconOption.fromKey(o.key));
        assertEquals(AppIconOption.ART, AppIconOption.fromKey("art"));
    }

    @Test
    public void segmentIndexMapping() {
        assertArrayEquals(new String[]{"刻度梨", "大肥鱼", "本本"}, AppIconOption.labels());
        assertEquals(AppIconOption.PEAR, AppIconOption.at(0));
        assertEquals(AppIconOption.ART, AppIconOption.at(1));
        assertEquals(AppIconOption.DEFAULT, AppIconOption.at(5));
        assertEquals(AppIconOption.DEFAULT, AppIconOption.at(-1));
    }

    @Test
    public void benbenIsHiddenEasterEgg() {
        assertTrue(AppIconOption.BENBEN.hidden);
        assertFalse(AppIconOption.DEFAULT.hidden);
        assertArrayEquals(new AppIconOption[]{AppIconOption.PEAR, AppIconOption.ART}, AppIconOption.visible(false));
        assertArrayEquals(AppIconOption.values(), AppIconOption.visible(true));
        assertEquals(AppIconOption.BENBEN, AppIconOption.fromKey("benben"));
    }

    @Test
    public void aliasesAreDistinctAndInAppPackage() {
        assertEquals(3, java.util.Arrays.stream(AppIconOption.values()).map(o -> o.alias).distinct().count());
        assertNotEquals(AppIconOption.PEAR.alias, AppIconOption.ART.alias);
        for (AppIconOption o : AppIconOption.values()) assertTrue(o.alias.startsWith("com.ecut.campus.Launcher"));
    }
}
