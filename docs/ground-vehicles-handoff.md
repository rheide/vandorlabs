# Ground vehicle handoff: smoothing and performance

## Current state

The 2.0 alpha implements reversible Configurizer assembly/parking, a sittable Pilot Seat, W/S acceleration, A/D steering, braking, safe exits, adjustable third-person distance, programmable materials, movement-driven propulsion brightness, manual vanilla/Vandor doors, extended ramp capture and channel-triggered conversion. Limits are 4,096 occupied cells, 64 cells per axis, 4 MiB serialized data and 8,192 merged collision boxes. There is no flight, engine requirement or fuel system. Parking restores the original orientation at a grid position, rather than rotating block states into the current driving heading.

Foreign blocks and tile NBT are retained. Their rendering and collision are best effort; arbitrary machines do not tick or operate while assembled. Known Vandor multi-block ownership and ramp coordinates are translated. Inertial dampeners retain their complete component geometry and settings; this does not add inertial simulation. Ramps retain their captured pose. Walking on a stationary craft is supported; carrying unseated players on moving or rotating decks is not implemented.

The [implementation guide](ground-vehicles-analysis.md) describes compatibility and transfer guarantees. This document distinguishes code findings from hypotheses and proposes the next work; it does not claim that turning is already smooth or that maximum-size craft meet a frame-time target.

Subsequent testing reports two interaction gaps: a large assembled craft can be solid yet impossible to board by clicking its Pilot Seat, and the assembly/parking dialog has no redstone channel field. Channel-triggered conversion exists, but its settings are currently exposed only through the separate placed-seat channel menu. These issues remain open; the investigation below adds follow-ups without changing gameplay code or the `t140` JAR.

## New priority: large-craft boarding and channel configuration

### Boarding: identified code paths and failure mechanisms

The reported symptom is a solid, assembled large craft whose Pilot Seat does nothing when right-clicked. This investigation inspected the current source and the mapped Minecraft/Forge 1.12.2 bytecode; it did not reproduce the user's particular structure in a live client.

1. Once assembled, the visible Pilot Seat is stored geometry, not a world block. Its `BlockPilotSeat.onBlockActivated` cannot receive ordinary block clicks. Boarding requires the vehicle entity interaction path.
2. `EntityGroundVehicle.processInitialInteract` can mount a player, but relies on vanilla delivering an entity interaction. `VehicleClient` has no general right-click route for the captured seat. `VehicleLookup.pointed` is used by `ItemConfigurizer`, so its distant-hull fix reaches parking but not empty-hand boarding or generic door interaction.
3. Vanilla `EntityRenderer.getMouseOver` discovers candidates through `WorldClient.getEntitiesInAABBexcluding`, which uses origin-based chunk/section indexing. A seat far from the craft origin can miss discovery despite the full collision/render bounds.
4. Independently, vanilla `NetHandlerPlayServer.processUseEntity` requires player-to-entity **origin** distance squared to be less than 36 when visible, or 9 when not visible. Even a successful client pick can be silently rejected before `processInitialInteract` when a player is beside a distant seat. Increasing entity bounds or fixing only client picking cannot remove this server gate.
5. Existing live checks mount by scheduling a direct server call to `processInitialInteract` (`VehicleRuntimeChecks.tick`), bypassing both discovery and packet reach validation. The distant-hull test verifies Configurizer targeting and floor/render coverage, not real mouse-click boarding. Those passing checks therefore do not cover this report.

These are verified architectural gaps consistent with the symptom. After boarding is repaired, verify that actual driving input reaches the mounted vehicle before diagnosing a separate large-hull movement/collision stall. Also surface occupied-seat and unavailable-snapshot conditions instead of silently doing nothing.

**Follow-up B1 — one craft interaction route, before smoothing work:**

