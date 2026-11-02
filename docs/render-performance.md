# Dense-build rendering in 2.8

Dense builds can spend substantial client time submitting many small tile renders and searching unrelated loaded objects. Version 2.8 reduces that work while keeping collision and saved state on the server.

## Garage comparison

The focused comparison loads MalisisDoors 8.0.0 with MalisisCore 7.0.0 alongside Vandor Labs in an isolated Forge client. Each garage contains nine columns of five sections. The reference JARs match the installed artifacts by SHA-256. Both implementations render their native geometry through the tile dispatcher in the same context. The Vandor scene includes its terrain geometry. Measurements are medians of client rendering time and thread allocation, using Mesa software rendering; they are not hardware FPS or Complementary shader acceptance.

A representative 64-garage run, containing 2,880 sections, produced:

| State | Malisis | Vandor | Relative speed |
| --- | ---: | ---: | ---: |
| Closed | 17.70 ms | 1.23 ms | 14.4× |
| Open | 17.28 ms | 1.40 ms | 12.4× |
| Pose advancing every frame | 17.82 ms | 1.76 ms | 10.1× |

The advancing-pose test measures fresh animation poses, rather than repeatedly drawing one warmed pose. Allocation fell from approximately 37 MB to 14 KB per animated frame in that case. Steady open and closed rendering allocated no memory after warm-up. Small-scene ratios vary: in the same run, a single garage's advancing-pose comparison was 3.9× faster. Timing depends on driver overhead, JVM warm-up, scene size and camera movement.

Closed garage panels belong to Minecraft's chunk geometry. A column retains identical fallback panels until replacement chunk meshes finish uploading, preventing a late rebuild from leaving a hole or a duplicate animated door. Moving and raised panels share the atlas batch. Pose templates are shared; translated column vertices and lighting are reused while their inputs remain unchanged. Template and column caches have separate byte budgets, invalidate on atlas/vertex-format changes and release world references. Active OptiFine dynamic lights refresh lighting each frame.

A native server matrix separately exercises 64 independent garages. One motion command publishes 576 top-owner updates, rather than 2,880 per-section updates. Repeating an unchanged input 6,400 times emits no additional updates. Every section still retains its authoritative saved motion state. The fixture also checks distinct channels and owner replacement from saved NBT.

Native interaction checks cover ordinary placement, cross-chunk columns, a 32-section door viewed only at its header, configuration through the Configurizer and sneak-right-click, channel operation, reversal, root mining and resource reload during motion. Cached geometry is compared with reference vertex payloads for all four facings and heights 1, 5 and 32.

## Other dense paths

A 29×29 unclaimed Surface floor reduces 1,682 individual face submissions to one shared atlas submission. In the focused comparison, rendering fell from 5.43 ms to 0.92 ms, with identical pixels. Controller-owned and joined surfaces retain their established rendering path.

Consecutive compatible planar glass panes retain sorted order while sharing one submission. The 216-pane fixtures cover both orientations and all three depths, with identical pixels. Special glass finishes, corner contacts and fluids retain their existing renderer.

Stationary opaque ramp endpoints cache clipped geometry in VBOs. The byte-limited cache uses weak tile references so discarded vehicle views can be collected. A 78-cell fixture fell from 2.64 ms to 0.58 ms with identical pixels. Motion, glass, escalators and moats retain their established path. Opaque diagonal-wall fallback submissions also batch; ordinary in-section walls already use chunk geometry.

Canopy and moved-door collision queries use nearby loaded-owner buckets. They check current tile identity and never load chunks. An empty query among 6,144 unrelated loaded tiles no longer walks those tile maps. Turntable collision handling similarly avoids unrelated entity scans when no turntables exist.

## Shader rain

Fixed Canopy Glass already stops native precipitation columns. The Complementary integration scopes its skylight-derived rain-wind uniform while the camera is beneath a confirmed fixed-glass roof, then restores the previous value. Native height, roof removal and exception-safe shader-state restoration are checked. The reported visual result remains subject to manual verification with Complementary Unbound on a hardware client.
