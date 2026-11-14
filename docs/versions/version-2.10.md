# Vandor Labs 2.10.0

This development version adds material choices, directional texture joins and
smaller Canopy item models. Forge 1.12.2 remains the reference target; NeoForge
1.21.1 supports these changes through its converted building families and now
includes the supported standalone arm family.
User testing and final release validation remain pending.

## Materials and joined artwork

The new **Floor** category contains thirteen finishes, including coloured tiles,
staggered tiles, limestone and terrazzo designs. **Doors / Utility** gains
Bathroom White, Modern Oak Windowed, Windowed Utility and Windowed White.
Existing saved texture choices keep their identities.
The **Hull** category also includes the supplied **Port Hull** face.

Filesystem Door artwork now mirrors correctly across double-door leaves,
matching the behavior of selected vanilla-block textures.

Material dialogs consistently call image mapping **Texture: Tile / Fit (Aspect)
/ Stretch**. Connected surfaces offer directional joining and an All/Even/Odd
filter for the neighbouring coordinate. The neighbour's own filter does not
override that choice. See [texture layout and joining](../texture-joining.md)
for examples, axis meanings and supported families.

The Duplifier's **Respect block joins** option restricts Connected Matching
Blocks to the clicked joined artwork. Leave it off to retain the existing
matching-region behavior.

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

Rotating Arms offer **Hinge: Base / Middle**. Middle rotates the lower linkage at
the visible elbow; Base retains the existing motion. Saved assemblies default to
Base. Payload motion and collision follow the selected hinge.

Vehicles using two independent complete Ski supports can enter Taxi mode. A
single Ski remains insufficient, and other support families keep their existing
admission rules. See the [modular landing gear guide](../modular-landing-gear.md).

### Native Minecraft 1.21.1 arms

NeoForge now includes regular and Large Extenders, Rotating Arms, mounts, Plates,
Skis and Wheels as ten ordinary creative/search items. Six-face placement, joined
platforms, Foot direction, Middle hinge and the dedicated Configurizer dialog use
the Forge 1.12.2 construction rules. Duplifier copying and Undo transfer settings only.
Native/captured payload handoffs, active-foot attachment and captured-block mining
use durable recovery, and platforms carry native players through motion.

Tracks, horizontal **Push blocks** and Vehicle Mode remain unavailable on this
target. Unsupported vanilla endpoint orientations stay captured and selectable
until they reach a representable endpoint. See the
[native arm construction guide](../native-joined-arm-platforms.md).

## Resource Ports

Forge 1.12.2 and NeoForge 1.21.1 add **Power Port**, **Fluid Port** and **Matter Port** for transferring RF,
fluids and items. Configure all six faces, an optional shared output limit, input
pulling and Redstone conditions in their dedicated dialog. Stored resources remain
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

On Forge 1.12.2, Couplers can travel on moving Arm payloads. Linked Arms preserve every head contact while passive peers retain their own control settings. Rigid connections retain the full head orientation; articulated connections permit their configured motion. Arm, payload and vehicle animation share one coherent frame during lag, reversal and reload.

Release preserves both sections and cargo. Return general body poses and connected Arms to their assembly reference before separation when requested, open the other head independently if needed, and drive the sections apart. Nearby Arms use actual vehicle surfaces for pushes instead of the empty space inside an enclosing hull. See the [Coupler guide](../couplers.md).

Native arm-endpoint attachment and release retain their original physical before
images while renewing the receiving vehicle association. Interrupted transfers
recover through their durable journals, including copied capability data.

Failed structural updates restore the original cargo, seats and vehicle position.
If the original source cannot be reinstalled, the vehicle remains inert and keeps
that complete snapshot for recovery.

## Module Rack

The new **Module Rack** has four horizontal slots for **Blank Modules**. Click a specific slot to insert a module with a sliding animation, or use an empty hand to retrieve it. The matching row of cyan/amber buttons lights when occupied.

Racks mount on all six faces, including floors and ceilings. Their controls and insertion direction rotate with the front.

Its darker graphite housing follows the compact equipment design: a one-pixel rim, three-pixel right panel recessed by one pixel, and paired vertical module handles. The right panel contains slot controls without an Access Card or Chip Mount.

Seated modules and the rack share Minecraft's ordinary chunk mesh. Only active insertions need a temporary renderer; idle racks have neither ticking tiles nor permanent tile-entity rendering.

See [Module Rack usage and crafting](../module-rack.md).
