# Running release checks

On Linux, run the standard release checks from the repository:

```sh
./tools/verify_release.sh
```

Use `./tools/verify_release.sh --check` to check dependencies without building or starting Minecraft. The script requires a Java 8 JDK, Python 3 with Pillow, and Xvfb. The existing client harness prepares its Forge runtime as needed.

Allow roughly an hour for the standard gate on a prepared runtime; downloads, graphics and hardware can change that duration. The console and retained log show each completed focus.

The script checks the texture browser, builds the standard JAR and runs `verifyRelease`: portable and non-rendering matrices, dedicated-server linkage, shader startup/reload, rendered regressions and the live gameplay/recovery fixtures. It then runs the isolated exact-JAR Dynmap test with WorldEdit and compares artifact hashes. Mekanism is required by this wrapper so its cold-restart fixture is included. It does not update the production server.

The default dependency location is a sibling `MC-Forge12-2` installation. Override these environment variables for another installation:

| Variable | Input |
| --- | --- |
| `JAVA_HOME` | Java 8 JDK |
| `VANDOR_LABS_RELEASE_SERVER` | Forge installation used only to locate dependencies |
| `VANDOR_LABS_COMPAT_MODS` | Installed compatibility-mod directory |
| `VANDOR_LABS_TEST_OPTIFINE` | OptiFine G5 JAR |
| `VANDOR_LABS_TEST_SHADERPACK` | Shader pack ZIP |
| `VANDOR_LABS_TEST_MEKANISM` | Mekanism JAR |
| `VANDOR_LABS_TEST_FORGE` | Forge 1.12.2 universal JAR |
| `VANDOR_LABS_TEST_DYNMAP` | Dynmap JAR |
| `VANDOR_LABS_TEST_WORLDEDIT` | WorldEdit 6.1.10 compatibility JAR |
| `VANDOR_LABS_TEST_HEAP` | Per-client heap, default `8G` |

The default shader ZIP belongs in the ignored runtime dependency cache at `testclient/runtime/shaderpacks/ComplementaryUnbound_r5.6.1.zip`; shader packs and third-party JARs are not distributed by this repository. `--help` lists the overrides.

Each invocation writes a separate directory under `build/release-checks/`, including the release log, Dynmap log and artifact hash. Captures and generated worlds remain available. Successful runs discard their copied mod installations and regenerable caches; set `VANDOR_LABS_KEEP_TEST_INSTALLS=1` to retain those too. Failed runs keep the full installation for debugging. Review or remove completed evidence directories periodically: screenshots and worlds can still accumulate.

This is the standard release gate, not every diagnostic or benchmark in the repository. Specialized previous-release native-world upgrades, abrupt-process-termination matrices and hardware shader/FPS acceptance remain additional checks selected for the changes being released. Windows uses `gradlew.bat verifyRelease` and the Windows client harness; this Bash wrapper is for Linux.
