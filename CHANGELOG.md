# Changelog

## 1.5

- Batch compatible opaque Programmable Doors using cached geometry, CPU motion transforms and Forge's shared tile-renderer buffer. Preserve glass/custom-face and OptiFine rendering through the existing path, and support CodeChickenLib's vanilla item-renderer delegate.
- Add Large Programmable Door: one indivisible 3×3 opening with two 1.5×3 leaves and the regular door's appearance, movement, control-panel, placement-depth and redstone options. Any cell addresses the anchor; only the anchor renders. Placement validates all nine loaded cells before writing, and removal clears the assembly.
- Animate large doors over 12 ticks rather than 9, and lower their optional control panels to the regular door's center height, including collision and click regions.
- Guard collision clipping against disjoint boxes so opened large doors leave a traversable passage in every orientation and movement mode.
- Add White Glass doors: pale moving leaf rails around a large glass opening, with simple shallow rectangular handles on both faces. Crop/repeat the rail and pane textures at consistent proportions; keep the stationary frame independently optional.
- Thicken the moving borders of glass-rim doors and add Dark Glass using the Door Interior (Dark) material with contrasting light handles.
- Keep large-door inventory icons inside their slots with padding after GUI rotation.
- Add Bussard Classic/Modern, Deflector Amber/Blue and Nacelle 1–4 light artwork with matching On/Off textures. Preserve existing texture indices.
- Submit redstone-screen backgrounds as textured surfaces with normals and explicit lightmaps for shader compatibility. Add Up/Down row controls that move labels and complete channel lists together.
- Add Linear, Curve In and Curve Out spatial interpolation for Ramp and Filled Ramp modes. Keep endpoint heights, use the same profile for rendering/collision/rider motion, and preserve Linear for older saves. Include interpolation in configuration copying.

## 1.4

- Replace propulsion particle On/Off with Off/Light/Medium/Heavy. Keep existing On settings and emission unchanged as Light; Medium and Heavy add density and wider plumes around the original stream. Preserve direction, particle types, connected assembly centers, saved settings and copying.
- Match diagonal screen picking and the selection outline to the solid wedge, and use cached collision slices so its empty space remains accessible.

- Make complete redstone-screen rows clickable, add a saved editable header, and constrain edited text to the visible screen width. Copy headers and complete row definitions with the Duplifier, enabling screen copying on older saved tools while preserving subsequent exclusions. Match configured diagonal screen inventory icons to their solid housing.

- Batch redstone-screen panel geometry, avoid unused animation work, and provide distinct row-control inventory icons. Keep text clear on all mounting directions, preserve power while editing row labels, and send row-status updates only when their visible highlight changes.
- Add `Redstone...` to the artwork pickers of existing programmable screens, inputs and consoles. Configure independent labelled channel controls on each surface, with full-row interaction, editable headers, saved definitions and Duplifier copying. Limit half-height surfaces to four rows at readable text proportions and hide their headers in the world and editor. Remove the separate alpha redstone blocks and recipes.
- Rename Programmable Viewscreen to Programmable Screen in the interface, preserving its registry ID and existing worlds.

- Configure comma-separated redstone channel lists on switches, levers, buttons and channel-aware programmable blocks. Controls operate every listed channel; consumers respond when any listed channel is active. Preserve complete lists through save/load, picked items, Duplifier settings and joined assemblies, with automatic migration of existing single-channel settings.

- Extend diagonal chunk rendering to section-boundary positions whose conservative geometry remains inside the section. Keep tile rendering for actual overhangs, with a shared routing rule for model and tile paths.

- Keep Programmable Walls and Porthole Walls visible throughout the loaded render distance, matching diagonal walls instead of disappearing beyond the tile renderer's 64-block cutoff.

- Merge compatible coplanar Diagonal Wall faces during chunk construction, preserving texture interpolation, normals, clipping and two-sided visibility. Simple walls use 20 quads instead of 44.

- Fix corrupted triangles on chunk-rendered Diagonal Walls when using the vanilla block renderer. Use the standard baked-quad vertex layout and retain the existing surfaces, normals and lighting.
- Validate the actual vanilla output buffer as well as the Forge rendering path.

