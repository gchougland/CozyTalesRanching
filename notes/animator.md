# Animator: new animations for Cow, Chicken, Pig, Sheep, Frog

Branch `wave/animator`. There are 30 new clips: 25 adult clips and 5 baby `Happy` clips. All are bound in 9 new
model assets under `src/main/resources/Server/Models/CozyRanch/`. Each asset is a whole copy of the vanilla
asset with the new sets added. No vanilla file is touched.

## How the clips are made

- **Source.** Clips are code. `tools/animator/clips.mjs` holds one function per clip, written as deltas from the
  bind pose on the vanilla rigs, with vanilla node names. `tools/animator/gen.mjs build` turns them into
  `.blockyanim` files through `animation_build`. The files are 60 fps, carry all five channels per node, use
  whole-number times, and built with no warnings.
- **Assets.** `gen.mjs assets` writes the model assets. Each set has `BlendingDuration` 0.3 for loops and 0.15
  for one-shots, and one-shots have `Looping:false`.
- **Pictures.** `gen.mjs render` writes the pictures in `notes/pictures/animator/`, through our own assets.
- **Tests.** `gen.mjs tests` writes `tests/hytale/animator.test.json`.
- **Idle base pose.** Every clip includes the vanilla idle base offsets: tails hang, chicken wings fold, frog arms
  tuck. Without them the channels a clip leaves out fall back to the bind pose and the animal looks stiff.
- **Babies.** Baby clips live in `<Species>/Animations/<Baby>/` and are built on the baby rigs (Calf, Chick,
  Piglet, Lamb).
- **Eyes.** Pig, piglet and lamb squint with their eyelids, as their vanilla blinks do. The others squash the eye.

## Sets

Durations are at 60 fps. Ratings come from the offline renders only. None of this has been seen in the client yet.

| Asset | Set | Frames / s | Kind | Rating |
| --- | --- | --- | --- | --- |
| CozyRanch_Cow | Happy | 60 / 1.0 | one-shot | good |
| | EatTrough | 80 / 1.33 | loop | good |
| | Love | 90 / 1.5 | one-shot | okay |
| | Graze | 120 / 2.0 | loop | good |
| | TailSwish | 150 / 2.5 | loop | okay |
| CozyRanch_Cow_Calf | Happy | 60 / 1.0 | one-shot | good |
| CozyRanch_Chicken | Happy | 60 / 1.0 | one-shot | good |
| | EatTrough | 60 / 1.0 | loop | good |
| | Love | 90 / 1.5 | one-shot | good |
| | DustBathe | 90 / 1.5 | loop | okay |
| | PeckGround | 60 / 1.0 | loop | good |
| | FlapHappy | 75 / 1.25 | one-shot | good |
| CozyRanch_Chicken_Chick | Happy | 60 / 1.0 | one-shot | okay |
| CozyRanch_Pig | Happy | 60 / 1.0 | one-shot | good |
| | EatTrough | 80 / 1.33 | loop | good |
| | Love | 90 / 1.5 | one-shot | good |
| | MudRoll | 120 / 2.0 | loop | okay |
| | Snuffle | 90 / 1.5 | loop | good |
| CozyRanch_Pig_Piglet | Happy | 60 / 1.0 | one-shot | good |
| CozyRanch_Sheep | Happy | 60 / 1.0 | one-shot | good |
| | EatTrough | 80 / 1.33 | loop | okay |
| | Love | 90 / 1.5 | one-shot | okay |
| | Graze | 120 / 2.0 | loop | good |
| | Pronk | 60 / 1.0 | one-shot | good |
| CozyRanch_Sheep_Lamb | Happy | 60 / 1.0 | one-shot | good |
| CozyRanch_Frog | Happy | 60 / 1.0 | one-shot | good |
| | EatTrough | 80 / 1.33 | loop | okay |
| | Love | 90 / 1.5 | one-shot | good |
| | LilyHop | 72 / 1.2 | one-shot | good |
| | CroakSit | 120 / 2.0 | loop | good |

What each clip does, and what to improve:

- **Happy** (cow, calf, pig, piglet, sheep, lamb). The animal crouches, hops (higher for babies), raises its tail and
  squints with its head up. It reads well from far away.
