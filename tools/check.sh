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
# the helpers are a Scala CLI (everyui.checks.Checks), run from the classpath the build stages
. "$ROOT/tools/durable.sh"           # the network module's check: server_start, durable_web … (needs $CHECKS)

checks_cli() { CHECKS="java -cp $(cat "$ROOT/checks/out/classpath.txt") everyui.checks.Checks"; }
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
  # (webcheck: the web checks run the page in a real Chrome via RoboBrowser — the live frame rate and the durable demo)

  "$SBT" "checks/checksClasspath; reactiveTest/run; network/run; reactive/emitRoku; reactiveTest/emitRoku; roku/emitRoku; network/emitRoku; desktop/desktopClasspath; server/serverClasspath; webcheck/webcheckClasspath" &&
    [ "$(grep -c 'emitRoku: emitting' "$LOGS/build.log")" = 4 ]
}

# suite_lines LOG MARKER OUT: one program's RX lines from the build log — from the line containing MARKER to its RX DONE
suite_lines() { awk -v m="$2" 'index($0, "RX ") && index($0, m) { p = 1 } p && match($0, /RX .*/) { print substr($0, RSTART) } p && /RX DONE/ { exit }' "$1" > "$3"; }

jvm_suite() {
  suite_lines "$LOGS/build.log" "var.initial" "$LOGS/jvm-suite.txt"
  diff -u reactive-test/expected-output.txt "$LOGS/jvm-suite.txt" &&
    echo "identical to reactive-test/expected-output.txt ($(wc -l < "$LOGS/jvm-suite.txt") lines)"
}

# the network probe: spice's durable client over a loopback socket, the JVM run is its golden for the devices
jvm_probe() {
  suite_lines "$LOGS/build.log" "socket.open" "$LOGS/jvm-probe.txt"
  diff -u network/expected-output.txt "$LOGS/jvm-probe.txt" &&
    echo "identical to network/expected-output.txt ($(wc -l < "$LOGS/jvm-probe.txt") lines)"
}

gaps() {
  local rc=0 out
  for d in reactive reactive-test roku network; do
    out=$($CHECKS gaps "$d/out-roku/components")
    echo "$d: $(echo "$out" | tail -n 1)"
    echo "$out" | tail -n 1 | grep -qE 'TOTAL GAPS +: 0$' || { echo "$out"; rc=1; }
  done
  return $rc
}

brighterscript() {
  [ -f "$BSC" ] || { echo "brighterscript not found at $BSC (set BSC to its dist/cli.js)"; return 1; }
  local rc=0 stage errors count
  for d in reactive reactive-test roku network; do
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
    "file://$ROOT/web/out-js/index.html" "$seconds" "$LOGS/web-live.png" "$LOGS/web-live-earlier.png" > "$LOGS/web-live-console.log" 2>&1
  grep -h '\[webcheck\] viewport' "$LOGS/web-live-console.log"
  $CHECKS fps "$LOGS/web-live-console.log" --median-min "${FPS_MEDIAN_MIN:-59.5}" --min "${FPS_MIN:-58.5}" ||
    { echo "--- console:"; tail -n 20 "$LOGS/web-live-console.log"; return 1; }
  $CHECKS motion "$LOGS/web-live-earlier.png" "$LOGS/web-live.png"
}

desktop_frame() {
  command -v xvfb-run > /dev/null || { echo "xvfb-run not found"; return 1; }
  timeout 120 xvfb-run -a -s "-screen 0 1920x1080x24" \
    java -Dprism.order=sw -Dprism.lcdtext=false -cp "$(cat desktop/out/classpath.txt)" everyui.desktop.DesktopApp \
    --freeze="$FREEZE" --screenshot="$LOGS/desktop-frame.png" > "$LOGS/desktop-console.log" 2>&1
  frame desktop "$LOGS/desktop-console.log" || { echo "--- desktop console:"; tail -n 20 "$LOGS/desktop-console.log"; return 1; }
}

# ── input: the input demo on every backend ──────────────────────────────────
# The self-driven probe must print the same line everywhere; on the web and Android, real events must reach the same
# channels: the arrows and Enter (a keyboard, a remote in a browser, a D-pad), a click or a tap on the third box.
INPUT_OK="input ok focus=a focus=b click=b focus=a"

input_result() {
  grep -q "\[ui\] $INPUT_OK" "$1" || { echo "expected: $INPUT_OK"; grep "\[ui\]" "$1" | tail -n 8; return 1; }
  echo "$INPUT_OK"
}

