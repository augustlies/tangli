package com.ecut.campus.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionCompareTest {
    @Test
    public void compares() {
        assertTrue(VersionCompare.isNewer("v1.0.1", "1.0.0"));
        assertTrue(VersionCompare.isNewer("v1.10.0", "1.9.9"));
        assertTrue(VersionCompare.isNewer("v1.0.0", "0.4.1-gauge"));
        assertFalse(VersionCompare.isNewer("v1.0.0", "1.0.0"));
        assertFalse(VersionCompare.isNewer("1.0", "1.0.0"));
        assertFalse(VersionCompare.isNewer("v0.9.0", "1.0.0"));
        assertEquals(0, VersionCompare.compare("V2.0.0-beta", "2.0.0"));
    }
}
