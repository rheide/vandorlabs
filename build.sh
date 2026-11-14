#!/usr/bin/env bash
# Build the standard JAR without running tests.
set -euo pipefail

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    echo "Usage: $0"
    echo 'Build the standard JAR with Java 8, without tests. Output: build/libs/.'
    exit 0
fi
if (( $# != 0 )); then
    echo "Usage: $0" >&2
    exit 2
fi

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
[[ -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]] || {
    echo 'Set JAVA_HOME to a Java 8 JDK.' >&2
    exit 1
}
java_version=$("$JAVA_HOME/bin/java" -version 2>&1)
[[ "$java_version" == *'version "1.8.'* ]] || {
    echo 'Gradle 4.9 requires Java 8; set JAVA_HOME to a Java 8 JDK.' >&2
    exit 1
}

cd -- "$repo_dir"
exec ./gradlew jar -x testCopyCompatibility --no-daemon
