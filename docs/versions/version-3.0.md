# Vandor Labs 3.0

## Maxwell Drive

The Maxwell Drive gives a Forge 1.12.2 vehicle sustained forward thrust. Build a frame **3 × 3 × 4**, **4 × 4 × 5**, **5 × 5 × 7**, **6 × 6 × 8** or **9 × 9 × 12** (width × height × depth). Each drive has a **1,000 mB** tank. Connect a fluid line to the marked inlet on the front or either side. The front gauge shows the remaining fuel.

See the [illustrated Maxwell build guide](../multiblocks/maxwell-drive/README.md) for frame counts, port positions and a table of how much each drive lifts at takeoff and at altitude.

Use Refined Fuel, Ethanol, Fuelium or Rocket Fuel. Right-click the drive to choose **Redstone ON** or **Redstone OFF** as its activation, select a **Digital** or **Analog** signal, or purge the tank before changing fuels. At full thrust, a drive uses **1 mB every 10 seconds** at 20 ticks per second. Analog signal strength scales thrust and consumption together. A signaled drive can burn fuel while placed, taxiing or hovering. With **Activation: Disabled**, the drive starts automatically when the pilot engages Flight.

## Taking off

1. Assemble a craft with a Vehicle Controller, Pilot Seat, Vehicle Status screen and at least one fueled Maxwell Drive. Open the Controller's **Flight** tab to compare the effective weight to the weight the available thrust can lift. Antigravity Plates and Repulsor Arrays reduce effective weight.
2. Sit in the Pilot Seat and select **Flight** on Vehicle Status. Park and Taxi occupy the top row, Hover and Flight the bottom row. Gray buttons show modes the craft cannot currently select. From Hover, the craft starts accelerating forward. From Taxi, use a clear runway to build takeoff speed, then press **S** to lift the nose.
3. Hold **A/D** to roll left or right on Forge. Release the key to hold the bank and turn, or use the opposite key to level the wings. With enough clearance, keep rolling through a full turn. **W/S** lower or raise the nose relative to the aircraft. The view follows the aircraft’s heading, pitch and roll, so a window or instrument you aim at stays in the same place in the cockpit view while the aircraft turns. Above 45 degrees nose-up, the aircraft accelerates more slowly and tends to level back toward 45 degrees.
4. If the Maxwell output can no longer support your altitude, the nose lowers and the aircraft keeps moving in Flight while it descends. If the drives stop entirely, the aircraft glides, then levels out near the surface to land. A craft with Hover equipment can level and switch to Hover below the cruise band. Terrain contact starts a controlled recovery.

Selecting **Hover** from Flight levels the craft while it slows along its current heading. It carries the remaining forward motion into Hover at a supported speed.

In Forge Hover, hold **Space** to rise or **Sneak** to descend at up to **8.4 blocks per second**. Release the key to hold the selected altitude. Horizontal Hover speed is 16 blocks per second, or 20 while sprinting.

For recovery below the cruise band, Creative players holding a Configurizer can select **Emergency Park** on Vehicle Status. It uses the Controller’s Safe Park search to find space for the craft.

## Flight instruments

In the Forge screen or cockpit artwork picker, expand **Vehicle Status** and choose a display for that surface. **Status** keeps the mode controls and fuel readout. The **Attitude Indicator** shows pitch and bank, **Compass** shows heading, **Altimeter** shows elevation, and **Airspeed Indicator** shows speed in m/s. The airspeed dial scales to the craft’s projected maximum.

On a full-height display, enable **Split display** and choose a **Vehicle Status** option on the **Top** or **Bottom** tab. **Status (Half)** keeps the clickable mode controls. Pair it with a compact instrument or regular artwork, or put a different instrument on each half.

Canopy, Long Canopy, and Front Canopy glazing can also display these instruments. Use the canopy’s **Main** and **Side** tabs to choose each finish independently. The Side selection applies to both side panes. If framebuffers are disabled in your video settings, the pane displays ordinary glass until framebuffers are enabled.

Open the Controller’s **Flight** tab for projected maximum speed and altitude at full installed Maxwell thrust. The projections account for effective weight and the dimension’s ceiling. Below Y=256 the normal powered Flight target is 30 m/s. A light craft with one Maxwell can build toward 100 m/s in the first part of its climb above that height.

## Overworld cruise

From Y=256 to Y=512, the seed surface gradually fades in over the visible Overworld chunks. The aircraft uses normal terrain tracking throughout this band. Above Y=512 it enters its configured cruise mode with a fully opaque seed surface. On descent, the destination terrain starts loading at Y=512, and excess airspeed bleeds off through the band to the low-altitude limit at Y=256. The view starts preparing at Y=200. Near the build ceiling, each ground pixel covers four blocks. Coarser tiles extend the horizon about 4,000 blocks in each direction. The viewing distance grows with altitude. Nearby hills, coastlines and slope shading in standard Overworlds use the seed’s terrain density. Distant tiles use the seed’s biome layers. Player builds and trees are absent from this view. Vanilla Overworlds generate tiles on a background thread and share up to 64 MiB of reusable tile pixels per world in memory. Cruise does not write these tiles to disk. Custom biome generators use a limited world-thread sampler. Instruments show flight altitude and omit ground clearance during cruise. Descend to Y=256 or below to select Taxi or Hover manually.

