# Vandor Labs 2.10.0

This development version adds material choices, directional texture joins and
smaller Canopy item models. Forge 1.12.2 remains the reference target; NeoForge
1.21.1 supports these changes through its converted building families and now
includes the supported standalone arm family.
User testing and final release validation remain pending.

## Module Rack and Autopilot

Module Racks are available on Forge 1.12.2 and NeoForge 1.21.1. Autopilot fits
inside them with an abstract cyan/amber route indicator and
per-bay route, activation and receiving controls. On Forge 1.12.2, existing Autopilot computers
migrate after chunk loading while preserving navigation, authorization and saved
data. Rack placement, extraction animation, housing and proportional Redstone
controls follow the standard dialogs. Native captured Racks retain every module
through mining and abrupt recovery; Vehicle navigation remains Forge-only.
Custom housing affects only the five exterior faces, leaving the front and bay interiors unchanged. Mounted module edits belong to the physical module immediately and survive extraction, relocation and reload independently of the rack housing/output settings.
See the [Module Rack guide](../module-rack.md).

## Materials and joined artwork

The new **Tiles** category contains thirteen finishes, including coloured tiles,
staggered tiles, limestone and terrazzo designs. **Doors / Utility** gains
Bathroom White, Modern Oak Windowed, Windowed Utility, Windowed White, Bathroom
and Stall.
Existing saved texture choices keep their identities.
The **Hull** category also includes the supplied **Port Hull** face. Fuel Port Dual and Fuel Port Round have been removed from the picker and artwork; their old saved slots use the standard fallback.

Filesystem Door artwork now mirrors correctly across double-door leaves,
matching the behavior of selected vanilla-block textures.

Material dialogs consistently call image mapping **Texture: Tile / Fit (Aspect)
/ Stretch**. Connected surfaces offer Vertical, Horizontal, North/South and East/West joining. Both neighbours must allow the shared axis: Vertical can join Vertical or On above and below, but cannot join a Horizontal-only neighbour.
Older saved Even/Odd choices now behave as All. Copifier batch changes refresh
previously joined surfaces immediately. See [texture layout and joining](../texture-joining.md)
for examples, axis meanings and supported families.

The Duplifier's **Respect block joins** option restricts Connected Matching
Blocks to the clicked joined artwork. Leave it off to retain the existing
matching-region behavior. Hover tooltips explain all three connected selection options.

## Native tables, controls and surfaces

**Programmable Chair, Luxury Seat, Military Seat and Pilot Seat** are available as native stationary furniture. They retain authored styles, heights, connected benches and configured items, with the same dedicated editor through ordinary Creative sneak-right-click and the Configurizer. Right-click to sit and sneak to dismount. Pilot Seat keeps saved dormant data and compatible Canopy backrest sharing; Vehicle/Taxi behavior and carried seats remain excluded. See [Native seating](../native-seating.md).

NeoForge 1.21.1 now includes the existing **Industrial Table** and **Programmable Pressure Plate**. Table modules connect only to matching neighbors and support ordinary or inverted placement; their authored end struts refresh after placement and mining without table entities or tickers.

Pressure Plates share Forge's Wood, Gold and Iron entity-count sensing and release cadence. Their dedicated General/Redstone dialog uses the normal material picker, reverse-value clicks and inline transmission controls. Physical analogue power stays independent of copied channel settings. Native Vehicle Mode and vehicle-carried sensing remain excluded. See [Industrial Tables](../industrial-tables.md) and [Pressure Plates](../programmable-pressure-plate.md).

Programmable Surfaces are available on NeoForge 1.21.1 with the same Active/Inactive picker, saved layouts and six-face construction as Forge 1.12.2. Joined drawings keep opaque surviving faces through mining and repair; settings changes refresh terrain meshes, including cropped and non-square artwork. Native Turntable and Copy Controllers remain separate conversions. See [Programmable Surfaces](../programmable-surfaces.md).

## Canopy items

Canopy inventory, held, displayed and dropped items use prepared item geometry
and smaller display transforms. World Canopy geometry remains detailed. Item
meshes retain the authored materials and visible shell outline while avoiding
the cost and oversized appearance of drawing a complete multiblock in hand.

## Arms and landing gear

Forge assemblies have a separate **Foot** direction option for Skis and Wheels,
including sideways mounts and Large Wheels. It changes the foot's facing without
changing the supporting blocks' ownership footprint.

Grid-aligned activated Rotating Arm sockets accept Plates, Skis and matching
Wheels, including complete Large Wheels. Connected native feet can join an
existing joined arm group. Ownership changes use the durable recovery journal;
fractional endpoints refuse placement without consuming the held item.

Rotating Arms offer **Hinge: Single / Double**. Double shares the angle between
the base and elbow while preserving the total foot or payload rotation. **Kink**
chooses **Inward / Outward** relative to **Fold**, keeping that relationship when Fold changes. Older saved middle-only
strokes finish with their original motion before adopting Double at native
zero-degree rest. Payload motion and collision use the same shared geometry.

Forge arms can also carry a complete formed Core construction, selected through
either its Core or a reserved part. Chips, settings and original capability data
stay with the complete machine. Joined Rotating Arms include the whole validated
Core footprint from their contact layer. Set the same Join, range, fold, payload
mode, hinge and kink on every supporting column; independent neighbouring arms
remain real obstacles. Configure or dismantle it at a supported native
endpoint; unsupported tilts stay captured. See the
[Core construction guide](../universal-constructors.md).

