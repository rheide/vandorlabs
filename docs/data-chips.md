# Data Chips

Data Chips retain their company identity separately from their programmed type. Existing company chips and Access Cards remain available.

- **Journal Data Chip:** stores the title and pages written by the Chip/Card Programmer. Programming a journal changes the chip to the Journal appearance.
- **Vehicle Data Chip:** stores a vehicle design. Writing a design from a Vehicle Controller changes the inserted Data Chip to the Vehicle appearance. The chip must remain in the controller's attached Data Chip Mount while writing.
- **Navigation Chip:** stores a dimension and X/Y/Z location written by a [Navigation Table or Angled Navigation Console](navigation-displays.md#seed-navigation-forge-1122). Supported displays show a 1,000-block radius around its saved point without a linked observer. A chip programmed inside a recorded sweep also carries a snapshot of its surface pixels. Creative also offers a blank chip.
- **Catalog Chip (Master):** contains explicit, saved blueprint lists for each builder. A builder reads its own list; the word “Master” is a label, not a permission switch.

## Browsing a catalog

In Minecraft 1.12.2, insert a Catalog Chip into a screen's built-in reader or its attached Data Chip Mount, then select the chip display surface. Joined screens use one shared display. The same display is available on the Chip/Card Programmer's physical screen when a catalog occupies its input or output slot.

Use the previous/next controls just as with Journal pages. Each page shows one blueprint, its name and a rotating 3D preview. Core blueprints use the same complete multiblock model as the builder's preview; other blueprints show their actual item model. Viewing pages does not change the chip's blueprints.

## Master blueprints

The Master Catalog contains these lists:

| Builder | Blueprints |
| --- | --- |
| Synthesis Tower | Synthesis Tower Core, Assembly Press Core, Enclosed Gantry Core, Sentinel Array Core, Survey Dish Core, Vector Fold Array Core, Meridian Drive Core, Maxwell Drive Core, Oculus Drive Core, Vehicle Controller, Ramp Controller |
| Assembly Press | Data Chip, Access Card, Battery Module, Autopilot Module, Navigation Data Module |
| Enclosed Gantry | Extending Arms, Rotating Arms, their Large variants, Landing Wheels and Large Landing Wheels, Landing Skis, Landing Plates, Square Couplers, and Slab Couplers |

The three builder Cores are available in the Synthesis Tower without a catalog. The Assembly Press always offers Data Chips and Access Cards. The Enclosed Gantry's ordinary arm and landing recipes remain available without a catalog. Extra blueprints come from the lists stored on the inserted chip.

The expanded builder recipes and catalog model browsing are currently for Minecraft 1.12.2. Minecraft 1.21.1 includes the four chip appearances and creative variants, and Journal programming selects the Journal appearance; its existing native programming restrictions still apply.

## Vehicle previews

On Forge 1.12.2, chips containing a complete vehicle design use an adaptive,
material-colored surface preview. The default density is eight voxels per
Minecraft block on each axis. Large designs reduce that density to stay within
131,072 voxels and 512 voxels on any axis. A further reduction limits the emitted
mesh to 65,536 exposed voxel faces.

The preview samples ordinary baked block-model surfaces, their texture UVs,
vertex colors and block tint. This preserves sub-block shapes such as slabs and
slopes where their models provide those surfaces. Blocks without a usable model
fall back to their selection bounds and map color. The preview does not construct
a vehicle world or instantiate tile entities, so tile-renderer effects and
world-dependent connected models are not reproduced. Texture sampling uses the
first animation frame.

Building this preview costs more CPU on its first display than the old fixed
grid: the client decodes the design and samples up to two million surface points.
Subsequent frames reuse the sample and compiled mesh. Samples and meshes each
have an 8 MiB cache budget; block-model sampling records have a 4 MiB budget.
These are implementation bounds, not measured frame-rate results.

Old chips containing only their saved occupancy thumbnail retain that preview.
Display changes never rewrite the chip or its vehicle design.

### Catalogs from the Synthesis Tower

The Tower's Digitize mode consumes one eligible single block and 8,000 RF to
replace an inserted Data Chip with a Catalog for that block. Its label names
the block; inserting it into the Tower enables synthesis of the scanned design,
and a Screen can display its preview. See the [Digitize instructions](universal-constructors.md#digitize-a-block)
for supported blocks, retained appearance settings and cancellation behavior.
