# Flat panel placement against supports

When placing a flat Programmable Wall, Glass or Porthole Wall against a horizontal support face normal to the panel, its four-pixel body now occupies the destination edge touching that support. Previously, clicked-cell fractions were interpreted as destination-cell fractions, selecting the opposite edge. The correction is shared by Forge and native placement adapters.

The saved depth values and geometry are unchanged. Top/bottom clicks and tangent-plane extension retain the clicked fractional depth and existing orientation. Diagonal-end placement retains its earlier priority; diagonal geometry is unchanged. Full-depth Porthole Blocks remain full-cell bodies. Replacement of the clicked cell retains the original fractional placement policy.

Focused regressions exercise canonical block IDs through actual Survival item use, compare emitted selection bounds to the support boundary, and assert one placed block consumes one item. The matrix includes solid supports, thin supports at all three saved depths, top/tangent continuation and full-depth Porthole controls. The released 2,592-case empty-world placement callback golden remains unchanged; native actual-world replay explicitly distinguishes the intentionally corrected flat adjacent-support cases from that original callback corpus.

The correction is a placement change. It does not migrate existing blocks, change collision or artwork, or enable an unconverted family. Focused validation is separate from the deferred release gate.

## Focused acceptance

Both versions pass 160 canonical Survival placement profiles; native checks include 480 assertions. The Source client additionally sends 16 real placement packets after observing the synchronized game mode, selected slot, held count and position, then checks the placed client bounds and authoritative one-item consumption. Source dedicated-server linkage passes separately. Native historical replay passes 5,184 contexts and 48 actual uses (13,008 assertions), retaining the original empty-world callback hash and the corrected flat support expectation. No release gate was run.
