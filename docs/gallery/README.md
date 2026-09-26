# Vandor Labs block guide

These images come from a real Forge 1.12.2 client in the repeatable ReproLab
world. The close-ups show individual shapes, controls, and states. Open an image
at full size to inspect its texture and model.

## Blocks and systems

- [Programmable displays and consoles](programmable.md)
- [Programmable Door](doors.md)
- [Rocket Thruster, Ion Drive, Plasma Vent, and Impulse Engine](propulsion.md)
- [Programmable Ramp](ramp-controller.md)
- [Lights](lights.md)
- [Buttons, switches, and levers](controls.md)
- [Chairs](chairs.md)
- [Building blocks, finishes, and glass](building.md)

The [Configurizer](../CONFIGURIZER.md) opens programmable block settings. The
[Duplifier](../DUPLIFIER.md) copies selected settings between compatible blocks.
Crafting recipes are listed in the in-game recipe book and in the main
[README](../../README.md).

## Regenerating screenshots

Run `bash testclient/generate_gallery.sh` from the repository root. This builds
the current jar, starts the software-rendered client, checks live behavior, and
exports the selected screenshots to `docs/images/gallery`. See the
[gallery maintenance guide](CONTRIBUTING.md) for details.
