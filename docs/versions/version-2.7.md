# Vandor Labs 2.7

Version 2.7 separates display artwork, frame finishes and redstone transmission,
and expands the controls for moving mechanisms.

The standard JAR also includes [shader material maps](../shader-materials.md)
for every runtime texture. With Complementary's RP Support set to labPBR,
bare metals, coated panels, glass and fabric have distinct reflective finishes.
The maps preserve existing diffuse artwork and add no light emission.

The Materials picker includes Satin Silver, Cool Steel and Pale Titanium,
using their native 128px artwork and matching shader material finishes.

Programmable glass and diagonal glass have separate Front, Interior and
Exterior finishes on top-level tabs. Portholes use Main artwork for their front,
with separate Interior and Exterior finishes. Wall and diagonal-wall finish tabs
are labeled Exterior. Interior and Exterior offer the general material catalog
alongside frame artwork. Older Interior/Exterior defaults retain their appearance
until edited. Taxi collision preparation retains exact arm pose precision to
keep grounded supports in contact during height changes. Interrupted saved Taxi
height changes can recover
from shallow support penetration when a clear, loaded exit exists. Parking
paintings checks the restored blocks and other obstacles while excluding the
craft being retired; failed transfers retain exactly one painting owner.

Texture lists include a quick search field. Click the field or press **Ctrl+F**
to filter immediately by artwork name, category or identifier. Search ignores
case and accepts multiple words. Clearing the search restores the current
category view; filtering keeps the selected material and supports wheel scrolling
and scrollbar dragging. Escape closes the dialog normally.

Trapdoor leaf pickers also offer the authored Frame artwork. Transparent frame
regions retain a solid metal backing. These choices apply the existing image to
the leaf surface; they do not introduce another frame assembly or new textures.

Artwork pickers use short company names and explicit Full/Half frame labels,
with the current selection revealed when switching tabs. Glass and unsplit
screens offer full-height frames; split screens also support half-height frames.
Joined glass frames retain their single-block border thickness and extend edge
pieces while preserving the authored corners, using the same cuts as screen
frames. Padded and asymmetric frames preserve their borders and glass openings, and
diagonal half consoles correctly sample native half-height control artwork.
Carried paintings use the craft’s lighting. Supported integral landing-gear
height changes can run while driving, with physical collision checks retained.

Coupled sections recover from interrupted cross-chunk saves and rollbacks while
retaining exact cargo ownership. Recovery journals and pending latch updates
remain locked until their disk checkpoints complete. Transmitter editors reject
discarded vehicle views, and rejected ramp requests preserve channel and analog
settings. Diagonal frame caches distinguish both selected finishes.

Vehicle light discovery retains only the nearest 32 candidates, and valid large
display snapshots retain their internal lighting. Glass frames keep their outer
edge beside Bare glass. Rejected arm settings leave the previous configuration
intact, and rebuilding a transmitter dialog saves pending channel edits.

Duplifier undo uses a bounded memory budget. If a large operation exceeds it,
the settings still apply and the player is told that undo is unavailable.

Vehicle light probes are removed when a craft becomes unavailable. Building
compatibility checks reject overflowing screen dimensions and leave unloaded
furniture-door neighbors unloaded.

Transmitting blocks use a built-in **Range** slider with 4, 8, 16, 32, 64, 128 and 256 block settings, plus **Local Redstone: On/Off**. No item insertion is required. Physical output can be disabled while channels remain active. Screen rows and cockpit controls share host settings and keep independent channel targets. [Handheld Redstone Transmitters](../redstone-transmitters.md) remain in creative and retain their original item ranges and use behavior.

Different places can receive different levels on the same channel. A receiver
uses the strongest signal that reaches it. Linked controls within a transmitter's
range share their latch state; a distant transmitter can continue supplying an
ON signal. See the transmitter guide for the overlap rules and upgrade behavior.

[Frame finishes](../frames-and-displays.md) are independent of display artwork.
Choose front borders and interior materials from the Frame category, retain a
single border around a group, and select independent upper and lower images on
a full-height display. Display Fit supports square groups such as 2×2 and 3×3;
frame Fit also surrounds rectangular groups with constant border thickness. Screen content reserves the selected border width, and the Frame picker includes Bare. Split controls and Top/Bottom selectors live under Display; both halves share one frame. Frame finishes use 128-pixel artwork, preserve the original glass frame depth and have opaque interior surfaces. The Copifier retains these finishes on configured glass items. Programmable Input is now named
**Programmable Flatscreen**; its saved registry identifier remains unchanged.

Dialogs open on their first tab and use shared tabs, a Vandor logo and consistent
Redstone and Security pages. Redstone is the last tab, and receiver-only dialogs omit transmitter controls. Built-in range sliders and Local Redstone toggles replace module slots. Existing module settings remain compatible, and previously inserted items stay recoverable through mining. Screen rows share their host range; each row keeps its own channels. Cockpit controls may also use the shared default channel list.

