# Journal (wave 3, batch 1)

Branch `wave/journal`. The Rancher's Journal: an item and a command that open a book with a contents page, one page per
species and a memories page, filled in per player from the public ranch events only.

## What was built

- **Item** `CozyRanch_Journal` (secondary use opens the journal; permission `cozyranch.journal`). Recipe at the
  Workbench, Crafting tab: 1 `Ingredient_Hide_Light` + 3 `Ingredient_Fibre`. Placeholder look: vanilla brown grimoire
  model, texture and icon, until the art role delivers its own.
- **Command** `/ranch journal` (contents) and `/ranch journal <species>` (that page). Admin, under `cozyranch.admin`:
  `/ranch journal demo <empty|partial|full>` opens a made-up journal (nothing saved);
  `/ranch journal discover <species> <seen|diet|care|house|behaviour|breeding|trait|product|hearts|visitor|rare|all>`
  writes into your own journal through the real rules (chat line and sound included); `/ranch journal reset`.
- **Pages** (`JournalContents.ui`, `JournalSpecies.ui`, `JournalMemories.ui`, styles and row documents): a leather book
  open on two parchment pages. Contents: cover vignette, "Kept by <player>", progress, a card per species (painting once
  met, silhouette and `???` before). Species page: name, first-met date, painting, description, diet, favourite treats,
  friendship, home and habitat (house, needs, likes), visitors (wildlife hint), breeding, products, traits, habits, and a
  rare-sighting line once seen. Anything unknown is `???` plus a hint on how to learn it. Previous/next wrap around.
  Memories: three per page, six per spread, paged.
- **Discovery** (`JournalRules`, pure, idempotent, per player), from `JournalListeners`:

  | Event | Reveals |
  | --- | --- |
  | `AnimalTameAttemptEvent` | entry + diet |
  | `AnimalTamedEvent`, `AnimalOwnerChangedEvent` (new owner) | entry, diet, friendship, treats, the animal's traits; first-friend / new-friend memory |
  | `RanchHousePlacedEvent` | entry, habitat, visitors hint; first-house memory |
  | `AnimalHouseAssignedEvent` (into a house) | habitat, visitors hint |
  | `AnimalBehaviourEvent` (to the witness) | that behaviour; first-behaviour memory |
  | `AnimalBredEvent` | breeding, offspring traits; first-breeding memory |
  | `AnimalGrewUpEvent` | the animal's traits |
  | `AnimalFriendshipChangedEvent` crossing 5 | first five-hearts memory |
  | `AnimalProducedEvent` | that product |
  | `RanchVisitorArrivedEvent` | entry, diet, visitors hint; first-visitor memory; if rare, the rare line and first-rare memory |

  Anything new sends one quiet chat line (`notify.*`) and `RanchSounds.JOURNAL_DISCOVERY` (2D, to that player).
  Opening plays `RanchSounds.JOURNAL_OPEN`.
- **Save**: `<data>/Journal/<uuid>.json`, loaded at setup, written on the journal's own thread, debounced by
  `SaveDebounceSeconds`, atomic (tmp, force, `.prev`, atomic move), flushed at shutdown. Only real changes are saved.
- **Data**: `Server/CozyTalesRanching/Journal.json` per species: order, description key, rare key, art, silhouette,
  behaviours to list. A species with no entry still gets a page (defaults, `Unknown` art). `<data>/Journal.json`
  replaces it; `/ranch reload` re-reads it.
- **Art**: the animals are the in-game models (Gibson: "it should be using images of the actual animals in game").
  `tools/journal/render.ps1 <dir>` renders each species' ranch model asset (`model_render`, three-quarter, transparent,
  Idle frame 0; the sheep through `tools/animator/woolcheck.py` so it has its wool), then
  `python tools/journal/portraits.py <dir>` writes the 240/480 portraits, the silhouettes, `Unknown` (the sheep
  silhouette) and the cover (all five in a row at their relative heights). Re-run both after a model change or a new
  species (add it to both scripts). `notes/pictures/journal/make_art.py` still paints the book textures (leather, pages,
  cards, divider); its fal painting cut-outs are no longer shipped. Shipped in `Common/UI/Custom/CozyRanch/Journal/`
  at 1x and `@2x` (species 240 logical).
- **Tests**: `JournalRulesTest` (11: which event reveals what, idempotent, per player, save round trip, corrupt file
  falls back to `.prev`) and `JournalLayoutTest` (9: layout facts, every key sent exists, every listed behaviour has
  strings and art, and `tests/hytale/journal.test.json` holds exactly the commands Java sends; rewrite with
  `JOURNAL_WRITE_CASES=true`). `node notes/pictures/journal/render.mjs` redraws the pictures from that file.

## Pictures (`notes/pictures/journal/`, also the goldens in `tests/hytale/golden/`)

