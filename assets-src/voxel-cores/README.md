# Imported Core models

These nine JSON files are the unmodified Voxel Machines block models supplied
for the Core redesign. Their exact PNG atlases are stored under
`texture-packs/additional/assets/vandorlabs/textures/blocks/voxel_cores/`.
`mapping.json` records their existing Vandor Labs design IDs; no registry IDs
are added or changed. Plasma Drive maps to `longburn_drive`, whose display name
is Maxwell Drive Core. The two navigation Core models are not replaced.

`tools/import_voxel_cores.py <voxel-machines-project>` imports the supplied
models/atlases, emits portable cuboid and face-UV data, and writes the Source
block-model adapters. Only namespaces and inventory display scale change in
the runtime JSON adapters. The portable importer resolves each model's occupied
quarter-model-unit cells, emits exposed surfaces, and prints a stable bottom-up
voxel prefix during fabrication. Partial top faces use their cuboid's own UVs.
Both platform renderers and Dynmap consume this shared mesh.
