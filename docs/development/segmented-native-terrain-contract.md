# Segmented Door native terrain contract

A settled native panel can be baked only when the column is UP, centered and
curved, its root texture is built in and outside Frame categories, and every
row has the released native panel style. Eligibility examines the root texture
and each row's style; the terrain snapshot still carries each cell's own texture.
`exportSegmentedTerrainEligibilityReference` freezes 1,115 actual `canBake` cases.

`NativeSegmentedTerrainSnapshot` contains immutable cell facts for section workers.
Its client projection is ephemeral and never enters update tags, saved state or
contained-fluid data. Entity model data merges the terrain and fluid snapshots.
An unstaged entity derives its header from its own row and settings, because native
listed TOP/BOTTOM properties are not currently maintained by the lifecycle. UP and
center are both required; a lower member never receives the root header.

The classic UP-center header always belongs to terrain, including while the body
is moving. Non-UP-center headers remain in the existing block-entity backend.
A missing tile retains the released canonical header fallback. The native model
must not infer a loaded tile's header solely from a stale listed TOP flag.

`SegmentedTerrainQuads` reproduces the released settled cell faces and authored
header. The header retains Forge float Rodrigues transforms, center translations,
canonical face order and the double-precision .999/.001 UV contraction. All 64
actual source models and 352 quads match native position, UV, normal, color,
cap, shade and ambient-occlusion records. Both the direct FaceBakery path and the
actual Forge VanillaModelWrapper loader path produce this reference for the header.

`NativeSegmentedTerrainModel.Bake` owns a conservative 16 MiB quad cache per
resource generation. Cache keys exclude publication revision, so a handoff rebuild
reuses unchanged geometry. Its material source is the same generation-owned bake
used by the moving renderer; material cache access is synchronized for section
workers. Canonical header geometry uses the released scalar path. Changed active
header model geometry is forwarded rather than overwritten with builtin elements.

The root model hook obtains the shared bake from `NativeSegmentedRenderer.prepare`
before replacing world models, constructs `NativeSegmentedTerrainModel.Bake` with
the model map and active resource manager, then wraps each world Segmented model
inside the existing fluid wrapper. Inventory models are left on their existing path.
The old later call to `NativeSegmentedRenderer.prepare` must be removed to avoid
creating a second resource-generation cache.

`NativeSegmentedSectionUploads` binds an upload to a world epoch, publication
generation, section origin and current rebuild task. A changed compiled instance
alone is insufficient: an older task may finish while a newer task is pending.
Only a successful current task whose uploaded compiled instance is still installed
can acknowledge the section, and dirty sections cannot acknowledge. Reload, recycle
and world replacement invalidate old publications. Untracked tasks acquire no future
completion callback. The mixins are client-only; common/server mixin lists remain
unchanged. CPU negatives and actual client mixin application are separate from
world upload and rendering acceptance.

The terrain model and acknowledgement primitives are an incremental foundation.
The coordinator, asynchronous suppression/resume and real-world performance checks
must pass before claiming the complete terrain handoff is accepted.
