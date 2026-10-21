# Turntables

Place **Turntable Surface** blocks as a filled square, from 1×1 to 16×16. The face
you click when placing each block becomes its active face. Every connected
surface must face the same way. Place a **Turntable Controller** against an edge,
by clicking a Surface block at its edge; the controller automatically points back toward it. Open the controller by right
clicking it or using the Configurizer.

Use the **Height** slider to set the number of blocks to capture away from the active face
(1–16), and click **Scan**. The contents become an inert rotating display;
the full square footprint above the surface is captured, including parts that overhang the disc. They do not need a pilot seat, landing gear or propulsion. Block inventories,
tile data and non-player entities are preserved. Captured machines stop ticking. Complete stationary Ramp Controller assemblies
can be captured, including their protected tread blocks; their clocks pause
while captured and resume after Restore.
Players remain in the world. Dismount players or mounts that cross the scan
boundary before scanning.

The surface orientation determines the rotation axis automatically. Capture
always extends away from the textured face. For a ceiling, place downward-facing
Surface blocks; for a wall, face them toward the display. Restore before changing
height. Settings apply immediately.

Activating the rotation input automatically rescans the surface and newly parked
blocks. An existing capture must be at a 90-degree heading to rescan without
snapping its contents. At other angles it keeps rotating its existing capture
and reports why rescan was deferred. Live Taxi vehicles remain in the world
and ride the disc; they are not frozen by a scan.

**Scan** can also trigger when its input changes to ON or OFF. Disabled turns off this separate scan input; rotation activation still rescans
automatically. **Rotate** uses ON or OFF redstone activation, like a Ramp
Controller. New controllers default to ON. Each input has its own comma-separated channel
list. Zero uses local redstone; configured channels require all listed channels
to be powered. Use the speed slider from 0.5 to 10 RPM in 0.5 RPM steps (default 1 RPM) and choose clockwise or
counterclockwise as viewed from outside the active face.

**Direction** accepts a separate channel list. Zero disables direction pulses.
When all listed channels become powered, the direction reverses once. Holding
power does not keep reversing it; release the channels before the next pulse.
The direction button updates to show the result. Right-click an option button
to cycle backward.

**Restore pulse** accepts its own channel list. Zero disables this input. When
all assigned channels become powered, the contents restore once at the nearest
quarter turn, using the same permissions and obstruction checks as the Restore
button. Release the signal before sending another pulse. Holding a signal,
including across save/load, does not keep restoring subsequent captures.

The square shares one surface image. Its circular plate rotates with the captured
contents, surrounded by a fixed square housing. Joined surfaces use the supplied
512px artwork, while standalone and inventory surfaces use a smaller version.
The controller front shows the chosen direction; illuminated artwork indicates
the enabled input state. Obstructions can stop movement while the input stays on.

When the rotation input becomes inactive at 0, 90, 180 or 270 degrees, the
captured contents automatically return to ordinary, editable blocks at that
heading. **Auto-snap** widens this tolerance from 0 to 5 degrees in 0.1-degree
steps (default 0). At 5 degrees, a stop at 85 degrees snaps to editable blocks
at 90 degrees; 355 degrees snaps to 0. The nearest quarter turn is used on
either side of each heading. Stops outside the tolerance keep the exact displayed pose captured. While
paused, use the **Angle** slider to select any heading from 0 to 360 degrees,
or press **Restore** to return blocks at the nearest quarter turn. Manual angle
changes check the swept path for obstructions and carry supported entities.
If an automatic return is blocked, the controller reports the reason and keeps
the captured contents; clear the obstruction and use Restore to retry.

**Restore** returns ordinary blocks at the nearest 90-degree heading, matching
vehicle parking; arbitrary angles cannot be represented by normal block states.
Hover over Restore to preview the heading. Block directions, supported tile data and entity headings follow
the displayed pose. Clear the return positions first: restoration refuses to
overwrite blocks and keeps the captured contents if blocked. The entire assembly
and return volume must be loaded. Sideways platforms can restore ordinary blocks
at cardinal headings; blocks whose orientation cannot be represented and sideways
tile entities require a supported heading before restoring. Remove the controller
after restoring its contents. Scanned Surface blocks are protected from mining,
pistons and explosions until Restore finishes. Protection covers those Surface
blocks, without reserving terrain behind or beneath them. You can attach levers
and other blocks to the controller while contents are captured. If an external
world editor bypasses protection, rotation stops, remaining surfaces keep their
claims, and Restore retains its recovery layout. A blocked rotation reports the
obstruction coordinates in the controller dialog.

The status panel keeps scan errors visible until the next operation; hover over
it to read longer messages. While rotating it reports captured block/entity
counts and the number of live vehicles being carried. The controller reports holes, non-square surfaces, conflicting owners, unloaded
boundaries and incomplete native multiblocks. Capture and restoration respect
world editing permissions and Forge protection events. A durable recovery record
preserves interrupted transfers until their affected chunks and components are
available. Keep the world data and mod installed when moving a save containing
captured displays.

