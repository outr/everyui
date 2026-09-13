#!/usr/bin/env bash
# Every everyui check, in one command.
#
#   tools/check.sh             offline: reactify on the JVM matches its golden; all three Roku channels compile with no
#                              gaps and no brighterscript errors; the shared font table matches the bundled fonts; and
#                              youi's AnimationExample, frozen at the same moment, has the geometry in
#                              tools/golden/animation-tree.txt (every component's position, size and rotation, within
#                              1px) on the web (headless Chrome) and the desktop (JavaFX, under Xvfb)
#   tools/check.sh --live      also the web build's live frame rate, in a real (headful) Chrome on a virtual
#                              1920x1080 display via RoboBrowser — headless Chrome throttles requestAnimationFrame
#   tools/check.sh --native    also the desktop as a GraalVM native image: build it, the same frame, the frame rate
#   tools/check.sh --android   also on Android: build the APK, run it on an emulator, compare the same frame
#   tools/check.sh --device    also on the Roku: reactify acceptance, the same frame, and the live frame rate
#   tools/check.sh --tvos      also on the Apple TV simulator (via the Mac, ssh mbp): the same frame, and the live
#                              frame rate — tools/tvos-run.sh
#   tools/check.sh --update    rewrite the golden from the web's frame, after an intended change
#
# Every backend is compared with the ONE golden: identical geometry on every platform is the point.
#
# Environment: SBT, BSC (brighterscript's cli.js), CHROME; ANDROID_SERIAL (default: the first emulator on adb); for
# --device ROKU_PASS (and ROKU_IP); for --live and --device FPS_SECONDS (60), FPS_MEDIAN_MIN (59.5), FPS_MIN (58.5).
# Logs, and each backend's screenshot of the frozen frame, go to target/check/.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

ANDROID=0
NATIVE=0
TVOS=0
LIVE=0
DEVICE=0
UPDATE=0
for arg in "$@"; do
  case "$arg" in
    --android) ANDROID=1 ;;
    --native) NATIVE=1 ;;
    --tvos) TVOS=1 ;;
    --live) LIVE=1 ;;
    --device) DEVICE=1 ;;
    --update) UPDATE=1 ;;
    -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) echo "unknown option: $arg (see --help)" >&2; exit 2 ;;
  esac
done

SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
BSC=${BSC:-/home/mhicks/projects/clients/nabo/app/roku/node_modules/brighterscript/dist/cli.js}
CHROME=${CHROME:-$(command -v google-chrome || command -v chromium || echo google-chrome)}
FREEZE=0.434                      # the moment of youi's reference capture
LOGS="$ROOT/target/check"
GOLDEN="$ROOT/tools/golden/animation-tree.txt"
CHECKS="python3 $ROOT/tools/checks.py"
rm -rf "$LOGS"
mkdir -p "$LOGS" "$(dirname "$GOLDEN")"

RESULTS=()
FAILED=0
STARTED=$(date +%s)

# run NAME LOG FUNCTION: run a check, print PASS/FAIL, keep its output in target/check/LOG
run() {
  local name=$1 log=$2 fn=$3
  printf '%-50s ' "$name"
  "$fn" > "$LOGS/$log" 2>&1
  local rc=$?
  if [ $rc = 0 ]; then
    echo "PASS"
    RESULTS+=("PASS  $name")
  else
    echo "FAIL   (target/check/$log)"
    tail -n 15 "$LOGS/$log" | sed 's/^/    /'
    RESULTS+=("FAIL  $name   (target/check/$log)")
    FAILED=1
  fi
  return $rc
}

skip() {
  printf '%-50s SKIP   (%s)\n' "$1" "$2"
  RESULTS+=("SKIP  $1   ($2)")
}

# frame BACKEND CONSOLE_LOG: extract the frozen frame and compare it with the golden (which --update writes from the web)
frame() {
  local backend=$1 console=$2
  $CHECKS extract-tree "$console" "$LOGS/$backend-tree.txt" || return 1
  if [ $UPDATE = 1 ] && [ "$backend" = web ]; then
    cp "$LOGS/$backend-tree.txt" "$GOLDEN"
    echo "golden written: tools/golden/animation-tree.txt"
  else
    [ -f "$GOLDEN" ] || { echo "no golden yet: run tools/check.sh --update"; return 1; }
    $CHECKS compare-tree "$GOLDEN" "$LOGS/$backend-tree.txt"
  fi
}

# ── offline ──────────────────────────────────────────────────────────────────

faces() { $CHECKS faces assets/fonts shared/src/main/scala/everyui/font/Faces.scala; }

