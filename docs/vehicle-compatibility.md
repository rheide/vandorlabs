# Vehicle block compatibility

Vehicle inspection checks rotation before it allows assembly. An unsupported block leaves the craft and its tile data in the world, and the error identifies its registry name. A mod is never approved wholesale: different blocks in the same mod can have different requirements.

The snapshot retains complete block properties and persistent tile NBT, including inventories, energy, owner information and saved configuration. Foreign machines do not run while carried. Restoring their saved data does not imply that a machine, cable, pipe or external network can function in a vehicle.

| Blocks | Vehicle eligibility |
| --- | --- |
| Vanilla blocks and Vandor Labs components | Supported by explicit state and tile adapters. Incomplete multiblocks and moving assemblies still prevent conversion. |
| Immersive Engineering ladders and LV/MV/HV capacitors | Supported. Facing and world-side capacitor configuration rotate; energy and other saved data are retained. Other IE machines and multiblocks require separate adapters. |
| Mekanism energy cubes | Supported. Facing rotates; side configuration is relative to facing, and energy/security/inventory data remain in the saved NBT. Other Mekanism machines, transmitters and generators require separate adapters. |
| Malisis Doors standard DoorTileEntity and TrapDoorTileEntity families | Supported, including replacements of vanilla registry entries. Other Malisis tile types require separate adapters. |
| Ordinary tile-free building cubes, slabs and directional building blocks | Supported when their persistent properties use the recognized building-material fields and their rotation operation passes the orientation and four-turn checks. This can admit materials from Chisel, Biomes O' Plenty, Ceramics, Thermal Foundation, and other building-content mods. Eligibility depends on the particular block. |
| Unknown tile entities, network endpoints, multiblock machines and private orientation formats | Refused before assembly. Keeping their NBT bytes alone cannot guarantee correct relocation or rotation. |
| Unregistered or missing blocks, liquids, bedrock, unbreakable blocks, missing component data | Refused. |

The installed mod collections include Applied Energistics 2, Advanced Rocketry/LibVulpes, Galacticraft, OpenComputers, Thermal Expansion/Dynamics, Railcraft, RFTools/XNet, NuclearCraft, Extreme Reactors, Thaumcraft, Astral Sorcery, Draconic Evolution, Decocraft, BiblioCraft, furniture mods, and several vehicle-content packs. Their private tile types do not receive a blanket adapter. Their ordinary building blocks can pass the tile-free checks above. Entity vehicles supplied by other mods are not blocks and are not captured by this system.

With the checked optional mods installed, Mekanism's replacement for `minecraft:portal` and Malisis Doors' six vanilla fence-gate replacements are refused. These use third-party runtime classes despite their vanilla registry names. Standard Malisis door and trapdoor adapters do not cover fence gates.

The dependency-free checks use the actual registered block and tile classes. In particular, an unknown block's default no-op rotation is insufficient for a directional property. Horizontal facing and axis values must rotate correctly; a complete four-turn cycle must restore the state. Fixed non-cube decorations without an orientation representation are refused. The installed Mekanism energy-cube implementation interprets its side configuration using its facing; Immersive Engineering capacitors store explicit world-side keys. These formats require different adapters.

## Vehicle Controller and pilot seats

Place one Vehicle Controller in the connected craft and at least one complete Pilot Seat. Right-click the controller, use the Configurizer, or sneak-right-click to inspect the craft and assemble it. The captured controller provides parking controls. New assemblies require the controller; older captured craft can still be parked so a controller can be installed.

Pilot Seat dialogs contain seat height and vehicle-mode settings and apply changes automatically. They do not assemble or park the craft. Multiple Pilot Seats are supported: right-click an empty seat to board it. The first seated pilot controls movement until they dismount, then control passes to the next seated pilot. Each occupant keeps their own seat position and height. Parking transfers seated occupants to their corresponding restored seats.

Autopilot uses a cyan navigation texture, Waypoint an amber route marker, and Vehicle Controller a green chassis symbol.

## Limits and wheels

`vandorlabs-blocks.cfg` has a `vehicles.maxBlocks` setting, default 4096 and range 1â€“32768. It limits new assemblies. Lowering it does not prevent existing vehicles from being restored. The 64-block limit on each axis, 4 MiB snapshot limit and collision-complexity limit still apply.

Small and Medium Wheels occupy one block. Large Wheels reserve a 2Ã—2 vertical footprint. Place a wheel on a vertical side of a slab; the wheel faces outward from that side. Large wheels need free space above and to the right of the placement point when viewed from outside. Wheels count as landing supports, including mixed wheel/landing-gear craft when their actual bottom surfaces have the same height. At least three independent supports are required. Breaking any large-wheel part removes the whole wheel. In Taxi mode, wheels and landing-gear tyres roll with actual travel, reverse with the craft and swivel with steering. Stopping holds their rotation; Hover centers steering and stops rolling.

Top slabs have an empty lower half. Vehicle discovery does not connect a top slab to a floor beneath that empty space. Slabs that physically touch another block still connect normally.

## Copying and carried ramps

Ramp controllers and generated ramp cells save a journal origin. When a clipboard tool changes only the tile's `x`, `y`, `z`, loading translates the private source, controller, source-tile and occupied-cell journals by the same displacement. Internal vehicle translation and rotation update this origin too. This repairs new copies without altering the original ramp. Existing malformed copies made before the journal origin was saved may still require replacing or reconfiguring the copied controller; guessing its original source positions could damage unrelated blocks.

Landing gear performs one deferred footprint check after loading. It recreates missing invisible reserved cells only in air and leaves occupied cells intact. Copy the entire assembly where possible, especially rotated large gear and its covers.

Programmable Ramp channels can switch ordinary ramps between their configured endpoints while carried, including capturing an initially retracted platform. The adapter uses the existing platform discovery, material data and tread geometry. It builds a replacement snapshot and checks terrain, occupants, bounds and serialization before applying it. Movement is an immediate endpoint change while carried. If the new geometry is obstructed, the previous snapshot remains intact. Escalator belts remain frozen in vehicle mode.
