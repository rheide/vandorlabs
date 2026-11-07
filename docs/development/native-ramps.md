# Native standalone Ramps

The conversion keeps standalone Ramp Controllers, their Slab variant and generated controlled-ramp cells in scope. Vehicle adapters remain deferred. Existing portable `RampControllerData`, `RampCellData`, `ControllerPlatform`, `RampGeometry`, `RampAxis` and `VerticalRampGeometry` provide scalar persistence, bounded platform discovery and motion geometry. They do not replace the controller's snapshot/journal ownership protocol.

## Source settings and item baseline

`exportRampSettingsSourceReference` freezes 256 actual Source observations: both controller roles, all sixteen metadata inputs, fresh factory defaults and seven saved-data profiles. Each observation calls the real tile reader/writer, `ProgrammableSettings.capture`, configured pick and block bounding box. The expanded `ramp-settings-source.jsonl.gz` is 464656 bytes, SHA-256 `285ce8fc7ad07ba72b83383dbdc2f338a521630d08727369d847f0241b0bc9c7`. This is a fixed-clock memory-world callback fixture; it does not establish real placement, mining, generation, journal durability or player authorization. No platform or generated cell is transferred.

The registry IDs are `programmable_ramp` and `programmable_ramp_slab`, sharing tile identity `programmable_ramp`; generated cells use `controlled_ramp`. Full-controller metadata preserves horizontal faces and active bits plus vertical scan faces. Slab metadata encodes six scan faces and two halves; activity is derived from the tile. Its body remains horizontal half height for every scan face. Scan direction and horizontal travel direction are distinct, including UP/DOWN placement.

Fresh factory state and reading an empty saved compound differ. Existing scalar migration also normalizes old LEFT travel by reversing offsets into RIGHT travel. Half-step endpoints, interpolation, escalator, vertical-layout and moat settings supplement `RampControllerData`; native settings must compose those fields rather than silently dropping them. Configured pick writes a private `RampSettings` choice compound, excluding captured blocks and journal state. Actual placement consumes that compound through `configureHalfOffsets` and then applies channels, so configuration refusal and gameplay activation refusal must be distinguished. Copied settings use the same configuration callback and can update the channel/transmission footer independently; their actual callback oracle is the next prerequisite.

## Ownership boundary

Native generation and public activation remain unimplemented. A future host must keep `onLoad` local, defer registration and recovery until installed identity is confirmed, preserve unknown or failed snapshot envelopes inert, and refuse unsupported ownership-bearing state. No active captured platform may be normalized into choices or released before durable recovery. Journal publication must reuse `JournalFiles` and prove asynchronous completion, abrupt restart and partial-save ownership separately from these scalar fixtures.
