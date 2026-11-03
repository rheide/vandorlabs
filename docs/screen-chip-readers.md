# Screen chip readers

Select **Chip** from the **Display** texture list, immediately below Vehicle Status:

- **Programmable Diagonal Half Screen:** adds a small front tray and a chip reader opening toward the player in front of the incline.
- **Programmable Diagonal Screen:** adds a sideways reader below the display. The display keeps its original area.
- **Programmable Console:** adds a chip reader opening toward the player at the right of the lower deck, cropping the remaining controls at their original scale.
- **Programmable Half Console:** adds a reader opening toward the player at the right of the lower deck. The left part of the original controls texture is cropped at its original scale. Display is the first tab, followed by Controls.

The built-in slot appears only while Chip is selected. Right-click the slot with a Data Chip to insert it; right-click it with an empty hand to remove it. Survival insertion transfers one item. Selecting different artwork hides the reader and retains its chip; select Chip again to retrieve it.

The screen shows Journal contents, Vehicle Chip names and rotating shape previews, or the blank chip state. Vehicle Chip models use the chip company colour: Pinnacle gold, Ashiro coral red, Paladyne lime green, Vektor violet, Seyr orange and Vandor cyan. Generic chips use bright silver. Company Master Data Chips show their matching Corp logo. Slow, Normal and Fast control Vehicle Chip rotation; changing speed preserves the current angle. For Journals, click the left or right side of the displayed content to move to the previous or next page. This does not modify the chip.

The regular **Programmable Screen** uses an external **Data Chip Mount** instead. Attach the mount to any face, with its back against the screen, then reopen Display to select Chip. The screen reads the attached mount; it has no built-in slot. If several mounts are attached, the first one in Down, Up, North, South, West, East order is used. Removing that mount selects the next attached mount, or shows the empty-chip prompt.

Built-in readers retain their chip data through saving, configured block drops, vehicle capture and parking. Copy Controller designs include the inserted chip; Survival copying requires that exact chip in the material supply. A Duplifier copies the display settings without copying the physical chip.

All reader insertion motion uses the shared pause-aware chip animation. Console readers insert from the bottom edge toward the display, including rotated and upper diagonal placements. The Diagonal Screen retains sideways insertion.

Adjacent regular Screens share their attached reader in Chip mode. Selecting Chip applies it across the joined artwork rectangle; the view stretches across the complete screen. One mounted reader is sufficient for the rectangle. If several readers are attached, the first occupied reader in the shared layout supplies the view.
