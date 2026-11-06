# Ordinary Door trace compatibility

The inactive native Door geometry adapter preserves the released Forge selection ray behavior. It traces the selection leaf even when an open sliding door has no collision box. It then considers each configured control panel in authored order. A panel hit on the control-facing side replaces the current hit even when farther from the ray origin; other panel faces replace only a strictly farther current hit. Equal distances retain the earlier candidate.

`NativeDoorGeometry.trace` accepts coordinates relative to the queried half, resolved facing/hinge/pair state, and the lower-host settings. An absent lower half uses the block prototype outline and disables panel tracing. The world adapter resolves each half before calling this method and translates the returned coordinates back to the queried block position. `LegacyBoxTrace` retains the 1.12 plane order, inclusive edge tests, inside-to-exit hits and squared near-parallel cutoff. Modern `AABB.clip` is not interchangeable with this contract.

`exportDoorRayReference` records actual `BlockConfigurableSpaceDoor.collisionRayTrace` results and asserts the released result digest. `testNativeDoorRays` compares the native adapter against those records, including both halves, cardinal facings, motion/hinge/frame/depth combinations, paired doors, panel modes, security padding and stale states after lower removal. Targeted rays prove the farther control-face priority. The Forge exporter also compares the scalar kernel with the actual legacy AABB method on deterministic arbitrary boxes and segments.

These are focused geometry checks. Door registration, world interaction routing, authored mesh emission and rendered acceptance remain separate requirements.

## Mesh emission boundary

`DoorMeshTransform` preserves the released CPU batch arithmetic and keeps complete caller-owned vertex records intact. The native `NativeDoorMesh` backend retains immutable authored records, applies live motion without rebuilding the model, preserves diffuse groups, and emits body or pane vertices into the caller's selected render layer. Native model preparation can reuse the authored float correction when element data is supplied. Full-height artwork uses the padded sprite's content UV descriptor. Each bake owns a cache bounded by retained bytes; resource reload replaces or clears the bake.

`DoorControlPadMesh` emits the six numeric-pad faces used by both Forge's regular renderer and its opaque batch. Far-edge placement changes the geometry without shifting the wall artwork's authored UV region. Enlarged pads retain their established vertical centering. Native mirrored rear pads and security-reader contents still require the corresponding world renderer transforms.

`testDoorMeshTransform` freezes the actual Forge CPU batch records across 480 poses. `testDoorControlPadMesh` freezes actual numeric-pad emission across all eight motion/depth/hand combinations. `testNativeDoorMesh` checks native consumer positions, colors, UVs, light, overlay, normals, diffuse grouping and immutable source records. These checks do not establish final world rendering, material overrides, X-panel clipping, reader contents or shader-pack acceptance.

The authored Door atlas also retains all eighteen company artworks at their standard full-height resolution. Their full-content UV descriptors serve both the world renderer and the picker. Company mirroring, base reversal and light-panel behavior come from generated source metadata rather than display labels. The standard Forge resource pack and its legacy PNG aliases provide the pixel reference; the pre-existing source hashes remain unchanged. `exportDoorPixelsReference` also compares every built-in artwork policy with the released catalog helpers.

Native baked colors use packed ABGR records. Door mesh emission converts those bytes to explicit RGBA consumer components; the consumer's packed integer overload instead expects ARGB. The focused `test_door_color.py` check draws asymmetric blue/orange source records into the actual GPU target and verifies the resulting channel order. Default white artwork alone cannot establish this contract.
