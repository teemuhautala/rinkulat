# Changelist

Format: `- (dd.mm.yyyy) [AREA] <short change summary> (files: filename1, filename2)`

- (11.10.2026) [PROJECT] Scaffold Compose Android project fi.dreamr.rinkulat with portrait-only activity and vector launcher icon (files: settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, app/build.gradle.kts, AndroidManifest.xml, project.properties)
- (11.10.2026) [CORE] Add ring-in-liquid simulation: jet flow field, drag, tumbling, wall/ring/peg collisions, peg catching and stacking, timer and win detection (files: ChamberWorld.kt)
- (11.10.2026) [UI] Add game screen with toy case, chamber canvas rendering, push button with haptics, HUD, reset and win overlay; tilt input via gravity sensor (files: RinkulatScreen.kt, GameViewModel.kt, TiltSensor.kt, Theme.kt, MainActivity.kt)
- (11.10.2026) [TEST] Add simulation unit tests for settling, jet lift, catching, stacking/win and tilt (files: ChamberWorldTest.kt)
- (11.10.2026) [CORE] Add idle ring tumbling and weaken the lie-flat torque so resting rings don't always end up horizontal; caught rings now couple less to the jet, have upward-only peg friction and a larger release margin so they are harder to knock off (files: ChamberWorld.kt, ChamberWorldTest.kt)
- (11.10.2026) [CORE] Add second tumble axis (roll about the view axis): rings can sit tilted in-plane, catching requires the ring axis to be near vertical, jet spin on both axes depends on ring position (strongest low and near the nozzle), wall/peg collisions use the rolled ellipse (files: ChamberWorld.kt, RinkulatScreen.kt, ChamberWorldTest.kt)
- (11.10.2026) [CORE] Start the game timer when the first ring lands on a peg instead of on the first pump (files: ChamberWorld.kt, ChamberWorldTest.kt)
- (11.10.2026) [CORE] Add tier mode rules (5 rings/60 s, +1 ring/+10 s per level, capped by peg ring capacity, faster clears score higher) and resettable ring count (files: Tiers.kt, ChamberWorld.kt)
- (11.10.2026) [DATA] Persist top-10 tier scores and free-play best times in SharedPreferences (files: ScoreRepository.kt)
- (11.10.2026) [UI] Add start menu, level intro/complete, time's-up/all-cleared and free-play win cards, scores list with tabs and new-entry highlight, level/countdown HUD (files: GameOverlays.kt, RinkulatScreen.kt, GameViewModel.kt)
- (11.10.2026) [TEST] Add tier rule, capacity and score list tests (files: TiersAndScoresTest.kt)
- (11.10.2026) [CORE] Level/free-play clock stops only once every caught ring has settled onto its stack (replaces the jet-calm check); shorten pegs to 12 rings each (24 total) and add 2 rings per tier level, giving 10 levels of 5–23 rings (files: ChamberWorld.kt, Tiers.kt, GameOverlays.kt, ChamberWorldTest.kt, TiersAndScoresTest.kt)