[Company screen layers](../company-screen-layers.md) add 12 borders and 72
six-frame animations, grouped by company under Frame and Screens. Transparent
frame artwork supplies separate opaque edge strips for physical frame interiors.

[Arms](../joined-arm-platforms.md) can move the connected layer of Programmable
Surfaces attached at their tips, including overhangs beyond joined Extender supports. Horizontal Extenders offer an optional
push mode; they leave pushed blocks in place when returning. Rotating Arms offer
separate Start and End angle sliders in 15-degree steps and retain collision checks against real obstructions.

[Ramp Controllers](../landing-ramps.md) can deploy proportionally to redstone
levels 0–15. Analog mode and start/end offsets persist together when editing channel lists. Stationary full-height attachment faces accept doors. Permitted
interaction or configuration authorizes later automation, including recovery of
an existing ramp; the Configurizer uses the same authorization path.
[Hexagonal Tanks](../multiblocks/hexagonal-tank/README.md) join into complete
2×2 and 3×3 sections with native storage in each block.

The Duplifier's **Undo last application** restores one previous block-settings
application, including connected groups. It checks permissions, loaded chunks
and subsequent edits before restoring settings. It does not restore inventories.

Vehicle discovery stops at water, Programmable Surfaces and generated ramp
sections belonging to another controller. Captured signs display their saved
text. Carried lamps illuminate the vehicle locally; the optional OptiFine dynamic
lighting bridge also illuminates nearby terrain when dynamic lights are enabled.
Static opaque vehicle artwork reuses geometry and drawing order between frames.

Matched wheel or track supports can change vehicle height in Taxi mode when the complete
result stays on the block grid and has a clear native parking footprint. Stop
and align the craft first. The feet remain fixed while the chassis lowers or
rises. Modular wheel and track assemblies retain removed sections in their roots
and reuse them when raised, including after parking and recapture. Raise a
shortened parked assembly in Taxi before dismantling it. See the
[landing-gear guide](../modular-landing-gear.md) for supported layouts.

Native paintings supported entirely by the vehicle retain their artwork and facing through Taxi/Hover movement, rotation and parking. They return as ordinary paintings when parked.

Company display artwork fills the selected frame opening for single screens and
square fitted groups. Frame controls include a Framed/Bare button that remembers
the chosen finish. Top and Bottom split displays retain independent artwork and
animation playback. Authored frame interiors use their corresponding left, top,
right and bottom border material instead of stretching the complete hollow image.

The Redstone Receiver Block now uses `vandorlabs:redstone_receiver_block`. The previous `vandorlabs:redstone_receiver` ID is removed without remapping; old receiver blocks, items and saved assemblies are not migrated. Handheld redstone transmitters retain their separate item ID.

Square artwork on diagonal surfaces keeps a tradeoff between preserving the
complete image and preserving square pixels. A shallow slope is about 11.8%
longer than its vertical projection; a 45-degree slope is about 41.4% longer.
Diagonal trapdoors already account for that length in **Tile** mode, repeating
the existing artwork without creating textures. **Fit (aspect)** also tiles
when the complete image would have the wrong aspect. **Stretch** preserves the
whole image, including authored frame borders, and therefore stretches it to
the surface. For frame and door artwork, keep Stretch when the complete border
must remain visible.

Diagonal walls retain their existing complete-image mapping. A general repeat
mode would need to split geometry at sprite boundaries in both the game and
Dynmap, including joined walls, fills and inverted pieces. Simply multiplying
atlas UVs would smear sprite-edge pixels; cropping would remove parts of the
artwork. No derived textures or automatic crop are introduced.

Autopilot route markers are selected directly on General from the nine hotbar
items, preserving block metadata without consuming a stack. General and Redstone
are the two top-level tabs. Dedicated Redstone pages use their full content width,
including regular and Large arm controllers and all programmable thruster shapes.


Programmable Blocks, Slabs and Stairs share empty configuration data after loading.
Stairs also use compact render-state snapshots to reduce chunk-build allocations.
Custom face choices, stair profiles, fluids and saved block IDs retain their
existing behavior. These changes reduce memory and state-building costs without
changing texture resolution or geometry.


Cyan and Amber light frames backlight solid Strong replacement materials in
Programmable Glass and Diagonal Glass, including joined layouts. Native and
translucent glass retain their normal world lighting.


Frame finishes on Programmable Trapdoors and Diagonal Trapdoors have open centers.
Their inner edges follow the authored frame border through hinged and sliding
motion. Tiled layouts repeat the frame per leaf; joined Fit/Stretch layouts keep
an outer border without internal rails. This changes rendering only.


Selected Programmable Glass frames follow the outer contour of joined panels,
including stepped and unequal layouts. Internal borders are suppressed independently
of the rectangles used to fit pane artwork; concave corners retain solid frame faces.
