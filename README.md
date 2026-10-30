# Rinkulat

A phone version of the classic handheld water ring toss toy. Colored rings float in a liquid-filled chamber; holding the push button drives a jet of liquid up from the bottom that throws the rings up, and they then drift back down. The goal is to land every ring on one of the two pegs. Tilting the phone changes which way the rings sink.

Package: `fi.dreamr.rinkulat` · single-module Compose app (`:app`), portrait only.

## Architecture

- `game/ChamberWorld.kt` — pure-Kotlin simulation (no Android dependencies, unit-tested). Holds rings, pegs, decorative particles, jet strength, timer and win state. All tuning constants live in its companion object.
- `game/GameViewModel.kt` — owns the world, mode (free play / challenge) and phase state machine (start, level intro, playing, level complete, run over, free-play won), countdown, scoring and score lists; a `frame` counter drives canvas redraws.
- `game/TiltSensor.kt` — `rememberTiltReading()`: gravity sensor (accelerometer fallback) converted to screen-axis gravity in g units.
- `game/LevelLayout.kt` — level mechanics only: jets (nozzle + direction), obstacle, current and upside-down flag. `LEVELS` lists every level with its map `number`.
- `game/Tiers.kt` — map rules: chapters of 5 levels (theme id, rings and seconds per level), checkpoints (1, 6, 11, …), level lookup by number, level score.
- `game/ScoreRepository.kt` — SharedPreferences `rinkulat_scores`: top-10 lists with player names (challenge points; free-play times per level), last player name, highest level reached (unlocks) and best free-play time per level.
- `ui/theme/Theme.kt` — `ToyTheme` per `ThemeId` (liquid, button, particle colors, scenery), provided through `LocalToyTheme`.
- `ui/GameOverlays.kt` — start buttons with checkpoint selector, free-play level grid, toy-styled level/result cards, scores list.
- `ui/AdBanner.kt` — AdMob banner shown at the bottom of the start screen and free-play level grid (sample IDs for now).
- `ui/RinkulatScreen.kt` — frame loop (`withFrameNanos`), HUD, toy case, chamber Canvas rendering, push button, win overlay.

## Physics notes

- World units: chamber is 1.0 × 1.5, y grows downward. Fixed 1/120 s sub-steps.
- Rings have two tumble angles: `phi` about the horizontal screen axis (0 = face-on, π/2 = lying flat) and `roll` about the viewing axis (in-plane tilt), drawn as a rotated ellipse. Catching needs `flatness = |sin(phi)·cos(roll)|` (ring axis near vertical). The jet spins rings on both axes, strongest low down and near the nozzle (`jetSpinInfluence`). Drag toward the local liquid velocity; flat rings resist vertical motion more. Falling rings tend to lie flat; flow shear and turbulence tumble them.
- Liquid flow is an analytic field: Gaussian plume from the nozzle widening upward, fan-out at the top, return flow along the walls, inflow near the nozzle, plus time-varying turbulence. Jet strength ramps up while pressed and decays after release.
- A ring is caught when it comes down onto a peg tip, centered within `CATCH_TOLERANCE`, and lying flat enough (`CATCH_MIN_FLATNESS`). Caught rings slide on the peg, stack, and can be pushed off the top by a strong jet.
- Bubbles drift opposite the device gravity used by rings, including the vertical flip in upside-down levels. Their rise weakens as the device lies flat. In weightless space levels, bubble motion (buoyancy and flow) is reduced to 12% speed.
- Win: all rings caught and settled onto their stacks (the clock stops at that moment). Pegs hold 12 rings each.

## Build

`env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug`, then `~/bin/build_apk.sh` (output in `build_output/`).

## Progression

- **Challenge:** a run starts from a checkpoint (first level of a chapter: 1, 6, …), chosen with the arrows under the Challenge button once more than one is unlocked. Clearing a level unlocks the next one (not in practice runs).
- **Free play:** opens a level grid; only levels reached in Challenge can be played (10 rings, timer from first catch). Tiles show the best time per level. Debug builds (`FLAG_DEBUGGABLE`) can start locked levels and swipe the chamber to change level.

## Chapters

| Levels | Theme | Rings | Time |
|---|---|---|---|
| 1–5 | Orange (garden) | 5, 6, 7, 8, 9 | 1:00 … 1:40 (+10 s/level) |
| 6–10 | Sea (blue water, seaweed) | 10 | 1:50 … 2:30 |
| 11–15 | Space (stars, planet) | 10 | 2:30 |
| 16–20 | Mechanicals (brass gears and copper pipes) | 10 | 2:30 |
| 21+ | last chapter's theme | 10 (default) | 2:30 (default) |

Shakes and nudges push the rings opposite to the phone's motion; chapter 3 (11–15) reacts 2.5× stronger. Edit `Tiers.CHAPTERS` to change a chapter's theme, rings, times or shake strength.

**Moving a level:** change its `number` in `LevelLayout.LEVELS` (e.g. swap 7 and 9 by editing both numbers). The theme, rings and time come from the chapter the number falls in. Numbers must run 1..N without gaps (unit-tested). Best free-play times are stored by number, so they stay with the number, not the layout.

