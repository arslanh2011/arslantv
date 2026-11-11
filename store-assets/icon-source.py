"""Arslan TV icon set.

The mark is a cable-TV set reading as Pakistani: a bright-green cabinet, a
white screen, and the flag's crescent and star sitting in the screen where a
picture would be, with two aerials above. Flag green (#01411C) is near-black
at icon size, so the cabinet uses a lifted green and keeps the flag's green
only in the crescent.
"""
from PIL import Image, ImageDraw
import math, os

GREEN_HI   = (14, 122,  66)
GREEN_LO   = (3,   70,  38)
FLAG_GREEN = (1,   65,  28)
WHITE      = (255, 255, 255)

SS = 4  # supersample factor; everything is drawn big and resampled down


def _star(cx, cy, r, rot=-math.pi / 2):
    pts = []
    for i in range(10):
        rad = r if i % 2 == 0 else r * 0.42
        a = rot + i * math.pi / 5
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    return pts


def _crescent(size, cx, cy, r, colour):
    """Outer disc minus an offset disc, so the horns face right."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour)
    cut = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dc = ImageDraw.Draw(cut)
    ox, orr = cx + r * 0.40, r * 0.86
    dc.ellipse([ox - orr, cy - orr, ox + orr, cy + orr], fill=(0, 0, 0, 255))
    layer.paste((0, 0, 0, 0), (0, 0), cut)
    return layer


def mark(size, scale=1.0, cabinet=True, mono=False):
    """The TV mark on a transparent canvas. `scale` shrinks it into a safe zone."""
    S = size * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    w = S * 0.74 * scale            # cabinet width
    h = w * 0.70                    # cabinet height
    top = c - h * 0.38              # sits low, leaving room for the aerials
    box = [c - w / 2, top, c + w / 2, top + h]
    ink = WHITE if mono else None

    # Aerials
    aw = max(2, int(S * 0.016 * scale))
    ay = top - h * 0.34
    d.line([c - w * 0.03, top + h * 0.05, c - w * 0.30, ay], fill=ink or WHITE, width=aw)
    d.line([c + w * 0.03, top + h * 0.05, c + w * 0.30, ay], fill=ink or WHITE, width=aw)
    for sx in (-1, 1):
        kr = aw * 1.5
        kx, ky = c + sx * w * 0.30, ay
        d.ellipse([kx - kr, ky - kr, kx + kr, ky + kr], fill=ink or WHITE)

    radius = h * 0.17
    if cabinet and not mono:
        d.rounded_rectangle(box, radius=radius, fill=WHITE)
        inset = h * 0.10
        screen = [box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset]
        d.rounded_rectangle(screen, radius=radius * 0.62, fill=GREEN_HI)
        fg = WHITE
    else:
        # Monochrome / themed: outline only, so the system can tint it.
        d.rounded_rectangle(box, radius=radius, outline=WHITE, width=int(S * 0.022 * scale))
        screen = [box[0], box[1], box[2], box[3]]
        fg = WHITE

    # Crescent and star inside the screen
    sw = screen[2] - screen[0]
    sh = screen[3] - screen[1]
    scx = screen[0] + sw * 0.52
    scy = screen[1] + sh * 0.50
    r = sh * 0.33
    img.alpha_composite(_crescent(S, scx - sw * 0.04, scy, r, fg))
    d.polygon(_star(scx + sw * 0.20, scy - sh * 0.10, r * 0.42), fill=fg)

    # Feet
    fy = box[3]
    fwid = w * 0.12
    fh = h * 0.07
    for sx in (-1, 1):
        fx = c + sx * w * 0.26
        d.rounded_rectangle([fx - fwid / 2, fy, fx + fwid / 2, fy + fh],
                            radius=fh * 0.4, fill=ink or WHITE)
    return img.resize((size, size), Image.LANCZOS)


def backdrop(size, rounded=False, radius_frac=0.22):
    """Green gradient plate used behind the mark."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        grad.putpixel((0, y), tuple(
            int(GREEN_HI[i] + (GREEN_LO[i] - GREEN_HI[i]) * t) for i in range(3)) + (255,))
    grad = grad.resize((size, size))
    if rounded:
        mask = Image.new("L", (size * SS, size * SS), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, size * SS - 1, size * SS - 1],
            radius=int(size * SS * radius_frac), fill=255)
        img.paste(grad, (0, 0), mask.resize((size, size), Image.LANCZOS))
    else:
        img.paste(grad, (0, 0))
    return img


def composed(size, rounded=False, scale=0.74):
    img = backdrop(size, rounded=rounded)
    img.alpha_composite(mark(size, scale=scale))
    return img
