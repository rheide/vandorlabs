# Further rendering work

## Request (2026-09-26)

> Sounds good, implement these. Commit what you have so far first.

> When you're at 80% quota left, find a good stopping point and leave it there.

The previous work was clean and committed through `0f10158d` before this pass.
Quota was 100% at the start. Stop at a tested commit near 80%.

## Before-state coverage

- [ ] Capture and commit benchmark variants for 8x8 floors and 4x4x4 volumes
  of programmable blocks/slabs and vanilla stone/slabs.
- [ ] Capture and commit closed/open programmable and iron-door fixtures.
- [ ] Run baseline Java 8 build and live benchmark before changing production rendering.

## Implementation

- [ ] Move programmable blocks and slabs to cached chunk geometry.
- [ ] Cull hidden full faces while preserving partial slab faces.
- [ ] Reduce programmable door stationary and moving rendering costs.
- [ ] Run full live suite and compare before/after images and performance.

Report limits and any remaining work at the requested quota threshold.
