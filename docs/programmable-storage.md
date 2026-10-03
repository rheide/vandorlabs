# Programmable Storage

Programmable Storage holds **27 stacks**, the same capacity as a single Minecraft
chest. Each block has its own inventory, even when placed beside another one.
Its front faces you when placed.

Craft one with a **chest + Programmable Block** in any arrangement. Right-click
to open its inventory. Shift-click transfers stacks between storage and your
inventory. Hoppers and Forge item-handler automation can insert and extract
from all sides; comparators report how full the storage is.

Shift-right-click in Creative mode, or use the Configurizer, to open the shared
[categorized texture picker](unified-materials.md). New blocks default to
**Cabinet**, the first Storage set alphabetically. The ten Storage choices each
have matching front, side and top artwork. The back uses the side, and the
underside uses the top. Other categories, filesystem textures and Custom block
samples apply the selected finish to all six faces.

The Storage category is available in every shared material picker. Other
programmable blocks use the selected set's front artwork as their material.

Breaking storage drops its contents separately from the configured block.
Picking the block, copying its appearance with the Duplifier, or extending it
with Better Builder's Wands does not duplicate the inventory. Changing its
texture preserves the contents. Both contents and appearance survive saving.

The processed artwork consists of thirty 140×140 PNGs. Each supplied horizontal
sheet was split into top, side and front panels, reduced on a 70px grid,
lightly sharpened, then enlarged exactly 2× with nearest-neighbor sampling.
This preserves the supplied artwork with crisp pixel edges. The processed
files are also exported separately; the originals remain unchanged.

Dynmap shows the default Cabinet finish and block orientation. Configured
storage finishes are rendered in Minecraft.

![Storage material sets](images/gallery/storage/sets.png)

![27-slot storage inventory](images/gallery/storage/inventory.png)

![Shared storage texture picker](images/gallery/storage/picker.png)

![Default and configured storage hotbar icons](images/gallery/storage/hotbar.png)

To refresh only these live gallery captures:

```bash
testclient/generate_gallery.sh --focus storage
```
