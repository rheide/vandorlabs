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

## Ordinary world renderer

`NativeDoorRenderer` consumes one immutable lower-owner visual snapshot. It renders the authored fixed part, moving leaf and configured pane in separate body/pane layers, followed by the fixed numeric or shared credential-reader panels. Rear panels retain the released mirror transform. Frame/interior artwork, selected leaf materials, glass palette/opacity, company mirroring and faint company glazing follow the released adapters. Standard authored geometry receives the source float correction; an active resource pack with different element data retains its own baked geometry.

The renderer observes the saved OPEN state through the shared local `DoorAnimation`: nine visual ticks for an ordinary Door and twelve for the later Large adapter. Its pause-aware clock survives tile/snapshot replacement by world and owner position. Each world retains at most 1,024 motion owners and clears them on unload. Render-only interpolation does not change authoritative collision endpoints. Recovery owners remain visible, and the upper half does not draw a second body.

Each resource generation owns byte-bounded source, material-part and visual-snapshot caches. Live motion reuses immutable meshes; render layers and scratch buffers are reused. A completed model bake publishes the new renderer generation together. Contained fluids remain the chunk model's responsibility. Large and X-panel orchestration are separate from this ordinary adapter.

`testNativeDoorVisualMotion` checks the local timelines and owner lifetime. The title-only `test_door_render.py` fixture draws twelve asserted cases through the production orchestration, compares numeric panels with an absent-panel control, compares a fresh rejection glyph with its nonrejected reader, checks visible recovery housing and verifies that changing the pose does not rebuild material meshes. It retains a rendered image. Actual placed-block lifecycle, interaction and shader-pack acceptance remain distinct checks.

Late model-bake completion cannot replace a newer published generation. Static resource-pack element changes bypass canonical float correction. A dynamic external model that cannot be frozen falls back to this family's authored geometry for the current generation, retaining visible ownership rather than failing each render frame. The focused title fixture exercises that fallback in addition to its ordinary material cases.

The Large draw path reuses the ordinary bake, material mapping, layer emission and
reader clock. Each root emits two paired leaves at scale 1.5; member tiles do not
emit another body. X panels are clipped before the per-frame transform, including
the diagonal opaque caps. `exportDoorXMeshReference` captures the released Forge
`StaticSurfaceMesh.xPanels` vertex buffers; `testNativeDoorXMesh` compares native
positions, UVs and normal bytes exactly for both hands, inset variants and cap
policies. The native draw uses the released GL path's normalized X travel; the
separate legacy opaque fast path's progress arithmetic is unchanged.
