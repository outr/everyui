#!/usr/bin/env bash
# Drive an EveryUI app running on an Apple simulator (tvOS, iOS, iPadOS) the way a Roku's ECP drives a channel: each
# command goes to the app's own test input channel on the Mac's localhost, and the app does it through its own input
# (a tap is a touch the app hears as any other, a key a key), so nothing touches the Mac's own mouse or keyboard.
#
# The app must be a build made to be driven, launched to listen: tools/tvos-run.sh --drive [--ios] builds the example
# so (swiftc -D EVERYUI_DRIVE) and leaves it running with --drive=$DRIVE_PORT; an app's own debug build passes
# -D EVERYUI_DRIVE to swiftc and is launched with `--drive=<port>` (or SIMCTL_CHILD_EVERYUI_DRIVE=<port> in
# `xcrun simctl launch`'s environment). A release build, compiled without the flag, has none of it.
#
#   tools/apple-drive.sh ping                       the platform, the device, whether a remote drives it
#   tools/apple-drive.sh tap 120 300                a tap at a point (the screen's points: a screenshot's pixels over
#                                                   the screen's scale, 3 on an iPhone 17 Pro, 1 on an Apple TV)
#   tools/apple-drive.sh hold 120 300 [ms]          a long press (600 ms)
#   tools/apple-drive.sh swipe 200 600 200 200 [ms] a finger dragged from one point to the other (300 ms)
#   tools/apple-drive.sh key down                   a key: up, down, left, right, select (ok, enter), back (menu),
#                                                   escape, tab, space, backspace, play, or any web key name
#   tools/apple-drive.sh type "abc"                 into the text dialog that is up (key enter is its Done, key
#                                                   escape its Cancel), else a key for each character
#   tools/apple-drive.sh press "English"            a tap on the middle of what is showing named so (its label or
#                                                   its words: exactly, then in any case, then a part of them)
#   tools/apple-drive.sh find "English"             where it is, with no tap
#   tools/apple-drive.sh labels                     each focusable thing showing, by name and where it is
#   tools/apple-drive.sh focused                    what has the focus
#   tools/apple-drive.sh screenshot [file.png]      the simulator's screen (target/apple-drive/screen.png)
#   tools/apple-drive.sh < commands.txt             one command a line, each answered in turn (a screenshot apart)
#
# Each answer is a line starting "ok" or "error" (labels adds a line per thing); the exit status is 1 if any was an
# error. Environment: MAC (ssh host, default mbp), DEVICE (the simulator's UDID or name, default the booted one), and
# DRIVE_PORT: by default 47001 for the booted one, and for a DEVICE named a port of its own, 47100 to 47899, from its
# UDID (the port tools/tvos-run.sh --drive launches that simulator's app with), so two simulators driven at once each
# have theirs. Where a DEVICE is named, the app answering is asked first which simulator it is on (its `ping`), and a
# drive that reached another simulator's app stops there, saying so, before anything is done in it.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
MAC=${MAC:-mbp}
DEVICE=${DEVICE:-booted}
SSH="ssh -o VisualHostKey=no"
# the simulator named, by its UDID, and its port (`drive_port`, as tools/tvos-run.sh has it)
drive_port() { echo $((47100 + $(printf '%s' "$1" | cksum | cut -d' ' -f1) % 800)); }
UDID=""
if [ "$DEVICE" != booted ]; then
  if [[ "$DEVICE" =~ ^[0-9A-Fa-f-]{36}$ ]]; then UDID=$DEVICE
  else
    UDID=$($SSH "$MAC" "xcrun simctl list devices available" | grep -F "$DEVICE (" | grep -oE '[0-9A-F-]{36}' |
      head -n 1)
  fi
  [ -n "$UDID" ] || { echo "error no simulator named $DEVICE"; exit 1; }
fi
if [ -n "${DRIVE_PORT:-}" ]; then PORT=$DRIVE_PORT
elif [ -n "$UDID" ]; then PORT=$(drive_port "$UDID")
else PORT=47001; fi

if [ "${1:-}" = screenshot ]; then
  OUT=${2:-$ROOT/target/apple-drive/screen.png}
  mkdir -p "$(dirname "$OUT")"
  SHOT=$($SSH "$MAC" "f=\$(mktemp -t apple-drive).png && \
    xcrun simctl io '$DEVICE' screenshot \"\$f\" > /dev/null 2>&1 && echo \"\$f\"")
  [ -n "$SHOT" ] || { echo "error no screenshot from $DEVICE"; exit 1; }
  scp -q "$MAC:$SHOT" "$OUT" && $SSH "$MAC" "rm -f '$SHOT' '${SHOT%.png}'"
  echo "ok $OUT"
  exit 0
fi

# the commands, one a line: the arguments as one, or what comes on stdin
if [ $# -gt 0 ]; then LINES="$*"; else LINES=$(cat); fi

# on the Mac: one connection, each line sent and its answer read up to the empty line that ends it (the commands
# go as base64, so no quoting stands between them and the app)
send() {
  $SSH "$MAC" bash -s -- "$PORT" "$(printf '%s\n' "$1" | base64 | tr -d '\n')" <<'EOF'
exec 3<>"/dev/tcp/127.0.0.1/$1" || { echo "error nothing listens on 127.0.0.1:$1 (launched with --drive?)"; exit 1; }
printf '%s' "$2" | base64 -D | while IFS= read -r l; do
  [ -z "$l" ] && continue
  printf '%s\n' "$l" >&3
  while IFS= read -r r <&3; do [ -z "$r" ] && break; echo "$r"; done
done
EOF
}

# the app answering is the named simulator's, or nothing is sent to it
if [ -n "$UDID" ]; then
  PING=$(send ping)
  case "$PING" in
    *"udid=$UDID"*) ;;
    *udid=*)
      OTHER=$(printf '%s' "$PING" | grep -oE 'udid=[^ ]+' | cut -d= -f2)
      echo "error the app on 127.0.0.1:$PORT is on simulator $OTHER, not $DEVICE ($UDID): launch this one's with" \
        "--drive=$PORT (tools/tvos-run.sh --drive does)"
      exit 1 ;;
    *) echo "$PING"; exit 1 ;;
  esac
fi
ANSWERS=$(send "$LINES")
STATUS=$?
echo "$ANSWERS"
[ $STATUS = 0 ] || exit 1
if printf '%s\n' "$ANSWERS" | grep -q '^error'; then exit 1; fi
exit 0
