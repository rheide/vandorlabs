# Couplers on moving arms

This work applies to Forge 1.12.2 vehicles. Native Vehicle Mode is outside its scope.

## Existing behavior

Coupler blocks already store all six mounting faces. Locked opposing heads split a captured vehicle into physical sections. One articulated connection becomes a joint; the current bridge graph folds parallel connections and cycles into a rigid body. This is deliberate existing behavior, not evidence that arm-mounted parallel articulated couplers satisfy their constraints.

Existing joint state has yaw and pitch around a fixed pivot. Its second axis comes from the child body's horizontal axle direction, with a fallback when the axle is directly above or below the pivot. The movement proposal derives heading from that axle point. A vertically stacked, centered body therefore needs an explicit degeneracy test: its axle point alone cannot determine heading. Saved version-one poses and the existing two-short-per-joint network representation must continue to load and round-trip unchanged.

An arm payload already exposes its current mounted transform, including arbitrary mounting direction. Its grid endpoint helper currently admits only progress zero or one before applying integer translation and signed-permutation basis checks. That progress shortcut does not establish whether an integral intermediate Extender pose is parkable. A fractional or non-cardinal payload remains unsuitable for direct block placement even if its bounding box resembles a grid box.

Carried tile views are read-only and remote even on the server. Calling the ordinary arm tile update cannot establish authoritative carried arm movement; its remote branch intentionally returns without advancing physics.

## Constraint boundary

The requested two-pair arrangement along Z must permit rotation around Z while the arms telescope, with translation bounded by available stroke. Other axes retain the ordinary articulated freedoms when no additional couplers constrain them. Mounting direction is independent of the arm surface normal.

The implementation will distinguish rigid connections, ordinary articulated connections, and arm-supported constraints. Arm heads need stable identities combining the arm owner and payload cell, plus transformed head centers and axes. They must not become additional inventory owners. Dynamic body collision, selection, rendering, and parking must consume the same resolved transform.

A collinear multi-pair constraint must retain its free line axis rather than entering the old parallel-edge rigid fold. Additional pairs constrain the remaining freedoms. Unsupported or inconsistent arm layouts must give an explicit refusal, not silently behave as rigid couplers. General orientation/translation state will require a versioned extension; absence retains the existing yaw/pitch representation and behavior.

Grid parking requires the complete transformed payload to have integer cell centers, cardinal block-state orientation, no duplicate destinations, and an admitted ownership transaction. Integral intermediate strokes may qualify; fractional poses refuse ordinary parking. Safe Park can request a separately checked grid-alignment result through a shared resolver. Recovery must preserve opaque capability data and original failed-load snapshots, and pending asynchronous preparation must remain pending.

## Validation order

First freeze vertical full/slab coupler placement, actual section topology, loaded and saved poses, driven turning and lift, connection-point continuity, collision, and driver admission. A passing current case remains documented as passing; a changed behavior requires its failing-before reproduction.

Then cover arbitrary-mounted arms and captured coupler heads, all six mounting directions, regular and Large/joined arms, integral intermediate strokes, fractional refusal, moving body constraints, and collinear multi-pair limits. Reuse previous-release NBT and saved coupling/split fixtures, actual arm endpoint/recovery paths, Taxi/Hover and parking checks, and dedicated-server linkage. Focused checks do not constitute a release gate or authorize production-server changes.

## Vertical centered-axle correction

The released proposal reproduced a wrong-direction turn for a centered vertical hitch: a saved −20° joint with a −4° parent turn became −24° instead of −16°. The axle's horizontal displacement is zero, so using its point to derive heading amplified floating-point noise. The corrected degenerate branch derives heading from the previous child orientation in the new parent frame. It reuses the existing transform and scalar matrix entries; ordinary nondegenerate trailer proposals are unchanged.

The focused actual Forge client passes all four saved ±20° / parent ±4° cases, exact version-one snapshot/pose round trips, coincident head centers, collision checks, and integrated-server driver input through the normal vehicle update. This first 36-assertion check covers the existing vertical joint, not arm-mounted couplers or a new network format. Dedicated-server linkage also passes.

