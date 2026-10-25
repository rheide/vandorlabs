# Changelog

## 2.6 (in development)

- Keep Forge 1.12.2 as the default target and add an independent, experimental
  NeoForge 1.21.1 build. The modern adapter is under conversion; see the
  [feature coverage](docs/development/feature-coverage.md) before using it.
- Share channel-network behavior, saved settings, finish identifiers, dialog
  layout and input rules across both targets. Run the same portable contracts
  on Java 8 and Java 21, with platform dependencies excluded from shared code.
- Share redstone screen rows, linked levels, pulse timing and overlay layout,
  together with joined texture planes, filesystem texture discovery and atlas
  padding. Forge uses these same implementations while retaining native world,
  lifecycle and rendering adapters.
- Add native filesystem material reloads for programmable cubes, preserving
  saved identities and updating artwork dimensions in an open picker. Keep
  native channel membership event-driven with explicit removal and unload cleanup.

- Add native console, half-console, input and diagonal screen variants with
  authored surfaces, independent console slots, shared row editors and native
  ray interaction. Preserve placement defaults, solid fluid behavior, light,
  canonical metal properties and held-item Adventure editing permissions.
- Add the plain native Programmable Matter block with its original artwork,
  padded item, recipe and loot. Verify filesystem materials on full viewscreen
  housing and display surfaces through actual selection and open-picker reloads.
  These adapters remain experimental; vehicles, moving assemblies, custom block
  samples and the other outstanding families remain under conversion.

- Join native Programmable Block Stretch/Fit faces with the same bounded plane
  discovery as Forge. Publish immutable client-thread render snapshots, preserve
  authored UV orientation and draw opaque Fit margins without worker world reads.
- Add native Push Button and Rocker Switch adapters with shared mount/settings
  policies, scheduled pulse expiry, persistent bank isolation, exact dialogs and
  configured item padding.

- Add the native Programmable Trigger with shared exact-level/band settings and
  four independent texture lists. Reuse cube housing storage/rendering, preserve
  uniform live artwork and OFF item previews, and keep channel edits immediate.
  Larger outstanding families remain under conversion.

- Add the remaining native power levers, throttles and wall slider with shared
  saved settings, four-step levels, authored collision and draft/Done/Escape
  dialogs. Preserve hidden compatibility IDs, canonical configured-item drops,
  type/size changes and padded icons. Bound shape caching and avoid repeated
  voxel-union optimization for complex tilted controls.

- Add the native Industrial Table with canonical models and shared exact collision
  geometry. Rebuild connected end modules from captured chunk data and refresh
  loaded boundaries when neighbouring chunks arrive or leave. Preserve the
  reference wall/floor support, inversion, plain item and tool requirements.

## 2.5

- Share navigation-table activation with Core-based multiblocks: consume physical redstone or input channels, optionally require a matching Data Chip, and never emit physical or channel power from Cores or reserved cells.
- Configure each modeled screen, half-screen and keyboard independently through the Configurizer, using the standard material/screen/panel artwork picker, custom block samples and static/animated playback. Preserve the authored planes, facing, model artwork and saved choices.
- Render integrated chip holders with the native Data Chip Mount frame, contents and retained-item behavior. Use a pause-aware insertion clock that survives repeated packets, tile replacement and world-time corrections. Hide multiblock and navigation-table/console mounts while Security is disabled, retaining any stored chip safely until Security is re-enabled.

- Prevent client crashes during rapid multiblock destruction: stale Core tiles now return safe local render bounds and emit no geometry after their block disappears, including during shader shadow passes. Guard detached, invalidated and replaced Core states through the same lifecycle checks, and apply the shared live-host guard to navigation tables/consoles and integrated mounts.

- Cache native-water adjacency for door and trapdoor render hints, avoiding repeated neighbour scans during pass selection, batching and fluid drawing. Keep stored fluids live, invalidate on world/chunk updates, and share bounded neighbourhood invalidation with fluid surface meshes. Add legacy-parity matrices and actual-client CPU/allocation comparisons.

- Join Flat Navigation Tables across opposing and mixed facings into one continuous screen, keeping exterior controls. Move the integrated Data Chip Mounts near the top of both navigation modules and use the standalone mount's exact size and proportions.
- Add navigation display artwork selection from Programmable Block materials and Programmable Screen designs, with static/animated playback. Optional redstone and matching-chip security gates control visibility; navigation modules consume power and never emit physical or channel signals. Preserve settings, artwork orientation and the shared chip through resizing, reloads and vehicle rotation.

- Fix Dynmap's selected-material rendering for Programmable Blocks, Slabs, Stairs, Walls, Diagonal Walls and Trapdoors. Add the bundled catalog artwork that normally exists only as client-generated crops.
- Render programmable door frames and leaves from the existing authored models, with saved facing, depth, motion and selected material. Preserve authored UVs through shared clipped-face rendering for doors, canopies and diagonal walls; retain inverted half-height fill limits. Correct canopy material counts so glazing and frame materials remain available.
- Preserve Dynmap atlas subdivisions during runtime PNG deduplication, preventing a lever atlas error from aborting later texture definitions. Remove map records for unregistered model prototypes and material choices. Add packaged atlas checks, renderer matrices and an isolated real-Dynmap fixture.
- Add Pinnacle Meridian Drive, Pinnacle Vector Fold Array, Vektor Longburn Drive, and Paladyne/Seyr Navigation Computer Core assemblies. Reuse the existing Core-and-Programmable-Matter frame validation, corner detection, reservations and dismantling, with rectangular depths and narrow columns supported by the same placement path.
- Add Flat Navigation Tables and Angled Navigation Consoles with cached joined layouts and one deterministic front-center chip holder. Preserve the reader and its chip when resizing moves its owning module.
- Reuse Data Chip Mount insertion, filtering, channels, saved inventory and four retention/return modes in the modeled holders. Drive idle/active appearance follows the configured input and security gates; navigation computers offer Off, Idle and Computing appearances. These models do not yet implement navigation, propulsion or dimension transfer.
- Add illustrated, step-by-step build guides for every Core assembly and both navigation layouts.
- Reverse Hover steering while travelling backwards so left/right controls follow the reverse direction. Keep stationary Hover turning available.
- Preserve packed OBJ texture atlases at their authored resolution, including matching static and animated navigation computer artwork. Validate packaged atlas pixels and all navigation layout variants automatically.

- Fix dedicated-server crashes when modular landing gear retracts in Hover mode with Thaumic Augmentation installed. Carried tile changes now queue vehicle snapshot persistence without creating synthetic chunks or invoking chunk capability handlers.
- Apply the same dirty-state handling to all carried tiles, ignore replaced tile instances, and isolate comparator callbacks from foreign blocks in the read-only vehicle world.
- Keep loaded-chunk lookups non-allocating during global collision events, including canopy discovery reached by moving gear in existing saved vehicles.
- Read support solidity and light opacity directly from the captured blocks. Route additional light and sky queries through loaded parent-world sampling so foreign block models do not create synthetic chunks.
- Add shared headless/live regressions for gear input reversal, saved targets, generic carried tiles, stale instances, comparator isolation and chunk-free block queries. Extend the isolated dedicated-server check to accept compatibility mods and run the same regression.

## 2.4

- Synchronize cockpit toggle and slider controls with regular redstone screens on shared channels, including partial channel banks, saved levels, pulses and carried controls. Persist pending control changes before rebuilding a carried ramp.
- Allow six redstone items on cockpit display screens while retaining three on smaller touch panels. Preserve existing rows, settings and legacy control indices.

- Keep shader shadow-map draws separate from main-view vehicle visibility. Preserve the hidden-origin fallback for first-person pilots so the hull and cockpit screens remain visible when looking forward.

- Add Ramp Controller Slab with top/bottom placement, six active scan faces, the full controller's settings and seven mode indicators. Use newly drawn half-height panels and preserve top-slab separation from the ground during vehicle discovery.

- Follow actual overhanging diagonal-wall and canopy contacts during vehicle discovery, preventing attached ceiling slabs from being left behind and trapping a newly assembled Hover craft. Preserve empty-half slab separation from terrain.

- Preserve the vehicle mesh vertex format, stride and shader attributes when converting blocks to Taxi/Hover, including cache invalidation after shader format changes.
- Keep the exposed top and bottom caps of filled diagonal walls beneath adjoining flat walls.
- Allow Ramp Controllers to scan upward or downward while retaining horizontal ramp deployment and existing horizontal controller metadata.
- Require a dedicated Vehicle Controller for new Taxi/Hover conversions. Vehicle Status reports "Vehicle Controller missing" and hides mode controls when absent; previously captured craft retain their recovery path.

