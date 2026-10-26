package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** detect 交叉矩阵：每份样本只允许被自己的厂商判为最高分。 */
public class VendorDetectionTest {

    private static final String ZF_URL =
            "https://jw.example.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default";
    private static final String QZ_URL = "https://jw.example.edu.cn/jsxsd/xskb/xskb_list.do";
    private static final String OTHER_URL = "https://jw.example.edu.cn/cas/login.action";

    @Test
    public void crossMatrix() throws Exception {
        String zf = QiangzhiSourceTest.fixture("fixtures/zhengfang/xskbcx-index-sample.html");
        String qz = QiangzhiSourceTest.fixture("fixtures/qiangzhi/xskb_list-sample.html");
        String other = QiangzhiSourceTest.fixture("fixtures/other/kingosoft-login-sample.html");
        ZhengfangSource zfs = new ZhengfangSource();
        QiangzhiSource qzs = new QiangzhiSource();

        assertTrue(zfs.detect(ZF_URL, zf) > qzs.detect(ZF_URL, zf));
        assertTrue(qzs.detect(QZ_URL, qz) > zfs.detect(QZ_URL, qz));
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, TimetableSources.detectVendor(ZF_URL, zf));
        assertEquals(SchoolProfile.Vendor.QIANGZHI, TimetableSources.detectVendor(QZ_URL, qz));
        assertEquals(SchoolProfile.Vendor.OTHER, TimetableSources.detectVendor(OTHER_URL, other));
        assertNull(TimetableSources.detect(OTHER_URL, other));
    }

    @Test
    public void detectsFromHtmlWhenUrlIsOpaque() throws Exception {
        // 走 WebVPN 改写后网址里看不出厂商，只能靠页面特征
        String qz = QiangzhiSourceTest.fixture("fixtures/qiangzhi/xskb_list-sample.html");
        String zf = QiangzhiSourceTest.fixture("fixtures/zhengfang/xskbcx-index-sample.html");
        String vpn = "https://webvpn.example.edu.cn/https/77726476706e69737468656265737421/";
        assertEquals(SchoolProfile.Vendor.QIANGZHI, TimetableSources.detectVendor(vpn, qz));
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, TimetableSources.detectVendor(vpn, zf));
    }

    @Test
    public void urlOnlyDetection() {
        assertEquals(SchoolProfile.Vendor.ZHENGFANG, TimetableSources.detectVendor(ZF_URL, null));
        assertEquals(SchoolProfile.Vendor.QIANGZHI, TimetableSources.detectVendor(QZ_URL, null));
        assertEquals(SchoolProfile.Vendor.OTHER, TimetableSources.detectVendor("https://example.com/", null));
    }

    @Test
    public void registryByVendor() {
        assertSame(SchoolProfile.Vendor.ZHENGFANG,
                TimetableSources.forVendor(SchoolProfile.Vendor.ZHENGFANG).vendor());
        assertSame(SchoolProfile.Vendor.QIANGZHI,
                TimetableSources.forVendor(SchoolProfile.Vendor.QIANGZHI).vendor());
        assertNull(TimetableSources.forVendor(SchoolProfile.Vendor.OTHER));
    }

    @Test
    public void loginPages() {
        ZhengfangSource zfs = new ZhengfangSource();
        QiangzhiSource qzs = new QiangzhiSource();
        assertTrue(zfs.isLoginPage("https://jw.example.edu.cn/jwglxt/xtgl/login_slogin.html"));
        assertTrue(zfs.isLoggedInPage("https://jw.example.edu.cn/jwglxt/xtgl/index_initMenu.html"));
        assertTrue(qzs.isLoginPage("https://jw.example.edu.cn/jsxsd/"));
        assertTrue(qzs.isLoginPage("https://jw.example.edu.cn/jsxsd/xk/LoginToXk"));
        assertTrue(qzs.isLoggedInPage("https://jw.example.edu.cn/jsxsd/framework/xsMain.jsp"));
        assertTrue(!qzs.isLoggedInPage("https://jw.example.edu.cn/jsxsd/"));
    }
}
