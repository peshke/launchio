package io.github.peshke.launchio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * What the hero and the rows show: your shows from Watch Next and the add-on's catalogs. Catalogs are kept for a
 * few hours, show blurbs for good; Home asks again each time it comes back and shows what this hands it.
 */
final class Feed {

    static final long PICKS_TTL_MS = 3 * 60 * 60 * 1000L;

    interface Done {
        /** On the main thread: all of Watch Next, the shows the hero takes (with blurbs), and every catalog. */
        void on(List<Featured.Item> watching, List<Featured.Item> heroShows, Map<String, List<Featured.Item>> catalogs);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> blurbs = new ConcurrentHashMap<>();
    /** Only read and written on the main thread. */
    private Map<String, List<Featured.Item>> catalogs = new HashMap<>();
    private long catalogsAt;

    /**
     * @param watching read Watch Next at all (for the hero or the Continue watching row)
     * @param heroShows how many of your shows the hero takes (they get a blurb); 0 for none
     */
    void load(Context ctx, String addon, Collection<String> need, boolean watching, int heroShows, boolean plainArt,
              Done done) {
        boolean refetch = !need.isEmpty() && (catalogsAt == 0 || !catalogs.keySet().containsAll(need)
                || SystemClock.elapsedRealtime() - catalogsAt > PICKS_TTL_MS);
        List<String> wanted = new ArrayList<>(need);
        try {
            io.execute(() -> {
                List<Featured.Item> all = watching ? Featured.watching(ctx, Featured.ROW, plainArt) : new ArrayList<>();
                // The hero takes your latest few that have a picture; the row takes them all.
                List<Featured.Item> shows = new ArrayList<>();
                for (Featured.Item w : all) if (w.backdrop != null && shows.size() < heroShows) shows.add(w);
                for (Featured.Item show : shows) {
                    if (!blurbs.containsKey(show.key)) {
                        Featured.describe(addon, show);
                        if (show.description != null) blurbs.put(show.key, show.description);
                    }
                    show.description = blurbs.get(show.key);
                }
                Map<String, List<Featured.Item>> got = new HashMap<>();
                boolean any = false;
                if (refetch) {
                    for (String c : wanted) {
                        List<Featured.Item> l = Featured.catalog(addon, c, Featured.ROW);
                        any |= !l.isEmpty();
                        got.put(c, l);
                    }
                }
                boolean fresh = any;   // offline: keep the catalogs we had
                main.post(() -> {
                    if (fresh) {
                        catalogs = got;
                        catalogsAt = SystemClock.elapsedRealtime();
                    }
                    done.on(all, shows, catalogs);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // closed
        }
    }

    void close() {
        io.shutdownNow();
    }
}
