# Last session

**Date:** 11.10.2026

**What changed:**
Added a start menu with two modes. Tiers: level 1 has 5 rings and 60 s, each level adds 2 rings and 10 s, up to level 10 (23 rings; pegs hold 12 each, 24 total, derived from peg length / stack height). The countdown starts when the player taps Start on the level card. Level score = 100 × rings + 10 × whole seconds left; the run total is saved when time runs out or all tiers are cleared. Free play keeps 10 rings with the timer starting at the first caught ring; win times are saved. A scores view (top 10 per mode, newest entry highlighted) opens from the menu and from the end-of-run cards.

Earlier in the day: project created; extra idle tumbling; peg friction; second (roll) tumble axis with position-dependent jet spin; free-play timer starts at first caught ring. The clock now stops only when every caught ring has settled onto its stack.

**Verification performed:**
- `env -u ANDROID_SDK_ROOT ./gradlew test assembleDebug` — 18/18 unit tests pass.
- `env -u ANDROID_SDK_ROOT ~/bin/build_apk.sh` — signed debug APK at `build_output/Rinkulat-debug.apk`.
- Not yet play-tested on a device.

**Known issues / follow-ups:**
See `TODO.md`.
