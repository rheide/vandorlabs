# Texture layout and joining

The material dialogs use **Texture** for image layout:

| Choice | Appearance |
| --- | --- |
| Tile | Repeat the image at its normal block scale. |
| Fit (Aspect) | Preserve the image aspect ratio using the family's fitted mapping. |
| Stretch | Fill the surface without preserving the image aspect ratio. |

Where a family supports connected artwork, **Join** selects which neighbouring
blocks may participate. Matching material, orientation, surface geometry and
other existing family rules still apply.

| Join | Candidate direction |
| --- | --- |
| Off | Keep this block separate. |
| On | Consider every supported direction on the surface. |
| Vertical | Consider the surface's rising and falling neighbours. |
| Horizontal | Consider neighbours at the same height. |
| North/South | Consider neighbours along the Z axis. |
| East/West | Consider neighbours along the X axis. |

**Neighbours: All / Even / Odd** filters candidate coordinates for the four
directional choices. Vertical checks Y, North/South checks Z, and East/West
checks X. Horizontal checks the horizontal axis along which the neighbour lies.
A sloping surface may also step in depth; its existing geometry rules determine
whether the two surfaces meet.

The filter belongs to the block being configured. For example, a block at X=3
using **East/West** and **Even** can join matching neighbours at X=2 and X=4.
Those neighbours may use All, Even or Odd themselves. An Even block at X=2 does
not consider X=1 or X=3. Negative coordinates follow the same rule: -2 is even
and -3 is odd. The candidate's filter does not override the owner's decision.

Left-click an option to advance; right-click to select its previous value.
The neighbour filter is inactive for On and Off. Existing worlds retain their
previous joining behavior until these settings are changed.

Directional controls apply to connected programmable cubes, walls, Portholes
and Glass. Slab and Stair material dialogs retain their existing independent
texture layout controls; selecting Fit does not introduce a new joined mesh.

## Duplifier selection

Enable **Connected Matching Blocks**, then **Respect block joins**, to keep a
connected application within the clicked joined artwork. The clicked block's
direction and coordinate filter guide discovery; existing matching and edit
permissions still apply. Matching textures on another unjoined surface do not
receive the copied settings. Unsupported joining families remain a single
logical target with this option enabled.

Leave Respect block joins off to retain the original matching-region selection,
including supported edge and corner contacts. Changing this selection option
does not change which saved properties are enabled for copying.
