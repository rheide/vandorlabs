# Programmable Door

One Programmable Door can use any of 22 designs, with or without a frame. Its motion, placement depth, hinges, control panel, detail level, and redstone behavior are configurable. Craft one from six Programmable Matter Ingots in two adjacent columns of three. Place a second compatible door beside the first to make a pair; the inner frame rails disappear.

In Creative mode, shift-right-click either half to open the settings; in survival, right-click with the Configurizer. The optional button panel also opens them when clicked. Changes apply as soon as you select them. Right-click toggles a manually controlled door; a door using a redstone trigger instead follows its signal.

Open large-door panels and regular rotating leaves remain clickable where they extend into neighbouring blocks. The selection outline follows the panel under the cursor, and ordinary right-click closes a manually controlled door there.

![Programmable Door settings](../images/gallery/doors/door-config.png)

## Choose a design and frame

Each image shows the **framed door on the left** and the **bare door on the right**. The **Small** and **Large** size choices change texture resolution without changing the chosen design. Each design appears once in the picker; the size button controls its artwork tier.

| Design | Framed (left) and bare (right) |
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
| White Glass | ![White Glass](../images/gallery/doors/design-white-glass.png) |
| Dark Glass | ![Dark Glass](../images/gallery/doors/design-dark-glass.png) |
| Slim Glass | ![Slim Glass](../images/gallery/doors/design-slim-glass.png) |
| Plain Cargo | ![Plain Cargo](../images/gallery/doors/design-plain-cargo.png) |
| Stepped Freight | ![Stepped Freight](../images/gallery/doors/design-stepped-freight.png) |
| Observation Leaf | ![Observation Leaf](../images/gallery/doors/design-observation-leaf.png) |
| Reinforced Leaf | ![Reinforced Leaf](../images/gallery/doors/design-reinforced-leaf.png) |

Observation, Viewport, Laboratory, Glazed Hangar, White Glass, Dark Glass and Slim Glass have translucent windows. A design does not force a motion: choose either independently.

## Large doors and double-door artwork

The Large Programmable Door fills a **3×3 opening** with two 1.5×3 leaves. Place it at the bottom center on three solid supports. Craft it from two Programmable Doors and seven Programmable Matter Ingots. Configure any cell to edit the assembly; breaking any cell removes it.

Large doors accept all 22 regular designs plus these **eight Double Doors designs**. Each image shows a framed assembly on the left and a bare assembly on the right. The default is **Cross Braced Bay**. Large doors support the regular motions and **Sliding X**.

| Double Doors design | Framed (left) and bare (right) |
| --- | --- |
| Warehouse Shutter | ![Warehouse Shutter](../images/gallery/doors/design-warehouse-shutter.png) |
| Slotted Bay | ![Slotted Bay](../images/gallery/doors/design-slotted-bay.png) |
| Cross Braced Bay | ![Cross Braced Bay](../images/gallery/doors/design-cross-braced-bay.png) |
| Split View Bay | ![Split View Bay](../images/gallery/doors/design-split-view-bay.png) |
| Offset Cargo | ![Offset Cargo](../images/gallery/doors/design-offset-cargo.png) |
| Twin Observation | ![Twin Observation](../images/gallery/doors/design-twin-observation.png) |
| Armored Biparting | ![Armored Biparting](../images/gallery/doors/design-armored-biparting.png) |
| Service Freight | ![Service Freight](../images/gallery/doors/design-service-freight.png) |

Slim Glass has wide gunmetal outer rails, a central strut and a cyan accent.
It is also available on large doors. Its **Small** artwork is 128×256
and **Large** artwork is 256×512, with the existing glass reflection material
behind the two clear openings.

![Slim Glass large doors](../images/gallery/doors/design-slim-glass-large.png)

Select any shared material to replace a leaf face. **Face texture: Fit/Tile** controls its mapping; the native thin edges and hardware remain. See [unified materials](../unified-materials.md) for Custom and filesystem artwork.

## Choose how the door opens

Each clip shows a complete open/close cycle. These are live in-game captures at 420×350; [capture workflow](../animated-documentation.md).

| Motion | Opening and closing |
| --- | --- |
| Rotate | ![Rotate](../images/gallery/doors/observation-rotating.gif) |
| Slide sideways | ![Slide sideways](../images/gallery/doors/observation-sideways.gif) |
| Slide up | ![Slide up](../images/gallery/doors/observation-up.gif) |
| Slide down | ![Slide down](../images/gallery/doors/observation-down.gif) |

The frame stays in place while the leaf moves. Rotating doors can show or hide their hinges; the hinge setting is saved even when you temporarily switch to sliding motion. Paired doors share appearance and settings.

Large Programmable Doors also offer **Sliding X**: four triangular panels retract
outward from the center. When frameless, each fully open panel retains a
one-pixel tip (1/16 block) inside the opening for click targeting.

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
