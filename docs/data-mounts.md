# Access Card and Data Chip Mounts

Use the [Card / Chip Programmer](card-chip-programmer.md) to assign companies, duplicate credentials, and edit titled Journal Data Chips.

The modeled holders on the [drive assemblies and joined navigation tables](multiblocks/README.md) use these same Data Chip Mount behaviors. A navigation assembly shares one holder on the front-center module; its chip and settings follow that holder when the layout changes.

Core-based multiblocks, navigation tables and consoles are display-only consumers. Their optional Security gate checks the inserted chip; they never emit a physical or channel redstone signal. Their mount is visible only with Security enabled; hiding it retains any stored chip safely until re-enabled. See [Navigation display settings](navigation-displays.md) and [Machine display settings](machine-displays.md).

## Standalone mounts

Place a mount against a solid face, including a floor or ceiling. It occupies a small area around the token rather than the entire block face. Right-click with a matching Access Card or Data Chip to insert one item. Click with an empty hand to remove it. The two mount types accept their corresponding unbranded and six company variants.

Use the Configurizer or sneak-right-click to open the dialog. **Any card** or **Any chip** accepts every company in the corresponding family. The adjacent sample slot opens the same inventory picker as a Programmable Door: drag one company-specific credential into the sample slot to restrict the mount to that company. This copies the filter without consuming or moving inventory items. The selected company name replaces the wildcard label; click that label or right-click the sample slot to clear it. Unbranded samples also select Any. Insert physical items by using them on the block.

Choose **Stay Inserted**, **Stay Inserted: Pulse**, **Return After Open: Toggle**, or **Return After Open: Pulse**. Right-click the option to cycle backwards. Standalone mounts provide ordinary redstone and the configured output channels in these modes. Integrated machine/display readers instead authorize their display while the matching chip remains inserted; they never emit power or latch the display on after returning the chip. Every mode rejects tokens outside the company filter without consuming them, briefly displaying an amber X. Removing a retained token stops its output. Empty mounts show the selected company logo, or an unbranded background when Any is selected. The bottom cyan indicator brightens while powered; the amber indicator remains constant.

Both return modes slide an accepted token down through the open top, activate at the scan midpoint, then withdraw it upwards and return it to the inserting player's inventory after one second. Toggle retains the chosen output across returns and reloads; another accepted token switches it again. Pulse emits a one-second signal that continues after the item returns. One real item is borrowed even in Creative mode. Unaccepted designs are left untouched. Changing the company filter or behavior clears the saved toggle state.

An item awaiting return cannot be extracted by another player or automation. If the player's inventory is full or the player disconnects, the mount retains the item and retries while loaded. Its saved return owner and scan progress survive reloads and vehicle conversion. Breaking the mount releases the held item once.

The inserted item retains its complete item data. Breaking a mount releases the inserted token separately. Picking the block does not copy its contents.

Cards are rectangular and chips square. Both use closed models one model unit thick, with 128px front artwork extracted from the supplied collages and one texture pixel removed from each corner. The solid outline matches those clipped corners.

Programmable Doors offer **Security: Off** plus the same four reader behaviors. **Return After Open: Toggle** opens at the scan midpoint and remains open after the card returns; the next accepted insertion at either face toggles the same door closed. **Return After Open: Pulse** returns the card after one second and provides a five-second opening window. **Stay Inserted** holds the door open while an accepted card remains; **Stay Inserted: Pulse** uses the five-second opening window while retaining the card. Security takes precedence over manual and redstone opening, including in vehicle mode. A valid card can be used anywhere on a secured door, including any cell of a large door, and enters the reader on the clicked side. Empty-hand extraction still targets the panel. Centered doors offer **Panel: Both**; near/far doors offer Off and On. Each face has its own physical card and return animation. Picking a configured door copies its security policy, without copying cards or an active toggle state.
