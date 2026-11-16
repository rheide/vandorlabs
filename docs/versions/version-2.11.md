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
Matter and RF, supplied through a connected power port or a Battery Module.
The original construction frame is unchanged. A new Production dialog shows
rotating, textured previews of the finished structures; the Redstone tab
controls physical and channel activation while retaining paused work.

Watch each Core grow in the chamber beneath four scanning beams, then collect
it in the world or from the output slot. See the [constructor guide](../universal-constructors.md)
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
