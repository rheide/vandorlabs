# Changelog

## 3.0.0 (in development)

- Control powered Forge airspeed with an Autopilot Module and a full or half-height Autopilot panel. Flight instruments report kiloblocks per hour to one decimal place. Existing waypoint modules keep their routes under the name Autonavigation Module.
- Check gross weight, fuel and cargo against installed lift capacity when selecting Hover on Forge. The Vehicle Controller shows lift capacity on its Flight tab. Climb, descent and vertical braking respond to gross weight relative to available lift.

- Walk on a moving Forge aircraft’s deck with Sprint + Sneak in powered Flight. The aircraft levels out while the pilot walks. Click the pilot seat or press the same keys to take the controls again. Jump at an open edge to leave at the aircraft’s geographic position.
- Keep the full width and diagonal of the Shallow screen when lowering its casing by four pixels with a one-pixel lip at each end. Select independent top and bottom cockpit displays and see active throttle knobs in cyan.
- Choose a small or large centered transparent flight HUD on the canopy windshield. The short rear roof of a Long Angled canopy uses the Side glass finish.

- Recover completed parking after a drive burns fuel or controls change. A conflicting hull edit protects the affected area while nearby independent vehicles can still enter Taxi or Hover.

- Limit Flight to 300 m/s at maximum altitude. Align distant JourneyMap beacons to the interpolated client camera and send populated destination columns while the rest of the arrival footprint loads.

- Fix the Forge Copy Controller channel increment for built-in transmitters including Tanks and Thruster Levers, cockpit channel banks, installed Autopilot modules, and stored Arm sections.
- Retire Resident cruise. Anchored and Physical cruise anticipate the flight path when requesting and drawing finer terrain. JourneyMap waypoint labels follow the cockpit camera heading.
- Render finer Forge seed terrain farther out, extend the clear-air range, and double the seed atlas cache. Keep the interpolated cockpit and terrain steady when the vanilla tracker reports a steep climb or dive.
- Blend native Overworld terrain into the Forge seed view from Y=256 to Y=320 by default. Reduce drawing of overlapping terrain layers and let servers lower the handoff altitude to limit chunk loading during flight. High-altitude descent sheds excess speed through this band. Seed-view water occludes the stars.

- Count upright wheels on settled side-mounted swing arms as landing supports. Taxi and ordinary Park use the same tire contact at the runway surface.

- Fuel Maxwell Drives through three inlets and select Digital or Analog redstone response with the shared activation selector. Build five engine sizes and inspect remaining fuel on the front gauge. The front casing and corner trim render without flickering.
- Operate Thruster Levers on placed and assembled Forge vehicles. Right-click advances the handle, and Creative Shift + left-click reverses it. The output changes after four quiet server ticks so intermediate positions send no pulses.
- Select Flight from Vehicle Status and build speed on a runway or accelerate from Hover. The Controller's Flight tab shows actual weight, the reduction from lift plates, and effective weight. Compare the current takeoff limit with the theoretical maximum from every installed drive.
- On Forge, use A/D to roll through a full turn and release to hold the bank. W/S pitch in the aircraft's frame, and the cockpit view and attitude indicator follow the hull. Above 45 degrees nose-up, acceleration falls and pitch eases toward 45 degrees.
- Keep flying forward as reduced Maxwell thrust lowers the altitude ceiling. Glide on total thrust loss and level out for landing or a return to Hover. Recover to a level pose after terrain contact.
- Keep the Forge pilot’s view anchored to the cockpit through repeated rolls, and carry forward motion into Hover while slowing down.
- Raise Forge Hover ascent and descent to 8.4 blocks per second.
- On Forge, show cruise coordinates and waypoints in JourneyMap, start preparing the seed view at Y=200, and interpolate the ground view and geographic map position between route updates. Independently cached near and distant seed tiles show finer ground and a wider horizon. Rain and snow stop above the build ceiling. Selecting a manual Taxi or Hover transition above Y=256 displays a descent reminder.
- Stretch a single texture across side-by-side Canopies or Long Canopies.
- Choose anchored or continuous physical X/Z cruise in the Forge server config. Anchored cruise is the default. A solo pilot can use physical mode to fly and operate the cockpit at the geographic coordinates shown by F3. Normal descent restores terrain at the destination.
- Teleporting through JourneyMap or a vanilla command releases the rider. When the last pilot leaves, the craft lands at the surface beneath its geographic position.
- Supply onboard engines and observers through Thermal Dynamics, Mekanism and Galacticraft conduits. Route RF, fluids or items through settled Arm endpoints.
- On Vehicle Status, select Park, Taxi, Hover or Flight from a two-row grid. Unavailable modes are gray and fuel stays within the screen.
- On Forge, choose Status or four flight instruments in the Vehicle Status folder. The Controller projects maximum speed and altitude, and lighter Maxwell aircraft gain speed sooner above Y=256.
- Choose **Status (Half)** or a compact flight instrument independently for the top and bottom of a split display. Park a stopped craft with a signaled Maxwell running. On formation, a Maxwell immediately connects to existing Fluiducts at its inlets. A drive at zero throttle stops burning fuel and its nozzle stops glowing.
- On Forge, choose **Redstone (Half)** independently for each half of a split display, with up to four controls per half. Select **Full** or **Shallow** on a Programmable Diagonal Screen to choose the original wedge or a lowered, front-clipped 45° face with the same display proportions.
- Configure Main and Side glazing independently on Forge Canopies and Front Canopies, with Vehicle Status and flight instruments selectable for each pane.
- Copy Thermal Dynamics fluiducts and fluxducts with their attachments on Forge. Survival copies start with empty supported storage and require the matching duct and attachments. Creative copies preserve stored amounts.
- Transmit Tank and Battery fullness over redstone channels.
- Display a received analog redstone channel as battery bars on a screen, console or cockpit.

- On NeoForge 1.21.1, drive, hover, fly and park a Pilot Seat vehicle. Recover the mounted pilot after a server restart in high cruise.
- Operate onboard Redstone screen rows, route resources through settled Arms and use finite Mekanism Fluid Tanks, Energy Cubes and unlinked RFTools Dimensional Cells on native vehicles.
- Fabricate both Vehicle Couplers in the native Gantry and engage matching couplers before assembly to connect parked sections into a rigid hull. Configure all seven stationary Cockpit surfaces.

## 2.12.0

- Draw all stationary Programmable Glass as ordinary chunk geometry, like vanilla glass: selected artwork, Screens in static playback, opaque panes and every stock tint. Glass walls no longer add per-block sorting, draw calls or tile renders each frame, and under shader packs they cast shadows like stock glass. Opaque panes use the solid layer. Animated Screens, corner glass, glass with contained fluids and glass on moving vehicles keep their existing renderers.
- Shade only one sheet of translucent artwork glass, with the same overall opacity as before, roughly halving its fragment cost under shader packs.
- Keep the faintest stock tints (Soft Cyan Mild and Normal, Soft Dark Grey Mild) visible as chunk geometry by raising their faintest pixels to Minecraft's minimum visible alpha; they look slightly more tinted.
- Remove the `vandorlabs.staticGlass` and `vandorlabs.staticGlassRenderer` startup options.

- Add flat and sloped Programmable Corner Glass. The Middle flat bevel joins two perpendicular Middle glass panels. Placement aligns with the clicked or adjacent glass and bends towards the placing player; stacked corners keep their bevel, and corner frames render without seam stubs or see-through rails.
- Clear the rear aperture and moving lower crossbar of Canopy and Long Canopy. Repair side glass gaps and the Long Angled Front Canopy’s open rear and frame faces.
- Choose a true geometric center split or a cardinal opening direction for joined Programmable Trapdoors, including odd-width groups. Leaves of groups up to eight wide stay solid and selectable wherever they move, on Forge and NeoForge.
- Keep Segmented Door animations independent when WorldEdit pastes a door at another location.
- Shape Diagonal Corner Glass like the diagonal glass it continues: it stays at 45 degrees, takes the clicked glass's size, lean and near or far position, and joins only matching glass. Choose its Size and Slope in the glass dialog.
- Always draw stacked Corner Glass, including with OptiFine and shaders.
- Keep Diagonal Corner Glass frames at full thickness where they meet diagonal glass, so top and bottom frames run through a corner as one mitred frame.
- Add Programmable Corner Wall and Diagonal Corner Wall on Forge and NeoForge: 45-degree bevels that join Programmable Walls at the same depth, or continue diagonal walls with matching size, lean and position, using the wall material and editor.

- Open Large Doors from a horizontal seam with Sliding Split, or join compatible doors into a 3×6, 6×3, or 6×6 opening.
- Build Arm columns through open Large Doors, including joined groups. The door stays open while an arm runs through it, and carried blocks pass through the doorway.
- Let wide Arm attachments such as a 3×3 Turntable Surface pass through every part of an open Large Door, including the leaf edges left in the opening.
- Join four Large Doors placed one by one into one 2×2 door, whichever wall each was placed from: a door with Join On takes the orientation of the joining door beside it. Sneak-click a door's edge or a joined door's face to place the next door on that side, on Forge and NeoForge.
- Keep joined Large Doors drawn while only part of the opening is in view, open joined rotating doors to the full group width, and tile glass materials across the whole joined door, on Forge and NeoForge.
- Show selected resource artwork on Arm mounting faces and Extender end caps, with a reversible flow setting and activity bar. Keep Large Arm inventory icons at least as visible as regular variants.

- Open Survey Dish and Sentinel Array dialogs with ordinary right-click. Show bottom RF connection status, consume 20 RF/tick only while Scanning, and let Redstone select Scanning or Idle. Linked Screens and Navigation Tables show a haiku when no observer is powered.
- Charge or discharge inserted Battery Modules through a Module Rack’s rear Power Port, with an In/Out dialog setting.
- Preserve standalone Thermal and Mekanism machines on Arms, including rotated tank fluid levels and transfers.
- Carry RFTools machines, logic blocks, screens and Power Cells on Arms, vehicles and Turntables. Power Cells keep their network's energy and rejoin it where they land. Blocks tied to fixed coordinates, such as Matter Transmitters, Monitors and Elevators, are refused with the reason.
- Connect Thermal Dynamics ducts and Mekanism pipes to an Arm's tip instead of carrying them; the arm transfers its selected resource through them.
- Keep a Thermal Dynamics flux duct feeding an Arm in Power mode connected across extensions and retractions, so a carried Survey Dish keeps scanning.
- Keep every item a Thermal Retriever delivers through an Arm; stuffed items returned after a move no longer vanish from the receiving inventory.
- Log an unreadable Arm recovery journal instead of silently refusing every arm move in the world.

- Press flat Programmable Plates in the Assembly Press and use them to craft the four programmable armor pieces.
- Use the former Programmable Matter finish as the default for Programmable Blocks. Multiblocks accept any Programmable Block finish. Existing standalone Matter blocks convert to Programmable Blocks.
- Mount Large Programmable Doors horizontally against a block side on Forge and NeoForge to open a vertical passage. Arms and their payloads can pass through the clear opening.
- Capture Forge vehicles with retracted Extenders while retaining displaced arm sections and their materialized attachments.
- Route RF power, fluids, or items through a properly aligned stationary Arm endpoint on Forge and NeoForge. Transfers are off by default.
- Form Maxwell, Meridian, Oculus and Vector Fold structures and fabricate their Cores on NeoForge.
- Form the standalone Paladyne and Seyr Navigation Computers with authored Off, Idle and Computing displays.
- Copy idle, disconnected Synthesis Towers, Assembly Presses, Enclosed Gantries and eligible standalone drives and Navigation Computers with empty slots at exact frame cost. Grid-aligned Extenders can carry empty idle production builders with block pushing off.
- Import mapped Forge drive, computer and production snapshots. Retain supported builder inventories and resume supported standard recipes from their saved RF credit.
- Add the native Advanced Pilot Chair with its authored footprint, adjustable height and two seated armrest sliders.
- Form and sit in a stationary Cockpit with a reversible screen bank and independent Redstone rows on the center monitor and right armrest. Use the standalone Yoke and Screen’s two Redstone surfaces.
- Copy complete stationary Navigation Table groups and Survey Dish and Sentinel Array frames at exact material cost on NeoForge.
- Copy complete passive Programmable Screen layouts in all seven roles, including diagonal joins.
- Add an opt-in native Turntable with block capture, swept-collision rotation, Redstone, upward-facing rider transport and journaled quarter-turn restoration.
- Show Forge-matched Press and Gantry fabrication, mount interactions and pickup positions on NeoForge.
- Add opt-in legacy wheel conversion on world load or in a player’s inventory and stationary conversion by operator command. Add bounded cold Ramp actor recovery for adult cows.
- Share armor appearance settings from inventory samples, supported world faces, and the Duplifier on NeoForge. World samples include static Light artwork, ordinary and Large Door leaves, Trapdoors, flat and diagonal Wall housings, powered Triggers and static ordinary Screen artwork.
- Preserve native floor and ceiling propulsion wedge geometry and tile data through WorldEdit quarter-turns.
- Add catalog-gated Ramp Controller production to the native Synthesis Tower and show the four native machine categories in JEI.
- Enable the NeoForge Copy Controller with material-priced Survival copying, journaled recovery, and output updates visible to connected players.
- Add the remaining Assembly Press recipes to NeoForge, preserving the existing Navigation Data Module recipe and saved jobs.
- Add NeoForge Gantry production for all eight mechanical recipes and rear corner port support for the Press and Gantry.
- Copy complete double chests on NeoForge, keeping both material and inventory halves protected through recovery.
- Craft and configure programmable armor on NeoForge, with the authored role and company designs, shared Programmable Block materials, and padded inventory icons. Configure mounted pieces and sample inventory block/door artwork without consuming the item.

