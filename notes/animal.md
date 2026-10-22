# Animal role: persistence, deduplication, carry-over, compatibility

Branch `wave/animal`. Everything here is checked by build, unit tests and the MCP gate only. **No server
was started**: nothing in this file has been seen running in a world yet.

## What I built

| File (`animal/` unless noted) | What it is |
| --- | --- |
| `AnimalMarker` | `CozyRanch_AnimalMarker` entity component (`AnimalId` UUID string, `Generation` long), persisted with the entity. Registered in `AnimalFeature.register`. |
| `AnimalStore` | All records plus indexes (by entity, owner, house, world), tombstones, last role per animal, dirty worlds. No engine types, one lock (worlds tick on separate threads). Holds the dedup decision `judge`. |
| `AnimalSaveFile` | The save format and the atomic write / safe read. |
| `PersistentAnimalRegistry` | The `AnimalRegistry` implementation. Debounced saves on its own daemon thread, flush on shutdown. Installed with `Ranch.installAnimals`. |
| `AnimalPresence` | Memory-only map of which animals have their current entity loaded, and since when missing ones are missing. |
| `AnimalEntitySystem` | `RefSystem` on NPCs: dedup on add, adoption, last position on unload, "removed outright → missing". |
| `AnimalDeathSystem` | `OnDeathSystem`: `AnimalDiedEvent`, record → MISSING. |
| `AnimalProtectionSystem` | `DamageEventSystem` in the filter group. |
| `AnimalRecovery` | Periodic sweep per world (on the world thread) and safe respawn. |
| `AnimalAdminCommand` + `AdminInspectCommand`, `AdminListCommand`, `AdminResummonCommand` | `/ranch admin inspect|list <player>|resummon <animalId>`. Not registered yet (see REQUESTS). |
| `compat/CompatGuard` | `isManageable` now also checks `IgnoredComponentClasses`. |
| `cozyranch_animal.lang`, `tests/hytale/animal.test.json`, 2 JUnit classes (11 tests) | |

## Save format

`<plugin data>/Animals/<world>.json`, one file per world (world name with anything outside `A-Za-z0-9._-`
replaced by `_`). Gson, pretty printed:

```json
{
  "FormatVersion": 1,
  "World": "default",
  "SavedAtMs": 1792700000000,
  "Records": [ { "animalId": "...", "generation": 2, "entityUuid": "...", "ownerId": "...", "speciesId": "cow", "state": "ACTIVE", ... } ],
  "Tombstones": { "<animalId>": 1792690000000 },
  "Roles": { "<animalId>": "Tamed_Cow" }
}
```

- `Records`: `AnimalRecord` exactly as the shared class defines it (field names are the format).
- `Tombstones`: ids removed for good (released, butchered, unowned and killed by a player). An entity that
  still carries one is removed when it loads. Kept 180 days.
- `Roles`: the last NPC role seen for each animal, so recovery respawns the same kind of entity
  (`AnimalRecord` has no role field).
- Write: `<file>.tmp` written and `force`d, previous good file copied to `<file>.prev`, then an atomic move
  over `<file>`. Read: `<file>`, else `<file>.prev`; a leftover `.tmp` is deleted unread; an unreadable
  `<file>` is renamed to `<file>.corrupt-<ms>`, never deleted.
- All world files are read once in `register` (plugin setup, before any world adds entities), so a
  record is always in memory before its entity can appear.
- Saves: the first change starts a timer of `SaveDebounceSeconds`; later changes ride along. JSON is built
  and written on the `CozyRanch-Animals` thread, never the world thread. A failed world stays dirty and is
  retried. On `ShutdownEvent` at priority `SHUTDOWN_WORLDS + 4` (after the worlds unloaded their entities,
  so last positions are fresh) everything dirty is written synchronously; a JVM shutdown hook does the same
  if the event never came.

## Deduplication, step by step

`AnimalEntitySystem.onEntityAdded` runs for every NPC entering a world (spawn, chunk load, role change,
which the engine does as remove + re-add of the same holder):

1. Role not in `SpeciesRegistry.byRole` → return. (Every other entity: one map lookup, nothing else.)
2. Read the marker. No marker → adoption (below).
3. The entity carries a component from `IgnoredComponentClasses` or its role is ignored → never removed or
   changed. If it is the current incarnation it is only noted as present, so recovery does not make a second one.
4. `AnimalStore.judge(animalId, generation, entityUuid)` under the store lock:
   - **no record, tombstoned** → STALE.
   - **no record, no tombstone** → UNKNOWN: the record was never saved (server died after the entity was
     saved but before the debounced record save). Rebuild the record with the same id and generation,
     unowned and `adopted`, fire `AnimalTamedEvent(ADOPTED)`. Never deletes.
   - **marker generation > record generation** → CURRENT_CAUGHT_UP: generations only grow, so the record's
     save was lost; the record takes the entity's generation and UUID (nothing else from the entity).
   - **same generation and same entity UUID** → CURRENT.
   - otherwise (older generation, or same generation with a different UUID, i.e. a copy) → STALE.
