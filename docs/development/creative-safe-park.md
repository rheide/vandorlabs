# Creative Safe Park

The requested Forge feature aligns the vehicle and articulated sections, resolves supported arm poses and parks the retained material in a nearby loaded, flat area. It must use the existing durable Vehicle transfer rather than teleporting the live craft into place or removing blocks during search. Native Vehicle Mode remains outside the current conversion scope.

The first shared foundation is a bounded grid-candidate iterator. It orders complete XYZ candidates by distance from the preferred origin and caches loaded-column admission only within one search. Its heap retains one entry per horizontal offset; it does not allocate the complete XYZ search volume. A caller-supplied budget limits new-column probes per slice. The caller must still check terrain, permissions, entities, full physical clearance and loaded chunks again before committing.

Java 8 and 21 checks cover the nearest clear placement beside a blocked column, skipping cold columns, bounded cold exhaustion, complete ordered coverage, deterministic ties, large height scores and allocation bounds. This helper does not activate a button or establish safe ownership transfer. Remaining work includes the actual flat-area adapter, immutable aligned parking snapshot, Creative-only authenticated dialog action, durable prepared-plan publication, arm integration and focused player/restart acceptance.
