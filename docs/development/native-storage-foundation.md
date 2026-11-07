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

## Item envelope staging

The separate `exportStorageItemSourceReference` records seventeen actual Source item envelopes and all 411 vanilla item IDs, including a filled shulker item produced by the released block's real break callback. That payload has no nested block-entity `id`; inferring the known vanilla container type from its item identity is necessary. Source's actual registered vanilla data fixer reports version 1343. The item reference SHA-256 is `99fb19ded8f6e18754d0acb2af238e0326db6d136ff596a310bbcec7b8c5548d`.

`NativeInventoryItems` stages vanilla items with the platform's real item-stack DataFixers and registry-aware codec. Its narrow container preflight checks known ownership envelopes before conversion. Unknown positive-count items, nonempty item-level or nested block-level `ForgeCaps`, and unknown nested inventory fields are refused without modifying the input. Opaque capability data is not traversed. Proven credential imports use the existing credential adapter. An empty count must not erase an opaque capability owner either. Tagged legacy mod items and non-container block/entity payloads require their own proven adapter. The Source vanilla-ID manifest is checked before DFU, so a nonexistent old ID cannot become a newly registered modern item. Known container children are staged first and removed from the parent's DFU input, then installed through the native container component; vanilla DFU therefore cannot reprocess a credential child or discard its opaque payload.

The dedicated `test_storage_codec.py` focus exercises damage, names, lore, enchantments through the real dynamic registry, written books, nested chest contents and the actual filled shulker drop. Unknown nested owners are refused. Bounds apply to nested item count and container depth; the helper retains no world or rendering cache. This stage does not yet publish an inventory or implement host placement/mining recovery.

The shared helper is `com.vandorlabs.neoforge.inventory.NativeInventoryItems`, also available to the inactive Programmer adapter. Its `legacy`, `modern` and `write` methods are world-free; optional shared budgets bound an entire staged inventory. The real dedicated codec focus passes 67 assertions across ten supported and seven refused Source envelopes plus adversarial native ownership/depth cases. Retained negative checks exposed zero-count capability erasure and acceptance of a modern-only vanilla ID before the corresponding guards were added. All disposable server runtimes are finalized through the shared retention helper.

## Complete slot staging

`NativeStorageInventory` returns a separate, fixed 27-slot result and never changes the previous owner during decoding. Native saves write schema 1, the current item data version and a complete `Items` list, including an explicit empty envelope. Future or incomplete native schemas are refused. Legacy missing or wrongly typed top-level `Items` preserves the previous slots; a present list replaces them, matching the frozen repeated-read cases. Unsupported positive-count out-of-range entries are retained through refusal rather than silently discarded.

The dedicated codec focus additionally passes all 33 supported snapshot reads and refuses six ownership-bearing snapshots, including a late-slot failure that must not publish an earlier decoded prefix. Native round trips cover every slot. The combined item and inventory codec checks pass 1,948 assertions; the runner requires both phase markers before retirement.
