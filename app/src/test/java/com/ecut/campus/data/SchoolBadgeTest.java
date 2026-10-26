package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SchoolBadgeTest {

    @Test
    public void rootDomainStripsSubdomainsAndAtrustNodes() {
        assertEquals("ecut.edu.cn", SchoolBadge.rootDomain("172-20-130-13.atrust.ecut.edu.cn"));
        assertEquals("ecut.edu.cn", SchoolBadge.rootDomain("*.ecut.edu.cn"));
        assertEquals("xyz.edu.cn", SchoolBadge.rootDomain("jwxt.xyz.edu.cn:8080"));
        assertEquals("example.com", SchoolBadge.rootDomain("jw.example.com"));
        assertEquals("", SchoolBadge.rootDomain("172.20.130.13"));
        assertEquals("", SchoolBadge.rootDomain(""));
        assertEquals("", SchoolBadge.rootDomain("localhost"));
    }

    @Test
    public void schoolDomainVotesAcrossHosts() {
        SchoolProfile p = new SchoolProfile();
        p.hosts.addAll(Arrays.asList("*.ecut.edu.cn", "172.20.130.13"));
        p.jwBase = "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/";
        p.loginUrl = "https://atrust1.ecut.edu.cn/portal/#/app_center";
        assertEquals("ecut.edu.cn", SchoolBadge.schoolDomain(p));
        assertEquals("", SchoolBadge.schoolDomain(new SchoolProfile()));
    }

    @Test
    public void iconCandidatesPreferSharpIconsAndResolveRelative() {
        String html = "<head><link rel=\"icon\" href=\"/_upload/tpl/favicon.ico\" />"
                + "<link rel='icon' sizes='192x192' href='img/logo192.png'>"
                + "<link rel=\"apple-touch-icon\" href=\"https://cdn.x.edu.cn/touch.png\">"
                + "<link rel=\"mask-icon\" href=\"/mask.svg\"><link rel=\"stylesheet\" href=\"a.css\"></head>";
        List<String> c = SchoolBadge.iconCandidates(html, "https://www.x.edu.cn/main.htm");
        assertEquals("https://cdn.x.edu.cn/touch.png", c.get(0));
        assertEquals("https://www.x.edu.cn/img/logo192.png", c.get(1));
        assertEquals("https://www.x.edu.cn/_upload/tpl/favicon.ico", c.get(2));
        assertEquals("https://www.x.edu.cn/favicon.ico", c.get(3));
        assertEquals(4, c.size());
    }

    @Test
    public void iconCandidatesFallBackToRootFavicon() {
        List<String> c = SchoolBadge.iconCandidates("<html></html>", "https://y.edu.cn/");
        assertEquals(1, c.size());
        assertEquals("https://y.edu.cn/favicon.ico", c.get(0));
        assertTrue(SchoolBadge.homepageCandidates("y.edu.cn").get(0).startsWith("https://www.y.edu.cn"));
    }
}
