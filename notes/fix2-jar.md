# fix2-jar: placeable cricket jar, solid lid

## What changed

1. **The jar is placeable, the way vanilla foods are.** In vanilla, food items (`Template_Food`, `Food_Pie_*`) carry a
   `BlockType` (Material `Empty`, DrawType `Model`, the food's model, `Gathering` Harvest/Soft, a `Food_*` hitbox).
   Their Secondary root (`Condition_Consume_Food_T*`) is a `Condition` with `Crouching: false` (and Adventure mode):
   when it holds the food is eaten, when it fails (crouching) it runs `Block_Secondary`, which is `UseBlock` and then
   `PlaceModeSelect` (place). The jar now does the same:
   - `CozyRanch_Food_Crickets.json` has a `BlockType`: Material `Empty` (walk through it, like a pie), DrawType `Model`,
     `CustomModel` = the same `CricketJar.blockymodel` and texture, `CustomModelScale` 0.5 (the vanilla scale for an item
     model used as a block: Decorative potion, Watering can), so it stands as big as it looks in the hand: 0.27 blocks
     wide, 0.31 tall. `RandomRotation` `YawStep1` like `Template_Food`. Hitbox `CozyRanch_CricketJar`
     (new `Server/Item/Block/Hitboxes/CozyRanch/CozyRanch_CricketJar.json`, 0.34 x 0.33 x 0.34, centred, from the floor).
     `Gathering` Harvest and Soft with no ItemId: breaking it drops the jar itself (as vanilla pies).
   - The model was lifted one unit (`R-Attachment` y 5 -> 6) so its bottom is at y 0: it sat 1 unit below its origin
     before, which would have sunk the placed jar into the ground. Held, that is 1/64 of a block, invisible.
   - Secondary is now `Condition { Crouching: false, Next: CozyRanchOfferFood, Failed: Block_Secondary }`.
   - The description line says how to place it.

   **Which click does what (Adventure and Creative alike):**
   - Right-click (not crouching), looking at an animal within 6 blocks: offers crickets (CozyRanchOfferFood, unchanged).
   - Right-click (not crouching) at the ground or a block: nothing (the offer finds no animal and fails), as before.
     Deliberate: a near miss at a frog should not drop a jar on the ground.
   - Crouch + right-click at a surface: places the jar (vanilla `Block_Secondary`; on a usable block such as a chest it
     uses that block first, like every vanilla food).
   - Left-click a placed jar: breaks it instantly (Soft) and drops the jar item. Use (F) on it also picks it up
     (vanilla unarmed `Use` falls through to `BreakBlock` Harvest), same as a placed pie.

2. **See-through lid fixed.** `ClothSkirt` (16 x 3 x 16) had no top face; `ClothTop` above it is 15 x 15, so a half-unit
   ring of the skirt's top was open and showed the blue neck/glass inside (visible as a light-blue line under the lid,
   also in the old icon). `ClothSkirt` now draws its top face from a new 16 x 16 gingham region at (1, 70) of
   `CricketJar_Texture.png` (a copy of the cloth top's own painting, rows 70-85 were empty). The glass windows stay
   cut-out (alpha 0/255, double-sided), as models-a made them; no faces needed `"transparent": true`.

3. Icon re-rendered with a copy of `art/build/artpolish/icons.mjs` in `build/artpolish/` (gitignored) pointed at this
   worktree by `build/target.mjs`: same 64 x 64 three-quarter orthographic render. The lid ring is gone from it.

## Verified

- `holes.mjs`: 0 open faces (the Floor bottom lies on y 0 under the drawn glass bottom). `zfight.mjs`: 0 visible pairs.
  `sizecheck.mjs`: all whole. `alphacheck.mjs`: only the 4 glass sides (the intended windows).
- `texture_check` CricketJar_Texture.png with the model: PASS, no findings.
- `asset_check` jar item: 0 errors, 1 warning (the plugin type CozyRanchOfferFood, as before). Hitbox: clean.
- `asset_validate` project `_worktrees/CozyTales-Ranching-fix2-jar`: valid, 0 errors (13 plugin-type warnings).
- `.\gradlew.bat build` green; `check-lang` 0 missing.
- Test cases `cricket jar` (models-a) and `cricket jar` (taming), run as copies in `build/tests-jar/` pointed at this
  worktree: green. The models-a golden `tests/hytale/golden/models-a/cricket-jar.png` was updated after looking (the
  diff is the 1-unit lift and the filled lid ring).

Pictures (this worktree):
- `notes/fix2-jar/jar-item.png` item views (three-quarter, top, bottom, back), final
- `notes/fix2-jar/jar-held.png` held by the player model, final
- `notes/fix2-jar/jar-placed.png` the model on the one-block grid at placed size (0.27 x 0.31 blocks, bottom at y 0),
  final geometry; model_render cannot draw a block's hitbox for an item model, so the hitbox is only by numbers.

## Not seen in the client

- Placing (crouch + right-click), the placed jar's size and seating, its hitbox outline, breaking/dropping, and that
  the plain right-click still offers to a frog with the Condition in front. Nobody has run a server on this branch
  (no port in this wave). Check that crouch + right-click while looking at an animal does not place a jar behind it
  (vanilla food behaves the same way).

## REQUESTS

- None required. Optional (taming owner): `tests/hytale/taming.test.json` could add a step checking the jar's
  `BlockType` and hitbox; `models-a.test.json` already covers the model.
