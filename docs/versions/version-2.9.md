# Vandor Labs 2.9 development

Version 2.9 begins incremental multiversion integration while retaining Forge 1.12.2 as the default and behavioral reference. NeoForge 1.21.1 is under development; the version bump does not establish modern feature parity or release readiness.

The first milestone is non-vehicle parity: programmable building families, screens and controls, materials, tools, storage, machines and standalone mechanisms. Segmented Doors, moving arms, ramps, Copy Controllers and turntables remain in scope, including shared surface and recovery infrastructure. Vehicle entities, driving, articulation and vehicle-specific carried, cockpit and gear integration follow only after the complete non-vehicle candidate passes the full release gate.

Historical native implementations are integration references. Each family still needs current-code persistence, permissions, runtime and rendered acceptance before support can be claimed. Existing Forge registry IDs, saved behavior and recovery guarantees must be preserved. See the [coverage inventory](../development/feature-coverage.md) for family status and the [architecture](../development/multiversion.md) for target boundaries.

The previous feature overview remains available in [Vandor Labs 2.8](version-2.8.md).

The experimental native build includes the plain matter block, matter/alloy ingots, programmable cubes and the Configurizer for cube editing. Cubes retain configured items, face overrides, texture layouts and joined artwork, using the shared current Appearance controls and saved-data rules. Native geometry is compared with the actual Forge renderer.

Shared startup filesystem discovery preserves saved IDs, categories and existing failure behavior. Native filesystem artwork reload, custom block sampling and the matter-production machines remain under development. Saved external material IDs are retained even when artwork is unavailable.
