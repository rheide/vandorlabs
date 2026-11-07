# Vandor Labs 2.9 development

Version 2.9 begins incremental multiversion integration while retaining Forge 1.12.2 as the default and behavioral reference. NeoForge 1.21.1 is under development; the version bump does not establish modern feature parity or release readiness.

The first milestone is non-vehicle parity: programmable building families, screens and controls, materials, tools, storage, machines and standalone mechanisms. Segmented Doors, moving arms, ramps, Copy Controllers and turntables remain in scope, including shared surface and recovery infrastructure. Vehicle entities, driving, articulation and vehicle-specific carried, cockpit and gear integration follow only after the complete non-vehicle candidate passes the full release gate.

Historical native implementations are integration references. Each family still needs current-code persistence, permissions, runtime and rendered acceptance before support can be claimed. Existing Forge registry IDs, saved behavior and recovery guarantees must be preserved. See the [coverage inventory](../development/feature-coverage.md) for family status and the [architecture](../development/multiversion.md) for target boundaries.

All eleven standalone Canopy and cockpit-glass components are now available in the experimental native build with their original recipes, configured items, dedicated editor, joined shells and authored world/item artwork. Their motion uses the pause-aware client clock while authoritative collision and reservation timing stay on the server. Loaded membership repair preserves accepted legacy absent-root saves and retained failed owners. Follow the [staged Canopy construction guide](../canopies.md#standalone-construction-in-the-experimental-native-build). Vehicle and carried integration remain deferred; this focused family acceptance is not a full release gate.

Connected Duplifier Undo now captures all selected blocks before applying the first change. This fixes linked settings such as Segmented Door direction, layout, speed and channels being left at their copied values after Undo. Physical contents remain outside settings history.

Forge canopy placement also validates stored custom footprints before changing blocks. Foreign blocks and supports remain intact when placement is refused, and the held stack keeps its data. The native family reuses this same placement preflight.

Redstone channel input keeps the shared range of 1–2,147,483,647, with zero disconnected and up to 64 channels per bank. Forge Copy Controllers now use that same range for checked channel translation; Coupler and Segmented Door fields retain full-width banks, and saved cockpit/coupling channels preserve high IDs. Converted native editors use the same int-backed policy. See the [channel input contract](../development/channel-input-range.md).

Flat Programmable Walls, Glass and Porthole Walls now anchor against a clicked horizontal support in both versions. Continuing a panel above, below or along its plane retains its selected depth. Full-depth Porthole Blocks keep their existing full-cell placement. See the [placement contract](../development/wall-against-placement.md).
Forge Copy Controllers now discover complete connected Surface pads regardless of individual selected textures. Their drawing groups remain appearance-specific, and existing Turntable artwork boundaries are unchanged.

The previous feature overview remains available in [Vandor Labs 2.8](version-2.8.md).

The experimental native build includes the plain matter block, matter/alloy ingots, and the programmable cube with its Appearance editor, six face overrides and bounded joined images. Creative shift-click and the main-hand Configurizer share the authenticated cube dialog. Configured items retain their settings, and native geometry is compared with the actual Forge renderer.

Native cubes also support installed filesystem PNG categories, path-based saved identities and reloads of already discovered files. Open pickers keep their selection while portrait or wide artwork changes dimensions. A missing client entry preserves its saved ID and returns when the file is installed at the same path and Minecraft restarts. New entries still require a restart; Inventory block sampling is a separate workflow: the native cube can select default-state block artwork without consuming the sampled stack, while retaining configured and unavailable material identities.

The native Trigger adds Off/On or signal-band artwork, exact-level matching and Front Only/Sides choices through shared dialog controls. Cube and Trigger use the same native material picker. Handheld transmitters retain stationary channel sources across saves and unloaded origin chunks; their four ranges use the existing crafting patterns.

Native Push Buttons and Rocker Switches retain configurable mounts, saved channel banks and inline transmission ranges. Both use the same General/Redstone dialog: mount edits commit on Done, while valid transmission edits apply immediately. Their authored shapes, configured items and physical redstone behavior are covered by focused native checks.

Native Industrial and Small Power Levers now use the same mounted-control dialog and channel banks. The Size option retains Compact and Large legacy variants, and configured items preserve their mount and size. Focused save/restart, emitted-model and exact-JAR client checks cover both families.

Native four-step levers and sliders share that dialog and renderer. Throttles retain their three selectable styles; the Redstone page sets the two middle detents. Type, mount and limit edits commit together on Done, while inline transmission remains immediate.

Native world transmitters and receivers add six-face analog/digital ports, strongest-input reception and separate On/Off output levels. Their dedicated editor shares the transmission controls, and ordinary dropped items retain the existing plain-item behavior.

Other native families, optional material providers, copy tools, carried assemblies, hardware shader acceptance and matter-production machines remain incomplete. Dedicated-server checks, emitted-model checks and ordinary client interaction checks are separate acceptance requirements; this remains a development build.

The Forge 1.12.2 compatibility checkpoint has passed the full release gate. The [development baseline](../development/multiversion-checkpoint.md) records the validated artifact and the preserved branches for resuming native work; this does not mark the partial 1.21.1 build release-ready.

Mounted controls in the native build use one clean hover selection box, matching Forge. Hovering no longer draws the detailed collision grid or incurs its edge-enumeration cost.

Texture quicksearch excludes the common mod namespace from ordinary words, so searching `vand` finds Vandor artwork instead of every mod texture. Names, categories and texture filenames remain searchable; explicit namespace terms such as `vandorlabs:` are still supported.

The experimental native build now includes programmable plain walls, diagonal walls, thin, full-depth and diagonal portholes, and diagonal glass. Diagonal walls retain the three sizes, slope reversal, inside/outside fills and join settings. Porthole panes use the current glass palette or programmable artwork, including Off/static/animated playback. These blocks share the existing housing editor, frame controls, configured item data and contained-fluid adapters. These use the current palette, frame materials, joined contacts and shared picker controls. Integration with the remaining glass families and broader rendered acceptance remain in progress.

Ordinary and Large Doors are enabled in the experimental native build. Shared geometry, authored assets and credential behavior preserve current Forge rules. Focused native checks cover ordinary linked placement, physical reader inventories, opaque recovery items, restart, moved-leaf targeting and the dedicated Door/Frame/Redstone/Security/Glass editor. Large Door structure and geometry have separate source comparisons and restart checks. The ordinary programmable Door is now enabled with its shared renderer, padded configured icons, dedicated editor and physical credential readers. Retained rotating/sliding IDs remain loadable while the creative tab exposes the canonical item. The Large Door is also enabled with the same renderer and dedicated editor. Focused public-registration checks cover survival nine-part placement, X/vertical motion, moved-leaf use, credential ownership, old-schema layouts, per-part outlets and process restart. Segmented Doors are also enabled with per-section Door/Glass/Redstone editing, moved-panel interactions, configured picks and stationary fluid ownership. Focused real-client checks cover survival placement, all direction/angle profiles, sneak/Configurator access, buckets, section-boundary visibility and mining. Closed terrain-render handoff now passes focused upload-delay, fallback and rendered-pixel checks. Regular and diagonal Trapdoors are also enabled with source-matched placement and assemblies, dedicated General/Redstone editing, configured picks and harvest, moved-leaf interactions and contained-fluid rendering. Focused canonical restart and actual-client checks pass; broader workflows and native release acceptance remain pending.


Screen pulse expiry now survives switching control artwork away and back: restoring the screen keeps the original twenty-tick deadline. Shared activation and legacy artwork migration retain the existing Forge dialogs and saved controls while the native screen family remains in preparation.

Native flat and diagonal wall/porthole placement now preserves original clicked-face coordinates. Focused source comparisons and actual Survival uses cover depth, anchoring and replaceable target cells.

Programmable Lights now share save decoding, brightness/activation policy and cached housing meshes across versions. Forge artwork, lighting and saved defaults remain unchanged. Housing emission reuses immutable pixel-space geometry within a bounded cache and retires atlas references after each draw; Programmable Light, Light Frame and Light Slab are enabled in the experimental native build with dedicated editing, configured items and source-matched grouping. Focused canonical recipe, Survival, restart, editor and production-render checks pass. Standalone propulsion and particle families remain in conversion; broader native release acceptance is still pending.

Ordinary and Large native Doors also use original clicked-face coordinates for depth and hinge choice, including legacy float half-click ties. Frozen actual item callbacks, real Survival placement and focused ordinary/Large client checks preserve the existing player paths.

Native failed-load recovery preserves original component and attachment envelopes, including unknown types and decoders that return no value or fail internally. Inert Trapdoor picks and mining retain the complete failed snapshot in an opaque recovery item. Its ordinary placement is refused without consuming it; normal configured Trapdoors keep their existing placement and harvest behavior.

Standalone propulsion and redstone-light blocks are enabled in the experimental native build, including the original 27 block identities and seven catalog items/recipes. Connected square, hexagonal and wedge artwork, shape sampling, dedicated controls and natural particle effects have focused canonical player and saved-world checks. Configured Survival placement, ordinary stone-tool harvesting and all 21 padded item variants are covered. Lighting workers use prepared auxiliary data, and real Off/On interactions update stored client light. Vehicle thrust and carried effects remain deferred.

The experimental native build now registers all seven standalone Programmable Screen roles, including their dedicated editor, configured items, joined displays and non-Vehicle Chip content. Vehicle Mode remains deferred.

The Duplifier is also enabled with its original recipe, dedicated Apply Settings window, connected application, settings-only Undo and off/on/connected artwork. Both inventory and crafting-table grids support copying settings onto converted block items while retaining the tool and its saved options. Ramp integration remains in progress.

The experimental native build also enables standalone Card and Data Chip readers, including their dedicated configuration, credential inventory and authored mounted artwork. The Card / Chip Programmer is also enabled with its original four-cell identity, three retained slots, dedicated Journal editor, Program/Copy operations and authored world/item artwork. Focused player and saved-world checks cover exact inventory ownership, mining, low-TPS timers, resource reload and recovery. Follow the [native construction guide](../card-chip-programmer-neoforge.md). Vehicle Chip operations remain deferred.


Ordinary Programmable Glass is enabled in the experimental native build with its original recipe, configured variants, dedicated Glass and frame editing and two-sided contained fluids. Its connected geometry, actual picking, world and item artwork, resource reload and saved restart have focused checks. The diagonal-glass recipe uses this original ingredient. Canopy and cockpit glass remain separate conversion work.


Programmable Storage is enabled in the experimental native build with its original recipe, independent 27-slot inventory, ordinary inventory menu and dedicated material editor. Configured placement and mining preserve settings while keeping stored items separately owned. Source-matched world and padded item artwork, automation, resource reload and saved restart have focused canonical checks.

Segmented Door Tile artwork now retains the same physical scale and phase across solid and Window segments in both versions. Segment bodies are two pixels thick; the top border remains four pixels. Selection, collision, fluid regions, moving models and settled terrain use the shared geometry. Existing settings, registry IDs and control indices are unchanged.
