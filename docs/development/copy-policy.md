# Shared Copy Controller policy

Copy Controller preflight and construction-chip decoding share engine-neutral policy classes. Engine adapters continue to own block discovery, permissions, supported tile factories, opaque snapshot data, inventory ownership and durable transfer journals.

`CopyGeometry` retains the released scan prism and blueprint-centering rules. Pads are at most 32 by 32 cells, with scan height from 1 to 16. The drawing normal selects the depth axis; blueprint centering changes only the other two axes. A design that does not fit is refused before materials or output blocks change.

`MaterialWithdrawal` computes the remaining counts in the material chest without modifying its slots. Needs retain their established order, with exact item-data costs before empty block costs. Each need consumes matching slots in chest order. A shortage identifies the first unsatisfied need and its exact remaining count. Item matching, physical chest halves and before/after inventory receipts remain platform responsibilities.

`CopySettings` preserves height, channel increment, activation and channel-bank validation. Changes to activation or channel membership reset edge detection; height, increment and input-source changes do not reset that bank.

`BlueprintCompression` retains the Vehicle Chip construction-data codecs: raw data is limited to 4 MiB; GZIP data is limited to 768 KiB stored and 4 MiB expanded. Unknown codecs, corrupt streams and excessive expansion are refused. This codec supplies construction input and grants no vehicle-operation authority.

The portable regression compares these policies with independent released algorithms, including negative planar coordinates, all supported prism dimensions, design-fit refusals, chest slot ordering, missing counts and codec limits. The Forge world focus exercises actual Copy plans and channel shifts through the same adapters. The extraction leaves block IDs, saved fields, packet operations, settings and opaque tile data unchanged.
