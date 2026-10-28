# Redstone transmitters and channel range

A receiver keeps its input-channel selection. A transmitting block needs an inserted Redstone Transmitter item: without one, its switches, wired input or control rows do not publish any channel output. Physical redstone behavior still belongs to the block.

| Transmitter | Radius |
| --- | --- |
| Short Range | 16 blocks |
| Medium Range | 64 blocks |
| Long Range | 256 blocks |
| Infinite Range | The same dimension |

Finite ranges are spheres measured between block positions, including the stated boundary. Receivers have no range upgrade. Receiving on several channels uses the strongest in-range signal, from 0 to 15. A channel can therefore be ON near one transmitter and OFF elsewhere.

Shift-right-click a transmitter to enter its comma-separated channel list. Its item name includes the selected channels. Open a block's Redstone settings and choose **Transmitter...** to insert or remove the item using the shared inventory slot. **Channels...** edits the inserted item. Analog/digital modes, slider limits, pulses and activation polarity remain in the block dialog. Screen rows and cockpit controls have separate transmitter slots. Remove installed transmitters before deleting or moving their rows.

Linked switches and sliders within the operated transmitter's range share their per-channel latch levels, allowing one control to turn another off. Outside that range, their saved levels remain independent. A receiving linked control also has its own transmitter, so it may extend coverage to nearby receivers. Different transmitter ranges can make the connection asymmetric: a short-range switch cannot switch off a distant long-range switch outside its own radius. A continuing in-range ON source wins over OFF, just as with ordinary independent redstone sources. Momentary controls keep their own pulse timers.

Regular right-click with the item toggles its channels at the point of use. This creates a stationary source saved with the dimension; moving the item afterward does not move that source. A later use clears matching manual sources whose origins are within the operated item's range before publishing its new state. Operating a nearby linked block control also clears those matching manual sources in its own range. Manual sources survive saving and restarting, independently of a loaded host block.

To copy channel selection in a crafting table, place the original in the center and a second transmitter immediately to its left or right. Take the configured second transmitter from the result slot. The original returns unchanged, and the second transmitter keeps its own range tier. Other ingredients or positions are refused.

The former wireless transmitting block is now **Redstone Transmitter Block**, registry ID `vandorlabs:redstone_transmitter_block`. The handheld item uses `vandorlabs:redstone_transmitter` with four metadata variants. Existing transmitter-block IDs are intentionally not remapped. Existing channel selections do not create free transmitter items: install the appropriate item to enable channel output.

## Runtime cost

Channels remain dimension-local and block participants remain limited to loaded tiles. Changing a finite source visits 27 receiver-grid buckets in its channel and updates only receivers inside its old or new coverage. Each affected receiver checks at most 81 source-grid buckets across the three finite range tiers, plus infinite sources. The indexes contain loaded participants rather than world blocks; channel activation does not scan terrain or load chunks. Infinite-range activation necessarily reaches every loaded subscriber on its channels.

The receiver stores its last complete channel-level bank, so unchanged values send no repeated consumer callbacks, while a changed channel still updates ALL-channel controls even when the maximum signal stays the same. Transmitter settings are cached until their saved item compound changes. Grid entries leave the indexes when members unload; world unload clears the network. Moving vehicles update existing links only when their physical block positions change. Per-source range and position are retained in new coupling-recovery journals; previous global-channel journal records keep their legacy decoder.

Handheld activation also inspects the saved manual-source list to remove matching origins. It has no continuous player or world scan. Dense networks inside one transmitter's coverage still require work proportional to the receivers that actually change; range cannot eliminate that inherent fan-out.
