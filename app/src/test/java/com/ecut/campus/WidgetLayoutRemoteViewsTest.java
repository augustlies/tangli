package com.ecut.campus;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1.2.6 vc42：桌面小组件布局只能用 RemoteViews 允许的类。vc39–41 的 2×2「下一节课」用了一个普通 &lt;View&gt; 当弹簧
 * （initialLayout 也是它），真机上整块加载失败，只剩空白圆角方块。那个小组件已去掉，这条测试守住「今日课程」。
 */
public class WidgetLayoutRemoteViewsTest {

    /** API 26 起 RemoteViews 都能 inflate 的类（不含 31+ 才加的 CheckBox / Switch 等）。 */
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
            "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextView",
            "ViewFlipper", "ListView", "GridView", "StackView", "AdapterViewFlipper", "ViewStub", "TextClock"));

    private static final Pattern TAG = Pattern.compile("<([A-Za-z][A-Za-z0-9_.]*)[\\s>/]");
    private static final Pattern LAYOUT_REF = Pattern.compile("@layout/([a-z0-9_]+)");

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    /** 小组件用到的布局：appwidget-provider 里引用的 + 列表条目。 */
    private static List<String> widgetLayouts() throws Exception {
        Set<String> names = new HashSet<>();
        for (String info : new String[]{"widget_classes_info"}) {
            Matcher m = LAYOUT_REF.matcher(read("src/main/res/xml/" + info + ".xml"));
            while (m.find()) names.add(m.group(1));
        }
        names.add("widget_class_item");
        names.add("widget_classes");
        names.add("widget_classes_loading");
        names.add("widget_exam_flipper");
        names.add("widget_exam_page");
        return new ArrayList<>(names);
    }

    @Test
    public void widgetLayoutsUseOnlyRemoteViewsClasses() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String name : widgetLayouts()) {
            String xml = read("src/main/res/layout/" + name + ".xml").replaceAll("(?s)<!--.*?-->", "").replaceAll("<\\?xml[^>]*\\?>", "");
            Matcher m = TAG.matcher(xml);
            while (m.find()) {
                String tag = m.group(1);
                if (!ALLOWED.contains(tag)) bad.add(name + ": <" + tag + ">");
            }
        }
        assertTrue("not allowed in RemoteViews: " + bad, bad.isEmpty());
    }

    @Test
    public void initialLayoutIsAVisiblePlaceholder() throws Exception {
        String info = read("src/main/res/xml/widget_classes_info.xml");
        assertTrue(info.contains("android:initialLayout=\"@layout/widget_classes_loading\""));
        assertTrue(info.contains("android:minResizeWidth=\"110dp\""));
        assertTrue(info.contains("android:minResizeHeight=\"110dp\""));
        String loading = read("src/main/res/layout/widget_classes_loading.xml");
        assertTrue(loading.contains("@string/widget_classes_placeholder_top"));
        assertFalse(loading.contains("android:visibility=\"gone\""));
        assertTrue(read("src/main/res/values/strings.xml")
                .contains("<string name=\"widget_classes_placeholder_top\">烫梨 · 今日课程</string>"));
    }

    @Test
    public void separateNextWidgetIsGone() throws Exception {
        assertFalse(new File("src/main/res/xml/widget_next_info.xml").exists());
        assertFalse(new File("src/main/java/com/ecut/campus/NextWidget.java").exists());
        String manifest = read("src/main/AndroidManifest.xml");
        assertFalse(manifest.contains(".NextWidget"));
        assertTrue(manifest.contains("android:name=\".ClassWidget\""));
        String sc = read("src/main/res/xml/shortcuts.xml");
        assertTrue(sc.contains("pin_class_widget"));
        assertFalse(sc.contains("pin_next_widget"));
    }

    @Test
    public void countdownChronometersLiveInTheItem() throws Exception {
        String item = read("src/main/res/layout/widget_class_item.xml");
        assertTrue(item.contains("<Chronometer\n                android:id=\"@+id/wi_cd_live\""));
        assertTrue(item.contains("<Chronometer\n                android:id=\"@+id/wi_cd_soon\""));
    }

    @Test
    public void examFlipperHasStaticFallbackAndProgressBar() throws Exception {
        String flipper = read("src/main/res/layout/widget_exam_flipper.xml");
        assertTrue(flipper.contains("android:flipInterval=\"6000\""));
        assertTrue(flipper.contains("@anim/widget_exam_in"));
        String page = read("src/main/res/layout/widget_exam_page.xml");
        assertTrue(page.contains("@+id/w_exam_bg"));
        assertTrue(page.contains("@+id/w_exam_badge_bg"));
        assertTrue(page.contains("@+id/w_exam_bar"));
        assertTrue(page.contains("@+id/w_exam_name"));
        assertTrue(page.contains("@+id/w_exam_day"));
        String java = read("src/main/java/com/ecut/campus/ClassWidget.java");
        assertTrue(java.contains("if (exams.size() > 1 && flipSeconds > 0 && examMotionEnabled(c))"));
        assertTrue(java.contains("setFlipInterval"));
        assertTrue(java.contains("v.addView(R.id.w_exam_panel, firstPage)"));
        assertTrue(java.contains("setTextViewText(R.id.w_exam_name, event.name)"));
    }

    @Test
    public void examPageAvoidsWireframeAndLeakedLabels() throws Exception {
        String page = read("src/main/res/layout/widget_exam_page.xml");
        assertFalse(page.contains("w_exam_sheet"));
        assertFalse(page.contains("w_exam_tick_"));
        assertFalse(page.contains("w_exam_step"));
        String java = read("src/main/java/com/ecut/campus/ClassWidget.java");
        assertFalse(java.contains("七日刻度外"));
        assertFalse(java.contains("七格刻度"));
        assertFalse(java.contains("临考刻度"));
        assertFalse(java.contains("otherEvents.append(\" · \")"));
    }
}
