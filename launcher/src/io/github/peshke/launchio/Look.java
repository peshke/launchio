package io.github.peshke.launchio;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launchio's look, following Nuvio's design language: its theme palettes (accent, focus ring,
 * focused-surface tint, the dark surfaces), Inter / DM Sans, and its corner and focus sizes.
 * The values match Nuvio's design tokens; the code here is Launchio's own.
 */
final class Look {

    /** One theme: what Nuvio calls secondary (the accent), its focus ring, and the surfaces. */
    static final class Palette {
        final String name;
        final int accent, onAccent, ring, focusBg, background, raised, card, menu;
        final int[] ringGradient; // the Supporter themes draw their ring as a gradient

        Palette(String name, int accent, int onAccent, int ring, int[] ringGradient, int focusBg,
                int background, int raised, int card, int menu) {
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
    }

    static final int WHITE = 0xFFFFFFFF, INK = 0xFF111111;
    static final int TEXT_SECONDARY = 0xB3FFFFFF, TEXT_TERTIARY = 0x80FFFFFF;
    static final int RATING = 0xFFFFD700, SUCCESS = 0xFF4CAF50;
    /** Nuvio's glass panel, top to bottom; used lighter here so the backdrop shows through. */
    static final int[] GLASS = {0x664A4F59, 0x593F454F, 0x5240474F};

    // Corners and focus, in dp: backdrop cards 16, menus 14, buttons 12, panels 28; 2dp focus border.
    static final int CARD_RADIUS = 16, MENU_RADIUS = 14, BUTTON_RADIUS = 12, PANEL_RADIUS = 28, RING = 2;
    static final float FOCUS_SCALE = 1.04f, DOCK_FOCUS_SCALE = 1.06f;

    /** Nuvio's themes, in its own order; "white" is its default. */
    static final Map<String, Palette> THEMES = new LinkedHashMap<>();

    static {
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

    private static void add(Palette p) { THEMES.put(key(p.name), p); }

    static String key(String name) { return name.toLowerCase().replace(' ', '_'); }

    static Palette theme(String key) {
        Palette p = THEMES.get(key);
        return p != null ? p : THEMES.get("white");
    }

    // ---- fonts: Inter (Nuvio's default) or DM Sans, variable weight

    static final String[][] FONTS = {{"inter", "Inter"}, {"dm_sans", "DM Sans"}};
    private static final Map<String, Typeface> faces = new HashMap<>();

    static Typeface font(AssetManager assets, String font, int weight) {
        String file = "dm_sans".equals(font) ? "fonts/DMSans.ttf" : "fonts/Inter.ttf";
        String k = file + weight;
        Typeface t = faces.get(k);
        if (t == null) {
            try {
                t = new Typeface.Builder(assets, file).setFontVariationSettings("'wght' " + weight).build();
            } catch (RuntimeException e) {
                t = null;
            }
            if (t == null) t = Typeface.create(Typeface.SANS_SERIF, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            faces.put(k, t);
        }
        return t;
    }

    /** The weight for the old family names the layout code still passes around. */
    static int weight(String family) {
        switch (family) {
            case "sans-serif-thin": return 250;
            case "sans-serif-light": return 350;
            case "sans-serif-medium": return 500;
            case "sans-serif-semibold": return 600;
            case "sans-serif-bold": return 700;
            default: return 400;
        }
    }

    /** A rounded focus ring, solid or in the theme's gradient. */
    static final class Ring extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float radius, width;
        private final int[] colors;
        private final int color;

        Ring(Palette p, float radius, float width) {
            this.radius = radius;
            this.width = width;
            this.colors = p.ringGradient;
            this.color = p.ring;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
        }

        @Override protected void onBoundsChange(Rect b) {
            box.set(b.left + width / 2, b.top + width / 2, b.right - width / 2, b.bottom - width / 2);
            if (colors != null) paint.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, colors, null, Shader.TileMode.CLAMP));
            else paint.setColor(color);
        }

        @Override public void draw(Canvas c) { c.drawRoundRect(box, radius, radius, paint); }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
