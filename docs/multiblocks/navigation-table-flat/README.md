# Flat Navigation Table

These are placeable modules, so they use **no Core block and no Matter frame**. The example needs **six Flat Navigation Tables** and optionally one Data Chip.

## 1. Place the first module

Place a navigation module facing the operating side. Enable Security in its dialog to show its chip holder.

![Flat Navigation Table: place the first module](step-1.png)

## 2. Extend the front row

Place matching modules side by side, facing the same way. Complete a three-module front row.

![Flat Navigation Table: extend the front row](step-2.png)

## 3. Complete the layout

Add a second complete row behind the first, facing the opposite direction, to make a 3 × 2 rectangle. Keep the buttons around the outside; the inner edges merge into one screen.

![Flat Navigation Table: complete the layout](step-3.png)

## 4. Use the shared chip mount

Use the Configurizer or sneak-right-click to choose screen artwork, enable Security, and set the accepted company. Then insert a Data Chip into the one shared exterior holder. This example enables Security without Redstone, so the selected image appears while its matching chip is inserted.

![Flat Navigation Table: use the shared chip mount](step-4.png)

## Joining and editing

All members must have the same type and height. Flat tables accept mixed facings and require a filled rectangle; holes and L shapes remain unjoined. Angled consoles require one row with a common facing. Assemblies are limited to 256 loaded modules. A group touching an unloaded boundary waits for complete discovery rather than inventing another chip owner.

With Security enabled, there is exactly one native-size Data Chip Mount on a valid assembly, just below the top of an exterior front panel. Disabling Security hides the mount and safely retains any stored chip until it is re-enabled. Uniform rows choose the front-center module; even widths choose the smaller local-X cell. Mixed facings choose a deterministic exterior-facing module. If resizing moves the holder, its chip and configuration move with it. Remove chips before merging occupied tables: only one retained chip can occupy the shared holder, and extras are dropped once. Chips awaiting return keep their original return owner.

Choose artwork from the Programmable Block materials and Programmable Screen designs, including static or animated playback. The default is black. These displays only consume redstone: no module emits physical or channel power. Redstone and Security are independent gates; enabling both requires power and a matching inserted chip. See [Navigation display settings](../../navigation-displays.md) for the full behavior table.

[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)
