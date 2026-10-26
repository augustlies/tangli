package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

public class SchoolDirectoryTest {

    private static final String JSON = "{\"schools\":["
            + "[\"华东理工大学\",\"zhengfang_new\",\"https://jwglxt.ecust.edu.cn/jwglxt/xtgl/login_slogin.html\"],"
            + "[\"泉州信息工程学院\",\"qiangzhi\",\"https://jwgl.qzuie.edu.cn/Logon.do?method=logon\"],"
            + "[\"湖南科技大学\",\"qiangzhi\",\"http://kdjw.hnust.edu.cn/jsxsd/\"],"
            + "[\"某某学院\",\"kingosoft_new\",\"https://jw.example.edu.cn/cas/login.action\"],"
            + "[\"坏数据\",\"zhengfang_new\",\"not a url\"]]}";

    @Test
    public void parsesAndSkipsBadRows() {
        assertEquals(4, SchoolDirectory.parse(JSON).size());
        assertEquals(0, SchoolDirectory.parse("garbage").size());
    }

    @Test
    public void searchPrefersPrefixThenContainsThenFuzzy() {
        SchoolDirectory d = SchoolDirectory.parse(JSON);
        List<SchoolDirectory.Entry> r = d.search("华东", 10);
        assertEquals("华东理工大学", r.get(0).name);
        assertEquals("泉州信息工程学院", d.search("信息工程", 10).get(0).name);
        assertEquals("华东理工大学", d.search("华理", 10).get(0).name);
        assertTrue(d.search("", 10).isEmpty());
        assertTrue(d.search("清华", 10).isEmpty());
    }

    @Test
    public void vendorTrustedOnlyWhenUrlAgrees() {
        SchoolDirectory d = SchoolDirectory.parse(JSON);
        SchoolProfile zf = SchoolDirectory.toProfile(d.search("华东理工", 1).get(0));
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, zf.vendor);
        assertEquals("华东理工大学", zf.name);
        assertEquals("https://jwglxt.ecust.edu.cn/jwglxt/", zf.jwBase);
        SchoolProfile qz = SchoolDirectory.toProfile(d.search("湖南科技", 1).get(0));
        assertEquals(SchoolProfile.Vendor.QIANGZHI, qz.vendor);
        // 目录说是强智，但网址看不出来：交给登录后的页面识别
        SchoolProfile unsure = SchoolDirectory.toProfile(d.search("泉州", 1).get(0));
        assertEquals(SchoolProfile.Vendor.OTHER, unsure.vendor);
        assertTrue(unsure.allowsUrl("https://jwgl.qzuie.edu.cn/jsxsd/"));
    }

    @Test
    public void typeLabels() {
        SchoolDirectory d = SchoolDirectory.parse(JSON);
        SchoolDirectory.Entry k = d.search("某某", 1).get(0);
        assertEquals("青果", k.typeLabel());
        assertFalse(k.importable());
        assertTrue(d.search("湖南", 1).get(0).importable());
    }

    @Test
    public void bundledDirectoryLoads() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/school-directory.json")), "UTF-8");
        SchoolDirectory d = SchoolDirectory.parse(json);
        assertTrue(d.size() > 1400);
        assertNotNull(d.search("泉州信息工程", 1).get(0));
        assertTrue(d.search("东华理工大学", 5).isEmpty());
    }
}
