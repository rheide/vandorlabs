# Shared Surface layout

Programmable Surfaces, Turntable Controllers and Copy Controllers use the same planar coordinate policy. The engine-neutral `SurfaceLayout` validates complete squares or rectangles, with each axis limited to 32 cells. Its six face bases match the existing saved Surface normal ordinals. `SquareLayout` remains the Forge adapter and retains its public methods, sorted cell order and NBT format.

`SurfaceDiscovery` traverses four neighbors in the Surface plane. It reads only loaded cells and boundaries, checks the tile and facing, and applies the caller's membership predicate. Drawing and Turntable discovery preserve artwork boundaries. Copy Controller ownership can span different artwork, while its input and output remain disjoint. Missing cells, conflicting normals and unloaded boundaries cannot silently become a smaller valid pad.

Discovery uses each engine's immutable position objects directly. The Forge adapter caches the current subject's block state and tile during the read sequence; extracting the policy does not add a position conversion or repeated tile read for each predicate. Geometry repair remains deferred and coalesced by the existing Surface lifecycle.

`SurfaceUvMesh` contains the atlas-independent clipping algorithm for Stretch, Tile and Fit. The Forge renderer retains its existing atlas-generation cache, opaque Fit backing and triangle emission order. The same policy can build an individual cell's slice of a joined drawing without sampling outside its atlas sprite. Whole-face and cell-slice coverage use identical dimensions, aspect ratio and layout IDs.

`SurfaceArtwork` supplies the existing authored texture IDs, labels and filenames. Material-catalog resolution stays in the engine adapter. This extraction does not change selected texture IDs, Surface claims, packet operations, saved layouts or controller settings.
