# Last session

**Date:** 29.10.2026

**What changed:**
Bubbles now rise opposite the gravity direction used by rings instead of always toward screen-top. This follows device tilt, respects upside-down layouts, and naturally weakens when the device lies flat. In weightless space levels, bubble buoyancy and liquid-flow motion are scaled to 12% speed. Non-bubble particles retain their existing movement.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes; 65 tests in each variant.
- New regressions cover upright, inverted, sideways, diagonal and flat device gravity; upside-down layouts; slow motion in all space levels with/without streams; and recycling across all chamber edges.
- `~/bin/build_apk.sh` — generated `build_output/Rinkulat-debug.apk`.

**Known issues / follow-ups:**
- Not yet checked on a device. Play-test tilt response and space-level speed.

Previous session:

**Date:** 29.10.2026

**What changed:**
Reproduced the fish crossing into the peg gap: target selection chose the highest ring across both pegs, even when the other peg was closer. It now chooses the nearest accessible stack, takes its exposed ring, and approaches from the nearer side. That side stays fixed while following the ring.

Removed the misleading one-hit attack retreat. Preserving the original three-hit rule, the first two hits now show a brief body-color flash and faster tail beats while the fish holds its ground. The third hit within one second clears the target, starts the escape swim, and starts the full 30-second cooldown. Roaming fish still evade individual streams and cannot acquire a target while escaping.

Earlier level-6 eligibility, immediate Free-play appearance, halfway Challenge appearance, focused stream/range rules and ten-second pull remain. Rings sink normally until actual fish contact, and can settle again if contact is lost.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes; 62 tests in each variant, including twenty-one fish tests.
- Reproduced the occupied-pegs approach failure before fixing it. Frame-driven tests now verify outside approaches from either edge with both pegs occupied, and actual button bursts with jet ramp-up: hold ground on hits one/two, retreat on hit three, no reacquisition for 30 seconds.
- Existing focused-cone, range, ring-pull, reset, moving-peg and cooldown tests pass.
- `~/bin/build_apk.sh` — generated `build_output/Rinkulat-debug.apk`.

**Known issues / follow-ups:**
- Not verified on a device. Play-test the nearest-stack behavior and the distinction between hit feedback and successful retreat.

Previous session:

**Date:** 28.10.2026

**What changed:**
Added the experimental fish to every Challenge level at half time. Animated fish targets caught rings, gives up after three distinct stream contacts in one second, or steadily pulls its target along the peg and drops it from the tip at ten seconds. The pull follows hanging and moving pegs; scaring the fish mid-pull leaves the ring attached to settle again. Both outcomes start 30 seconds of roaming, with faster curved escape from streams. Free play remains unchanged.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes, including seven fish regression tests in both build variants. New full-physics tests verify progressive pull, tip release on normal/hanging/moving pegs, and settling after a mid-pull scare.
- `~/bin/build_apk.sh` — generated `build_output/Rinkulat-debug.apk`.

**Known issues / follow-ups:**
- Not play-tested on a device. Tune visibility, burst difficulty and swimming speed.

Previous session:

**Date:** 28.10.2026

**What changed:**
Attracting orbs in levels 12–15 now have dark violet shading, a subtle rim/highlight and a lavender attraction halo. Earlier obstacles retain their pale appearance. Verified with `assembleDebug` and regenerated the APK; visual appearance has not been checked on a device.

Enabled the full set of ten push controls in every Mechanicals level (16–20). Swapped levels 16/17 and 18/19: sliding rails, rocking brass, clockwork, turning cogs, full revolution. Updated existing layout and motion tests and the README. Levels 19 and 20 now also rise/fall ±0.10 world units in opposite phases (about 8.4 seconds per cycle), preserving their swing/full rotation. Moving-frame physics includes vertical velocity for catches, collisions and attached rings.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes.
- `~/bin/build_apk.sh` — generated `build_output/Rinkulat-debug.apk`.
- Regression tests cover opposite vertical phases, travel bounds, preserved peg length, reset, moving-tip catches, attached stacks and full-rotation clearance.
- Not play-tested on a device.