## 2.11.0 development history

See the [2.11 showcase](docs/versions/version-2.11.md) for the three builders in motion and a Navigation Table pan and zoom demo.

### Vehicles

- Raise Hover cruise speed to 16 blocks per second. Sprint adds 25%, up to 20 blocks per second. Taxi reaches 6 blocks per second without a thruster and 8 with an active thruster.

### Ramp Controllers

- Restore both horizontal axis choices on side-mounted controllers and slabs, including southward ramps on west-facing controllers.

### Builder compatibility

- Preserve legacy Core save data and recipe IDs during recovery, including when the Core block has yet to be restored. Bound the NeoForge builder queue by job count and snapshot size.

- Protect the Master Catalog Chip from being overwritten by Digitize on both versions.

- Dismantling a three-block-high Enclosed Gantry from 2.10 restores the Matter stored in its original top rim.
- A Synthesis Tower keeps the chosen block template when a production job starts, including across chip removal and world reload. Digitize checks the stored input against its admitted sample during processing.

### Devices

- Keep the Printer and Scanner buttons stable when switching tabs, preventing a repeated click and client freeze on Forge.

- Add redstone edge triggers and sided item automation to the Printer and Scanner. Unconnected devices keep their manual output behavior.
- Seat the Printer and Scanner buttons flush against the front. Seat books in the Scanner’s top frame and display the original pixels on mounted maps. Rename their texture-picker category to Devices.

- Add dark cyan and silver Indoor AC slab and Outdoor AC cube creative presets. Place ordinary programmable slabs/blocks with six editable face textures, preserving normal placement, slab combination and configured drops.
- Reuse map palette matches across pixels on both versions. The Forge Printer requests the biome grid for a map in one batch and skips that request when scanned pixels cover the entire map.
- Add functional Printer and Scanner blocks with front Chip Mounts, half-size buttons and standard inventory dialogs. Print Journal/Catalog books and prefilled scale-0 maps for one ink sac plus one paper; scan original maps/books without consuming them, including enchanting-font journals. Save scanned map squares and pixels on Navigation Chips for offline display. Import native 64px appliance artwork and expose its face finishes in the texture picker.
- Reuse detailed Vehicle Chip material previews in the Vehicle Controller dialog and Vehicle Status screen, retain compact parked previews, and sample vanilla chest entity skins correctly.
- Separate navigation messages from terrain depth, brighten the loading pulse across the entire display background, and start it immediately on local pan/zoom input.
- Simplify observer names to Survey Dish Core and Sentinel Array Core while preserving existing registry IDs.

### Seed navigation (Forge 1.12.2)

- Show zoom and live observer ranges on Navigation Tables. Offer seven zoom levels from 128 to 8,192 blocks across on a single table and sharpen the preview after panning or zooming stops at every zoom level. Give each active observer its own radar sweep and show uncrouched players above sea level. The Sentinel Array sweeps a 128-block radius every eight seconds in red and the Survey Dish sweeps a 256-block radius every 24 seconds in cyan. Terrain coverage spans 63–1,000 blocks for the Array and 250–4,000 blocks for the Dish.
- On stationary Navigation Tables, use the active radar sweep to pick up actual loaded surface colors. Deduplicate recordings in a bounded world archive shared by Navigation Data Modules, and let a Navigation Chip carry a surface snapshot.
- Match Programmable Surface texture orientation and legacy Fit Aspect defaults in Dynmap.

- Apply queued map images before evicting cached textures. Keep unavailable captured observers out of discovery while retaining their saved payloads. Skip fully indexed circles during the fallback coverage scan.

- Keep removed Nav Table bounds safe during native/vehicle handoffs with OptiFine, and release parked/destroyed craft from navigation discovery.
- Support carried Navigation Tables, Navigation Screens and formed observers in Vehicle Mode. Follow physical position and altitude, center Screens with a crosshair, record module coverage along the route and retain it through saves and parking. Show a missing-observer haiku aboard unequipped vehicles.

- Enlarge the formed Survey Dish and Sentinel Array models within their existing frames. Widen their mounting plates, enlarge the Dish body and raise the Array body; retain clearance through the full Dish sweep, all facings, inversions and frame sizes. Keep existing frame sizes, range and saved layouts.

- Cache observer layout, pivots and model keys to reduce settled draw allocations to 24 bytes. Keep enlarged models at the same 145 Dish / 114 Array quads and retain model resource reloads.
- Scale Survey Dishes to N×N×N (N=3–16) and Sentinel Arrays to N×N×2N (N=2–8), including inverted construction and arm-carried ownership. Keep base scanning range at Y=64; increase the size bonus linearly through Y=128, capped at +100% radius for twice the base linear dimensions. Preserve existing base-size saves and record the enlarged coverage in Navigation Data Modules.

- Manufacture Navigation Data Modules in the Assembly Press. Add one low cassette bay to joined tables, record whole source coverage in a dimension-bound item, preserve offline browsing, and eject it once if its bay disappears. Reuse native rack insertion/withdrawal motion and Navigation Chip artwork.
- Replace the spinner with smooth geographic pan/zoom transitions and dark pulsing newly exposed areas; draw a crosshair icon for recentering. Set source radii to 500/2,500 at Y=64, rising linearly to a capped 1,000/5,000 at Y=128.

- Keep map pixels and controls at a fixed physical scale as tables grow; render one cached surface per assembly. Add player/facing markers, CTR recentering, smooth loading transitions, deeper biome detail and shared bounded geographic caches.
- Retire console-equipped Survey Dish and Sentinel Array variants with saved block/item remaps. Remove their standalone chip slots, support upside-down formation, show scanning radius in configuration, and follow physical height and position on moving arms. Preserve animation phase and independent receive/transmit settings through arm handoffs.
- Keep fallback Core scaffolds visible in Dynmap with valid palette-face UVs and cover inverted observer clipping. Register the battery rack's native charge-bar textures so Dynmap starts with the current models.

- Add a north-up, seed-generated biome map to navigation tables/consoles and existing chip-capable programmable displays, with pan/zoom controls and center coordinates. Sample generators without requesting chunks; distinguish uncovered areas from generator errors.
- Link formed Sentinel Arrays and Survey Dishes through ranged transmit/receive channels. Combine matching sources across selected channels, with height-dependent observation radii based on 500/2,500 blocks at Y=64 and capped at 1,000/5,000 at Y=128, independent of connection distance and redstone power.
- Write dimension/X/Y/Z locations to Navigation Chips from navigation tables/consoles. Show saved-location markers, recenter on reinsertion, and retain a 1,000-block observation radius without a source. Preserve saved display artwork and existing chip ownership.
- Allow vanilla and Vandor wall-mounted switches/levers on enclosed builder multiblocks, and let placement clicks reach the held block item for redstone activation.

### Catalogs and Forge builder production

- Add Synthesis Tower Digitize mode: reverse-scan one eligible block into a one-design Catalog Chip for 8,000 RF. Preserve supported appearance settings and physical item ownership across cancellation and saves. Reorganize Production with a Mode toggle, Chip/Input → Output arrangement, narrow RF bar and horizontal batteries; hide production inventory on the Redstone tab.

- Preserve fabricated item proportions and authored world scale: keep rack modules horizontal, flatten only cards/chips and shrink only Gantry models that exceed the workspace. Keep random Matter interior colors exclusive to the Press.
- Tile door-interior artwork on builder wall caps, preserve Tower border detail on half-panels, and remove duplicate glass faces and actual upper-rail overlaps.

- Double Press/Gantry build speed to 200 ticks at 40 RF/tick, retaining 8,000 RF per job. Label inputs Block/Ingot and explain the required Matter ingredient in slot tooltips.
- Fit previews to actual transformed item geometry. Tile grey casing, remove the Press hood and add flat item fabrication with green corner beams and Matter-colored interior voxels.
- Raise the Gantry frame to four blocks (34 Matter Blocks and one Core), halve its head and animate the head/rail over printed blocks with a blue fan beam. Allow finished chamber-item pickup. Dismantle existing three-high frames to recover their materials before rebuilding.
- Adapt Vehicle Chip preview density toward eight voxels per block per axis, reducing detail to fixed voxel, surface-work and cache budgets for large models.

- Increase saved Vehicle Chip preview sampling from 12³ to 15³ cells and color voxels from their represented block materials. Cache thumbnails and material colors; keep legacy occupancy-only chips readable.

- Widen builder dialogs with a full-height design list, selected-design preview heading, right-column inventory, vertical module slots and a shared top row with exact combined-RF tooltips. Use the same responsive layout in both versions. Correct Catalog Chip previews to view models from above.

- Keep item appearance overrides on concrete item models when sharing packaged geometry, so typed chip artwork and other item predicates remain selectable in Minecraft 1.12.2.

- Add supplied Journal, Vehicle, Navigation and Catalog Chip artwork; retain company cards and change chip appearance when programming journals or vehicles. Blank Navigation Chips can be programmed at navigation tables.
- Store explicit per-machine blueprint lists on Catalog Chip (Master), including Assembly Press Battery Module and Autopilot Module designs. Browse catalog models using existing Forge journal screens and their previous/next controls.
- Extend Forge Tower production to the supplied Core families plus Vehicle and Ramp Controllers when listed on an inserted catalog. Keep the three builder Cores available without one.
- Give Forge Assembly Press and Enclosed Gantry the shared production inventory, Redstone controls, RF/battery handling and adjacent output. Press recipes use Matter Ingots; Gantry recipes use Matter Blocks. Refresh both casings, corner ports and lower workstations using shared mount coordinates. NeoForge also supports the Navigation Data Module recipe in the Assembly Press.

### Three builders on NeoForge 1.21.1

- Add only Synthesis Tower, Assembly Press and Enclosed Gantry and their Core items, retaining the shared frame sizes and all horizontal facings/Core corners. Other Core multiblocks remain deferred.
- Add durable asynchronous formation, dismantling and Survival mining, with escrowed inventory drops and abrupt cross-chunk restart recovery. Preserve malformed saved ownership inertly and refuse non-player removal of retained assemblies.
- Match powered Synthesis production, physical battery/chip handling, rear-bottom RF/item ports, Production/Redstone dialogs and textured previews. Publish every reserved cell and its owner data to clients after completion.
- Reuse fixed-size, reload-safe meshes for all three structures, bottom-up Cores, four scanning beams and nearby-only fabrication effects. Native arm/copy integration for these assemblies remains deferred.

### Synthesis Tower

- New Core jobs use 16,000 RF over forty seconds at full power. Jobs already underway retain their original cost. Finished vanilla outputs use their stationary block models in the chamber.

- Produce Synthesis Tower, Assembly Press and Enclosed Gantry Cores from Programmable Matter and RF, with saved build progress and one owned output. Keep existing frame dimensions and materials.
- Add an inventory dialog with full textured rotating structure previews, battery power status and a chip slot for future designs. Ordinary right-click and Configurizer open the same dialog.
- Add a standard Redstone tab with physical and receiving-channel activation, including Redstone On/Off and saved pause/resume behavior. Wait for deferred inputs to settle after loading before consuming Matter or RF.
- Fit four sliding Battery Modules in a flush rack, retaining 10,000 RF per item and displaying up to 40,000 RF in the shared Source/native dialog. Preserve each bay, charge and chip through physical insertion, extraction and reload; keep client inventory-slot updates separate from authoritative bay snapshots so removal and charge updates retain shared rack motion.
- Use 20 RF per tick for each 16,000 RF Core, taking at least forty seconds. Export finished Cores into a chest or compatible inventory beside an item-output face; retain output when the destination cannot accept it.
- Light rear RF/item ports from compatible endpoint presence, including idle or push-only connections. Add rear RF/item ports, a smaller left display with a projecting keyboard, a right chip mount/rack and neutral casing panels. Show bottom-up Core fabrication with four scanning beams only while spending RF; retain the partial Core while paused. Keep previews inside their screen, raise the fabrication tray by two Minecraft pixels, and use lighter, translucent scanning beams. Align the chip and rack to the right-hand casing blocks. Remove casing faces covered by the rack and preserve authored texel density on shortened side panels and rack rims. Cull fabrication effects beyond nearby viewing range.

### Core models and JEI

