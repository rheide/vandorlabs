# Diagonal wall joins

This page describes the geometry in `1.3-alpha` and the proposed smooth-join design. The proposed geometry is **not implemented** in that tag.

## Why a stepped continuation shows a seam

A Full width / Full height diagonal wall is four texture pixels thick. Across its sixteen-pixel height, its near surface moves twelve pixels in depth; its far surface is four pixels farther out. Keeping both surfaces inside the owning cell leaves the centerline moving from depth 2 to depth 14.

When the next cell moves up sixteen pixels and over sixteen pixels along the slope, its bottom centerline starts at depth 18. The centerlines differ by four pixels at their shared height. This produces the visible thickness-sized step. Translating every wall by the same amount preserves that difference; a fixed offset alone cannot make a long run continuous.

Coordinates below use height and slope depth, in texture pixels, for a normal slope. The other facings and reversed slopes are rotations/reflections of the same geometry.

| Geometry | First wall center at its top | Next wall center at its bottom | Difference |
| --- | ---: | ---: | ---: |
| Current: center depth = 2 + 12 × height / 16 | 14 | 18 | 4 |
| Proposed: center depth = height | 16 | 16 | 0 |

The renderer and collision slices share the current twelve-pixel span. Placement's plane-matching calculation also uses that span, so changing only the visible model would leave placement and hitboxes inconsistent.

## Proposed smooth geometry

Use a centerline that reaches the actual cell corners and place half the wall thickness on each side of it. A full-height, full-width wall would span depth −2 to +2 at its bottom and 14 to 18 at its top. The next stepped wall has the same 14-to-18 interval at its bottom: both broad faces meet on the same planes. The overhang is **half the thickness**, two pixels, rather than half a block.

A shared geometry definition should drive rendering, collision slices, selection bounds and plane matching. Full-height half-width slopes would travel eight pixels instead of six; half-height full-width slopes would also travel eight pixels, with an upper band offset of eight instead of six. Their continuous lattice steps differ from the full/full mode: a half-width tall run advances two cells vertically per one depth cell, while a shallow run advances two depth cells per one vertical cell.

Matching wall-to-wall joins should omit their internal end caps. Perpendicular corners, inside/outside fills and diagonal portholes need to use the same planes. Diagonal trapdoors currently inset their leaves inside the old wall planes, so matching a smooth wall also requires a corresponding leaf geometry while retaining its animation and clearances.

## Placement and occupied neighbors

Each wall should retain exactly one owning block and tile. The overhang must never place helper blocks, reserve adjacent cells, replace their contents or require them to be empty.

For a continuation from `(X,Y,Z)` to `(X+1,Y+1,Z)`, only the destination cell must accept the new wall. Blocks already at `(X+1,Y,Z)` or `(X,Y+1,Z)` must remain valid and untouched. Part of the overhang can lie inside an existing solid block; the solid block simply hides that part of the wall.

Placement should infer the continuation from compatible loaded diagonal neighbors even when the clicked support is an ordinary block. It must also preserve explicitly configured item settings and normal edit permissions.

Minecraft's ordinary cell-based ray tracing and collision queries can miss a surface extending out of its owner. Selection and collision therefore need a bounded, loaded-only search for nearby owners, using the actual wall shape. This follows the ownership approach already used for Next block trapdoors, without claiming additional cells or loading chunks.

## Existing builds and validation

A saved Smooth joins option, off when absent from old NBT, would preserve existing wall shapes. It should persist in world saves, configured items, pick-block and the Duplifier's diagonal geometry settings. A global geometry replacement is also possible, but changes existing walls, corners, filled shapes and matching trapdoors.

Acceptance checks should include all facings and reversed slopes, tall and shallow modes, multi-cell runs, perpendicular corners, fills, portholes, matching trapdoors, and both placement orders. The occupied-neighbor example above needs real item placement on client/server, plus selection and collision checks on the exposed overhang. Compare before/after screenshots of plain materials and patterned textures to verify both face continuity and UV seams.
