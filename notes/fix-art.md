# fix-art (fix wave 1): purple frog eyelids, sheepfold wool, barn gables

Branch `wave/fix-art`. Asset changes only, no code. Pictures are under `build/fix-art/` in this worktree (not committed):
before/after sheets plus the scripts that made the changes (`build/fix-art/scripts/`).

## 1. Rare purple frog: green eyelids (done)

- The rare frog uses the vanilla frog model (`Common/NPC/Critter/Frog/Models/Model.blockymodel`) with
  `Common/NPC/CozyRanch/Rare/Frog_Purple.png`. Both eye boxes share one UV block, x 74-104, y 0-14: top 79,0, bottom 89,0,
  left 74,5, front 79,5, right 89,5, back 94,5. The earlier recolour had left that block in the green frog's colours, so
  the lids (the top face, the sides, the back and the rim texels of the front face) stayed green.
- Repainted those 183 green texels into the body's violet. Each texel keeps its painted light and dark from the original,
  so the top of the lid is still the lightest part and the edges are still shaded. Darker texels turn slightly towards
  blue-violet and lighter ones towards magenta, matching the body. This is how vanilla `Blue.png` handles the blue frog's
  lids. The yellow eye, pupil, highlights and orange rim are untouched.
- The model is unchanged. `texture_check`: PASS, no findings.
- The mint and pink speckles on the back and head are the intended "warts turned pink" pattern, not leftover green.
- Pictures: `build/fix-art/frog_before.png`, `build/fix-art/frog_after.png` (front and three-quarter, 768 px);
  texel close-ups in `build/fix-art/frog_eye_region_before.png` and `frog_eye_region_after.png`.

## 2. Sheepfold: wool on the sides (done)

- The wool was 12 separate boxes (`Wool` to `Wool12`) stuck to the four walls and one corner post. They were deleted from
  `Sheepfold.blockymodel` (191 nodes down to 179). The diff is deletions only.
- Every wall and post face under the tufts was already drawn, because the tufts sat on top of whole faces, so no holes
  open. Nothing was moved or resized, so there's no new z-fighting and no fractional sizes. The texture is unchanged; the
  wool's atlas cells are now just unused.
- Depth goes from 3.02 to 3.01 blocks, since a tuft used to stick out past the front.
- Pictures: `build/fix-art/sheepfold_before.png`, `build/fix-art/sheepfold_after.png` (front, right, back, left).

## 3. Cow barn: gable looks like a grid (done)

- Cause: each gambrel gable is a stack of 14 step boxes (`Gable` to `Gable14`), 1-3 units tall. Each step's front and back
  strip was painted on its own: vertical board lines that didn't line up from step to step, plus a top-light/bottom-dark
  gradient on every 3-texel strip. Together that read as a grid.
- Fix: repainted all 28 gable strips, front and back, from the wall face's own boards (the walls Gibson likes), keyed to
  the world position. Strip column u takes wall column `(108 - W)/2 + u`, and strip row v takes wall row
  `(103 - stripTop) + v`. Board seams now run unbroken from the eave band to the peak. The light and wear are the wall's,
  so there's no per-strip banding and no single-texel noise. The front gable uses the front wall's boards, and the back
  gable uses the back wall's.
- No UVs, sizes or other faces were changed. The script checked that no other face samples those rectangles.
  `texture_check`: PASS, no findings.
- Pictures: `build/fix-art/cowbarn_before.png`, `build/fix-art/cowbarn_after.png` (front, back, three-quarter), and a
  close-up `build/fix-art/cowbarn_gable_closeup_before_after.png`.

## Verification

- `asset_validate` on `_worktrees/CozyTales-Ranching-fix-art`: valid, 0 errors, 0 schema errors. The 13 warnings are
  the usual plugin-registered interaction types.
- `texture_check`: PASS for `Frog_Purple.png`, `CowBarn_Texture.png` and `Sheepfold_Texture.png` (through their assets).
- Tests: the committed test files point at the main checkout (`CozyTales-Ranching/src/...`), so they cannot see a
  worktree's changes. I ran worktree-pointed copies of `models-b.test.json` (all cases) and the `rare frog` case of
  `wildlife.test.json` from `build/fix-art/tests/`. First run: the barn and frog goldens differed as expected. After
  I looked at the new renders, I ran with updateGoldens, and all 4 cases are green.
- Goldens updated (opened and checked): `tests/hytale/golden/models-b/CozyRanch_House_CowBarn.png`,
  `tests/hytale/golden/models-b/CozyRanch_House_Sheepfold.png`, `tests/hytale/golden/wildlife/frog-rare.png`. The trough
  goldens were rewritten byte-identical (no diff).
- No Gradle build (no code touched).

## Not seen in the client

- All three changes were seen only in `model_render`. Look at the frog's lids in daylight, and at the barn gable from
  the ground, where the roof overhang shades it.

## REQUESTS

- After merge, the original `models-b` and `wildlife` tests (which render the main checkout) match the new goldens. Before
  merge, running them from this worktree would fail on the three updated goldens.
- The sheepfold icon `Common/Icons/ItemsGenerated/CozyRanch_House_Sheepfold.png` (not owned by me) was rendered with the
  wool tufts. Please re-render it from the new model. The cow barn icon is too small for the gable change to matter much,
  but could be re-rendered at the same time.