- Replace all thirteen Forge Core block/item models and the three native builder Cores with Minecraft-pixel cages in each Core’s primary color: larger legacy-textured inner cube, four stepped diagonal supports, recessed face crosses and detailed solid corner cubes on a 32×32 pixel grid. Use the same geometry for fabrication and Dynmap, with padded inventory icons.
- Add shared Matter Infuser and Synthesis Tower JEI categories for iron conversion, Battery Module charging and the three Core outputs, including costs and processing requirements. Support Infuser input/catalyst transfer; keep tower design selection in Production.

### Native Matter Infuser

- Add the NeoForge 1.21.1 Matter Infuser, its original recipe and Creative Infuser Core, authored artwork, shared right-click/Configurizer inventory and sided item access.
- Retain sunlight, redstone catalyst, iron-to-Matter production and 50 RF/tick Battery Module charging, with saved progress and charge ownership.
- Give both Infusers the same shared standard dialog, branded header and solar/progress indicators instead of a vanilla furnace background and flames.

### Ramp Controllers and dialog parity

- Enable the native Ramp Controller and Ramp Controller Slab with the same seven modes, six scan faces, horizontal slab housings, configured items and receiving channels as the Source reference.
- Drive escalators through ordinary scheduled ticks, retain their phase when stopped, and sleep outside the nearby-player range without elapsed-time catchup.
- Keep generated platforms and supported actors under durable ownership through capture, reversal, ordinary return and interrupted restarts. Preserve private item data and retain unsupported or malformed saved ownership instead of guessing a repair.
- Share Ramp, Port and Surface dialog definitions between versions alongside the Infuser and Tower. Reuse Source family headers, logos, notebook tabs, button artwork and the Light's brightness track across native editors; preserve reverse clicks and compact layouts.

### Battery Modules

- Add single-item Battery Modules holding 10,000 RF, with a Programmable Matter Ingot, gold and redstone recipe and an item energy capability.
- Charge modules in Forge and NeoForge Matter Infusers using sunlight and redstone catalyst. Keep partial charge on the physical item through charging, rack insertion, extraction and reload.
- Show the original Power Port red on/off bar between the module handles, lighting one authored pixel column at a time with charge. Share the display across items, racks and towers; remove the former cyan/amber bars and include an empty and a full module in Creative.


## 2.10.0

### Release review and recovery

- Require a successful Navigation Table chip transfer before taking or returning the player’s chip on NeoForge. Read chip appearance and battery charge without copying their full saved data.
- Include the 2.11 gameplay and performance focuses in the live release inventory, keep both vehicle navigation restart phases in one fixture on Linux and Windows, and check the visible Battery Module charge bar at all five fill levels in Dynmap.

- Keep native Arm and Ramp inventories frozen during publication callbacks. Restrict internal block writes to one exact cell at a time so retained inventory wrappers cannot duplicate captured contents.
- Verify actual native entity write completion and expected owner records before completing recovery checkpoints. Refuse unsupported Ramp actors before capture, and retain malformed Arm drop escrow inertly instead of throwing during world load.
- Remove quadratic retained-Glass restoration and native join-cache accounting. Preserve cache budgets, lifecycle invalidation and quiet-frame reuse.
- Reapplying an identical effective join policy preserves dormant settings without dirtying or rebuilding ordinary blocks.
- Restore the original Arm payload coordinate mapping after a failed placement is completely rolled back; retain the destination mapping when unfinished cleanup still needs journal recovery.
- Invalidate cached vehicle joint render transforms when loading a replacement pose.
- Finish tall Segmented Door terrain handoffs when lower sections are outside the camera view, using bounded asynchronous upload scheduling.
- Use consistent vertex placement for individual and batched static material Glass panes, avoiding artwork-edge rounding differences while preserving their normals, lighting and UVs.
- See the [saved-data and performance review](docs/release-compatibility-2.10.md) for findings, validation boundaries and compatibility details.

### Module Rack (Forge 1.12.2 and NeoForge 1.21.1)

- Preserve undecodable Forge Rack module inventories as inert recovery items instead of dropping invalid rows or truncating stack counts; retain opaque module and housing data.
- Keep the exact original Autopilot controller inert and recoverable if migration and rollback both fail to install. Preserve its canonical save and one configured recovery item for a later retry.
- Add a four-slot Module Rack and three-dimensional Blank Module item. The darker compact housing has a one-pixel rim, a full-depth right wall around a recessed inner panel, and two tiny status squares per bay: lower amber when empty and upper cyan when occupied.
- Insert a module by clicking its intended slot or aligned button, with an eased slide into the rack. Empty-hand interaction retrieves that slot's item; saved contents retain their data and drop when the rack breaks.
- Mount racks against any of the six faces, including upward-facing floor racks and downward-facing ceiling racks. Reuse the Light and Port viewing-angle placement rule. Slot selection, indicators and module travel rotate together.
- Render settled racks and modules in shared opaque chunk meshes. Use pause-aware temporary client animation during insertion and extraction, including rapid reversals, without ticking idle tiles or submitting a permanent tile renderer.
- Install Autopilot as a module with an abstract route indicator, with per-bay route, activation and receiving controls. On Forge 1.12.2, existing Autopilot blocks migrate after chunk loading while preserving navigation, authorization, capability data and their original backup. Older carried snapshots retain their saved representation until returned to the world.
- Configure housing and proportional Redstone output through the standard tabs; the four occupied bays produce levels 4, 8, 11 and 15.
- Apply custom housing only to the five outside faces; preserve the front panel and recessed artwork. Keep route, activation and channel edits on the physical Autopilot Module immediately, preserving independent module settings and opaque module data through extraction and reload.
- Native Racks retain modules exactly once through captured mining and interrupted cross-chunk transfers. Insertion and extraction use bounded, isolated rendering buffers and release their terrain observers when settled. Native Vehicle navigation remains unavailable.
- Include crafting recipes, model assets and focused geometry, inventory, animation and rendering checks. See the [Module Rack guide](docs/module-rack.md).

### Forge vehicle fixes

- Keep inspection geometry separate from Coupler ownership-prepared geometry. Valid native Couplers no longer trigger a changed-craft refusal during Taxi assembly. Both stages retain exact snapshot and ownership checks; edits during preparation still refuse safely.

### Materials and connected artwork

- Add Port Hull to the generic Hull texture category, retaining all earlier saved texture choices.
- Remove Fuel Port Dual and Fuel Port Round from the picker and bundled artwork. Retain their saved choice numbers with the standard compatibility fallback; update the texture browser and Dynmap palette.
- Add thirteen Tiles finishes and six Utility Door designs, including Bathroom and Stall, resized to the existing material and Door artwork resolutions. Preserve prior texture indices and regenerate the browser, category collages and Dynmap data.
- Mirror filesystem Door artwork on paired leaves, matching selected vanilla-block materials in Forge 1.12.2 and NeoForge 1.21.1.
- Standardize image layout captions as Texture: Tile / Fit (Aspect) / Stretch. Keep Join for connectivity and Junction for diagonal junction shape.
- Add Vertical, Horizontal, North/South and East/West texture joining to supported connected building surfaces. Remove the Even/Odd coordinate filter; older saved choices behave as All. Invalidate warmed joins after Copifier batch changes while preserving original saved snapshots and opaque capability data. See the [texture joining guide](docs/texture-joining.md).
- Require both Glass and Porthole neighbours to allow the shared join axis. Duplifier changes rebuild the original group’s frames and aperture rims, including splitting Horizontal groups into individual North/South blocks.
- Ignore obsolete thin-wall depth metadata when joining full-depth Porthole Blocks. Existing stacked blocks with matching visible settings now join without replacement; thin walls still require matching planes.
- Apply connected Duplifier settings to every matching full-depth Porthole Block regardless of its unused depth flag. Refresh unchanged original members when an edited neighbour enables a previously rejected join, including retained Forge frame and pane buffers; preserve thin-wall depth matching on both versions.
- Add Respect block joins to connected Duplifier selection. Explain Connected Matching Blocks, Respect block joins and Ignore directionality with hover tooltips. Retain the original matching-region behavior when disabled, and preserve saved option indices and existing message sizes.

### Coupler ownership recovery (Forge 1.12.2)

- Retain the Amber and Cyan Coupler face artwork through Arm rotation and native endpoint handoffs. Track full rigid orientation while preserving registry metadata and old saved payload mappings. Slab Couplers keep their horizontal housing; unsupported tilted endpoints stay captured.

- Preserve release behavior for older saved coupled vehicles, including clearing authorized stale manual requests on the connections they keep.

- Support Couplers on moving Arm payloads, including linked active/passive Arms, rigid head orientation and articulated connections. Keep arm phases and payload/body rendering coherent during lag, reversal and reload. Preserve authenticated post-release travel and prevent empty space inside a detached vehicle’s bounds from causing a large arm push. See the [Coupler guide](docs/couplers.md).

- Preserve source-scoped Coupler receipts when attaching, detaching or splitting native arm endpoints. Validate the exact physical preimage independently of refreshed association headers, retain opaque capability data, and recover interrupted transfers through the durable journal.

- Restore the original source, cargo, occupied seats, root position and ownership metadata if a same-vehicle structural publication fails. A failed rollback leaves an inert vehicle retaining its complete original snapshot.

### Native furniture, controls and surfaces (NeoForge 1.21.1)

- Preserve inherited item components, including named container contents, when picking or dropping Seats, Pressure Plates and Surfaces. Keep malformed Chair and Plate item settings inert and exact through saving, reloading and recovery items.
- Add the canonical Track with Compact, Armoured and Bogie styles, regular/Large six-face arm formation, bounded shared geometry and settings-only Duplifier copying/Undo. Retain hidden historical Track IDs. Add Small, Medium and Large stationary slab-side wheels with native footprint ownership and one configured drop. See [Native Tracks and side wheels](docs/native-tracks-and-wheels.md).
- Cache exact arm collision shapes and answer lighting opacity without rebuilding geometry. Keep moving pose changes and world unload invalidation bounded.

- Add stationary Programmable Chairs, Luxury Seats, Military Seats and Pilot Seats with shared authored settings, connected benches, native sitting/dismounting, configured two-cell item ownership and dedicated previous-value dialogs. Preserve saved Pilot data while keeping Vehicle/Taxi behavior and moving-seat capture unavailable. See [Native seating](docs/native-seating.md).

- Add the canonical Industrial Table with authored connected end struts, ordinary and inverted placement, tabletop support, padded items and native saved block states. Share exact collision geometry with Forge 1.12.2 without adding a table entity or ticker.
- Add the Programmable Pressure Plate with shared Wood/Gold/Iron sensing, analogue physical power and independent Pressed/Released/Disabled channel output. Reuse the standard artwork picker and inline transmission editor through the Configurizer and ordinary sneak-right-click.
- Preserve native plate choices, settings-only transmission copying/Undo and inert unsupported saved data. Native Vehicle Mode and vehicle-carried plate sensing remain outside this standalone conversion. See [Industrial Tables](docs/industrial-tables.md) and [Pressure Plates](docs/programmable-pressure-plate.md).

- Add native Programmable Surfaces using shared layout and artwork policies. Joined drawings keep opaque surviving faces through mining and repair; settings changes rebuild actual terrain meshes, including cropped and non-square artwork. See [Programmable Surfaces](docs/programmable-surfaces.md). Native Turntable and Copy Controllers remain separate conversions.

### Item presentation

- Use smaller prepared Canopy item meshes in inventory, both hands, item frames and dropped items on both versions. Preserve authored materials and recognizable silhouettes while bounding item geometry and retiring cached variants on reload.

### Arms and landing gear (Forge 1.12.2)

- Extend existing stopped, stowed Plate, Ski and regular Wheel assemblies by sneak-clicking their visible sides with a matching item. Preserve the stopped pose, mounting frame and one-item placement through rebuilding.
- Add a separate Foot direction setting to formed Ski and Wheel assemblies, including sideways mounts and Large Wheels. Preserve the physical source footprint separately from the chosen visual orientation, configured items and copied settings.
- Attach native Plates, Skis and matching Wheels at grid-aligned activated Rotating Arm sockets, including complete Large Wheels and connected joined landing groups. Journal every ownership handoff, preserve opaque capability data and retain items when a fractional endpoint refuses placement.
- Use Single / Double hinge selection on Rotating Arms in both Forge 1.12.2 and NeoForge 1.21.1. Double shares the selected angle between the base and elbow. Choose Kink Inward / Outward relative to Fold and preserve that relationship when Fold changes; retain matching foot/payload motion, collision, configured items and copied settings. Older active middle-only strokes keep their saved motion until native zero-degree rest.
- Reuse bounded shared arm poses and allocation-free constraint projections to reduce repeated kinematic preparation.
- Allow Taxi mode with two independent complete Ski supports. Preserve single-Ski refusal, lowest-support selection and the existing rules for other landing supports.

### Core constructions on arms (Forge 1.12.2)

