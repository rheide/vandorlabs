# Module Rack

The Module Rack holds four **Blank Modules** in independently selectable horizontal bays. It is available in the Vandor Labs creative tab on Forge 1.12.2.

![A Module Rack with all four bays populated in Minecraft](images/module-rack/populated.png)

## Use

- Place the rack against any block face. Its open front faces toward your view, using the same placement rule as Programmable Light. A shallow floor click faces you horizontally; looking steeply down or up places an upward- or downward-facing rack.
- Hold a Blank Module and right-click the front of the intended bay. The module slides into that bay; the other bays remain unchanged.
- Each row has two tiny vertically stacked indicators in the recessed right-hand panel: bright cyan above for an occupied bay, or amber below for an empty bay. A row's buttons select the same bay as its opening.
- Use an empty hand on an occupied bay or its buttons to retrieve its module. If the inventory cannot accept it, the module remains in the rack.
- Breaking the rack releases its contained modules. Middle-clicking the rack picks the empty block, without duplicating the contents.

Insertion consumes one module in Survival. Creative insertion follows ordinary creative placement and leaves the held stack unchanged. Stored modules retain their item data, including custom names. Blank Modules are decorative inserts; this initial item does not provide a machine function or redstone output.

![A Blank Module sliding into the top bay, with the other three bays empty](images/module-rack/inserting.png)

![An extracted module sliding outward while the remaining bays stay populated](images/module-rack/extracting.png)

## Shape and artwork

The front is a 16 by 16 model-pixel square. The top, bottom, left and right rims are one pixel thick and reach the front of the casing. The two-pixel control strip inside the right rim stays recessed by one pixel; it has no card or chip mount. Four three-pixel-high module faces are separated by half-pixel rails, with quarter-pixel clearance above and below the group.

The rack and modules use darker graphite-grey housing, restrained amber/cyan details, and raised vertical handles at both ends of each module face. The module body has side runners and rear contacts. The inventory item is a padded three-dimensional model of the same cassette.

On a floor-facing rack the top row points north; on a ceiling-facing rack it points south. Slots, buttons and insertion travel use the same local orientation in all six mounting directions.

## Crafting

One rack requires six Industrial Alloy Ingots and one Redstone Dust. Arrange the ingots in the left and right columns, with the Redstone Dust in the center.

One Blank Module requires three Iron Ingots, two Gold Nuggets and one Redstone Dust. Put the three ingots in one row and Gold Nugget / Redstone Dust / Gold Nugget immediately below.

## Rendering

The casing, populated indicators and seated modules are ordinary opaque chunk geometry. Prepared quads are shared across equivalent rack states. Stationary racks have no tile-entity renderer and no ticking tile entity. Inventory contents are serialized for saves and actual interactions, never by the rendering loop.

Insertion and extraction use a temporary client animation. Only racks with an active slide enter that renderer. At the end of the slide, the module transfers to the chunk mesh after the replacement section has uploaded. Removing a seated module slides it outward, and rapid reversals retain the displayed offset. Repeated state packets do not replay a slide; loading an existing populated rack displays its seated modules immediately. The visual clock pauses with the game.

The indicators do not emit world light. Their visual brightness changes with occupancy, avoiding a lighting update whenever a module is inserted or removed.

Dynmap uses the same native model parts and displays the rack's saved slot occupancy on all six mounting faces.

The implementation targets Forge 1.12.2. The experimental NeoForge adapter is not included in this feature.
