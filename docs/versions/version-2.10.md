# Vandor Labs 2.10.0

This development version adds material choices, directional texture joins and
smaller Canopy item models. Forge 1.12.2 remains the reference target; NeoForge
1.21.1 supports these changes through its existing converted building families.
User testing and final release validation remain pending.

## Materials and joined artwork

The new **Floor** category contains thirteen finishes, including coloured tiles,
staggered tiles, limestone and terrazzo designs. **Doors / Utility** gains
Bathroom White, Modern Oak Windowed, Windowed Utility and Windowed White.
Existing saved texture choices keep their identities.

Filesystem Door artwork now mirrors correctly across double-door leaves,
matching the behavior of selected vanilla-block textures.

Material dialogs consistently call image mapping **Texture: Tile / Fit (Aspect)
/ Stretch**. Connected surfaces offer directional joining and an All/Even/Odd
filter for the neighbouring coordinate. The neighbour's own filter does not
override that choice. See [texture layout and joining](../texture-joining.md)
for examples, axis meanings and supported families.

The Duplifier's **Respect block joins** option restricts Connected Matching
Blocks to the clicked joined artwork. Leave it off to retain the existing
matching-region behavior.

## Canopy items

Canopy inventory, held, displayed and dropped items use prepared item geometry
and smaller display transforms. World Canopy geometry remains detailed. Item
meshes retain the authored materials and visible shell outline while avoiding
the cost and oversized appearance of drawing a complete multiblock in hand.

## Stationary Glass and Portholes

On Forge 1.12.2, eligible stationary Glass panes use ordinary translucent chunk
geometry. Other stationary panes, partially transparent emissive frames and
Porthole geometry reuse retained render groups, with separate ownership for
frames and panes. Joined holes remain holes, and each surface retains its
artwork, lighting and opacity. Moving, animated and fluid cases retain their
appropriate fallbacks. See [stationary Glass and Porthole rendering](../stationary-glass-rendering.md)
for the supported paths and diagnostic command.

## Coupler ownership recovery

Native arm-endpoint attachment and release retain their original physical before
images while renewing the receiving vehicle association. Interrupted transfers
recover through their durable journals, including copied capability data.
