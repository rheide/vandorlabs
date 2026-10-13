# Wall connection acceptance screenshots

This workflow captures Minecraft's actual client rendering and produces a local HTML review page. It does not run or update the documentation gallery.

## Resume diagonal review

The diagonal implementation can remain unchanged while the remaining size modes are reviewed. Capture and review each mode separately:

| Mode | Size | Review state |
| --- | --- | --- |
| `1` | Full width / full height | An accepted 54-case baseline exists. Later changes still require review when comparison flags them. |
| `0` | Half width / full height | Visual acceptance remains pending. |
| `2` | Full width / half height | Visual acceptance remains pending. |

Start with mode `0`, then repeat for mode `2`. These modes use different geometry paths; approval of one does not approve another. Each page has the same 60 stable arrangements: 18 L, 24 T and 18 four-way. Cases cover wall, glass and porthole materials, four directions, uniform upper/lower placement, and both placement orders for L corners. T and four-way material-pair coverage is pooled across poses, rather than every Cartesian combination. Vertical, straight-only, mixed-size, mixed-upper/lower, fill and manual-join sweeps are excluded.

The local workspace can retain `build/diagonal-acceptance-resume.json`, an ignored index of existing review pages, feedback and comparison reports. Use those pages when available; otherwise regenerate the selected mode with the commands below. Committed baselines remain the authority for accepted cases.

## Build and capture

Use Java 8 for the Gradle 4.9 build and the project's configured Forge client dependencies. On the configured Windows checkout, the local build helper selects Java 8:

```powershell
& .\build\tooling\build-local.ps1 -Offline -Tasks @('assemble','testDiagonalJoins')
python testclient/run_windows.py --focus connection-acceptance --connection-family diagonal --connection-mode 0
python testclient/run_windows.py --focus connection-acceptance --connection-family diagonal --connection-mode 2
```

Run from the repository root. Use the configured Python interpreter if `python` does not have the project dependencies. Only the standard mod JAR in `build/libs/` is used. Do not rebuild while a client is still running.

The launcher prints its output directory, `testclient/render-run.<timestamp>-<pid>/`. A complete capture generates `index.html` automatically. Open that file locally. Keep completed folders intact; every rerun has a new capture ID and its own saved review marks. Captures are ignored by Git, so their local HTML/PNG files are separate from committed baselines.

Each capture records its JAR hash, size mode, screenshot dimensions and camera manifest. A complete diagonal capture requires 60 screenshots and the client completion/fixture coverage markers. Primary camera coordinates must remain unchanged for comparisons with accepted images.

## Review and export

All displayed cases default to correct. Click an image once to mark it Wrong; click again to undo. Add notes where useful. **Copy feedback** produces text for pasting into a conversation. **Export JSON** produces the portable review file needed to create a baseline or capture diagnostic views. Export before clearing marks or changing browsers; saved marks otherwise use browser-local storage scoped to the capture.

After fixes, capture the same mode again. To put previously flagged cases first without importing their old Wrong marks:

```text
python testclient/review_connections.py testclient/render-run.<new-capture> --scope diagonal-half-width-full-height --feedback-order build/review.json --no-contact-sheet
```

Scope names are `diagonal-full-width-full-height`, `diagonal-half-width-full-height` and `diagonal-full-width-half-height`. Keep the original feedback file and capture folder; do not overwrite screenshots that were reviewed.

## Opposite-side diagnostic views

For an obscured or ambiguous junction, capture the cases marked Wrong from a lower opposite angle using the same built JAR and mode:

```text
python testclient/run_windows.py --focus connection-acceptance --connection-family diagonal --connection-mode 0 --connection-opposite-feedback build/review.json
python testclient/review_connections.py testclient/render-run.<primary> --scope diagonal-half-width-full-height --feedback-order build/review.json --secondary-capture testclient/render-run.<opposite> --no-contact-sheet
```

This adds a **View opposite side** button within the existing flagged cards, without adding review cases. Image clicks and exports still identify the primary case. The generator validates matching case IDs, JAR hash, mode, scope, camera, PNG dimensions and checksums, then copies the additional views and records `secondary-provenance.json`. Partial diagnostic captures do not compare or promote baselines.

## Preserve approvals and detect regressions

`testclient/baselines/active.json` selects immutable accepted originals. The full-width/full-height baseline `diagonal-full-width-full-height-v1` contains 54 accepted cases and excludes six flagged cases. It does not automatically approve subsequent fixes. The other two modes have no accepted baseline yet.

A complete capture automatically compares against its mode's active baseline, if one exists. The launcher writes `connection-regression/index.html` and `comparison.json` inside the new capture. Exit code `2` means accepted images changed and need review; it does not discard the captured screenshots. Check completion markers and the report before deciding whether a failure is a regression, an intentional correction, or a capture problem. No comparison updates accepted originals.

To highlight changed accepted cases in the same review page, add:

```text
--regression-report testclient/render-run.<new-capture>/connection-regression/comparison.json
```

to the reviewer command. Previously Wrong cases appear first, followed by changed accepted cases with amber comparison links, then passing cases. These badges do not mark a case Wrong automatically. Keep the comparison report at the page's `connection-regression/` relative path when publishing a separate local review folder.

After explicit approval, preserve the review's accepted cases in a **new** baseline directory:

```text
python testclient/connection_baseline.py create --feedback build/review.json --capture testclient/render-run.<reviewed-capture> --baseline testclient/baselines/diagonal-half-width-full-height-v1
```

Then update that mode's entry in `active.json` to the new sibling directory name. Existing baseline directories are never overwritten. JSON `displayedFiles` limits default-correct approval to displayed cases; hidden or unreviewed images are not promoted. See [baseline details](baselines/README.md) for comparison tolerances and explicit comparison commands.

## Tooling checks

```text
python -m unittest discover -s testclient -p "test_connection*.py"
```

The checks cover approval boundaries, comparison behavior, sampled coverage and reviewer controls. With Node.js available, they also exercise view switching, one-click Wrong marking, persistence and exports through the actual page JavaScript.
