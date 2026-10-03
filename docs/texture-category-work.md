# Texture category and storage work

Performance work committed separately as `d109a3c9`. Existing numeric choices and source paths are retained; deleted entries are hidden from pickers so saved worlds retain their artwork. The spelling errors “Fual” and “Greem” refer to the existing Fuel Port Round and Green Console.

## Owner category request

```text
Keep existing items in the category they are already in, below are instructions for moves and renames.


# Example
- Don't show this category at all if it only contains the default sample panel

# Tech
Server Compute Rack
Server Data Storage
Server Network Core
Fuel Port Dual
Fual Port Round
Battery Cell Bank
Battery Charging Dock
Battery Power Buffer
T1 Circuit panel
T1 Data Cores
T1 Greebled Panel 1
T1 Greebled Panel 2
T2 Blue Node
T2 Blue Socket
T2 Greem Console
T2 Green Core
T2 Switch Bank

# Hull
Rename all preexisting names (e.g. R1 C2, T1 R1 C1) to Dark Hull 1, 2, 3 etc.
Dark T1 R1 C3 -> Delete
T1 Gravity Floor
T1 Metal Floor
T1 Perforated Deck
T1 Nano-Fiber Hull
T2 Ivory Hatch 1
T2 Ivory Hatch 2
T2 Gray Panel 1
T2 Gray Panel 2
T2 Braced Hull
T2 Dark Socket
T2 Green Panel
T2 Weathered Hull 1
T2 Weathered Hull 2
T2 Worn Bulkhead
T2 Industrial Frame

# Hull Plating
Delete all of these

# Materials
Delete Ribbed Padding, Seamed Padding, Stitched Padding, Wall Vent
(list of moves to Panels below)

# Panels
Bolted Wall Plate
Dark Wall Panel
Framed Wall Pipes
Industrial Block
Industrial Grate
Industrial Trim
Light Industrial Panel
Light wall Panel
Matter (+Amber, Cyan, Red)
Ribbed Wall
Wall Pipes
T1 Biomech Block 1
T1 Biomech Block 2
T1 Composite Wall 1
T1 Composite Wall 2
T1 Heavy Bulkhead 1
T1 Heavy Bulkhead 2
T1 Thermal Shield 1
T1 Thermal Shield 2
T2 Microchip
T2 Octagon Plate
T2 Scaled Armor
T2 Twin Panels

# Storage
Rename Storagecrate to Storage Crate

# Texture Pack 1
Remove this category, blocks moved to other categories
Drop the T1 prefix from all of these blocks when moving

# Texture pack 2
Remove this category, blocks moved to other categories
Drop the T2 prefix from all of these blocks when moving
T2 Blue Hex 1 and 2: move these to Programmable Light, 1 is off, 2 is on
T2 Amber Hex: move this to Programmable Light, use the image skill to create an "On" state for this that is similar to the transition between Blue Hex 1 and 2
```

## Additional requests

> One more thing, I added more textures for our Programmable Storage block in ~/LLMShareDrive/bins. It's 4 new combinations of textures for front, side and other faces. They are pre-split but need resizing to 140x140 to match the rest of the mod.

> After you're done with the texture moves I would like you to investigate if it's possible to create animated gifs for our documentation. For now I just want you to demonstrate whether it's possible or not by animating one of the already documented doors in the docs/ folder.

## Completion

- [x] Apply categories, names and picker removals, preserving saved choices.
- [x] Add paired Blue/Amber Hex light artwork.
- [x] Import four storage sets and verify all twelve faces are 140×140.
- [x] Build and run relevant live client checks; refresh documentation images.
- [x] Commit texture work separately.
- [ ] Demonstrate a documented door as an animated GIF after texture work.

## Evidence

- Java 8 standard build and non-rendering checks passed.
- All 227 pre-existing additional choice IDs, indices and face paths remain unchanged. Five choices append: four Storage sets and Amber Hex On.
- Twelve new PNGs are 140×140; their source SHA-256 hashes match the unchanged files in `~/LLMShareDrive/bins`. Face assignments are recorded in `texture-packs/additional/storage-bins-sources.json`.
- `testclient/render-run.VMPz3A`: focused Storage suite passed, including all fourteen live world models, face atlases, save/load, inventory, automation, comparator, copying, GUI and hotbar checks. Five documentation images refreshed.
- Filesystem checks passed for a hidden sole sample, visible populated Example category, stable saved identities and missing-peer fallback.
- Live menu checks passed for moved categories, hidden saved choices, and paired Hex light manual/redstone state and persistence.
- `testclient/render-run.392gKU`: sixteen dialog captures and shared picker layout checks passed, including both eight-style light pickers and Custom reopen behavior. The outer shell returned 143; the client continued and shut down normally. The focused validator was run directly and passed before exporting images.
- Standard `build/libs/vandorlabs-1.3.jar` contains the menu metadata, all twelve new 140×140 storage faces and the 64×64 Amber On artwork. No archive texture JAR or external build copy was produced.
