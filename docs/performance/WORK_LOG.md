# Programmable block performance work

## Request (2026-09-26)

> In this folder is a minecraft mod, read the details and how it works. In ~/LLMShareDrive is a quota checking script. I want you to analyze the performance of this mod and comeup with performance optimizations and code cleanups. They should be testable changes and we should make sure that we have tests that cover the before-state before making changes. I am interested in a comparison of rendering performance of all the Programmable blocks of this mod: how do they compare against vanilla minecraft blocks? Is there anything we can do to make them render faster? Second, there's a lot of similar functionality in the Programmable Blocks, e.g. how they join adjacent blocks, how they position, how they handle redstone, and so on. I want you to check the code for this code and make sure this is clean and decently generalized and abstracted. Continue with these tasks until you're at 40% quota left and then give me a report on what you changed.

## Plan and evidence

- Baseline revision: `a3962677`; working tree initially clean.
- Initial live weekly quota: 49%. Stop at 40%, reserving capacity for validation/report.
- [x] Read project architecture, build instructions, quota script and existing tests.
- [x] Add/run baseline rendering benchmarks and regression contracts before production edits.
- [x] Compare all programmable block families with vanilla controls.
- [x] Optimize measured redundant work and consolidate shared connection behavior.
- [x] Run Java 8 build and full live client suite after changes.
- [x] Publish measurements, limitations, architecture findings and next steps.

Keep final build in `build/libs/`. Benchmarks run in the isolated test client.

### Baseline test additions

- Original geometry: 864 slices across all four shapes, both borders and six
  assembly sizes. Exact coordinate fingerprint `-8752866524235774271`, plus
  frame/glass coverage and bounds assertions. Passed on unchanged production code.
- Rectangle partition: all 512 occupancy masks of a 3x3 grid compared with a
  brute-force largest-rectangle oracle, including deterministic tie breaking.
  Passed on unchanged production code.
- Added opt-in live submission benchmark, CSV output and per-family screenshots.
  Warmups exclude first texture decoding. Includes every programmable registry
  family, initially seven vanilla controls, 16/64 instances, joined round portholes,
  all six light/cube/slab facings and tiled upper slabs.
- First exploratory measurement used contiguous defaults. Final baseline uses
  separated defaults and explicit joined variants, with valid upper door/chair
  cells. Only the final matching harness runs will be used for before/after claims.

### Implemented changes and validation

- Bounded porthole outline/slice caches keyed only by geometry, retaining at most
  1,024 slices and 65,536 coordinate doubles, plus 32 small outlines. No world,
  tile, texture or GL resource is retained in these caches.
- Forge batched rendering for opaque programmable blocks, trigger blocks, slabs
  and lights. Existing fallback rendering remains available. Finite cube/slab
  meshes and texture names are precomputed.
- Shared plane axes for portholes/lights/propulsion, bounded loaded-cell flood
  fill for portholes/lights, one porthole eligibility predicate, shared panel
  depth selection and collision-box rotation. Family-specific joining rules remain.
- Rectangular and unjoined light groups bypass general rectangle partitioning.
- Replaced raw culling calls with GlStateManager calls. Pixel comparison caught
  a stale state-cache mismatch that otherwise hid light faces in the batch.
- Added six-plane traversal safety checks, including a rejected neighbor, an
  unloaded-cell guard, and the 4,096-cell ceiling.
- The first screenshot microbenchmark read the previous game framebuffer.
  Corrected the harness to bind the framebuffer explicitly, then visually
  inspected the fixtures. A later brightness comparison exposed inherited
  current-color state in direct TESR calls; the harness now sets white as the
  normal world dispatcher does.
- Final measurements use cached VBO geometry, matching the normal terrain
  backend. The original production code is rebuilt in an isolated worktree
  with the identical corrected harness. Exploratory display-list timings are
  excluded from final performance claims.

### Completed evidence

- Baseline tests committed before production edits: `71819719`.
- Full original live suite: `render-run.gRvrGz` PASS. Full optimized suite:
  `render-run.PDnsaa` PASS, including 39,408 controller assertions. An intermediate
  full suite also passed in `render-run.QaXz4V`.
- Joined-light channel notifications now settle shared derived work once within
  the synchronous call, retaining reconciliation and safe iteration snapshots.
  Nested-scope/ordering/exception contracts pass; every benchmark receiver is
  checked after both rising and falling edges.
- Final benchmark uses the same allocation-enabled harness on the original
  production code and optimized code: `render-benchmark.bawwR0` and
  `render-benchmark.PrvD0B`. 96 rendering cases cover 30 programmable registry IDs
  and eight vanilla controls. Thirty static images pass the documented tolerance.
- Final Java 8 build passed; packaged JAR contains the new renderer, cache,
  shared helpers and batching classes. Artifact stays in `build/libs/`.
- The final pair measures opaque-solid submission reductions around 43–54%,
  joined round-porthole reductions around 47%, and a 256-light pulse pair falling
  from 69.44 ms to 1.24 ms. Java allocation for that pulse pair falls from
  122.7 MB to 1.7 MB. Software-renderer results are not hardware GPU/FPS claims.
- Full report, architecture assessment, raw CSVs, two additional paired runs,
  chart and reproduction commands are in this directory. Active ramps,
  moving-door poses, particles and real hardware remain explicit measurement gaps.

- Tested implementation and final diagnostic harness committed as `3cbec41c`.
- Final review added a regression for queued settlement after a later receiver
  fails. Java 8 build passed in `/tmp/vandor-final-contract-build.log`. Final JAR
  class/resource contents match the measured optimized JAR; only build timestamps
  differ. The report records the distributable SHA-256.
- Final review commit `445755d7` adds the partial-failure contract and allocation
  columns to the comparison script. The temporary baseline source worktree was
  removed after preserving its JAR, CSVs and reconstruction instructions.

### Stop condition

- Live `~/LLMShareDrive/codexstatus.py` reached **40% weekly allowance remaining**.
  Work stopped at the requested threshold. The final read-only tick review found
  repeated connected-engine discovery during particle emission as a follow-up;
  no additional production changes were started.
