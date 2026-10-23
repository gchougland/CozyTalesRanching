# care — troughs, eating, habitat scans, happiness

Branch `wave/care`. Everything is in `com.hexvane.cozytalesranching.care`, plus `Habitat.json`,
`cozyranch_care.lang`, the interaction fields of `CozyRanch_Trough.json` and `tests/hytale/care.test.json`.

## What I built

### Troughs (`TroughInteraction`, `TroughBlocks`, `TroughBreakSystem`, `CareStore`, `CareSaveFile`)

- `CozyRanch_Trough` has `BlockType.Interactions.Use` = `CozyRanchTrough` and the hint
  `cozyranch_care.hint.trough`. The Filled state inherits both from the base block.
- **Use with food** in hand puts the whole held stack in, or as much as fits. Only items some species eats count
  as food (any Basic, Preferred or Favorite list); other items get `trough.not_food`.
- A trough holds **4 stacks**, each up to the item's `MaxStack`. Food types can be mixed, and a stack of the same
  item is topped up before a new slot is opened.
- **Crouch-use with an empty hand** takes everything back. It goes through `ItemUtils.interactivelyPickupItem`,
  so whatever doesn't fit in the inventory drops at the player.
- **Plain use with an empty hand** prints the contents to chat: a header, then one line per stack with the
  item's translated name.
- **Block state.** The block shows `Filled` while it holds food and `default` when empty. State is set with
  `BlockOperations.setBlockInteractionState` on the world thread, and only when the chunk is loaded. Troughs
  whose state may be stale are re-synced every 5 s, and all known troughs are checked every 60 s.
- **Breaking** a trough (`BreakBlockEvent`) queues a sync for after the event. Food drops only if the block is
  really gone, so a break that another system cancels drops nothing. A trough that disappears any other way
  (an explosion, a prefab paste) also drops its food at the next 60 s check.
- **Persistence.** Contents and the last trough meal of each animal live in `<data>/Troughs/<world>.json`.
  - Writes happen on the `CozyRanch-Care` daemon thread, debounced by `SaveDebounceSeconds`.
  - The write scheme matches `AnimalSaveFile`: `.tmp`, force, copy to `.prev`, then an atomic move.
  - A corrupt file is renamed `.corrupt-<ms>` and `.prev` is read instead.
  - On shutdown the save is flushed at `SHUTDOWN_WORLDS + 4`, plus a JVM hook.
  - Empty troughs are not saved.
- **Permission.** Every trough action checks `RanchPermissions.has(player, HOUSE_MANAGE)`. A dedicated node is
  under REQUESTS.

### Eating (`CareWorld.feed`)

- Owned animals that are ACTIVE or STORED in this world, and within `TroughRange` of a trough, eat one item
  per **meal interval**. Range is measured horizontally, within 4 blocks up or down.
  - The interval is one in-game day in real time (`daytime + nighttime` seconds), clamped to 5–60 min. With
    the default day it is about 30 min.
  - I used real time so that feeding still works on servers that freeze the clock.
- The meal is the best food the species likes across all troughs in range: Favorite, then Preferred, then
  Basic, each in species-file order. The nearer trough wins a tie.
  - Eating sets `lastFedAtMs` (real clock, like taming) and records the meal kind in the care store.
  - If the entity is loaded, it plays `EatTrough` in the Emote slot, but only when the model's animation set
    has it.
- **No walking.** Animals eat in place. Feeding is decided by distance and never depends on pathfinding.
  A walk-to-trough state is under REQUESTS.
- **Favourite treat** from a trough adds `0.1` friendship through `TamingRules.addFriendship`.
  `AnimalFriendshipChangedEvent` fires after `markDirty` when the whole heart count changes.
- **Positions.** A STORED animal's position is its house door; a live entity uses its own position; otherwise
  the last seen position is used.
- Hand feeding by taming sets `lastFedAtMs` but not the kind, so care counts it as Basic.
  `CareApi.recordMeal` lets taming report the real kind (REQUESTS).

### Habitat scan per house (`HabitatScan`, `WorldHabitatGrid`, `HabitatConfig`, `Habitat.json`)

- **Schedule.** Every 5 s, at most one house is picked whose last scan is older than
  `HabitatScanIntervalSeconds` and whose origin chunk is loaded. Its scan then runs at 96 columns per second
  until done.
- **Shape.** The scan covers a disc of `HabitatScanRadius` columns around the house origin, from 4 blocks below
  to 10 above.
