package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class PinLauncherTest {

    @Test
    public void vivoLauncherPackageIgnores() {
        assertTrue(PinLauncher.ignoresPinRequest("com.bbk.launcher2", "Google"));
        assertTrue(PinLauncher.ignoresPinRequest("com.bbk.launcher2", null));
    }

    @Test
    public void vivoAndIqooMakerIgnoresEvenWithOtherLauncher() {
        assertTrue(PinLauncher.ignoresPinRequest(null, "vivo"));
        assertTrue(PinLauncher.ignoresPinRequest("com.teslacoilsw.launcher", "VIVO"));
        assertTrue(PinLauncher.ignoresPinRequest(null, " iQOO "));
    }

    @Test
    public void othersUnchanged() {
        assertFalse(PinLauncher.ignoresPinRequest("com.miui.home", "Xiaomi"));
        assertFalse(PinLauncher.ignoresPinRequest("com.google.android.apps.nexuslauncher", "Google"));
        assertFalse(PinLauncher.ignoresPinRequest(null, null));
        assertFalse(PinLauncher.ignoresPinRequest("com.bbk.launcher2.fake", "vivotech"));
    }

    @Test
    public void originStepsNameTheWidget() {
        String s = PinLauncher.originSteps();
        assertTrue(s.contains("长按桌面空白处"));
        assertTrue(s.contains("原子组件"));
        assertTrue(s.contains("「今日课程」"));
        assertFalse(s.contains("下一节课"));
    }
}
