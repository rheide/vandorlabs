# Vandor Labs 2.7

Version 2.7 separates display artwork, frame finishes and redstone transmission,
and expands the controls for moving mechanisms.

[Redstone Transmitters](../redstone-transmitters.md) carry channel lists and an
outgoing range of 16, 64 or 256 blocks, or unlimited range within one dimension.
Insert a transmitter into a transmitting block to enable channel output.
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

Matched wheel supports can change vehicle height in Taxi mode when the complete
result stays on the block grid and has a clear native parking footprint. Stop
and align the craft first. The wheels remain fixed while the chassis lowers or
rises. Modular Extender-wheel assemblies retain removed sections in their roots
and reuse them when raised, including after parking and recapture. Raise a
shortened parked assembly in Taxi before dismantling it. See the
[landing-gear guide](../modular-landing-gear.md) for supported layouts.

Native paintings supported entirely by the vehicle retain their artwork and facing through Taxi/Hover movement, rotation and parking. They return as ordinary paintings when parked.