- **Never loads chunks.** Cells are read with `SectionReader` on sections already in memory. A column with any
  unloaded cell is skipped whole, and the count is kept in `columnsSkipped`.
- **Result.** When the scan finishes, care writes `habitatRequirementsMet`, `habitatScore`, `habitatMissing` and
  `habitatScannedAtMs` on the `HouseRecord`, then calls `Ranch.houses().markDirty`. If every column was
  skipped, only the timestamp changes.
- **Feature ids → blocks.** These live in `Server/CozyTalesRanching/Habitat.json`, and a copy in
  `<data>/Habitat.json` overrides it.
  - `water`: fluids `Water`, `Water_Source`, `Water_Finite`, at least 4 cells.
  - `lily_pads`: `Plant_Flower_Water_*`, at least 1.
  - `reeds`: `Plant_Reeds_Marsh/Water/Wet/Arid/Winter`, at least 2.
  - `tall_grass`: `Plant_Grass_*_Tall/Overgrown/Wild` and `Plant_Fern_Tall`, at least 3.
  - `grass`: `Soil_Grass*` and `Plant_Grass_*`, not Burnt, Poisoned or Cave, at least 20.
  - `flowers`: `Plant_Flower_*` (not Poisoned) and `Plant_Sunflower_Block`, at least 3.
  - `hay`: `Plant_Hay_Bundle`, at least 1.
  - `dust`: dirt, dry dirt and sand, on the surface, at least 4.
  - `mud`: `Soil_Mud(_Dry)`, on the surface, at least 3.
  - `vegetables`: `Plant_Crop_*` (not mushrooms, wild grass, cotton or apple), at least 4.
  - `food`: special. It is met when a trough within the scan radius holds food this species eats.
- **Surface rule.** A `Surface` feature only counts with an empty cell above it, so buried mud doesn't count.
- **How shade and open space are decided**, per column:
  - A *surface* is a solid, fluid or leaves cell with at least 2 passable cells above it.
  - The column's *ground* is the surface nearest the house's height, within 4 blocks. Because of this, a roof
    3 blocks up is not mistaken for ground.
  - **Shaded:** anything solid, fluid or `Plant_Leaves_*` sits above that ground, within the scanned height.
  - **Open:** nothing does.
  - `shade` needs at least 6 shaded columns.
  - `open_space` needs at least max(40, 35 % of the scanned columns) open columns.
  - Leaves more than 10 blocks above the house are not seen.
- **Score.** 0.5 × (share of required features present) + 0.5 × (share of preferred features present).
  - An empty list counts as all present.
  - `requirementsMet` means every required feature is present.
  - Ids the config doesn't know are left out of both shares and never reported missing. A unit test checks that
    every id used by the five species files is mapped.

### Happiness (`CareRules`, `CareWorld.careFor`)

Care is the only writer of `AnimalRecord.happiness`. Each animal is updated once a minute; the roster is spread
over 60 one-second steps.

**Target** (clamped to 0.30..1):

| part | value | when |
|---|---|---|
| base | 0.30 | always |
| fed | +0.20 | `lastFedAtMs` within 2 meal intervals (about 60 min) |
| good food | +0.10 | fed recently and the last meal was Preferred or Favorite |
| house | +0.10 | `houseId` set |
| habitat met | +0.10 | house and `habitatRequirementsMet` |
| habitat score | +0.10 × score | house |
| petted | +0.10 | `lastPettedAtMs` within one game day |
| treat | +0.05 | a trough treat within 2 meal intervals |

Worked examples:

- Neglected: 0.30, which is also the floor.
- Fed basic food in a house with its requirements met and half the preferences: 0.30 + 0.20 + 0.10 + 0.10 +
  0.075 = **0.775**. That is above the default `BreedingMinHappiness` of 0.7.
- Fed and housed only: 0.60.
- Everything: 1.0.

**Drift.** `h += (target − h) × (1 − e^(−dt/τ))`:

- τ is 8 min when rising and 20 min when falling.
- dt is capped at 10 min, so a long pause or restart never jumps straight to the target.
- The result is clamped to 0.30..1.
- Starting from 0.6 toward 0.9: about 0.79 after 8 min and about 0.86 after 16 min.
- From 0.9, a missed meal drops the target to 0.7, and happiness reaches about 0.77 after 20 min.

