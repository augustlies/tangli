package com.ecut.campus;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class NetworkSecurityPolicyTest {
    @Test public void userCertificatesAreLimitedToEcutAndCleartextRemainsForCustomSchools() throws Exception {
        File config = findConfig();
        assertTrue("network security config must be present", config.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(config);
        Element root = doc.getDocumentElement();
        Element base = (Element) root.getElementsByTagName("base-config").item(0);
        assertEquals("true", base.getAttribute("cleartextTrafficPermitted"));
        assertEquals(java.util.Collections.singleton("system"), anchors(base));

        NodeList domains = root.getElementsByTagName("domain-config");
        assertEquals(1, domains.getLength());
        Element ecut = (Element) domains.item(0);
        NodeList domainNodes = ecut.getElementsByTagName("domain");
        Set<String> domainNames = new HashSet<>();
        for (int i = 0; i < domainNodes.getLength(); i++) {
            Element domain = (Element) domainNodes.item(i);
            domainNames.add(domain.getTextContent().trim());
            assertEquals("true", domain.getAttribute("includeSubdomains"));
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList(
                "atrust1.ecut.edu.cn", "atrust.ecut.edu.cn")), domainNames);
        assertEquals(new HashSet<>(java.util.Arrays.asList("system", "user")), anchors(ecut));
    }

    private static Set<String> anchors(Element scope) {
        Set<String> out = new HashSet<>();
        NodeList certificates = scope.getElementsByTagName("certificates");
        for (int i = 0; i < certificates.getLength(); i++) {
            out.add(((Element) certificates.item(i)).getAttribute("src"));
        }
        return out;
    }

    private static File findConfig() {
        for (String path : new String[]{"app/src/main/res/xml/network_security_config.xml",
                "src/main/res/xml/network_security_config.xml"}) {
            File f = new File(path);
            if (f.isFile()) return f;
        }
        return new File("app/src/main/res/xml/network_security_config.xml");
    }
}
