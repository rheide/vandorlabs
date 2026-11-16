# Universal Constructors

See the [step-by-step multiblock guides](multiblocks/README.md) for screenshots of each construction stage, including the newer drive and navigation computer assemblies that use the same frame system.

On Forge 1.12.2, all three builders produce items from Programmable Matter and RF. The Synthesis Tower builds Cores and controllers, the Assembly Press builds chips, cards and modules, and the Enclosed Gantry builds mechanical parts. Frame sizes and construction steps are unchanged. On NeoForge 1.21.1, only the Tower has production; the Press and Gantry retain structure-only behavior.

| Core | Width x depth x total height | Matter Blocks + Core |
| --- | --- | --- |
| Assembly Press | 4 x 3 x 3 | 25 + 1 |
| Synthesis Tower | 3 x 3 x 5 | 28 + 1 |
| Enclosed Gantry | 5 x 3 x 3 | 30 + 1 |

Use exactly one matching Core; every `M` below must be a Programmable Matter Block, not another Core. Place the matching Core in **any bottom corner**, facing any horizontal direction. Fill the rest of the bottom layer with Programmable Matter Blocks. Build four corner pillars to the listed total height, counting the bottom layer, and connect their tops with a rectangular rim. Leave the interior empty, including the middle of the roof. The footprint can run along either horizontal axis; the Core detects it automatically and prefers its placed facing when that facing fits.

For example, an Assembly Press uses these layers (`C` = Core, `M` = Programmable Matter Block, `.` = air):

```text
Bottom    Middle    Top
CMMM      M..M      MMMM
MMMM      ....      M..M
MMMM      M..M      MMMM
```

Right-click the Core to assemble the complete frame. Matter Blocks are ordinary building blocks and do not activate the constructor. If it fails, the message identifies a mismatched cell in the closest frame. Check that the base is solid, the height includes the base, the roof is a rim rather than a solid layer, and every interior cell is air.

Sneak-right-click the Core to dismantle the assembly back into its frame. It stays dismantled until you right-click the Core again. Mining a reserved part dismantles the assembly and removes that cell. Breaking the Core also restores the Matter Blocks. Hidden occupied cells prevent other blocks from overlapping the complete model. Each cell records its original frame material so restoration survives chunk unloading. The detected Core corner is saved and synchronized to clients.

## Carrying a formed Core with an arm (Forge 1.12.2)

Build and activate the complete frame first, then place an Extender Arm or Rotating Arm so its attachment touches the Core or one of its reserved parts. Keep the arm's own cells outside the machine. Contact with one part selects that machine's complete footprint, including its interior reservations; adjacent walls and floors are not added to a single arm's load. Every reserved cell must be loaded and still belong to the same Core. Joined selections must contain the complete machine; incomplete selections are refused.

Activate the arm normally. The Core, its relative part links, settings and inserted chip travel together. At fractional captured poses, click the machine body or a reserved part to cycle its normal activity. Use the Configurizer on either to open the same machine settings dialog used by the placed Core. These actions keep the arm’s target unchanged; settings stay on the arm’s single machine snapshot and survive saving and native restoration. To change the frame, insert or recover a chip, return the arm to a pose where the machine becomes ordinary world blocks. A tilted Core remains captured because Core blocks have horizontal facing states; the original construction pose is available for native editing.

Allow space for the complete machine throughout the swing, including below the folded body. Tilted captured machinery follows the arm socket without being shifted onto a block grid. Ground and nearby blocks can prevent a stroke; the machine remains retained and selectable. Raise the construction or clear its swept area before expecting the full motion.

At a supported native endpoint, ordinary Core configuration and chip changes are retained by the next stroke. Native part mining and sneak-right-click dismantling keep their usual behavior. Mining a captured Core or part is refused with an instruction to return it to native blocks, so it cannot leave an incomplete moving machine.

Core machinery stays visible while an arm returns it to native blocks, including when the client receives the block, machine data and arm update at different times. Its detected corner and current settings appear together; the native machine takes over once that data and the arm’s visual pose are ready.

<a id="synthesis-tower-production-forge-1122"></a>

## Synthesis Tower production

Build the same 3 x 3 x 5 frame with 28 Programmable Matter Blocks and one
Synthesis Tower Core, following the [Synthesis Tower construction guide](multiblocks/synthesis-tower/README.md).
This production behavior is available on Forge 1.12.2 and NeoForge 1.21.1.
Right-click the Core to form the machine. Its finished front has a square
display on the left and a Data Chip Mount and Module Rack on the right.

