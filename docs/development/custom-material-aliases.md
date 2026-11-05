# Custom block material identities

A saved custom material choice is the legacy CRC-32/ISO-HDLC of the UTF-8 string `registry-name@metadata`, masked to 29 bits and combined with `MaterialChoicePolicy.CUSTOM_BASE`. `CustomMaterialIdentity` preserves this calculation. Metadata normalization belongs to the platform caller: the legacy inventory path masks ordinary block metadata to four bits and uses zero for door items. Loading a saved choice must preserve its integer even when its source block is unavailable.

## Vanilla flattening manifest

`src/main/resources/assets/vandorlabs/data/legacy_vanilla_blocks.tsv` contains only Minecraft 1.12.2 vanilla **block** registry IDs and names, sorted by numeric ID, plus sixteen per-requested-metadata normalization slots. Each slot records `getMetaFromState(getStateFromMeta(requestedMetadata))`, or `-1` if the legacy state lookup rejects that metadata. Export also asserts that the normalized metadata reconstructs the exact original state. The vanilla registry has 4064 requested slots: 2263 normalize to another metadata value, 151 are unsupported and 3913 resolve. For example, stone metadata 7–15 becomes stone metadata 0; piston metadata 6 and wheat metadata 8 throw and remain unresolved. It is exported from the actual `Block.REGISTRY` after vanilla bootstrap, rather than inferred from item IDs or maintained as a handwritten flattening table. Item IDs differ: the wooden door block is 64, while its item is 324.

Regenerate with Java 8:

```sh
./gradlew exportLegacyVanillaBlockManifest --no-daemon
./gradlew testLegacyVanillaBlockManifest --no-daemon
```

The check is part of `check`. Exporting does not scan modded registries or establish modded numeric-ID compatibility.

## Native alias adapter

`LegacyVanillaMaterialAliases` resolves an original saved CRC through the original registry name and metadata to the actual normalized numeric vanilla state. The saved CRC still uses the originally requested metadata. Unsupported legacy slots remain unresolved, matching the legacy lookup rather than guessing a default. Minecraft's `BlockStateData.getTag(blockId << 4 | normalizedMetadata)` supplies the authoritative flattening state. This packing belongs to the data-fixer table; it is not the legacy `Block.getStateId` packing. The block-state data fixer advances that state from version 1451 to the running Minecraft version, including later changes such as `oak_bark` to `oak_wood`, `grass_path` to `dirt_path`, and `grass` to `short_grass`.

The adapter validates the manifest and metadata range before calling Minecraft's utility, whose default for unknown numeric IDs is AIR. Unknown saved IDs remain unresolved. A known non-AIR block unexpectedly mapping to AIR fails explicitly. The cache has at most 4096 fixed state slots, returns copied tags and retains no worlds, players or item stacks. No client API is involved. `testLegacyVanillaMaterialAliases` checks the actual data fixers, literal previous saved IDs, orientation properties, unavailable IDs and cache isolation.

This adapter is a prerequisite and is not connected to native material discovery or rendering. It does not convert worlds, palettes, tiles or items.

## Native sampling boundary

A new native block-item sample uses its current named block and default block-state properties. `CustomMaterialIdentity.flattenedBlockIdentifier(name)` hashes the fixed UTF-8 preimage `flattened-block:<current-registry-name>@0` with the same CRC mask and custom-material namespace. The prefix is version-independent and covered by literal saved-ID goldens. It must not choose an arbitrary inverse legacy metadata alias: flattening is many-to-one, and invalid or unused legacy metadata can map to defaults.

When a current name also existed in 1.12.2, its `name@0` saved ID already has a legacy interpretation. Before enabling a sampler, compare the actual legacy selected sprite with the native default-state selected sprite. Door materials require both lower and upper halves; other materials follow the existing component/particle selection policy. A facing difference alone does not establish either an artwork match or a mismatch. Preserve the legacy interpretation and explicitly handle or refuse genuine conflicts. Missing modded blocks retain their saved ID and fallback; this manifest does not supply a universal modded migration map.

## Actual sprite collision assessment

The narrow legacy `material-sprites` client focus exercises the real custom material lookup for all 254 vanilla block names at metadata zero, the loaded-client door-item reflection path and distinct door halves, then repeats after resource reload. The native smoke `test_material_sprites.py --legacy-reference <legacy-material-sprites.json> --xvfb` compares actual atlas sprite identities for the original alias state and the same-name current block's default state, including both door halves and another resource reload. This is a material assessment, not an FPS or complete rendering parity claim.

On Minecraft 1.21.1, 182 vanilla names overlap and seven select different artwork: Dispenser, Stone Slab, Stone Stairs, Pumpkin, Dropper, Purple Shulker Box and Structure Block. All six same-name door blocks retain matching lower/upper artwork. Consequently new native default-state samples use the separate, version-independent identity preimage domain above. Reusing `current-name@0` would change the appearance of existing saved IDs. The legacy aliases retain their original interpretation.


## Snapshot and collision boundary

The common native registry snapshot is initialized only after the enqueued common-setup boundary has finalized block and item registration. It includes the native cube and material block, preserves all 4064 original vanilla CRCs as reserved identities, and resolves the 3913 supported legacy states using the exported normalization. Every current named block receives a prospective new-domain ID; collisions against reserved old IDs or another current block refuse the ambiguous new sample without replacing old aliases. The tested registry has 1060 vanilla blocks and two native blocks with no known collisions.

Distinct preimage domains do not form a mathematically disjoint CRC space. A raw unavailable legacy integer cannot prove its original modded preimage, so arbitrary unknown-origin discrimination is not claimed. Saved integers remain unchanged. Explicit named provenance and optional component-renderer adapters require separate designs.

Client bake snapshots capture selected lower/upper sprites from that generation's actual models. Publication for picker availability occurs only after successful baking. World and item models resolve stored IDs against their own immutable material maps, including when the current GUI catalog no longer advertises an ID. Registry/DFU work occurs at initialization and sprite resolution at bake time; ordinary rendering does not copy tags or run data fixers. Configured cube samples read their housing choice only on a user click, including the supported nested `CUSTOM_DATA.BlockEntityTag` form. Reload keeps an inline grid and scroll when geometry is unchanged; resizing rebuilds the catalog while retaining the selected material and settings.