**Known issues / follow-ups:**
Saved best times remain tied to level numbers. Play-test the revised order and full controls; see `TODO.md`.

Previous session (27.10.2026):

**Date:** 27.10.2026

**What changed:**
Added chapter 4, Mechanicals (16–20), copying the jet layouts from 1–5 with 10 rings / 2:30 and checkpoint 16. Levels 16/18 rock pegs around their bases, 17 slides them sideways, 19 combines both. Level 20 now uses continuous 360° rotation with fixed bases, shorter 0.18-unit pegs, 0.4 rad/s speed and a 45° offset between pegs. Swept outlines stay clear of each other and the walls; each short peg holds 9 rings. Steampunk scenery uses brass gears, copper pipes, riveted panels and a pressure gauge. Peg-frame physics keeps catching, mount/shaft collisions, stacking, ring orientation and release aligned with the moving pegs. Restart resets movement phase. Earlier levels retain stationary pegs.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes; 40 tests in each debug/release suite.
- `~/bin/build_apk.sh` — generated `build_output/Rinkulat-debug.apk`.
- New regressions cover motion bounds/reset, moving-tip catches, solid rotated mounts, release and winning with moving stacks; level 20 also checks multiple full turns, constant phase, smooth angular velocity, full swept clearance and capacity.
- Not play-tested on a device.

**Known issues / follow-ups:**
Play-test movement speeds, difficulty and scenery contrast; see `TODO.md`.

Previous session (23.10.2026):

**Date:** 23.10.2026

**What changed:**
Added an AdMob banner (`play-services-ads` 25.4.0) at the bottom of the screen, below the game area, on the start screen and free-play level grid (`GamePhase.Menu`/`LevelSelect`); it disappears when a game starts. Overlays live in a box above it so they never cover it. `MobileAds.initialize` runs on a background thread in `MainActivity`. The manifest has the `APPLICATION_ID` meta-data. Both the app ID and the banner unit ID are Google's sample/test IDs.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — passes.
- `~/bin/build_apk.sh` — `build_output/Rinkulat-debug.apk`.
- Not checked on a device.

**Known issues / follow-ups:**
See `TODO.md` (real ad IDs, consent).

Previous session (20.10.2026):
Level 15 is now "Wormholes": the center planet became a wormhole mouth at (0.5, 1.0) with its twin mirrored to (0.5, 0.5), on the other side of the pegs. Rings entering one mouth exit the other with unchanged velocity (`ChamberWorld.travelThrough`, `Ring.wormholeExit` prevents bouncing back). The two side planets stay. Levels 12–15 were also made weightless like level 11 earlier in the day.

Levels 12–15 no longer have currents; their obstacles now attract nearby free rings like small planets (`LevelLayout.attractingObstacles`, `ChamberWorld.obstaclePull`, `ATTRACT_ACCEL` 1.4 / `ATTRACT_REACH` 0.18), drawn with a faint halo. Follow-up: level 12 switched to normal gravity and level 14 got two attracting obstacles, so 12–15 are all distinct again (12 normal + 1, 13 upside down + 1, 14 upside down + 2, 15 upside down + 3). Then 12–15 were made weightless like 11 (same `WEIGHTLESS_GRAVITY`).

Shakes and nudges now move the rings: `TiltReading` also carries device linear acceleration (`shakeX/Y`), which `ChamberWorld` applies opposite to the motion. Strength is `SHAKE_RESPONSE` (0.6) × the chapter's `shake` multiplier (1 for levels 1–10, 2.5 for 11–15). Previously nothing reacted to shakes; only the smoothed gravity sensor was read.

Level 11 is now a weightless level (normal orientation, full jets, gravity scaled to `WEIGHTLESS_GRAVITY` = 0.12 of the tilt reading so rings float but tilt still steers them and caught rings still settle). The upside-down levels moved up to 12–15 and the old level 15 was dropped.

Earlier (16.10.2026):
Map extended to 15 levels. Levels 8–10 are now normal-gravity three-obstacle levels (plain, + current right, + split currents); the upside-down levels moved to chapter 3 (11–13) with two new ones (14: three hanging obstacles + current left, 15: + split currents). Chapter 3 uses a new space theme (starfield, ringed planet, moon) and 10 rings / 2:30. `LevelLayout.obstacle` became `obstacles: List<Obstacle>`.

