# Canopies

Version 1.8 adds glass and framed cockpit components to the Vandor Labs creative tab.
Sliding, rear-hinged and angled-front canopies place with their opening facing away from the
player. Other components retain their existing orientation. Width runs to the
component's right; length runs rearward. Every cell of a larger component belongs to its placement anchor.
Placement requires the complete footprint to be free. Breaking any occupied cell removes
that component; survival harvesting returns one item. Pistons cannot move these assemblies.

| Component | Width × length × height | Recipe |
| --- | --- | --- |
| Canopy Glass | 1 × 1 × 1 | Base recipe below |
| Angled Front Canopy Glass | 1 × 1 × 1 | Canopy Glass + 1 iron nugget |
| Long Angled Front Canopy Glass | 1 × 2 × 1 | Canopy Glass + 2 iron nuggets |
| Sliding Canopy | 1 × 2 × 1 | Canopy Glass + 3 iron nuggets |
| Rear-Hinged Canopy | 1 × 2 × 1 | Canopy Glass + 4 iron nuggets |
| Wide Sliding Canopy | 2 × 2 × 1 | Canopy Glass + 5 iron nuggets |
| Wide Rear-Hinged Canopy | 2 × 2 × 1 | Canopy Glass + 6 iron nuggets |
| Large Front Canopy Glass | 3 × 1 × 3 | Canopy Glass + 8 iron nuggets |
| Figher Canopy Glass | 2 × 1 × 2 | Canopy Glass + 1 gold nugget |

The conversion recipes are shapeless. The base recipe uses three glass blocks across
the top, Programmable Matter Ingots at both middle edges, and three more ingots across
the bottom. Figher Canopy Glass retains the kit's spelling. Its glass is only 1/16 block
deep, with a one-block-deep placement footprint.

## Fixed glass

Canopy Glass tiles at the same elevation join sideways and lengthways, regardless
of the direction each tile was placed.
Shared side glazing and perimeter rails disappear, leaving the roof and outer perimeter.
All sixteen neighbor combinations are supported, including irregular layouts. Matching
angled fronts join sideways; they also join a regular tile immediately behind their
complete footprint. Angled fronts retain their slope and elevation requirements. Canopy Glass can join
their rear edge even when the glass tile faces another direction.

The short angled front rises steeply; the longer front uses the kit's shallower nose.
Large Front Canopy Glass narrows toward its forward face. The retired Fighter canopy provides
a thin stepped front window with radial framing.

## Additional fixed shells

The collection includes Crystal (2 x 2 x 0.75), Shuttle (2 x 3 x 1.25),
Wide Shuttle (3 x 3 x 1.25), and Bubble Wedge (3 x 3 x 1.25) shells.
Their roofs and sides remain fixed, with open bottoms.
Models taller than one block reserve an upper follower cell even when their actual
height is only 1.25 blocks.

Visor, Wide Visor, Extra-Wide Visor, and Tall Extra-Wide Visor are fixed front windows,
respectively 2 × 1 × 1, 3 × 1 × 1, 5 × 1 × 1, and 5 × 1 × 2 blocks.
They have no roof, rear or bottom. Each has a one-pixel-thick border along its complete
upper outline. Standalone Visor sizes and the 3 � 3 Bubble Wedge center on the clicked
block, including the two-wide Visor's half-block alignment. New two-wide Visors reserve
three cells for that centered outline; older placed Visors retain their original two-cell
footprint and alignment. All standalone sizes occupy their complete footprint
and return one item when any occupied cell is harvested.

| Result | Shapeless ingredients |
| --- | --- |
| Crystal Canopy | 2 Canopy Glass + glass pane |
| Shuttle Canopy | Crystal Canopy + glass pane |
| Wide Shuttle Canopy | Shuttle Canopy + glass pane |
| Bubble Wedge Canopy | Crystal Canopy + diamond + glass pane |
| Visor Canopy | Canopy Glass + iron bars |
| Wide Visor Canopy | Visor Canopy + glass pane |
| Extra-Wide Visor Canopy | Wide Visor Canopy + 2 Canopy Glass |
| Tall Extra-Wide Visor Canopy | 2 Extra-Wide Visor Canopies |

## Opening shells

Right-click any occupied cell or the moved shell to open or close it. Sliding shells move
two blocks rearward. Rear-hinged shells lift their front through 105 degrees around the
rear upper edge. Both use a 16-tick smooth motion and retain their stationary side mounts.
Saves preserve the target, progress, ownership and pairing; clients interpolate the pose.

Opening does not reserve empty space behind the canopy. Sliding and rear-hinged
shells pass through rear bodywork, so a cabin roof or hull behind them does not prevent
opening. Placement still requires the original footprint to be free. The original interior
remains traversable when fully open; collision and precise face picking follow the
moving surfaces into their destination cells. Selecting any visible surface highlights
the complete posed assembly instead of one small collision fragment.

