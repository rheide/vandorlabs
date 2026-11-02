# Vandor Labs 2.8

Version 2.8 focuses on dense builds: programmable Surface floors, glazing, diagonal panels, stationary ramps, collision queries and garage doors. Existing block registry IDs and saved designs remain intact.

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

This is a development candidate. Focused checks accompany the changes; the full release gate remains deferred.
