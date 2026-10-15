# Ground vehicles: behavior, safeguards and validation

## Supported behavior

Version 2.0 supports reversible Configurizer assembly/parking, Pilot Seat driving, braking, safe exits, adjustable third-person distance, movement-driven propulsion brightness, manual vanilla/Vandor doors and trapdoors, extended ramp and paused escalator transport and channel-triggered conversion. Limits remain 4,096 occupied cells, 64 cells per axis, 4 MiB serialized data and 8,192 merged collision boxes. The bounded performance workloads stop at 2,048 cells in anticipation of a smaller release limit. There is no flight, engine requirement or fuel system.

Taxi Mode defaults to ON, including older seats without a saved setting. Sitting starts discovery while the player remains in the placed seat; successful conversion transfers the pilot to the craft. Exiting stops the craft and restores blocks. Turn Taxi Mode off in placed-seat settings or a Configurizer preview to convert manually. Discovery schedules each coordinate once, processes at most 256 positions per job per tick and shares a 1,024-position world budget. It remains iterative and bounded even when gear ownership and hull connections form loops.

Every captured landing gear must be stationary and its physical bottom must share the same Y value as the other gears. The comparison uses full gear geometry before clipping it into occupied cells, including size and fractional extension. Different mounting heights are permitted when extension lengths put the feet on the same plane. Misalignment fails discovery before blocks change. Retracted and legacy fixed gears participate in the same support-plane check.

Parking restores a nearby grid position in 90-degree steps, with a twelve-degree alignment tolerance. Other headings are refused. Unknown rotation adapters preserve the assembled craft and warn on exit; dismounting remains available. Reboard and return to the original heading before parking. All rotation preflight occurs before block mutation. Supported adapters include all vanilla Minecraft and Vandor blocks, IE ladders and capacitors, Mekanism energy cubes and standard Malisis doors/trapdoors. Other machines require individual adapters and remain restorable at their original heading.

An obstructed automatic return preserves the stopped craft and retries once per second. Reboarding cancels the pending return. Pending status and actor identity survive saves and rollback. Recovery requires the initiating player online in the same dimension with placement permission; manual Configurizer parking remains available. Transfer journals are durably written before mutation and retired only after chunk/entity saves are flushed. Conversion still performs synchronous block, lighting, neighbor and disk work; it is not a guarantee of instant boarding.

## Seat settings and channels

Placed-seat settings and the assembly/parking preview expose Pilot Seat channels and Taxi Mode. The preview includes channels, Disabled/Redstone ON/Redstone OFF trigger and Base/+2/+4/+6 px height directly on its first screen, using the shared channel-list parser. Saving channels does not confirm conversion. It refreshes the preview and token so a stale preview cannot restore old settings. The Taxi Mode preview toggle saves immediately.

Assembled settings identify the craft UUID and seat through a server preview token. The server validates ownership, player/world state, current source, expiry, actual transformed reach or controlling-rider identity, and protection permissions. A local vehicle coordinate is never treated as the position of a placed real-world tile. Both immutable snapshot NBT and the presentation tile are updated, encoded snapshots invalidated, and clients synchronized. Editing a powered channel samples its current real-world level as the baseline: only a subsequent low-to-high transition converts the craft.

## Movement and presentation

The server queues at most 64 accepted input commands and acknowledges commands only when consumed by simulation. Client replay includes only commands already simulated locally. Duplicate/older commands and invalid input are rejected. STATE packets carry a server tick so unchanged acknowledgments can still deliver new motion, while stale or reordered states are ignored.

Authoritative collision pose remains immediate. Position and wrapped yaw corrections ease through a separate visual pose. Hull rendering, rider anchors, camera correction and translucent camera transforms use that shared interpolated pose, including seats far from the pivot. After the first custom STATE, observers use that stream without competing vanilla entity interpolation. Teleports reset visual history. Surface selection, collision and parking continue to use the simulation pose.

## Performance and lifecycle

- Craft lookup uses a per-world spatial index over conservative hull bounds, independent of vanilla origin-chunk entity indexing. Only entities actually added to the world are registered; movement, removal, re-addition, death and world unload update its lifecycle.
- Detailed hulls share terrain candidates from touched eight-block sections for one simulation step. Small merged hulls query their actual swept bounds directly. SAT tests avoid temporary nested arrays. A three-entry cache retains recently transformed hulls across angular trials and subsequent translations. Hollow interiors are not filled by the batching scheme.
- Rendering stores eight-block section meshes with separate propulsion geometry. Changed regions and neighboring faces are invalidated; prior complete geometry stays visible until replacement finishes. New geometry takes priority over lighting refresh. Main-thread rebuild/upload work has a shared soft budget of 2 ms and 512 cell/layer callbacks per frame. A single mod callback or upload can exceed the time budget.
- Transparent layers are drawn after opaque layers, with section ordering and per-section vertex sorting in the shared visual camera pose. Tile-renderer visibility uses conservatively transformed render bounds in the same visual pose; unavailable or unbounded bounds retain rendering. GL resources are retired on reload, despawn and world changes.
- Encoded immutable snapshots and rotated parking snapshots are cached by source identity. Channel changes avoid collision rebuilds. Changed snapshot tile types/NBT are reconciled in the local view; unchanged tiles retain their identity.
- Optional diagnostics report bounded controller percentiles, terrain queries, replay depth, angular correction, discovery ticks and separate journal/block-lighting/save-flush timings. They are disabled during ordinary play.

