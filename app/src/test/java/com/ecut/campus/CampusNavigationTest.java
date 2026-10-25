package com.ecut.campus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CampusNavigationTest {

    @Test
    public void bareCampusHostIsRewrittenToAtTrustProxy() {
        String input = "http://172.20.130.13/sso/jziotlogin?url=kbcx%2Findex.html&x=1";
        String actual = WebShellActivity.rewriteBareCampusHost(input);
        assertTrue(actual.startsWith(
                "https://172-20-130-13.atrust.ecut.edu.cn/sso/jziotlogin?"));
        assertTrue(actual.contains("url=kbcx%2Findex.html"));
        assertTrue(actual.contains("x=1"));
    }

    @Test
    public void unrelatedHostIsNotRewritten() {
        String input = "https://ehall.ecut.edu.cn/new/index.html";
        assertEquals(input, WebShellActivity.rewriteBareCampusHost(input));
    }

    @Test
    public void fragmentAndQueryArePreservedWhenRewriting() {
        String input = "http://172.20.130.13/sso/jziotlogin?url=kbcx%2Findex.html#/home";
        assertEquals(
                "https://172-20-130-13.atrust.ecut.edu.cn/sso/jziotlogin?url=kbcx%2Findex.html#/home",
                WebShellActivity.rewriteBareCampusHost(input));
    }

    @Test
    public void nonCampusHostsAreBlocked() {
        assertTrue(CampusUrlPolicy.isAllowedCampusUrl("https://ehall.ecut.edu.cn/new/index.html"));
        assertTrue(CampusUrlPolicy.isAllowedCampusUrl(
                "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/"));
        assertFalse(CampusUrlPolicy.isAllowedCampusUrl("https://example.com/"));
        assertFalse(CampusUrlPolicy.isAllowedCampusUrl("javascript:alert(1)"));
    }

    @Test
    public void jwglxtLoginPageIsRejectedAsSessionSuccess() {
        assertTrue(CampusSyncClient.isLoginPage(
                "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/xtgl/login_slogin.html"));
        assertFalse(CampusSyncClient.isLoginPage(
                "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"));
    }

    @Test
    public void ehallRecognizesDirectAndAtTrustProxyHosts() {
        assertTrue(CampusSyncClient.isEhallUrl(
                "https://ehall.ecut.edu.cn/new/index.html"));
        assertTrue(CampusSyncClient.isEhallUrl(
                "https://ehall-ecut-edu-cn-s.atrust.ecut.edu.cn/new/index.html"));
        assertFalse(CampusSyncClient.isEhallUrl(
                "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/"));
        assertFalse(CampusSyncClient.isEhallUrl("https://example.com/ehall.ecut.edu.cn"));
    }

    @Test
    public void targetUrlUsesCachedProxyHostAndRequiredMenuId() {
        String timetable = CampusSyncClient.targetUrl(
                "172-20-130-13.atrust.ecut.edu.cn", CampusSyncClient.Kind.TIMETABLE);
        assertEquals("https://172-20-130-13.atrust.ecut.edu.cn"
                + "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
                timetable);
        String grades = CampusSyncClient.targetUrl(
                "172-20-130-13.atrust.ecut.edu.cn", CampusSyncClient.Kind.GRADES);
        assertEquals("https://172-20-130-13.atrust.ecut.edu.cn"
                + "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
                grades);
    }
}
