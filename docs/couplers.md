# Vehicle Couplers

## NeoForge 1.21.1

1. Make a **Vehicle Coupler** in the Enclosed Gantry with one Programmable Block
   and 8,000 RF, or craft it from a Programmable Block, two iron ingots and two
   redstone dust. The Gantry also makes the **Vehicle Coupler Slab**. Craft one
   full coupler into two slabs, or combine two slabs into a full coupler.
2. Place one coupler against the mounting block on each section, with the two
   heads facing each other in adjacent block spaces. Full couplers can mount in
   all six directions. Place slabs against a wall below or above its midpoint
   to select the lower or upper half. Floor and ceiling placement keep slab
   heads horizontal. Pair identical forms with matching slab heights.

   ![An upper Coupler Slab and a full Vehicle Coupler on their mounts](images/couplers-neoforge/native-coupler-world.png)

3. Right-click a head or use the **Configurizer** to open its **Vehicle Coupler**
   dialog. Use **Engage Coupler** on both heads. For signal operation, configure
   the channels and **Lock when signal is On / Off** for each head. Physical
   redstone also drives the input. A manual setting lasts until that combined
   input changes or the settings are edited.

   ![Engaged Coupler controls showing two receive channels](images/couplers-neoforge/native-coupler-menu.png)

4. With both heads engaged, assemble the parked hull through its Vehicle
   Controller. The matching heads connect the sections during that scan. The
   resulting rigid hull needs one Vehicle Controller, one Pilot Seat and the
   usual support for its selected mode.

### Moving sections

NeoForge 1.21.1 supports this rigid connection during initial assembly.
Automatic pickup, release from a moving hull and articulated joints are
available in Forge 1.12.2.

## Forge 1.12.2

Vehicle Couplers join sections through opposing coupling heads. The full block retains its original `square_coupler` identifier and saved settings. It supports all six mounting directions. Each half has its own channel list and **Lock when: On / Off** setting; physical redstone and the configured channel bank control the connection. Couplers consume signals and emit no redstone or channel power.

Ordinary right-click toggles coupling without opening a dialog. Sneak-right-click or use the Configurizer to open the authorized Vehicle Coupler dialog, then use **Done** to close it. Right-click the behavior option to select its previous value. A manual choice persists until the redstone input changes or the settings are changed. The status line explains pending connection work or an unsuccessful operation.

**Vehicle Coupler Slab** uses the same settings in half the height. Click a wall below its midpoint for the bottom half or above the midpoint for the top half. Clicking a floor places the bottom half; clicking a ceiling places the top half. Floor and ceiling placement keep the head horizontal, oriented from the player's facing. Its native panels retain the original artwork proportions by cropping vertical UV regions instead of stretching the textures. Collision, selection and the two connection endpoints follow the same flattened geometry.

Craft a Vehicle Coupler from a Programmable Block, two iron ingots and two redstone dust. One full coupler converts to two slab couplers; two slab couplers recombine into one full coupler. Placed couplers retain their channels and activation behavior through saving and vehicle transport.

## Joining a section in Vehicle Mode

An existing Taxi or Hover vehicle can pick up a section that is still made of world blocks. Put a coupler on each side of the connection, facing the heads toward one another, and activate both halves. Slab heads must use matching heights. Stop the vehicle and line it up precisely with the block grid. The controller scans the opposing section automatically; its blocks and inventories become part of the same vehicle entity.

The player who assembled the vehicle or authorized its carried Coupler dialog, or the current driver, must be online, alive and in the same dimension, with permission to modify the complete section. Older saved vehicles need a driver or one authorized visit to the carried Coupler dialog before automatic transfers are enabled. Copied block settings do not copy another player's building authority. The entire section and its boundaries must be loaded. Leave its seats empty and move other entities clear. The piloted physical segment keeps one Vehicle Controller; controllers in other coupled segments remain saved blocks and do not control the vehicle. A joined vehicle keeps at most one Autopilot and the configured vehicle block, size and saved-data limits. Unsupported blocks and incomplete assemblies are refused under the same [Vehicle Mode compatibility policy](vehicle-compatibility.md). This operation picks up world blocks; it does not merge two existing vehicle entities.

## Hover lift per section

Each section connected through enabled couplers needs at least three
Antigravity Plates, Repulsor Arrays or Vertical Hover Thrusters before the
vehicle can enter Hover. Lift in one section cannot support another section.
This also applies to rigid connections and sections joined by parallel coupler
pairs. Taxi keeps its existing support requirements and does not require lift
blocks in every section. An ordinary block bridge makes the adjoining sections
one rigid body.

## Couplers on moving arms

1. Build and configure the Arm with its mounting block and supports in place, following the [arm construction guide](modular-landing-gear.md) or [joined platform guide](joined-arm-platforms.md). Choose its payload mode and extension before making the connection.
2. Place a Coupler on the Arm's native payload, then place an opposing head on the other section. Assemble or attach the sections through their Vehicle Controllers as described above. Each independently released section still needs its own controller, seat and required supports.
3. Use **Articulation: No** for a rigid connection that retains the complete head orientation. Select **Yes** on either half to permit articulation. Multiple arm connections must be physically compatible; an overconstrained motion refuses safely rather than opening a gap between the heads.
4. Activate an Arm through its usual channel or control. Linked passive Arms follow only as needed to preserve the connections; their own channels and requested targets remain unchanged. Keep the mounting blocks in place and leave clearance for the complete moving payload.

The arm, attached payload and connected sections animate together, including during reversals and saved fractional motion. Actual collision checks can stop a stroke; remove the obstruction and retry the ordinary control. A stopped stroke retains its attachment and controls.

