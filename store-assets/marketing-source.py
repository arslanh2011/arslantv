"""Store graphics for Arslan TV, built around 'Pakistan Live TV Channels Free'."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icon import backdrop, mark, GREEN_HI, GREEN_LO
from PIL import Image, ImageDraw, ImageFont, ImageFilter

F_BOLD  = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
F_REG   = "/System/Library/Fonts/Supplemental/Arial.ttf"
F_BLACK = "/System/Library/Fonts/Supplemental/Arial Black.ttf"
ACCENT  = (229, 9, 20)
WHITE   = (255, 255, 255)
MUTED   = (196, 214, 203)

def font(path, size):
    return ImageFont.truetype(path, size)

def text_w(d, s, f):
    return d.textbbox((0, 0), s, font=f)[2]

def rounded(img, radius):
    m = Image.new("L", (img.width * 2, img.height * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, img.width * 2 - 1, img.height * 2 - 1], radius=radius * 2, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), m.resize(img.size, Image.LANCZOS))
    return out

def plate(w, h):
    """Dark green marketing ground."""
    base = backdrop(max(w, h)).resize((w, h)).convert("RGBA")
    dark = Image.new("RGBA", (w, h), (0, 0, 0, 90))
    base.alpha_composite(dark)
    return base

# ---------- feature graphic ----------
def feature_graphic():
    W, H = 1024, 500
    MARGIN = 40
    img = plate(W, H)
    d = ImageDraw.Draw(img)
    img.alpha_composite(mark(272), (44, 114))

    x = 346
    avail = W - x - MARGIN

    # Shrink the headline until the widest line fits, rather than trusting a
    # hand-picked size that overflows the moment the wording changes.
    size = 62
    while size > 28:
        f = font(F_BLACK, size)
        l1 = text_w(d, "PAKISTAN LIVE TV", f)
        l2 = text_w(d, "CHANNELS", f) + 18 + text_w(d, "FREE", f) + 34
        if max(l1, l2) <= avail:
            break
        size -= 2
    fh = font(F_BLACK, size)
    gap = int(size * 1.18)

    y1 = 124
    d.text((x, y1), "PAKISTAN LIVE TV", font=fh, fill=WHITE)
    y2 = y1 + gap
    d.text((x, y2), "CHANNELS", font=fh, fill=WHITE)
    cw = text_w(d, "CHANNELS", fh)
    fw = text_w(d, "FREE", fh)
    pad_x, pad_y = 17, int(size * 0.13)
    chip = [x + cw + 18, y2 - pad_y,
            x + cw + 18 + fw + pad_x * 2, y2 + size + pad_y]
    d.rounded_rectangle(chip, radius=12, fill=ACCENT)
    d.text((chip[0] + pad_x, y2), "FREE", font=fh, fill=WHITE)

    fs = font(F_REG, 25)
    sub1 = "News  ·  Drama  ·  Cartoons  ·  Documentary"
    sub2 = "Built for Android TV. Works on your phone."
    ys = y2 + gap + 26
    d.text((x, ys), sub1, font=fs, fill=MUTED)
    d.text((x, ys + 40), sub2, font=fs, fill=MUTED)
    return img.convert("RGB")

# ---------- captioned phone screenshot ----------
def phone_shot(src_path, line1, line2):
    W, H = 1080, 1920
    img = plate(W, H)
    d = ImageDraw.Draw(img)
    f1 = font(F_BLACK, 58)
    f2 = font(F_REG, 32)
    d.text(((W - text_w(d, line1, f1)) // 2, 86), line1, font=f1, fill=WHITE)
    d.text(((W - text_w(d, line2, f2)) // 2, 164), line2, font=f2, fill=MUTED)

    shot = Image.open(src_path).convert("RGBA")
    avail_h = H - 300 - 70
    scale = avail_h / shot.height
    shot = shot.resize((int(shot.width * scale), avail_h), Image.LANCZOS)
    shot = rounded(shot, 26)
    sx = (W - shot.width) // 2
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (sx + 8, 308), shot.split()[3])
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(14)))
    img.alpha_composite(shot, (sx, 300))
    return img.convert("RGB")

# ---------- captioned TV screenshot ----------
def tv_shot(src_path, line1):
    W, H = 1920, 1080
    img = plate(W, H)
    d = ImageDraw.Draw(img)
    f1 = font(F_BLACK, 50)
    d.text(((W - text_w(d, line1, f1)) // 2, 44), line1, font=f1, fill=WHITE)

    shot = Image.open(src_path).convert("RGBA")
    scale = (W - 180) / shot.width
    shot = shot.resize((W - 180, int(shot.height * scale)), Image.LANCZOS)
    shot = rounded(shot, 20)
    sx, sy = 90, 140
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 160), (sx + 8, sy + 10), shot.split()[3])
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(16)))
    img.alpha_composite(shot, (sx, sy))
    return img.convert("RGB")
