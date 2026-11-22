# Copy Controller

On Forge 1.12.2 and NeoForge 1.21.1, the Copy Controller makes a complete block copy between two Programmable Surface pads. It preserves block states, orientation, settings, saved inventories and supported links. The input remains in place. Entities are not copied.

Place the controller between two pads. When placed, its front faces you: the cyan input is on your left and the amber output is on your right. Put the material chest directly behind the controller, away from you. Both pads must be complete rectangles with matching width and height, face the same direction, and belong to this controller. Pads can be up to 32 × 32 blocks. Configure their artwork and Stretch, Tile or Fit independently through the Programmable Surface dialog; claiming a pad does not change its artwork. Connected Surface blocks can use different active/inactive textures: artwork does not split Copy Controller membership. Drawing still groups only matching appearance, so each cell keeps its selected texture and layout.

The controller automatically rechecks the pads as you add or remove Surface blocks. **Check areas** also performs an immediate manual check. Invalid-pad messages name the input/output side and the controller-adjacent seed position; hover the status area to read a long error in full. Use the **Height** slider, shared with the Turntable Controller, to set the scan height from 1 to 16 blocks. Scanning starts one block outward from each surface, follows its face normal, and includes the entire rectangle at every depth. The copied design is centered by its occupied block bounds across the two Surface axes. Placement stays on whole blocks; an odd spare block remains on one edge. Its position through the scan depth is preserved. The output volume must be empty, including players and other entities. Keep complete doors, machines and other multiblock assemblies inside the input volume.

Use **Copy** to start a manual operation. Redstone modes can instead trigger a copy on an active edge: powered or unpowered. An empty channel list uses adjacent physical redstone; a configured list requires all listed channels. The configuring player must remain online, alive and in the same dimension so the operation can recheck their building permissions. Loading a world with an already active signal does not trigger a copy.

## Changing the surface size

Add blocks along the outer edges of each pad until both are filled rectangles with matching
dimensions, up to 32 × 32. Keep each pad connected to the Surface block directly
beside its side of the controller, and keep every added block facing the same way.
For example, enlarge both 5 × 5 pads to 7 × 5 by adding two columns
on each side's available outer edges. The controller detects the new area once
both rectangles are complete; rebuilding the controller is unnecessary.

While one side is larger or a pad has holes, copying is unavailable and the dialog
explains the mismatch. Remaining Surface blocks keep their artwork and stay
opaque. An incomplete pad displays its blocks independently until it forms a
complete rectangle again. Removing or replacing blocks preserves the appearance
settings saved on the remaining surfaces. Finish an active copy before editing
its protected pads.

## Materials and exact contents

Creative mode needs no chest or materials. Survival mode withdraws the building items and all retained items from the chest behind the controller. The supply can be a double chest. To copy a double chest as part of the input, include both halves in the scan volume. The dialog reports the first missing material and leaves both areas and the chest unchanged when preflight fails.

Building items use their obtainable item and variant. A double slab costs two slabs; snow costs its layer count. Reserved parts of a logical assembly do not each cost another item: a four-cell Card / Chip Programmer, for example, costs one programmer plus its retained items.

Stored items must match their complete item data, including names, enchantments, container contents and compatible Forge capabilities. A filled shulker box is paid for as that exact filled item, rather than charging its contents again. A copied chest needs a chest item and matching copies of every stored stack. Retained arm payloads also cost their held blocks and contents; payloads already materialized in the input volume are counted once.

Open unopened loot containers before copying them in Survival. On Forge 1.12.2, Survival copies start with empty Vandor Batteries, Hex Tanks, Maxwell fuel reservoirs, standalone Mekanism fluid tanks and Energy Cubes, and Thermal Dynamics fluiduct and fluxduct buffers. The original retains its contents. Older versions required matching filled Vandor carrier items. Empty a charged battery or filled tank item before using it as a building material. For Mekanism storage, supply a fresh empty item of the same tier. Other supported machines with stored resources still follow their own material rules. Creative copies preserve saved charge and fluid levels.

## Channels and supported blocks

The signed **Redstone Channel Increment On Copy** adjusts Vandor redstone channels in the result, including saved input/output lists, control rows and supported nested captured structures. Negative values subtract. Unlinked zero values stay unlinked. Every linked result must remain between 1 and 2,147,483,647; overflow or underflow rejects the whole operation before withdrawal. Item contents and unrelated foreign data are preserved.

Forge 1.12.2 Copy Controllers support Thermal Dynamics fluiducts and fluxducts. They preserve installed attachments, including fluid Servos and Retrievers, with their tier, face and settings. Survival uses the matching duct and attachment items for each copy. The general block support rules come from Vehicle Mode and Turntables. NeoForge 1.21.1 uses its native copy adapters. The controller checks every block, including complete multiblock assemblies and available tile data. Finish motion, parking or recovery and release other controllers' claimed assemblies before copying them. Keep the complete input/output regions and their boundaries loaded and inside the world border. [Vehicle Mode](vehicle-compatibility.md) · [Turntables](turntables.md) · [Native copy adapters](development/native-copy-controller.md)

Thermal itemducts and long-range crossover networks require separate copy adapters. RFTools Power Cells and Mekanism Dynamic Tanks have shared storage and are refused. Mekanism requires permission to access a standalone tank or Energy Cube before it can be copied.

## Saving and recovery

The controller validates the full input, output, permission checks and material chest before changing anything. It saves a recovery journal before withdrawal. Installed inventory tiles remain inaccessible to players, hoppers and item-handler consumers until their affected chunks have passed a verified save checkpoint. The output pad is active while copying and briefly after completion.

A failed operation either completes a verified rollback or retains its journal with the output area, material chest and controller protected. World loading rebuilds those protections before gameplay ticks. When all affected chunks are loaded and saving is available, recovery retries automatically. Keep the affected area intact and inspect the controller's status. If external changes make the saved output or chest disagree with the journal, recovery stays locked rather than refunding materials against missing or changed blocks. Restore the exact protected data from a world backup before retrying such a recovery.

Automation authorization is stored separately at the controller’s world position. Configure or manually operate the controller to authorize it. Copying its complete configuration preserves the saved settings and operator fields, while the copied controller receives the actual copying player’s authorization. Pasting tile NBT or replacing the controller requires configuration again; portable operator UUIDs do not grant permissions.

## Copying from a Vehicle Chip

Attach a Data Chip Mount directly onto the Copy Controller, insert a written Vehicle Chip and select **Source: Vehicle Chip** in the dialog. The dialog explains this option when no Data Chip Mount is attached. Only a filled output Surface touching the controller’s output side is required in this mode; the input side can be empty. **Source: Input Surface** restores the usual two-pad workflow.

Set the output height and Surface size to contain the entire saved design. The occupied block footprint is centered on the output Surface without rotating the design. If either footprint dimension exceeds the Surface, or the design exceeds the configured height, the entire copy is refused before materials are withdrawn or blocks are placed. **Copy** uses the chip's exact block and tile data, including stored inventories; the rotating silhouette does not determine placement. The material chest, Survival costs, channel increment, empty-output checks and recoverable copy journal work as they do for Surface copies. The chip is retained. Changing or removing it before publication cancels the copy safely. Existing unsupported copying adapters remain refused.

Rectangles may be narrow strips or larger filled areas, with each side from 1 to 32 blocks. A 3 × 5 input requires a 3 × 5 output on the same surface axes; a transposed 5 × 3 output does not match. Holes, disconnected cells and mismatched faces remain invalid. This does not change the Turntable Controller’s square-pad requirement.