## Challenge levels

Buttons accumulate through levels 1–15. Mechanicals (16–20) has the full set of push controls in every level. Existing jets keep their position and direction. Both button strips (above and below the chamber) are always shown at the same size with 5 slots: corner, ¼, center, ¾, corner.

| Lv | Jets | Twist |
|---|---|---|
| 1 | bottom center (big flow) | Classic |
| 2 | + bottom ¼ and ¾ | — |
| 3 | + top center (shoots down) | — |
| 4 | + top ¼ and ¾, + bottom corners aimed at the chamber center | — |
| 5 | + top corners aimed at the center (full set: 5 bottom + 5 top) | — |
| 6 | full set | obstacle |
| 7 | full set | obstacle + current right |
| 8 | full set | three obstacles (center + two higher on the sides) |
| 9 | full set | three obstacles + current right |
| 10 | full set | three obstacles + split current (top right, bottom left) |
| 11 | full set | weightless: rings float, moved by the jets, currents and a faint pull from tilting |
| 12 | full set | weightless + attracting obstacle (normal orientation) |
| 13 | full set | weightless + upside down (pegs hang from above) + attracting obstacle |
| 14 | full set | weightless + upside down + two attracting obstacles (high on the sides) |
| 15 | full set | weightless + upside down + two attracting side obstacles + wormhole pair (center, mirrored across the pegs) |
| 16 | full set | Mechanicals: pegs slide sideways |
| 17 | full set | Mechanicals: pegs rotate back and forth |
| 18 | full set | Mechanicals: rotation + sideways movement |
| 19 | full set | Mechanicals: pegs swing while rising/falling in opposite phases |
| 20 | full set | Mechanicals: shorter pegs, continuous 360° rotation, 45° phase offset, opposite vertical movement |

Mechanicals uses a brass/copper theme with animated decorative gears, pipework, panel rivets and a pressure gauge. Levels 16–19 rock pegs ±0.48 radians around their bases and/or slide them ±0.10 world units horizontally. Levels 19 and 20 also move their pegs vertically ±0.10 world units in opposite phases, with an approximately 8.4-second cycle. Level 20 uses horizontally fixed bases and shorter 0.18-unit pegs (normally 0.245), rotating continuously in the same direction at 0.4 radians/s (about 15.7 seconds per revolution), 45° apart. Their full swept outlines, including mounts, stay clear of each other and the chamber walls; the shorter pegs still hold 9 rings each. Catches, shaft/mount collisions, ring orientation and stacking use the moving peg frame; caught rings travel with the pegs and can still be pumped off. Restart resets the mechanism phase. Level 16 is the new chapter checkpoint.

Attracting obstacles (12–15) pull free rings within `ATTRACT_REACH` of their surface, up to `ATTRACT_ACCEL` at contact; a faint halo shows the range. The pull bends passing rings but jets free a ring resting against one.

Wormholes (`LevelLayout.wormholes`, level 15) come in connected pairs: a free ring whose center enters one mouth reappears in the other with the same velocity, on the opposite side so it keeps travelling the same way, and can't re-enter until it has left the exit mouth. They don't pull or collide.

Upside down only flips the vertical gravity component, so left/right tilt still behaves intuitively. In debug builds, swipe the chamber left/right during play to jump between levels (play-testing aid). In free play this keeps 10 rings; in challenge it uses the level's real rings/time and turns the run into an unsaved practice run.
# rinkulat

## Fish challenge

From level 6 onward, an animated fish appears halfway through the allotted Challenge time, or immediately when starting Free play. Levels 1–5 have no fish in either mode. It chooses the nearest occupied peg’s exposed ring, approaches its nearer side and keeps that side for the attack, and then steadily pulls it along the peg over ten seconds of actual pulling, releasing it from the tip unless hit by three separate jet contacts within one second. Rings keep sinking normally until the fish arrives; if it loses contact, pulling pauses and the ring can settle again. Scaring it off mid-pull leaves the ring on the peg to settle back down. Release and press again for another burst; holding one stream counts only once. After a successful three-hit scare it roams for a full 30 seconds measured from the third hit before selecting any ring again; theft also starts a 30-second cooldown. The first two hits cause a brief color flash and faster tail beats while the fish holds its ground; only the third hit triggers an attack retreat. The wider center cones cover inward approaches, and the quarter-position buttons cover outward approaches. Only focused bursts through the central three quarters of the visible stream cone affect the fish. Effectiveness decreases with nozzle-to-fish distance, and streams beyond 70% of the swim area height (1.05 world units, independent of screen size) have no effect. These limits apply both to attack hits and to pushing or steering the roaming fish; cone edges, return currents and distant streams do not move it. Roaming fish react to focused hits with a stronger shove and a fast sideways escape swim lasting 0.65 seconds after contact ends, with faster tail beats. A fish that is still evading streams does not acquire a new ring target. With no pegged rings it waits while swimming.