- Render static Programmable Diagonal Walls through cached chunk geometry. Preserve existing surfaces, material coordinates, two-sided faces and uniform lighting. Keep the existing tile renderer for section overhangs and portholes; see the [diagonal rendering notes](docs/performance/1.4/diagonal-chunks.md).
- Refresh nearby diagonal-wall chunk meshes when geometry settings arrive in a tile update packet.

- Reuse bounded Static/Off screen texture identifiers and the existing loaded-neighbor lighting sampler, reducing render-loop allocation while preserving texture paths, resource-pack resolution and light values.

- Reject offset-trapdoor collision scans in loaded sections whose live block palette contains no trapdoors. Avoid per-cell world reads for ordinary entity/particle queries; retain the original scan for possible owners and unsupported storage.

- Release light and porthole group-cache world references and geometry on client-world unload, including when no later light or porthole is rendered.

- Store housing mesh settings in compact immutable snapshots and create full Forge property maps only on demand. Preserve listed/unlisted transitions, validation errors and concurrent reads.

- Reuse private per-thread corner and artwork-coordinate buffers during trapdoor rendering. Preserve independently owned geometry results for collision callers and isolate nested draws.

- Use scalar face intersection for diagonal trapdoor picking, preserving exact hit coordinates, edge tolerance and nearest-face precedence.

- Resolve saved ramp materials once per slice and reuse its face emitter, reducing repeated source-tag decoding and per-face temporary objects without changing UVs or lighting.

- Prepare immutable door quad groups once per resource bake, avoiding per-frame list assembly while preserving Forge lighting transitions, item transforms and custom-renderer fallbacks.

- Cache immutable enum-property hashes and directly read canonical trapdoor properties, avoiding repeated map hashing in rendering, collision and state queries. Preserve vanilla property equality, metadata and transitions.

- Avoid redundant chunk-dirty notifications for unchanged trapdoor group power. Preserve saved channel signals and group state, and stop physical input reads once the group is known powered.

- Reuse bounded ray-candidate coordinate lists for offset trapdoor picking, preserving hit order and current loaded-world reads. Offset collision scans reuse a mutable query position.

- Precompute fixed catalog texture names for square, unlit and storage surfaces, avoiding per-draw JSON traversal and string allocation while retaining live Custom material and atlas resolution.

- Reuse exact diagonal trapdoor collision meshes in a bounded cache, avoiding repeated subdivision and allocating only intersecting boxes. Preserve collision bounds, box order and edge contacts.

- Skip physical power scans when a screen's display mode is independent of power or its virtual channel is already powered. Joined lights stop reading inputs after the first powered source while still synchronizing the entire group.

- Reduce Block, Slab, Storage and Stairs chunk-mesh allocation with single-pass extended states, reused neighbor positions and bounded caches of common housing face lists. Preserve exact quads, face overrides, storage artwork and Forge property behavior. See the [performance measurements](docs/performance/1.4/README.md).

- Reduce loaded redstone channel work by revalidating a known powered source before searching other members. Preserve immediate OR propagation and coalesced input handling.
- Share immutable trapdoor assembly membership after validation, reuse each draw's group snapshot, and avoid redundant corner/edge allocations and opposing-cover lookups.
- Reuse trapdoor texture-clipping layouts across frames and moving poses. Keep lighting and resource-pack texture coordinates live, with bounded cache storage and unchanged leaf geometry, artwork, and controls.

## 1.3

See the [animated 1.3 highlights](docs/gallery/version-1.3.md), [illustrated programmable block improvements](docs/gallery/task-improvements.md), and [filesystem texture setup](docs/filesystem-textures.md).

### Latest improvements

- Streamline developer documentation and make external test-mod locations configurable.

- Correct shader inputs across programmable wall/porthole frames, screen/control housings, shaped lights, glass panes, moving ramp cells, landing-gear arms and optional door panels: outward faces, geometric normals and explicit vertex lighting. Preserve existing brightness, emission and transparency rules. Cache ordinary wall and porthole geometry alongside diagonal walls to reduce allocation; see the [lighting and performance report](docs/performance/SHADER_LIGHTING_FIXES.md).
- Correct ramp vertex attribute ordering after the lighting-format change, preventing disappearing deployed cells and corrupt black/blue texture strips. Regression checks exercise production color, opacity, UV, normal and lightmap packing for thin and full-height moving slices.
- Exclude all 16 retired picker textures from the standard JAR and atlas. Preserve saved choice numbers with default-material fallback, and retarget 180 legacy configured-item models and Dynmap texture aliases so removed artwork produces no missing-texture references. Keep source archives for validation.