Place two or three closed Sliding or Rear-Hinged Canopies side by side at the same height and facing
to form a two- or three-block-wide shell. Placement order does not matter. For example,
three adjacent Sliding Canopies create one three-wide, two-deep canopy; right-click
any constituent or the moved shell to operate the complete assembly. Rear-Hinged
Canopies also support three constituents. The local-left anchor draws the shared shell, without duplicated
inner walls or mounts. A fourth constituent stays independent.

Breaking a constituent removes only its own two cells, closes the survivors, and
reconnects adjacent survivors where possible. Breaking the middle of a three-wide
canopy leaves two independent narrow canopies. Ownership and joined width survive
saving, craft snapshots, and cardinal craft rotation. The separate Wide Sliding Canopy
item has been removed from creative and crafting; existing placed blocks and saved
items remain compatible. Use joined narrow Sliding Canopies for new wider assemblies.
Wedge and Longnose items have also been removed from creative and crafting: their
rounded rear shells do not match the modular front/glass join height. Existing blocks
and saved items remain compatible; use Long Angled Front Canopy Glass for a joined nose.
Figher Canopy Glass is likewise removed from creative and crafting, with existing
worlds and items preserved.
The Wide Rear-Hinged Canopy remains available.

Canopy Glass and both widths of Shuttle and Bubble Wedge can share the upper
backrest space of a Pilot Seat placed directly below them. Both placement orders
are supported; the canopy remains intact when the seat is removed.

The opening shells are one block high. Provide a footwell at least one block below the canopy
for a standing player; there is no bottom panel.

Dynmap uses tile-aware closed overview meshes, clipped into each occupied cell. It shows
the fixed joins and connected widths; opening animation remains an in-game feature.

## Rendering and validation

The kit's authored face, UV and material data are stored in `canopy_meshes.json`.
The renderer draws stationary mounts and transformed shells once, with opaque framing
before sorted translucent glazing. Collision uses thin subdivided surface boxes, cached
by model, facing and pose. Picking tests the actual posed triangles; highlights enclose the complete owner geometry.
Collision and picking inspect loaded chunks only. Item models
are rebuilt on resource reload and fitted after GUI rotation to retain slot padding.

Run the Java 8 standard build and the focused Forge client suite:
`python testclient/run_windows.py --focus canopies --jar build/libs/<standard-jar>.jar`.
The suite discovers active canopy designs from the registry, checks four facings,
regular neighbor masks, angled joins, three-wide opening groups, clearance, saved
state, single-item harvesting, exact picking and projected inventory padding.
`--focus large-canopy` runs the configurable window checks without rendering the
complete kit gallery. Configurable large canopies exercise both widths, zero and maximum depth,
10% and 100% inner frames, every glass palette/intensity combination, and rejected
expansion into occupied cells. Focused captures show actual client rendering;
these checks are correctness tests and do not establish a frame-rate guarantee.

## Configurable Large Front Canopy Glass

Large Front Canopy Glass has a 3 x 3 outer frame. Large Front Canopy Glass (5 x 5)
has a 5 x 5 outer frame. Both preserve the kit's chamfered frame, mullions, rubber,
metal trim and connecting glass sides. Use the Configurizer on any occupied cell
(or sneak-use with configuration permission) to open the owner settings.

- Inner frame depth: 0�80 Minecraft pixels, where 16 pixels equals one block.
- Inner frame size: 10�100% of the outer frame, centered within it.
- Glass: Clear, the Vandor soft shades, all sixteen Minecraft colors, and Mild,
  Normal or Strong intensity.

The default depth is 16 pixels and the inner frame size is 71%, matching the
original outer extent of the detailed inner frame. A zero-depth setting retains
a thin physical frame so collision and selection remain usable. Save applies all
four options together; Close discards pending edits. Increasing depth checks the
entire added footprint for obstructions and editing permission before changing
any cells. Reducing depth removes only cells owned by that canopy.

For example, use a 5 x 5 frame at depth 64 pixels and 40% inner size for a deep
nose window, or depth 0 and 100% size for a flat broad window. Cyan with Mild
intensity keeps the glazing light; Soft Dark Grey with Strong gives a darker
cockpit. Pick-block and harvested items retain their configured geometry and
glass. Captured craft retain the complete owned footprint and settings.

Modular Visor Front, Front Latch, Corner and Side pieces, including Tall variants, are
removed from creative and crafting. Existing assemblies remain compatible. The older
2 � 2 Bubble Wedge is also retained only for saved-world compatibility; new Bubble
Wedge placements use the centered 3 � 3 version.

Sliding and Rear-Hinged Canopies remain usable after vehicle assembly. Right-click
the actual canopy surface with an empty hand to open or close it, including joined
widths of one, two and three. Clicking again during movement reverses it. The
shell animates over 16 ticks and its collision follows its current pose. Parking,
saving and newly tracking clients retain the current position and target; a
partly open canopy resumes moving after loading. Other captured tile entities do
not tick as part of canopy movement. Settled canopies do not rebuild collision.
