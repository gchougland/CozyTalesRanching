# fix2-sound: Visitor_Arrived remade

Gibson: "The noise for a wild animal appearing does not sound good. Remake it."

**Nobody here has heard the new files.** Everything below was measured or read off spectrograms. Gibson needs to
listen in game (spawn a wildlife visitor near a ranch, or play `SFX_CozyRanch_Visitor_Arrived`).

## What was wrong with the old one (measured)

`Visitor_Arrived_01.ogg` (wave 3, raw take 20, "wooden wind chimes"): 1.24 s, -22.0 LUFS, peak -5.4 dBFS, but the
spectrogram shows **seven irregular strikes** (a clatter, not a cue), broadband clacks down to 20 Hz on each strike,
and most of its tonal energy between 2 and 14 kHz (RMS above 4 kHz only 4 dB under the full band). It reads as
rattling and shrill, which fits "does not sound good".

## What changed

- `Common/Sounds/CozyRanch/Wildlife/Visitor_Arrived_01.ogg` replaced: soft marimba strike, then the same strike a
  **fourth higher** 180 ms later (a rising "da-dong"), 0.85 s.
- `Common/Sounds/CozyRanch/Wildlife/Visitor_Arrived_02.ogg` new: the same strike, then a **fifth higher** 200 ms later.
- `Server/Audio/SoundEvents/CozyRanch/SFX_CozyRanch_Visitor_Arrived.json`: both files in the layer's `Files` (the
  client picks one). `Volume -5`, `Parent SFX_Attn_Moderate`, `MaxInstance 1` unchanged.

## How they were made

ElevenLabs Sound Effects V2 through fal (`fal-ai/elevenlabs/sound-effects/v2`), 2026-10-23, output `pcm_44100`
(WAV, so no MP3 artefacts), 7 takes, 10.2 s requested (6 x 1.5 s + 1 x 1.2 s) at $0.002/s = **$0.0204** in total.

| # | Prompt (influence) | Raw measurements | Verdict |
| --- | --- | --- | --- |
| 0 | Two soft warm marimba notes, a gentle rising interval, low-mid register, mellow wooden tone, short natural decay, cozy farm game notification that a new animal has arrived, clean, no reverb tail, no voices (0.6, 1.5 s) | 1.48 s, peak -0.5, -16.7 LUFS, hf -36 | rejected: one strike (G3 + C5 + G5), boomy, slow 1 s decay |
| 1 | A short warm two-note pluck on a nylon string guitar, rising, soft and round, followed by a faint gentle rustle of leaves in a bush, cozy farm game, a curious visitor appears, quiet, no voices (0.6, 1.5 s) | 1.48 s, peak 0.0, -12.3 LUFS, hf -38 | rejected: nicest pluck, but **clipped** (flat tops of 4, 6 and 8 samples on the attack), a faint 60 ms pre-note flam, no rustle |
| 2 | Two mellow hollow bamboo tube chime tones, struck softly one after the other, warm and woody, low register, pleasant cozy notification, short decay, no clatter, no voices, no synth (0.6, 1.5 s) | 1.48 s, peak -16.0, -26.6 LUFS | rejected: one near-sine 588 Hz tone that sustains the whole 1.4 s (a beep) |
| 3 | A soft kalimba playing two gentle rising notes, warm thumb piano, round and mellow, a friendly hello, cozy game notification, short and clean, quiet, no voices, no reverb (0.6, 1.5 s) | 1.48 s, peak -15.9, -28.0 LUFS | rejected: one 393 Hz tone with tremolo, sustains to the end (a hum) |
| 4 | Two separate soft wooden marimba notes a quarter second apart, the second note higher than the first, warm mid register, gentle mallets, short clean decay, cozy farm game: a new animal has arrived, no voices (0.7, 1.2 s) | 1.20 s, peak -3.5, -21.2 LUFS, hf -35, **no clipping**, single clean onset, decays 50 dB in 0.6 s | **used** for both files (one soft strike of B3 + E4 + G4) |
| 5 | Soft nylon string guitar plucks two single notes, one then a higher one a moment later, warm and round, then a faint soft rustle of grass, cozy farm game, a shy visitor appears, short, quiet, no voices (0.7, 1.5 s) | 1.48 s, peak -24.4, -39.8 LUFS | rejected: almost silent |
| 6 | A light rustle of leaves in a bush followed by two gentle rising wooden xylophone notes, soft mallets, mellow and warm, cozy game notification, short, no reverb, no voices (0.7, 1.5 s) | 1.48 s, peak 0.0, -14.2 LUFS, hf -20 | rejected: clipped at both rails, one note, the "rustle" is a 3 to 13 kHz hiss band after the note |