build() {                          # one sbt run: the JVM suite, every Roku channel, the desktop (and web checker) classpath
  local live=""
  [ $LIVE = 1 ] && live="; webcheck/webcheckClasspath"
  "$SBT" "reactiveTest/run; reactive/emitRoku; reactiveTest/emitRoku; roku/emitRoku; desktop/desktopClasspath$live" &&
    [ "$(grep -c 'emitRoku: emitting' "$LOGS/build.log")" = 3 ]
}

jvm_suite() {
  grep -oE 'RX .*' "$LOGS/build.log" > "$LOGS/jvm-suite.txt"
  diff -u reactive-test/expected-output.txt "$LOGS/jvm-suite.txt" &&
    echo "identical to reactive-test/expected-output.txt ($(wc -l < "$LOGS/jvm-suite.txt") lines)"
}

gaps() {
  local rc=0 out
  for d in reactive reactive-test roku; do
    out=$(python3 tools/emit-gaps.py "$d/out-roku/components")
    echo "$d: $(echo "$out" | tail -n 1)"
    echo "$out" | tail -n 1 | grep -qE 'TOTAL GAPS +: 0$' || { echo "$out"; rc=1; }
  done
  return $rc
}

brighterscript() {
  [ -f "$BSC" ] || { echo "brighterscript not found at $BSC (set BSC to its dist/cli.js)"; return 1; }
  local rc=0 stage errors count
  for d in reactive reactive-test roku; do
    stage=$(mktemp -d)
    # BS1054 (`: if` one-liners in the runtime) and BS1104 (a local named like a function) are brighterscript
    # strictness the Roku itself accepts.
    errors=$(node "$BSC" --rootDir "$d/out-roku" --stagingDir "$stage" --createPackage false 2>&1 |
      grep -E ' - error ' | grep -vE 'BS1054|BS1104')
    rm -rf "$stage"
    count=$([ -z "$errors" ] && echo 0 || echo "$errors" | wc -l)
    echo "$d: $count error(s)"
    [ "$count" = 0 ] || { echo "$errors" | head -n 20; rc=1; }
  done
  return $rc
}

web_build() {
  mkdir -p web/out-js &&
    cp web/index.html web/out-js/ &&
    cp -r assets/images assets/fonts web/out-js/ &&
    scala-cli --power package web -o web/out-js/main.js -f
}

web_frame() {
  local page="file://$ROOT/web/out-js/index.html?freeze=$FREEZE"
  local chrome=("$CHROME" --headless=new --disable-gpu --no-sandbox --force-device-scale-factor=1
    --virtual-time-budget=3000)
  # The page lays out in the window minus 87px of browser UI, but a screenshot is taken at the full window size, and
  # the page re-lays out to that before it is captured. So the frame is logged from a 1920x1167 window (a 1080px
  # viewport) and photographed from a 1920x1080 one (re-laid out to 1080px): the same frozen frame both ways.
  "${chrome[@]}" --window-size=1920,1167 --enable-logging=stderr --v=0 --dump-dom "$page" \
    > /dev/null 2> "$LOGS/web-console.log"
  "${chrome[@]}" --window-size=1920,1080 --screenshot="$LOGS/web-frame.png" "$page" > /dev/null 2>&1
  frame web "$LOGS/web-console.log"
}

web_live() {
  command -v Xvfb > /dev/null || { echo "Xvfb not found"; return 1; }
  local seconds=${FPS_SECONDS:-60}
  timeout $(( seconds + 120 )) java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
    -cp "$(cat webcheck/out/classpath.txt)" everyui.webcheck.WebLive \
    "file://$ROOT/web/out-js/index.html" "$seconds" "$LOGS/web-live.png" > "$LOGS/web-live-console.log" 2>&1
  grep -h '\[webcheck\] viewport' "$LOGS/web-live-console.log"
  $CHECKS fps "$LOGS/web-live-console.log" --median-min "${FPS_MEDIAN_MIN:-59.5}" --min "${FPS_MIN:-58.5}" ||
    { echo "--- console:"; tail -n 20 "$LOGS/web-live-console.log"; return 1; }
}

desktop_frame() {
  command -v xvfb-run > /dev/null || { echo "xvfb-run not found"; return 1; }
  timeout 120 xvfb-run -a -s "-screen 0 1920x1080x24" \
    java -Dprism.order=sw -Dprism.lcdtext=false -cp "$(cat desktop/out/classpath.txt)" everyui.desktop.DesktopApp \
    --freeze="$FREEZE" --screenshot="$LOGS/desktop-frame.png" > "$LOGS/desktop-console.log" 2>&1
  frame desktop "$LOGS/desktop-console.log" || { echo "--- desktop console:"; tail -n 20 "$LOGS/desktop-console.log"; return 1; }
}

# ── desktop, native image ────────────────────────────────────────────────────

desktop_native() { tools/desktop-native.sh; }

# ── Android ──────────────────────────────────────────────────────────────────

android_build() { "$SBT" "android/packageApk"; }

