# Landing-angle settings and endpoint admission

The shared `ArmLandingAngles` policy separates saved raw angles from effective
landing angles. An absent `ArmLandingAnglesVersion` retains the original 0°/90°
landing stroke; unrelated configuration edits preserve both absence and raw
values. Version 0 is explicit legacy behavior, and version 1 enables configured
angles. Unknown versions and malformed tags are refused before mutation. Ordinary
bare-arm angle behavior is unchanged.

The choice field is `LandingAnglesVersion`. New angle edits and clipboard
captures are explicit settings. Capturing an old landing assembly exports its
effective 0°/90° values, while applying an old clipboard without the marker
preserves a landing target's current angle policy. Settings-only Undo restores the
previous effective settings through the same admitted adjustment path.

`LandingAnglePolicy` projects every corner of every complete reserved foot cell
through the existing authored `GearFootProjection`. Both endpoints are checked
for an integral proper cardinal basis and integral cell corners. The immutable
result separately records native endpoints and upright support endpoints. A
caller-owned single-descriptor cache invalidates on geometry/settings/topology
changes; motion-only queries reuse it. Configuration does not inspect terrain.

`GearMountFrame` shares the exact six mounting bases, including signed-zero
behavior. `GearDialog` defines logical controls and layout for platform widgets.
`ArmAngleAdjustment` preserves the existing Source temporary-End stroke,
collision refusal/reversal and saved in-flight fields. Completing a carried
adjustment uses the physical parent's authority, updates every same-owner
member, and persists its new configuration without replacing opaque capability
preimages. Client cosmetic clocks do not publish those authoritative changes.

Source Taxi support uses the actual authored foot contact boxes, including their
posed bottom and horizontal contact location. Only downward mounts can provide
Taxi support. Its target selection accepts a valid upright End endpoint when
Start is unsuitable, while existing ground/collision checks remain authoritative.

## Focused verification

- `testArmLandingAngles`: 1,728 geometry profiles and 1,382,113 checks, including
  old physical 0°/90° interpolation, socket offsets, hinge/kink combinations and
  bounded shared controls.
- `testArmAngleAdjustment`: 15,625 scalar profiles and 1,232,506 comparisons with
  the prior Source adjustment/reversal/reload expressions.
- `testGearMountFrame`: 473,922 literal six-mount and native-grid comparisons.
  The isolated shared checks pass on Java 8 and Java 21.
- `testDedicatedServerLinkage`: shared gameplay paths retain dedicated-server
  linkage; these additions use six-coordinate AABBs and explicit midpoints.
- `testclient/test_landing_angles.sh`: real Configurizer dialog and settings
  packets, legacy raw-angle preservation, visible sliders, actual adjustment and
  phase-0-only support, reload/reversal and invalid-endpoint refusal.
- With `VANDOR_LABS_LANDING_ANGLE_TAXI_ONLY=true`, the same runner exercises a
  real queued joined-Wheel Taxi/Hover lifecycle. It covers ordinary Pilot Seat
  boarding, actual ascent input, refused Taxi entry, carried adjustment without
  a pose jump, every joined member's saved settings, queued parking, native
  reload/reversal, an untouched legacy hinge, opaque capabilities and 37 cargo
  items. This passed 577 checks with OptiFine G5 and an 8 GB heap.

The accepted Source carried candidate SHA-256 is
`ffb1f699f28bff1c32424e554dd07c19d455f381715e28090a63bf74652a3912`.
A fixture-only terminal guard was added afterward to suppress repeated completion
callbacks during shutdown. This is focused validation, not a release gate.
