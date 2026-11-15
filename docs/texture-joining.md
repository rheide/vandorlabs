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

Every compatible neighbour in the selected direction participates. A sloping
surface may also step in depth; its existing geometry rules determine whether
the surfaces meet. Older Even/Odd selections now behave as All while retaining
the saved join direction.

Left-click an option to advance; right-click to select its previous value.

Directional controls apply to connected programmable cubes, walls, Portholes
and Glass. Slab and Stair material dialogs retain their existing independent
texture layout controls; selecting Fit does not introduce a new joined mesh.

## Duplifier selection

Enable **Connected Matching Blocks**, then **Respect block joins**, to keep a
connected application within the clicked joined artwork. The clicked block's
join direction guides discovery; existing matching and edit
permissions still apply. Matching textures on another unjoined surface do not
receive the copied settings. Unsupported joining families remain a single
logical target with this option enabled.

Leave Respect block joins off to retain the original matching-region selection,
including supported edge and corner contacts. Changing this selection option
does not change which saved properties are enabled for copying.

**Ignore directionality** allows connected matching across different placement
facings, axes, rotations, hinges and halves. Other matching settings and edit
permissions still apply. Hover over the three selection options for help.