- Correct stretched cockpit Redstone text, restore buttons after shared renderer buffer offsets, and align four-position slider clicks with their visible segments.
- Open cockpit configuration on the clicked screen, keyboard or throttle page, including blank and gated-off displays.
- Give physical cockpit throttles the Thruster Lever's configurable Low/High levels and four-position Off/Low/Medium/High behavior, keeping animation aligned with custom output levels.
- Add a saved Single/Twin cockpit throttle layout. Preserve both controls' settings and suppress the hidden right throttle's output in Single mode.
- Hide unavailable Autopilot and departure controls on Vehicle Status displays while retaining vehicle mode controls.

- Hide retired block items from JEI's registry scans, including duplicate Power Lever/Industrial Power Lever sizes and the old Landing Gear, Ski Landing Gear and landing struts. Preserve saved blocks and inventory items; current modular gear and configurable lever sizes remain available.

- Animate cockpit screen banks and sliding seats with the client's pause-aware visual clock, avoiding skips or rewinds from server world-time corrections. Preserve visual motion across repeated settings packets and recreated vehicle tiles without increasing packet frequency. Server collision, reservations and saved motion remain authoritative.
- Play cockpit display artwork from the same local visual clock so remote server time synchronization cannot stall or rewind its frames.

- Fix dedicated-server disconnects and crashes when players or entities approach a placed canopy, including Large Front Canopy Glass. Use a server-compatible collision-bounds constructor while preserving the existing geometry.
- Existing saved canopies load with corrected collision bounds automatically; no block removal or world conversion is required.
- Remove client-only bounding-box center calls from contained-fluid geometry, escalator landing steps and turntable collision checks to prevent similar dedicated-server failures.
- Add a shared-bytecode check for client-only API references and a live regression for collision after loading saved canopy data.
- Defer canopy, Autopilot, cockpit and credential-reader load callbacks until chunk tile iteration finishes. Reject non-finite saved canopy progress and overflowing footprint offsets.
- Keep failed vehicle and turntable snapshot loads inert, preserving original component data. Refuse vehicle recovery that would overwrite changed blocks, inventory contents or an unrelated entity UUID.
- Reduce vehicle movement state payloads from 105 to 82 bytes while preserving positions, steering, timing and input acknowledgements. Require matching 2.4 client and server builds.
- Add load-failure, recovery-conflict and packet-boundary checks, with dedicated-server startup/reload coverage and contributor guidance for these invariants.

## 2.3

- Extend release regressions for plain Canopy Glass sharing cockpit and Pilot Seat upper space, including rear wall clicks, all seat heights and facings, both placement orders, collision and removal.

- Allow canopy placement against wall faces directly behind a cockpit or Pilot Seat by recognizing shared upper space in the client placement check as well as on the server.

- Resolve Configurizer clicks on shared cockpit/canopy cells by the visible surface. Let the seat's upper rear reservation share compatible canopy space, retaining seat collision and preserving the canopy when returning.
- Use Access Card names for card items and mounts, including registry IDs and assets, without legacy registry remapping.
- Replace mount company multiselects with the door's shared inventory sample picker, one selected company, and click-to-clear wildcard. Retain the four shared reader behaviors and use the common redstone channel editor.

- Add independent Auto / Left / Right hinge selection for diagonal trapdoors. Fresh vertical placement retains the clicked leaf's physical hinge edge, including reversed-facing continuations; the existing swing-direction option remains available.
- Default new Programmable Stairs to Tile, preserve saved Fit settings, and match vanilla neighbor-brightness sampling and directional UVs on generated stair surfaces. Mounted surfaces carry their texture orientation and normals with them.
- Preserve lighting of the neighboring block visible through the empty recess beneath sideways stairs.
- Align opening canopies placed against the rear of a Cockpit with its back edge, without reserving an extra block behind the cockpit.
- Add a saved Rotation: Normal / Reverse control for each diagonal trapdoor. Vertically placed rotating leaves inherit the clicked trapdoor's rotation direction; sliding motion and hinge positions remain independent.
- Allow Canopy and Long Canopy to share the raised cockpit reservation, preserving cockpit collision and controls. Inherit facing and upright/inverted mounting when placing against another opening canopy.
- Show the selected company name and “Click to clear” in the Programmable Door card filter; unrestricted filters remain “Any card”.

- Remove native-water interface sheets over the solid footprints of wet walls, panes and doors, including the top/near half-width diagonal join. Retain genuine dry-gap boundaries and verify fully wet contacts across the native-fluid matrix.

- Include contained water in wet glass, wall, porthole and slab render bounds, preventing water rectangles from disappearing when the solid pane leaves the camera view. Add bounds/frustum regressions and close-range pool-edge views.

- Match vanilla water lightmap channel order for contained water and native water adjoining programmable blocks, correcting overly bright pool edges under uneven sky/block lighting. Add a direct vanilla vertex comparison across all 256 light-level pairs.

- Cull contained-water faces at opaque neighbor contacts, separate closed-leaf fluid boundaries from door geometry, and prevent underwater back faces from painting water over panes or leaves. Preserve visible free water–air surfaces and exterior pool compositing.
- Expand fluid regression coverage to 780 solid-contact geometry cases, 760 submerged contact/door camera views and 32 pool viewpoints above, below and across the waterline, including inward/outward glass and porthole views. Retain independent exterior comparisons, sorted chunk geometry checks and optional OptiFine compatibility runs.

- Sample vanilla solid-block materials with the Duplifier, preserving block metadata and applying through the existing shared material path.
- Add saved Programmable Stairs step sizes of 1, 2, 4 and 8 pixels and dialog-controlled Normal / Sideways orientation. Keep vanilla wall placement, rotate face textures with sideways mounts and preserve exposed neighbor faces; share rendering, collision and fluid geometry and retain shape in configured items and copies.
- Replace the Programmable Door card multiselect with Any card and an optional, non-consuming single-company card sample.
- Continuously supply vanilla water flow while a door with just one stored water side stays open. Drain the managed outlet after closing, draining or removing the door, preserve existing sources and retain independent compartment storage.
- Include wet diagonal columns above native flowing water when calculating shared corner heights, preventing gaps beneath stacked wet glass and doors.
- Match contained and native free-liquid heights, separate inset pane contacts from frame pixels, and render water surfaces from inside the liquid, including forward views through glass near the waterline.

- Keep Advanced Pilot Chair armrest slider selection on each moving grip at every height and detent.

- Batch both contained-fluid sides into one draw, reuse cached fluid descriptions and remove per-triangle temporary arrays. Release checks compare rendered pixels against the previous renderer across all waterlogged block families.
- Restore underwater camera effects and eye immersion inside contained fluids, including the exact center of an open waterlogged door. Keep dry door halves and dry compartments outside the fluid viewpoint.

- Restore the exposed frame rail at clipped diagonal-glass ends, including the bottom edge of half-width full-height panels. Discard zero-area clipping fragments that incorrectly cancel exposed frame edges; check rail positions and artwork across all diagonal shapes, orientations and wet/dry states.

- Use still-fluid artwork on the remaining native-water faces at contained-fluid contacts, including flush diagonal-glass ends beneath porthole walls. Preserve visible boundaries beside dry compartments.

- Inset exposed door water surfaces to prevent z-fighting with the door top at a two-block water level, while preserving seamless water between door blocks.

- Let vanilla sponges absorb contained water from supported Vandor blocks, retaining the blocks and other liquids. Bound absorption to connected water within vanilla sponge reach.
- Keep one-block-deep door waterlogging in the submerged block; bucket filling, draining, persistence and reversible opening fill operate per block.
- Include diagonal waterlogged neighbors in native water corner-height calculations to prevent mismatched surfaces around shallow pools.

- Automatically retain source and flowing liquids when placing supported walls, glass, portholes, diagonal variants, doors, trapdoors, slabs and stairs in fluid. Respect wet/dry boundaries on the two faces, preserve registered fluid identities and capture each door block independently through Forge placement snapshots.
- Join contained water/lava to ordinary world liquid without duplicate boundary faces or false falling-water borders. Clip the native liquid face to the actual wet compartment while retaining exposed faces beside dry compartments.
- Rebuild adjacent native-fluid meshes when contained-fluid packets fill or drain a side, including across chunk/section boundaries and both door halves.
- Preserve diagonal thickness faces against solid neighbours and retain glass rim geometry at flat porthole joins. Add rendered contact matrices across shapes, orientations, inversions, fills and frame settings, plus real submerged item-placement and native liquid vertex regressions.
- Render opaque diagonal-glass rails before translucent world water. Use still liquid artwork, vanilla alpha and directional lighting for contained fluid. Restore the dedicated glass interior material and existing wall UV mapping on diagonal rim interiors, keeping clipped pane edges aligned with their inward-facing caps.

- Move the outer cockpit keyboard decks and their supports 2.5px toward the pilot to clear the lowered side screens.

