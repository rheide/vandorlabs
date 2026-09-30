# Programmable Diagonal Trapdoor

A movable panel aligned with Programmable Diagonal Walls. Choose the same 78
block finishes and redstone settings as [Programmable Trapdoor](programmable-trapdoor.md),
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
select texture, shape, movement, slope/band inversion, trigger and channel.
Geometry copies between diagonal walls and trapdoors through the Duplifier's
**Diagonal Geometry** switch. Fill settings do not apply to trapdoors.

## Motion and groups

Sliding moves one block along the surface's horizontal width; it never moves
up or down. Rotating swings 90 degrees around a sloping side edge. Adjacent
compatible leaves pair and open toward opposite sides without reversing their
closed surface. Compatible leaves have the same shape and matching orientation, or the reversed
facing/inversion that continues the same wall plane.

A complete 2×2 surface group opens together in every placement order. Tall
groups span two blocks across and two vertically; shallow groups span two
across and two along their footprint. The two columns open outward. Groups
retain their links on save/reload, and rebuilding a broken square restores the
four-leaf group. Larger areas remain separate pairs or squares.

Right-click any member to toggle the loaded group. Physical or virtual redstone
at any member controls the whole group. Configuration and copying update the
group together, subject to edit permission on every member. Switching between
tall and shallow geometry removes the previous links; place members in the new
layout to form the new group. No unloaded chunk is forced to load.

## Crafting and validation

Combine a Programmable Trapdoor with an Industrial Alloy Ingot, in any crafting
grid, to make one Programmable Diagonal Trapdoor. Drops and pick-block retain
settings; opening-side and group links stay in the world.

Non-rendering checks cover wall placement alignment, one-pixel insets, rigid
motion, mesh normals/UVs/lightmaps, all square placement orders, persistence,
redstone, copying and recipe matching. Hardware client and Complementary
Unbound 5.6.1 visual checks remain pending; the software renderer was not run.
