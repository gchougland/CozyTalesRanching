"""Draws the house page's small icons (hearts, mood faces) at 1x and 2x. Run once; output is committed."""
import math
import sys
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
SS = 8  # supersampling


def heart_poly(cx, cy, s):
    pts = []
    for i in range(240):
        t = 2 * math.pi * i / 240
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s, cy - y * s))
    return pts


def heart(size, full):
    W = size * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = W / 36.0
    cx, cy = W / 2, W / 2 + W * 0.03
    if full:
        d.polygon(heart_poly(cx, cy, s), fill=(122, 34, 58, 255))
        d.polygon(heart_poly(cx, cy - s * 0.4, s * 0.86), fill=(232, 92, 120, 255))
        d.polygon(heart_poly(cx, cy - s * 1.4, s * 0.55), fill=(244, 132, 152, 255))
        d.ellipse((cx - s * 9.5, cy - s * 9.5, cx - s * 4.5, cy - s * 5.5), fill=(255, 214, 222, 255))
    else:
        d.polygon(heart_poly(cx, cy, s), fill=(132, 98, 112, 255))
        d.polygon(heart_poly(cx, cy - s * 0.3, s * 0.78), fill=(58, 44, 58, 255))
        d.ellipse((cx - s * 9.5, cy - s * 9.5, cx - s * 4.5, cy - s * 5.5), fill=(96, 74, 90, 255))
    return img.resize((size, size), Image.LANCZOS)


def face(size, mood):
    W = size * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pal = {
        "happy": ((150, 92, 20), (250, 196, 72), (255, 226, 140)),
        "okay": ((136, 104, 52), (232, 200, 132), (248, 228, 178)),
        "sad": ((70, 88, 120), (150, 176, 214), (196, 214, 238)),
    }[mood]
    m = W * 0.04
    d.ellipse((m, m, W - m, W - m), fill=pal[0])
    m2 = W * 0.10
    d.ellipse((m2, m2, W - m2, W - m2), fill=pal[1])
    d.ellipse((W * 0.24, W * 0.17, W * 0.50, W * 0.36), fill=pal[2])
    ink = (66, 40, 30) if mood != "sad" else (40, 52, 80)
    er = W * 0.065
    for ex in (0.36, 0.64):
        d.ellipse((W * ex - er, W * 0.42 - er * 1.3, W * ex + er, W * 0.42 + er * 1.3), fill=ink)
    lw = int(W * 0.07)
    if mood == "happy":
        d.chord((W * 0.28, W * 0.40, W * 0.72, W * 0.80), 0, 180, fill=ink)
        d.ellipse((W * 0.16, W * 0.56, W * 0.30, W * 0.66), fill=(244, 150, 96))
        d.ellipse((W * 0.70, W * 0.56, W * 0.84, W * 0.66), fill=(244, 150, 96))
    elif mood == "okay":
        d.arc((W * 0.33, W * 0.50, W * 0.67, W * 0.72), 20, 160, fill=ink, width=lw)
    else:
        d.arc((W * 0.32, W * 0.64, W * 0.68, W * 0.86), 200, 340, fill=ink, width=lw)
    return img.resize((size, size), Image.LANCZOS)


def save(name, fn, logical):
    fn(logical).save(f"{OUT}/{name}.png")
    fn(logical * 2).save(f"{OUT}/{name}@2x.png")


save("HouseHeartFull", lambda n: heart(n, True), 18)
save("HouseHeartEmpty", lambda n: heart(n, False), 18)
for mood in ("happy", "okay", "sad"):
    save("HouseFace" + mood.capitalize(), lambda n, m=mood: face(n, m), 32)
