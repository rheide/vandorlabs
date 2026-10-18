# Turntables

A **Turntable** turns the blocks above, below or beside a square platform of
**Turntable Blocks**. Place a **Turntable Controller** beside the platform,
look toward it, and the platform is scanned from the controller's front face.
While a trigger is asserted, the captured volume rotates evenly about the
platform's axis; when the trigger clears, the original blocks are restored in
place.

Craft the controller with a piston and redstone in a 3×3 grid. Craft Turntable
Blocks in a 3×3 grid around a Programmable Matter Ingot; the recipe yields four
blocks.

## Building a turntable

1. Place **Turntable Blocks** in a flat, square platform. The platform must be
   filled: every cell inside its outline must be a matching Turntable Block.
   Squares from 1×1 up to 16×16 are allowed.
2. Place the **Turntable Controller** against one edge, facing the platform. Its
   front face carries the sensor lens, and an arrow on its top points at the
   seed cell the platform grows from.
3. Right-click the controller (creative shift-right-click, or with the
   Configurizer) to open the dialog and choose **Scan turntable**.

### Reading the textures

Both blocks show which way they point:

- The **controller** has a lens on the face that scans and an arrow on top
  pointing at the platform, so the front is obvious from above.
- A **Turntable Block** carries the marked **arrow face** on the side the
  controller scans. Placed on a floor the arrow face is on top, so the arrow
  shows which way the payload turns; placed on a wall it marks the clock face.

The scan flood-fills the connected Turntable Blocks in the plane in front of the
controller. If the outline is not a square, or the square has holes or is not
flat, the dialog reports the problem and nothing is captured. A valid platform
shows its size and the number of blocks that would turn.

### Rotation axis

Every Turntable Block in the platform must share the same facing, and that
facing chooses what turns:

| Block facing | Mounting | Rotates |
| --- | --- | --- |
| **Up** | placed on a floor | the blocks **above** it |
| **Down** | placed on a ceiling | the blocks **below** it |
| **North / South / East / West** | placed against a wall | the clock face **in front** of it |

The controller sits beside the platform and always faces a horizontal edge. The
axis itself comes from the platform blocks, so a wall-mounted ring turns like a
clock while a floor-mounted one spins like a record.

## Configuring

Valid edits are sent immediately; there is no Apply button.

- **Trigger:** **Disabled** (never turns), **Redstone ON** (turns while
  powered), or **Redstone OFF** (turns while unpowered).
- **Channels:** the redstone channels that also drive the turntable, the same
  virtual channels used by ramps and other redstone-controlled blocks.
- **Height (1–16):** how many blocks deep to capture along the platform facing,
  measured outward from each platform cell.
- **Speed (°/s):** rotation rate from 1 to 360 degrees per second. Type a value or
  use the −/+ buttons to step by 15.
- **Rotation:** clockwise or counter-clockwise.
- **Scan turntable:** verifies the square and reports the payload size without
  moving any blocks. The current result stays visible in the dialog.

## Running

When the trigger is asserted the controller captures the volume, removes those
blocks from the world, and turns them about the platform pivot. The blocks are
redrawn each frame at their fractional angle, so the rotation is smooth rather
than snapping to whole blocks. A small green hand on each platform block spins
in step with the payload — it is deliberately asymmetric so the turning is
visible even when nothing sits on the platform — and the controller turns active
while it runs.

Clearing the trigger restores every captured block exactly where it was,
including tile-entity data. Removing the controller, or breaking any Turntable
Block in the platform, also stops the turntable and restores the payload. If a
captured cell is occupied when the turntable stops, its block is dropped as an
item instead of overwriting the new block.

Turning the platform changes its axis to match the mounted blocks, so the
same dialog drives floor, ceiling and wall turntables. Trigger polarity is kept
in the dialog and in copied controllers, as are height, speed, direction and
channels.

### Capture rules

- Only non-air blocks are captured, up to **4096** blocks.
- Liquids, bedrock and unbreakable blocks are rejected with an error.
- The controller must not sit inside the rotating volume.
- All involved chunks must be loaded; the payload does not force-load chunks.
- The rotating volume is a **display**: it is not solid while it turns. Build and
  stand clear of the swept space for the same reason you would keep an
  escalator path clear.

## Limits and notes

- A platform's outline may span at most 16×16 cells.
- While active, the payload is server-authoritative. Nearby clients receive a
  compact, fragmented snapshot and reassemble it for rendering; a client that
  joins mid-rotation requests the snapshot again.
- An idle controller does not tick. Redstone changes and scheduled load checks
  start and stop rotation.
- The turntable captures blockstates only; it does not carry entities, and this
  version does not move players or mobs standing on the turning blocks.

## Tests

`./gradlew testTurntables` checks the portable rotation math: angle wrapping,
speed clamping, per-axis quarter turns, pivot-radius preservation and square
validation, including that a hollow outline is rejected.

`./gradlew testNonRendering` drives the real tile entities in an in-memory world:
a 3×3 floor platform scans, captures and restores its payload, the angle advances
with world time in both directions, a rectangle and a hollow square are rejected
with a message, ceiling and wall platforms pick the right axis, and removing a
platform block drops the payload and flags the error.

`testclient/run_windows.py --focus turntables` runs the live client against an
integrated server: redstone engages the turntable, the client receives the
payload snapshot, the turning volume and the platform hand are captured offscreen
and verified to change as the angle advances, and clearing the redstone restores
the blocks.
