# Universal Constructors

See the [step-by-step multiblock guides](multiblocks/README.md) for screenshots of each construction stage, including the newer drive and navigation computer assemblies that use the same frame system.

The Assembly Press, Synthesis Tower, and Enclosed Gantry are decorative multiblock assemblies. Their Core blocks are currently creative-only. They do not process recipes yet.

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