## Read-only arm grid geometry

`ArmPayload.gridMapping(arm, progress)` returns an immutable `ArmGridMapping` only for finite progress in [0,1], integer translation, and an orthonormal signed-permutation basis with determinant +1. Its `cell` and `facing` methods map payload-local cells and directions into carrier/world coordinates. Integral intermediate strokes are admitted geometrically; fractional translations, non-cardinal rotations and reflections are refused. Queries do not mutate payload snapshots or arm progress.

The actual existing payload-transform comparison passes 15,552 profiles, 93,317 assertions, 6,048 aligned poses and 9,504 refusals. Of 3,168 aligned intermediate parameter samples, 144 are genuine Extender strokes; the remaining samples are static poses. The sampled Rotating Arm interiors remain fractionally translated and are correctly refused even when their orientation is cardinal. It compares all eight corners of multiple positive/negative unit cells and all six directions across six mounts, both size classes, modes, heights, angles and progress samples. This is a geometry-method boundary; it does not establish joined/Large world publication, block-state/NBT rotation, or ownership admission. Existing endpoint and restoration guards remain unchanged.

## Collinear constraint primitive

`CouplerLineConstraint` solves corresponding current parent/child anchors with an explicit preferred orientation and bounded free twist. It derives translation from those current anchors, rather than retaining initial head positions. Two Z-axis arm/coupler segments can therefore twist around Z while changing arm strokes; axial translation follows those strokes. Additional collinear anchors must have matching spans. Non-collinear, collapsed, reflected, nonfinite or over-budget inputs return an explicit failure instead of creating a rigid connection.

The isolated mathematical check passes 34,068 assertions on both Java 8 and Java 21. It varies both strokes during Z twist, verifies actual anchor coincidence and off-axis rotation, preserves preferred frames on arbitrary axes, and tests refusal controls. The existing bridge graph and vehicle runtime are not changed by this primitive. Loaded head discovery, actual controls, collision admission and saved/network integration remain required before claiming arm-coupler support.

## Loaded head observations

`VehicleArmCouplers.observe` currently exposes an unbound, main-server-thread observation layer. Native and captured heads have separate stable address namespaces, a carrier cell, transformed point/direction, and scalar lock/input/articulation settings. Returned observations retain no tile, world, stack or snapshot-NBT references and are bounded by the existing vehicle block limit. Missing native data, unrecovered snapshots and unsupported nested moving frames refuse explicitly. Materialized attachment heads retain their payload addresses while reading the installed native tile, as described below.

Native settings read current installed `TileCoupler` fields. Captured settings read the committed payload's immutable cell headers, without constructing a nested `VehicleWorld` or deserializing its capabilities. Subsequent captured edits must publish a typed updated payload before a runtime consumes those settings; this observation layer does not yet implement editing or actuation.

Saved local-view checks pass 1,080 profiles and 14,041 assertions covering all six arm mounts and head facings, current stroke/rotation geometry, absent legacy input defaults, stable addresses, missing-data refusal, unchanged opaque snapshots, and no nested factory construction. A separate getter check records zero bytes for 10,000 scalar header reads; 32 defensive copies of the same 512 KiB opaque payload allocate 16,792,608 bytes. This is the header-read boundary, not full adapter or per-tick performance acceptance. Standard Java 8 assembly and dedicated-server linkage pass.

## Detached translated payload candidate

`ArmPayload.gridPlacement` builds an immutable ordered native-cell list and a defensive materialized payload tag for an integral translation, including intermediate Extender strokes. The original construction snapshot and live arm are unchanged. Mapped cells may lie outside the current craft box; the enclosing layout resolver must normalize all bodies together before journal publication. Existing known tile-coordinate translation is reused, while Coupler settings, unknown fields and opaque capabilities remain intact. Ownership journals refuse; fractional or rotated bases return no candidate. Rotated typed tile mappings are a separate extension.

