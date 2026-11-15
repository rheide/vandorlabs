# Porthole joining and retained invalidation

Full-depth Porthole Blocks have one physical plane regardless of the saved panel
depth flag. Visual grouping, connected Duplifier matching and Respect Joins use
that same plane rule. Thin Porthole Walls still require matching depths. This is
comparison-only: applying join settings does not rewrite block metadata.

A rejected edge is still a topology dependency. In particular, a Vertical lower
block must remember the upper cell even while that upper block is Horizontal.
Changing the upper block can then invalidate the unchanged lower block's old
single-cell aperture. Discovery records examined endpoint positions before edge
admission, preserving loaded-chunk checks and the existing scan limits.

When a discovery replaces an overlapping cached component, it invalidates the
retained geometry of the displaced original members before discarding their
CPU descriptors. Unrelated cache eviction still permits retained geometry to
outlive the CPU cache under its own dependency bounds. No global clearing or
continuous rediscovery is needed.

The retained renderer examines at most 256 invalidation keys per flush and
rebuilds at most eight actual sections. Keys without a tracked Porthole section
are discarded without consuming a section rebuild slot, reading the world or
creating a section. This prevents startup lighting updates for empty sections
from starving nearby windows.

## Focused regression

`testclient/test_saved_porthole_joins.sh <world.7z>` prepares the bounded supplied
scene using `tools/prepare_porthole_join_fixture.py` (7z and nbtlib required). It
retains eighteen original block states and tile records plus the saved Duplifier
options, without importing the rest of the world. The ordinary test runner keeps
logs and the tested JAR hash under its normal artifact policy.

The failing run selected only two of six full-depth originals. After a second
real copy made all six settings Vertical, an untouched lower member still had a
single-cell aperture. Both uploaded housing and pane records differed from a
fresh reconstruction. The corrected same scene passes 1,070 checks: six of six
members are selected and dirtied, all original warm geometry and GPU buffers
match the fresh oracle, and an independent pair of single-block copies refreshes
the untouched lower member. The adjacent thin Portholes and Glass groups remain
correct. The queue proof exercises the 256-key limit with thousands of absent
lighting keys.

The existing directional focus also passes 2,600 emitted checks and 173 actual
Duplifier checks on the same JAR, retaining thin-depth negatives and all direction
combinations. These are focused regressions, not a release gate.

On NeoForge, `python3 test_porthole_originals.py --xvfb` from the platform
directory exercises the same two failure mechanisms through a connected Survival
player. The negative run retains a warmed four-member original after editing its
previously rejected fifth neighbour. The corrected run refreshes all five
published layouts and emitted frame, rim and pane records, including the former
terminal rim. A subsequent real connected Duplifier copy selects all six
full-depth originals despite their different unused depth flags; every emitted
record matches a separately initialized reference. The focused pair passes 159
client checks and 15 server checks. The shared native directional codec/contour
checks also pass (216 contours and 18 NBT checks). This checks native published
model data and emitted records; the GPU-buffer readback belongs to the Forge
scene above.