The generator never made two separate notes (a 20 ms RMS envelope of every take shows a single decay), so the two
notes were made by hand from take 4, the cleanest take:

1. mono, 3 ms fade-in (the take starts at -6 dBFS on sample 0; without this the delayed copy gave a broadband click
   up to 13 kHz, seen on the spectrogram and fixed), high-pass 110 Hz (mallet rumble; lowest partial is 246 Hz),
   low-pass 8 kHz, both 12 dB/oct;
2. a copy pitched up 5 semitones (01) or 7 semitones (02) by resampling (so it also decays a little faster, like a
   higher bar), -1 dB, delayed 180 ms (01) or 200 ms (02), mixed with the original;
3. cut at 0.85 s (the take's tail below that is noise at about -80 dB RMS), 4 ms fade-in, 120 ms fade-out;
4. gain to -22 LUFS integrated with the peak capped at -3 dBFS (the cap did not bind), Ogg Vorbis q6, 44.1 kHz mono,
   no metadata. Same format as before and as the other positional cues.

Script (scratchpad, not in the repo) `build.sh RAW SEMITONES DELAY_MS OUT`; the raw WAVs and losers stay out of the
repo. The fal result URLs for take 4 expire; the request id is `01a16a5c-2040-7240-9261-6be7018a9b56`.

## Measurements (art/sounds/measure.ps1 from the main checkout)

| File | Length | Ch / rate / codec | Peak dBFS | True peak | Integrated LUFS | Lead / trail silence | RMS > 4 kHz rel. | Onsets |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| old Visitor_Arrived_01 | 1.24 s | 1 / 44.1k / vorbis | -5.4 | -5.1 | -22.0 | 0 / 0 | -4.2 dB | 7, irregular |
| new Visitor_Arrived_01 | 0.85 s | 1 / 44.1k / vorbis | -6.7 | -6.7 | -22.0 | 0 / 0 | -58.6 dB | 2 (0, 180 ms) |
| new Visitor_Arrived_02 | 0.85 s | 1 / 44.1k / vorbis | -6.9 | -6.9 | -21.9 | 0 / 0 | -56.6 dB | 2 (0, 200 ms) |

Both sit where notes/sounds.md puts reward moments (-21 to -22 LUFS, peaks under -3 dBFS), no clipping, no leading or
trailing silence, energy between about 150 and 700 Hz (warm, not shrill).

## Why this one

It is the only take that was both clean (no clipping) and the right character (soft wooden mallet, fast natural
decay). A rising interval is the usual "someone's here" shape and the overlap of the two strikes keeps it soft
rather than beepy. It is much darker than the old file (HF -58 vs -4 dB); if it is too dull or too quiet against
ambience in game, raise the event's `Volume` before changing the files.

## Weak

- Unheard. The chord in the strike (B, E, G) is ambiguous between E minor and G major; it could read slightly wistful.
- If Gibson prefers one interval, drop the other file from `Files`.
- No leaf rustle: every take that tried for it gave hiss or nothing.

## Gate

- `asset_validate` on `_worktrees/CozyTales-Ranching-fix2-sound`: valid, 0 errors, 0 schema errors, 13 known
  plugin-type warnings.
- `tests/hytale/sounds.test.json`: run as a copy pointed at this worktree (`build/fix2-sound-tests/`, ignored),
  **3 of 3 cases pass** (gate with ui_check_java; every RanchSounds id 0 errors 0 warnings, so both .ogg paths resolve; gradle build green).
- No Java changed, no server run (no port for this role).

## REQUESTS

None.
