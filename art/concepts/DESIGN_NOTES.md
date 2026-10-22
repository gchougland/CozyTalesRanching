# Design notes: animal houses (for modellers)

One section per house, built from the chosen concept (`<house>/<house>_chosen.png`) and its
turnaround (`<house>/<house>_turnaround.png`). Sizes are in blocks (1 block = 32 units for block
models). Where the concept and the brief disagree, the brief's footprint wins; the concept is
there for character, not measurements.

Conventions for all five:
- **Front** is the face that was on the viewer's left in the concept images (the three-quarter view
  is from the front-right). It is the same side as the door of the vanilla `ChickenCoop` render in
  `reference/vanilla_coop.png`. Model the front facing the player when placed.
- Origin at the bottom centre of the footprint. Keep everything inside the footprint except small
  overhangs (roof eaves, a ramp tip) of 2 to 4 units.
- Hex palettes: values marked (s) were sampled from the chosen image; the rest are picked by eye
  from lit faces. Paint them as mid-tones with soft lighter tops and cooler, darker undersides, like
  vanilla.
- Concept textures (single shingles, single stones) are texture detail, not separate boxes, unless
  listed as a mass below.

---

## 1. Chicken Coop: 2 W x 2 D x 3 H

Chosen: `chicken_coop/chicken_coop_chosen.png` (option 1). Runner-up for a variant: option 3 (dark
logs, thatch roof, red door).

**Main masses**
| Part | Size (W x D x H) | Notes |
| --- | --- | --- |
| Stilts x4 | 0.25 x 0.25 x 0.6 | under the corners, inset slightly |
| Floor sill / base frame | 1.75 x 1.75 x 0.15 | at y = 0.6, darker plank band |
| Body (walls) | 1.6 x 1.6 x 1.1 | y 0.75 to 1.85; corner posts as 0.15 thin boxes slightly proud |
| Gable ends | 2 triangles stepped from 3 to 4 boxes each | front and back |
| Roof | 2 slabs, each ~1.1 x 2.0 x 0.15, ~45 degrees | 0.15 to 0.2 overhang at eaves and gables; ridge cap 0.2 x 2.0 x 0.2 |
| Doorway | 0.5 W x 0.6 H opening, front, centred, at floor level | dark inset box plus a 0.08 frame |
| Ramp | 0.45 W x ~0.9 long, 4 to 5 cross slats | from the doorway sill to the ground, front |
| Nesting box | 0.6 W x 0.5 D x 0.5 H on the right side | lid slab tilted up slightly; straw tuft as 2 to 3 crossed flat planes |
| Weathervane | post 0.06 x 0.4, arrow 0.5 long, rooster ~0.35 x 0.3 flat | on the front end of the ridge, so top reaches ~3.0 |

Roughly 45 to 60 boxes.

**Palette**: walls honey wood `#C98A3E` (lit), `#A96823` (s); wall shadow `#855627` (s); roof red
shingle `#A64B35` (lit), `#89402F` (s), dark course lines `#682C1D` (s); stilts and sill `#73370E` (s);
straw `#E2B85A`, `#B9893C` (s); vane `#754F27` (s) with a red comb `#B23A2A`.

**Signature details**
1. Tall, narrow, raised silhouette on four visible stilts with a clear gap underneath.
2. Steep red-brown shingle roof with chunky stepped courses and visible overhang.
3. The slatted ramp running down from the front door.
4. Boxy nesting box bulging from the right side with straw spilling out.
5. Rooster weathervane (flat silhouette) on the ridge.

**Animals**: chickens go up the ramp and in through the front door; they sleep inside the body.
The nesting box is where eggs appear (good spot for a produce variant, like vanilla
`ChickenCoop_WithProduce`). **Front**: door and ramp side.

---

## 2. Pigsty: 3 W x 3 D x 2 H

Chosen: `pigsty/pigsty_chosen.png` (option 1). Option 2's turnip trough is worth borrowing.

**Main masses**
| Part | Size (W x D x H) | Notes |
| --- | --- | --- |
| Plank platform | 3.0 x 3.0 x 0.35 | thick boards, square end-grain blocks at the corners |
| Hut | 1.5 W x 1.6 D, walls 1.1 H front rising to 1.4 H back | back-left quarter-plus; walls of mismatched vertical planks (a few boxes offset by 1 to 2 units) |
| Lean-to roof | 1.8 x 1.9 slab, tilted, ~0.1 thick | slopes down to the front-left, crooked: rotate a few degrees on two axes; plank seams as texture |
| Doorway | 0.55 W x 0.8 H, front of hut | dark inset; pink frame of three 0.1 boxes |
| Pig-face sign | 0.35 x 0.3 flat board on the hut front, right of door | hung from a nail; pink |
| Trough | 0.6 x 0.35 x 0.3 open box | just right of the hut front |
| Mud wallow | 1.2 x 1.2 raised box with 0.12 plank rim, mud surface 0.05 below rim | back-right quarter |
| Fence | 2 to 3 posts 0.15 x 0.15 x 0.6, two rails | front-left corner, along the front edge |

