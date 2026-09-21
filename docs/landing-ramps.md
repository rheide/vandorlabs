# Ramp / Elevator Controller

The standalone Landing Ramp items have been removed. Use **Ramp / Elevator
Controller** beside a horizontal platform made of matching slabs or ordinary
solid blocks. Look toward the platform while placing the controller: its top
arrow points to the seed block it will select.

Right-click to configure. Every valid setting edit is sent immediately; there is
no Apply button. An edit resets the platform to its original position, then
evaluates the redstone signal using the new settings:

- **Mode:** ramp (hinged first tread) or elevator (whole platform translates).
- **Top / Bottom:** top lowers from the original platform; bottom raises from it.
- **Ramp direction:** north, east, south or west, independent of the controller's
  arrow. The arrow still selects the adjacent platform, so the controller may
  sit alongside the ramp. The chosen direction points toward the far treads
  (lower for Top, higher for Bottom).
- **Vertical travel:** 1–8 blocks. The far ramp tread, or the entire elevator,
  travels exactly this distance.
- **Deploy when redstone is ON / OFF:** choose either signal polarity.
- **Base speed:** fast or slow. Slow takes twice as long.
- **Treads:** stairs (two per block) or smooth (eight per block), used in ramp mode.

Full-stroke time is `max(platform length, travel) × 10 ticks` on fast, or
`× 20 ticks` on slow (the previous fast speed). For example, a four-long platform
with three-block travel takes two seconds on fast and four on slow. Reversing redstone mid-animation
keeps the current pose and takes only the remaining portion of the stroke.
Elevators and ramps carry standing entities; obstructed riders stop movement
and leave an error for the next signal edge or setting edit. Riders do not collide
with their own platform during transport, but foreign ceilings still stop them.
Player transport uses vertical-only support updates, not repeated teleports, so
walking and camera input remain active. Moving collision surfaces tolerate a
small difference between player and platform update times at block boundaries.

## Automatic selection and restoration

There is no connect/disconnect operation. Each deployment scans face-connected
blocks matching the type **and complete blockstate** of the block directly in
front, on that same horizontal layer. Different slab halves/material variants
remain separate. Maximum footprint: **8 blocks wide × 8 blocks long** (64 source
blocks), with **8 blocks maximum vertical travel**. Matching blocks beyond those
bounds are ignored, not treated as an error. Selection grows from the arrow's
front block using a repeatable connected flood fill; sideways/backward growth
also counts toward the eight-block spans. Diagonal contact does not connect;
holes remain holes. Bounds are shown in the dialog. Previously deployed larger
platforms retain their saved blocks until restored; new deployments use these limits.

Before any mutation, the controller checks the whole selection and its vertical
movement space. That space must be air, all involved chunks must be loaded,
and the travel must fit inside world height. Chests/machines with tile entities,
unbreakable blocks and non-cuboid shapes are excluded.

While moving, temporary cells protect only the occupied space and the next
animation step, supplying animated collision. Space behind the platform is
released immediately; at rest only the occupied cells remain locked. You can build
in the released travel space, including the old platform position. Movement checks
the next step while animating and stops safely if new construction blocks it.
Remove the obstruction and toggle redstone to retry. The platform blocks themselves
remain protected while deployed.

On completing retraction, the exact original blocks are
restored and every reservation is removed. The platform can then be edited
normally; the next activation discovers the new footprint. Remove the controller
to restore immediately. All settings may change while deployed: the old platform
is reset before the new configuration is used. If its original position or a
rider's reset path is blocked, the reset is rejected and the saved blocks and old
settings are retained. A valid travel value is submitted as it is typed; blank or
out-of-range intermediate text is not submitted.

Validation errors persist in the dialog. A failed scan changes no blocks.
A partial installation rolls back from the saved journal. Unexpected foreign
blocks are never overwritten during recovery: if one displaced an original
source, that original is refunded at the controller. A setting edit retries the
current signal after an obstruction has been fixed. Ordinary neighboring block changes
do not cause repeated scans while the signal remains unchanged.

## Updates, persistence and rendering

Neither the controller nor its internal cells implement a ticking tile entity.
Neighbor/redstone events and configuration saves initiate movement. Scheduled
block updates exist only while animating or for one-shot load recovery; idle
controllers do not poll. Chunk-load notifications recover orphan cells or
resume interrupted animations. Removal loads the bounded platform chunks so
restoration is complete.

The controller journal, source blockstates, configuration, last error and
timestamped animation survive saving/loading and synchronize to clients. The
renderer and collision use the same clipped geometry. Side-texture coordinates
stay attached to each moving segment, including where
it crosses a world-cell boundary. The checked-in twelve HD-2 face textures
(six on, six off) are packaged unchanged. The supplied cube models use standard lighting:
no brightness tint, emitted light, or extra arrow geometry is added.
Active appearance means the deployment target is active, including inverted
redstone operation.

## Tests

`./gradlew testRamps` exercises the production Java selection and motion math
across lengths, heights, modes, anchors, speed settings and reversals.
`testclient/test_viewscreen.sh` runs actual Forge-world tests for automatic
selection, both signal polarities, obstruction rollback, partial-install fault
injection, preserved source variants, automatic cleanup, persistence and elevator
passengers, and captures the four mode/direction combinations and settings dialog.
