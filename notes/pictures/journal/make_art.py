"""Builds the journal's UI textures.

Inputs: the fal illustrations in the main checkout's art/journal/src/ (cow, chicken, pig, sheep, frog, cover; prompts in
notes/journal.md). Outputs: src/main/resources/Common/UI/Custom/CozyRanch/Journal/*.png and *@2x.png in the worktree.

- Species art: the flat cream background is keyed out by a flood fill from the border (so pale bellies inside the outline
  stay), feathered, trimmed to the animal, padded square, then area-reduced to 480 (@2x) and 240.
- Silhouettes: the cut-out's alpha filled with a soft ink brown, as if pencilled into the book.
- Cover vignette: background keyed to alpha softly (no flood fill), so the painted vignette fades into the page.
- Book: leather cover, two paper pages with a gutter shadow, a card, a hovered card and a divider, painted with numpy.

Run: python make_art.py <art/journal dir> <worktree dir>
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ART = Path(sys.argv[1])
OUT = Path(sys.argv[2]) / "src/main/resources/Common/UI/Custom/CozyRanch/Journal"
OUT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(7)

SPECIES = {"cow": "Cow", "chicken": "Chicken", "pig": "Pig", "sheep": "Sheep", "frog": "Frog"}
INK = np.array([107, 84, 64], dtype=np.float32)


def save_pair(img: Image.Image, name: str, logical: tuple[int, int]):
    w, h = logical
    img.resize((w * 2, h * 2), Image.Resampling.BOX if img.width >= w * 2 else Image.Resampling.LANCZOS).save(OUT / f"{name}@2x.png")
    img.resize((w, h), Image.Resampling.BOX if img.width >= w else Image.Resampling.LANCZOS).save(OUT / f"{name}.png")


def bg_colour(a: np.ndarray) -> np.ndarray:
    corners = np.concatenate([a[:24, :24].reshape(-1, 3), a[:24, -24:].reshape(-1, 3), a[-24:, :24].reshape(-1, 3), a[-24:, -24:].reshape(-1, 3)])
    return np.median(corners, axis=0)


def cutout(src: Path) -> Image.Image:
    a = np.asarray(Image.open(src).convert("RGB"), dtype=np.float32)
    bg = bg_colour(a)
    d = np.sqrt(((a - bg) ** 2).sum(axis=2))
    near = d < 22
    h, w = near.shape
    outside = np.zeros_like(near)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if near[y, x] and not outside[y, x]:
                outside[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if near[y, x] and not outside[y, x]:
                outside[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and near[ny, nx] and not outside[ny, nx]:
                outside[ny, nx] = True
                q.append((ny, nx))
    # soft edge: inside the flood region, alpha follows the colour distance; elsewhere opaque
    soft = np.clip((d - 8) / 26, 0, 1)
    alpha = np.where(outside, soft, 1.0)
    alpha_img = Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    alpha = np.asarray(alpha_img, dtype=np.float32) / 255
    # un-mix the background from edge pixels so no cream halo is left
    a_safe = np.maximum(alpha, 0.05)[..., None]
    rgb = np.clip((a - bg * (1 - a_safe)) / a_safe, 0, 255)
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    img = Image.fromarray(rgba, "RGBA")
    box = img.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()
    img = img.crop(box)
    side = int(max(img.size) * 1.08)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(img, ((side - img.width) // 2, side - img.height - (side - img.height) // 2))
    return sq


def silhouette(img: Image.Image) -> Image.Image:
    alpha = np.asarray(img.getchannel("A"), dtype=np.float32) / 255
    h, w = alpha.shape
    shade = np.linspace(1.08, 0.9, h)[:, None] * np.ones((1, w))
    rgb = np.clip(INK[None, None, :] * shade[..., None], 0, 255)
    rgba = np.dstack([rgb, alpha * 0.42 * 255]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def cover_vignette(src: Path) -> Image.Image:
    a = np.asarray(Image.open(src).convert("RGB"), dtype=np.float32)
    bg = bg_colour(a)
    d = np.sqrt(((a - bg) ** 2).sum(axis=2))
    alpha = np.clip((d - 6) / 40, 0, 1)
    alpha = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2)), dtype=np.float32) / 255
    a_safe = np.maximum(alpha, 0.05)[..., None]
    rgb = np.clip((a - bg * (1 - a_safe)) / a_safe, 0, 255)
    img = Image.fromarray(np.dstack([rgb, alpha * 255]).astype(np.uint8), "RGBA")
    box = img.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
    return img.crop(box)


def smooth_noise(h: int, w: int, scale: int, amp: float) -> np.ndarray:
    small = rng.normal(0, 1, (max(2, h // scale), max(2, w // scale))).astype(np.float32)
    n = np.asarray(Image.fromarray(small).resize((w, h), Image.Resampling.BICUBIC), dtype=np.float32)
    return n * amp


def rounded_alpha(h: int, w: int, r: float) -> np.ndarray:
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32) + 0.5
    cx = np.clip(xx, r, w - r)
    cy = np.clip(yy, r, h - r)
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    return np.clip(r - dist + 0.5, 0, 1)


def leather(size: int = 192, border: int = 48) -> Image.Image:
    h = w = size
    base = np.array([112, 74, 46], dtype=np.float32)
    n = smooth_noise(h, w, 6, 7) + smooth_noise(h, w, 24, 6)
    yy = np.linspace(1.12, 0.86, h)[:, None]
    rgb = (base[None, None, :] + n[..., None]) * yy[..., None]
    # bevel: lighter top-left rim, darker bottom-right rim
    edge = np.minimum.reduce([np.mgrid[0:h, 0:w][0], np.mgrid[0:h, 0:w][1], h - 1 - np.mgrid[0:h, 0:w][0], w - 1 - np.mgrid[0:h, 0:w][1]]).astype(np.float32)
    rim = np.clip(1 - edge / 10, 0, 1)
    rgb = rgb * (1 - 0.28 * rim[..., None])
    # stitched line inset by 16 px (@2x), dashes 10 on 6 off
    yy_i, xx_i = np.mgrid[0:h, 0:w]
    inset = 18
    on_line = ((np.abs(yy_i - inset) < 1.5) | (np.abs(yy_i - (h - 1 - inset)) < 1.5)) & (xx_i >= inset) & (xx_i <= w - 1 - inset)
    on_line |= ((np.abs(xx_i - inset) < 1.5) | (np.abs(xx_i - (w - 1 - inset)) < 1.5)) & (yy_i >= inset) & (yy_i <= h - 1 - inset)
    dash = (((xx_i + yy_i) // 8) % 2 == 0)
    stitch = on_line & dash
    rgb[stitch] = np.array([214, 178, 120], dtype=np.float32)
    alpha = rounded_alpha(h, w, 20)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8), "RGBA")


def paper(size: int = 256, gutter_right: bool = True) -> Image.Image:
    h = w = size
    base = np.array([243, 231, 205], dtype=np.float32)
    n = smooth_noise(h, w, 32, 2.2) + smooth_noise(h, w, 8, 0.8)
    rgb = base[None, None, :] + n[..., None]
    xx = np.mgrid[0:h, 0:w][1].astype(np.float32)
    yy = np.mgrid[0:h, 0:w][0].astype(np.float32)
    gx = (w - 1 - xx) if gutter_right else xx
    gutter = np.exp(-gx / 22) * 0.20 + np.exp(-gx / 6) * 0.08
    ox = xx if gutter_right else (w - 1 - xx)
    outer = np.exp(-np.minimum.reduce([ox, yy, h - 1 - yy]) / 10) * 0.10
    shade = 1 - gutter - outer
    warm = np.array([0.0, -6.0, -16.0], dtype=np.float32)
    rgb = rgb * shade[..., None] + (gutter + outer)[..., None] * warm[None, None, :] * 2
    alpha = np.ones((h, w), dtype=np.float32)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8), "RGBA")


def card(hovered: bool) -> Image.Image:
    h = w = 96
    base = np.array([236, 220, 188] if not hovered else [246, 230, 196], dtype=np.float32)
    n = smooth_noise(h, w, 16, 1.5)
    rgb = base[None, None, :] + n[..., None]
    edge = np.minimum.reduce([np.mgrid[0:h, 0:w][0], np.mgrid[0:h, 0:w][1], h - 1 - np.mgrid[0:h, 0:w][0], w - 1 - np.mgrid[0:h, 0:w][1]]).astype(np.float32)
    line = (edge >= 2) & (edge < 4)
    border_col = np.array([170, 128, 84] if not hovered else [196, 140, 72], dtype=np.float32)
    rgb[line] = rgb[line] * 0.3 + border_col * 0.7
    rgb = rgb * (1 - 0.10 * np.clip(1 - edge / 12, 0, 1))[..., None]
    alpha = rounded_alpha(h, w, 10)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8), "RGBA")


def divider() -> Image.Image:
    w, h = 640, 24
    img = np.zeros((h, w, 4), dtype=np.float32)
    col = np.array([150, 104, 62], dtype=np.float32)
    xx = np.arange(w, dtype=np.float32)
    cx = w / 2
    fade = np.clip(1 - np.abs(xx - cx) / cx, 0, 1) ** 0.6
    cy = h / 2
    for y in range(h):
        dist = abs(y + 0.5 - cy)
        line = np.clip(1.4 - dist, 0, 1) * fade
        dx = np.abs(xx - cx)
        diamond = np.clip(7.5 - (dx + dist * 1.0), 0, 1)
        a = np.maximum(line, diamond)
        img[y, :, :3] = col
        img[y, :, 3] = a * 230
    return Image.fromarray(img.astype(np.uint8), "RGBA")


for key, name in SPECIES.items():
    src = ART / "src" / f"{key}.png"
    art = cutout(src)
    art.save(ART / f"{name}_cut.png")
    save_pair(art, name, (240, 240))
    save_pair(silhouette(art), name + "Silhouette", (240, 240))

unknown = silhouette(cutout(ART / "src" / "sheep.png"))
save_pair(unknown, "Unknown", (240, 240))

cover = cover_vignette(ART / "src" / "cover.png")
ch = round(cover.height * 400 / cover.width)
save_pair(cover, "Cover", (400, ch))

leather().save(OUT / "Leather@2x.png")
paper(gutter_right=True).save(OUT / "PageLeft@2x.png")
paper(gutter_right=False).save(OUT / "PageRight@2x.png")
card(False).save(OUT / "Card@2x.png")
card(True).save(OUT / "CardHovered@2x.png")
divider().save(OUT / "Divider@2x.png")
print("cover logical size", 400, ch)
for p in sorted(OUT.glob("*.png")):
    print(p.name, Image.open(p).size)
