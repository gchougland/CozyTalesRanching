# Wildlife (Design doc §8)

Branch `wave/wildlife`, worktree `_worktrees/CozyTales-Ranching-wildlife`.

## Built

- **Visitors.** `WildlifeSystem` adds up time per world and every `PassSeconds` (45) queues `WildlifeWorld.pass` with
  `world.execute` (no store writes in the tick, no disk IO on the world thread). A pass first updates its own visitors,
  then looks at houses whose next visit is due (4 to 10 minutes, random per house). A house is only considered when
  its origin chunk is loaded, its owner is online and a player is within `PlayerRange` (48). Attraction comes from
  care's cached `HouseRecord` fields (requirements met, score, missing features), residents' happiness, trough
  contents (`CareApi.troughsNear`) and stored products; nothing is rescanned. A house's own species comes when
  requirements are met and score >= 0.5 (weight x score x happiness factor); other species come for attractors in
  `Wildlife.json` (frog to a sty with water and mud, chicken to a fold with flowers or a barn with hay, sheep to a barn
  with grass and hay, cow to a fold with grass and open space). The strongest draw is the chance a visit happens; the
  species is drawn by weight; per-species cooldowns per house.
- **Spawn spot.** 24 random columns 12 to 24 blocks out (`SpotRules`, reads loaded sections only): solid non-leaf
  ground with two clear dry cells, three of four neighbours walkable (never on a wall, fence or pillar top), open sky
  for 12 cells (never inside a build), water within 3 for frogs; then the engine's own `SpawningContext.canSpawn` for
  the role's model. Spawned with `NPCPlugin.spawnEntity` as the vanilla wild role; its leash point is set to the house
  door, so the vanilla Leash/ReturnHome behaviour walks it in.
- **Leaving.** After its stay (5 to 9 min) the leash point moves 48 blocks out past the house and it walks off; it is
  removed when no player is within 28, or after 60 s with no player within 10, or after 240 s anyway.
- **Ownership.** Visitors are remembered by entity UUID in `<data>/Visitors/<world>.json` (atomic write with `.prev`,
  corrupt file kept and `.prev` read; debounced on the wildlife thread; flushed at shutdown). Only entities in that
  file are ever touched. If the role changes or `Ranch.animals().byEntity` knows it (tamed), it is forgotten and stays
  a ranch animal. If its chunk is loaded and it is gone, it is forgotten. Unloaded and overdue for 72 h: forgotten,
  stays an ordinary wild animal.
- **Caps** (data-driven): 1 per house, 3 per owner, 8 per world, 2 arrivals per pass.
- **Rare variants**, one per species: Golden Chicken (flowers, >= 16 corn in troughs), Red Highland Cow (hay, >= 2
  cows with average happiness >= 0.8), Spotted Pig (mud, >= 3 truffles stored), Black Sheep (grass, flowers, habitat
  score >= 0.9), Purple Frog (lily pads, reeds, >= 2 frogs). 25% of qualifying visits, 60 min cooldown. Wild roles
  `Server/NPC/Roles/CozyRanchWild/*` (copies of the vanilla Variant with the rare model; no vanilla Variant references
  a concrete role), ranch roles `Server/NPC/Roles/CozyRanch/Rare/*`, mapped in each species' `WildRoles` and
  `RanchRoleVariants`, so taming keeps the colour and release returns the rare wild role.
- **Art**, by script from vanilla textures (`notes/pictures/wildlife/recolour.py`): `Common/NPC/CozyRanch/Rare/*.png`;
  models `Server/Models/CozyRanch/Rare/*` use `Parent` on our ranch models. No vanilla model or texture is copied.
- **Lang** `cozyranch_wildlife.lang`: `hint.<species>` and `hint.<species>.rare` for all five, role names, chat lines,
  command strings. The journal already picks the hints up.
- **Event** `RanchVisitorArrivedEvent` (after the save is scheduled), owner chat line when the owner is within 48
  ("A wild Chicken wandered over to Chicken Coop."; rare: "Something special: ..."), `RanchSounds.VISITOR_ARRIVED`.
- **Command** `/ranch wildlife status | spawn <house> [--rare] [--species=<id>] [--stay=<s>] | clear | pass`
  (admin; house = `near`, its name or id prefix). `spawn` respects caps; `--rare` forces the variant.

## Verified

- Unit tests (`src/test/java/.../wildlife`, 33): scoring, happiness factor, attractors, cooldowns, pick chance and
  weights, caps order, every rare condition, rare roll chance and cooldown, role fallback, stay and visit ranges,
  leave rules, spot rules on a fake grid (roofs, canopy ok, leaves, water, wall tops, slopes, unloaded, frog water,
  ring radii), store round trip, corrupt file fallback, config covers all species and every role file exists.
