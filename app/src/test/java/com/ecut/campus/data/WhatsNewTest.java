package com.ecut.campus.data;

import static org.junit.Assert.*;

import org.junit.Test;

/** 1.2.7：「新版本说明」只给从老版本升上来的人看一次，新装不弹。 */
public class WhatsNewTest {

    @Test
    public void freshInstallNeverShows() {
        assertFalse(WhatsNew.shouldShow(0, 43, false));
        assertFalse(WhatsNew.shouldShow(0, 44, false));
    }

    @Test
    public void upgradeFromOldVersionWithoutRecordShowsOnce() {
        // 1.2.6 及以前没记过 versionCode：靠覆盖安装判断
        assertTrue(WhatsNew.shouldShow(0, 43, true));
        // 打开过一次后记下 43，就不再弹
        assertFalse(WhatsNew.shouldShow(43, 43, true));
    }

    @Test
    public void laterUpdatesDoNotRepeatTheSameNotes() {
        assertFalse(WhatsNew.shouldShow(43, 44, true));
        assertFalse(WhatsNew.shouldShow(44, 44, true));
    }

    @Test
    public void downgradeOrBuildsWithoutNotesNeverShow() {
        assertFalse(WhatsNew.shouldShow(0, 42, true));
        assertFalse(WhatsNew.shouldShow(50, 43, true));
    }

    @Test
    public void knownOlderCodeShows() {
        assertTrue(WhatsNew.shouldShow(42, 43, false));
    }

    @Test
    public void upgradeDetection() {
        assertFalse(WhatsNew.isUpgrade(1_000_000L, 1_000_000L));
        assertFalse(WhatsNew.isUpgrade(1_000_000L, 1_000_500L));
        assertTrue(WhatsNew.isUpgrade(1_000_000L, 5_000_000L));
        assertFalse(WhatsNew.isUpgrade(0L, 5_000_000L));
    }
}