- Add shared craft-local hit testing that returns entity identity, local cell, hit point and intended action. Use actual selection shapes where available, with a bounded best-effort fallback for unknown mod blocks. Respect intervening terrain and nearer captured cells. The full enclosing hull must not act as an invisible solid target from inside a cabin.
- Route actual client right-clicks on captured Pilot Seat cells through a dedicated vehicle interaction message. Include entity ID/UUID and bounded action/hit information. The server must independently ray-test from the player's current eyes against the current simulation pose, validate reach to the actual hit surface, same world, alive/available craft, spectator status and seat occupancy. Never trust a client-selected cell or relax global vanilla reach checks.
- Keep the Configurizer's parking action and door action priorities explicit. Reuse the same route for distant door interaction so it does not retain the same vanilla failure. Consume each click once; prevent main/off-hand duplication and duplicate vanilla/custom packet actions. Return actionable feedback when boarding is rejected. Preserve seated conversion and independent mouse look.
- Confirm player mounting on the server before enabling driving controls. Keep one authoritative mounting method shared with existing interaction code; reset the current driver's input history consistently. Do not change the craft origin to the seat as a shortcut: collision, persistence, parking and snapshot transforms currently use the structure minimum corner.

**B1 acceptance tests:** Use genuine client clicks and network dispatch, rather than calling the mount method directly. Board seats more than six blocks from the origin, across X/Z chunks and Y sections, from inside and outside the hull, at several headings, with origin offscreen. Exercise both seat cells and the model's protruding bounds. Assert server/client riding identity, then W/S and A/D input/motion. Check an occupied seat, spectators, blocked sight, stale UUIDs, out-of-reach clicks, off-hand duplication, nearby doors and Configurizer parking. Add a dedicated-server case to expose packet reach assumptions hidden by integrated-server fixtures.

### Channels: existing support versus missing dialog integration

`TileEntityPilotSeat` implements `RedstoneChannelMember`, persists its channel list and polls rising edges. `BlockPilotSeat.onBlockActivated` opens `GUI_REDSTONE_CHANNEL` only on sneak-right-click of a **placed** seat (either cell resolves to the lower tile). `ItemConfigurizer.onRightClickBlock` forwards that sneaking case; ordinary Configurizer use opens assembly preview. `GuiHandler`, `GuiRedstoneChannel` and `MessageRedstoneChannel` support the placed tile and its generic channel field.

The dialog in `VehicleClient.Preview` only creates Assemble/Park and Cancel buttons; it contains no channel field or settings action. There is no dedicated Pilot Seat configuration screen. In vehicle mode, the seat is a tile in `VehicleWorld`, while the existing container/message resolve the tile from the real world at a block position. They cannot safely configure an assembled seat. The channel conversion regression calls `setRedstoneChannel` directly and therefore proves pulse behavior/persistence, not discoverability or GUI editing.

The user's exact screen was not captured. If it is the ordinary assembly/parking preview, the missing field is confirmed by its implementation. If it is the separate placed-seat Redstone Channels menu, the code does create a field: investigate opening the wrong screen, client tile synchronization or running an older build rather than assuming a missing widget there.

**Follow-up C1 — expose channels in the Pilot Seat/vehicle dialog:**

- Add a clearly labeled channel-list field or an explicit Pilot Seat settings action accessible from assembly and parking previews, including when seated/inside a craft. Show the saved list and explain that a low-to-high transition toggles mode; held high does not repeatedly toggle. Reuse `ChannelFields`/`ChannelList` parsing and existing limits rather than introducing a second channel format.
- Define Save/Cancel separately from confirming conversion. For placed seats, retain validated container-based editing. If settings change while an assembly preview is open, invalidate/rebuild its snapshot/token: source revalidation compares tile NBT, and transferring an older preview must neither fail confusingly nor restore stale channels.
- For assembled seats, add an authenticated vehicle settings operation keyed by entity UUID and seat identity. Validate permissions and reach against the seat/hit, or the controlling rider, in the real world. Do not send a fake-world local coordinate to `MessageRedstoneChannel` and accidentally edit an unrelated real block.
- Update the authoritative stored seat NBT and local view together, invalidate encoded snapshot caches and synchronize channel settings to clients/previews. Mutating only the live `VehicleWorld` tile does not update immutable `VehicleStructure` persistence. Preserve owner and pulse baseline semantics; editing a powered channel must not immediately convert the craft. Do not invoke real-world block notification for a fake local tile as though it were a placed seat.

