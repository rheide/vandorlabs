# Experimental diagonal wall chunk rendering

Version `1.4-alpha.1` moves static Programmable Diagonal Wall surfaces into
Minecraft's chunk buffers. Interior walls no longer submit their tile mesh every
frame. Their geometry is rebuilt when the chunk mesh becomes dirty, using an
immutable snapshot of the wall settings, neighboring geometry and light value.

This first preview uses chunk geometry only when all three coordinates within
the 16-block chunk section are between 1 and 14 inclusive. Walls on section
boundaries retain their existing tile renderer and expanded visibility bounds,
so their overhanging surfaces remain visible. Diagonal Portholes, glass and
moving trapdoors retain their existing renderers.

The chunk path reuses the existing diagonal surface emitter. It preserves
material UVs, packed normals, uniform owner lighting and two-sided surfaces.
The cutout layer preserves texture alpha testing and atlas mipmaps. Geometry
update packets invalidate neighboring meshes as well as the changed wall.
Collision, selection, saved settings and server behavior are unchanged.

## Validation and measurement

The non-rendering checks compare 576 combinations of facing, slope, width,
height, fill and neighboring geometry against the tile surface emitter. They
check positions, UVs, normals, reversed faces, section-boundary routing,
uniform lighting and neighboring render invalidation.

A live focused gallery exercises actual chunk rendering for half-height walls,
fills and inside/outside corners alongside the shared gameplay and GUI checks.
Hardware shader acceptance remains pending.

The live benchmark renders the old tile path and new chunk path in the same
client run, under the same lightmap. Four 64-wall fixtures cover half width,
full width, shallow and clipped walls. Compare their images with:

```sh
bash testclient/benchmark_programmable.sh
python3 testclient/compare_diagonal_chunk_images.py <benchmark-output-directory>
```

Run the relevant gallery and numerical checks with Java 8:

```sh
./gradlew testNonRendering --no-daemon
bash testclient/test_viewscreen.sh --focus gallery_v12
```

Chunk geometry shifts work to chunk rebuilds and adds reversed faces to preserve
the old renderer's disabled back-face culling. This increases stored vertex
payload. CPU submission measurements do not establish a hardware FPS or shader
performance improvement; changing scenes, chunk rebuilds and GPU load can have
different costs.

## Player testing

Check existing diagonal walls with normal rendering and the shader pack used
for play. Compare surfaces, lighting, transparent texture holes, fills and
joins across all wall shapes. Change materials and geometry, place and remove
neighbors, and reload chunks. Include walls crossing horizontal and vertical
section boundaries, and view them from both sides and at a distance.

Hardware shader appearance and real-world frame-time improvements require
player testing before expanding this preview to boundary walls or other blocks.
