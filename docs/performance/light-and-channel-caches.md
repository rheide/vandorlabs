# Light rendering and channel publication costs

This Forge 1.12.2 patch builds on master
`33d789534fdf5f9f845e9dd2bded242ef2fe9acb`. Existing housing meshes, glass
batching, terrain publications and vehicle geometry caches remain in use.
The changes below remove repeated work around those caches. They preserve
geometry, texture resolution, live lighting, animation cadence, collision,
packet formats and saved data.

## Measured results

The paired headless fixtures use the previous light algorithms retained in
`ReferenceLightGroups`, and the exact baseline `RedstoneChannels.java`
compiled separately ahead of the runtime classpath. Both sides use the same
fixtures and Java 8. The source-network baseline is not an estimate based on
the new fast path.

| Work measured | Before | After |
| --- | ---: | ---: |
| Joined layouts: block-state reads, 1,024 lights over 200 ticks | 704,000 | 0 |
| Joined layouts: tile reads, same workload | 198,400 | 0 |
| Joined layouts: allocated bytes, same workload | 39,795,944 | 2,208 |
| Hidden faces: block-state reads, 1,024 lights over 200 frames | 3,123,200 | 0 |
| Hidden faces: tile reads, same workload | 1,894,400 | 0 |
| Hidden faces: allocated bytes, same workload | 37,689,600 | 864 |
| Stable channel notifications: receiver candidates | 526,336 | 0 |
| Stable channel notifications: source candidates | 262,144 | 0 |
| Stable channel notifications: receiver-bucket visits | 55,296 | 0 |
| Stable channel notifications: source-bucket visits | 21,233,664 | 0 |

The channel workload has 256 receivers, four channels and 256 unchanged
notifications. It still samples the notifying source's four levels each
time; it removes the subsequent index mutation and receiver work. Duplicate
callbacks were already suppressed in the baseline and remain zero.

One paired timing run measured the following totals. These timings are
microbenchmark observations, not whole-client frame times or TPS/FPS claims.
Work counters and parity assertions are the deterministic regression gates;
elapsed time can vary with JVM compilation and host load. Allocation totals
include the measurement fixture's overhead.

| CPU workload | Before | After |
| --- | ---: | ---: |
| Joined-layout lookups over 200 ticks | 182.962 ms | 29.080 ms |
| Hidden-face lookups over 200 frames | 414.624 ms | 12.658 ms |
| 256 unchanged source notifications | 247.042 ms | 0.616 ms |

For 1,000 ordinary vehicle state updates to one tracked driver and one
observer, the packet round-trip fixture recorded:

| Delivery metric | Before | After |
| --- | ---: | ---: |
| Total deliveries | 3,000 | 2,000 |
| Encoded Vandor payload bytes | 246,000 | 164,000 |
| Deliveries to driver | 2,000 | 1,000 |
| Deliveries to observer | 1,000 | 1,000 |

This is a 50% reduction for the driver's duplicated stream and a 33.3%
reduction across that two-viewer fixture. It excludes Forge/transport framing
and does not measure a live multiplayer connection. Additional observers
reduce the percentage saved across the whole audience.

## Connected-light layouts

`TEAnimatedScreenSelector.lightGroup` previously discarded one global map
whenever the client tick, rendered world or global light revision changed.
Rendering a stable light wall therefore repeated its connected traversal and
rectangle partitioning every tick. Alternating between a placed world and a
carried vehicle view could repeat that work within one frame.

`LightGroupCache` now retains the same `LightGroup` results per world. A cold
lookup still uses the existing membership traversal, rectangular fast path
and irregular rectangle partitioner. Warm lookups use the stored layout.

Discovery records the bounds of every consulted cell, including rejected
neighbors and unloaded boundary cells. A change inside those bounds retires
the entire affected connected component, including all rectangles into which
it was partitioned. This handles bridge placement/removal and components that
previously reached the traversal limit. Unrelated distant edits keep cached
components reusable. Bounds are conservative: an edit inside a component's
bounds can cause an unnecessary rebuild, but cannot preserve stale topology.

Settings, NBT reads and tile lifecycle changes notify the cache through the
sided proxy. The notifications do not read neighboring blocks during
`onLoad()`. Chunk load/unload, world unload and resource reload use the
existing rendering lifecycle. Carried worlds also check tile revision and
snapshot identity; translation of the same vehicle does not change its
local topology.

The cache retains at most 8,192 admitted positions plus one overflow component
bounded by the existing 4,096-cell discovery limit. Keeping a stable admitted
set avoids clearing all earlier panels whenever a larger scene exceeds the
budget. Cached values do not strongly retain a world, tile or vehicle
snapshot. Eviction or a cache miss recomputes the same layout.

## Hidden light faces

The ordinary light renderer and Forge batch path both use
`TEAnimatedScreenSelector.lightVisibleFaces`. It previously inspected all six
neighbor cells on every draw, constructing temporary positions and repeatedly
reading block/tile state even when the scene was unchanged.

