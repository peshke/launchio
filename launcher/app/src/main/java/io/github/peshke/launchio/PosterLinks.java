package io.github.peshke.launchio;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom poster links in Nuvio's format (its CustomPosterUrlResolver), so the links set up in Nuvio work here too:
 * {imdb_id}, {tmdb_id}, {id}, {id_type}, {typed_id}, {type} and {shape}. "{x?}" may be empty; "{a|b}" takes the
 * first one the title has.
 */
final class PosterLinks {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z_|]+)(\\??)\\}");

    private PosterLinks() {}

    /**
     * @param id    the title's id: "tt0137523", "tt0903747:2:3" (an episode) or "tmdb:1396"
     * @param shape "poster" or "landscape"
     * @return the link for this title, or null when it needs an id the title does not have (use the standard art)
     */
    static String resolve(String pattern, String id, String type, String shape) {
        if (pattern == null || pattern.trim().isEmpty() || id == null) return null;
        String p = pattern.trim().replace("%7B", "{").replace("%7b", "{").replace("%7D", "}").replace("%7d", "}");
        String base = id.split(":")[0];
        String imdb = base.startsWith("tt") ? base : null;
        String tmdb = id.startsWith("tmdb:") ? id.substring(5).split(":")[0] : null;
        String t = "series".equals(type) || "tv".equals(type) ? "series" : "movie";
        Map<String, String> v = new HashMap<>();
        v.put("id", imdb != null ? imdb : id);
        v.put("imdb_id", imdb);
        v.put("tmdb_id", tmdb);
        v.put("id_type", imdb != null ? "imdb" : tmdb != null ? "tmdb" : null);
        v.put("typed_id", imdb != null ? imdb : tmdb != null ? t + "-" + tmdb : null);
        v.put("type", t);
        v.put("shape", shape);
        Matcher m = TOKEN.matcher(p);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String value = null;
            for (String key : m.group(1).split("\\|")) if ((value = v.get(key)) != null) break;
            if (value == null && m.group(2).isEmpty()) return null;
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
