# Programmable block performance work

## Request (2026-09-26)

> In this folder is a minecraft mod, read the details and how it works. In ~/LLMShareDrive is a quota checking script. I want you to analyze the performance of this mod and comeup with performance optimizations and code cleanups. They should be testable changes and we should make sure that we have tests that cover the before-state before making changes. I am interested in a comparison of rendering performance of all the Programmable blocks of this mod: how do they compare against vanilla minecraft blocks? Is there anything we can do to make them render faster? Second, there's a lot of similar functionality in the Programmable Blocks, e.g. how they join adjacent blocks, how they position, how they handle redstone, and so on. I want you to check the code for this code and make sure this is clean and decently generalized and abstracted. Continue with these tasks until you're at 40% quota left and then give me a report on what you changed.

## Plan and evidence

- Baseline revision: `a3962677`; working tree initially clean.
- Initial live weekly quota: 49%. Stop at 40%, reserving capacity for validation/report.
- [x] Read project architecture, build instructions, quota script and existing tests.
- [ ] Add/run baseline rendering benchmarks and regression contracts before production edits.
- [ ] Compare all programmable block families with vanilla controls.
- [ ] Optimize measured redundant work and consolidate shared connection behavior.
- [ ] Run Java 8 build and full live client suite after changes.
- [ ] Publish measurements, limitations, architecture findings and next steps.

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
  family, seven vanilla controls, 16/64 instances, joined round portholes,
  all six light/cube/slab facings and tiled upper slabs.
- First exploratory measurement used contiguous defaults. Final baseline uses
  separated defaults and explicit joined variants, with valid upper door/chair
  cells. Only the final matching harness runs will be used for before/after claims.
