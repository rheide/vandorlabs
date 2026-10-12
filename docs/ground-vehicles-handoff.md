# Ground vehicle handoff: smoothing and performance

## Current state

The 2.0 alpha implements reversible Configurizer assembly/parking, a sittable Pilot Seat, W/S acceleration, A/D steering, braking, safe exits, adjustable third-person distance, programmable materials, movement-driven propulsion brightness, manual vanilla/Vandor doors, extended ramp capture and channel-triggered conversion. Limits are 4,096 occupied cells, 64 cells per axis, 4 MiB serialized data and 8,192 merged collision boxes. There is no flight, engine requirement or fuel system. Parking restores blocks at a nearby grid position in 90-degree steps. Non-cardinal headings and unrecognized rotation adapters are refused; unknown components remain restorable at the original heading.

Foreign blocks and tile NBT are retained. Their rendering and collision are best effort; arbitrary machines do not tick or operate while assembled. Known Vandor multi-block ownership and ramp coordinates are translated. Inertial dampeners retain their complete component geometry and settings; this does not add inertial simulation. Ramps retain their captured pose. Walking on a stationary craft is supported; carrying unseated players on moving or rotating decks is not implemented.

The [implementation guide](ground-vehicles-analysis.md) describes compatibility and transfer guarantees. This document distinguishes code findings from hypotheses and proposes the next work; it does not claim that turning is already smooth or that maximum-size craft meet a frame-time target.

## Taxi Mode and cardinal parking

Pilot Seats default to **Taxi Mode: ON**, including older seats without a saved setting. Sitting starts a bounded connected-craft scan and transfers the seated player to the assembled craft. Exiting stops the craft and restores its blocks without remounting the player. Turn Taxi Mode off in the placed seat's sneak-right-click settings or the Configurizer preview to keep conversion manual. The preview toggle saves immediately; Done saves the placed-seat settings.

Parking supports cardinal headings only, with a five-degree alignment tolerance. Unknown rotation adapters prevent exit while keeping the driver seated; return to the original heading before exiting. The rotation preflight runs before any blocks change. The preview highlights the actual rotated cells. Supported adapters cover native block states, common vanilla inventory tiles, Vandor seats/programmable doors/trapdoors/material selectors/landing gear/ship systems, IE ladders and LV/MV/HV capacitors, Mekanism energy cubes, and standard Malisis door/trapdoor tiles, including vanilla registry replacements. Other machines and multi-block systems require individual adapters. Their full saved state can still return at zero rotation.

An obstructed automatic return preserves a stopped craft and retries once per second. Reboarding cancels that pending return. Pending status and actor identity survive entity saves and transfer rollback. No parking operation overwrites an obstruction or bypasses protection events. Automatic retry requires the initiating player to be online in the same dimension with permission to place the blocks. Manual Configurizer parking remains available.

## Boarding and programmable door corrections

Captured Pilot Seat cells and doors now share a surface-based right-click route. Client selection searches loaded craft independently of vanilla's origin-chunk entity index. A dedicated interaction packet carries the entity ID, UUID and selected local cell; the server independently selects the nearest actual surface from the player's current eyes and simulation pose. Reach, terrain, nearer captured geometry, ordinary entity occlusion, spectator status and craft availability are checked before dispatch through Forge's normal entity interaction hook.

This removes vanilla's entity-origin distance gate for supported craft interactions without increasing global interaction reach. Configurizer parking takes priority, followed by door interaction and explicit Pilot Seat boarding. Arbitrary hull clicks do not board the craft. Mouse clicks are consumed before duplicate vanilla/off-hand dispatch; keyboard use also has a Forge event fallback. Boarding resets the driver's pending input history and reports an occupied seat.

Door selection uses block ray callbacks, including open leaves and programmable door geometry, rather than an unrelated head-yaw ray. Door changes validate complete two-cell, paired four-cell and large nine-cell groups, plus vanilla and programmable trapdoor groups of up to 64 cells before replacing captured OPEN states. Patches preserve tile settings and reach both tracking clients and the controlling rider. Arbitrary tile ticking remains disabled.

The focused live client suite uses real vanilla right-click dispatch and network messages for lower/upper seats more than six blocks from the craft origin, rotated boarding, subsequent steering/propulsion input, and open/close cycles for normal, paired and 3x3 programmable doors at two headings. Normal and diagonal trapdoors cover all three placement positions, hinged/sliding motion and transformed partner links. It checks component states on client/server and retained material settings. Taxi checks exercise both settings dialogs, repeated quarter-turn parking, blocked/cancelled return and retry, saved pending state, and unsupported-heading exit refusal. With the optional foreign fixtures installed, immediate placement audits compare every captured state and complete tile NBT, including furnace/chest inventories, nonzero IE/Mekanism energy, IE side configuration and Malisis descriptors. The launcher rejects asynchronous event/spawn exceptions as well as missing completion markers.

Remaining validation limits include dedicated-server/multiple-observer coverage and the complete matrix of programmable door motions, frames, placement depths and per-face materials. The live click fixture is representative coverage, not a claim that every foreign block interaction works.

### Channels: existing support versus missing dialog integration

`TileEntityPilotSeat` implements `RedstoneChannelMember`, persists its channel list and polls rising edges. `BlockPilotSeat.onBlockActivated` opens `GUI_REDSTONE_CHANNEL` only on sneak-right-click of a **placed** seat (either cell resolves to the lower tile). `ItemConfigurizer.onRightClickBlock` forwards that sneaking case; ordinary Configurizer use opens assembly preview. `GuiHandler`, `GuiRedstoneChannel` and `MessageRedstoneChannel` support the placed tile and its generic channel field.