Before releasing a connection with a general body pose, align the sections and return connected Arms to their original assembly reference pose. The dialog reports this requirement when needed. A supported single articulated hitch can retain its existing bounded fractional release. Release preserves each output's cargo and association; open the other head independently if necessary, then drive the sections apart. Carried Arm payloads remain captured vehicle contents at their endpoints; standalone native Arms retain their normal world-block handoff.

## Articulated trailers

1. Build each section separately. Put one pair of opposing, enabled coupling heads between the sections; leave room around the hitch for the sections to turn.
2. Open either head's dialog with sneak-right-click or the Configurizer and select **Articulation: Yes**. Either head enables the hinge. The default **No** retains rigid vehicle behavior.
3. Join the sections as described above. Additional trailers can have their own single-pair hitches. Each joint follows independently in Taxi and Hover, including vertical changes in terrain or altitude.
4. Drive forward to straighten the train before parking, attaching or restoring a cargo section as blocks. These operations materialize blocks on the world grid and require aligned joints. Separating two complete vehicles keeps their current positions and headings instead. Parking accepts up to five degrees of yaw and three degrees of pitch per joint, with no more than 0.75 block of accumulated displacement and 7.5 degrees of accumulated rotation. Final snapped destinations must still be clear and editable. Carried Ramp Controllers also wait for alignment before rebuilding their endpoints.

A joint cannot exceed a combined angle of 75 degrees. Terrain and the actual collision shapes of the other sections can stop it sooner. Wide hulls need more clearance than narrow drawbars. Two or more coupling pairs between the same sections keep that connection rigid even when Articulation is enabled. A solid block path or a closed loop also prevents the affected joint from bending.

Connected trailers share one vehicle owner and inventory ownership. Releasing a connection can create two vehicle entities when both halves are complete vehicles. Saved vehicles retain each joint's pose. Clicking a carried control, boarding a seat and opening a container use its section's current position. Trailer wheels follow their own section rather than steering with the tractor.

![A straight four-section vehicle](images/coupler-straight.png)

![The same sections with independent yaw and pitch](images/coupler-articulated.png)

## Releasing a section

Right-click either carried coupling head, without sneaking or holding the Configurizer, to request separation in Taxi or Hover. Clicking records the current authorized player; there is no need to open the dialog first. The manual choice lasts until the physical/channel input changes. Releasing either half's activation input also requests separation.

1. Stop the vehicle and leave the detached section's seats empty.
2. Give each half its own Vehicle Controller, Pilot Seat and required supports. In Hover, each half also needs propulsion and three lift blocks in every connected section. Taxi halves need wheels or skis.
3. A small suspension tilt can settle into the detached driver's frame through a collision-checked motion. This is limited to three degrees and 0.75 block of displacement; larger tilts or blocked settling require more clearance or leveling first.
4. Click the carried coupler. Both halves retain their heading, mode, block settings and inventories as separate vehicles. A bounded clearance/settling adjustment may separate the coupling heads. This operation does not place blocks on the terrain and does not require a cardinal heading. The current driver stays with the retained Pilot Seat.

A cargo section without its own controller and seat retains the world-block release behavior: straighten the joints, stop near a cardinal heading and leave its snapped parking destination clear. The retained vehicle keeps its actual position and heading. Terrain, players, other mechanisms and the retained hull must leave enough room for the complete parked geometry. A section with a controller and seat that fails independent vehicle validation stays attached instead of being converted silently.

If another block path or active coupler still connects the section to the controller, releasing one head leaves that section on the vehicle. Unavailable chunks, invalid equipment, occupied detached seats or denied permissions also leave the complete original vehicle intact. The Coupler dialog reports the reason. A manual click releases one coupling pair; a detached passenger seat does not replace the seat beside its Vehicle Controller. Completing a manual split also clears older failed manual release requests on the connections it keeps, avoiding an unintended second separation. Terrain visible through empty space inside a bent vehicle's bounds remains selectable and editable.

Hover sections follow vertical movement with a short delay and settle horizontal after the height stops changing, including near the ground. Where leveling would dip a section into the ground, a clear upward translation can lift the existing pose first. Actual terrain and self-collision checks still apply to every accepted step. Once stopped and level near the ground, Hover can select Park directly with fully extended gear; a Taxi transition is not required. This avoids re-tilting mixed wheel/track sections before the ordinary parking preflight.

Separation writes and syncs an ownership journal before changing either entity. Both outputs remain inert until their affected chunks have been checkpointed. Interrupted work recovers the original vehicle or the committed pair without duplicating blocks or inventories. World-block release retains its exact-destination checks and durable recovery.

The startup setting `vehicles.maxArticulationPoints` in `config/vandorlabs-blocks.cfg` limits independent hinge points in new assemblies and attachments. Its default of `7` permits eight independently articulated segments; `0` permits rigid assemblies only. The accepted range is 0–16384, with the existing block, axis, data, collision and per-step work limits still applying. Restart Minecraft or the server after editing it. Parallel pairs, rigid links and bridged sections do not add independent segments. Lowering the limit does not prevent longer saved vehicles from loading, being recovered or parking. Four- and eight-segment driving regressions cover acceleration from a slight initial bend, terrain collision and saved poses.

### Arm face orientation

Coupler face artwork rotates with the complete Arm payload frame. The Amber and
Cyan sides remain attached to the same authored faces when a full Coupler moves
between horizontal and vertical orientations, including native endpoint handoffs.
Slab Couplers retain a horizontal half-height housing. A pose that cannot represent
that housing as native blocks stays captured and selectable until the Arm returns
to a supported pose. Older saved Arm payload mappings remain readable.