**C1 acceptance tests:** Open the settings through real UI interactions on lower/upper placed seat cells and from the assembly/parking dialog; assert field visibility, saved-value display, editing, validation and Cancel behavior. Save a channel list through the actual packet, close/reopen, assemble while seated, park and reload; assert the list survives. Test fresh pulses, held-high changes, permissions, an obsolete preview and an unrelated real-world tile sharing the same coordinates as the vehicle-local seat. Do not substitute a direct setter call for these checks.

These follow-ups extend the earlier verification scope. Address B1 first because a craft that cannot be boarded cannot be used to assess steering improvements, then C1 and the smoothing/performance work below.

## Corrections included in this handoff

Minecraft indexes entities by their origin chunk and vertical section. A structure extending beyond that location can be absent from ordinary entity collision and targeting queries. RenderGlobal can also omit its origin section before the renderer's full-bounds frustum check runs.

- `VehicleService.collision` now considers loaded craft independently of vanilla origin-based entity queries, then filters by hull bounds and component boxes. This restores collision at distant programmable floors and slabs.
- `VehicleLookup.pointed` ray-tests local captured cells within six blocks, with terrain occlusion. Configurizer air and block use both consult this path, so a distant hull surface and clicks from an interior can reach parking. A seated driver can still right-click without aiming at the seat.
- `EntityGroundVehicle.getRenderBoundingBox` conservatively covers the full snapshot, all headings and previous/current positions. `RenderGroundVehicle.renderMissing` renders visible craft omitted by the ordinary entity pass; per-frame tracking prevents duplicate rendering.
- Canopy serialization uses existing loaded tile-map entries instead of lazily creating neighbor tiles during chunk-packet iteration. Propulsion serialization also avoids power queries. This fixes the reproduced startup concurrent modification crash in older saves.
- The shared screen-off asset is restored. The client launcher rejects missing Vandor assets and model-loading exceptions, and supports isolated existing-save compatibility tests.

The broad craft lookup intentionally favors correctness and simple lifecycle handling. It scans loaded entities and allocates a result list on each use; this is a performance debt, especially because terrain collision calls it repeatedly. Do not increase Minecraft's global entity search radius as a substitute.

## Turning jitter: findings and likely causes

| Priority | Location | Finding and implication |
| --- | --- | --- |
| First | `EntityGroundVehicle.authoritative`, `onUpdate` | Local reconciliation eases position error but writes authoritative yaw directly and replays pending controls. Yaw has no equivalent visual correction. Every state update can visibly change heading. This is a concrete asymmetry; its contribution to reported jitter needs measurement. |
| First | `input`, `acknowledgedInput`, `VehicleClient.tick`, `VehicleNetwork.state` | The server records the latest received sequence immediately; simulation consumes the latest intent once per server tick. The client retains and replays one step per pending packet. Packets received together can therefore be acknowledged without each representing a simulated server step. Jitter and latency can make prediction diverge even if both sides use the same controller. |
| First | `updatePassenger`, `RenderGroundVehicle.doRender`, `VehicleClient.camera` | Hull rendering interpolates yaw, but rider placement uses current simulation yaw plus position correction. Third-person rendering follows the player's history. These are different pose timelines. Seats far from the hull pivot magnify angular corrections into large rider displacements. Translucent sorting also transforms its camera using simulation yaw rather than the interpolated render yaw. |
| Next | `setPositionAndRotationDirect`, `authoritative` | Observers receive vanilla entity interpolation and custom STATE interpolation, with independently chosen step counts. They can overwrite the same targets. Establish one documented source of observer pose interpolation. |
| Next | `VehicleCollision.turn`, `advance` | Angular collision advances in substeps and stops at the first blocked step; translation then clips on separate axes. Ground contact, tolerance and step logic can cause genuine heading stalls. Separate collision stalls from network correction before adding visual smoothing. |

The current steering test proves A/D changes yaw. Its position-monotonicity check does not quantify angular smoothness, rider motion, remote observers or latency. Passing it cannot establish that the reported jitter is fixed.

