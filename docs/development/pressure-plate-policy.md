# Programmable Pressure Plate policy

`PressurePlateSettings` supplies the same sensing, output and release rules to the Forge and NeoForge adapters. Wood emits full power when a trigger-eligible entity is present. Gold reaches full power at 15 entities, and Iron at 150. Weighted modes count entities rather than items within a stack and retain the released float-ceiling calculation.

The physical plate remains an independent analogue source. Disabled, Pressed and Released select its channel output; they do not latch a press or substitute a received channel signal for physical sensing. Local redstone follows the physical plate when enabled. A raised plate reacts to entity contact, while a depressed plate schedules release checks every 20 ticks for Wood and 10 for weighted modes.

The native adapter keeps world access on the owning server thread and defers channel registration and sampling until after loading. Its public projection contains settings and current output without opaque saved envelopes. Unsupported own data remains inert with its original saved fields retained. Native player acceptance and public availability are tracked separately from this shared policy.
