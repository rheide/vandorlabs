# Maxwell Drive

The Maxwell Drive supplies thrust for Flight on Forge 1.12.2 and NeoForge 1.21.1. Build it with one Maxwell Drive Core and [Programmable Blocks](../README.md).

| Width × height × depth | Programmable Blocks |
| --- | ---: |
| 3 × 3 × 4 | 25 |
| 4 × 4 × 5 | 41 |
| 5 × 5 × 7 | 66 |
| 6 × 6 × 8 | 87 |
| 9 × 9 × 12 | 173 |

The construction below shows the 3 × 3 × 4 frame. Use the same base, pillars and roof rim for each size. Drive mass and thrust increase with the enclosed volume.

## How many drives do I need?

Read the **effective weight** on the Vehicle Controller. It includes the installed drives and cargo, after lift from Antigravity Plates and Repulsor Arrays. Each column below shows the maximum whole weight units **one fueled drive at full thrust** can support at that altitude in the Overworld.

| Drive size | Takeoff / Y=256 | Y=1,000 | Y=5,000 | Y=10,000 | Example ceiling with one drive |
| --- | ---: | ---: | ---: | ---: | --- |
| 3 × 3 × 4 | 512 | 433 | 232 | 142 | 400 units reach Y≈1,406 |
| 4 × 4 × 5 | 1,137 | 963 | 517 | 316 | 1,000 units reach Y≈825 |
| 5 × 5 × 7 | 2,488 | 2,108 | 1,132 | 691 | 1,000 units reach Y≈6,082 |
| 6 × 6 × 8 | 4,096 | 3,470 | 1,863 | 1,138 | 1,000 units reach Y=10,000 |
| 9 × 9 × 12 | 13,824 | 11,711 | 6,287 | 3,841 | 5,000 units reach Y≈7,088 |

Choose the column for your target altitude and add the capacities of the drives you plan to run. For example, a finished craft weighing **1,000 effective units** can take off with **two 3 × 3 × 4 drives** (2 × 512), but needs **five** of that size for Y=5,000 (5 × 232). **One 5 × 5 × 7** also covers that climb. Recheck the weight after adding engines or loading cargo.

The Controller’s **Theoretical Max Takeoff Weight** includes every installed drive at full power. The **current takeoff limit** responds to fuel and redstone throttle. The altitude ceiling uses that current thrust.

## 1. Build the base

Lay out a solid 3-block-wide by 4-block-deep floor. Place the Core in a bottom corner, facing the front of the drive. The nozzle will point toward the opposite end.

![Maxwell Drive: build the base](step-1.png)

## 2. Raise the corner pillars

Build up from each of the four corners until the pillars are three blocks tall, counting the base. Leave the middle layer empty between the pillars.

![Maxwell Drive: raise the corner pillars](step-2.png)

## 3. Connect the top rim

Join the pillar tops with a continuous rectangular rim. Leave the middle of the roof open.

![Maxwell Drive: connect the top rim](step-3.png)

## 4. Assemble

Right-click the Core to form the drive and reserve the frame volume. If assembly fails, check the reported frame cell and clear the interior.

![Maxwell Drive: assembled fuel inlets and gauge](step-4.png)

The assembled drive shown here has fuel in its tank.

## Fuel and throttle

The front and sides each have a Fluid Port In that feeds the same 1,000 mB tank. On even-sized faces the side inlet is in the center cell closer to the front. The bars beside the front inlet show the remaining fuel. Fill the drive with Thermal Refined Fuel, Ethanol, MrCrayfish’s Fuelium, or Rocket Fuel from Advanced Rocketry or Galacticraft. Use one fuel at a time. On NeoForge, the `vandorlabs:maxwell_fuels` fluid tag can add other registered fuels.

Right-click the formed drive or use the Configurizer to open its **Engine** and **Redstone** tabs. The Engine tab shows the tank level and has a **Purge Tank** button that discards its contents.

- **Activation: Disabled** lets the drive run automatically during powered Flight and its takeoff roll.
- **Activation: Redstone ON** uses the stronger of the physical input and any assigned receiving channel. **Activation: Redstone OFF** inverts that signal.
- **Signal: Digital** applies full thrust when the effective signal is positive. **Signal: Analog** scales thrust and consumption from 0 to 15. Full throttle uses 1 mB every 10 seconds and produces a yellow-white afterburner plume.

A redstone command can burn fuel when the drive is parked, for example to test the exhaust before flight. Vehicles can use several drives. Their weight contributes to takeoff and altitude limits.

## Removal

Sneak-right-click to dismantle, keeping the tank in its Core. Breaking the Core restores the remaining frame blocks. Mining a reserved cell dismantles the machine and removes that cell.

[All multiblock guides](../README.md)
