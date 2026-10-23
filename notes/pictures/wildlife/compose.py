"""Joins the renders from render.ps1: one picture per species, normal on the left, rare on the right, two views."""
import os
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
pairs = [("Chicken", "Chicken_Golden"), ("Cow", "Cow_Red"), ("Pig", "Pig_Spotted"), ("Sheep_Normal", "Sheep_Black"), ("Frog", "Frog_Purple")]
for normal, rare in pairs:
    cells = []
    for view in ("three_quarter", "back_three_quarter"):
        row = [Image.open(os.path.join(src, f"{n}_{view}.png")).convert("RGBA") for n in (normal, rare)]
        cells.append(row)
    w = max(c.width for row in cells for c in row)
    h = max(c.height for row in cells for c in row)
    out = Image.new("RGBA", (w * 2, h * len(cells)), (40, 42, 48, 255))
    for r, row in enumerate(cells):
        for c, im in enumerate(row):
            out.paste(im, (c * w, r * h), im)
    name = rare.split("_")[0].lower() + "-rare.png"
    with open(os.path.join(dst, name), "wb") as f:
        out.save(f, format="PNG")
    print("wrote", os.path.join(dst, name))