- Add Industrial to the shared picker and apply the Hull/Panels/Tech moves for 27 finishes, preserving numeric choices and source artwork.

- Replace door/trapdoor/ramp motion illustrations with smaller 420×350 live GIFs and add an animated 1.3 highlights guide. Buffer capture frames and use a steady output cadence to reduce jitter.
- Shorten the eight trapdoor finish labels without changing saved material IDs.
- Match the Programmable Diagonal Screen hotbar icon to its current solid wedge housing.
- Preserve manually extended landing gear on reload when redstone input is unchanged; channel unregistration during unload no longer overwrites saved extension.

- Demonstrate animated documentation with a live captured Observation door GIF and a reproducible capture/encoding workflow.

- Reorganize shared texture categories into Tech, Hull and Panels, simplify names, and hide removed picker entries while preserving saved choice numbers. Retired artwork is excluded from the runtime build and uses the default-material fallback described above.
- Add paired Blue Hex and Amber Hex programmable lights and four 140×140 overhead-bin storage sets (fourteen storage choices total).

- Cached static Programmable Diagonal Wall meshes and Programmable Door replacement-material surfaces/model quads, reducing per-frame geometry allocation while retaining live lighting, motion and resource reloads. Reapplying identical programmable settings no longer triggers appearance rebuilds; shallow-wall Duplifier copies avoid the redundant legacy width reset.
- Landing gear and blocks moved by Programmable Ramps now share diagonal walls' loaded-terrain render distance instead of disappearing beyond 64 blocks.

- Fixed repeating diagonal wall connections, neighboring-block clipping, stacked orientation and opened diagonal trapdoor selection. Added regular trapdoor Slide over surface movement.
- Expanded programmable dialogs with tall texture lists, shared two-column layouts, full-width face buttons and independent porthole/slab side textures. Screen and Controls pickers share general materials while keeping full-height and half-height artwork separate.
- Simplified light texture labels and restored Doors in regular/diagonal trapdoor pickers. Door pickers now show each design once, with a separate Small/Medium/Large selector.

- Programmable Storage now supports optional per-face texture overrides, including inherited Storage-set faces, configured-item persistence and Duplifier copying without changing inventory contents.

- Added Programmable Storage: 27 inventory slots, hopper/item-handler access, comparator output, shared material picker and matching top/side/front Storage sets. Defaults to Cabinet; crafted from a chest and Programmable Block. Appearance copying preserves target contents, and mining drops contents separately.

- Added ten Storage material sets from matching top/side/front sheets, split into 30 crisp 140×140 textures.

- Gallery updates support focused live captures and incremental export, preserving unrelated screenshots and avoiding a second client run. Full refreshes require `--full`.

- Slide into next block overlaps the owning mounting block by one pixel when closed, rather than protruding past the far edge of the covered block. Texture mapping, selection and collision follow the corrected leaf.

- Ordinary rotating trapdoors align with vanilla trapdoors when open, retaining only a tiny anti-z-fighting clearance. The alignment applies to existing saved groups as well as individual leaves.
- Bundled 33 additional materials in Computing, Fuel, Hull, Power, Trapdoors and Windows. New normal and diagonal trapdoors use Cyan-lit Armored Sci-Fi Hatch-4 by default; saved material choices retain their existing indices.

- Normal trapdoors combine placement and motion in four Movement choices: Rotating, Sliding, Rotate into next block, and Slide into next block. Individual sliding trapdoors allow hinge selection, saved in configured items and copied by the Duplifier. Next-block rotating leaves open flush to the covered cell with only a tiny z-fighting clearance.

- Half-width and half-height diagonal trapdoors join connected partial patches, including three-leaf corners and mixed ordinary/staggered neighbors, rather than splitting into separate pairs. Membership and shared settings survive saves.
- Diagonal trapdoors offer Rotating, Slide over wall (the existing lift-and-slide motion), and Slide into wall (sideways motion without lifting). The sliding choice is saved in worlds/items, synchronized across groups, and copied with the Duplifier movement option.
- Opposing Next block trapdoors spanning a two-block opening suppress their one-pixel protrusion so their leaves meet without overlap or a visible mount gap. Configured Next block items place open on client and server.
- Shared material dialogs reopen with the Custom row selected, scrolled into view, and showing the current sample thumbnail instead of highlighting a built-in fallback.

