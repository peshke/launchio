package io.github.peshke.launchio;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Launchio's built-in photos, carried over from tv-home: twelve landscapes from Wikimedia Commons'
 * Featured Pictures (assets/PHOTO-CREDITS.md). Home rotates them behind everything when the theme
 * brings no wallpaper of its own, unless Settings › Background photos is off.
 */
final class Photos {

    static final String KEY = "photos";

    private Photos() {}

    /** The photos already copied out, as files (the wallpaper player reads files); none before the first copy. */
    static List<File> files(Context ctx) {
        List<File> out = new ArrayList<>();
        File[] found = new File(ctx.getFilesDir(), "photos").listFiles((d, name) -> name.endsWith(".jpg"));
        if (found != null) Collections.addAll(out, found);
        Collections.sort(out);
        return out;
    }

    /** Some photo in the APK has not been copied out yet (first start, or a copy cut short). */
    static boolean needCopy(Context ctx) {
        try {
            String[] names = ctx.getAssets().list("photos");
            if (names == null) return false;
            for (String name : names) {
                File f = new File(new File(ctx.getFilesDir(), "photos"), name);
                if (name.endsWith(".jpg") && (!f.isFile() || f.length() == 0)) return true;
            }
        } catch (IOException ignored) {
            // no photos in this build
        }
        return false;
    }

    /** Copies the photos out of the APK. Slow (about 4.6 MB): call it off the main thread. True if it copied any. */
    static boolean copy(Context ctx) {
        boolean any = false;
        File dir = new File(ctx.getFilesDir(), "photos");
        try {
            String[] names = ctx.getAssets().list("photos");
            if (names == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
            for (String name : names) {
                File f = new File(dir, name);
                if (!name.endsWith(".jpg") || (f.isFile() && f.length() > 0)) continue;
                copy(ctx, "photos/" + name, f);
                any = true;
            }
        } catch (IOException e) {
            // storage full or unreadable: the plain background, and the copy is tried again next start
        }
        return any;
    }

    /** Through a temporary file, so a copy cut short never leaves a half picture behind. */
    private static void copy(Context ctx, String asset, File to) throws IOException {
        File tmp = new File(to.getPath() + ".part");
        try (InputStream in = ctx.getAssets().open(asset); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(to)) throw new IOException("rename " + tmp);
    }
}
