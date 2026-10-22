# Concept art sources: animal houses

Concept art only; nothing here is loaded by the game. Made 2026-10-22 by the concept-art agent
for Cozy Tales: Ranching. Every generated image is fal model `fal-ai/nano-banana-pro/edit`
(Nano Banana Pro edit, US$0.15 per image at this account's rate), 1024 x 1024, aspect `1:1`,
resolution `1K`, one image per request.

**Total spent: about US$3.15** (21 successful images x $0.15). Two further requests failed on fal's
side with `runner_connection_error` before producing output (failed jobs are normally not billed;
worst case +$0.30, $3.45).

## Style references (not generated)

Rendered from vanilla Hytale with the Hytale MCP `model_render` (view `three_quarter`, 768 px,
background `#dfe8ef`). Vanilla game files: reference only, never ship these.

| File | Source |
| --- | --- |
| `reference/vanilla_coop.png` | `Common/Blocks/Farming/ChickenCoop.blockymodel` (20 shapes) |
| `reference/vanilla_farming_bench.png` | `Common/Blocks/Benches/Farming.blockymodel` (39 nodes) |
| `reference/vanilla_trough.png` | `Common/Blocks/Farming/Trough.blockymodel` (rendered, not sent to fal) |

`vanilla_coop.png` and `vanilla_farming_bench.png` were uploaded to fal and passed as
`image_urls` (in that order) to every option request below.

## Option images

Every option prompt is the shared **style prefix** followed by `OBJECT: ...`.

Style prefix (for the sheepfold the words "painted wood grain" read "painted stone and straw"; for
the frog pond "painted stone, water and plants"):

> The attached images are in-game models from the video game Hytale; use them ONLY as a style
> reference. Create a NEW object in exactly that style: a chunky game model built only from
> rectangular boxes (cuboids), flat faces, hard edges, no curves, no bevels, few large parts;
> hand-painted soft low-resolution textures with painted wood grain and soft shading (not pixel art,
> not voxels, not Minecraft, not a photoreal render); warm saturated colours; slightly exaggerated
> cozy whimsical proportions. Three-quarter view from the front-right, camera slightly above, the
> whole object isolated and centred on a plain light grey-blue background, soft contact shadow, no
> scenery, no people, no animals, no text, no letters.

| File | Seed | Cost | Chosen | OBJECT text |
| --- | --- | --- | --- | --- |
| `chicken_coop/chicken_coop_opt1.png` | 1101 | $0.15 | **yes** (= `chicken_coop_chosen.png`) | a whimsical chicken coop, 2 blocks wide, 2 deep, 3 tall (taller than wide). A raised wooden hen house standing on four short square stilts, warm honey-brown plank walls, a small square doorway at the front with a sloping ramp of wooden slats leading down to the ground, a hinged nesting-box hatch sticking out of the right side with golden straw spilling out, a steep pitched roof of chunky red-brown shingles, and a small blocky rooster-shaped weathervane on the ridge. |
| `chicken_coop/chicken_coop_opt2.png` | 1102 | $0.15 | no (round door hole, mobile-game look) | a cheerful chicken coop, 2 blocks wide, 2 deep, 3 tall. A tall narrow hen house raised on chunky stilts, walls painted pale butter-yellow with white corner trim, a round-topped looking but actually square doorway with a slatted ramp, a small square window, a nesting box bump-out on the right side with a lift-up lid and straw poking out, a steep teal-green shingle roof with a wide overhang, and a small square rooster sign board (picture of a rooster, no words) above the door. |
| `chicken_coop/chicken_coop_opt3.png` | 1103 | $0.15 | no (runner-up; thatch, not the brief's shingles) | a rustic storybook chicken coop, 2 blocks wide, 2 deep, 3 tall. Dark-stained log-and-plank hen house lifted on four stubby stilts with a straw-filled gap underneath, a ramp with crosswise slats to a small front door, a nesting-box hatch on the side with straw spilling out, a steep golden thatch roof with a wooden ridge cap, and an iron rooster weathervane made of flat blocky shapes on top. A little red accent: red painted door. |
| `pigsty/pigsty_opt1.png` | 2101 | $0.15 | **yes** (= `pigsty_chosen.png`) | a cheerful pigsty on a square 3 by 3 block footprint, only 2 blocks tall, low and wide. At the back-left a low lean-to hut of rough mismatched planks with a crooked sloping plank roof that leans to one side, a dark open doorway. In the front-right corner a shallow square mud wallow with glossy chocolate-brown mud. A small wooden feeding trough along one side. A short low post-and-rail fence segment along the front edge. Pink accents: a pink painted door frame and a small pink pig-face sign shape (no words). Muddy splashes on the plank bases. |
| `pigsty/pigsty_opt2.png` | 2102 | $0.15 | no (soft 3D-render look, mud reads as dry dirt) | a pig house, footprint 3 by 3 blocks, 2 blocks tall. A squat wooden lean-to shelter covering the back half, its single-slope roof of wide planks sagging and crooked, patched with one pink-painted plank, held up by two thick posts at the front. The front half is a sunken mud pool with a raised plank edge, wet brown mud. A long hollowed wooden trough with turnips in it beside the mud. A short crooked fence of two posts and two rails on one side. Earthy browns with soft pink highlights. |
| `sheepfold/sheepfold_opt1.png` | 3101 | $0.15 | **yes** (= `sheepfold_chosen.png`) | a sheepfold, footprint 3 by 3 blocks, 3 blocks tall. A rounded-square low wall of stacked chunky grey dry-stone blocks with moss on top, about waist height, with a gap entrance at the front. Inside the back half, a small shelter of four wooden posts carrying a hipped golden thatch roof. White fluffy wool tufts (simple boxy clumps) caught on the stones. A wooden hay rack with yellow hay attached to the inside wall. |
| `sheepfold/sheepfold_opt2.png` | 3102 | $0.15 | no (closed hut, loses the "fold") | a cozy sheep hut, footprint 3 by 3 blocks, 3 blocks tall. A squat square hut whose lower walls are chunky mossy fieldstone blocks with chamfered corners, topped by a tall pointed four-sided thatch roof like a witch hat with a small wooden finial. A wide low doorway at the front with a wooden lintel. Little white wool tufts snagged on the stones. A wooden hay rack full of hay mounted on the right outer wall. Cream, grey-green and straw-gold palette. |
| `sheepfold/sheepfold_opt3.png` | 3103 | $0.15 | no (too many stones, shaggy thatch not buildable from boxes) | a sheepfold, footprint 3 by 3 blocks, 3 blocks tall. An open octagonal-ish fold of stacked flat stone slabs, knee-high on the front, rising to a full-height stone back wall. Over the back half, a lean wooden frame with a thick shaggy thatch roof sloping forward. Clumps of white wool on the wall tops, a small wooden hay rack on legs inside with hay, a little wooden gate hanging open at the front entrance, moss and tiny flowers in the cracks. |
| `cow_barn/cow_barn_opt1.png` | 4101 | $0.15 | no (clean but plain; runner-up) | a small classic red barn, a cube-ish 4 by 4 blocks footprint and 4 blocks tall, chunky and toy-like. Red vertical plank walls with bold white trim on corners and edges, a gambrel roof (two slopes each side, made of flat angled slabs) with dark grey shingles. Big double doors on the front, each with a white X brace. Above them a small square hayloft door standing open with yellow hay poking out and a little hoist beam above. A dented metal milk churn standing beside the doors. |
| `cow_barn/cow_barn_opt2.png` | 4102 | $0.15 | **yes** (= `cow_barn_chosen.png`) | a cozy storybook cow barn, 4 by 4 blocks footprint, 4 blocks tall, slightly squat and chubby. Warm brick-red board walls on a low grey fieldstone base, crisp cream-white trim, a gambrel roof with mossy brown wooden shingles and a small square cupola on the ridge. Wide double front doors with white X braces, slightly ajar. A hayloft door under the roof peak with hay spilling out. A metal milk churn and a small hay bale beside the door. A black-and-white cow-spot pattern painted on a little square board over the doors (no letters). |
| `cow_barn/cow_barn_opt3.png` | 4103 | $0.15 | no (good; open doors and trough worth borrowing) | a miniature red barn, 4 blocks wide, 4 deep, 4 tall. Faded cherry-red horizontal plank walls, thick white corner posts and white door frames, a steep gambrel roof of warm tan wooden shingles with a white fascia edge. Front: tall double barn doors, each with a white X brace, swung partly open showing straw inside. Hayloft door at the top with a tuft of hay sticking out. A lantern hanging beside the doors, a silver milk churn on the ground at the front right corner, and a simple wooden water trough along the right side. |
| `frog_pond/frog_pond_opt1.png` | 5101 | $0.15 | no (rim and reeds read as Minecraft pixel art) | a frog pond as a single placeable decoration, footprint 3 by 3 blocks, 2 blocks tall at its highest point. A shallow square-ish pond basin with a chunky rim of mossy grey stones, a flat translucent teal-green water surface inside. Three flat square-ish green lily pads floating, one with a small pink flower. Clumps of tall reeds and brown cattails at the back-left corner. At the back-right, a big red-and-white spotted toadstool mushroom with a boxy cap, tall enough to shelter under, beside a short mossy hollow log. |
| `frog_pond/frog_pond_opt2.png` | 5102 | $0.15 | no (glass-tank cutaway front, pixel textures) | a frog pond decoration, footprint 3 by 3 blocks, 2 blocks tall. A low square basin edged with chunky flat river stones and moss, filled with flat translucent blue-green water. Across the back sits a big hollow mossy log lying on its side, its open end facing front as a little sleeping den, with a tiny wooden plank door frame. Square lily pads on the water, reeds and cattails rising behind the log, a couple of small blue flowers on the rim. |
| `frog_pond/frog_pond_opt3.png` | 5103 | $0.15 | **yes** (= `frog_pond_chosen.png`) | a whimsical frog home pond, footprint 3 by 3 blocks, 2 blocks tall. A raised square pond basin with a rim of mossy cobblestone blocks, flat glassy teal water. In the back-left corner a tiny toadstool hut: a stout cream mushroom stem with a small square doorway and a wide flat-topped orange-red cap with white spots. Big flat lily pads on the water, one with a white flower. Reeds and cattails at the back-right corner. A small flat stepping stone at the front. |

## Turnaround sheets

Input: only the chosen option image (its fal URL) in `image_urls`. Same model, `1:1`, `1K`.
The model added view labels (FRONT, RIGHT SIDE, ...) although the first prompts asked for none;
they are spelled correctly and were kept.

Shared turnaround prompt (try 1), with the bracketed part changed per house:

> Make an orthographic turnaround model sheet of EXACTLY the object in the attached image (same
> design, same parts, same colours, same proportions, same boxy hand-painted game-model style).
> Arrange four views in a clean 2 by 2 grid, each view the same scale, flat orthographic projection
> with no perspective, evenly lit, on a plain light grey background: top-left FRONT view [what the
> front is], top-right RIGHT SIDE view [...], bottom-left BACK view, bottom-right TOP-DOWN plan view
> looking straight down [...]. Keep the object upright and centred in each cell with generous
> margins. No text, no labels, no arrows, no grid lines, no people.

| File | Seed | Cost | Chosen | Per-house wording / notes |
| --- | --- | --- | --- | --- |
| `chicken_coop/chicken_coop_turnaround_try1.png` | 7101 | $0.15 | **yes** (= `chicken_coop_turnaround.png`) | FRONT = "the face with the doorway and ramp"; RIGHT = "the side with the nesting box". (First submission of this exact request failed on fal, unbilled; this is the resubmission.) Side and plan views disagree on which side the ramp and nesting box are: trust the front view and the chosen image. |
| `pigsty/pigsty_turnaround_try1.png` | 7102 | $0.15 | no | FRONT = "the face with the pink-framed doorway, fence and mud box"; RIGHT = "the side with the mud wallow". (First submission failed on fal, unbilled.) Rejected: front view drops the mud box and fence. |
| `pigsty/pigsty_turnaround_try2.png` | 7202 | $0.15 | **yes** (= `pigsty_turnaround.png`) | Rewritten prompt with explicit layout: "...a square 3 by 3 block raised plank platform; the plank lean-to hut sits on the LEFT half of the platform, toward the back, its single-slope crooked plank roof; its pink-framed doorway faces the FRONT; a pink pig-face sign hangs on the hut's front wall right of the door; a small wooden trough stands just right of the hut; a square raised mud box with glossy brown mud fills the RIGHT-BACK quarter; a short post-and-rail fence stands at the front-left corner beside the doorway; the front-right of the platform is open deck..." plus per-view expectations; labels allowed. Right-side view is mirrored; plan view is the authority. |
| `sheepfold/sheepfold_turnaround_try1.png` | 7103 | $0.15 | no | FRONT = "the gap entrance in the stone wall"; plan "showing the rounded-square wall ring, the posts and the hipped thatch roof". Rejected: front view shows the hay rack sitting in the wall instead of an entrance gap. |
| `sheepfold/sheepfold_turnaround_try2.png` | 7203 | $0.15 | **yes** (= `sheepfold_turnaround.png`) | Rewritten prompt with explicit layout: "...a low mossy stone wall ring with cut (chamfered) corners runs around all four sides; the wall has ONE open gap entrance, in the middle of the FRONT side, about one block wide...; four wooden posts with knee braces carry a hipped golden thatch roof with a wooden ridge cap; the wooden hay rack with hay stands inside against the BACK wall; small white wool tufts hang on the outer wall..." plus per-view expectations; labels allowed. Consistent across views. |
| `cow_barn/cow_barn_turnaround_try1.png` | 7104 | $0.15 | **yes** (= `cow_barn_turnaround.png`) | Asked to keep the design "but make its footprint exactly square"; FRONT = "the double doors and hayloft door, showing the gambrel roof profile"; plan "at the roof and cupola"; "Keep the milk churn and hay bale beside the front door." Good and consistent. |
| `frog_pond/frog_pond_turnaround_try1.png` | 7105 | $0.15 | **yes** (= `frog_pond_turnaround.png`) | FRONT = "the side with the stepping stone, with the mushroom hut doorway facing the viewer"; RIGHT = "the side with the cattails"; plan "showing the square stone rim, water, lily pads, mushroom cap and cattails". Usable for proportions, but the mushroom and stepping stone drift between views: follow DESIGN_NOTES.md for placement. |

## Failed requests (no output)

| Request id | What | Error |
| --- | --- | --- |
| `01a16542-b397-77e0-b0d5-d4c956bab760` | coop turnaround, seed 7101 | `runner_connection_error` |
| `01a16542-b3d4-7880-8275-cd3f60f1c6a6` | pigsty turnaround, seed 7102 | `runner_connection_error` |

## Licence and use

AI-generated concept art for internal reference by the modellers. Do not ship these images in the
mod or use them as its public face without asking Gibson (`asset-pipeline.md` section 1).
