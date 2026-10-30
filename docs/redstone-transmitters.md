# Redstone transmitters and channel range

Transmitting blocks have built-in range settings. Open the block's **Redstone** tab and use the **Range** slider: **4, 8, 16, 32, 64, 128 or 256 blocks**. New blocks default to 16 blocks. No transmitter item is required or accepted.

Ranges are spheres measured between block positions, including the stated boundary. Receivers keep their separate input-channel selection and use the strongest in-range signal, from 0 to 15. A channel can therefore be ON near one transmitter and OFF elsewhere.

**Local Redstone: On/Off** controls physical redstone output independently of channels. Existing switches, plates and other physical outputs default to On. Screens, cockpits and wired transmitters keep their previous absence of physical output until enabled. Off keeps channel transmission active without powering adjacent blocks. Screen rows and cockpit controls share their host's range and local-output setting, while retaining independent target channels. A blank cockpit target uses its shared default channels. The physical output of a screen or cockpit is the strongest active control level. A wired Transmitter Block reads its selected input port and, when local output is On, emits from the opposite side.

The channel field applies with Enter, clicking outside it or Done. Analog/digital modes, slider limits, pulses and activation polarity remain in each block's dialog. Passive receiver dialogs keep their existing controls.

The handheld transmitter items remain in the creative catalog with their original tiers:

| Handheld transmitter | Radius |
| --- | --- |
| Short Range | 16 blocks |
| Medium Range | 64 blocks |
| Long Range | 256 blocks |
| Infinite Range | The same dimension |

Linked switches and sliders within the operated transmitter's range share their per-channel latch levels, allowing one control to turn another off. Outside that range, their saved levels remain independent. A receiving linked control also has its own transmitter, so it may extend coverage to nearby receivers. Different transmitter ranges can make the connection asymmetric: a short-range switch cannot switch off a distant long-range switch outside its own radius. A continuing in-range ON source wins over OFF, just as with ordinary independent redstone sources. Momentary controls keep their own pulse timers.

Regular right-click with the item toggles its channels at the point of use. This creates a stationary source saved with the dimension; moving the item afterward does not move that source. A later use clears matching manual sources whose origins are within the operated item's range before publishing its new state. Operating a nearby linked block control also clears those matching manual sources in its own range. Manual sources survive saving and restarting, independently of a loaded host block.

To copy channel selection in a crafting table, place the original in the center and a second transmitter immediately to its left or right. Take the configured second transmitter from the result slot. The original returns unchanged, and the second transmitter keeps its own range tier. Other ingredients or positions are refused.

The placed **Redstone Transmitter Block** uses registry ID `vandorlabs:redstone_transmitter_block`; worlds using its earlier block ID are remapped on load. The handheld item retains `vandorlabs:redstone_transmitter` and its four metadata variants. Saved module channels and finite ranges remain effective until edited; an old unlimited block range becomes 256 blocks. Previously inserted modules remain saved for recovery when the host is mined, and changing built-in settings does not consume or alter them. New blocks do not require hardware. Surplus modules from older per-row or per-control layouts remain recoverable as well.

## Runtime cost

Channels remain dimension-local and block participants remain limited to loaded tiles. Changing a finite source visits 27 receiver-grid buckets in its channel and updates only receivers inside its old or new coverage. Each affected receiver checks at most 81 source-grid buckets across the three finite range tiers, plus infinite sources. Source buckets keep separate strength banks from 1 through 15 and a cached maximum. Queries visit populated local buckets in descending strength and stop at the first active source inside its sphere; equal analog sources do not cause a linear scan. A bounded pool reuses the query buffers. The indexes contain loaded participants rather than world blocks; channel activation does not scan terrain or load chunks. Infinite-range activation necessarily reaches every loaded subscriber on its channels.

The receiver stores its last complete channel-level bank, so unchanged values send no repeated consumer callbacks, while a changed channel still updates ALL-channel controls even when the maximum signal stays the same. Built-in settings are read from the host tile; legacy item settings are cached until their saved compound changes. Grid entries leave the indexes when members unload; world unload clears the network. Moving vehicles update existing links only when their physical block positions change. Per-source range and position are retained in new coupling-recovery journals; previous global-channel journal records keep their legacy decoder.

Handheld activation also inspects the saved manual-source list to remove matching origins. It has no continuous player or world scan. Dense networks inside one transmitter's coverage still require work proportional to the affected receivers. When many overlapping analog sources share a channel, finding each receiver's local maximum can inspect candidates near a sphere boundary when their higher-level sources fall outside the exact radius. Range removes distant work, but cannot eliminate dense local fan-out or expensive mechanism callbacks.

A focused scalability check with 2,049 subscribers and 100 alternating short-range changes visited 2,700 receiver buckets and 200 nearby candidates, delivering 200 callbacks. The previous global broadcaster would notify 204,900 receivers for that arrangement. The measured dispatch total was about 1.04 ms using lightweight receiver fixtures; actual mechanism work and client rendering are additional costs. A dense analog check with 1,024 overlapping level-8 sources and 2,048 receiver queries inspected 2,048 source candidates instead of a previous linear 2,097,152, taking about 3.03 ms in the fixture. These timings do not establish client FPS. Separate native wireless checks cover all six port directions and all 16 signal levels.

Newly configured blocks need only their base building materials in Survival Copy Controllers. If an older block still owns a saved module, copies require the matching legacy item separately from the base block. Filled host items cannot be consumed as empty building materials. Creative copying retains its free-copy behavior. Native mining, Creative mining and explosions eject installed hardware before configured host drops can duplicate it; permission probes and active ownership journals retain the original inventory. Captured arm payload mining includes modules in its existing durable harvest journal.