### Added

- Extra Large (2×2) landing-gear alignment with matching reservations and saved/copied size settings.
- Adjacent next-block trapdoor placement, preserving the owning tile outside the gear shaft and holding the leaf open until retraction completes. Supports either rotating or sliding movement.
- Custom block/door texture sample slot across shared material menus, with vanilla/mod atlas artwork, upper/lower door mapping and non-consuming inventory selection.
- Fit/Tile housing controls for screens, half/full inputs and consoles. Static surface and material choices participate in Duplifier copying.

- Shared categorized texture picker with thumbnails across programmable materials, lights, doors and static screen/control surfaces. Adds full door artwork and static first-frame screen textures while preserving existing finish indices.
- Filesystem PNG categories under `config/vandorlabs/textures`, with first-run Example folder/sample panel, path-based saved identifiers and missing-client fallback. Different client/server catalogs are supported.

- Programmable Light Frames offer centered Small/Full sizing; Light Slabs offer side-texture Fit/Tile. Small frames remain individual artwork panels.

- Loaded-only rectangular programmable trapdoor groups through 8×8, with shared outer hinges and scaled sliding travel. Diagonal trapdoors connect across opposite-slope rows and full-width vertical/depth offsets.

- Programmable Diagonal Trapdoor with wall-compatible half-width/full-height, full-width/full-height and full-width/half-height placement. Its 2px leaf leaves 1px clearance on each wall surface; pairs and 2×2 surface groups rotate or slide toward opposite sides. Shares textures, redstone channels, configuration and copying with programmable trapdoors.
- Programmable Trapdoor with 78 block finishes, Rotating or horizontal Sliding motion, Bottom/Middle/Top positions, physical redstone and virtual channels. Adjacent pairs and complete 2×2 squares open together toward opposite sides, without frames or hinge hardware. Includes Configurizer, Duplifier, configured items and Dynmap support.
- Non-rendering Minecraft/Forge regression checks for placement, diagonal vertex data, connected copying, ramp clearance and trapdoor geometry, grouping, persistence and redstone.

### Fixed

- Staggered diagonal trapdoor groups connect in all three shape modes, including Half width / Full height and Full width / Half height, with saved links and shared movement/settings.

- Opposite-slope diagonal groups derive their outside direction from the convex bend. Both rotating rows and the staged sliding clearance move outward, preserving hinge pivots and the inset/lift animation. Reversed coplanar rows share an outside face and retain their established rotation direction.

- Programmable screen, input and console dialogs use categorized list thumbnails for previews, removing the separate preview areas while preserving animation and display controls.

- Diagonal trapdoor hotbar icons use a smaller scale for default, configured and Custom finishes. The Duplifier dialog grows to fit all options, keeping its last-row trapdoor hinge/layout switches clear of Done and fitting smaller GUI resolutions. Its movement switch explicitly includes trapdoors.

- Plain side-mounted trapdoors hinge against the clicked supporting block instead of its opposite edge. Configured Next block items retain their explicit saved hinge.

- Floor- and ceiling-mounted trapdoors sit flush visually, using a tiny coplanar-face inset instead of a one-pixel gap. Rotating leaves retain clearance from their support when open.

- Next-block trapdoor leaves project one pixel beyond the covered cell’s far edge, with matching selection/collision and fully retracted sliding endpoints. Normal and diagonal trapdoor thin edges use the standard programmable door’s metal side texture, including Custom materials.

- Next-block placement is restricted to individual trapdoor mounts, preserving joined groups when configuration or copied settings request an offset. Offset leaves can be selected and collided with in neighboring cells after hinge changes; selection outlines follow their actual bounds.

- Diagonal trapdoors have separate width/height controls and apply combined width edits together. Trapdoor Fit/Tile is saved/copied; Tile repeats and mirrors built-in and Custom door artwork instead of stretching it over 2×2 groups, while thin edges keep native door artwork.
- Rotating trapdoors retain clearance from neighboring block faces; sliding diagonal trapdoors lift clear of continuation walls. Next-block closed-leaf placement is independent of Rotating/Sliding and offers an explicit hinge direction, replacing the forced-sliding Cover option.

