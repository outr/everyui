# universal-ui — design & status

> **Handoff doc.** This project was spun out of work done in the `sart` repo's
> Claude Code session (which is namespaced to Sart). Everything needed to
> continue lives here — this file is the source of truth. Memory does not cross
> project boundaries, so start fresh conversations from this doc.
>
> **Git:** universal-ui is a git repository (initialised 2026-09-12, branch
> `master`, no remote yet). Build output is ignored — regenerate Roku channels
> with `<project>/emitRoku` and the web bundle with scala-cli (see "Build / ops
> notes").
>
> **Sart is reference-only (decided 2026-09-12).** universal-ui owns its Roku
> toolchain — `compiler/`, `compiler-api/`, `roku/runtime/`, the `emitRoku`
> task — and depends on Sart in no way: no plugin, artifact, import or runtime
> name. Sections marked *history* describe work done in the sart repo before
> the extraction; see "Sart extraction".

## Vision

**One lean UI that deploys everywhere.** The original goal of Flutter, but
Flutter is too bloated and its "draw everything ourselves" model is arbitrarily
limiting/broad. We get there by merging:

- **youi**'s reactive UI model (`/home/mhicks/projects/open/youi`, Scala 3,
  Scala.js + DOM, reactive components) — the API and the render seam.
- **A Scala 3 → TASTy compiler** for runtimes with no Scala of their own (Roku).
  universal-ui's is `compiler/`. It started from Sart's Roku emitter
  (`/home/mhicks/projects/open/sart`), which is now a reference only.

**Flutter is DROPPED as a target.** The legacy Flutter app covers Apple
transitionally.

Naming: TBD (user researching separately; wants .com/.org; not "youi"/"Sart").
`universal-ui` is a working title.

## The "native" principle (settled)

Native = **the platform's own app runtime + a native feel** (Flutter is the
*quality* benchmark, not the mechanism). A VM is fine when it IS the platform
runtime.

| Platform | Native runtime | Notes |
|---|---|---|
| Android / Android TV | JVM/ART lineage | Scala-JVM is the native path — settled |
| Web | JS engine | Scala.js |
| Roku | BrightScript | via universal-ui's Roku compiler |
| iOS / tvOS | native | Scala Native → UIKit (research; undecided) |
| Desktop | *(none exists)* | → ship a **native binary**, not JVM+JRE |

**Desktop is the outlier**: no platform-standard app runtime exists, so bundling
a JVM ships a *foreign* runtime. Desktop should be a self-contained native
binary. Near-term: **GraalVM native-image / Gluon + JavaFX** (JavaFX already
self-renders like Flutter, mature, one backend, no JRE). Pure **Scala Native +
native UI** is a research item, justified mainly on **macOS** as a byproduct of
the iOS Scala-Native→UIKit work (AppKit sibling). Avoid speculative Win32/GTK
native bindings.

## Render-backend tiers (settled)

The render seam is an abstraction with per-tier backends — a feature, not a
failure. The reactive/component layer is written once; each platform provides a
render backend.

| Tier | Platforms | Backend |
|---|---|---|
| Drawn (apps ARE a rendered surface) | desktop, iOS, macOS, Win, Linux | **Skia** (via Scala Native) |
| Retained-semantic | web | **DOM** (Scala.js) |
| TV retained | Roku | **SceneGraph** |
| Android / ATV | phones, Android TV | **Views** (chosen 2026-09-12; see "AnimationExample on every platform") |