The Configurizer preview provides Assemble/Park, Cancel and Taxi Mode controls, but contains no channel field. The placed Pilot Seat settings dialog combines channel entry and Taxi Mode. In vehicle mode, the seat is a tile in `VehicleWorld`, while the channel container/message resolve the tile from the real world at a block position. They cannot configure an assembled seat's channels. The channel conversion regression calls `setRedstoneChannel` directly and therefore proves pulse behavior/persistence, not assembled channel editing.

The separate placed-seat Redstone Channels menu already contains a channel field. Missing fields there require checking screen selection, client tile synchronization and build version independently of assembly/parking dialog integration.

**Follow-up C1 — expose channels in the Pilot Seat/vehicle dialog:**

- Add a clearly labeled channel-list field or an explicit Pilot Seat settings action accessible from assembly and parking previews, including when seated/inside a craft. Show the saved list and explain that a low-to-high transition toggles mode; held high does not repeatedly toggle. Reuse `ChannelFields`/`ChannelList` parsing and existing limits rather than introducing a second channel format.
- Define Save/Cancel separately from confirming conversion. For placed seats, retain validated container-based editing. If settings change while an assembly preview is open, invalidate/rebuild its snapshot/token: source revalidation compares tile NBT, and transferring an older preview must neither fail confusingly nor restore stale channels.
- For assembled seats, add an authenticated vehicle settings operation keyed by entity UUID and seat identity. Validate permissions and reach against the seat/hit, or the controlling rider, in the real world. Do not send a fake-world local coordinate to `MessageRedstoneChannel` and accidentally edit an unrelated real block.
- Update the authoritative stored seat NBT and local view together, invalidate encoded snapshot caches and synchronize channel settings to clients/previews. Mutating only the live `VehicleWorld` tile does not update immutable `VehicleStructure` persistence. Preserve owner and pulse baseline semantics; editing a powered channel must not immediately convert the craft. Do not invoke real-world block notification for a fake local tile as though it were a placed seat.

**C1 acceptance tests:** Open the settings through real UI interactions on lower/upper placed seat cells and from the assembly/parking dialog; assert field visibility, saved-value display, editing, validation and Cancel behavior. Save a channel list through the actual packet, close/reopen, assemble while seated, park and reload; assert the list survives. Test fresh pulses, held-high changes, permissions, an obsolete preview and an unrelated real-world tile sharing the same coordinates as the vehicle-local seat. Do not substitute a direct setter call for these checks.

Channel dialog integration and smoothing/performance changes remain deferred. The current scope keeps vehicle mode simple and fixes boarding and manual door interaction.

### Shared selection foundation

`VehicleSelection` provides simulation-pose surface selection and classifies the nearest captured cell as a seat, door or other surface. It uses block selection callbacks, including protruding seat bounds and partial blocks; unknown callbacks that fail use a bounded cell fallback. Callers supply the nearest terrain or other-craft distance, with ties occluded. Empty space inside a hull is not a target.

`testVehicleSelection` checks distant lower/upper seat cells, protruding bounds, cabin gaps, slab/door shapes, occlusion, invalid rays, rotated transforms and interaction packet identity without a renderer. Production client/server interactions use this selector; the live suite covers actual click dispatch separately.

## Deferred interactive onboard systems

Working inventories and configurable blocks require more than forwarding a click.
`VehicleWorld` is currently read-only and reports `isRemote=true` on both sides;
block mutations and update notifications are discarded. Its tiles do not tick.
Entity saves and parking serialize `VehicleStructure`, rather than the mutable
tile instances in the local view. Existing GUIs, container reach checks and
settings packets address real-world block positions. Calling their activation
callbacks unchanged is therefore insufficient and can lose onboard edits.

If this feature is resumed, use explicit adapters for vanilla chests and Vandor storage, screens, lights and
controls. Preserve unsupported blocks and NBT without activating arbitrary mod
machines. Do not enable unrestricted tile ticking in the presentation world.

Implementation dependencies and acceptance boundaries:

1. Complete shared interaction routing and independent server selection for
   boarding, doors and supported onboard blocks. Identify targets by craft UUID
   and local cell; authorize using actual transformed reach and current ownership.
2. Establish one authoritative mutable onboard state with bounded revisions.
   Save, unload/reload, parking, transfer journals and rollback must all use its
   current contents. Door and settings patches must preserve inventory changes.
3. Add craft-aware container sessions. Reuse slot-transfer logic, but validate
   transformed distance and tile identity; close sessions on conversion, removal
   or loss of access. Keep inventory payloads out of public appearance snapshots.
   Test shift-clicks, simultaneous viewers, disconnects and parking during use
   before claiming protection against item loss or duplication.
4. Adapt screen and light configuration messages to authenticated onboard
   sessions. Synchronize appearance revisions and invalidate affected meshes.
   Distinguish emissive fixture rendering from actual illumination of terrain:
   the current view has no moving-world light propagation.
5. Define whether channel signals are isolated per craft or connected to world
   channels before enabling onboard controls. Separate this from physical vanilla
   redstone wiring, scheduled block updates, automation and foreign machine ticks.
6. Run real client and dedicated-server tests for interaction, GUI synchronization,
   persistence and multiple players. Headless selection and NBT tests alone do not
   validate these features.

This is a staged feature rather than a generic compatibility switch. Full
foreign-machine behavior would require ongoing integration work for world,
chunk, ticking, capability, networking and relocation assumptions.

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