This API remains unbound. It starts no arm restoration journal and does not authorize parking or publish blocks. A complete coupled layout must enter the existing vehicle transfer journal as one ownership transaction. Actual endpoint/mining, client ownership and abrupt cross-chunk tests remain required at that binding boundary.

Focused checks pass 15,552 mapping profiles / 131,909 assertions and 1,080 loaded-head profiles / 15,769 assertions after adding candidate comparisons, defensive output edits and exact Coupler tile preservation. These are detached candidate checks, not world publication acceptance.

## Typed rotated Coupler candidates

Rotated candidates now admit tile-free full blocks through the existing representable state mapper, full six-facing Couplers, and slab Couplers only while their half-height axis remains vertical. Half inversion is mapped explicitly; the existing slab metadata is unchanged. Other tiles and unrepresentable states refuse. This is still an unbound candidate builder, not a new runtime admission path.

A new optional `GridTileMapping=1` / signed-permutation `GridTileBasis` describes ordered `PlacedBlocks`. Decode validates a proper cardinal basis, every corresponding cell/state, exact Coupler tile identity, and the complete original tile tree after only the known x/y/z shift. Unknown own fields and Forge capabilities are not interpreted. Missing legacy markers retain the previous rule that rotated placed blocks cannot contain tiles. Unknown versions, mistyped/missing fields, altered opaque data, mismatched state/order and reflected bases refuse. Cardinal craft rotation conjugates the mapping basis and revalidates it; original construction cells remain separate.

Focused checks cover all 24 proper cardinal bases across 12 full and 16 slab metadata states (672 profiles: 416 representable, 256 explicit slab refusals), actual authored bounds, ordered two-cell identity, and unknown tile refusal. Loaded saved-arm checks also exercise the new marker, malformed data, opaque tree equality and all four cardinal craft rotations. Whole-layout solving, publication and player/endpoints/restart acceptance remain pending.

## Stable addresses at native endpoints

Materialized payload Couplers now retain the same `armRoot + originalLocalCell` address used while captured. Two readonly ordered endpoint accessors expose the saved position/state correspondence, without returning mutable payload data. Observations require the exact expected block/orientation at every materialized head and reject duplicate arm claims or missing installed heads. Installed native Coupler settings remain authoritative; old captured header values cannot replace a current input/articulation value.

The saved-view corpus now passes 19,297 assertions across 1,080 arm/head profiles. Each representable detached candidate is installed in an actual local view, compared with its captured counterpart for point/normal/address, then updated through the installed tile header and tested with that tile removed. This is an observation boundary, not vehicle movement, journal publication or a per-tick allocation claim. The coupled scene will still need cached descriptors and live scalar frame updates rather than rebuilding these observations for every pose.

## Bounded stroke fitting primitive

`CouplerArmConstraint` fits legal arm progress values for an explicit requested relative body orientation. Its callback evaluates the actual arm point paths; body translation is derived from the fitted current anchors. Each shared arm has one bounded parameter, so several heads cannot independently invent different progress for the same mechanism. The callback and input controls are read-only by contract. The result is accepted only after every point constraint is within 1e-7; exhausted strokes, invalid models/frames and unsatisfied orientations return explicit failure. No rigid fallback occurs.

The numerical primitive has at most 24 iterations, 64 pairs/arms, and 3,265 callback evaluations. A reusable workspace retains less than 160 KiB at maximum dimensions and less than 4 KiB for two arms. Output allocation and callback costs are separate from scratch reuse. The focused Java 8/21 check passes 1,455 profiles and 26,202 assertions, including actual shared eased Extender/Rotating/Level point paths, changed controls while Z twisting, single-pair orientation freedom and legal-stroke exhaustion. This is not yet active vehicle behavior or a world/collision/per-step performance acceptance. The initial runtime slice will admit only supported single pairs until staged multi-arm solving is complete.

## Detached arm pair topology

The saved-view gap fixture freezes the current native-only limitation: an actual captured Coupler head and opposing native head coincide, but native adjacency discovery sees no edge and the legacy disconnected-node rule folds the two sections rigid. This is not a claim that the player-driven arm path has been fixed.