**Skia is explicitly REJECTED for web** (= Flutter Web / CanvasKit): it buys
pixel-consistency but loses the browser's native form semantics — 1Password /
password-manager integration, autofill, IME, **reliable text selection**,
find-in-page, the a11y tree — plus ~1.5–2.5 MB of WASM (contradicts "one lean
UI"). DOM stays web's backend: native-feeling *and* lean there.

Skia-on-Scala-Native is a future **de-risk spike** (like reactify-on-Roku was)
before betting desktop+mobile on it.

## Architecture — the key insight

youi's design is **already inherently multi-backend; it just never got a second
backend.** The chain is:

```
reactify (reactive core)  →  Prop[T](getter, setter)  →  backend node
       Var/Val                 = reactify.Var[T]           (DOM element today)
```

- **`Prop[T](getter, setter) extends reactify.Var[T]`** — a reactive Var that
  writes `setter(v)` on change and reads `getter` on refresh. **Backend-neutral
  already** — nothing in `Prop` knows about the DOM; only *which* getter/setter
  you hand it is DOM-specific. **Zero changes needed.**
- **`Component(val element: html.Element)`** — the ONLY DOM-bound part: it wraps
  a concrete `html.Element`, and its `id`/`classes`/`css = element.style`/`title`
  Props are built on it. `Feature`s (SizeFeature etc.) read/write via
  `parent.css` — and SizeFeature already *parameterizes* its setters, so it's
  half-abstracted already.
- **reactify** is already cross-platform (JS/JVM/Native, no macros) and — PROVEN
  — runs on Roku unmodified, compiled to BrightScript (see below).

**`shared/` uses reactify on every backend.** A hand-rolled `Var` stood in for
it until the Roku compiler could compile reactify (`class Var[T] extends
Val[T]() with Mutable[T]`, `AtomicBoolean`, `ThreadLocal`, by-name parameters).
reactify now runs on the Roku unmodified, identical to the JVM (see "reactify on
Roku, UNMODIFIED"), and the stand-in is gone.

Portability map:

| Layer | Status |
|---|---|
| reactify | ✅ JS/JVM/Native; emits to Roku |
| `Prop[T](getter, setter)` | ✅ backend-neutral, zero changes |
| youi `core` + `spatial` (crossProject) | ✅ cross-platform |
| `Component.element`, `css`, concrete Props | ⛔ the only DOM-bound code |

**universal-ui's one invention** = an abstract **render node** (attribute
get/set, a style/property bag, children, measure, events) that
`Component`/`Feature` build their `Prop`s against, instead of `html.Element`.
DOM becomes *one* implementation of it (youi's existing `dom`/`ui` code);
Roku-SceneGraph / Skia / Android-Views are others.

## Reuse from youi (agreed: bring the code into universal-ui)

Decision: bring youi's proven code **into universal-ui** and evolve it here (new
name, full control) rather than refactor the abandoned youi tree in place.

youi modules (`/home/mhicks/projects/open/youi`, Scala 3.8.2, reactify 4.2.0):

- `core` — crossProject(JS,JVM), depends on reactify. Portable primitives
  (Color, storage, streams, util). **Reusable.**
- `spatial` — crossProject(JS,JVM). Geometry (points/sizes/matrices).
  **Reusable.**
- `dom` — JS-only. DOM bindings. **= the web backend.**
- `ui` — JS-only. `Component`/`Feature`/`Prop` + widgets. **The component model
  to generalize** (extract the render-node seam out of it).
- `capacitor`, `optimizer`, `example`, `utilities` — later/optional.

Key files to study when extracting the seam:
- `ui/src/main/scala/youi/component/Component.scala` (the DOM coupling)
- `ui/src/main/scala/youi/component/types/Prop.scala` (the atomic seam — ~20 LOC)
- `ui/src/main/scala/youi/component/feature/{Feature,SizeFeature,...}.scala`
- `example/.../ui/HelloWorld.scala` (reference API shape)

## Early proof — *history* (done in the sart repo, before the extraction)

**Reactive-on-Roku — PROVEN on the physical device (2026-09-12).** A minimal
Sart-friendly reactive core (Var + observer list) authored in Scala, compiled
`--target=roku` → BrightScript, sideloaded to the Roku, printed the correct
reactive-propagation sequence in the debug console. Proves a Var→observer→
derived-Var→observer loop executes correctly on real Roku hardware — the exact
chain `Prop` will drive against a SceneGraph node.

**RokuEmitter generalizations landed** (sart master, commit `92128ca`;
100/100 emitter goldens green):
- operator-method name mangling (`::`→`_colon_colon`, `:=`→`_colon_eq`, …)
- cross-object user-method dispatch (`Owner_method(recv, args)`)
- user-instance `.apply()` (`v()` → `Var_apply(v)`)
- function-VALUE invocation via `SartInvoke`
- list `::`/`:::`/`++` + `_concat`/`println` runtime helpers
- `NoDenotation.owner` guard in `emitRef` (RokuEmitter + JsEmitter)

**Facade regression fixed** (sart master, commit `52d19bb`): phantom generic
type params now emit covariant (`+T`), fixing `RouterConfig[Any]` /
`MaterialApp.router` for downstream Flutter-target builds. (Flutter is dropped
here, but the fix unblocked another project.)

**Still pending for reactify-VERBATIM on Roku** — this early estimate listed
three items and under-counted; the measured list is ten, in
"Emitting reactify UNMODIFIED" below, derived by actually emitting the library
rather than by reading it.

## Render-node seam vertical slice — ✅ DONE (2026-09-12)

The abstraction is proven end to end on two backends that share ONE set of
sources. `shared/src/main/scala/universal/` holds the whole portable layer:

| File | What it is |
|---|---|
| `ui/Prop.scala` | **the atomic seam** — youi's `Prop(getter, setter) extends reactify.Var` |
| `ui/Component.scala` | `x`/`y` Props built on a `RenderNode`, not an element |
| `ui/Label.scala` | `content` Prop |
| `app/HelloWorld.scala` | the app — names no DOM or SceneGraph type |
| `app/FrameProbe.scala` | the animation measurement |

Each backend supplies its own `universal.render.RenderNode` with an identical
signature (`getText/setText/getX/setX/getY/setY/append`) plus `Nodes` and
`Platform`. **The backend is selected by SOURCE ROOT at compile time** — the
Scala.js / Scala Native pattern — not by subtype dispatch. One app targets one
backend, so nothing is lost and nothing pays for dispatch.

- **Roku** (`roku/`): `RenderNode` over a SceneGraph node via ten `@native`
  facades, backed by `roku/runtime/UniversalRuntime.brs`; `emitRoku` copies it
  into the channel and splices a `<script>` line into the generated
  `AppScene.xml`.
- **Web** (`web/`): REAL Scala.js over the live DOM — the web tier's whole
  argument (browser form semantics, a11y, find-in-page) only holds if the web
  backend genuinely IS the DOM. Built with **scala-cli**, because Scala.js has
  no sbt 2 plugin (only `sbt-scalajs_2.12_1.0` exists on Central).

**Verified on the physical Roku** (Hisense 4Series-40 / K213X, 720p UI,
192.168.1.112): device screenshot shows the label rendered, and the debug
console reads back through the seam's getter half —

```
[ui] mounted: Hello, World!         | node: Hello, World!
[ui] updated: Hello, Universal UI!  | node: Hello, Universal UI!
[ui] x: 100 | node x: 100
```

The `node:` half is the real proof: it asks the SceneGraph node itself, so the
Prop's setter demonstrably reached the backend, not just the reactive cell.

**Verified in Chrome** (headless, same shared sources): `#app` contains
`<div style="left: 100px; ...">Hello, Universal UI!</div>`.

## Animation on the TV tier — ✅ MEASURED, and the answer is yes

Every frame pushes new values through the FULL chain — `Var.set` → observer →
`Prop.set` → `RenderNode.setX` → SceneGraph field — never poking nodes
directly, so these are the rates the ABSTRACTION sustains.

| Bound animated labels | FPS on the Roku |
|---|---|
| 1 | 59.94 |
| 25 | 59.94 |
| 100 | 59.94 |
| 150 | 51.6 |
| 200 | 38.0 |
| 250 | 31.3 |
| 400 | 19.5 |
| 1000 | 7.7 |

**Budget: ~100 simultaneously animated bound properties at a locked 60 fps.**

A three-way decomposition at n=400 says the reactive layer is NOT the
bottleneck:

| Configuration | FPS | Reads as |
|---|---|---|
| 400 nodes, 400 animated, bound | 19.5 | the full cost |
| 400 nodes, **1** animated | 30.0 | merely HAVING 400 SceneGraph Labels on screen already halves the frame rate — pure render cost, nothing to do with us |
| 400 animated, **unbound** (reactive only, no node writes) | 101.8 | 400 full chain traversals per frame cost ~10 ms total, and the Timer free-runs past vsync when the scene is never dirtied |

So the cost is dominated by SceneGraph node count and field writes (~45 µs per
`translation` write), not by reactive propagation (~25 µs per full traversal,
and that figure still includes timer overhead). **The framework can promise
real animation on the TV tier**; the thing to budget is how many nodes are on
screen and how many fields change per frame.

Caveat: the unbound row free-runs above vsync, so it is not frame-for-frame
comparable with the others — it bounds the reactive cost, it does not price a
frame. `FrameProbe`'s `Count` / `Animated` / `Bound` constants are the sweep
knobs; `/tmp/.../sweep2.sh`-style re-emit-and-sideload loops regenerate the
table.

## youi's `AnimationExample`, first reproduction — *history* (superseded by "youi's AnimationExample, verbatim")

youi's `example/.../ui/AnimationExample.scala` — one labelled row per easing
curve, each running `right → sleep → left → sleep` forever, plus an image
spinning while it bounces around the four edges — now runs from `shared/` on
both backends. This was the real test of whether youi's API survives the seam,
because it needs the two things youi does with subtyping.

**Easings** (`shared/ui/Easing.scala`) — all 31 curves, youi's verbatim,
renormalised from `(time, start, change, duration)` to 0..1 progress (which is
all youi's own `wrap` did). youi's `trait Easing { def calculate(p) }` is a SAM,
so here **an easing IS `Double => Double`**. Not a simplification for its own
sake: a heterogeneous `List[Easing]` cannot dispatch `calculate` virtually on a
flattened emitter, but a list of defunctionalised closures dispatches
perfectly — `RtInvoke` is exactly a vtable of one method. **Functions are the
cheapest polymorphism on every backend.**

**The task DSL** (`shared/ui/Animation.scala`) — `animate` / `sleep` / `sync` /
`sequential` / `parallel` / `forever`, plus an `Animator` pump. youi's
`trait Task { def update(delta, reset): Conclusion }` with five subclasses in a
`List[Task]` is textbook virtual dispatch; here **a `Task` IS its update
function**, same state machine line for line, closures instead of subclasses.
`Conclusion` becomes a Boolean (an emitted `enum` is its own gap). Per-task
state lives in a `Var`, never a captured local — the emitters capture a
closure's environment BY VALUE, so a mutated local silently resets each frame.

Departures from youi, both forced by what the seam does not have yet:
 - youi animates `position.right` / `position.bottom`, DERIVED coordinates that
   need the component's measured width and height. Rows animate `x` between
   explicit bounds instead — same motion, arithmetic done in the example.
 - youi's rotation is in turns; `RenderNode.setRotation` takes radians.

### The two backends agree, measured rather than eyeballed

Rather than compare by eye, the demo can run deterministically: `Deterministic`
steps the animation 180 virtual frames at a fixed 1/60 s SYNCHRONOUSLY at
startup, then freezes. Both backends then hold the identical animation time —
no dependence on either one's real frame rate — and report every position.

| Comparison | Result |
|---|---|
| 31 easing curves x 4 sample points | max difference **5.0e-6** |
| 31 row positions at frame 180 | max relative difference **8.2e-4** (sub-pixel; worst cases are values near zero) |
| icon x / rotation at frame 180 | 1319.8200 vs 1319.8181 px; 5.592030 vs 5.592035 rad |

That residue is exactly 32-bit float rounding: BrightScript floats are single
precision, Scala.js doubles are not. The one genuine difference is the icon's
`y` (440 on Roku, 396.5 on web) — the Roku lays out in its manifest's fixed
1920x1080 design space while the browser viewport was 1920x**993**, so
`height / 2 - IconSize / 2` differs. Correct behaviour, different screen.

**Live, the whole example holds 59.94 fps on the Roku** — 31 rows plus the
image is ~130 bound properties changing per frame, right at the budget the
FrameProbe sweep measured. (A headless browser under virtual time cannot be
asked for a frame rate; rAF is throttled there, so the web figure in that mode
is an artifact, not a measurement. `tools/check.sh --live` measures it in a
real browser instead — see "AnimationExample on every platform".)

## Web payload — the "one lean UI" claim, measured

Development builds stay unoptimized; `scala-cli --power package web --js-mode
release` runs the Closure compiler.

| | raw | gzip | **brotli** |
|---|---|---|---|
| Dev build (what we iterate on) | 484 KB | 66 KB | 50 KB |
| Release — AnimationExample (31 curves + task DSL + image) | 97 KB | 27 KB | **23 KB** |
| Release — framework floor (reactive core + Prop + Component + Label + DOM backend) | 25 KB | 8.2 KB | **7.3 KB** |

Whole page over the wire, release + brotli, icon PNG included: **29.4 KB**.

For the comparison DESIGN already makes against Flutter Web / CanvasKit
(~1.5–2.5 MB of WASM before any app code): **the framework floor here is 7.3 KB**,
roughly 200x smaller. The lean claim holds with a number behind it.

## Serving the web build — Spice, not `python3 -m http.server`

`server/` is a small JVM project on **spice-server + spice-server-undertow**
(outr's own stack, the one youi's example server used), consumed from
`~/.ivy2/local`. `sbt "server/run"` serves `web/out-js` on
http://localhost:8731/; `sbt "server/run 9000 some/dir"` overrides port and
root. This is the seam for the backend half of the system — API endpoints,
WebSocket hot-reload, OpenAPI — rather than a dev-only prop.

Spice ships `ClassLoaderPath`, which serves from the CLASSPATH. That is wrong
here: `web/out-js/main.js` is rewritten by every scala-cli build and a classpath
copy would be frozen at JVM start. `server/.../DirectoryPath.scala` reads
through to the file per request, so a rebuild shows up on the next reload with
no server restart. It resolves against the root's canonical path and rejects
anything that escapes it (verified: `/../../../etc/passwd` → 404).

## Roku compiler fixes — *history* (made in the sart repo, now in `compiler/`)

Three RokuEmitter/JsEmitter generalizations, all forced by the slice:

1. **`other.field` collapsed to `self.field`.** `inCurrentClass` tests symbol
   OWNERSHIP, so it was also true for another instance of the same class —
   `child.node` inside a `Component` method emitted `self.node`. Now guarded on
   the receiver actually being `this`; a paren-less getter on another user
   instance emits `Owner_getter(recv)` rather than reading a function ref.
   Same bug, same fix, in `JsEmitter`.
2. **A user class's own method lost to a builtin of the same name.** The
   collection/string cases match on NAME alone, so `Var.map` became `_arrMap`.
   User-owned methods now take precedence.
3. **Module member calls double-applied.** `emitRef` renders a module member as
   `Obj_m()`, assuming a no-arg getter, and the generic tail then applied it
   again — `Nodes_root()()`. Covers the bare-Ident form (`build(x)` inside the
   same object), which is how it actually surfaced, at runtime, on the device.

Three more, forced by the AnimationExample port — and these were not merely
missing, they were **silently wrong**:

4. **A `def` returning a lambda emitted `SartTodo("Closure")`.** Scala wraps a
   closure as `Block(List(defdef), Closure)`; `emitBody`'s general Block case
   tore that apart, dropped the DefDef and handed a bare `Closure` to
   `emitTerm`, which has no case for one. Every `Easing.backIn(overshoot)`-style
   curve became a placeholder Label node. Fixed in both emitters.
5. **`if` in return position dropped its branches' statements.** An `if` forced
   through `emitTerm` becomes `_iif(c, a, b)` — and BrightScript evaluates BOTH
   arguments, so a branch that is a block of statements loses them entirely,
   with no warning. `Task.sequential` emitted as nested `_iif`s with every
   `Var_set` gone, which is why the animation ran once and stopped.
   Return-position `if`s now emit as real if/else statements whose branches
   return.
6. **A control-flow tail after `val`s took the arg-hoist path.** `isStmtExpr`
   recognised only `Assign`, so `{ val demo = "animation"; if demo == "hello"
   then a() else b() }` folded into `_iif(..., HelloWorld_run_(),
   AnimationExample_run_())` and **ran both demos**. `If`/`Match`/`Try` tails
   are now statements.

Regression check: re-emitting sart's own 13 demos (`sartEmitRoku` +
`sartEmitJs`) with all six patches leaves the JS **byte-for-byte identical** and
changes the Roku output only where it was already broken:

 - `TvAppState.onKey` was emitting `_iif((key = Left), true, _iif(...))` — **all
   four of its `setState(...)` calls had been silently discarded.** The TV
   demo's remote handling never worked.
 - `_iif((d = 0), SartThrow("divide by zero"), 100.0 / d)` threw on EVERY call,
   because both arms evaluate.
 - `_iif(jsonDecode(payload)("name").isNull, ..., jsonDecode(payload)("name").str)`
   decoded the payload twice.

This is the case for Roku emitter goldens in one paragraph: six real bugs, three
of them silently wrong output in shipped demos, none of them visible to the 100
Dart goldens.

⚠️ **The 100 emitter goldens cover `DartEmitter` ONLY** — there is no test
coverage for the Roku or JS emitters at all. All three bugs above were found by
reading emitted BrightScript and by a device crash, with zero test signal. The
goldens stayed 100/100 green throughout, which means exactly nothing for these
changes.

## reactify on Roku, UNMODIFIED — ✅ IDENTICAL TO THE JVM (2026-09-12)

reactify v4.2.0 (1010 lines, 24 files) compiles to BrightScript
with **not one line of reactify changed**, and on the physical Roku it produces
output byte-identical to the JVM. This is the cross-build proof: the Roku
compiler consumes a library written for the JVM, not code written for it.

**The proof is reproducible.** `reactive-test/` is an acceptance suite over
reactify's public API: 42 checks (plus 22 hot-path edge cases, see the performance pass) covering Var/Val, automatic and dynamic
dependency tracking, attach/detach, changes/once, priorities and
stopPropagation, anonymous Reactions, Channel/Trigger/map, Dep, groups,
two-way bind, Transaction, the ListVar sugar and failures. On the JVM
(`sbt reactiveTest/run`) it writes `reactive-test/expected-output.txt`. The same
program, emitted with reactify compiled through as a library
(`sbt reactiveTest/emitRoku`) and run on the Roku, must match it line for
line. `ROKU_PASS=… tools/roku-acceptance.sh` sideloads, captures and diffs:

```
install: Install Success
captured 65 suite lines
PASS: device output is identical to the JVM (65 lines)
```

`tools/emit-gaps.py <channel>/components` is the static meter: `RtTodo`
markers, dangling symbols, duplicate definitions, unknown method calls. For
reactify alone and for the suite, every count is **0**.

### What the compiler needed (now in `compiler/`)

| Feature | What it does | reactify construct that needed it |
|---|---|---|
| Object model | vtable per concrete class from its linearization (most-derived wins); `C_init` chains super → mixins (guarded for diamonds) → own fields and constructor statements; calls on a model member dispatch `recv.m(recv, …)` | `Var extends Val with Mutable`; groups overriding `set`/`get`; `GroupReactions` overriding `+=` |
| Super calls | resolved statically to the implementation TASTy names | `super.+=`, `super.static` |
| Lazy vals | virtual getter slot, value cached on first read | `override lazy val reactions` in every group |
| Vals overriding defs | slot holds a getter, value under `__v_<name>` | `override val priority` in `FunctionReaction` |
| Default arguments | instance defaults emitted as methods; companion defaults inlined; dropped only for callees the emitter doesn't write | `attach(f, priority = Priority.Normal)`, `fire(…, reactions = this.reactions())` |
| Namespaces | companion members take an `Obj` infix when they would collide with a type's; overloads get an arity suffix | `Val.set` vs companion `Val.set` (the instance one recursed into itself); `Transaction.commit()` / `commit(v)` |
| By-name parameters | a zero-arg thunk at the call site, forced at EVERY use in the callee | `Var(f)`, `Val(…)`, `:=`, `Transaction { … }` — dependency tracking depends on re-evaluation |
| Boxed captured `var`s | a `var` captured by a closure/thunk/local class becomes `{ v: … }` | `once`: `var reaction = null; … reaction = Reaction(function, …)` |
| A-normalisation | every expression is emitted INTO a sink (discard / return / assign); control flow in value position is hoisted into statements with left-to-right order preserved; `_iif` only for pure arms | `val evaluated = try … finally …`; `if useA() then a() else b()` must not touch `b` |
| try / catch / finally | real `throw`; unmatched exceptions rethrown; `finally` always runs, including after a throw from a handler | `Reactive.fire`, `bind`, `ChangeFunctionReaction.apply` |
| Local / anonymous classes | emitted out of line with `__outer` and captured locals; can extend runtime classes | `new Reaction[T] { … }`, `new ThreadLocal[…] { override def initialValue() … }` |
| Patterns | stable identifiers match by equality (they previously ALWAYS matched); `Some(x)` on a nullable Option; guards see their bindings; `MatchError` when nothing matches | `case BindSet.LeftToRight =>`, `case Some(e) if e.v eq v =>` |
| Module vals | evaluated once, as Scala objects are | `Val.evaluating` — previously a NEW ThreadLocal on every access |
| Stdlib shims | structural `_eq` and identity `RtIdentity` (objects stamped with an id on first ask); Set; object-keyed Map; Option/Try; ThreadLocal; AtomicBoolean; Promise/Future; Ordered sort; `synchronized`; `assert`; `Double.MinValue/MaxValue`; `->`; case-class `copy`; implicit-class factories; curried calls | throughout |

### Two bugs only the device found

Worth remembering, because both produced code that looked right:

- **`xs ::: ys` is right-associative** — it desugars to `ys.:::(xs)`, so the
  receiver is the SUFFIX. The emitter concatenated receiver-first, so every
  `list ::: List(x)` append was a prepend. Reactions fired in reverse; it had
  also been silently reversing lists in sart's own demos.
- **Boxed numbers change `type()`.** A value read back out of an array or assoc
  array reports `type() = "roInteger"`, which a type-name table missed, so
  `_eq(2, 2)` was false. Every Var re-fired on unchanged values, and a two-way
  `bind` recursed until the stack overflowed. `_rtKind` now classifies by
  interface (`ifInt`, `ifFloat`, `ifDouble`, `ifLongInt`, …), which is the same
  boxed or not.

### Regression

- sart's 13 demos: JS output **byte-identical**; Roku `RtTodo` count
  unchanged (4); brighterscript reports **no new diagnostics** and two
  pre-existing ones are gone (an undefined `global`, an arity mismatch).
- universal-ui's AnimationExample: brighterscript diagnostics unchanged; on the
  Roku it renders correctly and holds **59.94 fps**.
- The 100 Dart goldens stay green — they cover none of this.

### Known limitations (none reached by reactify)

- A match guard that itself needs hoisted statements emits a todo.
- A `while` condition that needs hoisted statements is hoisted once, not per
  iteration.
- `head :: tail` list patterns (a list is an array; there is no `_cls` to test).
- Local `def`s inside method bodies are dropped.
- `Some(())` collapses to `None`: Option lowers to a nullable. `RtUnit()`
  exists in the runtime but the emitter doesn't produce it yet.
- BrightScript is case-insensitive, so a parameter named `text` shadows the
  runtime's `Text()` (BS1104; pre-existing in universal-ui, harmless unless the
  function calls `Text`).
