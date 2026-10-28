# Frame finishes and programmable displays

Programmable screens, Flatscreens, consoles, glass, walls, portholes and doors
have a **Frame** page. Its material list contains the **Frame** category only.
The same 58 finishes are available across these pages, including company
borders, plain and bolted screen borders, metallic window borders and interior
materials.

**Front** selects the visible border; **Interior** selects the material on the
inner and side faces. **Original artwork** restores the block's original
material. Existing glass and door finishes retain their original defaults.
Previously framed screen and control artwork migrates to a separate bolted
frame, so the display artwork can be selected independently.

For glass, **Fit** extends the front border around the connected rectangular
pane group. **Tile** repeats a complete border in every cell. The frame's
inner sides remain opaque. Borders with amber or cyan lights emit a faint
level-three block light when used on glass, walls or doors.

The screen, Flatscreen and diagonal screen frame page also provides
**Display: Tile / Fit**. Fit spreads one display over a complete square group,
including 2×2 and 3×3 arrangements. Rectangular groups such as 2×3 or 3×1
retain individual display images. The frame's Fit layout is independent and
can surround a rectangular group. Diagonal display artwork reaches both side
edges, allowing adjacent displays to meet without a housing-colored stripe.

Enable **Split display** to show two half-height images on a full-height
screen or Flatscreen. Select them independently using **Top** and **Bottom**.
The outer frame continues to surround the complete display. Turning splitting
off retains the selections for later reuse.

Display artwork and playback controls stay on the **Display** or **Controls**
page. Housing artwork and its layout controls are on **Sides**. Channel trigger
controls and transmitter access are on **Redstone**. Door access-card filtering
and panel security settings are on **Security**.

Frame artwork is stored at 64×64 material resolution. Separate sampled strips
supply narrow physical rails without using transparent center pixels as frame
side surfaces. Resource reloads rebuild sprite-dependent geometry; connected
layout discovery reads loaded blocks only and has fixed scan and cache limits.
