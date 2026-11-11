#!/usr/bin/env bash
# everyui on an Apple simulator: emit the app as Swift, build it into an app bundle on the Mac with Xcode's
# toolchain (no Xcode project), install and launch it on the simulator, and read its console.
#
#   tools/tvos-run.sh                 run live for LIVE_SECONDS (30), report the frame rate
#   tools/tvos-run.sh --freeze        freeze the frame at 0.434s, compare it with tools/golden/animation-tree.txt
#                                     (on a phone, laid out at a 1920x1080 viewport)
#   tools/tvos-run.sh --console       run for LIVE_SECONDS with EXTRA_ARGS on the launch and print the console
#   tools/tvos-run.sh --show          also open the Simulator window on the Mac
#   tools/tvos-run.sh --ios           an iPhone simulator instead of the Apple TV
#
# Environment: MAC (ssh host, default mbp), MAC_DIR (default ~/everyui-spike), TVOS_DEVICE (default
# "Apple TV 4K (3rd generation) (at 1080p)"), IOS_DEVICE (default "iPhone 17 Pro"), LIVE_SECONDS. Logs and the
# screenshot go to target/tvos/ (target/ios/ with --ios).
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
MAC=${MAC:-mbp}
MAC_DIR=${MAC_DIR:-everyui-spike}
TVOS_DEVICE=${TVOS_DEVICE:-"Apple TV 4K (3rd generation) (at 1080p)"}
IOS_DEVICE=${IOS_DEVICE:-"iPhone 17 Pro"}
LIVE_SECONDS=${LIVE_SECONDS:-30}
SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
CHECKS="java -cp $(cat $ROOT/checks/out/classpath.txt 2>/dev/null) everyui.checks.Checks"   # staged by the sbt step below
SSH="ssh -o VisualHostKey=no"
BUNDLE=com.outr.everyui
FREEZE=0.434
MODE=live
SHOW=0
PLATFORM=tvos
for arg in "$@"; do
  case "$arg" in
    --freeze) MODE=freeze ;;
    --console) MODE=console ;;   # run for LIVE_SECONDS with EXTRA_ARGS on the launch and print the console (the durable demo)
    --show) SHOW=1 ;;
    --ios) PLATFORM=ios ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done
# per platform: the simulator SDK, the target triple's OS, the device, the app's device family (1 iPhone, 2 iPad, 3 TV)
# and the app icon set in swift/app/Assets.xcassets (sbt makeIcons), compiled into the bundle by actool
if [ $PLATFORM = ios ]; then SDK=iphonesimulator; OS=ios; DEVICE=$IOS_DEVICE; FAMILY='[1,2]'; APPICON=AppIcon; TARGETS="--target-device iphone --target-device ipad"
else SDK=appletvsimulator; OS=tvos; DEVICE=$TVOS_DEVICE; FAMILY='[3]'; APPICON='App Icon & Top Shelf Image'; TARGETS="--target-device tv"; fi
LOGS="$ROOT/target/$PLATFORM"
rm -rf "$LOGS"
mkdir -p "$LOGS"

step() { printf '%-46s ' "$1"; }
pass() { echo "PASS  ${1:-}"; }
fail() { echo "FAIL  ${1:-}"; FAILED=1; }
FAILED=0

step "emit: everyui → Swift"
if "$SBT" "checks/checksClasspath; tvos/emitSwift" > "$LOGS/emit.log" 2>&1 && [ -f tvos/out-swift/App.swift ]; then
  todos=$(grep -c 'RtTodo(' tvos/out-swift/App.swift || true)
  pass "$(wc -l < tvos/out-swift/App.swift) lines, $todos todo(s)"
else
  fail "(target/tvos/emit.log)"; tail -n 20 "$LOGS/emit.log"; exit 1
fi

APP="$MAC_DIR/app-$PLATFORM"
step "ship to $MAC:$APP"
$SSH "$MAC" "mkdir -p $APP" > /dev/null 2>&1
if rsync -a --delete tvos/out-swift/ "$MAC:$APP/src/" > "$LOGS/ship.log" 2>&1; then pass; else fail; exit 1; fi

step "build: EveryUI.app ($OS simulator, arm64)"
SDK_VERSION=$($SSH "$MAC" "xcrun --sdk $SDK --show-sdk-version" | tr -d '\r')
$SSH "$MAC" "cd $APP && rm -rf EveryUI.app && mkdir -p EveryUI.app && \
  xcrun -sdk $SDK swiftc -O -suppress-warnings -target arm64-apple-${OS}${SDK_VERSION}-simulator \
    -framework UIKit -framework CoreText -framework QuartzCore \
    src/ScalaRuntime.swift src/UniversalRuntime.swift src/App.swift src/main.swift -o EveryUI.app/EveryUI 2>&1 | grep -v '^\$'; \
  test -x EveryUI.app/EveryUI && cp src/Info.plist EveryUI.app/ && cp -r src/images src/fonts EveryUI.app/ && \
  { cp src/*.html EveryUI.app/ 2>/dev/null; true; } && \
  plutil -replace UIDeviceFamily -json '$FAMILY' EveryUI.app/Info.plist && \
  xcrun actool src/Assets.xcassets --compile EveryUI.app --platform $SDK $TARGETS --minimum-deployment-target 15.0 \
    --app-icon '$APPICON' --output-partial-info-plist icons.plist > actool.log 2>&1 && \
  /usr/libexec/PlistBuddy -c 'Merge icons.plist' EveryUI.app/Info.plist && \
  codesign --force --sign - EveryUI.app > /dev/null 2>&1; test -x EveryUI.app/EveryUI" > "$LOGS/build.log" 2>&1
