package com.ecut.campus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ecut.campus.data.SchoolProfile;

import org.junit.Test;

/** 所选学校的域名白名单；东华理工的放行规则保持不变。 */
public class SchoolUrlPolicyTest {

    @Test
    public void customSchoolHostsAreAllowed() {
        SchoolProfile p = SchoolProfile.custom("https://jw.example.edu.cn/jsxsd/", null);
        assertTrue(CampusUrlPolicy.isAllowedFor(p, "https://jw.example.edu.cn/jsxsd/xskb/xskb_list.do"));
        assertTrue(CampusUrlPolicy.isAllowedFor(p, "https://authserver.example.edu.cn/authserver/login"));
        assertFalse(CampusUrlPolicy.isAllowedFor(p, "https://ads.example.com/"));
        assertFalse(CampusUrlPolicy.isAllowedFor(p, "weixin://dl/business"));
        // 东华理工域名照旧放行
        assertTrue(CampusUrlPolicy.isAllowedFor(p, "https://ehall.ecut.edu.cn/new/index.html"));
    }

    @Test
    public void ecutUnchanged() {
        assertTrue(CampusUrlPolicy.isAllowedFor(null, "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/"));
        assertFalse(CampusUrlPolicy.isAllowedFor(null, "https://example.com/"));
    }

    @Test
    public void ecutBuiltinMatchesSyncHost() throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/assets/schools.json")), java.nio.charset.StandardCharsets.UTF_8);
        SchoolProfile ecut = SchoolProfile.find(SchoolProfile.parseList(json), SchoolProfile.ECUT_ID);
        assertEquals(CampusSyncClient.DEFAULT_JW_HOST, ecut.jwHost());
    }
}
