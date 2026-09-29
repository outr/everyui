# everyui — design & status

> **Handoff doc.** This project was spun out of work begun in the `sart` repo. Everything needed to
> continue lives here — this file is the source of truth.
>
> **Git:** everyui is a git repository (initialised 2026-09-12, branch
> `master`, no remote yet). Build output is ignored — regenerate Roku channels
> with `<project>/emitRoku` and the web bundle with scala-cli (see "Build / ops
> notes").
>
> **Sart is reference-only (decided 2026-09-12).** everyui owns its Roku
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
  everyui's is `compiler/`. It started from Sart's Roku emitter
  (`/home/mhicks/projects/open/sart`), which is now a reference only.

**Flutter is DROPPED as a target.** The legacy Flutter app covers Apple
transitionally.

Naming: **EveryUI** (everyui.com registered 2026-09-13).
`everyui` is the project name (EveryUI, everyui.com; `universal-ui` was the working title until 2026-09-13).

## The "native" principle (settled)

Native = **the platform's own app runtime + a native feel** (Flutter is the
*quality* benchmark, not the mechanism). A VM is fine when it IS the platform
runtime.

| Platform | Native runtime | Notes |
|---|---|---|
| Android / Android TV | JVM/ART lineage | Scala-JVM is the native path — settled |
| Web | JS engine | Scala.js |
| Roku | BrightScript | via everyui's Roku compiler |
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

**everyui's one invention** = an abstract **render node** (attribute
get/set, a style/property bag, children, measure, events) that
`Component`/`Feature` build their `Prop`s against, instead of `html.Element`.
DOM becomes *one* implementation of it (youi's existing `dom`/`ui` code);
Roku-SceneGraph / Skia / Android-Views are others.

## Reuse from youi (agreed: bring the code into everyui)

Decision: bring youi's proven code **into everyui** and evolve it here (new
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
sources. `shared/src/main/scala/everyui/` holds the whole portable layer:

| File | What it is |
|---|---|
| `ui/Prop.scala` | **the atomic seam** — youi's `Prop(getter, setter) extends reactify.Var` |
| `ui/Component.scala` | `x`/`y` Props built on a `RenderNode`, not an element |
| `ui/Label.scala` | `content` Prop |
| `app/HelloWorld.scala` | the app — names no DOM or SceneGraph type |
| `app/FrameProbe.scala` | the animation measurement |

Each backend supplies its own `everyui.render.RenderNode` with an identical
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
  backend genuinely IS the DOM. Published by sbt (sbt-scalajs 1.22.0 runs on sbt 2)
  as `everyui-web`; the example bundle is still built with **scala-cli**.

**Verified on the physical Roku** (Hisense 4Series-40 / K213X, 720p UI,
192.168.1.112): device screenshot shows the label rendered, and the debug
console reads back through the seam's getter half —

```
[ui] mounted: Hello, World!         | node: Hello, World!
[ui] updated: Hello, EveryUI!  | node: Hello, EveryUI!
[ui] x: 100 | node x: 100
```

The `node:` half is the real proof: it asks the SceneGraph node itself, so the
Prop's setter demonstrably reached the backend, not just the reactive cell.

**Verified in Chrome** (headless, same shared sources): `#app` contains
`<div style="left: 100px; ...">Hello, EveryUI!</div>`.

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

The static gap meter (`Checks gaps <channel>/components`, once `tools/emit-gaps.py`): `RtTodo`
markers, dangling symbols, duplicate definitions, unknown method calls. For
reactify alone and for the suite, every count is **0**.

### What the compiler needed (now in `compiler/`)

| Feature | What it does | reactify construct that needed it |
|---|---|---|
| Object model | vtable per concrete class from its linearization (most-derived wins), one table shared by the class's instances (`_vt`); `C_init` chains super → mixins (guarded for diamonds, the guards dropped once it is made) → own fields and constructor statements; calls on a model member dispatch `recv._vt.m(recv, …)` | `Var extends Val with Mutable`; groups overriding `set`/`get`; `GroupReactions` overriding `+=` |
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
- everyui's AnimationExample: brighterscript diagnostics unchanged; on the
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
  runtime's `Text()` (BS1104; pre-existing in everyui, harmless unless the
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
fix below). The compiler half is in everyui's `compiler/`; it was made in
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

## Sart extraction — everyui owns its Roku toolchain (2026-09-12)

Decision: **Sart is reference-only.** Nothing in the build, the sources or a
generated channel depends on it or carries its name.

### What moved, and where

| Was (sart repo / plugin) | Now (everyui) |
|---|---|
| `sbt-sart` plugin, compiler from `~/.ivy2/local` | `compiler/` — an in-build project on `scala3-tasty-inspector`; `everyui.compiler.Main` (Roku only) |
| `sart.dart.native` | `compiler-api/` — `everyui.compiler.api.native` |
| `sart.stdlib.math` for `Platform.sqrt/pow/sin/cos` | `uiSqrt` / `uiPow` / `uiSin` / `uiCos` facades in `UniversalRuntime.brs` |
| `sartEmitRoku` task | `emitRoku` (reactify compiled through via `--library`) |
| `SartScene.xml/.brs`, `SartRuntime.brs` | `AppScene.xml/.brs`, `ScalaRuntime.brs` |
| runtime names `Sart*`, `_sart*`, `__SART_*`, `ga.sartRoot` | `Rt*`, `_rt*`, `__RT_*`, `ga.appRoot` |
| `tools/sart-gaps.py` | the gap meter, now `everyui.checks.Checks gaps` |
| `example/` (the first Sart probe) | retired; a copy is in the session scratchpad only |

The GitHub Packages resolver (there for Sart's siblings) is gone; `server/`
still resolves Spice.

### What was removed

Sart's **Flutter-on-SceneGraph layer**, which everyui never used (its
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

`shared/src/main/scala/everyui/example/ui/AnimationExample.scala` is youi's
`example/.../ui/AnimationExample.scala` with only its package and imports
changed (`youi.*` → `everyui.*`, `rapid.Task` → `everyui.async.Task`,
`spice.net._` → `everyui.net._`). The class body is youi's, line for line:
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
  shows the doubled offset: centre at 75 + 502.5 + 75). everyui uses the
  container's coordinates — `container.size.middle`,
  `bottom to container.size.height`, `top to 0.0`. youi is the reference, not
  always correct: its bugs are fixed here and noted.

### What made that possible — youi's model, ported onto `RenderNode`