Happiness only matters below `BreedingMinHappiness` (breeding reads it); nothing else is penalised. A record is
re-saved only when happiness moved by at least 0.002 or the animal ate.

Unhoused animals of offline owners are not visited, so their happiness pauses (REQUESTS has a fix).

### Read API (`CareApi`, public, static)

- `troughContents(world, x, y, z)` returns `Optional<Trough>`, where a trough is world, position and
  `List<Stack(itemId, count)>`.
- `troughsNear(world, x, y, z, radius)` returns the troughs holding food whose origin is within `radius`
  horizontally and 4 blocks vertically (the same reach animals eat by).
- `happinessBreakdown(AnimalRecord)` returns `CareRules.Breakdown`: every part of the target, plus `target`.
  It must be called on the world thread.
- `lastTroughMeal(UUID)` returns the kind, time and last treat time.
- `recordMeal(AnimalRecord, FoodKind)` is for taming's hand feeding.
- `troughRange()`.

All of these are thread-safe except `happinessBreakdown`.

### Admin commands (`CareCommand`, `/ranch care …`, each leaf needs `cozyranch.admin`)

- `status`: the troughs and the houses in this world, with their habitat results.
- `animals`: your animals here, with happiness, target and its parts.
- `run`: feeds and updates everyone now, scans every house fully, and syncs every trough.
- `fill <x> <y> <z> <item> <count>`: puts food into the trough at a block.

They work only once `RanchCommand` adds the sub-command (REQUESTS).

## Cost

| system | work |
|---|---|
| `CareSystem` tick | adds dt per world; once a second queues one `world.execute` |
| roster | rebuilt once a minute from `houses().inWorld` residents plus online players' `ownedBy`; no entity scan |
| per animal | once a minute: a range check against this world's trough list (tens at most), a few record reads, maybe one `takeOne` |
| habitat | only while a house is due: 96 columns × 15 cells = 1 440 cached reads a second. With radius 12 that is 1–5 s per house, then idle until `HabitatScanIntervalSeconds` |
| trough state | every 5 s for dirty troughs, every 60 s for all; one block lookup each, no chunk loading |
| disk | only on the save thread, debounced |

## Verification

- `gradlew build`: green, with no compiler warnings. 61 tests, of which care adds 4 classes:
  - `TroughContentsTest`: the 4-slot limit, max stack, top-up and take order.
  - `CareRulesTest`: food choice, interval clamps, target values, drift (slow, rises faster than it falls,
    floor, dt cap).
  - `HabitatScanTest`, on a fake grid: a frog pond with lily pads, reeds and shade under leaves; a missing
    requirement; trough food; open pasture; a roof that is not open; the buried-mud surface rule; crop states
    and poisoned flowers; an unloaded column skipped; unknown ids; every species feature mapped.
  - `CareStoreTest`: a save round trip across two worlds, empty troughs forgotten, break drops, a corrupt
    file falling back to `.prev`.
- `asset_check` on the trough: 0 errors. Its one warning is the plugin type `CozyRanchTrough`.
- `asset_validate`: valid, 0 schema errors.
- `ui_check_java`: valid.
- `node tools/check-lang.mjs`: 0 missing en-US keys.
- `test_run` on the worktree: 16/16 cases passed, including both care cases.

## Untested (no server for me) — steps for Gibson

1. **Interaction.** Place a trough and hold wheat.
   - Use it: the chat says "added", the model turns Filled, and the hint reads "Press F to fill or check the
     trough".
   - Add 4 other foods: the 5th says the trough is full.
   - Use with an empty hand: the chat lists the stacks.
   - Crouch-use with an empty hand: everything comes back and the model turns empty.
2. **Persistence and breaking.**
   - Fill a trough, stop the server, and check `mods/.../Troughs/default.json`.
   - Restart: the contents are still there.
   - Break the trough: the food drops next to the trough item.
3. **Eating.**
   - Set `DaytimeDurationSeconds` short or wait ~30 min. `/ranch care run` forces it once the command is
     registered.
   - A tamed cow within 8 blocks eats one item, plays EatTrough, and `/ranch care animals` shows it fed.
   - A pumpkin raises its friendship by 0.1.
   - Check that EatTrough looks right while the cow stands in place (it does not walk over).
4. **Habitat.**
   - Put a frog house by a pond with lily pads, then `/ranch care run` and `/ranch care status`: water and
     lily pads are found, and shade is found under a tree.
   - Check the block ids I mapped: in particular `Plant_Flower_Water_*` for lily pads, the `Plant_Reeds_*`
     names, and whether `Soil_Grass` really is the grass block id.
