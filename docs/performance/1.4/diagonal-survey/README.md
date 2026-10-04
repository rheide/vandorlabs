# Diagonal wall mesh and boundary investigation

Source reference: `35db6e4d`. This is an offline investigation of the current chunk geometry. It does not
change the production renderer or the standard mod JAR. Candidate counts are
not frame-time measurements or a validated replacement mesh.

## Reproduce

With Java 8 selected:

```sh
./gradlew --no-daemon -I docs/performance/1.4/diagonal-survey/probe.gradle diagonalMeshSurvey
python3 docs/performance/1.4/diagonal-survey/analyze.py \
    build/diagonal-mesh-survey/meshes.jsonl build/diagonal-mesh-survey/survey.csv
```

The analyzer requires NumPy. The Java probe exports actual baked vertices,
material names and normals for 960 fixtures: four facings, both slopes, three
wall modes, four fills and ten neighbor arrangements. Results grouped by mode
and neighbor fixture are in [results.csv](results.csv).

Modes 0/1/2 are half width, full width and shallow. Neighbor fixtures are:
0 isolated, 1 solid in front, 2 solid above, 3 front corner, 4 same wall above,
5 same wall behind, 6 flat wall below, 7 flat wall above, 8 corners in front and
behind, and 9 solid beside the wall. Some arrangements have no effect for
particular modes; the sample is a fixture survey, not a gameplay distribution
or exhaustive coverage of all possible neighborhoods.

## Coplanar merging

The current emitter uses five vertical strips. The analyzer attempts to merge
adjacent quads only when they have matching materials and normals, opposite
shared edges, a common plane and one affine UV mapping fitting all eight
original vertices. It removes collinear shared-edge endpoints only if the
result is a convex quadrilateral with the sum of the original areas. It keeps
UV clamps, material seams, normal changes and non-affine mappings separate.
Geometric and attribute comparisons use a tolerance of 1e-7.
Explicit reversed faces remain present in the reported counts.

| Mesh | Existing two-sided quads | Candidate quads | Reduction |
| --- | ---: | ---: | ---: |
| Isolated, unfilled half-width wall | 44 | 20 | 54.5% |
| Isolated, unfilled full-width wall | 44 | 20 | 54.5% |
| Isolated, unfilled shallow wall | 44 | 20 | 54.5% |
| Sum across all 960 fixtures | 45,440 | 22,856 | 49.7% |

For a simple wall, the ordinary 28-byte chunk format would store 4,928 bytes
of vertex data before merging and 2,240 bytes afterward. These numbers exclude
object overhead, other blocks, driver storage and shader-specific formats.

This Python algorithm is a feasibility probe, not suitable for a render loop.
A production implementation should merge during mesh construction, preferably
by combining strip runs with known topology. It must retain exact texture
interpolation and outward/back-face behavior. Numerical surface/UV checks,
paired images through both vanilla and Forge pipelines, joins and clipping,
and hardware shader testing are needed before accepting changed topology.

## Boundary coverage

The existing routing rule excludes both ends of all three section axes, allowing
14 cubed = 2,744 of 4,096 local block positions (67.0%). Actual geometric bounds
are less restrictive for many configurations. Across the exported fixtures,
complete containment permits 3,136–4,096 positions, depending on geometry.

For isolated unfilled walls, exact containment permits:

| Shape | Current eligible positions | Geometrically contained positions |
| --- | ---: | ---: |
| Half width | 2,744 (67.0%) | 3,840 (93.75%) |
| Full width | 2,744 (67.0%) | 3,584 (87.5%) |
| Shallow | 2,744 (67.0%) | 3,840 (93.75%) |

These percentages describe local positions, not the proportion of walls in an
actual build. For example, placing every wall at a section's bottom level
currently sends the entire arrangement through the tile renderer.

The lower-risk extension is to admit boundary walls whose conservative geometry
bounds remain inside their section, retaining the tile fallback for actual
cross-section overhangs. Full-height walls never extend beyond their vertical
block interval, so their Y-boundary exclusion can be removed independently of
horizontal corners. Shallow walls have no corner arms and stay within their
horizontal block footprint; their vertical extrusion determines containment.
The final routing predicate must agree in the chunk model and tile renderer,
including during setting changes and neighbor updates. It should not construct
meshes or scan neighbors every frame merely to decide which path to use.

Moving genuinely protruding boundary geometry into chunk buffers is a separate,
higher-risk change. Minecraft culls/traverses fixed section bounds and section
visibility, rather than the tile renderer's expanded bounds. Enlarging only a
frustum box is not a complete visibility solution. Splitting overhangs between
sections also requires handling ownership, empty neighboring sections,
independent rebuilds, lighting, unloading and cross-section invalidation.

Recommended implementation order: merge compatible strip runs, extend routing
to fully contained boundary walls, then measure whether remaining overhanging
walls justify changes to section visibility or geometry ownership.
