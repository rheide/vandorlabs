# Ground vehicles: 2.0 design and implementation

The Configurizer converts a connected craft into one persistent vehicle entity and parks it back as blocks. Sitting only mounts the player. The craft drives forward/backward and steers on the ground. There is no flight, engine requirement or fuel system.

## Player interaction

1. Build a craft with exactly one Pilot Seat and at least one landing gear. Leave air between the hull and surrounding buildings or terrain; only landing gear should touch the ground.
2. Use the Configurizer on either cell of the Pilot Seat. Inspect the highlighted selection and choose **Assemble**.
3. Right-click the assembled craft to sit. **W/S** accelerates forward/backward along the current heading, **A/D** steers, **Space** brakes, and **Sneak** dismounts. Steering requires motion and reverses when backing up.
4. Stop and use the Configurizer anywhere on the craft, including while seated. **Park as blocks** restores it at the highlighted grid position, with its original orientation. Obstructed destinations are rejected. A seated pilot transfers between the vehicle and the placed seat; a player inside a parked craft is seated if needed to avoid the grid snap placing blocks through them.

In third person, mouse look orbits the pilot and the mouse wheel adjusts camera distance from 4 to 96 blocks. The default distance scales with craft dimensions. Terrain shortens the camera arm.

Sneak-right-click the placed Pilot Seat, with a bare hand or Configurizer, to set its redstone channels. Each low-to-high channel transition toggles block/vehicle mode. Holding a signal high does not repeat the action, including across conversion and save/load. Channel changes sample the current level and require a fresh pulse. The player who placed or configured the seat must be online in that dimension so permission and protection checks have an accountable player. Receivers establish a baseline after a one-second load grace period to avoid treating restored power as a fresh pulse. Failed pulses report the reason and require another pulse; moving craft must stop before parking.

The replacement Pilot Seat reserves a second cell for its backrest. Seats placed before that model upgrade must be replaced before assembly. Missing component messages identify the expected block and coordinates.

Propulsion starts dark on assembly and ramps brighter during horizontal movement, regardless of their saved redstone/particle settings. This effect adds no particles and does not change the saved configuration. Propulsion blocks are decorative and are never required for movement.

## Discovery and compatibility

Discovery is a six-face flood fill starting at the seat, processed in slices of 256 queued positions. Landing gear and its owned footprint are included but terminate their own branches, preventing traversal into terrain through the wheels. Other branches continue. Gear is not a closed boundary: a hull block touching a hangar is still connected, so inspect the preview.

The limits are 4,096 occupied cells, 64 cells on each axis, 4 MiB of serialized structure data, and 8,192 merged collision boxes. Unloaded boundaries, fluids, unbreakable blocks, incomplete known components and limits fail without partially assembling a craft. Moving canopies, gear and controlled ramps must settle first. Ramp controllers follow their reserved cells even when extended geometry is separated from the hull. Their current extended pose, source materials, textures and recovery journals are retained. Ramps remain fixed in vehicle mode and resume normal operation after parking.

General registered blocks from other mods are accepted, including non-cube blocks and tile entities. There is no mod/block allowlist. Full block properties and persistent tile NBT are stored separately from the vehicle's presentation. Foreign tile entities do not tick while assembled; machines, inventories and networks cannot be operated in vehicle mode. The Pilot Seat receiver continues to read the real dimension's redstone channels. Vanilla and Vandor doors can be opened/closed manually in vehicle mode, including paired Vandor doors and 3x3 doors; their redstone networks and settings menus remain suspended until parking. Door clicks select actual block surfaces, update collision and preserve saved tile settings. Vanilla iron doors also allow manual vehicle-mode interaction.

The local world view feeds the blocks' existing models and collision code. If a mod cannot supply a local tile/model/collision shape, presentation is best effort: unavailable tiles are skipped, failing block models use a missing-model placeholder, and failing collision shapes use their cell-sized box. Those fallbacks never replace the stored original block or tile data. Parking reconstructs original block types, properties and NBT, translating tile positions. Foreign mod-specific absolute links remain opaque; the owning mod may need to rebuild its networks or multi-block relationships after relocation. Generic machine simulation is outside scope.

