#!/bin/bash
# Verify reproducible builds: build twice in Docker, compare hashes
set -e

echo "=== Void Reproducible Build Verification ==="
echo ""

mkdir -p /tmp/void-build-1 /tmp/void-build-2

echo "[1/3] Building first APK..."
docker build --no-cache -t void-build-1 .
docker run --rm -v /tmp/void-build-1:/output void-build-1

echo "[2/3] Building second APK..."
docker build --no-cache -t void-build-2 .
docker run --rm -v /tmp/void-build-2:/output void-build-2

echo "[3/3] Comparing hashes..."
HASH1=$(sha256sum /tmp/void-build-1/void-release.apk | cut -d' ' -f1)
HASH2=$(sha256sum /tmp/void-build-2/void-release.apk | cut -d' ' -f1)

echo "Build 1: $HASH1"
echo "Build 2: $HASH2"
echo ""

if [ "$HASH1" = "$HASH2" ]; then
    echo "✅ REPRODUCIBLE — hashes match"
    exit 0
else
    echo "❌ NOT REPRODUCIBLE — hashes differ"
    exit 1
fi
