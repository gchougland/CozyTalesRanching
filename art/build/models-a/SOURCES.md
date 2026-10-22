# Sources: models-a (Chicken Coop, Pigsty, Frog Pond)

Everything here is made by script on this machine. No generator was used and nothing was bought:
fal spend **US$0.00**.

| Output | Made by | From |
| --- | --- | --- |
| `Common/Blocks/CozyRanch/Houses/*/*.blockymodel` | `coop.mjs`, `pigsty.mjs`, `frogpond.mjs` (shared `lib.mjs`) | boxes placed by hand after the concept art in `art/concepts/` and `DESIGN_NOTES.md` |
| `Common/Blocks/CozyRanch/Houses/*/*_Texture.png` | `paint.mjs` painters through `lib.mjs` `build()` | procedural painting: value-noise tone fields, boards, shingles, cobbles, mud, water; palette hex values from `DESIGN_NOTES.md`; light pass per face |
| `Common/Icons/ItemsGenerated/CozyRanch_House_*.png` | `icon.ps1` | `model_render` of the item (orthographic, clear background), cropped and box-averaged to 64 x 64 |

Rebuild: `node coop.mjs; node pigsty.mjs; node frogpond.mjs`, then `.\icon.ps1 <House>` for each house.