- Support contained fluid sides on Programmable Door, Programmable Trapdoor and Programmable Diagonal Trapdoor. Fill open passages from stored or adjacent water and restore the original sides on closing. Keep upper/lower door fluid storage independent and render diagonal fluid boundaries as continuous planes; only doors supply the managed outward water flow described above.
- Reestablish texture units, alpha testing and depth state before drawing cached turntable contents, and synchronize both texture-unit caches when restoring nested render state.

- Preserve Advanced Pilot Chair height when saving pilot controls or reopening the casing page. Synchronize cockpit riders and their camera to the seat timeline instead of periodic mount position packets.
- Match cockpit screen/input playback to Programmable Screen speeds. Open the shared redstone row editor from cockpit surface selectors, including row labels, channels, pulse/toggle/slider controls and slider limits.
- Angle all three cockpit keyboard decks with matching 2:1 proportions, lower the extended screen bank by 2px, and tile casing artwork at one-block scale.

- Add small cockpit buttons for retracting the whole screen bank and sliding the seat and armrests backwards for exit clearance, with reversible saved motion, blocked-path checks, rear reservations and vehicle support.
- Lower Advanced Pilot Chair by 6px by default and add +0/+2/+4/+6px height settings for its chair, side rests, controls and rider. Preserve height through configured items and vehicles.
- Flip new Cockpit placement, use red seat upholstery, enlarge the side screens with a shallower tilt, separate the outer input decks and add an angled center deck. Default to Bridge Bare Pilot/Engineering, Navigation/Sensor Controls and Bare Personal Terminal.
- Extend seated throttle hit areas to cover the visible grips in all detents, move both levers forward and keep their hit areas independent.
- Render the cockpit screen bank as one rigid model and cache display corners, status matrices, chair-height meshes and reusable render-state buffers.
- Add access-card security to Programmable Doors, including centered Both-side panels, company filters, leaf-wide insertion, retained-card and scan-and-return pulse/toggle behaviors.
- Add directional Redstone Transmitter and Receiver blocks with channel lists, Analog/Digital modes and configurable receiver On/Off levels.
- Add Security Glass, Ancient Relief and ceiling-tile panel/speaker/light finishes. Correct six-direction light placement and smooth the fluid surfaces and glass recesses of waterlogged wall families.
- Add a generated texture browser with nested categories, search, previews and category collages, served through repository documentation and GitHub Pages.

- Flip the Redstone Transmitter side arrows while preserving its port and type faces and the Receiver artwork.

- Make the plain credential selector accept Any Card or Any Chip and keep its unbranded background fixed. Slow company logo cycling to three seconds. Batch reader overlays and reuse mounted-item transforms to reduce render calls and per-frame allocations.

- Add animated insert-to-toggle-and-return behavior to Access Card and Data Chip Mounts, with saved output, inventory-safe returns and reload-safe scan progress.

- Add compact Access Card and Data Chip Mounts, with a non-consuming single-company sample filter or Any, output channels and continuous/pulse behavior.
- Correct cockpit dialog sizing and contrast, keyboard proportions, armrest orientation and overlapping chair geometry. Add selectable casing textures, name Advanced Pilot Chair and use red upholstery.
- Support Programmable Chair passengers in captured vehicles, preserving their style, height and assigned seat through movement, assembly and parking.
- Use server-compatible bounds constructors for cockpit collision and related vehicle, gear, canopy and door interactions.
- Cache rigid turntable wall artwork within the shared frame budget, sort only translucent Vandor tiles, and reuse the platform bounds. Add opt-in client/server CPU diagnostics.

- Let turntable surfaces use general materials as well as the six Turntable images; shorten native surface names to single words.
- Add independent liquid storage on both sides of programmable wall, porthole and glass panels, including diagonal variants. Support compatible Forge liquid buckets and preserve legacy waterlogged slabs/stairs.

- Queue turntable capture, restore and redstone rescans behind background journal writes. Revalidate contents before conversion and preserve inventory edits made during preparation.
- Share a transfer coordinator between turntables and vehicles. Budget turntable lighting and neighbor updates across ticks.
- Verify affected chunk saves by reading transaction markers back from region storage. Retry missing writes without waiting for the world's entire save queue; retain recovery records on failure.
- Keep temporary entity pauses out of chunk saves and refuse recovery that would overwrite changed block or entity contents.

- Add four turntable surface designs with independent On/Off selectors, and expose all six surface images in the shared Turntable texture category.
- Expose Cockpit, Advanced Pilot Chair and Yoke and Screen in creative menus; retire the duplicate Cockpit A identifier.
- Build captured turntable meshes within a shared frame budget, parse incoming structure NBT on a bounded worker, and stream turntable snapshots in bounded windows.
- Process vehicle block-update batches within a tick time budget while retaining durable transfer checkpoints.
- Batch modular landing gear rendering, omit bound member draws, and preserve hull meshes during pose updates. Reuse rigid hull collision geometry and bounded assembly pose caches where supported; index world gear by chunk for collision queries.
- Synchronize gear motion with compact, revision-checked pose packets and preserve correction interpolation.

## 2.2

- Add a saved 0-5 degree Auto-snap slider to turntables, with 0.1-degree steps and a default of zero. Redstone-off stops within tolerance restore at the nearest quarter turn. Compact the status panel and synchronize active surface artwork from the server, including channel-driven rotation.
- The two cockpit consoles, chair with side rests and yoke/screen were added as work in progress and are temporarily hidden from creative menus. Their registered blocks, models, saved-world support and implementation remain available for future refinement.

- Restore the original thin alpha-cutout card/chip silhouettes. Preserve vanilla sprite geometry and replace only the side material to prevent transparent edge sampling, with no square backing or enlarged body. Validate rendered front masks and opaque edge-on coverage for every design.

- Add pulse-controlled Restore channels to turntables, and limit speed to 0.5 to 10 RPM in 0.5 RPM steps. Existing speeds migrate to the supported range.

- Automatically restore captured turntable contents when redstone rotation stops at a quarter turn; other headings remain captured.

- Preserve diagonal wall top and bottom caps against flat programmable glass walls while retaining aligned panel joins.

- Use the standard first-person held-item pose for every access card and chip, keeping them beside the screen edge in either hand.

- Preserve tinted wall windows and canopy glazing on captured turntable structures, with separate opaque and glass rendering passes.

- Keep diagonal wall end caps visible through adjoining glass, preventing views into the wall interior while preserving glass-frame joins.

- Add persistent turntable status/errors and an RPM slider; infer the axis from the surface and prevent angle refreshes from sending commands. Redstone rescans new cargo at cardinal headings, Taxi craft stay live, and large surfaces remain visible when their rendering corner is off-screen. Complete stopped ramps can be captured and restored with their ownership links intact.

- Give Large landing gear arms a one-block mounting footprint while preserving heavy mechanisms and exclusive Large Wheel compatibility. Existing four-cell arm mounts migrate automatically.

- Swing-arm wheels attach at their top fork connector. The connector turns with the arm during retraction while the tyre stays level; both sizes share matching posed collision and selection.

- Battery casing textures are configurable, and RF port lettering stays upright on every face. Corp light textures appear directly in Lights without an On suffix.

- Recover ramp reservations and captured turntable contents when commands remove their controller, including early tile invalidation. Preserve blocked turntable returns in durable recovery records while removing orphan displays.
- Route Configurizer clicks on generated ramps, turntable surfaces/captured blocks and Taxi/Hover entities to the owning configuration, with component-based reach and normal edit permissions.

- Default new Programmable Blocks to Tile; preserve saved and configured modes. Fit contains the complete image inside joined faces: a square image occupies a centered 2x2 area on a 2x3 panel.

- Add pulse-controlled turntable direction channels, automatic controller orientation, precise Surface protection, controller-side block placement and correct cardinal restoration of corner thrusters.
- Join Programmable Block Stretch/Fit textures across matching rectangular surfaces; contain the complete Fit image with its original proportions and opaque margins. Report applied block counts in Duplifier messages.
- Support right-click reverse option cycling in landing gear, turntable, tank and battery dialogs.
- Correct Large Mount/Wheel and retracted Large Swing Arm/Wheel face overlap.
- Cache stationary landing gear transforms and constructor facing geometry; remove empty Constructor Core tick callbacks while keeping moving gear and Taxi wheels continuous.
- Store identical PNG artwork once behind compatible resource paths and losslessly recompress remaining images without changing decoded pixels or resolution.

- Add 18 company logos, backgrounds and murals in the new Corp texture category. Six paired company light designs appear under Corp/Lights only in programmable light dialogs, with automatic on/off artwork.
- Add instant Block/Hexagon battery shape selection, six-axis placement and segmented green charge bars on the top and bottom. Preserve RF, shape and existing saved orientations.
- Document all 12 modular landing gear size/arm/foot combinations with complete live-rendered deployment and retraction animations.

