# Vandor Labs 2.9 development

Version 2.9 begins incremental multiversion integration while retaining Forge 1.12.2 as the default and behavioral reference. NeoForge 1.21.1 is under development; the version bump does not establish modern feature parity or release readiness.

The first milestone is non-vehicle parity: programmable building families, screens and controls, materials, tools, storage, machines and standalone mechanisms. Segmented Doors, moving arms, ramps, Copy Controllers and turntables remain in scope, including shared surface and recovery infrastructure. Vehicle entities, driving, articulation and vehicle-specific carried, cockpit and gear integration follow only after the complete non-vehicle candidate passes the full release gate.

Historical native implementations are integration references. Each family still needs current-code persistence, permissions, runtime and rendered acceptance before support can be claimed. Existing Forge registry IDs, saved behavior and recovery guarantees must be preserved. See the [coverage inventory](../development/feature-coverage.md) for family status and the [architecture](../development/multiversion.md) for target boundaries.

The previous feature overview remains available in [Vandor Labs 2.8](version-2.8.md).

The experimental native build includes the plain matter block, matter/alloy ingots, and the programmable cube with its Appearance editor, six face overrides and bounded joined images. Creative shift-click and the main-hand Configurizer share the authenticated cube dialog. Configured items retain their settings, and native geometry is compared with the actual Forge renderer.

Native cubes also support installed filesystem PNG categories, path-based saved identities and reloads of already discovered files. Open pickers keep their selection while portrait or wide artwork changes dimensions. A missing client entry preserves its saved ID and returns when the file is installed at the same path and Minecraft restarts. New entries still require a restart; Inventory block sampling is a separate workflow: the native cube can select default-state block artwork without consuming the sampled stack, while retaining configured and unavailable material identities.

The native Trigger adds Off/On or signal-band artwork, exact-level matching and Front Only/Sides choices through shared dialog controls. Cube and Trigger use the same native material picker. Handheld transmitters retain stationary channel sources across saves and unloaded origin chunks; their four ranges use the existing crafting patterns.

Native Push Buttons and Rocker Switches retain configurable mounts, saved channel banks and inline transmission ranges. Both use the same General/Redstone dialog: mount edits commit on Done, while valid transmission edits apply immediately. Their authored shapes, configured items and physical redstone behavior are covered by focused native checks.

Native Industrial and Small Power Levers now use the same mounted-control dialog and channel banks. The Size option retains Compact and Large legacy variants, and configured items preserve their mount and size. Focused save/restart, emitted-model and exact-JAR client checks cover both families.

Native four-step levers and sliders share that dialog and renderer. Throttles retain their three selectable styles; the Redstone page sets the two middle detents. Type, mount and limit edits commit together on Done, while inline transmission remains immediate.

Other native families, optional material providers, copy tools, carried assemblies, hardware shader acceptance and matter-production machines remain incomplete. Dedicated-server checks, emitted-model checks and ordinary client interaction checks are separate acceptance requirements; this remains a development build.
