#!/usr/bin/env bash
# The desktop app as a self-contained native binary: GraalVM native-image over the JavaFX backend.
#
#   tools/desktop-native.sh           build desktop/out/native/everyui-desktop, then run it under Xvfb: the frozen
#                                     frame against tools/golden/animation-tree.txt, a live frame-rate sample, live
#                                     windows in ES2 and SW at uiScale 1 and 1.75 (failing on a class, a shader or a
#                                     JNI field the image lacks, an ES2 asked for and not had, or a blank window), and
#                                     the tray (--demo=tray --headless=1) on a bus of its own, as a panel sees it
#                                     (everyui.tray.TrayNativeCheck): registered, its menu run, its popover opened
#   tools/desktop-native.sh --screens also run every screen of the example (each demo, as `screens` lists them) live
#                                     in ES2 and SW at uiScale 1 and 1.75, each checked as above (target/native/screens/)
#   tools/desktop-native.sh --trace   first rerun GraalVM's tracing agent to refresh desktop/native-config/, the
#                                     reflection/JNI/resource metadata JavaFX needs — after a change to what the app
#                                     touches of JavaFX: a frozen and a short live run, a frozen one at uiScale 1.75,
#                                     the tray's, and every screen of the example in ES2 and in SW at uiScale 1 and
#                                     1.75 (some 13 minutes, SLOTS=8 at a time)
#
# Needs `native-image` on the PATH (GraalVM), Xvfb (with GLX, for ES2: Mesa's llvmpipe) and ImageMagick. The binary
# needs nothing from the machine: JavaFX's own native libraries ride inside it as resources and are extracted on first
# run. It draws with Prism's own choice, as the JVM does: ES2 where OpenGL is a GPU's, SW where there is none (or only
# a software one, unless -Dprism.forceGPU=true). Logs go to target/native/.
#
# Nothing here reaches the desktop it is run from: every window is on an Xvfb of its own, GTK speaks X to it, sbt runs
# under one too, and what a screen plays goes to ALSA's null device.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
unset WAYLAND_DISPLAY DISPLAY
export GDK_BACKEND=x11
SBT=${SBT:-$(command -v sbt || echo "$HOME/.local/share/coursier/bin/sbt")}
CHECKS="java -cp $(cat $ROOT/checks/out/classpath.txt 2>/dev/null) everyui.checks.Checks"   # staged by the sbt step below
CFG="$ROOT/desktop/native-config"
OUT="$ROOT/desktop/out/native/everyui-desktop"
LOGS="$ROOT/target/native"
FREEZE=0.434
SLOTS=${SLOTS:-8}
xvfb() { xvfb-run -a -s "-screen 0 1920x1080x24" "$@"; }
sbt_() { env -u WAYLAND_DISPLAY xvfb-run -a "$SBT" --server -batch "$@"; }
mkdir -p "$LOGS" "$(dirname "$OUT")"
# a home of its own for the app's runs, whose ALSA plays to nothing (JavaFX's media and the JDK's sound speak ALSA)
QUIET="$LOGS/home"; mkdir -p "$QUIET"; printf 'pcm.!default { type null }\n' > "$QUIET/.asoundrc"
quiet() { env HOME="$QUIET" PULSE_SERVER=unix:/nonexistent PIPEWIRE_REMOTE=/nonexistent "$@"; }

TRACE=0; SCREENS=0
for arg in "$@"; do
  case "$arg" in
    --trace) TRACE=1 ;;
    --screens) SCREENS=1 ;;
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
if sbt_ "checks/checksClasspath; desktop/desktopClasspath; desktop/Test/compile" > "$LOGS/sbt.log" 2>&1; then pass; else fail "(target/native/sbt.log)"; exit 1; fi
# (scala-java-time, which scribe's perfolation brings for Scala.js, is java.time over again: the JDK's own is
# there, and native-image refuses a class of its own in the image's classpath)
CP=$(tr ':' '\n' < desktop/out/classpath.txt | grep -v '/scala-java-time' | paste -sd:)
# the tray played as a panel would, on a bus of its own, against the command given
traycheck() { sbt_ "desktop/Test/runMain everyui.tray.TrayNativeCheck $*"; }