- Programmable light dialogs hide separate Off materials while keeping automatic on/off artwork, and grow to show up to seven textures plus the category heading. Temporarily reduce GUI scale when needed to fit the taller dialog, restoring it on close.

- Native screen/control options use categorized thumbnail lists while retaining animation, framing and speed controls. Housing Fit/Tile is directly available in screen/console/input dialogs.
- Door artwork on ordinary blocks uses its square lower half. Replacement door faces offer saved/copied Fit/Tile and keep the native edge textures and hinges.

- Material lists use larger aspect-preserving thumbnails, alphabetic categories/entries and pinned category headings, preserving existing saved material numbers.
- Custom texture sampling reads configured programmable item materials; its Back/Use texture controls follow their revised order. Light sizing applies only to Frames, and propulsion dialogs use the list thumbnails instead of a separate housing preview.

- Replacement programmable-door artwork follows the native leaf bounds and retains selected hinge hardware, including bare/framed variants.

- Register custom atlas sprites before ordinary sprite registration, and reuse existing configured item geometry for the expanded material catalog.

- Light Frames sit against the supporting wall, floor or ceiling face. Their collision and rendering share the same bounds. Light Frame and Light Slab inventory models are centered, scaled to fit and show the lamp artwork.

- Rotating tall diagonal trapdoors open upward consistently across either slope.

- Configured Programmable Stairs now predict their saved appearance during client placement, matching programmable blocks and slabs.

- Plain Programmable Trapdoors select Bottom, Middle or Top from the clicked wall face thirds. Configured items retain their saved placement.
- Open programmable trapdoor leaves retain one pixel inside their own block, reducing overlap flicker against neighboring blocks for sliding and rotating motion, including diagonal leaves.

- Configured Programmable Blocks predict their saved texture during client placement, avoiding the default-finish flash while waiting for server tile data.
- Diagonal panels submit consistent winding, transformed normals and lightmap data for shader lighting. Hardware validation with Complementary Unbound 5.6.1 remains pending.
- Diagonal walls no longer disappear at the default tile-render distance; loaded chunks and the view frustum still bound rendering.
- Duplifier connected matching follows face, edge and corner neighbors, including vertically adjacent top/bottom slabs while preserving their halves.
- Ramp Controller clearance uses each source block's occupied bounds, allowing top slabs to descend 2.5 blocks onto the ground and bottom slabs to meet ceilings without accepting real obstructions.

## 1.2

### Added

- Duplifier's optional Connected Matching Blocks mode applies selected copied settings to a face-connected group with the exact same block type and original configuration, ignoring facing and rotation. It uses the multi-block item icon, stays within loaded chunks and rejects groups larger than 4,096 occupied block cells before applying settings.
- 23 selected housing finishes from `textures2`, using 64x64 artwork and short descriptive menu names.
- 16 selected housing finishes from the shared `textures` artwork folder, with shorter names prefixed `T1`, available across Programmable Blocks and the other blocks that share the housing finish menu.
- 11 Hull Plating finishes from `hulls`, numbered 1–11 and available in the shared housing finish menu.
- The building guide now shows all 78 selectable finishes on Programmable Blocks, in groups of up to ten with names in display order.
- Extra Large Landing Gear doubles Large's model dimensions. Its centered mount and moving parts reserve a three-by-three footprint; extension remains configurable from 0–4 blocks.
- Programmable Light Frame, a one-pixel-deep light panel, and Programmable Light Slab, a half-height light with top and bottom placement. Both use Programmable Light artwork, brightness, joining, redstone channels and configuration.
- Ramp Controller start and end offset sliders with half-block steps from -16 to 16 blocks. The controller, moving cells, saved settings and copied items retain these intermediate positions.

### Fixed

