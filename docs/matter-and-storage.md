# Programmable matter and utility storage

Programmable Matter Infusers turn one iron ingot into one Programmable Matter ingot in 200 working ticks. The lens needs an unobstructed view of the sun; night and rain pause processing and fuel consumption. Redstone dust supplies 33 working ticks, and a redstone block supplies 300, matching a vanilla log. A blocked output also pauses fuel use. Insert iron from above, redstone from the sides, and extract the product below with hoppers or item-handler pipes.

Craft the infuser with iron around a glass lens, an Infuser Core in the center, and redstone at the bottom. Infuser Cores are currently available in creative mode only. The former direct crafting recipe for Programmable Matter ingots is removed. Nine ingots craft one Programmable Matter Block; the block can be unpacked into nine ingots.

Battery Blocks hold 100,000 FE and transfer up to 1,000 FE per operation. The green front port accepts energy; the orange rear port supplies energy and pushes it to an adjacent receiver. Other sides are insulated. Right-click to inspect the stored charge. Rotate placement to choose the input direction.

Hexagonal Fuel Tanks hold 16,000 mB per block and accept registered Forge fluids through their top and bottom ports. Right-click with a fluid container to fill or empty them. Stack tanks vertically to share capacity: filling starts at the bottom, and either end can access the combined contents. Different fluids cannot be mixed. Each cell saves its own volume, so splitting a stack preserves the contents of each remaining cell. Mining a tank preserves its contents in the dropped block. The two windows show each cell's fluid color and fill level. Use the Configurizer to choose a casing texture.

The implementations use Forge's built-in `IEnergyStorage` and `IFluidHandler` capabilities, requiring no extra mod. Forge Energy is based on the Redstone Flux API, but machines exposing only the older CoFH interfaces need a compatible bridge. See [Forge's capability documentation](https://docs.minecraftforge.net/en/1.12.x/datastorage/capabilities/).