All block/tile callbacks and GL work remain on their Minecraft threads. Durable transfer guarantees are retained instead of spreading a half-converted craft across ticks.

## Validation coverage and limits

The headless vehicle checks cover applied-input acknowledgments, duplicate/missing/batched controls, bounded queues, yaw wrapping, distant-seat transforms, surface selection, occlusion and packet validation.

Focused live checks exercise actual boarding and steering key packets, distant lower/upper seats, rotated targeting, normal/paired/3x3 doors, normal and diagonal trapdoors, placement positions, hinges/sliding, partner links, programmable materials, full NBT and safe exits. The additional door matrix exercises all exposed motion, frame, depth and facing combinations for normal, paired and 3x3 doors through the production grouping/collision path; the representative real-click fixture covers dispatch separately.

Taxi checks include controller assembly and parking UI, automatic settings application without Save, independent seat height and Taxi toggles, canonical channel lists, held-high baselines, stale previews, protected edits, forged seat identities, real-world local-coordinate isolation, default/manual modes, repeated cardinal trips, unsupported-heading dismount with craft preservation, blocked/cancelled parking and retry, entity save/reload, and complete immediate placement audits. Optional foreign fixtures retain furnace/chest inventories, nonzero IE/Mekanism energy, IE side configuration and Malisis descriptors. Gear checks cover five sizes, unequal mounts with aligned bottoms, half-block misalignment, moving gears, bounded loops and mutation-free stacked-gear rejection.

The dedicated-server lab connects separate driver and observer clients through real Forge networking. It injects bounded application-message delays of 0/50/100/200 ms, deterministic jitter and dropped INPUT/STATE messages, and verifies ongoing observer updates, driver heading continuity and remote door patches. This injection occurs after packet decoding: it does not emulate TCP loss/retransmission, actual network congestion or arbitrary server stalls.

The performance fixture uses four workloads: a small craft, a 2,048-cell solid craft, a 1,352-cell hollow craft and a 256-cell detailed floor with 256 collision boxes. It records collision-step median/p95/p99 and terrain query counts, checks floor support at cardinal/diagonal driving headings, and tests spatial-index lifecycle at negative coordinates and distant hull sections. These are bounded microbenchmarks, not a large-fleet or whole-modpack frame-time guarantee.

The hidden-origin rendering fixture spans 64 blocks along one axis. It puts the Pilot Seat and origin section behind/below the camera, verifies a distant hull and chest renderer remain visible after a mesh reload, and captures a frame. Resource reload, unusual third-party renderers, hardware/driver differences, additional observers, extended sessions and large fleets still require release playtesting. Rotated walking collision remains conservative at hull edges; unseated passengers are not carried on moving decks.

## Deliberately deferred onboard systems

Foreign blocks and complete tile NBT are preserved. Arbitrary machines and inventories do not tick or operate while assembled. Redstone Screen rows, channel-operated doors, Canopies, lights and landing gear use explicit vehicle adapters. `VehicleWorld` is a read-only presentation/collision view on both client and server, and uses classes available on a dedicated server. Manual door interaction, Pilot Seat settings and supported channel controls have explicit authoritative adapters. Vehicle subscriptions share the channel bank in the parent dimension and retire when the entity unloads or parks.

Working onboard inventories require craft-aware container sessions, authoritative mutable inventory state, transformed reach checks, session closure during conversion, and concurrent-player/disconnect/rollback tests. Additional controls and foreign machines need individual configuration and persistence adapters. Enabling unrestricted tile ticking or forwarding real-world position packets into local storage is not a compatibility solution. These features remain outside the current simple vehicle scope.

### Pilot Seat inspection failures

The Configurizer opens a compact selected Pilot Seat dialog for height and automatic Taxi Mode. Vehicle Controller owns assembly inspection, trigger polarity, toggle channels and explicit Park/Taxi/Hover channels. An incomplete controller selection still allows controller settings edits while assembly remains disabled. Valid edits apply automatically; closing flushes pending valid edits. Source identity, transformed reach, expiry and protection checks also apply to captured settings.
