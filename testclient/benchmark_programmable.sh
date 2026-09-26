#!/bin/bash
# Rendering submission benchmark in a disposable live Forge world.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew build --no-daemon
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-benchmark.XXXXXX")
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
TEST_JAR=${VANDOR_LABS_TEST_JAR:-build/libs/vandorlabs-$VERSION.jar}
rm -f testclient/runtime/game/mods/vandorlabs-*.jar
cp "$TEST_JAR" "testclient/runtime/game/mods/vandorlabs-$VERSION.jar"
VANDOR_LABS_REPRO_OUT="$RUN_OUT" VANDOR_LABS_RENDER_BENCHMARK=true \
    VANDOR_LABS_BENCHMARK_ONLY=true timeout 600 testclient/run.sh > "$RUN_OUT/client.log" 2>&1
grep -q '\[vandorlabs\]\[reprolab\] render-benchmark PASS' "$RUN_OUT/client.log"
echo "Benchmark artifacts: $RUN_OUT"
