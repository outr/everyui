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
| Android / ATV | — | **Views** (or could join the Skia tier — OPEN) |

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

## youi's `AnimationExample`, reproduced — ✅ BOTH BACKENDS (2026-09-12)

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
is an artifact, not a measurement.)

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

## IMMEDIATE NEXT STEP

**1. Make the Roku compiler regression-proof.** The acceptance suite, the gap
meter (`tools/emit-gaps.py`) and a brighterscript check over the three emitted
channels should become goldens that run without the device.

**2. Commit the reactify performance pass and `_references` fix** in the
reactify repo, once confirmed there (it's a separate project).

**3. Then port youi's widget layer.** Class inheritance works (the object model
above), so `Label extends Component` can replace the composition workaround in
`shared/`.

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
- `shared/app/Main.scala` picks the demo: `"animation"` (youi's
  AnimationExample), `"hello"` (the seam proof + FrameProbe), `"easings"` (the
  numeric curve dump). `AnimationExample.Deterministic` freezes at frame 180 for
  backend-vs-backend comparison.
- Shared assets live in `assets/images/`; `emitRoku` copies them into the
  channel (`pkg:/images/`), and the web bundle serves the same files from
  `web/out-js/images/`, so the two backends are compared on identical pixels.
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

- **Android/ATV:** Views (true native widgets) vs Skia (join the drawn tier)?
- **iOS/tvOS path:** Scala Native → UIKit vs Kotlin interop vs bytecode routes.
- **Naming** (.com/.org available; retire "youi"/"Sart").
- **Skia-on-Scala-Native** de-risk before committing desktop + mobile to it.
- **Naming the seam's read half:** `Prop.refresh()` exists and works, but
  nothing in the slice calls it — measure/layout is where it earns its keep.
- **#1 vs #2 for Roku long-term:** decision was **#2 (generalize RokuEmitter)** —
  write idiomatic Scala once, compiles everywhere — with hand-written-runtime
  kept only as a per-construct fallback. The lean-core slice (next step) is the
  pragmatic near-term; the #2 climb makes reactify-verbatim viable later.
