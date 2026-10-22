# models-a: Chicken Coop, Pigsty, Frog Pond

Three house blocks, each one block item that places a multi-block furniture model. Branch `wave/models-a`.

## Design change during the wave: houses are enclosed

Gibson (2026-10-22, CONTRACT "Houses are enclosed"): animals walk to the door and vanish inside, so no house
shows an interior. All three follow it:
- Coop: the front door is a shallow dark blue-black inset (1 unit proud of the wall) inside a frame proud by
  2.5, so it reads as a recess. No interior. The gable is stepped boxes whose outer corners sit inside the
  roof courses, and a ridge cap closes the V between the two roof sides: no gap between walls and roof.
- Pigsty: the hut is a closed box plus a sloped wedge under the lean-to roof (no gap under the roof). The
  doorway is a dark inset in the pink frame. Deck, trough, wallow and fence stay open.
- Frog Pond: the toadstool stem is solid with a small dark inset door in a wood frame, a step in front.
  Pond, pads and cattails stay open.

## Per house

| | Chicken Coop | Pigsty | Frog Pond |
| --- | --- | --- | --- |
| Item id | `CozyRanch_House_ChickenCoop` | `CozyRanch_House_Pigsty` | `CozyRanch_House_FrogPond` |
| Footprint (cells) | 2 x 2 x 3 | 3 x 3 x 2 | 3 x 3 x 2 |
| Model size (blocks) | 2.23 x 3.09 x 2.16 (eaves, straw and ramp tip overhang by at most 3.7 units) | 3.0 x 1.90 x 3.04 (roof back overhang 1.2 units) | 3.0 x 1.78 x 3.0 |
| Nodes | 61 (60 boxes) | 47 | 55 |
| Texture | 256 x 224, 32 texels per block | 512 x 192 | 256 x 224 |
| texture_check | PASS | PASS | WARN: shadow hue (palette: dark moss and reeds against light teal water) |
| Recipe (Farmer's bench, Farming tab) | Wood_All 20, Ingredient_Fibre 8, Ingredient_Hay 4 | Wood_All 24, Soil_Mud 6, Ingredient_Fibre 6 | Rock_Stone_Cobble 16, Plant_Moss_Block_Green 4, Plant_Crop_Mushroom_Cap_Red 2, Ingredient_Fibre 4 |
| Hitbox | body and roof x 0..1.95, y 0..2.75, z 0.03..1.47; ramp box in front | full deck to 0.375; hut to 1.85; wallow to the mud surface 0.5625; trough; fence | four rim runs to 0.5625; basin floor 0.094 (you wade in the pond); toadstool stem and cap to 1.78; stepping stone |
| Drop | itself | itself | itself |
| Honest rating | 8/10: reads like the concept from every side | 7/10: right layout and colours, hut and roof plainer than the concept | 8/10: very close to the concept |

The model origin is the placed cell's bottom centre (vanilla convention). The coop's root node is offset by
(16, 0, 16) so its 2 x 2 footprint covers cells x 0..1 and z 0..1, like the vanilla tables; the 3 x 3 houses
are centred on the placed cell. Front (+Z, door side) faces the player when placed (`VariantRotation: NESW`).
The frog pond's block has `RequiresAlphaBlending: true` and `Opacity: Transparent`; its water is one top face
at alpha 0.72 over a dark basin floor.

Checks done (offline): zero fractional `settings.size` and every stretch within 0.5..2 (scanned the written
files), no coplanar same-facing overlaps among axis-aligned boxes (script check), every face drawn (no faces
left out), `asset_check` clean on items and hitboxes, `asset_validate` valid with 0 errors and 0 schema errors,
`ui_check_java` valid, `check-lang` 0 missing, Gradle build green, `tests/hytale/models-a.test.json` green
(goldens in `tests/hytale/golden/models-a/`, looked at).

Pictures (`notes/pictures/models-a/`): `<House>_sides.png` (front, right, back, left), `<House>_three_quarter.png`,
`<House>_player_eye.png`, `<House>_vs_concept.png` (concept left, model right). Best per house:
`ChickenCoop_vs_concept.png`, `Pigsty_three_quarter.png`, `FrogPond_player_eye.png`.

## What a later pass should improve

- Coop: roof courses could be chunkier with bigger butt steps, like the concept; the rooster vane is small
  at a distance; the nesting box could have a front lip. A `_WithProduce` state (eggs in the nest) fits here.
- Pigsty: the concept's hut is darker and more weathered, with more crooked, separated roof boards and a
  little window on the side; ours is cleaner. The sloped wedge shows as a slightly inset panel above the side
  walls. The mud's gloss blobs read more as stains than shine.
- Frog Pond: the rim cobbles are more regular than the concept's; the stone could be warmer with orange
  patches. The `shadow hue` WARN comes from the palette, not the shading.
- Textures are procedural. A fal swatch pass (planks, shingles, cobble) could add brush character; not spent.

## Only the client can confirm

- That the hitbox makes the right filler cells (2 x 3 x 2 and 3 x 2 x 3) so blocks cannot be placed inside,
  and that players collide as expected. `model_render` does not draw `BlockBoundingBoxes`; hitboxes were
  checked by numbers against the model bounds.
- The water's alpha blending and how it sorts with the lily pads and reeds.
- Placement rotation and that the door faces the player.
- `IconProperties` (scale and translation of the 3D inventory icon) are estimates; the PNG icons render
  correctly at 64 x 64.

## REQUESTS

1. **Hitbox assets outside my listed paths.** `HitboxType` must name a `BlockBoundingBoxes` asset, and no vanilla
   hitbox fits 2 x 2 x 3 or 3 x 3 x 2 houses. I added three new files (no shared file touched):
   `src/main/resources/Server/Item/Block/Hitboxes/CozyRanch/Houses/CozyRanch_House_{ChickenCoop,Pigsty,FrogPond}.json`.
   Please add `Server/Item/Block/Hitboxes/CozyRanch/Houses/CozyRanch_House_{ChickenCoop,Pigsty,FrogPond}.json` to the
   models-a row of the ownership table (models-b will need the same for its houses).
2. **Golden folder.** Goldens live in `tests/hytale/golden/models-a/`; please list that folder as models-a's.
3. **Test paths.** The test file uses worktree paths (`_worktrees/CozyTales-Ranching-models-a/...`), as the other
   roles do. At merge, replace `_worktrees/CozyTales-Ranching-models-a` with `CozyTales-Ranching`.
4. **`.gitignore` hides `art/build/`** (rule `build/`). The model scripts were committed with `git add -f`. Suggest
   anchoring the rule as `/build/` so `art/build/**` is tracked normally.
