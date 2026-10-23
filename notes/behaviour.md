# Behaviour role: trough snacks, environment, social, toys, personalities

Branch `wave/behaviour`. Built, unit tested, gated, and run on a local server (port 15542, insecure,
headless bot, cows only) nine times. What was and was not seen running is listed below; nothing visual
(clips, sounds, how it looks) was seen, because there is no client in the loop.

## What I built

| File (`behaviour/` unless noted) | What it is |
| --- | --- |
| `Template_CozyRanch_Animal.json` (role) | Three new states, all `IgnoreMissingSetState`: `Behave` (Leash sensor, Seek the leash point at 0.4 speed with the pathfinder, else stand), `Tag` (walk behind the entity in `LockedTarget`, watch it when close), `Meet` (walk up to a `LockedTarget` and face it). No other state changed. |
| `Server/CozyTalesRanching/Behaviour.json` | All tuning: decision timing, distances, ground tags, toy block map, 22 behaviour definitions, per-species adult/baby clip lists, personality weights. Reloaded by `BehaviourFeature.reload`. |
| `BehaviourConfig` | Gson model of the file, with normalising and lookups. |
| `BehaviourRules` | Pure rules, no engine types: may it start, may it continue (with a reason), which options exist, weights and personality multipliers, the pick, durations, cooldowns (seconds or a fraction of the day), the 60 s event limit, the personality roll, ground tag matching. All unit tested. |
| `BehaviourPlanner` | The once-a-second pass per world: personality rolls, toy scans, per-resident decisions, sessions (walk, act, stop), the shy reaction, events, `force`, `stopAll`. |
| `BehaviourSystem` | `TickingSystem` that queues `planner.pass(world)` with `world.execute` once a second. All store writes happen there, outside ticks. |
| `WorldBlocks` | Reads only loaded sections. A ground sample is about 14 block reads, taken only when a "here" behaviour with a ground rule is being considered. |
| `ToyIndex` | Per-house toy cache: one house scanned per pass, each house again every 60 s (`ToyScanSeconds`), a disc of `ToyScanRadius` around the door, 2 below to 3 above. |
| `BehaviourFeature` | Loads config (bundled, or `<data>/Behaviour.json`), registers the system, listens to `AnimalBehaviourEvent` to count received events, `reload()`. |
| `BehaviourCommand` | `/ranch behaviour status [--animal=]`, `force <animal> <behaviourId>`, `toy <blockId> [--behaviour=toy_ball]`, `log on|off`. Admin only. `<animal>` is `nearest`, `baby`, `adult`, an id prefix or a name. |
| `cozyranch_behaviour.lang` | Behaviour names, personality names, command text, force refusal reasons. |
| `BehaviourRulesTest` (15 tests), `tests/hytale/behaviour.test.json` | Unit tests; gate and asset checks for the template and two variants. |
| `tools/sessions/behaviour-live.json`, `behaviour-tests.json` | The live session and the copy of the existing test cases pointed at this worktree. |

## How it stays out of the way

The planner only starts something when all of these hold: by day (0.25 to 0.75), the record is owned,
ACTIVE, housed, not following, its entity is loaded with a ranch role, the NPC is in `Idle`, it is
inside the home range, and nothing interacted with it in the last 10 s. While a session runs, every pass
checks the same things again. If the state is no longer ours (GoHome at dusk, Follow/Stay from taming, a
role swap when a baby grows up), the planner drops the session **without touching the state**. It only
sets `Idle` back when the animal is still in one of its own states (session ended, night came while it was
standing in `Behave`, it left its house). The house's day pass already ignores anything that is not
`Idle` or `GoHome`, so `Behave`, `Tag` and `Meet` are invisible to it. Seen live at nightfall:

```
04:33:25 Ranch animal 7bd28519-… walks home to house eae624e9-… from 6.5 blocks from the door (dy 0.0)
04:33:25 Ranch animal 7bd28519-… stops graze (NOT_IDLE)
04:33:25 Ranch animal f97afe86-… stops nap (NOT_IDLE)
04:33:31 Ranch animal f97afe86-… went into house eae624e9-… at the door
```

## Verified on the live server

Setup per run: barn from `/ranch house debug place cow`, a filled trough, `Soil_Hive` registered as a
`toy_ball` toy, `/ranch breed testhouse cow` and `/ranch breed morning` at midnight, then midday, paused.
Quotes are from the server log with `/ranch behaviour log on`.

- **Personalities rolled and saved.** Every owned record without one gets one; the saved
  `Animals/default.json` in the run folder held `"personality": "lazy"`, `"playful"`, `"curious"`.
  `04:34:59 Ranch animal f6fe94fc-… (cow) has personality lazy`
- **Baby follows parent.** `04:35:12 Ranch animal 4aba4ece-… (cow baby, playful) starts follow_parent` /
  `fired AnimalBehaviourEvent follow_parent (witness 11111111-…)`; it chose it again unforced at 04:36:27.
  Status showed it in `Tag.Default`.
- **Other behaviours started and acted, unforced:** `graze` (Graze), `tail_swish` (TailSwish),
  `socialise` (two cows walk together, Happy, both fire), `nap` (Sleep, the baby), `toy_ball` (walked to the
  toy, Happy, toy sound, event). Across runs: graze, tail_swish, socialise, nap, toy_ball, follow_parent.
