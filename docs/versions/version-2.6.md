# Vandor Labs 2.6

## Cards and journals

The [Card / Chip Programmer](../card-chip-programmer.md) keeps three physical items inserted: an optional company master, an optional source, and a destination. Use a company Access Card or Master Data Chip to brand a blank destination. A source and destination must be the same kind of item.

Data Chips support Master and Journal types. Journals have a short title and eight editable pages. Open the Journal page to read or edit text; use Inventory to insert and remove items. A journal in the destination slot can be edited without making another copy. Closing the dialog keeps the items in their sockets and retains the draft.

## Arms, tracks and couplers

[Extender, Rotating and Fixed Arms](../modular-landing-gear.md) can form mechanisms without a landing foot or hull above them. Place arms on floors, ceilings or walls to choose their motion axis. A generic mechanism's Off state is its construction pose; configure its active extension or rotation on the dialog's second page. The payload can stay level or rotate with the arm.

Ordinary attachments become placed blocks again at exact whole-block translation endpoints. Fractional translations and rotations retain their block data in a moving snapshot. Captured machinery pauses while held, as it does in vehicle snapshots. Discovery and motion remain bounded and require loaded cells and permission to move the attachment.

Compact, Armoured and Bogie Tracks use their supplied connected tread models. Mount them under an arm or beside a slab, then select their style with the Configurizer. Tracks count as vehicle boundaries. Nested landing groups can include connecting blocks and lower landing assemblies; inspection uses the outer supports and excludes the ground beyond them.

[Square Couplers](../couplers.md) control discovery across an adjacent pair. Both halves must be locked to include the section beyond the pair. Each half supports input channels and inverted activation. Switching a carried coupler changes its state; it does not split or merge vehicles already assembled.

## Core assemblies

Five new assemblies use the existing Core and Programmable Matter workflow:

- [Pinnacle Oculus Drive](../multiblocks/oculus-drive/README.md), with split shutters and an energized field.
- [Survey Dish](../multiblocks/survey-dish/README.md), with an animated dish.
- [Sentinel Array](../multiblocks/sentinel-array/README.md).
- [Standalone Dish](../multiblocks/standalone-dish/README.md).
- [Standalone Antenna](../multiblocks/standalone-antenna/README.md).

Each assembly provides configurable input channels, chip security and a native retained Data Chip holder. These models do not perform radar detection or dimensional travel.

Longburn Drive is now **Maxwell Drive**. Pinnacle Meridian Drive is now **Seyr Meridian Drive**, with one Seyr nameplate. Saved registry IDs remain unchanged.

## Materials and vehicle compatibility

New programmable screens, inputs and consoles default to Door Interior (Dark). Existing saved finishes remain selected. The twelve ceiling textures now use their authored 64×64 artwork.

Strong programmable glass is opaque, including Clear and replacement materials. Its renderer writes depth in the opaque pass. Programmable Block texture mapping preserves the active vertex record format used by OptiFine.

Stationary vehicle controls now say **Park Mode**. Cockpit clipboard copies use relative reservation links and remain independent of their originals. Downward propulsion blocks terminate discovery alongside landing supports.

Ramp Controllers accept all steel and aluminum scaffolding patterns from Immersive Engineering. Additional explicit vehicle adapters cover inert IE structures and Mekanism storage and glow panels; see [the supported block list](../vehicle-compatibility.md) for their limits.

[All version guides](README.md) · [Changelog](../../CHANGELOG.md)