5. **Happiness.** Watch `/ranch care animals` over ~15 min after feeding: happiness should climb toward the
   target and not jump.
6. **Live state.** Check that `setBlockInteractionState` back to `default` works on a rotated trough; it is
   the only state change I could not test.

## Wave 3: feeds, toys, care traits, first scans

### Advanced feed (`Feeding`, `GiveFeedInteraction`, `Habitat.json` `Feeds`/`FeedEffects`)

- Items `CozyRanch_Feed_Hearty/_Sweet/_Breeders/_Premium`, crafted at the Farmer's bench (Farming tab), 2 per
  craft: Hearty = 3 wheat + 2 corn + 1 potato; Sweet = 2 carrot + 1 pumpkin + 1 wheat; Breeder's = 2 lettuce +
  1 cauliflower + 1 turnip + 1 wheat; Premium = corn + carrot + pumpkin + tomato + rice. Max stack 25.
- Every species accepts them in a trough (`CareService.isTroughFood`) and by hand: Secondary (right-click) on
  your own ranch animal within 6 blocks (`CozyRanchGiveFeed`). Hand feeding needs `RanchPermissions.TROUGH` and
  ownership (or `ADMIN_MANAGE_ANY`); it counts as a Preferred meal and gives Preferred-meal friendship.
- One bonus at a time in `AnimalRecord.feedBonus`/`feedBonusUntilMs`; a new feed replaces the old. Durations in
  real minutes, from `Habitat.json`: hearty 120, sweet 60, breeders 60, premium 90. Expired bonuses are
  cleared at the next care visit.
- Trough choice: with no bonus active an animal eats feed first; with a bonus active it eats normal food first
  (favourite > preferred > basic) and feed last, so a trough of feed is not burned through.
- Effects:
  - **hearty**: the "fed" and "good food" parts stay on while the bonus lasts, even past 2 meal intervals.
  - **sweet**: friendship from meals and treats ×1.5 (`FeedEffects.SweetFriendshipMultiplier`).
  - **breeders**: on eating, `lastBredAtMs` moves back by 0.5 × the animal's cooldown (`BreedersCooldownCut`),
    never past "ready", once per feeding. Never-bred or already-ready animals are unchanged.
  - **premium**: only sets the bonus. Produce reads it (REQUESTS).

### Toys (`Toys/`, `Habitat.json` `Features` + `SpeciesToys`)

- Placeable blocks `CozyRanch_Toy_Ball/_ScratchingPost/_HangingBell/_FloatingDuck/_Log`, Farmer's bench,
  Decorative tab. Each is a habitat feature (`toy_*`, 1 block within the scan) added to the species' preferred
  list: pig ball; cow and sheep post; chicken bell; frog duck and log.
- They raise happiness through the existing score (preferred share). Side effect: adding a preference dilutes
  the share for a house without the toy, so a house's score drops slightly until a toy is placed (e.g. a
  cow at 2 of 4 preferences now has 2 of 5).

### Care traits (`CareTraits`, effects read from breeding's `Traits.json` through `TraitBook`)

