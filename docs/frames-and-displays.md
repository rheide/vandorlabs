# Frame finishes and programmable displays

Programmable screens, Flatscreens, consoles, glass, walls, portholes and doors
have a **Frame** page. Its material list contains the **Frame** category only.
The same 70 finishes are available across these pages, including company
borders, plain and bolted screen borders, metallic window borders and interior
materials.

**Front** selects the visible border. Doors, glass, walls and portholes also
provide **Interior** for their inner and side faces. **Original artwork** restores the block's original
material. Existing glass and door finishes retain their original defaults.
Previously framed screen and control artwork migrates to a separate bolted
frame, so the display artwork can be selected independently.

For glass, **Fit** extends the front border around the connected rectangular
pane group. **Tile** repeats a complete border in every cell. The frame's
inner sides remain opaque. Borders with amber or cyan lights emit a faint
level-three block light when used on glass, walls or doors.

The screen, Flatscreen and diagonal screen **Display** page provides
**Texture: Tile / Fit**. Fit spreads one display over a complete square group,
including 2×2 and 3×3 arrangements. Rectangular groups such as 2×3 or 3×1
retain individual display images. The frame's Fit layout is independent and
can surround a rectangular group, including neighboring displays with different artwork or playback settings. Display Fit can join matching displays with different frame finishes. Diagonal display artwork reaches both side
edges, allowing adjacent displays to meet without a housing-colored stripe.

On **Display**, enable **Split display** to show two half-height images on a full-height
screen or Flatscreen. Select them independently using **Top** and **Bottom**.
With Display Fit enabled on a square group, Top spans the upper half of the whole group and Bottom spans the lower half. Different split selections form separate display groups.
The outer frame continues to surround the complete display. Turning splitting
off retains the selections for later reuse.

Display artwork and playback controls stay on the **Display** or **Controls**
page. Housing artwork and its layout controls are on **Sides**. Channel trigger
controls are on **Redstone**. Transmitter slots appear only on transmitting controls; passive blocks, lights, doors and ordinary displays do not need them. Door access-card filtering
and panel security settings are on **Security**.

Frame artwork is stored in 128×128 atlases, with half-height artwork retaining its proportions. The original glass artwork is unchanged. Custom glass frames retain the original four-pixel depth. Separate opaque sampled strips supply narrow physical rails without using transparent center pixels as frame
side surfaces. Resource reloads rebuild sprite-dependent geometry; connected
layout discovery reads loaded blocks only and has fixed scan and cache limits.

Company finishes are grouped directly under **Frame / company name**. Other finishes remain at the Frame root. The Copifier copies front, interior and layout settings, including onto glass items for later placement.
