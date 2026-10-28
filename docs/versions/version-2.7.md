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
frame Fit also surrounds rectangular groups. Programmable Input is now named
**Programmable Flatscreen**; its saved registry identifier remains unchanged.

Dialogs use shared tabs, a small Vandor logo and consistent Redstone and Security
pages. Housing artwork is named **Sides**. New artwork includes six replacement
corporate armor sets, two Surface helipads and 78 static half-height control
panels, grouped under Controls in the texture picker.

[Arms](../joined-arm-platforms.md) can move the connected layer of Programmable
Surfaces attached at their tips. Large horizontal Extenders offer an optional
push mode; they leave pushed blocks in place when returning. Rotating Arms offer
a 15-degree angle slider and retain collision checks against real obstructions.

[Ramp Controllers](../landing-ramps.md) can deploy proportionally to redstone
levels 0–15. Stationary full-height attachment faces accept doors.
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
