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
