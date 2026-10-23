"""Journal portraits from the in-game models.

Renders come from the Hytale MCP (model_render, three_quarter, transparent, no hitbox, Idle frame 0):
    tools\\journal\\render.ps1 <outdir>
then this script crops each render to the animal, centres it in a square and writes
<Species>.png (240), <Species>@2x.png (480), the matching silhouettes and the cover (all five) into the
journal folder.

    python tools/journal/portraits.py <render dir>
"""
import sys
from pathlib import Path

from PIL import Image

SPECIES = ["Cow", "Chicken", "Pig", "Sheep", "Frog"]
SILHOUETTE = (104, 83, 61)
SILHOUETTE_ALPHA = 107
PAD = 0.06
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/Common/UI/Custom/CozyRanch/Journal"


def square(im: Image.Image) -> Image.Image:
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    im = im.crop(box)
    side = int(max(im.size) * (1 + 2 * PAD))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - im.width) // 2, (side - im.height) // 2), im)
    return canvas


def silhouette(im: Image.Image) -> Image.Image:
    alpha = im.getchannel("A").point(lambda a: SILHOUETTE_ALPHA if a > 8 else 0)
    out = Image.new("RGBA", im.size, SILHOUETTE + (0,))
    out.putalpha(alpha)
    return out


COVER_ORDER = ["Sheep", "Cow", "Chicken", "Pig", "Frog"]
COVER_HEIGHT = {"Cow": 1.0, "Sheep": 0.8, "Pig": 0.62, "Chicken": 0.5, "Frog": 0.36}


def cover(render_dir: Path) -> None:
    """The five animals side by side, standing on one line, at their in-game relative heights."""
    width, height = 800, 578
    tallest = int(height * 0.9)
    parts = []
    for name in COVER_ORDER:
        im = Image.open(render_dir / f"{name}.png").convert("RGBA")
        im = im.crop(im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox())
        h = int(tallest * COVER_HEIGHT[name])
        parts.append(im.resize((max(1, im.width * h // im.height), h), Image.LANCZOS))
    overlap = 0.12
    total = sum(p.width for p in parts) - int(overlap * sum(p.width for p in parts[1:]))
    scale = min(1.0, (width * 0.94) / total)
    if scale < 1.0:
        parts = [p.resize((int(p.width * scale), int(p.height * scale)), Image.LANCZOS) for p in parts]
        total = int(total * scale)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    base = (height + max(p.height for p in parts)) // 2
    x = (width - total) // 2
    for i, p in enumerate(parts):
        if i:
            x -= int(overlap * p.width)
        canvas.alpha_composite(p, (x, base - p.height))
        x += p.width
    canvas.save(OUT / "Cover@2x.png", optimize=True)
    canvas.resize((width // 2, height // 2), Image.LANCZOS).save(OUT / "Cover.png", optimize=True)
    print("wrote Cover")


def main(render_dir: Path) -> None:
    cover(render_dir)
    for name in SPECIES:
        art = square(Image.open(render_dir / f"{name}.png").convert("RGBA"))
        for suffix, size in (("", 240), ("@2x", 480)):
            portrait = art.resize((size, size), Image.LANCZOS)
            portrait.save(OUT / f"{name}{suffix}.png", optimize=True)
            silhouette(portrait).save(OUT / f"{name}Silhouette{suffix}.png", optimize=True)
        print("wrote", name)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
