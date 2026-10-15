# Rinkulat

A phone version of the classic handheld water ring toss toy. Colored rings float in a liquid-filled chamber; holding the push button drives a jet of liquid up from the bottom that throws the rings up, and they then drift back down. The goal is to land every ring on one of the two pegs. Tilting the phone changes which way the rings sink.

Package: `fi.dreamr.rinkulat` · single-module Compose app (`:app`), portrait only.

## Architecture

- `game/ChamberWorld.kt` — pure-Kotlin simulation (no Android dependencies, unit-tested). Holds rings, pegs, decorative particles, jet strength, timer and win state. All tuning constants live in its companion object.
- `game/GameViewModel.kt` — owns the world, mode (free play / challenge) and phase state machine (start, level intro, playing, level complete, run over, free-play won), countdown, scoring and score lists; a `frame` counter drives canvas redraws.
- `game/TiltSensor.kt` — `rememberTiltReading()`: gravity sensor (accelerometer fallback) converted to screen-axis gravity in g units.
- `game/LevelLayout.kt` — per-level jets (nozzle + direction), obstacle, current and upside-down flag; `CLASSIC` for free play, `forTier(level)` for tiers.
- `game/Tiers.kt` — challenge mode rules: rings/seconds per level, max level from `ChamberWorld.RING_CAPACITY`, level score.
- `game/ScoreRepository.kt` — top-10 score lists (tier points, free-play times) stored in SharedPreferences `rinkulat_scores`.
- `ui/GameOverlays.kt` — start buttons, toy-styled level/result cards, scores list.
- `ui/RinkulatScreen.kt` — frame loop (`withFrameNanos`), HUD, toy case, chamber Canvas rendering, push button, win overlay.

## Physics notes

- World units: chamber is 1.0 × 1.5, y grows downward. Fixed 1/120 s sub-steps.
- Rings have two tumble angles: `phi` about the horizontal screen axis (0 = face-on, π/2 = lying flat) and `roll` about the viewing axis (in-plane tilt), drawn as a rotated ellipse. Catching needs `flatness = |sin(phi)·cos(roll)|` (ring axis near vertical). The jet spins rings on both axes, strongest low down and near the nozzle (`jetSpinInfluence`). Drag toward the local liquid velocity; flat rings resist vertical motion more. Falling rings tend to lie flat; flow shear and turbulence tumble them.
- Liquid flow is an analytic field: Gaussian plume from the nozzle widening upward, fan-out at the top, return flow along the walls, inflow near the nozzle, plus time-varying turbulence. Jet strength ramps up while pressed and decays after release.
- A ring is caught when it comes down onto a peg tip, centered within `CATCH_TOLERANCE`, and lying flat enough (`CATCH_MIN_FLATNESS`). Caught rings slide on the peg, stack, and can be pushed off the top by a strong jet.
- Win: all rings caught and settled onto their stacks (the clock stops at that moment). Pegs hold 12 rings each.

## Build

`env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug`, then `~/bin/build_apk.sh` (output in `build_output/`).

## Challenge levels

Buttons accumulate: each level keeps the previous jets unchanged (position and direction) and may add more. Both button strips (above and below the chamber) are always shown at the same size with 5 slots: corner, ¼, center, ¾, corner.

| Lv | Jets | Twist |
|---|---|---|
| 1 | bottom center (big flow) | Classic |
| 2 | + bottom ¼ and ¾ | — |
| 3 | + top center (shoots down) | — |
| 4 | + top ¼ and ¾, + bottom corners aimed at the chamber center | — |
| 5 | + top corners aimed at the center (full set: 5 bottom + 5 top) | — |
| 6 | as 5 | obstacle |
| 7 | as 5 | obstacle + current right |
| 8 | as 5 | upside down (pegs hang from above) + obstacle |
| 9 | as 5 | upside down + obstacle + current left |
| 10 | as 5 | upside down + obstacle + split current (top right, bottom left) |

Upside down only flips the vertical gravity component, so left/right tilt still behaves intuitively. Swipe the chamber left/right during play to jump between levels (play-testing aid). In free play this keeps 10 rings; in challenge it uses the level's real rings/time and turns the run into an unsaved practice run.
