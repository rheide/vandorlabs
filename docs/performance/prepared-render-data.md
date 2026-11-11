# Prepared rendering and incremental updates

These Forge 1.12.2 changes reuse completed render data and suppress redundant
updates. They preserve saved formats, registered content and the existing window
property protocol. The implementations use Vandor's own models and render paths;
they do not import source or assets from other mods.

## Configured item variants

Completed native item variants use a shared cache with limits of 256 entries and
16 MiB of estimated retained memory. Keys include the owning model and the
resolved material, layout and mounting choices. They do not retain ItemStack,
NBT or world objects. Custom models and unsupported dynamic cases continue
through the existing model path. Only stable null-state, seed-zero item requests
are prepared for reuse.

Prepared quads preserve the complete vertex record, including shader extensions.
Callers receive defensive vertex-data and vertex-format copies. UV rewriting is
limited to an aligned FLOAT2 UV0 element; absent, unknown or differently encoded
UV elements retain the original quad. Resource reload, world unload and vertex
format changes retire cached variants. The mounted-control world cache uses the
actual immutable listed-property map, block identity, mount and seed. It does not
use Forge's potentially stale clean-state projection after a listed property is
changed on an extended state. Unsupported unlisted properties bypass reuse.

## Decorative thruster particles

Admission happens before particle construction. Emitters must belong to the
current client world, have a camera within 64 blocks and run while the game is
unpaused. All admits at most 256 particles per world tick; Decreased alternates
requests and admits at most 64; Minimal admits none. A lone Decreased emitter
alternates across ticks instead of being permanently suppressed. World identity
or tick changes reset the allowance; additional render frames do not replenish
it. Admission does not consume emitter randomness or modify authoritative
thruster behavior. The shared allowance follows arrival order, so a busy early
emitter can consume capacity before later emitters.

## Hex tanks and batteries

Hex-tank fluid outlines are keyed by the exact solver inputs, including the
unrounded amount. Their cache is bounded to 64 KiB. Static tank templates retain
geometry for each shape, axis, layout and supported joined size within a 2 MiB
limit. Battery templates cover the twelve shell variants within 64 KiB.

Templates retain positions, pixel-space UVs, material slots and normals, rather
than atlas sprites. Rendering resolves current sprites, fluid color, alpha and
light. The battery's charge surface retains its exact fractional level. Resource
reload and world unload clear the templates. Frozen reference emitters and
focused live checks cover cold/warm geometry parity, joined tanks, fluid changes
and resource reload boundaries.

## Storage Status and Programmable Glass

Each Storage Status listener receives an initial snapshot of all three integer
fields. Later updates send only changed fields, preserving both 16-bit halves of
each integer. Listener identity is tracked independently, and removal discards
its snapshot. Signed wire halves are masked during reconstruction. The
deterministic trace of an initial snapshot, 1,000 unchanged updates and 100
amount-only updates falls from 6,606 property messages to 206.

Programmable Glass compares decoded semantic fields and complete fluid NBT
before invalidating rendering or topology. First updates and lifecycle changes
still force refreshes. Load, copy, world/position changes and carried-tile paths
retain their invalidation boundaries. Duplicate packets still settle a pending
light refresh, and changes to carried fluid contents still mark the frame
changed. This removes redundant invalidation without changing packet encoding.

## Vehicle mesh construction and upload

CPU preparation retains the existing 512-cell and soft 2 ms frame allowances.
Finished geometry scans bounds in groups of 1,024 vertices and can resume on the
next frame. Opaque layers upload complete quad records in batches of at most
256 KiB, with a shared 1 MiB and soft 1 ms frame allowance. Every record retains
its complete immutable build format and extended payload.

The previous mesh remains visible until all layers of its replacement are
ready. Structure, resource and format invalidation discard unfinished GPU data.
Translucent layers remain a single sortable stream to preserve blend order. An
oversized translucent layer may exceed the byte allowance when it is the first
submission of a frame, preventing starvation. Camera-dependent translucent
resorting retains its existing upload path outside this construction budget.

These are cooperative limits. A single model render, merge or OpenGL call cannot
be interrupted after it begins. Splitting an opaque layer increases VBO and draw
counts in exchange for smaller individual upload submissions. Upload diagnostics
report bytes, batch count and large translucent submissions separately.

## Machine displays

Adjacent visible display slots that resolve to the same texture share a draw
submission. All textures are resolved before emission because a lazy animation
upload can change the OpenGL texture binding. Slot order, animated UVs, color and
lighting remain live. An A/B/A texture sequence still needs three submissions;
the renderer does not reorder slots to combine nonadjacent textures.

## Verification scope

The verification tasks cover full-record parity, unsupported-format fallbacks,
cache limits and invalidation, particle admission, property-packet round trips,
listener lifecycle, glass lifecycle updates and upload budgeting. Focused live
fixtures cover tank/battery geometry and actual vehicle GPU payloads. CPU timing,
allocation, message counts and structural parity do not establish hardware FPS
improvements or acceptance with a particular OptiFine shader pack.
