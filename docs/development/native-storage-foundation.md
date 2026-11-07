# Programmable Storage foundation

Programmable Storage is an independent 27-slot inventory. Adjacent blocks do not merge. It exposes the same inventory through the item-handler capability on every face, uses the vanilla chest menu and comparator calculation, and emits no block light. Its Source subclass suppresses inherited deferred redstone-channel behavior; shared Screen defaults and services must not be installed as Storage behavior.

## Released behavior reference

`exportStorageSourceReference` invokes the actual Source block, tile, item, inventory wrapper, chest transfer, configured pick/drop, appearance-copy and Configurizer callbacks. The bounded reference contains 39 typed/repeated snapshot reads, 180 inventory operations, 36 automation profiles, 252 comparator profiles, 192 actual configured ItemBlock placements, 48 ordinary block activation profiles, eight Configurizer event callbacks, 32 metadata decodes and fourteen Storage artwork sets. The reference SHA-256 is `02a04c006aa4211dff586fa6fcaf8dae05f5d2eefbeb8cab3291feb265938fe5`.

These are actual callback and vanilla implementation observations in memory worlds, not live player packets. The test initializes the real Forge item-handler capability. Its Configurizer event subclass supplies the declared cancelability normally installed by Forge transformation; the actual handler performs the routing.

A fresh Source factory selects Cabinet at choice 295. Reading an empty or incorrectly typed old housing snapshot first inserts that default, then applies the unversioned palette migration, producing 294. Actual item placement merges settings into a freshly saved, versioned tile and therefore retains 295 when the item does not specify a housing. Factory defaults and old-snapshot decoding are intentionally separate.

Missing or incorrectly typed `Items` does not clear an existing inventory. A present list clears it before loading; a list with the wrong element type therefore leaves it empty. Duplicate slot records use the later entry. Invalid slots and unknown item IDs are discarded by the released vanilla decoder. The native ownership boundary must retain and refuse unsupported inventory snapshots rather than silently losing their bytes.

Configured drops, pick-block and copied item settings exclude `Items`. Breaking the block ejects the inventory separately and empties its old slots. Applying appearance settings preserves the target inventory. A disabled face-override source also preserves the target's stored face choices. Storage inheritance uses each set's matching front, side and top artwork; explicit face overrides use the selected front artwork.

Ordinary main-hand activation opens inventory. Creative sneak activation and the Configurizer use the shared material configuration path. Offhand activation is refused. Inventory usability requires the installed tile identity and distance at most eight blocks. The native adapter will reuse existing housing settings, material picker and configuration authorization, with a separate inventory codec and no public registry hooks until private ownership and saved-world checks pass.

## Inactive native settings

`NativeStorageSettings` composes the existing `HousingSettings` and material catalog, without installing Screen display defaults or behavior. Its factory and raw-snapshot readers preserve the distinct versioned and legacy default paths. Item settings first merge into a fresh versioned housing save, matching the actual ItemBlock observations. The saved-settings wrapper owns a defensive original copy; appearance edits preserve `Items` and opaque sibling fields.

`testNativeStorageSettings` compares all 39 snapshot and 192 placement cases against the frozen Source reference and checks defensive retention. It passes 1,812 assertions. This is settings parity only: inventory codec, private host, menu, ownership recovery and rendered acceptance remain separate prerequisites for public registration.
