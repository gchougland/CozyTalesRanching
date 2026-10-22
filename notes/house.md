# House role: registry, placement, assignment, home range, the enclosed night

Branch `wave/house`. Build, unit tests and the MCP gate are green, and one full night and morning ran on a
real server with the headless bot (port 15541). What that run did and did not show is under Verification.

## What I built

| File (`house/`) | What it is |
| --- | --- |
| `HouseGeometry` | Footprint per house block (from the block's hitbox file), rotation (turns like the engine's `rotate90Y`), door point 0.6 blocks outside the front face, `covers(x,y,z)`. |
| `HouseRules` | Pure rules: dusk 0.77 / dawn 0.23 of the day, `cycleDay`, `morningDue`, arrival distances, assign radius 8, name length. |
| `HouseSaveFile`, `PersistentHouseRegistry` | The `HouseRegistry` implementation. `<plugin data>/Houses/<world>.json`, same pattern as the animal saves: debounced on its own thread, `.tmp` then `.prev` then atomic move, corrupt file kept as `.corrupt-<ms>`, flush on `ShutdownEvent` (`SHUTDOWN_WORLDS + 4`) and a JVM hook. Also saves `Nightfall` (last evening each house went in), so a restart at night does not repeat it. |
| `HouseBlocks` | Block id to species, house lookup by cell, door position, loaded-chunk checks. |
| `HousePlacement` | `PlaceBlockEvent` / `BreakBlockEvent` systems. Place needs `cozyranch.house.place`. Break needs the owner or `cozyranch.admin.manageany`. The record work runs in a world task once the block is really there or gone. Fires `RanchHousePlacedEvent` (the api has no removed event). On removal, residents inside come out at the door (or go MISSING so recovery brings them back), every resident is unassigned with `AnimalHouseAssignedEvent`, and only the house record is deleted. |
| `HouseServiceImpl` | `assign`, `unassign`, `rename`, `setBreeding`, `resummon`, `isInside`, with the permission and owner checks of the contract. Installed with `Ranch.installHouseService`. |
| `HouseUse`, `UseHouseInteraction` | The assign gesture: Use on a house block (`CozyRanchUseHouse`, added to the five house items with an interaction hint). It assigns every owned, following, ACTIVE, unassigned animal of that species within 8 blocks. With none, it opens `Ranch.housePages()`, and if no page is installed it sends a chat summary. |
| `HouseResidents` | Leash to the door, Idle/GoHome state switches, `storeInside` (record goes STORED, entity removed), `bringOut` (fresh entity at the door, generation goes up, ACTIVE). |
| `HouseDaySystem` | Once a second per world with houses: home range by day, walk home at dusk, morning at dawn, block check. |
| `HouseDayHooks` | `register(Hook)` with `onNightfall` / `onMorning`, called once per house per evening / day. Morning runs **before** the residents come out, so a hook can add STORED babies that come out with them. |
| `HouseCommand` | `/ranch house list`, `debug place <species> [--rotation]`, `debug use`, `debug claim`, `debug nightfall`, `debug morning`. All need `cozyranch.admin`. Not registered (see REQUESTS). |
| `HouseFeature` | Wiring. |
| `Template_CozyRanch_Animal.json` | New `GoHome` state: the `Leash` sensor plus `Seek` with the pathfinder to the leash point (the door). |
| `cozyranch_house.lang`, `tests/hytale/house.test.json`, 3 JUnit classes (17 tests) | |

## The day, step by step

- **Day.** Each ACTIVE resident that is not following its owner is leashed to the door. Idle beyond
  `HomeRangeRadius` switches to GoHome, and GoHome within half that range (at least 2 blocks) switches back
  to Idle. Stay and other states are left alone.
- **Dusk.** Residents that are out are set to GoHome. One that arrives (1.6 blocks horizontally, 2.5
  vertically) goes inside: the record is STORED with its last position, then the entity is removed. One that
  has not arrived after 60 s goes in where it stands. A MISSING resident goes in at once. A following one stays
  out with its owner. When nobody is out, `onNightfall` fires once and the evening is saved.
- **Dawn.** `onMorning` fires once per day (`lastMorningDay`), then every STORED resident comes out. The
  spawn spot is the nearest free column within 4 blocks of the door that is not under the house, tested with
  the engine's `SpawningContext`, with a fresh context per column. The fallback is its last position. If both
  fail, it retries every 30 s.
- A house whose block or door chunk is not loaded is skipped. Nothing loads chunks.

## Cost per tick

| What | When | Cost |
| --- | --- | --- |
| `HouseDaySystem.tick` | every tick, every world | one map add; once a second, an empty check and `world.execute` |
| The pass | once a second per world with houses, as a world task (outside system ticks) | per house: two loaded checks plus its residents through the record index (no entity scan). Per live resident: a few component reads. Every 10th pass: one block read per house. |
| Going in / coming out | once per animal per dusk / dawn | one entity removal / one spawn with up to 81 column tests |
| Place / break events | per player block action | a block id lookup, and nothing more unless it is a house block |
| Saves | at most once per `SaveDebounceSeconds`, off-thread | JSON of the dirty worlds |

