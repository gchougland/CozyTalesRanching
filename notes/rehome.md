# Rehome (wave 3, batch 2)

Giving animals away, moving them between houses, the Butcher's Block, and protection from hostile mobs.
Branch `wave/rehome`. Everything is in `com.hexvane.cozytalesranching.rehome` except the hostile-mob part of
`animal/AnimalProtectionSystem.java`.

## What was built

| Piece | What it does |
|---|---|
| `/ranch give <animal> <player> [--force]` (`GiveCommand`, `cozyranch.transfer`) | Offers an owned animal to an online player. The recipient gets a chat line with `/ranch accept` and `/ranch decline`. `--force` needs `cozyranch.admin.manageany`, works on anyone's animal and transfers at once with no offer. |
| `/ranch accept`, `/ranch decline` (`AcceptCommand`, `cozyranch.transfer`) | Accept takes the pending offer, re-checks it on the animal's world (the giver still owns it, the recipient is under `MaxAnimalsPerPlayer`), then transfers. Offers expire after 60 s (`RehomeRules.OFFER_TTL_MS`); one offer per recipient and one per animal (a newer one replaces the older). |
| Transfer (`TransferService.transfer`) | Removes the animal from its house through `HouseService.unassign` (as the admin, else the giver, else the new owner), changes `ownerId`, stops following (clears the follow target, sets the leash where it stands, "Stay" state), keeps everything else (name, hearts, traits, personality, happiness, age), `markDirty`, then fires `AnimalOwnerChangedEvent` after the save is queued. If the house refuses the unassign, the owner is reverted and both players are told. |
| `/ranch move <animal> <house name or id prefix or nearest>` (`MoveCommand`, `cozyranch.house.manage`) | Calls `HouseService.assign` on the house's world. `nearest` picks the nearest own house of that species with room, other than the current one. The result is logged and sent as a lang message. |
| `CozyRanch_ButcherBlock` | Placeable workstation with the fields from `notes/art.md` REQUESTS item 9 (model, texture, icon, hitbox, wood sounds and particles, NESW rotation). Recipe: 8 `Wood_All` + 1 `Ingredient_Bar_Iron` at the Farmer's bench (`Farmingbench`, tier 1). Use runs the `CozyRanchButcher` interaction. |
| Butchering (`ButcherService.process`) | Needs `cozyranch.butcher`. Processes the nearest of the player's **following, adult** animals within 8 blocks of the block. Named animals and animals with 4+ hearts need a second use within 10 s; the chat says why (`confirm_named`, `confirm_hearts`). Babies are refused with a message. It never touches another player's animal (owner-only, even for admins) or anything `SpeciesRegistry.byRole` / `CompatGuard.isManageable` rejects. On success: record removed, entity removed with a dust poof (`Block_Land_Hard_Dust`) and `RanchSounds.BUTCHER`, items go into the inventory and spill to the ground when full, then `AnimalDiedEvent(BUTCHERED)` fires. |
| `/ranch butcher` (`ButcherCommand`, `cozyranch.butcher`) | The same path as using the block, for a Butcher's Block within 5 blocks of the player. This exists because the headless bot cannot use blocks. |
| `/ranch rehome prep / hurt` (`RehomeDebugCommand`, `cozyranch.admin`) | Test commands: `prep` sets name, hearts, happiness and traits on the player's followers; `hurt` makes the nearest non-player, non-ranch NPC within 16 blocks hit each follower through `DamageSystems.executeDamage`. |
| Hostile-mob protection (`AnimalProtectionSystem`) | `ProtectFromHostileMobs` is read as `RanchConfig.protectFromHostileMobs() || RehomeSettings.protectFromHostileMobs()`. The local fallback is `<data>/Rehome.json`, key `ProtectFromHostileMobs`, default true. Damage to an owned, managed animal from any non-player entity is cancelled; one log line per animal per 10 s. The player rules are unchanged. |

### Yields

`ButcherRules`: rolls of the animal's vanilla drop list (`Role.getDropListId()`, cow = `Drop_Cow`) =
`2 x (0.75 + 0.75 x happiness) x (1 + 0.1 x hearts (max 5)) x ProductionMultiplier (bountiful)`.
The whole part is guaranteed, the fraction is a chance, and each roll has a `BonusProductChance` (lucky) chance of
one extra roll. Minimum 1 roll for an adult. A species with an empty drop list (the frogs) is refused and kept.

## Verified

- Unit tests (`src/test/java/.../rehome/`, 17 tests, all pass): yield factor and clamping, rolls and lucky
  statistics, the confirmation rule and its 10 s window, offer expiry (including a clock going back),
  `OfferBook` replacement, who may give, force, accept and butcher, name and id matching, the settings parse.
- Gate: `gradlew build` green with no warnings, check-lang 0 missing, `asset_validate` on
  `_worktrees/CozyTales-Ranching-rehome` valid with 0 schema errors, `ui_check_java` valid.
- Render: `notes/pictures/rehome/butcherblock_item.png` (stump, board, cleaver and towel, 0.82 x 1.01 x 0.82
  blocks, no notes). Golden in `tests/hytale/golden/rehome/`.
- Existing tests: `tools/sessions/rehome-tests.json` runs copies of the nine `tests.json` files plus
  `rehome.test.json` from `build/tests-copy` (paths rewritten to the worktree, the care server on 15543).
  27 of 28 passed; the failure was "feed in the trough", whose first `/ranch house debug place cow` failed on
  the terrain at that run's random spawn. Re-run alone: care 4 of 4 pass.
