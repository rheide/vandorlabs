# Vandor Labs 2.11.0

Version 2.11 is in development, adding rechargeable Battery Modules and powered
Core production in the Synthesis Tower. Minecraft 1.21.1 now includes the three
builder multiblocks; other Core multiblocks remain outside this update.

## Battery Modules

[Battery Modules](../module-rack.md#battery-module) hold **10,000 RF**, one tenth
of a Battery Block. Craft one from four Programmable Matter Ingots, four Gold
Ingots and one Redstone Dust. Mounted modules show a red lightning mark and
four green charge segments; removing a module retains its stored energy.
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
with a small inner cube, eight diagonal struts, outer edges and thicker corner
nodes. The three enabled native builder Cores use the same geometry. Real gaps
remain visible in placed blocks, fabrication, padded inventory icons and
Dynmap; Core frame requirements and identities are preserved.

With JEI installed, **Matter Infuser** shows iron conversion and Battery Module
charging, and **Synthesis Tower** shows its three Core outputs with the energy
and processing requirements. Infuser input/catalyst transfer is supported;
choose tower designs in Production before supplying Matter.
