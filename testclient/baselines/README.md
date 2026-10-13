# Accepted connection screenshots

`regular-wall-connections-t-v2` contains all 58 reviewed T scenes after the center-material corrections. `regular-wall-connections-x-v2` contains all 29 reviewed four-way scenes after the glass-frame corrections. These displayed samples were approved together; other images in the full capture catalogs remain outside the accepted sets. `active.json` selects these versions for T and four-way regression checks.

`regular-wall-connections-t-v1` preserves the earlier 34 unmarked cases from the reviewed 58-case T sample. `regular-wall-connections-x-v1` preserves the earlier 20 unmarked cases from the reviewed 29-case four-way sample. The 24 marked T cases and nine marked four-way cases were excluded. Hidden cases outside each displayed sample were not approved. These earlier immutable versions remain available. Later renderer changes, including intentional changes to accepted junctions, require review of comparison reports; they do not update baselines automatically.

`regular-wall-connections-v2` contains all 72 corrected regular-wall L-corner screenshots approved together after the fixes. It covers four front/back and left/right bend arrangements, nine ordered wall/glass/porthole material assignments and both placement orders. `active.json` selects this version for automatic L regression checks.

`regular-wall-connections` preserves the original eight accepted wall-wall screenshots. Its other 64 reviewed screenshots were marked Wrong and were excluded from that first baseline. Both versions retain their original images and manifests. Each `baseline.json` records stable case IDs, roles, camera coordinates and SHA-256 digests. The PNGs are copied without changing their bytes.

Compare a completed matching live capture:

```text

python testclient/connection_baseline.py compare --capture testclient/render-run.<capture>

```

The default L baseline is the version selected by `testclient/baselines/active.json`. `--baseline` selects another baseline, including the preserved first version, and `--output` selects a report directory. Reports otherwise go to `build/connection-regression/<capture>/`. Exit code 0 means every accepted case passed; exit code 2 requests review. The report contains JSON measurements, accepted/current crops and difference images in an HTML comparison. Capture files and baselines remain unchanged.

The live launcher also compares complete connection captures automatically. Use `python testclient/run_windows.py --focus connection-acceptance --connection-topology l` for L corners, or select `t` or `x` for the other junctions. Partial captures are not compared automatically. The launcher places reports in the capture's `connection-regression` directory and returns a nonzero result when accepted cases need review. Its `--connection-baseline` option selects a different baseline. By default, `active.json` selects the approved version for each topology. Conventional unversioned names are fallbacks only when that topology has no active mapping. Create each newly approved version in a new directory from human review feedback using the command below; preserve earlier versions.

Comparison requires identical camera/fixture coordinates and 1920 × 1080 screenshots. It samples a fixed junction region at half resolution. A union of the accepted and current foreground masks detects both removed geometry and additions beyond the old silhouette; the mask is dilated by two sampled pixels. Pixels that are green terrain in both images are excluded. Neutral framing and blue/cyan glazing remain in the comparison. A coherent illumination change can first be estimated from corresponding green terrain pixels, independently of the panel colors. Each RGB channel requires at least 1,000 samples, standard deviation of at least 3, correlation squared of at least 0.985, mean fit residual at most 1.5 and 95th-percentile residual at most 3 levels. Gains are bounded to 0.65–1.55 and offsets to six levels; changes outside those bounds still require review. The comparison records the fitted coefficients and quality measurements. A remaining median lighting adjustment is limited to six RGB levels. A pixel changes when a channel differs by more than 12 levels after that adjustment. A case requires review if changed pixels exceed 0.3% of its comparison mask or the mean channel-maximum error exceeds 2. These tolerances allow minor lighting noise and do not establish geometric correctness. Review flagged differences before deciding whether the implementation or capture needs correction.

Preserve newly approved cases from a portable review export into a **new** directory:

```text

python testclient/connection_baseline.py create --feedback review.json --capture testclient/render-run.<capture> --baseline testclient/baselines/<new-baseline>

```

Only `ok` cases are preserved; an explicitly exported `defaultStatus` is honored. When a review export includes `displayedFiles`, only those displayed screenshots are eligible: its default status never approves hidden catalog entries. Wrong, Unsure, Skip and unreviewed cases are excluded. Creation refuses an existing baseline directory. Comparisons verify original image digests and never promote a current screenshot automatically. New approvals require a deliberate new baseline and review of the resulting fixture changes.

After reviewing a new version, update the relevant `l`, `t` or `x` entry in `active.json` to its sibling directory name. Keep prior versions intact. Without an active entry, the launcher checks the conventional `regular-wall-connections`, `regular-wall-connections-t` or `regular-wall-connections-x` directory if it exists. An active entry pointing to a missing baseline is an error, so an accidentally removed fixture cannot silently disable its checks.

Run the comparison algorithm checks with:

```text

python testclient/test_connection_baseline.py

```

Pillow is required. The script can use the live harness's local `build/tooling/python-packages` installation when Pillow is not installed in the selected Python runtime.