- A virtual call whose receiver isn't a simple value goes through
  `RtVcallN` — one extra call.
- `pureValue` never sees unary `!` / `-`: the general `Select` case above it
  matches first (the build's one "Unreachable case" warning, inherited with the
  port). A negated value is treated as impure, so it is hoisted instead of
  inlined — slower output, never wrong output.

## reactify in the product — correct on both backends (2026-09-12)

`shared/` now uses reactify on every backend: `Prop` is youi's
`Prop extends reactify.Var` again, bindings are reactify expressions
(`label.x := pos()`, `label.content := "Hello, " + name() + "!"`) resolved by
reactify's dependency tracking, and the hand-rolled `Var` is gone. Animation
bookkeeping (elapsed time, remaining sequence) is plain `var`s, as in youi's
own `DurationTask`. The seam proof prints identical lines on the Roku and in
the browser.

Switching cost a lot on the Roku at first: the 60 fps budget fell from ~100
animated properties (lean Var) to ~15–20, about 1.05 ms per bound property per
frame. The optimisation pass below halved that without changing any reactify
behaviour.

## reactify performance pass — 2.2× on Roku, ~2× on Scala.js, behaviour unchanged (2026-09-12)

⚠️ The library half is **uncommitted in the reactify repo**
(`/home/mhicks/projects/open/reactify`, 5 files, including the `_references`
fix below). The compiler half is in universal-ui's `compiler/`; it was made in
the sart repo before the extraction, which still holds it uncommitted.