The only entity scan is `/ranch house debug claim` (admin test command, 8-block sphere).

## Verification

- `.\gradlew.bat --no-daemon build`: green, no warnings. 17 house tests plus all the others pass. They cover
  footprints from the hitbox files, door per rotation, rotation the way the engine turns blocks, the
  dusk/dawn/morning/assign rules, the save round trip, removal, the corrupt-file fallback, and the flush on
  close.
- `asset_validate`: 0 schema errors. `ui_check_java`: valid. `node tools/check-lang.mjs`: 0 missing.
  `test_run` of `tests/hytale/house.test.json`: green.
- **Server, headless bot**, on a stone platform at y 150 built with `/setBlocks`, using
  `/ranch house debug place cow`, `debug claim`, `debug use`, `debug nightfall`, `debug morning`, and `list`:
  - Log: `House e93aaf0e… placed … at -822 151 396 rotation 0, door -822.00 151.00 398.60`, then
    `walks home … from 2.5 blocks from the door`, `went into house … at the door`, `nightfall`,
    `morning`, `came out of house … (generation 2)`, `Saved ranch animals.` / `Saved ranch houses.`
  - `/ranch house list` resident lines in order: generation 1 ACTIVE, generation 1 STORED (twice), generation 2
    ACTIVE.
  - Save files: the animal save written during the night (kept as `Animals/default.json.prev`) has the cow at
    `generation` 1, `state` STORED, `entityUuid` `3351c462…`. After shutdown, `Houses/default.json` has the house with door, capacity 4,
    `lastMorningDay` -719162 and `Nightfall` for that day. `Animals/default.json` has the cow with
    `generation` 2, `state` ACTIVE, the `houseId`, and a new `entityUuid` (`b65d7eff…`, was `3351c462…`).
  - Earlier runs also showed the 60 s "did not reach the door in time" path and the "it was missing" path.

## Untested or weak

- Real Use and real breaking of a house block by a player (the bot cannot press Use or break blocks). Placement
  ran through the engine's `BlockPlaceUtils.placeBlock`, which fires the same `PlaceBlockEvent`. Breaking was
  only seen when the block broke by itself for lack of support.
- Rotations other than 0 on a server, the other four species, more than one resident, a full house.
- Home range by day (GoHome after wandering off) was not watched on the server. Only the dusk walk was.
- Breaking a house with residents inside, unloaded chunks at dusk or dawn, a restart in the middle of the night.
- The house page (`Ranch.housePages()`) and the service calls from a UI.
- A house block needs ground under it (`Support Down Full`). Placed in the air, it breaks within seconds and the
  record goes with it (animals unassigned, not lost).
- An animal standing in the house's hitbox suffocates. The spawn spot avoids the footprint, but an animal can walk
  into it by day.
- Coming out on wet ground: the spot test allows fluid, so a spot in shallow water is possible.

## Client steps for Gibson

1. Merge, then add the `RanchCommand` line from REQUESTS.
2. In a world, claim a cow (`/ranch house debug claim` near one, or tame one), make it follow you, place a cow barn
   on solid ground, step back, and press Use on the barn. Chat says the cow was assigned.
3. Watch the hint text on the barn, and that Use with no animal near opens the house page (or the chat summary).
4. `/ranch house debug nightfall`: the cow walks to the door and disappears. `/ranch house list` shows STORED.
5. `/ranch house debug morning`: it comes out beside the door. `list` shows generation 2.
6. Let a real night pass with `/time` and check the same without the debug commands.
7. Break the barn as another player (refused), then as the owner (the cow is unassigned and stays).
8. Try each rotation: the cow should come out at the front.

## REQUESTS

1. `command/RanchCommand.java`: add `this.addSubCommand(new com.hexvane.cozytalesranching.house.HouseCommand());`
2. Animal: `AnimalRecovery.findSpot` reuses one `SpawningContext` for every column. On the server that made every
   column fail (`FAIL_NO_MOTION_CONTROLLER_MATCH`, `FAIL_INVALID_POSITION`), so recovery likely never finds a spot.
   Use a fresh context per column, as `NPCPlugin.spawnNPCWithColumnProbe` does (see `HouseResidents.findSpot`).
3. Animal: a ranch animal killed or removed goes MISSING with only a debug log. An INFO line would help Gibson
   when animals "vanish".
4. Animal: recovery and house spawns make a new entity, and nothing puts the animal's nameplate back. A named
   animal comes out with no name until something re-applies it.
5. Species and config: the ranch roles' `WanderRadius` (6) is the leash wander around the door, while
   `HomeRangeRadius` is enforced by GoHome. Keep `HomeRangeRadius` above `WanderRadius`, or residents will
   switch between Idle and GoHome all day.
6. Api: a `RanchHouseRemovedEvent` (houseId, speciesId, world, breaker or null) would let other features clean up
   when a house goes. Residents already get `AnimalHouseAssignedEvent` with a null house.
7. `tests/hytale/house.test.json` names `_worktrees/CozyTales-Ranching-house`. Change the paths to the main
   project at merge.
