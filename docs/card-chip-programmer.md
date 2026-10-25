# Card / Chip Programmer

Place the programmer with two blocks of width and two blocks of height clear. The console faces the player. All four occupied cells open the same dialog and share one inventory. Breaking any occupied cell dismantles the console and releases its three retained items once.

The compact dialog has **Inventory** and **Journal** pages. Both pages keep the same three physical machine slots visible. Inventory shows the player inventory; Journal provides the title and page editor. Switching pages preserves the draft.

The dialog has three physical slots. **Master** accepts a company Access Card or Master Data Chip. **Input** optionally holds an Access Card or Data Chip to copy. **Output** holds the actual item to write: a blank item or one belonging to the master company. Input and output must be the same kind of item. Programming changes the existing output item; it does not create a card or chip from nothing, consume the master or remove the input. Items remain inserted after closing the dialog, saving, or completing a write. The three pads show their actual retained items.

With a company master inserted, programming a blank Access Card makes it an Access Card for that company. A branded destination must match the master. When copying without a master, use an unbranded destination; it inherits the source company. Input from a different company is rejected when a master is present.

Company Data Chips in the Creative inventory are **Master Data Chips**; the unbranded item is a **Blank Data Chip**. Their existing item identifiers and company variants are preserved, so existing readers continue to recognize them.

Select **Master** or **Journal** in the type row. Left-click or right-click cycles the choice. Writing a Master Data Chip requires a company master. Journals have a title of up to 40 characters and eight plain-text pages of up to 512 characters each. The title appears in the chip's item name.

Insert a Journal Data Chip in Input to load its title and pages for viewing or copying. To edit that same physical journal, leave Input empty and place the journal in Output instead; its current contents load into the editor. Use the page arrows to switch pages. Click the paper area to type, press Enter for a new line, and use Backspace, Delete, Home, End and the arrow keys to edit. Copy and paste work through the usual keyboard shortcuts; Select All selects the current page. Scroll the mouse wheel to inspect longer pages. **Save draft** saves the editor contents in the machine; **Program** writes them to the physical output chip. Draft edits autosave after a short pause. Escape and the inventory key save pending changes before closing the dialog. The input chip remains an unchanged source. An output-only journal is edited in place.

The ready, writing and complete screen appearances use the supplied console artwork. Programming updates the destination on activation and shows a short writing indication; repeated activation during that indication cannot start another write.

See [Access Card and Data Chip Mounts](data-mounts.md) for the readers used by doors, navigation tables and Core assemblies.
