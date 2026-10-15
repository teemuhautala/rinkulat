# Last session

**Date:** 11.10.2026

**What changed:**
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
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — 25/25 unit tests pass.
- `env -u ANDROID_SDK_ROOT ~/bin/build_apk.sh` — signed debug APK at `build_output/Rinkulat-debug.apk`.
- Not yet play-tested on a device.

**Known issues / follow-ups:**
See `TODO.md`.