- Add Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor armor designs to the programmable armor dialogs: 24 slot-specific choices with native wearable atlases and padded icons. Existing saved designs keep their IDs.
- Filter redundant hatch detail variants from armor material menus while retaining saved selections and custom samples.
- Pause turntable captures at their current angle when redstone turns off. Add a paused Angle slider and explicit Restore with a visible nearest-quarter-turn preview. Carry Taxi craft about their physical center and cover both drive-on/rotate/drive-off and park/scan/rotate/reassemble journeys in live integration checks.
- Compact the tank dialog, use square side windows, and add persistent Tile/Fit casing texture control without changing contents or connections.
- Match tank shell shading and hexagon dimensions to hexagonal propulsion blocks. Give Rocket Thrusters, Ion Drives, Plasma Vents and Impulse Engines straight rear rims for clean tank-to-engine joins.
- Give all seven Access Cards and seven Data Chips opaque, closed extruded edges and crisp 128px cutout artwork; verify inventory padding and held models.

- Prevent a cockpit destruction crash when the renderer queries a removed or replaced cockpit tile's bounds.
- Update both cockpit consoles with continuous shells, tilted screen banks and center touch keyboards. Use tighter collision shapes around the angled footwell walls and preserve existing per-surface settings.
- Add standard and Large fixed Landing Gear Mounts for Plates, Skis and matching-size Wheels. Permit partial mounting-cap support and mixed-facing straight Large Extender columns sharing plates.
- Restore the legacy Extra Large wheel proportions, enforce matching wheel/arm sizes, and align large-part placement to the clicked footprint. Include the enlarged tyre in collision, selection and Taxi wheel animation.
- Prevent retracted Extender sockets and skis from sharing visible faces with the fixed sleeve collar. Enlarge and center the Large Wheel inventory icon while retaining slot padding.


- Capture the complete square build above a turntable, including overhangs above its fixed housing corners, so scan boundaries do not leave pieces that immediately obstruct rotation.
- Keep remaining turntable surfaces visible after a member is removed, use exact terrain collision tests, and report obstruction coordinates.
- Prevent a wheel-removal render crash; keep retracted extender wheels clear of their casing and carry contacted entities through landing-gear motion.
- Unify tank settings and contents into one dialog. Align horizontal hexagonal tanks with their axial ports, keep flat top/bottom faces and broad diagonal windows, and render level fluid with accurate partial volume.
- Keep neighboring terrain visible through formed constructor model cutouts, and omit unused split constructor exports from the runtime package.

- Migrate saved integrated wheel gear and existing stacks to modular Extenders and Wheels when the complete footprint is clear; retire legacy crafting and creative entries.
- Allow ski-only Taxi craft over ice, stop movement at its edge, and preserve manual gear overrides until the next redstone edge.
- Preserve adjacent trapdoor-cover behavior for modular gear in the world and on captured vehicles. Modular wheels roll, reverse and steer in Taxi mode; Hover pauses wheel animation.

- Prevent recursive chunk loads while rebuilding saved landing-gear membership.
- Add seven Access Cards and seven matching Data Chips using the supplied item artwork: unbranded, Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor.
- Update the three constructor meshes and company artwork, and replace Programmable Matter block/ingot textures with the supplied designs at standard resolution.
- Add an instant Hexagon/Block tank shape setting, preserving fluid, capacity and axial connections in both hollow shapes.
- Rotate only the circular surface disc while its square housing corners remain fixed. Preserve walking input while carrying players, use tighter rotated collision footprints, restore blocks automatically at the nearest quarter turn on stopping, and use redstone ON/OFF activation.

- Add compact Large Extenders and Swing Arms, stackable swing-arm columns, and separate standard/wide Wheel feet for either arm. Reserve oversized wheel footprints, carry wheel gear through vehicle conversion, and animate tires in Taxi mode. Select visible retracted feet to operate their gear and permit adjacent blocks outside the swing path.

- Apply modular landing gear settings immediately. Orient the swing-arm elbow toward its fold direction to clear the level plate, and close the unmounted swivel underside. New skis inherit the clicked ski's rotation without disconnecting their extender.

- Apply turntable settings immediately, use a height slider and capture only from the textured face. Carry players and loose items on bare floor surfaces and prevent captured displays from blocking nearby block selection.
- Restore modular landing gear selection and placement; retire the older standalone skis and straight/angled struts from crafting and the creative catalog, retaining saved blocks.
- Add live RF/fluid fullness dialogs, axial tank placement/connections, recessed fluid windows and centered fuel/battery port artwork. Restyle Matter Blocks with iron, redstone and gold.
- Assemble constructors only by interacting with their Core. Dismantled frames stay dismantled; mining a reserved cell removes it.

- Add modular landing plates and skis, extendable columns and level-foot swing arms. Connected feet and matching struts share controls, collision, animation and saved ownership, with Hover capture and cardinal restoration support.

- Add Turntable Controller and directional Surface blocks for floor, wall and ceiling displays on square platforms, with configurable capture height, direction, speed and independent redstone/channel inputs. Preserve captured block inventories and entities, and share illuminated surface artwork across the platform.

- Rename Programmable Ramp to Ramp Controller while preserving existing blocks, items and recipes.

- Add the sunlight-powered Programmable Matter Infuser with redstone fuel, iron input, hopper automation, and a creative-only Infuser Core. Remove direct ingot crafting.
- Add Programmable Matter Blocks with reversible nine-ingot packing.
- Add Assembly Press, Synthesis Tower, and Enclosed Gantry models assembled from creative-only cores and matter-block frames. Dismantling restores the frame.
- Add connected Fuel Tanks with fluid-level windows and configurable casings, plus directional RF Battery Blocks using Forge energy capabilities.
- Allow independent ON and OFF artwork on Programmable Lights.
- Expand filesystem texture subfolders as nested picker categories.
- Add water-filled variants of Programmable Slabs and Stairs using water and empty buckets.
- Add two multiblock cockpits, a chair with side controls, and a yoke/screen assembly with configurable seats, independent displays, touch controls, throttle outputs and vehicle support.
- Standardize ordinary artwork at up to 128 pixels per block, retaining native pixel art, model atlases and animation sheets. Remove resolution choices from material dialogs while preserving old saved selections.
- Add Stretch, Tile and Fit (aspect) texture layouts with saved settings and Duplifier copying. Fit stretches matching aspect ratios and otherwise tiles without distortion.
- Join diagonal wall/glass artwork across connected vertical rows and folds using a stable texture origin.
- Detect constructor frames from any bottom Core corner along either horizontal axis, regardless of the placed Core facing. Report a mismatched cell when assembly fails.

See the [2.2 release highlights](docs/versions/version-2.2.md), [matter and storage](docs/matter-and-storage.md), [Universal Constructors](docs/universal-constructors.md), [cockpits](docs/cockpits.md), and [material settings](docs/unified-materials.md).

## 2.1

- Replace canopy closure's nested sweep/ramp comparisons with cached spatial queries while retaining exact touching margins. Prepare cold swept geometry on a bounded worker and hold the safe pose until ready, without waiting on the server thread. Build cold canopy collision indexes on a bounded worker, retaining exact ordered collision checks while preparation is pending. Remove temporary vectors from canopy triangle picking, and reuse prepared landing-gear model vertices with resource-reload invalidation.
- Start Autopilot from outside a block-mode craft through bounded discovery and protected queued assembly. Report missing route markers beneath the sensor and other blocked-start reasons instead of claiming to follow a route while stationary.
- Smooth repeating diagonal route steps with a longer lookahead while retaining the existing lookahead for single 90-degree corners. Allow an unambiguous side continuation when starting at a corner, preserve repeated closed-loop travel, and retain manual waypoint stops.

- Add a **Next >** departure button to Vehicle Status screens, plus side-by-side **Block**, **Taxi**, and **Hover** mode buttons. Retain permission and mode-transition checks on full and half-height screens, inputs, and consoles.
- Allow Hover-to-Taxi transitions within three blocks of solid ground only with fully extended landing gear. Support redstone-controlled gear animation and matching collision in both modes, allow retraction only in Hover, and stop extension at obstacles instead of clipping through terrain. Remove automatic altitude-based gear retraction.
- Double Hover cruising speed while holding Sprint, with matching client prediction. Taxi/Hover propulsion lighting follows exhaust direction against actual travel, including reverse and vertical movement. Continuously emit a few family-specific particles from Hover Sprint thrusters, including stationary sprint; lift blocks emit sparse ambient hover effects even when stationary or unoccupied.
- Apply the latest Hover input instead of replaying stale queued commands. Discard movement input during conversion and synchronize the conversion hold before client prediction resumes.
- Move vehicle collision preparation and transfer journal/ownership writes onto bounded workers. Keep durable preparation ahead of world mutation, defer lighting and neighbor notifications, and checkpoint affected chunks incrementally without waiting for disk on the server thread. Pause source machine ticking during pending assembly and restore it when assembly is cancelled.
- Reuse canopy vertex buffers and trace selection rays in canopy-local coordinates. Deduplicate spatial collision candidates before testing them, and avoid repeated parking collision builds during seat exit and mode requests.

