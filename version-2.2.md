# Vandor Labs 2.2

Build working landing gear, showcase your creations on rotating platforms, and give your ships a new generation of cockpits and industrial detail.

## Landing gear you can build your way

Choose fixed **Mounts**, or combine **Extenders or Swing Arms** with separate **Plates, Skis or Wheels**. Stack arm sections for longer travel, connect matching feet into a shared assembly, and choose standard or **2×2 Large** parts to suit your ship. Swing arms fold while keeping their feet level; collision and selection follow the moving mechanism. Contacted entities move with the gear during a stroke.

Settings apply immediately. Operate visible feet by hand or use redstone and channels, including aboard captured vehicles. Adjacent trapdoor covers open for deployment and stay clear until the gear finishes retracting.

Wheeled craft can Taxi with tyres that roll, reverse and steer. **Ski-only craft can Taxi on ice, packed ice and frosted ice**, stopping before their runners leave the ice. Hover retains its normal hardware requirements.

Existing integrated wheel gear can migrate into matching modular arms and wheels, retaining its facing, deployment and controls. Conversion waits when the new footprint or mounting support is blocked; old saved IDs remain readable.

[Build modular landing gear](docs/modular-landing-gear.md)

## Put your builds in motion

**Turntables** turn block-built displays on floor, wall or ceiling platforms. Build a square surface, add a controller, choose the capture height and Scan. Redstone controls rotation, with configurable direction and speed.

Only the circular surface disc rotates; its square housing corners stay still. Scan captures the build across the full square footprint, so overhangs move together. Players and loose items travel with floor platforms, and players can walk and jump freely. Stopping holds the current angle. Adjust it with the Angle slider while paused; Restore explicitly returns blocks at the nearest quarter turn, preserving supported block directions, inventories and entity headings. Obstructed return positions retain their captured contents until cleared.

[Build a turntable](docs/turntables.md)

## Give your workshop a centerpiece

Three **Universal Constructors**—the Assembly Press, Synthesis Tower and Enclosed Gantry—bring large industrial models and updated company artwork to your builds. Assemble them from a matching Core and Programmable Matter frame. The Core can occupy any bottom corner, along either horizontal axis. Failed assembly identifies a mismatched cell; dismantling returns the frame.

These are decorative multiblocks with creative-only Cores; they do not process recipes yet.

[Constructor frame dimensions and assembly](docs/universal-constructors.md)

## Matter, power and visible storage

The sunlight-powered **Programmable Matter Infuser** converts iron using redstone fuel, with hopper and item-handler automation. Pack nine Matter Ingots into a building block or unpack them again. Both forms have new artwork.

**Fuel Tanks** have hollow interiors, side windows and actual fluid volumes that stay level as they fill. Horizontal hexagonal tanks keep flat top and bottom faces, with square windows on the diagonal sides. Choose Hexagon or Block instantly, place the connections vertically or horizontally, and join tanks along their axis for pooled storage. **Battery Blocks** offer directional RF input and output. Both have live current/max readouts and vertical fullness meters. Tank shape, casing, Tile/Fit artwork and contents share one compact dialog. Hexagonal tanks match thruster dimensions and shell shading, with straight engine rear rims for clean joins.

[Matter and storage details](docs/matter-and-storage.md)

## Outfit the cockpit—and the crew

Two multiblock cockpits, a chair with side controls, and a separate yoke/screen assembly add independent displays, touch controls and physical throttle outputs. Seated assemblies support normal vehicle conversion and parking.

Programmable armor gains complete **Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor** sets. Choose company designs independently for helmets, chestplates, leggings and boots in the Armor category, alongside the existing role designs.

![Six company armor designs with their matching inventory icons](docs/images/gallery/armor/company-sets.png)

Seven **Data Cards** and seven matching **Data Chips** add handheld props in unbranded, Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor designs. Their opaque, extruded item models retain crisp artwork and solid edges in hand. They are creative-catalog items, with no placement or data-storage behavior.

Material tools also gain Stretch, Tile and Fit layouts, independent ON/OFF light artwork, nested texture folders, and water-filled programmable Slabs and Stairs.

[Cockpit controls](docs/cockpits.md) · [Material settings](docs/unified-materials.md) · [Full changelog](CHANGELOG.md)
