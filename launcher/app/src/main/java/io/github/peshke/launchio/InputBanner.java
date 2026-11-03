package io.github.peshke.launchio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

/** A dock tile's picture for a TV input: the theme's colours, the input's icon and its name. */
final class InputBanner extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private final String name;
    private final int top, bottom;

    InputBanner(Context ctx, String name, boolean hdmi) {
        this.name = name;
        Look.Palette look = Look.current(ctx);
        // From the accent towards the theme's card colour, so each theme gets its own input tiles.
        top = blend(look.accent, look.card, 0.55f);
        bottom = look.card;
        icon = ctx.getDrawable(hdmi ? R.drawable.ic_input : R.drawable.ic_av).mutate();
        icon.setTint(0xFFFFFFFF);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Look.typeface(ctx, R.attr.fontMedium));
        text.setTextAlign(Paint.Align.CENTER);
    }

    static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override public int getIntrinsicWidth() { return 320; }

    @Override public int getIntrinsicHeight() { return 180; }

    @Override
    protected void onBoundsChange(Rect b) {
        fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, top, bottom, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, fill);
        int h = b.height(), size = Math.round(h * 0.26f);
        int cx = b.centerX(), iconTop = b.top + Math.round(h * 0.2f);
        icon.setBounds(cx - size / 2, iconTop, cx + size / 2, iconTop + size);
        icon.draw(c);
        text.setTextSize(h * 0.16f);
        CharSequence shown = TextUtils.ellipsize(name, text, b.width() * 0.86f, TextUtils.TruncateAt.END);
        c.drawText(shown, 0, shown.length(), cx, b.top + h * 0.78f, text);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); text.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