- Prepare cold vehicle canopy animation poses before advancing their collision shape, including immediate closure on boarding. For supported configurations, perform canopy collision subdivision and merging on the bounded worker; keep the current pose while preparation is pending.
- Index canopy collision boxes spatially so particles and nearby entities only test relevant surfaces. Reject canopy selection rays outside the posed geometry bounds while preserving exact hits and collision shapes.
- Align Autopilot forward travel with manual Pilot Seat driving and use bounded route lookahead for smoother headings along stair-step diagonal routes. Preserve marker traversal, waypoint stops and junction safeguards.
- Reduce canopy animation stalls in vehicle mode by grouping and deduplicating collision boxes with primitive tables, retaining the same ordered collision geometry at every animation step.
- Prepare collision poses for supported single-canopy assemblies incrementally after vehicle conversion, merging captured coordinates on a bounded background worker. Reuse bounded geometry caches while block states and settings match, discard stale preparation after changes, and retain the last exact pose until replacement collision is ready, including after preparation failures. Cache immutable seat, controller and propulsion indexes with each vehicle snapshot.
- Render canopy glazing after opaque walls, screens and other tile artwork, preserving tint when viewed from either side of the pane in block and vehicle modes.
- Keep third-person cameras outside parked Pilot Seat cockpits while preserving collision with surrounding terrain, and correct the front-view camera extension for captured vehicles.
- Put Park, Taxi and Hover channel fields directly on Vehicle Controller, with an adjacent switch button for each mode and the current mode disabled. Apply channel changes before switching modes.
- Prefer safe standing space near the occupied Pilot Seat when leaving or parking a vehicle, including usable space inside the hull; retain a safe exterior fallback when the interior is obstructed.
- Reduce vehicle collision rebuild allocations and repeated terrain queries while retaining the same collision geometry. Reuse terrain within a synchronous prediction replay, then discard it so later movement sees changed blocks.
- Reduce temporary geometry during canopy animation and reuse source-material lookups within ramp draws. Reuse landing-gear model and console texture identifiers without retaining stale resource-pack models or textures.

- Keep shared-channel Moat canopies fully open until retraction finishes, using the complete ramp travel path even when its current pose is clear. Recover saved closed canopies that still obstruct an attached Moat's return path.

- Check actual ramp segment geometry against the canopy's swept path without extra clearance padding, so blocks below or beside a Canopy do not falsely block manual or redstone closure.
- Preserve joined Canopies when a removed Pilot Seat's backrest is replaced by a canopy helper during the same world tick.
- Let seated pilots click nearby screen controls in block mode without their own seat's selection box intercepting the click; retain other blocks as obstructions. Use current view angles for screen controls when looking and clicking in the same tick.

- Allow joined Canopies to close over a Pilot Seat's recreated invisible backrest reservation, preserving the seat and the supported shared cockpit space.

- Settle redstone channels after vehicle transfers finish, discard callbacks to removed tiles, and preserve channel sources across assembly and parking. Prevent moving ramps, doors, trapdoors, lights and linked controls from reacting to partial transfers. Keep an extended Moat and its source journal together without leaving duplicate source blocks or ramp segments behind.
- Publish the complete transferred block footprint before invalidating old tile entities, preventing neighbor lookups from recreating ghost tiles. Restore occupied seats after cancelled assembly and retain pilot assignments and return-seat settings during parking rollback.

- Release canopy helper cells that are clear when fully open. Coordinate shared-channel ramps and canopies so ramps wait for opening clearance and canopy closure waits for ramp retraction, preserving both assemblies and unrelated blocks.
- Restore queued canopy helper tiles correctly during normal world ticks, preventing unfinished helpers from blocking closure. Preserve manual commands until the next redstone input change, synchronize settled poses, and retain glazing joins while helpers are released. Skip ramp sweep checks when no ramp segments are nearby.

- Restore editable source blocks when Moat is inactive at zero Start offset, and prevent controlled ramp segments from opening controller dialogs. Slice stored Moat towers vertically using Tread px and distribute deployed End offset across their full length, with all three Ramp interpolation profiles available.
- Match all seven Ramp mode logos to the original angular housing corners, show the selected mode on top, and mark the active direction on the top, bottom and two side faces.
- Choose Autopilot route markers from the inventory sample picker, retaining block variants and pending dialog settings.
- Restore switching Vehicle Status screens to Redstone controls across screen, input and console surfaces, including the Half Console top.
- Size Small, Medium and Large Wheel inventory icons progressively with padding, and reduce the held Large Wheel model.

- Keep Pilot Seat height and automatic Taxi Mode in the compact chair-preview dialog. Put assembly inspection, redstone controls and explicit Park/Taxi/Hover mode-channel lists on Vehicle Controller. Open Vehicle Controller, Programmable Ramp, Autopilot and Waypoint dialogs with ordinary right-click or the Configurizer. Show the selected Ramp mode on its front face.

- Add a separate Vehicle Controller for assembly and parking, retain Pilot Seat settings on seats, and support multiple Pilot Seats with the first seated pilot in control. Give Autopilot, Waypoint and Vehicle Controller distinct primary textures.

- Make Moat mode deploy a vertical source tower as native tread-sized chunks. Match the ordinary extended Ramp End-offset and per-tread height profile, retain slab halves and physical thickness, and use Start to skew the stored tower. Preserve existing flat-source and rigid Moat geometry and journals until restored. Support full vanilla and Vandor glass blocks as transparent ramp materials, and shaped stairs across all ramp modes.
- Unify Configurizer and sneak-right-click Pilot Seat settings, remove Save, and apply valid changes automatically. Keep mode-channel controls visible on smaller screens.
- Allow stationary Hover turns and hold the craft in position after a manual pilot exit. Preserve each hover thruster family's particle style and color.
- Add momentary Pulse rows alongside Toggle and Slider on Programmable Screens, Inputs, Consoles and their variants, including controls aboard vehicles.
- Add Disabled / Redstone ON / Redstone OFF triggers to Canopy, Long Canopy and both front canopy sizes. Preserve polarity in copied settings and captured vehicles.
- Clean stale Front Canopy reservations after removal without deleting overlapping ramp cells. Prevent rear glazing from fighting with diagonal walls.
- Keep Windows live client tests from capturing the desktop mouse, and run feature checks sequentially in one client.

- Remove the clicked constituent of joined Canopies; correct Wheel texture UVs and animate vehicle tyres and steering. Restore previous-value right-clicks across Pilot Seat, canopy, Autopilot and Waypoint controls.

- Add Hover mode with altitude hold, lift requirements, safe Taxi transitions, redstone-controlled landing gear and optional Park/Taxi/Hover channels. Captured switches, buttons and analog controls share the world's channel network.
- Add Autopilot and Waypoint blocks, configurable route markers, pulse journeys, delayed departures and Vehicle Status screens. Stop at route gaps, ambiguous junctions, obstacles and out-of-range terrain.
- Add slab-mounted Small, Medium and Large Wheels, configurable vehicle block limits, early rotation checks and conservative support for modded building blocks. Repair newly copied ramp journals and missing landing-gear reservations; allow channel-controlled ramp endpoints aboard vehicles.
- Add Long Canopy with a three-block depth and four profiles. Both opening canopy sizes join up to nine columns with shared materials and opening motion.
- Document supported slab-adjacent screen placement and the limits of embedding screens inside occupied block cells.

## 2.0

- Keep Redstone Screen row buttons and sliders operational aboard vehicles. Share the parent world's channel network with captured doors, trapdoors, Canopies, lights and landing gear, synchronize control changes, and unregister vehicle sources when parking or unloading.
- Animate channel-operated landing gear while driving and prepare fully extended gear, including owned lower cells, in the checked parking footprint. Raise the chassis when needed to retain support height, remove obsolete rotated reservations, and preserve the vehicle when extension is blocked.
- Reuse opaque vehicle wall rendering, skip hidden canopy followers and inactive tile renderers, cull baked hull sections using their actual geometry, and retain captured glass joins between ticks. Avoid repeated tile-NBT copies and static hull regrouping during canopy animation.

- Let seated pilots select captured Canopies and other cabin surfaces without their own Pilot Seat's selection box intercepting the ray. Keep ordinary terrain and cabin-wall occlusion, and exercise real seated clicks for hinged and both sliding modes in parked and captured cockpits.
- Reuse settled ramp geometry and collision boxes until the pose, shape settings, source origins or facing changes. Keep moving ramps and escalators animated, and verify direct setting edits against freshly loaded ramp cells.
- Keep the fixed Canopy Glass roof frame next to an opening Canopy, and retain the Canopy's complete rear frame, including both vertical uprights and the bottom sill. Preserve the open cabin connection and joins between fixed glass. Check joined surfaces for duplication, and add live shallow/deep and small/large nosecone comparisons for both front-window sizes.
- Keep the opening Canopy's top rear crossbar when connected to Canopy Glass, including its selection surface for closing a hinged shell.
- Align animated Canopy picking and collision with the visible shell by applying its pose once. Verify that exposed fixed mounts close hinged and forward/backward sliding shells when bodywork covers the moving glass.

