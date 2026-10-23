"""Rare variant textures for the wildlife role, recoloured from vanilla textures (read, never copied as they are).

    python notes/pictures/wildlife/recolour.py

Writes src/main/resources/Common/NPC/CozyRanch/Rare/*.png. Every change keeps the painted shading of the source:
colours move in hue and saturation, lightness keeps its relative shape. Masks pick the fur, feathers or skin and
leave eyes, beaks, mouths and noses alone.
"""
import colorsys
import os
import random

from PIL import Image

VANILLA = r"C:\Users\gchou\OneDrive\Documents\Hytale-Modding\HytaleSourceCode\hytale-shared-source\HytaleAssets\Common\NPC"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "..", "src", "main", "resources", "Common", "NPC", "CozyRanch", "Rare"))


def load(rel):
    return Image.open(os.path.join(VANILLA, rel)).convert("RGBA")


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "wb") as f:
        img.save(f, format="PNG")
    print("wrote", path, img.size)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def per_pixel(img, fn):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            res = fn(x, y, h * 360, l, s)
            if res is None:
                continue
            nh, nl, ns = res
            nr, ng, nb = colorsys.hls_to_rgb((nh % 360) / 360, clamp(nl, 0.03, 0.97), clamp(ns))
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return img


def golden_chicken():
    """Cream feathers turn warm gold; the comb, beak, legs and eye stay."""
    img = load(r"Livestock\Chicken\Models\Texture.png")

    def fn(x, y, h, l, s):
        if not (25 <= h <= 70) or s > 0.55 or l < 0.25:
            return None
        # gold: hue ~41, a rich saturation, a little darker so highlights stay below white
        return 38 + (h - 45) * 0.2, 0.18 + l * 0.55, 0.72 + s * 0.4

    save(per_pixel(img, fn), "Chicken_Golden.png")


def red_cow():
    """Highland red: the brown coat goes deep auburn, the golden locks go copper; nose, mouth and horns stay."""
    img = load(r"Livestock\Cow\Models\Texture.png")

    def fn(x, y, h, l, s):
        if not (12 <= h <= 50) or s < 0.25:
            return None
        if h < 24 and l > 0.5:  # pink nose and mouth
            return None
        return 8 + (h - 30) * 0.35, l * 0.92, clamp(s * 1.12 + 0.05)

    save(per_pixel(img, fn), "Cow_Red.png")


def spotted_pig():
    """Pink skin with soft brown patches painted over the body faces, away from the face."""
    img = load(r"Livestock\Pig\Models\Texture_Pink.png")
    w, h = img.size
    scale = w / 128
    rng = random.Random(7)
    face = [(0, 25, 60, 105), (50, 70, 90, 100), (55, 25, 85, 40)]  # snout and eyes, ears, eye
    spots = []
    while len(spots) < 34:
        cx, cy = rng.uniform(0, 128), rng.uniform(0, 128)
        if any(x0 - 4 <= cx <= x1 + 4 and y0 - 4 <= cy <= y1 + 4 for x0, y0, x1, y1 in face):
            continue
        if any((cx - sx / scale) ** 2 + (cy - sy / scale) ** 2 < 14 ** 2 for sx, sy, _, _ in spots):
            continue
        spots.append((cx * scale, cy * scale, rng.uniform(6, 10) * scale, rng.uniform(0.8, 1.15)))
    px = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            hh *= 360
            if not (hh <= 25 or hh >= 340) or s < 0.2 or l < 0.3 or l > 0.92:
                continue
            k = 0.0
            for cx, cy, rad, squash in spots:
                dx, dy = (x + 0.5 - cx), (y + 0.5 - cy) * squash
                d = (dx * dx + dy * dy) ** 0.5 / rad
                # soft painted edge over about a fifth of the radius
                k = max(k, clamp((1.0 - d) / 0.22))
            if k <= 0:
                continue
            # a dark chestnut patch that keeps the skin's light and shade, blended in RGB so the edge has no ring
            k = k * k * (3 - 2 * k)
            tr, tg, tb = colorsys.hls_to_rgb(20 / 360, l * 0.36, 0.42)
            px[x, y] = (round(r + (tr * 255 - r) * k), round(g + (tg * 255 - g) * k), round(b + (tb * 255 - b) * k), a)
    save(img, "Pig_Spotted.png")


