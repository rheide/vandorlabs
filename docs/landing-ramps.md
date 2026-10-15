# Programmable Ramp

The standalone Landing Ramp items have been removed. Use **Programmable Ramp**
beside a horizontal platform made of matching slabs or ordinary
solid blocks. Look toward the platform while placing the controller: its top
arrow points to the seed block it will select.

Craft the controller with a piston in the center of a 3×3 crafting grid and
Programmable Matter Ingots in the eight surrounding slots.

Creative shift-right-click to configure, or use the Configurizer. Every valid setting edit is sent immediately; there is
no Apply button. An edit resets the platform to its original position, then
evaluates the redstone signal using the new settings:

- **Mode:** ramp (treads travel different distances), filled ramp (the same
  tread slope with material filling from each tread's starting position),
  lift (whole platform translates), or extend (whole platform expands to its
  destination). **Escalator Up** and **Escalator Down** keep a deployed slope
  and circulate its treads uphill or downhill instead.
- **Start / off offset** and **End / on offset:** values from -16 to +16 in half-block steps,
  measured from the original platform along the chosen travel axis. Up/down
  uses positive for up and negative for down. Use the sliders; valid edits apply
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
- **Filled ramp / extend** keep each starting surface and fill the space to
  its current position with the source block material. Retraction removes that
  fill. These modes do not deliberately carry standing entities.
- **Deploy when redstone is ON / OFF:** choose either signal polarity.
- **Base speed:** fast, medium or slow. Medium is the previous fast speed;
  new fast takes half as long and moves at a constant rate. Slow takes twice
  as long as medium.
- **Tread px:** tread width along the ramp, choose **1, 2, 4, 8 or 16 pixels** (16 pixels =
  one block). Type an allowed value or use minus/plus to step through the choices;
  edits apply immediately. `1` is
  finest, `8` is half-block stairs, and `16` is a full-block step. This setting is
  disabled for lift and extend; filled ramp retains tread control. Existing stairs retain 8px treads; existing smooth
  ramps retain their actual 2px treads. A one-block ramp with a single 16px tread
  moves that entire tread because it has no separate hinge tread.

Travel is the absolute difference between the two offsets (up to 32 blocks).
Full-stroke time is `max(platform length, ceil(travel)) × 4 ticks` on fast,
`× 8 ticks` on medium, or `× 16 ticks` on slow. A full 32-block stroke takes
6.4 seconds on fast, 12.8 on medium, and 25.6 on slow. Reversing redstone mid-animation
keeps the current pose and takes only the remaining portion of the stroke.
Longer strokes check more space when deployment begins and stay active for more ticks.
The platform footprint remains capped at 128 source blocks; an idle controller does
not scan its travel path each tick.
Moving lifts and ramps carry standing entities; obstructed riders stop movement
and leave an error for the next signal edge or setting edit. Riders do not collide
with their own platform during transport, but foreign ceilings still stop them.
Vertical player transport uses support updates. Sideways travel uses server
position updates. Moving collision surfaces tolerate a
small difference between player and platform update times at block boundaries.

## Escalators

Choose **Escalator Up** or **Escalator Down** in the same controller. The platform
forms a ramp at **Ramp height**, even with redstone off. Redstone controls only
tread circulation: switching it off freezes the steps and leaves a solid,
walkable staircase. Switching it on continues from the frozen positions.
**Trigger: redstone OFF** reverses the signal requirement. Physical redstone
and virtual channels both work.

Escalators use vertical travel with Linear, Curve In or Curve Out height profiles.
Start/off offset and sideways travel are disabled in these modes. Height 0
creates a horizontal walkway; the selected platform must be rectangular with at least two treads.
The existing 8�16-block selection limit and source material/slab rules apply.
Different programmable finishes are retained when Match textures is Off;
finishes travel with their source treads. Up and Down refer to physical height,
so a negative Ramp height reverses the horizontal direction of uphill travel.

| Example | Settings | Result |
| --- | --- | --- |
| Entrance escalator | 2 blocks wide � 4 long, height `3`, 8 px, Escalator Up, trigger ON | Redstone ON carries riders toward the raised end; OFF leaves the stairs extended. |
| Exit escalator | Same platform, Escalator Down | Riders travel from the raised end back toward the original platform level. |
| Descending passage | Height `-3`, Escalator Up | Riders travel back from the lower end toward the original level. |
| Normally running stairs | Escalator Up, trigger OFF | Movement runs without power and stops when power is applied. |

**Base speed** sets both formation speed and the belt's horizontal speed:
Fast is 1 block/second, Medium is 0.5, and Slow is 0.25. Riders follow the
vertical slope as well. Standing players and other supported entities are
carried explicitly; walking and camera direction remain available, and jumping
or flying detaches a rider. Riders leave at the destination rather than wrapping
to the other end. Landing floors and side walls clip a rider's movement without
disabling the belt. A ceiling that blocks upward rider motion stops the escalator;
clear it and toggle the signal or edit a setting to retry.

Movement also requires a nonspectator player within **32 blocks** of the ramp.
A running escalator sleeps after every player is beyond **40 blocks**, avoiding
repeated switching at the boundary. A shared proximity check runs once per
second, so sleep/wake can take up to one second. Sleeping freezes the belt phase
and stops recurring controller block ticks, rider scans and motion packets.
Mobs and items alone do not keep it awake. The ramp stays extended, and player
proximity does not override an inactive redstone signal. No chunks are forced
loaded for operation.

The running belt keeps a fixed set of protected cells covering every tread
position, rather than replacing blocks each tick. These cells may include empty
space immediately around the moving steps; that clearance remains protected
while the escalator is stopped. Clients animate from a synchronized clock;
state changes synchronize the belt and occupied riders receive support updates.
The extended ramp and committed phase survive saving/loading, without catching
up missed rider movement after unloading. Removing the controller restores the
original platform. Changing settings uses the same guarded reset as other modes.

See the [uphill and downhill live animations](gallery/ramp-controller.md#escalators).

## Automatic selection and restoration

There is no connect/disconnect operation. Each deployment scans face-connected
blocks matching the type **and complete blockstate** of the block directly in
front, on that same horizontal layer. Different slab halves/material variants
remain separate. Maximum footprint: **8 blocks wide × 16 blocks long** (128 source
blocks), with **each endpoint within 16 blocks of the original position**. Matching blocks beyond those
bounds are ignored, not treated as an error. Selection grows from the arrow's
front block using a repeatable connected flood fill; sideways/backward growth
also counts toward these spans. Length follows the configured ramp direction;
width is perpendicular to it, independently of the controller’s selection arrow. Diagonal contact does not connect;
holes remain holes. Bounds are shown in the dialog. Previously deployed larger
platforms retain their saved blocks until restored; new deployments use these limits.

**Match textures** is On by default: programmable sources must also match their
effective face textures and slab side layout. Turn it Off to include different
finishes without mixing block types or slab halves. Each source retains its
own finish. Immersive Engineering regular and scaffold slabs are supported,
including their material and half settings. The option is saved and copied.

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
block updates exist only while deploying/retracting or running an awake escalator or for one-shot load recovery; idle
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

For focused Windows validation and the four escalator recordings:

```powershell
python testclient/run_windows.py --focus ramps
python testclient/encode_animations.py testclient/render-run.RUN
```

The focused run exercises existing ramp contracts and escalator rider, redstone,
proximity, persistence and restoration behavior, then records only the four
escalator scenes. It does not regenerate the full gallery. `testRamps` also checks
continuous tread coverage and loop boundaries for every allowed tread size.

Horizontal escalators use height 0 and retain the Escalator Up/Down mode names: Up moves forward and Down moves backward relative to Ramp direction. Their original top texture scrolls with the belt; no extra stripes are drawn. Linear, Curve In and Curve Out use the same height-profile interpolation as ordinary ramps, with constant horizontal belt speed. At height 0 all three profiles are flat. Rider movement clips against landing floors and side walls; side contact holds the rider without disabling circulation. A blocked upward lift still stops to avoid pushing a rider through a ceiling. Walking against an uphill belt can step onto the fixed lower landing using normal player step height and head clearance.

An extended ramp stays extended when assembled into a ground vehicle. Its generated segments, source materials, collision and recovery data travel together. An escalator keeps its captured tread pose with the belt paused while in vehicle mode. Parking preserves the extended pose and resumes normal redstone operation. Cancelled assembly restores the exact original expanded geometry and recovery data.

Dropped items are supported belt passengers. While supported, the belt cancels their sideways push-out drift and sends position updates to nearby tracking clients; items keep normal gravity, pickup and despawn behavior. Item transport adds tracking traffic only while items are being carried.

## Moat drawbridge

Build an upright source tower immediately in front of the controller, with its
bottom level beside the controller. Matching blocks may extend upward and
sideways to make a wider bridge. Choose Mode: Moat. Off retains that upright
tower; On rotates the whole assembly outward around its controller-adjacent
bottom edge into a deck. It stays straight throughout the movement and does
not unfold one segment at a time. At zero offsets, the deployed deck top meets
the hinge and its thickness extends downward.

Start skews the stored tower; End sets the deployed deck slope. These half-block
values select angles using atan2(offset, tower height); they do not translate the
entire platform vertically. Both endpoints retain the same hinge. Tread px sets
the vertical slice size used for collision segmentation during the rotation.
The visible assembly and its original material UVs rotate rigidly together.

Full blocks, slabs, stairs and saved programmable finishes keep their actual
source solids. In particular, slabs stacked at integer heights keep their gaps;
those gaps rotate with the tower. Slabs are not stretched or reoriented into a
continuous conventional slab deck. Source facing, stair corners and all source
tile settings return exactly when the mechanism is recovered.

Keep the entire rotation path clear. Terrain, occupants, protected blocks and
unloaded chunks can prevent movement. The off state retains its source journal,
including at Start zero. Changing mode or removing the controller restores the
original upright source tower when its cells are clear. Carried vehicles switch
between checked endpoints on channel edges, checking the full rotation path
against terrain and other vehicle components. Settled mechanisms do not scan or
animate while idle.
Full glass platforms support vanilla Glass, all Stained Glass colors, and every Vandor cockpit glass variant. Source transparency and exact material/color survive deployment, copying, rotation and recovery. Glass panes, thin Programmable Glass panels and canopies keep their own geometry and are excluded from platform capture.

Vanilla stairs and Programmable Stairs are supported in ramp, filled ramp, lift, extend, both escalator directions and Moat modes. Captured stairs retain their facing, top/bottom half and inner/outer corner shape, with collision following the moving steps. Programmable finishes and source tile settings survive copying, vehicle rotation and recovery. Platform selection still requires matching source block states and, when enabled, matching finishes.