- Omit unused legacy previews, door masks and retired trim from the runtime JAR after checking code, model, catalog and Dynmap references. Share identical Clear glass strength textures and repair stale Dynmap texture paths.
- Share texture-picker input handling across six dialogs, index configured item-material lookup, remove the obsolete porthole tint path, and release both screen and input animation textures on resource reload.
- Retain joined glass material layouts until their world or settings change, bound the per-world cache, and invalidate on chunk changes and unload. Share world-render listener plumbing with the diagonal mesh cache.
- Stream compact animation deltas one frame at a time instead of retaining the entire decoded delta payload during texture upload.

- Cache canopy rigid-part overlap removal and tiled texture subdivision during animation, retaining clipping where moving shells meet stationary mounts. Reuse appearance geometry for rendering and picking instead of rebuilding it for each interpolated pose.
- Allow canopy placement over an existing Pilot Seat through normal item interaction, including its invisible backrest cell. Trim overlapping Pilot Seat cushion and armrest surfaces across every height and armrest variant.

- Apply Mild/Normal/Strong material opacity to all canopies and visors, including both front canopy sizes and opening Canopy. Keep frames opaque, sort translucent replacement panels with glass, and retain native glass alpha and configured icon padding.
- Join matching non-glass Programmable Glass and Diagonal Glass artwork with Tile or Stretch mapping. Add independent porthole Glass tabs with the complete glass/material picker, tint intensity, opacity and joined-window layout, including full-depth portholes.
- Fix coincident canopy side surfaces, align the angled/rear sill trim and allow back-to-back placement by clicking the rear glass, including ceiling mounts.
- Offer the regular material catalog in Programmable Glass and Programmable Diagonal Glass. Mild/Normal Tint gives materials 35%/65% opacity; Strong is opaque. Preserve material choices in saved blocks, configured items and cross-family Duplifier copying.
- Add reversible ground vehicles and default-on Taxi Mode: sit in a Pilot Seat to assemble a connected craft, drive with W/S and A/D, and dismount to park. Include manual Configurizer previews, guarded cardinal rotation, obstruction retries, saved block/tile data, safe exits, movement-driven propulsion brightness and adjustable third-person distance.
- Add Pilot Seat height and Redstone ON/OFF channel settings in placed and vehicle previews. Hide each armrest beside a solid cabin wall.
- Keep doors, normal/diagonal trapdoors and Canopies manually operable aboard vehicles; retain their motion and settings through saving and parking. Capture settled extended ramps and pause escalators while driving.
- Combine Sliding and Rear-Hinged Canopy into Canopy, with selectable opening mode, Flat/Angled/Long Angled fronts, physical redstone and channel lists, forward/backward sliding with one-pixel lift, ceiling mounting and automatic widths of one, two and three. Join regular glass to the rear opening.
- Configure 3 Ãƒâ€” 3 Front Canopy Glass and 5 Ãƒâ€” 5 Large Front Canopy Glass with 0Ã¢â‚¬â€œ80 px depth and 10Ã¢â‚¬â€œ100% inner frames. Place the broad back at the clicked face and extrude towards the player on wall, floor or ceiling mounts. Add optional opening with four local hinge positions and redstone/channel control.
- Align Canopy and front-window registry IDs with their final names; remove obsolete canopy registrations, assets and runtime references without remapping aliases.
- Keep floor- and ceiling-mounted front-canopy frames clear of their supporting surfaces; closed glazing blocks cabin placement clicks.
- Copy canopy shape, opening, Fit/Tile and redstone settings and Pilot Seat height, signal mode and Taxi Mode through the Duplifier, including configured crafting outputs. Support all five Landing Gear sizes in crafting and preserve ramp half-offset application filters.
- Reserve only large-canopy surface cells and save exact owned-cell offsets for resizing, removal and vehicle rotation. Clean stale follower cells without deleting unrelated cabin blocks.
- Keep front-window depth and inner-size sliders responsive during dragging, coalesce live world updates and immediately apply the final released value.
- Add single-view live canopy dialogs with large unified glass/material lists, shared glass colors/intensity and normal Programmable Block materials including trapdoor artwork, with Fit/Tile mapping at correct panel/block scale. Copy tint and intensity between canopies, visors and regular/diagonal programmable glass with the Duplifier.
- Add Programmable Diagonal Glass, shared regular/diagonal glass finishes and improved reciprocal wall/glass joins. Allow long angled noses against walls and angled noses under ceilings.
- Add redstone-controlled Escalator Up/Down modes with rider transport, nearby-player sleeping and preserved extended ramp geometry.
- Fix floor-mounted Push Button notification order so neighboring dust receives the press immediately. Keep ordinary sliding trapdoors selectable and collidable outside their original cells.
- Reuse settled canopy poses and submit rectangular faces as direct quads. Cache vehicle hull rendering and cull offscreen tile renderers.
- Add block-category availability settings and live canopy animations for widths, opening modes, large hinges and ceiling mounts. See the [2.0 feature guide](docs/versions/version-2.0.md).

## 1.8

- Add thirty canopy components using the supplied cockpit meshes and translucent materials: regular glass, two angled fronts, narrow and wide sliding/rear-hinged shells, Large Front Canopy Glass, Figher Canopy Glass, nine additional fixed shells and twelve composable visor sections.
- Join compatible fixed glass without interior walls or perimeter rails. Support all sixteen regular neighbor masks and reciprocal angled-to-regular rear joins.
- Animate opening shells over sixteen ticks, with server clearance checks, persistent state, synchronized client poses, moved surface collision and picking, and multiblock ownership/removal. Pair two matching closed narrow shells into one wide shell; leave third columns independent.
- Compose visor windows from normal or Tall fronts, optional latch fronts, explicit left/right corners and straight sides. Tall sections use continuous two-high meshes without center rails; support the supplied 3-, 5- and 7-wide layouts at depths 1 and 3.
- Add survival recipes, creative-tab items and padded inventory models, plus focused live canopy validation for all thirty designs and every supplied composition.

## 1.7

- Name Thruster Lever types Standard, Twin and Grip, preserving saved types and existing controls.

- Replace the textured Push Button with a compact square recessed button that lights cyan while pressed. Keep its ID, one-alloy recipe and automatic release; share the solid height/tilt mounting options. Give Push Button and Rocker Switch cyan faces a simple bright highlight using the Industrial Power Lever indicator palette.

- Reduce Rocker Switch and Wall Slider models by 8%, with matching collision and selection bounds.

- Fit Slim Glass artwork and glazing inside framed door openings, preserving the original thin rails instead of cropping them beneath the outer frame. Bare doors keep their full-size artwork.

- Add upright base-height extensions and filled tilt wedges to Power Lever, Wall Slider, Rocker Switch and Industrial Power Lever, with shared persistence, copying, padded icons and solid collision. Merge Compact/Industrial lever sizes into one Industrial Power Lever item. Make the Rocker SwitchÃ¢â‚¬â„¢s top face fully cyan while On.

- Add Slim Glass artwork for regular and Large Programmable Doors at both supported resolutions, with the existing translucent glass shimmer behind its clear openings.

- Merge Thruster, Airliner and Fighter controls into one Thruster Lever item with a Type button, retaining existing placed controls, settings and configured item appearances.

- Merge twin-arm power levers into Power Lever with a Small/Large configuration button, preserved legacy sizes and configured drops. Shorten the Small arm by one pixel.

- Redesign Rocker Switch as a compact solid-color split rocker, with wall/floor/ceiling mounts, an amber bottom border while Off and a fully cyan top button while On. Retain its block ID and stick/alloy recipe; remove the separate Toggle Switch block.

- Recess twin-arm power lever sides inside their pivot caps to prevent Z-fighting. Shorten Large Power Lever arms by 2 pixels without changing its angles; increase Small Power LeverÃ¢â‚¬â„¢s On angle to 45 degrees for 67.5 degrees of total travel.

- Keep throttle height extensions perpendicular to the support face, with the tilted assembly on top; increasing height no longer shifts it sideways. Preserve filled mounting geometry and matching collision/selection bounds.

- Add Power Lever with supplied twin-arm on/off artwork, selectable Small/Large sizes, wall/floor mounts, Industrial Power Lever channel behavior, a shared recipe and padded inventory icons.

- Add Open helmets and Short Sleeves chestplates for all eight armor roles, with matching inventory icons and 16 additional slot-filtered choices. Preserve original IDs, artwork and defaults; map Duplifier copies to matching alternate pieces and standard leggings/boots.

