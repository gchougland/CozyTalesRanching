# Fix wave 1, behaviour: follow after housing, behaviour clips, water lilies

Branch `wave/fix-behaviour`. Three bugs from Gibson's playtest of 2026-10-23. Live runs on a local server
(port 15541, insecure, headless bot, stone platform at y 150 built with `/setBlocks`), all servers stopped.

## Bug 2: behaviour clips never seen (fixed, proven on the wire)

**Root cause.** Every ranch clip (Graze, TailSwish, MudRoll, DustBathe, PeckGround, Snuffle, Sleep for naps,
Happy, ...) was played on the `Emote` animation slot. On the client that slot plays *cosmetic emote ids*, not
the model's animation sets. Gibson's own client log of the playtest says so, 47 times:

```
2026-10-23 11:26:18 DEBUG No emote with id Snuffle on entity with model NPC/Livestock/Pig/Models/Model.blockymodel
2026-10-23 11:26:22 DEBUG No emote with id PeckGround on entity with model NPC/Livestock/Chicken/Models/Model.blockymodel
2026-10-23 11:26:56 DEBUG No emote with id Sleep on entity with model NPC/Critter/Frog/Models/Model.blockymodel
```

The server agrees: `AnimationUtils.playAnimation` skips the model check for `Emote` (the id is not a model set),
and `/emote` sends cosmetic emote ids on that slot. Vanilla NPCs play every flavour clip (Eat, Laydown, Sleep, Wake,
Alerted, Roar) on `Status` (697 uses in vanilla roles, 0 on `Emote`). The clip ids, model assets and role
appearances were all fine: `animation_render` of Graze (cow), MudRoll (pig) and DustBathe (chicken) through
`Server/Models/CozyRanch/*.json` bind with no missing nodes, and the models list every set.

**Fix.** `behaviour/AnimalClips` (public): plays a model clip on `Status`, `playFor` clears it after a time
(a one-shot clip on `Status` holds its last frame and would freeze the legs), `stop` clears it. The planner
(`BehaviourPlanner.play`) and `AnimalControl.cheer` (Happy on pet, feed, tame) use it.

**Verified.** Live, the bot received exactly what a client gets:
`PlayAnimation {entityId 531, animationId TailSwish, slot Status}`, `{532, Graze, Status}`, then the clears
`{531, slot Status}` and `{532, slot Status}` when the sessions ended; a natural nap sent `{532, Sleep, Status}`
and the follow order cleared it. New test case "behaviour clips resolve on the real models" renders Graze,
TailSwish, MudRoll, DustBathe, LilyHop and Pronk through the model assets.

**Only the client can confirm** that `Status` beats the walk and idle clips on these models as it does for
vanilla's Eat (the slot order is the client's), and how the blend in and out looks.

## Bug 3: animals break water lilies (fixed, proven with a control)

**Root cause.** Vanilla `Plant_Flower_Water_*` has a `CollisionEnter` interaction: `Condition`
(`RequiredGameMode: Adventure`) then `BreakBlock`. `ConditionInteraction` checks the game mode only when the
entity is a player, so for any NPC it passes. NPC motion controllers run block triggers
(`MotionControllerBase.processTriggers` -> `CollisionResult.defaultTriggerBlocksProcessing`) unless
`enableTriggers` is false. No role or motion JSON field reaches that flag; only `setEnableTriggers`.

**Fix (no vanilla asset touched).** `behaviour/GentleStepSystem`, a `RefSystem` ordered after the engine's
`RoleActivateSystem`: when an NPC with a `CozyRanch_*` role is added (spawn, chunk load, every role swap, since a
swap re-adds the entity) its active motion controller gets `setEnableTriggers(false)`. Block damage (cactus, fire)
is a separate switch and stays on. Cost: one string check per NPC added, nothing per tick. Side effect: ranch
animals no longer set off any block trigger (pressure plates, other trampleable plants). Wild visitor roles
(`CozyRanchWild_*`) and vanilla animals are unchanged. `/ranch behaviour gentle off|on` is an admin test switch
for animals added afterwards; `/ranch behaviour status` shows how many animals walk softly.

**Verified, same run, same lily field type on stone:**
- Control (`gentle off`, a claimed `CozyRanch_Cow` following the bot across lilies at z 160..166): every lily on
  its path broke, `block get` 348/345/342/339 151 162 = `Empty`, while 345 151 165 off the path stayed a lily.
- Fix (`gentle on`, a new cow across lilies at z 184..190): 348/345/342/339/337 151 186 all still
  `Plant_Flower_Water_Green` after the cow walked to 337.6 151.1 186.8. An earlier run: two following cows crossed
  a 17 x 19 lily field, five sampled cells on their path intact.
