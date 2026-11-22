#!/bin/bash
# Run one live client focus with a one-line result. See AGENTS.md "Live focus runs".
#
#   tools/focus.sh <focus> [--shots] [--no-build] [-- extra test_viewscreen.sh args]
#
# - Preflight fails in seconds on a missing Java 8, compat-mod directory or Trove library.
# - Rebuilds the standard JAR only when sources are newer than it (--no-build skips).
# - Prints PASS/FAIL, the first failure cause and the log path; full output stays in the log.
# - --shots keeps the run long enough to copy its screenshots to build/focus-shots/<focus>/
#   and tile them into contact-sheet.png, then removes the runtime per the retention policy.
#
# Machine-specific settings (for example VANDOR_LABS_COMPAT_MODS) belong in the untracked
# tools/focus.local.env, which is sourced when present.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
[ -f tools/focus.local.env ] && . tools/focus.local.env
FOCUS=${1:?usage: tools/focus.sh <focus> [--shots] [--no-build] [-- args]}
shift
SHOTS=false BUILD=true EXTRA=()
while [ $# -gt 0 ]; do
    case "$1" in
        --shots) SHOTS=true ;;
        --no-build) BUILD=false ;;
        --) shift; EXTRA=("$@"); break ;;
        *) echo "focus: unknown option $1" >&2; exit 2 ;;
    esac
    shift
done
JAVA8=${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
fail(){ echo "focus $FOCUS PREFLIGHT-FAIL: $*"; exit 2; }
[ -x "$JAVA8/bin/java" ] || fail "Java 8 not found at $JAVA8 (set JAVA8_HOME)"
MODS=${VANDOR_LABS_COMPAT_MODS:-"$HOME/.minecraft/mods"}
[ -f "$MODS/worldedit-forge-mc1.12.2-6.1.10-dist.jar" ] || fail "compat mods missing in $MODS (set VANDOR_LABS_COMPAT_MODS in tools/focus.local.env)"
find "$HOME/.gradle/caches/modules-2/files-2.1/net.sf.trove4j" -name trove4j-3.0.3.jar 2>/dev/null | grep -q . || fail "trove4j-3.0.3.jar missing from the Gradle cache"
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
JAR=build/libs/vandorlabs-$VERSION.jar
LOGS=build/focus-logs; mkdir -p "$LOGS"
STAMP=$(date +%Y%m%d-%H%M%S)-$$
if $BUILD && { [ ! -f "$JAR" ] || [ -n "$(find src generated-resources build.gradle -newer "$JAR" -type f -print -quit)" ]; }; then
    if ! JAVA_HOME=$JAVA8 ./gradlew --offline assemble --no-daemon -q > "$LOGS/build-$STAMP.log" 2>&1; then
        echo "focus $FOCUS BUILD-FAIL log=$LOGS/build-$STAMP.log"
        grep -m5 -E 'error:|FAILURE|What went wrong' "$LOGS/build-$STAMP.log"
        exit 1
    fi
fi
LOG=$LOGS/$FOCUS-$STAMP.log
KEEP=${VANDOR_LABS_TEST_KEEP_ARTIFACTS:-}
$SHOTS && KEEP=1
VANDOR_LABS_COMPAT_MODS=$MODS VANDOR_LABS_TEST_SKIP_BUILD=true VANDOR_LABS_TEST_KEEP_ARTIFACTS=$KEEP \
    bash testclient/test_viewscreen.sh --focus "$FOCUS" "${EXTRA[@]}" > "$LOG" 2>&1
STATUS=$?
RUN=$(sed -n 's/^Vandor client run directory: //p' "$LOG" | head -1)
CLIENT=${RUN:+$RUN/client.log}
RESULT=PASS; [ $STATUS -eq 0 ] || RESULT=FAIL
HASH=unavailable
if [ -n "$RUN" ] && [ -s "$RUN/artifact.sha256" ]; then HASH=$(cut -c1-12 "$RUN/artifact.sha256"); fi
echo "focus $FOCUS $RESULT exit=$STATUS jar=$HASH log=$LOG${RUN:+ run=$RUN}"
if [ -n "$CLIENT" ] && [ -f "$CLIENT" ]; then
    grep -m3 -oE '\[vandorlabs\]\[[a-z]+\] [^ ]+ PASS.{0,120}' "$CLIENT" | sort -u | head -3
    if [ $STATUS -ne 0 ]; then
        grep -m1 -E 'Caused by: |Exception in|IllegalStateException: ' "$CLIENT"
        grep -m1 -E '^\s+at com\.vandorlabs\.' "$CLIENT"
    fi
fi
[ $STATUS -eq 0 ] || grep -m3 -E 'FAIL|Error|error:' "$LOG" | grep -v '^\s*at ' | head -3
if $SHOTS && [ -n "$RUN" ]; then
    OUT=build/focus-shots/$FOCUS; rm -rf "$OUT"; mkdir -p "$OUT"
    find "$RUN" -maxdepth 1 -name '*.png' -exec cp {} "$OUT/" \;
    if ls "$OUT"/*.png >/dev/null 2>&1; then
        python3 tools/contact_sheet.py "$OUT/contact-sheet.png" $(ls "$OUT"/*.png | grep -v contact-sheet) > /dev/null && echo "shots: $OUT/contact-sheet.png ($(ls "$OUT" | grep -vc contact-sheet) images)"
    fi
    [ -z "${VANDOR_LABS_TEST_KEEP_ARTIFACTS:-}" ] && [ $STATUS -eq 0 ] && rm -rf "$RUN"
fi
exit $STATUS
