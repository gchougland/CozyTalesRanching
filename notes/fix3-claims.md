# fix3-claims: animal houses follow QuestLinesClaims permissions

## Root cause

Not the claims mod. QuestLinesClaims 1.7.0 gates a house the way it gates any block: it replaces the `UseBlock`
interaction and listens to `UseBlockEvent.Pre`, `BreakBlockEvent` and `PlaceBlockEvent`, and asks its own resolver
(`canPlayerAct`) for the block's flag. Our house blocks classify as `INTERACT` (no container, door or seat), breaking as
`BREAK`, placing as `BUILD`. A trusted member passes all of those, so the claims mod let them through.

Our own house rules then refused them:

- Every house action (rename, breeding, collect, move out, call back, locate, move to another house) required the
  actor to be the **house owner** (`HouseRules.checkManage` via `HouseServiceImpl.manage`, and a copy in
  `HousePage.manageCheck`). A member who opened the owner's barn got "not yours" on every button.
- `HousePlacement.BreakSystem` cancelled the break of any house whose owner was not the breaker (unless
  `cozyranch.admin.manageany`), so a claim owner could not pick up a house a member placed in their claim.

## What changed

- `compat/claims/`: `ClaimsAdapter` (what we need from a claims mod), `QuestLinesClaimsAdapter` (reflection, no
  compile dependency), `Claims` (lazy binding, config switch, the decision `claimAllows`).
- `house/HouseAccess` (public): the one place that answers manage / collect / break for a house; `HouseServiceImpl`,
  `HousePlacement.BreakSystem`, `HouseUse` and `HousePage` use it.
- `HouseRules.checkManage(..., claimAllows)` and `HouseRules.mayBreak(...)`: the pure rules.
- `RanchConfig` key `ClaimsIntegration` (default `true`).
- `HouseServiceImpl.assign` also checks the house the animal moves **out of** (it may stand in another claim);
  `unassign` checks the animal's house instead of the animal's owner (same answer outside claims).
- Messages: `cozyranch_house.break.by_other` (always sent to the house owner when someone else took their house down),
  `cozyranch_house.assign.other_owner` (a member who may look after a house tries to move their own animals in).
- `/ranch house debug access` (`cozyranch.admin`): what the nearest house lets you do, the claims mod in use and the
  claim around the house. Also logged. Useful to server owners debugging permissions.

## The API used (QuestLinesClaims 1.7.0, public API only)

`QuestLinesClaimsPlugin.getApi()` (plugin found in `PluginManager.getPlugins()` by class name, then its classloader),
`QuestLinesClaimsAPI.getTopRegionAt`, `getClaimAtBlock`, `isOwnerOfRegion`, `isOwnerOfClaim`,
`canPlayerAct(PlayerRef, world, Vector3i, RegionFlags)`, `RegionFlags.INTERACT / CONTAINER / BREAK`,
`AdminRegion.getId`, `PlayerClaim.getClaimId`. `canPlayerAct` is the same resolver the claims mod's own protection
uses (owner, member and group flags, permission groups, claim defaults, plots, rent/buy regions). Nothing of the
claims mod is copied or shipped. If the API is missing or has another shape, one warning is logged and houses behave
as without the mod.

## Rule table

"In a claim" = the house's origin block is inside a QuestLinesClaims region or chunk claim, the mod is installed and
`ClaimsIntegration` is true. Otherwise every row is exactly the old rule.

| Who | Open the page / summary | Manage (rename, breeding, move residents, call back, locate) | Collect produce | Break / pick up | Move own followers in |
| --- | --- | --- | --- | --- | --- |
| House owner | yes | yes (needs `cozyranch.house.manage`) | yes | yes | yes |
| `cozyranch.admin.manageany` | yes | yes | yes | yes | only into own houses |
| Claim owner (or region tenant/buyer) | yes | yes (needs `house.manage`) | yes | **yes** | only into own houses |
| Claim member/group with `INTERACT` | yes | yes (needs `house.manage`) | only with `CONTAINER` | only with `BREAK` | only into own houses |
| Claim member without the flag, stranger | the claims mod blocks the Use itself | no (NOT_OWNER) | no | no (claims mod or us) | no |
| Anyone, outside claims | yes | owner only (old rule) | owner only | owner only | own houses only |

- The claim stands in for house ownership; it never transfers ownership. Animals and their records stay their owner's;
  naming an animal stays the animal owner's (`HousePage.canName`, unchanged).
