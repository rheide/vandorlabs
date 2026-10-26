# Joined arm platforms

An unjoined Extender or Rotating Arm moves only the block at its tip; touching walls, floors
and other surrounding blocks stay in place. Both regular and Large Extenders support
joined platforms across two axes; Rotating Arms join along their hinge axis. Use
one arm type and size within each group, with matching section counts and settings.

## Joined motion in game

Four Extenders move this hollow 3×4 frame together. The chest rides within the
selected load height. At each aligned endpoint the platform becomes ordinary
blocks again, including while retracted.

![Four joined Extenders lowering and raising a hollow platform and chest](images/joined-arms/extender-platform.gif)

A sideways mount uses the same platform layer and joins across the two axes
perpendicular to travel. Carry load is unavailable for sideways movement.

![Four wall-mounted Extenders retracting and extending a vertical frame](images/joined-arms/sideways-extender-platform.gif)

Three matching Large Rotating Arms share one hinge axis. Their **stays level**
payload moves with the swivelling tips throughout the fold and return.

![Three joined Large Rotating Arms folding and returning with a level platform](images/joined-arms/rotating-platform.gif)

## Build an Extender platform

1. Place matching arms at the corners of a rectangle. Use the same mounting
   face and number of stacked sections at every corner. Keep their supporting
   blocks in place. Intermediate supports are allowed: a 2×3 arrangement of
   six arms works, as does a straight row of three arms.

   ![Matching supported arm columns](images/joined-arms/columns.png)

2. Build one layer of blocks between the arm tips. Air holes are allowed.
   Face-adjacent platform blocks must connect every support; diagonal contact
   alone does not connect a corner. Connect any loose blocks inside the rectangle
   before moving it. A hollow frame is a valid platform.

   ![A connected platform with an open centre](images/joined-arms/platform.png)

3. With the arms fully extended, open each arm's dialog using the Configurizer.
   Enable **Join arms** in the same dialog and set
   **Range** from 1 to 32 blocks. Range is the distance to a neighbouring arm,
   so intermediate arms can connect a longer platform. Every participating arm
   must opt in and use identical configuration and section counts. Differently
   configured or shorter/longer neighbouring arms are ignored. Both neighbours
   must have sufficient range.

   ![Joined platform controls](images/joined-arms/options.png)

4. Activate an arm to move the joined group. The default selection contains only
   the platform layer. For vertically moving Extenders, **Carry load** adds
   supported blocks above the platform; **Load height** selects 1–32 blocks above
   its surface. Vehicles, boats and minecarts must fit entirely inside the platform
   rectangle and selected load height; their inventories stay with them. Sideways Extenders and Rotating Arms use the platform layer only.
   A load extending outside the selected area or height must be separated before
   moving it.

   ![Platform with a chest above it](images/joined-arms/load.png)
   ![Retracted joined platform](images/joined-arms/retracted.png)

The group is bounded to 64 arms, a 64-block span per platform axis, and 32,768
captured blocks. Saved-data and network size limits also apply: data-heavy loads
may reach those limits before the block-count limit. Unsupported or oversized
loads are refused as a whole. Keep all platform chunks loaded while moving.
Extend fully before changing the group or its load settings. The dialog stays
open when joining selects a different controller. Large arms need enough room
for their wider moving parts; use a joined group to move a continuous platform.

## Editing an Extender platform

At whole-block endpoints the platform becomes ordinary world blocks, including
when retracted. This works on floors, ceilings and all wall faces. The controller
stores the displaced arm sections separately, preserving each column's length.
One-section Extenders stop halfway between blocks and keep a captured payload.

You can mine or replace platform blocks while retracted. On the next move, arms
capture the current connected platform, including newly added blocks inside the
supported rectangle. Removed blocks stay removed. Air holes are allowed, but a
remaining platform must still connect every corner with face-adjacent blocks.
A disconnected platform stays in place with an error. Removing the entire
platform lets the empty arms extend normally.

Extend fully before removing arm sections or changing joining/load settings.
Matching neighbours are reconsidered at the extended position; newly placed
arms stay independent until they can join a compatible extended group. An arm
with a different section count or configuration is ignored. Removing an extended
arm changes the available supports; the next platform move still requires all
corners of its resulting rectangle to be supported and connected.

## Joined Rotating Arms

1. Place two or more matching Rotating Arm columns in a straight row along
   their hinge axis. Keep the mounting blocks in place. Use the same arm size,
   mounting face, section count, fold direction and payload mode.
2. Connect their extended tips with one face-connected layer of blocks. A row
   can contain three or more arms, as shown in the animation above.
3. Enable **Join Arms** on every column and give each the same neighbour range,
   large enough to reach the next arm (1–32 blocks). Keep their other settings
   identical too. Rotating Arms join along one axis only.
4. Close the dialog and activate an arm to fold the whole joined row. Extend
   fully before changing arm sections or joining settings.

**Payload: stays level** keeps the platform attached to the swivelling tips
throughout the stroke. **Payload: rotates** turns it with the arms. At a
representable endpoint, supported payloads return to ordinary block mode.

![A level payload attached to its rotating arm](images/joined-arms/level-contact.png)
