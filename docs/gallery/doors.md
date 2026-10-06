# Programmable Door

One Programmable Door can use any of 21 designs, with or without a frame. Its motion, placement depth, hinges, control panel, detail level, and redstone behavior are configurable. Craft one from six Programmable Matter Ingots in two adjacent columns of three. Place a second compatible door beside the first to make a pair; the inner frame rails disappear.

In Creative mode, shift-right-click either half to open the settings. The optional button panel also opens them when clicked. Changes apply as soon as you select them. Right-click toggles a manually controlled door; a door using a redstone trigger instead follows its signal.

![Programmable Door settings](../images/gallery/doors/door-config.png)

## Choose a design and frame

Each image shows the **bare door on the left** and the **framed door on the right**. The Small and Large artwork sizes change texture resolution without changing the chosen design.

| Design | Bare (left) and framed (right) |
| --- | --- |
| Observation | ![Observation](../images/gallery/doors/design-observation.png) |
| Airlock | ![Airlock](../images/gallery/doors/design-airlock.png) |
| Standard | ![Standard](../images/gallery/doors/design-standard.png) |
| Security | ![Security](../images/gallery/doors/design-security.png) |
| Reactor Service | ![Reactor Service](../images/gallery/doors/design-reactor-service.png) |
| Viewport | ![Viewport](../images/gallery/doors/design-viewport.png) |
| Laboratory | ![Laboratory](../images/gallery/doors/design-laboratory.png) |
| Cargo | ![Cargo](../images/gallery/doors/design-cargo.png) |
| Ventilation | ![Ventilation](../images/gallery/doors/design-ventilation.png) |
| Cargo Lift | ![Cargo Lift](../images/gallery/doors/design-cargo-lift.png) |
| Blast Shield | ![Blast Shield](../images/gallery/doors/design-blast-shield.png) |
| Glazed Hangar | ![Glazed Hangar](../images/gallery/doors/design-glazed-hangar.png) |
| Quarantine Seal | ![Quarantine Seal](../images/gallery/doors/design-quarantine-seal.png) |
| Reactor Barrier | ![Reactor Barrier](../images/gallery/doors/design-reactor-barrier.png) |
| Modular Shutter | ![Modular Shutter](../images/gallery/doors/design-modular-shutter.png) |

Observation, Viewport, Laboratory, and Glazed Hangar have translucent windows. A design does not force a motion: choose either independently.

## Choose how the door opens

Each clip shows a complete open/close cycle. These are live in-game captures at 420×350; [capture workflow](../animated-documentation.md).

| Motion | Opening and closing |
| --- | --- |
| Rotate | ![Rotate](../images/gallery/doors/observation-rotating.gif) |
| Slide sideways | ![Slide sideways](../images/gallery/doors/observation-sideways.gif) |
| Slide up | ![Slide up](../images/gallery/doors/observation-up.gif) |
| Slide down | ![Slide down](../images/gallery/doors/observation-down.gif) |

The motion selector also offers **Slide Left**, **Slide Right**, **Split Horizontal**, and **Split Vertical** for single, paired and Large Programmable Doors:

| Mode | Travel |
| --- | --- |
| Slide Left / Right | Move the entire door opening to one side: one block for a single, two for a pair, or three for a Large Door. |
| Split Horizontal | Divide at the middle and move the halves left and right. A single leaf splits too. |
| Split Vertical | Divide at mid-height and move the lower half down and the upper half up. |

Left and right refer to the door's local orientation; viewing it from behind reverses them. Split cuts preserve the selected artwork and glazing. Frames and control panels stay fixed.

New single doors retain placement-dependent left/right sliding. Placing a compatible second door changes that default to **Split Horizontal**; removing it restores the single-door default. Existing saved sideways sliding follows the same rule without changing its travel. Explicitly selected modes remain selected when pairing changes. Existing rotating, up, down and large-only Sliding X modes also retain their saved behavior. All new modes survive world saves, pick-block and Duplifier copying.

The frame stays in place while the leaf moves. Rotating doors can show or hide their hinges; the hinge setting is saved even when you temporarily switch to sliding motion. Paired doors share appearance and settings.

## Large Programmable Door

A Large Programmable Door occupies one **3×3 opening** and is placed or removed as a whole. Any of its nine cells opens the shared settings. It offers the regular door designs plus eight large-only bay designs, and defaults to Cross Braced Bay. Its texture size setting changes artwork resolution, while its opening remains 3×3.

The same movement choices apply; **Sliding X** is an additional large-only option that retracts four triangular panels left, right, up and down. Large doors open and close slightly more slowly than regular doors. Leave room beside, above or below the opening for the selected motion.

## Choose the position in the block

**Near**, **Middle**, and **Far** shift the entire assembly within the block, including its frame, hinges, panel, and collision. This lets the door line up with the face or center of an adjacent wall.

| Position | Close-up |
| --- | --- |
| Near | ![Door near the front](../images/gallery/doors/position-near.png) |
| Middle | ![Door in the middle](../images/gallery/doors/position-middle.png) |
| Far | ![Door toward the back](../images/gallery/doors/position-far.png) |

## Control panel and trigger

The small button pad can be shown or hidden. It sits on a jamb and opens the settings without sneaking. On a pair, only the outer jamb has a panel.

| Panel on | Panel off |
| --- | --- |
| ![Door with panel](../images/gallery/doors/panel-on.png) | ![Door without panel](../images/gallery/doors/panel-off.png) |

**Trigger: Disabled** keeps manual right-click operation. **Redstone ON** opens while powered; **Redstone OFF** opens while unpowered. Choose a physical signal or a virtual redstone channel. Creative pick-block retains selector settings for another placement; the new placement click sets its initial depth. For pairing, save details, and existing-world behavior, see the [door reference](../space-doors.md).
