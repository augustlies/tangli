package com.ecut.campus;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 从 assets/ad-rules.runtime.json 加载域名规则。
 *
 * 匹配策略（顺序即优先级）：
 *   1. allow 命中  -> 放行（白名单最高优先级，避免误伤定位/支付/推送）
 *   2. block 命中  -> 拦截
 *   3. observe 命中且用户开启 -> 拦截
 *   4. 其余 -> 放行
 *
 * 域名按后缀匹配：规则 "amap.com" 同时命中 "adiu.amap.com" 与 "amap.com" 本身。
 */
public final class DomainRules {

    private static final String TAG = "DomainRules";
    private static final String ASSET = "ad-rules.runtime.json";

    private final Set<String> allow = new HashSet<>();
    private final Set<String> block = new HashSet<>();
    private final Set<String> observe = new HashSet<>();

    private static volatile DomainRules instance;

    public static DomainRules get(Context ctx) {
        DomainRules r = instance;
        if (r == null) {
            synchronized (DomainRules.class) {
                r = instance;
                if (r == null) {
                    r = new DomainRules(ctx.getApplicationContext());
                    instance = r;
                }
            }
        }
        return r;
    }

    private DomainRules(Context ctx) {
        try (InputStream is = ctx.getAssets().open(ASSET)) {
            byte[] buf = new byte[is.available()];
            int off = 0;
            while (off < buf.length) {
                int n = is.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            String json = new String(buf, 0, off, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            fill(root.optJSONArray("allow"), allow);
            fill(root.optJSONArray("block"), block);
            fill(root.optJSONArray("observe"), observe);
            Log.i(TAG, "rules loaded allow=" + allow.size()
                    + " block=" + block.size() + " observe=" + observe.size());
        } catch (Exception e) {
            Log.e(TAG, "failed to load rules", e);
        }
    }

    private static void fill(JSONArray arr, Set<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String d = arr.optString(i, "").trim().toLowerCase();
            if (!d.isEmpty()) out.add(d);
        }
    }

    public int blockCount() {
        return block.size();
    }

    public int allowCount() {
        return allow.size();
    }

    public int observeCount() {
        return observe.size();
    }

    private static boolean matches(String host, Set<String> rules) {
        if (host == null || host.isEmpty()) return false;
        String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (rules.contains(h)) return true;
        // 后缀匹配：a.b.c 逐级去掉最左标签
        int idx = h.indexOf('.');
        while (idx >= 0) {
            String suffix = h.substring(idx + 1);
            if (rules.contains(suffix)) return true;
            idx = h.indexOf('.', idx + 1);
        }
        return false;
    }

    /** @return true 表示应当拦截该域名 */
    public boolean shouldBlock(String host, boolean blockObserve) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        // 白名单优先
        if (matches(h, allow)) return false;
        if (matches(h, block)) return true;
        return blockObserve && matches(h, observe);
    }

    public Set<String> blockSet() {
        return Collections.unmodifiableSet(block);
    }
}
