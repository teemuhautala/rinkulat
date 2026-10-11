# TODO

- Play-test and tune the physics constants in `ChamberWorld` companion (jet strength/width, drag, catch tolerance, tumbling) on a real device.
- Sound effects (pump squish, ring click on peg, win jingle).
- Tier mode: playtest whether later levels (20+ rings) are too crowded in the chamber.
- No in-progress tier run survives process death.
- Rings currently can't be knocked off by other rings, only by the jet.
- Physics state is not saved across process death (rings reset on restart).
