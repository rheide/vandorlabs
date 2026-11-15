# Shared track policies

Track topology, mounting rules, appearance packing and authored part selection live in the Java 8 portable source set. Both platform adapters must discover only loaded cells before calling `TrackTopology`; a rejected component must never become a partial valid assembly.

`TrackVisualState` keeps the released appearance bit layout. Its legacy reader masks the same fields as the original saved-data reader; new native data uses the strict reader to reject unknown bits and noncanonical lower-side masks. `TrackMeshPolicy` streams references to 220 immutable part templates, preserving authored part ordering, quarter turns and the replacement of the standalone socket on an arm. Platform resource identifiers remain outside the portable policy.

The Source adapter retains its public `TrackAppearance` and `TrackParts` interfaces. Resource wrappers are allocated once per template; mesh selection does not construct duplicate portable part lists or per-part descriptors. Native rendering must invalidate atlas-dependent meshes when resources reload.

`testTrackMeshPolicy` compares all 248,832 valid style, facing, belt, mounting, side, bridge and lower-side combinations against an independent selection oracle. The isolated live track focus also exercises real item placement, saved styles, arm motion, emitted geometry and the compact dialog error footer.
