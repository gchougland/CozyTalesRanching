# Taming (wave 1)

Branch `wave/taming`. Covers befriending, claiming, petting and feeding, following and staying, naming, release,
and the frog food. Not run on a server yet. Everything below "Untested" needs a real client.

## How a player uses it

- **Befriend a wild cow, chicken, pig or sheep.** Hold a food from its species file and press Use (F) on it.
  Each feeding takes one item and says `(n/needed)`. A favourite treat counts as two. When the count reaches
  `FeedingsToTame`, `AnimalTameAttemptEvent` fires. If nobody cancels it, the animal becomes a `CozyRanch_<Species>`
  (or the baby role), gets a record and starts following. Pressing Use with an empty hand or a wrong item only
  shows a hint. Progress belongs to one player and is forgotten after 5 minutes without food or on restart.
- **Shy animals.** At a feeding, if the player is sprinting or running and not crouching, the animal startles
  with probability `Shyness`. It goes to the vanilla `Panic` state (flees) and the player is told to crouch.
- **Frogs.** Vanilla frogs never show a Use prompt and flee within 4 blocks, so Use cannot reach them. The new
  **Jar of Crickets** (`CozyRanch_Food_Crickets`) offers food on **right-click** (Secondary) to whatever animal the
  player looks at, within 6 blocks. It works on every supported animal and goes through the same code as Use.
  Pocket recipe: 2 `Ingredient_Fibre` and 1 `Plant_Crop_Wheat_Item` make 2 jars. It needs the Frog.json request
  below; until then frogs say "not interested".
- **Claim an adopted animal** (a record with no owner). The first player to press Use on it owns it, with no
  feeding. The same gate applies as for taming (blocked world, `cozyranch.tame`, limit). Vanilla `Tamed_*` adopt
  roles are swapped to our ranch role. `AnimalOwnerChangedEvent(null -> player)` fires.
- **Pet** (empty hand, Use): hearts particles and the `Happy` animation if the model has one. It gives 0.25
  heart once per animal per in-game day, measured on the world's game clock.
- **Feed an owned animal** (food in hand, Use): takes one item. Basic food gives 0.1 heart, preferred 0.15,
  favourite 0.3. After a meal the animal is "full" for 20 s of real time and no item is taken.
  `heartProgress` rolls over into whole hearts, up to 5 hearts, and never decays.
  `AnimalFriendshipChangedEvent` fires only when the whole-heart count changes.
- **Follow or stay.** Crouch and press Use on your animal to toggle (needs `cozyranch.follow`).
  `/ranch follow` and `/ranch stay` act on the animal you look at. With nothing looked at, `follow` calls all
  your animals within 16 blocks and `stay` stops all your followers in this world.
- **`/ranch name <name>`** names the owned animal you look at (`cozyranch.name`). The name is trimmed, control
  characters are removed, it is cut to 24 characters, and it is shown on a nameplate.
- **`/ranch release [--confirm]`** releases the owned animal you look at (`cozyranch.release`). A named
  animal needs `--confirm`. `AnimalReleasedEvent` fires while the record exists, then the record is removed and
  the entity is swapped to the species' first wild (or wild baby) role.
- **`/ranch animals`** lists your animals: name or species, hearts, world, and whether they follow.

Admins with `cozyranch.admin.manageany` can feed, pet, toggle, name and release anyone's animals.
`cozyranch.admin.bypassworlds` lets a player tame and claim in blocked worlds.

## Roles

`Server/NPC/Roles/CozyRanch/Template_CozyRanch_Animal.json` is our own Abstract template; no vanilla role is
edited or overridden. A Variant can only change template parameters, so the ranch behaviour needed a template.
It has four states:

- `Idle`: wanders inside `WanderRadius` around its leash point and watches players within 5 blocks.
- `Follow`: seeks the `LockedTarget` and stops at `FollowStopDistance`.
- `Stay`: stands still and watches players.
- `$Harvest`: the vanilla livestock harvest (milk, shear), copied over.

`Follow` and `Stay` are only ever set from Java, so they use `IgnoreMissingSetState`. Use prompts carry `{key}`:
feed when holding one of `FoodItems`, pet with an empty hand, interact otherwise. There are nine Variants
(cow, calf, chicken, chick, pig, piglet, sheep, lamb, frog), all using vanilla models and drops by id.
Each has 0 errors and 0 warnings in `asset_check`.

Variant `Modify` keys are not checked by `asset_check`. I checked them with a script against the template's
`Parameters`: every key exists and the types match.

## Scoping (what is not touched)

- One `UseEntityEvent.Pre` system. It returns at once unless the target is an NPC whose role is in a species
  file and `CompatGuard.isManageable` is true. Unsupported species, other mods' NPCs and every other entity keep
  vanilla behaviour.
- The vanilla Use is cancelled only for supported wild roles (so the vanilla `Tamed_*` swap never runs for
  them) and for adopt roles that have a record (so vanilla feeding does not also take the item). Our own
  ranch roles are not cancelled, because their template does nothing on use.
- The food items themselves are not modified. The only new behaviour lives on the new cricket jar.
- Changes are queued with `world.execute`; the event handler only reads. Before taking an item, the code checks
  again that the player still holds it.

## Per-tick cost

- The use system runs only when a player presses Use on an entity. It does a few component reads and one
  registry lookup.
- `FollowSystem` adds a float each tick per world. Once a second it queues one pass when the world has players.
  The pass loops over players in the world, then `ownedBy(player)`, then `getRefFromUUID`. There are no entity
  scans and no block scans. Block reads happen only when an animal must be teleported: at most 10 angles × 2
  distances × 7 heights × 3 cells, from loaded sections only (`SectionReader`, nothing loads).
- `InMemoryAnimalRegistry.ownedBy` streams all records. The real registry should index by owner.

