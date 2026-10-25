# Card / Chip Programmer

Place the programmer with two blocks of width and two blocks of height clear. The console faces the player. All four occupied cells share one inventory. Breaking any occupied cell dismantles the console and releases its three retained items once.

Right-click the actual **Master**, **Input**, or **Output** pad or socket with a suitable held item to insert one item without opening the dialog. Use an empty hand on an occupied pad to remove its item. Master accepts a company Access Card or Master Data Chip; Input and Output accept Access Cards and Data Chips. Clicking another console surface opens the standard dark dialog. Items stay inserted when the dialog closes or the world saves, and their actual models appear in the sockets.

Put the item to program in **Input** and leave **Output** empty. **Program** consumes that input item and moves the programmed result into Output. It retains the Master item. An occupied Output blocks programming and is never edited or overwritten. Remove the result or move it back into Input to program it again. Program is disabled while Input is empty.

For example, place a Vandor Master Data Chip in Master and a blank Access Card in Input. Program produces a Vandor Access Card in Output. A branded input must match an inserted company master. Already branded items retain their company when no master is inserted. Blank Access Cards and blank Master Data Chips need a company master. An unbranded Journal Chip can be written without one.

The compact dialog has **Inventory** and **Journal** pages, both showing the same three retained slots. Inventory shows the player inventory. **Done** closes the dialog. The **Type** selector applies only to Data Chips; it is disabled for Access Cards. Left-click selects the next type and right-click selects the previous type. Existing company Data Chips are Master Data Chips, and the unbranded item is a Blank Data Chip; their existing item identifiers and company variants remain compatible with readers.

Insert a Journal Chip in **Input** to display its title and contents on the Journal page. Journals have a title of up to 40 characters and eight plain-text pages of up to 512 characters each. The title appears in the chip's item name. Use the page arrows to paginate. Click the text area to type; Enter, Backspace, Delete, Home, End, arrow keys, Select All, copy and paste are supported. The mouse wheel scrolls longer pages. Editing applies only to the input chip's pending journal contents. Program writes those contents and moves the physical chip to Output. Putting a journal in Output never loads it into the editor or changes it.

Pending input journal edits save automatically after a short pause and before closing with Done, Escape or the inventory key. The console's large in-world screen is plain black; its keyboard uses the existing keyboard artwork with its original proportions.

See [Access Card and Data Chip Mounts](data-mounts.md) for readers used by doors, navigation tables and Core assemblies.
