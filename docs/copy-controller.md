# Copy Controller

The Copy Controller makes a complete block copy between two Programmable Surface pads. It preserves block states, orientation, settings, saved inventories and supported links. The input remains in place. Entities are not copied.

Place the controller between two pads. When placed, its front faces you: the cyan input is on your left and the amber output is on your right. Put the material chest directly behind the controller, away from you. Both pads must be complete squares of the same size, face the same direction, and belong to this controller. Pads can be up to 16 × 16 blocks. Configure their artwork and Stretch, Tile or Fit independently through the Programmable Surface dialog; claiming a pad does not change its artwork.

Open the controller and choose **Check areas** after building or changing the pads. Use the **Height** slider, shared with the Turntable Controller, to set the scan height from 1 to 16 blocks. Scanning starts one block outward from each surface, follows its face normal, and includes the entire square at every depth. The output volume must be empty, including players and other entities. Keep complete doors, machines and other multiblock assemblies inside the input volume.

Use **Copy** to start a manual operation. Redstone modes can instead trigger a copy on an active edge: powered or unpowered. An empty channel list uses adjacent physical redstone; a configured list requires all listed channels. The configuring player must remain online, alive and in the same dimension so the operation can recheck their building permissions. Loading a world with an already active signal does not trigger a copy.

## Materials and exact contents

Creative mode needs no chest or materials. Survival mode withdraws the building items and all retained items from the chest behind the controller. A double chest is supported. The dialog reports the first missing material and leaves both areas and the chest unchanged when preflight fails.

Building items use their obtainable item and variant. A double slab costs two slabs; snow costs its layer count. Reserved parts of a logical assembly do not each cost another item: a four-cell Card / Chip Programmer, for example, costs one programmer plus its retained items.

Stored items must match their complete item data, including names, enchantments, container contents and compatible Forge capabilities. A filled shulker box is paid for as that exact filled item, rather than charging its contents again. A copied chest needs a chest item and matching copies of every stored stack. Retained arm payloads also cost their held blocks and contents; payloads already materialized in the input volume are counted once.

Open unopened loot containers before copying them in Survival. Nonempty battery and Hex Tank resources require matching lossless configured carrier items. Other supported machines with stored energy, fluid, gas or burning fuel must be emptied before Survival copying when their resources cannot be represented by an exact material item. Creative copies retain their complete saved data.

## Channels and supported blocks

The signed **Redstone Channel Increment On Copy** adjusts Vandor redstone channels in the result, including saved input/output lists, control rows and supported nested captured structures. Negative values subtract. Unlinked zero values stay unlinked. Every linked result must remain between 1 and 65535; overflow or underflow rejects the whole operation before withdrawal. Item contents and unrelated foreign data are preserved.

Copying uses the same lossless block support policy as [Vehicle Mode](vehicle-compatibility.md) and [Turntables](turntables.md). Every block is checked; there is no fidelity setting or sampling. Unsupported blocks, incomplete assemblies, unavailable tile data and tiles that cannot round-trip exactly are refused. Finish motion, parking or recovery and release other controllers' claimed assemblies before copying them. Complete input/output regions and their boundaries must be loaded and inside the world border.

## Saving and recovery

The controller validates the full input, output, permission checks and material chest before changing anything. It saves a recovery journal before withdrawal. Installed inventory tiles remain inaccessible to players, hoppers and item-handler consumers until their affected chunks have passed a verified save checkpoint. The output pad is active while copying and briefly after completion.

A failed operation either completes a verified rollback or retains its journal with the output area, material chest and controller protected. World loading rebuilds those protections before gameplay ticks. When all affected chunks are loaded and saving is available, recovery retries automatically. Keep the affected area intact and inspect the controller's status. If external changes make the saved output or chest disagree with the journal, recovery stays locked rather than refunding materials against missing or changed blocks. Restore the exact protected data from a world backup before retrying such a recovery.

Automation authorization is stored separately at the controller’s world position. Configure or manually operate the controller to authorize it. Copying its complete configuration preserves the saved settings and operator fields, while the copied controller receives the actual copying player’s authorization. Pasting tile NBT or replacing the controller requires configuration again; portable operator UUIDs do not grant permissions.
