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
