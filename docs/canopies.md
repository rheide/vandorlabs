# Canopies

Build a cockpit from connected glass, angled noses, openable shells and broad front windows. Use the Configurizer, or sneak-use with configuration permission, to change settings on any occupied cell. Changes apply immediately; the compact dialog leaves the surrounding world visible.

## Components and placement

| Component | Size | Shapeless conversion from Canopy Glass |
| --- | --- | --- |
| Canopy Glass | 1 × 1 × 1 | Base recipe |
| Angled Front Canopy Glass | 1 × 1 × 1 | 1 iron nugget |
| Long Angled Front Canopy Glass | 1 × 2 × 1 | 2 iron nuggets |
| Canopy | 1 × 2 × 1; joins to two or three wide | 3 iron nuggets |
| Large Front Canopy Glass | 3 × 3 outer frame; adjustable depth | 8 iron nuggets |
| Large Front Canopy Glass (5 × 5) | 5 × 5 outer frame; adjustable depth | Large Front Canopy Glass surrounded by 8 iron ingots (shaped) |

The base recipe has three glass blocks across the top, Programmable Matter Ingots at both middle edges, and three more ingots across the bottom. Visor, Wide Visor, Extra-Wide Visor and Tall Extra-Wide Visor remain available as fixed front windows. Their widths are two, three, five and five blocks; the Tall variant is two blocks high. They have no roof, rear or bottom, and a one-pixel border follows the upper outline. Visors center on the clicked block, including the two-wide variant's half-block alignment.

Click a wall to mount an angled nose or Canopy with its rear against that wall. Long noses extend outward instead of requiring space inside the support. Click the underside of a ceiling to place either angled nose or Canopy upside down. The large windows mount against any of the six block faces: their broad rear opening stays centered on the clicked location and their smaller front projects outward, towards the player.

Placement checks the complete owned footprint and editing permissions before writing blocks. Breaking an owned cell removes its component and returns one configured item in survival. Joined Canopies lose only the broken constituent and reconnect the survivors where possible. Pistons cannot move these assemblies.

## Glass and materials

The **Glass** picker offers Clear, soft Vandor shades and all sixteen Minecraft colors. **Tint** selects Mild, Normal or Strong intensity. Switch to **Materials** for the normal Programmable Block texture catalog, including trapdoor artwork and filesystem materials. Selecting a glass entry restores glazing; selecting a material replaces the glazed surfaces while preserving the frame and shape.

The Duplifier copies glass shade and intensity between canopies, visors, regular programmable glass and diagonal glass walls. Joined opening shells share their finish. Pick-block and harvested items retain their settings.

## Connected fixed glass

Canopy Glass joins neighboring tiles at the same elevation and mounting orientation, regardless of horizontal facing. Shared glazing and perimeter rails disappear. Matching angled noses join sideways and connect to regular glass behind their complete footprint. Regular glass also opens its side towards the rear of a Canopy. Ceiling-mounted parts use the same local joins after inversion.

## One Canopy, two opening modes

Choose **Sliding** or **Rear hinge** in the Canopy dialog. Place one, two or three matching closed Canopies side by side to form a shared shell. Their opening mode, front profile and mount must match. A fourth constituent stays independent.

- **Flat** has a vertical front matching Canopy Glass.
- **Angled** uses the short angled nose followed by a roof section.
- **Long Angled** uses the two-block nose profile.

Both motions take sixteen ticks and can reverse during movement. Sliding lifts the shell by one pixel before carrying it two blocks rearward. Rear hinge raises the front through 105 degrees around the rear upper edge. The stationary mounts stay in place. Right-click an occupied cell or the actual moved shell to operate it. Enable **Redstone** to follow physical power or any configured comma-separated channel. The channel field accepts the same lists as other programmable blocks.

| Width / profile | Sliding | Rear hinge |
| --- | --- | --- |
| Single / Flat | ![Single flat sliding Canopy](images/gallery/canopies/slide-1.gif) | ![Single flat rear-hinged Canopy](images/gallery/canopies/hinge-1.gif) |
| Double / Angled | ![Double angled sliding Canopy](images/gallery/canopies/slide-2.gif) | ![Double angled rear-hinged Canopy](images/gallery/canopies/hinge-2.gif) |
| Triple / Long Angled | ![Triple long-angled sliding Canopy](images/gallery/canopies/slide-3.gif) | ![Triple long-angled rear-hinged Canopy](images/gallery/canopies/hinge-3.gif) |

