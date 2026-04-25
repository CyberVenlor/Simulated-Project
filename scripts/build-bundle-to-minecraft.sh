#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODS_DIR="/Users/n3ur0/.minecraft/versions/1.21.1-NeoForge/mods"
LIBS_DIR="$ROOT_DIR/aeronautics-bundled/build/libs"

cd "$ROOT_DIR"

./gradlew :aeronautics-bundled:build

mkdir -p "$MODS_DIR"

BUNDLE_JAR="$(find "$LIBS_DIR" -maxdepth 1 -type f -name 'create-aeronautics-bundled-*.jar' | sort | tail -n 1)"

if [[ -z "$BUNDLE_JAR" ]]; then
  echo "No bundled jar found in $LIBS_DIR" >&2
  exit 1
fi

cp "$BUNDLE_JAR" "$MODS_DIR/"

echo "Copied $(basename "$BUNDLE_JAR") to $MODS_DIR"
