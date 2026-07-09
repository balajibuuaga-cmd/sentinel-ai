#!/bin/sh
set -eu

MAVEN_VERSION="${MAVEN_VERSION:-3.9.9}"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
MAVEN_HOME="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if [ ! -x "$MAVEN_BIN" ]; then
  mkdir -p "$WRAPPER_DIR"
  ARCHIVE="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  echo "Downloading Maven $MAVEN_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$ARCHIVE"
  else
    echo "Neither curl nor wget is available, and mvn is not installed." >&2
    exit 1
  fi
  tar -xzf "$ARCHIVE" -C "$WRAPPER_DIR"
fi

exec "$MAVEN_BIN" "$@"