- Keep captured arm attachments visible when their mounting columns fall outside the camera's rendered terrain sections. Cull against cached posed model bounds, and avoid duplicate main-view draws or shader-shadow admission.
- Keep tilted captured Core machinery aligned with its arm socket. Apply endpoint grid rounding only when that Core orientation can become native blocks; retain normal collision checks and the original native-rest pose. Allow clearance below the whole folded machine: ground contact safely refuses movement while retaining the selectable payload.
- Keep one correctly placed Core renderer while native endpoint block, tile and arm updates arrive separately. Wait for complete native layout data and the settled visual pose; hand rendering to the current native machine without a blank frame or temporary factory corner.
- Preserve the shared client animation phase when Core machines move from native blocks to an arm attachment and back. Dish scanning no longer restarts at native endpoints; another machine placed at the vacated source keeps its own animation.
- Move a complete formed Core construction by touching its Core or any reserved part with an Extender or Rotating Arm. Preserve every reservation, chip, setting and opaque capability field through native endpoints, reversal and durable recovery.
- Include the complete validated Core footprint when joined Rotating Arms contact it. Match Join, range, fold, payload mode, hinge and kink across the columns; an independent neighbouring arm can still obstruct the machine.
- Keep unsupported tilted poses captured. Ordinary body clicks cycle a captured machine’s activity, and the Configurizer opens its existing settings dialog without toggling the supporting arm. Preserve settings and per-channel data through saving and native restoration. Chip insertion/removal, dismantling and partial mining require a supported native pose. See the [Core construction guide](docs/universal-constructors.md).

### Landing foot controls (Forge 1.12.2 and NeoForge 1.21.1)

