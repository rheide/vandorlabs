# Lights

Version 1.6 adds [signal-level controls and live demonstrations](version-1.6.md).

Programmable Light offers selectable artwork with matching unlit and lit textures; the close-ups place those states side by side. In Creative mode, shift-right-click to choose the appearance, wall finish, redstone behavior, channel, and Join setting. Normal right-click changes its manual state. A joined group can respond together to a signal received by one member.

The taller texture lists show up to seven textures and a pinned category heading. Separate Off artwork is hidden in this dialog: choose the On appearance and the light uses its matching unlit texture automatically.

![Programmable Light settings](../images/gallery/lights/light-config.png)

| Appearance | Off and on close-up |
| --- | --- |
| Porthole | ![Porthole light states](../images/gallery/lights/porthole.png) |
| Light Column | ![Light Column states](../images/gallery/lights/light-column.png) |
| Slatted Lamp | ![Slatted Lamp states](../images/gallery/lights/slatted-lamp.png) |
| Window Lamp | ![Window Lamp states](../images/gallery/lights/window-lamp.png) |
| Lightbar | ![Lightbar states](../images/gallery/lights/lightbar.png) |
| Logo | ![Logo light states](../images/gallery/lights/logo.png) |

Use redstone **On** to light while powered or **Off** to light while unpowered. A virtual channel carries the same activation across loaded blocks in the dimension. Configure the block from its own menu, or copy compatible settings with the [Duplifier](../DUPLIFIER.md).

## Shape, size and side layout

Light Frames attach flush to the supporting wall, floor or ceiling. **Size: Small** centers a half-width light; **Full** restores the normal size. Small lights display individual artwork even when Join is enabled. Size applies only to Light Frames. Light Slabs retain their normal upper/lower half footprint, and Light blocks retain their full size. **Side layout: Tile/Fit** controls whether housing artwork keeps its block scale or stretches to the exposed side. The chosen size and layout survive saving, configured items and Duplifier copying. Light Frame and Light Slab icons show their light artwork and fit within the hotbar slot.

## Signal brightness

Programmable Light, Light Slab and Light Frame can follow signal strength rather
than a fixed brightness. Select **Signal + offset** and use the brightness slider
as a signed offset; the result is clamped to 0–15. Joined lights use the strongest
loaded signal received by the group. Manual brightness remains available.

See the [Wall Slider controlling a light](version-1.6.md#slider-controlling-a-light)
for an animated example and the complete signal settings.
