# Builder machines on Minecraft 1.21.1

The Synthesis Tower fabricates Cores and copies supported blocks. The Assembly
Press fabricates chips, cards and modules, and the Enclosed Gantry makes
mechanical parts. The standalone drives and navigation computers use the same
Matter frame assembly process. The native [Matter Infuser](matter-and-storage.md)
supplies Matter Ingots and charges modules.

## Build and form a machine

Use the same native Programmable Matter Blocks, dimensions and construction
stages as the illustrated guides:

| Machine | Width × depth × height | Matter Blocks and Core | Building stages |
| --- | --- | --- | --- |
| Synthesis Tower | 3 × 3 × 5 | 28 + one matching Core | [Tower guide](multiblocks/synthesis-tower/README.md) |
| Assembly Press | 4 × 3 × 3 | 25 + one matching Core | [Press guide](multiblocks/assembly-press/README.md) |
| Enclosed Gantry | 5 × 3 × 4 | 34 + one matching Core | [Gantry guide](multiblocks/enclosed-gantry/README.md) |

Place the Core in any bottom corner, facing a horizontal direction. Fill the
bottom layer with Matter Blocks, raise the four corner pillars, and join their
tops with an open rectangular rim. Keep the interior and roof centre empty.
Right-click the Core to form the machine. Formation waits for its durable
journal preparation before replacing the frame; a pending operation keeps the
original frame and its contents owned by the same machine.

The first Core is available in Creative. Core items have no crafting-table
recipes; a powered Synthesis Tower can subsequently produce all three designs.

## Synthesis Tower operation

Right-click the formed tower or use the Configurizer on a reserved member to
open the same Production dialog. Select a design from the list; its textured,
rotating preview shows the complete multiblock. Supply one Programmable Matter
Block in the Matter slot. One Core costs **16,000 RF**, consumed at **20 RF per
tick**, for a minimum of forty seconds at normal server speed.

Install up to four **10,000 RF Battery Modules** in the flush four-bay rack or
its four dialog slots, for **40,000 RF** total, or connect an external RF source.
Use each bay button to insert a held module or retrieve it with an empty hand.
The fitted rack uses the same sliding cassette motion and charge segments as a
placed Module Rack. The tower has no built-in battery.
Connected RF recharges the installed modules, including while Redstone pauses the
build. Charging and work retain the same energy accounting regardless of
whether the source or tower ticks first.

Viewed from the rear, the **right bottom corner** accepts RF on its back and
bottom faces. The **left bottom corner** accepts Matter on its back face and
exposes completed Cores on its outward left and bottom faces.

The Redstone tab offers Always, Redstone On and Redstone Off activation, plus
receiving channels. Physical Redstone or any active listed channel enables On;
Off requires both to be absent. Blank channels use physical Redstone only.
Right-click activation to cycle backwards. Pausing, unloading and reloading
retain the selected job and paid work. Processing waits for deferred input
registration after loading, so Off cannot briefly run before its inputs settle.

The chamber displays the open, colored Core scaffold forming from the bottom
upwards. Four scanning beams track its top surface while the tower spends RF;
paused or unpowered work keeps the partial Core without beams. The smaller
square display and projecting keyboard are on the front left; the chip mount
and flush rack are on the right. The display shows the full rotating structure
preview.
These fabrication effects are drawn only within 24 blocks. Collect a finished
Core through the output slot or by right-clicking it in the chamber. The next
queued Matter Block waits until that output is removed. A chest or compatible
item inventory immediately outside either output face receives finished Cores
automatically. A full or unavailable destination leaves the output in the tower.

Data Chips can be inserted and retrieved through their physical mount or the
dialog. All three default Core designs work without a chip. The Master
Catalog also adds Sentinel Array, Survey Dish, Maxwell, Seyr Meridian, Oculus and
Vector Fold Array Cores and the full-block Ramp Controller. A Ramp Controller consumes one Programmable
Matter Block and **8,000 RF** at **20 RF per tick**.

## Assembly Press operation

Supply one Programmable Matter Ingot per item. The Press fabricates at
**40 RF per tick** for **200 ticks**, using **8,000 RF** per item. Data Chips
and Access Cards are included. Insert a Master Catalog Chip to add Battery
Modules, Autopilot Modules and Navigation Data Modules to the recipe list.

Choose a recipe in Production. The item forms flat on the bed under four
green lasers. Right-click the finished item on the bed or take it from the
output slot. Load Battery Modules into the four front rack bays or their
dialog slots. The rear ports provide external power, material feed and
automatic output. Paused jobs retain their chosen recipe and paid work, even
if the Catalog Chip is removed.

