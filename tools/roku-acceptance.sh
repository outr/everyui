#!/usr/bin/env bash
# reactify-on-Roku acceptance run.
#
# Sideloads the emitted reactive-test channel, captures the Roku debug console,
# keeps only the LATEST run of the suite (the console replays recent history on
# connect), and diffs it line for line against the same suite's output on the
# JVM. Identical output is the pass condition.
#
#   sbt "reactiveTest/emitRoku"                        # emit the channel
#   ROKU_PASS=<dev password> tools/roku-acceptance.sh      # run and diff
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

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

[ -f "$CHANNEL/manifest" ] || { echo "no channel at $CHANNEL — run: sbt \"reactiveTest/emitRoku\"" >&2; exit 2; }
( cd "$CHANNEL" && zip -qr "$WORK/channel.zip" manifest source components images 2>/dev/null )

status=$(curl -s --digest -u "rokudev:$ROKU_PASS" -F mysubmit=Install -F "archive=@$WORK/channel.zip" \
  "http://$ROKU_IP/plugin_install" | grep -oE 'Install Success|Identical|Failed' | head -1 || true)
echo "install: ${status:-no response}"
[ "$status" = "Failed" ] && { echo "the Roku rejected the package (a BrightScript compile error)" >&2; exit 1; }
sleep 2

python3 - "$ROKU_IP" "$CAPTURE_SECONDS" "$WORK/raw.txt" <<'PY'
import socket, sys, time
host, secs, out = sys.argv[1], float(sys.argv[2]), sys.argv[3]
s = socket.create_connection((host, 8085), timeout=5)
s.settimeout(1.0)
buf, end = b"", time.time() + secs
while time.time() < end:
    try:
        d = s.recv(4096)
        if not d:
            break
        buf += d
    except socket.timeout:
        pass
open(out, "wb").write(buf)
PY

python3 - "$WORK/raw.txt" "$WORK/device.txt" <<'PY'
import io, sys
raw = io.open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
starts = [i for i, l in enumerate(raw) if l.startswith("RX ") and "var.initial" in l]
rx = [l.rstrip() for l in (raw[starts[-1]:] if starts else []) if l.startswith("RX ")]
io.open(sys.argv[2], "w", encoding="utf-8").write("\n".join(rx) + ("\n" if rx else ""))
print("captured %d suite lines" % len(rx))
PY

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
