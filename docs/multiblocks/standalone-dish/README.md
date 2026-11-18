# Survey Dish

Build a **3 wide × 3 deep × 3 high** assembly with **20 Programmable Matter Blocks** and **one Survey Dish Core**. Core blocks are available in Creative, following the existing Universal Constructor system.

## 1. Build the base

Place a solid **3 × 3** bottom layer: one matching Core in the front-left corner, facing you, and **eight Programmable Matter Blocks**.

![Survey Dish: build the base](step-1.png)

## 2. Raise the corner pillars

Add one Matter Block above each of the **four corners**. This adds **four Matter Blocks** and makes each pillar two blocks high. Leave the other five positions in this middle layer empty.

![Survey Dish: raise the corner pillars](step-2.png)

## 3. Connect the top rim

Add a **3 × 3 perimeter** as the third and final layer: **eight Matter Blocks**, with the center left empty. The completed frame uses twenty Matter Blocks plus its Core. Keep the middle-layer interior and the roof center empty.

![Survey Dish: connect the top rim](step-3.png)

## 4. Assemble

Right-click the Core to form the machine. Its occupied cells are reserved. If formation fails, correct the cell reported in chat and retry.

![Survey Dish: assemble](step-4.png)

## Operation and scanning

This standalone observer has no operator screen, keyboard or chip slot. Use the
Configurizer on the Core or any reserved cell to open its Redstone dialog. Set
Transmit channels and the connection range for nearby navigation displays. The
current **Scanning radius** appears below these controls. Scanning needs a
formed, loaded assembly; power and the cosmetic activity state do not gate it.

At Y=64, the Sentinel Array radius is 250 blocks and the Survey Dish radius is
1,000 blocks. Range rises linearly to 500 / 2,000 blocks at Y=128, then stays
capped for the base size. Minimum radii below Y=64 are 63 / 250 blocks. An arm's current physical pose determines the height and
coverage center while extending, retracting or rotating.


### Larger proportional frames

Use the same four construction stages for a larger frame: build a solid N×N
base with one Core at a corner, raise only the four corner pillars through the
middle layers, then connect the final N×N perimeter. Leave every other cell
air. For an inverted build, follow these stages downward from the top Core.
The assembled model scales uniformly to fit the frame.

Survey Dishes accept N×N×N frames for N=3–16. Sentinel Arrays accept N×N×2N
frames for N=2–8 (dimensions are width × depth × height). The existing limit is
16 blocks per axis. The frame needs `N² + 4H + 4N − 13` Matter Blocks plus one
Core, where H is its height.

| Observer | Frame | Matter Blocks | Radius at Y=64 | Radius at Y=128+ |
| --- | --- | ---: | ---: | ---: |
| Survey Dish | 3×3×3 | 20 | 1,000 | 2,000 |
| Survey Dish | 4×4×4 | 35 | 1,000 | 2,667 |
| Survey Dish | 5×5×5 | 52 | 1,000 | 3,333 |
| Survey Dish | 6×6×6 | 71 | 1,000 | 4,000 |
| Sentinel Array | 2×2×4 | 15 | 250 | 500 |
| Sentinel Array | 3×3×6 | 32 | 250 | 750 |
| Sentinel Array | 4×4×8 | 51 | 250 | 1,000 |

The size bonus rises linearly from zero at Y=64 to its full value at Y=128.
Twice the base linear dimensions gives +100% radius; larger frames retain that
cap. Height below Y=64 follows the base observer rule without a size bonus.
The Core's current physical height is used, including on moving arms.

## Upside-down construction

Mirror steps 1–3 vertically: start with the solid base overhead and its Core in
a corner, build the pillars downward, and finish the open rim at the bottom.
Right-click the top Core to form the inverted observer. All four horizontal
facings and corner placements work in either orientation. Sneak-right-click
the Core to dismantle it and restore the frame.

Older non-standalone variants remap to this model. Previously inserted chips
remain saved safely and return once when dismantling or breaking the owner.
New chips cannot be inserted.

[Navigation displays and scanning](../../navigation-displays.md#seed-navigation-forge-1122)

## Formed model footprint

The Survey Dish keeps its 3×3 base footprint and existing save/arm layout.
Its mounting plate spans 2.875 blocks in both horizontal directions, with
a margin inside the reserved cells. The body is enlarged within those same
limits; the Dish containment check includes its complete ±55-degree sweep.
Proportional larger frames and inverted builds retain this fit.
