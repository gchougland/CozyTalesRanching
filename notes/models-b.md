# models-b: Cow Barn, Sheepfold, Feeding Trough

Branch `wave/models-b`. Three block models placed as multi-block furniture, built by script
(`art/build/models-b/*.mjs`, rebuild with `node art/build/models-b/<model>.mjs` from the worktree root).
No fal money spent; textures are painted procedurally (`art/build/models-b/SOURCES.md`).

## Design change applied: houses are enclosed

Gibson's change (CONTRACT "Houses are enclosed") arrived before any model was built, so both houses
were designed closed from the start:
- **Cow Barn**: the double doors are closed. A one-unit sliver between the leaves shows a shallow
  dark inset (`#1C2030`, one unit deep, in front of the solid wall). The hayloft is the same kind of
  dark inset with hay spilling out. No interior, no see-through gaps.
- **Sheepfold**: rebuilt as a closed stone-and-thatch sheep cottage, still 3 x 3 x 3. Mossy dry-stone
  walls to the eaves on all four sides, timber posts at the four corners with knee braces on every
  wall face and a wall plate under the eaves, the three-tier thatch hip roof with a ridge beam, a
  closed arched plank door with iron straps, the hay rack on the outside wall right of the door,
  closed shutters on both side walls, and six big wool tufts. The concept's chamfered wall corners
  were dropped: the corner posts take that place and read better.
- **Trough**: unchanged by the design change.

## Per model

| | Cow Barn | Sheepfold | Trough |
| --- | --- | --- | --- |
| Id | `CozyRanch_House_CowBarn` | `CozyRanch_House_Sheepfold` | `CozyRanch_Trough` |
| Footprint (W x D x H) | 4 x 4 x 4 | 3 x 3 x 3 | 2 x 1 x 1 |
| Model size (blocks) | 3.84 x 4.03 x 4.00 | 3.00 x 2.96 x 2.78 | 1.94 x 0.88 x 0.63 |
| Nodes | 83 | 68 | 19 empty, 26 filled |
| Texture | 320 x 800, 32 texels/block | 352 x 480 | 128 x 224 (shared by both states) |
| `texture_check` | PASS, no findings | PASS | PASS |
| Fractional sizes / stretch outside 0.5-2 | 0 / 0 | 0 / 0 | 0 / 0 |
| Recipe (Farming bench, Farming tab) | tier 3: Wood_All 40, Rock 20, Ingredient_Hay 10, Ingredient_Bar_Iron 2 | tier 2: Rock 30, Wood_All 16, Ingredient_Fibre 20, Ingredient_Hay 6 | tier 1: Wood_All 12, Ingredient_Bar_Iron 1 |
| Hitbox | 5 boxes: body, churn and bale, lower roof, upper roof, cupola. Bounds X -1.81..1.81, Z -2..2, Y 0..4 (cells -2..1, 4 high) | 6 boxes: body, hay rack, three roof tiers, ridge. Bounds X -1..2, Z -0.94..2, Y 0..2.78 (cells -1..1, 3 high) | 1 box X -0.97..0.97, Z 0.06..0.94, Y 0..0.63 (cells -1..0) |
| Honest rating | 7/10 | 6.5/10 | 7/10 |

Placement: all three are `DrawType: Model`, `Opacity: Transparent`, `VariantRotation: NESW`,
`Support.Down: Full`, break with `GatherType: Benches` and drop themselves (`Gathering.Breaking.ItemId`).
Category `Furniture.Furniture`. Model origin is the placed cell's bottom centre: the even footprints
(barn, trough) are shifted half a block (barn -X and -Z, trough -X, exactly as vanilla `Bench_Trough`,
whose bounds -47..15 units ours match).

Trough states: `BlockType.State.Definitions.Filled.CustomModel` = `Trough_Filled.blockymodel`
(hay mash, a feed mound, two carrots and an apple). Same atlas as the empty model. Wave 2 switches the
state; nothing sets it yet. Block key for prefabs: `*CozyRanch_Trough_State_Definitions_Filled`.

### Cow Barn
Red board-and-batten walls, cream corner posts, eave band and two-pitch gable trim front and back,
fieldstone plinth, gambrel roof of mossy shingles (two slabs per side at 53 and 17 degrees), ridge beam,
cupola with louvred vents and a stepped shingle cap reaching exactly 4.0, closed X-braced double doors
with iron handles, cow-spot plaque, hayloft inset with hay spilling over the sill, milk churn (octagon
from two boxes) and a twine-bound bale at the front right. The building sits 3 units back from centre
so the churn and bale fit inside the footprint.