Initial arm pair discovery matches scalar head points and opposite normals through a bounded spatial map, retains stable addresses, and refuses ambiguous or missing locked payload partners. It is used only to prepare an initial local-frame descriptor; movement must retain the chosen pair addresses rather than rediscovering after each pose. No global world index or world scan is introduced.

`VehicleCouplerTopology.withArmPairs` prepares detached sections and bridges, assigning a materialized payload head to its actual arm carrier. It preserves ordinary native graph behavior, while explicitly refusing unsupported rigid arm links and new arm cycles/parallel links instead of invoking the legacy rigid-collapse fallback. The existing active topology factory remains unchanged. The saved-view corpus passes 72 profiles / 792 assertions across six mounts, six head faces and legacy input absence, exact opaque snapshot reload, materialized/captured identity, prepared two-body bridge, and missing-partner refusal. Active pose/collision admission and actual player placement are still pending.

## Collision component provenance

`ArmPayload.collisionCell(arm, index)` adds a readonly correspondence from the existing cached collision order to the original immutable cell. Cache preparation records one cumulative integer per original cell, including empty cells; lookup uses a binary search and does not clone NBT, reconstruct a nested view, or transform geometry on a warm hit. Existing box coordinates, order and ownership are unchanged. Native materialized endpoints cannot claim captured-box provenance.

The actual released cell-emitter comparison passes 29 checks over 11 components, including empty-cell boundaries, exact source order, Coupler-only identification, invalid indices, native endpoint refusal and unchanged opaque data. Ten thousand warmed metadata queries allocate zero bytes; the seven-cell fixture retains 28 bytes of new primitive metadata. This establishes the provenance seam only. Active articulation still needs to use it when retaining exact head-pair self-contact exemptions.

## Typed live Coupler merge and compact wire foundation

`TileCoupler.currentData(original)` merges only its current known control fields and coordinates into a defensive copy of the identified original tile tag. It preserves future own fields and the opaque physical `ForgeCaps` tree, without invoking capability serialization. Existing ordinary `writeToNBT` behavior is unchanged. A real Forge capability dispatcher is installed explicitly in the standalone fixture: ordinary serialization produces a different external representation, while all 128 typed-merge cases / 2,369 assertions retain the original envelope and current controls, including legacy request absence and cleared optional fields. This helper is not yet bound to carried controls.

The optional `ARM1` STATE suffix has a 15-byte header (magic, version, layout token and count) plus 25 bytes per root (packed position, control revision, progress and target bit), at most 64 roots / 1,615 added bytes. It follows the existing optional joint section. No-arm packets remain exactly 82 bytes, or 108 bytes with three ordinary joints. Its 430-check real `VehicleNetwork.Packet` codec test covers exact byte round trips and malformed versions/counts/roots/revisions/progress/flags, duplicate roots/tails, trailing bytes and truncated fields. Complete historical 82/108-byte boundaries remain valid because the suffix is optional. The entire scalar suffix is validated before constructing records.

No runtime currently attaches or applies this suffix. The pending adapter must use a persisted lifetime/layout identity, apply only after the entity's actual state-order admission and before joint reconstruction, keep unmatched early updates in a bounded scalar queue, and persist accepted live arm poses before save/endpoint cloning. Wire size checks do not establish active update frequency, player authorization or moving-arm correctness.

The exact standard JAR also passes the existing focused articulation client: one/two/four/eight-segment trains, saved and invalid pose retention, limits, actual 82/108-byte network round trips, stale/repeated remote visuals, full/slab Coupler geometry/dialogs/icons, and real Survival mining beside a bent 35-degree vehicle. This is an adjacent compatibility regression for the codec and ordinary tile-write refactor, not acceptance of active arm-mounted motion. Dedicated-server linkage passes 1,450 shared classes / 135,289 references. No release gate or production-server action was performed.

## Actual carried-head failure reproduction

