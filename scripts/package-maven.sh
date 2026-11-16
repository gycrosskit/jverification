#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VERSION:?Pass the immutable candidate version}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]] || { echo 'Invalid version' >&2; exit 2; }
[[ ! -e build/maven && ! -e build/jverification-maven.tar.gz ]] || { echo "Candidate outputs already exist" >&2; exit 2; }
bash gradlew --init-script "$PWD/scripts/ci-repositories.gradle" publishAllPublicationsToStagingRepository --no-daemon --max-workers=1 -Dorg.gradle.parallel=false "$@"
python3 jitpack-metadata.py build/maven
python3 release-pack.py build/maven build/jverification-maven.tar.gz "$VERSION"
shasum -a 256 build/jverification-maven.tar.gz > build/jverification-maven.tar.gz.sha256
