# fix3-coop: ranch chickens and the vanilla coop

Branch `wave/fix3-coop`. Playtester: 5 adopted (vanilla-tamed, then claimed and named) chickens lived in a vanilla
chicken coop; the coop was broken; 10 hours later the 5 records were still MISSING in `Animals/Orbis.json`, and the
world had 5 chickens with no nameplate that ignored F, laid no eggs and slept like plain chickens.

## Root causes (with evidence)

### 1. The vanilla coop takes our chickens and gives back strangers

- `Server/Farming/Coops/Coop_Chicken.json`: `AcceptedNpcGroups: ["Chicken", ...]`, `CaptureWildNPCsInRange: true`,
  `WildCaptureRadius: 10`, `MaxResidents: 6`, `ResidentRoamTime: [6, 18]`.
- `Server/NPC/Groups/Creature/Livestock/Chicken.json`: `IncludeRoles: ["*Chicken", "*Chicken_Chick"]`. The glob matches
  `Tamed_Chicken` **and our own `CozyRanch_Chicken`** (claiming swaps adopted chickens to it,
  `taming/AnimalInteractions.java` `claim`).
- `FarmingSystems.tickCoop` (builtin Farming, `FarmingSystems.java:528-568`) at every scheduled coop tick (the roam
  boundaries, 06:00 and 19:00) captures every accepted NPC within 10 blocks (`CoopBlock.tryPutWildResidentFromWild`,
  `CoopBlock.java:178-222`: adds `CoopResidentComponent`, stores the role id as `CapturedNPCMetadata`).
- At dusk `ensureNoResidentsInWorld` (`CoopBlock.java:442-504`) marks them for despawn and `CoopResidentTicking`
  removes them with `RemoveReason.REMOVE` (`FarmingSystems.java:774-775`). Our `AnimalEntitySystem.onEntityRemove`
  then marks the record MISSING (by design: "removed outright, recovery brings it back").
- At dawn, and when the coop is broken (`handleBlockBroken`, `CoopBlock.java:603-636`), `ensureSpawnResidentsInWorld`
  (`CoopBlock.java:355-434`) spawns a **new** entity from the stored role id: `NPCPlugin.spawnEntity(store, roleIndex,
  pos, rot, null, null)`. New UUID, none of our components (no `CozyRanch_AnimalMarker`, no nameplate).
- An untagged `CozyRanch_Chicken` was ignored by `AnimalEntitySystem` (only adopt roles were adopted, old
  `AnimalEntitySystem.java:79`), so it had no record: no nameplate, F does nothing (taming only acts on records), no
  eggs (produce works from records), and it ran the plain template. That is exactly the 5 chickens in the report.
  The 6th chicken was most likely never within 10 blocks of the coop at a coop tick (06:00 or 19:00), or the coop was already full.

### 2. Recovery never respawned anything, anywhere

`AnimalRecovery.findSpot` accepted a spot only if `ctx.groundFluid == null` (old `AnimalRecovery.java:256`). On dry
ground `SpawningContext.selectSpawnSpan` sets `groundFluid = Fluid.getAssetMap().getAsset(groundFluidId)`
(`SpawningContext.java:1176-1177`), and `Fluid.EMPTY` is preloaded at id 0 (`AssetRegistryLoader.java:418`,
`Fluid.java:216-218`). So `groundFluid` is `Fluid.EMPTY`, never null, every spot was rejected, `respawn` returned
false, and the sweep said nothing (no log on failure). Every MISSING record waited forever, owner online or not;
`/ranch admin resummon` always said "No safe spot near you".

Live proof on the unfixed jar (port 15543, open grass at world spawn, record `5770b896...` adopted from a spawned
`Tamed_Chicken`): `/ranch admin resummon 5770b896-58a3-497d-944d-cddb65d06d14` answered
`cozyranch_animal.admin.resummon.failed`. The house code does its own spawn test without that check, which is why houses
spawned fine and nobody noticed.

## The fix

