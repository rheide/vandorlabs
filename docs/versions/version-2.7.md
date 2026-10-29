# Vandor Labs 2.7

Version 2.7 separates display artwork, frame finishes and redstone transmission,
and expands the controls for moving mechanisms.

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

[Redstone Transmitters](../redstone-transmitters.md) carry channel lists and an
outgoing range of 16, 64 or 256 blocks, or unlimited range within one dimension.
Insert a transmitter into a transmitting block to enable channel output.
Carried vehicle controllers receive ranged signals at their physical position.
Replacing a shared screen transmitter refreshes every row output together.
Handheld toggles retain unrelated saved sources without rebuilding their indexes.
Receiver input channels and analog signal processing remain on the receiving
block. Shift-right-click a transmitter to edit its channels; ordinary use toggles
a saved signal at the point of use. A crafting-table copy preserves the central
source transmitter while copying its channels to the adjacent destination.

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
Redstone and Security pages. Redstone is the last tab, and receiver-only dialogs omit transmitter controls. Empty transmitter slots show a faded item icon;
inserted transmitters have an inline channel field. Existing configured redstone
outputs from earlier versions receive Long Range hardware when upgraded. Removing
a granted item leaves its slot empty on later loads. Screen control rows share one transmitter; edit its range on the Redstone tab and set output channels on each row. Row targets override the module channel list.
Cockpits share one transmitter
across their controls and group their individual surfaces below the main tabs.
The Vehicle Controller uses centered Vehicle and Redstone pages.
Frame settings and screen control rows stay inside the same configuration
window. Custom textures are sampled directly from an inventory grid in the
material picker. Activation selectors use **Manual**, **Redstone ON** and
**Redstone OFF**; controls that require a signal retain their two-state choices.
Housing artwork is named **Sides**. New artwork includes six replacement
corporate armor sets, two Surface helipads and 78 static half-height control
panels, grouped under Controls in the texture picker.

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
