#!/bin/bash
# Build script for jt400wrap.jar without requiring Ant

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building jt400wrap.jar..."

# Clean and create build directory
rm -rf build
mkdir -p build

# Compile Java sources
echo "Compiling Java sources..."
javac -source 1.8 -target 1.8 \
  -cp "lib/json-simple-1.1.1.jar:lib/hsqldb.jar:lib/jt400.jar" \
  -d build \
  src/nodejt400/*.java

# Create JAR
echo "Creating JAR..."
jar cf lib/jt400wrap.jar -C build .

echo "✓ Successfully built lib/jt400wrap.jar"
