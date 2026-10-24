# Vandor Labs 2.5

Version 2.5 fixes a dedicated-server compatibility crash when retracting modular
landing gear in Hover mode with Thaumic Augmentation installed.

Carried tiles live in a read-only simulation world. Previously, saving a changed
gear target could allocate a synthetic chunk and invoke global chunk capability
handlers. Thaumic Augmentation interpreted the remote simulation as a client world
and attempted to load a client-only class on the dedicated server.

Dirty tile updates now use the existing vehicle snapshot queue. The same handling
protects carried controls, seats, screens and other tiles. Replaced tile instances
cannot dirty the current snapshot, and comparator updates cannot call foreign
blocks inside the simulation. Solidity and opacity queries use captured blocks;
light and sky readers sample loaded parent-world positions.
Loaded-chunk lookups from global collision handlers also remain non-allocating,
including canopy discovery during a saved vehicle's gear movement.

Existing vehicles retain their saved configuration. Install 2.5 on the server and
clients. See [server compatibility](../server-compatibility.md) for the regression
checks and physical-server test options, and the [changelog](../../CHANGELOG.md)
for the complete changes.
