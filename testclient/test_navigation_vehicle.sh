#!/usr/bin/env bash
# Keep the historical Linux entry point for the shared two-phase launcher.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
exec python3 testclient/test_navigation_vehicle.py "$@"
