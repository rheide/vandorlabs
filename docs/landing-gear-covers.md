# Landing gear alignment and covers

## Extra Large 2×2

The Landing Gear size selector includes **Extra Large (2×2)** alongside the original centered Extra Large option. It uses the same wheel model, shifted half a block along positive world X and Z. The root block occupies the lower-coordinate corner of its two-by-two footprint. Changing the gear's facing rotates the wheel around that shifted center.

The mod reserves the matching two-by-two cells for the mount and moving wheel. The original Extra Large option keeps its three-by-three reservation. Both retain the extra cell of depth needed by the large wheel. Obstructions reject a size change before the configuration or reservations change. Alignment survives saves, configured items and Duplifier copies; inventory icons use the same Extra Large artwork.

## Adjacent trapdoor covers

The Programmable Trapdoor Movement button offers **Rotate into next block** and **Slide into next block** in addition to ordinary Rotating and Sliding. Both next-block choices keep the owning tile outside the shaft while the closed leaf covers the adjacent shaft cell. Rotation folds it upright into its mounting cell; sliding brings it back horizontally. A closed sliding cover overlaps the owning mounting cell by one pixel; its far edge stays inside the covered cell. There is no separate Closed leaf control.

For a rotating gear cover:

1. Place an individual normal Programmable Trapdoor beside the shaft, one block below the gear root. If it joins another trapdoor, Next block is disabled; configure each independent mount before placing the next one. The trapdoor tile must be outside the reserved shaft; placing it in the gear block itself is unnecessary.
2. Set **Movement: Rotate into next block**, and use **Hinge** to point north/east/south/west into the shaft. That direction identifies the covered neighboring cell directly.
3. Choose Bottom, Middle or Top for the cover's height. The open leaf folds vertically into the mounting cell, so leave enough vertical space there. The selection outline follows the offset closed leaf.
4. Set **Redstone: On** for automatic closure when the gear is retracted. A shared channel can also open it independently.

For a 2×2 shaft whose root is at `(x,y,z)` and occupies X `x..x+1`, Z `z..z+1`, put the mounts at `(x-1,y-1,z)`, `(x-1,y-1,z+1)`, `(x+2,y-1,z)`, `(x+2,y-1,z+1)`. The west mounts choose **Hinge: east**, and the east mounts choose **Hinge: west**. All four choose Rotate into next block. Each closed leaf then covers one reserved shaft cell; rotating opens it into its own outside mount.

The gear automatically opens nearby loaded covers whose closed cell belongs to its reserved footprint. Covers stay open while the wheel is extended or still retracting, then reevaluate their redstone setting when it has fully retracted. Turning off power therefore does not close the cover through a moving wheel. Disabled redstone mode leaves manual closure available after retraction.

Next-block placement applies to normal horizontal trapdoors and uses individual mounts rather than joined rotating/sliding groups. For a 2×2 opening, put two mounts along each of the west and east edges, facing inward: four independent covers fill the four shaft cells. Keep each mount outside the gear's reservation. The mod preserves them during reservation cleanup. Updates read only loaded cells and run when the gear changes target or completes motion; they do not load chunks or poll idle gears.

Movement, next-block placement, texture layout and hinge direction survive configured items and Duplifier copying. Shift-right-click configuration requires Creative mode; the Configurizer also opens these settings in Survival.

Landing gear retains manual extension across world/chunk reloads when its redstone input is unchanged. A subsequent power edge still applies the chosen On/Off mode; see the [load-state fix](landing-gear-load-fix.md).