- Not tested: lilies on real water, frogs hopping in a pond (frogs use the same Walk controller and role
  template, so the same switch applies).

## Bug 1: follow after housing (partly fixed; the exact report not reproduced)

**What I could and could not reproduce.** On the server the follow state itself is right after housing. Cow housed
with the real assign path (`/ranch house debug use`), Idle by its door, then `/ranch follow`: state
`Follow.Default`, `LockedTarget` = the bot, and it walked 14.7 -> 10.7 -> 3.9 blocks to the bot, then after the
bot moved 25 blocks, 21.6 -> 13.5. No code put a following resident back into Idle in any run (the house pass and
the planner both skip following animals; the planner only resets its own states). `isRoleChangeRequested`,
duplicate entities and the leash were checked and are not involved.

What did reproduce Gibson's picture is a fence. Housed animals live in pens. With the owner outside a fenced ring,
a following cow walked around the ring looking for a way in (339 178 -> 324.5 191.5, round the corner) instead of
coming over, and in a pen with a gap the cows slid along the fence. The old "stuck" rule only fired when the
animal moved less than 0.3 blocks a second, so an animal that circles or slides never counted as stuck and only came
over once the owner was 24 blocks away: "they don't follow, they wander, they teleport when too far".

**Fixes.**
1. `taming/FollowSystem`: stuck now means *no progress toward where the owner is now*
   (`behaviour/FollowProgress`, pure, unit tested): less than 0.4 blocks closer per second while more than 5 blocks
   away, for `FollowStuckSeconds` (6). Walking after a running owner is progress; standing, pushing into a fence,
   sliding or circling is not. Every bring-back is now logged at INFO with the reason and the NPC state, e.g.
   `Ranch animal e0e7e818-... brought back to its owner (no progress for 6 s, 5.0 blocks away, state Follow.Default)`
   (live, cow outside a closed fence ring around the bot).
2. `taming/AnimalControl.applyFollow`: a house resident told to stop following goes back to its house routine
   (Idle, leash at its door, target cleared) instead of `Stay`, which froze it where it stood all day. Live:
   `Follow.Default` -> `/ranch stay` -> `Idle.Default`, leash `340.0 151.0 174.6` (the door). An animal with no
   house still gets `Stay`. At night a following resident stays out with its owner, as designed (house notes).
3. `/ranch behaviour status --animal=<a>` adds a line: position, distance to you, leash point and whether its
   `LockedTarget` is you. That is what Gibson (or the next agent) should read the next time an animal "wanders".

**Only the client can confirm** that this was Gibson's case. If it happens again in open ground, run
`/ranch behaviour status --animal=<id>` next to it and look at the server log for `brought back` lines: state and
target there say which of the remaining causes it is.

## Gate

- `.\gradlew.bat build` green (all unit tests, including the new `FixBehaviourTest`, 6 tests).
- `asset_validate` valid, 0 schema errors; `ui_check_java` valid; `node tools/check-lang.mjs` 0 missing.
- `test_run` of `behaviour.test.json` and `house.test.json` (copies pointed at this worktree in
  `build/tests-copy`): all cases pass, including the new clip case.

## Files

`behaviour/AnimalClips.java` (new), `behaviour/GentleStepSystem.java` (new), `behaviour/FollowProgress.java` (new),
`behaviour/BehaviourPlanner.java`, `behaviour/BehaviourCommand.java`, `behaviour/BehaviourFeature.java`,
`taming/FollowSystem.java`, `taming/AnimalControl.java`, `cozyranch_behaviour.lang`,
`src/test/java/.../behaviour/FixBehaviourTest.java`, `tests/hytale/behaviour.test.json`.

## REQUESTS

1. **breeding** (`BreedingService.java:182`) and **care** (`CareWorld.java:268`) still play `Love` and `EatTrough`
   on `AnimationSlot.Emote`, so those two clips are still invisible (the client log shows "No emote with id Love").
   Replace each call with `com.hexvane.cozytalesranching.behaviour.AnimalClips.playFor(store, animal, "Love", 1.5)`
   (Love is 1.5 s) and `AnimalClips.playFor(store, animal, "EatTrough", 3)`; drop the model check, `playFor` does it.
2. **taming lang**: with fix 2 above, `cozyranch_taming.follow.off` ("... stays") is sent when a house resident goes
   back to its house. Consider a separate line, for example "{animal} goes back to its house." The call site is
   `AnimalInteractions.setFollowing` (taming's file).
3. Merge note: `tests/hytale/behaviour.test.json` keeps the main project paths; my run used a copy in
   `build/tests-copy` pointed at the worktree.
