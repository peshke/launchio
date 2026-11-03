package io.github.peshke.launchio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** The palettes exist twice, in Look.java (for code) and themes_look.xml (for layouts): they must agree. */
public class LookTest {

    private static final File RES = new File("src/main/res");

    private static Map<String, Map<String, String>> styles(File xml) throws Exception {
        NodeList list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
                .getElementsByTagName("style");
        Map<String, Map<String, String>> out = new HashMap<>();
        for (int i = 0; i < list.getLength(); i++) {
            Element style = (Element) list.item(i);
            Map<String, String> items = new HashMap<>();
            NodeList children = style.getElementsByTagName("item");
            for (int j = 0; j < children.getLength(); j++) {
                Element item = (Element) children.item(j);
                items.put(item.getAttribute("name"), item.getTextContent().trim());
            }
            out.put(style.getAttribute("name"), items);
        }
        return out;
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    @Test
    public void everyPaletteMatchesItsStyle() throws Exception {
        Map<String, Map<String, String>> styles = styles(new File(RES, "values/themes_look.xml"));
        assertEquals(13, Look.THEMES.size());
        for (Look.Palette p : Look.THEMES.values()) {
            Map<String, String> s = styles.get(p.styleName());
            assertNotNull(p.name, s);
            assertEquals(p.name, hex(p.accent), s.get("launchioAccent"));
            assertEquals(p.name, hex(p.onAccent), s.get("launchioOnAccent"));
            assertEquals(p.name, hex(p.ring), s.get("launchioRing"));
            assertEquals(p.name, hex(p.focusBg), s.get("launchioFocusBg"));
            assertEquals(p.name, hex(p.background), s.get("launchioBackground"));
            assertEquals(p.name, hex(p.raised), s.get("launchioRaised"));
            assertEquals(p.name, hex(p.card), s.get("launchioCard"));
            assertEquals(p.name, hex(p.menu), s.get("launchioMenu"));
        }
    }

    @Test
    public void theAppThemeStartsAsDefaultAndInter() throws Exception {
        Map<String, String> app = styles(new File(RES, "values/styles.xml")).get("Theme.TvHome");
        Map<String, String> white = styles(new File(RES, "values/themes_look.xml")).get("Look.Palette.Default");
        Map<String, String> inter = styles(new File(RES, "values/themes_look.xml")).get("Look.Font.Inter");
        for (Map.Entry<String, String> e : white.entrySet()) assertEquals(e.getKey(), e.getValue(), app.get(e.getKey()));
        for (Map.Entry<String, String> e : inter.entrySet()) assertEquals(e.getKey(), e.getValue(), app.get(e.getKey()));
    }

    @Test
    public void everyFontStyleNamesFilesThatExist() throws Exception {
        Map<String, Map<String, String>> styles = styles(new File(RES, "values/themes_look.xml"));
        for (String[] f : Look.FONTS) {
            Map<String, String> s = styles.get(f[2]);
            assertNotNull(f[1], s);
            assertEquals(f[1], 6, s.size());
            assertEquals("@style/Look.Text", s.remove("android:textViewStyle"));
            for (String ref : s.values()) {
                assertTrue(ref, new File(RES, "font/" + ref.substring("@font/".length()) + ".xml").isFile());
            }
        }
    }

    @Test
    public void everyThemeAndFontHasAStyleId() {
        for (Look.Palette p : Look.THEMES.values()) assertNotNull(p.name, Look.styleIds().get(p.styleName()));
        for (String[] f : Look.FONTS) assertNotNull(f[1], Look.styleIds().get(f[2]));
        assertEquals(Look.THEMES.size() + Look.FONTS.length, Look.styleIds().size());
    }

    @Test
    public void unknownChoicesFallBackToTheDefaults() {
        assertSame(Look.THEMES.get("default"), Look.theme(null));
        assertSame(Look.THEMES.get("default"), Look.theme("no_such_theme"));
        assertSame(Look.THEMES.get("white"), Look.theme("white"));
        assertEquals("rose_gold", Look.theme("rose_gold").key);
        assertEquals("inter", Look.font(null)[0]);
        assertEquals("dm_sans", Look.font("dm_sans")[0]);
    }
}