- **Chicken Happy and FlapHappy.** Both open the wings fully. FlapHappy flaps at 5 Hz with a small hop.
- **Chick Happy** (okay). The chick's wings are tiny quads that barely show. It reads mostly as a bob and a squint.
  To improve it, add more body bounce.
- **Frog Happy.** A big stretch upward with the arms out.
- **EatTrough.** A head-down chomp at trough height, shallower than Graze, with a jaw chew.
  - Sheep (okay): the motion is small under the wool helmet. It needs a bigger jaw or head bob.
  - Frog (okay): a tongue flick and gulp. The gulp stretch is subtle.
- **Love.** A slow sway, a head tilt, closed eyes and a tail wag. It is authored to start and end at rest, but it is
  bound as a one-shot (`Looping:false`).
  - Cow and Sheep (okay): too subtle from 10 blocks. Give them a larger side-to-side sway.
- **Graze** (cow, sheep). The head goes down to the grass, tears grass with a small tug, and the tail moves. The cow
  shifts its front feet a little. Foot contact was checked on the strip only.
- **Cow TailSwish** (okay). It is bound as a loop. Only the tail chain and an ear flick move, which is fine as an
  overlay but quiet when played alone. If it should be a one-shot, change `Looping` in the asset.
- **Chicken DustBathe** (okay). The chicken drops its belly, rolls, spreads its wings and shakes. It is busy and the
  wings clip the ground a little at the low point. To improve it, use fewer overlapping shakes and add a fluff
  particle (not this role's job).
- **PeckGround.** A double peck and a look around. It reads well.
- **Pig MudRoll** (okay). The pig rolls onto its back, rocks and kicks its legs. The head counter-rolls so the face
  stays readable. The body sinks into the ground at the side extremes, and the legs pass through the ground in
  places. It needs a hand-tuned ground height per angle.
- **Pig Snuffle.** The nose goes low, sweeps from side to side and sniffs.
- **Sheep Pronk.** A stiff-legged four-foot jump with the head up. Charming.
- **Frog LilyHop.** It crouches, jumps a high arc with a 360° turn, then lands with a squash.
- **Frog CroakSit.** The frog sits with a throat puff (the jaw moves forward and down) twice per loop.

Known limits that apply to every clip:

- **Wool not rendered.** The sheep pictures show the shorn vanilla model, because the renderer does not attach the
  wool. The clips are built for the shorn rig and the wool follows the body nodes.
- **Untested in the client.** Blending, layering against the walk and idle slots, and in-world scale are unproven.

## Verification

- `asset_validate` on `_worktrees/CozyTales-Ranching-animator`: valid, 0 errors, 0 schema errors.
- Gradle build: green.
- `tests/hytale/animator.test.json`:
  - Case one is the asset gate.
  - Case two renders all 30 sets through their assets and expects `nodesNotInModel` to be empty.
  - Six steps have goldens in `tests/hytale/golden/animator/`: Cow Graze, Chicken FlapHappy, Pig MudRoll, Piglet
    Happy, Sheep Pronk and Frog LilyHop. They were opened and accepted.
  - `test_run` is green with 0 different pixels.
- Per-tick cost: none. These are assets only, with no code.

## REQUESTS

1. Accept `tools/animator/` (`clips.mjs`, `gen.mjs`, `lib.mjs`, plus the helpers `sum.mjs` and `tree.mjs`). This is
   the clip source and generator. It is outside my owned paths, because the auto-review refused to run scripts from
   a temp folder. If it is not wanted in the repository, move it to a dev-only folder. The built files do not depend
   on it.
2. Taming/coordinator: the roles must use these assets. Set each role's `Appearance` (or model) to `CozyRanch_Cow`,
   `CozyRanch_Chicken`, `CozyRanch_Pig`, `CozyRanch_Sheep` and `CozyRanch_Frog`, and the babies to
   `CozyRanch_Cow_Calf`, `CozyRanch_Chicken_Chick`, `CozyRanch_Pig_Piglet` and `CozyRanch_Sheep_Lamb`. Without
   this the new sets are never found.
3. Whoever plays the sets: one-shots (`Happy`, `Love`, `FlapHappy`, `Pronk`, `LilyHop`) hold their last frame, which
   is the rest pose. Loops are meant for an action slot while the NPC stands still.
