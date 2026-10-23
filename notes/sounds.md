# Sounds (wave 3, batch 2)

Branch `wave/sounds`. Every id in `RanchSounds` is now a sound event with real audio:
`Server/Audio/SoundEvents/CozyRanch/SFX_CozyRanch_*.json` (12 events) and 23 Ogg Vorbis files in
`Common/Sounds/CozyRanch/{Animal,House,Care,Produce,Journal,Wildlife,Toys,Butcher}/`.

**Nobody has heard these.** Everything below is measured or read off spectrograms. Gibson needs to listen in
game; the files I trust least are listed under "Weak".

## How they were made

- ElevenLabs Sound Effects V2 through fal (`fal-ai/elevenlabs/sound-effects/v2`, $0.002 per second).
  32 takes, 32.6 s, about **$0.07** in total. Every prompt, take number and verdict (used or rejected and
  why) is in the main checkout's `art/sounds/SOURCES.md`; raw MP3s in `art/sounds/raw/`.
- Processing with ffmpeg 9.0 (winget Gyan build, libvorbis), script `art/sounds/process.ps1`:
  mono for positional sounds and stereo for the two journal UI sounds (as vanilla: mono for 3D, `_Stereo`
  for UI), a gentle 12 dB/octave low-pass (7 to 12 kHz per sound, to take the hiss off), start 15 ms before
  the first sound within 24 dB of the peak, tail cut below -60 dBFS, 4 ms fade in, 40 ms fade out, gain to a
  per-sound loudness target with the peak capped at -3 dBFS, Vorbis quality 6, 44.1 kHz, no metadata.
- Judged by `art/sounds/measure.ps1` (length, peak, true peak, integrated loudness, RMS, leading and
  trailing silence, RMS above 4 kHz relative to the whole as a shrillness proxy) and spectrogram plus
  waveform contact sheets (`art/sounds/raw_sheet_*.png`, `final_sheet.png`), which I looked at: they show
  separate note onsets for the chimes, single transients for knocks and chops, two pops for collect, a
  noise band for the pours.

## Vanilla reference (same measurements)

| Vanilla file | Length | Peak dBFS | Integrated LUFS |
| --- | --- | --- | --- |
| Chicken_Alerted_01 | 0.54 s | -9.1 | -21.7 |
| Sheep_Alerted_01 | 2.89 s | -11.0 | -23.2 |
| Door_Wooden_Open_01 | 0.94 s | -2.9 | -27.5 |
| Player_Item_Pickup_01 | 0.10 s | -8.0 | too short (RMS -21.5) |
| Discovery_Z1_Short_Stereo | 6.00 s | -5.7 | -21.1 |
| Drag_Weapons_Books_Stereo | 0.43 s | -8.9 | -24.1 |

