# Breeding (wave 2)

Branch `wave/breeding`. Breeding in houses overnight, babies, inherited traits, growing up. The record side ran
on a real server (port 15542, headless player, debug commands). The house role's day cycle, the parents and
babies coming out, and anything an eye has to judge have **not** been seen yet.

## How it plays

Put two adult animals of the house's species in a house. If they are friendly and happy enough, a baby may be
there the next morning: it comes out with its parents. The parents play `Love` and hearts float over the baby.
The baby grows into an adult after a while, a little faster when it is well cared for. Optimisers can breed for
traits. Nothing else is needed: no menu and no item.

## Rules, with numbers

**When the morning decides** (`BreedingService.onMorning`, registered with `HouseDayHooks`). The decision
happens at `onMorning`, not at nightfall, so a restart between night and morning loses nothing. A house breeds
when all of these hold, checked in this order (`BreedingRules.plan`). The first one that fails is the reason the
debug command reports.

| Check | Value |
| --- | --- |
| `RanchConfig.BreedingEnabled` | true |
| the house's `breedingEnabled` toggle | true |
| the house has an owner, and the owner has `cozyranch.breed` | `RanchPermissions.has` (works offline, LuckPerms-friendly) |
| species of the house is loaded | `SpeciesRegistry.byId(house.speciesId)` |
| habitat | `habitatRequirementsMet`, **except `habitatScannedAtMs == 0` (never scanned) counts as met** |
| room | residents < `capacity` |
| at least two residents are ready (below) | |

A resident is **ready** (`BreedingRules.whyNot`) when it is of the house's species, adult, `STORED` (inside
overnight), its `houseId` is this house, hearts >= `BreedingMinHearts` (3), happiness >= `BreedingMinHappiness`
(0.7) and its cooldown has passed. Happiness is a float and the threshold a double, so a 1e-6 tolerance applies
(without it 0.7f counted as below 0.7; a unit test caught it).

**One pair per house per morning**: the two ready residents that waited longest (lowest `lastBredAtMs`, never
bred first, then id order). One baby, or **twins** when a Twinborn roll succeeds and two more fit
(residents + 2 <= capacity). Twin chance = 1 - prod(1 - 0.3) over both parents' Twinborn traits: 30% with one,
51% with both, never without.

**Cooldown is real time** (wall clock): `BreedCooldownMinutes` (cow 60, frog 30, see the species files) times
0.6 for a Fertile animal (its own traits). Real time because every record time is already wall-clock ms, it
survives restarts without extra state, and the morning gate already ties breeding to the in-game day. Time while
the server is off counts. When the cooldown is longer than an in-game day, a pair simply skips mornings.

**A baby** (`BreedingService.breed`): `Ranch.animals().create(species, house.ownerId, world, true)`, then
`houseId` = the house, `state = STORED` (the house brings it out with its parents), `bornAtMs`, `parentA/B`,
inherited traits, `hearts = 1` (raised from birth), `lastX/Y/Z` = the house door, and
`spawnRole = BreedingRules.babyRole(species, role of a random parent)`. A parent's role is its `spawnRole`
(set by the house when it stores an animal), else its live role, else the species default. Both parents get
`lastBredAtMs`. Everything is `markDirty`'d, then one `AnimalBredEvent` with all offspring, then one
`AnimalFriendshipChangedEvent(0 -> 1)` per baby. Happiness is left at the record default (care owns it).

**Colour variants**: `ranchRoleFor` maps *wild* roles (`Frog_Blue`) to variants, but a ranch parent already has
the variant (`CozyRanch_Frog_Blue`), which `ranchRoleFor` turns back into green `CozyRanch_Frog`. So
`babyRole`/`adultRole` keep a role that is already a variant when the species has no baby role, and otherwise
call `ranchRoleFor` as the brief says. A blue frog pair gives a blue froglet that stays blue when grown.

**The signal**: after breeding, the parents' and babies' ids wait for 20 s. Each second the world pass looks
each one up through its record (`state == ACTIVE`, `entityUuid`, `getRefFromUUID`), plays `Love` on a parent
(only if the model has the set, `AnimationSlot.Emote`) and `TamingApi.cheer` (hearts + Happy) on a baby, once
each. Best effort: whatever has not come out in 20 s is skipped.

**Traits** (`Server/CozyTalesRanching/Traits.json`, a copy in the plugin data folder replaces it; read at start):
the nine traits of the design doc with an id, name and description key, and effect numbers.