Later pass: the hay at the hayloft is still a few blocky slabs, it should droop more; the cupola is
smaller than the concept's because the 4-block cap leaves little height above the ridge; the cupola
cap is stepped rather than a pyramid; the side walls are plain (the concept's are too); the plinth
stones are a touch regular.

### Sheepfold
Later pass: the thatch reads as stepped planks more than straw (strand texture on the step sides
could be stronger, the fringe could hang lower and rougher); the stone courses are quite even; the
roof steps are a ziggurat by construction (whole boxes, no tilted slabs, because tilted hip slabs
leave ears or holes at the corners); wool tufts could be fluffier (three lumps instead of two).

### Trough
Later pass: the tub floor is a single smooth plank; the wood is a little plainer and lighter than
the barn's; food is generic (hay mash, carrots, apple), so species-specific fills would be other
states.

## Pictures (all opened)
`notes/pictures/models-b/`:
- `cowbarn_sides.png` (three-quarter, front, right, back, left, with grid), `cowbarn_eye.png` (player's eye)
- `sheepfold_sides.png`, `sheepfold_eye.png`
- `trough_sides.png` (three-quarter, front, right, back, top), `trough_filled.png`
- `ref/coop.png`, `ref/trough.png`: vanilla calibration renders
Best per model: `cowbarn_eye.png`, `sheepfold_eye.png`, `trough_filled.png`.

## Verification
- `asset_validate` on `_worktrees/CozyTales-Ranching-models-b`: valid, 0 errors, 0 schema errors, 0 warnings.
- `asset_check` on the three items: 0 errors, 0 warnings.
- `ui_check_java`: valid. `node tools/check-lang.mjs`: 0 missing from en-US.
- `texture_check` on each item's texture: PASS with no findings.
- `tests/hytale/models-b.test.json` (gate, textures, four renders with goldens in
  `tests/hytale/golden/models-b/`): green twice (goldens created, then matched). Goldens opened. The size
  expectations caught the churn sticking 2 units out of the barn's footprint, now fixed.
  Run it through `node tools/mcp-session.mjs art/build/models-b/test-run.steps.json` (a one-shot
  `hmcp.ps1 test_run` loses its background job when the process exits).
- `.\gradlew.bat --no-daemon build`: BUILD SUCCESSFUL.
- No z-fighting by construction: every pair of touching parts was checked for coplanar overlapping
  faces (frames sit 0.1 to 0.5 units proud, braces at different depths, eave band below the wall top).
  Faces left undrawn are only ones another box covers completely (wall top and bottom, the
  barn's gable step tops and bottoms under the roof, the cupola bottom, inner thatch step bottoms).

## Only the client can confirm
- `model_render` draws no block hitbox (`hitboxBlocks: null`), so the collision boxes and the filler
  cells they produce were checked by numbers only. Place each block in game: walk into it, try placing
  a block inside the footprint, and rotate it through all four directions (even footprints rotate
  around the placed cell).
- Breaking drops the item; the `Benches` gather type and the tool needed were copied from vanilla.
- The trough `Filled` state renders offline; nothing sets it until wave 2.
- Icons: generated pictures; the `IconProperties` for the in-game icon camera are estimates.
- Lighting inside the dark insets and the look of `standard` shading on the painted light.

## REQUESTS
1. **Hitbox files are outside my listed paths.** Multi-block size and collision come from a
   `BlockHitbox` asset (`Server/Item/Block/Hitboxes/...`, as vanilla and CozyTales-Fishing do). I added
   three new files with my own ids, touching nothing shared:
   `src/main/resources/Server/Item/Block/Hitboxes/CozyRanch/CozyRanch_{House_CowBarn,House_Sheepfold,Trough}.json`.
   Please add `Server/Item/Block/Hitboxes/CozyRanch/CozyRanch_{House_CowBarn,House_Sheepfold,Trough}.json`
   to models-b's row (models-a will need the same for its three).
2. **Test golden folder**: goldens live in `tests/hytale/golden/models-b/` (the animator uses
   `golden/animator/`). Please list it under models-b.
3. **`.gitignore` `build/` also ignores `art/build/`**: the generator scripts were committed with
   `git add -f`. Consider `!art/build/` in `.gitignore`.
4. Test and tool paths name the worktree (`_worktrees/CozyTales-Ranching-models-b`), like the animator's;
   rewrite to `CozyTales-Ranching` at merge.