## Enclosed Gantry operation

Choose a regular or Large Extender, Rotating Arm or Landing Wheel, or choose a
Landing Ski or Landing Plate. These eight designs are built into the Gantry.
Feed one Programmable Matter Block through the rear item port or Production
dialog. Each item costs **8,000 RF**, drawn at **40 RF per tick** over
**200 ticks**. The rear right corner accepts external power. The rear left
corner provides the material feed and completed-item output. The carriage
moves the print head as its blue scanner traces the part. The Gantry holds
the partially formed item while power is paused. Right-click the completed
part on the bed to collect it, or use the output slot. The Production and
Redstone tabs offer the same inventory, battery and activation controls as
the Press.

## Standalone drives

The Maxwell, Seyr Meridian and Pinnacle Oculus Drive Cores use the same
Matter frame journals as the builders. Follow the illustrated
[Maxwell](multiblocks/longburn-drive/README.md),
[Seyr Meridian](multiblocks/meridian-core/README.md) and
[Pinnacle Oculus](multiblocks/oculus-drive/README.md) guides. Obtain each Core
from a Tower with a Master Catalog Chip.

Use the Configurizer on a formed drive to open its display, keyboard, engine
and receiving-channel settings. A regular right-click on an Oculus away from
its chip mount cycles Standby, Charging and Active. Its shutters reverse
from their current position when the target changes. The strongest loaded
physical or configured channel signal controls the drive when Redstone
activation is enabled. **Engine Redstone Off** inverts that level. Signal
brightness follows the effective output from 0 to 15. The Maxwell also offers
exhaust intensity and a minimum exhaust level in Signal brightness mode.
Right-click a setting to cycle backwards. The optional Data Chip mount retains
one chip for retrieval. The drive effects are cosmetic.

### Vector Fold Array

Follow the [Vector Fold Array build guide](multiblocks/vector-fold-array/README.md)
to assemble its two-block-high frame. The Configurizer exposes separate display
and keyboard artwork and a Redstone receiver. With activation enabled, any
loaded physical input or active configured channel switches the Array to its
authored active appearance. Its optional Data Chip stays in the mount until
removed.

## Navigation computers

The Paladyne and Seyr Navigation Computer Cores are available in Creative.
Build each column from its Core and two Matter Blocks using the
[Paladyne](multiblocks/paladyne-navigation-computer/README.md) or
[Seyr](multiblocks/seyr-navigation-computer/README.md) guide. A formed
computer starts in Idle. Right-click its case to cycle Computing, Off and Idle.

The Configurizer edits the single display's artwork, animation and optional
Redstone receiver. Redstone activation can hold the Off appearance while
the receiver is unpowered. The computer retains the selected activity and
resumes it once the signal returns.

## Dismantling and ownership

Sneak-right-click a formed member to restore its Matter frame. Mining a member
queues durable teardown and omits the mined cell from the restored frame; a
frame member yields one Matter Block. Mining a Core releases its owned input,
output, all four batteries and chip exactly once. An unfinished job returns its Matter
Block; spent RF is not refunded. Ordinary tool and Creative rules still apply
to the Core block item.

A dismantled Core can still hold its inventories. Mining it uses the same
durable ownership transfer even if the surrounding frame is incomplete.
Dialogs stay open but read-only during a pending transfer.

Direct non-player replacement, destruction and explosion removal of formed
machines or Cores holding recoverable contents is refused. Dismantle and empty
them before bulk world editing. Malformed saved ownership remains inert with
its original data retained. Native arm carrying and Copy Controller integration
for these assemblies remain deferred and are refused by their existing adapter
checks.

## Cores and JEI

The builder Core block and item models use the supplied Heavy Constructor,
Automated Fabricator and Industrial Printer cuboids and original 64×64 atlases.
Their occupied geometry also drives bottom-up fabrication, including the
textured faces of partially printed rows. The drives and computers use their
authored Core geometry and stage-specific structure artwork. Inventory icons
retain visible padding. The [Survey Dish and Sentinel Array](navigation-displays.md)
have their own native adapters.

With JEI installed, **Matter Infuser** lists iron conversion and Battery Module
charging. The **Synthesis Tower**, **Assembly Press** and **Enclosed Gantry**
categories list their fixed products, including cost, minimum time and any
Catalog Chip requirement. Choose the design in Production before supplying
Matter. Infuser recipe transfer fills its input and catalyst slots.
