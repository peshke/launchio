package io.github.peshke.launchio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hero's titles: the shows you are watching (the system Watch Next list, which Nuvio fills) and
 * recommendations from a Stremio-style catalog add-on (Cinemeta by default). Everything here blocks,
 * so call it off the main thread.
 */
final class Featured {

    static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    static final int MAX = 8, SHOWS = 3, ROW = 15;

    static final class Item {
        String key, title, facts, description, backdrop, logo, type, id, intentUri, pkg;
        /** From Watch Next: opens where you left off. Otherwise a recommendation, opened in Nuvio. */
        boolean resume;
        /** How far into it you are, 0 to 1; 0 when the app did not say. */
        float progress;
        /** Watch Next's last watched time and position: they change when you watch it again. */
        String seen;
    }

    /** Titles removed from Continue watching, as hideToken()s. Watch Next belongs to the app, so they are only hidden here. */
    static final String HIDDEN_KEY = "hidden_watching";

    /** Changes once you watch the title again, so a hidden title comes back then. */
    static String hideToken(Item it) {
        return it.key + '|' + it.seen;
    }

    static void hide(Context ctx, Item it) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<>(prefs.getStringSet(HIDDEN_KEY, Collections.emptySet()));
        hidden.add(hideToken(it));
        prefs.edit().putStringSet(HIDDEN_KEY, hidden).apply();
    }

    private Featured() {}

    /**
     * Your shows from Watch Next, most recent first. Empty without READ_TV_LISTINGS. Their pictures are the ones the
     * app picked (Nuvio's follow its artwork settings); plain swaps them for Metahub's, by the title's IMDb id.
     */
    static List<Item> watching(Context ctx, int max, boolean plain) {
        List<Item> out = new ArrayList<>();
        if (ctx.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) return out;
        String[] proj = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        };
        List<long[]> order = new ArrayList<>();   // {engaged, index into out}
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        Set<String> hidden = prefs.getStringSet(HIDDEN_KEY, Collections.emptySet());
        Set<String> stillHidden = new HashSet<>();
        // The provider rejects a selection or sort order from other apps: filter and sort here.
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, proj, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(11) == 0) continue;   // the owner removed it from the system row
                Item it = new Item();
                it.pkg = c.getString(0);
                it.title = c.getString(1);
                if (it.title == null || it.title.isEmpty() || !installed(ctx, it.pkg)) continue;
                it.resume = true;
                it.intentUri = c.getString(7);
                it.facts = episodeLine(c.getString(3), c.getString(4), c.getString(2), c.getLong(8), c.getLong(9));
                if (c.getLong(9) > 0) it.progress = Math.min(1f, Math.max(0f, (float) c.getLong(8) / c.getLong(9)));
                it.backdrop = !c.isNull(5) ? c.getString(5) : c.getString(6);
                // Nuvio's resume intent carries the title's id, its wide backdrop and its logo.
                try {
                    Intent in = it.intentUri == null ? null : Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                    if (in != null) {
                        it.id = in.getStringExtra("contentId");
                        it.type = in.getStringExtra("contentType");
                        if (in.getStringExtra("backdrop") != null) it.backdrop = in.getStringExtra("backdrop");
                        it.logo = in.getStringExtra("logo");
                    }
                } catch (Exception ignored) {
                    // not an intent we can read: keep the row's own art
                }
                if (plain && it.id != null && it.id.startsWith("tt")) {
                    String imdb = it.id.split(":")[0];
                    it.backdrop = "https://images.metahub.space/background/medium/" + imdb + "/img";
                    it.logo = "https://images.metahub.space/logo/medium/" + imdb + "/img";
                }
                it.key = "show/" + it.pkg + "/" + it.title;
                it.seen = c.getLong(10) + "/" + c.getLong(8);
                if (hidden.contains(hideToken(it))) {
                    stillHidden.add(hideToken(it));
                    continue;
                }
                order.add(new long[]{c.getLong(10), out.size()});
                out.add(it);
            }
            // Forget the ones watched again or gone; only after a full read, or a failed one would forget them all.
            if (stillHidden.size() != hidden.size()) prefs.edit().putStringSet(HIDDEN_KEY, stillHidden).apply();
        } catch (RuntimeException ignored) {
            // no TV provider on this device, or the permission was revoked
        }
        Collections.sort(order, (a, b) -> Long.compare(b[0], a[0]));
        List<Item> sorted = new ArrayList<>();
        for (long[] o : order) if (sorted.size() < max) sorted.add(out.get((int) o[1]));
        return sorted;
    }

    /** "S2 · E3 · Title", or "12 min left" for a film. */
    static String episodeLine(String season, String episode, String episodeTitle, long position, long duration) {
        StringBuilder b = new StringBuilder();
        if (season != null && !season.isEmpty()) b.append('S').append(season);
        if (episode != null && !episode.isEmpty()) b.append(b.length() > 0 ? " · E" : "E").append(episode);
        if (episodeTitle != null && !episodeTitle.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(episodeTitle);
        if (b.length() == 0 && duration > 0 && position > 0 && position < duration) {
            b.append(Math.max(1, (duration - position) / 60000)).append(" min left");
        }
        return b.toString();
    }

    /** One catalog ("movie/top") from the add-on, as hero titles; empty when offline. */
    static List<Item> catalog(String addon, String catalog, int max) {
        byte[] data = read(addon + "/catalog/" + catalog + ".json", 4 << 20);
        if (data == null) return new ArrayList<>();
        try {
            return parseCatalog(new JSONObject(new String(data, StandardCharsets.UTF_8)), catalog.split("/")[0], max);
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    static List<Item> parseCatalog(JSONObject json, String type, int max) {
        List<Item> out = new ArrayList<>();
        JSONArray metas = json.optJSONArray("metas");
        for (int i = 0; metas != null && i < metas.length() && out.size() < max; i++) {
            JSONObject j = metas.optJSONObject(i);
            if (j == null) continue;
            Item it = new Item();
            it.id = str(j, "id");
            it.title = str(j, "name");
            it.backdrop = str(j, "background");
            if (it.id == null || it.title == null || it.backdrop == null) continue;
            it.type = str(j, "type") != null ? str(j, "type") : type;
            it.description = str(j, "description");
            it.logo = str(j, "logo");
            List<String> facts = new ArrayList<>();
            if (str(j, "releaseInfo") != null) facts.add(str(j, "releaseInfo"));
            JSONArray genres = j.optJSONArray("genres") != null ? j.optJSONArray("genres") : j.optJSONArray("genre");
            for (int g = 0; genres != null && g < genres.length() && g < 2; g++) facts.add(genres.optString(g));
            if (str(j, "imdbRating") != null) facts.add("★ " + str(j, "imdbRating"));
            it.facts = String.join("  ·  ", facts);
            it.key = "pick/" + it.type + "/" + it.id;
            out.add(it);
        }
        return out;
    }

    /** A show's blurb from the add-on's meta endpoint (Watch Next has none); left null when offline. */
    static void describe(String addon, Item it) {
        if (it.type == null || it.id == null) return;
        byte[] data = read(addon + "/meta/" + it.type + "/" + Uri.encode(it.id) + ".json", 4 << 20);
        if (data == null) return;
        try {
            JSONObject meta = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONObject("meta");
            if (meta != null) it.description = str(meta, "description");
        } catch (JSONException ignored) {
            // a broken reply only costs the blurb
        }
    }

    /** One of your shows, then two recommendations, and so on; recommendations take turns between catalogs. */
    static List<Item> mix(List<Item> shows, List<List<Item>> catalogs, int max) {
        List<Item> picks = new ArrayList<>();
        for (int i = 0; ; i++) {
            boolean more = false;
            for (List<Item> l : catalogs) {
                if (i < l.size()) {
                    more = true;
                    picks.add(l.get(i));
                }
            }
            if (!more) break;
        }
        List<Item> out = new ArrayList<>();
        int a = 0, b = 0;
        while (out.size() < max && (a < shows.size() || b < picks.size())) {
            if (a < shows.size()) out.add(shows.get(a++));
            for (int k = 0; k < 2 && b < picks.size() && out.size() < max; k++) out.add(picks.get(b++));
        }
        return out;
    }

    /** A backdrop decoded at about this width, in 16-bit colour (it sits under a scrim). */
    static Bitmap backdrop(String url, int width) {
        return image(url, width, false);
    }

    /** A picture decoded at about this width; alpha keeps a logo's transparency (32-bit). */
    static Bitmap image(String url, int width, boolean alpha) {
        byte[] data = read(url, 8 << 20);
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= width) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    /** Whether a logo's visible pixels are mostly dark (black lettering would vanish on the dark hero). */
    static boolean dark(int[] argb) {
        long sum = 0, n = 0;
        for (int p : argb) {
            if ((p >>> 24) < 0x80) continue;   // transparent background
            sum += (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000;
            n++;
        }
        return n > 0 && sum / n < 90;
    }

    /** An https URL's body, at most max bytes; null on any failure. */
    static byte[] read(String url, int max) {
        HttpURLConnection http = null;
        try {
            http = (HttpURLConnection) new URL(url).openConnection();
            http.setConnectTimeout(8000);
            http.setReadTimeout(8000);
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                for (int n; (n = in.read(chunk)) != -1; ) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > max) return null;
                }
                return buf.toByteArray();
            }
        } catch (Exception e) {   // offline, a bad URL or a bad reply: no picture rather than a crash
            return null;
        } finally {
            if (http != null) http.disconnect();
        }
    }

    private static boolean installed(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /** A non-empty string field, or null (optString turns JSON null into "null"). */
    private static String str(JSONObject j, String key) {
        Object o = j.opt(key);
        return o instanceof String && !((String) o).isEmpty() ? (String) o : null;
    }
}
