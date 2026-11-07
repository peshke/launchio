package io.github.peshke.launchio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PosterLinksTest {

    @Test
    public void fillsNuviosPlaceholders() {
        assertEquals("https://api.ratingposterdb.com/KEY/imdb/poster-default/tt0903747.jpg",
                PosterLinks.resolve("https://api.ratingposterdb.com/KEY/imdb/poster-default/{imdb_id}.jpg",
                        "tt0903747:2:3", "series", "poster"));
        assertEquals("https://x.test/series/landscape/tt0903747",
                PosterLinks.resolve("https://x.test/{type}/{shape}/%7Bid%7D", "tt0903747", "series", "landscape"));
    }

    @Test
    public void tmdbIdsAndAlternatives() {
        assertEquals("https://x.test/movie-1396", PosterLinks.resolve("https://x.test/{typed_id}", "tmdb:1396", "movie", "poster"));
        assertEquals("https://x.test/1396", PosterLinks.resolve("https://x.test/{imdb_id|tmdb_id}", "tmdb:1396", "movie", "poster"));
        assertEquals("https://x.test/?i=", PosterLinks.resolve("https://x.test/?i={imdb_id?}", "tmdb:1396", "movie", "poster"));
    }

    @Test
    public void missingRequiredIdFallsBack() {
        assertNull(PosterLinks.resolve("https://x.test/{imdb_id}", "tmdb:1396", "movie", "poster"));
        assertNull(PosterLinks.resolve("", "tt1", "movie", "poster"));
        assertNull(PosterLinks.resolve("https://x.test/{imdb_id}", null, "movie", "poster"));
    }
}