The focused player fixture now starts from a retained backing block and surrounding ground, places a regular Extender and both Couplers through their ordinary item callbacks, scans the physical two-body rig and captures it through the existing durable vehicle transfer. Both heads initially form one articulated bridge. A real pilot transmitter-use packet then reaches the configured carried arm while normal world ticks run the existing `ModularGearVehicles` adapter.

The released path fails after its first animated step: target false, progress 0.9583333333333334, captured payload, zero joints, and `Motion blocked; clear the stroke`. The fixture's desired retained-bridge assertion fails. Mounting, assembly and player input checks pass before that failure. This confirms the captured-head topology/contact path, not a missing generic arm ticker. The existing generic lifecycle and compact gear correction transport will be reused; the optional ARM1 codec remains unbound unless an actual ordering failure establishes its need.

The gated `VANDOR_LABS_COUPLER_ARM_ONLY=1` articulation focus retains this independent failing-before case. Default articulation validation is unchanged. A passing rerun after the active fix, followed by reversal/reload/ownership and motion-cost checks, remains required.

## Private single-pair runtime checkpoint

The private ReproLab path now retains a captured head's original materialized address and prepares its articulated bridge through the existing topology. Current arm anchor points determine body translation while the existing yaw/pitch representation remains intact. The existing `ModularGearVehicles` ticker and compact gear corrections remain the only lifecycle/transport. Desired-phase admission uses component provenance, the combined articulated endpoint check and a loaded conservative translated-body terrain sweep; it does not clone another `VehicleWorld` for each desired arm phase. Unsupported new parallel links/cycles still refuse explicitly.

The actual player rerun passes 723 assertions: retained mounting/support and ordinary item placement, durable source assembly, actual pilot transmitter input, complete retraction, independent world-space head coincidence and collision-safe pose throughout, actual reversal, and moving saved-owner replacement with exact authoritative arm phase followed by complete extension. The pair corpus additionally passes 72 profiles / 1,224 assertions, including saved intermediate phases and stable head identities. Dedicated-server linkage passes 1,451 shared classes / 135,880 references. A same-UUID replacement fixture initially attempted immediate spawn after deferred removal; synchronous fixture retirement fixes that test lifecycle without changing gameplay.

This checkpoint is inactive by default and requires both the private arm-runtime property and ReproLab. It does not establish arbitrary steering/mount combinations, visual arm/body interpolation under packet delay, captured-head interaction/mining/release, multi-arm line solving, complete parking conversion, abrupt recovery, or per-step cost acceptance. Those remain required before public activation. The conservative sweep and desired-shape preparation also need bounded cost measurements before hot-path performance claims.

Collision provenance now exposes the original ordered cell index as well as the cell. Captured head exemptions use that index's last materialized coordinate, including translated/rotated saved endpoints, rather than assuming its original payload offset. The independent released-emitter metadata check passes 40 assertions / 11 components; warmed lookup remains zero-allocation. Ordinary joints allocate no unused dynamic-anchor scratch arrays. The 72-profile private pair corpus and dedicated linkage remain green after this refinement.

The next actual-player negative reaches the captured head with a dismounted camera and real Configurizer entity-interaction packet, after all preceding motion checks. The released selection route opens generic vehicle Preview instead of the dedicated Coupler dialog. `GearBlock` already traces the captured original cell, but `VehicleSelection` keeps the arm root's SURFACE action. The regression expects the same dedicated dialog as a native head; no projection fix is included in this failing-before checkpoint.

The first generic-Preview capture is not valid evidence of captured-head behavior: post-dismount safe-exit positioning overrode its one-shot teleport and the actual ray hit a repulsor near the former seat. The fixture now waits for completed dismount, then admits both server and client final mode, slot, held item and position, and asserts the captured original-cell hit marker before a real interaction. The corrected route must be replayed against unchanged production before comparing a candidate.

## Captured control identity and retained envelopes

A captured head can carry an optional `CouplerControlIdentity` UUID. Once present it must remain unchanged through typed edits and materialized tile save/load; malformed or replaced identities refuse. `ArmPayload.withCouplerData` accepts only the Coupler's known settings/input/manual/operator/status fields and first identity assignment, retaining every other own field and the complete opaque capability tree. Its ordered endpoint correspondence remains intact. The provenance/opaque corpus passes 55 assertions, including lifetime/type/unknown-field/capability mutation refusals and materialized identity retention.