# Every screen of the example, a line each: "-" for its first (the animation), each demo Main and FullDemos name, and
# a few demos' other views
screens() {
  echo "-"
  grep -ohE 'demo == "[a-z]+"' app/src/main/scala/everyui/app/Main.scala app/full/src/main/scala/everyui/app/FullDemos.scala |
    cut -d'"' -f2
  printf '%s\n' "colors --dialog=1" "dates --dialog=date" "dates --dialog=range" "blocks --style=tree"
}
# each screen in ES2 and SW at uiScale 1 and 1.75: "<pipeline> <scale> <screen> [args]"
screenjobs() { for pipe in es2 sw; do for scale in 1 1.75; do screens | sed "s/^/$pipe $scale /"; done; done; }
pipeflags() { if [ "$1" = es2 ]; then echo "-Dprism.forceGPU=true -Dprism.order=es2"; else echo "-Dprism.order=sw"; fi; }
# room for a 1600x900 window at the scale
screenat() { if [ "$1" = 1 ]; then echo "1920x1080x24"; else echo "3000x1700x24"; fi; }
# `fan f` runs `f <slot> <n> <job...>` for each job on stdin, SLOTS at a time, each slot's Xvfb numbered apart
fan() {
  local f=$1 lines pids=() s
  mapfile -t lines
  for ((s = 0; s < SLOTS; s++)); do
    ( for ((i = s; i < ${#lines[@]}; i += SLOTS)); do $f "$s" "$i" ${lines[$i]}; done ) & pids+=($!)
  done
  wait "${pids[@]}"
}
# one screen live for `secs` under a slot's Xvfb (with GLX), its display taken at `at` s: run <slot> <secs> <at> <shot>
# <log> <scale> <command...>
run() {
  local s=$1 secs=$2 at=$3 shot=$4 log=$5 scale=$6; shift 6
  quiet xvfb-run -a -n $((300 + s * 10)) -s "-screen 0 $(screenat "$scale") +extension GLX" bash -c \
    '"$@" > "'"$log"'" 2>&1 & pid=$!; sleep '"$at"'; '"${shot:+magick import -window root \"$shot\";}"' sleep '"$((secs - at))"';
     kill $pid; for i in $(seq 20); do kill -0 $pid 2> /dev/null || break; sleep 1; done; kill -9 $pid 2> /dev/null; wait $pid' \
    _ "$@"
}
screenargs() { [ "$1" = - ] || echo "--demo=$1"; }
# what the image lacks shows as one of these: a class, a stock shader (ES2 loads each by name: its loader, its GLSL),
# a JNI field or class, a library
LACKS='ClassNotFoundException|NoClassDefFoundError|Error loading stock shader|JNI exception|FindClass .* failed|Missing[A-Za-z]*RegistrationError|UnsatisfiedLinkError'
# a screen's findings: lacks (what LACKS matched), fallback (ES2 asked for and SW had), blank (one colour)
verdict() {   # <pipeline> <log> <shot>
  local v=""
  grep -qE "$LACKS" "$2" && v="$v lacks"
  [ "$1" = es2 ] && ! grep -q "Initialized prism pipeline: com.sun.prism.es2.ES2Pipeline" "$2" && v="$v fallback"
  [ "$(magick identify -format %k "$3" 2>/dev/null || echo 0)" -gt 1 ] || v="$v blank"
  echo "$v"
}

if [ $TRACE = 1 ]; then
  step "trace: native-image agent (frozen + live)"
  rm -rf "$CFG"; mkdir -p "$CFG"
  xvfb java -agentlib:native-image-agent=config-output-dir="$CFG" -Dprism.order=sw -Dprism.lcdtext=false \
    -cp "$CP" everyui.desktop.DesktopApp --freeze=$FREEZE --screenshot="$LOGS/agent-frame.png" > "$LOGS/agent-freeze.log" 2>&1
  # the live run is stopped by the clock, so the agent cannot finish its merge cleanly: its lock is a leftover
  timeout 12 xvfb-run -a -s "-screen 0 1920x1080x24" java -agentlib:native-image-agent=config-merge-dir="$CFG" -Dprism.order=sw -Dprism.lcdtext=false \
    -cp "$CP" everyui.desktop.DesktopApp > "$LOGS/agent-live.log" 2>&1
  rm -f "$CFG/.lock"
  # at a fractional scale, where outlined glyphs make a Path2D through JNI (never at scale 1)
  xvfb java -agentlib:native-image-agent=config-merge-dir="$CFG" -Dglass.gtk.uiScale=1.75 -Dprism.order=sw \
    -cp "$CP" everyui.desktop.DesktopApp --size=1000x600 --freeze=$FREEZE --screenshot="$LOGS/agent-scaled.png" \
    > "$LOGS/agent-scaled.log" 2>&1
  rm -f "$CFG/.lock"
  traycheck "$LOGS/agent-tray.log" java -agentlib:native-image-agent=config-merge-dir="$CFG" -Dprism.order=sw \
    -Dprism.lcdtext=false -cp "$CP" everyui.desktop.DesktopApp --demo=tray --headless=1 \
    > "$LOGS/agent-tray-check.log" 2>&1
  rm -f "$CFG/.lock"
  if [ -s "$CFG/reachability-metadata.json" ]; then pass "$(wc -c < "$CFG/reachability-metadata.json") bytes"; else fail; exit 1; fi

  # every screen in each pipeline at each scale, 15 s each with its probe on where it has one, each traced on its own
  # and merged after: ES2's pipeline and the shaders and effects' peers each screen draws with, and what SW and the
  # screens themselves reach of JavaFX (its CSS, AWT for an image, the media)
  step "trace: every screen, ES2 + SW at 1 and 1.75"
  TR="$LOGS/trace"; rm -rf "$TR"; mkdir -p "$TR"
  tracejob() {   # <slot> <n> <pipeline> <scale> <screen> [args]
    local s=$1 n=$2 pipe=$3 scale=$4 screen=$5; shift 5
    echo "$n $pipe $scale $screen $*" >> "$TR/jobs.txt"
    run "$s" 15 15 "" "$TR/$n.log" "$scale" java -agentlib:native-image-agent=config-output-dir="$TR/$n" $(pipeflags "$pipe") \
      -Dglass.gtk.uiScale="$scale" -Dprism.verbose=true -cp "$CP" everyui.desktop.DesktopApp --size=1600x900 \
      $(screenargs "$screen") "$@" --probe=1 --muted=1
  }
  screenjobs | fan tracejob
  dirs=("$CFG" "$TR"/*/); traced=$(ls "$TR"/*/reachability-metadata.json 2>/dev/null | wc -l)
  rm -rf "$CFG.merged"
  if native-image-utils generate $(printf -- '--input-dir=%s ' "${dirs[@]}") --output-dir="$CFG.merged" > "$LOGS/merge.log" 2>&1 &&
     [ -s "$CFG.merged/reachability-metadata.json" ]; then
    rm -rf "$CFG"; mv "$CFG.merged" "$CFG"; pass "$traced of $(screenjobs | wc -l) runs traced"
  else fail "(target/native/merge.log)"; exit 1; fi

  step "trace: what no run is sure to reach"
  # a Scala object's lazy values are found by their fields as it starts: every field of these, rather than each
  # and the byte order Glass asks of a buffer through JNI as it hands the frame to the window, which no traced run
  # recorded: without it the buffer stays big-endian and every colour on the screen comes out wrong (red as cyan);
  # and the glyph strike's transform (FT_Matrix, through JNI, in ES2 at a fractional scale) and the outline's Path2D,
  # where a trace missed them: without them glyphs are drawn at 1x on scaled advances, or nothing is drawn at all;
  # and all of ES2's stock shaders and effects, not only those the example's screens drew with: Prism makes each by
  # name the first time a shape, a paint or an effect needs it (a loader class and its GLSL, an effect's peer), so an
  # app's gradient or blend the example never draws would otherwise fail with "Error loading stock shader"
  FXJAR=$(tr ':' '\n' <<< "$CP" | grep -m 1 '/javafx-graphics-.*-linux\.jar$')
  unzip -Z1 "$FXJAR" > "$LOGS/javafx-graphics.txt"
  node -e 'const fs=require("fs"); const f=process.argv[1]; const n=JSON.parse(fs.readFileSync(f));
    const jar=fs.readFileSync(process.argv[2], "utf8").split("\n");
    n.reflection=n.reflection.map(e => ["everyui.font.MaterialIcon$","everyui.Color$"].includes(e.type) ?
      Object.assign({type: e.type, allDeclaredFields: true}, e.methods ? {methods: e.methods} : {}) : e);
    // an entry made sure of: added, or what it lacks of it added to the one traced (a run may have reached the type
    // by reflection alone, without the JNI access its native code needs), its keys in the order the agent writes them
    const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
    const ensure = g => {
      const i = n.reflection.findIndex(e => e.type === g.type), e = i < 0 ? {type: g.type} : n.reflection[i];
      const m = {type: e.type, ...(e.jniAccessible || g.jniAccessible ? {jniAccessible: true} : {})};
      for (const k of Object.keys(e)) if (!["type", "jniAccessible", "fields", "methods"].includes(k)) m[k] = e[k];
      for (const k of ["fields", "methods"]) {
        const all = [...(e[k] || [])]; for (const x of g[k] || []) if (!all.some(y => same(x, y))) all.push(x);
        if (all.length) m[k] = all;
      }
      if (i < 0) n.reflection.push(m); else n.reflection[i] = m;
    };
    ensure({type: "java.nio.ByteBuffer", methods: [{name: "order", parameterTypes: ["java.nio.ByteOrder"]}]});
    ensure({type: "java.nio.ByteOrder", jniAccessible: true, methods: [{name: "nativeOrder", parameterTypes: []}]});
    ensure({type: "com.sun.javafx.font.freetype.FT_Matrix", jniAccessible: true,
      fields: ["xx", "xy", "yx", "yy"].map(name => ({name}))});
    ensure({type: "com.sun.javafx.geom.Path2D", jniAccessible: true,
      methods: [{name: "<init>", parameterTypes: ["int", "byte[]", "int", "float[]", "int"]}]});
    const made = (re, m) => jar.filter(e => re.test(e)).map(e => ({type: e.slice(0, -6).replace(/\//g, "."), methods: [m]}));
    made(/^com\/sun\/prism\/shader\/\w+_Loader\.class$/, {name: "loadShader",
      parameterTypes: ["com.sun.prism.ps.ShaderFactory", "java.lang.String", "java.io.InputStream"]}).forEach(ensure);
    made(/^com\/sun\/scenario\/effect\/impl\/prism\/ps\/PPS(?!EffectPeer|OneSamplerPeer|TwoSamplerPeer|ZeroSamplerPeer)\w+Peer\.class$/,
      {name: "<init>", parameterTypes: ["com.sun.scenario.effect.FilterContext", "com.sun.scenario.effect.impl.Renderer",
        "java.lang.String"]}).forEach(ensure);
    const glsl = ["com/sun/prism/es2/glsl/*", "com/sun/scenario/effect/impl/es2/glsl/*"];
    n.resources = n.resources.filter(r => r.module || !r.glob || !glsl.some(g => r.glob.startsWith(g.slice(0, -1))));
    for (const g of glsl) n.resources.push({glob: g});
    // in the order the agent writes: types by name; resources plain, then by module, then bundles, each by name
    const by = k => (a, b) => (k(a) < k(b) ? -1 : k(a) > k(b) ? 1 : 0);
    n.reflection.sort(by(e => e.type));
    const group = r => r.bundle ? 2 : r.module ? 1 : 0;
    n.resources.sort((a, b) => group(a) - group(b) || by(r => (r.module || "") + " " + (r.glob || r.bundle))(a, b));
    fs.writeFileSync(f, JSON.stringify(n, null, 2) + "\n")' "$CFG/reachability-metadata.json" "$LOGS/javafx-graphics.txt"
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

step "pipelines live (ES2 + SW at 1 and 1.75)"
# each pipeline the image has, at scale 1 and at one that is not whole: a uiScale that is not whole gives a glyph
# strike a transform (FT_Matrix, through JNI, in ES2) and, in SW, draws large glyphs as outlines (a Path2D made through
# JNI), and without their metadata glyphs are drawn at 1x on 1.75x advances, or no frame reaches the window at all;
# ES2 must be had where asked for, with every shader it loads. Only the window shows it (the app's own snapshot is
# drawn at 1x), so each runs live for 8 s and the display is taken, which must not be one colour
PIPES=""; SEEN=""
for job in "es2 1" "es2 1.75" "sw 1" "sw 1.75"; do
  set -- $job; name="$1-$2"; log="$LOGS/pipe-$name.log"; shot="$LOGS/pipe-$name.png"; rm -f "$shot"
  run 0 9 8 "$shot" "$log" "$2" "$OUT" $(pipeflags "$1") -Dglass.gtk.uiScale="$2" -Dprism.verbose=true --size=1600x900
  v=$(verdict "$1" "$log" "$shot"); [ -n "$v" ] && PIPES="$PIPES $name:${v# }"; SEEN="$SEEN $name"
done
if [ -z "$PIPES" ]; then pass "$SEEN"
else fail "($PIPES ) target/native/pipe-*.log"; grep -ohE "($LACKS)[^ ]* ?[^ ]*" "$LOGS"/pipe-*.log | sort -u | head -n 5; fi

if [ $SCREENS = 1 ]; then
  step "every screen live (ES2 + SW at 1 and 1.75)"
  SC="$LOGS/screens"; rm -rf "$SC"; mkdir -p "$SC"
  screenjob() {   # <slot> <n> <pipeline> <scale> <screen> [args]
    local s=$1 n=$2 pipe=$3 scale=$4 screen=$5; shift 5
    local name="$pipe-$scale-${screen#-}$(printf '%s' "$*" | tr -c 'a-z0-9' '_')"; [ "$screen" = - ] && name="$pipe-$scale-first"
    # (the voice demo's probe, on ALSA's null device, keeps a SW window black on the JVM as well: it is shown unprobed)
    local probe=1; [ "$screen" = voice ] && probe=0
    run "$s" 12 10 "$SC/$name.png" "$SC/$name.log" "$scale" "$OUT" $(pipeflags "$pipe") -Dglass.gtk.uiScale="$scale" \
      -Dprism.verbose=true --size=1600x900 $(screenargs "$screen") "$@" --probe=$probe --muted=1
    local v; v=$(verdict "$pipe" "$SC/$name.log" "$SC/$name.png"); echo "$name${v:+:${v# }}" >> "$SC/results.txt"
  }
  screenjobs | fan screenjob
  bad=$(grep ':' "$SC/results.txt" | sort | tr '\n' ' ')
  if [ -z "$bad" ]; then pass "$(wc -l < "$SC/results.txt") runs"
  else fail "($bad) target/native/screens/"; grep -ohE "($LACKS)[^ ]* ?[^ ]*" "$SC"/*.log | sort | uniq -c | sort -rn | head -n 8; fi
fi

step "tray as a panel sees it (Xvfb, own bus)"
if TRAY_SHOT="$LOGS/tray-popover.png" traycheck "$LOGS/tray.log" "$OUT" --demo=tray --headless=1 > "$LOGS/tray-check.log" 2>&1; then
  pass "$(grep -c PASS "$LOGS/tray-check.log") steps"
else
  fail "(target/native/tray-check.log)"; grep -E "tray:" "$LOGS/tray-check.log"
fi

exit $FAILED
