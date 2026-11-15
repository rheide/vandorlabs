# Industrial Table geometry

Each Industrial Table is one native world block without a block entity. Four horizontal facings, normal/inverted placement and two neighboring connection bits produce 32 states. Only loaded tables with matching facing and inversion suppress an end strut.

`IndustrialTableGeometry` supplies the exact authored collision plates and eight-strip diagonal approximations to both platform adapters. Inverted collision reflects the vertical coordinate. The authored inverted visual models also reflect their horizontal strut positions; this existing visual/collision distinction is intentional.

The native adapter reuses 32 immutable collision shapes and baked model variants. Render-region model data derives connections from its supplied snapshot. Chunk availability changes queue bounded section refreshes using section palettes, without a table ticker or repeated block scans. The inventory model retains visible GUI padding.