In the server’s `config/vandorlabs-blocks.cfg`, choose `cruiseCoordinates` under `vehicles`.

| Setting | What moves during cruise | Terrain and map behavior |
| --- | --- | --- |
| `anchored` (default) | The aircraft’s geographic route advances. Minecraft keeps the riders at the departure X/Z. | Other mods querying the player’s surroundings see the departure area. JourneyMap follows the route. |
| `resident` | Every 40 ticks, after at least 64 blocks of travel, the aircraft can move to the current route position. | A hop requires the entire hull and all rider view areas to be loaded and populated already. The aircraft skips wilderness hops. Minecraft can reload existing terrain near an occupied stop. |
| `physical` | The aircraft and riders move continuously at the real geographic X/Z. F3 shows those coordinates. | Forge keeps terrain tracking at the departure area. Another mod that reads terrain at the rider’s actual coordinates can load route chunks. Players outside the departure tracking area may not see the aircraft overhead. |

Use the altimeter for flight altitude. F3 Y includes the cruise sky offset. Physical cruise supports one controlling pilot. Additional passengers can board before cruise for anchored group travel, or after descent. The aircraft also falls back to anchored travel when the server or pilot’s client lacks compatible tracking hooks.

With JourneyMap 5.5.5 installed, the minimap, the player-centered fullscreen map and the in-world waypoint beacons follow the geographic route. Creating a waypoint at your current position uses the geographic coordinates and flight altitude. Previously explored terrain is available from JourneyMap’s own cache. Unexplored terrain can appear blank on the map.

A vanilla or JourneyMap teleport releases the rider. When the last pilot teleports away, the aircraft stops and lands below its current geographic position. This loads the aircraft’s landing site as well as the player’s teleport destination.

![Seed-colored coastlines viewed from a Pilot Seat above the Overworld](../images/gallery/maxwell-overworld-cruise.png)

Available thrust and effective weight determine the altitude ceiling. A sufficiently powerful craft can reach **Y=10,000**, where its target speed is 50 times the low-altitude flight speed. Reducing thrust lowers the attainable ceiling.

One full 3 × 3 × 4 drive supports 512 effective weight units at takeoff. Larger drives scale by volume. Above Y=256, multiply the takeoff thrust requirement by `1 + 24 × (Y − 256) / (100,000 − Y)`. For example, twice the takeoff thrust reaches about **Y=4,246**. Reaching **Y=10,000** takes about **3.60 times** the takeoff thrust.

## Joined canopy glass

Place two or more Canopies side by side facing the same direction, or use a row of Long Canopies. Select **Stretch** to spread one image across the joined windshield and roof. Select **Tile** for repeated artwork. The image stays attached to the glass as the canopy opens.

## Supplying engines and observers on board

Connect an onboard tank's output to a Maxwell inlet with a compatible fluid pipe. For example, a Mekanism Mechanical Pipe can supply the engine from storage, and a Thermal Dynamics Fluxduct can carry RF from an unlinked RFTools Power Cell to the bottom of a Survey Dish. An Arm can pass its selected resource between its base and tip once it reaches a settled position. See the [onboard connection guide](../resource-ports.md#vehicle-mode-on-forge-1122) for supported pipes, storage and endpoint settings.

## Reading a channel

Choose **Channel status** in the Redstone row editor to display the strongest received level as five battery bars. A level of zero leaves the battery empty. Each bar covers three signal levels. Use a Tank or Battery's transmitter channel to monitor its fullness on a screen or cockpit panel.

## Split and diagonal displays

On Forge, turn on **Split display** and choose **Redstone (Half)** for **Top** or **Bottom**. Each half has its own four-row editor and can share a display with artwork or a vehicle instrument. A Programmable Diagonal Screen also offers **Profile** on **Displays**. **Full** keeps the original wedge, and **Shallow** uses a half-block rise with the same display proportions. Both choices are saved with the configured screen. [Screen configuration guide](../frames-and-displays.md)

## Minecraft 1.21.1

The experimental NeoForge build supports Taxi, Hover and Maxwell Flight, including a return to the same aircraft after a server restart in high cruise. Operate redstone controls from the assembled hull and feed engines or observers through native Ports and settled Arms. Finite Mekanism Fluid Tanks and Energy Cubes and unlinked RFTools Dimensional Cells retain their contents through parking.

The native Gantry makes both Vehicle Couplers. Engage a pair before assembly to join two parked sections into one rigid hull. The stationary Cockpit offers seven configurable surfaces. See the [1.21.1 vehicle construction and operating guide](../vehicles-1.21.md).
