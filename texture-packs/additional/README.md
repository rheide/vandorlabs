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

## Storage sets

Ten user-supplied horizontal top/side/front sheets were imported with
`tools/import_storage_textures.py SOURCE EXPORT`. Each square panel is reduced
on a 70px grid using area averaging, lightly sharpened, and enlarged with
nearest-neighbor sampling to exactly 140×140. This preserves the original
artwork while giving it crisp two-pixel steps. The original sheets are unchanged.

The `Storage` category uses the front panel for each picker thumbnail and for
ordinary programmable surfaces. Programmable Storage uses the accompanying
`top` and `side` entries, with the top also used underneath. Cabinet is first
alphabetically and is the default storage set. Catalog IDs are appended to
preserve existing saved material indices.

Storage PNGs remain 140×140 on disk. The shared atlas loader pads them internally
with edge pixels and bounds their UVs to the artwork, supporting Minecraft
mipmaps without stretching or changing the exported files.
