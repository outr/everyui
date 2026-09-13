#!/usr/bin/env bash
# The Swift spike (DESIGN.md, "tvOS"): reactify's acceptance suite, compiled from Scala to Swift by
# everyui's compiler, built by Xcode's toolchain on the Mac and run on the Apple TV simulator.
#
#   tools/swift-spike.sh              emit, build for macOS and the tvOS simulator, run the suite on both,
#   PROJECT=network DIR=network GOLDEN=network/expected-output.txt HARNESS=probe-main.swift tools/swift-spike.sh
#                                     the same for the network probe (spice's durable client through the emitter)
#                                     diff it against reactive-test/expected-output.txt, run the memory probe
#
# Environment: MAC (ssh host, default mbp), MAC_DIR (default ~/everyui-spike), TVOS_DEVICE (a simulator
# name, default "Apple TV 4K (3rd generation) (at 1080p)"). Logs go to target/swift-spike/.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
MAC=${MAC:-mbp}
MAC_DIR=${MAC_DIR:-everyui-spike}
TVOS_DEVICE=${TVOS_DEVICE:-"Apple TV 4K (3rd generation) (at 1080p)"}
PROJECT=${PROJECT:-reactiveTest}       # the sbt project to emit
DIR=${DIR:-reactive-test}             # its directory (out-swift/App.swift)
GOLDEN=${GOLDEN:-reactive-test/expected-output.txt}
HARNESS=${HARNESS:-main.swift}         # swift/spike/: main.swift runs the suite and the memory probe; probe-main.swift just runs main
SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
SSH="ssh -o VisualHostKey=no"
LOGS="$ROOT/target/swift-spike"
rm -rf "$LOGS"
mkdir -p "$LOGS"

step() { printf '%-46s ' "$1"; }
pass() { echo "PASS  ${1:-}"; }
fail() { echo "FAIL  ${1:-}"; FAILED=1; }
FAILED=0

step "emit: $PROJECT → Swift"
if "$SBT" "$PROJECT/emitSwift" > "$LOGS/emit.log" 2>&1 && [ -f $DIR/out-swift/App.swift ]; then
  pass "$(wc -l < $DIR/out-swift/App.swift) lines"
else
  fail "(target/swift-spike/emit.log)"; tail -n 20 "$LOGS/emit.log"; exit 1
fi

step "ship to $MAC:$MAC_DIR"
STAGE=$(mktemp -d)
cp swift/runtime/ScalaRuntime.swift $DIR/out-swift/App.swift "$STAGE/" && cp swift/spike/$HARNESS "$STAGE/main.swift"
$SSH "$MAC" "mkdir -p $MAC_DIR" > /dev/null 2>&1
if rsync -a --delete "$STAGE/" "$MAC:$MAC_DIR/src/" > "$LOGS/ship.log" 2>&1; then pass "3 files"; else fail "(target/swift-spike/ship.log)"; exit 1; fi
rm -rf "$STAGE"

# build NAME SDK [swiftc flags…]: compile the three sources on the Mac into bin/NAME (SDK "" is the host's)
build() {
  local name=$1 sdk=$2; shift 2
  $SSH "$MAC" "cd $MAC_DIR && mkdir -p bin && rm -f bin/$name && xcrun ${sdk:+-sdk $sdk} swiftc -O -suppress-warnings $* src/ScalaRuntime.swift src/App.swift src/main.swift -o bin/$name 2>&1 | grep -v '^$'; test -x bin/$name"
}

step "build: macOS (arm64)"
if build suite-macos "" > "$LOGS/build-macos.log" 2>&1; then pass; else fail "(target/swift-spike/build-macos.log)"; grep -E "error:" "$LOGS/build-macos.log" | head -n 20; exit 1; fi

step "run: macOS, suite vs JVM golden"
$SSH "$MAC" "cd $MAC_DIR && ./bin/suite-macos" > "$LOGS/macos-run.log" 2>&1
grep -oE '^RX .*' "$LOGS/macos-run.log" > "$LOGS/macos-suite.txt"
if diff -u "$GOLDEN" "$LOGS/macos-suite.txt" > "$LOGS/macos-diff.txt"; then
  pass "$(wc -l < "$LOGS/macos-suite.txt") lines identical, $(grep -oE 'suite ms=[0-9]+' "$LOGS/macos-run.log")"
else
  fail "(target/swift-spike/macos-diff.txt)"; head -n 30 "$LOGS/macos-diff.txt"
fi

step "build: tvOS simulator (arm64)"
TVOS_SDK_VERSION=$($SSH "$MAC" "xcrun --sdk appletvsimulator --show-sdk-version" 2>/dev/null | tr -d '\r')
if build suite-tvos appletvsimulator -target "arm64-apple-tvos${TVOS_SDK_VERSION}-simulator" > "$LOGS/build-tvos.log" 2>&1; then
  pass "tvOS $TVOS_SDK_VERSION"
else
  fail "(target/swift-spike/build-tvos.log)"; grep -E "error:" "$LOGS/build-tvos.log" | head -n 20; exit 1
fi

step "boot: $TVOS_DEVICE"
UDID=$($SSH "$MAC" "xcrun simctl list devices available" | grep -F "$TVOS_DEVICE (" | grep -oE '[0-9A-F-]{36}' | head -n 1)
if [ -z "$UDID" ]; then fail "no such simulator"; exit 1; fi
$SSH "$MAC" "xcrun simctl boot $UDID" > "$LOGS/boot.log" 2>&1 || true
if $SSH "$MAC" "xcrun simctl list devices" | grep -F "$UDID" | grep -q Booted; then pass "$UDID"; else fail "(target/swift-spike/boot.log)"; cat "$LOGS/boot.log"; exit 1; fi

step "run: tvOS simulator, suite vs JVM golden"
$SSH "$MAC" "cd $MAC_DIR && xcrun simctl spawn $UDID \$PWD/bin/suite-tvos" > "$LOGS/tvos-run.log" 2>&1
grep -oE '^RX .*' "$LOGS/tvos-run.log" > "$LOGS/tvos-suite.txt"
if diff -u "$GOLDEN" "$LOGS/tvos-suite.txt" > "$LOGS/tvos-diff.txt"; then
  pass "$(wc -l < "$LOGS/tvos-suite.txt") lines identical, $(grep -oE 'suite ms=[0-9]+' "$LOGS/tvos-run.log")"
else
  fail "(target/swift-spike/tvos-diff.txt)"; head -n 30 "$LOGS/tvos-diff.txt"; tail -n 5 "$LOGS/tvos-run.log"
fi

if [ "$HARNESS" = main.swift ]; then
step "memory: tvOS simulator, cycles"
$SSH "$MAC" "cd $MAC_DIR && xcrun simctl spawn $UDID \$PWD/bin/suite-tvos probe" > "$LOGS/tvos-probe.log" 2>&1
if grep -q "probe.collect.each.round" "$LOGS/tvos-probe.log"; then
  pass
  grep -E '^SWIFT probe' "$LOGS/tvos-probe.log" | sed 's/^/    /'
else
  fail "(target/swift-spike/tvos-probe.log)"; tail -n 10 "$LOGS/tvos-probe.log"
fi
fi

exit $FAILED