# the web, twice: the probe, then real DOM events (webcheck/WebInput), which must add the pointer's lines on box c
input_web() {
  timeout 60 java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
    -cp "$(cat webcheck/out/classpath.txt)" everyui.webcheck.WebLive \
    "file://$ROOT/web/out-js/index.html?demo=input&probe=1" 4 > "$LOGS/input-web-probe.log" 2>&1 || true
  input_result "$LOGS/input-web-probe.log" || return 1
  timeout 60 java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
    -cp "$(cat webcheck/out/classpath.txt)" everyui.webcheck.WebInput \
    "file://$ROOT/web/out-js/index.html?demo=input" > "$LOGS/input-web-events.log" 2>&1 || true
  local got expected="focus=a focus=b click=b focus=a enter c focus=c click=c leave c"
  got=$(grep -oE "^\[ui\] (focus=|click=|enter |leave ).*" "$LOGS/input-web-events.log" | sed "s/^\[ui\] //" | tr "\n" " " | sed "s/ $//")
  [ "$got" = "$expected" ] || { echo "real events: got '$got', expected '$expected'"; return 1; }
  echo "real events: $got"
}

input_desktop() {
  command -v xvfb-run > /dev/null || { echo "xvfb-run not found"; return 1; }
  timeout 20 xvfb-run -a -s "-screen 0 1280x720x24" \
    java -Dprism.order=sw -cp "$(cat desktop/out/classpath.txt)" everyui.desktop.DesktopApp \
    --size=1280x720 --demo=input --probe=1 > "$LOGS/input-desktop-console.log" 2>&1 || true
  input_result "$LOGS/input-desktop-console.log"
}

# Android: the probe, then the D-pad and a tap through adb (no hover on a touch screen, so no enter/leave)
input_android() {
  local serial=${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}
  [ -n "$serial" ] || { echo "no Android emulator on adb"; return 1; }
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am start -S -n com.outr.everyui/everyui.activity.MainActivity --es demo input --es probe 1 --es viewport 1920x1080 > /dev/null || return 1
  $CHECKS android-wait "$serial" "$LOGS/input-android-probe.log" --pattern "input ok|uncaught|FATAL" --timeout 60
  input_result "$LOGS/input-android-probe.log" || return 1
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am start -S -n com.outr.everyui/everyui.activity.MainActivity --es demo input --es viewport 1920x1080 > /dev/null || return 1
  sleep 4
  for k in KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER KEYCODE_DPAD_LEFT KEYCODE_DPAD_DOWN; do adb -s "$serial" shell input keyevent $k; done
  adb -s "$serial" shell input tap 630 160
  sleep 2
  adb -s "$serial" logcat -d -s everyui > "$LOGS/input-android-events.log"
  adb -s "$serial" shell am force-stop com.outr.everyui
  local got expected="focus=a focus=b click=b focus=a focus=c click=c"
  got=$(grep -oE "\[ui\] (focus=|click=).*" "$LOGS/input-android-events.log" | sed "s/^\[ui\] //" | tr "\n" " " | sed "s/ $//")
  [ "$got" = "$expected" ] || { echo "real events: got '$got', expected '$expected'"; return 1; }
  echo "real events: $got"
}

