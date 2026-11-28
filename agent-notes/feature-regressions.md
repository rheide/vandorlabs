# Feature regression notes

Read the matching section before changing one of these areas. Each note records a past
regression and the coverage that catches it. Keep notes here rather than in `AGENTS.md`,
which every agent session loads.

## Vehicles and VehicleWorld

- Failed vehicle or turntable deserialization must leave the entity inert and retain its original snapshot NBT for the next save. Do not keep a partially installed live snapshot after an installation failure.
- Vehicle/turntable/copy preflight must cover previous-release blocks with external `ForgeCaps` on Vehicle Controllers, Pilot Seats, adjacent programmable tiles and supported vanilla tiles such as signs. The synthetic VehicleWorld may not reserialize third-party capabilities identically; preserve the original opaque capability NBT through assembly, rotation and parking while still rejecting changes to the block's own tile fields. Exercise the old saved layout, not only fresh 2.6 tiles.
- VehicleWorld is a read-only simulation view and deliberately has isRemote=true even on the server. Authoritative carried-control checks must use its parent world, and publish through VehicleSignals instead of registering outputs on the view.
- VehicleWorld explicit chunk requests must initialize capabilities against the physical parent world, while retaining read-only snapshot block/tile access. Its remote flag does not make it a WorldClient on a dedicated server. Exercise the hardness-hook path with the installed Thaumic Augmentation and previous-release controller NBT using `test_server_startup.py --vehicle-world-only`; ordinary no-chunk reads and integrated tests miss this failure.
- Overhanging diagonal walls and canopies can touch blocks outside their reserved cells. Discovery must follow bounded actual collision surfaces, require overlap on two axes to exclude corner-only contacts, and retain loaded-chunk and scan limits. A six-neighbour flood fill alone can leave attached ceiling blocks behind and trap the craft.
- Vehicle discovery must use only a slab's own empty half to reject vertical attachments. Do not require decorative neighbours' selection bounds to meet exactly: diagonal wall bounds can stop short of their cell and leave ceiling slabs behind. Check symmetric top-slab floor exclusion and bottom-slab ceiling capture.
- A cockpit is not a substitute for a dedicated Vehicle Controller in new assembly or mode selection. Preserve legacy snapshot loading and safe parking recovery without a controller. Vehicle Status must report the missing controller before offering mode controls.

## Vehicle ramps and Ramp Controllers

- Ramp Controller Slab keeps a horizontal half-height collision/model housing for every active face. Its metadata stores six faces and two halves; activity comes from the tile actual state. Do not consume another metadata bit for activity or rotate the slab body vertically for UP/DOWN scan faces. Draw native half-height side panels and test configured item padding. Minecraft 1.12 requires square static atlas sprites: place the native half-height panel in a square atlas and select its half-height UV region, rather than stretching the artwork or shipping an invalid rectangular static sprite.
- Ramp Controller scan direction and horizontal travel direction are separate. Preserve existing horizontal metadata and save horizontal travel when placing a vertical scan face. Test all six active-face models and capture/recovery above and below.
- Vehicle ramp regressions must include saved generated `controlled_ramp` cells alongside their controllers. Check capture, all cardinal rotations, exact tile data, parking and recapture; controller-only fixtures miss part-specific clock fields and stale saved map geometry. Run the `vehicle-ramps` live focus.

## Cockpits

- Cockpit toggles/sliders must use the shared RedstoneChannelLatch bank, including per-channel saved levels; independent ON sources prevent other linked screens from turning them off. Pulses remain timed sources. Test cockpit/regular controls in both directions, partial overlaps, saved/reloaded levels and carried consumers. Persist pending controls before cloning ramp endpoints so geometry replacement cannot restore a stale latch.
- Cockpit display capacity is six rows for screens and three for smaller touch panels. Append extra control IDs after legacy controls to preserve operation indices; validate limits on the server and test six rendered rows and seated hit mapping.
- Cockpit Redstone overlays must use square-pixel layouts derived from the authored surface aspect, with the same slider regions used by hit testing. Reuse the batched screen renderer to reset shared buffer translations and layer solid controls before text. The focused cockpit suite checks rendered glyph proportions, buffer contamination, clicked-page selection, four-step throttle limits and both saved throttle layouts.

## Arms and Extenders

