# Multi-version code boundaries

The code under `src/portable/java` is the shared implementation, with its tests
under `src/portableTest/java`. It must compile
with a plain Java 8 compiler and must not import Minecraft, Forge, LWJGL, or a
version-specific mapping name. The 1.12.2 project includes that source root in
its main source set; future 1.16.5 and current-version projects should include
the same directory rather than copy it.

Code under `src/main/java` is the 1.12.2 adapter. It may translate portable
values to `BlockPos`, `EnumFacing`, `AxisAlignedBB`, NBT, tessellator calls,
registries, packets, and world operations. Keep those translations shallow:
choose values from the game API, invoke portable logic, then apply the result
through the game API.

## Extracted contracts

- `animation`: reversible door motion, screen playback rules and compact
  animation decoding. Renderers supply time and upload the resulting image.
- `ramp`: controller selection, motion, collision/render boxes and world-height
  bounds. Adapters translate directions and boxes at their boundaries.
- `render`: connected-texture decisions and renderer-neutral screen, door and
  cuboid geometry. Adapters only submit vertices and bind textures.
- `persistence`: stable field names and data versions. Each Minecraft version
  owns its NBT/tag calls through `PrimitiveData`, while shared codecs retain the
  same validation and legacy defaults. Ramp saves write explicit X/Y/Z values
  and a string UUID alongside the 1.12 packed-long/legacy UUID fields; new ports
  should prefer the explicit forms and keep the old fields as read fallbacks.
  Moving-ramp saves likewise include a canonical `namespace:block[property=value]`
  reference beside Forge 1.12's block-state NBT. Version adapters should map
  renamed vanilla ids/properties at that reference boundary.

## Rules for new features

1. Put state transitions, geometry, selection algorithms and serialized field
   identities in the portable source root.
2. Pass primitives, enums and small value objects across the boundary. Do not
   pass a world, block state, tile/block entity, renderer, packet or registry.
3. Treat metadata ordinals and mapping names as adapter details. Use semantic
   identities such as `DoorLeaf.LEFT` in shared code.
4. Keep saves additive. Read missing fields as legacy defaults, write a schema
   version, and migrate at the adapter boundary when a representation changes.
5. Test portable behavior once. For each supported game version, manually smoke
   test registration, placement, rendering, interaction, networking and loading
   an old save—the parts that necessarily remain version-specific.

When the multi-project build is introduced, one root build task should depend on
all version-specific `build` tasks. Normal feature work should remain in the
1.12.2 adapter plus the shared source root; port adapters can then be updated in
the same checkout without switching branches or Gradle configurations.
