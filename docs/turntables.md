# Turntables

Place **Turntable Surface** blocks as a filled square, from 1×1 to 16×16. The face
you click when placing each block becomes its active face. Every connected
surface must face the same way. Place a **Turntable Controller** against an edge,
with its front pointing at a surface block, then open the controller by right
clicking it or using the Configurizer.

Set **Height** to the number of blocks to capture away from the active face
(1–16), and click **Scan**. The contents become an inert rotating display;
they do not need a pilot seat, landing gear or propulsion. Block inventories,
tile data and non-player entities are preserved. Captured machines stop ticking.
Players remain in the world. Dismount players or mounts that cross the scan
boundary before scanning.

**Axis: Auto** uses the axis perpendicular to the surface. X, Y and Z explicitly
require the corresponding surface orientation. **Capture side: Opposite face**
selects the other side, useful for a ceiling or the back of a wall. Restore
captured contents before changing the height, axis or capture side.

**Scan** can also trigger when its input changes to ON or OFF. Disabled keeps
scanning manual. **Rotate** supports ON, OFF or Manual; the separate rotation
enable switch stops either mode. Each input has its own comma-separated channel
list. Zero uses local redstone; configured channels require all listed channels
to be powered. Set speed from 0.1 to 30 RPM and choose clockwise or
counterclockwise as viewed from outside the active face.

The square shares one surface image. Its circular plate rotates with the captured
contents, surrounded by a fixed square housing. Joined surfaces use the supplied
512px artwork, while standalone and inventory surfaces use a smaller version.
The controller front shows the chosen direction; illuminated artwork indicates
the enabled input state. Obstructions can stop movement while the input stays on.

Click **Restore**, or right click the captured display, to return its blocks and
entities to their original scan positions. Rotation does not rewrite their
original orientations. Clear those positions first: restoration refuses to
overwrite blocks. The entire assembly and return volume must be loaded. Remove
the controller after restoring its contents. Damaging a surface stops rotation;
the captured display retains its contents until they can be restored.

The controller reports holes, non-square surfaces, conflicting owners, unloaded
boundaries and incomplete native multiblocks. Capture and restoration respect
world editing permissions and Forge protection events. A durable recovery record
preserves interrupted transfers until their affected chunks and components are
available. Keep the world data and mod installed when moving a save containing
captured displays.

Craft a controller with eight Programmable Matter Ingots around redstone. Four
ingots in a 2×2 grid craft four Surface blocks.

Turntable artwork adapts the supplied Vandor Labs texture package, based on
Vandor Labs by rheide under CC BY 4.0. Both illumination states and the complete
square surface composition are retained.
