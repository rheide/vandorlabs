# Modular landing gear

**Landing Gear Extender** and **Landing Gear Swing Arm** are mounting struts.
**Landing Gear Plate** and **Landing Gear Ski** form their connected feet. These
parts supplement the existing telescopic gear, skis and struts; saved older
blocks keep their identities.

Build a vertical column of one to eight Extenders beneath a block with a solid
underside. Place a Plate or Ski directly below the column, then connect more
matching feet horizontally. A group can contain up to 64 feet within a 16×16
footprint. Align all connected skis in the same direction.

More columns can attach to the same foot group. They must use the same strut type
and height, and every column needs its own mounting block. The entire connected
group shares one controller, target and animation. Fully deploy it before
changing its topology. Replacing the controlling mount during a valid rebuild
retains the group's settings.

Swing Arms use exactly one arm per column. Align connected arms in the same
direction. The arms hinge upward while the lower swivels keep the entire foot
group horizontal. Straight Extenders retract the feet vertically. Fixed mounting
caps and sleeves stay attached to the hull.

Right click a strut with an empty hand to deploy or stow it. Use the Configurizer
on a member to open the shared dialog. Select Manual, ON deploys or OFF deploys,
and enter channels as comma-separated positive numbers. Zero uses local redstone;
configured lists require all channels to be powered. Fold direction can be
changed while fully deployed. Manual mode exposes a deployed/stowed target.

Source part blocks remain at their construction positions during animation.
Collision follows the moving feet and linkages, including beyond those cells.
Movement pauses at an obstruction or unloaded boundary and resumes when the path
clears. Keep people and objects clear of the mechanism during a stroke. Missing
controllers fall back to visible, collidable construction parts. Breaking the
assembly releases its surviving parts without deleting them.

Modular gear supports Hover conversion. Complete and fully deploy each group
before scanning a craft. Each mounting strut counts as a physical support; the
group still shares one control input. Skis and plates disable Taxi, as with the
existing non-wheeled gear. Use the configured channels to operate captured gear,
and deploy it before landing and parking. Member links, foot orientation and
fold direction rotate together when the craft is parked at a cardinal heading.

The native part models, connection masks and pixel textures are adapted from the
supplied MCTrek landing-gear kit. Inventory models have separate display
transforms to leave visible padding around the rendered icon.

Five Programmable Matter Ingots in a U shape craft two Plates. This recipe is
distinct from the existing Diagonal Half Console recipe.
