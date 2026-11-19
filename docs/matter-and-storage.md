# Programmable matter and utility storage

Programmable Matter Infusers turn one iron ingot into one Programmable Matter ingot in 200 working ticks. The lens needs an unobstructed view of the sun; night and rain pause processing and fuel consumption. Redstone dust supplies 33 working ticks, and a redstone block supplies 300, matching a vanilla log. A blocked output also pauses fuel use. Insert iron from above, redstone from the sides, and extract the product below with hoppers or item-handler pipes.

On Forge 1.12.2 and NeoForge 1.21.1, the same input slot also accepts a [Battery Module](module-rack.md#battery-module).
It stores 10,000 RF and receives 50 RF per sunny working tick while consuming
the same redstone catalyst. A full module moves into the output slot when
available. Partial charge remains on the item when it is removed or saved.
Right-click or use the Configurizer to open the same inventory dialog. Both
versions use the standard Vandor header, sunlight status, catalyst slot and
production or battery-charge progress display; solar work has no furnace flame.

Craft the infuser with iron around a glass lens, an Infuser Core in the center, and redstone at the bottom. Infuser Cores are currently available in creative mode only. The former direct crafting recipe for Programmable Matter ingots is removed. The Assembly Press turns one ingot and 8,000 RF into one Programmable Plate. Craft Programmable Armor from plates using the standard armor patterns.

Programmable Block uses the Matter artwork as its default finish and serves as the frame material. Craft four Programmable Blocks with four Industrial Alloy Ingots around Redstone. Existing Programmable Matter Blocks become Programmable Blocks.

Battery Blocks and Hexagonal Fuel Tanks use the same capacities, shapes and construction rules on Forge 1.12.2 and NeoForge 1.21.1. Power Ports connect to a battery's marked input/output faces; Fluid Ports connect through a tank's axial ports. Use the [Resource Port guide](resource-ports.md) for transfer limits and optional input pulling, and the [joined tank guide](multiblocks/hexagonal-tank/README.md) for complete 2 × 2 and 3 × 3 sections.

Battery Blocks hold **100,000 RF** and use the same **General**, **Housing** and **Redstone** dialog as Power Ports. Right-click or use the Configurizer to configure all six world faces, optional input pulling and the shared output limit. New batteries start with an Input facing the player's view, an Output opposite it, pulling Off and an unlimited output limit. Look steeply down or up to place a vertical input. Existing saved batteries retain their facing, shape, casing, charge and 1,000 RF transfer limit; clear the output-limit field to switch them to unlimited port behavior.

The **Housing** tab contains the live charge meter, standard material picker and **Shape: Block/Hexagon** control. The original Disabled-side artwork is the green battery-cell panel, with segmented markings that update as charge changes. Input and Output faces use the Power Port artwork; on a hexagonal shell their image is cropped and tiled at the normal texture scale. Shape and material changes preserve the single charge store and configured faces. Mining or picking a battery preserves its contents and configuration.

Hexagonal Fuel Tanks hold 16,000 mB per block and accept registered fluids through their two axial ports. Right-click with a fluid container to fill or empty them. Click the top or bottom of a support for vertical tanks, or a side for horizontal tanks. Connect matching tanks along their axis to share capacity; either end can access the combined contents. Filling starts at the lower end of a vertical stack. Right-click without a fluid container for the combined current/max volume and vertical fullness meter. Different fluids cannot be mixed. Each cell saves its own volume, so splitting a stack preserves the contents of each remaining cell. Mining a tank preserves its contents in the dropped block. Glass windows look into a hollow interior; horizontal hexagonal tanks use square windows on the four diagonal sides and solid flat top and bottom faces. Stored fluid occupies a three-dimensional volume with a horizontal surface. Horizontal tanks align their hexagonal end faces and ports with the connection axis, like hexagonal thrusters; their cross-section keeps flat sides above and below. The fill height accounts for the changing cross-section so the visible volume matches partial fills. Choose **Shape: Hexagon** or **Shape: Block** in the volume dialog; it applies immediately without changing contents, capacity or connections. Both shapes have a hollow interior, central side windows and a real fluid level. Right-click and the Configurizer open the same dialog, containing the casing picker, shape selector and live contents meter.

Both targets use their platform's built-in `IEnergyStorage` and `IFluidHandler` capabilities, requiring no extra mod. Forge Energy is based on the Redstone Flux API, but machines exposing only the older CoFH interfaces need a compatible bridge. See [Forge's capability documentation](https://docs.minecraftforge.net/en/1.12.x/datastorage/capabilities/).

Seven Access Cards and seven matching Data Chips are available in the creative catalog: unbranded, Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor. They use thin solid models with extracted front artwork and clipped corners. They can be held in hand and inserted in matching access mounts; cards also operate secured Programmable Doors. Their metadata values are 0 through 6 in that order (`vandorlabs:access_card` and `vandorlabs:data_chip`).

Tank settings use a compact combined dialog for casing, Hexagon/Block shape,
Tile/Fit texture layout, and live fluid quantity. Tile keeps the casing artwork's
block scale; Fit places the whole image across each side. Square side windows
show the modeled interior and actual fluid level. Shape and texture changes
preserve stored fluid and axial connections.

Hexagonal tanks share the cross-section and directional shell shading of
hexagonal propulsion blocks. Thrusters have straight rear rims so a tank can
meet the back of an engine without a beveled gap.

Battery Housing materials apply only to Disabled faces. Choose **Original artwork** to restore the green charge panels. Input and Output markings remain visible in both Block and Hexagon shapes. Tank end ports use the Fluid Port artwork; the windows, fluid storage and axial connections remain the same.


## Liquid-filled walls and partial blocks

Programmable Walls, Porthole Walls, Glass Walls and their diagonal variants can hold a bucket of liquid on each side independently. Use a filled bucket on a side to fill that side; use an empty bucket on the same side to recover its contents. The two sides can contain different liquids. A full side rejects another filled bucket without placing liquid beside the wall.

Registered Forge liquids with a compatible bucket or fluid container are supported; gases are excluded. The visible liquid fills the free space around the configured wall geometry and uses the fluid's texture, colour and luminosity. Contents stay contained and do not spread into neighbouring blocks. Saving, copying and vehicle capture preserve both sides. Slabs and stairs use the same contained-fluid storage; older waterlogged saves migrate automatically.

Placing a supported block into liquid automatically captures it in the free space. At a wet/dry boundary only the face adjoining liquid fills; the opposite face stays dry. Opposing liquids can fill their respective sides independently. An isolated replaced fluid cell is retained when neither face adjoins another liquid. This includes source and flowing water/lava and registered Forge liquids, with the same behavior for doors, trapdoors and diagonal variants. Ordinary water and lava join the matching contained liquid without an internal border; an empty compartment or a different liquid retains its visible boundary.

Visible fluid follows continuous diagonal surfaces and reaches inset porthole/glass panes. Adjacent volumes of the same liquid omit their shared faces, including partial joins; different liquids retain their separating interface. The visible surface cache refreshes after shape or fluid changes, rather than rebuilding meshes during rotation. Entity collision remains based on the solid block's collision geometry.

### Doors and trapdoors

Programmable Door, Programmable Trapdoor and Programmable Diagonal Trapdoor use the same two-sided bucket storage. A door's upper and lower halves share the stored sides. Sides refer to the closed leaf, so opening or rotating the leaf does not exchange them.

Door fluid storage is per block: the lower block can be waterlogged while the upper block remains dry, or vice versa. Buckets fill or drain the clicked block and side. Placement in shallow water captures only the submerged block.

A dry vanilla sponge also clears stored water from supported Vandor blocks without breaking them or removing other liquids. It follows connected water, with the same seven-block reach and a maximum of 64 visited wet blocks per absorption; the sponge becomes wet after absorbing water.

Opening a leaf with water on either side fills its passage with water at that block height. This includes directly adjacent world water. Closing restores the original sides: a dry opposite side clears again, while water originally stored on both sides remains. This passage fill stays inside the opening and does not create surrounding source blocks or a second bucket of stored water. Empty buckets drain the actual stored side. A different liquid explicitly stored opposite the water is retained.

The diagonal trapdoor uses continuous sloped fluid faces rather than collision slices, keeping its water surface free of horizontal bands. Stored sides survive saves and vehicle capture; the temporary opening fill is derived from the current open state.
