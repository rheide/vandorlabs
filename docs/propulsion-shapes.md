# Thruster shapes and joined assemblies

Rocket Thruster, Ion Drive, Plasma Vent and Impulse Engine support Block, Hexagon and Wedge shapes in their configuration dialog. The Shape option retains its existing Creative requirement.

A complete joined 2×2 Block assembly converts into one joined 2×2 Hexagon. Select Shape on any member to convert that complete square. Returning to Block converts the same complete group. The original physical settings tiles, channels, particle selections, signal settings and housing materials remain unchanged. Shape applies immediately; Done closes the dialog without resubmitting unrelated settings when only Shape was changed.

Adjacent squares may share an edge without becoming one malformed assembly. For example, two disjoint 2×2 squares offset by one row join independently even though the combined rows contain two, four and two blocks. Grouping uses the same deterministic complete-square partition from every member and every facing. A leftover single block, incomplete rectangle or oversized component prevents a partial join. Block squares retain their existing size limit; native joined Hexagons support 2×2 squares. Neighbors must have matching facing, Join setting and operating/particle mode.

Changing the shape checks every selected member before writing. If a group is unloaded or a member cannot be edited, no shape changes. A failed write restores the original shapes and settings. Native Hexagon artwork is clipped across its four cells without internal walls; solo Hexagons retain their original model and textures.
