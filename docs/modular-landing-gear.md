# Modular landing gear

**Landing Gear Extender** and **Landing Gear Swing Arm** are mounting struts.
**Landing Gear Mount** is the fixed alternative, available in standard and Large sizes.
Attach a Plate, Ski or matching-size Wheel below it; it stays deployed under
manual clicks and redstone. Fixed mounts remain valid Taxi/Hover supports.

**Landing Gear Plate**, **Landing Gear Ski** and **Landing Gear Wheel** form their connected feet. These
parts replace the integrated wheel gear for new construction. Saved integrated
wheel gear can migrate to these parts; older skis and struts remain readable.

Build a vertical column of one to eight Extenders beneath a block with a solid
underside. Place a Plate, Ski or Wheel directly below the column, then connect more
matching feet horizontally. A group can contain up to 64 feet within a 16×16
footprint. Align all connected skis in the same direction.

More columns can attach to the same foot group. They must use the same strut type
and height, and every column needs its own mounting block. The entire connected
group shares one controller, target and animation. Fully deploy it before
changing its topology. Replacing the controlling mount during a valid rebuild
retains the group's settings.

Swing Arms stack from one to eight sections per column, like Extenders. Align connected arms in the same
direction. The arms hinge upward while the lower swivels keep the entire foot
group horizontal. Straight Extenders retract the feet vertically. Fixed mounting
caps and sleeves stay attached to the hull.

Right click a strut or its visible foot with an empty hand to deploy or stow it. Use the Configurizer
on a member to open the shared dialog. Select Manual, ON deploys or OFF deploys,
and enter channels as comma-separated positive numbers. Zero uses local redstone;
configured lists require all channels to be powered. Fold direction can be
changed while fully deployed. Manual mode exposes a deployed/stowed target.

Source part blocks remain at their construction positions during animation.
Collision follows the moving feet and linkages, including beyond those cells.
Movement pauses at a block obstruction or unloaded boundary and resumes when the path
clears. Contacted entities move with the mechanism instead of stopping it; they can
be pushed into surrounding blocks. Missing
controllers fall back to visible, collidable construction parts. Breaking the
assembly releases its surviving parts without deleting them.

Modular gear supports Hover conversion. Complete and fully deploy each group
before scanning a craft. Each mounting strut counts as a physical support; the
group still shares one control input. Plates require Hover; ski-only craft can
Taxi over ice. Use the configured channels to operate captured gear,
and deploy it before landing and parking. Member links, foot orientation and
fold direction rotate together when the craft is parked at a cardinal heading.

The native part models, connection masks and pixel textures are adapted from the
supplied MCTrek landing-gear kit. Inventory models have separate display
transforms to leave visible padding around the rendered icon.

Five Programmable Matter Ingots in a U shape craft two Plates. This recipe is
distinct from the existing Diagonal Half Console recipe.

Controls apply immediately as you change them. The fold direction describes the
foot movement; the elbow bows toward that direction to remain above the level
foot while stowed. The lower swivel has a closed, textured underside even when
no foot is mounted.

Extend a ski row by clicking an existing ski: the new ski inherits its facing,
including when the clicked ski belongs to an assembled extender group.

Large Mounts, Extenders and Swing Arms occupy one block per section, with a
compact mounting cap and a heavier mechanism than standard arms. Attach each
column below a solid underside. Existing four-cell arm sections automatically
release their old companion cells when loaded; independent neighboring arms
remain in place. Large parts rotate with captured craft.

Separate Wheel feet reuse the tires, hubs and brackets from the telescopic
Landing Gear. The standard wheel fits one-block arms; the wide Large Wheel
reserves a 2x2 footprint for Large arms. Align adjacent wheels as for skis.
Wheels work under both Extenders and Swing Arms, retract with their arm, and
allow Taxi mode when all supports use wheels. Tires roll with vehicle travel.

Craft a Large Extender or Large Swing Arm from four matching standard arms.
Craft a Wheel from three Programmable Matter Ingots, one Slimeball and one Iron
Ingot. Four Wheels craft one Large Wheel. These recipes are shapeless.


Ski-only vehicles can use Taxi mode on ice, packed ice and frosted ice. Every runner must remain above ice; mixed plate/wheel/ski supports do not qualify for this exception. Taxi movement stops before a runner leaves the ice. Lift blocks are not required for ski Taxi mode; Hover still needs its usual lift and thruster hardware.

