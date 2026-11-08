# Coupler angular admission

`CouplerAngularAdmission` is a world-free Java 8/21 check for proposed proper rotations. It does not install a pose, clamp a solver result, choose topology, validate contact positions or admit terrain/ownership. Solver and inertia-prior output remains a proposal until the caller completes every applicable check.

## Directed ordinary v1 joint

The actual legacy joint rotation is `RY(-yaw) * Rot((-hz,0,hx), pitch)`. Its horizontal direction comes from the child's collision bottom-centre minus the joint pivot, not the head-facing property. The actual constructor normalizes that horizontal lever only when its length exceeds 0.01; otherwise it uses `(hx,hz)=(0,1)`. This includes a centred vertical UP/DOWN connection. Facing changes pivot placement; it does not introduce another Euler convention.

Legacy motion/network handling clamps both components to ±75 degrees, then scales both together until their combined proper rotation is within 75 degrees. Saved v1 admission checks each component and the actual matrix's total rotation, with a one-millionth-degree total-angle tolerance.

`ordinary(relativeR, leverX, leverZ)` preserves this directed two-degree-of-freedom manifold and fallback. It reconstructs the legacy rotation and refuses out-of-plane directions rather than widening the manifold to accommodate a solver result. Reversing a combined yaw/pitch transform does not generally remain in the same directed manifold: order reverses, and the caller must use the correct re-rooted legacy frame/lever. Pure-axis reversals and the actual legacy directions remain valid.

## Explicit multi-pair line

`line(relativeR, parentLineXYZ, childLineXYZ)` is available only after the caller proves an actual multi-pair line classification with ordered corresponding contacts. The directions belong to the respective construction-body frames. The check normalizes them and requires the relative rotation to map the child direction onto the parent direction. Degenerate lines are refused; no axis is fabricated. Contact positions, span lengths, coincidence and all remaining constraints must already be validated externally.

This first line policy inherits the existing total **75-degree** rotation envelope. It adds no Euler roll or twist clamp. A proper ±Z rotation can therefore preserve a Z contact line even when that direction lies outside an ordinary joint's lever-derived manifold. A wrong-axis rotation or a reversed single correspondence is refused. This is explicit line freedom, not a global relaxation of ordinary joints.

Both APIs take a row-major relative rotation in the directed neutral construction frame, normally `parentR^T * childR`. Common world-root rotation cancels there. The caller owns neutral-frame selection for moving mechanisms; the helper does not infer it from head facing, arm phase or a proposed world matrix.

## Results and numerical bounds

Results are `ADMITTED`, `INVALID`, `LEGACY_DIRECTION`, `ANGLE` or `LINE`. Arrays and scalars are never modified or retained. Each call uses constant scalar work and creates no result object or trial allocation. Nonfinite, malformed or reflected matrices are refused; matrices are not repaired or projected.

Proper-rotation validation uses 1e-7, ordinary reconstruction uses a component difference of 1e-9, and extracted ordinary components have a separately declared 1e-9-degree roundoff tolerance. The inherited total-angle tolerance remains 1e-6 degrees. Ordered unit-direction comparison uses 1e-7, with line lengths at or below 1e-9 refused. These tolerances serve different checks and are not interchangeable.

## Independent evidence

Before implementing the helper, an independent Source exporter froze the actual private v1 Joint constructor and actual `RigidTransform.joint` / `limitedAngles` output. Its 20,280 records cover all six facings, horizontal/centred vertical levers, values below/equal/above the 0.01 threshold, positive/negative boundary angles and clamped proposals. Saved-read labels reproduce the legacy read expression; this is actual Source math evidence, not a claim that a full world deserialization callback ran. The 2,494,448-byte reference has SHA-256 `02b586262faefba9dbf59acb372476908fe13b6b35c39cedc1cf06026e3a52f3`.

Portable replay checks that frozen reference plus rotated roots, ordinary directed reversals, explicit-line reversals, ±Z freedom, wrong axes, over-limit rotations, invalid/reflected inputs and unchanged caller arrays. Warm allocation measurements use repeated 20,000-call batches to distinguish first-measurement JVM overhead from settled calls. No runtime articulation, player motion or public binding is added by this slice.
