# The network module's check, sourced by tools/check.sh: every backend's DurableDemo against our own Spice
# server (server/: `/durable`, a DurableSocketServer with a ticker and a "drop" RPC). Each run must log
# `[ui] durable ok events=N resumed=1 gaps=0`: five events, an RPC round trip, a server-side drop, a resume
# with the missed events replayed, five more events, no gap in either sequence.
#
# The server binds every interface so devices reach it by this machine's LAN address; the emulator reaches the
# host as 10.0.2.2; the web and the desktop use 127.0.0.1.

DURABLE_PORT=8731
LAN_IP=${LAN_IP:-$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") print $(i + 1) }' | head -n 1)}
SERVER_PID=""

# start our server in the background (needs server/out/classpath.txt from `sbt server/serverClasspath`)
server_start() {
  [ -f "$ROOT/server/out/classpath.txt" ] || { echo "no server classpath (sbt server/serverClasspath)"; return 1; }
  java -cp "$(cat "$ROOT/server/out/classpath.txt")" everyui.server.serve $DURABLE_PORT web/out-js --host=0.0.0.0 \
    > "$LOGS/server.log" 2>&1 &
  SERVER_PID=$!
  for _ in $(seq 1 40); do
    curl -s -o /dev/null "http://127.0.0.1:$DURABLE_PORT/index.html" && { echo "server up (pid $SERVER_PID, LAN $LAN_IP)"; return 0; }
    sleep 0.5
  done
  echo "the server did not come up:"; tail -n 20 "$LOGS/server.log"; return 1
}

server_stop() {
  if [ -n "$SERVER_PID" ]; then kill "$SERVER_PID" 2>/dev/null; wait "$SERVER_PID" 2>/dev/null; SERVER_PID=""; fi
}

# durable_result LOG: the ok line, or the demo's own lines and a failure
durable_result() {
  if grep -q "durable ok" "$1"; then
    grep -o "durable ok.*" "$1" | tail -n 1
  else
    echo "--- no 'durable ok'; the demo's lines:"; grep -E "durable|Exception|rror" "$1" | tail -n 25; return 1
  fi
}

# web: the page our server serves, in a real Chrome on a virtual display via RoboBrowser (webcheck/WebLive), its
# console captured for 45 s — headless Chrome does not keep a page alive long enough to see the socket through
durable_web() {
  command -v Xvfb > /dev/null || { echo "Xvfb not found"; return 1; }
  timeout 120 java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
    -cp "$(cat "$ROOT/webcheck/out/classpath.txt")" everyui.webcheck.WebLive \
    "http://127.0.0.1:$DURABLE_PORT/index.html?demo=durable&socket=ws://127.0.0.1:$DURABLE_PORT/durable" 45 \
    > "$LOGS/durable-web.log" 2>&1 || true
  durable_result "$LOGS/durable-web.log"
}

# desktop: the JavaFX app under Xvfb; it exits itself when done
durable_desktop() {
  command -v xvfb-run > /dev/null || { echo "xvfb-run not found"; return 1; }
  timeout 60 xvfb-run -a -s "-screen 0 1280x720x24" \
    java -Dprism.order=sw -cp "$(cat "$ROOT/desktop/out/classpath.txt")" everyui.desktop.DesktopApp \
    --size=1280x720 --demo=durable "--socket=ws://127.0.0.1:$DURABLE_PORT/durable" > "$LOGS/durable-desktop.log" 2>&1 || true
  durable_result "$LOGS/durable-desktop.log"
}

# Android: the emulator, the host as 10.0.2.2, logcat until the demo reports
durable_android() {
  local serial=${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}
  [ -n "$serial" ] || { echo "no Android emulator on adb"; return 1; }
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am start -S -n com.outr.everyui/everyui.activity.MainActivity \
    --es demo durable --es socket "ws://10.0.2.2:$DURABLE_PORT/durable" > /dev/null || return 1
  $CHECKS android-wait "$serial" "$LOGS/durable-android.log" --pattern "durable ok|durable FAIL" --timeout 60
  adb -s "$serial" shell am force-stop com.outr.everyui
  durable_result "$LOGS/durable-android.log"
}

# Roku: the app channel with demo.txt and socket.txt added, the debug console captured
durable_roku() {
  $CHECKS roku-install roku/out-roku --add demo.txt=durable --add "socket.txt=ws://$LAN_IP:$DURABLE_PORT/durable" || return 1
  $CHECKS roku-console 40 "$LOGS/durable-roku.log"
  durable_result "$LOGS/durable-roku.log"
}

# tvOS: the simulator app launched with the demo's arguments (tools/tvos-run.sh passes EXTRA_ARGS through)
durable_tvos() {
  EXTRA_ARGS="--demo=durable --socket=ws://$LAN_IP:$DURABLE_PORT/durable" LIVE_SECONDS=40 tools/tvos-run.sh --console > "$LOGS/durable-tvos.log" 2>&1 || true
  durable_result "$LOGS/durable-tvos.log"
}
