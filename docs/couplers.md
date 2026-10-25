# Vehicle Couplers

Vehicle Couplers join sections through opposing coupling heads. The full block retains its original `square_coupler` identifier and saved settings. It supports all six mounting directions. Each half has its own channel list and **Lock when: On / Off** setting; physical redstone and the configured channel bank control the connection. Couplers consume signals and emit no redstone or channel power.

Ordinary right-click and the Configurizer open the same authorized Vehicle Coupler dialog. Use **Done** to close it. Right-click the behavior option to select its previous value. The status line explains pending connection work or an unsuccessful operation.

**Vehicle Coupler Slab** uses the same settings in half the height. Click a wall below its midpoint for the bottom half or above the midpoint for the top half. Clicking a floor places the bottom half; clicking a ceiling places the top half. Floor and ceiling placement keep the head horizontal, oriented from the player's facing. Its native panels retain the original artwork proportions by cropping vertical UV regions instead of stretching the textures. Collision, selection and the two connection endpoints follow the same flattened geometry.

Craft a Vehicle Coupler from a Programmable Block, two iron ingots and two redstone dust. One full coupler converts to two slab couplers; two slab couplers recombine into one full coupler. Placed couplers retain their channels and activation behavior through saving and vehicle transport.

## Joining a section in Vehicle Mode

An existing Taxi or Hover vehicle can pick up a section that is still made of world blocks. Put a coupler on each side of the connection, facing the heads toward one another, and activate both halves. Slab heads must use matching heights. Stop the vehicle and line it up precisely with the block grid. The controller scans the opposing section automatically; its blocks and inventories become part of the same vehicle entity.

The player who assembled the vehicle or authorized its carried Coupler dialog, or the current driver, must be online, alive and in the same dimension, with permission to modify the complete section. Older saved vehicles need a driver or one authorized visit to the carried Coupler dialog before automatic transfers are enabled. Copied block settings do not copy another player's building authority. The entire section and its boundaries must be loaded. Leave its seats empty and move other entities clear. A joined vehicle keeps one Vehicle Controller, at most one Autopilot and the configured vehicle block, size and saved-data limits. Unsupported blocks and incomplete assemblies are refused under the same [Vehicle Mode compatibility policy](vehicle-compatibility.md). This operation picks up world blocks; it does not merge two existing vehicle entities.

## Releasing a section

Release either half's activation input to request separation. The section remains attached while the controller checks whether it can safely restore the detached part as ordinary blocks. Stop near a cardinal heading, as when parking a vehicle. The detached blocks snap to the parking grid; the retained vehicle keeps its actual position and heading. Terrain, players, other mechanisms and the retained hull must leave enough room for the complete parked geometry.

The Vehicle Controller, main Pilot Seat, required supports and the equipment needed for the current Taxi or Hover mode must stay with the retained vehicle. Empty seats in the detached section before releasing it. If another block path or active coupler still connects the section to the controller, releasing one head leaves that section on the vehicle.

An unsafe angle, blocked destination, unavailable chunk or permission failure keeps the whole section attached. The coupler dialog explains the reason. Clear the obstruction or align the vehicle; pending requests retry automatically. A blocked request never discards the section or creates another vehicle entity.

## Transfer safety

Transfers preserve block states, complete supported tile data, stored items, vehicle identity, pilot assignments and control state. The affected inventories and vehicle controls remain unavailable until a verified save checkpoint finishes. Persistent journals and vehicle ownership epochs prevent an old saved entity from owning the same cargo twice. Interrupted transfers remain held for recovery until their exact saved ownership can be reconciled. Keep protected areas intact and allow world saving to finish; conflicting external edits leave the journal retained instead of overwriting newer blocks or refunding missing cargo.