if [ $? = 0 ]; then pass "$OS $SDK_VERSION"; else fail "(target/$PLATFORM/build.log)"; grep -E "error:" "$LOGS/build.log" | head -n 20; exit 1; fi

step "boot: $DEVICE"
UDID=$($SSH "$MAC" "xcrun simctl list devices available" | grep -F "$DEVICE (" | grep -oE '[0-9A-F-]{36}' | head -n 1)
[ -n "$UDID" ] || { fail "no such simulator"; exit 1; }
$SSH "$MAC" "xcrun simctl boot $UDID" > /dev/null 2>&1 || true
if [ $SHOW = 1 ]; then $SSH "$MAC" "open -a Simulator --args -CurrentDeviceUDID $UDID" > /dev/null 2>&1 || true; fi
if $SSH "$MAC" "xcrun simctl list devices" | grep -F "$UDID" | grep -q Booted; then pass "$UDID"; else fail; exit 1; fi

step "install + launch ($MODE)"
ARGS=""
[ $MODE = freeze ] && ARGS="--freeze=$FREEZE --viewport=1920x1080"
[ -n "${EXTRA_ARGS:-}" ] && ARGS="$ARGS $EXTRA_ARGS"
SECONDS_TO_RUN=$LIVE_SECONDS
[ $MODE = freeze ] && SECONDS_TO_RUN=12
# macOS has no `timeout`: the launch (which stays attached to the app's console) runs in the background for
# SECONDS_TO_RUN; with --show the app is left running on the simulator afterwards.
STOP="xcrun simctl terminate $UDID $BUNDLE > /dev/null 2>&1"
[ $SHOW = 1 ] && [ $MODE = live ] && STOP="true"
$SSH "$MAC" "cd $APP && xcrun simctl terminate $UDID $BUNDLE > /dev/null 2>&1; xcrun simctl install $UDID EveryUI.app && \
  (nohup xcrun simctl launch --console-pty $UDID $BUNDLE $ARGS > console.log 2>&1 &) ; sleep $SECONDS_TO_RUN; \
  xcrun simctl io $UDID screenshot screen.png > /dev/null 2>&1; sleep 1; xcrun simctl io $UDID screenshot screen2.png > /dev/null 2>&1; $STOP; \
  cat console.log" > "$LOGS/console.log" 2>&1
scp -q "$MAC:$APP/screen.png" "$LOGS/screen.png" 2>/dev/null; scp -q "$MAC:$APP/screen2.png" "$LOGS/screen2.png" 2>/dev/null
if grep -aq '\[ui\] root' "$LOGS/console.log"; then pass "$(grep -aoE '\[ui\] root .*' "$LOGS/console.log")"; else fail "(target/$PLATFORM/console.log)"; tail -n 15 "$LOGS/console.log"; exit 1; fi
if [ $MODE = console ]; then grep -a '\[ui\]' "$LOGS/console.log"; exit 0; fi

if [ $MODE = freeze ]; then
  step "frame vs golden"
  if $CHECKS extract-tree "$LOGS/console.log" "$LOGS/tvos-tree.txt" > "$LOGS/frame.log" 2>&1 &&
     $CHECKS compare-tree tools/golden/animation-tree.txt "$LOGS/tvos-tree.txt" >> "$LOGS/frame.log" 2>&1; then
    pass "$(tail -n 1 "$LOGS/frame.log")"
  else
    fail "(target/$PLATFORM/frame.log)"; tail -n 20 "$LOGS/frame.log"
  fi
else
  step "live frame rate"
  if $CHECKS fps "$LOGS/console.log" --samples-min 12 --median-min 59 --min 55 > "$LOGS/fps.log" 2>&1; then
    pass "$(tail -n 1 "$LOGS/fps.log")"
  else
    fail "(target/$PLATFORM/fps.log)"; tail -n 5 "$LOGS/fps.log"
  fi
  step "motion (two screenshots a second apart)"
  if $CHECKS motion "$LOGS/screen.png" "$LOGS/screen2.png" > "$LOGS/motion.log" 2>&1; then pass "$(tail -n 1 "$LOGS/motion.log")"; else fail "(target/$PLATFORM/motion.log)"; tail -n 2 "$LOGS/motion.log"; fi
  grep -aE '\[ui\] collect' "$LOGS/console.log" | tail -n 3 | sed 's/^/    /'
fi
grep -aE 'uncaught|not found|todo' "$LOGS/console.log" | head -n 5 | sed 's/^/    /'
echo "screenshot: target/$PLATFORM/screen.png"
exit $FAILED
