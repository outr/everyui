#!/usr/bin/env bash
# reactify-on-Roku acceptance run.
#
# Sideloads the emitted reactive-test channel, captures the Roku debug console,
# keeps only the LATEST run of the suite (the console replays recent history on
# connect), and diffs it line for line against the same suite's output on the
# JVM. Identical output is the pass condition.
#
#   sbt "checks/checksClasspath; reactiveTest/emitRoku"   # the helpers, and the channel
#   ROKU_PASS=<dev password> tools/roku-acceptance.sh      # run and diff
#
# Any emitted program that prints RX lines works the same way — the network probe:
#   CHANNEL=network/out-roku GOLDEN=network/expected-output.txt START=socket.open tools/roku-acceptance.sh
#
# Regenerate the expected output after changing the suite:
#   sbt "reactiveTest/run" | grep '^RX ' > reactive-test/expected-output.txt
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
ROKU_IP=${ROKU_IP:-192.168.1.112}
: "${ROKU_PASS:?set ROKU_PASS to the Roku developer password (see DESIGN.md, Build / ops notes)}"
CHANNEL=${CHANNEL:-$ROOT/reactive-test/out-roku}
GOLDEN=${GOLDEN:-$ROOT/reactive-test/expected-output.txt}
CAPTURE_SECONDS=${CAPTURE_SECONDS:-30}
START=${START:-var.initial}          # a substring of the run's first RX line

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

[ -f "$CHANNEL/manifest" ] || { echo "no channel at $CHANNEL — run: sbt \"reactiveTest/emitRoku\"" >&2; exit 2; }
( cd "$CHANNEL" && zip -qr "$WORK/channel.zip" manifest source components images 2>/dev/null )

status=$(curl -s --digest -u "rokudev:$ROKU_PASS" -F mysubmit=Install -F "archive=@$WORK/channel.zip" \
  "http://$ROKU_IP/plugin_install" | grep -oE 'Install Success|Identical|Failed' | head -1 || true)
echo "install: ${status:-no response}"
[ "$status" = "Failed" ] && { echo "the Roku rejected the package (a BrightScript compile error)" >&2; exit 1; }
sleep 2

CHECKS="java -cp $(cat "$ROOT/checks/out/classpath.txt") everyui.checks.Checks"   # sbt checks/checksClasspath
$CHECKS roku-console "$CAPTURE_SECONDS" "$WORK/raw.txt"
$CHECKS suite-lines "$WORK/raw.txt" "$WORK/device.txt" --start "$START"

if grep -qE "runtime error|BRIGHTSCRIPT: ERROR" "$WORK/raw.txt"; then
  echo "--- device runtime error:"
  grep -E "runtime error|BRIGHTSCRIPT: ERROR" "$WORK/raw.txt" | tail -2
  awk '/Backtrace:/{p=1} p{print} /Local Variables:/{exit}' "$WORK/raw.txt" | head -20
fi

if diff -u "$GOLDEN" "$WORK/device.txt"; then
  echo "PASS: device output is identical to the JVM ($(wc -l < "$GOLDEN") lines)"
else
  echo "FAIL: device output differs from the JVM (diff above)" >&2
  exit 1
fi
