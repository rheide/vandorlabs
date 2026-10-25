# Programmable Pressure Plate

Craft one plate from two Programmable Blocks arranged side by side.

Place the plate on a supporting block as with a wooden Minecraft pressure plate. Players, other living entities and dropped items press it. The plate has the vanilla inset outline and thin raised/recessed positions. **Sensitivity: Wood** supplies level 15 whenever an eligible entity presses it, with the normal 20-tick release check. **Gold** counts up to 15 entities; **Iron** counts up to 150. Weighted output uses vanilla rounding from 1 to 15 and checks every 10 ticks. An item stack counts as one entity, regardless of its item count. Wooden sensitivity respects the entity trigger-exempt flag; weighted sensitivity counts those entities as the native weighted plates do. Click sounds match the selected behavior.

Use the Configurizer or sneak-right-click to open its dialog. The shared material list includes the main material collection and **Trapdoors**. Use the mouse wheel or drag the scrollbar to browse artwork.

Configure output channels and choose **Pressed**, **Released**, or **Disabled**. These choices control the channel output. Pressed mirrors the physical analog level; Released supplies level 15 only while the plate is empty; Disabled supplies zero. Ordinary physical redstone always follows the selected sensitivity. Right-click the option to cycle backwards. A plate remains an independent source, so releasing another linked toggle does not turn off a physically pressed plate.

Settings survive saving and loading. An already pressed plate resumes its normal release check after loading. Carried plates use the vehicle's actual world pose to detect entities, preserve the selected 20/10-tick release polling, and publish channel changes through the vehicle's shared signal bridge.
