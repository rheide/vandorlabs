# Vandor Labs block gallery

These screenshots are produced by a real Forge 1.12.2 client running the
deterministic ReproLab scene. They show representative states and functions;
the dozens of selectable screen animations are intentionally summarized rather
than documented one by one.

- [Programmable displays and consoles](programmable.md)
- [Doors](doors.md)
- [Propulsion and hover systems](propulsion.md)
- [Controls, lighting, ramps, furniture, and building blocks](systems-and-building.md)
- [Maintaining and regenerating the gallery](CONTRIBUTING.md)

Regenerate every checked-in image with:

```bash
testclient/generate_gallery.sh
```

The gallery run builds the current jar, boots the software-rendered client,
executes the live runtime contracts, captures the scenes, and exports the
curated shots to `docs/images/gallery`.