input_tvos() {
  EXTRA_ARGS="--demo=input --probe=1" LIVE_SECONDS=8 tools/tvos-run.sh --console > "$LOGS/input-tvos-run.log" 2>&1 || { tail -n 5 "$LOGS/input-tvos-run.log"; return 1; }
  input_result target/tvos/console.log
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

# live on the same device: the frame rate, and two screenshots a second apart must differ (the animation moves)
android_live() {
  local serial=${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}
  [ -n "$serial" ] || { echo "no Android emulator on adb"; return 1; }
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am start -S -n com.outr.everyui/everyui.activity.MainActivity > /dev/null || return 1
  sleep "${ANDROID_LIVE_SECONDS:-25}"
  adb -s "$serial" exec-out screencap -p > "$LOGS/android-live-1.png"; sleep 1
  adb -s "$serial" exec-out screencap -p > "$LOGS/android-live-2.png"
  adb -s "$serial" logcat -d -s everyui:I AndroidRuntime:E > "$LOGS/android-live-console.log"
  adb -s "$serial" shell am force-stop com.outr.everyui
  $CHECKS fps "$LOGS/android-live-console.log" --samples-min 15 --median-min "${FPS_MEDIAN_MIN:-59.5}" --min "${FPS_MIN:-55}" &&
    $CHECKS motion "$LOGS/android-live-1.png" "$LOGS/android-live-2.png"
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

# roku_shot OUT: the device's screen right now (the developer web page's screenshot)
roku_shot() {
  curl -s --digest -u "rokudev:$ROKU_PASS" -F mysubmit=Screenshot -F archive= -F passwd= "http://${ROKU_IP:-192.168.1.112}/plugin_inspect" > /dev/null &&
    curl -s --digest -u "rokudev:$ROKU_PASS" -o "$1" "http://${ROKU_IP:-192.168.1.112}/pkgs/dev.jpg"
}

# the live build stays on the device afterwards; two screenshots a second apart must differ (the animation moves)
roku_fps() {
  $CHECKS roku-install roku/out-roku &&
    $CHECKS roku-console "${FPS_SECONDS:-60}" "$LOGS/roku-fps-console.log" &&
    $CHECKS fps "$LOGS/roku-fps-console.log" --median-min "${FPS_MEDIAN_MIN:-59.5}" --min "${FPS_MIN:-58.5}" &&
    roku_shot "$LOGS/roku-live-1.jpg" && sleep 1 && roku_shot "$LOGS/roku-live-2.jpg" &&
    $CHECKS motion "$LOGS/roku-live-1.jpg" "$LOGS/roku-live-2.jpg"
}

# ── run ──────────────────────────────────────────────────────────────────────

if run "build: JVM suite, Roku channels, desktop" build.log build; then
  checks_cli
  run "fonts: shared table matches assets/fonts" faces.log faces
  run "reactify on the JVM matches its golden" jvm-suite.log jvm_suite
  run "network probe on the JVM matches its golden" jvm-probe.log jvm_probe
  run "Roku channels: no emit gaps" gaps.log gaps
  run "Roku channels: brighterscript clean" brighterscript.log brighterscript
  BUILT=1
else
  for s in "fonts: shared table matches assets/fonts" "reactify on the JVM matches its golden" "network probe on the JVM matches its golden" "Roku channels: no emit gaps" "Roku channels: brighterscript clean"; do
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

if [ $BUILT = 1 ] && [ $WEB_BUILT = 1 ]; then
  run "input: web (probe, then real DOM events)" input-web.log input_web
  run "input: desktop (probe)" input-desktop.log input_desktop
else
  skip "input: web (probe, then real DOM events)" "build failed"
  skip "input: desktop (probe)" "build failed"
fi

if [ $NATIVE = 1 ]; then
  run "desktop: native image builds, frame, frame rate" desktop-native.log desktop_native
fi

# ── the network module: DurableDemo on every backend, against server/ (tools/durable.sh) ──
if [ $BUILT = 1 ] && [ $WEB_BUILT = 1 ] && run "durable: server up" server-start.log server_start; then
  trap server_stop EXIT
  run "durable: web (Chrome via RoboBrowser)" durable-web.log durable_web
  run "durable: desktop (JavaFX)" durable-desktop.log durable_desktop
  DURABLE_SERVER=1
else
  for s in "durable: web (Chrome via RoboBrowser)" "durable: desktop (JavaFX)"; do skip "$s" "no server"; done
  DURABLE_SERVER=0
fi

if [ $ANDROID = 1 ]; then
  if run "Android: APK builds" android-build.log android_build; then
    run "Android: AnimationExample frame" android-frame.log android_frame
    run "Android: live frame rate and motion" android-live.log android_live
    run "input: Android (probe, D-pad and a tap)" input-android.log input_android
    if [ $DURABLE_SERVER = 1 ]; then run "durable: Android emulator" durable-android.log durable_android; else skip "durable: Android emulator" "no server"; fi
  else
    skip "Android: AnimationExample frame" "APK did not build"
  fi
fi

if [ $TVOS = 1 ]; then
  run "tvOS: AnimationExample frame (simulator)" tvos-frame.log tvos_frame
  run "tvOS: live frame rate (simulator)" tvos-fps.log tvos_fps
  if [ $DURABLE_SERVER = 1 ]; then run "durable: tvOS (simulator)" durable-tvos.log durable_tvos; else skip "durable: tvOS (simulator)" "no server"; fi
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
    if [ $DURABLE_SERVER = 1 ]; then run "durable: Roku" durable-roku.log durable_roku; else skip "durable: Roku" "no server"; fi
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