- For moving arm changes, start a focused live fixture from the exact player placement and activation sequence, including the mounting block, surrounding ground and ordinary payload. Keep supports in place and advance every member tile as a world tick would; testing only the controller or endpoint transform can miss rebuilds and blocked strokes. Assert progress, source and target ownership, click/ray access, reversal and reload. If a move is refused, restore the last valid endpoint immediately when possible and retain a recoverable snapshot if restoration itself fails; a blocked arm must not silently leave its attachment hidden or unselectable. Reproduce a reported regression with a failing fixture before claiming its motion is fixed, then pass that same fixture on the built JAR. If the reported world state is unavailable and the closest fixture passes before the fix, say explicitly that the exact motion failure remains unconfirmed.
- Grid-aligned retracted Extender payloads must be native world blocks on all six mounting faces. Preserve displaced arm sections separately from payload ownership; never encode the former reserved-cell limitation as an expected test result. Run `arm-endpoints` for regular/Large joined platforms, edits, disconnected corners, reload and empty extension, and the endpoint partial-save phases of `test_arm_recovery.sh` when changing that handoff. Fractional endpoints still require captured-payload mining coverage.
- Restored arm sections need client tile data after journal proxies are published, even if their block state is unchanged. Endpoint tests must compare client mounting direction and controller ownership and assert only one assembly renderer after partial and complete native-payload mining; server-only block checks miss duplicate standalone models.
- Rotating-arm swept bounds must not grow along the world-space hinge axis. Face contact beside that axis is not an obstruction. Keep the saved wall-mounted pair in the `arm-wall` focus, and verify actual obstructions still refuse safely and permit retry.
- Arm regression coverage must include joined and independent assemblies, regular and Large sizes, all mounting faces and both rotating payload modes. Exercise edits at every materialized endpoint, then reversal and complete payload removal; a successful independent-arm stroke does not establish joined behavior. Keep exact saved-world reproductions in the shared Linux/Windows live focus.

## Copy Controllers and Turntables

- Shared surface changes must cover Copy Controllers as well as Turntables. Mine both a shared drawing root and another member, assert opaque surviving faces in the client renderer, and exercise rebuilding and expanding pads through temporary holes or unequal sizes. Invalid layouts must not retain a missing renderer root or stale copy area; topology notifications must settle without continuous rescanning. Run the `copy-surfaces` live focus.

## Rendering, shaders and animation

- Drive cosmetic mechanism animation with a pause-aware client clock rather than repeatedly comparing client world time with server start timestamps. Repeated packets must not restart motion, and vehicle tile replacement must retain visual state by world/position. Keep authoritative collision and saved timelines on the server; test clock corrections and reversals with `testCockpitVisualMotion` and the focused cockpit client check.
- Vehicle main-view visibility bookkeeping must ignore shader shadow-map draws. OptiFine renders the same entity into its shadow target before the main view; counting that as a main draw suppresses the hidden-origin fallback for interior cameras. Test separate shadow/main framebuffers, an origin behind the camera, forward/side look angles, tile rendering and the previous empty-frame negative control.
- Vehicle VBOs must retain an immutable copy of their build format and derive pointer strides/offsets from it. Shader format changes invalidate cached meshes. Preserve extended component payloads when combining records; OptiFine's addVertexData rewrites block/entity IDs. The focused render-regressions client checks vanilla/extended record parity separately from hardware shader-pack acceptance.
- Filled diagonal caps and adjacent flat wall contacts must be tested on actual emitted geometry for Inside/Outside/Both fills, all facings and inverted placement. A narrow flat wall does not seal the wider filled cap.

## Networking

- Network optimizations need before/after byte or message counts and round-trip checks. Keep world access on the server thread, preserve authorization/container/loaded-chunk checks, and bound decoded lengths before allocating.

## Creative catalog and JEI

- Hiding a block's creative tab does not hide it from JEI: its registry scans treat `Item.getCreativeTabs()` returning `{null}` as an uncategorized item. Default block items use `ItemCatalogBlock` to advertise an empty tab array when hidden; preserve their registry IDs. Use `test_viewscreen.sh --focus creative-catalog` with `VANDOR_LABS_TEST_JEI` set to a JEI JAR to check creative/search enumeration and the actual browser registry without running unrelated gameplay matrices.

## Optional mod adapters

- Update `docs/mod-compatibility.md` in the same change whenever an optional-mod adapter, copy, transfer, map or rendering integration is added, widened or removed.
- Resolve installed-mod members by exact descriptor (`MethodHandles.publicLookup()`), never with `Class.getMethods()` or `getField()` on a mod's tile or unit classes. Enumerating public members resolves every signature: Thermal Dynamics' `TileGrid.getBaseIcon()` names the client-only `TextureAtlasSprite`, so dedicated servers refused every carried duct with "Cannot preserve the installed transfer buffer" while integrated clients passed. Run `testclient/test_server_startup.py --thermal-transfer` with the Thermal mod set (CoFH Core/World, RedstoneFlux, Thermal Foundation/Dynamics, CodeChickenLib) after changing transfer-network adapters; the `vehicle-duct-fuel` live focus covers the same layout on a client.
- Optional building adapters need installed-mod metadata/orientation/NBT matrices plus the `foreign-buildings` live focus. Keep RF/fluid/network families deferred unless explicitly requested. Preserve native furniture pairs and constructor-only TV dimensions; carried Galacticraft Screens need client-only visual initialization without native `onLoad` callbacks or position packets. Keep incomplete/linked structures and unknown data refused.
