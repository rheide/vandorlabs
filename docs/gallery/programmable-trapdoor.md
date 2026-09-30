# Programmable Trapdoor

Programmable Trapdoors use the same 78 block finishes as Programmable Block.
Right-click to open or close. Shift-right-click in creative mode, or right-click
with the Configurizer in either game mode, to choose the finish, movement,
height, redstone trigger, and channel.

**Rotating** swings the leaf 90 degrees around its facing edge. **Sliding**
moves it one block sideways. Placement determines the facing; sliding always
stays horizontal. The model has no frame or hinge hardware.

The leaf is 3px thick. **Bottom** spans 1–4px above the block's base,
**Middle** spans 6.5–9.5px, and **Top** spans 12–15px. The 1px inset at the top
and bottom keeps a sliding leaf inside its chosen height.

## Pairs and 2×2 groups

Place two trapdoors side by side at the same height to pair them automatically.
They face opposite directions and open together in either movement mode.
The new member adopts the existing member's movement and redstone settings.
Each leaf retains its selected finish until you change the group's settings.

A complete horizontal 2×2 square at the same height becomes one four-leaf
group, regardless of placement order. The two rows or columns open toward
opposite sides. Right-clicking any leaf toggles all four, and redstone at any
member controls the whole group. Larger areas remain separate pairs or squares.
Breaking a leaf removes the square link; surviving pairs remain usable.

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
redstone, copying, recipe matching and submitted mesh data. Hardware client
screenshots and Complementary Unbound 5.6.1 visual checks remain pending.
