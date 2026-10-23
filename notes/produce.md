# Produce (wave 3, batch 1)

Branch `wave/produce`. Design doc section 10, automatic collection.

## What was built

- **Production.** Adult house residents (ACTIVE or STORED, not babies, with a `houseId`) make their species' `Products` over real minutes. A pass runs every `PassSeconds` (30) per world on the world thread through `world.execute`.
- **Rate.** `ProduceRules.rateFactor`:
  - happiness: `(Hz + (1 - Hz) * h)`, with `Hz = 0.5`, so it is never zero
  - hearts: `× (1 + 0.05 * hearts)`
  - bountiful: `× ProductionMultiplier` (1.25, from `Traits.json`)
  - premium feed: `× 1.2`, but only for the minutes of the pass that fall before `feedBonusUntilMs`
  - floor: `MinRateFactor` (0.25)

  Lucky (`BonusProductChance` 0.1) rolls once per unit made for one extra item. Partial units carry over in a per-animal, per-product progress value.
- **Caps.** Each house holds up to `HouseCap` items (64). When it is full, production pauses and progress is kept; overflow is not lost but stays in progress. Catch-up after a restart is capped at `CatchUpCapMinutes` (480).
- **Store.** Each world has its own file at `<data>/Produce/<world>.json`. It holds the house stocks with their door positions, the per-animal progress, `LastPassMs` and `PendingDrops`.
  - Saves are debounced on a `CozyRanch-Produce` thread. Each save writes a `.tmp` file with fsync, keeps a `.prev` copy, then does an atomic move. A corrupt file is renamed to `.corrupt-<ms>` and `.prev` is loaded.
  - The store is flushed at `SHUTDOWN_WORLDS + 4` and by a JVM shutdown hook.
- **Collection** (`Ranch.ProduceStorage`, installed with `Ranch.installProduce`).
  - Who may collect: the owner, or anyone with `cozyranch.admin.manageany`; either way they also need `cozyranch.house.manage`.
  - What fits goes into the hotbar and backpack. The rest stays. `RanchSounds.PRODUCE_COLLECT` plays and a pickup notification shows.
  - Products with `"Collect":"Bucket"` (milk) need one empty `Container_Bucket` per item. Each bucket is taken from the inventory and replaced with one milk bucket. Without a bucket the milk waits in the house.
- **Events.** `AnimalProducedEvent` fires once per animal per product batch, after the store is marked dirty and its save is scheduled.
- **House removed.** On `RanchHouseRemovedEvent` the stock moves to `PendingDrops` and is saved. It drops at the door once the chunk is loaded, split by max stack. A removed record stops producing because it is no longer a resident, and its progress is pruned.
- **Vanilla manual harvest is untouched.**
- **Products** (the `Products` field of each `Species/*.json`):
  - chicken: `Food_Egg` every 25 min, and `Ingredient_Feathers_Light` every 90 min
  - cow: `*Container_Bucket_State_Filled_Milk` every 40 min (Bucket)
  - sheep: `Ingredient_Fabric_Scrap_Wool` every 40 min
  - pig: `CozyRanch_Truffle` every 60 min (new item, `Server/Item/Items/CozyRanch/Produce/`)
  - frog: `Plant_Flower_Water_Green` (Waterlily) every 60 min
- **Tuning** lives in `Server/CozyTalesRanching/Produce.json`. A copy at `<data>/Produce.json` overrides it.
- **Admin command** `/ranch produce status|tick [minutes]|collect`. Every leaf needs `cozyranch.admin`.
  - `tick` forces a pass of N minutes (default 60, capped at the catch-up cap) without moving the clock.
  - `collect` collects from every house within 32 blocks.

## Verified

- **Unit tests** (`ProduceRulesTest`, `ProduceStoreTest`): happiness, the floor, hearts, bountiful, lucky (statistical), full and partial premium, progress carry-over, the full-house pause and overflow, the catch-up cap and a clock going back, the bucket plan, collect modes, who produces, a store round trip across a restart, take, retire and pending drops across a restart, progress pruning, the corrupt-file fallback, and parsing of the bundled tuning and products.
- **Gate:**
  - `asset_validate` on `_worktrees/CozyTales-Ranching-produce`: valid, 0 errors, 0 schema errors, and 7 warnings that were already there (CozyRanchTrough, CozyRanchUseHouse ×5, CozyRanchOfferFood).
  - `ui_check_java`: valid.
  - `.\gradlew.bat --no-daemon build`: green, 121 tests.
  - `node tools/check-lang.mjs`: 0 missing.
  - `test_run` with `tests/hytale/produce.test.json`: 3 of 3 cases pass.
- **Live run 1**, on port 15541 with a coop and a barn (`tools/sessions/produce-live.json`, log `36eb6c27-...log`):
  ```
  Produce registered: pass every 30 s, house cap 64, catch-up cap 480 min; ... cow *Container_Bucket_State_Filled_Milk every 40.0 min (bucket), ... chicken Food_Egg every 25.0 min, chicken Ingredient_Feathers_Light every 90.0 min
  ranch produce tick 120 -> Produce pass in default: 120.0 min, 23 item(s) from 6 animal batch(es) in 2 house(s)
  Collect from house 8a5a8696... by RanchBot: moved 15 {Food_Egg=13, Ingredient_Feathers_Light=2}, 0 left, 0 waiting for a bucket
  Collect from house e77cc0dd... by RanchBot: moved 0 {}, 8 left, 8 waiting for a bucket
  tick 600 -> Produce pass in default: 480.0 min, 64 item(s) from 4 animal batch(es) in 1 house(s)   (catch-up cap and house cap)
  /block set (coop removed) -> 64 product(s) will drop at -909.0 119.0 89.6
  Dropped 64 product(s) of house ...: {Food_Egg=49, Ingredient_Feathers_Light=15}
  ```
  In this run the barn at the teleported spot (-890 120 87) broke by itself one second after the first collect (`block at -890 120 87 is now Empty`). Its 8 milk then dropped correctly (`Dropped 8 product(s) ...: {*Container_Bucket_State_Filled_Milk=8}`). Produce never writes blocks, so I take this to be the terrain or support at that spot.
