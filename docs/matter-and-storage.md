# Programmable matter and utility storage

Programmable Matter Infusers turn one iron ingot into one Programmable Matter ingot in 200 working ticks. The lens needs an unobstructed view of the sun; night and rain pause processing and fuel consumption. Redstone dust supplies 33 working ticks, and a redstone block supplies 300, matching a vanilla log. A blocked output also pauses fuel use. Insert iron from above, redstone from the sides, and extract the product below with hoppers or item-handler pipes.

Craft the infuser with iron around a glass lens, an Infuser Core in the center, and redstone at the bottom. Infuser Cores are currently available in creative mode only. The former direct crafting recipe for Programmable Matter ingots is removed. Nine ingots craft one Programmable Matter Block; the block can be unpacked into nine ingots.

Battery Blocks hold 100,000 RF and transfer up to 1,000 RF per operation. The green front port accepts energy; the orange rear port supplies energy and pushes it to an adjacent receiver. Other sides are insulated. Right-click for a live charge readout, vertical fullness meter and instant **Shape: Block/Hexagon** control. Click a support face to point the input port in that direction, including up or down. The closed hexagonal shell uses the tank cross-section, with flat top and bottom faces when horizontal. Segmented green bars on both physical top and bottom faces show the stored charge; they update while energy flows. Shape changes preserve charge and ports. Existing batteries retain their block shape and saved facing. Mining or picking a battery preserves its charge and shape.

Hexagonal Fuel Tanks hold 16,000 mB per block and accept registered Forge fluids through their two axial ports. Right-click with a fluid container to fill or empty them. Click the top or bottom of a support for vertical tanks, or a side for horizontal tanks. Connect matching tanks along their axis to share capacity; either end can access the combined contents. Filling starts at the lower end of a vertical stack. Right-click without a fluid container for the combined current/max volume and vertical fullness meter. Different fluids cannot be mixed. Each cell saves its own volume, so splitting a stack preserves the contents of each remaining cell. Mining a tank preserves its contents in the dropped block. Glass windows look into a hollow interior; horizontal hexagonal tanks use square windows on the four diagonal sides and solid flat top and bottom faces. Stored fluid occupies a three-dimensional volume with a horizontal surface. Horizontal tanks align their hexagonal end faces and ports with the connection axis, like hexagonal thrusters; their cross-section keeps flat sides above and below. The fill height accounts for the changing cross-section so the visible volume matches partial fills. Choose **Shape: Hexagon** or **Shape: Block** in the volume dialog; it applies immediately without changing contents, capacity or connections. Both shapes have a hollow interior, central side windows and a real fluid level. Right-click and the Configurizer open the same dialog, containing the casing picker, shape selector and live contents meter.

The implementations use Forge's built-in `IEnergyStorage` and `IFluidHandler` capabilities, requiring no extra mod. Forge Energy is based on the Redstone Flux API, but machines exposing only the older CoFH interfaces need a compatible bridge. See [Forge's capability documentation](https://docs.minecraftforge.net/en/1.12.x/datastorage/capabilities/).

Seven Data Cards and seven matching Data Chips are available in the creative catalog: unbranded, Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor. They use thin solid models with extracted front artwork and clipped corners. They can be held in hand and inserted in matching access mounts; cards also operate secured Programmable Doors. Their metadata values are 0 through 6 in that order (`vandorlabs:data_card` and `vandorlabs:data_chip`).

Tank settings use a compact combined dialog for casing, Hexagon/Block shape,
Tile/Fit texture layout, and live fluid quantity. Tile keeps the casing artwork's
block scale; Fit places the whole image across each side. Square side windows
show the modeled interior and actual fluid level. Shape and texture changes
preserve stored fluid and axial connections.

Hexagonal tanks share the cross-section and directional shell shading of
hexagonal propulsion blocks. Thrusters have straight rear rims so a tank can
meet the back of an engine without a beveled gap.

Battery casings can use any general block material from the casing picker. Changes apply immediately and persist when the battery is saved, picked or broken. The green input and orange output ports retain their markings in both Block and Hexagon shapes.


## Liquid-filled walls and partial blocks

Programmable Walls, Porthole Walls, Glass Walls and their diagonal variants can hold a bucket of liquid on each side independently. Use a filled bucket on a side to fill that side; use an empty bucket on the same side to recover its contents. The two sides can contain different liquids. A full side rejects another filled bucket without placing liquid beside the wall.

Registered Forge liquids with a compatible bucket or fluid container are supported; gases are excluded. The visible liquid fills the free space around the configured wall geometry and uses the fluid's texture, colour and luminosity. Contents stay contained and do not spread into neighbouring blocks. Saving, copying and vehicle capture preserve both sides. Slabs and stairs use the same contained-fluid storage; older waterlogged saves migrate automatically.

Placing a supported block into liquid automatically captures it in the free space. At a wet/dry boundary only the face adjoining liquid fills; the opposite face stays dry. Opposing liquids can fill their respective sides independently. An isolated replaced fluid cell is retained when neither face adjoins another liquid. This includes source and flowing water/lava and registered Forge liquids, with the same behavior for doors, trapdoors and diagonal variants. Ordinary water and lava join the matching contained liquid without an internal border; an empty compartment or a different liquid retains its visible boundary.

Visible fluid follows continuous diagonal surfaces and reaches inset porthole/glass panes. Adjacent volumes of the same liquid omit their shared faces, including partial joins; different liquids retain their separating interface. The visible surface cache refreshes after shape or fluid changes, rather than rebuilding meshes during rotation. Entity collision remains based on the solid block's collision geometry.

### Doors and trapdoors

Programmable Door, Programmable Trapdoor and Programmable Diagonal Trapdoor use the same two-sided bucket storage. A door's upper and lower halves share the stored sides. Sides refer to the closed leaf, so opening or rotating the leaf does not exchange them.

Opening a leaf with water on either side fills its passage with water. This includes directly adjacent world water. Closing restores the original sides: a dry opposite side clears again, while water originally stored on both sides remains. This passage fill stays inside the opening and does not create surrounding source blocks or a second bucket of stored water. Empty buckets drain the actual stored side. A different liquid explicitly stored opposite the water is retained.

The diagonal trapdoor uses continuous sloped fluid faces rather than collision slices, keeping its water surface free of horizontal bands. Stored sides survive saves and vehicle capture; the temporary opening fill is derived from the current open state.