android_frame() {
  local serial=${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}
  [ -n "$serial" ] || { echo "no Android emulator on adb (start one, or set ANDROID_SERIAL)"; return 1; }
  echo "device: $serial ($(adb -s "$serial" shell getprop ro.product.model | tr -d '\r'))"
  adb -s "$serial" install -r android/out-apk/everyui.apk || return 1
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am start -S -W -n com.outr.everyui/everyui.activity.MainActivity \
    --es freeze "$FREEZE" --es viewport 1920x1080 || return 1
  $CHECKS android-wait "$serial" "$LOGS/android-console.log" || return 1
  adb -s "$serial" exec-out screencap -p > "$LOGS/android-frame.png"
  frame android "$LOGS/android-console.log"
}

# ── tvOS ─────────────────────────────────────────────────────────────────────

tvos_frame() { tools/tvos-run.sh --freeze; }
tvos_fps() { LIVE_SECONDS="${FPS_SECONDS:-60}" tools/tvos-run.sh; }

# ── Roku ─────────────────────────────────────────────────────────────────────

acceptance() { tools/roku-acceptance.sh; }

roku_frame() {
  $CHECKS roku-install roku/out-roku --add "freeze.txt=$FREEZE" &&
    $CHECKS roku-console 35 "$LOGS/roku-frame-console.log" &&
    frame roku "$LOGS/roku-frame-console.log"
}

roku_fps() {
  $CHECKS roku-install roku/out-roku &&
    $CHECKS roku-console "${FPS_SECONDS:-60}" "$LOGS/roku-fps-console.log" &&
    $CHECKS fps "$LOGS/roku-fps-console.log" --median-min "${FPS_MEDIAN_MIN:-59.5}" --min "${FPS_MIN:-58.5}"
}

# ── run ──────────────────────────────────────────────────────────────────────

run "fonts: shared table matches assets/fonts" faces.log faces

if run "build: JVM suite, Roku channels, desktop" build.log build; then
  run "reactify on the JVM matches its golden" jvm-suite.log jvm_suite
  run "Roku channels: no emit gaps" gaps.log gaps
  run "Roku channels: brighterscript clean" brighterscript.log brighterscript
  BUILT=1
else
  for s in "reactify on the JVM matches its golden" "Roku channels: no emit gaps" "Roku channels: brighterscript clean"; do
    skip "$s" "build failed"
  done
  BUILT=0
fi

if run "web bundle builds" web-build.log web_build; then
  run "web: AnimationExample frame" web-frame.log web_frame
  WEB_BUILT=1
else
  skip "web: AnimationExample frame" "web bundle did not build"
  WEB_BUILT=0
fi

if [ $LIVE = 1 ]; then
  if [ $BUILT = 1 ] && [ $WEB_BUILT = 1 ]; then
    run "web: live frame rate (RoboBrowser)" web-live.log web_live
  else
    skip "web: live frame rate (RoboBrowser)" "build failed"
  fi
fi

if [ $BUILT = 1 ]; then
  run "desktop: AnimationExample frame" desktop-frame.log desktop_frame
else
  skip "desktop: AnimationExample frame" "build failed"
fi

if [ $NATIVE = 1 ]; then
  run "desktop: native image builds, frame, frame rate" desktop-native.log desktop_native
fi

if [ $ANDROID = 1 ]; then
  if run "Android: APK builds" android-build.log android_build; then
    run "Android: AnimationExample frame" android-frame.log android_frame
  else
    skip "Android: AnimationExample frame" "APK did not build"
  fi
fi

if [ $TVOS = 1 ]; then
  run "tvOS: AnimationExample frame (simulator)" tvos-frame.log tvos_frame
  run "tvOS: live frame rate (simulator)" tvos-fps.log tvos_fps
fi

if [ $DEVICE = 1 ]; then
  if [ -z "${ROKU_PASS:-}" ]; then
    for s in "Roku: reactify acceptance" "Roku: AnimationExample frame" "Roku: live frame rate"; do
      skip "$s" "ROKU_PASS not set"
    done
    FAILED=1
  elif [ $BUILT = 1 ]; then
    run "Roku: reactify acceptance" acceptance.log acceptance
    run "Roku: AnimationExample frame" roku-frame.log roku_frame
    run "Roku: live frame rate" roku-fps.log roku_fps
  else
    for s in "Roku: reactify acceptance" "Roku: AnimationExample frame" "Roku: live frame rate"; do
      skip "$s" "build failed"
    done
  fi
fi

echo
echo "── summary ($(( $(date +%s) - STARTED ))s) ──"
printf '%s\n' "${RESULTS[@]}"
[ $UPDATE = 1 ] && echo "(the golden was rewritten from the web's frame: review tools/golden/ before committing)"
exit $FAILED
