# Maxwell Drive

Build a **3 wide × 4 deep × 3 high** assembly with **25 Programmable Matter Blocks** and **one Maxwell Drive Core**. Core blocks are available in Creative, following the existing Universal Constructor system.

## 1. Build the base

Lay out a solid 3 × 4 bottom layer. Put the matching Core in the front-left corner, facing you, and fill every other cell with Programmable Matter Blocks.

![Maxwell Drive: build the base](step-1.png)

## 2. Raise the corner pillars

At each of the four corners, build up to a total height of 3 blocks, including the bottom layer. Leave the interior empty.

![Maxwell Drive: raise the corner pillars](step-2.png)

## 3. Connect the top rim

Connect the four pillar tops with a rectangular rim of Programmable Matter Blocks. Keep the middle of the roof open; do not fill the interior.

![Maxwell Drive: connect the top rim](step-3.png)

## 4. Assemble

Right-click the Core. The frame becomes the complete model, and its occupied cells are reserved. If formation fails, use the reported cell to check the frame and its empty interior.

![Maxwell Drive: assemble](step-4.png)

## 5. Insert a Data Chip

The native Data Chip Mount is always visible on this model. Right-click it with any Data Chip to insert one physical item; empty-hand right-click removes it. The chip stays inserted and does not gate activation. Redstone settings alone control optional power activation. The machine never emits a redstone signal.

![Maxwell Drive: insert a data chip](step-5.png)

## Operation and activation

The active exhaust is yellow/white, with the native casing and nozzle rings retained.

Open the **Engine** tab with the Configurizer. **Redstone: Disabled** keeps the drive powered at level 15. With **Redstone: Enabled**, the strongest physical input anywhere on the assembly or configured input channel supplies a level from 0 to 15. **Activate on: ON** uses that level; **Activate on: OFF** uses 15 minus that level. A resulting level of zero switches the drive off. Input channel 0 means no channel.

**Brightness: On/Off** gives active effects full brightness; **Brightness: Signal** scales their brightness by the resulting level divided by 15. **Particles** selects Off, Light, Medium or Heavy. **Particles at level** sets a 0–15 threshold: in Signal mode, exhaust starts at or above this level, while the drive is active. On/Off brightness ignores the threshold. Particle intensity is selected separately from brightness. Defaults are Redstone Disabled, Activate on ON, On/Off brightness, Light particles and threshold 8. Right-click an option to select its previous value.

These effects work on placed machinery and aboard vehicles, following the drive’s facing and the vehicle’s pose. The drive supplies **no vehicle thrust** and does not qualify a craft for Taxi or Hover. Data Chips are optional retained data, accept every company and never authorize or gate operation; there are no integrated Security settings.

Select screen and keyboard artwork independently in their surface tabs. Model artwork preserves the supplied design.

## Facing and removal

The same frame works along either horizontal axis and with the Core in any bottom corner. The placed facing is preferred when it fits. Sneak-right-click the Core to dismantle; on a machine with a holder, aim away from the holder to dismantle rather than configure it. Mining a reserved part dismantles the frame and removes that cell. Breaking the Core restores the remaining Matter Blocks.

A retained chip stays in the Core during manual dismantling and works again after reassembly. Breaking the Core releases it once. Remove chips before rebuilding a machine when you want to keep them in your inventory.

Use the Configurizer to select artwork independently for the fitted screen/half-screen and keyboard surfaces. Model artwork preserves the original design; Black and the standard material/screen/panel catalog are also available. See [Machine display settings](../../machine-displays.md) for redstone activation and optional retained Data Chips.

These models do not yet perform crafting, propulsion, navigation calculations or dimension transfer. Their status appearances are visual states.

[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)