Roughly 40 to 55 boxes. Ground height: whole thing under 2.0; hut roof top ~1.75 with platform.

**Palette**: deck planks `#D5894F` (s); planks shadow `#A76C42` (s); weathered hut planks
`#9C6A43`; hut interior `#3C1C10` (s); roof planks `#CA8551` (s); pink trim `#D47C78` (s sign),
`#B66D6F` (s frame); mud `#864D30` (s) with glossy highlight `#B8806A`; mud splashes on wood
`#6E4A30`.

**Signature details**
1. Low, wide, squat silhouette on a chunky plank platform.
2. Crooked lean-to roof that visibly tilts.
3. Pink door frame and the pink pig-face sign.
4. Glossy mud wallow box with highlight blobs.
5. Mud splashes painted on the lower planks.
6. Short stubby fence segment at the front corner.

**Animals**: pigs sleep in the hut (enter by the front door), wallow in the mud box (they stand
in it; keep the mud surface walkable and low), eat at the trough. **Front**: hut door side.

---

## 3. Sheepfold: 3 W x 3 D x 3 H

Chosen: `sheepfold/sheepfold_chosen.png` (option 1). Turnaround is good (try 2).

**Main masses**
| Part | Size (W x D x H) | Notes |
| --- | --- | --- |
| Stone wall ring | outer 3.0 x 3.0, wall 0.35 thick, 0.75 H | chamfered corners: a short 45-degree wall box at each corner instead of a square corner; built from 4 straight runs + 4 corner pieces |
| Entrance gap | 0.9 W | centre of the front wall; the two front wall runs end with squared stone ends |
| Moss caps | thin 0.05 slabs on top of the wall, patchy | or paint it |
| Posts x4 | 0.2 x 0.2 x 1.9 | around a 1.9 x 1.6 rectangle, set slightly toward the back |
| Knee braces | 8 short angled boxes 0.1 x 0.1 x 0.5 | under the roof, both directions |
| Hipped roof | 3 stepped tiers, eaves ~2.6 x 2.3, top tier ~1.2 x 0.9 | each tier a flat-ish box with slanted edges, or four tilted slabs per tier; overhang beyond the posts on all sides |
| Ridge cap | 0.9 x 0.15 x 0.12 dark wood | top reaches ~3.0 |
| Hay rack | 1.0 W x 0.3 D x 0.6 H slatted box, hay mound inside | against the inside of the back wall |
| Wool tufts | 4 to 6 small boxes ~0.15, white | stuck on the outer wall faces and wall tops; make them bigger than the concept so they read |

Roughly 45 to 70 boxes.

**Palette**: stone `#8A8A80` (lit), `#7D7B72` (s); stone shadow `#5B5F59` (s); moss `#6B7847` (s),
lighter `#8C9A4F`; thatch lit `#D9AE58`, `#BE944D` (s), underside `#7D622F` (s); posts `#65482C` (s);
hay `#C6A433` (s), lit `#E8C55A`; wool `#F2EFE6` (lit), shade `#979586` (s).

**Signature details**
1. Rounded-square (chamfered-corner) low dry-stone ring with mossy tops.
2. Open front gap in the wall.
3. Tiered hipped golden thatch roof floating on four braced posts.
4. White wool tufts snagged on the stones.
5. Hay rack against the back wall, visible through the entrance.

**Animals**: sheep walk in through the front gap and sleep under the thatch shelter; they eat at
the hay rack. Leave the floor inside open. **Front**: wall gap side.

---

## 4. Cow Barn: 4 W x 4 D x 4 H

Chosen: `cow_barn/cow_barn_chosen.png` (option 2). The concept is deeper than wide; build it square
(4 x 4) as the turnaround shows. From option 3, the half-open door and side trough are worth
borrowing.

