package io.github.peshke.launchio;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Apple TV style home screen: clock top-left, pills top-right, the focused dock app's name over an
 * ambient glow, a dock of pinned apps, a Continue watching row from the system Watch Next list,
 * rows of picks from a Stremio-style catalog add-on, and every other app in a grid that scrolls
 * up on Down. Your shows and the picks take turns as the hero behind the dock; picks open in
 * Nuvio. Long press (or Menu) on anything for its options; Settings › Home screen for the rest.
 * Music playing in the background shows under the top bar with its controls.
 * Plain framework views only (no AndroidX), no background work; the network is used only for
 * the catalogs and artwork.
 */
public class MainActivity extends Activity {

    // Defaults come from res/values/config.xml; the dock, hidden apps and rows can change on the TV.
    private HomePrefs prefs;
    private final Set<String> hidden = new HashSet<>();
    private String liveTvPkg, inputPickerPkg;
    private final Set<String> continueApps = new HashSet<>();
    private final List<CatalogSpec> catalogSpecs = new ArrayList<>(); // the rows switched on
    private final Map<String, CatalogSpec> allCatalogs = new LinkedHashMap<>();
    private final List<String> defaultCatalogs = new ArrayList<>();
    private boolean heroOn, heroShows, continueOn; // heroOn: catalog picks take turns in the hero
    private String background, dockAlign; // titles | photos | bing | picsum; left | center | right
    private String catalogAddon, openInPkg, openInLabel;
    private int cols;

    static final class App {
        String pkg, label; // an input's pkg is "input:<id>", so dock and hiding treat it like an app
        String inputId;
        ComponentName component;
        Drawable art;
        boolean isBanner;
        int color;
    }

    /** One entry of the system Watch Next list (what Google TV shows as "Continue watching"). */
    static final class Program {
        String pkg, title, subtitle, art, intentUri, key;
        String contentId, contentType, backdrop, logo; // from the resume intent, when the app adds them
        long position, duration, engaged;
    }

    /** A card under the dock: something to resume, or a catalog pick. */
    static final class Media {
        String key, title, subtitle, art, backdrop, logo, description, action;
        String pkg, type, id, trailer; // owning app; catalog type and id; YouTube trailer id
        boolean resumable; // from Continue watching
        float progress = -1;
        Runnable open;
    }

    /** A catalog row from config.xml: "type/catalog id|Row title". */
    static final class CatalogSpec {
        String type, id, name;

        String key() { return type + "/" + id; }
    }

