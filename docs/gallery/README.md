# Vandor Labs gallery

These images come from a real Forge 1.12.2 client in the repeatable ReproLab
world. The close-ups show individual shapes, controls, and states. Open an image
at full size to inspect its texture and model.

- [All version guides](../versions/README.md)
- [New in 2.3: cockpits, access control and movement](../versions/version-2.3.md)
- [New in 2.2](../versions/version-2.2.md)
- [New in 2.1](../versions/version-2.1.md)
- [New in 2.0](../versions/version-2.0.md)
- [New in 1.7](../versions/version-1.7.md)
- [New in 1.6](../versions/version-1.6.md)
- [New in 1.5](../versions/version-1.5.md)
- [New in 1.4](../versions/version-1.4.md)
- [New in 1.3](../versions/version-1.3.md)
- [Animated motion guides](../animated-documentation.md)
- [New in 1.2](../versions/version-1.2.md)
- [Programmable block improvements](task-improvements.md)
- [New in 1.1](../versions/version-1.1.md)

## Items

- [Programmable armor: eight role sets and six company designs](armor.md)

## Blocks and systems

- [Canopies, Front Canopy Glass and Large Front Canopy Glass](../canopies.md)
- [Pilot Seat and Taxi Mode](../pilot-seat-taxi.md)
- [Programmable displays and consoles](programmable.md)
- [Programmable Storage](../programmable-storage.md)
- [Programmable Door](doors.md)
- [Programmable Trapdoor](programmable-trapdoor.md)
- [Programmable Diagonal Trapdoor](programmable-diagonal-trapdoor.md)
- [Rocket Thruster, Ion Drive, Plasma Vent, and Impulse Engine](propulsion.md)
- [Ramp Controller](ramp-controller.md)
- [Lights](lights.md)
- [Buttons, switches, and levers](controls.md)
- [Chairs and connected seats](chairs.md)
- [Landing Gear](../versions/version-1.1.md#landing-gear)
- [Building blocks, finishes, and glass](building.md)

The [Configurizer](../CONFIGURIZER.md) opens programmable block settings. The
[Duplifier](../DUPLIFIER.md) copies selected settings between compatible blocks.
Crafting recipes are listed in the in-game recipe book and in the main
[README](../../README.md).

## Regenerating screenshots

Run `bash testclient/generate_gallery.sh --focus storage` from the repository root. This builds
the current jar, starts the software-rendered client, checks live behavior, and
exports the selected screenshots to `docs/images/gallery`. See the
[gallery maintenance guide](CONTRIBUTING.md) for details.
