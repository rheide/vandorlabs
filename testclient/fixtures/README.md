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