- Live, port 15541 only, server stopped after each run (`tools/sessions/wildlife-*.json`, read with
  `notes/pictures/wildlife/readlive.py`):
  - forced pass at a coop with corn in a trough (care status `score 0.70, missing hay, flowers, toy_bell`; wildlife
    status `chances chicken 0.63, rare flowers`):
    `Wildlife: a visitor 8430f5a5-... (Chicken) arrived for house c0caccba-... at -293.5 122.0 -73.5, 23 blocks out, stays 515 s`
    and the owner got `cozyranch_wildlife.arrived {animal: cozyranch_species.chicken.name, house: ...ChickenCoop.name}`.
  - house cap: second spawn `cozyranch_wildlife.spawn.failed {reason: CAP_HOUSE}`.
  - forced rare: `Wildlife: RARE visitor e30ce302-... (CozyRanchWild_Chicken_Golden) arrived ... 18 blocks out, stays 15 s`,
    chat `arrived.rare {animal: cozyranch_wildlife.rare.chicken}`; then
    `... walks off after 34 s` and `... left after 45 s walking off, nearest player 86 blocks`; status lists 0.
  - eligibility: status shows `eligible false` once the player is 90 blocks away.
  - owner cap (with the override `tools/sessions/wildlife-caps.Wildlife.json`, MaxPerOwner 2, loaded by `/ranch reload`):
    two `spawn.ok`, then `spawn.failed {reason: CAP_OWNER}` twice (third chicken, and a frog).
  - natural visits from the timer alone (same override: visits every 1 to 1.5 min, stays under a minute):
    `house 866f5d2d-... (chicken) may draw a visitor in 60 s`, then three arrivals, e.g.
    `a visitor 876c8d4a-... (Chicken) arrived ... 21 blocks out, stays 49 s`, status distance 21 -> 9 -> 8 (it wandered
    in), `walks off after 60 s, 8 blocks from its house`, `left after 30 s walking off, nearest player 32 blocks`.
  - the journal role's listeners fired: `cozyranch_journal.memory.first_visitor` and `first_rare`.
- Existing cases: `python notes/pictures/wildlife/regress.py` copies `tests/hytale/*.test.json` (the nine in
  `tools/sessions/tests.json` plus mine) with worktree paths and port 15541 into `build/wildlife-regress`;
  `tools/sessions/wildlife-tests.json` runs them: 31 of 32 passed first time; care's "feed in the trough" failed at
  the house placement check and passed on rerun (see Notes).
- Pictures, normal beside rare, two angles each, opened and judged: `notes/pictures/wildlife/{chicken,cow,pig,sheep,frog}-rare.png`.
  The sheep is shown with its wool grafted on (offline renders drop `RandomAttachmentSets`); the shorn render first
  showed pink skin between dark tufts like a Holstein, so the skin was darkened too and the eyes kept.
- Goldens `tests/hytale/golden/wildlife/*.png` (`tests/hytale/wildlife.test.json`: gate, asset_check of every rare
  role and model, renders, texture_check). Gates: asset_validate valid, 0 schema errors, 12 known warnings;
  ui_check_java valid; build with no warnings; check-lang 0 missing; texture_check 0 FAIL (3 WARN, 3 PASS).

## Not verified or faked

- Taming a visitor live: the bot cannot use entities. The code path (record found by entity UUID or role changed,
  then forgotten) is reasoned, not run. Taming itself is untouched; the taming test cases pass.
- Owner offline and a blocked-taming world were not run live (needs a second player / world config).
- Natural visits were watched with fast override timings; the bundled 4 to 10 minute timings use the same code.
- Rare conditions arising naturally were not run live; rares were forced. The status line shows what is missing.
- Leaving relies on the vanilla Leash behaviour; on a fenced ranch a visitor may not get in or out on foot. It is
  still removed by the leave rules (at the latest 4 x LeaveSeconds after its stay).
- Babies of rare parents are normal (the variant does not breed true) except frogs, which have no baby role.

## Decisions

- No component on visitors: our own save by entity UUID is the only mark, so nothing else's entities are touched and
  a tamed visitor stops being ours at once.
- Forced admin spawns ignore habitat but not caps.
- Rare conditions use cached data only; "lots of flowers" for the black sheep is approximated by habitat score >= 0.9.
- Rare role names use `server.cozyranch_wildlife.role.*` like vanilla's `server.npcRoles.*`.

## Notes for others

- Renders in a project context read textures from `build/resources/main`: rebuild before `updateGoldens` after a
  texture change, or the golden keeps the old texture.
- `test_run` takes `directory` and `filter`, not `path`: `tools/sessions/tests.json` runs every file each time.
- `/ranch house debug place cow` sometimes logs `House ... block at ... is now Empty` a second after placing on uneven
  ground; that is what made care's live case flaky.

## Costs

No fal or other paid calls. Five live server runs on 15541 (3 to 13 minutes each), two regression runs.

## REQUESTS

1. `command/RanchCommand.java` (shared): add after the journal line
   `this.addSubCommand(new com.hexvane.cozytalesranching.wildlife.WildlifeCommand());`
   I added it locally to test; do not merge my copy (it is not committed).
2. Journal role: regenerate `tests/hytale/journal.test.json` (`JOURNAL_WRITE_CASES=true`) once
   `cozyranch_wildlife.lang` lands: the cow page now sends `cozyranch_wildlife.hint.cow` instead of
   `cozyranch_journal.wildlife.fallback`, so `JournalLayoutTest.testFileRendersWhatTheJavaSends` fails until then.
   My regenerated copy is local only.
3. Breeding role (optional): if rares should breed true, `BreedingRules.babyRole` needs rare baby roles; today babies
   of rare chickens, cows, pigs and sheep are normal.
4. Tools: `tools/sessions/tests.json` passes `path` to `test_run`, which ignores it; use `directory` or `filter`.
