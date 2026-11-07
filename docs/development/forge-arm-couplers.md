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

The actual existing payload-transform comparison passes 15,552 profiles, 93,317 assertions, 6,048 aligned poses including 3,168 intermediate poses, and 9,504 refusals. It compares all eight corners of multiple positive/negative unit cells and all six directions across six mounts, both size classes, modes, heights, angles and progress samples. This is a geometry-method boundary; it does not establish joined/Large world publication, block-state/NBT rotation, or ownership admission. Existing endpoint and restoration guards remain unchanged.

## Collinear constraint primitive

`CouplerLineConstraint` solves corresponding current parent/child anchors with an explicit preferred orientation and bounded free twist. It derives translation from those current anchors, rather than retaining initial head positions. Two Z-axis arm/coupler segments can therefore twist around Z while changing arm strokes; axial translation follows those strokes. Additional collinear anchors must have matching spans. Non-collinear, collapsed, reflected, nonfinite or over-budget inputs return an explicit failure instead of creating a rigid connection.

The isolated mathematical check passes 34,068 assertions on both Java 8 and Java 21. It varies both strokes during Z twist, verifies actual anchor coincidence and off-axis rotation, preserves preferred frames on arbitrary axes, and tests refusal controls. The existing bridge graph and vehicle runtime are not changed by this primitive. Loaded head discovery, actual controls, collision admission and saved/network integration remain required before claiming arm-coupler support.