5. CURRENT: if another valid entity is already the loaded current incarnation (a copied world, two stores
   loading at once) this one is treated as stale; first loaded wins. Otherwise note presence, clear MISSING,
   update world name and role.
6. STALE: `commandBuffer.tryRemoveEntity(ref, REMOVE)`. A plain removal produces no death and no drops; the
   record is not touched. Exception: a tombstoned animal whose role is now a WILD role was released by
   taming with its tag still on, so only the marker is removed.

Adoption (no marker): `shouldAdopt` = supported species, role not ignored, no foreign component, and the
role is an `AdoptRoles`/`AdoptBabyRoles` role or in `ExtraAdoptRoles`. `adoptOrGet(entityUuid)` returns the
existing record if this entity already has one (an add seen twice before the marker landed puts the same
marker again with no new generation and no second event), else creates one (`adopted = true`, no owner),
binds it (generation 1, marker via the command buffer) and fires `AnimalTamedEvent(view, null, ADOPTED)` with
`world.execute`, i.e. after the command buffer ran. The vanilla role is kept.

## Section 11 safeguards

| Safeguard | Status | How |
| --- | --- | --- |
| Stale entities never overwrite newer records | covered | `judge` only ever raises a record's generation (and then only takes generation + UUID). STALE never writes. Death/protection act only on `isCurrent`. |
| Duplicates in previously unloaded chunks | covered | Checked when each entity is added, whenever its chunk loads. |
| No duplicate spawning during simultaneous house load, chunk load, resummon | covered for this wave | Respawns happen only for MISSING records (or an admin command), on the world thread, after the old loaded incarnation is removed; the generation bump makes any unloaded copy stale. "First loaded wins" catches two live copies. Houses do not spawn yet (wave 2). |
| No item drops when removing stale duplicates | covered | `RemoveReason.REMOVE` removal, no `DeathComponent`. |
| Stale entities cannot breed, produce or modify data | covered for my systems | Stale copies are removed on add; protection and death check `isCurrent`. Other roles must check `isCurrent` too. |
| Atomic updates / synchronisation of registrations | covered | One lock in `AnimalStore` for records, indexes and the verdict; atomic file replace. Other roles mutate `AnimalRecord` fields without the lock (shared class design), see risks. |
| Not all duplicates detectable immediately | covered | Nothing assumes it: every load is judged, and a stale copy loading weeks later is still removed. |
| Crashes during resummoning | covered | Both orders are self-healing: record saved but entity not → sweep finds it missing and respawns; entity saved but record not → CURRENT_CAUGHT_UP; record never saved at all → UNKNOWN rebuilds it. Tested in `staleGenerations...` and `recreated...`. |
| House destruction / reassignment cannot orphan records | partly | Records do not depend on houses (`houseId` is only an index); a record with a dangling `houseId` still exists and is recovered near its owner. House behaviour itself is wave 2. |
| Records survive saves and restarts | covered | Round-trip test; `.tmp`/`.prev`/corrupt handling tested. |
| Safe spawn placement | covered | Columns in rings 2 to 4 blocks around the target, only in loaded chunks, through the engine's own `SpawningContext` (room for the model, breathing), and no fluid underfoot. |
| Only ranch animals are deduplicated; never other mods' entities | covered | Everything is gated on the species role map and the marker; foreign-component entities are never removed. |

## Protection and deaths (decisions to review)

- `ProtectOwnedAnimals` on: owned animals take no damage from **any player, the owner included** (no
  accidental kills; butchering will be its own action later). `ProtectFromHostileMobs` on: no damage from
  non-player entities. Falls, fire, drowning still hurt; recovery covers deaths.
- **Unowned adopted animals are not protected**, so vanilla-tamed animals behave exactly as before.
- Death of the current entity: `AnimalDiedEvent` (KILLED with the killer's entity UUID when the last damage
  came from an entity, else OTHER), record → MISSING, recovered later. **Exception: an unowned adopted animal
  killed by a player is removed for good** (tombstoned) as in vanilla; otherwise unowned animals would be
  an endless meat source. The event is fired after the record change, from `world.execute`.
- Vanilla death drops still happen for owned animals that die (e.g. killed while protection is off), and
  the animal comes back. With protection off that is a drop loop for the owner; worth deciding.
- A current entity removed outright (`RemoveReason.REMOVE`, e.g. a kill command) goes MISSING too, unless the
  record is STORED (houses will set that before removing entities).

## Recovery

Every 15 s the background thread posts one task per world that has records; the task walks that world's
records on the world thread. ACTIVE but not loaded while the chunk it was last seen in has been loaded for
3 sweeps → MISSING. MISSING for 60 s → respawn beside the owner (online, same world) or where it was last
seen (chunk loaded); otherwise it waits. Role: the last role seen, else the species' ranch or adopt role.
Houses have no position yet, so "near its house" is not there. `/ranch admin resummon` does it at once beside
the admin. Records in a world that no longer exists are never swept (admin resummon still works).