- **Live run 2**, a barn at spawn (`tools/sessions/produce-bucket-live.json`, log `d2524aee-...log`). Two bountiful cows with 5 hearts:
  ```
  Produce pass in default: 120.0 min, 8 item(s) from 2 animal batch(es) in 1 house(s)
  Collect from house 7834dbe9... by RanchBot: moved 0 {}, 8 left, 8 waiting for a bucket
  (/give Container_Bucket x2)
  Collect from house 7834dbe9... by RanchBot: moved 2 {*Container_Bucket_State_Filled_Milk=2}, 6 left, 6 waiting for a bucket
  Saved ranch products.
  ```
  - The bot received `cozyranch_produce.status.house` (8 milk, 2 producers), then `bucket.waiting` 8, `collect.done` 0 and `collect.bucket` 8. After the buckets it received `collect.done` 2 and `collect.bucket` 6, and the final status showed 6.
  - The save file matches the maths: 120/40 × 1.25 × 1.25 = 4.6875 gives 4 milk per cow, with `0.6875` progress left for each.
  - Both servers were stopped (job state `cancelled`, exit 0, and nothing left on 15541).

## Faked or unfinished

- The truffle uses the potato model, texture and icon as a placeholder. It needs real art.
- Collecting was only exercised through `/ranch produce collect`. No player has collected through the house's own Use interaction, because houseui owns that path and calls `ProduceApi.collect`. The only house breaks tested were `/block set` and the self-break above, not a player breaking a house.
- Catch-up after a real restart is covered by unit tests (the store round trip and `catchUpMinutes`). In the live runs `tick` stood in for it; no live restart was done.
- The barn self-break at -890 120 87 was not investigated further. It is not in produce code, but the house role may want to look.

## Decisions to review

- The frog gives Waterlily, which feeds back into the frog pond's `lily_pads` habitat preference.
- The truffle counts as the `Mushrooms` resource, so it works in mushroom recipes. It is a T3-consume food that heals.
- The rate numbers: happiness 0 gives half speed, each heart adds 5 %, premium feed gives ×1.2, and the floor is 0.25.
- The house cap is a flat 64 items for every house, not per species.
- Catch-up is capped at 480 minutes, which is 8 hours.
- Lucky rolls once per unit, not once per batch.
- Overflow when a house is full stays in progress rather than being thrown away. A long-full house therefore holds at most one interval's worth extra per animal.
- Events fire after `markDirty` and `scheduleSave`, as in the other roles, not after the disk write finishes.
- The `Collect` values are `House` (the default) and `Bucket`. `Harvest` and `Drop` were not needed and are treated as `House`.
- `collect` sends no chat itself. The command and houseui turn the `CollectResult` into messages.

## Cost

- `ProduceSystem` adds one float per tick per world, plus one `world.execute` every 30 s.
- A pass is O(houses + residents × products), using the record indexes. It does no entity or block scans and no disk IO; the save runs on the produce thread.
- Each pending drop costs one loaded-chunk check per pass until it drops.
- Collect is one inventory walk, plus one more for buckets when a product needs them.
- The save file is small: one entry per house with stock and one per producing animal.

## Public reads for houseui

- `ProduceApi.stored(houseId)` returns `Map<String,Integer>`.
- `ProduceApi.total(houseId)`, `ProduceApi.cap()` and `ProduceApi.isFull(houseId)`.
- `ProduceApi.needsBucket(speciesId, itemId)`, `ProduceApi.bucketItem()` and `ProduceApi.waitingForBuckets(house)`.
- `ProduceApi.collect(ref, store, house)` returns a `CollectResult(denied, moved, left, needBucket, movedItems)`. `Ranch.produce().stored(...)` and `Ranch.produce().collect(...)` work the same way through the contract.
- Lang keys: `cozyranch_produce.bucket.waiting {count}`, `cozyranch_produce.house.full`, `cozyranch_produce.collect.done {moved,left,items,species}`, `cozyranch_produce.collect.bucket {count}`, `cozyranch_produce.collect.denied` and `cozyranch_produce.collect.none`.

## REQUESTS

1. **`command/RanchCommand.java`** (owned by the command role): add this line beside the other `addSubCommand` calls.
   ```java
   this.addSubCommand(new com.hexvane.cozytalesranching.produce.ProduceCommand());
   ```
   My worktree has this edit locally and it is **not committed. Do not merge my copy.**
2. **`src/main/resources/Server/CozyTalesRanching/Produce.json`** is not in my owned list. It is the produce tuning file and follows the `Behaviour.json` and `Journal.json` convention. Please assign it to produce.
3. **`tests/hytale/produce.test.json`** uses the project `_worktrees/CozyTales-Ranching-produce`. Change it to `CozyTales-Ranching` at merge.
4. *(Optional)* A dedicated `cozyranch.produce.collect` permission node. Collect uses `cozyranch.house.manage` for now.
5. *(Optional, sounds role)* A distinct sound for milk going into a bucket. `RanchSounds.PRODUCE_COLLECT` is used for everything now.
