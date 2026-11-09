#!/usr/bin/env bash
# Builds every Fabric and Forge jar into dist/ and writes dist/SHA256SUMS.
# Release jars compile out the dev harness and the LabyMod try-on catalog.
# This script does not upload or publish.
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew releaseAll -Prelease=true "$@"
