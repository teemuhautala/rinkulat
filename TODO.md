# TODO

- Play-test and tune the physics constants in `ChamberWorld` companion (jet strength/width, drag, catch tolerance, tumbling) on a real device.
- Sound effects (pump squish, ring click on peg, win jingle).
- Tier mode: playtest whether later levels (20+ rings) are too crowded in the chamber.
- No in-progress tier run survives process death.
- Rings currently can't be knocked off by other rings, only by the jet.
- Physics state is not saved across process death (rings reset on restart).
- Tune per-level twists on device: small jet strength/width, CURRENT_SPEED, obstacle position/size, how hard upside-down levels feel.
- Currents are only visible through drifting particles; consider an on-screen hint (arrows) for them.
- Level swiping is debug-only now; remove it entirely before release if not wanted.
- Plan chapter 3+ (levels 11+): new mechanics in `LevelLayout.LEVELS`, new `ThemeId` + `ToyTheme`, and a `Tiers.CHAPTERS` entry (rings/time currently default to 10 / 2:30).
- Per-level best times are keyed by level number; after renumbering levels the old bests stay with the number. Consider clearing or migrating them once the map is final.
- Free-play times saved before per-level lists have level 0 and are no longer shown.
- Tune the three-obstacle positions (`SIDE_OBSTACLE_Y`, x 0.22/0.78) and level 15 (proposal) on a device.
- Tune `WEIGHTLESS_GRAVITY` (level 11): 0 would be truly weightless but caught rings would then never slide down to settle, so the level couldn't be won.
- Saved best times/lists are per level number: after the 15.10 → 16.10 renumbering, old level 8–10 times now show on the new three-obstacle levels.
- Tune shake feel on device: `SHAKE_RESPONSE`, chapter multiplier (2.5 for 11–15), dead zone/cap in `TiltSensor.kt`. Consider letting particles/liquid visibly slosh too, and whether shakes should knock caught rings off pegs.
- Tune `ATTRACT_ACCEL`/`ATTRACT_REACH`: levels 12–15 are weightless (gravity ~0.13), so the planets' pull (~1.2 at contact) dominates and rings stay stuck to a planet until a jet or shake frees them; consider a weaker pull on weightless levels.
- Play-test level 15 wormholes: mouth size (`WORMHOLE_RADIUS` 0.06), whether the mouth at (0.5, 0.5) sits too close to the hanging peg bases, and whether particles should also pass through.
