# Trapdoor native geometry and rendering contract

Regular and diagonal leaves share resolved, world-free `TrapdoorLeafPose` facts.
The host validates membership and resolves hinge/travel, opposing-cover overhang,
diagonal opening direction and lift before publishing the facts. Material and
channel choices do not enter the geometry cache key. Existing portable corner
kernels write into caller-owned storage for cosmetic motion.

`NativeTrapdoorGeometry.snapshot` caches immutable authoritative closed/open
endpoints within an 8 MiB accounted budget. The snapshot owns its corners,
bounds, collision boxes and precomputed face-ray coefficients. It exposes a
cached bounding-box `outlineShape` and a twelve-edge `outline` stream; neither
builds authored frame geometry. `leafEdges` is available separately for the
rigid geometry. Vanilla selected bounds use the bounding box.

Regular collision uses the released AABB. Diagonal collision uses sixteen
slices and sixteen across-columns when open and rotating. A frame's visual
aperture does not change the released leaf collision or ray surface.

Block and offset rays retain separate arithmetic. Regular block rays translate
the segment into local coordinates before tracing; regular offset rays trace
world-space bounds. Diagonal rays retain their exact six-face order, tolerances
and legacy nearest-axis tie rule. Named `blockTrace` and `offsetTrace` methods
write into caller-owned hit storage. Modern `AABB.clip` is not substituted.

`exportTrapdoorGeometryReference` freezes 768 actual Forge block/tile profiles,
including group pivots, opposing-cover seams, both sliding styles, diagonal
inversions and large coordinates. It captures 59,136 collision boxes and 24,576
block/offset ray pairs. There are 72 distinct regular block/offset results.
Canonical UTF-8/LF hashes preserve the baseline on Linux and Windows.

The focused native check compares every captured corner, box, hit point and
face exactly. Its dense hover probe performs 4,096 cached snapshot lookups and
49,152 outline-edge emissions without allocation or mesh tessellation. The
initial wrapper-key/recency-queue cache allocated 196,608 bytes for that probe;
the byte-bounded endpoint-pair cache removes those hit-path allocations.

The renderer foundation separates immutable clipped affine layouts from live corner poses.
`TrapdoorSurfaceLayout` owns clipped triangles and custom Door half selection;
`TrapdoorFrameLayout` owns frame strips and preserves the released two-stage affine
operation order. Both emit through primitive sinks with caller-owned scratch arrays.
`TrapdoorArtworkCoordinates` preserves the independent saved layout and tile flags,
Door artwork mapping, group extents and world-coordinate rounding.
`NativeTrapdoorMesh` streams through the active vertex consumer, applying atlas UVs,
explicit white RGBA, light and overlay without retaining frame state. It is inactive
until the host renderer bridge is supplied. `TrapdoorRigidEdges` streams the remaining native metal faces with the released
regular/tall/shallow thickness UVs. Material selection remains an orchestration
responsibility; no translucent pane is invented.

Focused evidence includes 4,800 released surface/frame profiles and 588,800 vertices
with byte-identical shared output, plus 36,864 artwork-coordinate profiles with exact
double-bit agreement. A warmed 4,096-draw surface probe emits 393,216 identical vertices:
the released cache-hit path allocates 98,304 bytes and the extracted path allocates zero.
This is an emission allocation measurement, not a frame-rate claim.

The small native title check covers regular, sliding, mirrored tile and all diagonal
modes, asymmetric frame apertures and omitted joined rails across resource reload.
Its frame/full-leaf negative control tests the open center. It uses explicit asymmetric
border fractions to isolate strip behavior; active resource-pack border discovery and
world-host acceptance are separate requirements.

`NativeTrapdoorVisualClock` samples one pause-aware frame clock and retains nine-tick
reversible `DoorAnimation` state by world/position, bounded to 1,024 positions per world.
Repeated observations and tile replacement preserve the sampled pose; world unload
clears the retained state. Authoritative collision uses the host's endpoint state.