Restore and detach an attached turntable before assembling its supporting blocks
as a vehicle. The vehicle scanner refuses active surface claims and captured
turntable contents.

Craft a controller with eight Programmable Matter Ingots around redstone. Four
ingots in a 2×2 grid craft four Surface blocks.

Turntable artwork adapts the supplied Vandor Labs texture package, based on
Vandor Labs by rheide under CC BY 4.0. Both illumination states and the complete
square surface composition are retained.

Players and loose items standing on a scanned floor turntable move with its
circular surface, including empty parts of the disc. Players can walk and jump
while being carried. The square surface corners stay stationary. Builds above those corners are included in the scan so connected overhangs move together; keep the surrounding rotation path clear. Flying players remain free.
Captured displays do not intercept block selection around the platform.
Taxi vehicles can drive onto a scanned floor turntable, rotate with it, and drive
off at the new heading. Alternatively, park the vehicle as blocks, activate
rotation to rescan at a cardinal heading, rotate it, and stop at a quarter turn to release it before
reassembling in Taxi mode. Use Restore for a stop at another angle. Keep every part of the parked craft within the scan
volume and dismount players before scanning.

For an older opposite-face capture, stop rotation and use Restore before scanning
again. This returns preserved contents to their original positions, including
terrain captured beneath a floor. Clear any newly placed blocks from those
positions first; Restore never overwrites them.


Using the Configurizer on a captured block or a claimed Surface opens its
Turntable Controller, including from the far side of a large platform.
Removing a controller with a command returns captured contents at their current
cardinal heading and releases the surviving surfaces. If return space is blocked,
the orphan display is removed and its contents remain in the recovery record;
they return automatically when the required chunks and clear positions are available.
Replacement terrain is preserved.

Capture and Restore requests prepare their recovery journal on a background
worker. During preparation, captured contents pause and the server checks them
again before conversion. An inventory edit aborts the capture without replacing
the edit. Restore pulses received during preparation are queued until capture
finishes. Lighting and neighbor notifications are processed across ticks.

Turntables and vehicles share a per-world transfer coordinator. Each transfer
checkpoints only its affected chunks, verifies their transaction markers in
region storage, then retires its recovery journal. Slow storage delays completion
without a server-thread disk wait. Conflicting recovery contents retain their
journal rather than overwriting newer blocks, inventories or entities.


### Rotation performance diagnostics

Add the JVM option `-Dvandorlabs.profileTurntables=true` to log turntable CPU timings every ten seconds. Diagnostics are disabled by default. The isolated live-client launcher also accepts `--profile-turntables`; combine it with `--world-copy` and `--turntable-position x,y,z` to compare sustained rotation with a captured, paused platform in a disposable save. The profiling scenario runs rotation for 800 ticks and pauses for 240 ticks before restoring the contents.

The log separates mesh construction, tile renderers and sorting, individual tile renderer classes, server obstacle queries, swept collision, and rider transport. Each section reports call count, mean, recent p95, and maximum duration in microseconds. Parent sections include their child timings; do not add them together. Recent p95 uses the last 512 calls, while mean and maximum cover the whole reporting interval. Initial mesh compilation and resource loading should be assessed separately from later intervals.

These measurements time CPU work and any driver waits encountered by the calling thread. They are not GPU execution timings or whole-frame FPS. Compare the same view, graphics settings, structure, and loaded surroundings while paused and rotating; use a full CPU/GPU profiler if these sections do not explain the slowdown. Reducing visual interpolation does not reduce server collision work.

The 2.3 native-client comparison used 64 opaque programmable walls, identical camera and lighting, 64 warm-up draws and 32 measured draws per path. Cached rendering produced identical pixels and reduced median rendering CPU time from 318 to 116 microseconds (64%); p95 fell from 595 to 190 microseconds. Rotation reused all 64 cached wall meshes. This isolates wall drawing and does not predict the same percentage improvement in whole-frame FPS.

An isolated server fixture with 128 captured walls above 256 floor blocks measured 488 microseconds median, 539 microseconds p95 and 701 microseconds maximum per movement update. This is a current-build measurement, not a before/after comparison or a substitute for profiling a populated world.

Rigid opaque wall artwork now compiles within the shared mesh budget; translucent and interactive surfaces continue to render live. The renderer sorts only surfaces needing the translucent pass, and platform bounds reuse a precomputed extent. Smooth interpolation remains enabled: lowering its visual rate would leave server obstacle queries and collision work unchanged. For a remaining slowdown, first compare the diagnostic sections in the affected save; consider spatial collision indexing or batching remaining live renderers when measurements identify either as the dominant cost.
