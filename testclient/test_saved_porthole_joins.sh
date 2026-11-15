#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <saved-world.7z> [viewscreen runner arguments]" >&2
    exit 2
fi
ARCHIVE=$1
shift
FIXTURE=$(mktemp -d "$ROOT/build/porthole-join-data.XXXXXX")
python3 "$ROOT/tools/prepare_porthole_join_fixture.py" "$ARCHIVE" --out "$FIXTURE"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvandorlabs.savedPortholeJoinsOnly=true -Dvandorlabs.savedPortholeJoinDir=\"$FIXTURE\""
exec "$ROOT/testclient/test_viewscreen.sh" --focus directional-joins "$@"
