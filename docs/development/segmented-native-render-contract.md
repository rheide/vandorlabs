# Segmented Door native rendering contract

The section transform, panel/window mesh builder and leading-edge border use
portable scalar kernels. Atlas lookup and vertex formats remain runtime adapters.
Meshes own pixel-space float records containing position, atlas UV and normal;
frame emission streams through the active vertex consumer, including its light,
overlay and normal attributes. Mesh caches must never retain tiles or worlds.

The native renderer must distinguish terrain eligibility from the closed-terrain
handoff. The released eligible panel path omits internal horizontal caps only at
exact closed/open endpoints. The feature/window path keeps its caps. Closed
suppressed columns draw their fixed border without drawing duplicate panels.
Contained fluid remains a separate chunk-model responsibility.

Window layouts use a bottom-up row index. Body texture layout uses the panel's
row within the column; window glass uses `height - 1 - row` within the resolved
window group. Saved layout IDs are Stretch 0, Tile 1 and Fit 2. Fit preserves the
released aspect-match threshold. Custom door artwork uses two half sprites and
an aspect of one half, with repeat-boundary clipping before UV interpolation.

Focused references capture actual released Forge emitters before extraction:

- `exportSegmentedMeshReference`: 960 section poses and 23,040 vertices.
- `exportSegmentedPanelReference`: 900 panel/window profiles and 48,288 vertices.
- `exportSegmentedHeaderReference`: 432 border profiles and 27,360 vertices.

The source adapters reproduce the complete frozen buffers byte for byte. Native
checks compare raw scalar positions, UVs and packed normals against those
references, and separately assert the native consumer's color/light/overlay
conventions and immutable mesh reuse. These checks do not establish placed-world
chunk handoff, shader-pack acceptance or hardware frame rates.
