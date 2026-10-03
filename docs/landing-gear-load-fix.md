# Landing gear extension on load

The load callback previously called `placed()`, which forces a redstone evaluation. Gear defaults to Redstone ON mode, but ordinary right-click can extend it manually between power edges. A saved, manually extended gear with no power therefore loaded as retracted: the forced evaluation replaced the saved EXTENDED block state with the unpowered target.

The block metadata and NBT already retain the extension target, progress, size, length, mode, channel and last evaluated signal. The fix separates load from placement: loading registers the channel, lets the load batch settle, and evaluates changes without forcing the target. Manual extension survives an unchanged input; real input changes continue to drive On/Off mode. Newly placed and newly configured automation still evaluates immediately.

There was also an unload side effect. Channel unregistration calls `setChannelSignal(false)`; previously this could retract the tile during unload/invalidation and overwrite its saved state. Detached tiles now suppress signal evaluation while unregistering. Other loaded channel members still receive normal channel updates. This preserves loaded-only redstone behavior and introduces no chunk loading or tick scan.

## Regression checks

The real Forge world test extends gear manually in Redstone ON mode, serializes its metadata/NBT, unloads and replaces the tile, restores the saved metadata/NBT and runs the deferred load callback. It verifies the extension target and progress, then adds/removes physical power to confirm subsequent signal edges still operate normally. It repeats for Small, Medium and Large. A manually retracted, remotely powered member is restored alongside its source to verify that registration order does not reset the manual override. A powered channel-member unload also verifies that unregistration preserves the saved target and last signal. Existing placement, inverse mode, channel and footprint checks remain active.

These checks exercise save decoding and load/unload callbacks in the disposable runtime world. They do not require opening or changing an owner's world.
