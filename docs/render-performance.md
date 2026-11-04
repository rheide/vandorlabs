# Dense-build rendering in 2.8

Dense builds can spend substantial client time submitting many small tile renders and searching unrelated loaded objects. Version 2.8 reduces that work while keeping collision and saved state on the server.

## Segmented comparison

The focused comparison loads MalisisDoors 8.0.0 with MalisisCore 7.0.0 alongside Vandor Labs in an isolated Forge client. Each door contains nine columns of five sections. The reference JARs match the installed artifacts by SHA-256. Both implementations render their native geometry through the tile dispatcher in the same context. The Vandor scene includes its terrain geometry. Measurements are medians of client rendering time and thread allocation, using Mesa software rendering; they are not hardware FPS or Complementary shader acceptance.

A representative 64-door run, containing 2,880 sections, produced:

| State | Malisis | Vandor | Relative speed |
| --- | ---: | ---: | ---: |
| Closed | 17.70 ms | 1.23 ms | 14.4× |
| Open | 17.28 ms | 1.40 ms | 12.4× |
| Pose advancing every frame | 17.82 ms | 1.76 ms | 10.1× |

The advancing-pose test measures fresh animation poses, rather than repeatedly drawing one warmed pose. Allocation fell from approximately 37 MB to 14 KB per animated frame in that case. Steady open and closed rendering allocated no memory after warm-up. Small-scene ratios vary: in the same run, a single door's advancing-pose comparison was 3.9× faster. Timing depends on driver overhead, JVM warm-up, scene size and camera movement.

Closed segmented panels belong to Minecraft's chunk geometry. A column retains identical fallback panels until replacement chunk meshes finish uploading, preventing a late rebuild from leaving a hole or a duplicate animated door. Moving and raised panels share the atlas batch. Pose templates are shared; translated column vertices and lighting are reused while their inputs remain unchanged. Template and column caches have separate byte budgets, invalidate on atlas/vertex-format changes and release world references. Active OptiFine dynamic lights refresh lighting each frame.

A native server matrix separately exercises 64 independent doors. One motion command publishes 576 top-owner updates, rather than 2,880 per-section updates. Repeating an unchanged input 6,400 times emits no additional updates. Every section still retains its authoritative saved motion state. The fixture also checks distinct channels and owner replacement from saved NBT.

Native interaction checks cover ordinary placement, cross-chunk columns, a 32-section door viewed only at its header, configuration through the Configurizer and sneak-right-click, channel operation, reversal, root mining and resource reload during motion. Cached geometry is compared with reference vertex payloads for all four facings and heights 1, 5 and 32.

## Other dense paths

A 29×29 unclaimed Surface floor reduces 1,682 individual face submissions to one shared atlas submission. In the focused comparison, rendering fell from 5.43 ms to 0.92 ms, with identical pixels. Controller-owned and joined surfaces retain their established rendering path.

Consecutive compatible planar glass panes retain sorted order while sharing one submission. The 216-pane fixtures cover both orientations and all three depths, with identical pixels. Special glass finishes, corner contacts and fluids retain their existing renderer.

Stationary opaque ramp endpoints cache clipped geometry in VBOs. The byte-limited cache uses weak tile references so discarded vehicle views can be collected. A 78-cell fixture fell from 2.64 ms to 0.58 ms with identical pixels. Motion, glass, escalators and moats retain their established path. Opaque diagonal-wall fallback submissions also batch; ordinary in-section walls already use chunk geometry.

Canopy and moved-door collision queries use nearby loaded-owner buckets. They check current tile identity and never load chunks. An empty query among 6,144 unrelated loaded tiles no longer walks those tile maps. Turntable collision handling similarly avoids unrelated entity scans when no turntables exist.

## Shader rain

Fixed Canopy Glass already stops native precipitation columns. The Complementary integration scopes its skylight-derived rain-wind uniform while the camera is beneath a confirmed fixed-glass roof, then restores the previous value. Native height, roof removal and exception-safe shader-state restoration are checked. The reported visual result remains subject to manual verification with Complementary Unbound on a hardware client.