- Configure Thruster Lever, Airliner Throttle and Fighter Throttle bases at their original minimum height, +2 px, +4 px or +6 px. Tilt the complete control by 15, 30 or 45 degrees forward, backward, left or right. Fill the tilted base down to its support and give the base, panel and handles solid component collision. Match selection bounds to the transformed artwork, preserve settings in saves and configured drops/items, and copy them with the DuplifierÃ¢â‚¬â„¢s Signal Levels option.

- Add Custom... to the armor picker, reusing the non-consuming inventory block/door sampler. Preserve the selected Custom row across dialog reopening, resolve configured item artwork, and support custom appearances in worn armor, icons and Duplifier copies.

- Copy programmable armor appearances with the Duplifier on armor stands, offhand items or crafting outputs. Role designs resolve to the destination pieceÃ¢â‚¬â„¢s corresponding artwork; generic and sampled textures transfer unchanged. Preserve item metadata and clipboard/apply options, with a separate Armor Texture switch.

- Design mounted programmable armor by right-clicking it with the Configurizer in creative or survival, on normal or small armor stands. Preserve equipment metadata and reject edits after the target or tool changes.
- Resolve selected Programmable Door designs and custom leaf textures, current light on/off artwork and housings, and programmable wall/trigger materials when sampling world textures onto armor.
- Add fifteen light artwork pairs to the armor pickerÃ¢â‚¬â„¢s Lights category with explicit On/Off labels; light artwork remains cosmetic.
- Refresh the building galleryÃ¢â‚¬â„¢s 114 current materials, all 21 regular and eight additional large-door designs, and current configuration dialogs.

- Shift-right-click a block face with programmable armor to copy its displayed texture in creative or survival, including programmable face overrides and storage top/side/front artwork. Samples persist on the item and appear in its texture picker.

- Add the Security Rescue armor set with matching slot-filtered designs, inventory icons and an armor stand gallery image.
- Center block-material artwork on each visible armor face instead of repeating tiles across the armor atlas, preserving texture proportions and vanilla cutouts.
- Default unconfigured programmable armor to its matching Civilian Staff design; preserve saved choices. Add armor stand images of all eight full sets to the gallery.
- Add eight role armor designs: Bioengineer, Scientist, Hazmat, Repairman, Pilot, Civilian Staff, Spaceship Staff and Security Rescue. The armor-only `Armor` category offers eight matching-slot choices per piece, with native worn artwork and matching inventory icons. Existing block materials remain available.

- Add Programmable Helmet, Chestplate, Leggings and Boots with diamond armor stats, durability, enchantability and repair behavior. Craft with Programmable Matter Ingots in the vanilla armor patterns.
- Shift-right-click held armor in creative, or with a Configurizer in the main hand, to choose its material from the default Programmable Block texture list. Each piece saves its choice independently and shows that material when worn and in inventory. Ordinary right-click equips armor.

## 1.6

- Leave one Minecraft pixel (1/16 block) visible at each fully open frameless Sliding X panel tip, with matching rendering, collision and click targeting.
- Restore version 1.5 door and trapdoor movement, placement and pairing. Revert the experimental Slide Left/Right, Split Horizontal/Vertical and added regular-door/trapdoor split modes; retain the large door's existing Sliding X.
- Keep large-door leaves and regular rotating doors targetable and collidable where they extend outside their original block cells. Draw selection outlines on the actual panel and allow ordinary right-click closing there.
- Remove White Glass and Dark Glass leaf designs from flat and diagonal trapdoor artwork pickers; retain them for regular Programmable Doors.
- Right-click setting buttons to cycle to the previous option, including light modes, screen Low/High limits, propulsion settings and the other configuration dialogs. Left-click still cycles forward.
- Use the shared dark door edge texture for Double Doors artwork, including large-door leaves, frames and inventory models.
- Widen Redstone screen slider labels by three normal character widths while retaining four bordered settings.
- Carry redstone levels 0Ã¢â‚¬â€œ15 on virtual channels, choosing the strongest loaded source without loading chunks. Existing switches and buttons retain their off/on behavior. Linked controls preserve intermediate channel values.
- Let Programmable Lights, Light Slabs and Light Frames follow signal strength with a signed brightness offset; joined lights share the strongest loaded group signal.
- Add signal-dependent propulsion light and emitter-texture brightness, with a configurable particle activation threshold and the existing particle density choices.
- Add slider rows to the Redstone screen/input/console controls. Use four bordered Off/Low/Medium/High settings matching the levers, with amber Off and cyan powered indicators, no numbers, and configurable Low/High limits. Order the editor as Add/Remove, Up/Down, Label, Channels, Control and Low/High, and reserve most of each display row for the controls. Preserve row settings through saving, configured items and Duplifier copying.
- Add exact-level activation and off/low/medium/high artwork to Programmable Trigger blocks.
- Add detailed Thruster Levers and Wall Sliders with four signal detents, configurable low/high limits, numeric physical/channel output, saving and copying. Levers and sliders mount on walls, floors and ceilings; their inventory icons use padded transforms. Preserve intermediate output when changing channel lists.
- Correct Wall Slider attachment on every wall and align flat-mounted handles with the panel markings, including rotated floor and ceiling mounts. Remove Simple variants and Thruster Control Blocks; simplify the remaining IDs to `thruster_lever` and `wall_slider`.
- Add detailed Airliner and Fighter Throttles with the same four signal detents, low/high limits, physical/channel output, configuration copying, six mounting faces and padded inventory icons. Keep Dynmap texture slots stable across listed signal levels.
- Orient floor-mounted Wall Sliders with the Off end nearest the placing player.
- Add a concise player introduction with links to the illustrated documentation, the version 1.6 block guide and live GIF demonstrations of a Thruster Lever controlling a Rocket Thruster and a Wall Slider controlling a Programmable Light.

## 1.5

- Preserve filesystem texture artwork in atlas/model lookups after legacy door-tier migration, including configured item previews. Update live checks for two artwork tiers and imported hatch variants.

- Give Framed and Redstone Input separate full-width rows in the half-height screen/input dialog, with spacing for the size, wall-texture and channel controls.

- Reduce door and Programmable Glass artwork to Small/Large: former Medium becomes Large, and former Large selections migrate to it without changing designs or physical door sizes. Glass panes now share one reflected pattern across resolutions.
- Resize imported trapdoor artwork to 128Ãƒâ€”128 Small and 256Ãƒâ€”256 Large, with a separate size selector. Standard inventory texture images are 128Ãƒâ€”128.
- Show one design row per Screens/Controls artwork pair. Bare/Framed changes its variant and thumbnail while preserving the selected row, animation and saved texture IDs.
- Resolve Custom Malisis door faces through their optional icon components, including upper/lower artwork, instead of sampling a missing baked-model placeholder. Skip missing face sprites when sampling ordinary doors.
- Remove obsolete High-tier runtime assets, redirect legacy references and share repeated JSON model geometry through parent templates. Validate packaged models against original geometry and texture bindings.

- Add four cargo leaf designs to Doors and eight complete bay designs to the large-only Double Doors category, each with Small/Large artwork resolutions. Newly placed large doors default to Cross Braced Bay; existing saved selections retain their IDs.
- Add large-only Sliding X: four triangular panels retract left/right/up/down from the center, retaining native and replacement artwork, glazing, fixed frames/panels, persistence and copying. Cache split meshes and assembly collision geometry; retain compatible opaque batching.
- Batch compatible opaque Programmable Doors using cached geometry, CPU motion transforms and Forge's shared tile-renderer buffer. Preserve glass/custom-face and OptiFine rendering through the existing path, and support CodeChickenLib's vanilla item-renderer delegate.
- Add Large Programmable Door: one indivisible 3Ãƒâ€”3 opening with two 1.5Ãƒâ€”3 leaves and the regular door's appearance, movement, control-panel, placement-depth and redstone options. Any cell addresses the anchor; only the anchor renders. Placement validates all nine loaded cells before writing, and removal clears the assembly.
- Animate large doors over 12 ticks rather than 9, and lower their optional control panels to the regular door's center height, including collision and click regions.
- Guard collision clipping against disjoint boxes so opened large doors leave a traversable passage in every orientation and movement mode.
- Add White Glass doors: pale moving leaf rails around a large glass opening, with simple shallow rectangular handles on both faces. Crop/repeat the rail and pane textures at consistent proportions; keep the stationary frame independently optional.
- Thicken the moving borders of glass-rim doors and add Dark Glass using the Door Interior (Dark) material with contrasting light handles.
- Keep large-door inventory icons inside their slots with padding after GUI rotation.
- Add Bussard Classic/Modern, Deflector Amber/Blue and Nacelle 1Ã¢â‚¬â€œ4 light artwork with matching On/Off textures. Preserve existing texture indices.
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

See the [animated 1.3 highlights](docs/versions/version-1.3.md), [illustrated programmable block improvements](docs/gallery/task-improvements.md), and [filesystem texture setup](docs/filesystem-textures.md).

