# art (wave 3, batch 1): toys, Butcher's Block, journal, feed sacks, truffle

Branch `wave/art`. Models, textures, icons and hitboxes only; no item JSON was edited (field lists under REQUESTS).
Generators live in the git-ignored main checkout `art/build/art3/` (not committed):
`toys.mjs`, `butcher.mjs`, `items.mjs` write models and textures; `preview.mjs` writes hitboxes (and temporary preview
items, `--clean` removes them); `icons.mjs`, `render.mjs`, `ghost.mjs` (hitbox overlays), `scene.mjs` (scale prefab),
`check.mjs` (texture_check + asset_validate), `tests.mjs` (writes and runs `tests/hytale/art3.test.json`).
`lib.mjs` and `materials.mjs` are models-b's library, extended (flat-shaded items with the full light pass painted in,
an `oct()` helper that builds round parts as vanilla does, a second texture on the same atlas, a node-chain wrap).

## What I made

| Id | Model / texture | Size (blocks) | Nodes | Atlas |
| --- | --- | --- | --- | --- |
| `CozyRanch_Toy_Ball` | `Blocks/CozyRanch/Toys/Toy_Ball` | 0.69 round | 22 | 96 x 192 |
| `CozyRanch_Toy_ScratchingPost` | `Blocks/CozyRanch/Toys/Toy_ScratchingPost` | 0.82 x 1.0 x 0.82 | 22 | 96 x 192 |
| `CozyRanch_Toy_HangingBell` | `Blocks/CozyRanch/Toys/Toy_HangingBell` | 1.03 x 1.01 x 0.35 | 30 | 96 x 128 |
| `CozyRanch_Toy_FloatingDuck` | `Blocks/CozyRanch/Toys/Toy_FloatingDuck` | 0.44 x 0.67 x 0.83 | 12 | 64 x 96 |
| `CozyRanch_Toy_Log` | `Blocks/CozyRanch/Toys/Toy_Log` | 1.82 x 0.89 x 0.75 (2 x 1 cells) | 14 | 64 x 352 |
| `CozyRanch_ButcherBlock` | `Blocks/CozyRanch/ButcherBlock/ButcherBlock` | 0.82 x 1.01 x 0.82 | 15 | 64 x 256 |
| `CozyRanch_Journal` | `Items/CozyRanch/Journal/Journal` | 0.27 x 0.37 x 0.12 | 14 + 2 | 64 x 128 |
| `CozyRanch_Feed_{Hearty,Sweet,Breeders,Premium}` | `Items/CozyRanch/Feed/FeedSack` + `FeedSack_<Variant>_Texture` | 0.28 x 0.36 x 0.22 | 12 + 2 | 96 x 96 each |
| `CozyRanch_Truffle` | `Items/CozyRanch/Produce/Truffle` | 0.25 x 0.17 x 0.24 | 8 + 2 | 32 x 96 |

- **Ball**: a stitched leather ball, red and cream gores with seams and a mustard button at each pole; octagon layers.
- **Scratching post**: a log-slice base, an octagonal post, a hemp rope wrap at the top, two stiff dark brushes
  (front and right) with bristle tufts, a wool tuft and a tuft of cow hair caught in them.
- **Hanging bell**: a plank base, two posts with knee braces, a beam under a small red shingle roof, a brass bell on a
  rope with an iron clapper, a red bow on the beam.
- **Floating duck**: a painted wooden duck (ochre body, orange beak, painted eyes, a red waterline stripe). Its hull
  sits 2 units (1/16 block) below the cell floor so it rides in the water when placed on a water surface; on
  ground it looks settled.
- **Log**: a hollow log of eight bark staves (rough, uneven cut ends), sawn rings on the ends, a dark rotten hollow,
  moss, a broken branch stub and two little mushrooms. Covers the placed cell and the one to its west (like the trough).
- **Butcher's Block**: a laminated board with light knife marks on a tree stump with root flares, a cleaver bitten
  into the board, a blue check tea towel over the corner and a whetstone. No meat, no blood.
