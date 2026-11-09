#!/usr/bin/env bash

set -e

# every sbt run is headless: the desktop backend's specs open JavaFX windows, which go to an Xvfb of their own
sbt_() { env -u WAYLAND_DISPLAY xvfb-run -a sbt -J-Xmx16g --server -batch "$@"; }

sbt_ clean
sbt_ Test/compile
sbt_ test
sbt_ publishSigned
sbt_ sonaRelease
