# Supplied Segmented Door artwork and gates

The imported resources are inactive foundations. They add no blocks, item registrations, catalog rows or player interaction paths yet. The existing 827-entry additional catalog and its saved order remain unchanged.

## Resources and Source reference

The pack contributes three 128×128 tiles, seven 128×128 gate material images and twelve block models: Picket, Industrial and Lattice, each with Single, Left, Middle and Right variants. Only texture namespace references change during import. Original element ordering, rotations, UVs and intentionally omitted faces are preserved. The import checker pins all original 827 IDs and the imported bytes, and can run without the external pack. The supplied blockstates and item registrations are not installed, because these are choices for existing Segmented Door cells.

Actual Forge model parsing and FaceBakery produce 48 orientation records, 3,040 quads and 12,160 vertices. The frozen full-record SHA-256 is `2c2a5246aeb2894ea2237f75f287f0a162047fd615ba2f60b8a54325d2e6d605`. Records include authored/material faces, all packed vertex words, culling and diffuse flags. This is the independent reference before either runtime gate adapter is introduced.

The posed models fit X=0–16, Y=0–15.5 and Z=7–9. Lattice element coordinates extend beyond that box before their authored rotations; the transformed geometry does not overhang. No whole-model stretching is proposed.

| Tile | Opaque pixels | Fractional-alpha pixels | Fully clear pixels |
| --- | ---: | ---: | ---: |
| Raised Black Panels | 16,384 | 0 | 0 |
| Ribbed Blue Steel | 16,384 | 0 | 0 |
| Spiral Aluminum Glazing | 9,247 | 7,137 | 0 |

The glazing tile requires a translucent image surface; its appearance does not itself create physical holes. Gate openings come from absent model geometry, not alpha pixels.

## Prepared join policy

`SegmentedGateJoin` accepts four direct prepared neighbours. It stores no world or tile references. Cells must match gate style, facing, authored depth position, travel direction, straight/perpendicular mode and speed. Active motion additionally requires the same motion identity. Their owner-relative unit plane bases and shared edges must coincide. Missing, differently posed or incompatible cells refuse a join.

Horizontal bits choose the supplied Single/Left/Middle/Right variants in authored local-X coordinates. All sixteen four-neighbour masks, mixed styles, positions, facings, mechanisms and separated moving planes are checked on Java 8 and Java 21: 13,824 profiles and 46,947 assertions with identical results.

`SegmentedGateShape` retains immutable authored cuboids, rotations and visible-face masks. Its caller-owned ray scratch avoids allocating per element. Vertical connectors extend only matching upright cross-sections to a shared boundary after the prepared join policy admits the neighbour. Spear tips retain their narrow footprint, original bars and rails are never rescaled, and already touching posts need no extension. Horizontal variant pairs share connector descriptions instead of duplicating whole meshes for every door dimension or pose.

The actual Forge-baked vertices agree with the shared corners for all twelve models. Independent face-plane tests cover 24,576 front/back rays, including holes and nearest faces. All 96 current/neighbour variant-and-side contexts validate 256 connectors against the original geometry and shared boundary: 39,392 Source assertions. The separate Java 8/21 shape checks each pass 10,012 assertions. Connectors stay inside the two-pixel cell envelope. A fixed twelve-model server-safe loader now parses bounded canonical JSON without client APIs. It follows at most sixteen same-namespace parents, including the build’s deduplicated model parents, and precomputes the 96 connector-pair lists. Prepared queries reuse immutable model/list identities. Actual Forge models independently check every parsed corner, and all prepared connector lists retain their fixed identity: 40,904 combined assertions. Dedicated-server linkage passes with the loader included.

`SegmentedGateConnectorUV` extends the prepared upright faces with their authored UV gradient instead of fitting the complete texture onto each short extension. It clips and wraps at normalized sprite boundaries before atlas mapping, preventing neighbour-sprite sampling. Preparation is bounded to 128 texture cells per face and accepts only axis-aligned post faces inside the cell envelope. Every connector is exercised with its actual Forge-baked source faces: 83,592 combined geometry/UV assertions. Independent Java 8/21 checks also verify unscaled density, clipping area and wrapped bounds (10,062 assertions each). No per-frame subdivision is intended; these connector meshes will be prepared once per resource generation and horizontal variant pair. Render emission and physical adapters remain pending; these geometry checks do not establish player-path integration. Precise model rays/collision must not turn the hover outline into hundreds of individual box wires; its displayed bounds remain cheap and bounded.

## Source prepared mesh adapter

`SegmentedGateMeshes` prepares twelve authored base meshes and their fixed connector-pair meshes for one resource generation. It streams through the existing rigid section transform and active buffer format. All source packed positions, atlas UVs and quantized normals survive the conversion; geometry is not fitted or stretched. A replacement model keeps its own base geometry and receives no canonical connectors when its authored corners/faces differ. No world/tile references are retained.

The CPU adapter replay covers 12,160 independently baked vertices over all four facings, every neighbour mask and horizontal peer variant, connector envelope/UV bounds, a changed model, and a moved atlas generation: 277,849 checks. Retained bytes reserve the lazy packed mesh arrays as well as source floats: 2,120,960 bytes within a 4 MiB ceiling. A warmed 10,000-draw batch allocates zero bytes on the measuring thread. `SegmentedGateResources` loads one active-pack model graph with 64 KiB per file, 2 MiB aggregate, 64 models and sixteen parent levels. It registers resolved face images before stitching, then releases the parsed graph after primitive mesh preparation. Tests cover inherited build-generated parents, stream closure, graph retirement, cycles and oversized input. This is adapter evidence, not a substitute for actual placed rendering and resource reload after binding.

## Native resource and scalar adapter

The native generator copies the same twelve model JSONs and ten 128×128 images, retaining their namespace paths and bytes. These models are not yet added to active world meshes or selectable catalogs. `NativeSegmentedGateModels` is a thin Gson/resource adapter over the same immutable shape and connector policy. Its fixed models are replayed against all 48 actual Forge orientation records: 3,040 quads, 12,160 vertices and 16,786 checks, including stable prepared-list identities. The Source reference hash is checked before replay.

`NativeSegmentedGateMeshes` prepares the same base and connector structure using `NativeSegmentedMesh` and the active vertex consumer. It applies authored float correction only when the caller establishes canonical resource geometry. Changed geometry retains its original quads and receives no canonical connector extensions. Content-region UV remapping is applied once. The native CPU emitter replay uses the frozen actual Forge records as input, preserving their positions, UVs and quantized normals: 277,843 assertions, including every top-connector positive. It retains 768,768 bytes within its 4 MiB ceiling; warmed 10,000 draws allocate zero bytes and make zero model queries. Actual native model baking, rendered pixels and real resource reload remain separate pending checks.

## Integration boundaries

The existing dark/light panel IDs and default `-1` remain stable. Their visible names/category will become Dark Segmented Panel and Light Segmented Panel under Doors/Segmented. Existing general-material eligibility must be preserved despite that category change. The duplicate default dark row may be hidden only in the Segmented-specific picker after actual artwork equivalence is established.

Model-backed choices will reuse per-cell artwork, existing section transforms, model loading and bake correction/content helpers. Static local models should share style/join-mask meshes rather than duplicate them for every door dimension or animation sample. Moving, settled terrain, alpha, exact selection, byte-bounded caches, resource reload, palette/browser/Dynmap mappings and real player checks are still pending; importing the resources and passing join/model references does not establish those behaviours.
