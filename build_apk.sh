#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_RELATIVE_PATH="app/build/outputs/apk/free/release/app-free-release.apk"
APK_PATH="$ROOT_DIR/$APK_RELATIVE_PATH"
DOWNLOADS_DIR="$HOME/Downloads"
DESTINATION_PATH="$DOWNLOADS_DIR/capyreader-free-release.apk"

require_file() {
  local path="$1"

  if [[ ! -f "$path" ]]; then
    echo "Missing required file: $path" >&2
    exit 1
  fi
}

require_file "$ROOT_DIR/release.keystore"
require_file "$ROOT_DIR/secrets.properties"
require_file "$ROOT_DIR/local.properties"

mkdir -p "$DOWNLOADS_DIR"

echo "Building signed freeRelease APK..."
(cd "$ROOT_DIR" && ./gradlew :app:assembleFreeRelease)

require_file "$APK_PATH"

cp "$APK_PATH" "$DESTINATION_PATH"

echo "APK copied to $DESTINATION_PATH"
