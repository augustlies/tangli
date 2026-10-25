package com.ecut.campus.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 烫梨指令里「设置 xx=yy」能改的开关。只描述名字和取值，真正读写由 App 层完成。
 * 值统一成规范写法：主题 0/1/2（跟随系统/浅色/深色）、显示天数 5/7、开关 1/0、昵称原文。
 */
public final class AppSetting {

    private AppSetting() {}

    public static final String THEME = "theme";
    public static final String DAYS = "days";
    public static final String INACTIVE = "inactive";
    public static final String GRADE_NOTIFY = "gradeNotify";
    public static final String FIT_ALL = "fitAll";
    public static final String UPDATE_CHECK = "updateCheck";
    public static final String OBSERVE = "observe";
    public static final String SHARE_NAME = "shareName";

    /** 导出顺序 + 导出时用的名字。 */
    public static final String[] ORDER = {THEME, DAYS, INACTIVE, FIT_ALL, GRADE_NOTIFY, UPDATE_CHECK, OBSERVE, SHARE_NAME};

    private static final Map<String, String> ALIAS = new LinkedHashMap<>();
    static {
        alias(THEME, "主题", "外观", "深色模式", "夜间模式", "暗色模式");
        alias(DAYS, "显示天数", "显示周末", "周末");
        alias(INACTIVE, "显示非本周课程", "显示其他周课程", "显示非本周课", "非本周课程");
        alias(FIT_ALL, "一屏显示10节", "一屏显示全部节次", "显示全部节次", "压缩课表", "一屏10节");
        alias(GRADE_NOTIFY, "成绩提醒", "新成绩提醒", "出成绩提醒");
        alias(UPDATE_CHECK, "自动检查更新", "检查更新", "更新提醒");
        alias(OBSERVE, "拦截统计域名", "拦截观察名单", "增强拦截");
        alias(SHARE_NAME, "分享昵称", "昵称");
    }

    private static void alias(String key, String... names) {
        for (String n : names) ALIAS.put(n, key);
    }

    public static String label(String key) {
        switch (key) {
            case THEME: return "主题";
            case DAYS: return "显示天数";
            case INACTIVE: return "显示非本周课程";
            case FIT_ALL: return "一屏显示10节";
            case GRADE_NOTIFY: return "成绩提醒";
            case UPDATE_CHECK: return "自动检查更新";
            case OBSERVE: return "拦截统计域名";
            case SHARE_NAME: return "分享昵称";
            default: return key;
        }
    }

    /** 规范值 → 人话（导出指令、预览都用这个，导出后能原样再导入）。 */
    public static String display(String key, String value) {
        if (value == null) value = "";
        switch (key) {
            case THEME: return "1".equals(value) ? "浅色" : "2".equals(value) ? "深色" : "跟随系统";
            case DAYS: return "5".equals(value) ? "5" : "7";
            case SHARE_NAME: return value;
            default: return "1".equals(value) ? "开" : "关";
        }
    }

    public static String keyOf(String name) {
        if (name == null) return null;
        String n = name.trim();
        String k = ALIAS.get(n);
        if (k != null) return k;
        for (String key : ORDER) if (key.equalsIgnoreCase(n)) return key;
        return null;
    }

    /**
     * @return {key, 规范值}
     * @throws IllegalArgumentException 名字或取值认不出，message 是给人看的原因
     */
    public static String[] resolve(String name, String rawValue) {
        String key = keyOf(name);
        if (key == null) throw new IllegalArgumentException("没有「" + name + "」这个设置");
        String v = rawValue == null ? "" : rawValue.trim();
        String n = name.trim();
        switch (key) {
            case THEME: {
                if (n.contains("深色") || n.contains("夜间") || n.contains("暗色")) {
                    Boolean b = bool(v);
                    if (b == null) {
                        if (v.contains("跟随") || v.contains("系统") || v.contains("自动")) return new String[]{key, "0"};
                        throw new IllegalArgumentException(n + " 只能是 开/关/跟随系统");
                    }
                    return new String[]{key, b ? "2" : "1"};
                }
                if (v.contains("跟随") || v.contains("系统") || v.contains("自动") || v.equals("0")) return new String[]{key, "0"};
                if (v.contains("浅") || v.contains("亮") || v.contains("白") || v.equalsIgnoreCase("light") || v.equals("1")) return new String[]{key, "1"};
                if (v.contains("深") || v.contains("暗") || v.contains("黑") || v.contains("夜") || v.equalsIgnoreCase("dark") || v.equals("2")) return new String[]{key, "2"};
                throw new IllegalArgumentException("主题只能是 跟随系统/浅色/深色");
            }
            case DAYS: {
                if (n.contains("周末")) {
                    Boolean b = bool(v);
                    if (b == null) throw new IllegalArgumentException("显示周末 只能是 是/否");
                    return new String[]{key, b ? "7" : "5"};
                }
                String d = v.replaceAll("[^0-9]", "");
                if (v.contains("五") || d.equals("5")) return new String[]{key, "5"};
                if (v.contains("七") || d.equals("7")) return new String[]{key, "7"};
                throw new IllegalArgumentException("显示天数只能是 5 或 7");
            }
            case SHARE_NAME: {
                if (v.length() > 20) throw new IllegalArgumentException("昵称最多 20 个字");
                return new String[]{key, v};
            }
            default: {
                Boolean b = bool(v);
                if (b == null) throw new IllegalArgumentException(label(key) + " 只能是 开/关");
                return new String[]{key, b ? "1" : "0"};
            }
        }
    }

    static Boolean bool(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "是": case "开": case "开启": case "打开": case "显示": case "要": case "on": case "true": case "yes": case "1": case "启用":
                return Boolean.TRUE;
            case "否": case "关": case "关闭": case "隐藏": case "不显示": case "不要": case "off": case "false": case "no": case "0": case "停用":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}
