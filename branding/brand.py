"""Launchio's branding, in Nuvio's design language (ribbon gradients, nested shapes, night glow).

    python -I brand.py concepts                              the concepts sheet, per-concept icon/banner
    python -I brand.py . --apply home ../launcher/res        the chosen concept as the app's assets

Each concept is a sibling of Nuvio's mark, not a copy: same palette and layering, its own shape.
Wordmark in Outfit (SIL Open Font License).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUTFIT = os.path.join(HERE, "Outfit.ttf")
SS = 4

# Nuvio's mark, sampled: cyan top, royal blue left, violet bottom, a pink tip; navy night behind.
CYAN, BLUE, VIOLET, PINK = (79, 227, 240), (45, 91, 214), (164, 75, 240), (232, 96, 200)
NAVY, GLOW = (12, 15, 29), (52, 28, 86)


def font(px, weight=700):
    f = ImageFont.truetype(OUTFIT, px)
    try:
        f.set_variation_by_axes([weight])
    except (OSError, ValueError):
        pass
    return f


def ribbon(S):
    """Inverse-distance blend of the four anchors, plus a dark fold low-left and a light one top-right."""
    y, x = np.mgrid[0:S, 0:S] / float(S)
    anchors = [((0.82, 0.12), CYAN), ((0.08, 0.42), BLUE), ((0.55, 0.95), VIOLET), ((1.0, 0.62), PINK)]
    acc, wsum = np.zeros((S, S, 3)), np.zeros((S, S))
    for (ax, ay), c in anchors:
        w = 1.0 / (((x - ax) ** 2 + (y - ay) ** 2) + 0.02) ** 1.6
        acc += w[..., None] * np.array(c, float)
        wsum += w
    rgb = acc / wsum[..., None]
    def band(cx, cy, r, soft=0.035):  # 1 inside the circle, easing to 0 across its edge
        return np.clip((r - np.hypot(x - cx, y - cy)) / soft + 0.5, 0, 1)[..., None]
    dark = band(0.08, 1.12, 0.74)  # the inner fold, low left
    rgb = rgb * (1 - 0.24 * dark) + np.array(NAVY, float) * 0.24 * dark
    light = band(1.08, -0.28, 0.66)  # the outer fold, top right
    rgb = rgb * (1 - 0.16 * light) + 255 * 0.16 * light
    return Image.fromarray(rgb.astype(np.uint8), "RGB")


def rounded_poly(S, pts, r):
    """Mask of a convex polygon (unit coords) with corners rounded by r: shrink, then grow by a disk."""
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    inner = []
    for px, py in pts:
        d = math.hypot(px - cx, py - cy)
        k = max(0.0, (d - r * 1.15) / d)
        inner.append(((cx + (px - cx) * k) * S, (cy + (py - cy) * k) * S))
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    d.polygon(inner, fill=255)
    R = r * S
    d.line(inner + [inner[0]], fill=255, width=int(2 * R))
    for x, y in inner:
        d.ellipse([x - R, y - R, x + R, y + R], fill=255)
    return m


def rotate(pts, deg, c=(0.5, 0.5)):
    a = math.radians(deg)
    return [(c[0] + (x - c[0]) * math.cos(a) - (y - c[1]) * math.sin(a),
             c[1] + (x - c[0]) * math.sin(a) + (y - c[1]) * math.cos(a)) for x, y in pts]


def scale(pts, k, c):
    return [(c[0] + (x - c[0]) * k, c[1] + (y - c[1]) * k) for x, y in pts]


def fill(canvas, mask, color_or_img):
    layer = color_or_img if isinstance(color_or_img, Image.Image) else Image.new("RGB", canvas.size, color_or_img)
    canvas.paste(layer.convert("RGBA"), (0, 0), mask)


def rim(canvas, mask, alpha=70):
    """Thin light edge, like Nuvio's mark."""
    edge = mask.filter(ImageFilter.MaxFilter(5)).point(lambda v: 255 if v else 0)
    inner = mask.filter(ImageFilter.MinFilter(9))
    ring = Image.fromarray(np.clip(np.array(edge, int) - np.array(inner, int), 0, 255).astype(np.uint8))
    ring = ring.point(lambda v: v * alpha // 255)
    canvas.paste(Image.new("RGBA", canvas.size, (255, 255, 255, 255)), (0, 0), ring)


TRI = [(0.2, 0.12), (0.88, 0.5), (0.2, 0.88)]


def mark_home(S):
    """A: the ribbon as a house, with Nuvio's nested cut-out and white play button as the door."""
    c = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    house = [(0.5, 0.1), (0.9, 0.44), (0.9, 0.9), (0.1, 0.9), (0.1, 0.44)]
    m = rounded_poly(S, house, 0.1)
    fill(c, m, ribbon(S))
    rim(c, m)
    door = scale(house, 0.47, (0.5, 0.64))
    fill(c, rounded_poly(S, door, 0.07), NAVY)
    play = scale(TRI, 0.2, (0.53, 0.67))
    fill(c, rounded_poly(S, play, 0.025), (255, 255, 255))
    return c


def mark_launch(S):
    """B: the play triangle taking off up-right, three trailing bars (the dock) behind it."""
    c = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = ribbon(S)
    for y, w, a in [(0.36, 0.2, 150), (0.5, 0.3, 235), (0.64, 0.2, 150)]:
        bar = Image.new("L", (S, S), 0)
        ImageDraw.Draw(bar).rounded_rectangle([(0.36 - w) * S, (y - 0.04) * S, 0.36 * S, (y + 0.04) * S],
                                              radius=0.04 * S, fill=a)
        c.paste(grad.convert("RGBA"), (0, 0), bar)
    tri = [(x + 0.12, y) for x, y in scale(TRI, 0.78, (0.5, 0.5))]
    m = rounded_poly(S, tri, 0.09)
    fill(c, m, grad)
    rim(c, m)
    inner = scale(tri, 0.42, (sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3))
    fill(c, rounded_poly(S, inner, 0.04), NAVY)
    return c


def mark_grid(S):
    """C: a launcher's grid of tiles in the ribbon, the last tile turned into the play button."""
    c = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    g, t, o = 0.06, 0.37, 0.1
    for col, row in [(0, 0), (1, 0), (0, 1)]:
        x0, y0 = o + col * (t + g), o + row * (t + g)
        d.rounded_rectangle([x0 * S, y0 * S, (x0 + t) * S, (y0 + t) * S], radius=0.1 * S, fill=255)
    fill(c, m, ribbon(S))
    rim(c, m)
    x0, y0 = o + t + g, o + t + g
    play = [(x0 + 0.04, y0 + 0.02), (x0 + t, y0 + t / 2), (x0 + 0.04, y0 + t - 0.02)]
    fill(c, rounded_poly(S, play, 0.05), (255, 255, 255))
    return c


CONCEPTS = [
    ("home", "A · Home ribbon", "Nuvio's ribbon, shaped as a house; the play button is the door.", mark_home),
    ("launch", "B · Launch", "The play button launching forward, speed lines (the dock) behind it.", mark_launch),
    ("grid", "C · Tile grid", "A launcher's app grid in the ribbon; the last tile plays.", mark_grid),
]


def mark(fn, size):
    return fn(size * SS).resize((size, size), Image.LANCZOS)


def night(w, h):
    """Nuvio's banner night: navy, with a violet glow rising from the bottom."""
    y, x = np.mgrid[0:h, 0:w] / float(h)
    xc = x - (w / h) / 2
    glow = np.clip(1 - np.hypot(xc * 0.7, (y - 1.25)) / 1.05, 0, 1) ** 1.6
    rgb = np.array(NAVY, float)[None, None, :] * (1 - glow[..., None]) + np.array(GLOW, float)[None, None, :] * glow[..., None]
    return Image.fromarray(rgb.astype(np.uint8), "RGB").convert("RGBA")


def wordmark(px):
    f = font(px * SS, 700)
    text = "launchio"
    tracking = int(px * SS * 0.02)
    widths = [f.getlength(ch) for ch in text]
    asc, desc = f.getmetrics()
    img = Image.new("RGBA", (int(sum(widths) + tracking * len(text)) + SS * 4, asc + desc), (0, 0, 0, 0))
    d, x = ImageDraw.Draw(img), 0
    for ch, w in zip(text, widths):
        d.text((x, 0), ch, font=f, fill=(255, 255, 255))
        x += w + tracking
    return img.resize((img.width // SS, img.height // SS), Image.LANCZOS)


def banner(fn, w=640, h=360):
    bg = night(w, h)
    m = mark(fn, int(h * 0.36))
    wm = wordmark(int(h * 0.2))
    gap = int(h * 0.06)
    x = (w - (m.width + gap + wm.width)) // 2
    bg.alpha_composite(m, (x, (h - m.height) // 2))
    bg.alpha_composite(wm, (x + m.width + gap, (h - wm.height) // 2 - int(h * 0.02)))
    return bg


def icon(fn, size=192):
    """Launcher icon: the mark on a navy squircle with a little of the glow."""
    bg = night(size, size)
    bg = Image.blend(bg, Image.new("RGBA", bg.size, (32, 36, 64, 255)), 0.45)
    msk = Image.new("L", (size, size), 0)
    ImageDraw.Draw(msk).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    m = mark(fn, int(size * 0.7))
    bg.alpha_composite(m, ((size - m.width) // 2, (size - m.height) // 2))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, (0, 0), msk)
    return out


def sheet(out):
    W, H = 1800, 1120
    s = night(W, H)
    d = ImageDraw.Draw(s)
    d.text((90, 70), "Launchio — brand concepts", font=font(52, 700), fill=(255, 255, 255))
    d.text((90, 140), "In Nuvio's design language: ribbon gradient, nested shapes, navy night with a violet glow.",
           font=font(24, 400), fill=(180, 186, 210))
    col_w = (W - 180) // 3
    for i, (key, name, blurb, fn) in enumerate(CONCEPTS):
        x0 = 90 + i * col_w
        d.text((x0, 230), name, font=font(30, 600), fill=(255, 255, 255))
        s.alpha_composite(icon(fn, 210), (x0, 290))
        s.alpha_composite(mark(fn, 110), (x0 + 250, 340))
        b = banner(fn, col_w - 40, int((col_w - 40) * 9 / 16))
        bm = Image.new("L", b.size, 0)
        ImageDraw.Draw(bm).rounded_rectangle([0, 0, b.width - 1, b.height - 1], radius=24, fill=255)
        s.paste(b, (x0, 540), bm)
        import textwrap
        for k, line in enumerate(textwrap.wrap(blurb, 46)):
            d.text((x0, 540 + b.height + 24 + k * 30), line, font=font(20, 400), fill=(170, 176, 200))
        icon(fn, 192).save(os.path.join(out, f"concept-{key}-icon.png"))
        banner(fn).convert("RGB").save(os.path.join(out, f"concept-{key}-banner.png"))
    d.text((90, H - 60), "Wordmark: Outfit Bold (SIL Open Font License). Colours sampled from Nuvio's mark.",
           font=font(18, 400), fill=(130, 136, 160))
    s.convert("RGB").save(os.path.join(out, "launchio-concepts.png"), optimize=True)


def apply(key, res, out):
    """The chosen concept becomes the app's icon and TV banner, plus large copies for the README."""
    fn = {k: f for k, _, _, f in CONCEPTS}[key]
    for d in ("drawable-xhdpi", "drawable-xxxhdpi"):
        os.makedirs(os.path.join(res, d), exist_ok=True)
    banner(fn, 640, 360).convert("RGB").save(os.path.join(res, "drawable-xhdpi", "launchio_banner.png"), optimize=True)
    icon(fn, 192).save(os.path.join(res, "drawable-xxxhdpi", "launchio_icon.png"), optimize=True)
    icon(fn, 512).save(os.path.join(out, "launchio-icon-512.png"), optimize=True)
    mark(fn, 512).save(os.path.join(out, "launchio-mark-512.png"), optimize=True)
    banner(fn, 1280, 720).convert("RGB").save(os.path.join(out, "launchio-banner-1280.png"), optimize=True)


if __name__ == "__main__":
    # brand.py <out>                         the concepts sheet and each concept's icon and banner
    # brand.py <out> --apply <key> <res>     make concept <key> (home | launch | grid) the app's branding
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    if len(sys.argv) > 4 and sys.argv[2] == "--apply":
        apply(sys.argv[3], sys.argv[4], out)
    else:
        sheet(out)
    print("ok")
