# Fix wave 3, follow: how to stop following, following a rider

Branch `wave/fix3-follow`. Two items from the 2026-10-24 playtest. Live runs on a local server (port 15542,
insecure, headless bot `RanchRider`, stone platform at y 150, Averion Mounts 1.0.6 copied into the run's mods folder
from `_worktrees/_fix3-follow-thirdparty`, never into the repo). All servers and bots stopped.

## 1. "How do I make it stop following?"

`tame.done`, `claim.done` and `follow.on` now say it:

- `The {animal} has befriended you and starts following you. To make it stay, crouch and use it (or type /ranch stay).`
- `{animal} is now yours and starts following you. To make it stay, crouch and use it (or type /ranch stay).`
- `{animal} is following you. To make it stay, crouch and use it.`

Chat cannot show a key binding. `FormattedMessage` (protocol) has text, params, colour, bold/italic, link and image,
nothing for a binding; `{key}` is filled by the client only in interaction hints (vanilla writes
`Crouch and press [{key}] to open` only in `interactionHints.*`). So the lines say "crouch and use", as asked. Still
sent through `RanchChat.tell`. en-US only.

## 2. Animals stop following while the owner rides an Averion mount

**Root cause.** The follow pass (`FollowSystem`) and the animal's `LockedTarget` both pointed at the owner's player
entity and read its `TransformComponent`. Averion Mounts rides the vanilla *NPC mount* way (javap of
`MountManager.mountPlayer`/`dismountPlayerExecute`): `NPCMountComponent` on its horse, `Player.setMountEntityId(id)`
and a `MountNPC` packet; dismount sets it back to 0. The same path vanilla horses use (`ActionMount`). On that path
the client steers the mount and reports it with `MountMovement`, which the server applies to the **mount entity's**
transform only (`GamePacketHandler.handleMountMovement`). The rider's own transform moves only when its own
`ClientMovement` is accepted, and those are dropped whenever the packet's `mountedTo` differs from
`PlayerInput.mountId` (which Averion flips by re-adding a `MountedComponent` to the rider). Averion itself treats rider
and mount drifting apart as normal: `MountFreezeSystem` kills and respawns the mount when the rider's transform is over
20 blocks from it.

Live control (old jar, same steps): mounted, the horse moved 302.5 -> 316.5 by `MountMovement`; 8 s later the
following cow stood at `298.3 151.0 171.7`, 2.4 blocks from the rider's server position (300 151 170) and 18 blocks
behind the horse. That is "it stops following when I mount".

**What I could not prove:** whether a real client also keeps sending rider `ClientMovement` while on an NPC mount
(the headless bot only sends what I tell it, and there is no client source). If it does, the rider's transform lags
or jumps rather than freezing. The fix below does not depend on it.

**Fix.** `taming/FollowAnchor` (new): while `Player.getMountEntityId() > 0` and that network id resolves to a loaded
entity with a transform, followers follow **the mount**; otherwise the owner. `FollowSystem` uses it for the
`LockedTarget`, the distance and stuck checks and the catch-up teleport (lands next to the mount, facing its way);
`AnimalInteractions.setFollowing` uses it too, so toggling follow while riding targets the mount. On dismount the id
is 0 and the next pass (1 s) points them at the owner again. Each switch is logged once:
`Ranch followers of <owner> now follow the mount <uuid> at x y z` / `... now follow their owner at x y z`.
Core API only (`Player.getMountEntityId`, `EntityStore.getRefFromNetworkId`), no Averion classes, nothing deprecated;
without a mount mod nothing changes. `CompatGuard` untouched. Vanilla `MountedComponent` rides (minecarts, seats)
already move the rider's transform (`MountSystems.HandleMountInput`), so they are left as they were.

**Live (fixed jar):**
- `/ranch breed testhouse cow`, `/ranch follow`; `/mount give`, Summon from Averion's page, Use packet on the horse
  -> `MountNPC` (entity 397). Log: `Ranch followers of 5f0c7a3e-... now follow the mount 54c455dd-... at 302.5 151.0 172.5`.
- Same ride as the control (horse to 316.5, rider left at 300): cow at `312.5 151.0 172.2`, state `Follow.Default`,
  `LockedTarget` "other" (the mount), 12.7 blocks from the rider's stale position.
- Rider and horse on to 346.5 over ~20 s: the cow kept up on foot, `342.6 151.0 172.2`, 3.9 blocks away, target the mount.
- Jump of 46 blocks: Averion's own 20-block rule removed the horse (`DismountNPC -1`). Log:
  `... now follow their owner at 346.5 151.0 172.5`, then
  `Ranch animal 2dc0b385-... brought back to its owner (too far, 46.2 blocks away, state Follow.Default)`; the cow stood
  at `298.5 151.0 190.5`, 2 blocks from the owner, target "you".

**Not exercised:** the catch-up teleport *while still mounted* (every far jump trips Averion's warp rule first; the code
path is the same `bringBack` with the mount's transform), a real client riding, vanilla horses (same server path,
not driven), flying Averion mounts.

## Gate

`gradlew build` green (unit tests incl. new `taming/FollowAnchorTest`: mount id rule, anchor choice, a mount
outrunning the animal counts as stuck, the three lines name "crouch and use it"); `asset_validate` valid, 0 schema
errors; `ui_check_java` valid; `node tools/check-lang.mjs` 0 missing; taming test cases via a copy in
`build/tests-copy` pointed at the worktree.

## Files

`taming/FollowAnchor.java` (new), `taming/FollowSystem.java`, `taming/AnimalInteractions.java`,
`cozyranch_taming.lang`, `src/test/java/.../taming/FollowAnchorTest.java`, `notes/fix3-follow.md`.

## REQUESTS

1. **behaviour (status line)**: `/ranch behaviour status --animal=` prints target `other` when an animal follows its
   owner's mount. It could say "your mount" when the `LockedTarget` is the entity the owner rides; not needed
   for the fix.
2. Merge note: `tests/hytale/taming.test.json` keeps the main project paths; my run used a copy in `build/tests-copy`.
