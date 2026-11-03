package io.github.peshke.launchio;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pictures from the network (backdrops, card art, logos), decoded off the main thread and kept in a memory cache
 * capped at 16 MB, so browsing the rows for a long time does not keep every picture it passed.
 */
final class Images {

    interface Done {
        void on(Bitmap bitmap);
    }

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(16 << 20) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    // Two threads: one slow server holds up only half the pictures, not every one.
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;

    /** Calls done on the main thread with the picture at about this width; never when it cannot be had. */
    void load(String url, int width, boolean alpha, Done done) {
        if (url == null) return;
        String key = url + '@' + width + (alpha ? "a" : "");
        Bitmap ready = cache.get(key);
        if (ready != null) {
            done.on(ready);
            return;
        }
        try {
            pool.execute(() -> {
                Bitmap bm = Featured.image(url, width, alpha);
                if (bm != null) main.post(() -> {
                    if (closed) return;
                    cache.put(key, bm);
                    done.on(bm);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // closed
        }
    }

    /** Home is covered by an app: let go of every picture; they load again on the way back. */
    void clear() {
        cache.evictAll();
    }

    void close() {
        closed = true;
        pool.shutdownNow();
        cache.evictAll();
    }
}
