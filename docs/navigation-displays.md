# Navigation display settings

[Flat Navigation Tables](multiblocks/navigation-table-flat/README.md) join into filled rectangles, including opposite-facing rows. Put their button panels around the outside to create one continuous inner screen. [Angled Navigation Consoles](multiblocks/navigation-table-angled/README.md) join sideways in a row facing the same direction.

![Opposing flat-table rows and an angled console, with a standalone mount for comparison](multiblocks/navigation-opposing-rows.png)

Use the Configurizer or sneak-right-click any module to edit the shared display. Choose a Programmable Block material or any screen/panel artwork, including the keyboard and half-screen designs. The shared **Custom...** picker also accepts sampled block materials. **Black** is the default. Animated artwork offers Static/Animated playback and Slow/Normal/Fast speed. The image spans the joined display; it does not repeat separately on each module.

![Navigation display dialog](multiblocks/navigation-display-dialog.png)

**Redstone** is disabled by default. With it disabled, the selected artwork is visible without a chip. Enable Redstone to require physical power on any member or ordinary Vandor Labs input channels; `0` means disconnected. Without power an enabled display is black. Every navigation module has **zero physical and channel output**, including when a chip is inserted. These settings do not add navigation or propulsion gameplay.

Every valid assembly shares one always-visible Data Chip Mount. Any Data Chip company or type is accepted. Inserting or removing the chip never changes display activation. New chips stay inserted until removed with an empty hand. The integrated dialog has no Security, company filter or return-mode controls. Older saved Security/filter/behavior selections are ignored; an already pending return still completes to its original owner, preserving the actual item and animation state.

The frame and chip use the standalone mount's native size, with half a pixel of clearance below the top of the front panel. Resizing moves the mount, chip and settings together. Settings are saved on every member so a split retains its display configuration; the physical chip remains one item on its owning component. Remove chips before merging occupied assemblies, since extra retained chips are dropped once. Standalone Data Chip Mount and Access Card reader settings remain independent; see [Data Chip Mounts](data-mounts.md).

Assemblies require a complete loaded rectangle of at most 256 modules. Holes, incomplete rows and unloaded boundaries do not create extra mount owners. Screens keep their configured orientation when carried structures rotate.

[Core-based machine display settings](machine-displays.md)