Right-click the formed machine, or use the Configurizer, to open Production.
Select Synthesis Tower, Assembly Press or Enclosed Gantry from the design list;
the rotating textured preview shows the complete structure. Insert one
Programmable Matter Block in the Matter slot. Each Core uses 8,000 RF, at
20 RF per tick, taking at least twenty seconds at normal server speed.
A running build retains its selected design if you select the next design.
Remove the completed Core from the output slot or right-click the finished
Core in the processing chamber. The next queued input waits for the output
to be removed. Dismantling returns unprocessed inventory and refunds the
Matter Block of an unfinished build; spent RF is not refunded.

The tower has no internal battery. Its flush four-bay rack accepts up to four
charged [Battery Modules](module-rack.md), for **40,000 RF** total. Insert or
retrieve each physical module at its bay button, or use the four Battery slots
in Production. Modules slide into and out of the rack and retain their charge.
Supply external RF through the Power In port; connected power also recharges
the installed modules. The bottom rear-right block accepts RF
on its back and bottom faces. The bottom rear-left block accepts Matter on
its back face and exposes completed Cores on its left and bottom faces.
The RF port lights when a compatible power source is connected, even when
no RF is moving. Place a chest or compatible item inventory immediately outside
either output face to receive completed Cores automatically. If that inventory
is full or unavailable, the Core remains in the tower until there is room or
you collect it.

The Redstone tab uses the standard activation selector and receiving-channel
list. Manual means continuous automatic processing whenever input and RF are
available; Redstone On requires a physical signal or an active listed
channel; Redstone Off requires both to be absent. An empty channel list uses
physical Redstone only. Right-click the activation option to cycle backwards.
Disabling the tower pauses its build without discarding input or progress,
including through saving and reloading. RF can still recharge its modules.

On Forge 1.12.2, a [Catalog Chip (Master)](data-chips.md) adds its explicitly
stored Tower designs to the three default builder Cores. Each machine reads
only its own design list; a Press or Gantry cannot produce Tower Cores.
NeoForge 1.21.1 retains the three default Tower recipes. The chamber shows the selected Core forming from
the bottom upwards. Four scanning beams follow its growing surface while RF
is being spent; paused or unpowered work retains its partial Core without
beams. The smaller square screen and its projecting keyboard sit to the left
of the chip mount and flush rack. Chamber effects and the front
display's rotating preview are rendered only within 24 blocks.

## Assembly Press and Enclosed Gantry production (Forge 1.12.2)

Both machines reuse the Tower’s Production and Redstone dialog, four-bay Battery
Module rack, RF charging, saved job progress and adjacent-inventory output.
Right-click or use the Configurizer on a formed member to open the same editor.
The rear-bottom right corner accepts RF on its back and bottom; the rear-bottom
left accepts ingredients on its back and outputs on its left and bottom.

Each job consumes **one ingredient and 8,000 RF**, at **20 RF/tick for 400 ticks**.
The Press takes **Programmable Matter Ingots**; the Gantry takes **Programmable
Matter Blocks**. Their front displays show the selected output, with lower
keyboards, fitted chip mounts and flush racks. Their chambers have no new
fabrication animation.

| Builder | Designs available without a Catalog Chip | Master Catalog additions |
| --- | --- | --- |
| Assembly Press | Data Chip, Access Card | Battery Module, Autopilot Module |
| Enclosed Gantry | Regular and Large Extending/Rotating Arms, regular and Large Landing Wheels, Landing Ski, Landing Plate, Coupler and Coupler Slab | The same mechanical designs |

Catalog permissions apply when starting a job. A started job retains its own
blueprint if the chip or selected design changes. Dismantling returns the
unfinished job’s original ingredient; spent RF is not refunded.

## Core appearance and recipe browsing

Eleven unformed Core block and inventory models use the supplied Voxel Machines
cuboids and their original 64×64 material atlases. Constructor, communications,
array and drive Cores each retain their distinct armor, cooling ribs and stepped
braces. The two navigation Cores retain their existing cage models. Gaps in the
imported models are real empty space, and inventory icons retain padding.
Fabrication prints each design's occupied voxels from the bottom upwards,
including partial rows and their individual textured top faces. Dynmap uses the
same geometry and artwork. NeoForge includes only the Synthesis Tower, Assembly
Press and Enclosed Gantry Core models; the other imports remain Forge-only.
Registry identities and frame assembly requirements are unchanged.

With JEI installed, the **Matter Infuser** category shows iron-to-Matter
production and Battery Module charging. The **Synthesis Tower** category shows
one Matter Block becoming each of the three default Cores, with the 8,000 RF
cost and twenty-second minimum. Select the matching design in Production
before inserting Matter; recipe browsing does not change the tower's selected
or running design. The Infuser supports ordinary recipe transfer into its
input and catalyst slots.
