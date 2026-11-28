# Mod compatibility

Vandor Labs works alongside these mods on Forge 1.12.2 unless a row says otherwise.
None of them is required. Each entry is the general picture; the linked pages give the
exact blocks and rules.

**Columns**

- **Carried** – blocks that ride along when a craft enters Taxi, Hover or Flight, or when an
  Arm moves them, keeping their contents and settings.
- **Transfer** – RF, fluid or item networks that keep working on a moving craft or through a
  settled Arm endpoint.
- **Runs carried** – machines that keep working while carried (otherwise they are carried
  idle and resume once parked).
- **Copy** – Copy Controller, WorldEdit and Better Builder's Wands handling.

## Content mods

| Mod | Carried | Transfer | Runs carried | Copy | Notes |
| --- | --- | --- | --- | --- | --- |
| Thermal Dynamics | Fluiducts, Fluxducts and item ducts, with Servos, Retrievers, Filters and Covers | RF and fluids between carried tanks, batteries, Maxwell Drive inlets and observers; through Arm endpoints | Ducts move RF and fluids on the craft | Fluiducts and Fluxducts with their attachments; Survival copies need the duct and attachment items | Item ducts with items in transit and long-range crossovers must be emptied or disconnected first |
| Thermal Expansion | Standalone machines and dynamos, Energy Cells, Fluid Tanks and Caches | Cells and tanks as sources and sinks | No | – | Facing and side configuration rotate with the craft. [Moving optional machines](moving-optional-machines.md) |
| Thermal Foundation | Glass, ores, rockwool and storage blocks | – | – | – | Refined Fuel is accepted by Maxwell Drives |
| Mekanism | Universal Cables, Mechanical Pipes, Pressurized Tubes and transporters; standalone processing machines, factories and chemical machines; Fluid Tanks, Energy Cubes, Dynamic Tanks | RF and fluids through cables and pipes; item delivery through Arm endpoints | Cables and pipes move RF and fluids | Fluid Tanks and Energy Cubes, respecting Mekanism security | Transporters must finish delivering items before moving. [Moving optional machines](moving-optional-machines.md) |
| Mekanism Generators | Standalone Reactor Glass | – | No | – | Glass that is part of a formed reactor stays in place |
| Galacticraft Core and Planets | Building blocks, Aluminum Wire, Fluid Pipes, Energy Storage Modules and Fluid Tanks | RF and fluids through wires and pipes | Wires and pipes move RF and fluids | – | – |
| RFTools | Power Cells, generators, crafters, logic blocks, screens, wireless redstone, storage terminals and most other self-contained blocks | Power Cells feed carried observers and machines | Power Cells supply RF | – | A moved Power Cell leaves its network and rejoins where it is placed. Teleporters, monitors and other blocks bound to fixed coordinates are refused with the reason. NeoForge: unlinked Dimensional Cells on native vehicles |
| Immersive Engineering | Toolboxes, wooden posts and watermills with their native links | – | No | – | Wire networks are not carried |
| MrCrayfish Furniture | Furniture blocks with their inventories | – | No | Furniture pairs are kept together | – |
| MalisisDoors | Doors (both halves) | – | – | – | – |
| Industrial Renewal | – | – | – | – | Catwalks treat Programmable Doors as doorways. [Appearance compatibility](appearance-compatibility.md#industrial-renewal-catwalks) |

Linked structures, shared remote inventories and partial multiblocks are refused rather than
split. [Resource ports](resource-ports.md) describes routing on moving craft.

## Tools, maps and rendering

| Mod | Integration |
| --- | --- |
| WorldEdit 6.1.10 | Rotating a selection rotates Vandor doors, propulsion blocks and other oriented blocks. [World-copy compatibility](copy-compatibility.md) |
| Better Builder's Wands | Wand placement copies the clicked Vandor block's exact settings, including both cells of doors and chairs. [World-copy compatibility](copy-compatibility.md) |
| JEI | Recipe categories for the Matter Infuser, Synthesis Tower and builder machines; hidden blocks stay out of the browser |
| Waila / Hwyla / Wawla | A `Texture:` line names the selected finish; doors report their movement. [Appearance compatibility](appearance-compatibility.md#looking-at-tooltips) |
| JourneyMap | Cruise coordinates and waypoints while flying high; waypoint beacons and labels follow the cockpit camera; teleporting releases the rider |
| Dynmap | Vandor blocks render on the web map with their selected artwork |
| OptiFine HD U G5, with or without shader packs | Supported and tested with Complementary Unbound 5.6.1. [Shader materials](shader-materials.md) |

## Not supported

- Textures from other mods cannot be selected for Programmable Blocks, Doors or other
  programmable surfaces; use the built-in catalog or [filesystem textures](filesystem-textures.md).
- The Synthesis Tower's Digitize mode scans vanilla and Vandor blocks only.

<!-- Keep this page current: update it in the same change as any optional-mod adapter, copy, transfer or rendering integration (see agent-notes/feature-regressions.md, Optional mod adapters). -->