Vanilla ships Vorbis 44.1 kHz, mono for world sounds, stereo for UI; peaks -3 to -11 dBFS; -21 to -28 LUFS.
Targets: -21 to -22 LUFS for reward moments (tamed, heart, baby, discovery, visitor), -24 for happy,
journal, butcher, collect around -23, -25 to -26 for door, trough and toys (vanilla's door is -27.5).

## What shipped, measured

All files: no clipping (highest peak -2.8 dBFS), no leading silence (first sound within 15 ms), no trailing
silence below -50 dBFS, no file over 1.8 s. "hf" is RMS above 4 kHz relative to full-band RMS.

| Event | Files | Length | Peak dBFS | LUFS | hf dB | Parent / range |
| --- | --- | --- | --- | --- | --- | --- |
| Animal_Happy | 4 (marimba, glockenspiel, kalimba, xylophone), pitch +-1 st, round robin 2 | 0.68-0.80 s | -3.5 to -11.0 | -23.9 to -27.6 | -4 (glock), -35 to -41 | Quiet, 15 blocks, 3 instances |
| Animal_Tamed | 2 (harp flourish, celesta jingle) | 1.60 s | -9.7, -4.2 | -21.0, -21.1 | -33, -41 | Moderate, 25 blocks |
| Heart_Up | 2 main (vibraphone, harp) + sparkle layer at -4 dB, 80 ms later | 1.08, 1.20 (+1.00) s | -10.9, -8.1 (-6.1) | -22.0, -21.9 (-26.1) | -47, -56 (-2) | Moderate |
| Baby_Born | 1 (music box) | 1.76 s | -9.3 | -21.9 | -27 | Moderate, 1 instance |
| House_Door | 2 (creak and thud), pitch +-1.5 st | 1.20, 0.90 s | -4.0, -2.8 | -26.0, -27.2 | -30, -29 | Quiet, 2 instances |
| Trough_Fill | 2 (grain pour, pellets) | 1.20, 0.50 s | -6.9, -5.1 | -25.3, -23.1 | -2, -12 | Quiet |
| Produce_Collect | 1 (two soft pops) | 0.42 s | -8.7 | -22.9 | -39 | Quiet |
| Journal_Open | 1 stereo (cover and page) | 0.40 s | -2.9 | too short (RMS -28.3) | -25 | 2D, AudioCat_UI |
| Journal_Discovery | 1 stereo (harp and sparkle) | 1.48 s | -7.7 | -22.0 | -34 | 2D, AudioCat_UI |
| Visitor_Arrived | 1 (wooden wind chimes) | 1.24 s | -5.4 | -22.0 | -4 | Moderate, 1 instance |
| Toy_Play | 3 (ball bounces, wooden knock, boing), pitch +-2 st, round robin 1 | 0.30-0.76 s | -2.9 to -12.1 | -26.3, -29.2, too short | -12, -35, -39 | Quiet, 3 instances |
| Butcher | 2 (dull wooden chop, thunk) | 0.37, 0.47 s | -3.0, -2.9 | too short, -25.7 | -27, -38 | Quiet, 1 instance |

Short transients (door 02, toy ball, butcher) hit the -3 dBFS peak cap before their loudness target, so they
sit 1 to 3 LU under it, like vanilla's door.

## Where they play

- Already called by their roles: Trough_Fill (care, `TroughInteraction` and `CareCommand`), Produce_Collect
  (produce), Journal_Open and Journal_Discovery (journal, 2D UI).
- Added by me (one-line calls):
  - `taming/AnimalControl.sound(animal, id, store)`: a three-line helper playing a `RanchSounds` id at the
    animal (0.5 above its feet).
  - `taming/AnimalInteractions`: Animal_Tamed when a wild animal is tamed and when an unowned animal is
    claimed; Heart_Up when feeding or petting gains a heart, otherwise Animal_Happy; Animal_Happy on each
    wild feeding that does not tame yet and on petting during the petting cooldown. `addFriendship` now
    returns whether a heart was gained (was void). Exactly one sound per interaction.
  - `house/HouseResidents`: House_Door when a loaded resident goes in (`storeInside`, only when it had an
    entity) and whenever a resident comes out (`bringOut`: morning, resummon, house removal).
  - `breeding/BreedingService`: Baby_Born at each baby when its "they bred" signal plays as it comes out.
- Not called yet (owned by other batch-2 roles): Visitor_Arrived (wildlife), Toy_Play and any behaviour use
  of Animal_Happy (behaviour), Butcher (rehome). The events exist, so their calls work as soon as they land.
- `TamingApi.cheer` (used by other roles) stays silent on purpose, so callers choose their own sound.

Cost: one sound packet per nearby player per moment, nothing per tick. A morning with eight residents sends
eight door sounds at once; `MaxInstance: 2` keeps the client to two of them.

## Gate

- `asset_validate` `_worktrees/CozyTales-Ranching-sounds`: valid, 0 errors, 0 schema errors, 12 known
  warnings (`CozyRanchJournal`, `CozyRanchTrough`, `CozyRanchUseHouse`, `CozyRanchOfferFood` types).
- `ui_check_java`: valid (in the test gate case).
- `.\gradlew.bat --no-daemon build`: green.
- `node tools/check-lang.mjs`: 0 keys missing from en-US (no lang added).
- `tests/hytale/sounds.test.json`: 3 cases pass (gate; one `asset_check` per id expecting type SoundEvent,
  0 errors, 0 warnings; build). I checked that a missing `.ogg` shows up as a reference warning, so the
  id case fails on a wrong path. It does **not** catch a misspelt `Parent` (tried `SFX_Attn_Nope`: no
  diagnostic).
- No server was run (no port for this role), so no `server_validate` and nothing heard in the client.

## Weak

- **Unheard.** Character (is it cozy, is it cute or cloying) is a guess from prompts and pictures.
- Trough_Fill_01 is mostly hiss even after a 7 kHz low-pass (hf -2 dB); if it grates, drop it from the event
  and keep Trough_Fill_02 alone.
- Animal_Happy_02 (glockenspiel), Visitor_Arrived and the heart sparkle layer are bright (hf -2 to -4 dB).
  The sparkle is meant to be, at -4 dB under the main note; the other two may want a lower low-pass.
- Animal_Happy repeats often; four takes in different instruments may sound like four different animals
  rather than one reaction. If so, keep the two most alike (marimba 01 and xylophone 04).
- Visitor_Arrived has five chime strikes over 1.2 s, not the two asked for.
- Toy_Play is one event for five toys: a ball bounce, a wooden knock and a boing at random. No bell or
  duck-specific sound (the bell take was a constant treble jingle and was rejected).
- Journal_Open (0.40 s) and Butcher_01, Toy_Play_02 are under 0.4 s, too short for an integrated loudness
  reading; their RMS is in line with the others.
- AudioCategory: world sounds inherit `AudioCat_SFX` from the vanilla `SFX_Attn_*` parents; the journal pair
  use `AudioCat_UI` (the music ducking that vanilla discoveries do was left out on purpose).

## REQUESTS

None required. For the other batch-2 roles, if they want suggestions: wildlife plays
`RanchSounds.VISITOR_ARRIVED` at the visitor when it spawns (the event carries 25 blocks); behaviour plays
`RanchSounds.TOY_PLAY` when a `toy_*` behaviour starts; rehome plays `RanchSounds.BUTCHER` at the block
when butchering is confirmed. For the coordinator: run `server_validate` on the merged pack once, since
nothing here was loaded by a real server.
