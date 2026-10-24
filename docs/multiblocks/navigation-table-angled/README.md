# Angled Navigation Console

These are placeable modules, so they use **no Core block and no Matter frame**. The example needs **three Angled Navigation Consoles** and optionally one Data Chip.

## 1. Place the first module

Place a navigation module facing the operating side. A standalone module has one chip holder.

![Angled Navigation Console: place the first module](step-1.png)

## 2. Extend the front row

Place matching modules side by side, facing the same way. The screenshot shows two modules joined into one sloped screen.

![Angled Navigation Console: extend the front row](step-2.png)

## 3. Complete the layout

Add the third matching module to extend the sloped display. Angled consoles join sideways only, not front to back.

![Angled Navigation Console: complete the layout](step-3.png)

## 4. Use the shared chip mount

Insert a Data Chip into the one front-center holder. Use the Configurizer or sneak-right-click for the standard company filter, channels and insertion/return behavior.

![Angled Navigation Console: use the shared chip mount](step-4.png)

## Joining and editing

All members must have the same type, facing and height. Flat tables require a filled rectangle; holes and L shapes remain unjoined. Angled consoles require one row. Assemblies are limited to 256 loaded modules. A group touching an unloaded boundary waits for complete discovery rather than inventing another chip owner.

There is exactly one holder on a valid assembly, at the center of its front row. Even widths choose the smaller local-X cell. If resizing moves the holder, its chip and configuration move with it. Remove chips before merging occupied tables: only one retained chip can occupy the shared holder, and extras are dropped once. Chips awaiting return keep their original return owner.

Screens remain blank for future navigation gameplay. Ordinary redstone output comes from the holder module; configured virtual channels use the existing dimension-wide channel bank.

[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)
