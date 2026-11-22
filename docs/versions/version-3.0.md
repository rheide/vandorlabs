# Vandor Labs 3.0

## Maxwell Drive

The Maxwell Drive gives a Forge 1.12.2 vehicle sustained forward thrust. Build a frame **3 × 3 × 4**, **4 × 4 × 5**, **5 × 5 × 7**, **6 × 6 × 8** or **9 × 9 × 12** (width × height × depth). Each drive has a **1,000 mB** tank. Connect a fluid line to the marked inlet on the front or either side. The front gauge shows the remaining fuel.

Use Refined Fuel, Ethanol, Fuelium or Rocket Fuel. Right-click the drive to set its redstone response or purge its tank before changing fuels. At full thrust, a drive uses **1 mB every 10 seconds** at 20 ticks per second. Analog signal strength scales thrust and consumption together. A signaled drive can burn fuel while placed, taxiing or hovering. A drive with redstone control disabled starts automatically when the pilot engages Flight.

## Taking off

1. Assemble a craft with a Vehicle Controller, Pilot Seat, Vehicle Status screen and at least one fueled Maxwell Drive. Open the Controller's **Flight** tab to compare the effective weight to the weight the available thrust can lift. Antigravity Plates and Repulsor Arrays reduce effective weight.
2. Sit in the Pilot Seat and select **Flight** on Vehicle Status. From Hover, the craft starts accelerating forward. From Taxi, use a clear runway to build takeoff speed.
3. Use **A/D** to turn and **W/S** to pitch the nose down or up. Below Y=256 the target flight speed is 1.5 times Hover sprint speed. The Vehicle Status screen shows speed, altitude and combined fuel.
4. A collision or a loss of thrust starts recovery. A craft equipped for Hover can level out and hold altitude. A Maxwell-only aircraft descends toward the ground.

## Overworld cruise

Above Y=256 the rider travels over a seed-colored view of the Overworld. The route uses virtual X/Z coordinates. The departure area stays loaded while occupied. Descent loads terrain at the destination. The instruments show altitude during cruise and omit ground clearance.

Available thrust and effective weight determine the altitude ceiling. A sufficiently powerful craft can reach **Y=10,000**, where its target speed is 50 times the low-altitude flight speed. Reducing thrust lowers the attainable ceiling. The craft descends until its thrust can support flight again.

## Reading a channel

Choose **Channel status** in the Redstone row editor to display the strongest received level as five battery bars. A level of zero leaves the battery empty. Each bar covers three signal levels. Use a Tank or Battery's transmitter channel to monitor its fullness on a screen or cockpit panel.
