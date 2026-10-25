import java.util.*;

/** DomainRules 匹配逻辑验证（纯 JVM，不依赖 Android） */
public class RuleMatchTest {

    static final Set<String> allow = new HashSet<>(Arrays.asList(
            "amap.com", "alipay.com", "alicdn.com", "taobao.com",
            "huachenjie.com", "netease.im", "getui.com", "hicloud.com",
            "dbankcloud.com", "aliyuncs.com", "weixin.qq.com"));

    static final Set<String> block = new HashSet<>(Arrays.asList(
            "adservice.sigmob.cn", "sigmob.cn", "api-access.pangolin-sdk-toutiao.com",
            "p1-lm.adkwai.com", "adkwai.com", "apps.oceanengine.com",
            "www.csjplatform.com", "gromore.pangolin-sdk-toutiao.com"));

    static final Set<String> observe = new HashSet<>(Arrays.asList(
            "applog.snssdk.com", "snssdk.com", "umeng.com", "log.snssdk.com"));

    static boolean matches(String host, Set<String> rules) {
        if (host == null || host.isEmpty()) return false;
        String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (rules.contains(h)) return true;
        int idx = h.indexOf('.');
        while (idx >= 0) {
            String suffix = h.substring(idx + 1);
            if (rules.contains(suffix)) return true;
            idx = h.indexOf('.', idx + 1);
        }
        return false;
    }

    static boolean shouldBlock(String host, boolean blockObserve) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        if (matches(h, allow)) return false;
        if (matches(h, block)) return true;
        return blockObserve && matches(h, observe);
    }

    static int pass = 0, fail = 0;
    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  PASS  " + name); }
        else { fail++; System.out.println("  FAIL  " + name); }
    }

    public static void main(String[] a) {
        System.out.println("=== 域名匹配逻辑测试 ===\n");

        // 广告应拦
        check("apmplus.volces.com 类广告拦",
                shouldBlock("adservice.sigmob.cn", false));
        check("子域继承 sigmob.cn",
                shouldBlock("tm.sigmob.cn", false));
        check("穿山甲接口拦",
                shouldBlock("api-access.pangolin-sdk-toutiao.com", false));
        check("快手联盟拦",
                shouldBlock("p2-lm.adkwai.com", false));
        check("聚合平台拦",
                shouldBlock("gromore.pangolin-sdk-toutiao.com", false));

        // ===== 白名单优先级：这是最关键的正确性保障 =====
        check("高德定位必须放行 (adiu.amap.com)",
                !shouldBlock("adiu.amap.com", false));
        check("高德子域放行 (lbs.amap.com)",
                !shouldBlock("lbs.amap.com", false));
        check("支付宝放行",
                !shouldBlock("mobilegw.alipay.com", false));
        check("App 自有 API 放行",
                !shouldBlock("api.huachenjie.com", false));
        check("OSS 存储放行",
                !shouldBlock("sd-apk.oss-cn-beijing.aliyuncs.com", false));

        // 华为推送：曾误判为可拦，这里锁死必须放行
        check("华为推送 hicloud 放行",
                !shouldBlock("metrics2.data.hicloud.com", false));
        check("华为 GRS 路由放行",
                !shouldBlock("grs.dbankcloud.com", false));

        // observe 行为
        check("observe 默认不拦 (applog.snssdk.com)",
                !shouldBlock("applog.snssdk.com", false));
        check("observe 开启后拦 (applog.snssdk.com)",
                shouldBlock("applog.snssdk.com", true));

        // 未知域名
        check("未知域名不拦",
                !shouldBlock("some-unknown-site.com", true));

        // 边界
        check("空域名不拦", !shouldBlock("", true));
        check("null 不拦", !shouldBlock(null, true));
        check("尾部点处理", shouldBlock("sigmob.cn.", false));

        // 大小写
        check("大小写不敏感", shouldBlock("ADSERVICE.SIGMOB.CN", false));

        // 不能因为后缀匹配误伤：example-sigmob.cn 不应命中 sigmob.cn
        check("相似域名不误伤 (notsigmob.cn)",
                !shouldBlock("notsigmob.cn", false));

        System.out.println("\n=== 结果: " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }
}
