# Dedicated-server API compatibility

Minecraft 1.12.2 and Forge expose a combined development API. Forge removes
classes, methods and fields annotated `@SideOnly(Side.CLIENT)` from a dedicated
server. Code can therefore compile, render correctly and pass an integrated-server
check while failing on a dedicated server with `NoSuchMethodError`,
`NoSuchFieldError` or a class-loading error. `world.isRemote` identifies the logical
side; it does not make a client-only API available to a physical server.

## Bounds corrections in 2.4

The two-vector `AxisAlignedBB(Vec3d, Vec3d)` constructor and `AxisAlignedBB.getCenter()`
are client-only in this Minecraft version. Shared code now uses the six-coordinate
constructor and explicit coordinate midpoints instead. Both preserve the original
normalization, coordinates and geometry.

| Shared path | Corrected API use | Affected behavior |
| --- | --- | --- |
| `TileEntityCanopy.geometryBounds` | Two-vector constructor | Canopy collision and selection, including existing saved Large Front Canopy Glass |
| `WaterloggedParts.volume` | `getCenter()` | Contained-fluid volume and physics sampling |
| `TileEntityRampController.lowerLandingStep` | `getCenter()` | Escalator passengers stepping onto a fixed lower landing |
| `TurntableCollision.intersects` | `getCenter()` | Rotated turntable clearance against terrain |

Canopy bounds are transient and rebuilt from the saved shape and pose. Loading an
existing canopy in 2.4 uses the corrected calculation automatically. No world-data
conversion or block removal is required for this error.

## Verification

Run the bytecode check with Java 8:

```sh
./gradlew testDedicatedServerLinkage --no-daemon
```

The check also runs during `check` and `build`. It examines shared compiled classes,
resolves inherited API members, and checks method calls, fields, type instructions,
method/field descriptors and lambda implementation handles against side annotations.
Two negative controls exercise rejection of the original canopy constructor and
`getCenter()`; the six-coordinate constructor must remain accepted.

Client packages, annotated client methods and Minecraft client callbacks are
excluded from server execution checks. The explicitly reviewed boundaries are
Forge client GUI callbacks, tile update packets, the two handlers registered for
`Side.CLIENT`, rendering light snapshots, and two sampling calls guarded by
`world.isRemote`. Synthetic lambdas inherit the client context of their enclosing
callback. New unreviewed client references fail the build.

The focused live canopy suite also recreates saved canopy owners from NBT without
replaying placement, then compares fresh and reloaded ordered collision boxes for
every canopy design in four horizontal facings:

```sh
bash testclient/test_viewscreen.sh --focus canopies
```

This static check addresses physical-side API availability. It does not prove the
absence of arbitrary gameplay bugs, reflective API mismatches, optional-mod version
changes, mismatched installed builds or coremod transformations. A dedicated-server
runtime remains necessary for release validation; an integrated server includes
client classes and cannot establish that boundary by itself.

## Load and recovery boundaries

Tile channel registration and reader/Autopilot actions run through
`DeferredTileLoad` after chunk tile iteration. Before executing a callback, the
queue verifies that the tile is still loaded, valid and installed at that position.
The load-safety check guards tile queries and mutations during `onLoad`, verifies
installed and replaced callback identities, and injects view-construction failures
partway through saved vehicle and turntable loading. A failed load clears the live
snapshot and retains the original NBT for the next save. Non-finite canopy poses
are normalized and out-of-range footprint offsets are rejected without integer
absolute-value overflow.

A retained vehicle recovery journal now checks current blocks and tile contents
before changing the world. Changed inventories or replacement blocks block
recovery and leave the journal intact. An unrelated entity with the journal's UUID
also blocks recovery. This is intentionally conservative: automatic recovery must
not replace newer player contents with an older rollback snapshot.

Run the focused invariant checks with Java 8:

```sh
./gradlew testDedicatedServerLinkage testLoadSafety testNetworkBoundaries --no-daemon
```

For a physical-server smoke check, supply an installed Forge server JAR with its
libraries, the Java 8 executable and the standard mod artifact:

```sh
python3 testclient/test_server_startup.py --forge FORGE_SERVER_JAR --java JAVA8 --jar build/libs/vandorlabs-2.4.jar
```

This creates an isolated world under `build/`, places four front-canopy facings,
an opening canopy and a waterlogged wall, runs nearby entity ticks, saves, then
restarts the same world. It does not load a player's existing world. The log records
the artifact hash. This focused check supplements `verifyRelease` and the full live client suite.

## Communication changes and remaining opportunities

Cockpit rendering in 2.4 separates visual time from server world
time. An integrated server can hide this distinction because both clocks normally
advance together. On a remote server, time synchronization can skip or rewind an
animation sampled from an absolute saved start tick. Cosmetic screen/seat movement
and display playback now use a pause-aware client clock. Motion survives duplicate
settings packets and replacement vehicle tiles without extra network messages;
server collision and saved timelines remain unchanged. `testCockpitVisualMotion`
and the focused cockpit live check cover these boundaries.

Vehicle state messages use 82 payload bytes in 2.4, compared with the measured
105-byte generic envelope in 2.3: 23 bytes, or approximately 22 percent, less per
state update. Position, vertical motion, speed, steering, yaw, server tick, hover
mode and input acknowledgement retain their precision and order. Snapshot-only
fields are omitted from state messages. Client and server must use matching 2.4
builds. This is a payload measurement, not a measurement of total TCP traffic,
latency or frame rate.

Packet checks cover all vehicle message types, exact state round trips, every
truncated state length and non-finite movement. Existing handlers marshal world
work to the main thread and use container, reach, permission or vehicle ownership
checks. Snapshot fragments have bounded allocation sizes and request throttling;
worker queues are bounded and receive copied data rather than mutable worlds.

Further opportunities require separate multiplayer measurements: avoid redundant
controller delivery when the driver is already in an entity's tracking set; avoid
no-op flight messages from passengers; and budget large snapshot bursts across
ticks. Vanilla coalesces many block notifications, so counting setter calls alone
is insufficient evidence of redundant transmitted packets. These paths retain
their existing delivery behavior in this change.

The audit also checked the opposite physical-side direction across the compiled
mod and found no references to APIs annotated `Side.SERVER`. Reflection and
third-party coremod transformations still require runtime compatibility testing.

## Test runtime and coverage

`verifyRelease` is the required release regression gate. `test_viewscreen.sh --full`
additionally produces the broad documentation gallery. They have different costs:
the wall/glass join gallery contains 144 views and waits at least 60 simulation
ticks for each view, a nominal 432 seconds before rendering and scene construction.
The full-suite analyzer script currently does not invoke the separate
`analyze_diagonal_join_matrix.py` archive comparison.

Exhaustive geometry, UVs, normals, collision, saved-data and packet matrices belong
in non-rendering checks. Keep rendered checks for lighting, transparency, culling,
GUI layout and representative integration failures. Replacing those rendered
checks with geometry alone would reduce coverage.

The client supports `vandorlabs.reproShotShards` and `vandorlabs.reproShotShard`;
the Windows launcher exposes them as `--shot-shards` and `--shot-shard`. A future
parallel full-gallery runner must isolate each worker's world, preserve stateful
scene groups, merge manifests without missing or duplicate scenes, verify the same
artifact hash in every worker, and run all relevant image analyzers on the merged
output. Sharding alone is not evidence of equivalent coverage or a measured speedup.
