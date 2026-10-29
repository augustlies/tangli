package com.ecut.campus.data;

/**
 * 更新后第一次打开时的「新版本说明」要不要弹。只给从老版本升上来的人看，新装的不弹。
 * 纯 Java，方便单测。
 */
public final class WhatsNew {

    private WhatsNew() {}

    /** 有说明可看的最低版本号（1.2.7 = 43）。以后换说明时改这里和 strings.xml 里 whats_new_*。 */
    public static final int NOTES_CODE = 43;

    /**
     * @param lastSeen 上次打开时记下的 versionCode；从没记过（新装，或从 ≤1.2.6 升上来）为 0
     * @param current  当前 versionCode
     * @param upgraded 这次安装是覆盖升级（lastUpdateTime 晚于 firstInstallTime）而不是新装
     */
    public static boolean shouldShow(int lastSeen, int current, boolean upgraded) {
        if (current < NOTES_CODE) return false;
        if (lastSeen >= NOTES_CODE) return false;     // 这份说明已经看过
        if (lastSeen > 0) return lastSeen < current;  // 记过更老的版本号 = 升级上来的
        return upgraded;                              // 没记过：升级才弹，新装不弹
    }

    /** 覆盖安装时 lastUpdateTime 会晚于 firstInstallTime；新装两者相同（留 1 秒余量）。 */
    public static boolean isUpgrade(long firstInstallTime, long lastUpdateTime) {
        return firstInstallTime > 0 && lastUpdateTime - firstInstallTime > 1000L;
    }
}
