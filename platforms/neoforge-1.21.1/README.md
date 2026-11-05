# Experimental NeoForge 1.21.1 bootstrap

This independent Java 21 Gradle target compiles the current portable Java source tree and packages a minimal NeoForge entry point. It registers no gameplay content. It is not a playable port, a feature parity claim, or a legacy world migration tool. Do not open existing Vandor Labs worlds with this target.

Run `python3 tools/build_all.py --target neoforge-1.21.1` from the repository root. Set `VANDOR_JAVA21_HOME` to a Java 21 JDK when automatic discovery does not find one. The orchestration script runs shared contracts on the selected target’s JDK (both JDKs when building both targets), then builds the selected target and places its standard JAR under the root `build/libs/`. Java 8 and Java 21 builds run in separate Gradle processes. The legacy root Gradle target remains independent.

`./gradlew runClient` and `./gradlew runServer` in this directory are development launch tasks; neither constitutes automated live acceptance. The historical feature branch contains broader adapters that require shared source extractions absent from the current portable tree. They are not imported here. Add adapters only with focused runtime checks and explicit coverage documentation.
