#!/usr/bin/env bash
# universal-ui on the Apple TV simulator: emit the app as Swift, build it into an app bundle on the Mac with
# Xcode's toolchain (no Xcode project), install and launch it on the simulator, and read its console.
#
#   tools/tvos-run.sh                 run live for LIVE_SECONDS (30), report the frame rate
#   tools/tvos-run.sh --freeze        freeze the frame at 0.434s, compare it with tools/golden/animation-tree.txt
#   tools/tvos-run.sh --show          also open the Simulator window on the Mac
#
# Environment: MAC (ssh host, default mbp), MAC_DIR (default ~/universal-ui-spike), TVOS_DEVICE (default
# "Apple TV 4K (3rd generation) (at 1080p)"), LIVE_SECONDS. Logs and the screenshot go to target/tvos/.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
MAC=${MAC:-mbp}
MAC_DIR=${MAC_DIR:-universal-ui-spike}
TVOS_DEVICE=${TVOS_DEVICE:-"Apple TV 4K (3rd generation) (at 1080p)"}
LIVE_SECONDS=${LIVE_SECONDS:-30}
SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
SSH="ssh -o VisualHostKey=no"
BUNDLE=com.outr.universalui
FREEZE=0.434
LOGS="$ROOT/target/tvos"
rm -rf "$LOGS"
mkdir -p "$LOGS"

MODE=live
SHOW=0
for arg in "$@"; do
  case "$arg" in
    --freeze) MODE=freeze ;;
    --show) SHOW=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

step() { printf '%-46s ' "$1"; }
pass() { echo "PASS  ${1:-}"; }
fail() { echo "FAIL  ${1:-}"; FAILED=1; }
FAILED=0

step "emit: universal-ui → Swift"
if "$SBT" "tvos/emitSwift" > "$LOGS/emit.log" 2>&1 && [ -f tvos/out-swift/App.swift ]; then
  todos=$(grep -c 'RtTodo(' tvos/out-swift/App.swift || true)
  pass "$(wc -l < tvos/out-swift/App.swift) lines, $todos todo(s)"
else
  fail "(target/tvos/emit.log)"; tail -n 20 "$LOGS/emit.log"; exit 1
fi

step "ship to $MAC:$MAC_DIR/app"
if rsync -a --delete tvos/out-swift/ "$MAC:$MAC_DIR/app/src/" > "$LOGS/ship.log" 2>&1; then pass; else fail; exit 1; fi

step "build: UniversalUI.app (tvOS simulator, arm64)"
SDK_VERSION=$($SSH "$MAC" "xcrun --sdk appletvsimulator --show-sdk-version" | tr -d '\r')
$SSH "$MAC" "cd $MAC_DIR/app && rm -rf UniversalUI.app && mkdir -p UniversalUI.app && \
  xcrun -sdk appletvsimulator swiftc -O -suppress-warnings -target arm64-apple-tvos${SDK_VERSION}-simulator \
    -framework UIKit -framework CoreText -framework QuartzCore \
    src/ScalaRuntime.swift src/UniversalRuntime.swift src/App.swift src/main.swift -o UniversalUI.app/UniversalUI 2>&1 | grep -v '^\$'; \
  test -x UniversalUI.app/UniversalUI && cp src/Info.plist UniversalUI.app/ && cp -r src/images src/fonts UniversalUI.app/ && \
  codesign --force --sign - UniversalUI.app > /dev/null 2>&1; test -x UniversalUI.app/UniversalUI" > "$LOGS/build.log" 2>&1
if [ $? = 0 ]; then pass "tvOS $SDK_VERSION"; else fail "(target/tvos/build.log)"; grep -E "error:" "$LOGS/build.log" | head -n 20; exit 1; fi

step "boot: $TVOS_DEVICE"
UDID=$($SSH "$MAC" "xcrun simctl list devices available" | grep -F "$TVOS_DEVICE (" | grep -oE '[0-9A-F-]{36}' | head -n 1)
[ -n "$UDID" ] || { fail "no such simulator"; exit 1; }
$SSH "$MAC" "xcrun simctl boot $UDID" > /dev/null 2>&1 || true
if [ $SHOW = 1 ]; then $SSH "$MAC" "open -a Simulator --args -CurrentDeviceUDID $UDID" > /dev/null 2>&1 || true; fi
if $SSH "$MAC" "xcrun simctl list devices" | grep -F "$UDID" | grep -q Booted; then pass "$UDID"; else fail; exit 1; fi

step "install + launch ($MODE)"
ARGS=""
[ $MODE = freeze ] && ARGS="--freeze=$FREEZE"
SECONDS_TO_RUN=$LIVE_SECONDS
[ $MODE = freeze ] && SECONDS_TO_RUN=12
# macOS has no `timeout`: the launch (which stays attached to the app's console) runs in the background for
# SECONDS_TO_RUN; with --show the app is left running on the simulator afterwards.
STOP="xcrun simctl terminate $UDID $BUNDLE > /dev/null 2>&1"
[ $SHOW = 1 ] && [ $MODE = live ] && STOP="true"
$SSH "$MAC" "cd $MAC_DIR/app && xcrun simctl terminate $UDID $BUNDLE > /dev/null 2>&1; xcrun simctl install $UDID UniversalUI.app && \
  (nohup xcrun simctl launch --console-pty $UDID $BUNDLE $ARGS > console.log 2>&1 &) ; sleep $SECONDS_TO_RUN; \
  xcrun simctl io $UDID screenshot screen.png > /dev/null 2>&1; $STOP; \
  cat console.log" > "$LOGS/console.log" 2>&1
scp -q "$MAC:$MAC_DIR/app/screen.png" "$LOGS/screen.png" 2>/dev/null
if grep -aq '\[ui\] root' "$LOGS/console.log"; then pass "$(grep -aoE '\[ui\] root [0-9x]+ scale [0-9.]+' "$LOGS/console.log")"; else fail "(target/tvos/console.log)"; tail -n 15 "$LOGS/console.log"; exit 1; fi

if [ $MODE = freeze ]; then
  step "frame vs golden"
  if python3 tools/checks.py extract-tree "$LOGS/console.log" "$LOGS/tvos-tree.txt" > "$LOGS/frame.log" 2>&1 &&
     python3 tools/checks.py compare-tree tools/golden/animation-tree.txt "$LOGS/tvos-tree.txt" >> "$LOGS/frame.log" 2>&1; then
    pass "$(tail -n 1 "$LOGS/frame.log")"
  else
    fail "(target/tvos/frame.log)"; tail -n 20 "$LOGS/frame.log"
  fi
else
  step "live frame rate"
  if python3 tools/checks.py fps "$LOGS/console.log" --median-min 59 --min 55 > "$LOGS/fps.log" 2>&1; then
    pass "$(tail -n 1 "$LOGS/fps.log")"
  else
    fail "(target/tvos/fps.log)"; tail -n 5 "$LOGS/fps.log"
  fi
  grep -aE '\[ui\] collect' "$LOGS/console.log" | tail -n 3 | sed 's/^/    /'
fi
grep -aE 'uncaught|not found|todo' "$LOGS/console.log" | head -n 5 | sed 's/^/    /'
echo "screenshot: target/tvos/screen.png"
exit $FAILED