The same per-world cache now stores up to 8,192 visibility masks. Entries
include the exact tile identity and requested facing. A single-cell edit
invalidates that position and its six neighbors; larger ranges invalidate
the expanded region using overflow-safe bounds. Small-input edits, slab-half
changes and chunk boundaries retain the original visibility decisions.
The original computation remains the cold path and is compared with an
independent reference in the regression fixture. Lightmap/brightness values
are still read during rendering.

## Stable redstone publications

`RedstoneChannels.Network.refresh` previously removed and republished a source
on every input notification, even when its output was identical. This
updated spatial/strength indexes, visited recipients in the old and new
range, and recomputed their complete channel banks. The existing delivery
cache suppressed duplicate callbacks only after that work had already run.

The new fast path compares the transmitted channel bank, source liveness,
each channel's clamped level, range and physical position with the published
source snapshots. Identical publications keep their indexes and skip
recipient settlement in `inputChanged`. Actual changes retain the existing
withdrawal, publication and settlement path.

Registration, movement, latch operations and transfers still perform their
required settlement even if source publication is unchanged. A receiver
moving without transmitting any channels therefore still receives its new
level. Swapping two channel levels while keeping the same aggregate maximum
also remains observable. No additional long-lived cache is introduced.

## Vehicle packet recipients

Forge's entity-tracking broadcast already includes tracked riders. Several
vehicle senders then sent the same packet directly to the driver, and the
Pilot Seat settings path repeated it for every rider.

`TrackingPackets.send` retains the existing entity broadcast and sends direct
fallback packets only to passengers absent from the tracking set. This
covers normal vehicle state, carried tile controls, Doors, Canopies, gear,
Pilot Seat settings and the delayed network regression path. Untracked
passengers in every seat receive the fallback; dismounted nonwatchers do
not. All calls remain on the authoritative server thread.

The packet format, identifiers, decoder bounds, authorization and publication
frequency are unchanged. Payload savings depend on the number of tracked
riders and observers. Duplicate delivery to a tracked driver is halved;
ordinary observer traffic remains the same.

## Reproduction and checks

Use Java 8 and the repository's Gradle wrapper:

```sh
./gradlew testLightGroupCache testChannelSourceRefresh testNetworkBoundaries \
  testChannelSourceIndexAdapters testChannelRecipientIndexAdapters \
  testDedicatedServerLinkage assemble --no-daemon
```

`testLightGroupCache` compares exact joined dimensions and UVs across full,
frame and slab lights, all six facings, irregular groups, mixed artwork,
small inputs and on/off states. It also compares all 64 neighbor-occupancy
masks for all three light families and all six facing arguments. Further
checks cover rejected/unloaded boundaries, bridge changes, settings/NBT,
local-only loading, chunk/world/resource lifetime, carried-world replacement,
movement reuse and cache limits.

The light cost fixture uses 1,024 lights in 32 separate 8 by 4 panels over
200 ticks, plus 200 visibility-query frames. It compares the previous
algorithms with the retained results after warming both paths, recording
world reads, current-thread allocations and elapsed CPU time. These isolate
cache preparation and lookup; they do not measure complete frame time.

`testChannelSourceRefresh` uses the real channel network in an in-memory
world. Its cost fixture has 256 receivers, four channels and 256 unchanged
notifications. Behavior checks include analog changes, unchanged aggregate
levels, source and receiver movement, finite range boundaries, channel
removal/readdition, disabled and inactive sources, replaced tile identity,
transfer coalescing, clamping and world isolation. It also runs 2,000
randomized channel-reconciliation sequences. The `channelRefreshMeasure`
project property disables only the new cost assertion, allowing the same
fixture to measure the original production implementation.

`testNetworkBoundaries` includes real packet encode/decode round trips and
recipient-policy checks for watcher arrival/departure, passenger fallback
and dismount. Its before/after fixture sends 1,000 ordinary 82-byte vehicle
states to a tracked driver and one observer. Byte totals count the encoded
Vandor payload, excluding Forge and transport framing.

The relevant live rendering regression is:

```sh
bash testclient/test_viewscreen.sh --focus programmable-lights
bash testclient/test_viewscreen.sh --focus channel-source-index
```

Live client rendering and multiplayer motion testing remain separate from
the headless checks. CPU work counters, allocation measurements and encoded
packet totals do not establish hardware FPS or shader-pack acceptance.

The recorded headless run passed 288 layout scenes (5,256 member layouts),
1,152 visibility-mask cases, locality/lifecycle/carried-world/cache-limit
checks, channel reconciliation, packet boundaries and round trips, both
channel-index adapter suites, and dedicated-server linkage across 1,619
shared classes and 154,020 references. Live graphics and multiplayer
acceptance are not included in these results.