| Package (`shared/.../everyui/`) | From youi |
|---|---|
| `component/` — `Component`, `Container`, `TextView`, `ImageView` | `youi.component` over a `RenderNode` instead of an `html.Element` |
| `component/feature/` — measured, size, position (`left/center/right`, `top/middle/bottom` as reactify `Dep`s), font, container children | `youi.component.feature` |
| `component/types/` — `Prop` (youi's own), `SizeProperty`, `PositionType`, `WhiteSpace`, `FontWeight` | `youi.component.types` |
| `task/` — `Animation` (youi's `task.Task`), `AnimateIn`, `PartialAnimate`, `Sequential`, `Parallel`, `Repeat`, `Sleep`, `Action`, `TaskInstance`, `TaskSupport`, the `to`/`forever`/`sequential` DSL | `youi.task`, line for line |
| `easing/` | youi's `Easing` files, verbatim |
| `app/screen/` — `Screen`, `UIScreen`, `ScreenManager` (reduced to running one screen) | `youi.app.screen` |
| `example/` — `ExampleApp`, `UIExampleScreen`, `Header` | youi's example app |
| `ui`, `Color`, `scribe`, `async.Task`, `net`, `image.Image`, `font.GoogleFont` | the parts of youi / rapid / spice the example uses |

youi's `rapid.Task` becomes `everyui.async.Task` (callback-driven) and youi's
`task.Task` becomes `Animation`: the Roku compiler names classes by their simple
name, so two `Task`s would collide. The example never names the latter.

**Backends** gained what the model needs, with identical signatures:
containers, width/height, colour, background, font (family/weight/size), measured
size, image source, removal, a Material Icons node, async image and font loading,
`resolvePath`, `onResize`. **Assets:** `assets/fonts/` (Open Sans 400/600 and
Material Icons as TTF, from the same Google Fonts youi loads), the header logo (since 2026-09-13 EveryUI's, `logo.png`), and a
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
  only fetches Open Sans 400); everyui bundles the real SemiBold, ~9px wider.
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
| the gap meter (`Checks gaps`): TOTAL GAPS 0 on reactive, reactive-test, roku | ✓ | ✓ |
| brighterscript: 0 errors on all three (BS1054/BS1104 excluded) | ✓ | ✓ |
| web bundle builds (scala-cli) | ✓ | ✓ |
| the shared font table (`everyui.font.Faces`) matches `assets/fonts` | ✓ | ✓ |
| web: AnimationExample frozen at 0.434s in headless Chrome over `file://`, every component vs `tools/golden/animation-tree.txt` (±1px) | ✓ | ✓ |
| desktop: the same frame in JavaFX under Xvfb, vs the same golden | ✓ | ✓ |
| web (`--live`): 60s live in a headful Chrome on a virtual 1920x1080 display (RoboBrowser), median ≥ 59.5 fps and minimum ≥ 58.5 | | |
| Android (`--android`): the APK builds; the same frame on an emulator, vs the same golden; live frame rate and motion | | |
| tvOS (`--tvos`): the same frame on the Apple TV simulator (built on the Mac by tools/tvos-run.sh), and 60 s live | | |
| Roku: `tools/roku-acceptance.sh` | | ✓ |
| Roku: the same frozen frame vs the same golden | | ✓ |
| Roku: 60s live, median ≥ 59.5 fps and minimum ≥ 58.5, and two screenshots a second apart differ | | ✓ |

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
  at a multiple of the size and scales back, reusing one probe `Label`. On the
  device the multiple mattered: at 4× a 720p panel still came out 1.3% narrow
  (glyph advances hint to the panel's pixels), at 16× the SemiBold header row
  was 1.3px off, at **32×** every row is within 0.92px of the golden
  (verified 2026-09-13: 41 components match).
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
derived from the Roku source at build time (every template
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
`tools/tvos-run.sh` builds `EveryUI.app` with `swiftc` on the Mac, signs it
ad hoc, installs it with `simctl` and reads its console.

| | result |
|---|---|
| emitted program (shared layer + reactify) | 8,577 lines of Swift, 0 todos, compiles |
| frozen frame vs the shared golden | **41 components match, largest difference 0.096 px** |
| live, Apple TV 4K simulator at 1080p | **60.0 fps** (26 one-second samples, median 60.0, minimum 60.0) |
| the cycle collector, every frame | 4–5 ms per frame over ~8,400 live instances |

So youi's AnimationExample now produces the same frame on **five** backends:
web, JavaFX, Android, Roku (pending re-verification) and tvOS — the last one
compiled from Scala to Swift by everyui's own compiler.

What the simulator taught: `ui.size.width @= 1920` reaches `uiSetWidth(root)`
before the height is read, so a runtime that resized the root view left it
1920x0 — the screen height read 0 and the transparent window showed the tvOS
home screen behind it (the root's geometry is the screen's, never the model's);
a `UILabel` with no explicit size must take its natural size, as a `div` or a
SceneGraph `Label` does, or it draws nothing.

**iOS, the same day (2026-09-13).** The same app, built against the iPhone
simulator SDK (`tools/tvos-run.sh --ios`; the device family and a
`UILaunchScreen` key in the plist are the only differences): the frozen frame
laid out at a 1920x1080 viewport matches the shared golden (41 components,
0.096 px) and it runs at 60.0 fps on the iPhone 17 Pro simulator at the phone's
own 402x874 — where the TV layout simply overflows the screen (the header runs
under the status bar), as expected until there is a phone layout.

### Deployment size, AnimationExample, as it stands

| platform | what ships | size |
|---|---|---|
| web (dev) | `main.js` | 1.88 MB raw, 230 KB gzip, 155 KB brotli |
| web (release, Closure) | `main.js` | 375 KB raw, 97 KB gzip, **77 KB brotli** |
| Roku | the channel zip (BrightScript + assets) | **331 KB** (App.brs 250 KB, runtimes 46 KB, before zip) |
| Android | `everyui.apk` (debug-signed, R8) | **423 KB** (classes.dex 260 KB; was 2.25 MB / 5.98 MB before R8) |
| tvOS | `EveryUI.app` | **1.55 MB** (binary 1.06 MB, 0.92 MB stripped; Swift runtime is the OS's) |
| iOS | `EveryUI.app` | **1.55 MB** (the same binary, iOS SDK) |
| desktop (JavaFX) | `everyui-desktop`, a GraalVM native image | **28.6 MB**, one self-contained binary (JavaFX's native libraries ride inside; links only libc and libz); was 14.6 MB of jars plus a JRE |
| assets, every platform | 3 images + 3 fonts | 501 KB (Material Icons alone 357 KB) |

The web figure is larger than the 97 KB release measured for the first slice
because the app now carries youi's whole component model, task DSL and
reactify.

### The desktop as a native binary (2026-09-13)

`tools/desktop-native.sh` builds the JavaFX backend with GraalVM `native-image`
into `desktop/out/native/everyui-desktop` and holds it to the same checks:
the frozen frame matches the golden (0.096 px) and it runs at 62 fps under Xvfb
— `tools/check.sh --native`. What it took:

- **No AWT.** The screenshot used `javax.imageio`; native-image on Linux does
  not take `java.desktop` gracefully, so the desktop writes its PNG by hand
  (`Png`, 40 lines over `java.util.zip`).
- **JavaFX's reflection, JNI and resources** come from GraalVM's tracing agent
  (`--trace`: a frozen run and a short live run under Xvfb), kept in
  `desktop/native-config/reachability-metadata.json` (25 KB). Rerun it when the
  app touches more of JavaFX.
- **`-Os`**: 23.5 MB against `-O2`'s 36.8, at the same frame rate (JavaFX 21);
  28.6 MB on JavaFX 26.
- **Self-contained, verified.** `ldd` lists libc and libz only; run with a fresh
  `HOME`, JavaFX reports every native library "loaded from resource". Build time
  19 s.

### Toolchain — the latest of everything (2026-09-13)

| | version |
|---|---|
| Scala | **3.10.0-RC3** (decided 2026-09-13: the RC now, the final when it lands; RC3 on 2026-09-28) — 3.9 needed no change; 3.10 needed two emitter cases, below |
| sbt | **2.0.9** (2.1 is at M3) |
| Scala.js | **1.22.0** (3.9 emits IR 1.22; the linker had to follow): sbt-scalajs for the published `everyui-web` and `everyui-network`, scala-cli **1.17.0** for the example bundle |
| JavaFX | **26.0.2** |
| GraalVM | **25.3.4** (Oracle GraalVM, JDK 25.0.4.1; sdkman default) |
| Android | build-tools **37.0.0** (R8 8.10), platform **android-37.0** (Android 17), target SDK 37, min 30 |
| RoboBrowser, spice, reactify | local snapshots / source, as before |

**What 3.10 changed for the compiler.** Two of its breaking changes alter the
TASTy the emitter reads: the `Rich*` wrappers became extension methods in the
primitive companions (`Int.until(0)(n)`, `Double.compare(a)(b)`, `Double.abs(a)`
instead of `intWrapper(0).until(n)`), and `k -> v` became a call of a top-level
`->` instead of `ArrowAssoc(k).->(v)`. `RokuEmitter` (and so `SwiftEmitter`,
regenerated) recognises both shapes — `isPrimitiveExtension`, restricted to the
wrapper methods it translates, since the same companions also hold the
implicit widening conversions the generic path already handles — so it reads
3.9's and 3.10's TASTy alike. Everything else in 3.10's notes (strict-equality
pattern matching by default, `with` as a type operator, `any2stringadd` gone)
passed without a change in our code or reactify.

Re-verified on 3.10.0-RC2: JVM golden, Roku emit (0 gaps, brighterscript
clean), web frame, desktop frame (JVM and native), Android frame on the
emulator. tvOS/iOS re-verified the same day (spike, frames), and the Roku (acceptance,
frame at 32×, 59.9 fps).

### R8 on the Android build (2026-09-13)

`android/packageApk` now runs R8 (it ships inside build-tools' `d8.jar`) in
place of D8: whole-program shrinking from the one class the manifest names,
then dexing. 5.98 MB of dex became 260 KB; the APK 2.25 MB → 423 KB; the frame
still matches the golden and the Google TV Streamer still holds 59.9–60.0 fps.
Two Scala-specific traps, both now rules in `android/proguard-rules.pro`:

- **Lazy vals.** Scala 3 reads a `lazy val`'s cell through a VarHandle the
  class initialiser finds *by name*. For a stdlib companion that
  `scala.package$`'s initialiser merely touches (`BigInt$`, `BigDecimal$`),
  no instance is live, so R8 removed the cell but kept the lookup — a
  `NoSuchFieldException` in `<clinit>`. `-keepclassmembers` needs a live
  instance and does not apply; an unconditional `-keep` pinned every class with
  a lazy val (233 KB → 1,045 KB). The precise rule is conditional: wherever a
  `$lzyHandle` survives, keep its `$lzy` cell, by name.
- **Trait super calls.** R8 merges a trait into its only implementor. A Scala
  `super.m()` reaches the parent trait through a super accessor that D8 lowers
  to `invoke-super` on that parent; after the merge it targets the child itself
  and recurses until the stack overflows (`UIExampleScreen.init →
  UIScreen.init`). `-keepnames interface **` pins every surviving interface
  against merging: +61 KB, no recursion.

The build writes R8's `seeds.txt`, `usage.txt` and `mapping.txt` next to the
dex (`target/out/…/everyui-android/apk/`), which is how both were found.

**The collector's cost, brought down (2026-09-13).** The first collector marked
through a `Set` of identifiers, swept a registry of weak references, and made a
`WeakObj` for every allocation: 4–5 ms of every 16.7 ms frame. Now every
instance sits on an intrusive doubly-linked list (unowned links, unlinked in
`deinit`, so acyclic garbage still goes the moment reference counting drops it)
and carries a mark epoch; a pass is one traversal stamping the epoch and one
walk of the list clearing what it did not stamp — no allocation, hashing or
weak load per object. And the frame driver runs it only when the allocations
since the last pass are worth a walk of the live graph (a quarter of it, at
least 2,000).

| | before | after |
|---|---|---|
| one pass over ~8,400 live instances | 4–5 ms | 2.6–3.4 ms |
| passes per second, live | 60 | 1–8 (the example allocates 20–300 instances a frame) |
| cost per frame, amortised | 4–5 ms | **0.05–0.35 ms** |
| the spike's probe: 200 suite runs, a pass each | 555 ms | 349 ms (a single pass over 77,200 garbage: 44 ms, was 68) |

Still 60.0 fps, still 0 growth. Remaining lever if a bigger app needs it: a
generational pass (young objects plus a remembered set) so a pass no longer
walks the whole live graph.

## EveryUI: the rename, the icons, and no Python (2026-09-13)

**The rename.** `universal.*` → `everyui.*` in every source root; sbt modules
`everyui-*`; the native binary `everyui-desktop`; app labels, the Android
package and Apple bundle `com.outr.everyui`, the Roku channel title, log tags,
scripts, docs. The emitted goldens did not change (package names never reach
the output). `youi.*` stays what it is in AnimationExample's header: that is
youi's example reproduced as the reference, not our branding.

**Icons.** `sbt makeIcons` (`project/Icons.scala`, rendering the SVGs in
`work/logo/svg` with `rsvg-convert` and composing with AWT) writes every
platform's icon from the logo: the mark alone at 256 px for the web favicon and
the desktop window icon (`assets/images/everyui-mark.png`); Android launcher
icons at five densities and the TV banner (`android/res`, compiled by aapt2
into the APK; `android:icon`/`android:banner` in the manifest); Roku channel
icons and splash screens at fhd/hd/sd (`roku/images`, declared in the manifest
by `emitRoku`); the Apple asset catalog (`swift/app/Assets.xcassets`: the iOS
1024 icon, the tvOS App Icon at 400x240 @1x/@2x, App Store 1280x768, Top Shelf
1920x720 and 2320x720), compiled into the bundle by `actool` in
`tools/tvos-run.sh` and merged into the plist. Verified on the desktop (window
icon), the web (favicon) and the Android emulator (launcher icon and banner
resolve); the Apple and Roku icons are wired but unverified until the Mac and
the Roku are back.

**No Python.** Tooling is Scala now:
- `checks/` — `everyui.checks.Checks`, one CLI behind `tools/check.sh` and the
  device scripts: frame extraction and comparison, the font table, frame-rate
  floors, the Roku gap meter, sideloading, console capture, Android waits, the
  suite-line extraction the acceptance script needs. Run from the classpath
  `sbt checks/checksClasspath` stages.
- `sbt makeIcons` — the icons, above.
- `SwiftEmitter.scala` is no longer checked in: `project/SwiftEmitterDerivation.scala`
  derives it from `RokuEmitter.scala` at build time (a source generator on the
  compiler project) by the replacements in `compiler/swift-derivation.txt`, a
  plain record file. Each replacement must match exactly the number of times the
  table says, so a change to the Roku emitter that moves a template fails the
  build. The first generated file was byte-identical to the last checked-in one.
The shell scripts remain as thin drivers (Chrome, adb, ssh, simctl); moving the
orchestration itself into sbt tasks is the next tooling step if wanted.

## The animation must visibly move — the motion check (2026-09-13)

A frame-rate line proves the tick loop runs, not that pixels change: a backend
whose `setX` did nothing would still report 60 fps. And a device left on the
`--freeze` build after a check sits on one frame by design — which is exactly
what a screen showed once (the Roku after a manual frozen sideload). So every
live check now also takes two screenshots a second apart and requires at least
1% of pixels to differ (`Checks motion`, a channel off by >12/255 counts), and
the device scripts leave the live build behind. Measured: Roku 7.5%, tvOS 6.4%,
iOS 22% (the phone shows a smaller, faster-moving region), Android 4.3%,
desktop 5.4%, web 5.4% — at 59.9–62 fps everywhere.

Screenshots per platform: the Roku's developer page (`plugin_inspect`),
`simctl io screenshot`, `adb screencap`, ImageMagick's `import` inside the Xvfb
session, RoboBrowser (a second capture a second before the last).


## Input: events and focus, one model on every backend (2026-09-13)

youi's `Events` over the render seam, and a focus model the platforms do not share, kept in the shared layer:

- `component.event.pointer.{down, up, move, enter, leave}` and `event.click` are reactify `Channel`s of a
  `PointerEvent(x, y)` in the render root's coordinates; `event.pointer.over` is a `Val`. Each channel listens on the
  node only once something asks for it. `event.key.{down, up}` carry a `KeyEvent(key)` while the component has the
  focus.
- Keys are named as the web names them, everywhere: `Enter`, `Escape`, `ArrowUp`…, `MediaPlayPause`, or the
  character typed. A remote's OK is `Enter`, its back is `Escape` (and is also left to the platform, so Back still
  leaves a Roku channel, a tvOS app, an Android activity).
- `Component.focusable`, `focused` and `focus()`; `everyui.focus.Focus` holds the focused component and routes every
  key to it: `Enter` clicks it, an arrow moves the focus to the nearest focusable component that way (the distance
  along the arrow plus twice the drift off it). A click on a focusable component focuses it. That is the same spatial
  navigation for a Roku remote, a Siri Remote, an Android TV D-pad, a keyboard and a TV browser — no platform focus
  engine is used (tvOS views stay out of UIKit's, so its presses arrive as keys).
- The seam grew two members: `RenderNode.onPointer(kind, f)` and `Platform.onKey(f)`. Web: DOM `pointer*`/`click`
  and `keydown`/`keyup` on the document. Desktop: JavaFX mouse handlers (`pickOnBounds`, so transparent panes hit)
  and key filters on the scene. Android: one touch/hover listener per node (an up inside the view is a click), the
  activity's `dispatchKeyEvent`. Roku: no pointer; `AppScene.onKeyEvent` → the generated runtime's `RtKeyEvent`
  → the hook `uiOnKey` set. tvOS: no pointer; `pressesBegan/Ended` → `uiKeyEvent` → the hook.
- `Component.parent` (kept by `ContainerFeature`) and `absoluteLeft/Top`, for the focus geometry.
- The key hook answers whether the focus layer took the key, so the platform leaves it be (the browser does not
  scroll on an arrow that moved the focus). With nothing focused, the first arrow focuses the first focusable
  component; a screen that was swapped out keeps its components, so only components under `ui` count.

`demo=input` (`everyui.app.InputDemo`): three focusable boxes; with `probe=1` it drives `Focus` itself and prints
`[ui] input ok focus=a focus=b click=b focus=a`. tools/check.sh runs the probe on the web, the desktop, Android and
tvOS, then real events: DOM keyboard and pointer events through RoboBrowser (`webcheck/WebInput`), the D-pad and a
tap through adb — both must reach the same channels (`focus=c click=c`, plus `enter c`/`leave c` where there is a
pointer). Verified 2026-09-13 on the web, the desktop and the Android emulator; the Roku (off) and the Apple TV
simulator (the Mac dropped off the network) compile clean and are not yet run.

## Screens, scrolling, and the rest of what a dashboard needs (2026-09-13)

What the DataWrangler dashboard's port asked of the shared layer, each on every backend:

- **Screens.** `ScreenManager` holds several screens and shows one at a time: each is init'd and loaded once, on
  its first showing, and activated after the last is deactivated. Showing a screen pushes its path; the browser's
  back and forward show the screen claiming the new path (`PathActivation.matches`, so `/run/<id>` is one screen);
  `run(first, others*)` starts on the screen claiming the page's path. The seam: `Platform.path`, `pushPath`,
  `onPath` (the DOM's history; nothing elsewhere).
- **Scrolling.** `ScrollContainer`: a viewport over `content`. The web scrolls the node itself (`overflow: auto`,
  so the browser's scrollbar, wheel, touch and keys); every other backend clips the node (`RenderNode.setClip`)
  and the shared container shifts the content on the wheel (`event.pointer.wheel`), a drag, or the arrows and page
  keys while focused. `Platform.nativeScroll` says which.
- **Text.** `Icon`, a text view over a Material Icons glyph. `Component.tooltip` (the DOM's `title`, a JavaFX
  `Tooltip`, Android's tooltip text; nothing on a TV). `TextView.whiteSpace` defaults to `NoWrap`, which is what
  every node starts as; `Normal` with an explicit width wraps on the web. Roboto Mono joins the bundled faces
  (`Faces.RobotoMono`, registered by every runtime) for logs. A text view applies the font defaults to its node on
  construction, so a view that never sets a font still lays out by the shared line box.
- **Layouts.** `HorizontalLayout(spacing, middle = true)` centres a row's children on its tallest.
- **Colour.** `Color.withAlpha`; a translucent background reaches the node as `#rrggbbaa` (a dialog's dim sheet).
- **Pointer.** `PointerEvent.consume()` keeps a click from the components underneath, so a button's click is not
  also its card's.
- **Platform.** `openUrl` (a new tab, the desktop browser, an Android intent), `copyText` (the clipboard).

The dashboard itself (datawrangler `ui/`) is the reference use: five screens, dialogs on a `Modal` sheet, tables,
toggles, a run history that loads when opened, a log that follows, and every daemon action, all over the live
channel. tools/check.sh is unchanged and green.

## The widget layer, and the platform's identity (2026-09-14)

The DataWrangler dashboard's port left a lot of widget code in the app that belongs in the framework. It moved
into `shared/` — every backend compiles it, the Roku channel emits it with no gaps — and the dashboard went from
1,518 lines to about 900, all of them about the dashboard.

- **Icons.** `everyui.font.MaterialIcon`: every glyph of the bundled face, by name, generated from the font's own
  codepoint table (`tools/MaterialIcons.scala`; `assets/fonts/MaterialIcons-Regular.codepoints`). The emitter's
  reachability pass keeps only the icons an app names. `MaterialIconView(icon, size)` draws one, as youi has it.
- **Theme.** `everyui.theme.Theme`: the palette the widgets default to, as `Var`s an app sets once.
- **Widgets** in `everyui.component`, each a few lines over `Container` and the seam: `TextView(text, size,
  colour)`, `TextView.bound(...)`, `TextView.wrapped(text, width)`; `Button`, `IconButton`, `Choice`, `Chip`,
  `Checkbox`, `ProgressBar`, `Bars` (a trend or a sparkline), `Table[T]` with `Column`s, and `Dialog` on the
  `Modal` sheet (youi's Modal: one at a time, a click on the sheet or Escape closes it; the body scrolls when
  taller than the screen leaves room for).
- **Screens.** `ScreenManager.host` is where the screens go (`ui`, or a region under an app bar);
  `ScrollScreen` gives a screen a stacked, scrolling `body` and a reactive `heading`; `Tab` is a screen's name in
  an app bar. `everyui.util.Clock` ticks once a second from the frame loop, so a bound duration counts;
  `everyui.util.Format` has the time, span and count formats.
- **The platform.** `Platform.kind` is a `PlatformKind` (Web, Desktop, Android, Roku, TvOS), and what the platform
  can do is asked separately, since a TV browser is Web with a remote and a tablet is Android with touch:
  `Platform.pointerSupport()`, `touchSupport()`, `remoteNavigation()`, `keyboardSupport()`. The web reads the
  user agent, the touch points and the pointer media query; Android asks the UI mode, the touchscreen feature and
  the keyboard configuration; a Roku and an Apple TV are a remote and nothing else.
- A frozen frame (tools/check.sh) now waits for the faces to load before it measures, which the live path
  already did in effect; the check was racing the icon font.

DataWrangler's `ui/` is now a Scala.js module of its own sbt build (sbt 1.12 has sbt-scalajs; EveryUI's sbt 2
build does not), taking EveryUI's and reactify's sources as source directories until they are published.
`sbt ui/webBundle` writes `ui/out`, which `docker/stage.sh` ships.

### The second pass: what the layouts carry (2026-09-14)

youi's layouts were already here; what was missing around them was what made the dashboard verbose:

- A container given a layout sizes itself: its height always, its width when the layout `fitsWidth()` (a row,
  a column; not a flow or a grid, which wrap to the width they are given). `padding` keeps the children in from
  the edges and counts toward the content extents. `Row(spacing, middle, padding)(children*)` and
  `Column(spacing, padding)(children*)` build the common cases in one call; `GridLayout` joins the three.
- `children.bind(values)(render)`: the children follow a reactive list of *data*, rendered again when what it
  reads changes. Data, not components: a component built inside the tracked expression makes its own
  measurements dependencies, and the list rebuilds itself forever (the first port did exactly that, and the
  page's script thread never came back).
- `TextView.optional(value)`: a line that is out of the way, with no height, while there is nothing to say.
- A screen declares a `pattern` (`/run/:id`) and reads `parameter("id")`; the manager's path matching and the
  query fall out of it.
- `demo=widgets` (`everyui.app.WidgetsDemo`) builds every widget on one page and logs its size — the desktop's
  stack dump is how a stuck layout is found.
- The emitter prunes a module's unreferenced lazy vals (the icon table is 2,231 of them; the Roku channel
  carries the three the demo names). BrightScript names are case-insensitive, so the icon generator drops a
  name that repeats in a different case.
- One class per file: the layouts, the button family, the events, the modal, the bar, the table column, the
  easings, the font face, the derived position each have their own.

The dashboard is at 786 lines, with no size bindings, no path parsing, and two clear-and-refill blocks left.
## NaboTV's sign-in screens, and what they needed (2026-09-14)

NaboTV is EveryUI's primary target: it has to ship everywhere. Its port starts with the sign-in screens, built and
driven on the desktop first. The app lives in the NaboTV repo (`app/everyui/src`, one source tree for every platform,
and `app/everyui/desktop` for the desktop launcher; `sbt appDesktop/run`). What the screens needed from the framework:

- **Text fields.** `TextInput(label, secure, width)`: a labelled field whose `value` binds both ways and whose Enter
  fires `submitted`. It is the platform's own field — an `<input>`, a JavaFX `TextField`/`PasswordField`, an Android
  `EditText` — behind `InputView` and a new node, `Nodes.input(secure)`, so typing, the caret, selection, the
  clipboard, autofill and an input method are the platform's, as the web backend's argument for the DOM already
  said. The shared focus drives the native one (`focusInput`), and a click on the native one takes the shared focus
  (`onInputFocus`). Where there is no field of its own (`Platform.nativeInput()` false: a Roku, an Apple TV) the
  field shows its value and asks the platform keyboard for a new one (`Platform.editText`); **those two runtimes are
  still stand-ins** that return nothing.
- **Where the keyboard is on the screen, Enter edits.** With a keyboard at hand (`Platform.keyboardSupport()`) the
  focus on a field is the caret. Without one (a TV remote, a phone) the focus only lights the field; Enter or a tap
  starts `editing`, which brings the on-screen keyboard up, and the keyboard's Done or Back ends it (`onInputDone`),
  as the Flutter app's TV fields do. On Android the `EditText` (`FieldView`) takes no focus and no touches until then:
  a TV hands the first focusable view the focus as a window opens, and Google TV brings the keyboard up with it.
  Verified on the Google TV Streamer with its remote. `--remote=true` (or `--keyboard=false`) on the desktop launch
  tries the same behaviour in a window.
- **Android lays out in density units.** The root is scaled by the display's density, so a 1080p TV is a 960x540
  layout, as its own apps have it, rather than 1920x1080 at half the size. A launch that pins `viewport` stays 1:1,
  which the frame checks do. Android also loads images from the network now, and starts at `--es path`.
- **The focus scrolls into view.** A `ScrollContainer` scrolls the least that shows a component the focus lands on
  under its content: a remote has no other way to reach what is below the fold.
- **Shared packaging and launchers.** `AndroidApk` (in the sbt-everyui plugin) is the APK build (R8, aapt2, zipalign,
  apksigner) that `android/packageApk` and an app's build both call. `EveryUIActivity` is the activity an app extends, as
  `Desktop.launch` is the desktop's launcher, and the example's own `MainActivity` moved to `app/android`.
- **Keys a component keeps.** `Component.keepsKey(key)`: a focused text field keeps Enter and the keys along its
  line, so the arrows move the caret rather than the focus. Tab now moves the focus on in tree order.
- **Borders.** `borderColor` and `borderWidth`, drawn inside the box so a border never changes a size: an inset
  shadow on the web, a Region border on the desktop, a drawable stroke on Android. A Roku Rectangle has none.
- **Button styles.** `ButtonStyle` (Filled, Tonal, Outlined, Text) replaces the `primary` flag; a button takes an
  icon, a text size its padding follows, `enabled` (muted, still in place) and `minWidth` (content centred).
- **Settings.** `Platform.setting(name)` and `saveSetting(name, value)`: localStorage, the Java preferences store,
  SharedPreferences. The Roku registry and tvOS UserDefaults are not wired yet, so a session does not survive a
  relaunch there.
- **The UI thread.** `Platform.later(f)`: the desktop's and Android's HTTP clients answer on their own threads, and
  a reply has to reach the components on the UI one. (DataWrangler never saw this: the web has one thread.)
- **App faces.** `Faces.register(face)` adds an app's font (NaboTV's Roboto); `Theme.family` makes it the default
  for text made after it is set. The web adds a registered face through the FontFace API and waits for it. The Roku
  and tvOS runtimes still pick faces by family in their own tables, so registered faces are not used there yet.
- **The rest.** `Platform.deviceName()`; `Image` accepts a URL (the desktop loads it in the background); the desktop
  wraps text (`whiteSpace = Normal`), which it silently did not; `--path=` starts the desktop at a screen as a page's
  address would; `Desktop.launch(args, title, icon)(run)` is the launcher an app's `main` calls, and the example's
  own `DesktopApp` moved to `app/desktop`.

Two bugs the screens found: `MaterialIconView` let its text feature reset the node to the default family at 16px, so
the desktop drew icons in the system font and the web drew every icon at 16px whatever size was asked for; and
`ProgressBar` sized its fill from its constructor width, so a bar whose width was bound later never filled. The Roku
emitter cannot capture a lambda's parameter inside an anonymous class built in that lambda
(`glyph.map(g => new MaterialIconView(g) { ... })` emits a free `g`); build the instance, then configure it.

## Themes and languages, as core features (2026-09-14)

Both went in before any more of NaboTV was ported, because retrofitting either into finished screens means touching
every string and every colour again.

**Themes.** `Theme.palette` was already a reactive `Var[Palette]`, so a theme change re-colours every binding that
reads `Theme.x()`; the work was making sure nothing reads one only once. A default like `Var(Theme.accent())` is
already live (reactify tracks it as a binding until something assigns the Var); the one real capture was
`IconButton`'s colour parameter, now a live `tint` with an optional override. `Palette.extras` carries an app's own
named colours (NaboTV's gold, amber, cyanDim, backgroundTop) and `Theme.extra(name, fallback)` reads them. NaboTV maps
the server's theme JSON (the Flutter palette's names) onto the palette (`NaboTheme.palette`), keeps the choice on the
device, and puts it on before anything is built; the signed-in placeholder offers `/api/themes` and switches live.

**Languages.** `everyui.i18n`:

- `Strings`: the current language's messages. A read (`Strings("signIn")`) is reactive, so text bound to it changes
  language when `Strings.use(language)` loads another; a key the language lacks falls back to the template language.
  Translations are ARB files among the app's assets (`/l10n/app_<code>.arb` by default), loaded with
  `Platform.readText` before the app builds (`Strings.start(template, current)` in the manager's `load`).
- `Arb.parse` and `Icu.format`: ARB messages (metadata skipped, escapes resolved) and the ICU subset app translations
  use — placeholders, plurals with `=n`/`one`/`other` and `#`, selects, nested. Written with substrings and comparisons
  only, so the Roku emitter carries them; `demo=widgets` logs `i18n ok` after checking both.
- `tools/Arb.scala` generates an app's typed accessors from its template ARB: one `def` per message, an `Int` parameter
  where the ARB declares one. NaboTV's `nabo.l10n.Tr` has 1,811.
- `Direction.rtl`: a right-to-left language flips it. The platform takes it at its root (`Platform.setDirection`: the
  page's `dir`, the Android root's layout direction, each JavaFX field's orientation), rows, flows and grids mirror, and
  every container is laid out again (`Container.relayout`). `TextView.align` is logical (`TextAlign`: Start, Center,
  End) and each backend resolves it against the direction.
- `Language(code, native, english, rtl)`, and `Platform.language()` for the device's.

Text that should follow a language change has to be bound: `TextView.bound(Tr.x)`, `button.label := Tr.x`. A value
passed where a plain `String` is taken is read once, when the component is made, which the Streamer showed on the
pairing screen's links before they were bound.

Verified on the desktop and the Google TV Streamer: the language screen first on a new device, German, Italian,
Traditional and Simplified Chinese (the platform fonts fill in the glyphs Roboto lacks), and Arabic mirrored (the pairing
row, right-aligned labels, a field's caret starting at the right, which needed `android:supportsRtl` in the manifest).
Themes switch live and survive a relaunch.

Not yet: the Roku and tvOS runtimes read no packaged text (`readText` returns nothing, so keys show), set no direction
and align no labels; right-to-left in custom components that place children by hand is theirs to mirror; dates and
numbers are not localised.

### What moved up from NaboTV (2026-09-14)

As with the dashboard, what the sign-in screens had built for themselves and any app would want went into the
framework, so the app keeps only what is NaboTV's:

- `FormScreen` (`everyui.app.screen`): a form on a card in the middle of the screen — header, fields, the error or
  notice, actions, links, scrolling when short, `busy`, a notice carried from the previous screen, the first focus —
  with `field`, `primary`, `secondary`, `link` and `note` whose words follow the language. NaboTV's `AuthScreen` is
  the logo, "Please wait…", and asking its server.
- `Centered(width)(child)`; `Notice(text, tone, width)`; `Button.bound(words, …)`, `TextInput.bound(label, value, …)`
  and `TextView.paragraph(words, width, …)`, each following what it reads; `ImageView(source, height)` scaled to a
  height; `Column(spacing, padding, center)`.
- `Format.countdown(millis)`; `Language.best(tag, supported, fallback)` (script, region, then language, with Taiwan,
  Hong Kong and Macau as Traditional Chinese); `Strings.remember` and `Strings.remembered`.
- `Network.postJson[Request, Response](url, request, headers)`, failing with `HttpFailure(status, body)` for the app to
  read its own error format from.

## Focus, as a core contract (2026-09-14)

NaboTV's Flutter app struggled with focus more than with anything else (its docs/ui-base-design.md: screens where
nothing had the focus after loading, zones that swallowed the keys running the other way, one-way doors, focus lost
on returning). EveryUI makes those shapes unwritable rather than patched per screen:

- **Every screen and dialog names its default focus.** `UIScreen.defaultFocus()` is abstract, and `Dialog.show` takes
  the component to focus, so a screen or dialog without one does not compile. The manager focuses it on every
  activation; a default not on screen yet (behind a loading state) takes the focus when it appears, unless the person
  has already moved it. Back returns to a screen with the focus where it was.
- **Layouts decide where arrows go.** `Layout.neighbour(container, children, from, direction)`: a row answers Left and
  Right (mirrored right to left), a column Up and Down, a flow or grid moves along a row and to the nearest item in the
  next. Moving the focus walks up from the focused component to the first container whose layout has a neighbour that
  way and enters it on its nearest line, most in line with where the focus was. A container without a layout looks
  among its own children by position, anything in line before anything aside, measured edge to edge. A container can
  state an exit (`exitFocus(direction, target)`) or stop the arrows at its edge (`stopFocus(directions*)`).
- **A dialog keeps the focus.** `Focus.scope` is the open dialog while one is up, so arrows never reach what it covers,
  and closing it returns the focus to where it was.

The theme list on NaboTV's placeholder home was the case that showed the old centre-to-centre search was wrong: with
buttons of different widths, the next button's centre could be farther than one a row up, so Right went up.

**Roku, running the widgets demo on the device for the first time** (the checks only compile it). What it took:

- **String literals.** The emitter wrote carriage returns and tabs raw into them, which the device refuses; they are
  now spliced as `Chr(13)`, `Chr(9)`.
- **`Map.getOrElse(key, default)`** was emitted as Option's; it is now `_mapGetOrElse`, with a Swift twin.
- **Shared code avoids** `Option.toList` (it is `.toList` on `invalid`) and a `def` nested in a method (not emitted).
- **The page collapsed into its corner** for two reasons. A `super` call forced its by-name argument, so every
  `size := contentWidth()` that went through an override became a one-off value; super-call arguments are now emitted
  against the super method's parameters. And `MaterialIconView(size)` overwrote `Component.size`, since a class is one
  flat associative array there; the parameter is now `points`, and the gap meter reports any constructor parameter
  that shadows an inherited member.
- **Keys did nothing.** A statement in an object's body is never run, and `Focus` took the platform's keys in its
  body. `ui` now calls `Focus.listen()`, and `Direction.set` replaced a body `attach`.
- **The background stayed white.** The root is a Group, so `ui.backgroundColor` now colours the scene's full-screen
  rectangle.
- **The stack is about 130 frames.** A reactive hop costs a dozen of them, and reactify's `Val` stored the overflow
  and rethrew it on a later read, so the failure surfaced far from its cause (now fixed in reactify). Opening a
  dialog overflowed it, because measuring a container fired each child's `measure`, one trigger per level.
  A container now refreshes everything under it in one loop, deepest first, and `Focus.candidates` walks with a
  list rather than recursion. Keep tree walks iterative in shared code.
- **No borders.** A SceneGraph Rectangle draws no edge, so every ring-style focus was invisible. The Roku
  `RenderNode` now draws a square border as four edge rectangles that follow the node's size, and a rounded one as a
  tinted 9-patch (see "NaboTV on the Roku").

With those fixes the page lays out as on the desktop (677×593 against 682×597). Arrows follow the layouts, a dialog
keeps the focus, and Back closes it and returns the focus. `i18n ok` passes.

The focus looks needed two more fixes, on every platform. A checkbox showed focus as the `raised` colour, invisible on
a dialog, so it now gets the accent ring. A chosen `Choice` did not change on focus at all, so it now turns to the
text colour as a filled button does.

### NaboTV on the Roku (2026-09-14)

`sbt appRoku/emitRoku` in the NaboTV repo builds the channel with `project/RokuChannel.scala`, shared with this build
the way `AndroidApk.scala` is; `compiler/compilerClasspath` hands an outside build the compiler. Getting NaboTV's own
code through took these, all in EveryUI:

- **Platform on the Roku:** packaged text (`ReadAsciiFile`), settings (the registry), the device's locale and name,
  and `Platform.parseMessages`, which parses a translation natively into a keyed table. The Scala ARB parser and
  entry-list Maps took the Roku past its launch watchdog on a 100 KB file.
- **Compiler:** `toMap`, a list's `indexOf`, `String.contains`, `stripPrefix`/`stripSuffix`, rapid's `attempt`, fabric's
  `isObj`/`isStr`/`value`, `Option.toList`/`filterNot`/`forall`. `Left`/`Right` are now `RtLeft`/`RtRight`, since
  BrightScript's own `Left` and `Right` are string functions. A spliced `obj(pairs*)` passes the array itself. A
  Char argument to `indexOf`/`split` is taken as its code. A facade returning a Map is no longer read as `Map(...)`.
  A by-name constructor parameter read from a closure or an inner class is forced from its field. Each has its
  Swift twin; the Swift side is not yet run on the Mac.
- **Stack:** reactify's `fire` now propagates in one function (`propagate`: the status, the loop over reactions, the
  stop rules), so a reactive hop is about 5 BrightScript frames rather than 7 to 9. `Platform.later` on the Roku runs
  on a later turn of the event loop (a one-shot Timer), as `runLater` and `setTimeout` do, and the screen manager
  makes every screen change on a turn of its own, so the chain that asked for it is not under it.
- **Layout:** batched (`LayoutPass`). A change asks for its container to be laid out; the pass lays out the deepest
  container asked for first, in a loop, so a child's layout that resizes it asks for the parent rather than laying the
  parent out inside itself. Outside a batch the request is laid out at once; a screen's `createUI` is one batch. The
  web and desktop golden frames are unchanged.
- **Runtime loops:** BrightScript's `for each` keeps its place in the object it walks, so a callback that walked the
  same array (a reaction reading a container's children while its flow layout placed them) ended the outer loop after
  four tiles. The runtime's callback helpers (`foreach`, `map`, `filter`, …) loop by index.
- **Screen changes:** each runs on a turn of its own (`Platform.later`), in one layout pass from leaving to arriving,
  so a change asked for by a key (under the key handling, the focus, the click and the action) does not also carry
  the new screen's build and layout on the Roku's stack; Back overflowed it before. A cons pattern on a List
  (`case previous :: rest`, the screen history) tested for a linked-list cell, but a List here is an array.
- **Patterns:** `case p: SomeCaseClass =>` can arrive as a plain `Typed` pattern, which matched anything; it is tested
  by the case class's tag now. (A type pattern on a class that is not a case class still matches anything.)
- **NaboTV:** translations load before the screens are constructed, so the first table fires nothing.

**Where it is:** Link your TV runs on the Roku against the NaboTV server run locally (`server/`, port 8200), the same
backend every platform is tested against (there is no stand-in API: one that invented its own codes made the same
screen look different on two TVs). It matches the Android TV build, after these fixes in the Roku backend:

- **Scale.** Android multiplies the layout by the display's density (2 on a Google TV Streamer, so 960x540 units).
  The Roku now lays out in the same units: `uiScale()` is 2 (1 for a sideload with `viewport.txt`, the golden frame),
  and the render node multiplies positions, sizes and font sizes by it. Scaling the root Group instead drew text at
  half size and enlarged it, pixelated; the node scaling draws it at its size on the screen.
- **Typeface.** Text was measured with its face's file but drawn in the Roku system font, since the runtime named
  only three families; it is now drawn with the same file it is measured with (`uiSetFontFile`).
- **Corners.** A SceneGraph Rectangle is square. A rounded fill or edge is now a Poster behind the node's children:
  a white 9-patch tinted with `blendColor` (and `opacity` for alpha), generated by `RokuChannel.corners` for every
  whole radius up to 64 pixels and edges up to 8. SceneGraph scales a node to the panel but draws a 9-patch's corners
  at the image's own pixels, so the image is chosen in panel pixels (`uiPanelScale`, 2/3 on a 720p UI); chosen in
  design pixels, a pill's ends came out as ellipses.
- **Reachability.** A module's lazy val was emitted only when something named it, so `family` on the `Open Sans`
  object, reached through its trait (`font.family`), was dropped and the example app failed on the device; an
  override is now kept when what it overrides is reached.
- **Long division.** `.toDouble` on a Long was emitted as nothing, and BrightScript keeps a LongInteger quotient
  whole, so the pairing countdown's fraction was 0 and its bar empty; it is `Cdbl` now.

- **Arabic and Chinese.** A Roku's system font has neither, and a Label draws in one face. Text in those scripts is
  drawn in runs (`uiRichText`): each a Label in a face that has its glyphs, Roboto for Latin and the channel's
  `fonts/fallback-*` for the rest (`RokuChannel.fallbackFonts`: Noto Sans Arabic, and Noto Sans CJK SC and TC cut down
  by hb-subset to the characters an app's translations use; `Platform.setLanguage` picks Traditional for zh_Hant). A
  Roku Label joins Arabic letters but lays them out left to right, mirroring each word, so an Arabic run arrives
  shaped (its letters in their presentation forms, lam-alef ligatures, marks kept with their letters, brackets
  mirrored) and reversed, and a paragraph that begins in Arabic draws its runs right to left. The shaping follows
  HarfBuzz's output for the NaboTV strings checked against it.
- **Batches end on a turn of their own.** The outermost `LayoutPass.batched` lays out on `Platform.later` and then
  completes (a screen change's focus still sees laid-out positions); flushed where it closed, at the bottom of a screen
  change's callbacks, the first screen's layout sometimes overflowed the stack on a fresh launch. A language change
  (`Strings.use`) is such a batch too.
- **Type patterns.** `case c: Container =>` on a class that is not a case class matches anything on the Roku (only
  case classes carry a tag to test), so the layout pass asks components by dispatch (`requestLayout`).
- **Backgrounds.** No screen shows an empty ground: the screen manager keeps a `Backdrop` in `ui`, behind every screen
  (a screen change swaps the screen in front of it and leaves it be), showing the active screen's own `background` or
  else `Theme.backgroundImage`, the whole screen covered at the image's own proportions. NaboTV sets a theme's artwork
  there; its build converts the WebP originals to JPEG (JavaFX draws no WebP), 1280 wide for the Roku. On Android an
  image from `http://` needs cleartext allowed in the app's manifest, which NaboTV's had not: its QR never loaded, and
  the loader now logs a failed load instead of dropping it.

Still to do there: text on the Roku does not wrap (a paragraph is one line with an ellipsis), `Platform.editText`
(the Roku keyboard) is not wired, and the gap meter's one `stepSize` (animation's `TaskInstance`, reachable, never
run) remains.

## The network module — Spice as the core, on every backend (2026-09-13)

The first release replaces Sart in DataWrangler's dashboard, and DataWrangler
moves to two-way sockets (it is a real-time dashboard). DataWrangler targets the
web only; the module exists on every backend so none is left behind.

**Decision: Spice is the core.** `spice.http.WebSocket`, `DurableSocketClient`
(sequenced, replayed events, typed RPC, reconnect with backoff) and the pluggable
client implementation are exactly the protocol and the seam we need, and they are
the same code DataWrangler's server runs. One module, `network/`, holds both
HTTP and DurableSocket for now; it can split later.

Layout (`build.sbt`, `backendSources`):
- `network/src` — the portable API: `Network.webSocket(url)`,
  `Network.durable[Event, Info](url, channel, info, config)`, `Network.http(HttpCall)`.
  Nothing in `shared/` depends on it.
- `network/spice` + `network/jvm` (desktop), `network/js` (web), `network/android`
  (OkHttp) — Spice's own client as published, named explicitly (`SpiceHttp.use`)
  because an APK or a native image has no `moduload.list` for the classpath scan.
- `network/native` — Roku and tvOS: the platform's socket and HTTP behind the same
  `WebSocket` trait via facades (`uiSocketOpen/Send/Close`, `uiHttp`). The Roku
  side is `roku/runtime/SocketTask.brs`, an `roStreamSocket` speaking RFC 6455 in
  a Task node (the render thread cannot own a socket; `ws://` only — no TLS),
  and `HttpTask.brs` (`roUrlTransfer`). tvOS: `URLSessionWebSocketTask` and
  `URLSession` in `swift/runtime/UniversalRuntime.swift`.
- `app/` — the example app, moved out of `shared/` (`shared/` is the framework);
  `app/protocol` — the demo's wire types, fabric case classes with `RW.gen`,
  compiled by the app and by `server/`.
- `server/` — `/durable`: a real `DurableSocketServer[String, Tick, ClientInfo]`
  with a ticker and an RPC (`echo`, `drop`). `--host=0.0.0.0` for devices.

**Wire types are fabric case classes, never hand-built JSON** (the user's rule):
`Tick`, `ClientInfo`, `DemoRequest`, `DemoResponse` with `given rw = RW.gen`.

### What the emitter needed for Spice's durable client (Roku, tvOS)

The spike (`network/emitRoku`, the loopback probe `NetworkProbe`, golden
`network/expected-output.txt`) sized it; all of this is in `RokuEmitter`:
- **Reachability**: only what `main` reaches is emitted — a class is live when
  constructed or an ancestor of a live class, a member when reached on a live
  class; a call into a never-constructed type becomes `RtDead`. It dropped the
  file-transfer facet (and its java.nio) untouched, and shrank the app channel
  by 44 KB. A library emitted alone (no `@main`) keeps everything.
- **Shims** for the libraries left out of the TASTy, runtime-neutral names in
  both runtimes: rapid's `Task` (`RapidTask_*`: a continuation-passing task on
  one thread, `sleep` on a SceneGraph Timer / the main queue, `sync` only for a
  task that completes at once), fabric's `Json` (native JSON values;
  `ParseJson`/`FormatJson`, `JSONSerialization`), fabric `RW` (the emitter
  derives `RW.gen[T]` itself: `_rwGen(name, keys, slots, field codecs, ctor)`,
  with Option/List/Map wrappers), scribe, `ConcurrentHashMap`,
  `ConcurrentLinkedQueue`, `AtomicLong`, `UUID`, `System.currentTimeMillis`,
  `FiniteDuration` arithmetic.
- Scala 3 enums' simple cases (`ProtocolState.Active`) as runtime singletons
  (`RtEnum`); case-class companions emitted (their `given rw`); a class extended
  only by an anonymous local class is now in the object model; case literals
  honour named and default arguments; constructors with several clauses.

Gaps: 0 on every channel; brighterscript clean; the probe's eight lines pass on
the JVM (the Roku and Swift runs wait for the devices).

### Status per backend (`tools/check.sh` → `tools/durable.sh`)

`DurableDemo` (`demo=durable`, `socket=<url>`): connect, five events, RPC echo,
a server-side drop, resume with the missed events replayed, five more events,
no gap in either sequence → `[ui] durable ok events=10 resumed=1 gaps=0`.
- ✅ on all five: desktop (JVM, `java.net.http`), web (Chrome via RoboBrowser),
  Android emulator (OkHttp, with the spice fix below), Roku (the Task-node socket)
  and the Apple TV simulator (`URLSessionWebSocketTask`). The probe is identical
  on the JVM, the Roku, macOS and the tvOS simulator; reactify's suite still is.

What the devices found that brighterscript and the JVM could not (all fixed):
- a block of user `val`s ending in a library call was treated as scalac's
  default-argument temporaries and its rhs inlined at every use — `val ws =
  createWebSocket()` made three sockets; the rule now needs compiler temporaries
- positional case-class arguments dropped (`isInstanceOf` on the Quotes API's
  abstract `NamedArg` erases to always-true); `Some("a" | "b")` alternatives
  matched anything; `"…".r` and parenthesis-less `json("k").asString` had no
  shim; the Swift task shim invoked bare functions where the runtime expects
  `{ fn, env }` closures; `m` is reserved as a loop variable on the device;
  case objects stringified by their emitted (qualified) name.

### Found on the way (library notes, for approval before any change)

- **spice-core's Scala.js artifact lists its dependencies by their JVM names**
  (`fabric-io_3`, `rapid-core_3`, …) in the locally published ivy; the web
  build names the `_sjs1` builds explicitly in `web/project.scala`.
- **`OkHttpWebSocket.onClosing` never completed the close handshake**, so a
  server-initiated close never reached `onClosed` and the durable client never
  learned the socket was gone; and a close frame without a status arrives as
  1005, which OkHttp refuses to echo. Fixed in spice (approved): `onClosing`
  replies with the peer's code, or 1000 for a status-less close. Published
  locally as 1.10.9-SNAPSHOT2; uncommitted in the spice repo.
- `String.split` in `URLPath.parse` needs the ES2018 regex engine on Scala.js;
  the web bundle targets ES2021 now.
- spice-core brings profig, which brings jackson (xml, yaml): the APK went from
  443 KB to 1.16 MB; R8 needs `-dontwarn` for `javax.xml.stream`. Worth an
  exclusion or a lighter spice-core split.
- Android has no Loom: `rapid.Task.Virtual = false` before any Task runs; R8
  must keep enum `values()` (`EnumSet` reflects on it).
- The native HTTP backends do not surface response headers yet.
- **reactify: `Val.evaluate` caught around `Reactive.fire` as well as the evaluation**, so an exception in any
  reaction (a stack overflow on the Roku, a bug in a binding) marked the value that was just set correctly as a
  `Failure`, was swallowed, and was rethrown by whichever later read came first. Fixed in reactify (4.2.1-SNAPSHOT,
  uncommitted there): only the value's own function is caught; a reaction's exception propagates to whoever set
  the value. Two new `VarSpec` cases cover both; the reactify suite, the JVM golden and the Roku acceptance run pass.
- **reactify: `fire` propagates in one function** (approved 2026-09-14, same snapshot): `fire` and a private
  `propagate` in place of fire, fireNow, fireInternal and fireOne, with the same status and stopping rules, so a
  change nested through many values costs a small stack (a Roku's) a fraction of the frames.
- **reactify: `Reactions +=` inserts in one pass** (approved 2026-09-15, same snapshot): the reaction goes where a
  stable sort of the list with it appended would put it, unless an equal one is there, instead of sorting and
  de-duplicating the whole list on every attach. The Roku runtime's sort and distinct are quadratic, and a screen's
  build attaches hundreds of reactions: NaboTV's sign-in screen went from 4.2 s to 0.9 s on the device.

## IMMEDIATE NEXT STEP

**-2. NaboTV, a screen at a time** (app in the NaboTV repo, `app/everyui`). The sign-in screens run on the desktop
and on the Google TV Streamer (`sbt appAndroid/appApk`). Next: the profile picker, then home. Before NaboTV goes to a
Roku or an Apple TV, those runtimes need the keyboard (`Platform.editText`), the settings store and registered faces;
and text alignment, so a wrapped note can centre.

**-1. Run the durable demo on the Roku and the Apple TV simulator** once they are
back on the network: `ROKU_PASS=… tools/check.sh --device` and `--tvos` (the
`durable:` rows), plus the probe: `CHANNEL=network/out-roku
GOLDEN=network/expected-output.txt START=socket.open tools/roku-acceptance.sh`
and `PROJECT=network DIR=network GOLDEN=network/expected-output.txt
HARNESS=probe-main.swift tools/swift-spike.sh`. Then DataWrangler's server gets
a `DurableSocketServer` and its app the client.

**0. tvOS and iOS are real: keep them green.** `tools/check.sh --tvos` runs the frame and
frame-rate checks on the simulator via the Mac. Next: bring the collector's
per-frame cost down (above), then a physical Apple TV.

**1. The Roku is verified** (2026-09-13): acceptance identical, the frame matches
the shared golden at the 32× probe, 59.9 fps live, the EveryUI header and mark on
screen.

**2. Commit the reactify performance work** (pass, `_references` fix, fire and
dependency-comparison changes) in the reactify repo, once confirmed there.

**3. Port more of youi's widget layer** onto the model in `shared/`, starting with
focus handling. Run `tools/check.sh --android --device` before each commit.

## Build / ops notes

- sbt: `~/.local/share/coursier/bin/sbt` (NOT on PATH).
- Scala 3.9.0. The Roku compiler is an in-build project (`compiler/`, with
  `compiler-api/` for `@native`). `<project>/emitRoku` compiles the project and
  runs `everyui.compiler.Main` over its TASTy, compiling `reactive` (reactify)
  through as a library. Nothing is consumed from the sart repo.
- Layout: `shared/` (portable UI layer + app), `roku/` (SceneGraph backend +
  `runtime/UniversalRuntime.brs`), `web/` (Scala.js DOM backend), `compiler/` +
  `compiler-api/` (the Roku compiler), `reactive/` (reactify's source, compiled
  for emission), `reactive-test/` (the acceptance suite), `server/` (Spice).
- Build: `roku/emitRoku` → `roku/out-roku/`. The example web bundle is scala-cli —
  `scala-cli --power package web -o web/out-js/main.js -f` (copy `web/index.html` into `web/out-js/` first);
  the published `everyui-web` is the sbt project `web` (`sbt publishLocal`, CONSUMING.md).
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
  (`ssh mbp`, `~/everyui-spike/`), builds for macOS and the tvOS simulator,
  runs both against `reactive-test/expected-output.txt`, then the memory probe.
  `MAC`, `MAC_DIR`, `TVOS_DEVICE` override the defaults. `SwiftEmitter.scala` is
  GENERATED at build time from `RokuEmitter.scala` (see "No Python").
- **tvOS:** `tools/tvos-run.sh` (live, 30 s, frame rate), `--freeze` (the frame vs the
  golden), `--show` (opens the Simulator window on the Mac and leaves the app
  running). `sbt tvos/emitSwift` → `tvos/out-swift/` (App.swift + the two runtimes,
  main.swift, Info.plist, assets). The Mac side lives in `~/everyui-spike/app`.
- **Desktop native image:** `tools/desktop-native.sh` (build + frame + frame rate;
  `--trace` refreshes `desktop/native-config/` first). `tools/check.sh --native`.
- **Tooling is Scala:** `checks/` (the check helpers CLI), `sbt makeIcons`,
  `project/SwiftEmitterDerivation.scala` (SwiftEmitter is generated — edit
  `RokuEmitter.scala` or `compiler/swift-derivation.txt`). No Python.
- **Run every check:** `tools/check.sh` (offline: JVM, Roku emit, web, desktop),
  `--android` (adds an emulator), `ROKU_PASS=<password> tools/check.sh --device`
  (adds the Roku), `--update` to rewrite the golden from the web's frame.
- Desktop: `sbt desktop/run` (a window; `--freeze=0.434 --screenshot=out.png`
  as arguments to capture a frame). Android: `sbt android/packageApk` →
  `android/out-apk/everyui.apk`; SDK from `ANDROID_HOME` (default
  `~/opt/android-sdk`), build-tools 36.0.0, platform android-35. Test on an
  emulator; the only physical Android device to use is the Google TV Streamer
  (`ANDROID_SERIAL=192.168.1.73:5555 tools/check.sh --android`). Google TV turns
  network ADB off after a reboot or update — if `adb connect` is refused, it has
  to be re-enabled on the TV (Settings → System → Developer options).
  Helpers (sideload, console capture, tree extract/compare, fps) are the Scala
  CLI `everyui.checks.Checks` in `checks/`.
- **The web's live frame rate:** `tools/check.sh --live`. `webcheck/` (a tool
  project, not the app) uses RoboBrowser (`com.outr::robobrowser-cdp`
  2.4.7-SNAPSHOT from `~/.ivy2/local`, plus `spice-client-netty` — spice's HTTP
  client needs an implementation) to run a HEADFUL Chrome kiosk-fullscreen on
  its own Xvfb display, where rAF follows the display's 60 Hz, and relays the
  page's console; `Checks fps` reads the `[ui] fps=` lines as it does
  the Roku's. First run: 27 samples, median 60.0, minimum 59.9. (The kiosk
  window reports a 1919x1079 viewport — immaterial to a frame rate, which is
  why the frame check stays on headless Chrome.)
- Freezing a frame by hand: `--freeze=0.434` on the desktop, `am start … --es
  freeze 0.434` on Android, `?freeze=0.434` on the web build, or add
  `freeze.txt` containing `0.434` to the Roku channel (`Checks roku-install
  roku/out-roku --add freeze.txt=0.434`). It steps a virtual 60 Hz
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
- Static gap meter for any emitted channel: `Checks gaps reactive-test/out-roku/components`
  (from the classpath `sbt checks/checksClasspath` stages).
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