### Latest improvements

- Streamline developer documentation and make external test-mod locations configurable.

- Correct shader inputs across programmable wall/porthole frames, screen/control housings, shaped lights, glass panes, moving ramp cells, landing-gear arms and optional door panels: outward faces, geometric normals and explicit vertex lighting. Preserve existing brightness, emission and transparency rules. Cache ordinary wall and porthole geometry alongside diagonal walls to reduce allocation; see the [lighting and performance report](docs/performance/SHADER_LIGHTING_FIXES.md).
- Correct ramp vertex attribute ordering after the lighting-format change, preventing disappearing deployed cells and corrupt black/blue texture strips. Regression checks exercise production color, opacity, UV, normal and lightmap packing for thin and full-height moving slices.
- Exclude all 16 retired picker textures from the standard JAR and atlas. Preserve saved choice numbers with default-material fallback, and retarget 180 legacy configured-item models and Dynmap texture aliases so removed artwork produces no missing-texture references. Keep source archives for validation.

- Add Industrial to the shared picker and apply the Hull/Panels/Tech moves for 27 finishes, preserving numeric choices and source artwork.

- Replace door/trapdoor/ramp motion illustrations with smaller 420Ãƒâ€”350 live GIFs and add an animated 1.3 highlights guide. Buffer capture frames and use a steady output cadence to reduce jitter.
- Shorten the eight trapdoor finish labels without changing saved material IDs.
- Match the Programmable Diagonal Screen hotbar icon to its current solid wedge housing.
- Preserve manually extended landing gear on reload when redstone input is unchanged; channel unregistration during unload no longer overwrites saved extension.

- Demonstrate animated documentation with a live captured Observation door GIF and a reproducible capture/encoding workflow.

- Reorganize shared texture categories into Tech, Hull and Panels, simplify names, and hide removed picker entries while preserving saved choice numbers. Retired artwork is excluded from the runtime build and uses the default-material fallback described above.
- Add paired Blue Hex and Amber Hex programmable lights and four 140Ãƒâ€”140 overhead-bin storage sets (fourteen storage choices total).

- Cached static Programmable Diagonal Wall meshes and Programmable Door replacement-material surfaces/model quads, reducing per-frame geometry allocation while retaining live lighting, motion and resource reloads. Reapplying identical programmable settings no longer triggers appearance rebuilds; shallow-wall Duplifier copies avoid the redundant legacy width reset.
- Landing gear and blocks moved by Programmable Ramps now share diagonal walls' loaded-terrain render distance instead of disappearing beyond 64 blocks.

- Fixed repeating diagonal wall connections, neighboring-block clipping, stacked orientation and opened diagonal trapdoor selection. Added regular trapdoor Slide over surface movement.
- Expanded programmable dialogs with tall texture lists, shared two-column layouts, full-width face buttons and independent porthole/slab side textures. Screen and Controls pickers share general materials while keeping full-height and half-height artwork separate.
- Simplified light texture labels and restored Doors in regular/diagonal trapdoor pickers. Door pickers now show each design once, with a separate Small/Medium/Large selector.

- Programmable Storage now supports optional per-face texture overrides, including inherited Storage-set faces, configured-item persistence and Duplifier copying without changing inventory contents.

- Added Programmable Storage: 27 inventory slots, hopper/item-handler access, comparator output, shared material picker and matching top/side/front Storage sets. Defaults to Cabinet; crafted from a chest and Programmable Block. Appearance copying preserves target contents, and mining drops contents separately.

- Added ten Storage material sets from matching top/side/front sheets, split into 30 crisp 140Ãƒâ€”140 textures.

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

- Extra Large (2Ãƒâ€”2) landing-gear alignment with matching reservations and saved/copied size settings.
- Adjacent next-block trapdoor placement, preserving the owning tile outside the gear shaft and holding the leaf open until retraction completes. Supports either rotating or sliding movement.
- Custom block/door texture sample slot across shared material menus, with vanilla/mod atlas artwork, upper/lower door mapping and non-consuming inventory selection.
- Fit/Tile housing controls for screens, half/full inputs and consoles. Static surface and material choices participate in Duplifier copying.

- Shared categorized texture picker with thumbnails across programmable materials, lights, doors and static screen/control surfaces. Adds full door artwork and static first-frame screen textures while preserving existing finish indices.
- Filesystem PNG categories under `config/vandorlabs/textures`, with first-run Example folder/sample panel, path-based saved identifiers and missing-client fallback. Different client/server catalogs are supported.

- Programmable Light Frames offer centered Small/Full sizing; Light Slabs offer side-texture Fit/Tile. Small frames remain individual artwork panels.

- Loaded-only rectangular programmable trapdoor groups through 8Ãƒâ€”8, with shared outer hinges and scaled sliding travel. Diagonal trapdoors connect across opposite-slope rows and full-width vertical/depth offsets.

- Programmable Diagonal Trapdoor with wall-compatible half-width/full-height, full-width/full-height and full-width/half-height placement. Its 2px leaf leaves 1px clearance on each wall surface; pairs and 2Ãƒâ€”2 surface groups rotate or slide toward opposite sides. Shares textures, redstone channels, configuration and copying with programmable trapdoors.
- Programmable Trapdoor with 78 block finishes, Rotating or horizontal Sliding motion, Bottom/Middle/Top positions, physical redstone and virtual channels. Adjacent pairs and complete 2Ãƒâ€”2 squares open together toward opposite sides, without frames or hinge hardware. Includes Configurizer, Duplifier, configured items and Dynmap support.
- Non-rendering Minecraft/Forge regression checks for placement, diagonal vertex data, connected copying, ramp clearance and trapdoor geometry, grouping, persistence and redstone.

### Fixed

- Staggered diagonal trapdoor groups connect in all three shape modes, including Half width / Full height and Full width / Half height, with saved links and shared movement/settings.

- Opposite-slope diagonal groups derive their outside direction from the convex bend. Both rotating rows and the staged sliding clearance move outward, preserving hinge pivots and the inset/lift animation. Reversed coplanar rows share an outside face and retain their established rotation direction.

- Programmable screen, input and console dialogs use categorized list thumbnails for previews, removing the separate preview areas while preserving animation and display controls.

- Diagonal trapdoor hotbar icons use a smaller scale for default, configured and Custom finishes. The Duplifier dialog grows to fit all options, keeping its last-row trapdoor hinge/layout switches clear of Done and fitting smaller GUI resolutions. Its movement switch explicitly includes trapdoors.

- Plain side-mounted trapdoors hinge against the clicked supporting block instead of its opposite edge. Configured Next block items retain their explicit saved hinge.

- Floor- and ceiling-mounted trapdoors sit flush visually, using a tiny coplanar-face inset instead of a one-pixel gap. Rotating leaves retain clearance from their support when open.

- Next-block trapdoor leaves project one pixel beyond the covered cellÃ¢â‚¬â„¢s far edge, with matching selection/collision and fully retracted sliding endpoints. Normal and diagonal trapdoor thin edges use the standard programmable doorÃ¢â‚¬â„¢s metal side texture, including Custom materials.

- Next-block placement is restricted to individual trapdoor mounts, preserving joined groups when configuration or copied settings request an offset. Offset leaves can be selected and collided with in neighboring cells after hinge changes; selection outlines follow their actual bounds.

- Diagonal trapdoors have separate width/height controls and apply combined width edits together. Trapdoor Fit/Tile is saved/copied; Tile repeats and mirrors built-in and Custom door artwork instead of stretching it over 2Ãƒâ€”2 groups, while thin edges keep native door artwork.
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
- 11 Hull Plating finishes from `hulls`, numbered 1Ã¢â‚¬â€œ11 and available in the shared housing finish menu.
- The building guide now shows all 78 selectable finishes on Programmable Blocks, in groups of up to ten with names in display order.
- Extra Large Landing Gear doubles Large's model dimensions. Its centered mount and moving parts reserve a three-by-three footprint; extension remains configurable from 0Ã¢â‚¬â€œ4 blocks.
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

See the [illustrated guide](docs/versions/version-1.1.md) for examples and settings.

### Added

- Optional per-face texture overrides for Programmable Block, Slab and the new Programmable Stairs. Disabled by default; faces inherit the main texture unless overridden.
- Programmable Diagonal Portholes with four opening shapes, half/full width, half-height/full-width geometry, glass shade and joining controls.
- Half-height diagonal walls, independent Fill Inside/Outside controls, and a standalone Programmable Diagonal Half Console.
- Luxury and Military Seats with joining controls and adjustable height (Ã‚Â±2 pixels). Default legs are one pixel taller than the original models.
- One configurable Landing Gear block with Small, Medium and Large sizes, animated extension/retraction, redstone modes and channels, and immediate size/length previews. Extension ranges from 0Ã¢â‚¬â€œ4 blocks in half-block steps; all sizes use equally thick arms with tiled frame-interior metal.
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
