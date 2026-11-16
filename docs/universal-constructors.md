# Universal Constructors

See the [step-by-step multiblock guides](multiblocks/README.md) for screenshots of each construction stage, including the newer drive and navigation computer assemblies that use the same frame system.

The Synthesis Tower produces Core blocks from Programmable Matter and RF. The Assembly Press and Enclosed Gantry remain decorative multiblock assemblies. Their frame sizes and construction steps are unchanged.

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

## Synthesis Tower production (Forge 1.12.2)

Build the same 3 x 3 x 5 frame with 28 Programmable Matter Blocks and one
Synthesis Tower Core, following the [Synthesis Tower construction guide](multiblocks/synthesis-tower/README.md).
Right-click the Core to form the machine. Its finished front has a square
display on the left and a Data Chip Mount and Module Rack on the right.

Right-click the formed machine, or use the Configurizer, to open Production.
Select Synthesis Tower, Assembly Press or Enclosed Gantry from the design list;
the rotating textured preview shows the complete structure. Insert one
Programmable Matter Block in the Matter slot. Each Core uses 8,000 RF, at
40 RF per tick, taking at least ten seconds at normal server speed.
A running build retains its selected design if you select the next design.
Remove the completed Core from the output slot or right-click the finished
Core in the processing chamber. The next queued input waits for the output
to be removed. Dismantling returns unprocessed inventory and refunds the
Matter Block of an unfinished build; spent RF is not refunded.

The tower has no internal battery. Install a charged [Battery Module](module-rack.md)
in its battery slot, or supply RF through its Power In port. Connected power
also recharges the installed module. The bottom rear-right block accepts RF
on its back and bottom faces. The bottom rear-left block accepts Matter on
its back face and exposes completed Cores on its left and bottom faces.
The RF port lights when a compatible power source is connected, even when
no RF is moving.

The Redstone tab uses the standard activation selector and receiving-channel
list. Manual means continuous automatic processing whenever input and RF are
available; Redstone On requires a physical signal or an active listed
channel; Redstone Off requires both to be absent. An empty channel list uses
physical Redstone only. Right-click the activation option to cycle backwards.
Disabling the tower pauses its build without discarding input or progress,
including through saving and reloading. RF can still recharge its module.

Data Chips currently retain their ordinary inventory behavior; additional
design data is reserved for a later update. The three default designs are
available without a chip. The chamber shows the selected Core forming from
the bottom upwards, with four scanning beams. Chamber effects and the front
display's rotating preview are rendered only within 24 blocks.
