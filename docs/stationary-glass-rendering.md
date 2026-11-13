# Stationary Programmable Glass rendering

Stationary, flat, translucent Programmable Glass with a stock tint uses persistent
pane meshes. The default performance mode smooths the stock glass material,
renders one surface, merges joined interiors and casts shadows from the opaque
frames while omitting translucent pane interiors.

This is a client rendering policy. It does not alter saved glass settings,
collision, placement, server behavior or the resource pack files on disk.

## Geometry and ownership

Each world section groups compatible glass by physical plane, placement, tint,
shader block identity and packed lighting. Joined cells become actual render
rectangles. Empty cells remain empty; independent cells retain their separate
rectangles. Different lighting values and material identities remain separated.
For example, a full compatible 16 by 16 joined plane becomes one rectangle.
Larger windows split at section boundaries and at material or lighting changes.

One real tile anchors each planar group. It supplies the group's complete render
bounds to frustum culling and to the existing glass ordering collector. Other
members stop entering the renderer after the replacement vertex buffer is ready.
Members which still need a frame fallback remain admitted until their frame
geometry has been published. The pane manager removes only its own members from
the exact uploaded section's tile-render list and restores them on invalidation.

The vertex buffer contains the complete vertex records created by the active
BufferBuilder, including OptiFine's extra normal, tangent, texture and material
data. Geometry stays relative to the section origin to retain precision far from
the world origin. Camera movement changes view ordering and translation without
rebuilding the vertex data.

The renderer supports the normal 32-byte format and the checked 56-byte OptiFine
layout and material hooks. It preserves the block-entity shader program rather
than selecting a terrain program. Unsupported formats, disabled VBOs and exhausted
buffer budgets retain the ordinary tile renderer, with the reason in diagnostics.
Retained GPU storage is bounded to 32 MiB; one group cannot exceed a section plane.

Block edits, relevant neighboring edits, light updates, tile removal, atlas
reloads, vertex-format or shader-pack identity changes and world unload invalidate
the affected data. Shader-pack identity includes reloading the same named pack,
so retained material aliases cannot silently survive a different pack instance.
Group publication follows a successful GPU upload. Shared gameplay classes call
through the common/client proxy boundary and retain dedicated-server linkage.

## Supported common case and fallbacks

The persistent path targets flat native tinted glass in a stationary world.
Custom material inserts, fluids, corners, opaque glass and carried VehicleWorld
geometry retain their existing renderer. Perpendicular groups that cross each
other's planes keep their original ordering granularity; a whole merged plane
must not replace geometry that needs to be interleaved with another plane.

Existing chunk-rendered frames remain in place. A selected frame that is waiting
for its native chunk upload keeps its tile fallback. The shadow policy omits only
translucent pane drawing, after required frame work; opaque inserts and contained
fluids are not removed by that pane policy.

## Performance appearance

Smooth mode uses the dominant first-frame color of each native glass palette
texture and constant companion materials. It intentionally removes repeated
shimmer, animated stock-glass artwork and fine material variation, including
resource-pack replacements of those native palette textures. Smooth sprites are
small and have nondegenerate UV coordinates for shader tangent generation.

The one-surface material compensates diffuse alpha using
`effectiveAlpha = 1 - (1 - alpha) * (1 - alpha)`. This preserves the simple
two-layer blend strength. It is not a promise to reproduce every shader pack's
two-surface refraction, specular or transmission appearance.

The default shadow policy keeps frame shadows and removes translucent glass
shadows. Full pane shadows remain selectable; they use the retained grouped
geometry independently of main-view ordering.

## Client options

These Java system properties are read at startup. No world migration is required.

| Property | Default | Effect |
| --- | --- | --- |
| `vandorlabs.staticGlass` | `true` | Enable persistent stationary pane groups. |
| `vandorlabs.staticGlassTexture` | `smooth` | `smooth` enables merging of the smoothed native palette; `authored` preserves the original per-cell texture representation. |
| `vandorlabs.staticGlassSingleSurface` | `true` | Use one compensated midpoint surface; `false` retains the original two surfaces. |
| `vandorlabs.glassPaneShadows` | `frames` | `frames` omits translucent pane interiors from shadows; `full` keeps them. |
| `vandorlabs.staticGlassDiagnostics` | `false` | Log recurring ownership/draw/upload counts and asynchronous batch GPU/CPU timings. |

