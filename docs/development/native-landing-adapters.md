# Native landing adapters

The native Track and standalone side-wheel adapters preserve the released block IDs. They remain private behind `vandorlabs.trackRuntime` and `vandorlabs.legacyLanding` until their complete player and rendering paths are accepted. Existing modular arms remain enabled independently.

Tracks use the shared Java 8 topology, mounting, appearance and mesh-selection policies. Stationary tracks bake into terrain; a moving arm publishes one immutable descriptor for every mounted Track cell. Collision and moving rendering consume that descriptor instead of rediscovering world topology. Descriptor decoding bounds coordinates, rejects duplicate cells and verifies membership against the saved arm assembly. Missing or unsupported moving descriptors retain the original inert snapshot.

Historical arm and Track relative positions use the released X26/Y12/Z26 packing through `LegacyBlockPosition`. They must not be decoded with modern `BlockPos.of`. Track style messages use a separate fixed 29-byte packet, leaving the existing arm settings packet unchanged. Source-compatible hidden Armoured/Bogie IDs share the canonical Track item.

Standalone side wheels share the released metadata and two-by-two footprint policy with Forge. Every reserved Large Wheel cell supplies clipped authored collision and a single configured wheel drop; removing one cell clears its companions. Static wheels have no block entities or tickers. Slab support includes vanilla, programmable and programmable-light slabs.

Focused server checks cover ordinary Survival wheel placement, all horizontal facings, per-cell mining and cold saved palettes. The first Track runtime check covers ordinary held-item placement, joined-foot formation, a complete Extender stroke and reversal, saved descriptor validation and rebuilding after mining. These checks do not establish the full Track mounting/size/motion matrix or client rendering acceptance.
