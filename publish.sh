#!/usr/bin/env bash

set -e

# a snapshot would go to Central's snapshot repository, not be released: set the release's version first
VERSION=$(sed -n 's/^ThisBuild \/ version := "\(.*\)"$/\1/p' build.sbt)
case "$VERSION" in
  ""|*-SNAPSHOT) echo "build.sbt's version is \"$VERSION\": set it to the release's before publishing" >&2; exit 1;;
esac

# every sbt run is headless: the desktop backend's specs open JavaFX windows, which go to an Xvfb of their own
sbt_() { env -u WAYLAND_DISPLAY xvfb-run -a sbt -J-Xmx16g --server -batch "$@"; }

sbt_ clean
sbt_ Test/compile
sbt_ test
sbt_ publishSigned
sbt_ sonaRelease