**Main masses**
| Part | Size (W x D x H) | Notes |
| --- | --- | --- |
| Stone plinth | 3.6 x 3.6 x 0.3 | grey fieldstone band, slightly proud of the walls |
| Walls | 3.4 x 3.4 x 1.9 | y 0.3 to 2.2, red vertical boards; white corner posts 0.2 x 0.2, white band at y 2.2 |
| Gambrel gable (front and back) | 3.4 W, rises 1.4 | stepped boxes for the wall triangle; white trim follows the two-pitch outline |
| Gambrel roof | per side: lower slab ~1.0 long at ~60 degrees, upper slab ~1.2 long at ~25 degrees, 3.8 deep | 0.2 overhang front and back; ridge beam 0.2 |
| Double doors | 1.5 W x 1.5 H, front centre | two leaves with white frames and X braces (each X = 2 thin diagonal boxes), slightly ajar showing dark inside |
| Cow-spot plaque | 0.45 x 0.35 board above the doors | white with black spots (texture), dark frame |
| Hayloft door | 0.6 W x 0.6 H opening under the front peak | white frame, hay tumbling out as 2 to 3 angled boxes |
| Cupola | 0.5 x 0.5 x 0.45 box + small pyramid roof 0.7 x 0.7 | on the ridge centre; top reaches ~4.0 |
| Milk churn | 0.3 x 0.3 x 0.45 + neck/lid | front, right of doors |
| Hay bale | 0.45 x 0.3 x 0.25 | beside the churn |

Roughly 60 to 90 boxes.

**Palette**: barn red lit `#B4472F`, shadow side `#8C3424`, deep shadow `#623321` (s); trim cream
`#EBDEB5` (s); roof mossy shingle `#7A6538` lit, `#53422A` (s) shadow, moss `#7D8A3E`; stone plinth
`#8E959A` lit, `#777E84` (s); hay `#F4C85B` (s); churn `#B8BCBE` lit, `#797979` (s); plaque spots
`#2B2926` on `#F1EEE6`.

**Signature details**
1. Gambrel (two-pitch) roof silhouette with white trim tracing it.
2. Big double doors with white X braces.
3. Hayloft door with hay spilling out under the peak.
4. Little cupola on the ridge.
5. Cow-spot plaque above the doors.
6. Milk churn and hay bale at the door; grey stone plinth.

**Animals**: cows enter through the double doors and sleep inside. The 1.5-block door is only
visual; the furniture's own collision decides where a cow can actually stand. **Front**: the
double-door side.

---

## 5. Frog Pond: 3 W x 3 D x 2 H

Chosen: `frog_pond/frog_pond_chosen.png` (option 3). The turnaround is usable for proportions but
moves the mushroom between views; use this placement.

**Main masses**
| Part | Size (W x D x H) | Notes |
| --- | --- | --- |
| Stone rim | outer 3.0 x 3.0, 0.35 thick, 0.55 H | 4 runs of mossy cobble boxes (paint the cobbles, don't model each) |
| Basin floor | 2.3 x 2.3 x 0.1 | dark, under the water |
| Water | 2.3 x 2.3 single flat plane at ~0.45 | translucent teal; needs alpha blending; one face up (and down if visible) |
| Lily pads x4 | flat 0.4 to 0.5 squares with a notch, 0.03 thick | floating just above the water; one carries a white flower (3 to 4 tiny boxes) |
| Toadstool stem | 0.6 x 0.6 x 0.6, tapered by stacking 2 boxes | back-left corner, standing on the rim/basin |
| Doorway | 0.25 W x 0.35 H on the stem's front face | dark inset with a thin wood frame |
| Toadstool cap | two stacked tiers: 1.1 x 1.1 x 0.25 and 0.8 x 0.8 x 0.25 | orange-red with painted white spots; top reaches ~1.6 |
| Cattails | 4 to 5 stalks 0.06 thick, 0.8 to 1.2 tall, brown heads 0.1 x 0.1 x 0.2 | back-right corner, plus 3 to 4 flat leaf blades |
| Stepping stone | 0.5 x 0.5 x 0.1 flat slab | front-centre, at water level |

Roughly 35 to 50 boxes.

**Palette**: rim stone warm `#B39A6A` and mossy `#8C8E44`, deep moss `#4C6A2A`; cobble shadow
`#6E6248`; water `#3C826E` (s) to lighter `#5FA595` at the edges, alpha ~0.7; lily `#4B7E21` (s), lit
`#6FA33A`; cap `#E0602A` lit, `#98361A` (s) shade; spots `#F3E6D2`; stem `#D2B682` (s);
cattail heads `#81441B` (s); reeds `#6B8523` (s); stepping stone `#B8AA90` (s).

**Signature details**
1. Square mossy stone rim with a flat glassy teal water surface.
2. Tiered orange-red toadstool hut with white spots and a tiny door.
3. Square, notched lily pads, one with a white flower.
4. Cattail clump in the back-right corner.
5. Flat stepping stone at the front.

**Animals**: frogs sleep in (or under) the toadstool hut, entering by its little front door; they
hop on the lily pads and stepping stone and swim in the water. **Front**: stepping-stone side, with
the toadstool door facing front.
