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