| trait | key | value | effect |
|---|---|---|---|
| hardy | `HappinessDeclineMultiplier` | 0.5 | falling τ 20 → 40 min; floor 0.30 → 0.45 (`DeclineTraitFloor`, or a trait's own `HappinessFloor`) |
| gourmet | `TreatBonusMultiplier` | 1.5 | treat part 0.05 → 0.075, favourite treat friendship 0.1 → 0.15 |
| efficient_eater | `FoodUseMultiplier` | 0.75 | a trough meal uses up the item 75 % of the time |
| affectionate | `FriendshipGainMultiplier` | 1.25 | care's friendship gains ×1.25; `CareApi.friendshipMultiplier` for taming |

Multipliers stack with sweet feed (affectionate + sweet = ×1.875).

### No unscanned house breeds

- `RanchHousePlacedEvent` queues the new house ahead of the regular rotation; its scan finishes within a few
  seconds (live: 4 s, 420 columns) and logs `Care: first habitat scan of new house …`.
- Morning: a care `HouseDayHooks` hook scans any house with `habitatScannedAtMs == 0` synchronously before
  breeding's hook. Order checked: hooks fire in registration order and `CozyTalesRanchingPlugin.setup()` calls
  `CareFeature.register` before `BreedingFeature.register`.
- A scan where every column was unloaded still sets the timestamp, so breeding then applies its habitat rule
  (HABITAT) rather than the unscanned pass.

### Other

- `RanchSounds.TROUGH_FILL` plays when a trough is filled (interaction and `/ranch care fill`).
- `/ranch care bonus <animal|id prefix|all> <feed item or bonus id>` applies a feed as if eaten.
- `/ranch care animals` adds a "feed bonus …, N min left" line.
- New read methods on `CareApi`: `friendshipMultiplier(record)`, `activeFeedBonus(record)`,
  `hasFeedBonus(record, bonus)`, `feedBonusOf(itemId)`, `toysLiked(speciesId)`.

### Verified (wave 3)

- `gradlew --no-daemon clean build` green. New: `FeedTest` (8: feeds are data, meal order with and without a
  bonus, replace, hearty, sweet, breeders once per feed, premium), `CareTraitsTest` (6: none, hardy, own floor,
  gourmet, efficient eater, affectionate), `ToyAndFirstScanTest` (7: every species has a mapped toy, toys join
  preferences, a ball raises a pig's score and happiness, another species' toy does nothing, never-scanned house
  is scanned before breeding and a poor habitat then blocks it, all-skipped scan blocks, good cow breeds).
  Existing care and breeding classes pass.
- `test_run` on the worktree, 6/6, including the new server case `feed in the trough`: house placed → first
  scan logged → trough set → 4 breeder's feed filled → test-house cows eat it on `/ranch care run` →
  `animals` shows the breeders bonus → `bonus all CozyRanch_Feed_Sweet` succeeds.
- `asset_validate` valid, 0 errors; `asset_check` 0 errors on all 9 items; `ui_check_java` valid;
  `check-lang` 0 missing.
- Pictures in `notes/pictures/care/`, all opened.

### Faked or unfinished

- All models and icons are vanilla placeholders: feeds share the feedbag; ball and duck are a Christmas
  bauble (off-centre in its block, it is a wall ornament); post is the hay target; bell is a standing lantern
  (not hanging); log is an oak half block. Waiting on art.
- Not tried live: right-click hand feeding (the bot cannot use items on entities), crafting, placing toys by
  hand, the floating duck on water, the fill sound being audible.
- Petting friendship from affectionate/sweet needs taming (REQUESTS).

## REQUESTS

1. `RanchCommand`: `this.addSubCommand(new com.hexvane.cozytalesranching.care.CareCommand());`
2. `CozyTalesRanchingPlugin.reload()`: call `com.hexvane.cozytalesranching.care.CareFeature.reload();` (re-reads
   Habitat.json).
3. A permission node `cozyranch.trough` for filling and emptying troughs. `cozyranch.house.manage` is used
   until then.
5. Taming: call `CareApi.recordMeal(record, kind)` after hand feeding, so a Preferred or Favorite hand meal
   counts as one.
6. House (owner of the roles): an optional walk-to-trough state before `EatTrough`. Feeding does not depend
   on it.
7. `AnimalRegistry.inWorld(world)`, so unhoused animals of offline owners keep updating.
8. Translations of `cozyranch_care.lang` for es-ES and pt-BR.

Wave 3:

9. Taming (`AnimalInteractions.addFriendship`, line ~283), first line:
   `amount *= com.hexvane.cozytalesranching.care.CareApi.friendshipMultiplier(record);`
   so petting and taming's hand meals get affectionate and sweet.
10. House UI (`cozyranch_houseui.lang`): names for the new habitat ids, shown when a toy is missing:
    `habitat.toy_ball = Play ball`, `habitat.toy_post = Scratching post`, `habitat.toy_bell = Hanging bell`,
    `habitat.toy_duck = Floating duck`, `habitat.toy_log = Climbing log`.
11. Produce: read the premium bonus with `CareApi.hasFeedBonus(record, "premium")`.
12. Breeding (optional, `Traits.json`): `"HappinessFloor": 0.45` in hardy's effects to own the floor there;
    care falls back to `Habitat.json` `DeclineTraitFloor` (0.45) until then.
13. Art: real models and icons for the 4 feeds (one shared sack with tinted variants is fine) and the 5 toys
    (the bell should hang from a support above; the duck should sit on water).
14. Breeding / house-breed-live: houses are now scanned at placement and before morning breeding, so a test
    house in a poor spot can now get HABITAT where it used to breed unscanned.
