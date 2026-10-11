# Rinkulat

A phone version of the classic handheld water ring toss toy. Colored rings float in a liquid-filled chamber; holding the push button drives a jet of liquid up from the bottom that throws the rings up, and they then drift back down. The goal is to land every ring on one of the two pegs. Tilting the phone changes which way the rings sink.

Package: `fi.dreamr.rinkulat` · single-module Compose app (`:app`), portrait only.

## Architecture

- `game/ChamberWorld.kt` — pure-Kotlin simulation (no Android dependencies, unit-tested). Holds rings, pegs, decorative particles, jet strength, timer and win state. All tuning constants live in its companion object.
- `game/GameViewModel.kt` — owns the world, mode (free play / tiers) and phase state machine (menu, level intro, playing, level complete, run over, free-play won), countdown, scoring and score lists; a `frame` counter drives canvas redraws.
- `game/TiltSensor.kt` — `rememberTiltReading()`: gravity sensor (accelerometer fallback) converted to screen-axis gravity in g units.
- `game/Tiers.kt` — tier mode rules: rings/seconds per level, max level from `ChamberWorld.RING_CAPACITY`, level score.
- `game/ScoreRepository.kt` — top-10 score lists (tier points, free-play times) stored in SharedPreferences `rinkulat_scores`.
- `ui/GameOverlays.kt` — menu, level/result cards, scores list.
- `ui/RinkulatScreen.kt` — frame loop (`withFrameNanos`), HUD, toy case, chamber Canvas rendering, push button, win overlay.

## Physics notes

- World units: chamber is 1.0 × 1.5, y grows downward. Fixed 1/120 s sub-steps.
- Rings have two tumble angles: `phi` about the horizontal screen axis (0 = face-on, π/2 = lying flat) and `roll` about the viewing axis (in-plane tilt), drawn as a rotated ellipse. Catching needs `flatness = |sin(phi)·cos(roll)|` (ring axis near vertical). The jet spins rings on both axes, strongest low down and near the nozzle (`jetSpinInfluence`). Drag toward the local liquid velocity; flat rings resist vertical motion more. Falling rings tend to lie flat; flow shear and turbulence tumble them.
- Liquid flow is an analytic field: Gaussian plume from the nozzle widening upward, fan-out at the top, return flow along the walls, inflow near the nozzle, plus time-varying turbulence. Jet strength ramps up while pressed and decays after release.
- A ring is caught when it comes down onto a peg tip, centered within `CATCH_TOLERANCE`, and lying flat enough (`CATCH_MIN_FLATNESS`). Caught rings slide on the peg, stack, and can be pushed off the top by a strong jet.
- Win: all rings caught and settled onto their stacks (the clock stops at that moment). Pegs hold 12 rings each.

## Build

`env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug`, then `~/bin/build_apk.sh` (output in `build_output/`).
