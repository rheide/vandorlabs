# Devices

The creative block list includes four ready-to-place AC finishes:

| Entry | Placed block |
| --- | --- |
| Indoor AC (Dark Cyan) | Programmable Slab |
| Indoor AC (Silver) | Programmable Slab |
| Outdoor AC (Dark Cyan) | Programmable Block |
| Outdoor AC (Silver) | Programmable Block |

These entries are configured stacks of the existing programmable items, with
six individual face textures. They do not register separate AC block or item
identities. Place them facing the player; indoor units support the normal upper
and lower slab placement and slab combination. Use the normal Configurizer to
change a face or any other programmable-block setting. Mining preserves the
appearance on the ordinary programmable item.

Printer and Scanner are solid blocks with four horizontal orientations. Each has a Chip Mount at the upper-left of its front and a half-size Push Button at the upper-right. Right-click the housing, or use the Configurizer, to open the standard inventory dialog.

The Printer accepts a Data Chip, ink sacs and paper. Press its front button or **Print** in the dialog. Each successful print uses one ink sac and one paper, keeps the chip, and ejects the result upward when the top port is unconnected. Leave the space above clear for ejection.

| Chip | Printed item |
| --- | --- |
| Journal | Written Book with the journal text |
| Navigation | Filled Map at scale 0, with prefilled navigation terrain pixels |
| Catalog | Written Book containing complete construction recipes that fit |
| Vehicle | No print; inputs remain untouched |

Print Navigation Chips in their recorded dimension. Map generation uses the same seed terrain as the navigation displays without loading chunks; scanned map pixels take precedence. Vanilla map colors approximate the navigation display palette.

The Scanner has an upward-facing item frame built into its top. Place one Filled Map, Written Book or Enchanted Book in it, insert a Data Chip, then press the front button or **Scan**. The original document remains in the frame. An empty-hand click on the frame retrieves it.

Maps program Navigation Chips with their center, dimension, scale and original 128×128 pixels. Navigation displays can show the complete saved square without a linked Survey Dish or Sentinel Array, including the corners of scale-4 maps. Original unexplored pixels remain blank. Written Books produce Journal Chips containing up to 4,096 characters of plain text; Enchanted Books produce random journal text shown in the enchanting alphabet. Scanning replaces the chip's previous document type and keeps its company design.

## Redstone and item ports

Open the **Redstone** tab and choose **Manual**, **Redstone On** (one action
when power arrives), or **Redstone Off** (one action when power ends).
The front button and inventory dialog still work in every mode. Use physical
redstone or enter receiving channels in the tab. A steady signal runs no
additional cycles.

| Device | Inputs on both sides, viewed from the front | Output |
| --- | --- | --- |
| Printer | Data Chip, ink sacs, paper | Finished book or map from the top |
| Scanner | Data Chip and an original document | Programmed Data Chip from the back |

The Scanner keeps the original document in its top frame. With no connected
output, the programmed chip stays in the front mount. A connected outlet can
hold one finished item in the **Output** slot while the route is blocked.
Clear the route or take the item from that slot to make room for the next job.

All supplied artwork retains its native 64×64 size. The appliance face textures
also appear in the **Devices** category of the regular texture picker and
[texture browser](texture-browser/index.html).

![Creative appliance inventory previews](appliances/items.png)
