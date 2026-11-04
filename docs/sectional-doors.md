# Sectional doors

Sectional Door sections form a sliding door with configurable travel. The default rises and bends onto an overhead track. Equal-height neighbouring columns facing the same way operate together. Each column may contain 1–32 sections; a linked door may contain up to 32 columns.

## Construction

1. Build a level sill across the opening. Leave room above and behind the header for the raised panels. A five-section column extends approximately five blocks behind its closed plane when open.

   ![Level sill before placing the door](images/garage-build-1-foundation.png)

2. Face the opening and place the first column from bottom to top. A section placed against a section above or below inherits its facing. The top section supplies its own narrow header.

   ![First five-section column](images/garage-build-2-column.png)

3. Place adjacent columns to the same height and facing. The example is nine columns wide and five sections high. Columns of a different height remain separate.

   ![Completed nine by five sectional door](images/garage-build-3-closed.png)

4. Right-click a section to toggle the linked door. The panels rise, turn over the header and retract overhead. Once open, right-click any visible panel to close the door. The narrow border follows the selected slide edge.

   ![Door raised overhead](images/garage-open.png)

5. Use the Configurizer, or sneak-right-click, to open the dedicated configuration dialog. **Door** selects **Slide: Up / Down / Left / Right**, **Angle: Perpendicular / Straight**, **Speed: Slow / Medium / Fast**, and **Position: Middle / Near / Far**, beside the artwork picker. Perpendicular turns the panels behind the opening; Straight keeps them in the door plane and leaves a one-pixel edge exposed when open. Artwork, travel, position and activation settings apply to the linked door. Right-click an option to cycle backwards.

   ![Sectional Door mechanism settings](images/sectional-mechanism-settings.png)

6. In Redstone, enter the usual channel list and choose whether to accept local redstone. Choose **Activation: Manual / Redstone ON / Redstone OFF**, using the same selector as Programmable Doors. Manual allows hand operation. Redstone ON opens while any enabled input is powered; Redstone OFF reverses that condition. Both redstone modes block manual opening and closing. Existing saved doors retain their former combined manual/redstone behavior until an activation mode is selected.

   ![Sectional Door Redstone Channels](images/sectional-redstone-settings.png)

Craft eight sections from six iron ingots, two pistons and one redstone dust. Mining the top section promotes the remaining top section and preserves its column settings. Changing a door requires its complete linked structure to be loaded and editable.

## Rendering and saved state

Closed, settled plain tiled panels render in Minecraft's chunk geometry. Window panels and other layout modes retain cached per-column geometry. Moving panels share the atlas batch and reuse bounded geometry templates at a 120 Hz animation cadence. Raised panels use the same template cache. The cache retains vertex-format information and clears on atlas reload or a format change.

The client retains closed fallback panels until all affected chunk sections finish uploading their replacement mesh. Off-screen sections are queued asynchronously through Minecraft's chunk dispatcher, with duplicate sections combined and at most eight submissions per client tick. Opening begins after the closed terrain panels are removed; closing switches back to terrain only after the closed panels have uploaded. Cosmetic animation uses a pause-aware local clock. Repeated or older motion packets do not restart it.

The server owns collision and the saved motion timeline. Sections remain native world blocks throughout motion; opening never transfers blocks or inventories. Only the top tile of each column advances motion, and only those owners send motion updates. Channel and topology work runs after chunk loading, with bounded loaded-block searches.

## Panel artwork

Open **Door** to select general materials, Frame, Trapdoors, Doors or Large Doors artwork. Use **Custom...** to sample a block from your inventory without consuming it. Custom artwork is not reflected across neighbouring segments. **Texture: Tile / Stretch / Fit (aspect)** uses the same layout choices as Programmable Door. Selection applies to every section in the linked door. The original dark slatted panel remains the default; **Industrial → Sectional Panel** and **Sectional Panel Light Grey** expose its two finishes to other programmable blocks too. The original artwork is the standalone `garage/garage_panel.png` texture. Changing artwork preserves the door’s channels, motion and saved section ownership.

![Sectional Door artwork and window controls](images/sectional-door-window-settings.png)

## Window segments

1. Configure the specific block you want to change. Opening shape, Window Tile/Fit and glass settings belong to that block; selecting artwork or Texture Tile/Stretch/Fit still applies across the linked door. To make a window row, configure each block in that row. On **Door**, change **Segment: Solid** to **Window**. Choose **Hexagon**, **Octagon**, **Square** or **Round**, using the same outlines as Programmable Porthole.
2. Choose **Window: Tile** to fit the opening to the selected section, or **Fit** to take that section’s slice of an opening spanning the linked door. Other sections keep their own opening settings. Each panel still moves independently along the selected path.
3. Open the **Glass** tab to choose the glass shade or replacement material and its tint. The Glass tab appears for Window segments and Frame artwork. Selecting glass for a Frame enables its square opening on the configured block. All tabs keep the same dialog dimensions.

![Round window fitted across linked sections](images/sectional-round-fit.png)

![Individual round windows tiled across the door](images/sectional-round-tile.png)

![Sectional Door glass selection](images/sectional-glass-settings.png)

Keep enough space above, below or beside the opening for Straight travel, and behind the opening for Perpendicular travel. A sideways door travels by its linked width; an upward or downward door travels by its height.

Better Builder’s Wands can extend the top row. Extending the top row updates each column’s height and header while preserving its settings.

## Contained water

Use a filled or empty bucket on either side to store or remove its fluid independently. Stored sides survive saving and door movement. Opening connects water across the aperture and supplies ordinary flowing water on the opposite side; closing drains the outlets created by the door while preserving its stored water. Middle/Near/Far placement also moves the fluid partition. Water remains in the native door cells as the panels retract.