## Costs

| System | When | Cost |
| --- | --- | --- |
| `AnimalEntitySystem` add/remove | once per NPC add/remove, not per tick | unsupported roles: one map lookup. Ranch animals: archetype check (cached per archetype), one locked map lookup. |
| `AnimalProtectionSystem` | per damage event on entities with the marker only | a few component reads and one locked lookup |
| `AnimalDeathSystem` | per death of a marked entity | trivial |
| `AnimalRecovery` sweep | every 15 s per world with records, on its thread | O(records in that world): map reads and one chunk-loaded check each; a spawn test only for MISSING animals due a respawn. No entity scan, no chunk load. |
| Saves | at most once per `SaveDebounceSeconds`, off-thread | JSON of the dirty worlds |
| `CompatGuard.isManageable` | per call | role check plus a cached per-archetype answer; first sight of an archetype walks its component types once |

## Compatibility

Foreign component classes are matched by name against `ComponentType.getTypeClass().getName()` of the
entity's archetype (also accepting a bare simple name such as `PetOwnerComponent`). The component registry
only exposes types by id, not by class, so resolution is lazy per component type rather than a scan at
start; answers are cached per type and per archetype and dropped when `RanchConfig.get()` returns a new
instance. Confirmed in the installed jars (entry listing only): `endgame.plugin.components.PetOwnerComponent`,
`com.danbagh...familiars.component.FamiliarComponent`, `com.danbagh...mounts.SummonedMountComponent` exist as
configured. Candidates not on the default list: see REQUESTS.

## Verification

- `.\gradlew.bat --no-daemon build`: green, no warnings (`-Xlint:deprecation,removal,unchecked`). 11 JUnit
  tests pass: save/load round trip, owner/world index moves, interrupted write (leftover `.tmp`), corrupt main
  file falls back to `.prev` and is kept, stale generation detection (and that it never changes the record),
  caught-up generation, tombstoned and unknown ids, adoption once per entity when seen twice, ignored role and
  foreign component never adopted, wild/ranch/unsupported roles not adopted.
- `asset_validate` on `_worktrees/CozyTales-Ranching-animal`: valid, 0 schema errors. `ui_check_java`: valid.
- `node tools/check-lang.mjs`: 0 missing.
- `test_run` of `tests/hytale/animal.test.json` (gate + gradle build): 2/2 passed.
- Not verified: anything in a running world (adoption, dedup on chunk load, recovery spawns, protection,
  the shutdown flush, the admin commands).

## Risks / unfinished

- Untested in game. First things to try: a vanilla `Tamed_Cow` gets a record and keeps its role;
  `/ranch admin resummon` twice leaves one cow; restart keeps records; kill an owned cow → it comes back
  after about a minute next to the owner.
- Other roles change `AnimalRecord` fields on world threads without the store lock; the save thread may
  serialise a record mid-change. Worst case a save is slightly inconsistent and the next one fixes it.
- `missingSince` is memory only (no field in `AnimalRecord`): a restart restarts the 60 s delay.

## REQUESTS

1. `command/RanchCommand.java`: add
   `this.addSubCommand(new com.hexvane.cozytalesranching.animal.AnimalAdminCommand());`
2. `api/event/AnimalDiedEvent.java` javadoc says "Its record is gone after this event". With recovery it is
   MISSING afterwards, except butchering and unowned animals killed by a player. Suggest: "The record is
   kept as MISSING and the animal is recovered, unless it was butchered or was an unclaimed adopted animal
   killed by a player."
3. `RanchConfig`: add `RecoveryDelaySeconds` (default 60) and optionally `RecoverySweepSeconds` (default 15);
   now constants in `AnimalRecovery`.
4. `RanchConfig.ExtraAdoptRoles` only works for roles `SpeciesRegistry` already maps (the mod must not touch
   other roles, and an extra role has no species). Suggest a `Role=speciesId` form that `SpeciesRegistry.load`
   adds as `RoleKind.ADOPT`.
5. `RanchConfig.ignoredComponentClasses` defaults: consider adding (found in installed jars, not confirmed to
   be the tamed/owner marker): `com.alechilles.alecstamework.companion.live.TameworkCompanionComponent`,
   `com.stonefallstudio.mount.MountOwnerComponent`, `com.airijko.endlessleveling.ecs.components.SummonOwnerComponent`,
   `com.zenkuro.arcanum.spell.builtin.summon.SummonOwnershipComponent`.
6. For **taming**: on release, remove the marker (`AnimalMarker.type()`) from the entity, or swap it to a WILD
   role (handled here); a released entity left with the ranch or adopt role and the marker is removed as a
   released animal when it is next added. When taming swaps a role or binds an entity, nothing else is needed:
   the role change re-adds the entity with its marker and dedup accepts it.
7. `CozyTalesRanchingPlugin.setup` installs an `InMemoryAnimalRegistry` before `AnimalFeature.register`
   replaces it. Harmless; the line can go.
