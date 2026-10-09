# Canopies

Version 1.8 adds thirty glass and framed cockpit components to the Vandor Labs creative tab.
Place them facing the same direction as the player. Width runs to the player's right;
length runs rearward. Every cell of a larger component belongs to its placement anchor.
Placement requires the complete footprint to be free. Breaking any occupied cell removes
that component; survival harvesting returns one item. Pistons cannot move these assemblies.

| Component | Width × length × height | Recipe |
| --- | --- | --- |
| Regular Canopy Glass | 1 × 1 × 1 | Base recipe below |
| Angled Front Canopy Glass | 1 × 1 × 1 | Regular glass + 1 iron nugget |
| Long Angled Front Canopy Glass | 1 × 2 × 1 | Regular glass + 2 iron nuggets |
| Sliding Canopy | 1 × 2 × 1 | Regular glass + 3 iron nuggets |
| Rear-Hinged Canopy | 1 × 2 × 1 | Regular glass + 4 iron nuggets |
| Wide Sliding Canopy | 2 × 2 × 1 | Regular glass + 5 iron nuggets |
| Wide Rear-Hinged Canopy | 2 × 2 × 1 | Regular glass + 6 iron nuggets |
| Large Front Canopy Glass | 3 × 1 × 3 | Regular glass + 8 iron nuggets |
| Figher Canopy Glass | 2 × 1 × 2 | Regular glass + 1 gold nugget |

The conversion recipes are shapeless. The base recipe uses three glass blocks across
the top, Programmable Matter Ingots at both middle edges, and three more ingots across
the bottom. Figher Canopy Glass retains the kit's spelling. Its glass is only 1/16 block
deep, with a one-block-deep placement footprint.

## Fixed glass

Matching regular tiles at the same elevation and facing join sideways and lengthways.
Shared side glazing and perimeter rails disappear, leaving the roof and outer perimeter.
All sixteen neighbor combinations are supported, including irregular layouts. Matching
angled fronts join sideways; they also join a regular tile immediately behind their
complete footprint. Different slopes, facings and elevations retain their separate borders.

The short angled front rises steeply; the longer front uses the kit's shallower nose.
Large Front Canopy Glass narrows toward its forward face. Figher Canopy Glass provides
a thin stepped front window with radial framing.

## Additional fixed shells

The expanded collection adds Wedge (1 × 2 × 1), Longnose (1 × 3 × 0.75),
Crystal (2 × 2 × 0.75), Shuttle (2 × 3 × 1.25), and Bubble Wedge
(2 × 2 × 1.25) shells. Their roofs and sides remain fixed, with open bottoms.
Models taller than one block reserve an upper follower cell even when their actual
height is only 1.25 blocks.

Visor, Wide Visor, Extra-Wide Visor, and Tall Extra-Wide Visor are fixed front windows,
respectively 2 × 1 × 1, 3 × 1 × 1, 5 × 1 × 1, and 5 × 1 × 2 blocks.
They have no roof, rear or bottom. All standalone sizes occupy their complete footprint
and return one item when any occupied cell is harvested.

| Result | Shapeless ingredients |
| --- | --- |
| Wedge Canopy | Long Angled Front Canopy Glass + glass pane |
| Longnose Canopy | Wedge Canopy + glass pane |
| Crystal Canopy | 2 Regular Canopy Glass + glass pane |
| Shuttle Canopy | Crystal Canopy + glass pane |
| Bubble Wedge Canopy | Crystal Canopy + diamond |
| Visor Canopy | Regular Canopy Glass + iron bars |
| Wide Visor Canopy | Visor Canopy + glass pane |
| Extra-Wide Visor Canopy | Wide Visor Canopy + 2 Regular Canopy Glass |
| Tall Extra-Wide Visor Canopy | 2 Extra-Wide Visor Canopies |

## Composable visor sections

