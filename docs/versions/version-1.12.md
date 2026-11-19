# Vandor Labs 1.12.0

The NeoForge 1.21.1 build now includes the **Copy Controller**. Build matching
Programmable Surface pads and put a material chest behind the controller for
Survival copying. The settings dialog shows refused blocks, and output becomes
visible to connected players when the transfer completes. Double chests copy
with both halves and their contents. Complete stationary Navigation Table
groups retain both their Navigation Chip and module mounts. Complete Survey
Dish and Sentinel Array frames get independent ownership. All seven Programmable Screen roles
can also be copied when they have static artwork and no private media or
active outputs. Receiver channels follow the configured Copy increment. Idle, disconnected Synthesis Towers, Assembly
Presses and Enclosed Gantries copy when all their slots are empty. [Copy Controller guide](../copy-controller.md)

The **Assembly Press** makes Data Chips and Access Cards by default. Insert a
Catalog Chip that lists the recipe to produce Battery, Autopilot, or Navigation
Data Modules. Existing Navigation Data Module jobs keep their progress when the
world loads. With a permitting Catalog Chip, the **Synthesis Tower** can
fabricate a Ramp Controller and the Maxwell, Meridian, Oculus and Vector Fold Cores. The **Enclosed Gantry** makes eight mechanical blocks, including
arm hinges, wheels and resource ports. Both machines render work at their
authored beds and let you collect completed products there. The **Paladyne**
and **Seyr Navigation Computers** form from their original Creative-only Cores
and display Off, Idle or Computing. [NeoForge builder guide](../builders-1.21.md)

**Programmable armor** uses the original four crafting recipes. Configure a held
piece with its role, company, or programmable material artwork in Creative, or hold a Configurizer and
use the armor from your offhand in Survival. The same picker works on mounted
armor and can sample configured Blocks and Doors from your inventory. Sample
vanilla model faces, full Programmable Blocks or static Programmable Light artwork in the world, or transfer an
appearance with the Duplifier. Static Light artwork is also available. [Programmable armor guide](../programmable-armor.md)

The **Advanced Pilot Chair** is available as stationary furniture. Its
two armrest sliders let a seated player set numeric levels on configured
Redstone channels. Adjust the height and channels through the chair dialog.
The **Cockpit** forms a stationary seat and console with a retractable screen
bank. **Yoke and Screen** operates Redstone from both displays. [Chair and cockpit guide](../cockpits.md)

With WorldEdit 7.3.8, turning a clipboard also rotates the corner of floor
and ceiling propulsion wedges. JEI lists the four native machine categories
and shows which recipes require a Catalog Chip.
[WorldEdit compatibility](../development/native-worldedit-rotation.md)

The experimental **Turntable Controller** can capture supported blocks above
a Programmable Surface, turn them and restore them to the world from the dialog or with configured
Redstone inputs. Stand on supported blocks or the disc of an upward-facing
Turntable to ride through the turn. Enable it
with `-Dvandorlabs.turntableControllers=true`. [Native Turntable guide](../development/native-turntable.md)

Imported stationary wheels and eligible wheel items in a player inventory
can be converted with
`-Dvandorlabs.autoConvertLandingGear=true`. [Legacy landing gear](../development/native-legacy-landing.md)

See the [NeoForge coverage inventory](../development/feature-coverage.md) for
the current feature list and port status.