For example, append `-Dvandorlabs.staticGlassDiagnostics=true` to the client's JVM
arguments when collecting a performance capture. The default fast settings do not
need additional arguments.

An explicit compatibility configuration is:

```
-Dvandorlabs.staticGlassTexture=authored
-Dvandorlabs.staticGlassSingleSurface=false
-Dvandorlabs.glassPaneShadows=full
```

For a complete old-renderer comparison also set
`-Dvandorlabs.staticGlass=false`. The existing
`-Dvandorlabs.benchmarkLegacyGlazing=true` benchmark switch disables all the new
pane policies as well.

## Diagnostics and measurement

An activation summary is logged after world rendering begins. It identifies the
selected policies, active stride, GPU and driver, published groups and owners,
actual rectangles, pruned tile admissions, retained bytes and fallback reason.
The existing world benchmark includes these counters too.

Detailed diagnostics report main and shadow draws and vertices separately, uploads
and uploaded bytes, and sampled CPU/GPU totals. These counters are cumulative for
the current world. GPU timestamps are retrieved only after a later frame reports
them available; the renderer never waits for each group to finish. A bounded
query pool reports dropped timing samples instead of blocking. CPU and GPU sample
counts can differ while GPU queries remain pending; unavailable timers are
reported explicitly. Batch timings are not total frame timings or a prediction
of FPS.

Compare the same camera, world, resolution, shader pack and settings after warmup.
Record frame times without detailed instrumentation as well as a separate
diagnostic run. A stationary scene should show stable geometry-upload counts;
camera movement alone should not upload the same panes again. In a uniform joined
window, published owner counts should greatly exceed group and draw counts. The
default fast shadow policy should submit no translucent pane-group shadow draws.

## Relationship to earlier glass patches

This change is based on public master
`97f1325420fd8e6f7d7281c913129b24aeea0637` and replaces the separately distributed
combined A/B/C/D and grouped-coverage glass patch. Keep unrelated performance
improvements already in the base. Do not apply both glass implementations on top
of each other: older per-block retention and coverage decisions do not describe
the full geometry owned by a new group anchor.

Persistent buffers and one-surface rendering now cover the supported shader path.
Actual mesh merging benefits a single joined window, independently of how many
windows overlap in depth. The existing finite-surface ordering and legacy batching
remain for their supported cases.

The earlier approximate eight-layer cap is not applied to these retained groups.
Main-view geometry is retained at every depth. A future depth cap must prove
coverage of every emitted group rectangle, count distinct physical planes and
keep light-view shadow coverage separate. Applying an anchor's one-block coverage
test to its whole group would incorrectly erase visible glass.

Apply this patch to a checkout without that earlier combined glass patch. If it
was applied as an uncommitted patch, reverse the same original patch first and
check the new diff before applying. If it was committed, revert only that glass
change on the intended branch. Preserve unrelated local changes.

## Focused checks

Use Java 8 with Gradle 4.9:

```sh
./gradlew testJoinedPaneMesher testStaticGlassOrdering testStaticGlassPolicy \
  testStaticGlassAdmissions testGlassPaneBuffers testGlazingOrder \
  testGlazingSweep testDedicatedServerLinkage jar reobfJar --no-daemon
```

The mesher checks exhaustive small shapes and randomized full sections. The
record checks compare native and extended records, both orientations and all
depth placements, actual emitted hole coverage, UV bounds and far-world
precision. Admission checks use real CompiledChunk tile lists and exercise
pending publication, replacement, invalidation, frame fallback and world cleanup.

The `GlassPaneGpuChecks` fixture can also run in an existing OpenGL compatibility
context. It uses the production emitter, complete uploaded records and array
binding code to compare unit and merged pixels, normal and extended formats,
holes, rotations and world-limit translations. It checks array/buffer restoration
and includes an intentionally wrong vertex stride as a negative control. The
`render-regressions` live-client focus runs the corresponding stitched-material
framebuffer check.

Run the focused client render regressions and glass/contact checks with the
actual optional shader configuration before treating a build as accepted for
that configuration. Structural tests, byte parity and software rendering do not
establish hardware frame-rate gains or shader-pack appearance.