## Teleport rules

An animal is teleported when it is farther than `FollowTeleportDistance` from its owner, or has been stuck
(moved less than 0.3 blocks per second while more than 4 blocks away) for `FollowStuckSeconds`. Candidate spots:
2.5 or 3.5 blocks behind the owner first, then ±30°, ±60°, ±90°, ±135°, 180°. Heights tried: the owner's
height, then +1, −1, +2, −2, +3, −3. A spot is used only with a solid, non-damaging floor that has no fluid,
and two empty, fluid-free, non-damaging cells above it. If no spot is found, it tries again next second.
**Cross-world following is not supported.** An owner in another world or offline is skipped, and the animal
waits in `Follow` with no target and stands still.

## Untested or known gaps

1. Nothing has run on a server. All engine calls compile against 0.7.3 source but are unverified in play.
2. Cancelling `UseEntity` makes the unarmed Use chain fall through to `BreakBlock` with Harvest. I expect it to
   find no target block when the player aims at an entity, but this is unverified. If it harvests grass or a
   crop behind the animal, the fix is to stop cancelling and instead take the vanilla tame path out with a
   role-change listener.
3. Frog colour: blue and orange frogs become the green `CozyRanch_Frog` when tamed (one ranch role, appearance
   changes). They need per-colour ranch roles in the species file if that matters.
4. `Stay` is not saved. After a chunk reload or restart, a staying animal goes back to `Idle` (wanders near
   where it loaded). `following=true` is saved and re-applied within a second.
5. The startle check happens only at a feeding, not on a fast approach. Vanilla wild roles have their own
   flee behaviour.
6. `Happy` animation: the vanilla livestock models probably lack it. The code checks the model and skips it
   silently.
7. The cricket jar uses vanilla art by reference: the green potion bottle model and texture, and the
   `Potion_Poison` icon. It is placeholder art.
8. Whether the `Teleport` component moves NPCs smoothly (rather than only players) is unverified.

### Exact server test steps

1. Apply the REQUESTS below, then build. Start a local server with the jar. Join in Adventure mode.
2. `/ranch ping` should succeed. The log should contain `Taming registered: interaction CozyRanchOfferFood`.
3. Spawn `Cow` near you (`/npc spawn Cow`). Hold `Plant_Crop_Wheat_Item` and press F three times. Expect
   `(1/3)` and `(2/3)`, then "has befriended you", hearts, and the cow becoming `CozyRanch_Cow` and walking
   after you. Check that no wheat is taken on a 4th press while it is full.
4. Hold a pumpkin on a fresh wild cow: 2 presses tame it (the favourite counts double).
5. Sprint at a wild sheep (Shyness 0.4) and press F with lettuce several times. It sometimes panics. Crouching,
   it never panics.
6. With an empty hand, press F on your cow: hearts and "Pet again tomorrow" on the second press.
   `/time` to the next day, then pet again: friendship rises.
7. Crouch and press F on the cow: "will stay here". Walk away 30 blocks: it stays. Crouch and press F again,
   walk 30 blocks: it teleports behind you within about a second. Box it in with blocks: within about 6 s
   it teleports out.
8. Make jars (pocket crafting). Right-click a `Frog_Green` from 5 blocks: `(1/2)`, then tamed.
9. `/ranch name Daisy` while looking at the cow, then `/ranch release`: you are asked to confirm.
   `/ranch release --confirm`: the cow becomes a wild `Cow`. `/ranch animals` no longer lists it.
10. Add the world to `TamingBlockedWorlds` and `/ranch reload`: feeding a wild cow says "cannot be tamed in this
    world". Your existing animals still follow and can be petted.
11. Set `MaxAnimalsPerPlayer` to 1 and try to tame a second animal: refused.
12. Press Use on a wild `Horse` and on a villager or another mod's NPC: vanilla behaviour, nothing from us.
13. Spam F at a wild cow in front of tall grass and watch whether the grass is harvested (gap 2).

## REQUESTS

1. **Frog food** (`src/main/resources/Server/CozyTalesRanching/Species/Frog.json`): replace
   `"BasicFoods": ["TODO_taming_agent_picks"]` with `"BasicFoods": ["CozyRanch_Food_Crickets"]`.
   Every other food id in the five species files exists in vanilla 0.7.3: Wheat, Corn, Rice, Tomato,
   Lettuce, Cauliflower, Pumpkin, Turnip, Potato, Aubergine and Carrot, each as `Plant_Crop_*_Item`.
2. **RanchCommand** (`command/RanchCommand.java`): add these lines to the constructor:
   ```java
   this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.NameCommand());
   this.addSubCommand(com.hexvane.cozytalesranching.taming.command.FollowCommand.follow());
   this.addSubCommand(com.hexvane.cozytalesranching.taming.command.FollowCommand.stay());
   this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.ReleaseCommand());
   this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.AnimalsCommand());
   ```
3. **Real AnimalRegistry** (animal role): taming calls `bindEntity` right before requesting a role swap. Any
   marker component it adds must survive the swap; the engine keeps the holder's components. `remove(animalId)`
   must also drop that marker from a loaded entity, so a released animal reads as wild. `ownedBy` is called
   once a second per online player and should be indexed by owner.
4. **Species products** (FYI, not taming): `Cow.json` and `Sheep.json` list `Products[].Item` as
   `Drop_Cow_Harvest` and `Drop_Sheep_Harvest`. Those are drop lists in vanilla, not items.
5. **Translations**: `cozyranch_taming.lang` needs es-ES and pt-BR copies (53 keys).
6. No new permission nodes are needed. The code uses TAME, FOLLOW, NAME, RELEASE, ADMIN_BYPASS_BLOCKED_WORLDS
   and ADMIN_MANAGE_ANY.
