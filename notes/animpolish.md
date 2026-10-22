# Animpolish: wave 2 animation fixes

Branch `wave/animpolish`. Six clips were reworked, the colour frogs are generated from one source, and two
checking helpers were added. The other 24 clips are byte-for-byte unchanged; a full rebuild changed only the six.
Everything is still code in `tools/animator/clips.mjs`, built with `node tools/animator/gen.mjs build <filter>`.

## New helpers

- `tools/animator/ground.mjs <filter> [--wool] [--node=<regex>] [--all]` runs forward kinematics for a clip on its
  vanilla rig every frame. It reports the lowest box corner against the rig's bind-pose feet. This number tuned the
  pig's roll height and kept the chicken's wings out of the ground. With `--wool` it adds the sheep's wool pieces.
- `tools/animator/woolcheck.py make|clean` writes a temporary woolly sheep into `src/main/resources` and removes it
  again (**never commit it**). Vanilla draws the wool through `Sheep.json` `RandomAttachmentSets.Fleece` with
  `Wool_Attachment.blockymodel` and its own texture. The pieces are shapeless `isPiece` nodes named after the sheep's
  bones. `animation_render` drops attachments, so the script grafts the wool boxes onto the sheep model and packs
  both textures into one atlas. It also binds vanilla `Eat`, `Shake` and `Curious` so they can be compared.
- `gen.mjs strip` writes comparison strips (env `DIR`, `PREFIX`, `N` or `FRAMES`, `SIZE`, `YAW`, `PITCH`, `MODEL`).
- `gen.mjs` now points at this worktree, and the test gate's project path is derived from it.

## Per clip

Pictures are in `notes/pictures/animpolish/`. `before_*` and `after_*` are 8-frame strips with the old settings.
`beforedetail_*` and `detail_*` show the same frames large.

| Clip | What changed | Still weak |
| --- | --- | --- |
| Pig MudRoll | Now 150 frames, a loop (was 120). The pig folds its legs, tips over and rolls a full turn onto its back. It rocks there with all four legs kicking in the air, its head lolling and its ears flopped, then rolls on over and stands up with a little shake. The body height follows the rolling box's lowest corner. The lowest point is 0.6 units below the bind-pose feet, where it was about 15 before. | On the back the head is upside down and sometimes faces away from the camera. The legs are short stumps, so the kicking reads through rhythm more than shape. |
| Chicken DustBathe | Now 120 frames (was 90). There is one slow lean to each side, the high wing lifts, and there is one fluff shake per loop instead of five overlapping shakes. The wings spread wide and flat. The belly sits 5 units down, and the wings stay at least 5.9 units above the ground (they went 12 below before). | At the lean the belly box dips 1 unit, which reads as nestling into dust. The wings look like thin blades when seen edge-on from a low side view. |
| Chick Happy | The wings unfold from the folded-back rest to the sides. They grow 1.5 times along their length and flap between 35° and 120° at 6 Hz, so each stroke reads. The hop is unchanged. | The wings are still small quads. From straight in front they are seen nearly edge-on. |
| Cow Love | `quadLove(R, { big: 1.8 })`: the whole body sways in yaw, the neck yaw and head roll are 1.8 times larger, the head rises, the two hops are higher with a front-up pitch, and the tail wags more. The pelvis roll was kept at 5° because a larger roll sank one side's feet. The chest pitch was removed for the same reason. Feet dip 2.1 units at the sway, where they dipped 3.5 before. | Pig Love is unchanged (`big` defaults to 1). The hearts particle would help more than more motion. |
| Sheep Love | The same with `big: 1.5`. At 1.8 the woolly head cap tipped so far it looked like it was sliding off (seen on the woolly render). | |
| Cow TailSwish | The tail hangs and swats across the rump in two 80° arcs, flicking up at each end. The cow looks round at its flank (neck yaw 50°, head 30°), and a hind foot stamps once. The ear flick, breathing and blink stay. The look round is what reads from the front; the swish reads from behind. | It is still a loop with a quiet second half (breathing and an ear flick), as wave 1 left it. |

## Sheep with wool

I checked all five sheep clips (Happy, EatTrough, Love, Graze, Pronk) on the woolly render
(`wool_Sheep_*.png`) and with `ground.mjs --wool`. No wool box goes below the feet in any clip. The wool is fixed to
the bones, so it follows every pose. The only fix needed was Sheep Love, made smaller as described above. In Graze the
head goes into the big neck wool and only the snout shows below it, which is what vanilla `Eat` does on the same
render. The ears poke through the head wool, as they do in vanilla. The lamb has no wool attachment.

## Frog colours

`gen.mjs assets` now writes `CozyRanch_Frog`, `CozyRanch_Frog_Blue` and `CozyRanch_Frog_Orange` from one entry list:
the vanilla `Frog_Green.json` with a `Texture` override. Regenerating gives files with the same content as the
coordinator's copies; only the texture line differs between the three. The test file now renders every frog set
through all three assets (40 clip steps).

## Verification

- `asset_validate` on `_worktrees/CozyTales-Ranching-animpolish`: valid, 0 errors, 0 schema errors. One warning,
  about the Crickets item's plugin interaction type, which is not mine.
- `animation_build`: all 30 clips built with no warnings.
- `test_run` with filter `animator`: 2 of 2 cases pass. Goldens were updated or created for MudRoll, DustBathe,
  Chick Happy, Cow Love, Sheep Love and Cow TailSwish, and I opened each one. The FlapHappy golden was rewritten to
  identical pixels.
- `.\gradlew.bat --no-daemon build`: BUILD SUCCESSFUL.
- Per-tick cost: none (assets only).
- Not seen in the client: the blending into and out of the 150-frame roll, and whether a looping MudRoll should
  instead be played once.

## REQUESTS

1. Accept `tools/animator/ground.mjs` and `tools/animator/woolcheck.py`. They are dev helpers beside the wave 1
   generator, outside my owned paths, like the rest of `tools/animator`.
2. Taming/house: MudRoll now starts and ends standing, so it can be played once as a one-shot. If a caller wants
   that, set `Looping: false` on the set in `gen.mjs` (one line).
3. Whoever owns particles: a small heart burst on Love and a dust puff on DustBathe and MudRoll would carry them at
   ten blocks better than more body motion.