## Large carried builds

The saved-world benchmark contains a 3,325-block craft with 3,032 tiles. Its
714 flat and diagonal walls previously bypassed the carried-wall display-list
cache when shaders selected extended vertex records. Opaque wall sections now
retain VBOs with immutable formats and each component's original shader material
payload. Combined CPU/GPU vertex retention is limited to 64 MiB, with an 8 MiB
geometry payload limit per section. Light-only updates refresh retained
lightmap fields while preserving geometry, tangents and material IDs. A
64-wall comparison reduced update submission from 182 µs to 46 µs and
allocation from 134 KB to 7 KB, with exact fresh-build record parity. Resource,
format, artwork and neighbouring-geometry changes invalidate the
relevant data. Native TILE updates that preserve tile identity and adjacent
non-tile placement/removal have dedicated cache regressions. The 64-wall driver
comparison reduces 64 submissions to one, with identical pixels for all four
facings and three depths.

The craft contains no canopies, but its surrounding fixed-glass roof contributed
to repeated collision work. Fixed glass can reject a missed unit cell before
reading connection masks or constructing posed geometry. Authored variants and
all four mounts are checked against that conservative bound; legacy paired
glass retains the broad search. Fixed native glass also skips its unnecessary
client mechanism tick. Server membership checks and opening mechanisms remain
active. Loaded canopy, moved-door and offset-trapdoor queries use spatial
indices. Primitive section keys avoid boxing in hot lookups. Nearby-vehicle
queries exclude the querying craft before allocating candidates and retain exact
multiple-craft and negative-coordinate behavior.

Large, relatively flat hulls use 16×4×16 collision-cache regions. Smaller and
tall structures retain 8×8×8 regions. In the extracted world, a six-pose
forward/reverse collision comparison reduced median step CPU from approximately
3.0 ms to 2.3 ms, and allocation from 2.22 MB to 1.35 MB. The adaptive selection
produced the same query counts as the selected fixed grid. Every pose retained
its original three-axis movement result. These are CPU collision-step timings,
not frame rates. Terrain reuse ends with its synchronous physics/replay batch,
so later world changes are queried again.

Complete frame intervals also include client prediction ticks between draws.
In a paired Complementary Unbound scene, conservative fixed-glass collision
bounds and adaptive terrain regions reduced stationary median intervals from
442 ms to 257 ms; sustained movement measured 261 ms. Completed rendering stayed
near 210 ms. Sustained shader render allocation fell from approximately 7.8 MB
to 4.3 MB per frame after light-only wall refresh. This explains why reducing draw submissions alone
could leave a large shader slowdown: expensive catch-up ticks delayed the next
frame. The benchmark uses the actual saved controller and Pilot Seat, retains
old tile NBT, and requires authoritative movement during the sampling window.
Mesa software-rendered measurements establish comparisons in that environment;
hardware FPS and owner shader acceptance require the hardware client.

## Contained fluids

Contained-fluid physics uses compact runs along the most subdivided axis. A
26-case comparison against the previous Cartesian partition reduced 2,284,318
fluid cells to 19,100 with identical ownership at every original grid cell.
Voxel faces are created only when requested, preserving their previous masks and
order. Panel and door rendering already uses analytic surfaces and now avoids
constructing the unused voxel volume. This reduces both cold construction work
and retained geometry without changing stored fluid sides or opening behavior.

## Current priorities

See the [2.8 performance summary and next priorities](performance/2.8-summary.md) for the consolidated results, Vehicle Mode preparation changes and remaining measured costs.

The optional reference comparison requires the installed comparison block identifier through the JVM property `vandorlabs.referenceDoorBlock` (for example with `JAVA_TOOL_OPTIONS`). Its registry name belongs to the external mod and is not aliased by Vandor Labs.