The modular wheels roll with actual Taxi travel, reverse their roll when backing up and steer with the vehicle controls. Hover pauses wheel roll and steering.

Adjacent Programmable Trapdoors configured to move into the next block can cover modular gear shafts. Aim the trapdoor toward any arm, wheel or other foot member. Covers open for deployment, remain open throughout retraction, and resume their own redstone setting only once fully stowed. Covers follow the gear into a vehicle and retain the same protection against closing through deployed feet.

Legacy integrated Landing Gear is removed from crafting and creative/search catalogs. Its saved block ID remains readable for migration. Saved wheel gear converts on chunk load, or when right-clicked, into an Extender column and separate Wheel. Small, Medium and Large gear use the standard parts; Extra Large variants use compact Large arms and oversized Wheels. Travel rounds up to whole-block column sections. Facing, deployment and redstone/channel settings are retained. Migrated controllers retain the legacy input rule: local power or any listed channel; their dialog labels this rule. Conversion checks the entire footprint and the mounting support first; blocked or incomplete mounts remain intact until there is room to convert. Existing inventory stacks and dropped items split into the corresponding arm sections and wheels; excess parts drop if the inventory is full. Captured legacy vehicles retain their saved representation during transfers and can convert after a later chunk reload or interaction.

A manual deployment override on modular gear lasts until its configured redstone input changes, including across saves and migration.

Extender-mounted wheels retain clearance beneath the fixed casing when fully retracted. The wheel bracket, socket and rod use the same adjusted pose for rendering and collision.

Large Extenders and Large Swing Arms each occupy one source block.
The Large Wheel has the legacy Extra Large tyre diameter (1.75 blocks), reaches
one block below its source cells, and requires a matching large Mount, Extender or Swing Arm. Standard
wheels require standard arms. Click any quadrant of a large part to align a new
large arm or wheel with its anchor and orientation. The arm mounting cap must touch a solid underside. Connected straight Large Extenders may face different ways.

Right-click an option button to cycle to its previous value. Settings apply immediately.

## Animated combinations

These in-game clips show complete deployment and retraction. The foot stays level as a Swing Arm folds; an Extender lifts its foot vertically. Large arms have a one-block mounting footprint; Large Wheels retain four source cells. Wheels use the matching arm size; Plates and Skis can form shared connected feet under either arm size.

| Foot | Small Extender | Small Swing Arm |
| --- | --- | --- |
| Plate | ![Small Extender with plate](images/gallery/landing-gear/gear-small-extender-plate.gif) | ![Small Swing Arm with plate](images/gallery/landing-gear/gear-small-swing-plate.gif) |
| Ski | ![Small Extender with ski](images/gallery/landing-gear/gear-small-extender-ski.gif) | ![Small Swing Arm with ski](images/gallery/landing-gear/gear-small-swing-ski.gif) |
| Wheel | ![Small Extender with wheel](images/gallery/landing-gear/gear-small-extender-wheel.gif) | ![Small Swing Arm with wheel](images/gallery/landing-gear/gear-small-swing-wheel.gif) |

| Foot | Large Extender | Large Swing Arm |
| --- | --- | --- |
| Plate | ![Large Extender with plate](images/gallery/landing-gear/gear-large-extender-plate.gif) | ![Large Swing Arm with plate](images/gallery/landing-gear/gear-large-swing-plate.gif) |
| Ski | ![Large Extender with ski](images/gallery/landing-gear/gear-large-extender-ski.gif) | ![Large Swing Arm with ski](images/gallery/landing-gear/gear-large-swing-ski.gif) |
| Wheel | ![Large Extender with wheel](images/gallery/landing-gear/gear-large-extender-wheel.gif) | ![Large Swing Arm with wheel](images/gallery/landing-gear/gear-large-swing-wheel.gif) |

Fixed Mounts hold the same three foot types permanently deployed. They use the same standard/large compatibility rules and do not animate.

![Standard fixed mounts with Plate, Ski and Wheel](images/gallery/landing-gear/fixed-standard.png)

![Large fixed mounts with Plate, Ski and Wheel](images/gallery/landing-gear/fixed-large.png)
