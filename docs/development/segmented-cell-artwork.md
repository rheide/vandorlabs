# Segmented Door individual artwork

A segment is one existing cell in the segment/window grid. Each cell already owns its local Window, Shape and Glass choices; individual artwork follows that same ownership rather than defining a second coordinate system.

The existing `SegmentedTexture` remains the shared default. An optional integer `SegmentedCellTexture` selects a cell override. Absence inherits the shared default; an explicit `-1` selects the existing default artwork and is distinct from inheritance. Material validity uses the existing Segmented Door catalog/custom-material predicate. No sentinel is reserved from the custom material namespace.

Existing saves without this field keep their artwork. The existing 15-byte settings body and legacy 14-byte decode remain unchanged. The new cell-artwork operation is appended after operations 0–5 and carries one bounded choice for the currently authorized menu cell, rather than accepting an arbitrary coordinate list. Saved/projected grids remain bounded by the existing 32×32 topology.

Moving columns need immutable effective artwork per row. Settled terrain consumes the same per-cell effective choice and the existing global column/row UV phase. Window geometry continues to use its existing connected Window layout; selecting a different image does not rescale the physical panel or change the aperture grouping.

Configured picking, Programmer settings, Duplifier capture/application and copied-item recipes must retain the optional local override whenever they retain existing Segmented Door configuration. Shared default changes must not discard explicit local choices. Dedicated configuration and Configurizer use continue through the same menu and authorization path.

The first portable scalar checkpoint verifies inherited versus explicit default choices and invalid-material refusal on Java 8 and Java 21. Host, transport, rendering and player acceptance are pending; this document does not claim those paths are implemented.
