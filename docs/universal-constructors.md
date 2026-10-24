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