Build wider cockpit windows from **Visor Front**, **Visor Front Latch**,
**Visor Corner Left/Right**, and **Visor Side Left/Right**. Each section has a
one-block horizontal footprint and comes in normal or Tall form. Tall sections are
one continuous two-block-high mesh with an upper follower cell and no horizontal
center rail. Right-hand sections use their own authored geometry.

Place a left corner, one or more front sections, and a right corner in one row,
all at the same height and facing. Three-wide layouts use one front section;
five-wide layouts use three; seven-wide layouts use five. Extend either side
rearward with its corresponding straight side section. A seven-wide, three-deep
layout uses five fronts, two corners, and four sides. Replace one front with a
Front Latch for an amber accent. These sections supply the front and sides;
add your own roof and rear window.

Connection edges have no repeated vertical bars or glazing endcaps. Keep each joined
layout at one height and facing so its edges meet. Sections retain independent
ownership: breaking one section removes that section and its upper follower, while
neighboring sections remain in place. Composable sections have no opening animation.
The bundled `visor_assemblies.json` contains examples for widths 3, 5 and 7,
heights 1 and 2, and depths 1 and 3.

| Result | Shapeless ingredients |
| --- | --- |
| Visor Front | Regular Canopy Glass + glass pane |
| Visor Front Latch | Visor Front + redstone |
| Visor Corner Left | Visor Front + iron bars |
| Visor Corner Right | Visor Corner Left + redstone |
| Visor Side Left | Visor Front + stick |
| Visor Side Right | Visor Side Left + redstone |
| Any Tall section | 2 matching normal sections |

## Opening shells

Right-click any occupied cell or the moved shell to open or close it. Sliding shells move
two blocks rearward. Rear-hinged shells lift their front through 105 degrees around the
rear upper edge. Both use a 16-tick smooth motion and retain their stationary side mounts.
Saves preserve the target, progress, ownership and pairing; clients interpolate the pose.

Opening requires clear loaded space across the complete movement envelope. Sliding
canopies reserve two additional blocks behind them. Hinged canopies reserve up to 3.25
blocks vertically and 3.5 blocks rearward from the front anchor. Blocks or another canopy
in that space prevent opening. The original interior remains traversable when fully open;
collision and picking follow the moving surfaces into their destination cells.

Two closed narrow shells with the same mode, facing and height automatically join
side by side. The local-left anchor owns one wide shell, with one center mullion and
no duplicate inner walls or mounts. A third shell remains independent. A rear footprint
cell never counts as another assembly. Breaking either constituent removes its two cells,
unpairs the survivor and closes it. Wide items place a complete wide component directly.

The opening shells are one block high. Provide a footwell at least one block below the canopy
for a standing player; there is no bottom panel.

Dynmap uses tile-aware closed overview meshes, clipped into each occupied cell. It shows
the fixed joins and paired width; opening animation remains an in-game feature.

## Rendering and validation

The kit's authored face, UV and material data are stored in `canopy_meshes.json`.
The renderer draws stationary mounts and transformed shells once, with opaque framing
before sorted translucent glazing. Collision uses thin subdivided surface boxes, cached
by model, facing and pose. Collision and picking inspect loaded chunks only. Item models
are rebuilt on resource reload and fitted after GUI rotation to retain slot padding.

Run the Java 8 standard build and non-rendering checks, then the Forge client suite.
`testclient/test_viewscreen.sh --focus canopies` checks all thirty designs in four facings,
all regular neighbor masks, angled rear joins, pairing, clearance, saved state, removal,
survival placement and single-item harvesting,
open entrance collision, real open/close packets, offset picking, and every mesh's GUI
projection. It captures the closed and open shell, all designs, the thirty item icons, and a seven-wide Tall visor assembly. It also checks all twelve
provided visor layouts in four facings and verifies the Tall front has no center rail.
The test launcher uses software rendering; these checks do not establish hardware shader
compatibility or frame-rate performance.
