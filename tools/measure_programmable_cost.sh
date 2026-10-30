#!/usr/bin/env bash
# Isolated Java 8 object-size/state-build benchmark; never opens a live world.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a Java 8 JDK}"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
cat > "$scratch/SizeAgent.java" <<'JAVA'
import java.lang.instrument.Instrumentation;
public class SizeAgent {
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation value) { instrumentation=value; }
    public static long size(Object object) { return instrumentation.getObjectSize(object); }
}
JAVA
"$JAVA_HOME/bin/javac" "$scratch/SizeAgent.java"
printf 'Premain-Class: SizeAgent\n' > "$scratch/MANIFEST.MF"
"$JAVA_HOME/bin/jar" cfm "$scratch/agent.jar" "$scratch/MANIFEST.MF" -C "$scratch" SizeAgent.class
export VANDOR_SIZE_AGENT="$scratch/agent.jar"
cat > "$scratch/benchmark.gradle" <<'GRADLE'
allprojects { afterEvaluate { p ->
    if(p.tasks.findByName('compileNonRenderingChecks')) {
        p.tasks.create(name:'measureProgrammableCost',type:JavaExec) {
            dependsOn p.tasks.compileNonRenderingChecks
            classpath=p.sourceSets.main.runtimeClasspath+p.files(p.tasks.compileNonRenderingChecks.destinationDir)
            main='com.vandorlabs.client.ProgrammableCostBenchmark'
            jvmArgs '-javaagent:'+System.getenv('VANDOR_SIZE_AGENT')
            workingDir=p.file("${p.buildDir}/non-rendering-checks")
            doFirst { workingDir.mkdirs() }
        }
    }
} }
GRADLE
./gradlew --offline -I "$scratch/benchmark.gradle" measureProgrammableCost --no-daemon "$@"
