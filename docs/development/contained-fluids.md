# Contained fluids across supported targets

Slabs, stairs and other partial blocks retain independent bucket volumes rather than treating every liquid as vanilla waterlogging. The native adapter must preserve lava, registered mod fluids, full component/tag data, occupied unresolved slots, and the collision-free portion of each cell.

`FluidVolume` owns partition cuts, empty cells, side ownership, boundary winding and half-open point lookup. `FluidSurfaceMesh` owns the extracted non-panel surface clipping and corner-height rules. Forge and NeoForge translate their collision bounds and fluid metadata at these boundaries. Geometry changes invalidate published fluid snapshots even when the fluid identity does not change.

A native `ContainedFluidHost` supplies its owner, immutable local geometry and geometry revision. Loading only installs saved data and queues work. The world service rechecks the exact owner identity on the appropriate world thread before publishing light, model data or neighbour updates. Chunk workers read immutable render snapshots, including nearby fluid-contact geometry, without inspecting live block entities.

Each contained side retains its original compound. A resolved `FluidStack` is a projection used for gameplay and rendering; an unknown registry entry or decode failure leaves the original compound occupied and inert. Save, pick, drops and actual configured-item placement retain this data. Ambient placement fills only available unoccupied sides. Slab merging retains the installed slab's complete snapshot, including dormant fields that the resulting cube does not expose.

Native bucket interaction retains the reference permission checks, strict one-bucket simulation/execution, gaseous-fluid rejection, inventory handling and sounds. It does not advertise an automation fluid capability that the reference block never provided. Native auxiliary light stores fluid luminosity so lighting workers do not query block entities.

Minecraft 1.21.1 lacks the legacy block material-intersection hooks. Narrow entity scan/eye hooks therefore merge contained-volume results at the engine's fluid-update stage, before water, lava, drowning and movement consumers run. Client terrain rendering clips adjacent ordinary-fluid faces against published compartment contacts. These hooks require actual body/eye, bucket, light, save/reload and boundary-render checks; a registered block or successful compile does not establish contained-fluid parity.
