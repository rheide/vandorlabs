# Vandor Labs 2.11.0

Version 2.11 is in development, adding rechargeable Battery Modules and powered
Core production in the Synthesis Tower. Minecraft 1.21.1 builder conversion is
also in progress; other Core multiblocks remain outside this update.

## Battery Modules

[Battery Modules](../module-rack.md#battery-module) hold **10,000 RF**, one tenth
of a Battery Block. Craft one from four Programmable Matter Ingots, four Gold
Ingots and one Redstone Dust. Mounted modules show a red lightning mark and
four green charge segments; removing a module retains its stored energy.
The item also exposes its charge to compatible RF integrations.

On Forge 1.12.2, charge a module in the Matter Infuser using sunlight and
redstone catalyst. It receives 50 RF per working tick and moves to the output
slot when full. NeoForge currently has no Matter Infuser adapter.

## Powered Synthesis Tower

Forge 1.12.2 Synthesis Towers produce three constructor Cores from Programmable
Matter and RF, supplied through a connected power port or a Battery Module.
The original construction frame is unchanged. A new Production dialog shows
rotating, textured previews of the finished structures; the Redstone tab
controls physical and channel activation while retaining paused work.

Watch each Core grow in the chamber beneath four scanning beams, then collect
it in the world or from the output slot. See the [constructor guide](../universal-constructors.md)
for power costs, ports and operation.
