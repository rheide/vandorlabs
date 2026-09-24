# Programmable Ramp

The standalone Landing Ramp items have been removed. Use **Programmable Ramp**
beside a horizontal platform made of matching slabs or ordinary
solid blocks. Look toward the platform while placing the controller: its top
arrow points to the seed block it will select.

Right-click to configure. Every valid setting edit is sent immediately; there is
no Apply button. An edit resets the platform to its original position, then
evaluates the redstone signal using the new settings:

Craft the controller with a piston in the center of a 3×3 crafting grid and
Programmable Matter Ingots in the eight surrounding slots.

- **Mode:** ramp (treads travel different distances), lift (whole platform
  translates), or extend (whole platform expands to its destination).
- **Start / off offset** and **End / on offset:** signed integers from -8 to +8,
  measured from the original platform along the chosen travel axis. Up/down
  uses positive for up and negative for down. Type a value or use the adjacent
  minus/plus buttons; valid edits apply
  immediately. For example, start `2`, end `-3` travels from two blocks up to
  three blocks down. Equal endpoints are allowed. Trigger polarity still decides
  which redstone signal selects the end position.
- **Ramp direction:** north, east, south or west, independent of the controller's
  arrow. The arrow still selects the adjacent platform, so the controller may
  sit alongside the ramp. The chosen direction points toward the far treads
  (the hinge stays at the original position at both endpoints).
- **Travel:** up/down or left/right. Left and right are relative to the selected
  ramp direction. A positive sideways offset goes right; a negative one goes left.
  The first ramp tread stays hinged; lift mode
  moves the whole platform sideways.
- **Extend** keeps the starting platform and fills the space to its current
  position with the source block material. Retraction removes that fill.
  Standing entities remain on the starting surface in extend mode.
- **Deploy when redstone is ON / OFF:** choose either signal polarity.
- **Base speed:** fast, medium or slow. Medium is the previous fast speed;
  new fast takes half as long and moves at a constant rate. Slow takes twice
  as long as medium.
- **Tread px:** tread width along the ramp, choose **1, 2, 4, 8 or 16 pixels** (16 pixels =
  one block). Type an allowed value or use minus/plus to step through the choices;
  edits apply immediately. `1` is
  finest, `8` is half-block stairs, and `16` is a full-block step. This setting is
  disabled for lift and extend. Existing stairs retain 8px treads; existing smooth
  ramps retain their actual 2px treads. A one-block ramp with a single 16px tread
  moves that entire tread because it has no separate hinge tread.

Travel is the absolute difference between the two offsets (up to 16 blocks).
Full-stroke time is `max(platform length, travel) × 5 ticks` on fast,
`× 10 ticks` on medium, or `× 20 ticks` on slow. For example, a four-long platform
with three-block travel takes one second on fast, two on medium, and four on slow. Reversing redstone mid-animation
keeps the current pose and takes only the remaining portion of the stroke.
Moving lifts and ramps carry standing entities; obstructed riders stop movement
and leave an error for the next signal edge or setting edit. Riders do not collide
with their own platform during transport, but foreign ceilings still stop them.
Vertical player transport uses support updates. Sideways travel uses server
position updates. Moving collision surfaces tolerate a
small difference between player and platform update times at block boundaries.

## Automatic selection and restoration

There is no connect/disconnect operation. Each deployment scans face-connected
blocks matching the type **and complete blockstate** of the block directly in
front, on that same horizontal layer. Different slab halves/material variants
remain separate. Maximum footprint: **8 blocks wide × 16 blocks long** (128 source
blocks), with **each endpoint within 8 blocks of the original position**. Matching blocks beyond those
bounds are ignored, not treated as an error. Selection grows from the arrow's
front block using a repeatable connected flood fill; sideways/backward growth
also counts toward these spans. Length follows the configured ramp direction;
width is perpendicular to it, independently of the controller’s selection arrow. Diagonal contact does not connect;
holes remain holes. Bounds are shown in the dialog. Previously deployed larger
platforms retain their saved blocks until restored; new deployments use these limits.

Before any mutation, the controller checks the whole selection and its
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

When the start offset is zero, completing retraction restores the exact original
blocks and removes every reservation. The restored platform can then be edited
normally; the next activation discovers the new footprint. A nonzero start offset
keeps the platform attached at that height, including while off; configuring it
initially positions it there immediately. Remove the controller
to restore immediately. All settings may change while deployed: the old platform
is reset before the new configuration is used. If its original position or a
rider's reset path is blocked, the reset is rejected and the saved blocks and old
settings are retained. A valid offset value is submitted as it is typed; blank or
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
injection, preserved source variants, automatic cleanup, persistence and lift
passengers, and captures the four mode/direction combinations and settings dialog.
