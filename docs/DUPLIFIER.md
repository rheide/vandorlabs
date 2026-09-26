# Duplifier settings

Craft one with a Programmable Matter Ingot and two redstone in a crafting grid:

```text
 R
IR
```

Shift-right-click a configurable block to replace the settings stored in the
Duplifier. Its item name then includes the copied block's name. Right-click a
target block to apply each stored setting that the target supports. The tool
works from either half of a Programmable Door. The display on the item lights
up when it holds settings. Shift-right-click in the air to clear the copy,
returning its name and display to the empty state.

| Shared setting | Sources and targets |
| --- | --- |
| Wall Texture | Programmable block, slab, wall, porthole, display housing, light housing, trigger off finish, and propulsion side finish. All use the ordered `ScreenHousingTextures` list. |
| Redstone Channel | Every configured block that implements `RedstoneChannelMember`. |
| Join | Portholes, Programmable Glass, Programmable Light, and connected propulsion blocks. |
| Trigger | Programmable Light and Door support Disabled, Redstone ON, and Redstone OFF. Displays support Disabled and Redstone ON. |
| Active | Manual light state, propulsion state, and switch or lever latch. |

Primary screen artwork transfers between displays. Light artwork transfers
between Programmable Lights. The two artwork catalogs are separate, so an
unrelated texture index is never applied across them. Display mode, animation
speed, framing, input panels, input size and wall position transfer between
displays. Light level transfers between lights. Porthole shape and glass shade,
slab side layout, propulsion shape and particles, glass size, trigger on finish,
chair style and height, door design and motion settings, and switch mount
rotation transfer only to targets that expose those settings. Framing and glass
shade also transfer across block types that share those controls.
Ramp controllers carry their offsets, tread size, speed, lift and extend modes,
direction, travel axis, trigger polarity, and redstone channel. Applying ramp
geometry uses the controller's normal validation and reset path; an obstructed
platform can reject the change.

Placement facing, physical position, live redstone signal, current door motion,
and ownership are not stored. The item uses semantic setting keys rather than
copying tile NBT, so existing saved-world field names and placement behavior
remain intact.
