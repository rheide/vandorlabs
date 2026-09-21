# Wider vertical Space-door groups

Investigated separately after the frame-pixel / live-selector fix, commit
`433bf5b`. No grouping behavior was changed in this investigation.

## Verdict

A 3-block-wide, 2-block-high opening is feasible, but it is not a small extension
of the current pair mechanism. Leave it out of this test build under the requested
"only if it does not require much structural change" constraint. Vertical motion
itself is already supported; the significant work is connected geometry and
consistent group membership across interaction, configuration and power.

The interpretation here is three normal 1x2 leaves side by side. If "2 tall"
means two complete doors stacked (4 blocks high), that is a larger change again:
horizontal frame seams, support checks and group-height travel must also change.

## What prevents simply adding a third leaf

- `BlockConnectingDetailedDoor.getActualState` stores only a `PAIRED` boolean.
  Its neighbor check requires opposite hinges and looks on just the hinge's
  matching side. `TileEntitySpaceDoor.mate()` returns exactly one position. There
  is no representation for a middle leaf connected on both sides.
- `tools/import_space_doors.py` generates standalone frames and paired end
  frames. A middle leaf needs neither vertical jamb and a full-width leaf/glass
  surface, with top/bottom rails retained. Existing paired geometry retains one
  jamb and crops one leaf edge. Reusing it would leave an internal border or gap.
  `ModBlocks.registerSpaceDoorModels` and `TileEntitySpaceDoor.metadata` also
  encode only standalone/paired variants.
- `BlockVandorDoor.onBlockActivated` toggles immediate opposite-hinge neighbors
  without recursion. Clicking the middle can affect two neighbors, but clicking
  an end does not reach the far end. This is not a reliable three-leaf group.
- `MessageSpaceDoor.Handler` validates and configures one tile plus one mate;
  placement inheritance also uses one mate. A group update must validate every
  member before changing any, and deal with a design/direction change that joins
  or separates neighboring groups.
- `BlockVandorDoor.updateRedstoneState` is deliberately per-door, with local and
  channel power combined at that door. A single connected assembly needs a
  defined group-power rule and updates on input, placement, removal and chunk
  load/unload, without feeding propagated state back into the channel network.
- Animation state is keyed per lower door position. A rigid assembly should
  share its animation clock/anchor so culled or newly loaded leaves do not begin
  their animation at a different time.

Changing just the maximum neighbor count would therefore produce a visually
connected doorway whose click, power and configuration behavior depends on which
leaf is used. That is not a completed group feature.

## Smallest sensible future implementation

Keep legacy/Detailed and sideways/rotating doors on the existing pair path. Add
a Space-only resolver for a bounded, straight, same-height row of complete,
loaded, identically oriented vertical sliding doors. Reuse that one membership
result for rendering, permission checks, configuration and open/power updates.
Add a two-sided connection mask and a middle-leaf model, preserving existing
saved design indices. Treat group formation/splitting explicitly and use a
common animation anchor. No chunk loading, world scans or server tick loop are
necessary; discovery can be bounded and event-driven, with geometry membership
cached or restricted to immediate neighbors during rendering.

The current travel is 31/16 block: it hides framed leaves beyond the inner rail
and retains the requested one-pixel bare reveal. An exact 2-block group lift is
easy mathematically, but is a separate policy for bare groups and should not
silently erase the existing bare-door reveal. Native 1x2 artwork can repeat
across the row; no stretching or new texture sizes are needed.

Test targets would include all facings, both vertical directions, end/middle
activation, conflicting power inputs, missing upper halves, mixed settings,
removing a middle leaf, chunk boundaries and permission failures before mutation.

For now, three bare sliding doors configured individually to Up and the same
nonzero redstone channel can be powered together. This is only synchronized
power, not automatic group frames or group-wide manual clicking/configuration.