- Live, `tools/sessions/rehome-live.json` on port 15543, two bots (RanchBot and RanchPal), server stopped after.
  Log `HytaleMCP/.hytale-mcp/logs/0ed4e8ca-a906-4be2-84ee-38a3a2ce710c.log`:
  - `Rehome registered: interaction CozyRanchButcher, hostile-mob protection on (Rehome.json fallback)`
  - Move: `Move cow b03c9beb-... "Daisy" to house 87e3db5d-... (null) by RanchBot: OK` (from house b76626f1, assigned by `house debug use`).
  - Give and accept: `Transfer offer: RanchBot offers cow b03c9beb-... "Daisy" to RanchPal`, then
    `Transfer cow b03c9beb-... "Daisy": owner 11111111-... -> 22222222-... (RanchPal), hearts 2, traits [], personality null, left house 87e3db5d-..., now in house null`
  - Admin force: `Transfer (forced) cow b03c9beb-... "Daisy": owner 22222222-... -> 11111111-... (RanchBot), ...`
  - Decline: `Transfer offer of b03c9beb-... declined by RanchPal`; a second `/ranch accept` got `accept.none`.
  - Unnamed adult: `Butchered cow 572767f8-... for RanchBot at the block 703.5 123.5 560.5: happiness 1.00, hearts 3, traits [bountiful, lucky], factor 4.88, 5 roll(s) of Drop_Cow -> {Food_Beef_Raw=7, Ingredient_Hide_Medium=5}`
  - Named: `Butcher: cow 225c40d9-... "Bessie" (hearts 1) needs confirmation from RanchBot` (chat `confirm_named`);
    after 14 s it asked again (the window ran out); used twice within 2 s:
    `Butchered cow 225c40d9-... "Bessie" ... factor 2.64, 3 roll(s) of Drop_Cow -> {Food_Beef_Raw=4, Ingredient_Hide_Medium=4}`
  - Calf: chat `cozyranch_rehome.butcher.baby`, nothing processed.
  - Hostile: a natural attack, `Blocked 24.965052 damage from Wolf_Black to owned cow 27817b39-... (hostile-mob protection)`
    (twice), and the debug path `Rehome hurt: Wolf_Black hits cow ... for 30.0, cancelled true` for two cows.

## Faked or unfinished

- **Using the real block is untested in game.** The bot cannot use blocks, so the live run used `/ranch butcher`,
  which calls the same `ButcherService.process`. The interaction is registered and the item validates.
- Offers live in memory only: a restart drops them. Expiry is lazy (checked on accept); the giver is not told
  when an offer expires.
- An animal name with spaces cannot be typed as one argument; use `this` (the animal you look at) or an id prefix.
- A `STORED` animal moved to another house stays inside until the house system's next release (morning).
- The second barn in the live session needs a try at four spots, because `/ranch house debug place` fails on
  some terrain (the house role's command, not mine).

## Decisions to review

- **Hostile-mob protection applies everywhere, not only near the house.** It is one cheap check per damage event;
  followers are most at risk away from home; and "near the house" would leave animals in a pen beside the
  house unprotected. Any non-player attacker counts as hostile (wolves, skeletons, other NPCs).
- The recipient gets the animal in "Stay" where it stands, not following and with no house.
- A normal transfer respects `MaxAnimalsPerPlayer`; an admin force ignores it.
- Butchering is owner-only even for admins (`manageany` does not let an admin process someone's animal).
- The yield numbers above; an empty drop list refuses rather than consuming the animal for nothing.
- Poof particle `Block_Land_Hard_Dust` (vanilla, soft and brown).
- Recipe 8 wood + 1 iron bar at the Farmer's bench, tier 1.

## Costs

No tick systems. The damage system does the same work per damage event as before, plus one map read for the log
rate limit. `/ranch butcher` scans an 11 x 5 x 11 cube of loaded sections, only when the command runs. Lookups go
through records (`ownedBy`, `inWorld`), never entity scans.

## REQUESTS

1. **Register the commands** in `command/RanchCommand.java` (my working copy has these lines; do not merge my copy):
   ```java
   this.addSubCommand(new com.hexvane.cozytalesranching.rehome.GiveCommand());
   this.addSubCommand(com.hexvane.cozytalesranching.rehome.AcceptCommand.accept());
   this.addSubCommand(com.hexvane.cozytalesranching.rehome.AcceptCommand.decline());
   this.addSubCommand(new com.hexvane.cozytalesranching.rehome.MoveCommand());
   this.addSubCommand(new com.hexvane.cozytalesranching.rehome.ButcherCommand());
   this.addSubCommand(new com.hexvane.cozytalesranching.rehome.RehomeDebugCommand());
   ```
   `CozyTalesRanchingPlugin` already calls `RehomeFeature.register` and `reload`.
2. **Config owner:** change the `RanchConfig` default of `ProtectFromHostileMobs` to `true`. Then in
   `AnimalProtectionSystem` replace `config.protectFromHostileMobs() || RehomeSettings.protectFromHostileMobs()`
   with `config.protectFromHostileMobs()`, and drop `RehomeSettings` and `Rehome.json`.
3. Optional: a bundled tuning file for the yield numbers in `ButcherRules` if Gibson wants them configurable.
4. Sounds role: `RanchSounds.BUTCHER` (`SFX_CozyRanch_Butcher`) is played; it needs a real sound event if it does
   not have one yet.
5. At merge, rewrite the worktree paths in `tools/sessions/rehome-live.json` and `rehome-tests.json`
   (`_worktrees/CozyTales-Ranching-rehome` -> `CozyTales-Ranching`, and `build/tests-copy` -> `tests/hytale`).