`GearTileEntity.currentData(original)` merges current known arm fields into the retained source tag without invoking external capability serialization. It clears obsolete optional own fields, keeps unknown own fields and original capability bytes, and is used by existing carried-arm snapshots and signal flushes. Thirty-two actual attached-capability cases compare every emitted current known field against the ordinary writer while independently demonstrating its external serializer divergence; the existing 128 Coupler typed-merge profiles also remain green (3,649 combined assertions). These are envelope/field checks, not abrupt transfer-recovery acceptance.

## Dedicated captured-head dialog route

The corrected pre-fix player fixture admits final server/client position after safe dismount and identifies the captured original-cell ray marker before interaction. Both sides hit that component, but the original route classifies the arm root as SURFACE and opens generic Preview. The candidate retains the root packet coordinate plus a separate captured address. A second intercepted path in `ItemConfigurizer.configureCraft` now delegates Couplers to the same `VehicleInteraction.activate` route instead of parking Preview. This delegates directly to configuration, without re-entering `player.interactOn`.

The dedicated native dialog now consumes a `CouplerControl` contract, while its native tile field remains available to existing callers. Captured controls validate current craft/root/payload identity, lifetime, authorization and actual transformed-head reach, and never instantiate a nested tile or capabilities. Existing bounded `MessageCoupler` window edits are reused. Captured Forge GUI coordinates use byte-bounded root/cell fields in a reserved Z range entirely outside valid world bounds; the original native 18-bit route is unchanged. A 116-check address corpus covers root 63, cell 127, ordinary negative world coordinates, the old sentinel and unused-bit refusals.

The exact corrected player comparison passes 777 assertions, including the preceding complete stroke/reversal/saved replacement and actual captured-head Configurizer opening `GuiCoupler`. This establishes dialog opening and its target identity only. Automatic channel/manual input, actual settings packets, denied/stale sessions and safe release remain separate pending checks; captured runtime remains private.

## Frozen Source input transitions

The independent installed-tile oracle invokes actual `TileCoupler.setChannelSignal` / neighbor power sampling across 3,072 profiles and 12,288 transitions. It covers both inversion choices, absent/false/true manual override, legacy requested-field absence, four-step channel and local redstone sequences, input-edge override cancellation and the physical locked state. The deterministic ASCII corpus is 279,552 bytes (expanded SHA-256 `4598c908436266a8959ef39e2180ae0b9019ae35fe6e22b16837f8881b12f724`), repeated against unchanged Source policy. Target identity and direct channel/local callback sanity checks prevent recording a detached tile that silently skipped its callback. This establishes scalar input behavior only, not player/manual dialog transport or carried release.

`CouplerInput` now shares the manual-choice and real-power-edge rule between installed and captured controls. The installed Source exporter remains byte-identical after extraction, and a separate pure replay matches all 12,288 frozen transitions. Typed Coupler/arm envelope checks and dedicated linkage also pass. This policy extraction is CPU/source-callback validated; the next connected-player captured-input check remains required before claiming carried channel/manual behavior.

Detached parking preparation can now read saved head aliases independently of the private driving switch. Its section graph retains every parallel/loop constraint rather than passing those links through the unfinished runtime bridge-only admission. The two entry points share the existing carrier/section union and strict ambiguity checks; live driving keeps its original refusal. A four-phase saved-layout check retains both parallel links, preserves complete opaque source data and leaves live topology/private activation untouched. Whole-layout phase selection, native publication and collision validation remain separate work.


## Captured input and transport checkpoint

The installed player comparison first reproduces a parent channel level of 15 with a stale captured request. The same path now passes 855 assertions: dedicated dialog option packets, ON/OFF channel inputs through the physical parent, plain manual release intent, and genuine power-edge override cancellation, in addition to full stroke, reversal and moving saved replacement. Captured local power reads use a bounded immutable block-state projection; unsupported producers remain explicitly pending without nested tile/capability creation. Changed known controls are merged into the latest arm data, staged and encoded before publication; unknown own fields and opaque capability data are retained.