| File | Change |
| --- | --- |
| `animal/AnimalRecovery.java` | Spot test is `isDryFooting(groundFluidId, waterLevel)`: fluid id 0 (`Fluid.EMPTY_ID`) and no water in the span. A failed respawn now logs a WARNING (at most every 10 min per animal). |
| `animal/VanillaCoopGuard.java` (new) | `RefChangeSystem` on `CoopResidentComponent` for NPCs. When a coop adds it to an entity that has a ranch record (tagged, or bound with the tag still queued), the component is removed again. The coop then drops that resident at its next dusk check (`ensureNoResidentsInWorld`: no component, resident removed) instead of despawning it. Only runs when that component is added. Installed only if the Farming module is present. |
| `animal/OrphanRelink.java` (new) | Pure matcher: nearest MISSING record of the same species, world and age, within 48 blocks, whose last known role (or `spawnRole`) is the entity's role. |
| `animal/AnimalEntitySystem.java` | An untagged entity of a ranch or adopt role (never a wild role, never one with another mod's component) is first matched with `OrphanRelink`; a match is bound to that record (new generation, tag, nameplate, ACTIVE) instead of being ignored or adopted as a new record. Runs once per add, only for those roles. |
| `animal/AdminRelinkCommand.java` (new), `AnimalAdminCommand`, `AnimalFeature.registry()` | `/ranch admin relink [--radius=48]` (`cozyranch.admin.manageany`): relinks untagged ranch/adopt animals around the admin, then respawns every still-MISSING animal of this world beside the admin, and reports the counts. |
| `cozyranch_animal.lang` | 3 keys for the command. |
| `src/test/.../CoopOrphanTest.java` | 6 tests: nearest record of the right role, five copies take five different records and a sixth is a stranger, distance/world/age/species/state limits, role rules, which role kinds may be orphans, the dry-footing test. |

Decision: guard applies to every animal with a record, adopted-unowned ones included. Letting the coop keep an
unowned adopted chicken duplicates it (record goes MISSING at dusk and recovery brings it back, while the coop spawns
its own copy at dawn). Plain vanilla `Tamed_Chicken`s that have no record yet are not affected, but every vanilla
`Tamed_Chicken` gets a record as soon as it loads (adoption), so in practice vanilla coops no longer hold tamed chickens
while this mod runs; wild chickens still go in. A ranch chicken may hold a coop slot during the day until the coop's
dusk check drops it.

## Repair for the playtester's world

Their 5 records are MISSING and their 5 chickens are untagged `CozyRanch_Chicken`s near where the coop stood, so:

- With the new jar, **nothing to do**: when the server starts and those chunks load, each untagged chicken is added,
  matched to the nearest MISSING chicken record and comes back with its name (log line "came back untagged as
  CozyRanch_Chicken ... relinked to its record"). If the chunk is already loaded, an admin standing near the coop runs
  `/ranch admin relink`.
- Any record that found no chicken (one wandered off or died) is respawned by recovery within about 75 s while the
  owner is in the world (now that spot finding works), or at once by `/ranch admin relink`.
- If recovery ran first (chickens' chunk not loaded at start), the 5 strangers stay strangers and the named chickens are
  back beside the owner; the strangers can be removed with any kill command.

## Verification

- `gradlew build`: green, deprecation/removal as errors. `CoopOrphanTest` 6/6, all animal tests green.
- `asset_validate` valid (0 errors, 0 schema errors), `ui_check_java` valid, `check-lang` 0 missing.
- Live, unfixed jar: resummon failure above (proves cause 2).
- Live, fixed jar (port 15543, debug logging on). The coop's despawn and re-create were reproduced with the same
  engine calls the coop uses: `/npc clean --confirm` removes NPCs with `RemoveReason.REMOVE` (as `CoopResidentTicking`
  does) and `/npc spawn Tamed_Chicken` spawns a role with none of our components (as `ensureSpawnResidentsInWorld` does):
  - `Ranch animal 05bdf469... entity removed; marked missing` and `36fabe41... marked missing` (16:37:42)
  - `Ranch animal 36fabe41... (chicken) came back untagged as Tamed_Chicken entity 62437abf... relinked to its record`
    (16:37:48): the new entity took the record, no new record was made.
  - `Ranch animal 05bdf469... recovered in default as Tamed_Chicken` (16:38:56, 74 s after going missing): cause 2 fixed.
  - Removed both again; `/ranch admin relink` answered relinked 0, respawned 2, missing 0 and both were
    `recovered in default`; a second `/ranch admin relink` answered 0/0/0 (no duplicates). Save file: 2 records, both
    ACTIVE. No errors or warnings from the mod in the server log.
- Not exercised:
  - The vanilla coop itself capturing a chicken. A coop placed with `/block set` has its `CoopBlock` component
    (`/block getstate`), but in two runs across two full game days (time dilation, `/time set`, `/block setticking`) it
    never captured anything, not even a wild `Chicken` (the guard logs every `CoopResidentComponent` add on any NPC at
    debug level, and logged none). So `VanillaCoopGuard` is proven by build and source reading only. A coop placed by a
    player in the client should be tried: put a ranch chicken next to one at dusk and check the debug line
    `kept out of the vanilla coop`.
  - A claimed (owned) chicken through any of this (the test bot cannot press F); the owner branch of recovery
    (respawn beside an online owner) is the same spawn code as the last-position branch that ran.
  - The playtester's own world file.

## REQUESTS

None needed for the fix. Optional, for the role owner: our roles match vanilla NPC groups by glob (`*Chicken`,
probably `*Cow`, `*Pig`, `*Sheep` too), so vanilla features that use those groups (coops, and any predator or quest
that targets the group) also see our animals. Renaming roles would break saves; the guard above covers coops.
