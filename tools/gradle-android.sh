#!/usr/bin/env bash
set -euo pipefail

altimeter_gradle_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "${altimeter_gradle_root}/tools/android-env.sh"

exec "${altimeter_gradle_root}/gradlew" "$@"
