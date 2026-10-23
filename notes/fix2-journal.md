# Fix wave 2: journal (settings, how to, held pose)

Branch `wave/fix2-journal`. Gibson: per-player toggles for chat and sounds; a short tutorial page; the journal is held
weird.

## What changed

### 1. Per-player settings (new package `prefs/`)

- `PlayerPrefs` (`Chat`, `Sounds`, both true by default), `PrefsStore` (memory + `<data>/Prefs/<uuid>.json`, written like
  the journal: tmp, force, `.prev`, atomic move; a broken file falls back to `.prev`, then to defaults; only players who
  changed something get a file), `RanchPrefs` (static facade, save 2 s after a change on its own thread, flushed at
  shutdown). Started and closed from `JournalFeature.register` (no change to the plugin class).
- **Sounds**: `RanchSounds.play` uses vanilla's `SoundUtil.playSoundEvent3d(..., Predicate<Ref> shouldHear, ...)` and
  skips listeners whose PlayerRef has sounds off. Cheap: while nobody has sounds off (a concurrent set is empty) it
  calls the plain overload exactly as before. New `RanchSounds.play2d(PlayerRef, id)` for UI sounds; the journal's open
  and discovery sounds use it. `AnimalControl.sound` already went through `RanchSounds.play` (no change needed), so
  every ranch sound in the mod now respects the toggle.
- **Chat**: `RanchChat.tell(PlayerRef, Message)` sends unless the player turned ranch messages off. Switched 19 lines
  (only the send line and an import in each file): taming progress/done/claim/follow/feed/pet/name/crickets tip, care
  feed given / trough added / taken, house placed / broken / assigned, butcher done, wildlife arrivals, journal notes.
- **The rule** (javadoc on `RanchChat`, enforced by `RanchChatRuleTest`): a line is toggled only when it reports what
  the player can already see happen. Always sent: command replies (no `*Command*.java` file uses `tell`), refusals and
  their reasons (no permission, not yours, not owner, full, wrong food, startled, refused, already petted, at limit,
  unregistered), confirmations the player must answer (butcher confirm), anything from or about another player (all of
  `TransferService`: offers, accept, decline, given, received), and information the player asked for (trough contents,
  house summary, house page locate, journal unavailable / denied).
- **Settings page** `JournalSettings.ui`, from a new **Settings** button on the contents page: "Ranch messages" and
  "Ranch sounds", each with a line saying what it covers and an On/Off button (action `toggle`, arg `chat|sounds`; the
  page rebuilds with the new state). Right page: what always shows.

### 2. How to page

`JournalGuide.ui` from a new **How to** button: six short sections (Make a friend, Build a home, Keep them happy,
Babies, Produce, Frogs), 16 plain lines, keys `cozyranch_journal.guide.*`, set from Java through the species page's
heading/line rows. `JournalLayoutTest.guideIsShortAndPlain` checks lines are short, have no dashes, and mention crouch,
naming, Farmer's Workbench, trough, decorations, Show area, once a day, baby, Collect, Jar of Crickets.

### 3. Held pose

Before: the book stuck up out of the fist by its bottom edge, cover facing forward, like a card
(`notes/pictures/journal/held-before.png`). Vanilla's held book is the grimoire (`Weapon_Spellbook_Grimoire_Brown`,
`PlayerAnimationsId` Spellbook, whose Idle is Item's Idle): held by the spine, spine upright, cover facing out from the
body, book reaching forward, item `Scale` 1.2. Ours now does the same: `Journal.blockymodel` root `R-Attachment` at the
grimoire's (-2, 13, -8), `Origin_Item` turned -90 degrees about Y, parts moved so the spine centre is the grip (part
sizes and UVs untouched); `CozyRanch_Journal.json` `Scale` 1 to 1.2. After: `held-after.png`; vanilla for comparison:
`held-vanilla-grimoire.png` (all rendered with a player holding the item, Item Idle frame 0).

## Verified

- `gradlew build` green; 266 unit tests (new: `RanchPrefsTest` 5, `RanchChatRuleTest` 3, `JournalLayoutTest` +2).
- `asset_validate` valid, 0 errors, 0 schema errors; `ui_check_java` valid, 0 errors, 0 warnings; `check-lang` 0 missing.
- `tests/hytale/journal.test.json` regenerated (16 renders, 3 new). Run from a copy pointed at this worktree: gate and
  all 16 renders pass. Goldens updated only where intended: `journal-contents-empty/partial/full` (two new buttons);
  new `journal-guide`, `journal-settings-on`, `journal-settings-mixed`. All others unchanged. Looked at every new and
  changed one (copies in `notes/pictures/journal/`).

## Nobody has seen in the client

- The new buttons, both pages, and the On/Off toggle round trip (click, rebuild).
- Whether a player with sounds off really hears nothing (packets are filtered server-side; no server run here).
- First-person view of the journal (only third person rendered). The inventory icon PNG was not re-rendered; its
  `IconProperties` were made for the old orientation, so a regenerated icon would look different.
- The dropped journal now lies turned 90 degrees compared with before.

## REQUESTS

1. **Coordinator**: `tests/hytale/journal.test.json` points at `CozyTales-Ranching` (main) as committed; run `test_run`
   on main after merge.
2. **Coordinator (optional)**: `docs/STATUS.md` journal line: Settings page (per-player chat/sound toggles), How to page,
   held like a vanilla book.
3. **Other roles, from now on**: send feedback lines with `com.hexvane.cozytalesranching.prefs.RanchChat.tell` and play
   sounds only through `RanchSounds`; `RanchChatRuleTest` fails if a toggled key is sent with plain `sendMessage` (add
   new feedback keys to its `TOGGLED` table).
4. **Art (optional)**: re-render `Icons/ItemsGenerated/CozyRanch_Journal.png` and its `IconProperties` for the new
   model orientation if the icon is ever regenerated.