## Recommended smoothing implementation

1. Instrument before changing behavior. Record simulation tick, received/applied input sequence, pending queue length, predicted/server position and wrapped yaw error, accepted angular delta, collision rejection, rider displacement and frame time. Use opt-in bounded counters or a ring buffer; no per-box production logging.
2. Define the input contract. Prefer a fixed simulation tick command stream with explicit server acknowledgment of commands actually simulated. Bound queue length and catch-up work; specify how missing commands repeat held intent and how duplicates are discarded. Alternatively use latest-intent snapshots with server simulation ticks and a matching history model. Do not retain latest-intent acknowledgment while pretending every packet is one simulated tick.
3. Introduce a shared pose representation: authoritative simulation pose, predicted pose and visual pose. Add wrapped angular error easing alongside position error, using elapsed render time or an explicitly defined tick constant. Interpolate previous/current visual corrections as well as positions. Snap only for teleports, ownership changes or large invalid errors; clear histories on mounting, world changes and conversion.
4. Render the hull, local pilot and camera anchor from the same interpolated visual pose. Transform the seat around the same pivot. Preserve independent mouse look. Keep collision, picking and parking validation on simulation state; visual smoothing must not move the actual collision hull through terrain. Use the visual yaw for transparent sorting and tile-renderer camera coordinates.
5. Give observers a short timestamped pose buffer and one interpolation path. Define behavior for packet gaps and yaw wrap at -180/180 degrees. Avoid smoothing already interpolated poses a second time.
6. Revisit angular collision only if traces show real blocked turns in clear terrain. Use a conservative swept broad phase, stable contact tolerances and sufficient angular subdivision for the furthest hull point. Keep contact stops authoritative rather than concealing them with long visual drift.

Retain server authority and bounded replay. Do not solve jitter by accepting client positions or disabling hull collision. Changing turning speed alone does not address reconciliation mismatch.

## Performance work, in order

| Priority | Change | Reason and correctness constraint |
| --- | --- | --- |
| First | Replace `VehicleLookup.loaded` with a per-world craft registry and X/Z bucket index covering the entire hull | Collision currently scans all loaded entities per query, including queries generated by each hull box. Register on accepted spawn; update when bounds/pose change; remove on death, chunk unload and world unload. Snapshot installation must update buckets. Never register temporary validation entities. Test negative coordinates and structures spanning multiple sections. |
| First | Batch terrain candidate queries in `VehicleCollision.turn/clip` | Each hull box currently calls `World.getCollisionBoxes`, which also invokes craft collision. Collect obstacles for a bounded swept region once per step, deduplicate and spatially filter per box. A large hollow hull must not query every block in its entire enclosing volume; use section buckets or grouped sweeps. Keep mod-provided collision shapes and moving obstacles correct. |
| First | Measure and budget render mesh rebuilds in `RenderGroundVehicle.mesh` | Both layer meshes depend on sampled light; rebuilding loops over every cell and layer, allocating component builders and vertex-state copies. A light transition or changed structure can stall a frame. Separate geometry/material/lighting invalidation, reuse builders, and queue bounded section rebuild/upload work. Render a consistent prior mesh until replacement is ready. Keep GL upload/deletion on the render thread. |
| Next | Cache transformed hull and terrain candidates per simulation step | `pose(yaw)` caches only one angle; turns overwrite it for each trial, then translation rebuilds the accepted pose. Cache accepted geometry and use reusable scratch geometry for angular trials. Avoid nested arrays allocated for every SAT test. Profile box counts, not only block counts. |
| Next | Localize door and propulsion invalidation | Door patches currently replace the snapshot/rebuild collision and cause mesh invalidation. Dirty only affected regions and neighboring faces. Propulsion already has a separate mesh, but brightness changes still rebuild it; prefer a compatible emissive/lightmap overlay where materials permit. Preserve programmable texture semantics. |
| Next | Reduce snapshot/preview overhead | Cache immutable encoded snapshots, bound reassembly and expire abandoned previews. Review repeated full preview transfers and cell-by-cell highlight drawing at 4,096 cells. Consider a compact preview outline or section bounds. Preserve complete NBT and server revalidation. |
| Next | Profile transfer stalls separately from driving | Discovery is sliced, but journal sync, bulk restoration, lighting, neighbor notifications and save flushes are synchronous. Measure each phase. If spreading conversion over ticks, retain durable ownership and prevent players interacting with a half-transferred craft. Never remove durability checks merely to improve timing. |
| Later | Budget tile-renderer work and transparency | Existing tile renderers run for every relevant stored tile. Add visibility/section filtering and resource-reload tests; some renderers require real-world state and remain best effort. Transparent sorting should use the shared visual pose and only rebuild when needed. |

