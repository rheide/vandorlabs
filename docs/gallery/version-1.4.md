# Version 1.4 alpha

Version 1.4 reduces repeated rendering, collision and redstone work in builds
containing many programmable blocks. Existing saved materials, geometry and
configuration remain compatible.

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
