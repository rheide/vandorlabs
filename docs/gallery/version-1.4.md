# Version 1.4 alpha

Version 1.4 adds multi-channel controls and editable redstone screens, while
reducing repeated rendering, collision and redstone work in builds containing
many programmable blocks. Existing saved materials, geometry and
configuration remain compatible.

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

## Redstone screens

**Programmable Redstone Screen** and **Programmable Diagonal Redstone Screen**
provide up to eight labelled controls on one block. Shift-right-click in
Creative mode, or use the Configurizer, to open the dialog. Add or remove rows,
edit the header or select a row to edit its label and comma-separated channels, then press Done.
The Housing tab uses the existing material picker. Labels support up to
24 characters, and headers up to 32. The editor limits both to the visible screen width; oversized labels from earlier saves remain editable.

Right-click anywhere on a row to toggle its channel bank. Green means
**every** listed channel is powered. An empty list or a partially powered list
appears inactive; clicking a partially powered row activates all its channels.
Rows can overlap, and ordinary switches, levers and buttons can control the
same channels. Held buttons and other physical inputs continue to supply
power independently of the screen's latch state.

Rows and latch states survive world saves. Pick-block copies the header, labels, channel
lists and housing without copying the live latch state. The Duplifier's
**Redstone Screen Items** option copies the header and complete row definitions between either screen
shape. Older saved Duplifier masks enable this new option by default; it can still be disabled deliberately. Editing labels does not interrupt an already active channel bank.
Only loaded rows participate in the channel network. Row changes are
event-driven; unchanged highlights do not send redundant status updates.

Craft either redstone screen by combining its regular screen counterpart
with one redstone dust. The former **Programmable Viewscreen** is now named
**Programmable Screen**; its registry ID is unchanged, so existing blocks,
items and recipes remain compatible.

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
