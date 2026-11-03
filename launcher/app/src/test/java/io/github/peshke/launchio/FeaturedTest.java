package io.github.peshke.launchio;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeaturedTest {

    private static List<Featured.Item> items(String... keys) {
        List<Featured.Item> out = new ArrayList<>();
        for (String k : keys) {
            Featured.Item it = new Featured.Item();
            it.key = k;
            out.add(it);
        }
        return out;
    }

    private static String keys(List<Featured.Item> l) {
        StringBuilder b = new StringBuilder();
        for (Featured.Item it : l) b.append(b.length() > 0 ? " " : "").append(it.key);
        return b.toString();
    }

    @Test
    public void oneShowThenTwoPicksAndCatalogsTakeTurns() {
        List<Featured.Item> shows = items("s1", "s2");
        List<List<Featured.Item>> catalogs = Arrays.asList(items("m1", "m2", "m3"), items("t1", "t2"));
        assertEquals("s1 m1 t1 s2 m2 t2 m3", keys(Featured.mix(shows, catalogs, 8)));
        assertEquals("s1 m1 t1 s2", keys(Featured.mix(shows, catalogs, 4)));
        assertEquals("m1 t1 m2 t2 m3", keys(Featured.mix(new ArrayList<>(), catalogs, 8)));
        assertEquals("s1 s2", keys(Featured.mix(shows, Collections.emptyList(), 8)));
    }

    @Test
    public void darkLogosAreJudgedByTheirVisiblePixelsOnly() {
        int clear = 0x00FFFFFF, black = 0xFF101010, white = 0xFFF0F0F0;
        assertEquals(true, Featured.dark(new int[]{clear, clear, clear, black, black}));
        assertEquals(false, Featured.dark(new int[]{black, white, white, clear}));
        assertEquals(false, Featured.dark(new int[]{clear, clear}));
    }

    @Test
    public void episodeLine() {
        assertEquals("S2 · E3 · Pilot", Featured.episodeLine("2", "3", "Pilot", 0, 0));
        assertEquals("E3", Featured.episodeLine(null, "3", "", 0, 0));
        assertEquals("12 min left", Featured.episodeLine(null, null, null, 18 * 60000, 30 * 60000));
        assertEquals("", Featured.episodeLine(null, null, null, 0, 30 * 60000));
    }

    @Test
    public void catalogSkipsTitlesWithoutABackdropAndBuildsFacts() throws Exception {
        JSONObject json = new JSONObject("{\"metas\":["
                + "{\"id\":\"tt1\",\"name\":\"One\",\"background\":\"https://x/1.jpg\",\"releaseInfo\":\"2024\","
                + "\"genres\":[\"Drama\",\"Crime\",\"War\"],\"imdbRating\":\"7.8\",\"description\":\"About one.\"},"
                + "{\"id\":\"tt2\",\"name\":\"No picture\"},"
                + "{\"id\":\"tt3\",\"name\":\"Three\",\"background\":\"https://x/3.jpg\",\"type\":\"series\",\"imdbRating\":null},"
                + "null]}");
        List<Featured.Item> l = Featured.parseCatalog(json, "movie", 8);
        assertEquals(2, l.size());
        assertEquals("pick/movie/tt1", l.get(0).key);
        assertEquals("2024  ·  Drama  ·  Crime  ·  ★ 7.8", l.get(0).facts);
        assertEquals("About one.", l.get(0).description);
        assertEquals("pick/series/tt3", l.get(1).key);
        assertEquals("", l.get(1).facts);
        assertEquals(1, Featured.parseCatalog(json, "movie", 1).size());
        assertEquals(0, Featured.parseCatalog(new JSONObject("{}"), "movie", 8).size());
    }
}
