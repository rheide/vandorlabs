# Builder machines on Minecraft 1.21.1

This native slice covers the Synthesis Tower, Assembly Press and Enclosed Gantry,
with their three Core items. Other Core multiblocks remain outside this conversion. The native
[Matter Infuser](matter-and-storage.md) supplies Matter Ingots and charges modules. The Assembly Press and Enclosed Gantry form their
finished structures; Core production belongs to the Synthesis Tower.

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
dialog. They retain their physical item ownership; additional chip designs are
reserved for a later update. All three default designs work without a chip.

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

The three Core block/item models use the supplied Heavy Constructor, Automated
Fabricator and Industrial Printer cuboids and their original 64×64 atlases.
Their occupied geometry also drives bottom-up fabrication, including the
textured faces of partially printed rows. Inventory icons retain visible padding.
This native conversion does not register the other ten Forge Core families.

With JEI installed, **Matter Infuser** lists iron conversion and Battery Module
charging; **Synthesis Tower** lists the three Core outputs and their RF/time
requirements. Choose the design in Production before supplying Matter. JEI
browsing does not change the selected or running tower design. Infuser recipe
transfer fills its input and catalyst slots.
