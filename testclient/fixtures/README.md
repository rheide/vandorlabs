# Arm journal regression

`arm-pending-detach-2.6.nbt` contains the block states, tile data and pending
attachment journal for a two-section upward Extender Arm with a smooth-andesite
payload. It preserves the 2.6 failure state in which directory synchronization
failed and left the controller locked. Operator identifiers and the original
filesystem path have been removed.

The `arm-live` focus imports these cells into a disposable world, processes the
journal through the normal world-tick handler, and checks recovery, retraction,
reversal and mining. It then places and activates fresh regular/Large Extender
and Rotating Arms on all six mounting faces. These checks use real disk journals
and real world ticks, without the matrix bypass. The fresh fixtures send real
client interaction packets and check configuration and dismantling afterward. Two
additional chest-payload fixtures check that all 37 diamonds survive the complete
extender and rotating-arm cycle.

Run `bash testclient/test_viewscreen.sh --focus arm-live` on Linux or
`python testclient/run_windows.py --focus arm-live` on Windows. Both launchers
select this saved fixture; the release gate includes this focus.

`arm-rotating-replacement-2.6.nbt` retains the three-section rotating arm and its
nearby pillar from the second reported world. Operator identifiers are removed.
The live check finishes the saved stroke, mines the payload, extends the empty
arm, places a replacement and retracts it again, twice. It asserts normal block
mode and an unlocked controller after each replacement.

Additional focused checks:

- `arm-mining`: actual Creative click and Survival held attack on a retracted
  payload, including chest contents and preservation of both supporting arms.
- `arm-joined`: all mounting faces and both sizes, hollow rectangles, disconnected
  corners, carried inventories, six-arm 2×3 layouts and three-arm rotating rows.
- `arm-riders`: a standing player, reversals and independent client rendering
  while the integrated server is delayed.
- `test_arm_authorization.sh`: normal shutdown and fresh-process login, including
  Copy Controller surface refresh without reconfiguration.
- `test_arm_recovery.sh`: forced process termination at durable ownership
  boundaries, followed by a fresh-process recovery check.

`arm-docs` captures construction stages separately from the asserted movement
regressions.


The `arm-wall` focus imports `arm-wall-rotating-2.6.nbt`: two independent,
two-section Large Rotating Arms mounted eastward against a wall, carrying the
outer blocks of a three-block granite strip. It preserves the reported saved
error state with operator identifiers removed. Real clicks must move each
payload without moving the middle block or wall, reverse, refuse a real
obstruction without losing the payload, and retry after clearing it. Geometry
checks cover both sizes, six mounts, four fold directions and both payload
modes, retaining the intermediate stroke while excluding inflation along the
hinge axis. Run `test_viewscreen.sh --focus arm-wall` on Linux or
`run_windows.py --focus arm-wall` on Windows.

The same focus imports `arm-wall-joined-2.6.nbt`, retaining the folded joined
Large Rotating Arms and their two-cell saved payload beside a three-cell native
platform. Operator identifiers are removed. The check must include the current
third block, extend and reverse the whole row, refuse a separate obstacle without
capturing it, and retry after it is removed.

It also exercises 48 live placement combinations: regular/Large Rotating Arms,
all six mounts, independent/joined columns, and rotating/level payloads. Each
combination edits the payload at a native endpoint, reverses, mines the whole
platform, and moves the empty arms without resurrecting blocks. Level-mode cases
replace a block with a chest and assert preservation of its 37 diamonds.

`copy-surfaces-2.6.nbt` preserves the reported Copy Controller and two 5×5 pads,
including their support blocks and saved artwork. Player identifiers are removed.
The `copy-surfaces` focus mines the shared drawing tile and a separate member
through the client, checks every remaining face with the actual TESR, replaces
the missing block, expands the pair to 7×7, rejects the intermediate size mismatch,
and checks seed replacement and controller removal. A further 24 cases cover
all six surface normals and four horizontal controller facings, including real
rendered faces and stable idle topology after expansion. It also runs the shared
surface renderer, picker and damaged-turntable regressions. Linux and Windows
launchers and the release gate select the same fixture.

## Saved vehicle ramp regression

`vehicle-ramps-2.6.nbt` contains a parked wheeled vehicle with two Ramp Controllers
and 56 generated ramp sections, plus its surrounding support blocks. Operator
identifiers are removed. The `vehicle-ramps` focus imports the saved native cells
and checks capture, snapshot reload, four rotations, clock freezing/thawing,
Taxi assembly, parking, repeat capture and unknown-data refusal. Run
`bash testclient/test_viewscreen.sh --focus vehicle-ramps` on Linux or
`python testclient/run_windows.py --focus vehicle-ramps` on Windows.

## Legacy vehicle controller and physical-server capabilities

`legacy-vehicle-controller-2.6.nbt` retains twelve saved Pilot Seat, Vehicle
Controller, programmable block and sign cells from a pre-2.6 vehicle. Operator
identifiers are removed; opaque third-party capability NBT is retained. The
vehicle upgrade check loads these as native tiles, then checks preflight and
four rotations without modifying the original data.

`test_server_startup.py --vehicle-world-only` runs the chunk, capability and
legacy-data checks on a physical Forge server without unrelated canopy or fluid
fixtures. Supply the standard JAR and the installed Thaumic Augmentation,
Thaumcraft and Baubles JARs using `--jar` and repeated `--mod` arguments. Include
NuclearCraft and RedstoneFlux to exercise the original capability provider too.
The regular Taxi live focus covers assembly, parking and recovery.

## Optional building/decorative blocks

`test_server_startup.py --foreign-buildings` enumerates the installed LibVulpes,
Advanced Rocketry, Furniture Mod, Galacticraft and Thermal registries. Supply the
installed mod JARs and their dependencies with repeated `--mod` arguments. The
physical-server matrix checks every distinct metadata state admitted by the
building adapters, four rotations, native furniture pairs, inventories, TV
constructor dimensions and negative unknown-data/cropped-part cases. RF/fluid
network equipment remains refused.

Run `bash testclient/test_viewscreen.sh --focus foreign-buildings` on Linux or
`python testclient/run_windows.py --focus foreign-buildings` on Windows with
those optional mods installed in the isolated test game. The live fixture uses a
real client click and asynchronous Taxi assembly, draws a carried 2×2 Galacticraft
Screen to framebuffers, then checks rotated native parking and recapture.
