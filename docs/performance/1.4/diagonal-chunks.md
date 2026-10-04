# Experimental diagonal wall chunk rendering

The current `1.4-alpha` build corrects the first chunk-rendering preview, whose vertex layout produced
corrupted triangles through the vanilla block renderer.

The preview moves static Programmable Diagonal Wall surfaces into
Minecraft's chunk buffers. Interior walls no longer submit their tile mesh every
frame. Their geometry is rebuilt when the chunk mesh becomes dirty, using an
immutable snapshot of the wall settings, neighboring geometry and light value.

Chunk geometry is used when conservative bounds, including possible neighbor
joins, fit inside the wall's 16-block section. Full-height walls can use its top
and bottom levels; shallow walls can use horizontal boundaries. Actual
cross-section overhangs retain their tile renderer and expanded visibility
bounds. Diagonal Portholes, glass and moving trapdoors retain their existing
renderers.

The chunk path reuses the existing diagonal surface emitter. It preserves
material UVs, packed normals, uniform owner lighting and two-sided surfaces.
The cutout layer preserves texture alpha testing and atlas mipmaps. Geometry
update packets invalidate neighboring meshes as well as the changed wall.
Collision, selection, saved settings and server behavior are unchanged.

## Vanilla renderer compatibility

The first preview used a tile-renderer vertex layout containing both a lightmap
and normals. Forge's lighting pipeline converts that layout, but the vanilla
block renderer copies the packed vertex array directly into its block buffer.
The extra word per vertex therefore corrupted positions, material coordinates
and colors. Walls using the boundary fallback were unaffected.

The corrected renderer uses Minecraft's standard baked-model ITEM layout. Forge
can read its normals, and vanilla can copy its packed vertices and replace the
last word with lighting. No rendering configuration change is required.
The regression suite passes all 960 cases through the actual vanilla flat
renderer and checks the resulting positions, UVs, colors and lightmap values.
The original preview fails this check before the fix.
The corrected layout also passed the 39-capture focused live gallery with
`forgeLightPipelineEnabled=false`, exercising actual vanilla chunk rendering
and the shared gameplay/GUI checks. With the Forge lighting pipeline restored,
all four paired tile-versus-chunk image comparisons passed as well: at least
99.9992% of pixels were within 3/255 per channel.

## Validation and measurement

The non-rendering checks compare 576 combinations of facing, slope, width,
height, fill and neighboring geometry against the tile surface emitter. They
check positions, UVs, normals, reversed faces, section-boundary routing,
uniform lighting and neighboring render invalidation.

The Java 8 standard build, non-rendering suite and focused live gallery passed.
The gallery produced 39 fresh captures, including actual chunk rendering for
half-height walls, fills and inside/outside corners, and completed its shared
gameplay and GUI checks. Full-gallery pixel analyzers were not rerun for this
preview. Hardware shader acceptance remains pending.

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

The original Forge-pipeline paired image comparisons passed for all four fixtures: at least 99.9992%
of pixels were within 3/255 per channel. These timings describe the original preview with Forge's lighting pipeline enabled. The full measurements, including
vanilla controls, are in [diagonal-chunks.csv](diagonal-chunks.csv).

| 64-wall fixture | Tile submission median (ms) | Chunk submission median (ms) | Reduction |
| --- | ---: | ---: | ---: |
| Half width | 0.414 | 0.200 | 51.6% |
| Full width | 0.399 | 0.195 | 51.1% |
| Shallow | 0.404 | 0.191 | 52.6% |
| Clipped | 0.483 | 0.224 | 53.7% |

Per-frame allocation fell from 15,392 to 32 bytes in each fixture, and the
number of submitted tile renderers fell from 64 to zero. This is additional
to the earlier 1.4 alpha changes, which reduced diagonal tile submission
allocation from 35,360 to 15,392 bytes in the original 64-wall fixture.

The new initial mesh build took 3.2–5.3 ms for these 64-wall fixtures. The
reference build column excludes tile mesh construction and therefore is not
a like-for-like measure of total geometry-building cost.

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
player testing.

For measured topology and containment opportunities, see the
[mesh and boundary investigation](diagonal-survey/README.md).

## Coplanar mesh merging

Compatible faces are merged before quad packing during chunk construction.
Materials, normal values, shared edges, planarity and a common affine UV mapping
must agree. The union must remain a convex quadrilateral with unchanged area.
UV clamps, non-affine mapping and geometric bends remain split; each retained
surface still has an explicit reversed face.

The 960-case geometry suite passes with 45,440 original two-sided quads reduced
to 22,528 merged quads (50.4% fewer). Simple unfilled walls use 20 rather than
44 quads. Independent triangle sampling checks surface coverage, interpolated
UVs and normals in both directions; area and exact back-face attributes are
checked separately. Both original and merged meshes also pass through the
vanilla block renderer's packed-buffer checks. These counts do not establish
a hardware frame-time improvement.

## Contained section boundaries

The chunk model and tile pass share one numeric routing predicate. It uses the
wall's facing, inversion and geometry settings without neighbor scans or mesh
construction in the render loop. Clipping can only shrink the conservative
bounds, so neighbor changes cannot create an unaccounted section overhang.

| Shape | Original eligible positions | Current eligible positions |
| --- | ---: | ---: |
| Half width | 2,744 / 4,096 (67.0%) | 3,360 / 4,096 (82.0%) |
| Full width | 2,744 / 4,096 (67.0%) | 3,136 / 4,096 (76.6%) |
| Shallow | 2,744 / 4,096 (67.0%) | 3,840 / 4,096 (93.75%) |

These are counts of possible local positions, not percentages of an actual
structure. Full-height walls retain space for horizontal corner arms; shallow
walls retain space for the protruding vertical end. The regression suite checks
the bounds of emitted geometry at section edges across all 960 fixtures,
negative-coordinate routing, matching model/tile decisions and packet-driven
geometry transitions.

The paired 64-wall boundary fixtures cross the top/bottom section levels; the
shallow fixture also starts on horizontal section boundaries. Half/full-height
fixtures now submit zero tile renderers. The shallow fixture submits eight
overhanging tiles and places the other 56 walls in chunk geometry. Per-frame
allocation is 32 and 1,952 bytes respectively, versus 15,392 bytes for all 64
walls using the original tile path. See the
[paired merged/boundary measurements](diagonal-merged-boundaries.csv).
All seven paired interior/boundary fixtures pass image comparison through both
the vanilla and Forge lighting pipelines: more than 99.99% of pixels differ by
at most 3/255 per color channel.
