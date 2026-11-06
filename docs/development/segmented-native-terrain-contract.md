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
the model bakery, model map, shared renderer bake and active resource manager, then wraps each world Segmented model
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

`NativeSegmentedChunks` stages every cell before refreshing model data and binds
each affected section to the current upload publication. Forced asynchronous
submissions are limited to eight per tick. Closed BER panels remain visible until
all sections acknowledge; opening resumes the cosmetic clock only after terrain
removal. Awaiting columns do not capture or sample motion. Settled columns skip
row lighting and body emission. Resource generations activate only after a wrapped
bakery completes, with older completions rejected; the compatibility constructor
without a bakery does not activate the coordinator.

The focused world fixture uses public hosts across horizontal and vertical section
boundaries. It checks successful task uploads, delayed acknowledgement eligibility,
closed fallback, terrain removal before opening, reversal, reload and section
replacement. The delay holds only receipt eligibility after actual uploads; it does
not claim to simulate a GPU driver stall. The private hold flag is unset in gameplay
and is accessed reflectively by smoke code only.

The same fixture loads 128 physical columns of 32 sections. Its former BER draw-state
control emits 66,560 endpoint vertices and samples 4,096 row lights per pass; settled
terrain emits neither through the BER. Allocation counters sample the actual adapter
after warm-up. GPU comparisons separately require a settled image to remain unchanged
when the BER is disabled, and to differ when both body paths are disabled while the
header remains. These focused software/structural checks do not establish hardware
frame rates, shader-pack compatibility or a full release gate.
