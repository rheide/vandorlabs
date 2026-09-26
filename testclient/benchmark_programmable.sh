#!/bin/bash
# Rendering submission benchmark in a disposable live Forge world.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew build --no-daemon
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-benchmark.XXXXXX")
TEST_JAR=${VANDOR_LABS_TEST_JAR:-build/libs/vandorlabs-1.0.jar}
cp "$TEST_JAR" testclient/runtime/game/mods/vandorlabs-1.0.jar
VANDOR_LABS_REPRO_OUT="$RUN_OUT" VANDOR_LABS_RENDER_BENCHMARK=true \
    VANDOR_LABS_BENCHMARK_ONLY=true timeout 600 testclient/run.sh > "$RUN_OUT/client.log" 2>&1
grep -q '\[vandorlabs\]\[reprolab\] render-benchmark PASS' "$RUN_OUT/client.log"
echo "Benchmark artifacts: $RUN_OUT"
