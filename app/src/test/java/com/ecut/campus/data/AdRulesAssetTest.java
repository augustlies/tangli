package com.ecut.campus.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/** 合并后的广告规则：数量正常、广告 SDK 被拦、学校与闪动校园本体不被拦。 */
public class AdRulesAssetTest {

    private static Set<String> set(JSONArray a) throws Exception {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        return s;
    }

    private static boolean hit(Set<String> rules, String host) {
        String[] p = host.split("\\.");
        for (int i = 0; i < p.length; i++) {
            if (rules.contains(String.join(".", java.util.Arrays.copyOfRange(p, i, p.length)))) return true;
        }
        return false;
    }

    @Test
    public void mergedRulesAreSane() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/ad-rules.runtime.json")),
                StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);
        Set<String> block = set(root.getJSONArray("block"));
        Set<String> allow = set(root.getJSONArray("allow"));
        assertTrue("规则数量异常: " + block.size(), block.size() > 10000);
        for (String ad : new String[]{"gromore.pangolin-sdk-toutiao.com", "adservice.sigmob.cn", "mi.gdt.qq.com"}) {
            assertTrue(ad, hit(block, ad) && !hit(allow, ad));
        }
        for (String ok : new String[]{"api2.lptiyu.com", "jwglxt.ecut.edu.cn", "www.ecut.edu.cn",
                "mp.weixin.qq.com", "alogus.umeng.com"}) {
            assertFalse(ok, hit(block, ok) && !hit(allow, ok));
        }
    }
}