### Frame rate (Roku FrameProbe sweep, every label bound and on screen)

| animated | lean Var | reactify, before | reactify, after |
|---|---|---|---|
| 15 | 59.9 | 59.9 | 59.9 |
| 25 | 59.9 | 38.3 | **59.9** |
| 50 | — | 19.4 | **37.4** |
| 100 | 59.9 | 9.5 | **18.9** |
| 100, unbound (no node) | — | 42.1 | **77.2** |

Above the knee fps ≈ 1890 / n: **~0.53 ms per animated property per frame**
(was 1.05), so the 60 fps budget is now **~30 bound properties**.
AnimationExample holds 59.9 fps. (The unbound row exceeds 60 because
`Platform.animate`'s Timer isn't vsync-locked when nothing renders.)

### Per operation (`ReactifyBench`, `val demo = "bench"`)

Roku, µs/op — before any performance work → after:

| step | before | after |
|---|---|---|
| read `v()` | 16 | 11 |
| `v @= x`, no reactions | 194 | 90 |
| `v @= x`, same value | 110 | 57 |
| `v @= x`, 1 attached reaction | 380 | 158 |
| `channel @= x`, 1 attached reaction | — | 94 |
| `v @= x`, 1 dependent Var | 685 | 299 |
| `v @= x` → bound Prop (no-op setter) | 867 | 376 |
| `v @= x` → bound Prop → node | **915** | **422** |
| direct node write (floor) | 29 | 35 |
| `Var(1.0)` creation | 260 | 164 |

Scala.js release (Closure), warm pass, median of 5, µs/op — original reactify →
optimised: 1 dependent Var 0.272 → 0.117, bound Prop 0.300 → 0.146, bound Prop
→ node 0.473 → 0.261, `@=` 0.058 → 0.042, read 0.006 → 0.002,
`reactions +=` of an attached reaction 0.139 → 0.004.

Where the Roku time went, measured step by step on bound Prop → node:
runtime fast paths 915 → 825; stable by-name args without thunks 825 → 804;
library fast paths plus the object-`var` fix they needed 804 → 757;
inline `synchronized` and no by-name `doFire` 757 → 666; **case objects
compared by identity** 666 → 422. That last one was the big one: `fire`
compared `status == ReactionStatus.Continue` three times per reaction through
`_eq`'s full structural walk.

### What changed

**reactify** (each change keeps the old observable behaviour exactly):
- `Val.evaluate` skips the reference diff when there are no dependencies (every
  `@=`). The `evaluating` ThreadLocal holds the `Evaluating` or null, so there's
  no `Option` per evaluation or per read. `option` no longer allocates a closure.
- `Var.set` builds one closure, shared by `Transaction.change` and the
  evaluation (`Val.setFunction`). `Var.static` calls `set` directly, which is
  the dispatch `super.static` → `Mutable.static` already did.
- `Reactive.fire` returns `Continue` at once when there are no reactions, the
  Reactive isn't `synchronize`d, and no propagation is active on this thread.
  A nested fire must still take the full path, because its `remove()` ends the
  outer propagation. The by-name `doFire` became a private `fireNow`.
- `Reactions.+=` skips the re-sort when that instance is already attached.
  Every re-evaluation of a dependent Val used to `sorted.distinct` its
  dependency's list.
- `Transaction` counts open transactions. While none are open, `change` (called
  on every set) skips the ThreadLocal.

**Roku compiler / runtime:**
- **Correctness fix:** an `object`'s `var` compiled to a getter returning the
  initialiser, and an assignment to it emitted invalid BrightScript
  (`X_open() = …`). It now has cached storage plus a `__set` function.
  reactify's transaction counter was the first code to hit this.
- `==` / `!=` / `Option.contains` against an `object` (case objects, `None`,
  `Nil`) → `RtIdentity`.
- `x == null` / `x ne null` on a class-typed value → `x = invalid`. Never
  applied to strings, numbers, functions or `Any`.
- `synchronized { … }` in statement or return position is inlined, since a
  BrightScript thread shares no memory.
- By-name arguments that are stable values (literals, immutable locals,
  `this`) pass without a thunk. `case None` is a direct test. A duplicate class
  test in typed extractor patterns is gone.
- Runtime fast paths: `RtIdentity` and `RtInvoke` check for an object
  first; earlier, `_rtKind`, `RtForce`, `_eq` and `_setDiff`.

### How it was verified

- **21 edge-case checks** were added to `reactive-test` (now 63 checks,
  64 lines). Their expected values come from the **unmodified** library, so a
  fast path that changes any corner fails. They cover:
  - self-reads seeing the previous value
  - a dropped dependency staying attached
  - `Reactions` distinct by equality
  - a nested empty fire ending the outer propagation
  - `status` during and after a fire
  - `Val.static`
  - `@=` with the same value
  - a failed first evaluation
  - nested, reverted and thrown `Transaction`s
- JVM output is identical to that golden, and the Roku is identical to the JVM
  (`tools/roku-acceptance.sh`, 64 lines).
- reactify's own 67 tests pass on the JVM and Scala.js. The compiler's goldens
  (still in the sart repo at the time) pass
  100/100, brighterscript reports no new error kinds, and the gap meter is 0.

### Found while pinning behaviour (reactify)

- **FIXED (approved 2026-09-12): `Val.evaluate` never assigned
  `_references`.** So `references` was always empty, a Val whose function
  switched dependencies stayed attached to the old ones forever, and every
  re-evaluation re-added its reaction to each dependency. It now records
  dependencies when they change, detaching from dropped ones and attaching to
  new ones, and `Val.static` detaches too.
  - Suite: `edge.refs.old.dep.detached = 0` and `edge.references = 1` replaced
    the pinned leak values; `edge.val.static.detached = 0` is new (64 checks).
  - reactify's 67 tests pass on the JVM and Scala.js; the Roku is identical to
    the JVM (65 lines); the Roku bench is unchanged (bound Prop → node 421 µs).