- Removed the other 16 first-pack housing finishes and their packaged textures. Existing saved finish numbers are not migrated and may resolve to different artwork.
- Removed the other 23 second-pack housing finishes and their packaged textures. Existing saved finish numbers are not remapped.
- Extra Large Landing Gear now uses a smaller, centered hotbar icon that fits its slot.
- Dynmap now reads Programmable Slab halves, Programmable Door facing and open state, and the clipped shapes and saved main finish of deployed Ramp cells. Propulsion emitter faces have enough depth to remain visible on the map.
- Newly placed Programmable Slabs now use Tile for their side layout by default; saved Fit selections still render as Fit, and the inventory model matches the default.
- Filled, full-height Programmable Diagonal Walls now support Programmable Doors above them.
- Framed Programmable Doors align with the block boundary at either depth edge. Frameless doors keep one pixel of clearance so sliding leaves retract behind the neighboring block.
- Programmable Diagonal Walls now provide per-face normals for shader lighting.
- Defer programmable-light joining, saved-state channel changes and redstone-channel registration until after chunk tile loading finishes. Landing Gear and door load callbacks now use the same safe point.
- Ramp controllers, doors, displays and Landing Gear read power only from loaded neighboring chunks, preventing redstone checks from loading another chunk during a tile callback.
- Programmable Slabs report their full outer face as solid, so ceiling and floor attachments such as Mekanism Glow Panels can be placed on the matching half.
- Programmable Input and Programmable Half Input now show their screen on the underside when mounted beneath a ceiling.
- Ceiling-mounted Programmable Inputs, Programmable Half-Inputs and Programmable Half-Consoles now use near, middle or far positions across the ceiling face based on where it was clicked.
- Propulsion wall textures now cover the housing's sides, top, bottom, rear and front recess across the Rocket Thruster, Ion Drive, Plasma Vent, Impulse Engine and three hover fixtures. Their configuration dialog shows the selected texture.
- The live-client test lab now performs bulk integrated-server scene setup on the server thread, reducing its chunk-packet tile-map race during setup.
- Dynmap now has fallback geometry for programmable blocks and walls. Removed model entries for three texture-only names that Dynmap rejected as air.

## 1.1

See the [illustrated guide](docs/gallery/version-1.1.md) for examples and settings.

### Added

- Optional per-face texture overrides for Programmable Block, Slab and the new Programmable Stairs. Disabled by default; faces inherit the main texture unless overridden.
- Programmable Diagonal Portholes with four opening shapes, half/full width, half-height/full-width geometry, glass shade and joining controls.
- Half-height diagonal walls, independent Fill Inside/Outside controls, and a standalone Programmable Diagonal Half Console.
- Luxury and Military Seats with joining controls and adjustable height (±2 pixels). Default legs are one pixel taller than the original models.
- One configurable Landing Gear block with Small, Medium and Large sizes, animated extension/retraction, redstone modes and channels, and immediate size/length previews. Extension ranges from 0–4 blocks in half-block steps; all sizes use equally thick arms with tiled frame-interior metal.
- Glass Frame Interior and Door Interior finishes in programmable texture menus.
- Copifier/Duplifier crafting: combine a configured tool with programmable items to apply selected settings while retaining the tool. Copying also supports the new block options and face overrides.

### Changed

- Diagonal placement follows clicked edges; a dialog control reverses the slope. Tall and shallow portholes join along their diagonal, including compatible reversed pieces.
- Two stacked Programmable Slabs become a Programmable Block, preserving the placed slab's settings.
- Programmable Ramp texture matching is optional (enabled by default). Ramps accept Immersive Engineering slabs and preserve each moved block's finish.
- Programmable Door animation uses the client clock for smoother motion on slow servers.
- Diagonal Porthole and Luxury/Military Seat recipes use Industrial Alloy instead of Programmable Matter.
- Landing Gear uses the single `landing_gear` block ID; earlier side, static and separate-size variants were removed without remapping. Legacy Space Doors are hidden from the creative menu.
- Updated block guides, configuration documentation and screenshot galleries.

### Fixed

- Programmable Light housing showing through the artwork, including distant single/joined lights rendered through Forge's batched renderer.
- Missing or flickering glass at joined Circular Porthole seams.
- Diagonal corner fills extending past corners or removing connecting sections; filled shallow undersides now use the main texture with correct UVs.
- Diagonal orientation after WorldEdit flips, and BetterBuildersWands `/wandOops` undo for affected blocks.
- Programmable Door and Half Input placement on Programmable Slabs, plus door removal crashes.
- A one-pixel seam on the Programmable Ramp controller.
- Landing Gear retraction, obstruction/reservation cleanup and saved settings; seat and gear inventory icons fit their slots.
- TextureFix sprite cleanup crashes and startup crashes caused by redstone-light initialization loading neighbouring chunks.
- Copying a block with face overrides disabled leaves them disabled without replacing stored face textures.

## 1.0

- Initial release.