Known Vandor multi-cell adapters include seat backrests, doors, canopies, ship systems, trapdoor assemblies, controlled ramps and telescopic gear footprints. Absolute Vandor ownership links are translated; relative links remain unchanged. Programmable built-in, filesystem, custom-block and per-face material settings remain in full tile NBT.

## Storage and transfer

`VehicleStructure` is an immutable sparse collection of local cells with a registry/property palette and complete tile NBT. `EntityGroundVehicle` owns that snapshot, UUID, transfer epoch, collision geometry and local view. Ordinary driving changes one entity position and yaw rather than world blocks.

Assembly and parking revalidate the server preview, held tool, reach, permissions, occupancy and source/destination state. A durable write-ahead record stores the pre-transfer owner. Bulk transfers avoid destructive block-break callbacks, restore all cells before neighbor notification, and flush chunk/entity saves before retiring the record. Startup recovery rolls an interrupted transfer back. A persistent UUID/epoch ledger rejects obsolete entities when previously saved chunks load later. Failed recovery retains its record and blocks further transfers in that dimension.

The server owns capture and conversion. Clients confirm only an expiring server-generated token. Snapshots arrive in bounded fragments when first tracking or previewing a craft; small door-state patches are sent on interaction; movement packets never contain full block NBT. The client verifies entity identity before applying the completed snapshot. Missing installed registry entries retain saved entity data in an unavailable, immobile state rather than deleting it.

## Ground controller and dismounting

The server accepts input only from the current driver, rejects old sequence numbers, and expires missing input after ten ticks. Speed is capped at 0.28 blocks/tick; acceleration is gradual, with stronger braking. Gravity and swept collision operate on cached component boxes; matching adjacent faces are merged without filling hollow space. Horizontal oriented boxes use separating axes against terrain, and angular sweeps check the outer hull throughout a turn. A grounded half-block step requires headroom and support. Movement stops at unloaded terrain and the world border. The driver predicts the same controller locally, reconciles sequenced input against precise server states and eases small position corrections. Observers interpolate server poses.

Turning still exhibits reported jitter. Position prediction checks do not prove angular smoothness; see the [smoothing and performance handoff](ground-vehicles-handoff.md) for the input acknowledgment, yaw correction and rider/camera work that remains.

Craft-wide targeting and collision lookup cover cells beyond the entity's origin chunk/section. The Configurizer ray-tests captured cells within six blocks and respects intervening terrain. A rendering fallback draws visible hulls whose origin section was omitted by vanilla's entity pass; full structure bounds cover decorative cells as well as collision geometry.

Safe dismount searches actual supporting collision surfaces with standing clearance, including partial blocks. It resets vertical motion and fall distance and reapplies the selected position after vanilla's final dismount placement. The same path handles placed Pilot Seats and assembled craft. Stationary interiors expose component collision rather than a solid enclosing box, so open doors allow passage. Rotated component boxes use conservative enclosing boxes for walking entities. Carrying unseated players on moving decks and additional seated passengers are outside this version.

## Rendering and performance

The renderer registers during Forge pre-initialization. Opaque/cutout/translucent geometry uses cached local VBOs; propulsion geometry is separate so brightness changes do not rebuild the entire hull. Programmable extended states use vehicle-local neighbors. Existing tile renderers receive a local world/camera context. Translucent block vertices are re-sorted when the local camera moves a block. Meshes are released on despawn, world change and resource reload.

Current limits bound work, but they are not a measured fleet-capacity guarantee. Collision still queries terrain for each merged box; animated tile renderers and mesh rebuilds can dominate complex craft. Transfer durability currently flushes world chunks and can cause a short assembly/parking pause. Initial meshes are built on the render thread rather than uploaded in frame-budgeted sections.

Further optimization should be driven by profiles:

