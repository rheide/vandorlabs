#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
export VANDOR_LABS_ARM_LANDING_ANGLES_ONLY=true
if [ "${VANDOR_LABS_LANDING_ANGLE_TAXI_ONLY:-false}" = true ]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvandorlabs.armLandingTaxiOnly=true"
else
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvandorlabs.armLandingAnglesOnly=true"
fi
exec "$ROOT/testclient/test_viewscreen.sh" --focus arm-live "$@"