- An animal only ever lives in a house of its own owner (unchanged): a member cannot move their animals into the
  owner's barn, and gets "Only animals of {owner} can live in {house}." Mixed-owner houses would touch breeding, produce
  and move targets; not done.
- Breaking a house owned by someone else runs the same removal as before (`HousePlacement.houseBroken`): residents
  inside come out at the door (or go MISSING for recovery), every resident is unassigned, `RanchHouseRemovedEvent`
  carries the breaker, **no animal record is removed**. The house item drops for the breaker like any block.
- A house owner who is no longer a member keeps managing their house by command (the old rule), but the claims mod
  blocks their Use and break of the block itself.
- Plot worlds (QuestLinesClaims plot generator) are not detected as claims: houses there keep the old rule.

## Verified

- Unit tests (`compat/claims/ClaimsTest`, 11; `house/HouseRulesTest`, 4 new): owner always, member exactly by flag,
  outside a claim nothing, action-to-flag mapping, config key decodes and defaults on, binding with no claims plugin
  (other plugins present) gives `NONE` and never throws, API not ready returns null (retry), an API of another shape
  gives `NONE`, the reflective adapter end to end against a stand-in with the same method shapes, offline player never
  allowed; old rule unchanged when the claim says nothing; break rule; claim access never mixes owners.
- Gate: `gradlew build` green (301 tests), `asset_validate` valid (0 errors), `ui_check_java` valid,
  `check-lang` 0 missing, house test cases (6) green.
- Server on 15541, **without** the claims mod: mod loads as before; owner bot `debug place cow`, `debug access` gives
  `claims none, claim -, manage OK collect OK break true`; a second bot gets `manage NOT_OWNER collect NOT_OWNER
  break false` (today's behaviour).
- Server on 15541 **with** `questlines-claims-1.7.0.jar` (copied into the run's mods only, deleted after), bots Owner
  and Member:
  - Owner claimed its chunk through the claim map page (`cell` event); log: "Houses follow QuestLinesClaims claim
    permissions"; Owner on its barn: `claim:1 (yours) manage OK collect OK break true`.
  - Member, not in the claim: `NOT_OWNER / NOT_OWNER / false`. Added as `trusted`: `OK / OK / true`. Moved to
    `untrusted`: back to `NOT_OWNER / NOT_OWNER / false`.
  - Member (trusted) opened Owner's barn page (`debug use`) and pressed Breeding: notice `result.breeding.off`, the
    house's breeding turned off. After `untrusted`: the same button gives `result.not_owner` and nothing changes.
  - Member (trusted) placed a chicken coop in Owner's claim; Member then made `untrusted`. Owner on Member's coop:
    `claim:1 (yours) manage OK collect OK break true`; Member on own coop: still `OK / OK / true`.

## Not verified

- No real block break by a bot: the bot cannot swing at a block. The break decision was shown through
  `debug access` against the live claims mod, and `BreakSystem` now calls the same `HouseAccess.mayBreak`. Gibson should
  try: owner breaks a member's house in the owner's claim (house item drops, owner of the house gets the `by_other`
  line, residents unassigned and safe).
- A group with `INTERACT` but not `CONTAINER` or `BREAK` (needs the claim's group editor UI): covered by unit tests only.
- The Use gesture through a real client (claims mod's `UseBlock` replacement then our interaction): bots used
  `/ranch house debug use`, which skips the claims mod's Use gate. Reading the claims jar, a member with `INTERACT`
  passes that gate.
- Nobody has seen any of this in the client.

## Cost

No ticks, no scans. A few reflective calls per house action or house break (claim lookup by chunk, one
`canPlayerAct`), only when the old rule would refuse. Binding is once per config load.

## REQUESTS

- `houseui/HousePage.java` (houseui role): `manageCheck` now asks `HouseAccess.manage`, and `collect` asks
  `HouseAccess.collect` instead of the inline owner check. Smallest change that lets claim members use the page.
- `CHANGELOG.md` (coordinator): "Animal houses inside a QuestLinesClaims claim follow the claim's permissions:
  trusted players can look after them, and the claim owner can always pick up a house placed in their claim. Turn it
  off with `ClaimsIntegration` in the config."
- `docs/DECISIONS.md` (coordinator): the rule table above; members never move their own animals into another
  player's house; collect maps to the claim's CONTAINER flag.
