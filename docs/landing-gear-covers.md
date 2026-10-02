# Landing gear alignment and covers

## Extra Large 2×2

The Landing Gear size selector includes **Extra Large (2×2)** alongside the original centered Extra Large option. It uses the same wheel model, shifted half a block along positive world X and Z. The root block occupies the lower-coordinate corner of its two-by-two footprint. Changing the gear's facing rotates the wheel around that shifted center.

The mod reserves the matching two-by-two cells for the mount and moving wheel. The original Extra Large option keeps its three-by-three reservation. Both retain the extra cell of depth needed by the large wheel. Obstructions reject a size change before the configuration or reservations change. Alignment survives saves, configured items and Duplifier copies; inventory icons use the same Extra Large artwork.

## Adjacent trapdoor covers

The Programmable Trapdoor movement selector now cycles through Rotating, Sliding and **Cover**. Cover mode keeps the tile in its mounting cell while the closed leaf spans the adjacent cell in its facing direction. Opening slides it back into its own mounting cell. Thus a cover can cross the gear shaft without occupying a cell the gear reserves.

For a simple cover:

1. Place a normal Programmable Trapdoor beside the shaft, one block below the gear root.
2. Configure **Movement: Cover** and point its facing into the shaft. The normal facing rule still applies; verify the closed leaf lies over the intended cell.
3. Choose Bottom, Middle or Top to set the cover's height within that cell. Leave clear space in its mounting cell for the open leaf.
4. Set **Redstone: On** for automatic closure when the gear is retracted. A shared channel can also open it independently.

The gear automatically opens nearby loaded covers whose closed cell belongs to its reserved footprint. Covers stay open while the wheel is extended or still retracting, then reevaluate their redstone setting when it has fully retracted. Turning off power therefore does not close the cover through a moving wheel. Disabled redstone mode leaves manual closure available after retraction.

Cover mode applies to normal horizontal trapdoors and uses individual cover mounts rather than joined rotating/sliding groups. Add separate mounts for larger openings, keeping each mount outside the gear's reservation. The mod preserves them during reservation cleanup. Updates read only loaded cells and run when the gear changes target or completes motion; they do not load chunks or poll idle gears.

Cover settings survive configured items and Duplifier copying. The Configurizer uses the same Creative-only configuration gate as the other programmable menus.
