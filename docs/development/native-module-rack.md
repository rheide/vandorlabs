# Native Module Rack adapter

The NeoForge adapter reuses the Forge Rack's authored shell, Blank and Autopilot cassette artwork, six-face hit layout, proportional output policy, compact tab widths, module configuration and pause-aware motion envelope. Its native placement delegates to the same view-facing adapter used by Programmable Light and the resource ports.

The five exterior bounding faces accept the configured housing material; the front and recessed interiors retain authored artwork. Classification uses facing and vertex planes, so atlas aliases cannot recolor the panel. Autopilot fronts use a small geometric cyan/amber route indicator and never sample the archived lettering strip.

Four internal bays each own one item. They are not an automation inventory or an item capability. Ordinary insertion consumes one held module; extraction first checks inventory room. Mining separates the empty shell from its modules. The Configurizer and empty-hand rim interaction open the same configuration-only menu. Module replacement sequences, the exact installed tile, loaded chunks, actor permissions, range and window identity guard settings changes. Packets carry bounded choices rather than inventories or arbitrary NBT.

## Channels and navigation

The Rack's output and its four module receivers use the existing native channel bank. Registration is deferred after chunk loading, and removal unregisters the old identity. Local output and wireless transmission share the proportional levels 0, 4, 8, 11 and 15. Each Autopilot Module retains its own receiving channels, activation mode and route marker. Mounted edits update the actual module stack before extraction; slot replacement, relocation and reload preserve the module choices and opaque item fields independently from the rack housing/output configuration.

Vehicle navigation is implemented on Forge 1.12.2. Native modules retain their navigation data and receiving state, but do not create, convert or drive native vehicles. Their General tab states this limitation. The physical old-Autopilot migration belongs to the Forge adapter; a native Rack's legacy item decoder does not imply general cross-version world conversion.

## Rendering and publication

Stationary shells, indicators and seated modules are ordinary chunk geometry. A Rack has no tile ticker or idle block-entity renderer. Prepared module records are shared across equivalent states. Terrain combinations retain at most 64 KiB of list references per finish, with at most sixteen shell finishes per facing; atlas reload replaces the complete model generation.

Only active module timelines enter the solid render stage. They use one lazy private 64 KiB direct buffer, flushing at most 1,024 vertices per batch. It cannot flush or modify Minecraft's shared batch and is closed on reload or unload. The stage's camera transform is applied and restored separately from the module pose. There is no idle draw or buffer allocation.

The active module clock pauses with the client and does not compare client world time against server timestamps. Repeated or older authoritative updates cannot restart a timeline. Entries follow the authoritative Rack incarnation across client tile replacement and are discarded on world replacement or unload.

A moving cassette starts after a fresh successful terrain upload removes its seated copy. The final handoff waits for an upload containing the seated cassette. Active Racks retain reference-counted section-observation leases. These leases attach completion callbacks only to their active sections and do not replace a Segmented Door's publication token. Acknowledgement requires the current task, new compiled identity, successful upload and unchanged section origin. The last lease leaves no idle section observer. Off-view timeouts settle the cosmetic state instead of retaining an invisible animation indefinitely.

## Saved ownership

Source module stacks retain their original private item data inside the one native module item. Unsupported native components or malformed ownership keep the complete original Rack tag inert. Public tile updates and arm payload projections include typed appearance and configuration only; they omit module items, opaque archives and navigation progress.

Captured Racks use the existing arm write-ahead journal. Its durable snapshot owns the modules before source removal. The typed installation hook clears the old live Rack without running ordinary mining drops; the captured harvest receipt later releases each module once. Rollback installs the original Rack data through the same strict factory and round-trip checks. This hook does not weaken unknown-field, capability or foreign-owner rejection.
