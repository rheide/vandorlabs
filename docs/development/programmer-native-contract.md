# Native Card / Chip Programmer contract

The canonical native candidate registers `vandorlabs:data_chip_programmer` for its block, item, block entity and menu. It supports the standalone non-Vehicle workflows through the production renderer, item model, dedicated dialog and authenticated command transport. [Construction steps](../card-chip-programmer-neoforge.md) include actual native player captures. Vehicle operations, previews and mutation remain unavailable; imported Vehicle ownership is retained intact or refused.

## Identity, placement and interaction

Four cells occupy a two-wide, two-high, one-deep footprint. The original horizontal facing and part occupy the same four metadata bits; part zero is the sole inventory and rendering owner. Physics and ordinary selection retain the actual full-cube shape identity. Authored ray access is separate: the shared immutable 216-triangle kernel preserves nearest hits, socket groups, normal selection and per-cell clipping.

One plain item stages four loaded, editable, replaceable cells and a sole empty root before source consumption and neighbor publication. Callbacks cannot mutate inventory during staging. Writes roll back in reverse order. If an available root survives rollback, it becomes an inert complete recovery snapshot and the source stays retained. If the factory is unavailable and an exact bare root survives, it consumes the corresponding plain source item, preventing later mining or factory recovery from minting a third usable block. Nested owned item components and item tile/recovery envelopes refuse placement unchanged.

Source hardness and final explosion resistance are 3.5, with Stone sound and wooden-pick harvest. Only the pickaxe tag is added; no stone tier or new crafting recipe is invented. Actual Source mining distinguishes the root from members: wrong-tool or Creative root mining has no plain drop, while member teardown historically cascades a plain root drop. All cases release retained credentials once and remove the footprint.

Decorative connections preserve the legacy SOLID face result while ordinary side support remains false. A Programmer-only stateless bridge covers vanilla fences, walls, bars and panes without querying owners or changing other families.

The authored socket/body precedence is shared by ordinary and main-hand Configurizer interaction, including sneak use. Socket insertion consumes one real item even in Creative; an empty hand returns the sole retained item. Journal page controls remain page controls. Server dispatch checks installed identity, current level/thread, permissions, reach and nearer-block occlusion.

## Inventory, commands and saved ownership

Master, Input and Output retain indices 0/1/2 and maximum size one. Program consumes Input into one Output and preserves Master. Copy requires matching base kinds, copies committed Input data into the occupied target, and preserves Input/Master/draft. Native Copy refuses opaque item capabilities or nested owned inventories on either side; Program retains its consume-and-preserve path for complete imported credentials. Vanilla menus and hoppers use the real Container contract, with no synthetic item capability.

The bounded command preserves Draft/Program/Copy operation indices 0/1/2; Vehicle 3/4 remain reserved and refused. It validates the current menu/window, exact installed owner, loaded level, reach and permissions before mutation. Client menu slots use a separate authorized shadow, and public block data never contains a physical stack, UUID or arbitrary retained tree.

Saved staging uses the shared item/credential decoder, bounded immutable draft and original fields. Platform components and attachments are validated before installation; successful serialization uses their live state. Failed loads remain inert and preserve the complete original snapshot. A failed reread of an already owned tile quarantines the previous owner together with each untouched rejected input. Repeated failures are idempotent. Removal produces one non-placeable recovery capsule, without separately materializing its opaque credentials.

## Timers, projection and rendering

Writing lasts twenty authoritative ticks and Complete lasts sixty. The counter timeline survives save/restart, game-time corrections, actual frozen server ticks and low TPS. Repeated or older public updates cannot restart a phase or install ownership. The writing image remains the released global animated atlas sprite rather than a per-owner server-time interpolation.

Content edits coalesce into one full bounded projection; page and phase transitions use compact updates. Actual vanilla packet round trips measure 8,547 to 139 bytes for a maximum authored page update and 4,429 to 139 for timer completion, with unchanged message count. Prepared JournalText is shared across phase/page updates. The same bounded journal canvas serves Screens and Programmer overlays; its canonical cached keys eliminate the measured equal-view iterator allocation.

One immutable successful atlas bake serves the public item and placed renderer. The twenty authored state parts, complete item OBJ and six original sprites retain their resource identities and animation metadata. Canonical float correction is applied only to the unchanged active JSON/OBJ/MTL graph; resource replacements and dynamic model payloads survive. Item normalization and GUI fit share the exact Java 8 Source policy. The 108-quad item retains 24,448 bytes and warmed immutable quad/pass submission allocates zero bytes in 10,000 calls.

Dock rendering uses bounded kind/company scalars and fourteen cached stock credential templates, with twenty-four cached Source-authored poses. Journal text/layout is prepared outside drawing. Independent panel-material changes, complete resource reload and world unload retire glyph caches. The sole root uses Source-sized bounds and bounded offscreen dispatch; actual upper-only visibility is tested with the owner section outside the frustum and a disabled-renderer negative.

## Focused evidence

| Actual Source contract | Coverage or frozen digest |
| --- | --- |
| Programming policy/results | 21,904 decisions and 392 results |
| Saved-state staging | 3,528 loads; `c9fab72182445314cc7f6999ac818f586df26e733ad50ebdfef59335c5a19de3` |
| Full artwork and item | 512 world profiles, 239,616 vertices, 108 item quads; `9eaca2d0f89efb82c389ad8ceb352e35ca5e7dce39892142346de742d0203621` |
| World ray behavior | 6,936 rays replayed in three native modes; separate 27,744-ray kernel corpus |
| Wire commands | 286 profiles, 153 supported native round trips; `1ab9e097109d822c9382f571957444293258799e27c41053bb881045a1b8f69a` |
| Journal selection/plane | 288 views and 1,120 rays; `ae454c4f674210607b32c269c552545ea123e2b62bbb767503694c1f4c98fbc4` |
| Actual dock GL matrices | 24 profiles; `1ad003dc329dd31e4d4ec75a1c59d3e43c7d911aab5553df78cf4b0801cf613c` |
| Actual harvest engine | 64 cases; `cc2bd5a3955dd868fda4292bd02e2464bbc47111d4941baa5efb64dbecfb5884` |
| Actual decorative contacts | 384 cases; `dbbc7bd78029ce41ef9c42414202bd0b35de0d09644d7d1afa3fe72a1feb9efc` |

Focused native evidence includes nine partial rollback layouts with retry and independent-process restart; all sixty-four canonical harvest cases and 384 contacts; canonical Survival/Creative placement in four facings; complete opaque item preservation through Program/save/restart/mining; and twenty-four isolated rendered dock pairs with zero changed pixels outside their authored pad projections. Baseline/reload/restored item images are identical, replacement geometry reaches the GPU, and all sixteen icon draws retain padding.

The actual canonical player path covers placement, sockets, Program/Copy, quick-move/throw, complete returns, page/occlusion checks and ordinary/sneak Configurizer access. It asserts the production menu/BER/item binding and a shared new atlas generation after reload, with a disabled-renderer negative. Dedicated timing matches all eighty-six Source counter states; frozen restart then 5 TPS completion preserves ownership and layout. These are focused family checks. The full release gate, optional-mod breadth, hardware FPS/shader acceptance and Vehicle integration remain deferred.
