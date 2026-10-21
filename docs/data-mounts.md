# Data Card and Data Chip Mounts

Place a mount against a solid face, including a floor or ceiling. It occupies a small area around the token rather than the entire block face. Right-click with a matching Data Card or Data Chip to insert one item. Click with an empty hand to remove it. The two mount types accept their corresponding unbranded and six company variants.

Use the Configurizer or sneak-right-click to open the dialog. The inserted-item slot holds the physical token. The seven design slots below it form an allow-list: click a design to toggle it, or click there with a carried token to add its design. Filter selection never consumes the carried item. Enable **Any type** to accept every design in that mount's card or chip family.

Choose **Keep inserted: continuous signal**, **Keep inserted: one-second pulse**, or **Insert to toggle, then return item**. All modes provide ordinary redstone and the configured output channels. In the two Keep modes, a token outside the allow-list can sit in the mount but cannot activate its output; removing a token stops the output. Empty mounts show light grey for Any, or coloured areas for the allowed company designs.

Toggle mode slides an accepted token into the reader, switches the saved output on or off at the scan midpoint, then slides it back out and returns it to the inserting player's inventory after one second. Right-click with a token, place it in the dialog's inserted slot, or shift-click it from the dialog inventory. One real item is borrowed even in Creative mode. Unaccepted designs are left untouched. The output stays in its selected state after the token returns and across world reloads; inserting another accepted token switches it again. Changing the allow-list or behavior clears the saved toggle state.

An item awaiting return cannot be extracted by another player or automation. If the player's inventory is full or the player disconnects, the mount retains the item and retries while loaded. Its saved return owner and scan progress survive reloads and vehicle conversion. Breaking the mount releases the held item once.

The inserted item retains its complete item data. Breaking a mount releases the inserted token separately. Picking the block does not copy its contents.
