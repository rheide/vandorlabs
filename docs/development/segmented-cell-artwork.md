# Segmented Door individual artwork

A segment is one existing cell in the segment/window grid. Each cell already owns its local Window, Shape and Glass choices; individual artwork follows that same ownership rather than defining a second coordinate system.

The existing `SegmentedTexture` remains the shared default. An optional integer `SegmentedCellTexture` selects a cell override. Absence inherits the shared default; an explicit `-1` selects the existing default artwork and is distinct from inheritance. Material validity uses the existing Segmented Door catalog/custom-material predicate. No sentinel is reserved from the custom material namespace. Source world loads retain an unsupported or malformed cell-artwork field unchanged and draw inherited artwork; explicit local/reset/all edits replace that field. Configured-item and command decoders refuse unsupported values. Native failed-load snapshots retain their existing inert recovery behavior.

Existing saves without this field keep their artwork. The existing 15-byte settings body and legacy 14-byte decode remain unchanged. The new cell-artwork operation is appended after operations 0–5 and carries one bounded choice for the currently authorized menu cell, rather than accepting an arbitrary coordinate list. Saved/projected grids remain bounded by the existing 32×32 topology.

Moving columns need immutable effective artwork per row. Settled terrain consumes the same per-cell effective choice and the existing global column/row UV phase. Window geometry continues to use its existing connected Window layout; selecting a different image does not rescale the physical panel or change the aperture grouping.

Configured picking, Programmer settings, Duplifier capture/application and copied-item recipes must retain the optional local override whenever they retain existing Segmented Door configuration. Unrelated settings edits retain local choices. An explicit All segments artwork action intentionally clears overrides, preserving the existing whole-door application behavior; resetting This segment restores inheritance. Dedicated configuration and Configurizer use continue through the same menu and authorization path.

The portable scalar checks verify inherited versus explicit default choices and invalid-material refusal on Java 8 and Java 21.

## Transport bounds

Operations 0–5 retain their original encodings. Operation 6 sets the current menu cell override and uses 12 + 4N bytes; operation 7 resets it to inheritance and uses 8 + 4N bytes, for N channels (0–64). The existing combined settings command uses 27 + 4N bytes. No grid-sized allocation is accepted by the new operations; decoders reject extra bytes and validate the single material against the existing catalog predicate. Both versions round-trip the appended commands and retain the original 4,672-profile settings digest.

## Focused acceptance

Both versions retain the original 4,672-profile settings digest. Source tile checks cover absent/default choices, configured items, save/load, inheritance reset, malformed types and bounded command round trips. The actual Source player focus passes the dedicated dialog, Configurizer path, local custom-inventory artwork packet, copied local override and configured pick, 13,077 runtime assertions, the existing 11,520 rendered profiles and 96 additional mixed-cell cache/UV profiles across facings, travel directions and solid/window rows.

Native title widgets pass 306 checks covering both scope labels, local/global/reset operations, reverse cycling, invalid drafts, picker scrolling/dragging, resize and reload. Canonical installed-menu tests verify distinct row choices, reset locality, whole-door replacement, per-row immutable publication, configured picking, actual copied-world commit and copied-item conversion. A fresh process retains the distinct choices after eight mechanism configurations and save/restart. All previous 72 canonical Source layouts remain covered. Actual Survival placement of a configured pick consumes exactly one of two items and retains the local override through a separate-process restart.

Actual native moving/window and settled-terrain consumers produce zero differing pixels with two different tall artworks in one column, before and after resource reload. The incorrect-row-phase control changes 33,550 pixels. Each resource generation performs 10,000 warm mixed-column lookups with zero allocated bytes and zero new meshes; this is a cache-lookup measurement, not complete-frame allocation or hardware FPS acceptance. Images and both dialog layouts were inspected. The connected native player fixture passes 14,353 checks covering real local Frame selection and unchanged peer inheritance, Configurizer and ordinary menus, eight mechanisms, lag/stale/moved-bucket use, culling, configured icons and mining. Capture waits until the reload overlay has retired and eight fresh frames have rendered; the final reloaded-world image was inspected. Its disabled-renderer control differs by 619,948 pixels.

Source uniform columns retain the existing compact fast key. Mixed columns include every effective cell choice in their exact cache guards. The existing row-validation pass determines whether artwork is uniform, and feature-cache checks reuse one member lookup per row. Retained template/column estimates reserve an additional 144 bytes each for the maximum 32-entry artwork array within the existing cache budgets. Native immutable per-row choices remain bounded by height 32 and share the existing byte-bounded prepared column caches. Resource reload and world unload use the existing retirement paths.

Generic configured-item and Duplifier projections flow through the existing Segmented Door configuration envelope. The standalone Card/Chip Programmer has no Segmented Door texture-specific schema; its generic retained credential/settings envelopes are unchanged. No Vehicle operation is added.

The reached Dynmap suite passes 1,464,527 assertions, and dedicated-server linkage passes 1,398 shared classes and 132,678 references.

These checks do not constitute a release gate, production-server action or shader-pack/hardware performance claim.
