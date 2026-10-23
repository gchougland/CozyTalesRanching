# Fix wave 1: journal (traits explained)

Branch `wave/fix-journal`. Gibson: "Nowhere does it actually say what a trait does ... it should also describe it in the
journal."

## What changed

- **Species page, Traits section**: instead of "Seen so far: a, b and c", each trait seen on that species gets its name
  (bold, ink-brown, new row document `JournalLineStrong.ui`) and under it what it does
  (`cozyranch_breeding.trait.<id>.desc`), then "N more to discover". Section stays where it was (after Products).
  The right page already scrolls; with all nine traits it runs below the fold (habits and the rare line further down).
- **New Traits pages** (`JournalTraits.ui`, rows `JournalTrait.ui` / `JournalTraitUnknown.ui`), reached by a new
  **Traits** button beside Memories on the contents page. Every trait in `Traits.json`, in its order: name, what it
  does, "Seen on: Cow, Pig and Frog." Traits no animal of yours has had show as `???` "Not seen on any of your animals
  yet." Header: "N of M traits recorded" and a line on how traits work (the max per animal comes from
  `Traits.json` `MaxTraits`). Four rows on the left page, five on the right, paged (Previous / Next) beyond nine.
- Data driven: no trait id in Java. A trait added to `Traits.json` shows up on both pages; a trait without `NameKey`
  shows its id prettified, without `DescKey` a journal fallback line.
- `JournalPage` handles a `traits` action (spread arg). `JournalDemo` FULL now also gives the first species (cow) every
  trait (via `JournalRules.traits`), so the longest species page is tested. PARTIAL = 1 trait, EMPTY = 0.
- `cozyranch_journal.lang`: added `nav.traits`, `traits.title/progress/intro/seen/unknown/page/no_description`;
  removed the now unused `traits.line`.

## Verified

- `gradlew build` green (no warnings). `JournalLayoutTest` +2 tests (species page names and describes every seen trait;
  traits page hides unseen, counts seen, 0/1/9). `tests/hytale/journal.test.json` regenerated (13 renders, 3 new).
- `test_run` (worktree copy of the file with paths pointing at this worktree): journal gate (asset_validate,
  ui_check_java) and all 13 renders pass, valid, 0 errors, 0 notes. `check-lang` 0 missing.
- Looked at: `notes/pictures/journal/journal-traits-empty.png`, `-traits-partial.png`, `-traits-full.png`,
  `-species-full.png`, `-species-partial.png`, `-contents-partial.png`, and `journal-species-full-traits-scrolled.png`
  (a check render, not a golden: the full cow page with only Traits/Habits/Rare on the right, to see the part that is
  below the fold). All descriptions fit; the longest (Bountiful) wraps to two lines; nothing overflows.
- Goldens updated (intended): `journal-contents-empty/partial/full` (Traits button), `journal-species-full`
  (nine traits), `journal-species-partial` (one trait described). New: `journal-traits-empty/partial/full`.
  Unchanged: species-fed, species-undiscovered, memories x3.

## Nobody has seen in the client

- The Traits button, the traits pages and their Previous/Next (disabled with nine traits), the bold trait names.
- Scrolling down to the traits on a full species page.
- Row heights are not fixed: a future description of 3+ lines on the left page could push four rows past the nav
  (today's longest uses 2 lines and leaves ~100 px free).

## REQUESTS

1. **Coordinator**: `tests/hytale/journal.test.json` points at project `CozyTales-Ranching` (main). I ran it from a
   copy with paths rewritten to this worktree. After merge, run `test_run` on main as is.
2. **Coordinator (optional)**: `docs/STATUS.md` journal line: the journal now has a Traits page and explains each trait.