def black_sheep():
    """Charcoal wool with a cool brown cast; the dark face stays. Never pure black."""
    def wool(x, y, h, l, s):
        # white wool spans lightness ~0.45..0.97: map to ~0.07..0.25 keeping the order of the shading
        return 250, 0.05 + max(0.0, l - 0.4) * 0.34, 0.08 + s * 0.2

    body = load(r"Livestock\Sheep\Models\Texture.png")

    def body_fn(x, y, h, l, s):
        if l < 0.48 or s > 0.3:  # skin and face are already dark; only the pale wool tufts change
            return None
        return wool(x, y, h, l, s)

    save(per_pixel(body, body_fn), "Sheep_Black.png")
    save(per_pixel(load(r"Livestock\Sheep\Models\Wool_Attachment_Texture.png"), wool), "Sheep_Black_Wool.png")


def purple_frog():
    """Green skin turns violet, the cyan warts turn pink; the eye and mouth stay."""
    img = load(r"Critter\Frog\Models\Model_Textures\Green.png")

    def fn(x, y, h, l, s):
        if 72 <= x * 128 / img.width <= 96 and y * 64 / img.height <= 14:  # the eye
            return None
        if 40 <= h <= 110:
            return 278 + (h - 70) * 0.3, l * 0.9, clamp(s * 0.9)
        if 160 <= h <= 210:
            return 320, l * 0.95, s
        return None

    save(per_pixel(img, fn), "Frog_Purple.png")


def woolly_previews():
    """Offline renders drop RandomAttachmentSets, so graft the wool onto the sheep for pictures only (not shipped),
    as tools/animator/woolcheck.py does: build/wildlife-woolcheck/Sheep_{Normal,Black}.blockymodel and .png. They
    hold vanilla model data, so they live under build/ and are never committed."""
    import json

    van = os.path.join(VANILLA, "Livestock", "Sheep", "Models")
    out = os.path.join(HERE, "..", "..", "..", "build", "wildlife-woolcheck")
    os.makedirs(out, exist_ok=True)
    for variant, body_tex, wool_tex in (
        ("Normal", os.path.join(van, "Texture.png"), os.path.join(van, "Wool_Attachment_Texture.png")),
        ("Black", os.path.join(OUT, "Sheep_Black.png"), os.path.join(OUT, "Sheep_Black_Wool.png")),
    ):
        with open(os.path.join(van, "Model.blockymodel"), encoding="utf-8-sig") as f:
            base = json.load(f)
        with open(os.path.join(van, "Wool_Attachment.blockymodel"), encoding="utf-8-sig") as f:
            wool = json.load(f)
        tb, tw = Image.open(body_tex).convert("RGBA"), Image.open(wool_tex).convert("RGBA")
        atlas = Image.new("RGBA", (tb.width + tw.width, max(tb.height, tw.height)))
        atlas.paste(tb, (0, 0))
        atlas.paste(tw, (tb.width, 0))
        by_name, next_id = {}, [10000]

        def index(n):
            by_name.setdefault(n["name"], n)
            for c in n.get("children", []):
                index(c)

        for n in base["nodes"]:
            index(n)

        def shift(n):
            n["id"] = str(next_id[0])
            next_id[0] += 1
            for face in (n.get("shape", {}).get("textureLayout") or {}).values():
                face["offset"]["x"] += tb.width
            for c in n.get("children", []):
                shift(c)

        def graft(n):
            piece = n.get("shape", {}).get("settings", {}).get("isPiece") and n["name"] in by_name
            for c in n.get("children", []):
                if piece and not c.get("shape", {}).get("settings", {}).get("isPiece"):
                    shift(c)
                    by_name[n["name"]].setdefault("children", []).append(c)
                else:
                    graft(c)

        for n in wool["nodes"]:
            graft(n)
        with open(os.path.join(out, f"Sheep_{variant}.blockymodel"), "w", encoding="utf-8") as f:
            json.dump(base, f)
        with open(os.path.join(out, f"Sheep_{variant}.png"), "wb") as f:
            atlas.save(f, format="PNG")
    print("wrote", out)


if __name__ == "__main__":
    golden_chicken()
    red_cow()
    spotted_pig()
    black_sheep()
    purple_frog()
    woolly_previews()
