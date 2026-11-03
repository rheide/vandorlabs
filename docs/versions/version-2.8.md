# Vandor Labs 2.8

Version 2.8 focuses on dense builds: programmable Surface floors, glazing, diagonal panels, stationary ramps, collision queries and garage doors. Existing block registry IDs and saved designs remain intact.

## Vehicle designs and security

Vehicle Controllers now retain a name and a small rotating shape preview. Vehicle Status screens display the name and conversion activity while bounded scan/preflight work prepares Taxi or Hover mode. Controllers also retain a last-scanned weight estimate, including supported inventory and fluid contents; it is informational and does not change driving or flight.

An attached Data Chip Mount lets the controller write its exact design to a Vehicle Chip. The [Card / Chip Programmer](../card-chip-programmer.md) creates blank Vehicle Chips, previews their shape and duplicates their full saved contents. The [Copy Controller](../copy-controller.md) can build directly from a Vehicle Chip using an output Surface and the usual materials and recovery checks. Copies are centered on the output Surface; oversized designs are refused before spending materials or placing blocks.

The controller’s Security tab accepts a selected Access Card through an attached mount. Leave that card inserted to allow Taxi/Hover conversion; removing it blocks future conversions while parking remains available. See [Vehicle controls](../pilot-seat-taxi.md).

When a mount is missing, the Vehicle tab explains how to attach a Data Chip Mount to write vehicle data, and the Security tab explains that an Access Card Mount enables security.

## Garage doors

The new [Garage Door](../garage-doors.md) lifts linked columns and retracts them overhead. It supports ordinary right-click operation, Redstone Channels and the standard Configurizer workflow. A dedicated dialog controls travel time, local redstone and signal polarity.

Settled closed panels use chunk geometry. Moving and raised panels share a batched renderer and a bounded mesh cache. Client animation tolerates repeated updates, clock corrections and reversals; server collision and saved timelines remain authoritative.

![Nine by five garage door](../images/garage-build-3-closed.png)

See the [rendering measurements and validation scope](../render-performance.md).

## Dense rendering

Unclaimed single Surface blocks share Forge's atlas batch and use tighter visibility bounds. Joined and controller-owned surfaces retain their established renderer. Ordinary planar translucent glazing batches consecutive compatible panes while preserving glass ordering and special finishes.

Stationary opaque ramp endpoints reuse VBO meshes. Motion, unsupported finishes and special ramp modes retain their existing geometry path. Opaque diagonal wall fallback rendering can join the fast batch; ordinary in-section walls continue to use terrain geometry.

Canopy and moved-door collision queries search nearby loaded-owner buckets instead of every loaded tile. Empty turntable searches no longer traverse unrelated entity lists for each particle collision.

## Rain beneath fixed Canopy Glass

Fixed glass already stops native rain columns. Complementary's wind effect can treat transparent roofs as outdoors because skylight remains high. Sheltered precipitation now scopes the wind uniform to the weather pass and restores it afterwards, preserving ordinary lighting. Hardware verification with Complementary Unbound remains part of manual testing.

Large vehicles reuse opaque wall sections with shader-sized vertex records, including per-block material IDs. Fixed Canopy Glass misses no longer rebuild neighbour geometry during collision searches, and loaded trapdoors use owner indices. Large flat hulls query shallower, wider collision regions. Geometry caches respond to carried artwork updates and neighbouring edits. Contained-fluid cells are compacted, and rendering skips unused voxel volumes. See the [performance notes](../render-performance.md) for measured comparisons and test conditions, and the [performance summary](../performance/2.8-summary.md) for the next priorities.

The standard 2.8 JAR passes the Java 8 release gate, including shader startup, rendering, glass and fluid contacts, shared surfaces, arm motion and saved Vehicle/Taxi regressions. Hardware shader acceptance remains a separate manual check. The [vehicle follow-up measurements](../performance/2.8-followup.md) distinguish scan latency, terrain allocation and remaining shader/publication costs.
