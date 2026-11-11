#!/usr/bin/env bash
# VLCKit for an Apple build, fetched once and checked every time: VideoLAN's own release (its CocoaPods download),
# pinned and checked against its SHA-256, kept in ~/.cache/everyui/vlckit/ and unpacked there once. Prints the
# xcframework's path. The pins are sbt-everyui's (sbt-support/.../VLCKit.scala): change both together.
#
#   tools/vlckit.sh tvos      TVVLCKit (the Apple TV)
#   tools/vlckit.sh ios       MobileVLCKit (iPhone and iPad)
#
# tools/tvos-run.sh runs it on the Mac (ssh mac bash -s -- tvos < tools/vlckit.sh). It needs curl, shasum or
# sha256sum, and a tar that reads xz (macOS's own does).
set -euo pipefail

case "${1:-}" in
  ios)
    NAME=MobileVLCKit VERSION=3.7.5
    URL=https://download.videolan.org/pub/cocoapods/prod/MobileVLCKit-3.7.5-a213fd11-6de05adc.tar.xz
    SHA=a76f7a6cfcde4209b56f943e6e18400659d8a7a93ff482f8b1d059468d11e6be ;;
  tvos)
    NAME=TVVLCKit VERSION=3.7.4
    URL=https://download.videolan.org/cocoapods/prod/TVVLCKit-3.0-20260930-0854.tar.xz
    SHA=07892cae8e59e95a23f1fcf11692f802eae106312aa5ad9782faa2556b2d65d5 ;;
  *) echo "usage: vlckit.sh ios|tvos" >&2; exit 2 ;;
esac

ROOM="$HOME/.cache/everyui/vlckit"
ARCHIVE="$ROOM/$NAME-$VERSION.tar.xz"
OUT="$ROOM/$NAME-$VERSION"
mkdir -p "$ROOM"

sum() { if command -v shasum > /dev/null; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1; }

if [ ! -f "$ARCHIVE" ] || [ "$(sum "$ARCHIVE")" != "$SHA" ]; then
  curl -fsSL -o "$ARCHIVE.part" "$URL"
  got=$(sum "$ARCHIVE.part")
  if [ "$got" != "$SHA" ]; then
    rm -f "$ARCHIVE.part"
    echo "vlckit: $URL is $got, not $SHA" >&2
    exit 1
  fi
  mv "$ARCHIVE.part" "$ARCHIVE"
  rm -rf "$OUT"
fi
if [ ! -d "$OUT/$NAME.xcframework" ]; then
  rm -rf "$OUT" "$OUT.part"
  mkdir -p "$OUT.part"
  tar -xJf "$ARCHIVE" -C "$OUT.part"
  mv "$OUT.part/$NAME-binary" "$OUT"
  rm -rf "$OUT.part"
fi
echo "$OUT/$NAME.xcframework"
