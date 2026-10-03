# Vandor Labs block guide

These images come from a real Forge 1.12.2 client in the repeatable ReproLab
world. The close-ups show individual shapes, controls, and states. Open an image
at full size to inspect its texture and model.

- [New in 1.2](version-1.2.md)
- [Programmable block improvements](task-improvements.md)
- [New in 1.1](version-1.1.md)

## Blocks and systems

- [Programmable displays and consoles](programmable.md)
- [Programmable Storage](../programmable-storage.md)
- [Programmable Door](doors.md)
- [Programmable Trapdoor](programmable-trapdoor.md)
- [Programmable Diagonal Trapdoor](programmable-diagonal-trapdoor.md)
- [Rocket Thruster, Ion Drive, Plasma Vent, and Impulse Engine](propulsion.md)
- [Programmable Ramp](ramp-controller.md)
- [Lights](lights.md)
- [Buttons, switches, and levers](controls.md)
- [Chairs and connected seats](chairs.md)
- [Landing Gear](version-1.1.md#landing-gear)
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
