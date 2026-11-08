# Continuous Coupler pose projection

`CouplerPoseConstraint` is a portable, world-free contact projector. It complements the fixed-orientation `CouplerGraphConstraint`; it does not replace parking's discrete integer-layout solver or admit ownership, terrain collision, ordinary joint angles or rigid-arm orientation.

## Inputs and result

`solve(Model, Graph, Stroke[], boolean[] driven, RigidTransform[] rotationPrior, Workspace)` reuses the existing graph, detached point callback and bounded stroke types. A driven arm is fixed exactly at its requested next phase. Passive phases start at their bounded requested/current value. Each non-root body starts at its preferred proper rotation and remains free on SO(3). The graph root has identity rotation and zero translation. Supplied translations must be finite but are ignored.

The workspace derives translations through a connected spanning tree. Every parallel, cycle and same-body link remains a residual. Projected damped Gauss-Newton uses passive phase scalars and three local rotation increments per non-root body. Minimum-norm corrections retain unconstrained prior axes; preferred rotations are not hard constraints. Contact accuracy is never exchanged for a smaller preference penalty. Before acceptance the kernel independently checks every link, including tree links, to a maximum point error of `1e-7`.

An accepted result owns its phases, rotations and translations. Primitive getters avoid readback allocation; `transform(body)` returns a defensive transform because the legacy transform type exposes its matrix array. Failures export no trial pose and leave the input/previous accepted result unchanged. `FRAME` means invalid/unsupported input or model output, `UNSAT` means this bounded local projection found no admitted fit, and `BUDGET` means work stopped at an explicit limit. `UNSAT` is not a global mathematical infeasibility proof.

## Bounds and cost

The existing limits remain 64 arms, 128 bodies and 512 links. Workspaces are serialized, reusable and retain no model or world reference. The default deterministic scalar-work budget is eight million units, with a configurable maximum of 64 million. The budget charges finite differences, point processing, Jacobian filling, normal equations, elimination and trial evaluation. A dense normal-equation pass is refused before execution if it cannot fit. At most 32 iterations and 8,192 rigid residual evaluations are permitted. Callback invocation/point charges and callback counts are exposed; arbitrary callback internals must independently remain bounded and detached.

Phase perturbations call the model; pure rotation perturbations reuse captured construction points. Model input phases are copied into reusable read-only scratch, and an attempted callback mutation is rejected. All trial arrays and SO(3) updates are allocation-free after workspace construction. Returned immutable result storage is allocated separately. The reported workspace footprint covers its arrays/overheads and excludes the caller-owned immutable graph and results.

## Focused evidence and remaining boundary

Portable checks independently reconstruct every contact and proper rotation. They cover changing hard commands with passive Z-line telescoping, free axial priors, single-pair freedom on all three axes, compatible/incompatible hard commands, exhausted strokes, cycles/same-body loops, nonzero graph roots, invalid frames, input/result immutability and budget refusal inside finite-difference work.

The Source-backed fixture compares against actual `ArmPayload.transform` points over all six mounts, EXTEND/ROTATE/ROTATE_LEVEL, both sizes and three heights. Its paired Z-line changes both the hard command and preferred axial rotation. Warm two/four/eight-arm measurements include the detached Source callback and all kernel trials; owned result allocations are counted separately. The maximum 128-body/64-arm/512-link inconsistent fixture returns `BUDGET` before dense algebra, rather than attempting an unbounded solve.

This checkpoint has no runtime receiver, persistent layout, collision forecast, ordinary joint-angle admission or public activation. In particular, a proper rotation alone does not establish the existing ordinary joint's physical angle limit. Runtime command classification, captured frame freshness, collision/terrain validation and last-valid-pose publication remain separate responsibilities.
