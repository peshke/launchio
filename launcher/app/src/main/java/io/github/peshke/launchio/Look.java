package io.github.peshke.launchio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launchio's look, following Nuvio's design language: its theme palettes (accent, focus ring,
 * focused-surface tint, the dark surfaces) and its fonts. The values match Nuvio's design tokens;
 * the code is Launchio's own.
 *
 * <p>The colours reach the layouts and drawables as theme attributes (attrs_look.xml): each palette
 * and font is a style in themes_look.xml that {@link #apply} lays over the activity's theme before
 * its views are inflated. The palettes are repeated here for what is drawn in code (the focus ring's
 * gradient, the dock band); LookTest checks the two copies agree.
 */
final class Look {

    static final String THEME_KEY = "look_theme";
    static final String FONT_KEY = "look_font";

    /** One theme: what Nuvio calls secondary (the accent), text on it, its focus ring, and the surfaces. */
    static final class Palette {
        final String key, name;
        final int accent, onAccent, ring, focusBg, background, raised, card, menu;
        /** The Supporter themes draw their focus ring as a gradient; null for a plain ring. */
        final int[] ringGradient;
        /** Default only: CleanYourTV's bright glass highlight instead of Nuvio's thin ring. */
        boolean glass;

        Palette(String name, int accent, int onAccent, int ring, int[] ringGradient, int focusBg,
                int background, int raised, int card, int menu) {
            this.key = key(name);
            this.name = name;
            this.accent = accent;
            this.onAccent = onAccent;
            this.ring = ring;
            this.ringGradient = ringGradient;
            this.focusBg = focusBg;
            this.background = background;
            this.raised = raised;
            this.card = card;
            this.menu = menu;
        }

        /** The Look.Palette.* style that carries this palette, e.g. Look.Palette.RoseGold. */
        String styleName() {
            return "Look.Palette." + name.replace(" ", "");
        }
    }

    static final int WHITE = 0xFFFFFFFF, INK = 0xFF111111;

    /** Default (the original look: black, the bright glass highlight, white buttons), then Nuvio's themes in its order. */
    static final Map<String, Palette> THEMES = new LinkedHashMap<>();

    static {
        Palette original = new Palette("Default", 0xF5FFFFFF, INK, 0xCCFFFFFF, null, 0x5574A987,
                0xFF000000, 0xFF141416, 0xFF1C1C1E, 0xFF2A2A2E);
        original.glass = true;
        add(original);
        add(new Palette("White", 0xFFF5F5F5, INK, WHITE, null, 0xFF303030, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF222222, 0xFF1E1E1E));
        add(new Palette("Crimson", 0xFFE53935, WHITE, 0xFFFF5252, null, 0xFF3D1A1A, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF241A1A, 0xFF1E1E1E));
        add(new Palette("Ocean", 0xFF1E88E5, WHITE, 0xFF42A5F5, null, 0xFF1A2D3D, 0xFF0D0D0F, 0xFF1A1A1E, 0xFF1A1F24, 0xFF1E1E1E));
        add(new Palette("Violet", 0xFF8E24AA, WHITE, 0xFFAB47BC, null, 0xFF2D1A3D, 0xFF0D0D0F, 0xFF1A1A1E, 0xFF1F1A24, 0xFF1E1E1E));
        add(new Palette("Emerald", 0xFF43A047, WHITE, 0xFF66BB6A, null, 0xFF1A3D1E, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF1A241A, 0xFF1E1E1E));
        add(new Palette("Amber", 0xFFFB8C00, WHITE, 0xFFFFA726, null, 0xFF3D2D1A, 0xFF0F0D0D, 0xFF1E1A1A, 0xFF24201A, 0xFF1E1E1E));
        add(new Palette("Rose", 0xFFD81B60, WHITE, 0xFFEC407A, null, 0xFF3D1A2D, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF241A1F, 0xFF1E1E1E));
        add(new Palette("Gold", 0xFFE8A91C, INK, 0xFFFFD45C,
                new int[]{0xFF8A5700, 0xFFE8A91C, 0xFFFFF1A8, 0xFFFFD45C, 0xFF9A6200},
                0xFF3D2D1A, 0xFF0F0E0B, 0xFF1D1A14, 0xFF262116, 0xFF211D16));
        add(new Palette("Jade", 0xFF22D37C, INK, 0xFF7BF08D, new int[]{0xFF7BF08D, 0xFF22D37C, 0xFF0BBF9A},
                0xFF153A2C, 0xFF0B0F0D, 0xFF141D18, 0xFF16251D, 0xFF17221C));
        add(new Palette("Rose Gold", 0xFFEC70A9, INK, 0xFFFFB37A, new int[]{0xFFB75AFF, 0xFFEC70A9, 0xFFFFB37A},
                0xFF442037, 0xFF100C0F, 0xFF1F161D, 0xFF281A24, 0xFF241921));
        add(new Palette("Arctic Blue", 0xFF3185F5, WHITE, 0xFF4DE3FF, new int[]{0xFF4DE3FF, 0xFF3185F5, 0xFF4D55E8},
                0xFF172844, 0xFF0B0E14, 0xFF141A24, 0xFF161E2A, 0xFF171F2C));
        add(new Palette("Graphite", 0xFFAAB2BE, INK, 0xFFF3F5F7, new int[]{0xFFF3F5F7, 0xFFAAB2BE, 0xFF687381},
                0xFF30343A, 0xFF0C0D0F, 0xFF17191D, 0xFF20242A, 0xFF1C1F23));
    }

    private static void add(Palette p) {
        THEMES.put(p.key, p);
    }

    static String key(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    static Palette theme(String key) {
        Palette p = key == null ? null : THEMES.get(key);
        return p != null ? p : THEMES.get("default");
    }

    /** Fonts as {key, name shown in Settings, Look.Font.* style}. Inter is Nuvio's default; Outfit is Launchio's wordmark. */
    static final String[][] FONTS = {
            {"inter", "Inter", "Look.Font.Inter"},
            {"dm_sans", "DM Sans", "Look.Font.DmSans"},
            {"outfit", "Outfit", "Look.Font.Outfit"},
    };

    static String[] font(String key) {
        for (String[] f : FONTS) if (f[0].equals(key)) return f;
        return FONTS[0];
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static Palette current(Context ctx) {
        return theme(prefs(ctx).getString(THEME_KEY, null));
    }

    static String[] currentFont(Context ctx) {
        return font(prefs(ctx).getString(FONT_KEY, null));
    }

    /**
     * Lays the chosen palette and font over the activity's theme. Call it before setContentView: views read
     * the theme when they are inflated. Both styles set every attribute, so this also works for activities
     * whose manifest theme is a plain Android one.
     */
    static void apply(Activity activity) {
        apply(activity, activity.getTheme());
    }

    /**
     * A context with Launchio's theme and the chosen look, for views a service inflates (the screensaver):
     * a service's own theme is the system default, which has none of Launchio's attributes.
     */
    static Context themed(Context ctx) {
        ContextThemeWrapper wrapped = new ContextThemeWrapper(ctx, R.style.Theme_TvHome);
        apply(ctx, wrapped.getTheme());
        return wrapped;
    }

    private static void apply(Context ctx, Resources.Theme theme) {
        int palette = style(ctx, current(ctx).styleName());
        int font = style(ctx, currentFont(ctx)[2]);
        if (palette != 0) theme.applyStyle(palette, true);
        if (font != 0) theme.applyStyle(font, true);
    }

    /** Each look style by name, so the compiler checks every one exists (LookTest checks the names match). */
    private static Map<String, Integer> styleIds;

    static synchronized Map<String, Integer> styleIds() {
        if (styleIds == null) {
            Map<String, Integer> m = new HashMap<>();
            m.put("Look.Palette.Default", R.style.Look_Palette_Default);
            m.put("Look.Palette.White", R.style.Look_Palette_White);
            m.put("Look.Palette.Crimson", R.style.Look_Palette_Crimson);
            m.put("Look.Palette.Ocean", R.style.Look_Palette_Ocean);
            m.put("Look.Palette.Violet", R.style.Look_Palette_Violet);
            m.put("Look.Palette.Emerald", R.style.Look_Palette_Emerald);
            m.put("Look.Palette.Amber", R.style.Look_Palette_Amber);
            m.put("Look.Palette.Rose", R.style.Look_Palette_Rose);
            m.put("Look.Palette.Gold", R.style.Look_Palette_Gold);
            m.put("Look.Palette.Jade", R.style.Look_Palette_Jade);
            m.put("Look.Palette.RoseGold", R.style.Look_Palette_RoseGold);
            m.put("Look.Palette.ArcticBlue", R.style.Look_Palette_ArcticBlue);
            m.put("Look.Palette.Graphite", R.style.Look_Palette_Graphite);
            m.put("Look.Font.Inter", R.style.Look_Font_Inter);
            m.put("Look.Font.DmSans", R.style.Look_Font_DmSans);
            m.put("Look.Font.Outfit", R.style.Look_Font_Outfit);
            styleIds = m;
        }
        return styleIds;
    }

    private static int style(Context ctx, String name) {
        Integer id = styleIds().get(name);
        return id != null ? id : 0;
    }

    /** The theme's font for one weight attribute (R.attr.fontMedium and so on), for text built in code. */
    static Typeface typeface(Context ctx, int attr) {
        TypedValue v = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attr, v, true) && v.resourceId != 0) {
            try {
                return ctx.getResources().getFont(v.resourceId);
            } catch (Resources.NotFoundException ignored) {
                // fall through to the system font
            }
        }
        return Typeface.DEFAULT;
    }
}