- **Journal**: a leather book standing upright, tooled border, brass corners, a raised spine with bands, cream page
  edges, a brass medallion with a paw print, a strap with a brass clasp and a red ribbon bookmark.
- **Feed sacks**: one open burlap sack (soft bulging belly, tie, rolled cuff, a mound of feed) with four textures:
  Hearty green label with a wheat ear and grain mash; Sweet rose label with an apple and red/orange bits; Breeder's
  purple label with a heart and pink pellets; Premium blue label with a gold border, gold star, gold twine and golden feed.
- **Truffle**: a warty dark lump of three overlapping lobes, a cut slice leaning on it showing pale marbling, and a leaf.
- **Icons** (`Icons/ItemsGenerated/CozyRanch_*.png`, 64 x 64): three-quarter renders framed like vanilla (bigger things
  fill more of the slot: log 58 px, furniture 50-54, sacks and journal 44-46, truffle 38).
- **Hitboxes** (`Server/Item/Block/Hitboxes/CozyRanch/CozyRanch_<Id>.json`): one box each, two for the scratching post
  (wide base, narrower post with brushes); the log spans x -0.94..0.94.

## Pictures (all opened), `notes/pictures/art/`

- `final_all.png`: every model at three-quarter. Best overall picture.
- `<Id>_sides.png` (front, right, back, left) and `<Id>_tq.png` for all twelve.
- `hitboxes.png` and `<Id>_hitbox.png`: each block with its hitbox drawn as green bars (debug copies; those renders
  draw the grid at item scale, so read the box against the model, not the grid).
- `scale_front.png`, `scale_back.png`: every block on grass beside the trough, a dirt block, a vanilla chest and the
  vanilla sack (prefab_render).
- `icons_vs_vanilla.png`: my icons, with the trough, Salt, Egg and a vanilla chest in the bottom row.
- `pass1_toys.png`, `pass2_all.png`, `pass3_all.png`: the passes. What changed after looking: the scratching post's
  pale block brushes became dark brushes with bristle tufts and the rope moved up into view; the duck's floating wing
  plank became a tucked wing; smeary bark became bark plates (then less brick-like); the label symbols became
  hand-drawn masks (the star looked like a crab, the apple a square); the sack got a bulging belly and darker burlap;
  the journal's paw print became a hand-drawn mask on a bigger medallion (it read as a cross); the truffle slice got
  hand-drawn veins (it read as a face).
- `ref/`: vanilla reference renders (book pile, ancient sack, salt, mushroom).

## Checks