All ten renders: valid, 0 errors, 0 notes. Best: `journal-species-full.png`, `journal-contents-partial.png`,
`journal-species-undiscovered.png`, `journal-memories-full.png`. Also `journal-contents-empty`, `journal-contents-full`,
`journal-species-partial`, `journal-species-fed`, `journal-memories-empty`, `journal-memories-full-2`.

## Gate

`gradlew build` green, no warnings; `asset_validate` 0 errors, 0 schema errors (one new warning: the `OpenCustomUI` page
id `CozyRanchJournal` is a plugin type, as expected); `ui_check_java` valid, 0 errors; `check-lang` 0 missing.

## Faked or unfinished

- Nothing has been seen in a running game (no server in this wave). Hover states, scrolling and clicks are unproven.
- The species page's right side scrolls when everything is known; the rare line sits below the fold on a full page.
- Products use the species files' item ids. Cow and sheep still list `Drop_Cow_Harvest` / `Drop_Sheep_Harvest`, which
  are not items, so they show as "Drop Cow Harvest" until the produce role's ids land.
- Visitor hints: `cozyranch_wildlife.hint.<species>` (and `.rare`) are used when the server has them; until then a
  generic journal line shows. Rare lines fall back to the journal's own `species.<id>.rare`.
- A "first rare variant" memory only comes from a rare visitor. A tamed rare variant is not visible in `AnimalView`.
- Taming fires the tame attempt and tamed events together, so the first tame gives two chat lines.

## Decisions to review

- Diet (basic and liked foods) on the first feeding try; favourite treats only once befriended.
- Habit lists per species in `Journal.json` are my guess (no mapping exists): cow graze/tail swish/ball/post, chicken
  peck/dust bath/flap/bell, pig mud roll/snuffle/ball/log, sheep graze/pronk/post/bell, frog lily hop/croak/duck/log,
  plus the shared ones.
- The journal writes its own behaviour wording (`cozyranch_journal.behaviour.<id>.name/.note`) rather than reading
  `cozyranch_behaviour.name.<id>`, so pages read as observations and do not depend on the behaviour role's strings.
- Habitat names reuse the house page's `cozyranch_houseui.habitat.<id>` (capitalised, e.g. "Needs Food nearby").
- Placing a house also counts as meeting the species (otherwise its page would show habitat under a `???` name).
- Memories: the seven firsts, plus one "new kind of friend" per further species. Dates use the server's time zone.

## fal spend

About **$0.48**: six images at $0.08. `fal-ai/nano-banana-2` (1:1, 1K) for the cow, then `fal-ai/nano-banana-2/edit`
with the cow as style reference for chicken, pig, sheep, frog and the 4:3 cover. Cut-outs done locally, no fal.

- Cow prompt: "A soft storybook illustration of a friendly cow for a cozy farming journal. Full body, three-quarter
  view, standing calmly, cream and brown patches, small horns, pink nose, gentle happy eyes. Stylised chunky proportions
  like a hand-painted fantasy video game (Hytale style): simple big shapes, soft painterly gouache and watercolour
  shading, warm light from above, cool soft shadows, visible gentle brush texture, no outlines, no pixel art. Isolated on
  a plain flat light cream background (#f4ead6) with nothing else, no ground, no cast shadow, no text, no border,
  centered with margin around the animal."
- Others: "Using exactly the same painting style, palette, brush texture, lighting and level of detail as this cow
  illustration, paint a new illustration of <a plump hen / a round pink pig / a fluffy sheep with a dark face / a round
  green frog, sitting / a small ranch on rolling hills: red thatched barn, coop, pond, fence, tiny animals, in an oval
  vignette fading to cream>. Same plain flat light cream background (#f4ead6)..."

## REQUESTS

1. **RanchCommand** (shared): add `this.addSubCommand(new com.hexvane.cozytalesranching.journal.JournalCommand());`
   after the `HouseCommand` line. I added it locally so the build runs it. **Do not merge my copy of `RanchCommand.java`.**
2. **Art role**: the journal item wants its own model, texture and icon. Fields to replace in
   `Server/Item/Items/CozyRanch/CozyRanch_Journal.json`: `Model`, `Texture`, `Icon`
   (`Icons/ItemsGenerated/CozyRanch_Journal.png`), `IconProperties`.
3. **Wildlife role**: write `cozyranch_wildlife.hint.<species>` (and `.rare`) for every species; the journal picks them
   up with no change.
4. **Contract**: if a tamed rare variant should give the "something rare" memory, `AnimalView` (or `AnimalTamedEvent`)
   needs a rare/variant flag.
5. **Coordinator**: `tests/hytale/journal.test.json` uses project `_worktrees/CozyTales-Ranching-journal`
   (`PROJECT` in `JournalLayoutTest`); rewrite to `CozyTales-Ranching` at merge and regenerate.
