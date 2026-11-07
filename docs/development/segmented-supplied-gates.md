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

Vertical connectors are the next adapter step. They may extend only matching upright cross-sections toward a shared boundary, while retaining every authored bar/rail without rescaling. They must not bridge different styles, depth planes, facing or incompatible section poses. Connectors and transformed models stay within the existing two-pixel cell envelope. Precise model rays/collision must not turn the hover outline into hundreds of individual box wires; its displayed bounds remain cheap and bounded.

## Integration boundaries

The existing dark/light panel IDs and default `-1` remain stable. Their visible names/category will become Dark Segmented Panel and Light Segmented Panel under Doors/Segmented. Existing general-material eligibility must be preserved despite that category change. The duplicate default dark row may be hidden only in the Segmented-specific picker after actual artwork equivalence is established.

Model-backed choices will reuse per-cell artwork, existing section transforms, model loading and bake correction/content helpers. Static local models should share style/join-mask meshes rather than duplicate them for every door dimension or animation sample. Moving, settled terrain, alpha, exact selection, byte-bounded caches, resource reload, palette/browser/Dynmap mappings and real player checks are still pending; importing the resources and passing join/model references does not establish those behaviours.
