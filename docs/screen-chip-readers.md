# Screen chip readers

Select **Chip** from the **Display** texture list, immediately below Vehicle Status:

- **Programmable Diagonal Half Console:** adds a small front tray and a sideways chip reader in front of the incline.
- **Programmable Diagonal Screen:** adds a sideways reader below the display. The display keeps its original area.
- **Programmable Half Console:** adds a sideways reader at the right of the lower deck. The left part of the original controls texture is cropped at its original scale. Display is the first tab, followed by Controls.

The built-in slot appears only while Chip is selected. Right-click the slot with a Data Chip to insert it; right-click it with an empty hand to remove it. Survival insertion transfers one item. Selecting different artwork hides the reader and retains its chip; select Chip again to retrieve it.

The screen shows Journal contents, Vehicle Chip names and rotating shape previews, or the blank/master chip state. For Journals, click the left or right side of the displayed content to move to the previous or next page. This does not modify the chip.

The regular **Programmable Screen** uses an external **Data Chip Mount** instead. Attach the mount to any face, with its back against the screen, then reopen Display to select Chip. The screen reads the attached mount; it has no built-in slot. If several mounts are attached, the first one in Down, Up, North, South, West, East order is used. Removing that mount selects the next attached mount, or shows the empty-chip prompt.

Built-in readers retain their chip data through saving, configured block drops, vehicle capture and parking. Copy Controller designs include the inserted chip; Survival copying requires that exact chip in the material supply. A Duplifier copies the display settings without copying the physical chip.

All reader insertion motion uses the shared pause-aware chip animation. The sideways readers move along their local horizontal axis, including rotated and upper diagonal placements.
