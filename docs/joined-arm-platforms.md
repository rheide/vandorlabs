# Joined arm platforms

An unjoined Extender or Rotating Arm moves only the block at its tip; touching walls, floors
and other surrounding blocks stay in place. Extender Arms and Large Extender Arms can share a platform. Rotating Arms and
Large Rotating Arms can share a platform along their hinge axis.

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

## Joined Rotating Arms

Place matching Rotating Arms in a row along their hinge axis, with the same
mounting face, section count, fold direction and payload mode. Enable joining
on every arm. A row can contain three or more arms; Rotating Arms do not join
across a second platform axis. Connect their tips with one continuous layer.

**Payload: stays level** keeps the platform attached to the swivelling tips
throughout the stroke. **Payload: rotates** turns it with the arms. At a
representable endpoint, supported payloads return to ordinary block mode.

![A level payload attached to its rotating arm](images/joined-arms/level-contact.png)
