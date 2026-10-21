# Data Card and Data Chip Mounts

Place a mount against a solid face, including a floor or ceiling. It occupies a small area around the token rather than the entire block face. Right-click with a matching Data Card or Data Chip to insert one item. Click with an empty hand to remove it. The two mount types accept their corresponding unbranded and six company variants.

Use the Configurizer or sneak-right-click to open the dialog. Seven design slots form an allow-list: click a design to toggle it, or click there with a carried token to add its design. Filter selection never consumes the carried item. Selecting all seven designs accepts the entire card or chip family. Insert physical items by using them on the block.

Choose **Stay Inserted**, **Stay Inserted: Pulse**, **Return After Open: Toggle**, or **Return After Open: Pulse**. Right-click the option to cycle backwards. All modes provide ordinary redstone and the configured output channels. In the Stay modes, a token outside the allow-list can sit in the mount but cannot activate its output; removing a token stops the output. Empty mounts show a subdued logo from the accepted designs. Click an empty mount with an empty hand to cycle its logo. The bottom cyan indicator brightens while powered; the amber indicator remains constant.

Both return modes slide an accepted token down through the open top, activate at the scan midpoint, then withdraw it upwards and return it to the inserting player's inventory after one second. Toggle retains the chosen output across returns and reloads; another accepted token switches it again. Pulse emits a one-second signal that continues after the item returns. One real item is borrowed even in Creative mode. Unaccepted designs are left untouched. Changing the allow-list or behavior clears the saved toggle state.

An item awaiting return cannot be extracted by another player or automation. If the player's inventory is full or the player disconnects, the mount retains the item and retries while loaded. Its saved return owner and scan progress survive reloads and vehicle conversion. Breaking the mount releases the held item once.

The inserted item retains its complete item data. Breaking a mount releases the inserted token separately. Picking the block does not copy its contents.

Cards are rectangular and chips square. Both use closed models one model unit thick, with 128px front artwork extracted from the supplied collages.

Programmable Doors offer **Security: Off**, **Return After Open**, and **Stay Inserted**, with the same seven card designs and reader appearance. Return After Open opens the door at the scan midpoint, returns the card after one second and closes the door after five seconds. Stay Inserted keeps the door open while an accepted card remains in the reader. Security takes precedence over manual and redstone opening, including in vehicle mode. Centered doors additionally offer **Panel: Both**; near/far doors offer Off and On. Each face has its own physical card and return animation; either accepted card can open the door. Picking a configured door copies its security policy, without copying inserted cards.
