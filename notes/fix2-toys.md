# fix2-toys: toys animate while an animal plays with them

Gibson: "Do the toys currently animate or do anything?" They did not: the animal walked over and played its own
clip and the toy sound; the toy block stood still. Now each toy moves for as long as the animal is playing, then
goes back to rest.

## What each toy does (one-second loop, ends at rest)

| Toy | Clip | Motion |
| --- | --- | --- |
| Ball | `Toy_Ball_Play.blockyanim` | rolls about 4 units forward (turning as it rolls), back, a small hop, a tiny bounce, rest |
| Scratching post | `Toy_ScratchingPost_Play.blockyanim` | base stays put; post, rope, cap and brushes wobble on the base (10, 7, 4, 1.5 degrees) |
| Hanging bell | `Toy_HangingBell_Play.blockyanim` | bell and rope swing front to back on the hook (24, 18, 11, 5 degrees, clear of the posts); clapper swings further, a beat late |
| Floating duck | `Toy_FloatingDuck_Play.blockyanim` | bobs up and down, turns 16 then 12 degrees each way, tilts, wings flick |
| Log | `Toy_Log_Play.blockyanim` | rocks on its round side about its long axis (13, 9, 4 degrees), rolling a hair each way |

All five models are flat (every node is a root), so a whole-toy motion is written per node as a rigid motion about
one pivot: orientation delta `q^-1 R q`, position delta `T + R(p - C) - (p - C)`. The generator was a scratch
script (not committed; the files are plain keyframes and can be edited by hand). Every animated node carries all
five channels, whole-number times, and the first and last keys are the rest pose.
`Toy_FloatingDuck.blockymodel` was not touched (SHA-256 `1a510aa2...` before and after); its clip uses its own node
names. Squash rule: the farthest vertex from any node centre is about 30 units (a log stave), under 64.

## How the trigger works

- Each toy item (`Server/Item/Items/CozyRanch/Toys/*.json`) has `BlockType.State.Definitions.Play` with
  `CustomModelAnimation` = its clip and `Looping: true`, the same pattern as vanilla benches (`CraftCompleted`).
- `behaviour/ToyAnimator` swaps the placed block to that state with the vanilla
  `BlockOperations.setBlockInteractionState(..., "Play")` and back with `"default"`, as chests and our trough do.
  The server sends the block change (`ServerSetBlock`, new block id); the client starts the state's clip.
- A toy session remembers its toy cell. At the end of every planner pass (one a second, a world task outside the
  tick) the toys that should play (acting toy sessions in that world) are compared with those playing, and only the
  difference is sent. So every way a session ends (finished, interrupted, animal gone, role change, reload) puts the
  toy back within the same pass; two animals on one toy keep it playing until both stop.
- A toy left in `Play` (server stopped mid-play: the state is saved with the chunk) is seen by the next toy scan of
  its house and put back unless an animal is using it. Reload (`stopAll`) resets every playing toy.
- The toy scan still finds a playing toy (`baseBlockId` strips `_State_Definitions_Play`). Breaking a playing toy
  drops the toy (state inherits `Gathering.ItemId`).
- Clips are one second, the planner's pass, so a stop after N whole passes lands close to the clip's rest frames.
- Test toys from `/ranch behaviour toy <blockId>` that have no Play state are left alone (`getBlockForState` is null).

## Proved

- `.\gradlew.bat build` green; `ToyAnimatorTest` (5): start, keep, stop, two animals on one toy, stale reset, the
  Play state id, and for every toy: a looping Play state naming its clip, clip nodes all in the model, five
  channels, whole rising times, rest at both ends.
- `asset_validate` valid (0 errors), `ui_check_java` valid, `check-lang` 0 missing.
- Test cases `art3` and `behaviour` green against this worktree (copies with paths pointed here); new case
  "toys animate while played with" renders each clip through the real item asset, with goldens in
  `tests/hytale/golden/behaviour/Toy_*_Play.png` (looked at before keeping).
- Live server on 15542 with the built jar, bot as player: barn by `/ranch house debug place cow`, ball, post and log
  by `/block set`, `/ranch breed testhouse cow`, noon, `/ranch behaviour log on`.
  - `force <cow> toy_ball`: `20:13:39 Ranch toy at -43 116 309 plays` (bot got `ServerSetBlock` id 6164 -> 6168),
    `20:13:44 ... back to rest` (6168 -> 6164), five seconds later as the session's length.
  - `force <cow> toy_log`: both cells of the two-cell log went 6158 -> 6171 (`ServerSetBlocks`, filler kept);
    `/block get` read `*CozyRanch_Toy_Log_State_Definitions_Play`; back to 6158 after the session.
  - Stale: `/block setstate -51 116 309 Play` on the post with no animal, then a rescan: 6167 -> 6156,
    `Ranch toy at -51 116 309 back to rest`.
  - Bell and duck were not run live (no chicken/frog house set up); same code path, assets checked by the test.

## What only the client can confirm

- That the clip actually plays on the placed block and loops, and how it looks at game scale and light.
- Whether the swap to `Play` and back pops (the clip starts and ends at rest, so it should not), and whether the
  block rebuild flickers on the swap.
- That the duck's bob reads well on water, and that the bell clapper looks like it strikes.
- Nobody here has heard the bell; the existing toy sound still plays once when the animal starts.

## REQUESTS

- To Gibson: place each toy by a house in the client, `/ranch behaviour force nearest toy_<ball|post|bell|duck|log>`,
  and watch it move then settle. Say if any motion is too small (the log is the gentlest) or too busy.
- To whoever owns the toy models (art): the models are flat; if a toy is ever rebuilt, a root group node per moving
  part would let clips rotate one node instead of every part.