- **Events.** `/ranch behaviour status` counts what the planner fired and what the event bus delivered; they
  always matched, e.g. `fired: follow_parent 1, graze 1, nap 1, tail_swish 1, toy_ball 1` and
  `received:` the same. The witness was the bot each time.
- **Cost.** `status` reports the last pass: 34 to 82 µs with no block reads on a normal pass, 428 µs on a
  pass that took a ground sample and a toy scan (22 block reads), three residents.
- Existing cases run against the worktree (copy in `build/tests-copy`, session
  `tools/sessions/behaviour-tests.json`): animal, taming, house, breeding and behaviour all pass, including
  their gates (asset_validate and ui_check_java on this worktree). `test_run` ran the whole `tests/hytale`
  folder: 25 of 26 cases pass; `houseui` "house page" fails with "different size" against
  `golden/houseui-empty.png`. This branch changes no UI, Java UI or golden files, so that failure is already
  on the base commit.
- Gate: `.\gradlew.bat --no-daemon build` green (all unit tests), `node tools/check-lang.mjs` 0 missing,
  asset_check of the template 0 errors 0 warnings.

## Not working or not verified

- **Trough snacks never finished live.** The cow picks a trough, walks toward it in `Behave`, and in every
  run stopped 3.6 to 6 blocks short and gave up after 20 s (`gives up trough_snack (4.2 blocks short)`), so
  `EatTrough` was never played next to a trough. The same adults also failed to reach the door after
  nightfall (house `GoHome`, same Leash + Seek pattern), while the small baby reached both toy and door. In
  every test world the adults came out of the barn 8 to 9 blocks above the door (`dy 8.0` in the house's
  own log) and the barn, trough and toy sat in a hollow. So I think this is adult-cow pathing in these
  worlds, not the trough logic, but I could not prove it. Fixed along the way: the trough is two blocks long
  and the first spot was inside it (now 2.1 from the block centre on the animal's side).
- `approach_player` / `greet_owner` started but never reached the bot (the bot stands still and the cows
  stalled 9 blocks off, same pattern as above). The shy retreat was never triggered.
- Only cows were spawned. Chicken, pig, sheep and frog behaviours (peck, dust_bathe, flap, mud_roll,
  snuffle, pronk, lily_hop, croak) and their ground tags are covered by unit tests and config checks only.
- Real toy blocks do not exist yet; the toy path was tested with `Soil_Hive` through `/ranch behaviour toy`.
  `RanchSounds.TOY_PLAY` is played but was not heard.
- Nap uses the vanilla `Sleep` clip where the model has one; the chick has none and stands still
  (`AllowStill`). A short "settle down / curl up" clip with a looping breathing hold would make naps read
  as naps instead of night sleep, and let chicks nap too.
- Clips play in the `Emote` slot, forced; how they blend with walking was not seen.
- The server died once mid-run (exit -1, no exception, no crash file; log just stops). Not reproduced in
  the next seven runs.

## Decisions

- **Engine does the moving, Java decides.** The role states only walk to the leash point or follow a
  marked entity; Java picks what, where and when once a second, plays the clip on arrival and fires the
  event. No per-tick Java.
- **Separate states instead of reusing Idle.** The house and taming code key off `Idle`/`GoHome`/`Follow`/
  `Stay`; new states mean neither has to know about behaviours, and any of them can take an animal back at
  any time.
- **Trough snacks are flavour only**: no food taken, no happiness change; once per quarter day per animal
  (`CooldownDayFraction`), only troughs that have food, within `TroughRange` of its own door.
- **Ground checks happen at decision time only**, and only for behaviours that ask for a ground tag.
- **Personality** is a uniform roll over the five, rolled lazily by the planner for any owned record with
  none (so existing saves get one), saved through `markDirty`. It scales weights per behaviour (exact id or
  `toy_*` prefix), the decision interval and the idle weight. Shy animals step away from strangers and never
  approach players; curious and affectionate ones are the only ones that walk up to players or the owner.
- `approach_player`, `greet_owner` and `retreat` do not fire `AnimalBehaviourEvent` (`Event: false`):
  they are reactions to a player, not something to witness.
- Events: at most once per animal per behaviour per 60 s; witness is the nearest player within 16 blocks or
  null.

## REQUESTS

1. **`command/RanchCommand.java`** (shared): register the subcommand. My copy has this line; **do not merge
   my copy**, add it in yours:
   ```java
   this.addSubCommand(new com.hexvane.cozytalesranching.behaviour.BehaviourCommand());
   ```
2. **Toy blocks** (models / care): `CozyRanch_Toy_Ball`, `CozyRanch_Toy_ScratchingPost`,
   `CozyRanch_Toy_HangingBell`, `CozyRanch_Toy_FloatingDuck`, `CozyRanch_Toy_Log`. The map is in
   `Behaviour.json` `Toys`; nothing else is needed from the planner.
3. **House** (`HouseResidents.findSpot`): adult cows come out 8 to 9 blocks above the door in the test
   worlds while babies come out at the door. Worth a look, since behaviours, and the house's own walk home,
   then start from a roof or hilltop.
4. **Animator** (optional): a nap clip per species (see above), and a chick nap.