    /** A labelled, sideways-scrolling row of media cards. */
    static final class MediaRow {
        TextView label;
        HorizontalScrollView scroller;
        LinearLayout row;
        final List<View> cards = new ArrayList<>();
        String key = "";
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();
    private final List<MediaRow> mediaRows = new ArrayList<>(); // top to bottom
    private final List<MediaRow> catalogRows = new ArrayList<>();
    private MediaRow continueRow;

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private FrameLayout hero;
    private LinearLayout grid, featuredBox;
    private ImageView featuredLogo;
    private TextView featuredName, featuredMeta, featuredDesc, featuredButton, featuredCount;
    private View heroShade;
    private final List<Media> featured = new ArrayList<>();
    private final List<Media> showsFeatured = new ArrayList<>(), picksFeatured = new ArrayList<>();
    private final Map<String, String[]> metaCache = new ConcurrentHashMap<>(); // type/id -> blurb, trailer
    private int featuredIndex;
    private long catalogsLoadedAt;
    private String backdropWanted, backdropShown, pendingBackdropUri;
    private final Runnable pendingBackdrop = () -> showBackdrop(pendingBackdropUri);
    private View glow;
    private ImageView wallBack, wallFront;
    private View gridShade;
    private String[] wallpapers = new String[0];
    private int wallIndex = -1;
    private long wallChangedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateWallpaper = this::nextWallpaper;
    private final Runnable rotateHero = this::nextHero;
    private long heroChangedAt;
    private final List<String> bingPhotos = new ArrayList<>(); // io threads only, under its lock
    private long bingPhotosAt;
    private final Runnable refreshContinue = this::loadContinue;
    // Apps update Watch Next as playback stops, often just after Home is pressed: reload on change.
    private final ContentObserver watchNextObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            handler.removeCallbacks(refreshContinue);
            handler.postDelayed(refreshContinue, 500);
        }
    };
    static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    static final String READ_PICTURES = "android.permission.READ_EXTERNAL_STORAGE";
    // An HDMI device turned on or off: refresh the input tiles' "Connected" line.
    private final Runnable rebuildApps = this::buildApps;
    private final TvInputManager.TvInputCallback inputsChanged = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) { rebuildSoon(); }
        @Override public void onInputAdded(String inputId) { rebuildSoon(); }
        @Override public void onInputRemoved(String inputId) { rebuildSoon(); }
        @Override public void onInputUpdated(String inputId) { rebuildSoon(); }
    };
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> artCache = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    static final long WALLPAPER_INTERVAL_MS = 10 * 60 * 1000;
    static final long FEATURED_INTERVAL_MS = 15 * 1000;
    static final long CATALOG_TTL_MS = 3 * 60 * 60 * 1000;
    static final int ROW_MAX = 15, FEATURED_MAX = 8, HERO_SHOWS = 3;
    static final String STREMIO = "com.stremio.one", YOUTUBE = "com.google.android.youtube.tv";
    private TextView greeting, clock, ampm, date, title, wifiPill, appsLabel;
    private int glowColor = 0xFF3A3A3A;
    private ValueAnimator glowAnim;
    private int lastFocusedId = View.NO_ID;
    private boolean firstShow = true;
    private int packageSeq;
    private PopupWindow inputsPopup;
    private TextView inputsPill, settingsPill;
    private String focusPkg; // app tile to focus after the next rebuild (moved in the dock)
    private boolean settingsChanged;

    // Now playing: a music app's session, shown under the top bar.
    private LinearLayout npRow;
    private TextView npInfo, npPrev, npPlay, npNext;
    private List<View> npViews = new ArrayList<>();
    private MediaSessionManager sessionManager;
    private MediaController playing;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickSession;
    private final MediaController.Callback playingCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { pickSession(null); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { showNowPlaying(); }
        @Override public void onSessionDestroyed() { pickSession(null); }
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateWifi(); }
    };
    // Ethernet plugged in or pulled, Wi-Fi joined or dropped: refresh the network pill.
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { updateWifi(); }
        @Override public void onLost(Network n) { updateWifi(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildApps(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new HomePrefs(this);
        hidden.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        liveTvPkg = getString(R.string.live_tv_package);
        inputPickerPkg = getString(R.string.input_picker_package);
        continueApps.addAll(Arrays.asList(getResources().getStringArray(R.array.continue_watching_apps)));
        catalogAddon = getString(R.string.catalog_addon).trim().replaceAll("(/manifest\\.json)?/*$", "");
        heroOn = prefs.flag("hero", true);
        heroShows = prefs.flag("hero_shows", true);
        continueOn = prefs.flag("continue", true);
        background = prefs.choice("background", "titles");
        dockAlign = prefs.choice("dock_align", "center");
        if (!catalogAddon.isEmpty()) {
            for (String row : getResources().getStringArray(R.array.catalog_rows)) {
                CatalogSpec c = parseCatalog(row);
                if (c != null) { allCatalogs.put(c.key(), c); defaultCatalogs.add(c.key()); }
            }
            for (String row : getResources().getStringArray(R.array.catalog_choices)) {
                CatalogSpec c = parseCatalog(row);
                if (c != null && !allCatalogs.containsKey(c.key())) allCatalogs.put(c.key(), c);
            }
            Set<String> on = prefs.catalogs(defaultCatalogs);
            for (CatalogSpec c : allCatalogs.values()) if (on.contains(c.key())) catalogSpecs.add(c);
        }
        openInPkg = getString(R.string.open_in_package);
        openInLabel = "Nuvio";
        try {
            openInLabel = String.valueOf(getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(openInPkg, 0)));
        } catch (PackageManager.NameNotFoundException ignored) { }
        cols = getResources().getInteger(R.integer.columns);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        buildApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputsChanged, handler);

        // Granted over ADB at install; ask once for sets where the launcher was installed by hand.
        if (checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("asked_tv_listings", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_tv_listings", true).apply();
            requestPermissions(new String[]{READ_TV_LISTINGS}, 1);
        }
    }

    /** "type/catalog id|Row title" from config.xml. */
    private static CatalogSpec parseCatalog(String row) {
        String[] parts = row.split("\\|", 2), path = parts[0].trim().split("/");
        if (path.length != 2) return null;
        CatalogSpec c = new CatalogSpec();
        c.type = path[0];
        c.id = path[1];
        c.name = parts.length > 1 ? parts[1].trim() : row;
        return c;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        loadContinue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(tick, tf);
        ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(network, handler);
        updateClock();
        updateWifi();
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last == null && !dockCards.isEmpty()) last = dockCards.get(0);
        if (last != null) last.requestFocus();
        // Catch installs/removals that happened while another app was in front.
        if (getPackageManager().getChangedPackages(packageSeq) != null) buildApps();
        if (firstShow) { firstShow = false; playEntrance(); }
        loadContinue();
        loadCatalogs();
        watchSessions();
        try {
            getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, watchNextObserver);
        } catch (RuntimeException ignored) { } // no TV provider
        // On return, move on to the next pick and photo if the current ones have had their time.
        long now = SystemClock.elapsedRealtime();
        if (!featured.isEmpty()) {
            if (now - heroChangedAt >= FEATURED_INTERVAL_MS) nextHero();
            else scheduleHero(FEATURED_INTERVAL_MS - (now - heroChangedAt));
        }
        if (!(titleBackdrops() && !featured.isEmpty())) {
            if (wallChangedAt == 0 || now - wallChangedAt >= WALLPAPER_INTERVAL_MS) nextWallpaper();
            else scheduleWallpaper(WALLPAPER_INTERVAL_MS - (now - wallChangedAt));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotateWallpaper);
        handler.removeCallbacks(rotateHero);
        handler.removeCallbacks(refreshContinue);
        handler.removeCallbacks(pendingBackdrop);
        unwatchSessions();
        getContentResolver().unregisterContentObserver(watchNextObserver);
        unregisterReceiver(tick);
        ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(network);
        if (inputsPopup != null) inputsPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputsChanged);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the first dock app.
        if (inputsPopup != null) inputsPopup.dismiss();
        if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; Back from the grid returns to the dock.
        if (scroll.getScrollY() > 0 && !dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Wallpaper: two stacked layers so a new photo can crossfade in over the old one.
        wallBack = wallpaperLayer();
        wallFront = wallpaperLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));
        // Scrim: darker at the top (clock, pills) and bottom (dock) so text stays readable.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x40000000, 0x26000000, 0x8C000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        // Left-side shade behind the featured pick's text; faded in once there is one.
        heroShade = new View(this);
        heroShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xB3000000, 0x4D000000, 0x00000000}));
        heroShade.setAlpha(0f);
        root.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0xB3000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));
        try {
            wallpapers = getAssets().list("wallpapers");
        } catch (java.io.IOException ignored) { }

        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        setGlow(glowColor);

        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        // Hero: exactly one screen tall, header on top, title + dock at the bottom.
        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        content.addView(hero, new LinearLayout.LayoutParams(-1, screenH));

        hero.addView(buildHeader(), lp(-1, -2, Gravity.TOP, dp(48), dp(36), dp(48), 0));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        hero.addView(bottom, lp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(30)));

        bottom.addView(buildFeatured(), llp(-1, -2, dp(8), 0, 0, dp(22)));

        title = text(34, "sans-serif-light", Color.WHITE);
        title.setShadowLayer(dp(18), 0, 0, 0x66000000);
        title.setSingleLine(true);
        LinearLayout.LayoutParams tp = llp(-2, -2, dp(8), 0, dp(8), dp(14));
        tp.gravity = dockGravity();
        bottom.addView(title, tp);

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(26));
        band.setColor(0x1FFFFFFF);
        band.setStroke(dp(1), 0x14FFFFFF);
        dockRow.setBackground(band);
        // The glass band wraps the docked apps and sits where Settings › Home screen puts it.
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(-2, -2);
        dockLp.gravity = dockGravity();
        bottom.addView(dockRow, dockLp);

        continueRow = addMediaRow("Continue watching");
        for (CatalogSpec c : catalogSpecs) catalogRows.add(addMediaRow(c.name));

        appsLabel = text(11, "sans-serif-medium", 0x99FFFFFF);
        appsLabel.setText("ALL APPS");
        appsLabel.setLetterSpacing(0.22f);
        content.addView(appsLabel, llp(-2, -2, dp(68), dp(8), 0, dp(18)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(68), 0, dp(68), dp(160)));
        return root;
    }

    private int dockGravity() {
        return "left".equals(dockAlign) ? Gravity.START : "right".equals(dockAlign) ? Gravity.END : Gravity.CENTER_HORIZONTAL;
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text(10, "sans-serif-medium", 0xB3FFFFFF);
        greeting.setLetterSpacing(0.25f);
        greeting.setSingleLine(true);
        greeting.setPadding(0, 0, dp(16), 0);
        left.addView(greeting);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = text(54, "sans-serif-thin", Color.WHITE);
        clock.setIncludeFontPadding(false);
        clockRow.addView(clock);
        ampm = text(12, "sans-serif-light", 0xCCFFFFFF);
        clockRow.addView(ampm, llp(-2, -2, dp(6), 0, 0, dp(8)));
        left.addView(clockRow, llp(-2, -2, 0, dp(2), 0, dp(2)));

        date = text(10, "sans-serif-medium", 0x99FFFFFF);
        date.setSingleLine(true);
        date.setPadding(0, 0, dp(16), 0); // letterSpacing is not counted in the measured width
        date.setLetterSpacing(0.22f);
        left.addView(date);
        for (TextView t : new TextView[]{greeting, clock, ampm, date}) t.setShadowLayer(dp(10), 0, dp(1), 0xB3000000);
        header.addView(left, new FrameLayout.LayoutParams(dp(420), -2, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setClipChildren(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        inputsPill = pill("Inputs", "⇥");
        inputsPill.setOnClickListener(v -> showInputs(v));
        wifiPill = pill("Wi-Fi", "◉");
        wifiPill.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        settingsPill = pill("Settings", "⚙");
        settingsPill.setOnClickListener(v -> showSettingsMenu(v));
        for (TextView p : new TextView[]{inputsPill, wifiPill, settingsPill}) {
            bar.addView(p, llp(-2, dp(30), dp(10), 0, 0, 0));
            pills.add(p);
        }
        right.addView(bar, new LinearLayout.LayoutParams(-2, -2));
        right.addView(buildNowPlaying(), llp(-2, -2, 0, dp(12), 0, 0));
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return header;
    }

    /** What a music app is playing in the background, with previous / play-pause / next. */
    private View buildNowPlaying() {
        npRow = new LinearLayout(this);
        npRow.setGravity(Gravity.CENTER_VERTICAL);
        npRow.setClipChildren(false);
        npRow.setVisibility(View.GONE);
        npInfo = pill("", "♪");
        npInfo.setMaxWidth(dp(300));
        npInfo.setSingleLine(true);
        npInfo.setEllipsize(TextUtils.TruncateAt.END);
        npInfo.setCompoundDrawablePadding(dp(8));
        npInfo.setOnClickListener(v -> openPlaying());
        npPrev = pill("", "");
        npPrev.setText("‹‹");
        npPrev.setOnClickListener(v -> { if (playing != null) playing.getTransportControls().skipToPrevious(); });
        npPlay = pill("", "");
        npPlay.setOnClickListener(v -> {
            if (playing == null) return;
            if (isPlaying(playing)) playing.getTransportControls().pause();
            else playing.getTransportControls().play();
        });
        npNext = pill("", "");
        npNext.setText("››");
        npNext.setOnClickListener(v -> { if (playing != null) playing.getTransportControls().skipToNext(); });
        npViews = Arrays.asList(npInfo, npPrev, npPlay, npNext);
        for (View v : npViews) npRow.addView(v, llp(-2, dp(30), dp(10), 0, 0, 0));
        return npRow;
    }

    /** The featured pick above the dock: logo (or name), a line of facts, a blurb, an Open button. */
    private View buildFeatured() {
        featuredBox = new LinearLayout(this);
        featuredBox.setOrientation(LinearLayout.VERTICAL);
        featuredBox.setClipChildren(false);
        featuredBox.setClipToPadding(false);
        featuredBox.setVisibility(View.GONE);

        featuredLogo = new ImageView(this);
        featuredLogo.setAdjustViewBounds(true);
        featuredLogo.setScaleType(ImageView.ScaleType.FIT_START);
        featuredLogo.setMaxHeight(dp(72));
        featuredLogo.setMaxWidth(dp(360));
        featuredLogo.setVisibility(View.GONE);
        featuredBox.addView(featuredLogo, llp(-2, -2, 0, 0, 0, dp(10)));

        featuredName = text(30, "sans-serif-light", Color.WHITE);
        featuredName.setShadowLayer(dp(18), 0, 0, 0x99000000);
        featuredName.setSingleLine(true);
        featuredName.setEllipsize(TextUtils.TruncateAt.END);
        featuredBox.addView(featuredName, llp(dp(560), -2, 0, 0, 0, dp(6)));

        featuredMeta = text(11, "sans-serif-medium", 0xCCFFFFFF);
        featuredMeta.setLetterSpacing(0.06f);
        featuredMeta.setSingleLine(true);
        featuredBox.addView(featuredMeta, llp(-2, -2, 0, 0, 0, dp(6)));

        featuredDesc = text(12, "sans-serif", 0xB3FFFFFF);
        featuredDesc.setMaxLines(2);
        featuredDesc.setEllipsize(TextUtils.TruncateAt.END);
        featuredDesc.setLineSpacing(dp(2), 1f);
        featuredBox.addView(featuredDesc, llp(dp(520), -2, 0, 0, 0, dp(14)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false);
        featuredButton = pill("Open", "▶");
        featuredButton.setOnClickListener(v -> { if (!featured.isEmpty()) featured.get(featuredIndex).open.run(); });
        featuredButton.setOnLongClickListener(v -> {
            if (!featured.isEmpty()) showMediaMenu(v, featured.get(featuredIndex));
            return true;
        });
        // Left / Right on the button flip through the picks.
        featuredButton.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                showFeatured(featuredIndex + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
                scheduleHero(FEATURED_INTERVAL_MS);
            }
            return true;
        });
        actions.addView(featuredButton, llp(-2, dp(30), 0, 0, 0, 0));
        featuredCount = text(10, "sans-serif-medium", 0x99FFFFFF);
        featuredCount.setLetterSpacing(0.15f);
        actions.addView(featuredCount, llp(-2, -2, dp(14), 0, 0, 0));
        featuredBox.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        for (TextView t : new TextView[]{featuredMeta, featuredDesc, featuredCount}) t.setShadowLayer(dp(8), 0, dp(1), 0xB3000000);
        return featuredBox;
    }

    private MediaRow addMediaRow(String name) {
        MediaRow r = new MediaRow();
        r.label = text(11, "sans-serif-medium", 0x99FFFFFF);
        r.label.setText(name.toUpperCase(Locale.getDefault()));
        r.label.setLetterSpacing(0.22f);
        r.label.setVisibility(View.GONE);
        content.addView(r.label, llp(-2, -2, dp(68), dp(8), 0, dp(4)));

        // Side padding lines the first card up with the grid; cards scroll out under it.
        r.scroller = new HorizontalScrollView(this);
        r.scroller.setHorizontalScrollBarEnabled(false);
        r.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        r.scroller.setClipChildren(false);
        r.scroller.setClipToPadding(false);
        r.scroller.setPadding(dp(68), dp(14), dp(68), dp(14));
        r.scroller.setVisibility(View.GONE);
        content.addView(r.scroller, llp(-1, -2, 0, 0, 0, dp(30)));

        r.row = new LinearLayout(this);
        r.row.setClipChildren(false);
        r.row.setClipToPadding(false);
        r.scroller.addView(r.row, new FrameLayout.LayoutParams(-2, -2));
        mediaRows.add(r);
        return r;
    }

    private TextView pill(String label, String glyph) {
        TextView p = text(11, "sans-serif-medium", 0xE6FFFFFF);
        p.setText(glyph + "  " + label);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(14), 0, dp(14), 0);
        p.setFocusable(true);
        p.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(15));
        bg.setColor(0x66000000);
        bg.setStroke(dp(1), 0x33FFFFFF);
        p.setBackground(bg);
        p.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x66000000);
            ((TextView) v).setTextColor(has ? 0xFF111111 : 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(220).start();
            if (has) {
                lastFocusedId = v.getId();
                scroll.smoothScrollTo(0, 0);
                fadeGlow(1f);
                if (v == featuredButton) backToHero();
            }
        });
        return p;
    }

    // ---------------------------------------------------------------- apps

    private void buildApps() {
        PackageManager pm = getPackageManager();
        android.content.pm.ChangedPackages changed = pm.getChangedPackages(packageSeq);
        if (changed != null) packageSeq = changed.getSequenceNumber();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, App> byPkg = new HashMap<>();
        Set<String> hide = new HashSet<>(hidden);
        hide.addAll(prefs.hiddenApps());
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (hide.contains(ai.packageName) || byPkg.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.component = new ComponentName(ai.packageName, ai.name);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            a.isBanner = banner != null;
            a.art = banner != null ? banner : ri.loadIcon(pm);
            a.color = sampleColor(a.pkg, a.art);
            byPkg.put(a.pkg, a);
        }
        // TV inputs (HDMI, AV, …) as tiles too, so they can go in the dock.
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = tim == null ? Collections.emptyList() : tim.getTvInputList();
        // A port with an HDMI-CEC device on it ("PlayStation 5" on HDMI 1) shows once, as the device.
        Set<String> ports = new HashSet<>();
        for (TvInputInfo t : inputs) if (t.getParentId() != null) ports.add(t.getParentId());
        for (TvInputInfo t : inputs) {
            if (!t.isPassthroughInput() || t.isHidden(this) || ports.contains(t.getId())
                    || hide.contains("input:" + t.getId())) continue;
            App a = new App();
            a.pkg = "input:" + t.getId();
            a.inputId = t.getId();
            a.label = inputLabel(t);
            a.isBanner = true;
            a.color = inputColors(t.getType())[1];
            a.art = inputBanner(t, a.label, tim.getInputState(t.getId()) == TvInputManager.INPUT_STATE_CONNECTED);
            byPkg.put(a.pkg, a);
        }

        List<App> dock = new ArrayList<>();
        for (String p : prefs.dock()) if (byPkg.containsKey(p)) dock.add(byPkg.remove(p));
        List<App> rest = new ArrayList<>(byPkg.values());
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));

        // Keep focus on the same tile through a rebuild (ids are regenerated).
        View had = getCurrentFocus();
        if (focusPkg == null && had != null && had.getTag() instanceof App) focusPkg = ((App) had.getTag()).pkg;
        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        // Tiles keep the width they have in a five-app dock, centred, so a shorter dock doesn't
        // grow taller (tiles are 16:9); a longer one shrinks them to fit.
        int dockInner = getResources().getDisplayMetrics().widthPixels - dp(48) * 2 - dp(20) * 2;
        int slots = Math.max(5, dock.size());
        int tileW = (dockInner - dp(20) * (slots - 1)) / slots;
        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(tileW, -2);
            p.setMargins(i == 0 ? 0 : dp(10), 0, i == dock.size() - 1 ? 0 : dp(10), 0);
            dockRow.addView(tile, p);
        }

        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, llp(-1, -2, 0, 0, 0, dp(26)));
            }
            View tile = tile(rest.get(i), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % cols == 0 ? 0 : dp(10), 0, i % cols == cols - 1 ? 0 : dp(10), 0);
            row.addView(tile, p);
        }
        // Pad the last row so tiles keep their width.
        if (row != null) for (int i = rest.size() % cols; i != 0 && i < cols; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(10), 0, i == cols - 1 ? 0 : dp(10), 0);
            row.addView(new View(this), p);
        }
        appsLabel.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
        wireFocus();

        if (!dock.isEmpty()) { title.setText(dock.get(0).label); setGlow(dock.get(0).color); }
        if (focusPkg != null) {
            String want = focusPkg;
            focusPkg = null;
            for (List<View> cards : Arrays.asList(dockCards, gridCards)) for (View c : cards) {
                if (((App) c.getTag()).pkg.equals(want)) { c.requestFocus(); return; }
            }
        }
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last != null) last.requestFocus();
        else if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    /** A 16:9 card with the app's banner; the name shows under it while focused. */
    private View tile(App app, boolean inDock) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        roundCorners(card);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(app.isBanner ? 0xFF1C1C1E : darken(app.color, 0.45f));
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            img.setPadding(dp(22), dp(14), dp(22), dp(14));
        }

        View shine = shine();
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text(11, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        if (!inDock) box.addView(label, llp(-1, -2, 0, dp(10), 0, 0));

        card.setOnClickListener(v -> {
            if (app.inputId != null) { openInput(app.inputId); return; }
            launch(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        });
        card.setOnLongClickListener(v -> { showAppMenu(v, app, inDock); return true; });
        card.setOnFocusChangeListener((v, has) -> {
            animateFocus(v, shine, label, has, inDock ? 1.12f : 1.1f);
            if (!has) return;
            lastFocusedId = v.getId();
            if (inDock) {
                title.setText(app.label);
                title.animate().alpha(1f).setDuration(200).start();
                animateGlow(app.color);
                fadeGlow(1f);
                scroll.smoothScrollTo(0, 0);
                backToHero();
            } else {
                fadeGlow(0.35f);
                title.animate().alpha(0f).setDuration(200).start();
                scrollToRow(box);
            }
        });

        (inDock ? dockCards : gridCards).add(card);
        return box;
    }

    private void animateFocus(View card, View shine, View label, boolean has, float scale) {
        float s = has ? scale : 1f;
        card.animate().scaleX(s).scaleY(s).translationZ(has ? dp(14) : 0)
                .setInterpolator(has ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator())
                .setDuration(has ? 320 : 200).start();
        shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
        label.animate().alpha(has ? 1f : 0f).translationY(has ? dp(4) : 0).setDuration(200).start();
    }

    /** Focus ring and sheen, drawn above a card's art. */
    private View shine() {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(2), 0xE6FFFFFF);
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        ring.setColors(new int[]{0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        View shine = new View(this);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        return shine;
    }

    private void roundCorners(View card) {
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
            }
        });
    }

    // ---------------------------------------------------------------- continue watching

    /** Read Watch Next off the main thread. The TV provider only shares other apps' rows with
     *  READ_TV_LISTINGS; without it the row stays hidden. */
    private void loadContinue() {
        if (checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED
                || (!continueOn && !heroShows)) {
            showRow(continueRow, Collections.emptyList());
            return;
        }
        try {
            io.execute(() -> {
                List<Media> list = queryWatchNext();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    showRow(continueRow, continueOn ? list : Collections.emptyList());
                    setShows(list);
                });
            });
        } catch (RejectedExecutionException ignored) { } // destroyed
    }

    private List<Media> queryWatchNext() {
        List<Program> found = new ArrayList<>();
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
                TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        };
        Set<String> removed = prefs.hiddenPrograms(), present = new HashSet<>();
        boolean read = false;
        // The provider rejects a selection or custom sort from non-system apps: filter and sort here.
        try (Cursor c = getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, proj, null, null, null)) {
            read = c != null;
            while (c != null && c.moveToNext()) {
                if (c.getInt(11) == 0) continue; // removed by the user from the system row
                Program p = new Program();
                p.pkg = c.getString(0);
                if (!continueApps.isEmpty() && !continueApps.contains(p.pkg)) continue;
                if (!isInstalled(p.pkg)) continue;
                p.title = c.getString(1);
                if (TextUtils.isEmpty(p.title)) continue;
                p.art = !c.isNull(5) ? c.getString(5) : c.getString(6);
                p.intentUri = c.getString(7);
                p.position = c.getLong(8);
                p.duration = c.getLong(9);
                p.engaged = c.getLong(10);
                p.subtitle = subtitle(c.getString(3), c.getString(4), c.getString(2), p);
                p.key = p.pkg + "/" + (c.isNull(12) ? p.title + "|" + p.subtitle : c.getString(12));
                present.add(p.key);
                if (removed.contains(p.key)) continue; // removed from the row with a long press
                // Nuvio's resume intent carries the title's IMDb id and art; use them when present.
                try {
                    Intent in = p.intentUri == null ? null : Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME);
                    if (in != null) {
                        p.contentId = in.getStringExtra("contentId");
                        p.contentType = in.getStringExtra("contentType");
                        p.backdrop = in.getStringExtra("backdrop");
                        p.logo = in.getStringExtra("logo");
                    }
                } catch (URISyntaxException | RuntimeException ignored) { }
                found.add(p);
            }
        } catch (RuntimeException ignored) { } // no TV provider, or permission revoked
        if (read) prefs.keepHiddenPrograms(present);
        Collections.sort(found, (x, y) -> Long.compare(y.engaged, x.engaged));
        List<Media> out = new ArrayList<>();
        for (Program p : found) {
            if (out.size() == ROW_MAX) break;
            Media m = new Media();
            m.key = p.key;
            m.title = p.title;
            m.subtitle = p.subtitle;
            m.art = p.art;
            m.backdrop = p.backdrop;
            m.logo = p.logo;
            m.pkg = p.pkg;
            m.type = p.contentType;
            m.id = p.contentId;
            m.resumable = true;
            m.action = "Resume";
            if (p.duration > 0 && p.position > 0) m.progress = Math.min(1f, (float) p.position / p.duration);
            m.open = () -> openProgram(p);
            // The ones that may go in the hero get a blurb (and a trailer for the menu).
            if (out.size() < HERO_SHOWS && m.type != null && m.id != null) {
                String[] meta = describe(m.type, m.id);
                m.description = meta[0];
                m.trailer = meta[1];
            }
            out.add(m);
        }
        return out;
    }

    private static String subtitle(String season, String episode, String episodeTitle, Program p) {
        StringBuilder b = new StringBuilder();
        if (!TextUtils.isEmpty(season)) b.append('S').append(season);
        if (!TextUtils.isEmpty(episode)) b.append(b.length() > 0 ? " · E" : "E").append(episode);
        if (!TextUtils.isEmpty(episodeTitle)) b.append(b.length() > 0 ? " · " : "").append(episodeTitle);
        if (b.length() == 0 && p.duration > 0 && p.position > 0) {
            long left = Math.max(1, (p.duration - p.position) / 60000);
            b.append(left).append(" min left");
        }
        return b.toString();
    }

    /** Resume where the app said to; fall back to just opening the app. */
    private void openProgram(Program p) {
        if (p.intentUri != null) {
            try {
                if (launch(Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME))) return;
            } catch (URISyntaxException ignored) { }
        }
        openPackage(p.pkg);
    }

    // ---------------------------------------------------------------- catalogs and the hero

    /** Fetch the catalog rows off the main thread; refetch on return once they're a few hours old. */
    private void loadCatalogs() {
        if (catalogSpecs.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (catalogsLoadedAt != 0 && now - catalogsLoadedAt < CATALOG_TTL_MS) return;
        catalogsLoadedAt = now;
        try {
            io.execute(() -> {
                List<List<Media>> lists = new ArrayList<>();
                boolean any = false;
                for (CatalogSpec c : catalogSpecs) {
                    List<Media> l = fetchCatalog(c);
                    lists.add(l);
                    any |= !l.isEmpty();
                }
                boolean ok = any;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (!ok) { catalogsLoadedAt = 0; return; } // offline: keep the photos, retry on return
                    for (int i = 0; i < lists.size(); i++) showRow(catalogRows.get(i), lists.get(i));
                    setPicks(lists);
                });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private List<Media> fetchCatalog(CatalogSpec c) {
        List<Media> out = new ArrayList<>();
        byte[] data = readUri(catalogAddon + "/catalog/" + c.type + "/" + c.id + ".json", 4 * 1024 * 1024);
        if (data == null) return out;
        try {
            JSONArray metas = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONArray("metas");
            for (int i = 0; metas != null && i < metas.length() && out.size() < ROW_MAX; i++) {
                JSONObject j = metas.optJSONObject(i);
                if (j == null) continue;
                String id = str(j, "id"), name = str(j, "name");
                if (id == null || name == null) continue;
                String type = str(j, "type") != null ? str(j, "type") : c.type;
                Media m = new Media();
                m.title = name;
                m.art = str(j, "background") != null ? str(j, "background") : str(j, "poster");
                m.logo = str(j, "logo");
                m.description = str(j, "description");
                List<String> facts = new ArrayList<>();
                if (str(j, "releaseInfo") != null) facts.add(str(j, "releaseInfo"));
                JSONArray genres = j.optJSONArray("genres") != null ? j.optJSONArray("genres") : j.optJSONArray("genre");
                for (int g = 0; genres != null && g < genres.length() && g < 2; g++) facts.add(genres.optString(g));
                if (str(j, "imdbRating") != null) facts.add("★ " + str(j, "imdbRating"));
                m.subtitle = TextUtils.join("  ·  ", facts);
                m.key = "pick/" + type + "/" + id;
                m.type = type;
                m.id = id;
                m.trailer = trailerOf(j);
                m.action = "Open in " + openInLabel;
                m.open = () -> openInApp(type, id);
                out.add(m);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    /** A title's blurb and trailer from the add-on's meta endpoint; cached once fetched. */
    private String[] describe(String type, String id) {
        String key = type + "/" + id;
        String[] got = metaCache.get(key);
        if (got != null) return got;
        String[] out = new String[2];
        if (catalogAddon.isEmpty()) return out;
        byte[] data = readUri(catalogAddon + "/meta/" + type + "/" + id + ".json", 4 * 1024 * 1024);
        if (data == null) return out; // try again next time
        try {
            JSONObject meta = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONObject("meta");
            if (meta != null) {
                out[0] = str(meta, "description");
                out[1] = trailerOf(meta);
            }
        } catch (JSONException ignored) { }
        metaCache.put(key, out);
        return out;
    }

    /** The YouTube id of a title's first trailer, from either field Stremio add-ons use. */
    private static String trailerOf(JSONObject j) {
        JSONArray streams = j.optJSONArray("trailerStreams");
        JSONObject first = streams != null ? streams.optJSONObject(0) : null;
        if (first != null && str(first, "ytId") != null) return str(first, "ytId");
        JSONArray trailers = j.optJSONArray("trailers");
        first = trailers != null ? trailers.optJSONObject(0) : null;
        return first != null ? str(first, "source") : null;
    }

    /** A non-empty string field, or null (optString turns JSON null into "null"). */
    private static String str(JSONObject j, String key) {
        Object o = j.opt(key);
        return o instanceof String && !((String) o).isEmpty() ? (String) o : null;
    }

    /** Open a pick's page through Nuvio's nuvio://meta link. */
    private void openInApp(String type, String id) {
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("nuvio://meta?type=" + Uri.encode(type) + "&id=" + Uri.encode(id)));
        if (isInstalled(openInPkg)) i.setPackage(openInPkg);
        launch(i);
    }

    /** The first few picks of each catalog, alternating, are the hero's picks. */
    private void setPicks(List<List<Media>> lists) {
        picksFeatured.clear();
        if (heroOn) for (int i = 0; picksFeatured.size() < FEATURED_MAX; i++) {
            boolean more = false;
            for (List<Media> l : lists) {
                if (i >= l.size()) continue;
                more = true;
                if (l.get(i).art != null && picksFeatured.size() < FEATURED_MAX) picksFeatured.add(l.get(i));
            }
            if (!more) break;
        }
        rebuildFeatured();
    }

    /** The shows you were last watching join the hero. */
    private void setShows(List<Media> list) {
        showsFeatured.clear();
        if (heroShows) for (Media m : list) {
            if (showsFeatured.size() == HERO_SHOWS) break;
            if (backdropOf(m) != null) showsFeatured.add(m);
        }
        rebuildFeatured();
    }

    /** One of your shows, then two picks, and so on; keeps the one on screen where it is. */
    private void rebuildFeatured() {
        List<Media> all = new ArrayList<>();
        int a = 0, b = 0;
        while (all.size() < FEATURED_MAX && (a < showsFeatured.size() || b < picksFeatured.size())) {
            if (a < showsFeatured.size()) all.add(showsFeatured.get(a++));
            for (int k = 0; k < 2 && b < picksFeatured.size() && all.size() < FEATURED_MAX; k++) all.add(picksFeatured.get(b++));
        }
        if (all.isEmpty()) return; // keep what's up (or the photos) rather than blanking the hero
        boolean first = featured.isEmpty();
        String current = first ? null : featured.get(featuredIndex).key;
        int at = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).key.equals(current)) at = i;
        boolean same = all.get(at).key.equals(current);
        featured.clear();
        featured.addAll(all);
        if (first) {
            featuredBox.setVisibility(View.VISIBLE);
            title.setVisibility(View.GONE); // the pick takes the dock app's name spot
            heroShade.animate().alpha(1f).setDuration(600).start();
            glow.animate().alpha(glow.getAlpha() * 0.35f).setDuration(600).start();
            wireFocus();
            if (titleBackdrops()) handler.removeCallbacks(rotateWallpaper); // backdrops follow the hero now
            showFeatured(0);
            scheduleHero(FEATURED_INTERVAL_MS);
        } else if (same) {
            featuredIndex = at;
            featuredCount.setText((at + 1) + " / " + all.size());
        } else {
            showFeatured(at);
        }
    }

    private static String backdropOf(Media m) {
        return m.backdrop != null ? m.backdrop : m.art;
    }

    private void showFeatured(int i) {
        if (featured.isEmpty()) return;
        int n = featured.size();
        featuredIndex = ((i % n) + n) % n;
        Media m = featured.get(featuredIndex);
        heroChangedAt = SystemClock.elapsedRealtime();
        featuredName.setText(m.title);
        featuredName.setVisibility(View.VISIBLE);
        featuredLogo.setVisibility(View.GONE);
        featuredLogo.setImageDrawable(null);
        String facts = m.resumable ? (TextUtils.isEmpty(m.subtitle) ? "Continue watching"
                : "Continue watching  ·  " + m.subtitle) : m.subtitle;
        featuredMeta.setText(facts);
        featuredMeta.setVisibility(TextUtils.isEmpty(facts) ? View.GONE : View.VISIBLE);
        featuredButton.setText("▶  " + m.action);
        featuredDesc.setText(m.description == null ? "" : m.description);
        featuredCount.setText((featuredIndex + 1) + " / " + n);
        // The title logo replaces the plain name once it has loaded.
        loadArt(m.logo, featuredLogo, dp(360), true, () -> {
            featuredName.setVisibility(View.GONE);
            featuredLogo.setVisibility(View.VISIBLE);
        });
        featuredBox.setAlpha(0f);
        featuredBox.animate().alpha(1f).setDuration(400).start();
        if (!mediaCardFocused()) showBackdrop(backdropOf(m));
    }

    /** Back on the dock or the Open button: the background follows the featured pick again. */
    private void backToHero() {
        handler.removeCallbacks(pendingBackdrop);
        if (!featured.isEmpty()) showBackdrop(backdropOf(featured.get(featuredIndex)));
    }

    private boolean mediaCardFocused() {
        View f = getCurrentFocus();
        for (MediaRow r : mediaRows) if (r.cards.contains(f)) return true;
        return false;
    }

    /** Crossfade the background to a title's backdrop. */
    private void showBackdrop(String uri) {
        if (!titleBackdrops() || TextUtils.isEmpty(uri) || uri.equals(backdropShown)) return;
        backdropWanted = uri;
        int want = getResources().getDisplayMetrics().widthPixels;
        String key = uri + "@" + want;
        Bitmap cached = artCache.get(key);
        if (cached != null) { backdropShown = uri; showWallpaper(cached, 700); return; }
        try {
            io.execute(() -> {
                Bitmap bm = decodeArt(uri, want, false);
                runOnUiThread(() -> {
                    if (bm == null || isDestroyed()) return;
                    artCache.put(key, bm);
                    if (!uri.equals(backdropWanted)) return;
                    backdropShown = uri;
                    showWallpaper(bm, 700);
                });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    // ---------------------------------------------------------------- media rows

    private void showRow(MediaRow r, List<Media> list) {
        StringBuilder key = new StringBuilder();
        for (Media m : list) key.append(m.title).append('|').append(m.subtitle).append('|').append(m.art)
                .append('|').append(m.progress).append('\n');
        if (key.toString().equals(r.key)) return;
        r.key = key.toString();

        int focusedAt = r.cards.indexOf(getCurrentFocus());
        r.row.removeAllViews();
        r.cards.clear();
        // Same width as a grid tile, so the first cards line up with the columns below.
        int width = (getResources().getDisplayMetrics().widthPixels - dp(68) * 2 - dp(20) * (cols - 1)) / cols;
        for (int i = 0; i < list.size(); i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, -2);
            p.setMargins(i == 0 ? 0 : dp(20), 0, 0, 0);
            r.row.addView(mediaTile(list.get(i), r), p);
        }
        int vis = list.isEmpty() ? View.GONE : View.VISIBLE;
        r.label.setVisibility(vis);
        r.scroller.setVisibility(vis);
        wireFocus();
        if (focusedAt >= 0) {
            if (!r.cards.isEmpty()) r.cards.get(Math.min(focusedAt, r.cards.size() - 1)).requestFocus();
            else if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
        }
    }

    /** A 16:9 card with the title's art and name, and how far in you are if it's under way. */
    private View mediaTile(Media m, MediaRow r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        roundCorners(card);
        card.setBackgroundColor(0xFF1C1C1E);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        loadArt(m.art, img, getResources().getDisplayMetrics().widthPixels / 3, false, null);

        // Title over a bottom fade, for art without the logo burned in.
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x66000000, 0x00000000}));
        card.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        TextView name = text(13, "sans-serif-medium", Color.WHITE);
        name.setText(m.title);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setShadowLayer(dp(6), 0, dp(1), 0xB3000000);
        card.addView(name, lp(-1, -2, Gravity.BOTTOM, dp(12), 0, dp(12), dp(12)));

        if (m.progress >= 0) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackgroundColor(0x40FFFFFF);
            View done = new View(this);
            done.setBackgroundColor(0xF2FFFFFF);
            bar.addView(done, new LinearLayout.LayoutParams(0, -1, m.progress));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - m.progress));
            card.addView(bar, lp(-1, dp(3), Gravity.BOTTOM, 0, 0, 0, 0));
        }

        View shine = shine();
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));
        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text(11, "sans-serif-medium", 0xCCFFFFFF);
        label.setText(m.subtitle);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        box.addView(label, llp(-1, -2, 0, dp(10), 0, 0));

        card.setOnClickListener(v -> m.open.run());
        card.setOnLongClickListener(v -> { showMediaMenu(v, m); return true; });
        card.setOnFocusChangeListener((v, has) -> {
            animateFocus(v, shine, label, has, 1.1f);
            if (!has) return;
            lastFocusedId = v.getId();
            fadeGlow(0.35f);
            title.animate().alpha(0f).setDuration(200).start();
            scrollToRow(box);
            // The background follows the focused card once you settle on it.
            if (backdropOf(m) != null) {
                pendingBackdropUri = backdropOf(m);
                handler.removeCallbacks(pendingBackdrop);
                handler.postDelayed(pendingBackdrop, 350);
            }
        });

        r.cards.add(card);
        return box;
    }

    /** Decode art off the main thread at about {@code want} pixels wide; logos keep their alpha. */
    private void loadArt(String uri, ImageView img, int want, boolean alpha, Runnable onShown) {
        img.setTag(uri);
        if (TextUtils.isEmpty(uri)) return;
        String key = uri + "@" + want;
        Bitmap cached = artCache.get(key);
        if (cached != null) {
            img.setImageBitmap(cached);
            if (onShown != null) onShown.run();
            return;
        }
        try {
            io.execute(() -> {
                Bitmap bm = decodeArt(uri, want, alpha);
                runOnUiThread(() -> {
                    if (bm == null || isDestroyed()) return;
                    artCache.put(key, bm);
                    if (!uri.equals(img.getTag())) return; // the view moved on to other art
                    img.setImageBitmap(bm);
                    img.setAlpha(0f);
                    img.animate().alpha(1f).setDuration(250).start();
                    if (onShown != null) onShown.run();
                });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private Bitmap decodeArt(String uri, int want, boolean alpha) {
        byte[] data = readUri(uri, 8 * 1024 * 1024);
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= want) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    /** http(s) over the network; content:// and android.resource:// from the app that owns it. */
    private byte[] readUri(String uri, int max) {
        HttpURLConnection http = null;
        try {
            InputStream in;
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                http = (HttpURLConnection) new URL(uri).openConnection();
                http.setConnectTimeout(8000);
                http.setReadTimeout(8000);
                in = http.getInputStream();
            } else {
                in = getContentResolver().openInputStream(Uri.parse(uri));
            }
            if (in == null) return null;
            try (InputStream s = in) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                for (int n; (n = s.read(chunk)) != -1; ) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > max) return null;
                }
                return buf.toByteArray();
            }
        } catch (Exception e) { // IO, bad URI, or the app's provider refusing us
            return null;
        } finally {
            if (http != null) http.disconnect();
        }
    }

    /** Explicit D-pad targets: one row per press, ends of rows and of the top bar don't wrap. */
    private void wireFocus() {
        int d = dockCards.size(), g = gridCards.size();
        List<List<View>> rows = new ArrayList<>();
        for (MediaRow r : mediaRows) if (!r.cards.isEmpty()) rows.add(r.cards);
        int topId = pills.get(0).getId();
        boolean heroShown = featuredBox.getVisibility() == View.VISIBLE;
        boolean npShown = npRow.getVisibility() == View.VISIBLE;
        // Top to bottom: the top bar, now playing, the hero's Open button, the dock.
        int dockMid = d > 0 ? dockCards.get(Math.min(d - 1, d / 2)).getId() : View.NO_ID;
        int belowNp = heroShown ? featuredButton.getId() : dockMid;
        int belowTop = npShown ? npInfo.getId() : belowNp;
        int aboveHero = npShown ? npInfo.getId() : topId;
        int aboveDock = heroShown ? featuredButton.getId() : aboveHero;
        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(belowTop == View.NO_ID ? p.getId() : belowTop);
        }
        for (int i = 0; i < npViews.size(); i++) {
            View p = npViews.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : npViews.get(i - 1).getId());
            p.setNextFocusRightId(i == npViews.size() - 1 ? p.getId() : npViews.get(i + 1).getId());
            p.setNextFocusUpId(pills.get(pills.size() - 1).getId());
            p.setNextFocusDownId(belowNp == View.NO_ID ? p.getId() : belowNp);
        }
        if (heroShown) {
            featuredButton.setNextFocusUpId(aboveHero);
            featuredButton.setNextFocusDownId(d > 0 ? dockCards.get(0).getId() : featuredButton.getId());
        }
        int firstGridRow = Math.min(cols, g);
        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(aboveDock);
            c.setNextFocusLeftId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            c.setNextFocusRightId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
            c.setNextFocusDownId(!rows.isEmpty() ? at(rows.get(0), i)
                    : g > 0 ? gridCards.get(Math.min(i, firstGridRow - 1)).getId() : c.getId());
        }
        for (int k = 0; k < rows.size(); k++) {
            List<View> row = rows.get(k);
            for (int i = 0; i < row.size(); i++) {
                View c = row.get(i);
                c.setNextFocusUpId(k > 0 ? at(rows.get(k - 1), i) : d > 0 ? at(dockCards, i) : aboveDock);
                c.setNextFocusDownId(k < rows.size() - 1 ? at(rows.get(k + 1), i)
                        : g > 0 ? gridCards.get(Math.min(i, firstGridRow - 1)).getId() : c.getId());
                c.setNextFocusLeftId(i == 0 ? c.getId() : row.get(i - 1).getId());
                c.setNextFocusRightId(i == row.size() - 1 ? c.getId() : row.get(i + 1).getId());
            }
        }
        int lastRow = (g - 1) / cols;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / cols, col = j % cols;
            c.setNextFocusUpId(r == 0
                    ? (!rows.isEmpty() ? at(rows.get(rows.size() - 1), col) : d > 0 ? at(dockCards, col) : aboveDock)
                    : gridCards.get(j - cols).getId());
            c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId()
                    : r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusLeftId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusRightId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    private static int at(List<View> row, int i) {
        return row.get(Math.min(i, row.size() - 1)).getId();
    }

    private void scrollToRow(View tileBox) {
        View row = (View) tileBox.getParent();
        // Media cards sit in a sideways scroller; grid tiles in a plain row inside the grid.
        View anchor = row.getParent() instanceof HorizontalScrollView ? (View) row.getParent() : row;
        int y = anchor == row ? grid.getTop() + row.getTop() : anchor.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        // First row under the dock: slide the dock band up to the top edge; later rows centre on screen.
        View band = (View) dockRow.getParent();
        int target = anchor == firstRowUnderDock()
                ? hero.getTop() + band.getTop() + dockRow.getTop() - dp(22)
                : y - (int) (screenH * 0.42f);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(target, max)));
    }

    private View firstRowUnderDock() {
        for (MediaRow r : mediaRows) if (!r.cards.isEmpty()) return r.scroller;
        return grid.getChildCount() > 0 ? grid.getChildAt(0) : null;
    }

    // ---------------------------------------------------------------- long-press menus and settings

    private void showAppMenu(View anchor, App app, boolean inDock) {
        List<String> dock = new ArrayList<>();
        for (View c : dockCards) dock.add(((App) c.getTag()).pkg);
        int at = dock.indexOf(app.pkg);
        List<TextView> rows = new ArrayList<>();
        rows.add(menuRow("Open", anchor::performClick));
        if (inDock) {
            if (at > 0) rows.add(menuRow("Move left", () -> moveInDock(dock, at, at - 1)));
            if (at >= 0 && at < dock.size() - 1) rows.add(menuRow("Move right", () -> moveInDock(dock, at, at + 1)));
            rows.add(menuRow("Remove from dock", () -> { dock.remove(app.pkg); prefs.setDock(dock); buildApps(); }));
        } else {
            rows.add(menuRow("Add to dock", () -> {
                dock.add(app.pkg);
                prefs.setDock(dock);
                focusPkg = app.pkg;
                buildApps();
            }));
        }
        rows.add(menuRow(app.inputId != null ? "Hide input" : "Hide app", () -> {
            dock.remove(app.pkg);
            if (inDock) prefs.setDock(dock);
            prefs.setAppHidden(app.pkg, true);
            buildApps();
        }));
        if (app.inputId != null) {
            rows.add(menuRow("Banner: default", () -> setInputArt(app, "")));
            rows.add(menuRow("Banner: built-in photo…", () -> showPhotoChoices(anchor, app)));
            rows.add(menuRow("Banner: picture on the TV…", () -> showPictureChoices(anchor, app)));
        } else {
            rows.add(menuRow("App info", () -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.pkg)))));
        }
        showMenu(anchor, rows, dp(260), null);
    }

    private void moveInDock(List<String> dock, int from, int to) {
        String pkg = dock.remove(from);
        dock.add(to, pkg);
        prefs.setDock(dock);
        focusPkg = pkg;
        buildApps();
    }

    private void showMediaMenu(View anchor, Media m) {
        List<TextView> rows = new ArrayList<>();
        rows.add(menuRow(m.action, m.open));
        if (m.resumable && m.type != null && m.id != null) rows.add(menuRow("Details in " + openInLabel, () -> openInApp(m.type, m.id)));
        if (m.trailer != null) rows.add(menuRow("Watch trailer", () -> openTrailer(m.trailer)));
        if (m.type != null && m.id != null && isInstalled(STREMIO)) {
            rows.add(menuRow("Open in Stremio", () -> launch(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("stremio:///detail/" + m.type + "/" + m.id)).setPackage(STREMIO))));
        }
        if (m.resumable) {
            rows.add(menuRow("Open " + appLabel(m.pkg), () -> openPackage(m.pkg)));
            rows.add(menuRow("Remove from Continue watching", () -> {
                prefs.hideProgram(m.key);
                loadContinue();
            }));
        }
        showMenu(anchor, rows, dp(260), null);
    }

    private void openTrailer(String youtubeId) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(youtubeId)));
        if (isInstalled(YOUTUBE)) i.setPackage(YOUTUBE);
        if (!launch(i)) launch(i.setPackage(null));
    }

    private String appLabel(String pkg) {
        if (pkg.startsWith("input:")) {
            TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            TvInputInfo t = tim == null ? null : tim.getTvInputInfo(pkg.substring(6));
            return t == null ? "Input" : inputLabel(t);
        }
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void showSettingsMenu(View anchor) {
        List<TextView> rows = new ArrayList<>();
        rows.add(menuRow("TV settings", () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        rows.add(menuRow("Home screen…", () -> showHomeSettings(anchor)));
        rows.add(menuLabel("Power"));
        rows.add(menuRow("Restart…", () -> power(PowerService.GLOBAL_ACTION_POWER_DIALOG)));
        rows.add(menuRow("Turn off (standby)", () -> power(PowerService.GLOBAL_ACTION_LOCK_SCREEN)));
        showMenu(anchor, rows, dp(220), null);
    }

    /** Through the accessibility service; without it, send you to switch it on. */
    private void power(int action) {
        PowerService service = PowerService.running;
        if (service != null && service.performGlobalAction(action)) return;
        launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    /** Hero, rows and hidden apps; the home screen rebuilds when the panel closes. */
    private void showHomeSettings(View anchor) {
        settingsChanged = false;
        List<TextView> rows = new ArrayList<>();
        rows.add(menuLabel("Home screen"));
        rows.add(menuToggle("Picks in the hero", prefs.flag("hero", true), on -> prefs.setFlag("hero", on)));
        rows.add(menuToggle("Your shows in the hero", prefs.flag("hero_shows", true), on -> prefs.setFlag("hero_shows", on)));
        rows.add(menuToggle("Continue watching", prefs.flag("continue", true), on -> prefs.setFlag("continue", on)));
        if (!allCatalogs.isEmpty()) {
            rows.add(menuLabel("Rows"));
            Set<String> on = prefs.catalogs(defaultCatalogs);
            for (CatalogSpec c : allCatalogs.values()) {
                rows.add(menuToggle(c.name, on.contains(c.key()), enable -> {
                    Set<String> now = prefs.catalogs(defaultCatalogs);
                    if (enable) now.add(c.key()); else now.remove(c.key());
                    prefs.setCatalogs(now);
                }));
            }
        }
        Set<String> hiddenApps = prefs.hiddenApps();
        if (!hiddenApps.isEmpty()) {
            rows.add(menuLabel("Hidden apps"));
            for (String pkg : hiddenApps) {
                rows.add(menuRow("Show " + appLabel(pkg), () -> { prefs.setAppHidden(pkg, false); buildApps(); }));
            }
        }
        rows.add(menuLabel("Background"));
        rows.addAll(menuChoices("background", background, new String[][]{
                {"titles", "Title art"}, {"photos", "Built-in photos"},
                {"bing", "Bing photo of the day"}, {"picsum", "Random photos (Picsum)"}}));
        rows.add(menuLabel("Dock"));
        rows.addAll(menuChoices("dock_align", dockAlign, new String[][]{
                {"left", "Left"}, {"center", "Centre"}, {"right", "Right"}}));
        rows.add(menuRow("Reset dock apps to default", () -> { prefs.resetDock(); buildApps(); }));
        showMenu(anchor, rows, dp(300), () -> { if (settingsChanged) recreate(); });
    }

    // ---------------------------------------------------------------- now playing

    private void watchSessions() {
        sessionManager = getSystemService(MediaSessionManager.class);
        ComponentName listener = new ComponentName(this, NowPlayingListener.class);
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            pickSession(sessionManager.getActiveSessions(listener));
        } catch (SecurityException e) { // notification access not granted: no now playing row
            setPlaying(null);
        }
    }

    private void unwatchSessions() {
        try {
            if (sessionManager != null) sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (RuntimeException ignored) { }
        setPlaying(null);
    }

    /** Follow whichever app is playing; a paused one stays until another starts. */
    private void pickSession(List<MediaController> sessions) {
        if (sessions == null) {
            try {
                sessions = sessionManager.getActiveSessions(new ComponentName(this, NowPlayingListener.class));
            } catch (RuntimeException e) {
                sessions = Collections.emptyList();
            }
        }
        MediaController pick = null;
        for (MediaController c : sessions) {
            if (c.getPackageName().equals(getPackageName()) || c.getMetadata() == null) continue;
            if (isPlaying(c)) { pick = c; break; }
            PlaybackState st = c.getPlaybackState();
            if (pick == null && st != null && st.getState() == PlaybackState.STATE_PAUSED) pick = c;
        }
        setPlaying(pick);
    }

    private void setPlaying(MediaController c) {
        boolean same = c != null && playing != null && c.getSessionToken().equals(playing.getSessionToken());
        if (!same) {
            if (playing != null) playing.unregisterCallback(playingCallback);
            playing = c;
            if (playing != null) playing.registerCallback(playingCallback, handler);
        }
        showNowPlaying();
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && (st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void showNowPlaying() {
        MediaMetadata md = playing == null ? null : playing.getMetadata();
        String name = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_TITLE),
                md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        boolean show = name != null;
        int vis = show ? View.VISIBLE : View.GONE;
        if (npRow.getVisibility() != vis) {
            if (!show && npViews.contains(getCurrentFocus())) pills.get(pills.size() - 1).requestFocus();
            npRow.setVisibility(vis);
            wireFocus();
        }
        if (!show) return;
        String artist = first(md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST), md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        npInfo.setText(artist == null ? name : name + "  ·  " + artist);
        npPlay.setText(isPlaying(playing) ? "❚❚" : "▶");
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (art != null) {
            BitmapDrawable d = new BitmapDrawable(getResources(), art);
            d.setBounds(0, 0, dp(20), dp(20));
            npInfo.setCompoundDrawables(d, null, null, null);
        } else {
            npInfo.setCompoundDrawables(null, null, null, null);
            npInfo.setText("♪  " + npInfo.getText());
        }
    }

    private static String first(String... values) {
        for (String v : values) if (!TextUtils.isEmpty(v)) return v;
        return null;
    }

    private void openPlaying() {
        if (playing == null) return;
        PendingIntent pi = playing.getSessionActivity();
        try {
            if (pi != null) { pi.send(); return; }
        } catch (PendingIntent.CanceledException ignored) { }
        openPackage(playing.getPackageName());
    }

    // ---------------------------------------------------------------- inputs

    private void showInputs(View anchor) {
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
            if (t.isPassthroughInput() && !t.isHidden(this)) inputs.add(t);
        }
        Collections.sort(inputs, (x, y) -> inputLabel(x).compareToIgnoreCase(inputLabel(y)));
        for (TvInputInfo t : inputs) {
            rows.add(menuRow(inputLabel(t), () -> openInput(t.getId())));
        }
        if (isInstalled(liveTvPkg)) rows.add(menuRow("Live TV", () -> openPackage(liveTvPkg)));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg)) rows.add(menuRow("All sources…", () -> openPackage(inputPickerPkg)));

        if (rows.isEmpty()) rows.add(menuRow("Settings", () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        showMenu(anchor, rows, dp(200), null);
    }

    private void openInput(String inputId) {
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!launch(i)) openPackage(inputPickerPkg);
    }

    private void rebuildSoon() {
        handler.removeCallbacks(rebuildApps);
        handler.postDelayed(rebuildApps, 400);
    }

    private void setInputArt(App app, String art) {
        prefs.setChoice("input_art:" + app.inputId, art);
        focusPkg = app.pkg;
        buildApps();
    }

    private void showPhotoChoices(View anchor, App app) {
        List<TextView> rows = new ArrayList<>();
        for (int i = 0; i < wallpapers.length; i++) {
            String name = "wallpapers/" + wallpapers[i];
            rows.add(menuRow("Photo " + (i + 1), () -> setInputArt(app, "asset:" + name)));
        }
        showMenu(anchor, rows, dp(220), null);
    }

    /** The newest pictures on the TV (LocalSend, USB copies, screenshots), from the media store. */
    private void showPictureChoices(View anchor, App app) {
        List<TextView> rows = new ArrayList<>();
        if (checkSelfPermission(READ_PICTURES) != PackageManager.PERMISSION_GRANTED) {
            rows.add(menuRow("Allow access to pictures", () -> requestPermissions(new String[]{READ_PICTURES}, 2)));
        } else {
            String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
            try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                while (c != null && c.moveToNext() && rows.size() < 30) {
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(0)));
                    rows.add(menuRow(c.getString(1), () -> setInputArt(app, uri.toString())));
                }
            } catch (RuntimeException ignored) { }
            if (rows.isEmpty()) rows.add(menuLabel("No pictures yet. Send one with LocalSend, then try again."));
        }
        showMenu(anchor, rows, dp(380), null);
    }

    /** Gradient start and end for an input's designed banner, by connector type. */
    private static int[] inputColors(int type) {
        switch (type) {
            case TvInputInfo.TYPE_HDMI: return new int[]{0xFF241A55, 0xFF6C3FD1};
            case TvInputInfo.TYPE_COMPOSITE: case TvInputInfo.TYPE_SVIDEO: case TvInputInfo.TYPE_SCART:
                return new int[]{0xFF0B3F3D, 0xFF1C9C8F};
            case TvInputInfo.TYPE_COMPONENT: return new int[]{0xFF4A2209, 0xFFD9822B};
            default: return new int[]{0xFF1E2430, 0xFF4B5A70};
        }
    }

    /** An input's tile: your picture (or a designed gradient with the connector), its name, and status. */
    private Drawable inputBanner(TvInputInfo t, String label, boolean connected) {
        int w = dp(320), h = w * 9 / 16;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap photo = inputArt(prefs.choice("input_art:" + t.getId(), ""), w);
        if (photo != null) {
            float scale = Math.max((float) w / photo.getWidth(), (float) h / photo.getHeight());
            float dw = photo.getWidth() * scale, dh = photo.getHeight() * scale;
            c.drawBitmap(photo, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
            paint.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
        } else {
            int[] col = inputColors(t.getType());
            paint.setShader(new LinearGradient(0, 0, w, h, col[0], col[1], Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            drawConnector(c, t.getType(), w, h);
        }
        paint.setShader(null);
        float pad = w * 0.07f;
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        paint.setTextSize(h * 0.2f);
        String name = TextUtils.ellipsize(label, new android.text.TextPaint(paint), w - pad * 2, TextUtils.TruncateAt.END).toString();
        c.drawText(name, pad, h - pad, paint);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(h * 0.085f);
        paint.setLetterSpacing(0.18f);
        paint.setColor(connected ? 0xFFB9F6CA : 0xB3FFFFFF);
        c.drawText(connected ? "● CONNECTED" : "INPUT", pad, h - pad - h * 0.24f, paint);
        return new BitmapDrawable(getResources(), bm);
    }

    /** A large faint outline of the port: HDMI's trapezoid, AV's three jacks, or a plain socket. */
    private void drawConnector(Canvas c, int type, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.035f);
        p.setColor(0x38FFFFFF);
        p.setStrokeJoin(Paint.Join.ROUND);
        float cx = w * 0.7f, cy = h * 0.38f, s = h * 0.42f;
        if (type == TvInputInfo.TYPE_HDMI) {
            Path port = new Path();
            port.moveTo(cx - s * 0.9f, cy - s * 0.32f);
            port.lineTo(cx + s * 0.9f, cy - s * 0.32f);
            port.lineTo(cx + s * 0.9f, cy + s * 0.02f);
            port.lineTo(cx + s * 0.62f, cy + s * 0.32f);
            port.lineTo(cx - s * 0.62f, cy + s * 0.32f);
            port.lineTo(cx - s * 0.9f, cy + s * 0.02f);
            port.close();
            c.drawPath(port, p);
            c.drawLine(cx - s * 0.55f, cy - s * 0.02f, cx + s * 0.55f, cy - s * 0.02f, p);
        } else if (type == TvInputInfo.TYPE_COMPOSITE || type == TvInputInfo.TYPE_COMPONENT) {
            for (int i = -1; i <= 1; i++) {
                c.drawCircle(cx + i * s * 0.62f, cy, s * 0.24f, p);
                c.drawCircle(cx + i * s * 0.62f, cy, s * 0.07f, p);
            }
        } else {
            c.drawRoundRect(new RectF(cx - s * 0.8f, cy - s * 0.35f, cx + s * 0.8f, cy + s * 0.35f), s * 0.12f, s * 0.12f, p);
        }
    }

    /** A chosen banner picture: "asset:<path>" (a built-in photo) or a content:// picture. */
    private Bitmap inputArt(String art, int want) {
        if (art.isEmpty()) return null;
        byte[] data;
        if (art.startsWith("asset:")) {
            try (InputStream in = getAssets().open(art.substring(6))) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                for (int n; (n = in.read(chunk)) != -1; ) buf.write(chunk, 0, n);
                data = buf.toByteArray();
            } catch (java.io.IOException e) {
                return null;
            }
        } else {
            data = readUri(art, 32 * 1024 * 1024);
        }
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= want) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    /** A rounded list under {@code anchor}: rows run their action and close it; toggles flip in place. */
    private void showMenu(View anchor, List<TextView> rows, int width, Runnable onDismiss) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF02A2A2E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        list.setBackground(bg);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);

        PopupWindow pw = new PopupWindow(sv, width, -2, true);
        List<TextView> nav = new ArrayList<>();
        for (TextView r : rows) {
            if (r.getTag() instanceof Toggle) {
                r.setOnClickListener(v -> ((Toggle) v.getTag()).flip((TextView) v));
            } else if (r.getTag() instanceof Runnable) {
                r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            }
            if (r.isFocusable()) nav.add(r);
            list.addView(r, new LinearLayout.LayoutParams(-1, r.isFocusable() ? dp(36) : -2));
        }
        for (int i = 0; i < nav.size(); i++) {
            TextView r = nav.get(i);
            r.setNextFocusUpId(nav.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(nav.get(Math.min(nav.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        // Long lists scroll inside the popup instead of running off the screen.
        sv.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int h = Math.min(sv.getMeasuredHeight(), screenH - dp(48));
        pw.setHeight(h);
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setOnDismissListener(() -> {
            inputsPopup = null;
            anchor.requestFocus();
            if (onDismiss != null) onDismiss.run();
        });
        inputsPopup = pw;
        // Under the item when it fits, otherwise above it (cards near the bottom of the screen).
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int below = at[1] + anchor.getHeight() + dp(8);
        int y = below + h <= screenH - dp(24) ? below : Math.max(dp(24), at[1] - dp(8) - h);
        int x = Math.min(at[0], getResources().getDisplayMetrics().widthPixels - width - dp(24));
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        if (!nav.isEmpty()) nav.get(0).requestFocus();
    }

    /** A settings row that flips in place; off keeps an invisible tick so labels stay aligned. */
    static final class Toggle {
        final String label;
        boolean on;
        final Consumer<Boolean> set;
        Runnable changed;
        List<TextView> group; // set for one-of-several choices: picking one clears the others

        Toggle(String label, boolean on, Consumer<Boolean> set) { this.label = label; this.on = on; this.set = set; }

        CharSequence text() {
            SpannableString s = new SpannableString("✓   " + label);
            if (!on) s.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return s;
        }

        void flip(TextView v) {
            if (group != null) {
                if (on) return;
                for (TextView g : group) {
                    Toggle o = (Toggle) g.getTag();
                    o.on = false;
                    g.setText(o.text());
                }
            }
            on = !on;
            v.setText(text());
            set.accept(on);
            if (changed != null) changed.run();
        }
    }

    private TextView menuToggle(String label, boolean on, Consumer<Boolean> set) {
        Toggle t = new Toggle(label, on, set);
        t.changed = () -> settingsChanged = true;
        TextView r = menuRow(label, null);
        r.setText(t.text());
        r.setTag(t);
        return r;
    }

    /** One-of-several settings rows, stored under {@code key} as the chosen option's value. */
    private List<TextView> menuChoices(String key, String current, String[][] options) {
        List<TextView> rows = new ArrayList<>();
        for (String[] o : options) {
            rows.add(menuToggle(o[1], o[0].equals(current), on -> { if (on) prefs.setChoice(key, o[0]); }));
        }
        for (TextView r : rows) ((Toggle) r.getTag()).group = rows;
        return rows;
    }

    private TextView menuLabel(String label) {
        TextView t = text(9, "sans-serif-medium", 0x80FFFFFF);
        t.setText(label.toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(14), dp(10), dp(14), dp(4));
        return t;
    }

    private TextView menuRow(String label, Runnable action) {
        TextView r = text(13, "sans-serif", Color.WHITE);
        r.setText(label);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.TRANSPARENT);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : Color.TRANSPARENT);
            r.setTextColor(has ? 0xFF111111 : Color.WHITE);
        });
        return r;
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "GOOD NIGHT" : h < 12 ? "GOOD MORNING" : h < 17 ? "GOOD AFTERNOON" : "GOOD EVENING");
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
        date.setText(new SimpleDateFormat("EEEE  d MMMM", Locale.getDefault()).format(now.getTime()).toUpperCase(Locale.getDefault()));
    }

    /** The pill names the active connection: Ethernet, the Wi-Fi network, or Offline. */
    private void updateWifi() {
        String name = "Offline";
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            name = "Ethernet";
        } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            name = "Wi-Fi";
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                String s = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) name = s.replace("\"", "");
            } catch (SecurityException ignored) { }
        } else if (caps != null) {
            name = "Online";
        }
        wifiPill.setText("◉  " + name);
    }

    // ---------------------------------------------------------------- glow

    private void setGlow(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        // The glow sits behind the dock, wherever it is.
        g.setGradientCenter("left".equals(dockAlign) ? 0.28f : "right".equals(dockAlign) ? 0.72f : 0.5f, 0.78f);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        g.setColors(new int[]{withAlpha(color, 0.5f), withAlpha(color, 0.14f), Color.TRANSPARENT});
        glow.setBackground(g);
    }

    private void animateGlow(int to) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void fadeGlow(float alpha) {
        // Keep the app-coloured glow faint over film backdrops, so it doesn't tint them.
        glow.animate().alpha(featured.isEmpty() ? alpha : alpha * 0.35f).setDuration(300).start();
        // The grid sits over the photo's busiest middle part, so dim it while browsing apps.
        gridShade.animate().alpha(alpha < 1f ? 1f : 0f).setDuration(300).start();
    }

    // ---------------------------------------------------------------- wallpaper

    private ImageView wallpaperLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    /** Title backdrops behind the hero (the default), or a photo source from Settings. */
    private boolean titleBackdrops() {
        return "titles".equals(background);
    }

    /** Next pick in the hero; it holds while you're on its button or browsing cards. */
    private void nextHero() {
        handler.removeCallbacks(rotateHero);
        if (featured.isEmpty()) return;
        if (!featuredButton.hasFocus() && !mediaCardFocused()) showFeatured(featuredIndex + 1);
        scheduleHero(FEATURED_INTERVAL_MS);
    }

    private void scheduleHero(long delay) {
        handler.removeCallbacks(rotateHero);
        handler.postDelayed(rotateHero, Math.max(1000, delay));
    }

    /** Next background photo: built-in, Bing's photos of the day, or a random one from Picsum. */
    private void nextWallpaper() {
        handler.removeCallbacks(rotateWallpaper);
        if (titleBackdrops() && !featured.isEmpty()) return; // the hero's backdrops are the background
        backdropShown = null;
        wallChangedAt = SystemClock.elapsedRealtime();
        if ("bing".equals(background) || "picsum".equals(background)) nextOnlinePhoto();
        else nextBuiltInPhoto();
        scheduleWallpaper(WALLPAPER_INTERVAL_MS);
    }

    /** Decode the next built-in photo off the main thread, then crossfade it in. */
    private void nextBuiltInPhoto() {
        if (wallpapers.length == 0) return;
        if (wallIndex < 0) wallIndex = getPreferences(MODE_PRIVATE).getInt("wallpaper", -1);
        wallIndex = (wallIndex + 1) % wallpapers.length;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", wallIndex).apply();
        String name = "wallpapers/" + wallpapers[wallIndex];
        new Thread(() -> {
            Bitmap bm = null;
            try (java.io.InputStream in = getAssets().open(name)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeStream(in, null, o);
            } catch (java.io.IOException ignored) { }
            Bitmap result = bm;
            runOnUiThread(() -> {
                if (!(titleBackdrops() && !featured.isEmpty())) showWallpaper(result, firstWallpaper ? 600 : 2000);
            });
        }).start();
    }

    /** Fetch a photo from the chosen service; offline, fall back to the built-in ones. */
    private void nextOnlinePhoto() {
        int want = getResources().getDisplayMetrics().widthPixels;
        int turn = wallIndex = wallIndex + 1;
        try {
            io.execute(() -> {
                String url = "picsum".equals(background)
                        ? "https://picsum.photos/1920/1080?random=" + SystemClock.elapsedRealtime()
                        : bingPhoto(turn);
                Bitmap bm = url == null ? null : decodeArt(url, want, false);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (bm != null) showWallpaper(bm, firstWallpaper ? 600 : 2000);
                    else nextBuiltInPhoto();
                });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    /** Bing's last eight photos of the day (1920×1080), refreshed twice a day. */
    private String bingPhoto(int turn) {
        synchronized (bingPhotos) {
            if (bingPhotos.isEmpty() || SystemClock.elapsedRealtime() - bingPhotosAt > 12 * 60 * 60 * 1000) {
                byte[] data = readUri("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8", 1024 * 1024);
                if (data != null) try {
                    JSONArray images = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONArray("images");
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; images != null && i < images.length(); i++) {
                        String u = images.optJSONObject(i) == null ? null : str(images.optJSONObject(i), "url");
                        if (u != null) urls.add(u.startsWith("http") ? u : "https://www.bing.com" + u);
                    }
                    if (!urls.isEmpty()) { bingPhotos.clear(); bingPhotos.addAll(urls); bingPhotosAt = SystemClock.elapsedRealtime(); }
                } catch (JSONException ignored) { }
            }
            return bingPhotos.isEmpty() ? null : bingPhotos.get(Math.floorMod(turn, bingPhotos.size()));
        }
    }

    private void showWallpaper(Bitmap bm, long fadeMs) {
        if (bm == null || isDestroyed()) return;
        // Settle a fade still in progress, so its end action can't blank the layer we fade over.
        wallFront.animate().cancel();
        wallFront.setAlpha(1f);
        // Swap roles: the old front becomes the back, the new photo fades in on top.
        ImageView old = wallFront;
        wallFront = wallBack;
        wallBack = old;
        ViewGroup root = (ViewGroup) wallFront.getParent();
        root.removeView(wallFront);
        root.addView(wallFront, 1, new FrameLayout.LayoutParams(-1, -1));
        wallFront.setImageBitmap(bm);
        wallFront.setAlpha(0f);
        wallFront.animate().alpha(1f).setDuration(fadeMs).withEndAction(() -> {
            wallBack.setImageDrawable(null); // release the previous photo
        }).start();
        firstWallpaper = false;
    }

    private boolean firstWallpaper = true;

    private void scheduleWallpaper(long delay) {
        handler.removeCallbacks(rotateWallpaper);
        handler.postDelayed(rotateWallpaper, Math.max(1000, delay));
    }

    /** Average the banner's vivid pixels, then push saturation so the glow reads as a colour. */
    private int sampleColor(String pkg, Drawable d) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        int color = 0xFF3A3A3A;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.75f); hsv[2] = 0.85f; }
                else { hsv[1] = 0f; hsv[2] = 0.45f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        colorCache.put(pkg, color);
        return color;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        View header = hero.getChildAt(0);
        header.setAlpha(0f);
        header.setTranslationY(-dp(12));
        header.animate().alpha(1f).translationY(0).setDuration(500).setStartDelay(80).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setDuration(500).setStartDelay(200).start();
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View t = dockRow.getChildAt(i);
            t.setAlpha(0f);
            t.setTranslationY(dp(24));
            t.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(450).setStartDelay(160 + i * 60L).start();
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean launch(Intent i) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Menu key on the remote opens the Inputs dropdown.
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !pills.isEmpty()) {
            // On a card or tile it opens that item's options, like a long press; elsewhere Inputs.
            View f = getCurrentFocus();
            if (f != null && f.isLongClickable()) f.performLongClick();
            else showInputs(inputsPill);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** 16:9 container. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** ScrollView that never auto-scrolls to the focused child; the activity scrolls it by row. */
    static final class HomeScrollView extends android.widget.ScrollView {
        HomeScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }
}
