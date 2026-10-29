package com.ecut.campus.data;

/**
 * 「应用图标」的选项和它们对应的桌面入口（AndroidManifest 里的 activity-alias）。
 * 纯 Java，方便单测；真正切换由 AppIcon 调 PackageManager。
 */
public enum AppIconOption {
    /** 默认：刻度梨（1.2.7 起） */
    PEAR("pear", "刻度梨", "com.ecut.campus.LauncherPear", false),
    /** 备选：大肥鱼（原来的插画小人，无校徽版） */
    ART("art", "大肥鱼", "com.ecut.campus.LauncherArt", false),
    /** 彩蛋：本本（3D 安全帽女孩）。默认不显示，在应用图标里连点当前选中的卡片解锁 */
    BENBEN("benben", "本本", "com.ecut.campus.LauncherBenben", true);

    public final String key;
    public final String label;
    /** activity-alias 的完整类名（不随 debug 包名后缀变） */
    public final String alias;
    /** 彩蛋：没解锁前不出现在选择器里 */
    public final boolean hidden;

    AppIconOption(String key, String label, String alias, boolean hidden) {
        this.key = key;
        this.label = label;
        this.alias = alias;
        this.hidden = hidden;
    }

    /** 连点几次当前选中的卡片解锁彩蛋 */
    public static final int UNLOCK_TAPS = 5;

    /** 选择器里该显示哪些（按顺序）；正在用彩蛋图标时它也算解锁。 */
    public static AppIconOption[] visible(boolean unlocked) {
        java.util.List<AppIconOption> out = new java.util.ArrayList<>();
        for (AppIconOption o : values()) if (!o.hidden || unlocked) out.add(o);
        return out.toArray(new AppIconOption[0]);
    }

    public static final AppIconOption DEFAULT = PEAR;

    /** 存的值认不出来（空、老版本、手改）一律回默认。 */
    public static AppIconOption fromKey(String key) {
        if (key != null) for (AppIconOption o : values()) if (o.key.equals(key)) return o;
        return DEFAULT;
    }

    /** 按分段选择器的位置取（越界回默认）。 */
    public static AppIconOption at(int index) {
        AppIconOption[] v = values();
        return index >= 0 && index < v.length ? v[index] : DEFAULT;
    }

    public static String[] labels() {
        AppIconOption[] v = values();
        String[] out = new String[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i].label;
        return out;
    }
}
