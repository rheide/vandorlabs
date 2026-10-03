# Additional bundled materials

These 33 user-provided PNGs were imported from the six `moretextures` folders:
Computing (3), Fuel (2), Hull (13), Power (3), Trapdoors (8) and Windows (4).
Original PNG bytes and dimensions are preserved; filenames are normalized for
Minecraft resource paths. `catalog.json` maps each file to its label and category.

The standard build installs these textures after the default texture pack.
The original texture archive remains the validation reference for the default
pack. This pack does not require a separate JAR or filesystem texture setup.

`tools/import_additional_textures.py SOURCE_DIRECTORY` imports category folders
and appends new manifest entries without reordering existing ones. Run
`tools/gen_unified_texture_catalog.py` afterwards to update the shared picker.
The imported choices follow all previously bundled choices, preserving saved
material indices.

Cyan-lit Armored Sci-Fi Hatch-4 is the default finish for new normal and diagonal
trapdoors. Existing saved material choices remain selected.
