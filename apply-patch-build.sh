#!/usr/bin/env bash
# Apply a patch and build the standard JAR without running tests.
set -euo pipefail

usage() {
    echo "Usage: $0 <patch-file>"
    echo 'Check and apply the patch, then build the standard JAR with Java 8.'
    echo 'Tests are skipped. A failed build leaves the applied changes in place.'
}
if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    usage
    exit 0
fi
if (( $# != 1 )); then
    usage >&2
    exit 2
fi

patch_file=$1
[[ -f "$patch_file" ]] || { echo "Patch file does not exist: $patch_file" >&2; exit 1; }
patch_file=$(cd -- "$(dirname -- "$patch_file")" && printf '%s/%s' "$PWD" "$(basename -- "$patch_file")")
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
[[ -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]] || {
    echo 'Set JAVA_HOME to a Java 8 JDK before applying the patch.' >&2
    exit 1
}
java_version=$("$JAVA_HOME/bin/java" -version 2>&1)
[[ "$java_version" == *'version "1.8.'* ]] || {
    echo 'Gradle 4.9 requires Java 8; set JAVA_HOME to a Java 8 JDK.' >&2
    exit 1
}

cd -- "$repo_dir"
git apply --check -- "$patch_file"
git apply -- "$patch_file"
mkdir -p build/patch-builds
build_log=$(mktemp "$repo_dir/build/patch-builds/build-XXXXXXXX.log")
echo "Patch applied. Build log: $build_log"
if ! ./gradlew jar -x testCopyCompatibility --no-daemon 2>&1 | tee "$build_log"; then
    echo "Build failed; patch remains applied. See $build_log" >&2
    exit 1
fi
echo 'Standard JAR built in build/libs/.'
