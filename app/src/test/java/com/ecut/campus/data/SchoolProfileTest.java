package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.List;

/** schools.json 解析、自定义学校、页面结构样本脱敏。 */
public class SchoolProfileTest {

    @Test
    public void builtinSchoolsJsonHasVerifiedEcut() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/schools.json")),
                StandardCharsets.UTF_8);
        List<SchoolProfile> list = SchoolProfile.parseList(json);
        SchoolProfile ecut = SchoolProfile.find(list, SchoolProfile.ECUT_ID);
        assertNotNull(ecut);
        assertTrue(ecut.isEcut());
        assertEquals("东华理工大学", ecut.name);
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, ecut.vendor);
        assertEquals(SchoolProfile.Access.ATRUST, ecut.access);
        assertTrue(ecut.verified);
        assertTrue(ecut.ecutFeatures);
        assertTrue(ecut.supportsGrades());
        assertTrue(ecut.supportsBackgroundSync());
        assertEquals("/jwglxt/", ecut.jwPathPrefix());
        assertTrue(ecut.allowsHost("ehall.ecut.edu.cn"));
        assertTrue(ecut.allowsHost("ecut.edu.cn"));
        assertTrue(ecut.allowsHost("172.20.130.13"));
        assertFalse(ecut.allowsHost("ecut.edu.cn.example.com"));
        assertFalse(ecut.allowsUrl("intent://scan/#Intent;end"));
    }

    @Test
    public void brokenJsonIsLenient() {
        assertTrue(SchoolProfile.parseList("{oops").isEmpty());
        assertTrue(SchoolProfile.parseList(null).isEmpty());
        List<SchoolProfile> list = SchoolProfile.parseList(
                "{\"schools\":[{\"name\":\"缺 id\"},{\"id\":\"x\",\"name\":\"示例大学\",\"vendor\":\"qz\","
                        + "\"jwBase\":\"https://jw.example.edu.cn/jsxsd\"}]}");
        assertEquals(1, list.size());
        SchoolProfile x = list.get(0);
        assertEquals(SchoolProfile.Vendor.QIANGZHI, x.vendor);
        assertEquals("https://jw.example.edu.cn/jsxsd/", x.jwBase);
        assertEquals(SchoolProfile.Access.DIRECT, x.access);
        assertTrue(x.allowsHost("jw.example.edu.cn"));
        assertEquals(x.jwBase, x.loginUrl);
        assertFalse(x.verified);
    }

    @Test
    public void customZhengfangFromLoginUrl() {
        SchoolProfile p = SchoolProfile.custom("jw.example.edu.cn/jwglxt/xtgl/login_slogin.html", null);
        assertNotNull(p);
        assertTrue(p.isCustom());
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, p.vendor);
        assertEquals("https://jw.example.edu.cn/jwglxt/", p.jwBase);
        assertEquals("https://jw.example.edu.cn/jwglxt/xtgl/login_slogin.html", p.loginUrl);
        assertTrue(p.allowsHost("jw.example.edu.cn"));
        assertTrue(p.allowsHost("authserver.example.edu.cn"));
        assertFalse(p.allowsHost("example.com"));
        assertTrue(p.supportsGrades());
        assertFalse(p.ecutFeatures);
        assertEquals("https://jw.example.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
                new ZhengfangSource().timetableUrl(p));
    }

    @Test
    public void customQiangzhiOnIpWithPort() {
        SchoolProfile p = SchoolProfile.custom("http://10.0.0.8:8080/jsxsd/", null);
        assertNotNull(p);
        assertEquals(SchoolProfile.Vendor.QIANGZHI, p.vendor);
        assertEquals("http://10.0.0.8:8080/jsxsd/", p.jwBase);
        assertEquals(1, p.hosts.size());
        assertTrue(p.supportsGrades());
        assertTrue(p.supportsBackgroundSync());
        assertEquals("http://10.0.0.8:8080/jsxsd/xskb/xskb_list.do", new QiangzhiSource().timetableUrl(p));
    }

    @Test
    public void customUnknownAndExplicitVendor() {
        SchoolProfile other = SchoolProfile.custom("https://jwxt.example.cn/", null);
        assertEquals(SchoolProfile.Vendor.OTHER, other.vendor);
        assertFalse(other.supportsBackgroundSync());
        assertFalse(other.supportsGrades());
        assertEquals("https://jwxt.example.cn/", other.jwBase);

        // 正方部署在根目录
        SchoolProfile root = SchoolProfile.custom("https://jw.example.edu.cn/xtgl/login_slogin.html",
                SchoolProfile.Vendor.ZHENGFANG);
        assertEquals("https://jw.example.edu.cn/", root.jwBase);
        assertEquals("/", root.jwPathPrefix());

        assertNull(SchoolProfile.custom("", null));
        assertNull(SchoolProfile.custom("不是网址", null));
        assertNull(SchoolProfile.custom("ftp://jw.example.edu.cn/", null));
    }

    @Test
    public void customRoundTripAndDiagHasNoQuery() throws Exception {
        SchoolProfile p = SchoolProfile.custom(
                "https://jw.example.edu.cn/jwglxt/xtgl/login_slogin.html?time=1761400000000&ticket=ST-1", null);
        p.name = "示例大学";
        SchoolProfile back = SchoolProfile.fromJson(new JSONObject(p.toJson().toString()));
        assertEquals(p.name, back.name);
        assertEquals(p.vendor, back.vendor);
        assertEquals(p.jwBase, back.jwBase);
        assertEquals(p.hosts, back.hosts);
        String diag = back.diagSummary();
        assertTrue(diag.contains("vendor=zhengfang"));
        assertTrue(diag.contains("access=direct"));
        assertFalse(diag.contains("?"));
        assertFalse(diag.contains("ticket"));
        assertFalse(diag.contains("1761400000000"));
    }

    @Test
    public void webvpnAccessIsGuessed() {
        assertEquals(SchoolProfile.Access.WEBVPN,
                SchoolProfile.custom("https://webvpn.example.edu.cn/jwglxt/", null).access);
    }

    @Test
    public void zhengfangDefaultTermAndFallbackEndpoint() {
        Calendar oct = Calendar.getInstance();
        oct.set(2026, Calendar.OCTOBER, 26);
        Calendar jan = Calendar.getInstance();
        jan.set(2027, Calendar.JANUARY, 10);
        Calendar apr = Calendar.getInstance();
        apr.set(2027, Calendar.APRIL, 1);
        assertEquals("2026", ZhengfangSource.defaultTerm(oct)[0]);
        assertEquals("3", ZhengfangSource.defaultTerm(oct)[1]);
        assertEquals("2026", ZhengfangSource.defaultTerm(jan)[0]);
        assertEquals("12", ZhengfangSource.defaultTerm(apr)[1]);
        String js = new ZhengfangSource().extractionJs(null, null, null);
        assertTrue(js.contains("xskbcx_cxXsKb.html"));
        assertTrue(js.contains("xskbcx_cxXsgrkb.html"));
    }

    @Test
    public void pageSampleDropsNamesAndIds() {
        String raw = "{\"path\":\"/jsxsd/xskb/xskb_list.do\",\"ids\":[\"div#张三的课表\",\"td#2024000000\"],"
                + "\"labels\":{\"周次(节次)\":3,\"李四\":1},\"classes\":{\"kbcontent\":9},"
                + "\"fields\":[\"input[mail]:text\"],\"note\":\"a@b.cn\"}";
        String out = PageSample.sanitize(raw);
        assertFalse(out.contains("张三"));
        assertFalse(out.contains("李四"));
        assertFalse(out.contains("2024000000"));
        assertFalse(out.contains("a@b.cn"));
        assertTrue(out.contains("kbcontent"));
        assertTrue(out.contains("周次"));
        assertTrue(out.contains("/jsxsd/xskb/xskb_list.do"));
        assertEquals("", PageSample.sanitize("not json"));
        assertEquals("/a/b", PageSample.scrub("/a/b?ticket=ST-1"));
    }
}
