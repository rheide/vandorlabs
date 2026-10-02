# Programmable Trapdoor

Programmable Trapdoors use the shared categorized material catalog, including door artwork and Custom block/door textures.
Right-click to open or close. Shift-right-click in creative mode, or right-click
with the Configurizer in either game mode, to choose the finish, movement,
height, redstone trigger, and channel.

**Rotating** swings the leaf 90 degrees around its facing edge. **Sliding**
moves it 15 pixels sideways, leaving one pixel visible in its own block. Placement determines the facing; sliding always
stays horizontal. Rotating leaves keep their full thickness at least one pixel inside the mounting cell at the hinge, with a tiny perimeter inset to avoid coplanar neighboring faces. Click the lower, middle, or upper third of a wall face to place a plain trapdoor at Bottom, Middle, or Top; floor and ceiling clicks choose Bottom and Top. Configured items retain their saved position. The model has no frame or hinge hardware.

The leaf is 3px thick. **Bottom** spans approximately 0–3px above the block’s base,
**Middle** spans 6.5–9.5px, and **Top** spans approximately 13–16px. Floor and ceiling mounts use only a 1/1024-block inset (1/64 of a texture pixel) to prevent coplanar faces, so closed leaves sit flush visually. Rotating panels move inward during opening to retain clearance from their support; sliding leaves keep their selected height.

## Closed leaf placement and texture layout

**Closed leaf: This block / Next block** is independent of **Movement: Rotating / Sliding**. Next block places the closed leaf across the neighboring cell in its facing direction, with the trapdoor tile remaining in its own mounting cell. The closed leaf projects one pixel past the covered cell’s far edge; its selection and collision include that overhang. In this mode **Hinge: north/east/south/west** selects that neighboring cell explicitly. Rotating folds the leaf upright into the mounting cell; Sliding brings it back horizontally. These offset mounts remain individual rather than joining a rectangle. Joined trapdoors show a disabled **Closed leaf: This block (joined)** control; configuration packets and Duplifier copies cannot turn them into offset mounts. Selection and collision follow an offset leaf into the neighboring cell for all four hinge directions, with interaction still routed to its owning tile. This replaces the old Movement: Cover mode. See [landing gear covers](../landing-gear-covers.md) for a gear shaft example.

If an older version split an assembly after selecting Next block, select each shifted leaf and restore **This block**. Once all members are restored at the same height, replace one member to rebuild the joined footprint.

**Texture: Tile / mirror** repeats block artwork at one-block scale. Door artwork is recognized automatically, including Custom vanilla/mod `BlockDoor` items: one block wide and two blocks long, with alternating columns mirrored left/right. A 2×2 hatch therefore shows two opposite-handed doors. A single-row door hatch uses its lower half. **Texture: Fit** stretches one image across the complete group. The standard programmable door’s metal side artwork covers the four thin edges in either layout. Texture layout and next-block hinge direction survive saves, configured items and Duplifier copying.

## Connected groups

Place two trapdoors side by side at the same height to pair them automatically.
They face opposite directions and open together in either movement mode.
The new member adopts the existing member's movement and redstone settings.
Each leaf retains its selected finish until you change the group's settings.

A complete horizontal 2×2 square at the same height becomes one four-leaf
group, regardless of placement order. The two rows or columns open toward
opposite sides. Right-clicking any leaf toggles all four, and redstone at any
member controls the whole group. Complete rectangular areas through 8×8 also form a group, including 2×4 and 5×2. The two halves slide clear or rotate around their outer edges as joined panels. Incomplete areas keep their existing smaller groups; completing the rectangle joins them.
Breaking a leaf removes the square link; surviving smaller groups remain usable.

Configuration and Duplifier applications update the loaded group together.
Group membership and individual finishes persist when the world is saved.
Unloaded chunks are never forced to load.

## Redstone

**Disabled** gives manual operation. **Redstone ON** opens when powered and
closes when power goes away; **Redstone OFF** reverses that behavior. Ordinary
right-click still toggles the group between power changes.

Channel 0 uses local redstone. A positive channel also listens to configured
senders in the same dimension. Any powered member opens the group in Redstone ON
mode. Pick-block and drops retain texture, height, movement, trigger and channel,
without retaining world coordinates or group links.

## Crafting

Use five Industrial Alloy Ingots (`I`) and one Programmable Matter Ingot (`M`)
to craft two trapdoors:

```text
III
IMI
```

## Validation

Non-rendering checks cover all finishes, heights and motion directions,
opposite-facing pairs, all 24 square placement orders, persistence, group repair,
redstone, copying, recipe matching and submitted mesh data. Live Forge checks cover
combined dialog edits, saved/copied layout and hinge settings, door-art tiling,
edge textures and clearance scenes; see the [illustrated examples](task-improvements.md).
These regression captures use software rendering. Hardware gameplay and
Complementary Unbound 5.6.1 shader appearance still need verification.