Actual application FML payload measurements compare the same GUI actions. Opening retains one snapshot (7,968 bytes), while redundant tile responses decrease from two / 1,608 bytes to zero. Seven configuration requests remain seven / 112 bytes; responses decrease from twelve tile messages / 9,714 bytes to five / 4,069 bytes. Two channel edges publish two tile responses / 1,653 bytes. The existing compact arm transport remains 100 motion messages / 5,600 bytes over the complete motion portion. Counts exclude TCP framing/compression; asynchronous STATE totals are not a throughput comparison. The fixture's Netty counter only reads buffer indices and counters, with no world access.

The power-projection corpus checks known positive/negative producers, tile-dependent and external pending producers without factory calls, and zero warmed allocation. Java 8 assemble, all 12,288 input transitions, and dedicated-server linkage pass. Physical release remains attached with an explicit pending status, and multi-pair driving remains refused; this checkpoint does not enable the private runtime publicly or establish its large-payload per-step costs.


## Detached captured release envelope

`ArmPayload.releaseCoupler(originalCell, locked, knownFields)` prepares an immutable captured-only edit. It validates the same known-field/lifetime/opaque boundary as ordinary captured configuration, changes only the physical latch state, and preserves ordered endpoint states, unknown payload headers and capabilities. Each proposed released head receives `VehicleArmCouplerReleased`; native tile save/load retains this marker. Marked heads are excluded from both geometric and saved-alias pair discovery, allowing an unclicked requested head to remain latched after a real split without inventing a partner. Unmarked missing captured partners still refuse. Only a genuine new owned pair may clear the marker.

Focused tests cover all six mounts/facings, requested latched orphan retention, coincidence/alias false re-pair, unchanged original snapshots, capability retention, native endpoint refusal and tile materialization. Both default and private pair discovery pass (72 profiles each), as do typed current-data and dedicated-server linkage checks. This is detached preparation only; durable release publication and its actual player/restart acceptance remain separate.


## Retained snapshot writer baseline

A focused CPU probe invokes the actual payload and current-arm writers at 4, 256 and 4,096 retained cells, with a 32 KiB opaque root capability tree. For 100 warmed calls, `payload.write` allocates approximately 0.318 / 8.29 / 123.16 MB; `GearTileEntity.currentData` allocates 4.26 / 19.65 / 233.54 MB. The largest current-data batch takes approximately 39.5 ms on the measurement host. These are writer-only measurements, excluding signal compression, collision preparation and the complete tick. They establish that rebuilding full retained arm data every progress step is a remaining public-runtime cost blocker; scalar-model allocation results do not cover it. The source envelope remains unchanged and the probe does not deserialize external capabilities.


The unbound `Cell.withGearPose` alternative copies only the seven approved scalar pose fields into a new private root map, sharing frozen private child tags. Public NBT access and serialization remain defensive. Ordinary cell construction creates a new opaque configuration identity; pose revisions retain it. Full-tree comparison with the complete writer covers phase endpoints, target, signal and blocked choices, while export-mutation tests protect both old and new snapshots. At all three payload sizes, 100 pose overlays allocate approximately 0.245 MB; the 4,096-cell full writer still allocates 233.55 MB. The measured overlay batch is approximately 0.155 ms versus 38.65 ms for the full writer. This is a CPU seam comparison, with no tick/cache binding or complete runtime performance claim yet.


The per-progress carried-arm snapshot now uses the immutable pose overlay; configuration, capture and ownership paths retain the full writer. The existing exact built-JAR player fixture passes 877 assertions after this binding, including complete stroke, reversal, moving saved replacement, dedicated dialog and parent-channel/manual behavior. Compact motion remains unchanged. This acceptance precedes the new parking-hold integration and does not cover that separate input/progression gate. Collision-description serialization remains a separate optimization pending focused proof.
