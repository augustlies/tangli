package com.ecut.campus;

import static org.junit.Assert.*;

import com.ecut.campus.data.AppIconOption;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1.2.7：桌面图标改成两个 activity-alias 切换。守住：每个选项都有 alias、只有默认那个 enabled、
 * 两个都挂 LAUNCHER 和长按快捷方式，MainActivity 自己不再挂 LAUNCHER（否则桌面会出现两个图标）。
 */
public class LauncherAliasManifestTest {

    private static String manifest() throws Exception {
        return new String(Files.readAllBytes(new File("src/main/AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
    }

    private static String alias(String m, String shortName) {
        Matcher x = Pattern.compile("<activity-alias[^>]*android:name=\"\\." + shortName + "\"[\\s\\S]*?</activity-alias>")
                .matcher(m);
        assertTrue("missing alias " + shortName, x.find());
        return x.group();
    }

    @Test
    public void eachOptionHasLauncherAliasAndOnlyDefaultEnabled() throws Exception {
        String m = manifest();
        for (AppIconOption o : AppIconOption.values()) {
            String a = alias(m, o.alias.substring("com.ecut.campus.".length()));
            assertTrue(a.contains("android:targetActivity=\".MainActivity\""));
            assertTrue(a.contains("android.intent.category.LAUNCHER"));
            assertTrue(a.contains("@xml/shortcuts"));
            assertTrue(a.contains("android:exported=\"true\""));
            assertEquals(o == AppIconOption.DEFAULT, a.contains("android:enabled=\"true\""));
        }
    }

    @Test
    public void mainActivityItselfIsNotALauncherEntry() throws Exception {
        String m = manifest();
        Matcher x = Pattern.compile("<activity\\s+android:name=\"\\.MainActivity\"[\\s\\S]*?</activity>").matcher(m);
        assertTrue(x.find());
        assertFalse(x.group().contains("android.intent.category.LAUNCHER"));
        // tangli:// 链接入口还在
        assertTrue(x.group().contains("android:scheme=\"tangli\""));
        // 整个清单里 LAUNCHER 只出现在两个 alias 上
        int n = 0;
        Matcher l = Pattern.compile("android.intent.category.LAUNCHER").matcher(m);
        while (l.find()) n++;
        assertEquals(AppIconOption.values().length, n);
    }
}
