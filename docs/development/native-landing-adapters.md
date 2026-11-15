# Native landing adapters

The native Track and standalone side-wheel adapters preserve the released block IDs. The canonical Track and three stationary side wheels are enabled by default in ordinary registration and creative/search. Historical Armoured/Bogie Track aliases remain hidden. The four older integrated telescoping families are separate work. Existing modular arms remain enabled independently.

Tracks use the shared Java 8 topology, mounting, appearance and mesh-selection policies. Stationary tracks bake into terrain; a moving arm publishes one immutable descriptor for every mounted Track cell. Collision and moving rendering consume that descriptor instead of rediscovering world topology. Descriptor decoding bounds coordinates, rejects duplicate cells and verifies membership against the saved arm assembly. Missing or unsupported moving descriptors retain the original inert snapshot.

Historical arm and Track relative positions use the released X26/Y12/Z26 packing through `LegacyBlockPosition`. They must not be decoded with modern `BlockPos.of`. Track style messages use a separate fixed 29-byte packet, leaving the existing arm settings packet unchanged. Source-compatible hidden Armoured/Bogie IDs share the canonical Track item.

Standalone side wheels share the released metadata and two-by-two footprint policy with Forge. Every reserved Large Wheel cell supplies clipped authored collision and a single configured wheel drop; removing one cell clears its companions. Static wheels have no block entities or tickers. Slab support includes vanilla, programmable and programmable-light slabs.

Focused server checks cover ordinary Survival wheel placement, all horizontal facings, per-cell mining and cold saved palettes. The Track matrix covers 108 mounting/size/style/motion profiles with complete strokes, reversal, saved descriptors and mining repairs. The real client path covers joined placement, previous-value style selection, settings copying/Undo, manual motion and mining. Emitted geometry covers 144 authored profiles and all three side-wheel inventory bounds. Public catalog checks run without enabling private feature flags.

Exact arm shapes use a weighted weak-owner cache, invalidated by immutable posed-box identity; lighting uses the Source zero-opacity policy without rebuilding selection geometry. The former repeated shape optimizer stalled the server thread during chunk lighting; the focused matrix now completes without that path. These checks do not establish hardware shader-pack or vehicle acceptance.
