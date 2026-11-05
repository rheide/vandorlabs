# Experimental NeoForge 1.21.1 material slice

This independent Java 21 target provides the plain Programmable Matter Block, Programmable Matter Ingot and Industrial Alloy Ingot, with a creative tab, native models, loot, stone-or-better pickaxe harvesting, and the canonical block packing/unpacking and alloy recipes. The matter infuser and other acquisition systems are not implemented; matter ingots currently require creative mode or commands.

This is a partial material slice, not a playable port or a legacy world migration tool. Programmable cubes, configuration tools, controls, vehicles and all other systems remain unimplemented. Do not open existing Vandor Labs worlds with this target.

Run `python3 tools/build_all.py --target neoforge-1.21.1` from the repository root. Set `VANDOR_JAVA21_HOME` to a Java 21 JDK when automatic discovery does not find one. The orchestration script runs shared contracts on the selected target’s JDK (both JDKs when building both targets), then builds the selected target and places its standard JAR under the root `build/libs/`. Java 8 and Java 21 builds run in separate Gradle processes. The legacy root Gradle target remains independent.

`./gradlew runClient` and `./gradlew runServer` in this directory are development launch tasks; neither constitutes automated live acceptance. The historical feature branch contains broader adapters that require shared source extractions absent from the current portable tree. They are not imported here. Add adapters only with focused runtime checks and explicit coverage documentation.


Resources are generated from existing canonical artwork, models, recipes and translations by `generate_matter_resources.py`; generated assets remain under `build/`. Runtime artwork uses the established 128-pixel resolution, including the compact alloy ingot icon. No duplicate artwork source is maintained here.

The development smoke source set is excluded from the production JAR. Its opt-in `matterPhase=write` and `matterPhase=read` server runs verify native recipes, harvesting tags and actual saved block/chest contents across process restart. `matterPhase=client` enables an isolated scene and native client model, placement/mining and screenshot checks. These checks use development launch classpaths; they do not establish exact-JAR launch, hardware FPS, or legacy upgrade acceptance.

Run `python3 platforms/neoforge-1.21.1/test_matter.py` from the repository root for the two-process native save/restart checks. Add `--client` for a real client; on a Linux host without a visible display add `--xvfb` (requires Xvfb and Pillow). The runner allocates disposable worlds and logs under `build/test-artifacts/`, checks each PASS marker and process exit, and retains the screenshot for inspection. It also checks all three native rendered item icons for visible slot padding and checks server observation of client placement/mining. Software rendering establishes this focused visual/interaction behavior, not hardware performance.
