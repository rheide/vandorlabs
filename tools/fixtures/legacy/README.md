# Original flat-wall renderer reference

`LegacyWallModelGoldens.java` captures 696 cases from the original Forge 1.12.2
renderer at commit `a7d26df8`. It belongs in a disposable checkout of that commit,
not in the current production source set. It intercepts the actual TESR uploader
with the stitched atlas and records the actual GL transforms, material sprites,
packed colors, normals, lightmap values, depth masks, and culling state. It
restores the world fixture, uploader, render pass, and GL state after capture.

Copy the file into the old checkout's
`src/main/java/com/vandorlabs/client/` directory. In `ReproLab.onClientTick`, immediately
before the existing `vandorlabs.flatWallDialogChecksOnly` branch, insert:

```java
if (Boolean.getBoolean("vandorlabs.wallModelGoldensOnly")) {
    try {
        if (LegacyWallModelGoldens.tick(mc, outDir.toPath())) mc.shutdown();
    } catch (Exception failure) {
        throw new IllegalStateException("wall model golden capture", failure);
    }
    return;
}
```

Build the standard JAR with Java 8 and the old checkout's normal Gradle wrapper.
Use the existing test-client harness with a fresh disposable game directory,
installing that exact test JAR and the harness's usual compatibility mods. Set
`VANDOR_LABS_REPRO_OUT` to a fresh capture directory,
`VANDOR_LABS_TEST_GAME_DIR` to the disposable game directory, and
`JAVA_TOOL_OPTIONS=-Dvandorlabs.wallModelGoldensOnly=true` before invoking
`testclient/run.sh`. Require a clean exit and the `wall-model-goldens PASS` marker.
The output is `legacy-wall-emission.json`; it must contain 696 unique case keys.

Before accepting a reference, compare every existing `.class` ZIP member with an
unmodified standard JAR from the same commit. Only `ReproLab.class` may differ;
the exporter and its nested classes must be the only added classes. Hash the
test JAR before and after the client run to verify it was not rebuilt during
capture. This prevents shared extraction code from becoming its own oracle.

Run the native `walls` client suite, then compare the outputs:

```sh
python3 tools/test_wall_original_parity.py \
  --legacy CAPTURE/legacy-wall-emission.json \
  --modern SCREENSHOTS/modern-wall-original-parity.json \
  --report parity.json
```

Material identity follows the recorded actual atlas sprite. In particular,
plain walls ignore a dormant `SideTexture` value and use the fixed metal sprite.
The normal comparison allows one signed-byte quantum after transforming the
original packed normal by its recorded GL normal matrix; this is not a claim of
identical normal bytes or hardware shader-pack acceptance. The matrix covers
three profiles, four facings, three depths, aperture shapes, rectangular
artwork, joined regions with holes, and L/T/cross contacts. Disabled aperture
joining, external-material reload, carried rendering, and graphics-pipeline
acceptance require their own tests.

`LegacyWallItemGoldens.java` exports the 36 actual item-model and gallery cases. Its opt-in entry in `FluidFocusedChecks.tick` is `if(Boolean.getBoolean("vandorlabs.wallItemGoldensOnly")){if(ticks<40)return false;return LegacyWallItemGoldens.tick(mc,output);}`. Keep the unpadded reference separate from the intentional padding correction: geometry, materials, UVs, shading and saved item data must remain unchanged; only GUI transforms change. Compare the corrected Forge gallery against native with the housing-item model and image tools.
