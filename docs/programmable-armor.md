# Programmable armor

Programmable Helmet, Chestplate, Leggings and Boots use vanilla diamond armor protection, toughness, durability and enchantability. Repair with diamonds, including in an anvil. They are available in the Vandor Labs creative tab. Craft each piece with Programmable Matter Ingots in the corresponding vanilla armor pattern (5, 8, 7 or 4 ingots).

Hold a piece, aim into the air and **shift-right-click** to open its categorized texture dialog. Configuration is available in creative mode, or in survival by holding a Configurizer in the main hand and the armor in the off hand. Choose a role design from **Armor**, a static **On** or **Off** artwork from **Lights**, or any material in the default Programmable Block texture list, then click **Done** or press Escape. Ordinary right-click equips the armor into its matching empty slot.

**Custom block texture:** choose **Custom...** in the armor texture list to open the same inventory sample dialog as Programmable Blocks. Drag a block or door item into the sample slot, or select it and click **Use texture**. The sample is not consumed. The picker remembers the custom selection when reopened, and the choice follows the armor through saving, equipping and Duplifier copying. Configured programmable block or door items resolve their stored material or design, just as in the block picker.

**Copy a world texture:** hold armor and shift-right-click the desired block face. This works directly in creative and survival without a Configurizer. A log's end and bark can be sampled separately. Programmable blocks resolve the clicked face's current material, including rotated per-face overrides; storage retains its matching top, side or front artwork. Programmable Doors resolve their selected leaf design or custom leaf material, including the upper half and large-door assembly cells. Programmable Lights resolve the artwork matching their current on/off state when sampling the light face, and their housing material on other faces. Programmable Walls, Trigger Blocks and Trapdoors use their current selected artwork. Programmable screens resolve the clicked surface’s selected artwork or off image; generated redstone-screen text has no single texture to sample. With a Configurizer in the main hand and armor in the offhand, the same gesture samples onto the offhand armor instead of opening the block's settings.

The sampled texture appears in the armor dialog under **Sampled**. Choosing another material or role design replaces it. Sampling preserves damage, names and enchantments. It copies artwork without consuming or modifying the source block. Baked models provide the nearest visible face; special renderers without exposed face artwork use their representative model or component texture. The source shape, tint and animation are not copied; armor uses the texture's first frame. Each client's resource pack supplies the artwork.

Each piece remembers its own material when equipped, dropped, moved between inventories or saved. Selecting a material preserves damage, enchantments and names. Each piece starts with its matching Civilian Staff design. Explicitly saved material choices are preserved. The selected artwork covers the armor and the padded inventory silhouette. Block materials retain the vanilla diamond helmet’s face opening and other cutouts. Block artwork is centered once on each visible armor face, preserving its proportions and cropping the edges to fit. It uses its first frame and is opaque on armor; this does not add light emission. Resource packs also affect armor materials.

The **Armor** category appears only for armor items. Each picker offers eight matching role designs for its own piece: Bioengineer, Scientist, Hazmat, Repairman, Pilot, Civilian Staff, Spaceship Staff and Security Rescue. A helmet lists helmet designs, a chestplate lists chestplates, and leggings and boots list their corresponding artwork. Role designs use native armor atlases, including their painted visors and transparent unused areas, and matching inventory icons. You can mix roles between pieces. Helmets also offer an **Open** design for every role, and chestplates offer **Short Sleeves** designs. Leggings and boots keep their eight original choices.

**Copy armor designs:** use the [Duplifier](DUPLIFIER.md#armor-textures) to copy a mounted or offhand piece’s appearance. Shift-right-click copies and right-click applies. Role designs select the destination piece’s matching artwork; generic and sampled textures transfer unchanged. Damage, names and enchantments are preserved.

See the [armor gallery](gallery/armor.md) for all eight full sets and their open-helmet/short-sleeve variants on armor stands.

**Armor stands:** hold a Configurizer in the main hand and right-click the mounted programmable piece to open its menu. Aim at the head, torso, legs or feet to choose the corresponding slot. This works in creative and survival, on normal and small stands, without removing the armor. Changes apply only to the clicked piece and preserve its damage, name and enchantments. Keep the Configurizer in hand and remain near the stand while editing.

![The Configurizer opens the mounted armor piece’s picker](images/gallery/armor/stand-picker.png)

Remove armor worn by your player and hold it to change its material.

![Programmable armor with independent materials and padded item icons](images/gallery/armor/block-materials.png)

![The helmet picker offers matching role designs in its Armor category](images/programmable-armor-picker.png)

![Hazmat role armor with matching padded inventory icons](images/programmable-armor-hazmat.png)

![Custom texture selected in the armor picker](images/gallery/armor/custom-picker.png)

When copying an Open helmet or Short Sleeves chestplate with the Duplifier, a destination helmet receives the matching Open design and a chestplate receives Short Sleeves. Leggings and boots use the role’s standard design. New items still default to the original Civilian Staff design.
