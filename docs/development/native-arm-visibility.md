# Native arm payload visibility

Run the focused ordinary-payload check from `platforms/neoforge-1.21.1`:

```sh
python3 test_gear_culling.py --xvfb
```

The scene uses normal Survival placement of four joined, eight-section downward Extender columns and a 3×3 diamond-block platform. Its ordinary activation uses the asynchronous arm journal. After source removal finishes, the fixture pauses a captured mid-stroke pose and observes the existing block-entity dispatcher from two downward-facing cameras. An independently emitted copy of the current public payload geometry is clipped against the actual main-view matrices; it does not draw a replacement payload or alter admission.

Both camera views currently pass: the platform contributes 108 clipped triangles, with exactly one controller renderer per sampled frame and no missing frames. The lower camera has the root section geometrically behind it while the payload remains visible. NeoForge expands section admission to include an eight-block camera cube, so this result does **not** establish that the root section was excluded from its visible-section list. The ordinary native scene did not reproduce the Source hidden-root failure; no native rendering fallback was added. This check does not cover a native Core, unavailable as a public block, or unsupported columns longer than eight sections.

The runtime retains its two screenshots, logs and accepted main-class hashes through the shared test artifact helper. This is a focused rendering regression, not a release gate or hardware/shader performance result.
