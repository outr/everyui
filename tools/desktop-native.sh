#!/usr/bin/env bash
# The desktop app as a self-contained native binary: GraalVM native-image over the JavaFX backend.
#
#   tools/desktop-native.sh           build desktop/out/native/everyui-desktop, then run it under Xvfb: the frozen
#                                     frame against tools/golden/animation-tree.txt, and a live frame-rate sample
#   tools/desktop-native.sh --trace   first rerun GraalVM's tracing agent (a frozen and a short live run) to refresh
#                                     desktop/native-config/, the reflection/JNI/resource metadata JavaFX needs —
#                                     after a change to what the app touches of JavaFX
#
# Needs `native-image` on the PATH (GraalVM) and Xvfb. The binary needs nothing from the machine: JavaFX's own
# native libraries ride inside it as resources and are extracted on first run. Logs go to target/native/.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
CHECKS="java -cp $(cat $ROOT/checks/out/classpath.txt 2>/dev/null) everyui.checks.Checks"   # staged by the sbt step below
CFG="$ROOT/desktop/native-config"
OUT="$ROOT/desktop/out/native/everyui-desktop"
LOGS="$ROOT/target/native"
FREEZE=0.434
xvfb() { xvfb-run -a -s "-screen 0 1920x1080x24" "$@"; }
mkdir -p "$LOGS" "$(dirname "$OUT")"

TRACE=0
for arg in "$@"; do
  case "$arg" in
    --trace) TRACE=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

step() { printf '%-46s ' "$1"; }
pass() { echo "PASS  ${1:-}"; }
fail() { echo "FAIL  ${1:-}"; FAILED=1; }
FAILED=0

command -v native-image > /dev/null || { echo "native-image not found (GraalVM)"; exit 1; }
command -v xvfb-run > /dev/null || { echo "xvfb-run not found"; exit 1; }

step "compile: desktop classpath"
if "$SBT" "checks/checksClasspath; desktop/desktopClasspath" > "$LOGS/sbt.log" 2>&1; then pass; else fail "(target/native/sbt.log)"; exit 1; fi
CP=$(cat desktop/out/classpath.txt)

if [ $TRACE = 1 ]; then
  step "trace: native-image agent (frozen + live)"
  rm -rf "$CFG"; mkdir -p "$CFG"
  xvfb java -agentlib:native-image-agent=config-output-dir="$CFG" -Dprism.order=sw -Dprism.lcdtext=false \
    -cp "$CP" everyui.desktop.DesktopApp --freeze=$FREEZE --screenshot="$LOGS/agent-frame.png" > "$LOGS/agent-freeze.log" 2>&1
  # the live run is stopped by the clock, so the agent cannot finish its merge cleanly: its lock is a leftover
  timeout 12 xvfb-run -a -s "-screen 0 1920x1080x24" java -agentlib:native-image-agent=config-merge-dir="$CFG" -Dprism.order=sw -Dprism.lcdtext=false \
    -cp "$CP" everyui.desktop.DesktopApp > "$LOGS/agent-live.log" 2>&1
  rm -f "$CFG/.lock"
  if [ -s "$CFG/reachability-metadata.json" ]; then pass "$(wc -c < "$CFG/reachability-metadata.json") bytes of metadata"; else fail; exit 1; fi
fi

step "build: native-image"
# -Os: 23.5 MB against -O2's 36.8, at the same frame rate
if native-image -cp "$CP" -H:ConfigurationFileDirectories="$CFG" -Os --enable-native-access=ALL-UNNAMED \
     -o "$OUT" everyui.desktop.DesktopApp > "$LOGS/build.log" 2>&1 && [ -x "$OUT" ]; then
  pass "$(du -h "$OUT" | cut -f1), $(grep -oE 'in [0-9.]+s' "$LOGS/build.log" | tail -n 1)"
else
  fail "(target/native/build.log)"; grep -E "^Error|error:" "$LOGS/build.log" | head -n 10; exit 1
fi

step "frame vs golden (Xvfb)"
timeout 120 xvfb-run -a -s "-screen 0 1920x1080x24" "$OUT" --freeze=$FREEZE --screenshot="$LOGS/frame.png" > "$LOGS/freeze.log" 2>&1
if $CHECKS extract-tree "$LOGS/freeze.log" "$LOGS/tree.txt" > "$LOGS/frame-check.log" 2>&1 &&
   $CHECKS compare-tree tools/golden/animation-tree.txt "$LOGS/tree.txt" >> "$LOGS/frame-check.log" 2>&1; then
  pass "$(tail -n 1 "$LOGS/frame-check.log")"
else
  fail "(target/native/frame-check.log)"; tail -n 10 "$LOGS/frame-check.log"
fi

step "live frame rate and motion (Xvfb, 20 s)"
# the app runs inside the Xvfb session; two screenshots of that display a second apart must differ
xvfb-run -a -s "-screen 0 1920x1080x24" bash -c "\"$OUT\" > \"$LOGS/live.log\" 2>&1 & pid=\$!; sleep 8; magick import -window root \"$LOGS/live-1.png\"; sleep 1; magick import -window root \"$LOGS/live-2.png\"; sleep 11; kill \$pid"
if $CHECKS fps "$LOGS/live.log" --samples-min 12 --median-min 59 --min 55 > "$LOGS/fps.log" 2>&1 &&
   $CHECKS motion "$LOGS/live-1.png" "$LOGS/live-2.png" >> "$LOGS/fps.log" 2>&1; then
  pass "$(tail -n 2 "$LOGS/fps.log" | tr '\n' ' ')"
else
  fail "(target/native/fps.log)"; tail -n 5 "$LOGS/fps.log"
fi

exit $FAILED
