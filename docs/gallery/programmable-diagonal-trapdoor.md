# Programmable Diagonal Trapdoor

A movable panel aligned with Programmable Diagonal Walls. Choose the shared categorized
material catalog and redstone settings as [Programmable Trapdoor](programmable-trapdoor.md),
with Rotating or Sliding movement and no frame or hinge hardware.

## Geometry and placement

The three shape modes match the diagonal walls:

| Menu option | Wall shape | Leaf clearance |
| --- | --- | --- |
| Half width / tall | Half-width, full-height | 1px on each surface |
| Full width / tall | Full-width, full-height | 1px on each surface |
| Full width / shallow | Full-width, half-height | 1px on each surface |

The walls are 4px thick in their depth coordinate; the inset leaf is 2px thick.
Plain items adopt the clicked diagonal wall or diagonal trapdoor's geometry.
Configured items retain their geometry. Facing, clicked edge and ceiling
placement follow the existing wall rules, including upper shallow panels.

Shift-right-click in creative mode, or right-click with the Configurizer, to
select texture, width, height, movement, slope/band inversion, trigger and channel. Width and height have separate controls: switching Half/Full width on a tall combined group updates every member together. Half-height geometry is always full width.
Geometry copies between diagonal walls and trapdoors through the Duplifier's
**Diagonal Geometry** switch. Fill settings do not apply to trapdoors.

## Motion and groups

Sliding first lifts the leaf clear of a solid continuation wall, then slides along the surface's width, leaving roughly one pixel visible in its original block. Tall leaves lift in their depth coordinate; shallow leaves lift vertically. Rendered vertices, selection and collision use the same motion. Rotating leaves retain the same one-pixel clearance at the side edge. Rotating swings 90 degrees around a sloping side edge. Adjacent
compatible leaves pair and open toward opposite sides without reversing their
closed surface. Compatible leaves have the same shape and matching orientation, opposite slopes in neighboring rows (V-shaped assemblies), or the reversed
facing/inversion that continues the same wall plane.

A complete 2×2 surface group opens together in every placement order. Tall
groups span two blocks across and two vertically; shallow groups span two
across and two along their footprint. The two columns open outward. Groups
retain their links on save/reload, and rebuilding a broken square restores the
four-leaf group. Complete rectangular surfaces through 8×8 open together, including 5×2. Full-width tall panels also connect across a one-block vertical plus one-block depth offset. Larger rotating halves share their outer hinge. Tall panels prefer upward opening for either slope.

Right-click any member to toggle the loaded group. Physical or virtual redstone
at any member controls the whole group. Configuration and copying update the
group together, subject to edit permission on every member. Switching between
tall and shallow geometry removes the previous links; place members in the new
layout to form the new group. No unloaded chunk is forced to load.

## Texture layout

**Texture: Tile / mirror** uses one-block-wide, two-block-high door art on tall surfaces and two-block-long door art on shallow surfaces. Alternating columns mirror left/right, so a 2×2 surface shows two doors rather than one image stretched across both columns. This works with built-in door artwork and Custom vanilla/mod `BlockDoor` items, retaining separate upper/lower sprites. **Texture: Fit** stretches one complete image over the connected group. Ordinary block art tiles once per cell. The four thin edges always use standard native door-edge artwork. Layout is saved in worlds, configured items and Duplifier settings.

## Crafting and validation

Combine a Programmable Trapdoor with an Industrial Alloy Ingot, in any crafting
grid, to make one Programmable Diagonal Trapdoor. Drops and pick-block retain
settings; opening-side and group links stay in the world.

Non-rendering checks cover wall placement alignment, one-pixel insets, rigid
motion, mesh normals/UVs/lightmaps, all square placement orders, persistence,
redstone, copying and recipe matching. Hardware client and Complementary
Unbound 5.6.1 visual checks remain pending. Live software-rendered regression captures validate the documented scenes.