- Share the arm dialog layout between versions. Show Start and End angle sliders for attached Plates, Skis, Wheels and Tracks; group Join range with Join arms and keep activation/channels in the standard Redstone tab.
- Preserve legacy physical 0°/90° landing strokes until an angle is explicitly edited. Validate the complete foot geometry and require at least one endpoint that can return to world blocks. Start changes move through the checked adjustment path, with copied settings and reload retaining the same angle policy.
- Add animated Inward and Outward Double-hinge examples to the [joined-arm construction guide](docs/joined-arm-platforms.md#double-hinge-examples).

- Use one shared availability rule for Foot orientation across landing families. Forge Tracks now expose the control alongside Skis, Wheels and Plates; native supported feet use the same rule. Keep direction changes restricted to the deployed endpoint.

### Standalone arms (NeoForge 1.21.1)

- Extend existing stowed Skis, Plates and regular Wheels by clicking their visible side with a matching item. Preserve the stopped pose and native owner, consume one item, and keep blocked placements unchanged. Use current posed faces for stopped foot attachment and dynamic collision shapes for mounted arms.
- Enable regular and Large Extenders, Rotating Arms, mounts, Plates, Skis and Wheels with six-face placement, joined platforms, Foot direction, Single / Double hinges, relative Kink and dedicated settings. Add all ten items to creative/search and support settings-only Duplifier copying and Undo.
- Preserve payload ownership through asynchronous native publication, active-foot adoption and captured-block mining; checkpoint terrain and dropped-item records before releasing ownership. Carry native players and restore a clear last-valid endpoint when a stroke is blocked.
- Add horizontal Extender **Push blocks** for independent and joined regular/Large platforms. Check the complete outward path before detachment, move contacted ordinary terrain through the native recovery journal, and leave pushed blocks in place on reversal.
- Keep the arm settings dialog open during motion and refresh endpoint controls without discarding edits.
- Keep Vehicle Mode unavailable. Tracks are available through the native Track family. Unsupported vanilla endpoint orientations remain captured and selectable. See the [native arm construction guide](docs/native-joined-arm-platforms.md).

### Battery and Hexagonal Fuel Tank (NeoForge 1.21.1)

- Add the canonical Battery Block and Hexagonal Fuel Tank with Source capacities, six-face placement, dedicated casing/shape/status dialogs and resource-preserving configured items. Keep tank fluid owned by each cell through joining, splitting, mining and reload.
- Connect native Power and Fluid Ports to these storage blocks, including optional input pulling. Reuse bounded tank membership and coalesce resource-only visual updates.

### Resource transfer ports (Forge 1.12.2 and NeoForge 1.21.1)

- Retain unavailable or malformed saved port resources in their original inert owner instead of loading an empty buffer. Reject mixed resource owners and duplicate inventory slots.

- Fix recursive chunk loading when reading older Forge Battery data. Resolve physical facing after chunk loading while preserving stored energy, capability data and the original face-policy format.

- Add Power, Fluid and Matter Ports with six configurable Input/Output/Disabled faces, optional input pulling, shared output limits, bounded persistent buffers and balanced outputs. Power uses RF-compatible Forge/NeoForge Energy; Matter accepts ordinary items.
- Add dedicated General/Housing/Redstone configuration, selectable disabled-face materials, clear red/yellow/blue inventory icons and consistently sharpened artwork. Label shared output limits as blank for unlimited. Support per-face activity artwork and settings-only Duplifier copying and Undo. Mining retains resources in one configured block item.
- Use standard capabilities without mandatory mod dependencies. Native player checks cover Mekanism energy, fluid and item networks, RFTools Powercells, and the native Battery and Tank.

### Stationary glass terrain and retained rendering (Forge 1.12.2)

- Ignore removed or replaced Glass owners during delayed frame publication, preventing the client crash that tried to read panel facing from Air. Keep warmed contour cache reads allocation-free.

- Render eligible stationary Programmable Glass in native translucent chunk buffers, with authored glimmer enabled by default and existing joined frame borders preserved.
- Use one compensated physical sheet with opposite windings for native backface culling. Keep authored RGB, animation metadata, resource-pack priority and normal/specular companions.
- Confirm actual installed pane records before retiring fallback drawing, and remove every settled pane-only tile from special-renderer admission. Handle partial/stale rebuilds, edits, resource reloads and vertex-format changes without steady-state owner scans.
- Use native terrain sorting, lighting and shader inputs. Stationary panes remain eligible with emissive frames; native cutout frames retain fullbright lighting, partially transparent emissive frames use independent retained groups, and empty joined interiors leave tile rendering.
- Retain grouped fallback VBOs for stationary flat panes, including faint palette artwork and static custom atlas inserts. Share each coplanar section/material group across main and shadow passes, preserve holes and per-cell light/UV data, and remove nonanchor pane-only tiles from renderer admission.
- Withdraw native pane snapshots through a completed replacement-build fence when an extension cannot expose installed vertex records, then hand ownership to retained groups. Keep explicit frame-only shadows on an optimized retained main-view path.
- Retain stationary Porthole housing and pane geometry in independent groups, covering flat, full-depth, diagonal, bent and T-bar apertures. Preserve authored material, opacity, glimmer, per-owner light and moving/fluid/animated fallbacks; retire buffers when their owner, format, resources or world changes.
- Add actual chunk-builder/publication, emissive-frame, retained-buffer and framebuffer regressions. `/vandorglassprofile` records frame intervals, optional asynchronous GPU timings and ownership/rejection counters without changing the camera or graphics settings. See [stationary glass rendering](docs/stationary-glass-rendering.md) for behavior, settings and validation limits.

### Rendering and client-server performance (Forge 1.12.2)

- Retain joined Programmable Light layouts across ticks and world switches, and cache their hidden-face masks. Invalidate affected layouts on nearby edits, settings, chunk changes and carried-world replacement; preserve exact artwork coordinates, face visibility and live lighting within bounded cache storage.
- Keep unchanged virtual-redstone source publications in their existing spatial and strength indexes. Repeated neighbor notifications no longer rescan receivers, while analog changes, movement, channel edits, source lifetime and transfer settlement retain their existing behavior.
- Deliver vehicle motion, carried Door/Canopy/gear and control updates once to each tracked rider. Retain the fallback for passengers whose tracking has not started, with unchanged packet formats and update frequency.
- Add focused layout, visibility, cache-lifetime, source-publication and packet-delivery regressions with before/after work and payload measurements; see the [performance notes](docs/performance/light-and-channel-caches.md).

### Appearance and mod compatibility (Forge 1.12.2)

- Show the selected primary texture in Waila/Hwyla (including Wawla setups), with Rotating or Sliding movement for Doors and Trapdoors. Resolve upper halves and Large Door parts to their settings owner. The HUD remains optional.
- Display copied Door face artwork and glazing, Trapdoor surfaces, and Porthole materials correctly in configured inventory items. Preserve the existing middle-click settings and use bounded caches invalidated on resource reload.
- Let Industrial Renewal catwalks recognize ordinary and Large Programmable Doors through Minecraft's native Door type, preserving Vandor placement, motion, security, metadata and multiblock ownership. Segmented Doors remain a separate mechanism.
- Add amber-gold Gold glass to the shared glass palette, its four tint levels, configured items, and Dynmap. Append the new shade without changing existing saved glass IDs.

## 2.9.1

### Lighting and prepared models

- Enable ambient occlusion for Programmable Block, Slab and Storage. Restore vanilla face-corner ordering after rotation, including floor and ceiling faces, while preserving texture coordinates and complete extended shader vertex records.
- Reuse prepared housing geometry, common immutable block-state snapshots and selector housing meshes instead of rebuilding equivalent faces and property maps. Preserve individual materials, joined layouts, visibility and live lighting.
- Cache Programmable Stair retexturing and collision geometry, reducing repeated face preparation and collision-query allocation while preserving facing, half, shape and selected artwork.
- Reuse completed configured-material, texture-layout and mounted-control item variants within entry and retained-memory limits. Preserve custom-model fallbacks, full shader records, item padding and resource/format invalidation.

### Glass and Portholes

- Batch static selected glass materials and static Screen artwork with ordinary transparent panes, retaining per-pane tint, opacity, depth and joined Stretch/Tile/Fit mapping. Preserve shader material boundaries, transparent ordering and animated-display fallbacks.
- Publish stationary custom Programmable Glass frames and regular junction frames into chunk geometry. Retain each face's sprite, alpha and lighting; keep tile rendering until replacement geometry is uploaded and for moving vehicle worlds.
- Let vanilla schedule pending frame-section rebuilds and poll upload completion without blocking the client thread. Coalesce dirty regions and avoid repeated frame publication and unnecessary neighboring-section rebuilds.
- Reuse prepared pane geometry, shader material data and render-pass decisions. Bound mapped-pane geometry by retained bytes so dense sets of small panes do not thrash a fixed entry limit.
- Cache glass topology, joined artwork layouts and Porthole groups with dependency-aware invalidation. Nearby edits, bridge placements, chunk changes and appearance updates refresh affected groups; unrelated distant edits retain reusable data.
- Refresh glass configuration, actual state, fluid rendering and emitted light through consistent update paths. Avoid repeated render/topology invalidation for identical packets while preserving first-load, copy, world/position, carried-fluid and pending-light refreshes.
- Skip empty joined frames and screen borders before lighting and graphics-state setup. Reduce glazing-sort allocation and choose the lower-cost overlap sweep while retaining cyclic overlap ordering.

### Doors and carried controls

- Reuse ordinary Door control-panel meshes, render bounds and pass decisions. Cache Segmented Door appearance contexts and chunk publications instead of repeating preparation for unchanged columns and render passes.
- Cache Large Door collision partitions and spatial indexes for collision and moved-leaf ray queries. Reject follower tiles before expensive root lookups, retain overhanging leaves, and avoid phantom collision boxes from disjoint clipping.
- Correct X-split Door batch travel while preserving leaf placement and animation.
- Use the physical parent world's authority for carried Door credential slots and signal updates. Validate the live installed owner before inventory changes so remote VehicleWorld views retain server-authoritative access and credential handling.

### Machines, particles and vehicle uploads

- Cache exact Hex Tank fluid outlines and static tank/Battery geometry while retaining current artwork, fluid color, lighting and fractional Battery charge.
- Combine adjacent machine-display slots using the same texture into one submission, retaining slot order, animated UVs and live appearance.
- Apply camera distance, particle settings and per-tick admission limits before constructing decorative thruster particles. Clear admission state across world/tick changes and preserve authoritative thruster behavior.
- Send Storage Status fields only when changed after each listener's initial snapshot. Preserve the existing split-integer protocol, signed-half reconstruction and independent listener lifecycle.
- Spread vehicle mesh finalization and opaque GPU uploads across frames using bounded batches. Preserve complete quads, extended vertex attributes and translucent ordering; keep the previous mesh visible until its replacement is ready and discard unfinished uploads on invalidation.
- Bound prepared caches by entries or retained memory as appropriate, and retire stale geometry on resource reload, format changes and world unload. Add focused regression fixtures for the changed rendering, collision, security, cache, packet and upload paths.

## 2.9

### New features and artwork

- Add Creative Configurizer Safe Park for existing articulated vehicle layouts, using a nearby loaded flat area and the durable parking transfer. Preserve original Hover mode and target height during recovery. Resolve supported fractional arm endpoints before publishing native blocks.
- Select artwork for individual Segmented Door cells; use the Copifier for sharing settings across sections. Preserve saved shared artwork, local overrides and configured picks.
- Add three supplied Segmented Door panel textures and Picket, Industrial and Lattice gate models in both versions. Preserve authored bars, compatible horizontal/vertical joins, translucent glazing and latent window settings; use exact gap-aware interactions and bounded prepared rendering. Group these choices and the renamed Dark/Light panels under Doors / Segmented while preserving saved IDs and existing general-material uses.
- Add twelve native-resolution Utility Door artwork choices for ordinary and Large Doors in both versions. Group Dark, Slim and White Glass designs under Doors / Glass; preserve existing saved choices and geometry indices. Update the shared browser, collages and Dynmap palette.
- Keep Segmented Door Tile artwork continuous across solid and Window segments in both versions. Make segment bodies two pixels thick while retaining four-pixel top borders, matching selection, collision, fluids and settled terrain rendering.
- Place flat Programmable Walls, Glass and Porthole Walls against the clicked support when using a horizontal normal face, rather than on the far edge of the next cell. Preserve top/bottom and tangent-plane continuation.


### Fixes and usability

- Reduce Forge 1.12.2 glazing-order allocations for acyclic scenes and choose the lower-cost overlap sweep for tall or wide walls while preserving cyclic overlap ordering. Skip empty joined glass frames and screen borders before lighting and graphics-state setup, including shader shadow passes.

- Fix an OptiFine shader crash when a solid Segmented Door has no translucent geometry to submit, including the shadow pass.
- Keep Segmented Door panels solid while moving and fully open, including beyond their original cells. Follow the selected bending treads, reuse bounded collision snapshots and remove them when the owner unloads or is removed.

- Keep Reactor artwork selected when choosing glazing in its Glass tab, for ordinary and Large Doors in both targets.
- Preserve the full Spiral Aluminum Glazing artwork and Tile/Stretch/Fit mapping across its slit geometry, inner borders and independently selected glass.
- Stop perpendicular Segmented Doors when the final tread completes the bend, removing extra open-track travel while retaining tread-dependent bend height.

- Preserve track-specific tile data when preparing carried vehicle collision snapshots.

- Avoid an old Java 8u51 C2 compiler failure in the ramp face renderer while preserving its vertex output.

- Remove the duplicate Dark Segmented Panel picker row and redundant artwork scope/reset buttons; preserve saved IDs and use the Copifier for sharing.
- Add Ramp-style Tread Px controls for 1/2/4/8/16-pixel Segmented Door bending strips, retaining sixteen-pixel motion for older saves.
- Give custom gates usable top placement faces and select Segmented Door depth from the clicked support or neighbouring door plane.
- Render Spiral Aluminum Glazing with opaque metal, inner reveals and selectable glass; give Reactor Door artwork a real octagonal pane and inner border, including sealed Large Door X-split cuts.
- Rename Utility Modern variants consistently, including Warehouse Modern, without changing saved choices.

- Keep centered vertical articulated couplers facing consistently while the parent turns in Forge 1.12.2. Preserve existing saved joint poses and packet fields.
- Keep Taxi/Hover double-jump taps within the current driving context. Opening a dialog, changing vehicle/player/world or losing the driver role clears a pending tap; single presses and held jump keep their existing behavior.
- Let Forge Copy Controller pads contain connected Surface blocks with different selected artwork. Keep each cell’s appearance and preserve artwork-sensitive Turntable discovery.
- Align redstone channel validation with the existing positive 32-bit range. Copy Controllers accept wide channel increments and check translated IDs before copying; Coupler and Segmented Door fields retain complete 64-channel banks, and saved cockpit/coupling channels no longer truncate valid high IDs.
- Fix connected Duplifier Undo for linked settings such as Segmented Doors: record all selected blocks before the first edit changes shared settings, so Undo restores their original configuration.
- Validate stored Canopy Glass footprints before placement so rejected configured stacks cannot replace foreign blocks or supports.
- Fix screen pulses remaining on after their control artwork is hidden and restored. Restored pulses keep their original expiry time.
- Fix texture quicksearch matching the shared mod namespace. Ordinary terms search names, categories and texture filenames; explicitly qualified texture IDs remain searchable.
- Align the Forge runtime version with the 2.9 artifact metadata, and reject mismatched version declarations during the root build.

### Rendering and performance

- Remove warmed allocation when rendering mixed Programmable Light housings, retaining exact vertices and a bounded cache.
- Share moving Segmented Door render descriptors across equivalent columns and account for their retained cache memory.
- Reuse native diagonal Glass/Porthole render projections across equivalent owners, removing repeated snapshot-cache allocation while preserving material, clipping and layout changes.
- Share identical Forge diagonal Glass/Porthole meshes after contact trimming and remove allocation from warmed flat Glass frame lookups, with retained-byte limits and atlas retirement.
- Share prepared ordinary Glass geometry between equivalent native tile publications, with allocation-free warmed lookup, bounded retained data and exact rebuilds after eviction or reload.
- Remove repeated allocation from Forge Glass/Porthole UV-cache hits, bound retained source/mapped geometry by bytes and clear old atlas entries on resource reload and world unload.
- Prepare native Light facing and atlas coordinates once per cached mesh, reduce retained vertex storage and reuse the common prepared-vertex emitter while preserving live lighting and reload behavior.
- Share Programmable Light save, signal policy and cached housing meshes between versions while preserving Forge defaults, lazy input sampling, artwork and existing lighting.
- Reuse physical fluid volumes when only joined panel artwork coordinates change. Preserve center-owned T-junction materials, stem caps and porthole pane deformation through shared rendering kernels.
- Reuse current stair collision, exposed surfaces and face mapping across targets, and share slab placement/merge decisions. Preserve exact emitted geometry and configured slab behavior while reducing temporary allocations in dense stair collision construction.
- Bound native mounted-control transformed meshes once per resource generation, sharing a 16 MiB cache across world states and configured items. Preserve source-model identity, dynamic-model bypass and immutable old reload snapshots.
- Share the current supporting-face control transform across targets, retaining exact mounted geometry and item transforms while reducing bounds, wedge and construction allocations.

### Experimental NeoForge 1.21.1 and shared refactors

Forge 1.12.2 remains the release reference. Native Vehicle Mode and the remaining standalone mechanism families are incomplete; these entries do not claim full 1.21.1 parity.

- Share the mounted-control height, tilt and direction save contract, preserving old preset migration, malformed-field defaults and caller-specific fallback without changing geometry or live latch state.
- Share Trigger and housing save contracts while preserving legacy missing-field defaults, material migrations, six face overrides and dormant Trigger playback data. Add a focused Trigger dialog regression without running unrelated gameplay fixtures.
- Share saved material-ID validation while retaining unavailable custom and filesystem artwork identities.
- Share existing layout, porthole geometry, category and glass/light material definitions between the independent Java 8 and Java 21 builds without changing their legacy behavior.
- Share built-in transmission ranges, typed saved defaults and mounted-control settings between targets. Keep Forge migration, geometry, configured-item fields and hot-path allocation behavior unchanged.

- Enable all eleven standalone Canopy and cockpit-glass components in the experimental native build with original recipes, configured placement, dedicated editing, joined shells, pause-aware motion, source-matched terrain/transparent artwork and safe loaded membership repair. Add staged native construction images. Vehicle/carried integration remains deferred.
- Enable the Duplifier in the experimental native build with its original recipe, dedicated Apply Settings window, connected application, settings-only Undo and three-state item artwork. Copied-settings crafting supports converted building, Door, Trapdoor, Light and Screen families; Ramp integration is still in progress.
- Enable the canonical Card / Chip Programmer in the experimental native build with four-cell placement, dedicated Inventory/Journal editing, Program/Copy, physical sockets, padded authored artwork and saved ownership recovery. Add a native construction guide with staged player images. Vehicle Chip operations remain deferred.
- Enable Programmable Storage in the experimental native build with its original recipe, independent 27-slot inventory, dedicated material editor, configured items and source-matched Cabinet artwork. Mining keeps contents separate from the configured block item.
- Enable ordinary Programmable Glass in the experimental native build with original recipes, configured variants, dedicated editing, two-sided fluids and source-matched world/item artwork. Standalone Canopy components are now separately enabled; Vehicle integration remains deferred.
- Enable standalone Card and Data Chip readers in the experimental native build with source-matched mounts, dedicated editing, inventory/hopper behavior and credential recovery.
- Prevent native Programmable Screens, metal switches, numeric controls, Lights and propulsion blocks from being replaced by buckets or flowing water/lava. Preserve their settings and owned items while retaining the intentional fluid behavior of power levers.
- Share Copifier option-mask migration across platform adapters while preserving existing saved choices, option indices, reverse cycling and apply/undo behavior in Forge.
- Enable standalone propulsion and redstone-light families in the experimental native build: original block identities, seven catalog items and recipes, connected authored shapes, dedicated controls, particles and configured Survival placement/harvest. Vehicle effects remain deferred.
- Correct native position-dependent light-source discovery and client light withdrawal while preserving failed-reader snapshot recovery.
- Enable Programmable Light, Light Frame and Light Slab in the experimental native build with source-matched grouping, configured items and dedicated ON/OFF/Sides/Redstone editing.
- Retain failed native platform snapshots through component/attachment decode failures and Trapdoor mining. Opaque recovery items preserve their original data and refuse ordinary placement.
- Enable regular and diagonal Trapdoors in the experimental native build with dedicated editing, configured items, source-matched assemblies, moved-leaf interactions and contained-fluid rendering.
- Preserve clicked-face coordinates when placing native walls, portholes and ordinary/Large Doors, keeping depth, anchoring and hinge choice consistent with Forge.
- Bake settled native Segmented Door bodies into chunk meshes after confirmed uploads, retaining visible animated fallbacks during rebuilds and removal.
- Share Programmable Screen activation and legacy artwork migration while preserving existing Forge dialogs, saved control rows and joined power behavior. Enable the seven original standalone Screen roles in the experimental native build with dedicated editing, authored configured items, joined media/rows and non-Vehicle Chip displays.
- Preserve native Segmented Door legacy water through opposite-side edits and complete drainage, preventing lost or recreated water after reload.
- Enable the native ordinary and Large programmable Doors with shared authored geometry, reversible motion, full company artwork, padded configured icons and the dedicated Door/Frame/Redstone/Security/Glass editor. Preserve paired controls, channel banks and credential ownership through restart and mining. Large Doors preserve nine-part placement, old-schema follower defaults, per-part water outlet ownership and credential recovery when mining any part.
- Share Segmented Door panel, window and border kernels, typed trapdoor defaults and bounded Segmented Door settings transport. Share regular/diagonal trapdoor dialog choices, wording and reverse cycling across versions. Enable native Segmented Doors with per-section editing, moved-panel use, configured picking and stationary fluid ownership.
- Keep the native diagonal-glass recipe conditional on its original programmable-glass ingredient becoming available.
- Extend the experimental 1.21.1 build with programmable plain walls, thin porthole walls and diagonal walls. Reuse the housing editor, configured items, frame materials, contained fluids and bounded panel renderer; preserve diagonal sizes, slopes, fills and joins. Portholes support the glass palette and per-block Off/static/animated artwork. Other building variants and full native acceptance remain in progress.
- Fix native mounted-control hover stalls and excessive selection lines. Buttons, switches, power levers and throttles now use the same single enclosing selection box as Forge; detailed collision shapes remain separate and are not built by hovering.
- Share current slab/stair artwork selection, UV calculations and immediate dialog controls while preserving existing rendered output and saved-value behavior. Add lossless native contained-fluid storage as a prerequisite for building shapes.
- Share exact stair tread/roll save and configured-item rules, including legacy 16-pixel treads and saved roll 2. Preserve invalid-edit refusal and unchanged Forge geometry, lighting and padded icons. Native slab/stair adapters are registered; broader native acceptance remains in progress.
- Add native directional wireless transmitter and receiver blocks with analog/digital modes, separate On/Off levels and six-face ports. Reuse the channel host and inline transmission controls while preserving receiver maximum aggregation, plain dropped items and loaded-world lifecycle checks.
- Share the current glass editor state, four tabs, option rows and reverse cycling. Preserve immediate preview, frame-face and playback panes, hidden legacy control IDs and live framing callbacks.
- Add native Thruster Levers, Airliner and Fighter Throttles, and Wall Sliders through the shared mounted-control host, dialog and renderer. Preserve four detents, saved limits, selectable throttle types, partial channel banks and canonical configured items. Share output math, save defaults and dialog rows with Forge.
- Share the current programmable-glass save contract while retaining frame appearance, playback, fluids, legacy size migration and typed saved defaults. Verify unchanged Forge save output and focused live glass appearance; native ordinary Glass is enabled with its dedicated editor.
- Share wireless transmitter/receiver scalar save defaults and analog/digital level rules across targets. Preserve the existing Forge six-face sampling, strongest-input receiver behavior and separate On/Off settings; native endpoint blocks now use this policy.
- Add native Industrial and Small Power Levers, retaining Compact and Large saved variants and canonical configured-item drops. Reuse the mounted-control dialog, latch host, activation path and renderer; share size defaults and the Size option with Forge. Preserve authored geometry and bounded shape caching.
- Add native Push Buttons and Rocker Switches with authored mounted geometry, configured items, ranged inline transmission and shared latch banks. Preserve twenty-tick button pulses and publish physical redstone after linked banks settle. Reuse mount rows and one native inline transmission editor, with consistent reverse cycling and separate Done/Escape mount drafts.
- Reuse mounted-control option labels and row layouts across targets, preserving existing Forge dimensions, option IDs and forward/reverse cycling.
- Share mounted-control per-channel persistence and output rules without changing Forge latch transitions. Add the native ranged-output and latch host for subsequent control families, preserving partial banks, independent pulses and unloaded-world cleanup.
- Preserve native transmitter-copy crafting: place the source in the center of a 3×3 grid and one target beside it. Copy channels while retaining the target tier and other components, and return the original source. Preserve this rule through native grid cropping and automated-crafter recipe caching.
- Add the native Programmable Trigger with shared saved state and dialog rules, static Off/On or signal-band artwork, exact-level matching and Front Only/Sides options. Reuse one native material picker for cube and Trigger dialogs, including independent artwork searches, inventory sampling and scrolling.
- Add native handheld transmitter tiers with canonical recipes, stationary per-dimension sources and authenticated channel editing. Preserve exact installed receiver identity through deferred registration and chunk unload/reload. Other control and wireless families remain incomplete.
- Support installed filesystem PNGs on the native programmable cube, using shared startup discovery and stable saved identifiers. Keep picker selections through missing-file and dimension reloads, refresh world/item UV snapshots coherently, and preserve separate startup-missing-client fallback. New file discovery still requires a restart; the PNG workflow remains separate from inventory block sampling.
- Share the exact custom block material saved-ID calculation, retaining legacy caller metadata normalization and literal previous-save identity fixtures. Add an actual vanilla 1.12.2 block-registry manifest and strict native data-fixer aliases, preserving requested saved IDs while resolving the actual normalized legacy states.
- Share programmable dialog panel dimensions and material-picker scrolling across block families and both targets. Cube Appearance and Trigger tabs, labels and edit transitions use common control descriptors while retaining their existing layouts, reverse cycling and immediate submissions. Share housing face-plane and packed-light calculations while preserving legacy discovery and light sampling.
- Add the experimental NeoForge plain matter block and matter/alloy ingots with canonical recipes, artwork and native persistence/interaction checks. Add the native programmable cube editor with built-in material search, six face overrides, all three layouts, bounded joined images and configuration-preserving items. Creative shift-click and the main-hand Configurizer share its authenticated menu. Other programmable families and the production chain remain incomplete.
- Add native cube inventory sampling with the shared non-consuming grid, default-state block artwork and both door preview halves. New samples use a fixed `flattened-block:` identity preimage; old vanilla aliases keep their saved appearance. Preserve configured item and face settings through reopen, restart, unavailable artwork and resource reloads. Optional component-renderer adapters remain deferred.
- Begin incremental integration of the historical NeoForge 1.21.1 target while preserving Forge 1.12.2 as the default and behavioral reference. Native feature parity and release readiness remain pending.
- Track current-master families and historical native implementations separately. Prioritize non-vehicle parity, including standalone mechanisms, Copy Controllers and shared turntable infrastructure; defer vehicle-specific integration.

## 2.8

- Preserve original articulated joint poses when vehicle loading fails after joint decoding, keeping unavailable vehicles recoverable on the next save. Reuse per-vehicle collision-query arrays instead of allocating them for every candidate pose.

- Detach one clicked coupling pair at a time, choose the detached controller’s own driver seat, and allow a bounded clear settling motion for small suspension tilts. Both separated vehicles retain Taxi/Hover mode at non-cardinal headings and fractional positions.

- Correct the base door artwork orientation for Vektor, Pinnacle, Paladyne and Ashiro-Mai, retaining Seyr/Vandor orientation and paired-leaf mirroring.

- Allow stopped Taxi/Hover vehicles to separate into two complete vehicle entities, preserving their poses, mode, tile data and inventories through durable ownership recovery. Each half needs its own controller, seat and mode equipment; detached seats must be empty and any small settling motion clear.
- Let Hover vehicles lift with their current articulated pose when intermediate leveling would catch the ground, then settle horizontal after ascent and descent. Allow stopped Hover vehicles to park directly near the ground with extended gear, preserving the leveled pose through ordinary parking checks. Preserve terrain selection and Survival mining through empty space inside a bent vehicle's enclosing bounds.
- Preserve opaque carried tile capabilities during state updates and pending latch handoffs.
- Mirror all company door artworks across paired normal and Large leaves in Fit, Tile and Auto layouts.

- Make the new-assembly articulation limit configurable with `vehicles.maxArticulationPoints` in `vandorlabs-blocks.cfg`. Default 7 preserves eight independent segments; 0 allows rigid assemblies only. Saved vehicle loading, recovery and parking remain available after lowering the limit.

- Put Segmented Door texture lists on the left; use Pos consistently in dialogs. Copy every segment setting through Duplifier and middle-click placement. Fit windows only across connected Window segments of the same Shape, tile the physical panel edges without stretching and separate square openings from their border rails. Synchronize linked columns on one pause-aware motion clock.
- Render nonsquare Surface pads as separate blocks in Fit (Aspect), retaining their Copy Controller ownership. Add faint native glass shimmer behind all six company Light door artworks.
- Index articulated collision shapes to reduce repeated narrow-phase work and bound steering work per step, retaining the last safe pose if the limit is reached. Carried coupler clicks authorize the current player in Taxi and Hover before requesting safe world-block release.
- Segmented Door now uses `vandorlabs:segmented_door`. Interim 2.8 development door names and fields are intentionally unsupported; Segmented Door did not exist in 2.7.

- Combine Segmented Door motion and artwork controls on Door; use compact buttons, Slow/Medium/Fast ordering and no dialog Open/Close buttons. Add Middle/Near/Far placement and reversible contained-water flow. Keep openings and glass local to the configured block while artwork and motion remain shared. Preserve transparent Frames at rest, move the border with Slide Direction and keep visible moved segments clickable. Straight travel retains a one-pixel edge.
- Rebuild rectangular Surface artwork after Better Builder’s Wands extrusion without copying stale renderer roots or controller claims. Identify invalid Copy Controller pads by side and position, expose full errors on hover and accept output-only pads in Vehicle Chip mode.
- Select a vehicle's controller from its piloted coupler segment; preserve controllers in other sections as ordinary blocks. Allow safe small trailer alignment errors when parking, with bounded cumulative drift, retaining the original joint pose in parking recovery journals. Eliminate false collision stops caused by movement subdivision rounding, reduce articulated collision allocations and limit new assemblies to eight independently articulated segments. Existing longer saved vehicles retain recovery and parking support. Correct lone Track steering direction.
- Keep filled Ramp Controller texture repeats within their source atlas sprite, including ordinary iron blocks on Y-axis layouts.

- Preserve opaque capability material references during retired-glass snapshot migration. Refuse missing saved material names instead of resolving them to air. Refuse unloaded scan sources before tile reads and avoid leaking pause leases when preparation fails. Extend release checks to the new Segmented Door, articulation, ramp-axis, chip-display and dense-render paths.

- Extend Segmented Door with four slide directions, Straight/Perpendicular motion, shared Speed and Activation selectors, fixed dialog dimensions, Tile/Stretch/Fit artwork, Custom block samples, Frame textures and joined or tiled porthole windows with configurable glass. Repair all headers after Better Builder’s Wands extrusion. Rename Ramp Controller’s Base speed label to Speed.

- Add optional Coupler articulation with independent trailer yaw and pitch, a combined 75-degree limit, hull/terrain collision checks, saved joint poses and section-aware rendering and interaction. Parallel coupling pairs remain rigid.
- Require three lift blocks in each connected coupler section before entering Hover, including rigid or parallel coupler connections. Taxi remains available without section lift.

- Add mounting-dependent Ramp Controller Axis choices, including upright Y source panels for side mounts, saved settings, generated geometry, collision, recovery and vehicle rotation. Existing horizontal layouts retain their behavior.

- Keep Canopy Glass placed by an automatic RFTools Builder: initialize pending tile ownership during placement before canopy membership repair.

- Add a saved 45-degree Angle slider to Programmable Chairs, including rotated models, configured items, seat mounts and Duplifier support.
- Swivel isolated top-mounted Tracks with vehicle steering while keeping their top supports fixed; retain normal joined/arm-mounted Track behavior.
- Apply Console and Half Console frame finishes only to Displays, leaving Controls artwork and controls unchanged.
- Standardize display tabs and categories on Displays across screens, consoles, cockpits and multiblocks.

- Add eighteen company door textures under Doors, with Standard, Security and Light designs in each company submenu.

- Allow Taxi without propulsion at a 0.23-block/tick cap, with no Sprint boost. Powered Taxi remains 0.28; Hover still requires propulsion and lift. Apply the same rule to controller/seat entry, waypoint departure and coupling.

- Mirror Custom block artwork across paired and Large Programmable Door leaves; preserve single-door orientation.
- Insert an accepted Access Card from inventory when right-clicking an empty secured door reader, using its existing security behavior.
- Match Programmable Glass and normal/Large Door rendering distance to Walls and Portholes.

- Keep differently textured Programmable Surface pads separate during discovery, so touching pads can each join and retain their own artwork.

- Colour Vehicle Chip model previews by company: Pinnacle gold, Ashiro coral, Paladyne green, Vektor violet, Seyr orange and Vandor cyan; generic chips use silver.

- Point the Console, Half Console and Diagonal Half Screen chip openings toward the player at the bottom. Keep the full Diagonal Screen reader sideways.

- Rename Programmable Diagonal Half Console to Programmable Diagonal Half Screen, including the block/item ID. Existing placements, inventory stacks and saved designs retain their data through migration.

- Prevent client crashes when removing joined Chip screens: stale tile render-bound queries no longer enter group discovery after removal or replacement.

- Fix Vehicle Chip preflight for unchanged extended ramps and migrated display frames, while retaining block, inventory and tile-setting change checks.
- Configure vehicle card filtering through the attached Access Card Mount. Vehicle readers always retain the card; the controller keeps the security switch and setup notes.
- Add the built-in chip reader to Programmable Consoles. Joined Screens share an attached reader and stretch one Chip view across the complete rectangle.
- Apply screen animation speeds to Vehicle Chip rotation, cache immutable preview geometry, and show company Corp logos for Master Data Chips.
- Increase Programmable Surface limits to 32 blocks per side. Screen joining retains its previous limit. Copy Controllers accept matching rectangles; Turntables keep square pads. Existing payload, height and snapshot limits still apply.
- Use Segmented Door throughout code, assets and registry identifiers, without old-name loading support. Add the common material/Trapdoor/Door/Large Door picker, and expose original and light grey segmented panels under Industrial.

- Retire Clear, Pale Cyan and Smoked Cockpit Glass and their recipes. Migrate placed blocks, items and saved designs to vanilla glass, cyan stained glass and gray stained glass.

See the [2.8 guide](docs/versions/version-2.8.md) and [Segmented Door construction guide](docs/segmented-doors.md).

- Slice vehicle discovery and preflight across ticks, reuse rotation checks for repeated block states and show conversion activity on Vehicle Status screens.
- Add saved vehicle names and rotating shape previews, exact Vehicle Chips, Programmer creation/duplication, output-only Copy Controller chip sources and retained Access Card security.
- Center copied block designs across output Surfaces, reject oversized chip designs before material handling, and explain chip copying when the Copy Controller has no mount.
- Add Display-only Chip artwork and sideways built-in readers to Diagonal Half Screens, Diagonal Screens and Half Consoles. Regular Screens read attached Data Chip Mounts; readers retain their chips through artwork changes, saving and vehicle transfers.
- Correct the Diagonal Half Screen dialog title and put the Half Console Display tab before Controls. Crop the lower controls texture without stretching it.
- Add shared base height, tilt and tilt-direction controls to Data Chip and Access Card Mounts, including posed contents and configured item preservation.
- Explain missing Data Chip and Access Card Mounts on the Vehicle Controller's Vehicle and Security tabs.
- Reuse terrain scratch collections and immutable transfer preflight; let a single scan consume unused shared work slices. Preserve fresh-world terrain, live authorization/hardness checks and durable recovery.
- Record informational vehicle weight estimates from material/shape heuristics and retained cargo/fluid contents; persist the estimate and rule version in controllers and Vehicle Chips without changing movement.
- Add linked overhead Segmented Doors with Redstone Channels, a shared Configurizer/right-click dialog, bounded topology queries and original panel artwork.
- Bake settled closed segmented panels into terrain; batch moving panels and cache reusable pose geometry. Preserve server collision and saved motion independently of cosmetic animation and chunk upload timing.
- Batch unclaimed single Surface blocks, ordinary planar glazing and opaque diagonal wall fallback meshes. Tighten Surface visibility bounds.
- Cache stationary opaque ramp endpoint VBOs, with state, lighting, vertex-format and resource invalidation.
- Index loaded canopy, moved-door and offset-trapdoor collision owners; reject fixed-glass misses before neighbour geometry queries and avoid unrelated entity scans when no turntables exist.
- Batch carried opaque wall sections in shader-safe VBOs with immutable formats, preserved block material IDs and byte-limited retention. Refresh lightmap fields without rebuilding geometry; rebuild affected sections after carried artwork updates or neighbouring block edits.
- Tune collision regions for large flat craft, use primitive spatial-index keys and avoid allocating nearby-craft candidates for self-collision. Skip unused fixed-glass client mechanism ticks.
- Compact contained-fluid cells along the most subdivided axis; defer voxel faces until requested and avoid unused panel/door volumes during rendering.
- Scope Complementary rain wind beneath fixed Canopy Glass roofs without changing world lighting.
- Add focused density, weather, native segmented interaction and many-door renderer comparisons. Pass the Java 8 release gate, previous-release world upgrades and restart recovery checks; hardware shader acceptance remains separate.

## 2.7

See the [illustrated 2.7 showcase](docs/versions/version-2.7.md), [display matrix](docs/display-matrix.html) and [control matrix](docs/control-matrix.html).

### Added

- Classic, Cool and Gold Full/Half display libraries, including matching 17-subject expansion sets and six-frame animations. Add Door Controller in all three styles, animated Classic Half Station/Targeting, and dedicated Classic Half Medical, Ship Status, Engineering Charts, Starmap and Weapons compositions.
- Large animated Viewscreen choices in Full/Half layouts: Classic at 256px wide and Cool/Gold at 512px wide. Add 512px Large City Transit Map designs for every style, retaining native aspect ratios and standard 128px choices.
- Independent front borders and frame interiors, company borders, faintly lit finishes, Bare selection, separate frame/display layouts and split Top/Bottom artwork on full-height displays. Joined frames extend authored edge pieces at constant border thickness.
- Disabled, Digital and four-band Analog artwork activation across programmable screens, Flatscreens, diagonal screens, inputs and consoles. Each state has independent artwork, playback and speed; Console Controls remain shared.
- Static Off/On or analog-band artwork on Programmable Trigger, including selectable Displays, All Faces / Front Only mapping and a separate Sides texture. Preserve legacy playback data without animating Trigger artwork.
- Displays in programmable Glass and Porthole glazing pickers. Show Off/Static/Animated and Slow/Normal/Fast directly on Glass when Display artwork is selected, including diagonal variants.
- Shared Small/Full sizing for Programmable Light, Half-Input and Flatscreen. Small Flatscreens match the half-block square Light size and retain individual frames across gaps.
- Built-in transmitter Range sliders for 4, 8, 16, 32, 64, 128 and 256 blocks, plus independent Local Redstone On/Off. Handheld transmitters remain available with their original manual signals and item ranges.
- Mild, Normal, Strong and Opaque glass finishes. Strong is translucent; Opaque explicitly fills the pane. Regular and Large White Glass, Dark Glass and Slim Glass doors offer a Glass material picker with Original reset.
- LabPBR specular companions for every runtime texture, with material-specific finishes and shared maps for equivalent artwork. Preserve diffuse colors, transparency, existing emission and resource-pack priority.
- Satin Silver, Cool Steel and Pale Titanium materials at native 128px resolution. Refresh six corporate armor designs and add Surface helipad artwork.
- Duplifier Ignore directionality beside Connected Matching Blocks, and guarded one-level settings undo for individual or connected applications. Modular Arm / Track Settings copies Join Arms, range and authored motion/load settings through controller or member sections without copying live pose or payload ownership.
- Proportional Ramp Controller deployment for levels 0–15, doors on stationary full-height ramp faces, and complete 2×2/3×3 Hexagonal Tank sections with native per-cell storage.

### Changed

- Remove obsolete inserted transmitter inventories and module drops. Preserve standalone transmitter blocks and handheld transmitters; saved development-era modules migrate to built-in settings at 64-block range while preserving channels, local-output choices and control state.

- Prevent Canopy Glass side faces from fighting with adjacent solid blocks, including carried copies.

- Keep upper diagonal Half-Console front borders on their reflected artwork plane, including Redstone and Vehicle Status displays.

- Avoid regex processing during animated display selection and preserve joined-display layout caches for unrelated distant block updates. Failed animation resources fall back to static artwork until the next resource reload.

- Rename the visible Screens category to Displays, with Classic/Cool/Gold/Ship folders and subject-first Full/Half names. Retain the six Bridge designs as primary Cool/Gold artwork; preserve original Seyr Communications, Navigation and Reactor in Classic. Root Engineering Screen resolves to Engineering (Classic/Full).
- Merge Root Controls into Classic, keeping existing Classic designs for duplicate names. Classic contains 20 choices; each of the six company folders contains ten. Flatten Corporate/Dense folders, rename Half-height to Classic, remove numeric prefixes and Bottom suffixes, and standardize related functional names such as Navigation Controls, Fader Bank and Keypad And Dial.
- Offer Half artwork for wide joined Screens and Flatscreens while retaining Full choices for single blocks and square groups. Configurizer and Copifier edits refresh joins immediately. Signal-band edits apply to the whole authored rectangle, with permission checks for every member.
- Preserve artwork, animation and speed when changing display redstone modes. Every state picker supports search and reveals its selection. Nearby local power activates the whole joined display rectangle.
- Put Glass, Front, Interior and Exterior finishes on top-level tabs for programmable Glass and diagonals. Portholes use Main for their front, with separate Glass/Interior/Exterior; remove the redundant Front picker. Wall finish tabs are labeled Exterior. Interior and Exterior include general materials alongside frames.
- Add case-insensitive multiword texture search with Ctrl+F, retained selection, wheel scrolling and scrollbar dragging. Keep frame controls, screen rows and custom sample editors inside their original configuration windows.
- Standardize dialog headings, tabs, branding, Redstone-last ordering and receiver-only pages. Replace transmitter slots with inline range/local-output controls, retain independent row channels, and share host transmission settings across screen rows and cockpit controls.
- Rename Programmable Input to Programmable Flatscreen without changing its registry ID. Combine Autopilot route selection into General using a non-consuming nine-slot hotbar picker; correct tab spacing and dedicated arm/thruster Redstone layouts.
- Put Inactive before Active on Programmable Surfaces. Attached Surfaces follow arm endpoints and retain departure artwork through movement, reversals and reloads.
- Omit retired duplicate animations, unlisted paired framed screens, unused Dynmap exports and their unused companions from the runtime JAR. Retain listed animations, Off artwork and saved aliases; packaging audits the display-resource/menu relationship.

### Rendering and performance

- Batch plain fixed Canopy Glass into Minecraft chunk layers with shared immutable geometry and an 8 MiB weighted mesh cache. Preserve joins, roof contacts beside opening canopies, collision and cockpit sharing; opening/carried geometry keeps its existing renderer. Held Canopy Glass uses ordinary block sizing and padded inventory artwork.
- Reduce repeated Canopy settings/connection work and skip empty Opaque passes. Retain measured vanilla/Canopy CPU submission, allocation and geometry comparisons in the performance guide; these figures do not establish hardware FPS.
- Share empty face configuration and fluid slots for Programmable Block, Slab and Stairs. Build compact stair render-state snapshots without temporary property maps, preserving custom geometry, overrides, fluids and saved IDs.
- Reuse static opaque vehicle meshes and drawing order, bound nearest-light discovery to 32 candidates, and retire dynamic-light probes when a craft becomes unavailable.
- Avoid redundant updates, dirty saves and render-cache invalidation when applying unchanged settings, including legacy display aliases and default transmission settings.

### Fixed

- Notify local redstone after a Rocker Switch’s tile latch is updated, preserving repeated On/Off activation. Keep passive full programmable cubes conductive so levers on the support below can activate all display variants.
- Apply Half Console state artwork to its inclined Display, with its flat Controls shared; preserve saved surface choices and label both dialog areas explicitly.
- Preserve saved local control latches when no channels are configured, so already-powered Wall Sliders restore joined analog display artwork after world/chunk reload without a retrigger.
- Order separated glazing by individual finite bounds and projected overlap from the actual rendered camera. Preserve valid occlusion constraints around cycles, avoid unrelated coplanar bounds merging, and remove the 128-plane ordering fallback. Include native Front Canopy Glass in the programmable glazing queue; cover perpendicular panes, opposite tile orders and unrelated scene clutter.
- Keep arm-mounted Surface decals visible until native endpoint tile data is ready, suppress duplicate native/carried decals during either packet order, and avoid resurrecting mined endpoint cells.
- Discover complete Surface pads in their placed plane, including overhangs and horizontal wall-mounted layouts. Prevent partial carried pads from drawing stale faces, synchronize copied Surface appearance, and explain when independent supports need Join Arms.
- Retain the original Extender attachment when retracting after pushing other blocks. Preflight complete Rotating Arm Start adjustments, restore valid endpoints after refusal, and retain recoverable snapshots on interrupted transfers. Preserve joined payloads, mounting directions and endpoint edits.
- Keep riders aligned with the rendered arm support through delayed/repeated updates, reversals and server lag, while retaining authoritative collision and rounded native endpoint floors.
- Preserve exact Taxi arm geometry during collision preparation and recover shallow saved support penetration when a clear loaded exit exists. Eligible matched wheel/track layouts retain grounded feet during chassis height changes, and modular sections survive parking, rotation, recapture and recovery.
- Restore paintings after vehicle movement/rotation without treating the retiring craft as an obstacle; retain exactly one owner after failed transfers. Preserve carried sign text, craft lighting for paintings, local lamp illumination and optional OptiFine terrain lighting. Ignore shader shadow-map draws for main-view vehicle visibility.
- Extend joined glass frames around stepped/unequal contours without internal rectangle rails, retain borders beside Bare glass, and preserve concave corners. Correct padded/asymmetric frame openings, half-height proportions and interior mapping.
- Give normal/diagonal trapdoor Frame artwork an open center with physical inner borders and ordinary side-texture pixel scale. Remove shared borders only against another cutout-frame leaf; retain solid ordinary materials and support virtual door sprites without opening them as PNGs.
- Add Slim Glass door inner reveal walls and apply Interior finishes to leaf reveals and fixed frames independently of Front. Tile square frame materials at world pixel scale on regular and Large doors.
- Backlight Opaque replacement artwork in Cyan/Amber glass frames, including joined and diagonal panes. Native/translucent glass and unlit/Bare frames retain world lighting. Match programmable cube/slab face lighting to vanilla positional sampling and geometric normals.
- Adapt atlas shader companions to the diffuse crop and dimensions, fixing OptiFine startup/reload failures on Half displays. Add real shaderpack startup/reload and native-world glazing captures to focused validation.
- Preserve complete artwork UVs and padded item silhouettes on consoles, diagonal half consoles and configured display items, using retained catalog textures.
- Preserve Ramp Controller analog mode and offsets when channel edits are accepted or rejected. Use the same authorized configuration path for ordinary interaction and the Configurizer; keep arm dialogs open during journal preparation.
- Save pending channel edits before dialog rebuilds, reject edits through discarded vehicle views, clear stale received power when the last channel is removed, and publish only the final installed signal during ownership transfers. Preserve cockpit latch levels and independent On sources.
- Recover coupled cargo after partial cross-chunk saves and interrupted rollback, checkpoint pending latch edits, durably retire recovery journals, and retain original failed-load snapshots. Keep asynchronous journal handling compatible with Java 8 on Windows and Linux.
- Preserve unchanged programmable snapshots through Moat recovery. Bound undo memory and release it on logout/world unload; oversized operations report that undo is unavailable. Reject overflowing foreign screen dimensions and avoid loading unloaded furniture neighbours during validation.

### Compatibility

- Preserve saved texture indices for supported renames and aliases. Former Vektor displays resolve to Gold; retired Ashiro/Seyr/Vandor company designs resolve to Cool except the original Seyr designs retained in Classic. Old native keyboard selections use Keyboard Full. Previous frame defaults retain their appearance until edited.
- Remove Tactical Starmap, Science Biology Wide Left/Right and the old Console display designs without replacement mappings. Console blocks remain available.
- The Redstone Receiver Block now uses `vandorlabs:redstone_receiver_block`. The former `vandorlabs:redstone_receiver` ID has no remap; old blocks, items and saved assemblies using it are not migrated. Preserve legacy placed transmitter IDs separately; handheld transmitters retain their item IDs.
- Replace block transmitter-item insertion with settings while retaining previously inserted hardware for recovery through mining. Receivers use the strongest in-range signal at their physical position; linked controls share latch state only within the appropriate transmission range.

## 2.6

- Support unlinked Mekanism Generators Reactor Glass as ordinary vehicle building material. Preserve its tile data and facing through capture, rotation and parking, while refusing glass still linked to a native fusion reactor.

- Add explicit vehicle building/decorative adapters for ordinary LibVulpes coils and structural materials, Advanced Rocketry building parts, Furniture Mod decorations and storage, Galacticraft materials/slabs/stairs/glass and Screens, and Thermal Foundation building materials. Preserve orientations, furniture pairs, native inventories and TV dimensions; initialize carried Galacticraft displays without native load callbacks or extra world-position packets. Keep formed machinery and fluid/RF networks refused.

- Fix vehicle inspection and Taxi conversion on dedicated servers with Thaumic Augmentation. Temporary vehicle chunks initialize capabilities on the physical server side while retaining read-only snapshot block and tile access.

- Preserve generated Ramp Controller sections when entering vehicle mode. Freeze and restore only the clock fields each tile owns, avoiding a false lossless-data refusal while retaining strict validation of unknown block data. Rotate saved ramp map bounds with the vehicle so turned snapshots remain consistent.

- Keep Copy Controller surfaces visible after removing the shared drawing tile or another pad block. Release invalid layouts and automatically rediscover repaired or expanded matching squares, preserving surface artwork and active-copy protection.

- Recapture Rotating Arm platforms at either native endpoint, including folded joined rows. Include replacement blocks in the next stroke, preserve their orientation and contents, and leave mined blocks removed. Keep real obstructions outside the platform blocking movement.

- Keep Rotating Arm sweep clearance within the rotation plane, so touching blocks beside the hinge axis do not falsely block wall-mounted arms. Preserve collision checks against real obstructions.

- Synchronize restored Extender sections after the ownership journal completes, preventing extra standalone arm models after editing a retracted platform on any mounting face. Rebuild surviving joined columns after a distant arm section is removed.

- Restore grid-aligned retracted Extender platforms as normal blocks on every mounting face, preserving displaced arm sections separately. Recheck edited platforms before movement; mined blocks stay removed, new platform blocks travel, and empty arms retain their length.

- Keep unjoined Rotating Arm payloads separate from touching support frames, ignore mismatched neighbouring arm columns, retain configuration dialogs when a joined controller changes, and restore the ordinary selection outline for inert arms. Exercise native middle-click placement, adjacent rotating frames and dismantling.
- Support complete Mekanism Dynamic Tanks, Dynamic Valves and Structural Glass in vehicle transport. Unlinked Structural Glass also works as a standalone building block. Preserve shared fluid tags and inventory through conversion, rotation, cold vehicle reload and native tank reformation; reject partial shells and copying of shared tank inventories.

- Keep unjoined Extender payloads limited to the tip block so touching surroundings do not cause a mounting-structure refusal. Put arm and joined-platform controls together on one page, including range and load-height sliders.

- Add opt-in joined arm platforms with a 1–32 block neighbour range. Extenders support connected rectangles with air holes and intermediate supports, including six-arm 2×3 layouts; Rotating Arms join along a common hinge axis. Vertically moving Extenders offer an optional 1–32 block load height.
- Preserve controller authorization through unload-before-save and reconnect, including Copy Controllers. Keep rotating level payloads attached to their sockets and restore replaced rotating payloads at valid endpoints without false collision stalls.
- Give captured payload blocks their own mining target, retaining the supporting arm and journaling inventory drops through restart recovery.
- Share rider transport and smoothing between arms and ramps. Keep player movement synchronized with platform collision under delayed server ticks, and preserve joined groups while member chunks are unloaded. Carry supported vehicles and their inventories within the configured platform footprint and load height.

- Keep arm retraction requests active while attachment journals reach disk, so one click moves the stacked arms and payload together. Fix Windows journal handling for arms, Copy Controllers and Vehicle Couplers; recover pending arm transfers without leaving blocks locked. Add live six-axis arm regressions covering real journal processing, reversal, configuration and dismantling.

- Restore pre-2.6 Taxi conversion for vehicles whose controller, Pilot Seat, programmable blocks and supported vanilla tiles carry external capability data; keep that opaque data in the saved snapshot while checking each block's own tile settings. Expose all fourteen turntable and surface designs in the Surface category of the shared block texture picker.
- Preserve pre-2.6 vehicle ramp data by comparing unordered reservation sets consistently; restore older hexagonal propulsion snapshots that predate the connected-part property. Report preservation failures without copier-specific wording.
- Ease low-speed steering for tracks and small wheels, including slower stationary tracked pivots and a gentler crawl-to-roll turn rate.
- Synchronize frame-machine reservation ownership to clients so clicks on embedded chip holders reach their controller. Eject retained chips once when a frame assembly is dismantled.
- Toggle Vehicle Couplers with ordinary right-click, while sneak-right-click and the Configurizer open their settings; manual state yields to the next redstone input change.
- Separate Programmer Inventory/Journal tabs from actions, put status below the content area, and select Inventory when an item slot is clicked. Show eight Journal lines on taller screens and add a draggable page scrollbar.
- Bias Programmable Surface artwork layers in the depth buffer to prevent distant backing/artwork overlap.
- Support Mekanism tile-free Basic Block building variants through the shared vehicle, turntable and copier policy; retain restrictions on unsupported connected-machine tiles.
- Use a single-column arm dialog. Standalone regular/Large Extender Arms retract from their placed maximum: half a block for one section, or whole blocks for longer stacks. Retain older in-flight strokes until they return to their construction pose. Restore the attached block at its previous endpoint when a new stroke is blocked, and keep blocked arm source blocks selectable.
- Write initial copy and coupling recovery records and copy completion records on a bounded disk worker, rechecking live inputs before transfer. Budget block lighting and neighbor updates after durable publication; inspect only coupler cells when looking for vehicle joins and reuse exact serialized-size accounting for carried chest edits.
- Preserve housing quad records at the format's declared width under OptiFine and across shader-format changes.

- Align rotating-mode attachments with the block grid at quarter-turn endpoints; keep level-mode attachments connected to their swivels; materialize safely representable full blocks and retain unsupported rotated states as snapshots. Preserve existing saved attachment alignment.
- Journal arm ownership changes before removing or placing blocks, recover interrupted transfers on restart, and keep affected inventories locked until their chunks are durably saved.
- Keep cosmetic arm motion smooth through delayed updates with a pause-aware client clock. Bound the attachment mesh cache, reuse render buffers, coalesce surface-group refreshes and cache carried pressure-plate positions. Send compact Programmer updates for page and activity changes.

- Add a Copy Controller with matching square input/output surfaces, configurable scan height, exact block and inventory copies, signed redstone-channel offsets and recoverable material transactions. Creative copies require no supply chest; Survival copies require matching blocks and stored items and report the first missing material.
- Rename Turntable Surface to Programmable Surface while preserving its saved registry ID. Configure active/inactive materials with the standard Active/Inactive texture tabs and Fit/Tile mapping on the surface itself; default new surfaces to Fit, share surfaces between turntables and copiers, and add eight supplied landing, docking, maintenance and copier designs to the shared texture catalog.
- Match Copy Controller scan directions to its front-facing placement: cyan input on the player’s left, amber output on the right, supply chest behind. Reuse the Turntable height slider and label the signed copy setting Redstone Channel Increment On Copy.
- Add a Programmable Pressure Plate with selectable vanilla Wood, Gold and Iron sensitivity and physical redstone levels, configurable channel behavior and the shared material catalog including trapdoor designs.
- Share one lossless block-support policy between Vehicle Mode, Turntable Controller and Copy Controller. Refuse tile data that cannot survive an exact saved-data round trip.
- Bind automated block movement to the player who configured or placed the controller, separately from copied settings. Copied controllers use the copying player's authorization; controllers without a saved authorization request configuration again.
- Extend Dynmap rendering to native programmer, coupler, track and Core assembly models, plus selected pressure-plate and surface materials.

- Add a Card / Chip Programmer with three retained slots, company-master validation, Master Data Chips and titled eight-page journals. Program the Input item into an empty Output slot, or use Copy to overwrite an inserted Output card/chip of the same kind with the Input item’s complete stored data. Permit programming foreign-company inputs with the selected Master. Use direct in-world socket insertion/removal, and edit input journals through the standard dark Inventory/Journal dialog. Render retained cards and chips and the established keyboard artwork. Show Journal Chip contents and clickable page controls on the large world screen; show the Journal dialog button only for a Journal Data Chip in Input.
- Generalize Extender, Rotating and Fixed Arms for unsupported columns, six mounting directions and ordinary block attachments. Infer extension from stacked arm count and configure level or rotating payloads in the shared single-column dialog; restore exact translation endpoints as placed blocks and retain other poses as bounded snapshots. Preserve attachment data through rollback, recovery and vehicle transport.
- Add one Track block with Compact, Armoured and Bogie styles selected in its dialog. Match clicked tracks and arm tips during placement, turn slab-side mounts correctly, and use the native rotated geometry for selection and collision. Two physical track blocks can support a vehicle; retain older style registry IDs for saved worlds. Keep Track settings separate from Arm settings and animate native tread belts during Taxi travel, with stationary belts in Hover.
- Reduce animated tread vertex submissions by retaining rectangular clipped faces, and validate each complete IE assembly once per snapshot rather than once per member. Preserve the geometry and exact-NBT regression checks.
- Add Vehicle Couplers and a horizontal top/bottom slab variant, with one shared right-click/Configurizer dialog, channel and physical redstone inputs and inverted activation. Automatically collect opposing world-block sections in Taxi/Hover mode; detach only when the complete released section can safely park as blocks. Keep unsafe sections attached and preserve inventories and vehicle ownership through verified save checkpoints. Include nested landing groups down to their outer supports and treat downward propulsion blocks as discovery boundaries.
- Scale Taxi turning radius by the average active wheel/track type and front-to-back wheelbase. Keep the Large Wheel baseline at a four-block wheelbase, tighten smaller wheels and tracks, allow grounded tracked pivot turns, and preserve reverse steering and Hover behavior.
- Open carried vanilla single and double chests through their normal inventory dialog. Preserve item changes, names and locks in the vehicle snapshot, check physical reach, and close access before ownership transfers.
- Capture a complete parked child vehicle on a lower vehicle. Use the lowest supports as the boundary, retain higher landing gear as cargo, and keep the outer vehicle's pilot, controller and Autopilot authoritative. Keep the raised child's running gear and automatic propulsion inactive while same-level attached Coupler sections remain active.
- Keep generic arm tips outside their adjacent payload cells, support ordinary placement and stacked arms on all six axes, restore extender underside artwork and prevent zero-length retraction. Retire stale materialized payload collision after mining and keep stacked Large arms connected when tracks are removed or replaced. Show motion and appearance controls together in a single-column dialog and display deployment errors.
- Support Immersive Engineering scaffolding in Ramp Controllers and extend explicit vehicle rotation adapters to inert IE structures and Mekanism storage and glow panels, preserving their inventories, energy and owner settings.
- Make Strong glass opaque in every shade, with opaque-pass depth writes for programmable panes and shared glazing. Preserve active OptiFine vertex layouts, normals and extended records when building and mapping Programmable Block surfaces; invalidate cached meshes when the format changes.
- Restore face-filling Fit mapping for Programmable Blocks without black margins. Compare vanilla rendered Panels against the previous crop behavior independently of shader-format checks.
- Convert joined 2×2 propulsion blocks between square and hexagonal shapes as one group, preserving their settings. Keep two staggered 2×2 groups joined when their touching outline is not a rectangle. Join complete 3×3 hexagons and pick their original single-block item.

- Add Pinnacle Oculus Drive, Survey Dish, Sentinel Array, Standalone Dish and Standalone Antenna Core assemblies, with matching chips, configurable redstone activation, native holders and cached shutter/dish animations. Their controls use the existing Core and Programmable Matter construction workflow, with illustrated step-by-step building guides.
- Remove Security controls and chip activation gates from all Core and navigation multiblocks. Keep their Data Chip holders visible, clickable and retained independently of redstone activation.
- Give Maxwell, Seyr Meridian and Oculus Drives the Rocket Thruster redstone modes and levels. Only Maxwell exposes particle settings; Meridian and Oculus emit no particles and offer no particle controls. Use warm yellow/white Maxwell artwork in both On and Off states, warm exhaust, and pulsing cyan Meridian lights. These drives remain decorative and supply no vehicle thrust.
- Rename Longburn Drive to Maxwell Drive and Pinnacle Meridian Drive to Seyr Meridian Drive. Replace the Meridian branding with one Seyr nameplate while retaining saved registry IDs and model dimensions.

- Keep copied cockpits independent of their originals. Save relative reservation links and validate each part against the owning cockpit footprint, including local recovery for older clipboard copies.

- Use Door Interior (Dark) as the default housing for new programmable screens, inputs and consoles; preserve saved finishes. Replace the twelve ceiling panels with their authored 64×64 vanilla-style artwork.

- Rename the stationary vehicle setting to Park Mode in cockpit dialogs, Autopilot dialogs and Vehicle Status screens. Saved vehicle modes remain compatible.

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