Avoid threading Minecraft block/tile callbacks or GL operations. Only immutable CPU data preparation may move off-thread after verifying dependencies. Optimize measured hotspots before introducing a custom physics engine or a new networking dependency.

## Verification and completion criteria for the next session

The handoff build is `2.0-alpha`, internal build `t140`. Java 8 compilation and standard JAR packaging passed. The focused live vehicle suite passed with the extended-hull collision, distant-targeting and hidden-origin rendering regressions; the captured far-hull frame was visually inspected. Packaged asset validation passed for 12,984 models, and the retired-texture check passed. Earlier full-client runtime checks, focused Pilot Seat/canopy checks and an isolated existing-save startup reproduction were exercised during implementation. The full client suite was not rerun after the final hull fixes; dedicated-server latency and fleet performance remain unverified.

Use Java 8 for Gradle 4.9. Build the standard JAR in `build/libs/`. Run the live client suite hidden and muted. Existing focused vehicle coverage includes conversion cancellation/recovery, full state/NBT round trips, programmable materials, generic mod blocks, inertial dampener rendering, actual steering key packets, propulsion, doors, ramps, occupied/channel conversion, safe exits, 4,096-cell discovery and rejection of 4,097. Extended-hull checks add slab/programmable floor support across X/Z chunks and Y sections, distant Configurizer ray targeting and full render bounds. A live render fixture checks that the fallback draws the far hull with its origin section behind/below the camera and captures a frame for inspection. These tests do not constitute a fleet benchmark.

Add the following scenarios before claiming smooth steering:

- Small, long, hollow and 4,096-cell craft; pilot at the center and each far end. Continuous left/right turns, reverse turns, rapid alternation, braking, wall contact and half-block steps.
- Driver and remote observer on a dedicated server with 0/50/100/200 ms latency, jitter and loss. Separate real transport delay from client/server tick stalls.
- First and third person, several camera distances, more than one frame rate, yaw wrapping, dismount/remount and parking. Include seats far from the rotation pivot.
- Stationary floor walking across slabs and programmable blocks, an origin section behind the camera, chunk unload/reload and snapshot installation after spawn. Confirm a far visible hull remains drawn when its origin section is culled; inspect actual frames, not only the renderer's bounds method.
- Door changes, propulsion brightness transitions, light changes, resource reloads and one/ten/many loaded craft. Check partial-block passage and the known conservative walking collision at rotated hull edges.

Collect median/p95/p99 client frame and server tick time, mesh rebuild/upload time, terrain queries and candidate boxes per tick, replay steps, wrapped angular correction and pilot displacement. Proposed targets, subject to a recorded baseline: no correction-induced heading reversal under steady input in clear terrain, no visible periodic yaw snap, no stationary floor falls, and no missing visible hull. A 60 FPS client has a 16.7 ms total frame budget and a 20 TPS server a 50 ms total tick budget; assign a vehicle share after profiling the rest of the mod pack. Report hardware-independent workload sizes and measurements, not an unsupported claim that 4,096 blocks are always fast.

Suggested next change order: diagnostics and applied-input acknowledgments; shared visual pose/yaw correction and rider alignment; observer interpolation; craft spatial index; terrain query batching; budgeted mesh invalidation. Re-run persistence and conversion tests after each structural change. Keep movement smoothing distinct from the transfer journal format unless a schema change is actually required.
