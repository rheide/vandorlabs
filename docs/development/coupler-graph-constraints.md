# Detached coupler graph constraints

The continuous graph fitter prepares scalar arm phases and body transforms for an explicit requested orientation. It does not change a world, admit an ownership transfer, authorize an actor, install an articulation graph or publish a motion packet. The existing runtime remains responsible for those boundaries.

`CouplerGraphConstraint.Graph` retains every closed contact, including parallel links, cycles and same-body contacts. A deterministic spanning tree derives body translations from the requested proper rotation matrices and the current arm-head points. Remaining links supply the residual equations. Only arm phases are numerical unknowns; the fitter does not add three translation unknowns for every body or rigidly collapse articulated loops.

The graph root translation is zero. Supplied transform translations are checked for finiteness and ignored; the caller composes the accepted construction-frame transforms with the physical craft root. Disconnected graphs refuse explicitly. Ordinary native nonarticulated links may be combined before fitting; nonarticulated arm-mounted links remain unsupported. Requested orientations are preserved exactly, so a single articulated pair retains freedom about each axis. Multiple pairs can constrain those choices geometrically, and exhausted strokes refuse instead of falling back to an unrelated rigid pose.

The fitter is a local bounded damped least-squares search, not a global impossibility proof. It permits at most 128 bodies, 512 contacts, 64 arm phases, 24 iterations and 3,265 model evaluations. Every phase remains within its supplied legal interval. Nonfinite frames, invalid strokes, incomplete model output, disconnected graphs and exhausted evaluation budgets produce explicit failures. Reusable workspace owns bounded arrays and no model or world references. Accepted result matrices are returned defensively.

## Source adapter

`VehicleArmConstraintFrame.prepare` requires the actual owning server thread and an exact current local view. It snapshots an explicit current configuration, not a cache keyed only by motion revision. Carried configuration changes do not necessarily advance that revision. A caller must prepare another frame when controls or settings change and must revalidate current ownership before any later publication.

`VehicleArmConstraintModel` retains only scalar pose descriptors and endpoint coordinates. It freezes mount orientation, current eased-motion parameters and the exact grid-alignment endpoint corrections. Capture verifies the descriptor against actual `ArmPayload.transform` queries before retaining it. It neither creates a nested payload view nor copies tile NBT or capabilities. Materialized aliases are resolved back to the original payload cell; their last placed coordinates are not substituted for a trial phase's construction point.

The Source model deliberately reuses `GearMotion.Pose` and its vertex primitive. These callbacks can allocate pose/vector objects; an allocation-free scalar fitter is not an allocation-free Source solve. The focused fixture reports actual warmed model and original callback costs separately. Its retained model estimate includes the latest reusable pose references.

## Focused evidence and remaining boundary

`testCouplerGraphConstraint` checks each accepted head equation independently, including two Z-line articulated pairs while arms telescope, changed commands during Z rotation, ordinary single-pair freedom on all three axes, stroke exhaustion, three-body cycles and same-body contradictions. It measures solver work between model calls separately from accepted result construction.

`testVehicleArmConstraintModel` compares the detached model directly with actual Source callbacks over all six mounts, both arm sizes, four directions, three heights, all three motion modes and varied angle ranges. It verifies explicit recapture after changed controls, prior-frame immutability and the absence of a payload/capability view. `testVehicleArmConstraintFrame` checks actual captured/materialized correspondence and the ordinary native rigid-link union in detached local views. These are CPU/callback comparisons, not player placement, live driver steering, durable transfer or packet acceptance. Runtime binding and its actual player path remain separate work.