Earlier (15.10.2026):
High-score names: when a score places, the result card shows a name field prefilled with the last name used (`player_name`); the name is saved onto the entry when the player leaves the card. Score lines now have a 4th name field (old 3-field lines still decode). Free-play times are kept as a top 10 per level, and the Scores free-play tab has ‹ Level N › arrows.

Progression rework. Levels now carry a map `number` in `LevelLayout.LEVELS`; theme, rings and time come from the 5-level chapter the number falls in (`Tiers.CHAPTERS`), so moving a level is a number change. Rings go 5→10 (+1 per level, then held at 10), time 1:00 → 2:30 (+10 s per level); defaults 10 rings / 2:30 past the defined chapters. Levels 6–10 use a new blue sea theme (colors, buttons, seaweed/starfish/shell, light rays); the theme also tints the HUD and cards.

Challenge runs start from an unlocked checkpoint (1, 6, …) chosen with arrows under the Challenge button. Reaching a level unlocks it (stored as `highest_level_reached`). Free play opens a level grid showing locks and best time per level (`free_play_best`); debug builds can start locked levels. Level swiping is now debug-only.

Previous session (11.10.2026):
Swiping the chamber now also changes level in challenge mode (play-testing aid): it jumps straight into the level with its real ring count and time. A run that used swiping is a practice run and its score isn't saved.

Menu card removed: the app opens on the toy with Challenge (formerly Tiers) and Free play buttons and a scores (star) icon in the top bar; the home icon returns there. Cards, scores and the top bar use the toy's orange gradient and case edge. `GameMode.Tiers` was renamed `GameMode.Challenge`; the SharedPreferences key `tier_scores` is unchanged so saved scores survive.

Jet schedule revised: 1 bottom → 3 bottom → +top center → +top ¼/¾ and aimed bottom corners → +aimed top corners. Jets never change once they appear; corner jets always aim at the chamber center.

Chamber view is now locked to the simulation's 1:1.5 aspect (previously the world was bottom-aligned in a taller canvas, so the visible center and walls didn't match the physics). HUD is fixed-height with icon buttons (material-icons-core added). Unused button slots are blank.

Tier buttons now accumulate (ending with 5 bottom + 5 top), every level uses the same fixed-size top and bottom strips with 5 equal slots, and from level 5 the corner jets aim at the chamber center.

Free play now doubles as a level tester: swipe the chamber left/right to switch to the next/previous tier layout (10 rings, instant reset, wraps 10↔1). The chosen level sticks across restarts and is shown in the HUD; best times record the level.

Tier levels now each change the toy (see the README table): 1 big jet → 2 bottom jets → +top jet → 4 corner jets → angled corner jets → obstacle → current right → upside down with hanging pegs → current left → split currents. Each jet has its own button in strips above/below the chamber (multi-touch). Free play keeps the classic single jet.

Added a start menu with two modes. Tiers: level 1 has 5 rings and 60 s, each level adds 2 rings and 10 s, up to level 10 (23 rings; pegs hold 12 each, 24 total, derived from peg length / stack height). The countdown starts when the player taps Start on the level card. Level score = 100 × rings + 10 × whole seconds left; the run total is saved when time runs out or all tiers are cleared. Free play keeps 10 rings with the timer starting at the first caught ring; win times are saved. A scores view (top 10 per mode, newest entry highlighted) opens from the menu and from the end-of-run cards.

Earlier in the day: project created; extra idle tumbling; peg friction; second (roll) tumble axis with position-dependent jet spin; free-play timer starts at first caught ring. The clock now stops only when every caught ring has settled onto its stack.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — 34/34 unit tests pass.
- `env -u ANDROID_SDK_ROOT ~/bin/build_apk.sh` — signed debug APK at `build_output/Rinkulat-debug.apk`.
- Not yet play-tested on a device.

**Known issues / follow-ups:**
See `TODO.md`.
