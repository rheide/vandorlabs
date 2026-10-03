#!/bin/bash
# Compatibility shortcut for the original door demonstration.
set -euo pipefail
exec bash "$(dirname "$0")/capture_animations.sh" --filter door-rotating "$@"