Not changed — still decisions:

- A Val whose **first** evaluation throws never records its dependencies, so it
  stays failed when they change (`edge.failure.first.eval.untracked`).
- A `Transaction` whose body throws is never closed; the thread stays in it
  (`edge.txn.throw.stays.active`).

### Remaining Roku candidates (not done)

- `Var` creation at 164 µs.
- Per fire: a `_getOrElseThunk` closure, repeated `status` ThreadLocal lookups,
  and a `_drop` array copy per `@tailrec` step.
- Per evaluation: the `Evaluating` and `Success` allocations, plus
  `_setAdd` / `_setDiff`.
- Pattern matches on case objects (`case BindSet.LeftToRight =>`) still use
  `_eq`.

## Sart extraction — universal-ui owns its Roku toolchain (2026-09-12)

Decision: **Sart is reference-only.** Nothing in the build, the sources or a
generated channel depends on it or carries its name.

### What moved, and where

| Was (sart repo / plugin) | Now (universal-ui) |
|---|---|
| `sbt-sart` plugin, compiler from `~/.ivy2/local` | `compiler/` — an in-build project on `scala3-tasty-inspector`; `universal.compiler.Main` (Roku only) |
| `sart.dart.native` | `compiler-api/` — `universal.compiler.api.native` |
| `sart.stdlib.math` for `Platform.sqrt/pow/sin/cos` | `uiSqrt` / `uiPow` / `uiSin` / `uiCos` facades in `UniversalRuntime.brs` |
| `sartEmitRoku` task | `emitRoku` (reactify compiled through via `--library`) |
| `SartScene.xml/.brs`, `SartRuntime.brs` | `AppScene.xml/.brs`, `ScalaRuntime.brs` |
| runtime names `Sart*`, `_sart*`, `__SART_*`, `ga.sartRoot` | `Rt*`, `_rt*`, `__RT_*`, `ga.appRoot` |
| `tools/sart-gaps.py` | `tools/emit-gaps.py` |
| `example/` (the first Sart probe) | retired; a copy is in the session scratchpad only |

The GitHub Packages resolver (there for Sart's siblings) is gone; `server/`
still resolves Spice.

### What was removed

Sart's **Flutter-on-SceneGraph layer**, which universal-ui never used (its
render seam is `UniversalRuntime.brs`):
- the widget, theme/token, media-player, render-normalisation, facade-stub and
  D-pad-focus sections of the runtime, including `RtInitGlobals` and its
  Flutter facade namespaces;
- the scene's key routing into that focus system (Back now gets SceneGraph's
  default handling, verified: it returns to the Roku home screen);
- the emitter's `setState` path, the rounded 9-patch image, Dart
  `async`/`await` markers and `sart.stdlib` type aliases.

The compiler went from 3,816 to **2,962 lines**; the generated runtime from
1,804 to **980 lines**. What remains is the Scala semantics BrightScript lacks
(equality and identity, collections, Option/Try, closures, exceptions, the
object model, stdlib shims) plus generic facade calls.

### How it was verified

- JVM suite identical to the golden; on the Roku, `tools/roku-acceptance.sh`
  PASS (65 lines) after the port and again after the prune.
- Gap meter on all three channels (`reactive`, `reactive-test`, `roku`): 0
  todos, 0 dangling symbols, 0 unknown methods. brighterscript: no new error
  kinds. No generated file mentions Sart.
- Easings dump byte-identical to the pre-port Roku capture (proves the new
  math facades); AnimationExample 59.9 fps; ReactifyBench unchanged (bound Prop
  → node 421 µs).
- The sart repo was only read, never changed, during the extraction. It still
  holds the earlier emitter and reactify-performance work uncommitted.

## youi's AnimationExample, verbatim — youi's component model on both backends (2026-09-12)

`shared/src/main/scala/universal/example/ui/AnimationExample.scala` is youi's
`example/.../ui/AnimationExample.scala` with only its package and imports
changed (`youi.*` → `universal.*`, `rapid.Task` → `universal.async.Task`,
`spice.net._` → `universal.net._`). The class body is youi's, line for line:
`new TextView { content @= …; font.weight @= fnt; position.left @= 50.0 }`,
``label.position.right `to` container.size.width - 50.0 `in` 5.seconds `easing` easingFunction``,
`forever(sequential(…)).start(this)`, `container.children += label`,
`Image("/images/icon.png").flatMap { img => GoogleFont.`Open Sans`.`regular`.load().map { fnt => … } }`.

Deliberate differences from youi's file:
- One line reads `label.position.right.to(…).in(5.seconds).easing(easingFunction)`
  (method-call syntax) instead of youi's backtick infix form — changed on disk
  after the port was written; same meaning.
- **A youi bug, fixed.** youi puts the bouncing logo in `container` (which starts
  under the 75px header) but gives it screen coordinates: it starts at
  `container.size.middle + header.size.height`, bounces to
  `bottom to ui.size.height` and `top to header.size.height`. So it started 75px
  low, never reached the top, and ran 75px off the bottom (youi's own capture
  shows the doubled offset: centre at 75 + 502.5 + 75). universal-ui uses the
  container's coordinates — `container.size.middle`,
  `bottom to container.size.height`, `top to 0.0`. youi is the reference, not
  always correct: its bugs are fixed here and noted.

### What made that possible — youi's model, ported onto `RenderNode`

