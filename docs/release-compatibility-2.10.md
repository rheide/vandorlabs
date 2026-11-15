# 2.10 compatibility and release review

The review baseline is release tag `2.9.1`. It covers the Forge 1.12.2 changes,
the shared Java 8 gameplay code and the experimental NeoForge 1.21.1 adapters.
NeoForge remains a separate, incomplete conversion; this release does not add
native Vehicle Mode or imply that a Forge world can be opened directly in
NeoForge.

## Saved resources and ownership

Resource Ports and Module Racks now retain undecodable resource data as an
inert owner. Unknown item or fluid providers, invalid counts, duplicate slots
and unsupported capability envelopes must not turn into an empty, usable
block. Picking or mining the recovery owner preserves its original data, and
construction copying requires that exact carrier rather than an ordinary empty
block. This also protects saves temporarily opened without a resource's mod.

If legacy Autopilot conversion and its prepared rollback both fail, the loaded
chunk retains an inert tile with the complete original Autopilot NBT. A displaced
partial Rack is invalidated and removed from ticking lists. The recovery block
can yield one exact configured item, and its canonical saved ID permits a later
cold load to retry ordinary conversion.

Native Chairs, Pressure Plates and Surfaces preserve inherited item components
when producing configured items. Regression inputs include a container of 37
diamonds and a custom name before any world save. Malformed Chair and Plate item
settings retain their original data and remain inert after saving and reloading,
without discarding inherited components.

Native arm and ramp publication no longer grants general access to held
inventories while executing a transaction. Only the exact internal block write
gets a one-use exception. A callback cannot use a retained inventory wrapper or
replace another held cell while its durable before-image still owns those
contents. Actual asynchronous publication regressions reproduced the duplicate
resource before this restriction and verified one owner afterwards.

Malformed or future native arm drop-escrow markers must remain frozen and
recoverable instead of throwing during entity loading or releasing an
unverified item. Entity checkpoint completion must account for both the native
storage write and the expected entity records; a successful flush alone is
insufficient if serialization omitted an owner.

On Forge, a failed Arm placement restores the previous coordinate mapping when
cleanup removed every partial destination cell. If cleanup leaves a live cell,
the retained destination mapping remains available for exact journal recovery.
Regression cases cover changed residual inventories, quarter-turn mappings,
reload and retry without creating a second owner.

Existing registry identities and horizontal Battery metadata remain intact.
Vertical Battery placement uses additional metadata values. Added controls and
packet types follow existing indices. Missing optional materials remain
unavailable rather than resolving through Minecraft's default air entry.
Opaque external capability NBT remains outside owned material migrations.

## Rendering and update cost

The review found quadratic bookkeeping in retained glass restoration and native
join dependency admission. Restoring 4,096 retained glass owners previously
performed 8,386,560 tile equality checks; identity-based admission removes those
comparisons. Registering 2,048 native join groups previously required 2,098,176
retained-cell probes; maintained accounting reduces that to 2,048. Cache limits,
owner removal and unload behavior remain part of the focused checks.

Reapplying an identical effective join policy no longer rewrites dormant
Porthole flags on ordinary building blocks. The no-op regression checks unchanged
NBT, revisions, world notifications, lighting and retained geometry, alongside
actual policy changes and mutual join barriers.

Arm visibility uses posed attachment bounds independently of the mounting
column's terrain visibility. Main-view admission remains separate from shader
shadow draws. Core render handoff waits for complete native data and the settled
visual pose, and cosmetic animation retains its client phase across tile
replacement. Collision and durable ownership remain authoritative on the
server.

Prepared table collision geometry is shared between versions and cached for the
finite facing, inversion and connection combinations. Surface and furniture
updates are deferred, identity-checked and bounded; clean controllers do not
continually rediscover their drawing pads.

Loading a replacement vehicle joint pose invalidates its cached visual
transforms. The rendered regression warms the old pose first, then checks both
the loaded transform and the changed image.

Tall Segmented Doors queue pending terrain uploads even when lower sections are
outside the camera view. Scheduling uses the ordinary asynchronous dispatcher,
attempts at most two requests per tick and preserves in-flight compiler tasks.
The temporary renderer remains until fresh compiled sections are acknowledged.

Individual static material Glass panes now use the same coordinate placement as
ordered batches. This removes high-contrast artwork seam differences caused by
applying the fallback's scale and translation in GL instead of on the CPU. The
regression keeps separate per-pane submissions and independently checks the
authored transform, UVs, colors, lighting and rotated normals before requiring
pixel equality. Animated material and ordinary Glass fallback paths retain their
existing rendering behavior.

## Validation boundaries

The Java 8 collect-first release inventory completed all CPU, non-rendering,
diagonal and shader checks plus all 27 selected live groups. It found six live
failures. Repairs were batched, and only failed or directly affected checks were
rerun, including the legacy Segmented Door handoff, Glass appearance and two
partial-restore Arm restart phases. All required failures are resolved. This is
complete baseline coverage plus targeted repair evidence, rather than a fresh
full-suite pass on the final JAR.

| Forge candidate SHA-256 | Evidence |
| --- | --- |
| `10ff4c6e1ea8d781dfc53b8c5e1d12c7c5552bcb276545093a81af3298edb688` | Complete baseline inventory: CPU checks and 21 live groups passed; six live failures collected. Physical dedicated-server upgrade checks also passed with Baubles, Thaumcraft and Thaumic Augmentation installed. |
| `e679520cc3b6b17785e39104fb9ae34f98d3e3c3516179eede5e6952695c910c` | Dedicated linkage, loaded-pose Coupler rendering, tall and legacy Segmented Door handoffs passed. Both selected abrupt-stop Arm restore/restart phases passed on this immutable artifact. |
| `39029ef5af66da44efd83124333b9c8a192e3758691f7014dd6e74745a5d3673` | Final standard JAR: Taxi, Utility 2.6 including rollback recovery, per-cell Segmented Door rendering, Glass appearance and render density passed. All 24 dense Glass scenes have zero significant pixel difference; independent placement and payload checks also pass. |

ZIP-entry comparisons establish that gameplay code outside the explicitly
repaired paths and all resource entries are unchanged between these candidates.
Earlier passing results are reused for those unchanged paths. The last repair
only changes the static Glass fallback renderer and diagnostic fixtures; Arm
ownership and Coupler code are identical to the tested preceding candidate.

The separate experimental NeoForge JAR has SHA-256
`9669df75433f97390a09378446f08020b0ab2c86048ecb37e9cf4870cebf58fd`.
Its focused native ownership, asynchronous checkpoint, recovery, component and
cache checks are separate from the Forge inventory. They do not establish
complete family parity or native Vehicle Mode.

The isolated Dynmap smoke test was explicitly skipped. CPU Dynmap contracts
passed. Live evidence was collected on Linux with software rendering; it does
not establish a Windows runtime pass.

Automated checks cannot prove compatibility with every third-party capability,
corrupted save or future mod version. Software-rendered checks establish the
asserted geometry, visibility and lifecycle behavior; they do not establish
hardware frame rates or universal shader-pack support. Unknown ownership data
must remain recoverable rather than being normalized away.
