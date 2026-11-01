# Redstone display artwork

Programmable Screens, Flatscreens, Diagonal Screens, Inputs and Consoles have
an **Activation** setting on their **Redstone** tab:

| Activation | Artwork tabs | Selection |
| --- | --- | --- |
| Disabled | Display or Controls | One shared selection, independent of redstone |
| Digital | Off, On | Off at zero; On at any positive signal |
| Analog | 0, 1–5, 6–10, 11–15 | One selection for each signal band |

Each enabled state has its own material/display choice, Off/Static/Animated
playback and Slow/Normal/Fast animation speed. Enabling redstone preserves the
shared artwork, playback and speed. Newly exposed analog bands inherit the
Digital On settings; previously configured states retain their own choices.
Editing a state applies its artwork, playback and speed to the whole matching
joined rectangle. Editing membership uses the shared artwork and placement
plane, so different signal-band artwork cannot disconnect its own dialog or
hide Half choices on a wide rectangle.

Changing state tabs clears a stale search filter and reveals the selected
texture. Search, wheel scrolling and scrollbar dragging work on every state tab. Receiving channels use the
existing channel selector; leave it blank for local redstone. **Signal: Low**
inverts the signal before selecting the artwork state. Right-click a cycling
option to select its previous value.

**Use Shared Artwork** restores a state's inherited selection without removing
its saved control or split-display setup. An explicit state texture overrides
the selected display’s shared source; frame finishes and Fit/Tile mapping remain shared.

Local redstone at any member activates the whole matching joined rectangle.
The highest local strength selects the shared analog band, and removing the
last source deactivates the whole rectangle. This uses the same authored plane
and rectangle partition as rendering, including diagonal layouts. Different
state artwork configurations stay separate; receiving a channel does not turn
the joined display into a channel repeater. Powered local controls retain their saved level after reload. Solid Programmable Blocks can conduct a lever’s strong power from the support beneath any display family.

On regular Consoles, redstone artwork changes the upright Display while the flat Controls stay shared. On Half Console, it changes the inclined Display; the flat Controls stay shared. Its dialog labels these surfaces **Display** and **Controls**. Diagonal Half Console changes its single inclined display. Existing saved display gates retain
their previous behavior until their activation settings are edited.

Programmable Screen and Flatscreen pickers include Half display designs when
the selected block belongs to a wide joined rectangle, including 4×2 layouts.
Single blocks and square layouts show Full designs. Texture **Fit** spans the
joined rectangle; **Tile** repeats its artwork. The picker uses the same
connection and rectangle-partition rules as rendering. Matching edits received
from a Configurizer or Copifier update the joined layout without replacing a
block.

## Programmable Trigger

Trigger blocks use static artwork. Their **Off / On** tabs, or four analog
signal-band tabs, select the corresponding textures. **Faces: All Faces**
changes the whole cube. **Faces: Front Only** changes just its placed front
face and exposes a **Sides** tab for one static texture on the other five faces.
This changes the artwork, rather than which faces receive redstone.
