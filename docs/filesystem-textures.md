# Filesystem textures

Vandor Labs scans PNG textures once during mod startup. The textures appear in the same categorized picker used by programmable blocks, trapdoors, doors, lights, static screen/control surfaces and programmable armor. Armor retains its separate slot-filtered **Armor** category.

## First launch

Start Minecraft with the mod installed. Inside that Minecraft instance's directory, the mod creates:

```text
config/vandorlabs/textures/
└── Example/
    └── sample_panel.png
```

When there are no category folders yet, the mod adds the Example folder and a small panel texture. Existing category folders and files are preserved. Dedicated servers can create these folders too; they do not need a graphics context.

## Add categories

Put your PNGs inside folders, then restart Minecraft:

```text
config/vandorlabs/textures/
├── Bridge/
│   ├── blue_panel.png
│   └── navigation.png
├── Engineering/
│   ├── red_panel.png
│   └── Panels/
│       └── maintenance.png
└── hull.png
```

The picker shows **Bridge**, **Engineering**, **Engineering/Panels** and **Filesystem** categories. Files directly in the texture root belong to Filesystem. File names become labels; underscores become spaces. Each entry has a thumbnail. Expand a category to select its texture.

Use PNG images up to 2048 pixels in each dimension. Square textures are useful for ordinary blocks; rectangular images are supported for full surface artwork. Start with 16×16, 32×32 or 64×64 images. The atlas pads rectangular images internally. Each file is one static texture; animation metadata is not imported. Invalid images are skipped with a startup log message.

Adding or renaming a file requires a restart because the selection list is discovered on mod load. F3+T reloads image contents for entries already discovered. Optionally launch with `-Dvandorlabs.textureDirectory=/absolute/path/to/textures` to use another directory.

## Multiplayer and missing textures

Client and server texture folders are allowed to differ. The mod saves and sends a stable identifier derived from each PNG's relative path, rather than its position in a local list. Adding another file therefore does not change a saved selection.

To see the same artwork, install the same PNG at the same relative path on each client. The server does not distribute images. The server can retain a selected texture even when it has no local copy. A client missing an entry displays the default dark wall panel and keeps the saved choice; installing the file at the original path and restarting restores the artwork. If an image already registered in the atlas disappears or becomes unreadable during F3+T, a neutral dark panel is used until a successful reload.

Keep relative paths and capitalization consistent between machines. Renaming a file creates a different texture identifier. The mod detects local identifier collisions during discovery and skips the conflicting entry with a log message; rename that file if this occurs.

Texture selection changes appearance. It does not carve collision holes from transparent pixels or change the block's geometry.

The default Example category is hidden from pickers when it contains only
`sample_panel.png`. Adding another PNG to Example reveals the category after
a restart; existing saved sample selections remain valid.
