# Vandor Labs 2.11.0

Version 2.11 is in development, adding rechargeable Battery Modules and powered
Core production in the Synthesis Tower. Minecraft 1.21.1 now includes the three
builder multiblocks; other Core multiblocks remain outside this update.

## Battery Modules

[Battery Modules](../module-rack.md#battery-module) hold **10,000 RF**, one tenth
of a Battery Block. Craft one from four Programmable Matter Ingots, four Gold
Ingots and one Redstone Dust. Mounted modules reuse the Power Port red on/off bar between their handles.
Lit pixel columns show charge; removing a module retains its stored energy.
Creative offers both an empty and a full module.
The item also exposes its charge to compatible RF integrations.

On Forge 1.12.2 and NeoForge 1.21.1, charge a module in the Matter Infuser using sunlight and
redstone catalyst. It receives 50 RF per working tick and moves to the output
slot when full. The native Infuser also retains sunlight-driven iron-to-Matter
production, redstone catalyst and hopper access.

## Powered Synthesis Tower

Forge 1.12.2 and NeoForge 1.21.1 Synthesis Towers produce three constructor Cores from Programmable
Matter and RF, supplied through a connected power port or up to four Battery
Modules. The flush four-bay rack holds **40,000 RF**; each Core uses **8,000 RF**
at **20 RF per tick**, taking at least **twenty seconds**.
The original construction frame is unchanged. A new Production dialog shows
rotating, textured previews of the finished structures; the Redstone tab
controls physical and channel activation while retaining paused work.

Watch each Core grow in the chamber beneath four scanning beams while RF is
being spent. Pausing retains the partial Core and stops the beams. Collect it
in the world, from the output slot, or automatically into a chest beside an
item-output face. The front has a smaller square screen and projecting keyboard
on the left, a chip mount and flush rack on the right, and neutral steel casing. See the [constructor guide](../universal-constructors.md)
for power costs, ports and operation.

## Catalog Chips and additional Forge production

The new Journal, Vehicle, Navigation and Catalog Chip artwork identifies chip
purpose. Programming a journal or vehicle changes its chip artwork; company
variants remain available. Navigation Chip (blank) has no assigned function.
[Catalog Chip (Master)](../data-chips.md) stores explicit blueprint lists
for each builder, and Forge Journal-capable displays can browse those models.

On Forge 1.12.2, the Master Catalog extends the Tower with the additional Core
models, Vehicle Controller and Ramp Controller. The Assembly Press produces
Data Chips and Access Cards from Matter Ingots, with Battery Modules and
Autopilot Modules supplied by the Master Catalog. The Enclosed Gantry produces
arms, landing gear and couplers from Matter Blocks. Both reuse the Tower’s
inventory, RF, Redstone, rack and output behavior, with updated casing and
lower workstation mounts. Their new production is not ported to 1.21.1.

## Builders on Minecraft 1.21.1

Synthesis Tower, Assembly Press and Enclosed Gantry now have native Core items
and their complete structures. Formation and teardown wait for durable
ownership preparation; mining and interrupted restarts retain one owner for
frames, inputs, outputs, batteries and chips. The Synthesis Tower includes the
same RF production and Redstone controls as Forge. Assembly Press and Enclosed
Gantry retain their current structure-only behavior.

See the [native builder guide](../builders-1.21.md) for construction, operation
and safe teardown. Other Core multiblocks and
arm/copy integration for these assemblies remain deferred. The current native
validation is focused development-runtime/software-rendering coverage, not a
full release gate or hardware shader acceptance.

## Open Core models and JEI

All thirteen Forge Core block types now use colored open tesseract scaffolds,
on a 32×32 pixel grid, with a legacy-textured inner cube, four stepped
diagonal supports, recessed face crosses and detailed solid corner cubes. The larger inner cube retains the original Core artwork, while each outer frame follows its Core’s primary color. The three enabled native builder Cores use the same geometry. Real gaps
remain visible in placed blocks, fabrication, padded inventory icons and
Dynmap; Core frame requirements and identities are preserved.

With JEI installed, **Matter Infuser** shows iron conversion and Battery Module
charging, and **Synthesis Tower** shows its three Core outputs with the energy
and processing requirements. Infuser input/catalyst transfer is supported;
choose tower designs in Production before supplying Matter.

## Native Ramp Controllers

Ramp Controller and Ramp Controller Slab now provide the seven Source modes on
Minecraft 1.21.1, including escalators, six scan faces and horizontal top/bottom
slab housings. Ordinary right-click and Configurizer use the same dedicated
editor, with shared half-block offsets, option order, receiving channels and
reverse cycling.

Escalators retain their tread phase when stopped and use nearby-player
sleep/wake hysteresis. Durable preparation and return keep the original
platform and supported actors owned through pending disk work and restarts.
See the [Ramp guide](../landing-ramps.md) and the
[native recovery boundaries](../native-ramp-recovery.md) for supported materials,
actors and saved-state behavior.

## Source-authoritative dialogs

Forge 1.12.2 remains the appearance and interaction reference. Native editors
reuse its family headers, logos, notebook tabs and ordinary button artwork,
including hover colors and cropped button edges. The Light retains its authored
brightness track. Infuser, Synthesis Tower, Ramp, Port and Surface now share
portable layout definitions between both versions. Other editors reuse their
existing shared controls and the common skin; their remaining local geometry is
recorded in the [dialog parity inventory](../development/native-dialog-parity.md).

## Fabrication refinements

Forge Press and Gantry jobs now take ten seconds at 40 RF/tick, retaining
8,000 RF total. The Press builds flat items with green corner lasers; the
Gantry moves its smaller head and rail over a blue fan beam. Both chambers
allow completed-item pickup. The Gantry now needs a four-high frame with
34 Matter Blocks and one Core; the former three-high frame is not supported.
Native includes the revised structures; its production scope remains unchanged.
Vehicle Chip previews use adaptive sub-block detail with explicit work and
voxel budgets rather than one fixed thumbnail resolution.