- Measure server tick time and candidate collision counts on solid and hollow craft at 128, 512, 2,048 and 4,096 blocks, then fleets of 1, 5 and 10.
- Add a spatial index or section-level collision broad phase if merged-box terrain queries dominate.
- Split large meshes into bounded upload batches and share immutable geometry where model identity allows it.
- Reduce lighting rebuilds with section lighting revisions; measure translucent sorting and dynamic tile rendering separately.
- Cache snapshot bytes across observers and measure tracking bursts independently of steady-state input traffic.
- Exercise prediction and reconciliation under latency, packet loss and separate dedicated-server clients; keep collision decisions server-authoritative.

## Validation

The focused live vehicle checks exercise server-world conversion, gear boundaries, programmable materials, full snapshot/NBT round trips, non-cube and foreign blocks, a twelve-cell inertial dampener, inventory retention, movement, steering, braking, rotated geometry, wall collision, transfer cancellation/recovery and stale-entity rejection. Client checks cover renderer registration, snapshot delivery, programmable custom materials, parking preview, actual movement-key packets, propulsion ramps, doors, extended programmable ramps, channel-triggered occupied conversion, discovery of 4,096 occupied cells and rejection of 4,097, and supported dismounts after subsequent physics ticks. A chunk-packet regression verifies that serializing propulsion settings and canopy connection masks does not create neighboring tile entities in older saves. The client launcher also supports testing an isolated copy of an existing save with its installed mod set. The Pilot Seat suite checks model facings, upper-cell ownership, sitting and inventory padding.

Run these together with the existing build and live client regression suite. Further compatibility testing should cover unusual mod renderers, mod-specific network links after relocation, dedicated-server observers, latency, resource reloads, large hollow hulls and power-loss behavior. Do not infer arbitrary machine functionality from successful decorative block rendering.

## Lessons from the installed reference mods

These findings concern the installed 1.12.2 artifacts and their class bytecode. Upstream links identify the projects; their default branches can differ from these versions. None of these comparisons establishes performance on Vandor Labs structures.

| Reference | Verified implementation detail | Useful lesson and limitation |
| --- | --- | --- |
| AdvancedRocketry `1.12.2-2.0.0-257`, with LibVulpes `0.4.2-88` | `StorageChunk` implements `IBlockAccess`, holds block metadata and tile entities, exposes `copyWorldBB`, `cutWorldBB`, `pasteInWorld`, NBT and network serialization, and a `WorldDummy`. `EntityRocket` and `RendererRocket` consume the stored structure. The renderer contains display-list compilation. | Separate captured structure from entity motion. A bounding-box copy can include unrelated blocks and is not a substitute for connectivity detection. Rocket flight and renderer choices are not a ready-made ground controller. |
| MovingWorld `1.12-6.353` | `ChunkAssembler` has bounded recursive and iterative assembly paths and an assembly interactor. `MobileChunk` implements `IBlockAccess` and exposes a `FakeWorld`. `MobileChunkRenderer` has legacy/VBO render paths, dirty marking, block rendering through the fake world, and tile rendering. `ChunkDisassembler` handles restoration. | Closest structural reference. Prefer bounded iterative discovery and reusable geometry. A fake world is a compatibility layer, not proof that arbitrary Vandor tile callbacks are safe. |
| Davinci's Vessels `1.12-6.355` | `EntityShip` is layered over the installed MovingWorld implementation. | Ship-specific behavior is separate from the reusable moving-block layer. Study the latter first; buoyancy and flight are unnecessary here. |
| MrCrayfish's Vehicle Mod `0.44.1-1.12.2` | `EntityPoweredVehicle.onClientUpdate` obtains acceleration and turn intent and sends dedicated messages when those values change. `EntityLandVehicle` implements ground motion, speed-dependent turning, wheel animation and drift behavior. | Borrow input responsiveness and acceleration/braking patterns. Speed-dependent steering and reverse steering inform this controller; axle simulation and drift are outside scope; it also does not solve arbitrary block capture or programmable rendering. |

Project references: [Advanced Rocketry](https://github.com/Advanced-Rocketry/AdvancedRocketry), [MovingWorld](https://github.com/TridentMC/MovingWorld), [MrCrayfish's Vehicle Mod](https://github.com/MrCrayfish/MrCrayfishVehicleMod). Pin the exact compatible source revision and check its license before reusing implementation code. No dependency on these mods is necessary merely to reproduce the interaction pattern.