Taxi conversion keeps its inspected geometry separate from the geometry prepared with new Coupler ownership data. Valid Couplers can assemble, park and recapture; actual source edits during preparation still refuse without removing the craft.

Vehicles using two independent complete Ski supports can enter Taxi mode. A
single Ski remains insufficient, and other support families keep their existing
admission rules. See the [modular landing gear guide](../modular-landing-gear.md).

### Native Minecraft 1.21.1 arms

NeoForge now includes regular and Large Extenders, Rotating Arms, mounts, Plates,
Skis and Wheels as ten ordinary creative/search items. Six-face placement, joined
platforms, Foot direction, Single / Double hinges, relative Inward / Outward Kink and the dedicated
Configurizer dialog use
the Forge 1.12.2 construction rules. Duplifier copying and Undo transfer settings only.
Native/captured payload handoffs, active-foot attachment and captured-block mining
use durable recovery, and platforms carry native players through motion.

Horizontal Extenders also support **Push blocks** for ordinary solid terrain,
including joined platforms. Pushed terrain stays in place when the original
platform retracts. Tracks now join regular and Large arm groups with Compact, Armoured and Bogie styles. Small, Medium and Large stationary side wheels mount on supported slab sides; see [native Tracks and side wheels](../native-tracks-and-wheels.md). Vehicle Mode remains unavailable on this target. Unsupported vanilla endpoint orientations stay captured and selectable
until they reach a representable endpoint. See the
[native arm construction guide](../native-joined-arm-platforms.md).

## Resource Ports

Forge 1.12.2 and NeoForge 1.21.1 add **Power Port**, **Fluid Port** and **Matter Port** for transferring RF,
fluids and items. Configure all six faces, an optional shared output limit, input
pulling and Redstone conditions in their dedicated dialog. The **Housing** tab
selects artwork for disabled faces; port faces retain their resource artwork.
The output-limit label identifies a blank field as unlimited. Sharpened artwork
and red/yellow/blue inventory accents distinguish the three resource types. Stored resources remain
in one configured item when mined; Duplifier copying transfers settings only.
See [Resource Ports](../resource-ports.md) for recipes, connections and buffer limits.

NeoForge 1.21.1 includes the Battery Block and Hexagonal Fuel Tank with the same capacities, shapes and complete joined sections. Their dedicated dialogs show live contents and reuse the casing picker. Mining and placement retain each cell's stored resources, including when a tank section is split. See [matter and storage](../matter-and-storage.md) and the [joined tank construction guide](../multiblocks/hexagonal-tank/README.md).

## Stationary Glass and Portholes

On Forge 1.12.2, eligible stationary Glass panes use ordinary translucent chunk
geometry. Other stationary panes, partially transparent emissive frames and
Porthole geometry reuse retained render groups, with separate ownership for
frames and panes. Joined holes remain holes, and each surface retains its
artwork, lighting and opacity. Moving, animated and fluid cases retain their
appropriate fallbacks. See [stationary Glass and Porthole rendering](../stationary-glass-rendering.md)
for the supported paths and diagnostic command.

## Coupler ownership recovery

Forge Couplers retain their Amber and Cyan face artwork through Arm motion and
native endpoint handoffs. Old payload mappings remain readable and upgrade when
ownership transfers. Slab Couplers keep a horizontal housing; unsupported tilted
endpoints remain captured until a representable pose.

On Forge 1.12.2, Couplers can travel on moving Arm payloads. Linked Arms preserve every head contact while passive peers retain their own control settings. Rigid connections retain the full head orientation; articulated connections permit their configured motion. Arm, payload and vehicle animation share one coherent frame during lag, reversal and reload.

Release preserves both sections and cargo. Return general body poses and connected Arms to their assembly reference before separation when requested, open the other head independently if needed, and drive the sections apart. Nearby Arms use actual vehicle surfaces for pushes instead of the empty space inside an enclosing hull. See the [Coupler guide](../couplers.md).

Native arm-endpoint attachment and release retain their original physical before
images while renewing the receiving vehicle association. Interrupted transfers
recover through their durable journals, including copied capability data.

Failed structural updates restore the original cargo, seats and vehicle position.
If the original source cannot be reinstalled, the vehicle remains inert and keeps
that complete snapshot for recovery.

## Module Rack

The new **Module Rack** has four horizontal slots for **Blank Modules**. Click a specific slot to insert a module with a sliding animation, or use an empty hand to retrieve it with an outward slide. Each bay has a lower amber square when empty and an upper cyan square when occupied.

Racks mount on all six faces, including floors and ceilings. They reuse the Light and Port placement rule, including shallow floor placement facing the player. Their controls and module travel rotate with the front.

Its darker graphite housing follows the compact equipment design: a one-pixel rim, full-depth right wall around a recessed inner panel, and paired vertical module handles. The right panel contains slot controls without an Access Card or Chip Mount.

Seated modules and the rack share Minecraft's ordinary chunk mesh. Only active insertion and extraction animations need a temporary renderer; idle racks have neither ticking tiles nor permanent tile-entity rendering.

See [Module Rack usage and crafting](../module-rack.md).