| Trait | Effect key | Value | Used |
| --- | --- | --- | --- |
| Fertile | BreedCooldownMultiplier | 0.6 | **this wave** |
| Swift Grower | GrowUpMultiplier | 0.7 | **this wave** |
| Twinborn | TwinChance | 0.3 | **this wave** |
| Bountiful | ProductionMultiplier | 1.25 | name only (wave 3 products) |
| Lucky | BonusProductChance | 0.1 | name only (wave 3 products) |
| Affectionate | FriendshipGainMultiplier | 1.25 | name only (taming/care could read it) |
| Hardy | HappinessDeclineMultiplier | 0.5 | name only (care) |
| Gourmet | TreatBonusMultiplier | 1.5 | name only (care's favourite treats) |
| Efficient Eater | FoodUseMultiplier | 0.75 | name only (care's troughs) |

Other systems read effects with `TraitRegistry.get().multiplier(record.traits, "<Key>")` or `.chance(...)`.

**Inheritance** (`BreedingRules.inheritTraits`): each parent trait passes with `InheritChance` 0.5 (a trait
both parents have gets two rolls: 75%); then the list is cut to `MaxTraits` 2 at random; then with
`MutationChance` 0.08 one random trait the baby lacks is added, or replaces one at random when the list is full.
Unknown trait ids are not passed on. Tamed animals start with no traits, so traits first appear by mutation.

**Growing up** (`BreedingService.growBabies`, every 5 s per world over the baby index only): grown when
now - start >= `GrowUpMinutes` (cow 90, frog 30) x 0.7 if Swift Grower x 0.85 if happiness >= 0.8. Real time,
start = `bornAtMs`, else `tamedAtMs` (tamed wild babies), else the pass sets `bornAtMs = now` and starts the
clock. The good-care factor uses the happiness at the moment of the check. Then:
- entity loaded and its role is our ranch (baby) role of the species: `TamingApi.swapRole(ref,
  adultRole(species, currentRole), null, store)`; only if that was accepted: `baby = false`,
  `spawnRole = adult role`, `markDirty`, `AnimalGrewUpEvent`.
- `STORED` or `MISSING`: `baby = false`, `spawnRole = adultRole(species, spawnRole)`, event.
- `ACTIVE` but not loaded: waits, so it never comes back as an adult record with a baby role.
- loaded with a role that is not ours (an adopted vanilla `Tamed_Cow_Calf`): left alone; vanilla grows it.

**Baby index** (`BabyIndex`, memory only): filled at birth, rebuilt once a minute per world from the world's
houses (`inHouse`) and its online players' animals (`ownedBy`), so babies from before a restart and tamed wild
babies are found within a minute.

## Cost

| Work | When | Cost |
| --- | --- | --- |
| `GrowthSystem.tick` | every tick, per world | one map merge of a float; once a second one `world.execute` |
| signals | each second, only while a breeding happened in the last 20 s | parents + babies record lookups and `getRefFromUUID` |
| grow-up | every 5 s per world | one record lookup per baby in that world; a role swap only when one is due |
| reindex | every 60 s per world | houses x residents + online players x their animals, all index lookups |
| morning hook | once per house per morning (house role) | O(residents), a few record writes and events when it breeds |

No entity scan, no block scan, no store write inside a tick (the passes run through `world.execute`), no disk IO
(saves go through the animal registry's debounced writer).

## Debug commands (`cozyranch.admin`, in `breeding/`)

- `/ranch breed testhouse <species> [--traits=a+b] [--role=<ranch role>]`: a house record at your feet with two
  adult residents asleep inside (5 hearts, happiness 1, the given traits and role). A fixture: no block is placed
  and it writes happiness, which only care does in play. Traits are joined with `+` (the command parser keeps
  only the first item of a comma list).
- `/ranch breed morning [--house=<id>]`: the morning check now, on one house or every house in this world, with
  the reason when it does not breed. It does not touch `lastMorningDay`.
- `/ranch breed growup [--animal=<id>]`: grows one baby or all your babies in this world now, with the result.

## Verification

- `.\gradlew.bat --no-daemon build`: green, no warnings. 18 new JUnit tests (47 in the project), all pass:
  every resident block reason; house gates (server/house toggle, owner, permission, unknown species); habitat
  never scanned counts as met, scanned and unmet blocks; capacity (full, room for one); longest-waiting pair;
  twins 30% / 51% / 0% without Twinborn / 0% with room for one only (20,000 seeded trials each, +-1.5%);
  cooldown 60 min and 36 with Fertile; inheritance 50% from one parent, 75% from both, never above two traits,
  mutation 8% uniform over traits and replacing when full, unknown traits dropped, same seed same baby; grow-up
  90 min, 76.5 with good care, 63 Swift Grower, 53.6 both, clock fallback to tamedAtMs, none without a clock;
  colour roles (blue frog parent gives a blue froglet that stays blue; cow calf to cow); the bundled
  `Traits.json` parses with the nine design-doc traits.
- `asset_validate` on `_worktrees/CozyTales-Ranching-breeding`: valid, 0 errors, 0 schema errors.
  `ui_check_java`: valid. `node tools/check-lang.mjs`: 0 missing. `test_run` of `tests/hytale/breeding.test.json`:
  2/2 passed.
- **Server, port 15542** (release 0.7.4, insecure, headless player op'd), with the `/ranch breed` sub-command
  registered locally (REQUEST 1; not committed). Messages arrived as lang ids with params; the log and the saved
  `Animals/default.json` (written by the shutdown flush) agree:
  1. `morning` with no house: `debug.noHouse`.
  2. `testhouse cow --traits=twinborn+fertile` and `testhouse frog --role=CozyRanch_Frog_Blue`: two houses, four
     STORED adults.
  3. `morning`: both houses `debug.morning.bred` (1 baby each, 2 ready, 2 of 4) plus `habitatUnscanned`. Log:
     `bred ... -> 1 baby: [... CozyRanch_Cow_Calf [gourmet]]` (a mutation) and `[... CozyRanch_Frog_Blue []]`.
  4. `morning` again: both `debug.morning.noPair`, 0 ready (cooldown), 3 of 4.
  5. `growup`: `debug.growup.grew` for the calf (`CozyRanch_Cow`) and the blue froglet (`CozyRanch_Frog_Blue`).
  6. Saved file: babies with `parentA/B`, `hearts 1`, `houseId`, `STORED`, `bornAtMs`, `baby false`, adult
     `spawnRole`; parents with `lastBredAtMs` set. No exception or warning from the mod in the log over the
     whole session, with the once-a-second world pass registered.

The session file used is not in my paths; to repeat it: start the jar on 15542 with `authMode: insecure`,
`bot_start` with uuid `11111111-2222-4333-8444-555555555555`, `op add` it, wait 5 s, then send the commands of
steps 1 to 5 two seconds apart, wait 10 s, read `bot_events` ServerMessage, stop the bot and the server, and read
`<run dir>/mods/Hexvane_CozyTalesRanching/Animals/default.json`. `job_status` accepts `waitMs` up to 5000.

## Not tested

- The real house: nobody fires `HouseDayHooks` until the house role is merged. Babies coming out, and the
  `Love` + hearts signal on spawned entities, have never run.
- A live role swap at grow-up (every baby on the server was STORED) and the automatic timer firing on the
  server (forced only; the timing is unit tested).
- Twins and a refused permission on the server (unit tested only).
- Whether the `Love` set plays well on the client, and how it looks.

## Client steps for Gibson (after house and care are merged)

1. Place a Cow Barn, tame two cows, raise them to 3 hearts and assign both. Make sure care says they are happy
   (0.7 or more) and the habitat is met.
2. Wait for night (or `/time` to evening, then morning). In the morning both cows come out and play their Love
   animation; a calf comes out with hearts over it. `/ranch admin list <you>` shows the calf.
3. The next morning nothing is born (cooldown 60 min real time).
4. Wait 90 minutes, or `/ranch breed growup` near the calf: it turns into an adult cow where it stands.
5. Repeat with two blue frogs in a Frog Pond: the froglet is blue.
6. Afterwards read `client_log_issues`.

## REQUESTS

1. `command/RanchCommand.java`: add
   `this.addSubCommand(new com.hexvane.cozytalesranching.breeding.BreedAdminCommand());`
2. `CozyTalesRanchingPlugin.reload()`: add `com.hexvane.cozytalesranching.breeding.TraitRegistry.load(getDataDirectory());`
   so `/ranch reload` re-reads `Traits.json` (now only read at start).
3. `SpeciesDefinition.ranchRoleFor`: when `fromRole` is already a value of `RanchRoleVariants` (and there is no
   baby role to return), return it rather than `RanchRole`. Breeding works around it in `BreedingRules`; taming's
   claim of an already-blue ranch frog would hit the same thing.
4. A species with both a `RanchBabyRole` and colour variants (none today) loses the colour at birth, since there
   is one baby role. It would need a `RanchBabyRoleVariants` map, or a record field for the inherited adult role.
5. For **house**: call `fireMorning` while residents are still `STORED`, bring out STORED residents by
   re-reading `inHouse` after the hooks (babies are new records), and set `spawnRole` when storing an animal
   (breeding passes the parent's colour on from it). The signal needs `state = ACTIVE` and `entityUuid` set within
   20 s of the morning.
6. Design call: tamed animals have no traits, so traits only enter a ranch through the 8% mutation. Taming or
   adoption could roll one trait at a small chance; not done here.
7. Translations: `cozyranch_breeding.lang` needs es-ES and pt-BR (48 keys).