- Every model: zero fractional sizes, every stretch within 0.5..2, zero coplanar same-facing overlaps (models-b and
  artpolish's `zcheck.mjs`), every face drawn, at most 30 nodes.
- `texture_check`: no FAIL. PASS for the scratching post, duck, log, butcher block, journal and all four sacks. WARN:
  ball (grain, from the stitched seam grooves), bell (21 colours per 8 x 8 against vanilla's 22), truffle (grain, its
  wart lattice). I looked at each and kept them.
- `tests/hytale/art3.test.json` (gate, twelve texture checks, twelve renders with goldens in `tests/hytale/golden/art3/`):
  green twice (goldens created and opened, then matched; refreshed once after the truffle change).
- `asset_validate` on `CozyTales-Ranching`: valid, 0 errors, 0 schema errors (warnings are the known
  plugin interaction types).
- Held items use vanilla Salt's chain: `R-Attachment` (piece, y -4) then `Origin_Item` (piece, y +8), flat shading.

## Weak or unfinished

- **Not opened in Blockbench.** The Blockbench bridge was not connected (it needs the plugin loaded and authorised by
  hand), so Gibson's Blockbench check is still to do. The models use the same writer as the houses and trough
  (whole sizes, `custom` unwrap, one rect per face at that size), which opened correctly before.
- Nothing has been seen in the client: placement, rotation, hitbox collision, how the duck sits on real water, the
  held pose of the journal, sacks and truffle, and the item icons in a real slot.
- The ball's octagon layers show as steps at close range; the gores read better than the roundness.
- The truffle is the weakest silhouette: from far it is a brown lump; the slice and leaf carry the read.
- The sacks are still a little boxy compared with a real sack; the burlap reads as fabric only up close.
- `Toy_FloatingDuck.blockymodel` was hand-edited by Gibson after this pass. Do not regenerate or rewrite it with a
  script; change it only by hand in Blockbench.
- Feed sacks share one model, so a variant can only differ by texture (as asked).

## Money

fal: $0 (not used). Notion: not used.

## REQUESTS

Paths below are relative to `Common/`. All blocks: `DrawType` `Model`, `Opacity` `Transparent`, `Material` `Solid`,
`VariantRotation` `NESW`, `PlayerAnimationsId` `Block`, `Gathering.Breaking` `{ "GatherType": "Benches", "ItemId": <own id> }`,
`Scale` 0.5 (as the trough). `IconProperties` are estimates; the PNG icons are what the inventory shows.

**care** (`Server/Item/Items/CozyRanch/Toys/*.json`):
1. `CozyRanch_Toy_Ball`: `Icon` `Icons/ItemsGenerated/CozyRanch_Toy_Ball.png`; `BlockType.CustomModel`
   `Blocks/CozyRanch/Toys/Toy_Ball.blockymodel`; `CustomModelTexture` `[{ "Texture": "Blocks/CozyRanch/Toys/Toy_Ball_Texture.png", "Weight": 1 }]`;
   `HitboxType` `CozyRanch_Toy_Ball`; `Support.Down` `[{ "FaceType": "Full" }]`; `BlockSoundSetId` `Cloth`;
   `PhysicalMaterialId` `Cloth`; `BlockParticleSetId` `Grass` (as vanilla's sack); `ParticleColor` `#C4573E`;
   `ItemSoundSetId` `ISS_Blocks_Soft`; `IconProperties` `{ "Scale": 0.6, "Translation": [0, -12], "Rotation": [22.5, 45, 22.5] }`.
2. `CozyRanch_Toy_ScratchingPost`: same pattern with `Toy_ScratchingPost`; `HitboxType` `CozyRanch_Toy_ScratchingPost`;
   `Support.Down` Full; `BlockSoundSetId` `Wood`; `PhysicalMaterialId` `Wood`; `BlockParticleSetId` `Wood`;
   `ParticleColor` `#9A6A3E`; `ItemSoundSetId` `ISS_Blocks_Wood`; `IconProperties` `{ "Scale": 0.5, "Translation": [0, -15], "Rotation": [22.5, 45, 22.5] }`.
3. `CozyRanch_Toy_HangingBell`: `Toy_HangingBell`; `HitboxType` `CozyRanch_Toy_HangingBell`; Support Full; Wood sounds,
   material and particles; `ParticleColor` `#946238`; `ISS_Blocks_Wood`; `IconProperties` as the post.
4. `CozyRanch_Toy_FloatingDuck`: `Toy_FloatingDuck`; `HitboxType` `CozyRanch_Toy_FloatingDuck`; **no `Support`** (so
   it can sit on a water surface); Wood sounds, material and particles; `ParticleColor` `#E2B848`; `ISS_Blocks_Wood`;
   `IconProperties` `{ "Scale": 0.6, "Translation": [0, -12], "Rotation": [22.5, 45, 22.5] }`. Whether a player can
   place it onto water depends on the block's placement rules; please try it in game.
5. `CozyRanch_Toy_Log`: `Toy_Log`; `HitboxType` `CozyRanch_Toy_Log` (2 x 1 cells: the placed cell and its west
   neighbour, as the trough); Support Full; Wood sounds, material and particles; `ParticleColor` `#6E5038`;
   `ISS_Blocks_Wood`; `IconProperties` `{ "Scale": 0.43, "Translation": [13.2, -10.8], "Rotation": [22.5, 45, 22.5] }` (the trough's).

**care** (`Server/Item/Items/CozyRanch/Feed/*.json`), held items, not blocks:
6. `CozyRanch_Feed_Hearty`: `"Model": "Items/CozyRanch/Feed/FeedSack.blockymodel"`,
   `"Texture": "Items/CozyRanch/Feed/FeedSack_Hearty_Texture.png"`, `"Icon": "Icons/ItemsGenerated/CozyRanch_Feed_Hearty.png"`,
   `"Scale": 1`, `"PlayerAnimationsId": "Item"`, `"ItemSoundSetId": "ISS_Items_Cloth"`,
   `"IconProperties": { "Scale": 0.9, "Translation": [0, -8], "Rotation": [22.5, 45, 22.5] }`.
7. `CozyRanch_Feed_Sweet`, `CozyRanch_Feed_Breeders`, `CozyRanch_Feed_Premium`: the same with
   `FeedSack_Sweet_Texture.png`, `FeedSack_Breeders_Texture.png`, `FeedSack_Premium_Texture.png` and their icons.

**journal** (`Server/Item/Items/CozyRanch/CozyRanch_Journal.json`):
8. `"Model": "Items/CozyRanch/Journal/Journal.blockymodel"`, `"Texture": "Items/CozyRanch/Journal/Journal_Texture.png"`,
   `"Icon": "Icons/ItemsGenerated/CozyRanch_Journal.png"`, `"Scale": 1`, `"PlayerAnimationsId": "Item"`,
   `"ItemSoundSetId": "ISS_Items_Leather"`, `"IconProperties": { "Scale": 0.85, "Translation": [0, -8], "Rotation": [22.5, 45, 22.5] }`.

**rehome** (`Server/Item/Items/CozyRanch/CozyRanch_ButcherBlock.json`):
9. `Icon` `Icons/ItemsGenerated/CozyRanch_ButcherBlock.png`; `BlockType.CustomModel`
   `Blocks/CozyRanch/ButcherBlock/ButcherBlock.blockymodel`; `CustomModelTexture`
   `[{ "Texture": "Blocks/CozyRanch/ButcherBlock/ButcherBlock_Texture.png", "Weight": 1 }]`; `HitboxType`
   `CozyRanch_ButcherBlock` (one box to 0.69 high: the board top, the cleaver is not solid); Support Full; Wood sounds,
   material and particles; `ParticleColor` `#B88A56`; `ItemSoundSetId` `ISS_Blocks_Wood`; `IconProperties` as the post.

**produce** (`Server/Item/Items/CozyRanch/Produce/CozyRanch_Truffle.json`):
10. `"Model": "Items/CozyRanch/Produce/Truffle.blockymodel"`, `"Texture": "Items/CozyRanch/Produce/Truffle_Texture.png"`,
    `"Icon": "Icons/ItemsGenerated/CozyRanch_Truffle.png"`, `"Scale": 1`, `"PlayerAnimationsId": "Item"`,
    `"IconProperties": { "Scale": 1.1, "Translation": [0, -5], "Rotation": [22.5, 45, 22.5] }`.

**coordinator**:
11. Hitbox ids: the ownership row lists `Server/Item/Block/Hitboxes/CozyRanch/{Toy_*,ButcherBlock}*`, but the naming
    rule is `CozyRanch_<Thing>` (as `CozyRanch_Trough.json`). I named them `CozyRanch_Toy_*.json` and
    `CozyRanch_ButcherBlock.json` in that folder; please read the row as `CozyRanch_{Toy_*,ButcherBlock}*`.
12. Goldens live in `tests/hytale/golden/art3/`; please list that folder as art's.
13. At merge, replace `CozyTales-Ranching` with `CozyTales-Ranching` in `tests/hytale/art3.test.json`.
14. The temporary `Server/Item/Items/CozyRanch/ArtPreview/` items were deleted before the last commit and never committed.
