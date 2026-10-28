#!/usr/bin/env sh
# Lightweight Gradle launcher for this archive. It bootstraps the pinned distribution
# without requiring a separate gradle-wrapper.jar to be shipped in this source package.
set -eu
GRADLE_VERSION="8.9"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_ROOT="$GRADLE_USER_HOME/cafeteria-gradle-distributions"
GRADLE_HOME="$DIST_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_ROOT"
  ZIP_PATH="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 --connect-timeout 20 "$URL" --output "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_PATH" "$URL"
  else
    echo "Se necesita curl o wget para descargar Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "Se necesita unzip para extraer Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  unzip -q -o "$ZIP_PATH" -d "$DIST_ROOT"
  rm -f "$ZIP_PATH"
fi
exec "$GRADLE_BIN" "$@"
