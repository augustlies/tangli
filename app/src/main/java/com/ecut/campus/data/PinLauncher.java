package com.ecut.campus.data;

import java.util.Locale;

/**
 * 哪些桌面会悄悄忽略「一键添加小组件」（requestPinAppWidget 返回成功却从不弹窗）。
 * 真机实测：vivo / iQOO 的 OriginOS 桌面（com.bbk.launcher2）即使开了「桌面快捷方式」权限也不弹。
 * 命中时添加卡片直接给手动步骤，不再自动请求、不再等。
 */
public final class PinLauncher {

    /** 已知会忽略放置请求的桌面包名。 */
    static final String[] IGNORING_LAUNCHERS = {
            "com.bbk.launcher2", // vivo / iQOO（OriginOS / Funtouch）
    };

    /** 已知会忽略的厂商（Build.MANUFACTURER），桌面包名解析不到或换过时兜底。 */
    static final String[] IGNORING_MAKERS = {"vivo", "iqoo"};

    private PinLauncher() {}

    /**
     * @param homePackage  默认桌面的包名（解析不到传 null）
     * @param manufacturer Build.MANUFACTURER
     */
    public static boolean ignoresPinRequest(String homePackage, String manufacturer) {
        if (homePackage != null) {
            String p = homePackage.trim();
            for (String l : IGNORING_LAUNCHERS) if (l.equals(p)) return true;
        }
        if (manufacturer != null) {
            String m = manufacturer.trim().toLowerCase(Locale.ROOT);
            for (String k : IGNORING_MAKERS) if (m.equals(k)) return true;
        }
        return false;
    }

    /** OriginOS 桌面的手动添加步骤（按行）。 */
    public static String originSteps() {
        return "1. 长按桌面空白处\n"
                + "2. 点「原子组件」（或「小组件」）\n"
                + "3. 找到或搜「烫梨」\n"
                + "4. 选「今日课程」\n"
                + "5. 拖到桌面";
    }
}
