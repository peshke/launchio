"""Launchio brand assets: icon, TV banner and a one-page brand sheet.

    python -I brand.py <res dir> <out dir>

Drawn at 4x and scaled down for smooth edges. Wordmark in Nunito Black (SIL Open Font License).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "Nunito.ttf")
VIOLET, PINK = (123, 77, 255), (255, 79, 154)
NIGHT, DUSK = (16, 12, 34), (44, 20, 82)
IO_PINK = (255, 122, 184)
SS = 4  # supersampling


def font(px, weight=900):
    f = ImageFont.truetype(FONT, px)
    try:
        f.set_variation_by_axes([weight])
    except (OSError, ValueError):
        pass
    return f


def gradient(w, h, a, b, diagonal=True):
    """Top-left a to bottom-right b (or top to bottom)."""
    g = Image.new("RGB", (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = ((x / max(1, w - 1)) + (y / max(1, h - 1))) / 2 if diagonal else y / max(1, h - 1)
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return g


def mark(size):
    """Gradient squircle with a white house arch whose door is a play button."""
    S = size * SS
    u = S / 100.0
    tile = gradient(S // 8, S // 8, VIOLET, PINK).resize((S, S), Image.BICUBIC).convert("RGBA")
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(23 * u), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(tile, (0, 0), mask)
    d = ImageDraw.Draw(out)
    # House arch: roof peak, walls, floor; round joins via dots at the corners.
    pts = [(25 * u, 49 * u), (50 * u, 26 * u), (75 * u, 49 * u), (75 * u, 75 * u), (25 * u, 75 * u)]
    stroke = int(7.5 * u)
    d.line(pts + [pts[0]], fill="white", width=stroke, joint="curve")
    for x, y in pts:
        d.ellipse([x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2], fill="white")
    # Play button as the door.
    d.polygon([(44 * u, 51 * u), (44 * u, 67 * u), (59 * u, 59 * u)], fill="white")
    return out.resize((size, size), Image.LANCZOS)


def wordmark(px, dark_bg=True):
    """'launch' plus 'io' in pink (the io it shares with Nuvio)."""
    f = font(px * SS)
    launch, io = "launch", "io"
    w1 = f.getlength(launch)
    w2 = f.getlength(io)
    asc, desc = f.getmetrics()
    W, H = int(w1 + w2) + 4 * SS, asc + desc
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.text((0, 0), launch, font=f, fill=(255, 255, 255) if dark_bg else NIGHT)
    d.text((w1, 0), io, font=f, fill=IO_PINK if dark_bg else PINK)
    return img.resize((W // SS, H // SS), Image.LANCZOS)


def banner(w=640, h=360):
    """The 16:9 TV banner: dusk gradient, the mark, the wordmark."""
    bg = gradient(w // 4, h // 4, NIGHT, DUSK).resize((w, h), Image.BICUBIC).convert("RGBA")
    m = mark(int(h * 0.42))
    wm = wordmark(int(h * 0.25))
    gap = int(h * 0.07)
    total = m.width + gap + wm.width
    x = (w - total) // 2
    bg.alpha_composite(m, (x, (h - m.height) // 2))
    bg.alpha_composite(wm, (x + m.width + gap, (h - wm.height) // 2 - int(h * 0.02)))
    return bg


def sheet(w=1600, h=1000):
    s = Image.new("RGBA", (w, h), (247, 245, 252, 255))
    d = ImageDraw.Draw(s)
    title = font(54)
    label = font(22, 700)
    body = font(24, 500)
    d.text((80, 64), "Launchio", font=title, fill=NIGHT)
    d.text((80, 134), "A home screen for Android TV that plays nicely with Nuvio.", font=body, fill=(90, 84, 110))
    # Icon at three sizes.
    d.text((80, 214), "ICON", font=label, fill=(120, 112, 140))
    x = 80
    for size in (220, 120, 64):
        s.alpha_composite(mark(size), (x, 254 + (220 - size)))
        x += size + 40
    # Banner.
    d.text((620, 214), "TV BANNER", font=label, fill=(120, 112, 140))
    b = banner(800, 450)
    bm = Image.new("L", b.size, 0)
    ImageDraw.Draw(bm).rounded_rectangle([0, 0, b.width - 1, b.height - 1], radius=28, fill=255)
    s.paste(b, (620, 254), bm)
    # Wordmark on dark and light.
    d.text((80, 760), "WORDMARK", font=label, fill=(120, 112, 140))
    dark = Image.new("RGBA", (440, 140), NIGHT + (255,))
    wm = wordmark(64)
    dark.alpha_composite(wm, ((440 - wm.width) // 2, (140 - wm.height) // 2))
    dm = Image.new("L", dark.size, 0)
    ImageDraw.Draw(dm).rounded_rectangle([0, 0, 439, 139], radius=22, fill=255)
    s.paste(dark, (80, 800), dm)
    wl = wordmark(64, dark_bg=False)
    s.alpha_composite(wl, (560 + (440 - wl.width) // 2, 800 + (140 - wl.height) // 2))
    # Colours.
    d.text((1080, 760), "COLOURS", font=label, fill=(120, 112, 140))
    for i, (name, c) in enumerate([("Violet", VIOLET), ("Pink", PINK), ("Night", NIGHT), ("io pink", IO_PINK)]):
        cx = 1080 + i * 120
        d.rounded_rectangle([cx, 800, cx + 96, 896], radius=18, fill=c)
        d.text((cx, 906), name, font=font(18, 700), fill=NIGHT)
        d.text((cx, 930), "#%02X%02X%02X" % c, font=font(16, 500), fill=(110, 104, 130))
    d.text((80, 958), "Wordmark: Nunito Black (SIL Open Font License).", font=font(16, 500), fill=(140, 132, 160))
    return s


def main():
    res, out = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(res, "drawable-xhdpi"), exist_ok=True)
    os.makedirs(os.path.join(res, "drawable-xxxhdpi"), exist_ok=True)
    os.makedirs(out, exist_ok=True)
    banner(640, 360).convert("RGB").save(os.path.join(res, "drawable-xhdpi", "launchio_banner.png"), optimize=True)
    mark(192).save(os.path.join(res, "drawable-xxxhdpi", "launchio_icon.png"), optimize=True)
    sheet().convert("RGB").save(os.path.join(out, "launchio-brand.png"), optimize=True)
    mark(512).save(os.path.join(out, "launchio-icon-512.png"), optimize=True)
    banner(1280, 720).convert("RGB").save(os.path.join(out, "launchio-banner-1280.png"), optimize=True)
    print("ok")


if __name__ == "__main__":
    main()