![Ceiling-mounted double Canopy](images/gallery/canopies/ceiling.gif)

Opening shells intentionally pass through rear bodywork. Opening does not reserve the destination cells. Collision and exact face picking follow the moving surfaces; the selection outline encloses the complete posed shell. Keep a footwell below an upright canopy for a standing player: it has no bottom panel. Canopy Glass and opening Canopies can share the backrest space of a Pilot Seat directly below them, in either placement order.

## Large Front Canopy Glass

Both frame sizes provide **Depth: 0–80 pixels**, **Inner size: 10–100%**, glass/material selection and optional opening. Sixteen pixels equals one block. The defaults are sixteen pixels deep and 71% inner size. Zero depth keeps a thin physical frame for usable collision and selection.

Changing depth moves the front while the broad rear stays fixed. Only cells touched by the closed surfaces are reserved; clear cabin cells can hold other blocks. Hold an ordinary block and click the outside glazing towards empty cabin space to build inside without entering the canopy. Normal placement and protection checks still apply. A blocked resize preserves the original geometry and all cabin blocks.

Enable **Openable** and choose a **Left**, **Right**, **Top** or **Bottom** hinge. These directions follow the frame's local orientation, including floor and ceiling mounts. The complete frame rotates through 105 degrees. Enable **Redstone** and set channels to operate it remotely, or right-click it manually. Close the canopy before changing its hinge or opening mode. Deep or moved surfaces remain operable and configurable even when their anchor lies farther away; interaction validates the reached surface and intervening terrain.

| Large top hinge | 5 × 5 left hinge |
| --- | --- |
| ![Large front canopy with top hinge](images/gallery/canopies/large-top.gif) | ![Five-wide front canopy with left hinge](images/gallery/canopies/large-left.gif) |

## Taxi Mode and compatibility

Canopy and enabled large-window hinges remain manually openable aboard a Taxi Mode vehicle. Click the actual surface with an empty hand. Motion, collision, target, geometry, finish, mount and owned cells survive saving, parking and cardinal rotation. As with captured doors, the vehicle's block redstone simulation and settings menus resume after parking.

The former separate Rear-Hinged Canopy, wide opening items and older fixed-shell variants remain registered for saved-world compatibility but are absent from new crafting and creative selection. Existing rear-hinged placements retain their opening mode.

Owned-cell offsets are saved explicitly. Resizing replaces that footprint only after validation, and removing a canopy uses its recorded cells. Loaded follower cells whose owner no longer includes them clean themselves up without deleting foreign blocks. These safeguards also cover translated and rotated vehicle snapshots.

## Rendering and validation

Rendering, collision, picking and Taxi Mode use one posed mesh. Settled shells reuse prepared vertices and sprite lookups; animation rebuilds only the moving pose. Ordinary four-vertex faces submit a quad directly instead of two triangle-shaped quads, halving vertex submission for those faces. Transparent glazing remains sorted separately from opaque frames. Large-window collision uses glazing triangles and thin rails along their edges, avoiding thousands of boxes for decorative frame bevels during rotation. Detailed face picking still follows the complete mesh. Captured selection tests each canopy owner once per ray instead of repeating the same mesh for every owned cell. These are CPU and submission reductions; they are not a measured frame-rate guarantee.

Dynmap uses the mounted closed overview geometry. Item models fit after GUI rotation so every configured variant retains inventory padding.

Run the standard Java 8 build, then the live suites:

```text
python testclient/run_windows.py --focus canopies
python testclient/run_windows.py --focus large-canopy
python testclient/run_windows.py --focus vehicle-taxi
python testclient/run_windows.py --focus canopy-animations
```

The canopy release checks exercise six-face large-window placement, mounted hinges, exterior cabin targeting, exact ownership, removal, ceiling joins, all front/opening combinations, channel operation, finish copying, floor-button dust output and armrest masking. Animation capture records the actual client renderer; encode and validate it with `testclient/encode_animations.py`.
