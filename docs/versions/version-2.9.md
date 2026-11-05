# Vandor Labs 2.9 development

Version 2.9 begins incremental multiversion integration while retaining Forge 1.12.2 as the default and behavioral reference. NeoForge 1.21.1 is under development; the version bump does not establish modern feature parity or release readiness.

The first milestone is non-vehicle parity: programmable building families, screens and controls, materials, tools, storage, machines and standalone mechanisms. Segmented Doors, moving arms, ramps, Copy Controllers and turntables remain in scope, including shared surface and recovery infrastructure. Vehicle entities, driving, articulation and vehicle-specific carried, cockpit and gear integration follow only after the complete non-vehicle candidate passes the full release gate.

Historical native implementations are integration references. Each family still needs current-code persistence, permissions, runtime and rendered acceptance before support can be claimed. Existing Forge registry IDs, saved behavior and recovery guarantees must be preserved. See the [coverage inventory](../development/feature-coverage.md) for family status and the [architecture](../development/multiversion.md) for target boundaries.

The previous feature overview remains available in [Vandor Labs 2.8](version-2.8.md).

The experimental native build includes the plain matter block, matter/alloy ingots, and the programmable cube with its Appearance editor, six face overrides and bounded joined images. Creative shift-click and the main-hand Configurizer share the authenticated cube dialog. Configured items retain their settings, and native geometry is compared with the actual Forge renderer.

Native cubes also support installed filesystem PNG categories, path-based saved identities and reloads of already discovered files. Open pickers keep their selection while portrait or wide artwork changes dimensions. A missing client entry preserves its saved ID and returns when the file is installed at the same path and Minecraft restarts. New entries still require a restart; PNG reloads do not implement custom block sampling.

Other native families, custom block sampling, copy tools, carried assemblies, hardware shader acceptance and matter-production machines remain incomplete.