| Package (`shared/.../universal/`) | From youi |
|---|---|
| `component/` — `Component`, `Container`, `TextView`, `ImageView` | `youi.component` over a `RenderNode` instead of an `html.Element` |
| `component/feature/` — measured, size, position (`left/center/right`, `top/middle/bottom` as reactify `Dep`s), font, container children | `youi.component.feature` |
| `component/types/` — `Prop` (youi's own), `SizeProperty`, `PositionType`, `WhiteSpace`, `FontWeight` | `youi.component.types` |
| `task/` — `Animation` (youi's `task.Task`), `AnimateIn`, `PartialAnimate`, `Sequential`, `Parallel`, `Repeat`, `Sleep`, `Action`, `TaskInstance`, `TaskSupport`, the `to`/`forever`/`sequential` DSL | `youi.task`, line for line |
| `easing/` | youi's `Easing` files, verbatim |
| `app/screen/` — `Screen`, `UIScreen`, `ScreenManager` (reduced to running one screen) | `youi.app.screen` |
| `example/` — `ExampleApp`, `UIExampleScreen`, `Header` | youi's example app |
| `ui`, `Color`, `scribe`, `async.Task`, `net`, `image.Image`, `font.GoogleFont` | the parts of youi / rapid / spice the example uses |

youi's `rapid.Task` becomes `universal.async.Task` (callback-driven) and youi's
`task.Task` becomes `Animation`: the Roku compiler names classes by their simple
name, so two `Task`s would collide. The example never names the latter.

**Backends** gained what the model needs, with identical signatures:
containers, width/height, colour, background, font (family/weight/size), measured
size, image source, removal, a Material Icons node, async image and font loading,
`resolvePath`, `onResize`. **Assets:** `assets/fonts/` (Open Sans 400/600 and
Material Icons as TTF, from the same Google Fonts youi loads), `youi.png`, and a
pre-rendered header gradient so both backends draw the same pixels.

### What the Roku compiler needed

- Objects that extend a user class or trait (including case objects) are
  singleton instances: `<Name>__module()`, stored before init as the JVM stores
  `MODULE$`; nested names owner-qualified (`ReactionStatus_Continue`).
- Superclass secondary constructors (`extends Var[T](getter)`), with constructor
  params read as locals and captured by closures in parent arguments.
- Trait super accessors (`pkg$T$$super$m`) resolved statically.
- Varargs to emitted functions as one array (`sequential(a, b, c)`, `Prop(…, callbacks*)`).
- A method named like a field (`AnimateIn(easing: Easing)` + `def easing(e)`) gets its own slot.
- Backtick names (`Open Sans`, `600`) sanitised; the init chain renamed `__init` so a user `init` can't collide.
- Stdlib: `Map(k -> v, …)`, `.toList`, `scala.math`, `scala.concurrent.duration`,
  `StringContext.s`, `String.length/isEmpty/indexOf`, `augmentString(…).nonEmpty`,
  case-class `toString` with Scala's `,`.
- Faster: `case SomeObject =>` is an identity test; `_eq` on class instances is identity.

### How it was verified

- **Web vs youi**, same frozen moment (`ExampleApp.freezeSeconds`, a virtual 60 Hz
  clock): logo, toggle, header gradient and all 31 rows within 2px of youi's own
  capture; the icon's centre identical.
- **Roku vs web**, component-tree dumps of the same frozen frame: row positions
  median 0.02px apart; text widths within 1% (after font calibration, below); row
  text at youi's height (median vertical offset 0px on the device screenshot).
- reactify: JVM golden unchanged, Roku acceptance PASS (65 lines); gap meter 0 and
  brighterscript 0 errors on both channels.

### Remaining differences (known)

- youi's header "600" weight is the browser's synthesized bold (its webfont loader
  only fetches Open Sans 400); universal-ui bundles the real SemiBold, ~9px wider.
- youi starts the icon's spin one async `rapid` step later, so its early rotation phase differs.
- youi shows scrollbars when the ElasticOut row or the icon overflows; the render seam clips.
- **Roku text:** on a 720p panel SceneGraph rasterises small text at fewer pixels
  and it comes out ~7.5% narrower. `uiSetFont` calibrates each face and size once
  (measures at 4× and picks the closest whole size), which brought widths within 1%.

### Roku frame rate — ✅ 60 fps, AnimationExample unchanged

Live on the Roku, youi's AnimationExample (31 moving rows plus the icon) now holds
**59.9 fps** — over 91 one-second samples: minimum 59.4, median 59.9, 90 of 91 at
≥59.5. None of it touched `AnimationExample.scala`.

How it got there, measured with `app/YouiBench.scala` (`val demo = "youibench"`,
µs on the device):

| | position write | `right := x` | frame: 32 moving rows | live fps while moving |
|---|---|---|---|---|
| as first ported | 849 | 931 | 39.5 ms | ~17–22.5 |
| lazy vals, derived positions, one-pass fire | 221 | 315 | 16.3 ms | ~49 |
| tasks stepped directly, cached accessors | 212 | 304 | 14.4 ms | ~54 |
| case objects inline, single-reaction fire, cached translation | 172 | 266 | 12.7 ms | 58–60 |
| closures called in place | — | — | — | **59.9** |

What changed, by layer:
- **Roku compiler.** A class with a `lazy val` goes through the object model —
  the flat class path had been evaluating lazy vals eagerly, so every component
  built all four position `Dep`s and every position write re-evaluated two of
  them (the biggest single cost: `position.x` had 3 reactions instead of 1).
  Case objects are created once in `rtMain` and read inline (`ga.__mod_…`);
  object and lazy-val accessors read the cached value first; a function value in
  a local or field is called in place (`cl.fn(cl.env, […])`); a discarded value
  expression becomes an assignment; locals of a `val` initialiser are captured.
- **Framework (`shared/`).** `center/right/middle/bottom` are a `DerivedPosition`:
  youi's `Dep` behaviour (reads derive from `left` + size, tracked; writes set
  `left := x - size`), but it only keeps a derived `Val` when something attaches
  to it. Each `TaskSupport` steps its running tasks directly, in start order, with
  youi's snapshot rules, instead of one reactive fire per task per frame. The Roku
  `RenderNode` keeps its x/y and writes `translation` once.
- **reactify** (uncommitted, in the reactify repo). `fireInternal` is one pass (no
  recursion on `reactions.tail`), with a direct path for a single reaction;
  `Val.evaluate` compares dependencies by identity before set equality. reactify's
  67 tests pass on the JVM and Scala.js; the Roku acceptance suite passes (65 lines).

⚠️ **A regression this caused, and its fix.** `DerivedPosition` no longer read
the size when created, so a component's measured size (lazy) came into being only
after `container.children += label` had already measured — widths stayed 0 and
the rows slid off the right edge. `SizeFeature` now creates the measured size with
the component, as youi's does. Verified on the frozen frame: Roku rows identical to
the earlier verified frame (no zero widths), web rows within 2px of youi's capture
(the four icon-covered rows aside, as before). **Lesson:** a frame-rate change needs
the frozen-frame position check, not only "runs without errors".

## One-command checks — `tools/check.sh` (2026-09-12)

The width regression above was caught by eye. Everything that verified it is now
one command that prints PASS/FAIL per check, exits non-zero on any failure, and
keeps every log in `target/check/`:

| check | offline | `--device` |
|---|---|---|
| one sbt run: `reactiveTest/run` + all three `emitRoku`s | ✓ | ✓ |
| reactify on the JVM identical to `reactive-test/expected-output.txt` (65 lines) | ✓ | ✓ |
| `tools/emit-gaps.py`: TOTAL GAPS 0 on reactive, reactive-test, roku | ✓ | ✓ |
| brighterscript: 0 errors on all three (BS1054/BS1104 excluded) | ✓ | ✓ |
| web bundle builds (scala-cli) | ✓ | ✓ |
| the shared font table (`universal.font.Faces`) matches `assets/fonts` | ✓ | ✓ |
| web: AnimationExample frozen at 0.434s in headless Chrome over `file://`, every component vs `tools/golden/animation-tree.txt` (±1px) | ✓ | ✓ |
| desktop: the same frame in JavaFX under Xvfb, vs the same golden | ✓ | ✓ |
| web (`--live`): 60s live in a headful Chrome on a virtual 1920x1080 display (RoboBrowser), median ≥ 59.5 fps and minimum ≥ 58.5 | | |
| Android (`--android`): the APK builds; the same frame on an emulator, vs the same golden | | |
| tvOS (`--tvos`): the same frame on the Apple TV simulator (built on the Mac by tools/tvos-run.sh), and 60 s live | | |
| Roku: `tools/roku-acceptance.sh` | | ✓ |
| Roku: the same frozen frame vs the same golden | | ✓ |
| Roku: 60s live, median ≥ 59.5 fps and minimum ≥ 58.5 | | ✓ |

About 10s offline and 2.5 min with the device. `--update` rewrites the frame
goldens after an intended change; review the diff before committing it.

- **The freeze is now a runtime switch, not a code edit.** `ScreenManager.freezeSeconds`
  comes from `Platform.freezeSeconds()`: `?freeze=<seconds>` in the page URL on
  the web, `pkg:/freeze.txt` in the channel on Roku. The check adds that file to
  a test sideload, so the channel built for real use never freezes.
- **One golden for every backend** (since "AnimationExample on every platform"):
  identical geometry on every platform is what is checked, not each backend
  against its own past.
- **Proven to catch the regression:** with `SizeFeature`'s measured size made
  lazy again, the web frame check fails (text `w=0`, x positions off by up to
  94px). Two device runs gave identical Roku frames (0px) and 59.9 fps.
- Not a check: YouiBench's per-layer cost. The live frame-rate floor covers what
  it guarded, on the real workload.

## AnimationExample on every platform — one geometry (2026-09-12)

youi's AnimationExample runs unchanged on four backends, and they produce the
same frame: `tools/check.sh` compares each backend's frozen frame (0.434s) with
ONE golden, `tools/golden/animation-tree.txt` — every component's position,
size and rotation within 1px. "Identical" means that geometry, with the same
fonts and images; each platform still rasterises its own text.

| backend | tier | nodes | vs the golden | live |
|---|---|---|---|---|
| web | DOM (Scala.js) | `div`, `img` | 0 px (writes the golden) | 60.0 fps (headful Chrome on Xvfb, via RoboBrowser) |
| desktop | JavaFX 21 (JVM) | `Pane`, `Text`, `ImageView` | 0.096 px | 62 fps (Xvfb) |
| Android TV | Views (JVM → dex) | `NodeGroup`, `TextView`, `ImageView` | 0.043 px on the **Google TV Streamer** (API 34, real hardware) and on the Pixel 7 emulator | **60.0 fps on the Streamer** — 59 one-second samples, every one ≥ 59.98 after start-up; 60.0 on the emulator |
| Roku | SceneGraph | `Rectangle`, `Label`, `Poster` | **not yet re-measured** — the device was switched off | 59.9 fps before these changes |

The pixels agree too, not only the boxes: over the 1080x1080 area all three
screenshots share, fewer than 0.6% of pixels differ by more than 25% (glyph
anti-aliasing), and a row's text ink spans the same pixel rows on web, desktop
and Android (83–106 for "BackIn Example").

### What made the geometry identical

- **One line box for text — `shared/.../font/Faces.scala`.** Every platform's
  line height differs (at 24px Open Sans: Chrome 33, a Roku `Label` 37). Every
  backend now takes a text box's height from the bundled font's own metrics,
  `round((ascender + descender) × size / unitsPerEm)`, and puts the baseline in
  it from the same numbers; the web sets it as `line-height`. `tools/check.sh`
  fails if the table drifts from `assets/fonts`.
- **Widths stay native, measured exactly.** Chrome, JavaFX and Android shape
  Open Sans within 0.1px of each other. Two traps: Android's
  `Paint.measureText` rounds UP to a whole pixel (every width was the ceiling of
  the browser's) — `getRunAdvance` is exact; a Roku `Label` draws at whole-pixel
  font sizes, so its bounding rect is a few pixels off — `uiTextWidth` measures
  at 4× the size and scales back, reusing one probe `Label`.
- **Roku rotation was mirrored.** SceneGraph's `rotation` is counter-clockwise,
  CSS's clockwise, so the logo spun the wrong way on the Roku. The tree check
  could not see it (it logs the model's value); a screenshot did.
  `uiSetRotation` flips the sign.
- **The same viewport everywhere.** Headless Chrome lays out in its window minus
  87px of UI but screenshots at the full window, re-laying out first — so the web
  frame is logged from a 1920x1167 window and photographed from a 1920x1080 one.
  Android launches with `--es viewport 1920x1080`, so the phone emulator lays out
  the TV frame (and shows its top-left).

### The two new backends

- **`desktop/`** — JavaFX 21 (base + graphics) on the JVM, DESIGN's near-term
  desktop path (a GraalVM native image later). A `Pane` never moves its
  children, so nodes are placed absolutely; a `Text` is placed by its baseline.
  `sbt desktop/run` opens a 1920x1080 window.
- **`android/`** — native Views: `NodeGroup` (a `ViewGroup` that measures each
  child at its own size and lays it out at the origin; its translation places
  it), single-line `TextView` (no font padding, baseline moved onto the shared
  line box), `ImageView`. No Gradle: `sbt android/packageApk` runs d8 over the
  program, reactify and the Scala 3.8 library (5.6 MB of dex in 3s), aapt2 for
  the manifest and assets, zipalign, and apksigner with the debug key. minSdk 30,
  target 35, a LEANBACK launcher entry so it runs on Android TV — verified on the
  Google TV Streamer (2026-09-13): the app is listed by the TV launcher, the
  frozen frame matches the golden, and it holds 60 fps live at the Streamer's
  1920x1080 UI size (a 3840x2160 panel with a 1080p override).
- `Platform.frozen()` joined the seam: the desktop saves its screenshot and exits
  there; the other backends do nothing.

## tvOS — the Swift spike ✅ (2026-09-13)

**The question.** Native on Apple TV means no JVM: nothing compiles Scala for tvOS
today (RoboVM has no tvOS target; Multi-OS Engine's is a stub; Scala Native is
unofficial there, with open GC crashes on iOS; Kotlin/Native would add a third
toolchain). The candidate that fits the project is the one we already have for
Roku: **our own TASTy compiler, printing Swift**. The risk was never TASTy
coverage — Roku proved the feature set — but two things Swift adds: the emitted
code must type-check, and Swift's reference counting never frees a cycle, while
reactify's dependency graph (a `Val` holds its references, each holds the
`Val`'s reaction) is cyclic by design.

**The spike.** `compiler/.../SwiftEmitter.scala` is [[RokuEmitter]] with the syntax
swapped — the same object model (vtable slots, `<Type>__init` chain, singletons),
defunctionalised closures, A-normalisation, if-chain pattern matching and stdlib
shims, printed as Swift over one dynamic value type `V`
(`swift/runtime/ScalaRuntime.swift`, the BrightScript runtime's twin). It is
produced by `tools/make-swift-emitter.py` from the Roku source (every template
replaced exactly once, so the two cannot drift silently). Type-erased on
purpose: instance fields are `this["name"]`, a method slot is called
`this["m"](this, …)`, every function is `throws -> V`. `--target=swift` on the
compiler; `sbt reactiveTest/emitSwift`; `tools/swift-spike.sh` ships the three
Swift files to the Mac (`ssh mbp`, Xcode 26.3), builds with `swiftc`, and runs
them.

| | result |
|---|---|
| reactify's acceptance suite, macOS (arm64) | **65 lines identical** to the JVM golden, 10 ms |
| the same, **Apple TV 4K simulator, tvOS 26.2** (`simctl spawn`) | **65 lines identical**, 3 ms |
| memory, reference counting alone | leaks **386 instances per suite run** (77,200 over 200 runs) — the cycles, as predicted |
| one cycle collection after those 200 runs | frees **all 77,200**, growth back to 0, in 68 ms |
| a collection after every run | growth **0**, peak **0**; 200 runs 555 ms vs 510 ms uncollected |

**Why the collector works.** Every reference the emitted code holds lives in an
`Obj`'s fields or an `Arr`'s items — nothing hides in a Swift closure context — so
the object graph is fully visible. `rtCollectCycles(roots)` marks from the global
table (where every singleton and cached object val lives) and clears the fields
of every unmarked instance, breaking its cycles; reference counting then frees
them. It runs only where no emitted code is on the stack (between frames, in an
app), because a Swift local is not a root. That is a mark phase over the live
graph, so its cost scales with the app's object count, not the garbage's.

**What the spike cost.** ~90 template replacements over the Roku emitter, a
650-line Swift runtime, and four fixes found by the Swift compiler and the
runs: `throw` is a Swift keyword (the emitter's `throw` detection); `-target`
belongs to swiftc, not xcrun; the probe must start from a fresh state (the
suite's last test leaves a transaction open on purpose); and case objects need
initialising before any code reads them (`rtInitModules`, now emitted). Nothing
in `RokuEmitter` changed but the trait it extends; `tools/check.sh` still passes.

**What it does not show.** No UIKit yet — this is reactify and the suite, not a
frame. Types are erased, so Swift-side interop (a `UILabel` from Scala) will
need typed facades at the boundary. And the collector's cost on a real UI graph
(41 components, ~130 bound properties, per-frame updates) is unmeasured.

**Decision it enables.** Scala → Swift is viable for tvOS: correctness proven on
the simulator, the cycle question answered. The next step is the real thing —
split the compiler into shared lowering + two printers (Roku green throughout),
then a UIKit `RenderNode` held to the shared golden and 60 fps on the Apple TV
simulator, with `rtCollectCycles` between frames.

## tvOS — AnimationExample on the Apple TV simulator ✅ (2026-09-13)

The spike became the backend the same day. `tvos/` is the Roku backend's twin:
the same 36 `@native` facade names (`tvos/.../facades.scala`), the same
`RenderNode` (copied, with the bundle in place of `pkg:/`), implemented by
`swift/runtime/UniversalRuntime.swift` over UIKit — a `UILabel` for text, a
`UIImageView` for an image, a `UIView` for a container, every view placed by
its own top-left and size from a side table (so a rotation `transform`, which
makes `frame` meaningless, never disturbs it) with rotation about the centre.
`swift/app/main.swift` is the UIApplication shell; there is no Xcode project —
`tools/tvos-run.sh` builds `UniversalUI.app` with `swiftc` on the Mac, signs it
ad hoc, installs it with `simctl` and reads its console.

| | result |
|---|---|
| emitted program (shared layer + reactify) | 8,577 lines of Swift, 0 todos, compiles |
| frozen frame vs the shared golden | **41 components match, largest difference 0.096 px** |
| live, Apple TV 4K simulator at 1080p | **60.0 fps** (26 one-second samples, median 60.0, minimum 60.0) |
| the cycle collector, every frame | 4–5 ms per frame over ~8,400 live instances |

So youi's AnimationExample now produces the same frame on **five** backends:
web, JavaFX, Android, Roku (pending re-verification) and tvOS — the last one
compiled from Scala to Swift by universal-ui's own compiler.

What the simulator taught: `ui.size.width @= 1920` reaches `uiSetWidth(root)`
before the height is read, so a runtime that resized the root view left it
1920x0 — the screen height read 0 and the transparent window showed the tvOS
home screen behind it (the root's geometry is the screen's, never the model's);
a `UILabel` with no explicit size must take its natural size, as a `div` or a
SceneGraph `Label` does, or it draws nothing.

**The collector's cost is the next thing to fix.** 4–5 ms of a 16.7 ms frame,
every frame, is a mark over the whole live graph. Options, in order: collect
every N frames or when allocations since the last pass exceed a threshold (the
example's steady state allocates little); a generational registry (mark only
objects created since the last pass plus their reachable set); or reactify's
Roku-era fast paths to allocate less per frame. Measured before it is chosen.

## IMMEDIATE NEXT STEP

**0. tvOS is real: keep it green.** `tools/check.sh --tvos` runs the frame and
frame-rate checks on the simulator via the Mac. Next: bring the collector's
per-frame cost down (above), then a physical Apple TV.

**1. Verify the Roku against the shared golden.** The Roku changes — rotation
sign, `uiTextWidth`, the shared line height, the icon built through `setFont` —
compile with 0 gaps and 0 brighterscript errors, but have not run on the device
(it was switched off). `ROKU_PASS=… tools/check.sh --device`, then check a
screenshot: the `Label` still places glyphs by its own vertical metrics inside
the shared box, and the frame rate must still hold 59.9.

**2. Commit the reactify performance work** (pass, `_references` fix, fire and
dependency-comparison changes) in the reactify repo, once confirmed there.

**3. Port more of youi's widget layer** onto the model in `shared/`, starting with
focus handling. Run `tools/check.sh --android --device` before each commit.

## Build / ops notes

- sbt: `~/.local/share/coursier/bin/sbt` (NOT on PATH).
- Scala 3.8.4. The Roku compiler is an in-build project (`compiler/`, with
  `compiler-api/` for `@native`). `<project>/emitRoku` compiles the project and
  runs `universal.compiler.Main` over its TASTy, compiling `reactive` (reactify)
  through as a library. Nothing is consumed from the sart repo.
- Layout: `shared/` (portable UI layer + app), `roku/` (SceneGraph backend +
  `runtime/UniversalRuntime.brs`), `web/` (Scala.js DOM backend), `compiler/` +
  `compiler-api/` (the Roku compiler), `reactive/` (reactify's source, compiled
  for emission), `reactive-test/` (the acceptance suite), `server/` (Spice).
- Build: `roku/emitRoku` → `roku/out-roku/`. Web is scala-cli, NOT sbt —
  `scala-cli --power package web -o web/out-js/main.js -f` (see build.sbt's
  web comment for why, and copy `web/index.html` into `web/out-js/` first).
  Add `--js-mode release` for the Closure-optimized bundle.
- Serve the web build: `sbt "server/run"` → http://localhost:8731/ (Spice +
  Undertow, reads `web/out-js` from disk so a rebuild needs no restart).
- Sweeping the FPS table: edit `FrameProbe`'s `Count`/`Animated`/`Bound`, then
  re-emit → zip `roku/out-roku` → sideload → read the debug console.
- `shared/app/Main.scala` picks the demo: `"animation"` (youi's example app
  running youi's AnimationExample), `"hello"` (the seam proof + FrameProbe),
  `"bench"` (ReactifyBench), `"easings"` (the numeric curve dump).
- **The Swift spike:** `tools/swift-spike.sh` — emits reactify's suite as Swift
  (`sbt reactiveTest/emitSwift` → `reactive-test/out-swift/App.swift`), rsyncs it
  with `swift/runtime/ScalaRuntime.swift` and `swift/spike/main.swift` to the Mac
  (`ssh mbp`, `~/universal-ui-spike/`), builds for macOS and the tvOS simulator,
  runs both against `reactive-test/expected-output.txt`, then the memory probe.
  `MAC`, `MAC_DIR`, `TVOS_DEVICE` override the defaults. `SwiftEmitter.scala` is
  GENERATED: edit `RokuEmitter.scala` or `tools/make-swift-emitter.py`, then rerun
  `python3 tools/make-swift-emitter.py` from the repo root.
- **tvOS:** `tools/tvos-run.sh` (live, 30 s, frame rate), `--freeze` (the frame vs the
  golden), `--show` (opens the Simulator window on the Mac and leaves the app
  running). `sbt tvos/emitSwift` → `tvos/out-swift/` (App.swift + the two runtimes,
  main.swift, Info.plist, assets). The Mac side lives in `~/universal-ui-spike/app`.
- **Run every check:** `tools/check.sh` (offline: JVM, Roku emit, web, desktop),
  `--android` (adds an emulator), `ROKU_PASS=<password> tools/check.sh --device`
  (adds the Roku), `--update` to rewrite the golden from the web's frame.
- Desktop: `sbt desktop/run` (a window; `--freeze=0.434 --screenshot=out.png`
  as arguments to capture a frame). Android: `sbt android/packageApk` →
  `android/out-apk/universal-ui.apk`; SDK from `ANDROID_HOME` (default
  `~/opt/android-sdk`), build-tools 36.0.0, platform android-35. Test on an
  emulator; the only physical Android device to use is the Google TV Streamer
  (`ANDROID_SERIAL=192.168.1.73:5555 tools/check.sh --android`). Google TV turns
  network ADB off after a reboot or update — if `adb connect` is refused, it has
  to be re-enabled on the TV (Settings → System → Developer options).
  Helpers (sideload, console capture, tree extract/compare, fps) are in
  `tools/checks.py`.
- **The web's live frame rate:** `tools/check.sh --live`. `webcheck/` (a tool
  project, not the app) uses RoboBrowser (`com.outr::robobrowser-cdp`
  2.4.7-SNAPSHOT from `~/.ivy2/local`, plus `spice-client-netty` — spice's HTTP
  client needs an implementation) to run a HEADFUL Chrome kiosk-fullscreen on
  its own Xvfb display, where rAF follows the display's 60 Hz, and relays the
  page's console; `tools/checks.py fps` reads the `[ui] fps=` lines as it does
  the Roku's. First run: 27 samples, median 60.0, minimum 59.9. (The kiosk
  window reports a 1919x1079 viewport — immaterial to a frame rate, which is
  why the frame check stays on headless Chrome.)
- Freezing a frame by hand: `--freeze=0.434` on the desktop, `am start … --es
  freeze 0.434` on Android, `?freeze=0.434` on the web build, or add
  `freeze.txt` containing `0.434` to the Roku channel (`python3 tools/checks.py
  roku-install roku/out-roku --add freeze.txt=0.434`). It steps a virtual 60 Hz
  clock, stops, and logs `tree …` lines (every component's position and size).
- Shared assets live in `assets/images/` and `assets/fonts/`; `emitRoku` copies
  them into the channel (`pkg:/images/`, `pkg:/fonts/`), and the web bundle serves
  the same files from `web/out-js/` (copy `assets/images` and `assets/fonts` next
  to `index.html`), so the two backends are compared on identical pixels.
- Roku screenshot (the only way to see the device from here):
  `curl --digest -u rokudev:<pw> -F mysubmit=Screenshot -F archive= -F passwd=
  http://192.168.1.112/plugin_inspect` then GET `http://192.168.1.112/pkgs/dev.jpg`.
- reactify acceptance: `sbt "reactiveTest/emitRoku"`, then
  `ROKU_PASS=<password> tools/roku-acceptance.sh` (diffs the device against
  `reactive-test/expected-output.txt`; regenerate that with
  `sbt "reactiveTest/run" | grep '^RX ' > reactive-test/expected-output.txt`).
- Static gap meter for any emitted channel:
  `python3 tools/emit-gaps.py reactive-test/out-roku/components`.
- When running brighterscript on an emitted channel, pass `--stagingDir`
  somewhere outside it — its default stages INTO the channel directory, and
  the next sideload zip picks that up.
- Roku device: **192.168.1.112**, dev password **`skcihM26`**. Sideload:
  `curl --digest -u rokudev:skcihM26 -F mysubmit=Install -F archive=@<zip>
  http://192.168.1.112/plugin_install`. Debug console: `telnet` / TCP
  `192.168.1.112:8085` (reactive probe `println`s land there).
- brighterscript (static check): `node
  /home/mhicks/projects/clients/nabo/app/roku/node_modules/brighterscript/dist/cli.js`
  (from the channel dir). It flags `: if` one-liners in the hand-written runtime
  as BS1054 — those are brighterscript strictness; the real Roku accepts them
  (the emitted channels run on the device).

## Open questions

- ~~Android/ATV: Views vs Skia~~ — **Views**, decided 2026-09-12.
- **Units on Android:** screen pixels today, so a 1920x1080 Android TV lays out
  exactly as the Roku does (TV-first). A phone, or a 4K panel without a 1080p
  override, will want density or design-resolution scaling — decide with the
  widget layer.
- **iOS/tvOS path:** Scala Native → UIKit vs Kotlin interop vs bytecode routes.
- **Naming** (.com/.org available; retire "youi"/"Sart").
- **Skia-on-Scala-Native** de-risk before committing desktop + mobile to it.
- **Naming the seam's read half:** `Prop.refresh()` exists and works, but
  nothing in the slice calls it — measure/layout is where it earns its keep.
- **#1 vs #2 for Roku long-term:** decision was **#2 (generalize RokuEmitter)** —
  write idiomatic Scala once, compiles everywhere — with hand-written-runtime
  kept only as a per-construct fallback. The lean-core slice (next step) is the
  pragmatic near-term; the #2 climb makes reactify-verbatim viable later.
