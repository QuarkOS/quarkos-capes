#!/usr/bin/env bash
# Builds every Fabric, Forge, and NeoForge jar into dist/ and writes dist/SHA256SUMS.
# Release jars compile out the dev harness and the LabyMod try-on catalog.
# Pass -PallowLocalService=true until a production -PserviceUrl is set.
# This script does not upload or publish.
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew releaseAll -Prelease=true "$@"
