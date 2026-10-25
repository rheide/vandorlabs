# NeoForge 1.21.1 adapter

This target is under conversion. Forge 1.12.2 remains the default and the behavioral reference. The NeoForge target includes programmable cube finishes and joined face layouts, viewscreen/console/input variants, wireless redstone endpoints, all eleven mounted control IDs, the Industrial Table, and programmable slabs/stairs with contained fluids. Registration and compilation do not establish complete feature parity; see the repository's [multiversion architecture](../../docs/development/multiversion.md) and [feature inventory](../../docs/development/feature-coverage.md) for the remaining families and validation requirements.

## Build

Use Java 21 and Python 3.9 or newer with Pillow for this independent Gradle build. Pixel comparison tools also use NumPy. Select another interpreter with `-PpythonExecutable=...` when needed:


```sh
./gradlew build --no-daemon
```

The root orchestrator runs shared contracts under Java 8 and 21, selects a separate JDK for each Gradle process, builds both targets, and places target-qualified standard artifacts in root `build/libs/`:

```sh
python3 tools/build_all.py
```

`JAVA8_HOME` and `JAVA21_HOME` select installed JDKs; the `VANDOR_JAVA8_HOME` and `VANDOR_JAVA21_HOME` aliases are also accepted. The default root Forge commands retain their existing behavior. `--verify-legacy-release` invokes the unchanged Forge release gate. Modern compilation and resource checks do not replace live client validation or the Forge release gate.

NeoForge, ModDevGradle, the Gradle wrapper and its distribution checksum are pinned. The ASM family is pinned to 9.8 because its JPMS descriptors provide the module names required by this SecureJarHandler runtime. Runtime heaps default to 4 GB and can be selected with `-PmodernRunHeap=6G`.

## Resources and persistence

`generate_resources.py` reads the canonical portable housing catalog, unified texture metadata, default artwork and additional artwork. It preserves numeric housing choices and aliases, generates modern language/recipe/loot layouts, and stages the original rectangular-art crop and used UV ranges. Screen generation reads the existing display catalog and compact animation files. No second artwork catalog or original-textures JAR is maintained.

The programmable cube uses a native block entity, server-owned editing menu, fixed-size settings payload and model data for per-face rendering. Settings retain the shared schema keys and legacy material migration. Unrecognized saved settings are retained for subsequent saves. Native item components preserve configured cube settings in drops. The viewscreen and wireless endpoint adapters use separate native menus and bounded payload codecs.

Filesystem materials use the shared discovery and padding policies through a native resource pack. Cube settings retain their material IDs when files disappear or change dimensions; resource reloads update the existing picker and baked model UVs. Full viewscreen housing and surface materials retain IDs and update in an already-open picker through missing-file and artwork-dimension reloads.

Remaining limitations include other gameplay families, custom block materials, carried-assembly integration, shader acceptance and external integrations. The emitted adapter coverage report is a generated implementation inventory, not a declaration of release acceptance.

## Disposable runtime checks

Run these only against the isolated target development world. The `write` phase creates test blocks, changes settings and saves; the `read` phase verifies a subsequent process restores the exact settings. Both phases stop the server after their assertions:

```sh
python ../../tools/test_modern_runtime.py --phase both
```

The wrapper requires every phase-specific PASS marker, a clean Minecraft shutdown and process completion within the timeout. It rejects crash evidence even when Minecraft or Gradle returns zero. `--compiled` uses an already frozen classes/resource set.

For rendered checks, configure the development server on localhost port 25570, then start it with `-PmodernClientFixture`. That opt-in fixture creates a test platform and blocks and positions each connecting player for the checks. Run the development client with `-PmodernClientSmoke`; it connects to that local server, checks emitted cube geometry and native menu input, exercises screen settings, captures screenshots in the target client run directory, and stops after success. Production worlds are outside this workflow.

The complete rendered gate can also manage its own local fixture server:

```sh
python ../../tools/test_modern_client.py
```

Build the native target first. This command freezes the compiled classes and resources, starts a fresh temporary world on a separate localhost port, checks every required runtime marker and fresh capture, and stops only the processes it started. It discards the fixture world afterward and retains reports under `build/reports/modern-client`. It selects an isolated virtual display when no display is available; `--xvfb` also selects it explicitly. `--port` changes the local test port.

For an independent slab/stair comparison, pass `--legacy-shape-reference` with the actual Forge model export produced by the focused fluid checks. The comparison checks emitted geometry, collision unions, material assignments, UVs, culling and shading. Without that argument, the gate records that the cross-version export comparison was not requested; native runtime and pixel checks still run.
