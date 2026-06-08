#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="${GRADLE_VERSION:-8.10.2}"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="${BASE_DIR}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_HOME="${DIST_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${DIST_DIR}/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${DIST_DIR}"
  if [ ! -f "${ZIP_FILE}" ]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  fi
  unzip -q -o "${ZIP_FILE}" -d "${DIST_DIR}"
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
