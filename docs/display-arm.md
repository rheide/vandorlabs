# Programmable Display Arm

The **Programmable Display Arm** is one configurable Forge 1.12.2 block for a thin
screen or a screen with an input deck. Its small mounting plate has four fasteners,
plain metal arm sections, two joints and a rear screen attachment. It does not move
other blocks like an Extender.

## Build and configure

1. Craft one with a **Programmable Flatscreen** above **Iron Bars**, with three
   **Industrial Alloy Ingots** across the bottom row. It is also in the creative
   catalog under consoles and inputs.
2. Click the top, underside or any side of a supporting block. The plate attaches
   to that face. A fresh screen faces the player horizontally.
3. Right-click in Creative, or use a **Configurizer** in Survival, to open the
   screen dialog. The **Arm** tab opens first. When a Redstone control is displayed,
   ordinary clicks operate it; sneak-right-click in Creative opens configuration.
4. Choose **Full height** or **Half height**, and turn **Input** on or off. These
   choices produce all four variants without replacing the block.
5. Set **Faces**, then adjust the position and angles with the sliders or type exact
   values in the adjoining fields. Changes preview immediately. Press Enter or
   leave a field to apply a valid value. Red values are outside the allowed range.
6. Choose screen artwork in **Displays**, keyboard/control artwork in **Controls**,
   housing artwork in **Sides**, and borders in **Frame**. Existing animation and
   redstone playback settings are available in the same dialog.

| Setting | Range | Meaning |
| --- | --- | --- |
| Size | Full / Half height | Screen width remains 16 pixels; height is 16 or 8 pixels. |
| Input | Off / On | Adds an 8-pixel-deep, 1-pixel-thick input deck below the screen. |
| Faces | North / East / South / West | Independent horizontal reference direction for the display. |
| Right | −32 to +32 pixels | Positive moves toward the viewer's right when looking at the screen. |
| Height | −32 to +32 pixels | Positive moves upward in the world. |
| Forward | −32 to +32 pixels | Positive moves toward the direction selected by Faces. |
| Yaw | −180° to +180° | Turns the screen about its upright reference axis. |
| Tilt | −90° to +90° | Tilts the screen upward or downward. |
| Roll | −180° to +180° | Rotates the screen and input deck together within the display plane. |

One block is 16 pixels. Position zero means the center of the placed display-arm
block. A fresh arm starts with Forward = 4 pixels to leave clearance behind the
display. **Reset pose** restores these offsets and zero angles while preserving
size, input visibility, artwork and Faces.

## Mounting and screen direction are independent

The plate always stays against the face clicked during placement. Changing the
display's direction or pose never rotates that plate. The arm connects the fixed
plate socket to the rear of the posed display automatically.

For a screen with **Faces: West**, Forward moves west, Right moves south, and
Height moves up. Those directions remain the same for floor, ceiling, north,
south, east and west mounts. Yaw, Tilt and Roll rotate the screen around its center;
they do not change the reference directions of the position controls.

The same settings at the same block position therefore produce identical display
corners for every mounting face. To match displays placed in different block
cells, account for the difference between those cells in their position offsets.

Positions and rotations are free within the listed ranges. They do not reserve
neighboring blocks or prevent deliberately placing a screen through a wall. Choose
offsets that give the arm and display the clearance required by the surrounding build.

## Saved settings and interaction

- Mining and pick-block retain the pose, cardinal screen direction, size, input
  visibility, housing and artwork. Replacing that item attaches the plate to the
  newly clicked face.
- Hiding the input deck preserves its selected artwork and configuration.
- Full and half screens share the same logical display/control layout; half height
  compresses that layout vertically without deleting configured rows.
- The display and arm remain selectable and collidable outside their owning block
  cell. Picking follows the actual component boxes rather than the enclosing empty
  volume. Player collision uses small slices around rotated components.
- Removing the support drops the configured display arm.
- Cardinal vehicle, turntable and copy rotations rotate the mounting face and
  display together, preserving the configured pose. Arbitrary folded arm payload
  orientations remain unsupported.
- Separate display arms keep separate images and frames; they do not auto-join into
  a large flat screen. This thin housing has no integrated Screen Chip reader or
  Navigation module slot.

## Developer checks

Use Java 8 for the root Forge build:

```sh
./gradlew testDisplayArm testDisplayArmItems testDisplayArmRotation testDedicatedServerLinkage
./gradlew assemble
```

`testDisplayArmGeometry` covers all six mount faces, four screen directions, both
sizes, both input states, pose inversion, component rays and render bounds.
`testDisplayArm` exercises the actual Forge tile/item adapters, NBT, packet ranges,
container permissions and posed control hit mapping. `testDisplayArmItems` checks
the emitted geometry, artwork direction, finite normals and inventory padding.
`testDisplayArmRotation` checks all mounting faces, display directions and variants
through cardinal structure rotations, including exact saved-data round trips.

The focused client check is `tools/focus.sh display-arm --shots` on Linux and the
`display-arm` focus in `testclient/run_windows.py` on Windows. Rendering acceptance
also requires the project's OptiFine and Complementary configurations. The block's
geometry and settings are loader-free; a NeoForge block/item/editor adapter has not
been added by this feature.
