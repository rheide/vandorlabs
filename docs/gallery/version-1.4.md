# Version 1.4 alpha

Version 1.4 turns existing programmable screens, inputs and consoles into
editable redstone control panels, adds multi-channel controls and selectable
propulsion particle intensity, and
reduces repeated rendering, collision and redstone work in builds containing
many programmable blocks. Existing saved materials, geometry and
configuration remain compatible.

## Turn existing displays into redstone control panels

**Choose `Redstone...` in an artwork picker to build a panel of labelled
controls on the blocks you already use.** It appears alongside `Custom...` and
opens the row editor; separate redstone blocks and crafting recipes are no
longer needed.

This works on **Programmable Screen**, **Programmable Diagonal Screen**,
**Programmable Input**, **Programmable Console**, **Programmable Half-Input**,
**Programmable Half Console** and **Programmable Diagonal Half Console**.
A console's screen and input deck can have independent control panels.

1. Shift-right-click in Creative mode, or use the Configurizer, to open the
   block's dialog.
2. Open the artwork picker for the surface you want to change and choose
   `Redstone...`.
3. Edit the header, add or remove rows, and give each row a short
   label and comma-separated channels. Press Done to save.

**Click anywhere on a row** to toggle every channel in its list. Green means
**all** listed channels are powered. A partially powered row appears inactive;
clicking it activates its complete channel bank. Empty channel lists remain
inactive. Labels and headers are limited to the visible screen width, so new
text is shown in full. Full-height panels support eight rows. Half-height
surfaces support four rows at the same text proportions and omit the header
both on the block and in the editor.

Rows can overlap, and switches, levers and buttons can control the same
channels. Physical inputs continue supplying power independently of a panel's
latch state. Choosing ordinary artwork disables that surface's controls while
retaining its saved row definitions for later use.

Headers, rows, channel lists and latch states survive world saves. Pick-block
copies the configuration and housing without copying live latch state. The
Duplifier's **Redstone Screen Items** option copies the complete panel
configuration, including both surfaces of a console. Copying to a half-height
surface retains the first four rows. Older saved Duplifier
masks enable the new option by default; it can still be disabled deliberately.
Editing labels does not interrupt an active channel bank. Only loaded rows
participate in the network, and unchanged highlights avoid redundant updates.

Programmable Diagonal Screens also have precise wedge-shaped picking,
collision slices and a housing-shaped selection outline. The empty half of
the surrounding cube no longer obstructs movement or interaction. Their
inventory model follows the same solid wedge as the placed block.

The former **Programmable Viewscreen** is now named **Programmable Screen**.
Its registry ID is unchanged, preserving existing blocks, items and recipes.

## Propulsion particle intensity

Propulsion dialogs now offer **Off**, **Light**, **Medium** and **Heavy**.
Existing On settings become Light, preserving the original particles, count,
positions and direction. Medium emits three times as many particles; Heavy
emits six times as many. Both retain the original core stream and add particles
across a wider area around its center. Connected assemblies keep one emitter
at their shared center. Intensity survives saves, configured items, shape
changes and Duplifier copying.

## Redstone channel lists

Channel fields now accept comma-separated positive numbers, such as `12, 25, 40`.
Use `0` or an empty field to disconnect the block. Spaces are allowed; repeated
numbers are removed and the saved list is sorted. Each block supports up to
64 channels. Incomplete lists are highlighted and cannot be submitted.

Switches and levers toggle all listed channels together. Buttons power all of
them while pressed. Doors, lights, ramps and other consumers respond when
**any** listed channel is active, using their existing redstone trigger mode.
An overlapping list does not relay power between unrelated channels. A switch
with only some channels latched on appears off; clicking it turns all its
listed channels on.

Existing single-channel worlds and configured items retain their settings.
Save/load, picked items, Duplifier channel selection and joined assemblies
preserve the full list. Channels still operate only in loaded chunks; they do
not force chunks to load.

## Diagonal walls

Static Programmable Diagonal Walls use Minecraft's chunk geometry when their
surfaces fit inside the owning section. Their shapes, two-sided faces, texture
coordinates and lighting are preserved. Walls that overhang a section keep
their existing renderer, as do diagonal portholes and moving trapdoors.

Compatible coplanar faces are combined while building the mesh. Simple walls
use 20 quads instead of 44; the broader geometry suite uses 50.4% fewer quads.
This reduces stored geometry and repeated submission work. Geometry and
material changes still refresh the affected surfaces.

Programmable Walls and Porthole Walls also remain visible beyond the previous
64-block tile-renderer cutoff, matching diagonal walls. Visibility remains
limited by loaded chunks and the camera view.

## Other performance improvements

- Reuse programmable block and slab render-state calculations and bounded
  screen texture identifiers.
- Reduce repeated allocation in wall, door, light and screen rendering.
- Skip offset-trapdoor collision scans in loaded sections without trapdoors.
- Revalidate known redstone power sources before scanning other channel
  members, while retaining event-driven, loaded-chunk-only operation.
- Release world-specific light and porthole caches when leaving a world.

See the [performance measurements](../performance/1.4/README.md) and
[diagonal rendering notes](../performance/1.4/diagonal-chunks.md) for methods,
results and validation limits. CPU and software-renderer measurements do not
predict hardware FPS or establish shader compatibility.
