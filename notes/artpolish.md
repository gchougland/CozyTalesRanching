# artpolish (wave 2): house and item art polish

Branch `wave/artpolish`. Generators live in the git-ignored `art/build/` of the main checkout
(`models-a/`, `models-b/`, and `artpolish/` for the jar, renders, icons, sheets and the test runner); `art/build/target.mjs`
points them at this worktree. Nothing under `art/` is committed.

Pictures: `notes/pictures/artpolish/<Item>_before_after.png`, each one row of concept | before at eye | after at eye |
before at three-quarter | after at three-quarter, perspective on the grid.

## Per item

1. **Sheepfold thatch: done.** The roof is rebuilt as four overhanging tiers. Each tier is a core plus edge bundles
   6–9 wide that lean 28–33° out over the walls, with alternating thickness, so the lower edge is ragged. Hip bundles
   cover the corners. There's a rolled ridge with three twine bands. The thatch texture has 2-texel strands, each with
   its own tone and dark gaps, lighter cut tips on the bundle ends, and a dark packed underside. The drystone has uneven
   course heights (5–9), irregular joints, sunk joints and stronger tone variation. Six existing z-fights were fixed
   (corner posts, wall plates, door step, rack rail and slats, a wool tuft). 191 nodes, size 2.995 × 2.791 × 3.02.
2. **Pigsty: done.**
   - The hut planks are a weathered darker brown with grey and patched boards, against a lighter orange deck.
   - The roof is six greyed boards with gaps, one darker.
   - The mud is darker, with lumps, dark hollows and up to four soft glints.
   - The side and back walls now have proud vertical cladding boards that cover the wedge under the roof, so it no
     longer shows as an inset panel. The left-wall window was rebuilt.
   - 75 nodes.
3. **Coop: done.**
   - The roof has four chunky courses of tiles in four widths, with shuffled thickness and length.
   - A new, much bigger rooster weather vane with a broadside rooster (comb, wattle, beak, eye, three tail plumes),
     compass arms and an arrow. It reads from 10 blocks.
   - 95 nodes. Height is now 3.32 blocks (it was 3.09).
4. **Frog pond: done.** The rims are unique per side, plus nine odd stones: cap stones set high, set low or
   overhanging, and two bulging footing stones, in two tones with moss. The cobble has varied row heights, stone
   lengths, and ochre and grey stones. 64 nodes.
5. **Cow barn: done.**
   - The hayloft is now a stepped dome of straw with twelve thin wisps. Ten drape over the sill and two poke out of
     the sides. All use a strand straw material with lighter tips.
   - The cupola is larger (body 20 wide, up from 16) with a sill, ring and four-step cap, as in the concept.
   - Existing z-fights were fixed (churn, sill).
   - 95 nodes, size unchanged (3.837 × 4 × 4.027).
6. **Jar of Crickets: done (the item JSON change is a request).**
   - New model `Common/Items/CozyRanch/Food/CricketJar.blockymodel` with texture `CricketJar_Texture.png` (64 × 96, 64
     texels per block), and a new icon `Common/Icons/ItemsGenerated/CozyRanch_Food_Crickets.png`.
   - The jar is glass with cut-out windows (alpha 0 or 255 only, like vanilla potion glass, double-sided) and white
     glints. It has a red gingham cloth lid with a puckered top, tied with string (a knot and two ends), a mossy soil
     floor, seven grass blades and three crickets (one climbing the glass).
   - It uses the vanilla Potion's `R-Attachment` → `Origin_Projectile` → `Origin_Item` chain, so it sits in the hand
     like a potion. 47 nodes.

House icons were re-rendered for all five houses. No hitboxes or footprints changed.

## Checks

- zcheck (my audit script): zero coplanar overlaps, zero fractional sizes, all stretch values within 0.5–2, every
  model ≤ 191 nodes.
- `texture_check`: PASS for every house atlas and the jar.
- Tests: `tests/hytale/models-a.test.json` and `models-b.test.json` all pass, and the goldens are updated.
  - In `models-a`, the coop texture is now 256 × 256 with height bounds 3.2–3.4, the pigsty 512 × 224, and the frog
    pond 256 × 384.
  - A new `cricket jar` case renders the model with its texture and checks the texture. Its golden is
    `golden/models-a/cricket-jar.png`.
  - The trough goldens came out byte-identical.
- `asset_validate` on the worktree: valid, 0 errors, 0 schema errors, 1 warning (the plugin's `CozyRanchOfferFood`
  type, which is expected). `ui_check_java` is valid, and `check-lang` reports 0 missing keys.
- `.\gradlew.bat --no-daemon build`: BUILD SUCCESSFUL.

## Still weak

- The sheepfold's hem corners are a little pointy, and a corner post top shows at the eave corners.
- The pigsty mud is still rather flat from far away. It reads as dark wet earth, not as a glossy puddle.
- The coop vane makes the model 3.32 blocks tall, which is over its 3-block-high footprint. The hitbox is unchanged.
- The frog pond's cap stones are slightly pale next to the rim.
- The crickets are small and partly overlap the grass, so in the hand they read as "bugs in a jar" rather than clearly
  as crickets.
- The barn's draped hay hides the top edge of the cow plaque at some angles.

## Money

fal: $0. Notion: not used.

## REQUESTS

1. In `Server/Item/Items/CozyRanch/Food/CozyRanch_Food_Crickets.json`:
   - `"Model": "Items/CozyRanch/Food/CricketJar.blockymodel"`
   - `"Texture": "Items/CozyRanch/Food/CricketJar_Texture.png"`
   - `"Icon": "Icons/ItemsGenerated/CozyRanch_Food_Crickets.png"`
   - Keep `IconProperties` and `Scale` as they are. The jar body is centred where the potion bottle's was
     (y 6 in the item frame).
2. At merge, in `tests/hytale/models-a.test.json` and `models-b.test.json`, replace `_worktrees/CozyTales-Ranching-artpolish`
   with `CozyTales-Ranching`, as for the other roles.
3. The coop's vane now goes to 3.32 blocks high. If the 3-block height must hold, accept the overhang, since the vane
   is decoration and the hitbox is unchanged, or ask me to shorten the post.
