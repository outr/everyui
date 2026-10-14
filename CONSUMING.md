# Using EveryUI from another build

EveryUI is published locally for now: run `sbt publishLocal` in this repository, and the artifacts go to
`~/.ivy2/local/com.outr/`.

## Coordinates

| Module | What it is | Platforms |
|---|---|---|
| `"com.outr" %% "everyui-web" % "0.1.0-SNAPSHOT"` | the UI layer (components, layouts, focus, keymaps, screens, theme, i18n) over the DOM | Scala.js |
| `"com.outr" %% "everyui-desktop" % "0.1.0-SNAPSHOT"` | the UI layer over JavaFX, with the network module | JVM |
| `"com.outr" %% "everyui-android" % "0.1.0-SNAPSHOT"` | the UI layer over Android's Views, with the network module | JVM (Android) |
| `"com.outr" %% "everyui-roku" % "0.1.0-SNAPSHOT"` | the UI layer over SceneGraph, with the network module, for the compiler | JVM (TASTy) |
| `"com.outr" %% "everyui-tvos" % "0.1.0-SNAPSHOT"` | the UI layer over UIKit, with the network module, for the compiler | JVM (TASTy) |
| `"com.outr" %% "everyui-network" % "0.1.0-SNAPSHOT"` | HTTP and DurableSocket, with Spice as the core: the web's, and a server's | JVM and Scala.js |
| `"com.outr" %% "everyui-seo" % "0.1.0-SNAPSHOT"` | what an app's server writes for search and answer engines | JVM and Scala.js |
| `"com.outr" %% "everyui-monaco" % "0.1.0-SNAPSHOT"` | Monaco as EveryUI components | Scala.js |
| `"com.outr" %% "everyui-compiler" % "0.1.0-SNAPSHOT"` | Scala 3 TASTy to BrightScript and Swift, run by the plugin | JVM |
| `"com.outr" %% "everyui-compiler-api" % "0.1.0-SNAPSHOT"` | `@native`, for a facade over a Roku or Apple TV global (comes with everyui-roku and everyui-tvos) | JVM |
| `addSbtPlugin("com.outr" % "sbt-everyui" % "0.1.0-SNAPSHOT")` | an app's build: web targets, and packaging for every other platform | sbt 2 |

The version stays 0.1.0-SNAPSHOT from one publish to the next. What a build has is in each jar's manifest instead:
`EveryUI-Commit` is the commit it was published from (with `-dirty` after it if the tree had uncommitted changes), and
the plugin's `everyuiCommit` answers it (`sbt show everyuiCommit`), warning when the jars on a classpath come from more
than one commit. Record it with a deploy to say which EveryUI shipped.

Everything is built with Scala **3.10.0-RC3** and Scala.js **1.22.0**, so a consumer needs Scala 3.10 (an RC or
later) to read it.

A backend is chosen when EveryUI is compiled: the shared layer names `everyui.render.RenderNode`, and each backend
supplies it. So there is no separate core artifact. The shared layer ships inside each backend module, and
`everyui-web` is the web one. An app builds every platform from these alone, with no EveryUI checkout: see
[The desktop, Android, the Roku and the Apple TV](#the-desktop-android-the-roku-and-the-apple-tv).

## sbt

`project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

`build.sbt` (sbt 2 picks the `_sjs1` artifact for a Scala.js project from `%%`):

```scala
lazy val app = project.in(file("app"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := "3.10.0-RC3",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("myapp.main"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)
      .withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2021))),
    // Spice's Scala.js artifacts name some dependencies by their JVM names; keep the JVM reactify off the page
    excludeDependencies += ExclusionRule("com.outr", "reactify_3"),
    libraryDependencies ++= Seq(
      "com.outr" %% "everyui-web" % "0.1.0-SNAPSHOT",
      "com.outr" %% "everyui-network" % "0.1.0-SNAPSHOT"   // only if the app talks to a server
    )
  )
```

### Republished snapshots

Each publish replaces the last under the same version, and one may bring other dependencies of its own (a newer Spice).
sbt resolves again only when what a build declares changes, so a build used to keep the old resolution and run the new
EveryUI jars with the old Spice, failing with `NoSuchMethodError`. With sbt-everyui a build notices: each project hashes
the descriptors (`ivy.xml`) of its snapshot dependencies in `~/.ivy2/local`, and of the snapshots those depend on, and
resolves again once after a publish changed any of them (in a running sbt server too, whose in-memory resolutions it
lets go), and never when nothing changed. A build without the plugin, or with an sbt-everyui from before this, recovers
in a new server (`sbt shutdown`) by running `update` itself, which resolves afresh when it is the command, or `clean`;
in the server that resolved it, neither helps, since that server remembers the old resolution.

## A minimal web app

```scala
package myapp

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Component, TextView}
import everyui.keymap.{Chord, Keymap}
import everyui.net.URLPath
import everyui.theme.Theme
import reactify.*

object Hello extends UIScreen {
  override def manager: ScreenManager = App
  override def path: URLPath = URLPath("/")

  private val count: Var[Int] = Var(0)
  private lazy val greeting = new TextView {
    content := "Hello, EveryUI (" + count().toString + ")"
    font.size @= 32.0
    color := Theme.text()
    focusable @= true
  }

  // this screen's own shortcut, in force while it shows
  keymap.add("hello.count", "Count", Chord("K", primary = true)) { count @= count() + 1 }

  override def defaultFocus(): Component = greeting
  override def createUI(): Task[Unit] = Task(container.children += greeting)
}

object App extends ScreenManager

@main def main(): Unit = {
  Keymap.global.add("hello.reset", "Reset", Chord.parse("Primary+Shift+R")) {
    everyui.kit.Toast.show("Reset")
  }
  App.run(Hello)
}
```

Check `UIScreen` for the members a screen overrides. The example app in `app/src/main/scala` shows more.

### Don't build components inside `:=`

A `:=` binding depends on every `Var` its expression reads, and building a component reads plenty (a `TextView`
measures its words as it is made). So a binding that makes components depends on the ones it just made, fires again,
makes new ones, and the page hangs:

```scala
refs.children := item().toVector.flatMap(_.refs).map(r => new Tag(r.name))   // never ends
```

Bind the data and make the components outside the tracked expression. For children made from a list, use
`bindMaking`: `values` is tracked, `render` is not, a value still in the list keeps its component, and one that has
left it is disposed:

```scala
refs.children.bindMaking(item().toList.flatMap(_.refs))(r => new Tag(r.name))
```

`bind` is the same for components that are kept and handed back rather than made. Anything else built from a value
belongs in a listener: `item.attach(i => panel.children @= …)`. A `:=` is for values: words, sizes, colours, icons.

### Taking components apart

A component bound to something that lives on (a theme colour, the clock, the host's size, the focus) is held by it
until it is taken apart with `dispose`, and with it everything it holds, pictures and nodes included. So what is made
and then dropped must be disposed of, or a page that makes rows, boards or tiles as it goes grows until a television
closes the app for its memory:

```scala
stage.children.replaceMaking(Vector(board.build()))   // the last board is taken apart as this goes in
rows.children.bindMaking(items())(item => new Row(item))
```

`replaceMaking` and `bindMaking` take apart what leaves; `children @= …` does not, and suits only components that are
kept and shown again. A component made but left off the screen (rows a view drops with `children := …`) is not under
the board as it is taken apart, so whatever made it disposes of it with the board: `list.onDispose(() =>
rows.foreach(_.dispose()))`. A dialog is taken apart once it has closed (`Modal`), its panel too unless it was put up
with `keep = true` to be put up again. A screen made for one showing (`oneShowing`) is taken apart by its manager once
nothing can go back to it. Taking a large tree apart runs a slice a turn (`Component.disposeGradually(roots, done)`),
so a screen of thousands of components never outlasts a Roku's watchdog; disposing twice does nothing.

### Moving a component to another container

A component put in another container (`children @=`, `+=`, `++=`, `replaceMaking`, `bind`) leaves the one it was in,
as the DOM's `appendChild` moves a node: that one's `children` no longer hold it, both are laid out again, and nothing
is taken apart, so `slot.children().contains(chat)` is true only while the chat is there. The container it left
changing again leaves it where it went, and a list that takes apart what leaves it (`bindMaking`, `replaceMaking`,
`dispose`) does not take apart one that has moved on. A list bound with `:=` keeps saying what it holds, and one `bind`
renders goes back to it at its next change while its value is still there. A focused field moved, or taken off the
screen and put back, keeps the focus and has the platform's again (its caret) as it is placed; `focus()` on what has
the focus already takes the platform's focus again. A click on the page's empty space lets go of the focus on the web,
as the browser does; on a remote's platform the focus stays where it is.

### Rebuilding a screen

Most changes need nothing rebuilt: text bound to `Strings` changes language, and anything bound to `Theme` changes
colour. Where a change reshapes the UI itself (a language whose screens are laid out differently, a theme that changes
structure), and there is no restarting the process (the web), the manager builds screens afresh in place:

```scala
Strings.use(french).flatMap(_ => App.rebuildAll()).startUnit()   // or App.rebuild(), App.rebuild(screen)
```

`rebuild()` rebuilds the showing screen on a turn of its own: it is deactivated, what its last build made is taken
apart (a slice a turn), `init` and so `createUI` run again, it is activated, and the focus goes to what stands where it
was (the same place under the screen, the same kind of component), else to `defaultFocus`. `rebuild(screen)` of a screen
not showing, and `rebuildAll()` for all but the showing one, mark it to be rebuilt the next time it is shown, Back
included, where its focus finds its equivalent the same way.

Keep state outside the built UI, so a rebuild has nothing to lose: what the screen shows lives in `Var`s on the screen
(or further out) and `createUI` reads them. Components a screen makes as fields, in its constructor, are kept, put back
where they were before its first build and placed again by `createUI`; everything `createUI` makes is made anew. So give
a field no lasting reaction in `createUI` (a `bind`, an `attach`), which a second build would add again: make that part
in `createUI` instead. What lives only in the built UI (a field's words, how far a list is scrolled) is carried across by
`onRebuild`, asked with the old UI still there, which answers what to do once the new one is built:

```scala
// search: the TextInput createUI last made
override protected def onRebuild(): () => Unit = {
  val typed = search.value()
  () => search.value @= typed
}
```

### A screen built over several turns

A Roku stops a turn that runs too long (an Execution timeout), and the screen whose build it was never shows. So a
screen's first showing is spread over turns of its own: the screen being left is deactivated on one, the new one's
`init` (and so `createUI`) runs on the next, its `load` on another and its `activate` on the last; a `FormScreen`'s
card, what `build` puts on it, and what a screen's own `createUI` adds after the form are a turn each. All of it is one
layout pass, laid out once it has arrived, before the focus moves; until then the screen being left shows and the new
one is in nothing drawn. A screen built afresh in sight (`rebuild()`) is built and arrives on one turn, so no frame
shows it half made. A change asked for while another is under way starts once that one is done (or once it has taken
`ScreenManager.stallSeconds`, when it is taken to have stalled and is waited for no longer).

A screen whose own `createUI` is long can give its parts turns of their own the same way, with the layout still held:

```scala
override def createUI(): Task[Unit] = Task(header()).flatMap(_ => Screen.turn(this)).map(_ => rails())
```

### When frames run

On the web, the desktop and Android (`Frames.onDemand`) the screen manager's frame loop runs only while something
needs it. A screen showing what it last heard asks the platform for no frames at all, so a dashboard left open for
days, or a laptop on battery, spends nothing on it. On a Roku and an Apple TV every frame runs, as before.

The loop wakes by itself, on the platform's next frame (the same one on the web), for:

- a change to anything drawn: a component's property, its size, its children. It runs the frame that draws it and
  the one after;
- a key or a pointer;
- an animation started on a `TaskSupport` (`x.to(1.0).in(…).start()`), for as long as it runs. A paused one needs
  none;
- a listener on a `TaskSupport`'s `delta`, for as long as it listens;
- the clock: while anything reads `Clock.now` (`Format.elapsed`) or listens to `Clock.tick`, a frame a second;
- a `Ticker` (a `Transcript`'s "5m" and live elapsed times, every second; a `RichList`'s times, every 20 s) moving on.
  Made with an owner (`Ticker.of(owner, seconds)`, as those two make theirs), it moves on only while the owner is on
  the screen (`OnScreen`: in `ui`, nothing it is in hidden) and the app at the front (`Platform.onAway`, `onResume`):
  a chat or list kept aside without being disposed of (in a tab not shown, taken out of its place) sets no timer and
  wakes nothing, and as it comes back it reads the clock at once, so its times are right the moment they show, and
  moves on from there. `stop()` ends one for good, as its owner disposed of does.

Motion (`Component.motion`) is run by the platform and needs no frames of the app's. Timers (`Platform.after`,
`Platform.later`) are not frames either.

Change what is drawn only on the thread that draws. What a socket, an HTTP call or a pool hands back on a thread of
its own goes there by `Platform.later` first. On Android a change made elsewhere once stopped the loop for good (its
frame was asked of that thread's Choreographer, which threw), and every scroll and animation after it stood still;
the frame now goes to the main thread whatever thread asks, and the first few such wakes are logged (`frames: woken
off the main thread`) with the stack that made them, to be moved.

Work done in `update` every frame must say it needs frames, or it stops when nothing else needs them:

```scala
override def update(delta: Double): Unit = {
  super.update(delta)
  if (counting()) now @= System.currentTimeMillis()
}
override def needsFrames: Boolean = super.needsFrames || counting()

// when it starts needing them, from a quiet screen
counting @= true
Frames.request()
```

Anything else can ask directly: `Frames.request()` for the next frame, `Frames.request(3)` for three, or
`Frames.hold()` for every frame until `release()`. The app's `frame(ms, updateMs)` hook is called on every frame that
runs. The first after an idle spell is timed from what woke it. `fps=1` at launch holds every frame, so the rate
it logs is the rate the app holds, not how often it idles.

On Android a component's motion runs only while its view is in the window and not hidden (`visible`): a spinner
taken off the screen and never disposed of stops, and turns again if it is put back. To find what keeps an Android app
busy while nothing moves, launch it with `--es idleAudit 1` (release builds too): every five seconds it logs, under
`[idle]`, the frames the loop ran and what keeps it running (holds, frames owed, the tasks or screen that want them,
and, once it has run a second without idling, where each wake came from), the motions running, every message the main
thread ran by who sent it with its time, where each `Platform.after` was asked for, and each thread's CPU. Off, it
costs nothing.

## The page

The bundle expects an element with the id `app` (it makes one if there is none) and its faces. With the sbt-everyui
plugin the build writes the faces into the page (see [The faces](#the-faces-everyuiwebfaces) below), so a minimal
`index.html` is:

```html
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, interactive-widget=resizes-content">
  <style>
    html, body { margin: 0; height: 100%; overflow: hidden; }
    #app { width: 100%; height: 100%; overflow: hidden; }
  </style>
</head>
<body>
  <div id="app"></div>
  <script src="main.js"></script>
</body>
</html>
```

On a phone the app is laid out in what the on-screen keyboard leaves showing: `ui` is as tall as the browser's visual
viewport (`window.visualViewport`), so what sits at its bottom (a chat's composer) stays just above the keyboard, and a
field being written in is brought into view in whatever scrolls it as the keyboard opens. The page is kept at its top,
so nothing is left shifted once the keyboard closes. iOS Safari always puts its keyboard over the page and says so only
in the visual viewport; `interactive-widget=resizes-content` asks Chrome on Android to make the window itself shorter
instead, so it never moves the page to show the field first.

A build without the plugin writes the bundled faces' rules itself, with the files in `fonts/` next to the page (copy
`assets/fonts/*.ttf` from this repository, and `assets/images` if the app uses them):

```html
  <style>
    @font-face { font-family: 'Open Sans'; font-weight: 400; src: url(fonts/OpenSans-Regular.ttf) format('truetype'); }
    @font-face { font-family: 'Open Sans'; font-weight: 600; src: url(fonts/OpenSans-SemiBold.ttf) format('truetype'); }
    @font-face { font-family: 'Material Icons'; font-weight: 400; src: url(fonts/MaterialIcons-Regular.ttf) format('truetype'); }
    @font-face { font-family: 'Roboto Mono'; font-weight: 400; src: url(fonts/RobotoMono-Regular.ttf) format('truetype'); }
    html, body { margin: 0; height: 100%; overflow: hidden; }
    #app { width: 100%; height: 100%; overflow: hidden; }
  </style>
```

That page still works as it always did: the runtime waits for Open Sans and Material Icons before the first frame, and
adds an app's own faces (`Faces.register`) from `fonts/<file>`. It ships every face whole, as TrueType, and fetches
Open Sans at the start whether the app draws in it or not.

Link with `fastLinkJS` or `fullLinkJS`, and serve `main.js` next to the page. DataWrangler's `ui/webBundle` task
(its `build.sbt`) does this, with a content hash on the script's URL.

## Keys

Every key goes through `everyui.focus.Focus`. A `KeyEvent` carries `ctrl`, `meta`, `alt` and `shift`. Remotes (Roku,
tvOS) report none.

Shortcuts are commands in a keymap. `Keymap.global` is always in force. Each screen has its own `keymap`, which is in
force only while that screen shows and is asked first. A chord that runs a command is taken before the focused
component and the focus layer, and is kept from the browser (`preventDefault`).

```scala
Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true)) { closeTab() }  // Ctrl+W, Cmd+W on a Mac
keymap.add("file.new", "New file", Chord.parse("Primary+Shift+N")) { newFile() }       // in a screen
Keymap.global.rebind("tab.close", Chord("F4", ctrl = true))
Keymap.global("tab.close").foreach(_.enabled := tabs().nonEmpty)
Keymap.listed   // every command in force, with its label and chords, for a shortcut list
```

Tab and Shift+Tab move the focus through the screen in tree order.

A double-tap is a modifier on its own, pressed and released twice in quick succession. It works in any binding:

```scala
Keymap.global.add("palette", "Command palette", Chord.doubleTap("Shift")) { palette.open() }
keymap.add("search", "Search everywhere", Chord.parse("Primary Primary")) { search() }  // Ctrl Ctrl, Cmd Cmd on a Mac
Keymap.doubleTapWindow @= 300.0   // ms from the first press to the second release; 400 by default
```

The modifiers are `Control` (`Ctrl`), `Shift`, `Alt` (`Option`), `Meta` (`Cmd`) and `Primary`. `Chord.parse` takes
the name twice with a space between: "Ctrl Ctrl", "Shift Shift", "Alt Alt", "Primary Primary". The command runs on
the second release, and only when:

- both taps fall within `Keymap.doubleTapWindow`, from the first press to the second release;
- no other key goes down in between, and no other modifier is held (so Ctrl+C then Ctrl is not one);
- the modifier's own auto-repeat is ignored: holding it down is still one press.

A double-tap always runs, even while the focus is inside an embed, whether or not the command is `inEmbeds`: a
modifier on its own types nothing, so the embed loses nothing to it. `Keymap.listed` shows it as "Ctrl Ctrl",
"Shift Shift" or "Alt Alt", and as "Cmd Cmd" or "Option Option" on a Mac. While a double-tap of Alt runs a command, a
lone Alt is kept from the browser (`preventDefault`), so the browser's or the system's menu bar does not take the
focus. With no such command, Alt is left alone as before. Remotes (Roku, tvOS, a TV browser, Android TV) have no
double-taps.

The browser never hands some chords to a page: Ctrl+W, Ctrl+T, Ctrl+Tab and Ctrl+N in a normal tab. They work when
the app runs as an installed app window. Choose defaults with that in mind.

### The mouse's back and forward buttons

They are keys: "BrowserBack" and "BrowserForward", so a keymap binds them as it binds any chord, beside a keyboard's
own, and a Navigate Back command takes both:

```scala
keymap.add("nav.back", "Navigate Back", Chord("BrowserBack"), Chord.parse("Primary+Alt+ArrowLeft")) { editors.back() }
keymap.add("nav.forward", "Navigate Forward", Chord("BrowserForward"), Chord.parse("Primary+Alt+ArrowRight")) { editors.forward() }
```

A chord may hold modifiers (`Chord("BrowserBack", shift = true)`). The back button no chord takes is `Key.Back`, as a
remote's is (a dialog closes, the screen goes back); the forward one is `Key.Other`. Neither is a press on what is
under the pointer, and neither is an embed's: a chord on one runs over a Monaco editor, a terminal or a web view
without `inEmbeds` or `Keymap.embed`.

- **Web.** Heard on the window before anything in the page (`MouseEvent.button` 3 and 4), Monaco and the terminal
  included, and in a `WebView` frame of the app's own origin. Where the app takes the press, the release is
  prevented (`mouseup` and `pointerup`), and the page does not go back or forward: Chromium navigates as the button is
  let go, and goes all the same for a press or an `auxclick` prevented. Where nothing takes it the browser does as it
  does. A frame of another origin, or a sandboxed one, keeps the buttons from the page: over one they are the browser's.
- **Desktop.** JavaFX's `MouseButton.BACK` and `FORWARD`, heard by the window before any node, a web view (Monaco, a
  terminal, a `WebView`) included.
- **Android.** A mouse's back button arrives as `KEYCODE_BACK` from the mouse (`InputDevice.SOURCE_MOUSE`), which is
  "BrowserBack"; a remote's Back stays "Back" (both `Key.Back`). The forward button is `KEYCODE_FORWARD`. The
  activity hears them before any view, a web view included.
- **Roku, Apple TV.** No mouse.

A guest in a web view of its own (`EmbedGuest`) prevents its page's own back and forward for them; the app has heard
them already.

### Embeds

An embed is something in the page that handles its own keys, such as a code editor. It is marked
`data-everyui-embed="<name>"` on the web, or with the node property `Host.embedKey` on the desktop. Anything the app
did not make (outside `#app`: a field, a frame, a password manager's menu) counts as the embed `"field"`. While
the page's focus is inside an embed, every key is the embed's, Escape included. EveryUI does not move the focus and
does not `preventDefault`. Only two kinds of command still run:

```scala
Keymap.global("file.new").foreach(_.inEmbeds @= true)       // wins inside every embed
Keymap.embed("editor", Chord("W", primary = true))           // the chords this embed leaves to the app
```

An embed gives back a key it did not want. For example, an editor's Escape with no popup to close becomes Back:
`Focus.handBack("Escape")`.

### Typing without the platform's keyboard (`Container.typeInto`)

A Roku types characters as keys: its phone app's keyboard, voice entry and a remote with keys send ECP's
`keypress/Lit_<c>`, `Backspace` and `Enter`. The Roku backend reports a character as its own name (`"a"`, `" "`,
`"é"`, percent-decoded where it still is), Backspace as `"Backspace"`, and that Enter, which is not OK, as
`Keys.TypedEnter`. The remote's own keys are unchanged.

A container that takes typing says where it goes. While the focus is on it or in it, a character, a space,
Backspace and a typed Enter go to its `TextEntry` instead of to the focused part's `event.key.down`. The nearest
container that says takes them. OK still presses what has the focus, and the arrows still move. A `TextInput` and a
`SearchField` with no platform field under them (Roku, Apple TV) say it of themselves, so the field with the focus
is typed into. A keyboard drawn by the app says it once, for the field it edits:

```scala
keys.typeInto(TextEntry(query, () => done()))   // after the words; Backspace takes off the last; Enter runs done
keys.typeInto(new TextEntry {                    // or the keyboard's own editing (a selection being replaced)
  def typed(s: String): Unit = typeIn(s)
  def erase(): Unit = backspace()
  def enter(): Unit = done()
})
```

On every platform a keyboard's printable keys, Space and Backspace, with no Ctrl, Alt or Meta, reach the entry the
same way. Drop any per-key `event.key.down` typing (for example, a Bluetooth keyboard typed through each focused key)
once the keyboard states an entry. Those keys no longer reach `event.key.down` there.

### Coach marks (`Focus.confine`, `everyui.coach.Spotlight`)

A tour that has the viewer do one thing at a time holds the focus in that thing and dims the rest of the screen.

`Focus.confine(region, presses = true, holds = true, arrows = true)` holds the focus in `region` until the function
it returns is called:

- The arrows move only among what is in the region, by its layouts and the exits it states. An exit leading out of
  it goes nowhere, and where nothing lies that way the focus stays. Nothing is found by position.
- With the focus outside the region, any arrow lands in it (its entry, else the part last focused, else its first),
  and nothing else reaches what has the focus there: OK does nothing.
- `presses = false` holds back OK, a click and a tap in the region. `holds = false` holds back OK held (OK then
  presses at once, where presses are let), a long press, the secondary button and the menu key. With presses held back
  and holds let, a short OK does nothing and OK held still fires `event.hold`; its release is no press.
- `arrows = false` lets no arrow (nor Tab) move the focus in the region: it stays where it is. What the page itself
  focuses in it is still taken, so confine a container that stays (a menu's rows' parent) and the page may build its
  rows again and focus the new one.
- The focused part taken out of the region (a row built again, a card disposed) hands the focus to what took its
  place there, else the one before, else the region's entry or first, a turn later; never to nothing or out of it.
  The region itself taken off the screen holds nothing any more, and the focus moves on to what took its place.
- `focus()` on something outside the region is refused. So is the pointer at what was on the screen when the
  confinement began; what is put up afterwards (the note beside it) is pointed at as ever. The screen's key hooks
  (`Focus.onKey`) hear nothing while it holds; Back answers as ever.
- A dialog put up over it has the focus and the keys while it is up, and gives the focus back inside the region as it
  closes. A confinement over another holds until it is released, and the one under it then holds again.

`Spotlight.on(targets, radius, ring, margin)` dims the whole screen but for a hole round `targets` (one hole, round
them all, `margin` wider), and `release()` fades it out and takes it apart. The hole follows the targets as they move,
scroll and glide. Under the dim the pointer reaches nothing: a click, a tap, a hover or the wheel. In the hole it
reaches what is there. The dim is `Theme.backdrop` and the ring `Theme.accent` unless `shade` and `ringColor` are set.
Change `targets` to move the hole to the next stop without a flicker. Put the note up after the spotlight, so it is
above the dim. On a Roku the spotlight is a handful of nodes (four bands and a ring), and the hole is square under a
rounded ring; there and on the Apple TV no corner pieces are made.

NaboTV's stops, the card focused and the light on it:

```scala
val light = Spotlight.on(List(rail), radius = 14.0, ring = 3.0)
var held = Focus.confine(rail)                    // Browse: along the rail only
def stop(region: Component, presses: Boolean = true, holds: Boolean = true, arrows: Boolean = true): Unit = {
  held()
  held = Focus.confine(region, presses, holds, arrows)
  light.targets @= List(region)
}
stop(card, presses = false)                       // Hold: OK held opens its menu; a short OK does not open the title
stop(card, holds = false)                         // Open: OK opens it, and only that
stop(menuRows, presses = false, holds = false, arrows = false)  // Play: nothing but Back, the rows built again
stop(askNabo)                                     // Ask: any arrow from Home lands on it
held(); light.release()                           // done
```

The example's `demo=coach` walks the same stops (`probe=1` walks them itself).

## General components

Beyond the core components (`Button`, `Dialog`, `Table`, `ScrollContainer`, and so on), `everyui.kit` has these:

- `TabBar` and `TabItem`: a strip of tabs, fixed (`TabBar(List(key -> label), current, width)`) or open-ended
  (`add`, `remove`, closable tabs, the `closing` channel), with `icon` and `marker` slots beside each label. `add(key, label, closable,
  at = i)` puts a tab at a place and `addAfter(key0, key, label)` beside another (a diff by its file); closing the tab
  shown shows the one now in its place, else the one before; a middle click closes a closable tab.
  `add(…, lead = TabLead.Person(p))` leads a tab with somebody's avatar (a conversation with an agent), and
  `TabLead.Icon(i)` with an icon (a file's type); `TabLead.None` by default, as before. A tab's `busy` and `busyDetail`
  Vars turn the busy ring (below) around its avatar, the avatar keeping the ring's room so the label never moves;
  right to left the lead is at the label's right. A tab's `lead` is a `Var[TabLead]`, changeable once it is made as its
  `label` is (`tab.lead @= TabLead.Person(renamed)`, or through `TabGroups.tab(k)` for a group's tabs, the groups'
  `lead` answering the same for tabs made later): a person for a person (a new name, a new portrait) and an icon for an
  icon show in the view they had, the tab exactly as wide; another kind is made afresh, the old view taken apart, and
  the tab widens or narrows by the difference (an avatar's room is 20 px and the ring's outset on each side, an icon
  18 px, and None has neither nor the 6 px gap after it). `leadView` is the view leading it now.
  `tabMenu` (`String => List[MenuItem]`, empty by default) gives a tab a context menu: the secondary button opens it
  at the pointer (keeping the browser's own away only where there is one), the menu key, Shift+F10 and OK held drop it
  from the tab. The bar is read as a tab list and each tab as a tab, and the tab shown is scrolled into view.
  `addMenu` (`Option[() => List[MenuItem]]`, None by default) puts a "+" after the last tab, as a browser's new-tab
  button, that drops the menu it makes afresh each time: just after the last tab, and kept at the bar's end while the
  tabs scroll before it once they overflow (at the left end, right to left). A click, a tap (a 48 px target), Enter,
  Space and OK open it under the "+" (a panel in the middle of the screen on a television), and so does Down where a
  keyboard drives the app; it closes as menus do, the focus going back where it was or where the chosen entry puts
  it. The arrow onward from the last tab (Right, or Left right to left) and Tab reach it, and the arrow back returns
  to the last tab. `addLabel` (default "New…", `Words("tabs.add")`) is its tooltip and its name to a screen reader.
  Its glyph is muted as a tab's close button is, brightening on a plate under the pointer, and it is lit as a tab is
  while focused. With `addMenu` None there is no "+" and the bar is exactly as it was.
- `TabGroups[K]`: tabs in groups split across and down, as an editor splits its files (below).
- `Toast.show(words, seconds, top, margin)`: one short message at a time.
- `AsyncPress(spin, minSpin = 0.5)`: a press that runs slow work, with a delayed spinner, and failures toasted. The
  spinner (`spin(true)`, the app's own: a button swapped for a `Spinner`, say) shows only once the work has run
  `AsyncPress.spinAfter` (0.4 s), so quick work looks instant, and once shown stays at least `minSpin` seconds (Material's
  half second), so work ending just after it shows does not flash it for a frame. The work's answer applies at once (its
  failure toasted, the button pressable again); only `spin(false)` waits out the rest. A press while it is still up keeps
  it spinning for the new work. `running` says whether work is in flight, `spinning` whether the spinner is up. A
  dialog's async button (`DialogAction.async`) spins the same way.
- `Spinner` and `Ring`: an indeterminate spinner and a determinate dial.
- `LazyRows`: a long list made as it is scrolled toward.
- `Turns`: work split over frames.
- `TextView.line`: one line that closes up when it is empty.
- `TextScroll`: text scrolled with the arrows.
- `ImageViewer`: pictures shown full screen, one at a time.
- `Mirror`: parts placed by hand, mirrored for right-to-left languages.
- `TextOutline.overPicture` and `TextOutline.overPictureLight`: edges for text over pictures.

## App chrome

Pieces most apps need around their content, each in a package of its own. They work with a pointer, a keyboard and a
remote alike, and take their words from the app (or from `everyui.i18n.Words` for OK, Cancel and the like).

### SplitPane (`everyui.split`)

Two parts side by side, or one over the other with `vertical = true`, and a handle between them that a pointer drags.
`ratio` is the first side's share (0 to 1): pass a Var the app keeps and saves. Each side keeps to its minimum
(`minFirst`, `minSecond`) while there is room for both. The `collapsible` side folds away on a double-click on the
handle, when dragged most of the way past its minimum, or by `toggle()`, and comes back at its ratio. The arrows cross
from one side to the other, and a split can be a side of another.

```scala
import everyui.split.{SplitPane, SplitSide}

val sidebar = Var(Platform.setting("sidebar").toDoubleOption.getOrElse(0.25))
sidebar.attach(r => Platform.saveSetting("sidebar", r.toString))
val split = new SplitPane(tree, new SplitPane(editor, console, vertical = true, ratio = Var(0.7)),
  ratio = sidebar, collapsible = Some(SplitSide.First))
split.size.width := ui.size.width()
split.size.height := ui.size.height()

Keymap.global.add("sidebar.toggle", "Toggle sidebar", Chord("B", primary = true)) { split.toggle() }
Keymap.global.add("sidebar.grow", "Wider sidebar", Chord.parse("Primary+Alt+ArrowRight")) { split.grow() }
Keymap.global.add("sidebar.shrink", "Narrower sidebar", Chord.parse("Primary+Alt+ArrowLeft")) { split.shrink() }
```

`split.handle.focusable @= true` puts the handle in the focus order too: the arrows along it move it, Enter folds.
Split across, the first side is at the start of the reading direction: on the right in a right-to-left language
(`Direction.rtl`), with the drag and the arrows mirrored.

A side can keep a length in pixels instead of a share, as an editor's sidebar keeps its width when the window is
resized: `length = Some(SplitSide.First -> width)`, `width` a Var the app keeps and saves, which a drag writes. As the
split grows or shrinks that side keeps its length and the other takes the change, the minimums still holding (a
window too narrow for both shows the side shorter, and the length kept comes back as it widens); `ratio` is not read.
`maxFirst` and `maxSecond` cap either side, in either kind of split (no cap unless set). A double-click still folds the
collapsible side, which comes back at its length.

```scala
val sidebar = Var(Platform.setting("sidebar").toDoubleOption.getOrElse(280.0))
sidebar.attach(w => Platform.saveSetting("sidebar", w.toString))
val split = new SplitPane(tree, editor, length = Some(SplitSide.First -> sidebar), collapsible = Some(SplitSide.First))
split.minFirst @= 180.0
split.maxFirst @= 480.0
```

### ResizeHandle (`everyui.split`)

The edge of a part an app lays out by hand (a sidebar between a tool rail and the main area), dragged to make the part
wider or narrower: the same handle as SplitPane's (`SplitHandle`), so it drags the same way, lit under the pointer,
with the resize cursor, 8 across for a pointer and 20 for a finger. `length` is the part's width (its height at its
top or bottom) in pixels, a Var the app keeps and lays the part out by, so the part keeps its width as the window
changes; it is kept between `min` and `max` (`minLength`, `maxLength`). `edge` is which edge of the part it is on, and
dragged away from the part it grows: `ResizeEdge.End` for a sidebar at the start (its left right to left, where the
drag is mirrored too), `Start` for a panel at the end, `Bottom` or `Top`. Given `collapsed`, a double-click folds the
part and back, as a drag most of the way past `min` does, and a drag out from the folded edge brings it back; the app
hides the part while it is folded. Made focusable (`focusable @= true`), the arrows along it move it by `step` (24)
and Enter folds. The app places it, its middle on the edge:

```scala
import everyui.split.{ResizeEdge, ResizeHandle}
import everyui.kit.Mirror

val width = Var(Platform.setting("sidebar").toDoubleOption.getOrElse(280.0))
width.attach(w => Platform.saveSetting("sidebar", w.toString))
val folded = Var(false)
val edge = new ResizeHandle(width, min = 180.0, max = 480.0, collapsed = Some(folded))
val shown = Val(if (folded()) 0.0 else width())
sidebar.size.width := shown()
sidebar.visible := !folded()
edge.size.height := ui.size.height()
// from the reading direction's start: on the right in a right-to-left language
Mirror.place(sidebar, () => ui.size.width(), () => rail.size.width())
Mirror.place(edge, () => ui.size.width(), () => rail.size.width() + shown() - edge.size.width() / 2.0)
Mirror.place(main, () => ui.size.width(), () => rail.size.width() + shown())
main.size.width := ui.size.width() - rail.size.width() - shown()
```

### StackedPanes (`everyui.split`)

Panes one over another, each under a header that folds it away, as an editor's sidebar stacks its explorer's sections
(Folders over Conversations). A `StackedPane` is a title, an optional `icon`, header `actions` (`RichAction`s, as a
`RichList`'s) and its content; `collapsed` and `weight` are Vars the app keeps and saves. The open panes share the
height the headers leave by weight, none shorter than its `minHeight` (60 unless set) while there is room; the one left
open has it all; with every pane folded the headers stack at the top. A handle sits under each open pane that has an
open one somewhere below it, and drags the room between the two, kept to both minimums: what it leaves is written
back to the weights (each open pane's set to its share, their total kept), so a folded pane opens again at its own
weight. Folding and opening glide (`glide`, 160 ms; 0 for none), at once at `Quality`'s Minimal.

```scala
import everyui.split.{StackedPane, StackedPanes}
import everyui.list.RichAction

val folders = new StackedPane("Folders", fileTree, collapsed = kept.foldersClosed, weight = kept.foldersShare)
folders.icon @= Some(MaterialIcon.Folder)
folders.actions @= List(RichAction(MaterialIcon.CreateNewFolder, "New folder", () => newFolder()),
  RichAction(MaterialIcon.UnfoldLess, "Collapse folders", () => fileTree.expanded @= Set.empty))
val chats = new StackedPane("Conversations", conversations, collapsed = kept.chatsClosed, weight = kept.chatsShare)
chats.icon @= Some(MaterialIcon.Forum)
chats.actions @= List(RichAction(MaterialIcon.Add, "New conversation", () => startChat(), shown = true))
chats.minHeight @= 120.0
val sidebar = new StackedPanes(Vector(folders, chats))
sidebar.size.width @= 280.0
sidebar.size.height := ui.size.height()
```

- A click or a tap on a header folds the pane or opens it; so do Enter and Space with the header focused. A header
  reads as a button with its open state (`aria-expanded` on the web), and the content as a region named by its title.
- Actions sit at the header's end, the title cut short of their room: each shows while the pointer is over the header
  or the focus is in it, always where it says `shown`, and always on a touch screen. A click on one runs it and folds
  nothing. Right (Left, right to left) from a header goes onto them one by one, Enter runs one, Left goes back.
- Down from a header goes into its pane's content where it is open and holds something to focus, else to the next
  header; Up goes to the content of the pane above (where the focus last was there) or its header. Out of a pane's
  content, Up goes to its header and Down to the next. Past the first and the last header the arrows are left to what
  the stack is in (`exitFocus` on it). A pane folded with the focus in it hands the focus to its header.
- On a touch screen the headers are 44 tall and a handle reaches 17 into the header under it for a finger (a tap there
  still folds that header); with a mouse it is 6 across and shows the row-resize cursor. Right to left, the chevron,
  icon and title start from the right and the actions sit on the left.

### Menu (`everyui.menu`)

A list of choices: a context menu at the pointer (`Menu.at`), a menu dropped from a button (`Menu.below`), or a panel
in the middle of the screen under a title (`Menu.show`, what a remote wants). A `MenuItem` has a label, an action, an
optional icon, a line of detail, a shortcut hint (a keymap `Chord`) or a note, and may be disabled, dangerous,
checkable or a submenu; `MenuItem.separator` is a rule. Up and Down move round the entries that can be chosen, OK or a
click chooses (every menu closes, then the action runs), Right opens a submenu (so does the pointer resting on its
entry for `Menu.hoverDelay`) and Left, Back or Escape closes it, and the pointer resting on an entry focuses it; over
the menu under an open submenu, another entry switches submenus and a plain one closes it, and a click outside every
menu closes them all. The focus goes back where it was. In a right-to-left language submenus open to the left, and Left and Right swap.
`Menu.context` wires a component's secondary click (`event.pointer.menu`, the new `PointerKind.Menu`; a long press on
Android), the menu key and OK held on a remote.

```scala
import everyui.menu.{Menu, MenuItem}

Menu.context(fileRow) {
  List(
    MenuItem("Open", () => open(file)),
    MenuItem.command(Keymap.global("file.rename").get),      // its label and its chord as the hint
    MenuItem.separator,
    MenuItem.sub("Copy", List(MenuItem("Path", () => copy(file.path)), MenuItem("Name", () => copy(file.name)))),
    MenuItem.check("Pinned", file.pinned, on => pin(file, on)),
    MenuItem("Delete", () => delete(file), MaterialIcon.Delete, danger = true))
}
moreButton.event.click.attach(_ => Menu.below(moreButton, items))
Menu.show(items, "Sort by")          // a television's menu
Menu.textSize @= 18.0                // set once, larger on a television
```

`Modal.show` and `Modal.over` take `dim = false` (leave what is under the dialog as it is) and `centre = false` (the
caller places the panel), which is how menus sit where they were opened. A panel is taken apart a turn after it closes,
as one made for its showing; `keep = true` leaves it whole, to be put up again.

### Dialogs (`everyui.dialog`)

Each helper puts its dialog up at once and answers as a `Task`, which completes once the dialog has closed and the
focus is back where it was. Back, Escape or a click outside gives the answer that changes nothing. The labels are the
app's; left out, they are EveryUI's own (`everyui.i18n.Words`).

```scala
import everyui.dialog.{DialogAction, DialogPanel, Dialogs, SaveChoice}

Dialogs.confirm("Delete notes.txt?", "It cannot be undone.", Words.delete, danger = true)   // opens on Cancel
  .map(yes => if (yes) delete()).start()

Dialogs.saveChanges("Save notes.txt?", "Your changes are lost if you don't save them.").map {
  case SaveChoice.Save => save()
  case SaveChoice.Discard => closeTab()
  case SaveChoice.Cancel => ()
}.start()

Dialogs.choose("Line endings", "", List("LF" -> "\n", "CRLF" -> "\r\n"), dismissed = current)   // any choice, as a T

// checked when OK or Enter is pressed: OK spins while `validate` works, and what it says shows under the field
Dialogs.prompt("Rename", file.name, label = "Name",
  validate = name => api.exists(name).map(taken => if (taken) Some("Already taken") else None))
  .map(_.foreach(rename)).start()

// any body, scrolling when it is taller than the screen, with async buttons (a spinner, a failure toasted)
Dialogs.show("Publish", List(summary), close => List(
  DialogAction(Words.cancel, close),
  DialogAction.async("Publish", default = true) { done =>
    api.publish().map(ok => if (ok) { close(); done(Right(())) } else done(Left("Publishing failed"))).start()
  })).start()
```

`DialogPanel` is the dialog those are made of, for a body the app builds (`new DialogPanel(title, body, actions).open()`).
A body of words taller than its room takes the focus, and Up and Down read through it with a remote. `DialogPanel.textSize`
and `Menu.textSize` are set once, larger, on a television.

`Words` holds OK, Cancel, Close, Done, Yes, No, Save, Don't save, Delete, Try again, Search, No results, Loading and
Back in English, German, Spanish, French, Italian, Dutch, Portuguese, Turkish, Arabic and Simplified and Traditional
Chinese, following `Strings.language`. `Words.add(code, messages)` adds a language or the app's own wording (messages by
key, as `Arb.parse` reads an ARB).

### Tooltip (`everyui.tooltip`)

A tip beside a component while the pointer rests on it or the focus is on it: words, or a component the app makes. It
comes after `Tooltip.delay` (0.6 s), goes as the pointer leaves or presses or the focus moves on, sits under the
component (over it where under has no room) and never off the screen. One shows at a time.

```scala
import everyui.tooltip.Tooltip

Tooltip(saveButton, "Save (" + Chord("S", primary = true).label + ")")
Tooltip.rich(fileTab)(() => new FilePreview(file))     // made afresh each time it shows
```

On a platform driven by a remote (`Platform.remoteNavigation()`), tips do not follow the focus unless the app sets
`Tooltip.onFocus @= true`: the focus moves with every press there, and a tip would cover the next thing along. A
plain tip drawn by the platform itself is still `Component.tooltip`.

What owns a component that is not its own pointer's or focus's (a list's row, under its cursor) asks for its tip
itself: `Tooltip.askFor(row, () => words)`, the words read as it shows and none shown where they are None by then, and
`Tooltip.hideFor(row)`. A list's rows have theirs from `Decoration.tooltip` (Tree).

### FilterPicker (`everyui.picker`)

A field over the list of what matches it: go to a file, a symbol, a command. Each change to the words asks the app's
`query` once the typing pauses (`debounce`, 0.12 s), and an answer for words that have since changed is dropped. The
list is an `everyui.list.VirtualList`, so it makes only the rows that show, however many matches there are. Up, Down
and the page keys move through the matches from the field, Enter or a click chooses (the picker closes and `chosen`
hears it), and Escape, Back or a click outside closes it.

```scala
import everyui.picker.{FilterPicker, HighlightRow, Match}

FilterPicker.show[FileEntry](q => index.search(q), _.path, _.name, _.folder, placeholder = "Go to file") { f =>
  open(f)
}
```

By default a row is the item's label with what the query matched lit (`Match.spans(query, text)`: the query's letters in
order, as early as they fall) and its detail after it, muted. An app with its own matching gives its own rows, handed
the words in the field:

```scala
new FilterPicker[Symbol](q => symbols.find(q), _.id, _.name,
  row = Some(words => new HighlightRow[Symbol](_.name, s => s.matched, _.kind))).open()
```

Segment chips narrow the matches to one group, on top of what the query found: a model picker by its provider, say.
The app gives the segments (`Segment.all(label)` shows every match) and `groupOf`, the key of an item's group. The
first segment is chosen to begin with, and `segment` holds the key of the one chosen (`shown` is what the list shows).
The chips wrap to another row where they are many. A click or a tap chooses a chip; from the field, Tab and Shift+Tab
go round the segments while the focus stays in the field; on a remote the chips are a stop between the field and the
list, Left and Right along them and OK choosing.

```scala
import everyui.picker.{Segment, SegmentIcon}
import everyui.font.MaterialIcon

new FilterPicker[Model](q => models.find(q), _.id, _.name, _.providerName,
  segments = List(Segment.all("All"),
    Segment("local", "This machine", Some(SegmentIcon(MaterialIcon.Computer))),
    Segment("acme", "Acme", Some(SegmentIcon.Url("/providers/acme.svg"))),
    Segment("shapes", "Shapes", Some(SegmentIcon.Path("M12 2 2 22h20Z")))),
  groupOf = _.provider).open()
```

A segment may have an icon before its words (`Segment(key, label, icon)`, None by default): a provider's logo, a kind
of file, a machine. It is drawn in the chip's colour for its words, whatever colours the icon has of its own, so it
follows the theme, dark or light, the chip chosen and the focus. It is 14 pixels square (its ink, which a 24 box
leaves a margin round, about the height of the words' capitals and a little more), centred in the chip, 6 pixels
before the words, at the start of the chip and so on its right in a right-to-left language; the chip is that 20
pixels wider and no taller. A chip without an icon is exactly as it was. `SegmentIcon` is one of:

- `SegmentIcon(icon)` (`SegmentIcon.Material`): a glyph of the bundled Material Icons, shown wherever fonts are,
  the Roku and the Apple TV too.
- `SegmentIcon.Path(d, width = 24, height = 24, left = 0, top = 0, evenOdd = false)`: SVG path data (a `<path>`'s
  `d`, every command, arcs and all) in its viewBox, filled; `evenOdd` for SVG's `fill-rule="evenodd"`.
- `SegmentIcon.Url(url)`: a monochrome SVG file, read once (`everyui.draw.VectorIcon.load`) and kept for every chip
  that shows it; the chip shows its words at once, the icon's room kept, and the icon once the file has come. On the web
  the page fetches it: from its own origin (`/providers/acme.svg`, or relative to the page), or from another only if
  that origin allows it (CORS), since the page reads the file to draw it; a file the browser may not read, or that is
  missing, leaves the room empty, and the console says `VectorIcon: no SVG could be read from …`. On the desktop and
  Android it is an http(s) URL, or a path of the app's own assets (`Platform.resolvePath`).

What of an SVG file is drawn (`everyui.draw.VectorArt.parse`): its viewBox (or width and height), and each `<path>`,
`<circle>`, `<ellipse>`, `<rect>`, `<line>`, `<polygon>` and `<polyline>`, filled unless its fill is `none` and
stroked where it has a stroke, by the `fill-rule` and `stroke-width` it has or takes from the `<svg>` and `<g>` round it
(attributes or `style`). Anything in `<defs>`, a clip, a mask or a `<title>` is not drawn. A `transform`, `<use>`,
gradients and `<text>` are not followed, so an icon with them should be flattened to plain paths first; one-colour icon
sets (simple-icons, Material's SVGs, Lucide) are drawn as they are. Path icons are drawn as vectors on the web (inline
SVG), the desktop (JavaFX shapes) and Android (a canvas), crisp at any scale. A Roku and an Apple TV draw no paths:
there a chip with a `Path` or `Url` icon shows its words alone, as one without an icon does.

### Markdown (`everyui.markdown`)

`MarkdownView` draws Markdown with EveryUI's own components, so it looks the same on the web, the desktop and Android:
CommonMark (headings, paragraphs, emphasis, code, links, images, quotes, lists, rules, hard breaks, escapes and
entities) with GFM's tables, strikethrough, task lists and bare links. Raw HTML shows as the text it is. It lays out to
the width it is given and is as tall as its blocks, so it fits a chat's bubble or a measured list row
(`VirtualList(measuredRows = true)`).

```scala
import everyui.markdown.{MarkdownStyle, MarkdownView}

val answer = new MarkdownView(style = MarkdownStyle.compact, onLink = url => openInBrowser(url))
answer.size.width := bubble.size.width() - 24.0
answer.content := message().text          // may grow token by token while the answer streams
```

- `content` can change at any time. As it grows, only the last block is parsed again (`MarkdownStream`), the blocks
  before it keep their components, and a paragraph or a code block that grows is drawn again in place: a 10 KB answer
  appended 16 characters at a time costs about 0.7 ms an append on the desktop, and 14 us of that is parsing. A fence
  that has not closed yet shows as a code block so far.
- Code blocks are coloured for the fence's language (`everyui.kit.Highlight`, which `CodeView(text, points, language)`
  uses too), scroll sideways rather than wrap, and have a copy button where the platform has a clipboard. Each takes
  the focus only when there is somewhere to scroll.
- A table's columns share the width given as a browser's automatic table layout shares it (`TableWidths`): where every
  cell fits on one line each column is as wide as its widest cell (a wide screen's table, as before); where not, the
  cells' words wrap, each column between its widest word and its widest cell, a column of long phrases giving up more
  than one of short words, and no word is cut. Only where the widest words alone are wider than the room (long code,
  a path, a URL) does the table scroll sideways, each column then at least its even share of the room so its words
  are not left a word a line, with a shade over each edge it goes on past, gone at that end.
- Every table sorts, filters, copies and exports as a `DataTable` does (below) where the platform has one (the web, the
  desktop, Android), with no change in the app: it rests as the plain table, laid out alike, and becomes a `DataTable`
  as the pointer comes over it (not while a drag is selecting words across it) or the focus reaches it, so a chat of
  answers with tables opens as fast as it did. A table of more than 8 rows, whose filter shows at rest, and every table
  on a touch screen, whose tools always show, is one from the start. As an answer streams in, its rows are taken in
  place: those that did not change keep what draws them, and its sort and filter stay. `richTables` (a `Var[Boolean]`,
  true unless `MarkdownView.richTables` says otherwise) draws a view's tables plain; a Roku and an Apple TV always do.
- A click on a link, or Enter (a remote's OK) on a paragraph that holds links, goes to `onLink`: by default a path of
  this app is shown in place and anything else opened (`Link.follow`). A paragraph with several links offers them in a
  menu. A picture's relative URL is found against `base`, `Platform.origin()` by default (`MarkdownView.resolve`).
- `MarkdownStyle` sets the sizes: the body's points, code's, the headings' scale, the gap between blocks, the list
  indent, a table cell's padding and the tallest picture. The colours are the theme's; a palette's `extras` can name
  "markdown.link", "markdown.code" and "markdown.quote", and "code.keyword", "code.string", "code.comment",
  "code.number" and "code.type" for the code.
- Headings, lists and their items, tables, rows and cells, and pictures carry their `Semantics`; a picture's alt text
  is its label. On the web the text can be selected.
- `direction` is which way its words run: `TextDirection.Auto` for words the app did not write turns each paragraph,
  list, quote and table by its own letters, and puts a list's markers and a quote's bar on the side its words start
  from. Code reads left to right and scrolls from the left in every language.
- With `measures = true`, `natural` (a `Val[Option[Double]]`) is how wide it would be drawn on its own: its widest line,
  or `None` where a block takes all the width it is given (code, a table, a rule, a picture). Each paragraph measures
  its lines again on one line, out of sight, for it. With `breaks = true`, a line break in the text is a line break
  where Markdown would join the lines, as a chat's messages have it.
- `Markdown.parse(text)` gives the blocks (`MdBlock`, `MdInline`) for an app that draws them its own way.

A Roku and an Apple TV draw a run's words in the paragraph's own style (no bold, no colours in a line), and pictures,
tables, lists and code blocks as everywhere else. The desktop draws a paragraph's runs in a `TextFlow` of their own
styles (`Nodes.richText`); JavaFX paints no background behind a run, so code in a line is told by its face.

### Drawing (`everyui.draw`)

`Drawing` is a plain component that draws paths in its own pixels: lines, cubic curves, arcs and circles, rectangles
and filled shapes, each a `Figure(path, fill, stroke, width)`. Bind `figures` to what they depend on (the item of a
list row or table cell, the theme's colours, the drawing's size) and it draws again only when that changes, so it is
cheap in rows that are recycled as they scroll.

```scala
import everyui.draw.{Drawing, Figure, Path, Sparkline}

// a commit graph's lanes, per row, from the row's commit
val lanes = new Drawing
lanes.size.width @= 72.0
lanes.size.height := row.size.height()
lanes.figures := row.item().toList.flatMap { c =>
  c.lanes.map(l => Figure(Path.line(l.x, 0, l.x, lanes.size.height()), stroke = Some(l.color), width = 2)) :+
    Figure(Path.circle(c.x, lanes.size.height() / 2, 4), fill = Some(Theme.accent()))
}

Path.empty.moveTo(0, 20).cubicTo(10, 20, 10, 4, 20, 4).arc(24, 4, 4, math.Pi, 2 * math.Pi)
val trend = Sparkline(stats().map(_.count.toDouble), area = true)   // a line of values across its box
```

The web draws it as inline SVG (the elements kept and only their attributes changed), the desktop as JavaFX shapes
and Android on a canvas of its own. A Roku and an Apple TV draw no paths: they show what rectangles can
(`Block.of`): straight strokes across or down, like a commit graph's lanes, and filled rectangles. Diagonals, curves
and circles are left out there.

### ZoomCanvas (`everyui.zoom`)

`ZoomCanvas` is a surface that pans and zooms over a world of items: a map, a diagram, a project seen from its sections
down to its symbols. Each `ZoomItem` is a box and a `make` for its component. Only what is in view, and wide enough on
the screen, is made and drawn; the rest costs a box in a list, so a world of thousands moves as a screenful does. What
leaves the view waits (`keep`, 200) and is taken apart after that.

```scala
import everyui.zoom.{ZoomCanvas, ZoomItem}

val map = new ZoomCanvas
map.size.width := panel.size.width()
map.size.height := panel.size.height()
map.items @= sections.map { s =>
  ZoomItem(s.key, s.x, s.y, s.width, s.height, () => SectionBox(s), childrenFrom = 360.0,
    children = () => s.modules.map(m => ZoomItem(m.key, m.x, m.y, m.w, m.h, () => ModuleCard(m), scales = false)))
}
map.clicked.attach(p => p.hit.foreach(h => map.zoomToItem(h.path*)))
map.zoomToItem("core", "network")   // glides there; fit() fits everything, zoomTo(rect) any box
```

- **Semantic zoom.** A child's box is in its parent's, 0 to 1 across and down, however deep. `childrenFrom` makes the
  children once the parent is that many pixels wide on the screen, and `from` and `until` show an item only between
  two widths, so a summary can give way to its detail.
- **Scaled or laid out.** By default (`scales`) a component is part of the picture, laid out in world units (or at
  `design`, scaled to fit its box) and drawn larger and smaller with the view. With `scales = false` it is laid out
  again at its box's size on the screen as the view moves, so its words stay their own size at any depth. Those are
  drawn over the scaled ones.
- **The view.** `view` is the world's point at the top left and the scale, within `minScale` and `maxScale`.
  `toWorld` and `toScreen` convert. `moveTo`, `zoomTo`, `zoomToItem` and `fit` glide there (`glide` seconds) on the
  app's frame loop, a screen manager's. `autoFit` fits everything the first time there is a size and something to show.
- **Hits.** `hit(x, y)` (or `hitAt(event)`) is the topmost item there that `hits`, with its path of keys, its component
  and the point in the component's own units. `ZoomHit.figure(figures)` picks a drawing's figure under it, within a few
  pixels of the screen however far the view is zoomed, so a drawing's shapes can be clicked. `everyui.draw.Hit` does the
  same for any drawing: `figureAt`, `inside`, `distance`, `bounds`.
- **Input.** A drag or one finger pans, and so does the wheel. The wheel with Ctrl (Command on a Mac), a trackpad's
  pinch, or two fingers zoom about where they are (`ZoomCanvas.pinches` says whether a pinch can come here). With the
  focus, the arrows pan while there is more of the world that way (then the arrow leaves, as the focus layer says),
  `+` and `-` zoom, and `0` fits. A click that did not move is `clicked`; `hovered` follows the pointer. The
  canvas takes the pointer itself: an item's component hears its own clicks on the web and the desktop but not on
  Android, so what must work everywhere goes by `clicked` and `hit`.
- **Grabbing.** `grab` says what a press takes hold of in place of a pan: given the world's point, it answers a
  `ZoomGrab(move, drop)`, whose `move` hears the pointer's world point as it drags and `drop` when it lets go. A press
  that does not move is still a click. A graph's nodes are dragged this way.
- **Built on.** `layer` is what a canvas built on this one draws itself, over the items and placed in the canvas's
  pixels as the view moves (a map's tiles, shapes and markers); `extent` is the world the arrows pan across and `fit`
  shows where that is not the items' box; `pressing` says whether a drag or a pinch is under way.
- **Roku and Apple TV.** The remote pans with the arrows, zooms with Fast Forward and Rewind and fits with Replay. OK
  clicks what is in the middle of the view, which a small cross marks while the canvas has the focus. Drawings there
  show what rectangles can (`Block.of`).

`Path.dashed(on, off)` cuts a path into dashes, which every backend draws the same way, and `Path.roundRect` is a
rectangle with rounded corners.

### GraphView (`everyui.graph`)

`GraphView[N, E]` draws a directed graph on a `ZoomCanvas`, laid out in layers in shared code (`GraphLayout`, no JS
library): cycles broken, layers by the longest path, crossings taken out by barycentre sweeps and swaps, and places
fitted to the neighbours' medians, with long edges kept straight. Its nodes and edges are the app's `Var[Vector]`s,
and it is laid out again when they change (once for everything changed in a turn), the nodes gliding to their new
places where there are up to 300.

```scala
import everyui.graph.{EdgeLook, GraphFlow, GraphView, NodeLook}

val graph = new GraphView[Module, Dependency](modules, dependencies, _.name, _.name, _.from, _.to)
graph.detailOf @= (m => Some(m.files.toString + " files"))
graph.nodeLook @= (m => NodeLook(accent = Some(groupColour(m)), dashed = m.planned))
graph.edgeLook @= (d => if (d.violation) EdgeLook(color = Some(Theme.danger()), width = 2.5) else EdgeLook(dashed = d.planned))
graph.flow @= GraphFlow.TopToBottom            // LeftToRight by default
graph.activated.attach(m => open(m))           // clicked twice, or Enter
graph.selected.attach(k => showDetails(k))     // the chosen node's key
```

- **Nodes.** A key, a label and an optional `detailOf` line, in a box of the graph's own sized to its words
  (`GraphNode`), or the app's `renderer` at the size `sizeOf` gives. `NodeLook` gives an accent colour (a bar and an
  edge) or a dashed outline. Far out, a layer's nodes are outlines in one drawing until the narrowest is `detailFrom`
  (40) pixels wide, so a graph of thousands fits in quickly.
- **Edges.** Curves between the layers with arrowheads, drawn with `Drawing`s, a drawing for the edges leaving each
  layer. `EdgeLook` gives a colour, a dash and a width. An edge the layout turned round to break a cycle is still drawn
  from its source; one from a node to itself is a loop. A Roku and an Apple TV draw them as lines across and down
  (`curved`), which rectangles can show.
- **Events.** `nodeClicked`, `edgeClicked` (`edgeAt` finds one near a point), `activated`, `selected` and
  `selectedEdge`. The node under the pointer, else the chosen one, is `lit` with its neighbours in and out and the
  edges between them, and the rest fades. `neighbours(key)` answers them, `reveal(key)` brings a node into view, and
  `plan` is the layout (every box and route) with `layoutMillis`.
- **Keys.** With the focus, the arrows move among the nodes: along the flow an arrow follows an edge to the neighbour
  most in line, or goes to the nearest node in the next layer that way; across it, to the next node in the layer.
  This is the graph's own navigation, as a list moves its cursor; the app's focus is never handed on by position, and
  an arrow with no node its way leaves the graph as the focus layer says. Enter activates, Escape lets go, the view
  follows the chosen node, and a screen reader hears its name. The canvas's keys (+, -, 0; Fast Forward, Rewind and
  Replay on a remote) still zoom.
- **Semantics.** The graph is an `application` named by `description`, each node a button named by its label and
  detail.

Layout time on the JVM (random graphs of 1.5 edges a node): 50 nodes about 5 ms, 500 about 40 ms, 2,000 about 0.5 s.
In Chromium the demo's 2,000 modules lay out in about 0.3 s and show in under a second, and a pan step costs about
1 ms.

`mode @= GraphMode.Force` lays the graph out by forces instead (`ForceLayout`, below): linked nodes pulled together,
the rest pushed apart, none overlapping. It moves a step a frame and stops, frames and all, when it settles; the view
follows it as it first spreads out, until the app or the user moves the view. A change to the lists keeps each node
where it was and starts a new one beside its neighbours. A node can be dragged (pinned while it is), `force` is the
layout itself (`pin`, `reheat`, `settle`), `forceSettings` how it moves (`GraphView.forces` by default, for boxes with
words), `weightOf` how hard a node pushes, and `strengthOf` and `lengthOf` how hard and how far an edge pulls.
`forceStepped` fires after each step is drawn. Edges are straight, and `directed @= false` draws them without
arrowheads, for ties both ways; the arrows move to the neighbour, else the nearest node, most the arrow's way.

```scala
val graph = new GraphView[Word, Pair](words, pairs, _.text, _.text, _.a, _.b)
graph.mode @= GraphMode.Force
graph.directed @= false
graph.weightOf @= (w => 0.6 + w.count / 20.0)
graph.strengthOf @= (p => p.together / most)
```

### ForceLayout (`everyui.graph`)

A force-directed layout in plain shared code, positions only, for an app that draws its own nodes (on a `ZoomCanvas`,
say) or for `GraphView`'s force mode. Every node pushes the others away, each edge is a spring, a little gravity
holds it together and no two nodes overlap. It settles and stops by itself: each `tick` is hot at first and cooler
after (`alpha`), and once it is cool enough it is `settled` (about 300 ticks). As d3-force does it; the pushes of a
few thousand nodes are worked out over a quadtree (Barnes and Hut).

```scala
import everyui.graph.{ForceEdge, ForceLayout, ForceNode, ForceSettings}
import everyui.zoom.{ZoomGrab, ZoomItem}

val layout = new ForceLayout(ForceSettings(linkLength = 40.0))
layout.update(people.map(p => ForceNode.circle(p.id, radius = 6.0 + p.links, weight = 1.0 + p.links / 10.0)),
  ties.map(t => ForceEdge(t.a, t.b, strength = t.weight)))
layout.animate(() => canvas.items @= draw(layout))   // a tick a frame while it is warm; frames stop when it settles
canvas.grab = (x, y) => layout.nodeAt(x, y).map(k => ZoomGrab((mx, my) => layout.drag(k, mx, my), () => layout.drop(k)))
canvas.clicked.attach(p => layout.nodeAt(p.x, p.y).foreach(open))
```

- **Nodes and edges.** `ForceNode(key, width, height, weight, round)`: `ForceNode.circle(key, radius, weight)` or
  `ForceNode.box(key, width, height, weight)` (a label, kept clear of as a box). A greater `weight` pushes harder.
  `ForceEdge(from, to, strength, length)`: `strength` 1 by default, `length` edge to edge (the settings'
  `linkLength` where it is None). An edge to a key the layout does not have is left out.
- **Changes.** `update` again with the new lists: every node keeps its place by its key, a new one starts beside its
  neighbours already placed (the first layout too, breadth first, so each part of the graph starts together), and it
  warms only to `reheat` (0.1), so an added node settles in and the rest barely move.
- **The app's hand.** `reheat(alpha)` warms it again; `pin(key, x, y)` holds a node, `unpin` lets it go; `place`
  puts one somewhere and lets the forces move it on; `drag(key, x, y)` pins it where the pointer is and keeps the rest
  warm to follow, and `drop(key)` lets it go (still pinned if it was before).
- **Running it.** `tick(n)` steps it, `settle()` runs it to the end at once (a test, a first look), and
  `animate(onTick)` runs it a tick a frame on the app's frames, telling `onTick` after each, for as long as it is warm:
  the frame loop idles when it settles (When frames run) and wakes when it is warmed again. `stop` ends that.
- **Out.** `x(i)`, `y(i)`, `positionOf(key)` and `positions` (each node's centre), `boxOf(i)`, `bounds`, and
  `nodeAt(x, y)` for a pick.
- **Settings.** `ForceSettings`: `charge` (30 for a weight of 1), `theta` and `distanceMax` for the far pushes,
  `linkLength` and `linkStrength`, `gravity`, `centred`, `collide` and `collideIterations` with `padding`, `flow`
  (a directed graph's edges lean that way; None for an undirected one) and `flowStrength`, `alphaDecay`, `alphaMin`,
  `reheat`, `velocityDecay`, and `seed`: the same nodes, edges and seed lay out the same way every time, and the web
  and the desktop lay the demo out alike.

Settling from the start (300 ticks), co-occurrence graphs of about two edges a node: 100 nodes 20 ms (0.07 ms a tick),
1,000 nodes 0.3 to 0.5 s (1 to 1.7 ms a tick), 3,000 nodes 1.2 to 1.6 s (4 to 5 ms a tick) on the JVM; in Chromium
0.2, 3.6 and 13 ms a tick. `demo=forcegraph` shows 240 words, a frame a tick at about 57 frames a second in headless
Chromium, and asks for no frames once it settles.

### MapView (`everyui.map`)

`MapView` is a slippy map, as OpenStreetMap's is, in shared code over a `ZoomCanvas` (no JS map library): tiles from a
tile server, polygons and lines in latitude and longitude over them, markers that keep their own size at every zoom,
gathered into clusters where they crowd, all in Web Mercator at zooms 0 (the whole Earth in one 256-pixel tile) to 19.

```scala
import everyui.map.*
import everyui.map.MapFeature.{ClusterFeature, MarkerFeature, ShapeFeature}

val map = new MapView
map.size.width := panel.size.width()
map.size.height @= 420.0
map.tiles @= Some(TileSource.openStreetMap)            // or TileSource.template(url, attribution, subdomains)
map.shapes @= parcels.map(p => MapShape.polygon(p.id, p.ring,
  MapStyle(fill = Some(teal.withAlpha(0.35)), stroke = Some(teal), width = 1.5), label = p.name))
map.markers @= wells.map(w => MapMarker.pin(w.id, w.at, label = w.name))
map.cluster @= Some(MapCluster())
map.legend @= Some(new OwnersKey)
map.clicked.attach(c => c.feature match {
  case Some(ShapeFeature(s)) => open(s.key)
  case Some(MarkerFeature(m)) => show(m.key)
  case _ => ()                                           // a cluster glides in by itself
})
LatLngBounds.around(parcels.flatMap(_.ring)).foreach(b => map.fitBounds(b, padding = 32.0))
```

- **Where it looks.** `setView(center, zoom, animate)`, `fitBounds(bounds, padding, animate)` (as close as `maxZoom`
  allows; asked before the map has a size, it is done once it has one) and `zoomBy(levels)`, within `minZoom` and
  `maxZoom` (0 and 19). `center`, `zoom` and `bounds` are `Val`s that follow the view. `toScreen(latLng)` and
  `toLatLng(x, y)` convert with the map's own pixels; `WebMercator` has the projection itself (`toWorld`, `toLatLng`,
  `tileAt`, `tileBounds`, `tilesIn`, `metersPerPixel`, `zoomToFit`). `LatLng(lat, lng)`; GeoJSON writes `[lng, lat]`.
- **Moving it.** A drag or a finger pans, and so does the wheel; Ctrl (Command) and the wheel, a trackpad's pinch, two
  fingers and the + and - buttons (`zoomButtons`) zoom about where they are. A press gives it the keys: the arrows
  pan, + and - zoom; on a remote Fast Forward and Rewind zoom and OK clicks the middle of the view. `map.canvas` is
  the `ZoomCanvas` underneath: the thing to focus.
- **Tiles.** `TileSource(address, attribution, minZoom, maxZoom, retina, link)`: `address(tile, sharp)` is where a
  tile's picture is, so a test (or an app that makes its pictures) gives a function; `TileSource.template` fills
  `{z}`, `{x}`, `{y}`, `{-y}` (TMS), `{s}` (a subdomain, the same for a tile every time) and `{r}` (`@2x` on a screen
  sharper than a pixel a point, and the source is then `retina`). Only the tiles of the nearest whole zoom that cover
  the view and `tileMargin` (128 px) round it are asked for, nearest the middle first, `tileLoads` (6) at a time; one
  that leaves the view before it was asked for is never asked for; one not yet come shows the nearest tile above it
  drawn larger (or those below it), so a zoom never blanks; `tileCache` (256) that came are kept, the longest unused
  let go first. Past the source's `maxZoom` its closest tiles are drawn larger.
- **Credit and scale.** The source's `attribution` is always shown in the bottom right (a link where it has one);
  OpenStreetMap's licence requires it. The scale bar (`scaleBar`) shows a round distance at the middle of the view, in
  metres or, with `imperial`, feet and miles.
- **Shapes.** `MapShape(key, rings, closed, style, label, hits, from, until)`, or `MapShape.polygon(key, ring, style)`
  and `MapShape.line(key, points, style)`. A polygon's rings fill by the nonzero rule, so a ring wound the other way (a
  GeoJSON hole) is a hole. `MapStyle(fill, stroke, width, opacity, dash)`: the width in pixels of the screen at every
  zoom, `dash = Some(MapDash(on, off))`. They are drawn as one `Drawing` in screen pixels for the view and half a view
  round it, each ring thinned to points a pixel apart and cut to that frame, so a county outline at a state's zoom is a
  handful of points. `from` and `until` show a shape only between two zooms (a section grid close in).
- **Markers.** `MapMarker(key, at, make, anchor, label, hits, from, until, clusters)`: `make` is any component, sized by
  itself, made only as it comes into view and let go a while after it leaves (`keep`, 300); `anchor` is the point of it
  at the place (`MapAnchor.Bottom` for a pin). `MapMarker.pin`, `.dot` (a rounded box, drawn everywhere) and `.text`
  (words with a dark edge) are ready-made. `MapCluster(cell, until, least, make, zoomsIn)` gathers the markers of each
  square of `cell` pixels (fixed to the world, so a pan does not reshuffle them) below zoom `until`; a click on one
  glides in to its markers.
- **Hits.** `clicked` hears a `MapClick(at, feature, modifiers)`, and `hovered` is the feature under the pointer:
  `MarkerFeature`, `ShapeFeature` or `ClusterFeature`, each with the app's `key`. Markers are found by their box,
  polygons anywhere inside, lines within `slop` (4) pixels. A feature's `label` shows in a tip as the pointer rests on
  it (`tips`). `featureAt(x, y)` asks directly. `hits = false` leaves a feature to the eye alone.
- **Legend.** `legend` is any component, shown over the map in `legendCorner` (`MapCorner.TopLeft` by default), clear
  of the buttons, the credit and the scale bar.

Panning moves the layers, laid out for a frame of the view and half a view round it, without laying out anything
again until the view leaves that frame; a zoom lays them out for the new scale. `demo=map` (259 polygons, 5 pins, 1,000
clustered dots and 257 labels) in headless Chromium without a GPU: a drag at 60 frames a second without a dropped
frame (under 2 ms of script a move), with or without clustering and with 600 sections; a wheel zoom 5 to 15 ms of
script a step, 60 frames a second with the odd frame at 33 ms (1,000 unclustered markers zooming out: some 83 ms at
worst).

The web, the desktop and Android draw everything. A Roku and an Apple TV draw the tiles and the markers that are
components (`dot`, `text`, the app's own); of the shapes and the pin only what rectangles can (`everyui.draw.Block`);
they move by the remote. A Roku computes in single precision, so past zoom 16 what it draws may be a pixel or more
out. Tests and demos never use OpenStreetMap's own server, whose tile usage policy forbids heavy use: `demo=map` draws
its tiles in the page, and takes `tiles=<template>` for a local server's.

### SplitButton (`everyui.menu`)

A main action with an arrow beside it that drops a `Menu` of alternatives. The menu also opens on the menu key, on
Down with a keyboard, and on OK held (a remote's long press).

```scala
val commit = new SplitButton("Commit", () => commit(),
  List(MenuItem("Commit and Push…", () => commitAndPush()), MenuItem("Amend", () => amend())),
  moreLabel = "More commit actions")
```

### TextArea (`everyui.text`)

A box of lines to type in: a commit message, a note. It wraps, scrolls when its lines outgrow it, and shows its
`placeholder` while empty. `value` is what it holds, both ways. Enter starts a new line, and Primary+Enter (Ctrl, or
Cmd on a Mac) is heard on `submitted`. With `grow = true` it is as tall as its lines, from `minHeight` to `maxHeight`,
and scrolls past that.

```scala
import everyui.text.TextArea

val message = new TextArea(placeholder = "Commit message", width = 360.0, grow = true, maxHeight = 200.0)
message.value @= draft()
message.submitted.attach(text => commit(text))
```

While it is being edited, the keys it types with (Enter, the arrows, the page keys, Backspace) go on to the
platform's own field and the focus layer makes no other use of them. Tab and Escape still move the focus and go back,
and the app's commands still run first. On a television it works as `TextInput` does: the focus only lights it, and
OK brings up the platform's keyboard. A component that wraps a platform field says which keys are its typing with
`Component.typesEvent`; those keys reach its `event.key.down` and then the platform, untouched. `TextInput` now does
this too. Before, it kept Backspace, Space and the arrows from its own field while it was being edited.

### Read-only fields (`readOnly`)

`readOnly @= true` on an `InputView`, a `TextInput` or a `TextArea` shows the value as the field does, where it can be
focused, chosen and copied, but typing, deleting and pasting change nothing: a share link, a key, a message to copy.
It is not `disabled` (out of use, which a screen reader hears as such): it looks as it did, and a screen reader hears
it is read-only.

```scala
val link = new TextInput("Share link")
link.value @= shareUrl
link.readOnly @= true
```

The web sets the field's `readonly` attribute and `aria-readonly`; the desktop makes the JavaFX field not editable,
which leaves its look; Android takes the `EditText`'s key listener away and makes its words selectable, so it offers
no paste and brings up no keyboard, and TalkBack reads it as not editable. A Roku's and an Apple TV's fields are only
their words, and a read-only one asks the platform's keyboard for nothing when it is clicked.

### Literal fields (`literal`)

`literal @= true` on an `InputView`, a `TextInput`, a `TextArea` or a `SearchField` says the field holds text meant
exactly as it is typed (a path, a shell command, an identifier or slug, a pattern, a branch name, a host, a key, code),
so the platform does not check its spelling, correct it or capitalize it. It says nothing to autofill: a field can be
literal and filled (a username is both), and `autofill` stays as it was.

```scala
val folder = new TextInput("Folder")
folder.literal @= true          // /home/matt/projects/nabotv, with no red underlines
```

The web sets `spellcheck="false"`, `autocapitalize="none"` and `autocorrect="off"`, as it already did for the
username, email, url, password and one-time-code autofill tokens. Android's field is plain text with no suggestions
(`TYPE_TEXT_FLAG_NO_SUGGESTIONS`) and no capitals, rather than the visible-password variation keyboards also honour,
which would tell autofill it is a password. The desktop (JavaFX) checks no spelling to begin with, and a Roku's and
an Apple TV's text comes from the platform's own keyboard, which is left as it is.

### Italic (`font.italic`)

`font.italic @= true` slants the words of anything with a `font`: a `TextView`, a `RichText` (every run, as the
paragraph's own style; a run marked `italic` is slanted either way), an `InputView` and a `Select`. A `Button` has
`italic` for its words. The measured width is the width as drawn, so what is laid out after the words follows them.

```scala
val empty = TextView("-- No Results --", 14.0)
empty.font.italic @= true
new Button("Retry").italic @= true
```

Each platform draws the family's own italic where there is one: a face registered with `italic = true`
(`Faces.register(new Face(..., italic = true))`, its file named in `everyuiAppFonts`), or the system's italic for the
system family. Where the family has none (Open Sans, as EveryUI bundles it) the upright letters are slanted: by the
browser (CSS `font-style`), by Android (`Typeface.ITALIC`, which a TextView fakes by skewing), by UIKit (the italic
trait, else a sheared face), and on the desktop by a shear about the baseline, as JavaFX has no slant of its own; a
desktop field in such a family stays upright. A Roku cannot slant a font, so it draws the registered italic face where
there is one and the upright letters where not.

### Words cut short to fit (`maxWidth`, `maxLines`)

`maxWidth @= 104.0` draws a line no wider than that, cut short with an ellipsis where the words run past it, and
`maxLines @= 2` cuts wrapped words (`WhiteSpace.Normal` and a width) to that many lines at a word. The cut is made by
measuring the words, so it is the one they really take in their face and size, and `size.width` is the cut width:
whatever sits after them lays out against what is drawn. A text view made before it is on the screen, as a row's are,
is cut once it joins it, and again when its face or size changes after its words were set.

```scala
val name = new TextView {
  content := s"${person.name} · ${person.episodes} eps"
  font.size @= 11.0
  maxWidth @= 104.0
}
```

On the web the words are measured once they are on the page and drawn in their own face: one still loading is waited
for, and until then the page holds them to their room itself (CSS `max-width` with `text-overflow: ellipsis`, and a
`max-height` of their lines). `demo=textcut` shows rows of them made off the screen. Words not cut are measured again
once a face they are drawn in has loaded, a `TextView`'s and a `RichText`'s runs' alike (code in a line is in a face
the page loads only once it is drawn), so a paragraph, a `MarkdownView` and a chat's bubble take the lines their words
take in their own faces; `demo=bubbles` shows messages wrapping with code, bold, italic and links, and one streamed.

`elide` says where a line too wide for `maxWidth` is cut: `Elide.End` (the default, "AppConnect…"), `Elide.Start`
for a path or anything whose end says the most ("…/backend/app +39 −7"), or `Elide.Middle` ("backend/sr…/app"). The
cut is measured the same way on every backend, halving the letters kept until they fit; wrapped words cut to
`maxLines` are cut at the end. `elided` says whether the words are drawn cut short. On the web, until the words can
be measured, the page holds them to their room with an ellipsis at the end whatever `elide` says.

### Words kept to their box (`textOverflow`)

A `TextView`'s words never run past their box by default: the room what they are in gives them. That is the width of
the nearest container with a width of its own, from where the words start (their left, or their right in a
right-to-left language) to its far edge, less its padding where it lays its children out, and, in a row, less what
comes after them; a row or a column given no width, and a container whose width is bound to what it holds (a
`Button`, a `Chip`, `size.width := label.size.width() + 20.0`), is sized to the words and leaves it to the one it is
in, up to the screen. Words wider than that room are cut short with an ellipsis where they are one line (the cut
`maxWidth` makes, where `elide` says), and wrapped within it where they wrap (`WhiteSpace.Normal`, `PreWrap`,
`PreLine`; the web, the desktop and Android wrap them there, and the Roku and the Apple TV, whose labels wrap only at a
width of their own, cut them on one line).
`maxLines` and `maxWidth` still hold within the box. Words that fit are drawn exactly as before, and nothing is
measured again for them: the box is looked at once a turn is over, after what they are in has followed them.

```scala
// a panel's rows: no rowWidth to thread through, the names cut and the descriptions wrapped at the panel's edge
val panel = new Container { size.width := sidebar.size.width() }
panel.children += Column(4.0, padding = 16.0)(
  TextView("A rather long tool name that will not fit on a phone", 14.0),
  new TextView { whiteSpace @= WhiteSpace.Normal; maxLines @= 3; content @= tool.description })
```

Words cut short are read whole: a text view with no `semantics` of its own is named by its words
(`Semantics(label = content())`) while they are cut, and plain again once they fit. Some words are left whole: words
given a width of their own (`size.width`, a grid's cell) are drawn as that width has them; a glyph
(`MaterialIconView`); words in something hidden; words placed outside the box altogether, past its edge or before its
start (made out of sight to be measured, a page waiting to slide in); words along a horizontal `ScrollContainer`, or in
a `ZoomCanvas`'s world, which run on past the viewport on purpose; and words in a box that turns out to shrink with
them (bound to their width through a value of its own, which the box cannot see), until their words change. In a row
too wide for its box, the words that come first take the cut, measured against what follows them at its own whole
width, so a title gives way to the badge after it. A `RichText` paragraph wraps to the width it is given, as before.

A `Button`'s words are one line by default, cut short at its room. `labelLines` lets them wrap within it, the button
growing as tall as they are and its pill becoming a box with rounded corners, its lines starting where a paragraph's
do (`labelAlign`, `Start` by default, by the reading direction): a choice someone must read whole before picking
it (a chat's choices, a form's, a dialog's). 0 is as many lines as the words need, more than 1 at most that many with
the last cut short; words that fit on one line are drawn on one. Where the platform cuts wrapped words on one line
(the Roku, the Apple TV), so does the button. `demo=widgets` ends with a card of such choices, 360 wide.

```scala
val choice = new Button("Proceed with the cleanup (the Firebase key is already moved to `.env.firebase`)", () => pick(1))
choice.labelLines @= 0
```

`textOverflow @= TextOverflow.Visible` draws a text view's words whole past their box, as before; `Fit` (the default)
keeps them in it. For an app laid out on words running past their box, `TextOverflow.default @= TextOverflow.Visible`
before the UI is built does the same for every text view made after it.

```scala
import everyui.component.types.TextOverflow

TextOverflow.default @= TextOverflow.Visible   // at start: the old behaviour, everywhere
title.textOverflow @= TextOverflow.Visible     // or for one text view
```

### Text direction and long words (`TextView`, `RichText`)

Words the app did not write (a message, a name, a title, code) should run the way their own letters do, not the
app's: an English message in an Arabic app keeps its question mark at the end, and an Arabic one in an English app
reads right to left. `direction @= TextDirection.Auto` does that: the first letter with a direction decides, as a web
page's `dir="auto"` does. `Inherit` (the default) is the app's direction (`Direction`), and `LeftToRight` and
`RightToLeft` fix it. It turns the words and the side `align`'s start is on, never where the box sits: layouts still
mirror with the app. `wrapAnywhere @= true` breaks a word too long for its line (a URL, a path, a hash) where it must,
rather than letting it run past the box.

```scala
import everyui.component.types.TextDirection

val words = new TextView {
  whiteSpace @= WhiteSpace.PreWrap
  direction @= TextDirection.Auto
  wrapAnywhere @= true
  content := message().text
}
everyui.text.Bidi.firstStrong("صباح الخير")   // Some(true): right to left
```

The web sets `dir="auto"` and `overflow-wrap: anywhere`; Android the TextView's first-strong text direction (it breaks
long words already). JavaFX has no automatic direction, so the desktop finds the first letter itself; it also draws
words with right-to-left letters in a face that has them (Segoe UI, DejaVu Sans or Arial, the first installed), since
JavaFX puts a line in the wrong order when a fallback face draws part of it, and wraps them itself, since JavaFX 22 and
later throw when a wrap falls inside right-to-left words. The Roku and the Apple TV keep the app's direction.

A chat transcript (`everyui.chat`) shows every message's words, names, times, tool calls and notes this way. When
earlier history arrives above an entry that led its group, that entry keeps its avatar and name, though an older
message by the same author now sits straight above it, so nothing on the screen changes or moves.

A transcript shows each message as Markdown by default (`MessageMarkdown`): as wide as its widest line up to the widest
a bubble's words may be, so a short reply makes a narrow bubble, with code, a table, a rule or a picture taking all of
that; the line breaks it was typed with kept; and a streaming message parsed again from its last block alone.
`render = MessageText.plain` shows plain text instead.

A message's words (its Markdown's code blocks and tables too) can be selected and copied where the platform selects text
(the web): a drag selects them, across lines and on across messages, Ctrl or Cmd+C copies them, and a plain click still
puts the transcript's cursor on the message it lands on, without scrolling. With nothing selected, Ctrl or Cmd+C copies
the message under the cursor, as it was written, and Ctrl or Cmd+A selects every message's words. The context menu over
a message (the secondary button, the menu key, a long press, OK held) is the transcript's own: Copy (the selection,
while there is one), Copy message and Select all (the words of the entries drawn: those in view and a few either side),
or Copy message alone where nothing is selected with a pointer (the desktop, Android). It is the app's rather than the
browser's so that every platform can copy a message whole, the desktop included, which selects no text; the browser's
own still shows over a link, over an entry the menu offers nothing for (a day's separator, a tool call, the app's
component), and with Shift held. `menu` changes what it offers:

```scala
transcript.menu @= (e => transcript.menuFor(e) ++ (e match {
  case m: ChatEntry.Message => List(MenuItem.separator, MenuItem("Reply", () => replyTo(m)))
  case _ => Nil
}))
```

Every message also has a small row of buttons beside its bubble (`MessageActions`), by default Copy message (its text as
it was written, the same as the menu's) wherever there is a clipboard (`actionsFor`). They show beside the message the
pointer rests on, and go a moment after it leaves both the message and them; with the focus on the transcript, beside
the message under its cursor, which a tap puts there on a touch screen, so a phone shows them on the message tapped
until the focus goes elsewhere. They sit after everybody else's bubbles and before one's own (mirrored right to left),
level with the bubble's top, or on its top corner, over its padding, where there is no room beside it (a phone's long
messages); they stay in view while any of the bubble does. There is one row for the whole transcript, drawn over the
rows and moved to the message, so nothing moves, no row changes height, and a history of thousands costs nothing more
per row. The forward arrow (Right, Left right to left) on a message goes onto them (Tab reaches them too, in the
page's order, after any controls the rows drawn hold); an arrow out of either end, up or down, Escape or Back, back to
the message. Each is a button to a screen reader, named by its `label`, which is its tooltip as well. An action with
`done` says it once run: Copy message turns to a tick for 1.5 s, its tooltip "Copied", and a screen reader reads
"Copied" out. A Roku and an Apple TV have no clipboard, so nothing shows there and nothing is made unless the app adds
an action of its own. `messageActions` changes what they offer, per message (Nil, none):

```scala
transcript.messageActions @= {
  case m: ChatEntry.Message if !m.self => transcript.actionsFor(m) :+
    MessageAction(MaterialIcon.FormatQuote, "Quote", () => quote(m))
  case m: ChatEntry.Message => transcript.actionsFor(m) :+
    MessageAction(MaterialIcon.Refresh, "Retry", () => resend(m), done = "Sent again")
  case _ => Nil
}
```

A click on a message's avatar or its author's name, and on a worker's avatar in the status bar, is an `AuthorPressed`
(the participant, and the message's id where it was a message). `onAuthor` (on the `ChatView` or its `Transcript`) is
the app's: None by default, which shows the author's picture whole over the screen (`AuthorPressed.showPicture`: the
`ImageViewer` at the avatar's own address, closed by Escape, Back or a click outside the picture) where they have one,
and does nothing where they are only a letter. Where a press does something the avatar and the name show the hand; the
click is theirs alone, so it neither moves the transcript's cursor nor starts a selection. The avatar is not a stop for
the focus of its own (the transcript is one, its cursor on the entries); the keyboard and a remote reach the author
through the message's menu, whose About entry does the same.

Everybody else's bubble is the theme's control colour, drawn whole; `MessageView.othersOpacity` (1 by default) draws
less of it, for an app that shows a picture behind its conversations and wants it through those bubbles as through
one's own (a wash of the accent): about 0.8 keeps the words easy to read. It is the app's, every conversation following
it, the bubbles shown too; the words, and the code and tables in a message, are drawn as they are.

```scala
chat.onAuthor @= Some(p => showProfile(p.participant.id))
```

### Letter spacing (`font.letterSpacing`)

`font.letterSpacing` is the space added after each letter, in pixels (negative draws them closer; 0, the default, is
the face's own), on anything with a `font`: a `TextView`, a `RichText`, an `InputView` and a `Select`. A `Button` has
`letterSpacing` for its words, and a `TextRun` its own `letterSpacing`, where the paragraph's would be. The spaced width
is the measured one, so a label and whatever is laid out after it move over by the spacing times its letters: 13
letters at 0.5 are 6.5 wider.

EveryUI has no type scale of its own, so an app porting a Material design sets Material's spacing where it uses each
style: displayLarge -0.25, displayMedium/Small and the headlines and titleLarge 0, titleMedium 0.15, titleSmall 0.1,
bodyLarge 0.5, bodyMedium 0.25, bodySmall 0.4, labelLarge 0.1, labelMedium and labelSmall 0.5.

```scala
val title = TextView("Settings", 16.0)
title.font.weight @= FontWeight.Medium
title.font.letterSpacing @= 0.15                    // titleMedium
new Button("Continue").letterSpacing @= 0.1           // labelLarge
TextRun("NEW", letterSpacing = Some(1.5))             // one run spaced apart from its paragraph
```

The web sets CSS `letter-spacing`; Android `TextView.setLetterSpacing`, in ems, so the pixels over the size (kept as the
size changes; a run with its own size and no spacing of its own is spaced in proportion to it there); the Apple TV
UIKit's kern. JavaFX has no letter spacing at all, so the desktop draws the letters as the face sets them and takes the
spaced width for their box: everything around them sits as it does elsewhere, the letters themselves a little closer
together. A field on the desktop is not spaced. A Roku's Label spaces none, and measures unspaced.

### Busy ring (`Avatar.busy`, `everyui.kit.BusyRing`)

Somebody at work (an agent running a tool, thinking) has a ring around their avatar in the accent: `avatar.busy @= true`,
and `avatar.busyDetail @= "Running bash"` for what a pointer resting on it says and a screen reader hears ("Ada, busy,
Running bash"). Half the ring is lit and turns once a second over the rest, faint; the platform runs the turn (the
browser's `animate`, a JavaFX timeline, an Android animator), so it asks the app for no frames and a screen of rings
stays idle, and it stops the moment `busy` is false or the ring is out of sight (a list's row put away). Where people
ask for less motion (`everyui.motion.ReduceMotion`: the web's `prefers-reduced-motion`, Android's animations removed,
an Apple TV's Reduce Motion; never on the desktop or a Roku, unless the app sets `ReduceMotion.on`), and on a Roku or
an Apple TV, the whole ring is lit and stands still. The ring is made the first time an avatar is busy, and sits
outside its box by `BusyRing.outset(diameter)`, so leave that room around it. `RichLook.busy` and `TabItem.busy` draw
it on a list's rows and a tab's lead; `demo=tabs` shows both kinds of lead and the ring turning and stopped.

### Tool calls (`ChatEntry.Activity`: `lead`, `body`)

A tool call is one line in the transcript: its `summary` after a status mark (a spinner, a tick, a cross). It can say
what it is rather than show its raw input: `lead` (an `ActivityLead`) puts somebody's avatar first, in place of the
mark (a message sent to them: a ring turns around the face while the call runs, and a tick or a cross sits on the
face's corner once it has), or an icon (a search, a command: a small spinner on its corner while it runs, then the
tick or the cross). The corner is read as the status, and the line as its summary, body and status ("Message to The
Oracle: Hello…, done"). `body` is a short text under the summary, shown without opening the line (the message sent, the
command run): Markdown drawn as the transcript draws a message's words (its `render`), selectable, and cut to
`transcript.bodyLines` lines (3); one cut short opens (a click, Enter) to be read whole, details or not. Both are
reactive, like `summary` and `status`, so a body can arrive as the call settles; with neither, the line is as it was.

```scala
val call = ChatEntry.activity("c-9", "Message to The Oracle", lead = ActivityLead.Person(oracle),
  body = "Hello Oracle. Luma here. Can you help me create a new Persona?")
ChatEntry.activity("c-10", "Ran a command", ActivityStatus.Succeeded, lead = ActivityLead.Icon(MaterialIcon.Terminal),
  body = "`ls -la backend/src`")
call.status @= ActivityStatus.Failed       // the ring stops, a cross on the face's corner
```

### Threads (`ChatEntry.rail`, `Transcript.rails`)

Any entry (a message, a tool call, reasoning, a note, the app's component) can have a rail: a thin line in a colour of
the app's down its start edge (the left, the right right to left), saying which thread or topic it is in. `rail` is a
`Var[Option[Color]]`, None by default, which draws nothing; set at any time, a message's thread known only after it
arrives, and changed or taken away. The line is 3 pixels wide, 4 in from the edge, and as tall as the entry's whole row,
the space above it too, so entries one after another of the same colour draw one unbroken line, and the colour changes
where the thread does (put the app's marker there, in the new thread's colour, to say so). A day's separator between two
entries of one thread carries its line on. `railLabel` (None by default) is what a screen reader says of it, after the
entry ("Ada: Morning!, Release planning"), since a colour alone tells it nothing. Every factory takes `rail`.

`rails` (on the `Transcript`, or the `ChatView`) keeps 6 pixels of room at the start edge for the lines, every row drawn
after it, so the entries with a rail and those without line up. Set it before the conversation shows, for one with
threads; set later, the rows draw a little narrower, and the entry at the top of the view stays where it is. Off, the
default, every row is drawn exactly as it was before rails, and a rail is drawn in the 12-pixel margin the rows keep at
either side anyway, 5 pixels from an avatar. A row makes its line the first time it shows an entry with a rail, so a
transcript without them makes nothing more, and the rail of an entry never drawn is never made.

```scala
val topicColors = Vector("#e5484d", "#f76b15", "#ffb224", "#99d52a", "#30a46c", "#12a594", "#05a2c2", "#0090ff",
  "#3e63dd", "#8e4ec6", "#d6409f", "#a18072").map(Color.fromHex)
chat.rails @= true
ChatEntry.marker("t-3", "Topic: Release 2.0", rail = Some(topicColors(3 % 12)))
reply.rail @= Some(topicColors(topic.index % 12))     // once its thread is known
reply.railLabel @= Some(topic.name)
```

### Composer (`everyui.chat`)

Where a chat's next message is written. It knows nothing of where messages go: `onSend` hears each `Draft` (its
words and its `Attachment`s), and the field is empty again. The app can set or clear `text` and `attachments` itself.

```scala
import everyui.chat.{Completion, CompletionTrigger, Composer}

val composer = new Composer(draft => chat.send(draft.text, draft.attachments), onStop = () => chat.stop(),
  onAcceptSuggestion = s => chat.took(s),
  complete = Some((trigger, prefix) => people.find(prefix).map(_.map(p => Completion(p.name, "@" + p.handle, Some(p.handle))))),
  triggers = List(CompletionTrigger('@'), CompletionTrigger('/', atStart = true)),
  accept = List("image/*", ".txt"), maxSize = Some(10L * 1024L * 1024L))
composer.size.width := ui.size.width() - 32.0
composer.busy := chat.replying()            // Send becomes Stop; Enter sends nothing until the reply is done
composer.enabled := chat.connected()        // out of use: dimmed, read-only, and a screen reader hears it
composer.placeholder := (if (chat.connected()) "Message" else "Reconnecting…")
composer.suggestion := chat.nextGuess()     // Option[String]: the app's guess at the next message
composer.leading.children += modelPicker
composer.trailing.children += micButton
composer.commands(Keymap.global)            // Primary+Enter sends, Primary+. stops, from anywhere
```

- Enter sends, Shift+Enter starts a new line, Primary+Enter sends too. An input method's Enter chooses its words
  (`Platform.keyComposing`) and never sends. On a touch screen (`touch`) Enter starts a new line and the Send button
  sends. While `busy` or out of use, Enter is kept and does nothing.
- The suggestion shows in the accent colour in the empty field, in the placeholder's place. Tab or Right takes it (the
  caret after it), Escape or typing puts it away until the next one. On a touch screen or a remote it is a chip above
  the field. A screen reader is told it has come and how to take it.
- Files come from the attach button (`Files.pick`) and from a drop on the composer; each is a chip with a remove
  button. `accept`, `allow` and `maxSize` decide which are taken; the rest are refused with a toast. A picture's preview
  is let go (`Files.release`) once it leaves the draft, removed or sent: to show a sent picture, read its `bytes`.
- `pasted` decides what a paste becomes: words in the field, or a file's chip, as a long log or stack trace should be
  (`ComposerPaste.longAsFile`; see Pasting below).
- Completions list over the composer for the word at the caret after a trigger: Up and Down, Enter or Tab to put the
  lit one in place of the word, Escape to close.
- Under `compactWidth` (480) the controls are a phone's, and `leading` folds behind a "+" with the attach button, in
  a row that opens above the field. `trailing` stays beside Send.
- On a Roku or an Apple TV it is one line written on the platform's keyboard, with Send, the suggestion as a chip, and
  no files or completions.

`ComposerKeys.action` is the key rule on its own, `TriggerWord` the word a completion replaces, and `ComposerWords`
what the composer says, for a translation.

**Queued messages.** With `queueWhileBusy` on (off by default, and off the composer is exactly as it was), a message
sent while a reply is coming waits its turn instead of Enter doing nothing:

```scala
composer.queueWhileBusy @= true
composer.busy := chat.replying()
composer.queue @= saved.load()                      // List[Draft]: read it, seed it, clear it; a reload can keep it
composer.queue.attach(q => saved.store(q))
composer.queueAfterStop @= QueueAfterStop.Drain     // Pause, the default for now, or Drain
```

- While `busy`, Enter (or the Send beside Stop, which stays) puts the draft, words and files, at the end of `queue`,
  and the field is empty for the next. An empty draft queues nothing.
- The queue shows over the field (`DraftQueue`), a chip for each in the order they go: the first line of its words,
  cut short to fit, and how many files go with it. Its handle drags it into another place; Edit takes it back into the
  field; × takes it off. Past `queueMaxHeight` (136) the chips scroll. A screen reader hears "2 messages queued" as the
  count changes (in EveryUI's eleven languages, under `chat.*` keys in `Words`).
- **Edit with words in the field**: what the field held is not lost, nor sent: it takes the edited message's place in
  the queue, and the edited message, queued again, goes back where it was (before the message that followed it). Sent
  while the composer is at rest, it is sent as any message is. Emptied and written again, it goes to the end.
- **Keys**: Up at the very start of the field goes to the last chip; Up and Down go from chip to chip, and Down from
  the last (or Escape from any) back to the field. On a chip, Enter or E edits it, Delete or Backspace takes it off
  (the focus to the chip that took its place), and Alt+Up and Alt+Down move it (said: "Moved to 1 of 3").
- **Draining**, one message per reply: once `busy` falls and stays down for `queueSettle` (0.3 s; a fall that rises
  again sooner is a flicker inside one reply), the composer sends the queue's next message through `onSend`. From
  every `onSend` it counts itself busy until it has seen `busy` rise and then fall again, so it never sends two before
  the app has started the first one's reply; if `busy` does not rise within `queueStartWait` (3 s: a message to a
  person, which starts no reply), it takes the message as done. A message sent at rest counts the same, so what is
  written while its reply is starting is queued behind it, and while messages wait (`holding`) a new one goes to the
  end rather than past them.
- **Stop** with a queue pauses it by default (`queueAfterStop = QueueAfterStop.Pause`): the queue stays, its line says
  "Paused · 2 messages queued" with Resume (`resume()`), and nothing goes until Resume, or until the person sends
  another message, which goes first, the queue following it. `QueueAfterStop.Drain` lets the queue go on after Stop as
  after any reply. `queuePaused` says which, and the app may set it.
- **The paused line's actions**: after Resume come **Send next**, which sends the queue's next message only, through
  the same path a drain takes (so it is out of use while a reply, or a message the composer sent that has not yet been
  seen to start and end, is under way: never two at once), the rest staying paused; and **Clear queue**, which takes
  every message off at once and asks nothing, as the chips show what goes (EveryUI has no undo for a toast to offer;
  an app that wants one can keep `queue()` before and set it back). Then the app's own, `queueActions`:

  ```scala
  composer.queueActions @= List(
    QueueAction("Stop running commands", () => tools.stopAll(), Some(MaterialIcon.StopCircle)))
  ```

  Where they do not all fit beside the line's words (a phone), Resume stays and the rest go, in their order, into a ⋮
  menu at its end, never wrapped or cut. Up from the first chip goes to Resume, Left and Right along the actions (the
  other way round in a right-to-left language), Down back to the first chip; each is a named button to a screen
  reader, the ⋮ "More queue actions", and the count is said again as Send next or Clear queue changes it. An action
  that ends the line (Resume, the last message sent, Clear queue) puts the focus back in the field. None show while
  the queue is not paused.
- `edit(draft)`, `unqueue(draft)`, `resume()`, `sendNext()` and `clearQueue()` do from the app what the queue's own
  controls do. No callback says which was pressed: a message Send next sends reaches `onSend`, as every drained one
  does, so a send and a removal are already told apart, and `queue` shows every change.
- On a Roku or an Apple TV, where a message is written on the platform's keyboard, it is off unless the app turns it
  on. `demo=chat&queue=1` shows it, each reply streaming for some seconds (`afterstop=drain` to go on after Stop,
  `seed=1` for a paused queue), with an app action, "Stop tools", on the paused line.

### Files (`everyui.file`)

Files from the person, on the web, the desktop and Android: the platform's own picker, and files dragged onto a
component from outside the app. A Roku and an Apple TV have neither (`Files.pickable`, `Files.droppable` are false).

```scala
import everyui.file.Files

Files.pick(List("image/*", ".txt"), multiple = true).map(_.foreach { f =>
  f.bytes.map(b => upload(f.name, f.mime, b)).start()   // read off the drawing thread, answered on it
}).start()
Files.dropTarget(panel)(over => panel.borderWidth @= (if (over) 2.0 else 0.0))(files => add(files))
```

A `PickedFile` has its name, size, type (the platform's, or by extension: `FileTypes.of`), a `Task` reading its bytes,
and a `preview` address an `ImageView` can show a picture from. `Files.release(preview)` lets it go (the web's object URL) once
nothing shows it. `FileTypes.accepts(types, name, mime)` says whether a
file is among the web's `accept` words ("image/*", "text/plain", ".md").

**Saving.** `Files.save(bytes, name, mime)` hands the person a file to keep, and `Files.saveUrl(url, name, mime)` what a
URL answers with; `mime` is an `Option` (by the name's extension when None). Each answers a `Task[SaveResult]`:
`Saved(name)`, `Cancelled` (the picker closed), or `SaveFailed(message)`.
`Files.saveLater(name, mime)(bytes)` opens the picker first and writes bytes made afterwards (a `Task[Either[String,
Array[Byte]]]`); a server's answer is saved with `everyui.network.HttpFiles.save` (below).
`Files.download(bytes, name, mime)` hands a file over at once: on the web straight into the browser's downloads (a Blob,
no picker), elsewhere as `save`.

```scala
import everyui.file.{Files, SaveResult}

if (Files.savable) Files.save(csv.getBytes("UTF-8"), "report.csv", Some("text/csv")).map {
  case SaveResult.Saved(name) => Toast.show("Saved " + name.getOrElse("report.csv"))
  case SaveResult.Cancelled => ()
  case SaveResult.SaveFailed(why) => Toast.show("Not saved: " + why)
}.start()
```

- **Web**: the File System Access API's save picker where the browser has one (Chromium's), the file written or the URL
  streamed into it; `Saved` has the name the person gave. Elsewhere (Firefox, Safari), or where the picker may not open
  (it needs a click to open from), a download: `Saved(None)`, since the browser says no more. Start a save from a click.
  A URL of another origin must allow the page to read it (CORS) for the picker; a download of one may open it instead.
- **Desktop**: JavaFX's save dialog; `Saved` has the whole path. A URL is fetched straight into the file.
- **Android**: the system's document creator (ACTION_CREATE_DOCUMENT), answered through the activity's result as a pick
  is; `Saved` has the document's name. A document that could not be written is deleted again. Not on an Android TV.
- **Roku, Apple TV**: `Files.savable` is false and a save answers `SaveFailed`.

A relative URL is found against `Platform.origin()`.

### Snapshots (`everyui.capture`)

`Snapshot` takes a picture of what the app draws, as a PNG: the whole window, or one component and everything in it,
`scale` times its size (2 for a sharper copy). Each answers a `Task[Option[SnapshotImage]]`, None where there is nothing
to take (a component of no size) or it cannot be taken here. A `SnapshotImage` has the PNG's bytes (`png`), its `width`
and `height` in pixels and its `mime`; `save(name)` hands it to the person as `Files.save` does, and `preview` is an
address an `ImageView` shows it from (the web's object URL goes with `Files.release(preview)`).

```scala
import everyui.capture.Snapshot

// a feedback form's screenshot, sent as a part
if (Snapshot.supported) Snapshot.window().map(_.foreach { shot =>
  Network.http(HttpCall.multipart("/feedback", List(FormPart.text("note", note()),
    FormPart.file("screenshot", "screenshot.png", shot.png, shot.mime)))).start()
}).start()

// a chart exported crisp at three times its size, straight from its figures
Snapshot.drawing(chart, scale = 3.0, background = Some(Theme.surface())).map(_.foreach(_.save("chart.png").start())).start()
```

A component is drawn as it is laid out, over the colour behind it (the nearest solid background round it, else the
window's), whatever lies over it on the screen; a scroll inside it shows what it has scrolled to. `Snapshot.drawing(d)`
and `Snapshot.figures(figures, width, height)` draw a `Drawing`'s figures into a PNG without the screen at all, over a
`background` or clear, at any scale: a graph's export (`Snapshot.drawable`).

- **Web**: a browser cannot draw its own elements into a picture, so they are drawn again. The component's elements are
  copied as they stand, each with the style the browser worked out for it, its pictures and the faces its words are in
  carried in as data, into an SVG `foreignObject`, which a canvas draws and writes as a PNG. It matches the page to the
  pixel in Chromium (text in the page's faces, pictures, drawings, lists, rounded corners), in about a tenth of a
  second. What the browser will not let the page read is left out, drawn as nothing: a picture of another origin that
  does not allow the page to read it (CORS: `Access-Control-Allow-Origin`), a video whose frame cannot be read (another
  origin's without CORS, or protected), and an embed or web view (another page). Scroll bars of the page's own scrolls
  are left out. Faces are carried from the page's `@font-face` rules and the faces the app registered (of a face in
  slices by script, the Latin ones, and the rest where the words go beyond Latin); a style sheet of another origin is
  read again where it allows that, and a face that cannot be read falls back to the system's.
- **Desktop**: JavaFX draws the node, or the scene's root, into an image (`Node.snapshot`) at the scale, over the colour
  behind it; the PNG is written on a thread of its own. A JavaFX popup window (a system tooltip) is not in the window's.
- **Android**: the window is copied from what the display shows (PixelCopy), video and hardware layers with it; a
  component's views are drawn into a bitmap of their own, a TextureView's frame drawn in where it is. A SurfaceView's
  picture (a video on its own plane) never passes through the app's drawing, so it is not in a component's picture,
  only the window's.
- **Roku, Apple TV**: nothing is taken (`supported` and `drawable` are false), and each answers None.

### Clipboard (`everyui.clipboard`)

`Clipboard.copy(text)` puts text on the system's clipboard where there is one (`Clipboard.copies`), and
`Clipboard.read()` reads it back as a `Task[Option[String]]` where the platform lets an app (`Clipboard.readable`):
None when it holds no text, or when reading it was refused.

```scala
import everyui.clipboard.Clipboard

if (Clipboard.readable) paste.event.click.attach(_ => Clipboard.read().map(_.foreach(t => field.value @= t)).start())
```

- **Web**: `navigator.clipboard.readText`. The browser reads it only for a page in front, from a click or a key, and
  asks the person first (Chromium's "clipboard-read" permission, remembered for the site; Firefox and Safari show a
  small Paste button of their own at each read). A refusal answers None (and logs why). Read it from a Paste button.
- **Desktop**: JavaFX's system clipboard, answered at once.
- **Android**: the ClipboardManager's first item as text. Android 10 on lets only the app in front read it, and
  Android 12 on tells the person it was read.
- **Roku, Apple TV**: no clipboard; `readable` and `copies` are false, and a read answers None.

### Pasting (`pasted`)

The app decides what a paste becomes before it lands. `pasted` on an `InputView`, a `TextInput` and a `TextArea` is
asked with the clipboard's words and answers a `PasteAction`: `Insert` (they land, as with no hook: the default),
`InsertText(other)` (these words land instead, at the caret or over the selection, and one undo takes them back as it
would the paste) or `Ignore` (nothing lands). A paste with no words (a picture, files) is never asked about, nor is
one into a read-only (`readOnly`) or disabled field, which takes no paste at all.

```scala
import everyui.component.types.PasteAction

phone.pasted @= Some(text => PasteAction.InsertText(text.filter(_.isDigit)))
```

A `Composer`'s `pasted` answers a `ComposerPaste`, which can also be `Attach(attachment)`: the paste goes with the
message as a chip, exactly as a dropped file does, taken where `accept`, `allow` and `maxSize` agree (refused, it is
refused with the same toast, and nothing lands). `Attachment.pasted(text)` is the words as a text/plain file, its bytes
their UTF-8, named after their first line (`Attachment.textName`: "stack trace.txt"); `ComposerPaste.longAsFile(n)`
makes a paste longer than `n` characters one, so a log or a stack trace does not flood the message. `demo=composer`
does this over 1500.

```scala
import everyui.chat.ComposerPaste

composer.pasted @= Some(ComposerPaste.longAsFile(1500))
```

It is asked wherever the platform has a field of its own (`Platform.nativeInput()`), for every way a paste comes:

- **Web**: the field's `paste` event, which Ctrl+V (Cmd+V), Shift+Insert, the context menu and a phone's Paste all fire;
  the words are its `clipboardData`. The default is prevented when the app changes the paste, and other words go in
  with `insertText`, on the browser's undo stack (else `setRangeText`, heard as an edit).
- **Desktop**: the fields are JavaFX fields whose `paste` is asked first, which Ctrl+V (Cmd+V), Shift+Insert and the
  context menu's Paste all call. Other words replace the selection as one undoable edit.
- **Android**: the field's `onTextContextMenuItem` for Paste and Paste as plain text, which its menu, Ctrl+V on a
  keyboard and a soft keyboard's own Paste key all come through. A soft keyboard's clipboard suggestion (Gboard's chip)
  is typed rather than pasted (`commitText`), so it is asked about only where the words typed are the clipboard's and
  run past a line (a newline, or over 80 characters): a short suggestion lands as typed, unasked. Reading the clipboard
  for that is what Android 12 on tells the person of, which is why a word typed is never read against it.
- **Roku, Apple TV**: no pasting (a field is written on the platform's keyboard), so it is never asked.

On the web a phone keyboard's clipboard suggestion is typed too, and is not asked about; nor is text dragged into a
field.

### ColorPicker (`everyui.color`)

A colour chooser in plain shared EveryUI, so it runs on every backend: a saturation and brightness area with a hue
slider, an alpha slider where `alpha` allows see-through colours, the colour beside its hex and its red, green and
blue fields, optional `presets`, and a row of the colours chosen lately. `value` is a `Var[Color]` the app gives and
reads, in step both ways with the area, the sliders and the fields; a field changes it only once it holds a colour.
`ColorDialog.pick` asks for one in a dialog (everyui.dialog) and remembers the one chosen.

```scala
import everyui.color.{ColorDialog, ColorPicker}

val colour = Var(Color.fromHex("1e88e5"))
val picker = new ColorPicker(colour, alpha = true, presets = ColorPicker.material)
ColorDialog.pick("Label colour", label.colour(), presets = ColorPicker.material)
  .map(_.foreach(c => label.colour @= c)).start()
```

A mouse or a finger presses or drags the area and the sliders (on the web, a finger's drag is theirs rather than the
page's: `Component.touchDrags`). From the keyboard, Tab goes through them; the arrows move the area's thumb and a
slider's, Shift ten times as far, and Home and End take a slider to its ends. Each is a `slider` to a screen reader,
named with its value ("Hue 208°"). `ColorPicker.recent` holds the recent colours for every picker (give one its own
`recent` to keep them apart); save and restore it as the app likes. `Hsv` and `ColorText` are the conversions: hue,
saturation and value that keep a grey's hue, and strict hex and channel parsing.

A Roku and an Apple TV draw no gradients, so there the picker has no area: saturation and brightness are sliders like
the hue, drawn in bands of solid colour, and all of it works from the d-pad (Up and Down between controls, Left and
Right along a slider or the swatches, OK on a swatch; a field takes its value from the platform's keyboard). Android
TV keeps the area: OK starts moving its thumb with the arrows, and OK or Back stops.

### Sliders and switches (`everyui.slider`, `everyui.component.Switch`)

Material's slider, range slider and switch, in plain shared EveryUI, so they run on every backend. A `Slider` sets a
`Var[Double]` from `min` to `max`, in steps of `step` (0 runs smoothly) or in `divisions` equal steps with a tick at
each; a `RangeSlider` sets two, `start` and `end`, which never cross and stay `minGap` apart, the track lit between
them. `value` (or `start` and `end`) changes as the thumb moves; `onChangeEnd` hears where it came to once a drag lets
go, and after each key, for work to do only then (a filter, a query). `showValue` puts the value over the thumb in
`format`'s words: `ValueLabel.Never`, `Moving` (while dragged, or set from the keys) or `Always`. `vertical = true`
runs one up; `length` is its long side (bind `size.width`, or `size.height`, to change it), and it is 40 across.
`disabled @= true` fades it and the focus passes it by.

```scala
import everyui.slider.{RangeSlider, Slider, SliderScale, ValueLabel}
import everyui.component.Switch

val volume = Var(40.0)
val slider = new Slider(volume, 0.0, 100.0, step = 1.0, label = "Volume", showValue = ValueLabel.Moving,
  format = v => SliderScale.text(v) + "%")
slider.onChangeEnd.attach(v => save(v))

val (low, high) = (Var(20.0), Var(80.0))
val price = new RangeSlider(low, high, 0.0, 100.0, step = 5.0, minGap = 10.0, label = "Price")
price.onChangeEnd.attach { case (from, to) => filter(from, to) }

val wifi = new Switch("Wi-Fi", Var(true))                              // the switch after its label
val line = new Switch("Apply as the slider moves", width = Some(360.0))  // label at one end, switch at the other
wifi.on.attach(on => network.enable(on))
```

A press on the track puts the (nearer) thumb there and a drag takes it along, held until the pointer or the finger lets
go wherever it goes; on the web a finger's drag is the slider's, not the page's. With the focus, the arrows along a
slider move it a step (a hundredth of the way where it runs smoothly), Page Up and Page Down a tenth, Home and End to
its ends; the arrows across it are the focus layer's, so on a remote Left and Right set it and Up and Down go on past
it, and nothing else is taken. Each of a range's thumbs takes the focus: Tab goes from the start to the end, and OK
from either to the other, which is how a remote reaches the second. A horizontal slider runs from the right in a
right-to-left language, and its arrows with it; a switch mirrors too, on at the left. A switch flips with a click, a
tap, OK, Enter or Space, and its thumb can be dragged across; it lands on the side it is let go nearer, sliding there.
The colours are the theme's (`Theme.accent` and the palette's text, control and muted colours), in light and dark.

A screen reader hears a slider's thumb as a slider with its value, its range and `format`'s words (on the web
`aria-valuenow`, `aria-valuemin`, `aria-valuemax`, `aria-valuetext`; TalkBack reads the words, Android 11 on), and a
switch as a switch that is checked or not (`aria-checked`): `Semantics.slider` and `Semantics.switch`, which any
component can say. `SliderScale` is the arithmetic on its own (snapping, clamping, a range's gap, where each key goes),
and `everyui.event.PointerDrag` the drag, held from the press until it lets go, for a control of an app's own.

### Dates (`everyui.date`)

A date picker and a date-range picker in plain shared EveryUI, on every backend, with the date type they work in.

`CalendarDate(year, month, day)` is a day with no time or zone, worked out in plain arithmetic (no `java.time`, which a
Roku has none of): `plusDays`, `plusWeeks`, `plusMonths` and `plusYears` (January 31 and a month is February 28 or 29),
`weekday` (1 Monday to 7 Sunday), `daysInMonth`, `isLeapYear`, `daysUntil`, `compare`, `isBefore`/`isAfter`, `within`
and `clamp` to optional bounds, `startOfWeek(firstDay)`, and ISO 8601 both ways (`iso`, `CalendarDate.parse`). To and
from the epoch: `toEpochMillis`/`fromEpochMillis` at UTC midnight, and `toLocalEpochMillis`/`fromLocalEpochMillis` where
the device is (`Platform.localDateTime`, so the platform's own zone), with `localOffsetMillis` and `CalendarDate.today()`.
`DateRange(start, end)` is both ends included (`DateRange.of` puts them in order), `DateRange.parse("2026-09-01/2026-09-29")`.

```scala
import everyui.date.*

val due = Var(Option(CalendarDate.today().plusDays(7)))
val field = new DateField("Due date", due, min = Some(CalendarDate.today()))   // typed, or its calendar button
val picker = new DatePicker(due)                                               // a calendar on the page, in step
DatePicker.pick(DateWords.selectDate, due()).map(_.foreach(d => due @= Some(d))).start()   // OK and Cancel

val period = Var(Option.empty[DateRange])
val presets = List(DatePreset.lastDays("Last 7 days", 7), DatePreset.lastDays("Last 30 days", 30),
  DatePreset.thisMonth("This month"), DatePreset.lastMonth("Last month"), DatePreset("Next week", t => ...))
new DateRangeField(period, max = Some(CalendarDate.today()), presets = presets)    // start and end fields
new DateRangePicker(period, presets = presets)
DateRangePicker.pick(DateWords.selectRange, period(), presets = presets).map(_.foreach(r => period @= Some(r))).start()
```

`Calendar` is the month both pickers are made of: the month's name (press it for a grid of twenty years a page), the
buttons to the month before and after, the days of the week, and the days, today ringed, the chosen filled, and those
outside `min` and `max` muted and out of reach. The keyboard moves among the days as a grid does: the arrows a day and a
week, Page Up and Down a month (Shift, a year), Home and End the week's ends, Enter chooses, and the month turns
as the focus leaves it; the grid is one stop for Tab, the header's buttons others. On a remote, Up from the first week
goes to the header and Down from the last out of the calendar (Left and Right still turn the month). A pointer or a
finger chooses by tapping. In a range picker the first day starts the range and the next one (not before it) ends it, the
days between drawn as a band, previewed to the day under the pointer or with the focus until the end is chosen
(`RangeChoice`); `presets` are buttons above it. To a screen reader it is a `grid` of `row`s of `gridcell`s, each named
with its whole date ("Tuesday, September 29, 2026, selected").

A field shows the date in numbers as the language writes them (`DateFormat.numeric`: "9/29/2026", "29/09/2026",
"2026/9/29"), or as `format` says: `DateFormat.medium` ("Sep 29, 2026"), `.full`, `.iso`, `.pattern("dd.MM.yyyy")`, or
the app's own `DateFormat(write, read)`. What is typed is read leniently (`DateParse`): numbers in the language's order
or with a four-digit year first, `20260929`, month names in the language or English, two-digit years near today, a year
left out as this one's. It changes the value only once it is a date in range; left holding anything else, it says so
under itself (`error`), and left holding a date it is written again in the format. Its calendar drops from it on a
screen 600 wide or more, and is a dialog otherwise; on a remote (`Platform.remoteNavigation`) there is nothing to type,
and OK on the field opens the dialog.

Month and weekday names, a date in full and a month with its year come from `everyui.i18n.DateText` (CLDR's
`yMMMMEEEEd`, `yMMMM`, `yMd` and the narrow weekdays now in `DateSymbols`) in the language in use; the week starts on
`everyui.i18n.WeekStart.current()`: CLDR's first day for the device's region where it speaks the app's language, else
the language's likeliest region (Sunday for `en` and `pt`, Saturday for `ar`, Monday for most), or `WeekStart.chosen`.
The pickers' own words are `DateWords` (Select date, Previous month, Not a date, …), in English unless the app gives them
with `Words.add` under `date.*` keys. In a right-to-left language the grid, the header and the band mirror, and Left and
Right with them. `Calendar.textSize` is set once, larger, on a television.

### Smaller pieces

- `new SplitPane(..., collapsible = Some(SplitSide.First), startCollapsed = true)` is folded from the first frame, so
  a sidebar restored folded never shows open first.
- `new Checkbox("Wrap lines", compact = true)` is 24 pixels tall with a smaller box and words, as wide as its label
  unless given a `width`, for dense tool windows. Its focus ring stays clear of the box and the words.

## Remembering small things (`everyui.store`)

`Stored(space, key, default)` is a `Var` kept on the device between visits: read once as the app starts, written on
every change after. It is for what a person picks for themselves, such as how a list is sorted or which tab is open.
It works the same on every backend (the browser's storage, files on the desktop, Android, a Roku and an Apple TV),
so an app never reaches for `localStorage` itself.

```scala
import everyui.store.{Codec, Stored}

given Codec[Sort] = Codec.named(Sort.values.toList)(_.toString)   // an enum, kept by name
val sort: Var[Sort] = Stored("dw.dashboard", "overview.sort", Sort.Name)
```

That second line replaces DataWrangler's `Remembered.load` / `Remembered.save` pair. (It is kept under a key of its
own, so a sort chosen before the change is not carried over.)

- The read is asynchronous. If the Var changes before it answers, because the app set it or the person chose, the
  change stands and the kept value is not applied over it. The value read is not written back.
- Nothing kept, or text the codec cannot read (an enum case since renamed), leaves the default. So does a browser
  that refuses storage (a private window, or blocked site data). A read may always come back empty, and the app must
  draw the same screen when it does.
- Writes are coalesced within a turn: a Var set several times in one pass is written once, with the value it ended
  on. Nothing waits past that, so a reload straight after a change keeps it. A value that changes every frame, such
  as a slider mid-drag, is written every frame, so keep the value it settles on.
- `Stored.connect(space, key, v)` does the same for a Var the app already has, and answers a `Task` with the value
  it applied. `Stored.get[T]` and `Stored.put[T]` read and write once. `Store.delete(space, key)` and
  `Store.clear(space)` forget (the next start is from the default).
- Codecs are given for `String`, `Int`, `Long`, `Double`, `Boolean` and `Option` of any of them. `Codec.named` covers
  one of a fixed set (`Codec.named(Sort.values.toList)(_.toString)`), and `Codec.from(encode, decode)` covers
  anything else. An app with fabric keeps a case class as JSON through its `RW`:
  `given Codec[Layout] = FabricCodec[Layout]` (in `everyui-network`, whose Scala.js build now depends on
  `everyui-web` for it).
- An empty String is kept by forgetting the key, because no backend tells an empty document from none, so it comes
  back as the default.

## The size the app is drawn at (`everyui.theme.UiScale`)

`UiScale.factor` (a `Var[Double]`, 1 by default) draws the whole app larger or smaller, as a browser's zoom draws a
page: every component, not only its words. At 1.5 a 30 px row is 45 px on the screen with its 14 px words at 21, a
field still fits its line, and `ui.size` is the screen over 1.5, so whatever follows `ui.size` lays itself out again in
the room left, as it would for a smaller window. A change takes effect at once. It is a whole-UI scale because
EveryUI's components have fixed heights (rows, fields, buttons): larger words alone would be cut short in them.

```scala
import everyui.theme.UiScale
import everyui.kit.ScaleControl

UiScale.remembered()             // as the app starts: the size chosen last time on this device, kept from now on
UiScale.commands(Keymap.global)  // Primary with = or + (Shift or not), - and 0
settings.children += new ScaleControl(label = "Size")   // − / 100% / +
```

- It is kept between 0.5 and 3.0 (`UiScale.least`, `UiScale.most`): set outside, it is taken to the nearer end.
  `UiScale()` is the size drawn at.
- `larger()`, `smaller()` and `reset()` step through `UiScale.steps` (a `SliderScale`, 0.8 to 2.0 by 0.1 unless the
  app sets it) and back to 1. `percent()` says it as "130%".
- `remembered(space = "everyui", key = "scale")` is off until the app calls it (once, at start): it is
  `Stored.connect` over `factor`, so a size set before the read answers stands.
- `commands(keymap)` adds `ui.larger`, `ui.smaller` and `ui.defaultSize`, labelled in the current language
  (`Words.larger`, `smaller`, `defaultSize`), on Primary+= (and with Shift, and Primary++ either way), Primary+- and
  Primary+0. A browser's own zoom keys are then the app's.
- `ScaleControl` is one stop for the focus: Left and Right (mirrored right to left), - and + step it, Enter or OK goes
  back to 100%, and Up and Down move on. A click on − or + steps it and one on the percentage resets it; each end fades
  where the steps end. A screen reader hears it as a slider.
- It multiplies what the platform does already, never replaces it: the browser's zoom and device pixel ratio, the
  desktop's display scale, Android's density. `Platform.pixelRatio()` includes it.
- How each backend draws it: on the web the root's CSS `scale` (from its top left, laid out in the window over the
  factor), with a box on the screen and a pointer's place read back over it; on the desktop a `Scale` on the root
  pane; on Android the root's scale is the density times it; on a Roku the root group's scale, and on an Apple TV the
  root view's sublayer transform. A Roku and an Apple TV enlarge what they have drawn, so words above 1 are a little
  soft there; the others draw them afresh at the size.
- An app that lays itself out once from `ui.size()` (outside `:=`) keeps that layout until it is built again; bind to
  `ui.size` to follow the size as it changes. `demo=scale` shows words, rows, a field, buttons and menus at each size.

## The app at work (`everyui.app.AppBusy`)

`AppBusy.state` is a `Var[Option[Busy]]` the app sets while it works, so the person sees it from elsewhere: None
when idle, `Some(Busy())` when busy with no end in sight, `Some(Busy(Some(0.42), Some("deliver")))` when 42%
through something named. `progress` is 0 to 1 (held to it) and `label` any short name. DataWrangler's, from its
running workflows:

```scala
AppBusy.state := running().headOption.map(w => Busy(Some(running().map(_.progress).sum / running().size), Some(w.name)))
```

Each platform shows it its own way, and restores everything exactly once it is None again:

- **Web**: the tab's icon. A ring around the app's own icon (the page's `<link rel="icon">`, drawn smaller inside
  it) fills with the progress, or an arc turns round it. Browsers do not animate GIF, APNG or SVG icons in a tab, so
  the pictures are drawn on a canvas and put in the link as data URLs; each icon link gets its own address back when
  idle, and a page with none gets a link for the time it is busy. The spinner runs on a clock of its own at 8
  pictures a second, not the frame loop, in a worker so that a tab in the background keeps turning (a page's own
  timers are slowed there to once a second, then once a minute). A picture is drawn only when it changes: a
  progress, only when it moves by 1/60. The ring is the theme's accent, deepened to read on a light or dark tab
  strip. An icon from another origin without CORS cannot be drawn on, so there the ring is drawn alone. An SVG icon
  needs `width` and `height` on its `<svg>`, not only a `viewBox`: Chrome shows no icon at all for one without them
  (`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">`). The console says once
  (`[everyui] AppBusy …`) when the page's icon did not load, is from another origin that does not allow it, or is
  such an SVG, so a ring around nothing points at the markup.
- **Desktop**: the dock or taskbar entry, through `java.awt.Taskbar`, where the system has one for the whole app:
  macOS fills the dock icon and badges it "…" when there is no progress; Linux fills the launcher entry where
  libunity is installed and a Unity launcher reads it (Ubuntu's dock, KDE's task manager). Windows only has a
  window's taskbar button, which a JavaFX stage is not, so nothing shows. KDE on Wayland without libunity (this
  build's machine) reports no taskbar.
- **Android**: an ongoing, silent notification with a progress bar (a moving one where there is none), titled with
  the label or else the app's name, which opens the app when tapped and goes when idle. It has a channel of its own
  (`everyui.busy`, "In progress", low importance). The app's manifest must declare
  `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`, and on Android 13 and later the person
  must allow it: `AppBusy.permit()` asks (a `Task[Boolean]`), best when they start something long;
  `AppBusy.permitted` says whether it may. An Android TV shows nothing (there, `supported` is false).
- **Roku, Apple TV**: nothing; `AppBusy.supported` is false.

`AppBusy.inTitle @= true` (off by default) also puts it before the title, "(42% deliver) Dashboard", "(…) Dashboard",
on the web's tab and the desktop's window, and takes it off again when idle; a title the app sets while busy takes it
at the next change. Where a platform shows nothing else (the desktop on Windows or most of Linux) this is the only
sign.

`AppBusy.stillAfter @= Some(3.0)` (None by default) keeps a long stage from looking stalled on the web: once a
progress has not moved for that many seconds, a small arc goes round its ring (pale over the fill, a faint accent
over the rest) until it moves again. Elsewhere the platform's own bar shows the progress, and nothing changes.

`demo=busy` in the example app goes round idle, busy, and "deliver" from 0 to 100% (`title=1` for the title,
`spin=<seconds>` to hold the spinner, `still=<seconds>` for `stillAfter`, with the progress held at 60% to show it).

## Asking before the app is left (`Platform.leaveGuard`)

An app asks before its page or window goes while any of its guards answers true. `Platform.leaveGuard(() => Boolean)`
adds a guard, asked each time the app is about to be left, and `Platform.leaveGuardWhile(flag)` one that is a
`Val[Boolean]`; each returns what removes it. Voidcraft's, guarded while signed in, and later only while there is
something to lose:

```scala
private var unguard: () => Unit = () => ()
session.attach {
  case Some(_) => unguard = Platform.leaveGuard(() => true)
  case None => unguard(); unguard = () => ()
}
// or narrower:
Platform.leaveGuard(() => editors.exists(_.dirty()) || agent.working())
Platform.leaveGuardWhile(unsaved)   // a Var[Boolean]
```

- **Web**: the browser's own "Leave site?" as the tab is closed, the page reloaded, or another address typed or
  followed (`Platform.navigate` included). Browsers show their own words, never the app's, and ask only once the
  person has clicked or typed on the page; before that the page goes without asking. A move within the app
  (`Platform.pushPath`, the history's back and forward) unloads nothing and is never asked. The `beforeunload`
  listener stands only while a guard might answer true (any `leaveGuard`, or a `leaveGuardWhile` flag that is set),
  since a page with one may be kept out of the browser's back and forward cache: prefer `leaveGuardWhile` for a guard
  that is usually false.
- **Desktop**: closing the window (its ×, the system's keys, the taskbar) asks with EveryUI's own dialog, "Leave the
  app?", Stay or Leave, opening on Stay. Stay keeps the window. Leave closes it as the close would have: the request
  is made again for the window's own handlers, so an app's `setOnCloseRequest` (asking its tabs with
  `TabGroups.closeAllTabs`, say) hears it once, after Leave, and may still keep the window. `Desktop.launch` guards its
  window; another the app opens is guarded with `WindowLeave.watch(stage)`. `Platform.quit()`, and a window the app
  closes itself, are not asked.
- **Android**: Back from the first screen (one nothing in the app takes) asks the same question; Leave sends the app
  to the background, as Back there does from Android 12 (before 12, unguarded, Back finishes the activity). Home and
  the recent apps leave without asking, as Android gives an app no say there. With no guard answering true, Back is
  as it was.
- **Roku, Apple TV**: nothing is asked; the guards are kept nowhere.

The dialog's words are EveryUI's (`Words.leaveTitle`, `leaveMessage`, `leave`, `stay`, in its eleven languages);
`Words.add` rewords them. A guard that throws answers false, so the app can always be left. `demo=leave` in the
example app tries it: `guard=on` or `guard=off`, F and C to set and clear a flag's guard, G to add a guard, R to remove
them, P to move within the app.

## Quality on a slow device (`everyui.perf`)

`Quality.level` (a `Val[QualityLevel]`: `Full`, `Reduced`, `Minimal`) is how much the app draws and moves. The app
gives it the level kept for the device at launch, and from then on the session is measured and steps itself down a
level where it stays slow; it never steps back up within the session, and EveryUI keeps nothing of it. Where the level
is kept, and when it changes from one launch to the next, is the app's (NaboTV's server, from the summaries it is
sent, with the person's own choice folded in).

```scala
import everyui.perf.{Perf, Quality, QualityLevel}

Quality.start(stored)                                  // the device's level (None: Full); measuring starts here
Perf.summaries.attach(s => Api.perfSession(s))         // how this run is going, now and then and as it goes away
follow(Quality.level, (l: QualityLevel) => particles.visible @= l == QualityLevel.Full)   // the app's own parts
```

- `start` may be called again (the server's answer come in after launch): the level given changes, but never back above
  what the session has stepped down to, and the session goes on, with the same `sessionId` and what it has measured
  (`perf: session … goes on, given Reduced, at Reduced` in the log). One run is one session. Nothing is measured, at
  no cost, until it is first called.
- An app's own costly parts read `Quality.level` as it changes, or `Quality.reduced` and `Quality.minimal` where they
  are built once. `Full` changes nothing in EveryUI's parts and costs nothing per frame; what they read is a plain field,
  set as the level changes.

What EveryUI's own parts do:

| | Reduced | Minimal |
|---|---|---|
| animations (`to … in`, fades, slides, a screen's treatment blending; not `sleep`) | half as long | a quarter |
| a ScrollContainer's glides (`glideTo`, `reveal`), a fling | half as long | at once |
| a box's `shadow` and `blur` | none | none |
| a text's `textShadow`, `motion` run by the platform | as at Full | none |
| pictures | Android's downloads in 16 bits a pixel where they have no transparency, and its picture cache at half; never decoded smaller than drawn | the cache at a quarter |
| built beyond what shows | a ScrollContainer's slack a quarter of a viewport each way (half at Full); a VirtualList's `overscan` halved | a tenth; one row |

What was drawn before a change is drawn again: what is on the screen at once, and what was made before and put on the
screen later as it goes on. A level that lowers mid-session takes effect where the app's own parts follow `level`.
The web decodes its pictures as the browser does, at every level.

What is measured (`Perf`), counted into buckets as it comes, never logged per event:

- **key to response**: from a key pressed to the start of the frame after the one that drew its handling, which is when
  that one was on the screen (`keyLatency`);
- **the app's update** in each frame (`ScreenManager`'s update and layout; on a Roku this is the BrightScript work), and
  the time from **frame to frame**, with the share **dropped**: a frame that missed at least one vsync of the display's
  own refresh, come one and a half refresh periods or more after the one before by the loop's own signal (Android's
  Choreographer, the web's animation frames, a Roku's render Timer), the refresh being the platform's where it says
  (`Platform.refreshRate`: Android's display, the web's animation frames, a Roku's video mode where it names a rate),
  else 40 ms, clear of a 30 Hz display's 33 (`QualityPolicy.dropMs`); a Roku TV drawing at 30 is not dropping its
  every frame. The same line counts the drops of the step down;
- **what went wrong**: errors nothing caught (a frame that failed; a thread's uncaught exception on the desktop and
  Android, the window's `error` and `unhandledrejection` on the web; on a Roku what RtGuard reports, its execution
  timeouts and stack overflows among them), and stalls: a change of screen not done in `ScreenManager.stallSeconds`, or
  a turn a Roku cut off. A run with either is judged no longer from then on (`quality: no longer judged in this run,
  which had 1 error`): its frames are of whatever was left on the screen, not of how the device copes;
- **the platform's frames** where it times them: Android's FrameMetrics (`Platform.frameTimings`, which now takes more
  than one listener). The web's frames are its `requestAnimationFrame`s;
- **memory**: a Roku's general memory level (`roDeviceInfo.GetGeneralMemoryLevel`: normal, low, critical) and the share
  of its limit in use (`roAppMemoryMonitor.GetMemoryLimitPercent`); Android's low-memory state and heap; Chrome's
  script heap. Read once a window, not per frame;
- **the device** at launch (`Perf.device`, a `DeviceHint`): a Roku's model number, kind (TV or STB) and name, its
  graphics platform and the memory the channel may use; Android's model, OpenGL ES version, RAM, memory class, cores and
  `isLowRamDevice`; the web's `navigator.deviceMemory` and `hardwareConcurrency`; the desktop's system, heap and
  cores. A call the box does not have is passed over.

They are read out a window at a time (`QualityPolicy.windowSeconds`, a minute): each to `Perf.windows`, a `PerfWindow`
whose `line` is the diagnostic one (`60s keys 12 p50/p95 140/420ms update p95 38ms frame p95 66ms drop 12% of 840 mem
normal 41% at Full`), and `Perf.current()` is the window under way, for an overlay. `perf=1` at launch logs each window.

The session so far goes to `Perf.summaries` every `summarySeconds` (five minutes) and as the app goes away, so a run
ended without warning has still said how it went; `Perf.session()` asks for it at any time. A `PerfSession` has plain
fields only, for `given RW[PerfSession] = RW.gen`, on the Roku too; whatever was not measured is None, never 0, and
nothing is sent before anything is measured:

```scala
case class PerfSession(sessionId: String,                 // the same from launch to exit: each summary an update
                       at: Long,                          // ms since 1970
                       sessionSeconds: Option[Int],
                       keyLatencyMsP50: Option[Int], keyLatencyMsP95: Option[Int],
                       frameDropPct: Option[Int],
                       memoryLevel: Option[String],       // "normal", "low", "critical"
                       evidence: Option[String],          // a line for people: windows, slow ones, the step, the device
                       keys: Option[Int], frames: Option[Int],
                       updateMsP95: Option[Int], frameMsP95: Option[Int], memoryUsedPct: Option[Int],
                       platform: Option[String], model: Option[String], graphicsPlatform: Option[String],
                       ramMb: Option[Int],
                       levelGiven: Option[String], levelRun: Option[String], steppedDown: Option[Boolean],
                       errors: Option[Int],               // errors nothing caught in the run
                       stalled: Option[Boolean])          // a change of screen that never finished, a turn cut off
```

The step down (`QualityPolicy`, every threshold in one place, `Quality.policy` to change them; placeholders that agree
with NaboTV's server until a slow device's numbers set them): a window calls for Reduced at a 95th percentile key time
of 300 ms (over at least 3 keys), 8% of its frames dropped (over at least 30), or memory low, and for Minimal at 600 ms,
20%, or memory critical. Three windows in a row each calling for less than the session runs at step it down one level,
logged in one line with the window and the device (`quality: stepped down Full -> Reduced at 184s: key p95 420ms >= 300
(3 windows) | …`); one bad minute does not. `steps = false` measures without stepping.

`demo=quality` shows the level, the window under way and the device, a button for each level, and shadowed cards, a
glide, a turning square and a blurred one that each level draws differently (`window=5`, `slow=2` to step down soon,
`level=Reduced` to start there, `tiles=<n>` for work to feel on a slow or throttled device).

## Relative URLs and the origin

`Network.http` and `Network.webSocket` take relative URLs and resolve them against `Network.origin`:

- a URL with a scheme (`https://…`) is used as it is;
- `//host/path` takes the origin's scheme;
- `/path` goes on the origin;
- any other path (`api/x`) goes under the origin's root, never under the current page's path;
- for a socket, `http` becomes `ws` and `https` becomes `wss`.

`Network.origin` is `""` by default, which means the platform's own. On the web that is the page's origin. Elsewhere
there is none, and a relative URL throws. An app off the web sets it once at start:

```scala
Network.origin = Platform.origin()     // "https://example.com"
```

`Platform.origin()` is the page's origin on the web. On every other platform it is the setting `"everyui.origin"`,
which the app saves with `Platform.saveSetting("everyui.origin", url)` or its build ships, and `""` without one.

On the web, a request to the page's own origin carries its cookies; one to another origin carries none unless the
call asks, `HttpCall(..., credentials = true)`, and that server must then answer with the page's origin in
`Access-Control-Allow-Origin` (not `*`) and `Access-Control-Allow-Credentials: true`. Elsewhere there is no browser
and `credentials` means nothing.

## Uploads: multipart forms (`everyui.network`)

`HttpCall.multipart(url, parts)` is a multipart/form-data POST, as a browser sends a form: fields of text and files.
A `FormPart` is `FormPart.text(name, value)`, `FormPart.json(name, value)` (a field of compact JSON, through fabric's
`RW`), or `FormPart.file(name, fileName, bytes, contentType)` (`application/octet-stream` when not given). A file the
person picked (`Files.pick`) is a part through `HttpFiles.part(name, picked)`, with its name and type as picked and
its bytes read; a message's attachment through `HttpFiles.attached(name, attachment)`. Both answer rapid Tasks, as
`Network.http` does:

```scala
import everyui.network.{FormPart, HttpCall, HttpFiles, Network}
import fabric.rw.*

Files.pick(List("image/*"), multiple = false).map(_.headOption.foreach { picked =>
  HttpFiles.part("image", picked).flatMap { image =>
    Network.http(HttpCall.multipart("/user/profile/picture/upload", List(FormPart.json("auth", auth), image)))
  }.map(r => if (r.ok) shown(r.body) else failed(r.status)).start()
}).start()

Network.http(HttpCall.multipart("/feedback", List(
  FormPart.json("auth", auth), FormPart.json("text", words),
  FormPart.file("screenshot", "screenshot.png", png, "image/png"))))
```

Fields go with no type of their own, as a form's inputs do (a server reads a JSON field from its text). A name or a
file name keeps its UTF-8, with `"`, CR and LF escaped as `%22`, `%0D` and `%0A`, as browsers write them. The call's
own headers go with it (an `Authorization`, say), but not a Content-Type: the body's names its boundary.

- **Web**: the browser writes the form: a `FormData` of the parts, each file a `Blob` of its bytes and type, sent
  with `fetch`. Another origin must allow the page (CORS: `Access-Control-Allow-Origin`); a multipart POST needs no
  preflight unless the call adds headers of its own. `credentials` and `timeout` are honoured as on any call.
- **Desktop, Android**: EveryUI writes the body (`Multipart.encode`, under a boundary of its own) and sends it through
  Spice's client.
- **Roku, Apple TV**: the same body, sent as bytes: the Roku's worker posts it from a file in `tmp:`, since a string
  would stop at the first NUL; the Apple TV's `URLSession` takes the bytes as they are.

## Downloads: a response's bytes, saved (`everyui.network`)

`HttpCall(..., binary = true)` reads the response's bytes as they came: `HttpResult.bytes` is `Some(bytes)` (None on a
call that did not ask), and `body` is the same read as UTF-8, for an error's text. `result.mime` is the Content-Type's
type ("image/png"), `result.contentType` the header whole, `result.fileName` the name its Content-Disposition gives
(`filename*=UTF-8''…` first, then `filename`, any path dropped), and `result.header(name)` any header, whatever its
case. `bodyBytes = Some(bytes)` sends a body of bytes, of `contentType`, in place of `body`.

`HttpFiles.save(call, name = None, mime = None)` makes the call for its bytes and saves them as `Files.save` does,
answering a `Task[SaveResult]`:

```scala
import everyui.network.{HttpCall, HttpFiles}

val export = HttpCall("POST", "/entity/download-export", body = Some(JsonFormatter.Compact(request.json)))
HttpFiles.save(export).map {                          // named as the server names it
  case SaveResult.Saved(name) => Toast.show("Saved " + name.getOrElse(entry.fileName))
  case SaveResult.Cancelled => ()
  case SaveResult.SaveFailed(why) => Toast.show("Not saved: " + why)   // "HTTP 403: …" for a refusal
}.start()
HttpFiles.save(export, Some(entry.fileName))          // the picker first, then the request
```

With a `name`, the save picker opens first, from the click (a browser opens it only for one), and the request is made
once the person has chosen where (`Files.saveLater(name, mime)(bytes)`, which any bytes made later can use); closing
the picker makes no request. Without one, the request is made first and the file named by the server (else by the
URL's last segment); a browser then opens its picker only while the click is recent (about five seconds in Chromium)
and downloads the file otherwise. A status that is not a success saves nothing and answers `SaveFailed("HTTP <status>:
<what it said>")`. `HttpFiles.fetched(call)` is the call alone: the bytes and the result, or why there are none.

- **Web**: the response read as an ArrayBuffer (Spice 1.10.9-SNAPSHOT6's client, which reads every response as
  bytes and a text one as its charset says). Another origin must allow the page (CORS) and expose the name,
  `Access-Control-Expose-Headers: Content-Disposition`, or `fileName` is None there.
- **Desktop, Android**: Spice's client; the file is written off the drawing thread.
- **Roku**: the worker takes the answer into a file in `tmp:` (a string would stop at the first NUL) and hands it back
  whole, as hex, so `bytes` are exact, but an `Array[Byte]` on a Roku is an array of numbers: keep it to small files.
  There is nowhere to save, so `HttpFiles.save` answers `SaveFailed` without a request.
- **Apple TV**: `URLSession`'s data, whole; nowhere to save either.

## An installable web app

An EveryUI web app can be installed from the browser, to run in a window of its own like any other app. It is opt-in,
and done by the app's build: `everyui.sbt.WebApp` (plain Scala in this repository's `sbt-support/`) writes a web app
manifest and a small service worker next to the bundle and links them from its `index.html`.

It comes with the `sbt-everyui` plugin ([Web builds](#web-builds-javascript-webassembly-or-both)). Call it once the
bundle is written, with the version `writeTo` answers:

```scala
import everyui.sbt.{WebApp, WebAppIcon, WebAppManifest}

webBundle := Def.uncached {
  val out = baseDirectory.value / "out"
  IO.delete(out)
  val version = everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  // ... fonts
  IO.copyDirectory(baseDirectory.value / "icons", out / "icons")
  WebApp.write(out, WebAppManifest("Voidcraft", shortName = "Voidcraft", description = "Code browser",
    themeColor = "#121212", backgroundColor = "#121212",
    icons = Seq(
      WebAppIcon("icons/icon-192.png", "192x192"),
      WebAppIcon("icons/icon-512.png", "512x512"),
      WebAppIcon("icons/maskable-512.png", "512x512", "maskable"))), version)
  out
}
```

It writes `manifest.webmanifest` (`display: standalone`, so the app opens in its own window), `sw.js` and `webapp.js`
(which registers the worker from a file, so a Content-Security-Policy of `script-src 'self'` allows it), and adds to
the page's head the manifest link, the theme colour and an Apple touch icon (the largest `any` icon). `version` names
the worker's cache, so a new bundle starts a new cache (the script's content hash does). The worker answers from the
network and keeps what it fetched, so the app still opens when the network is gone, and it shows and opens push
notifications ([Push notifications](#push-notifications-everyuipush)).

**Icons.** PNG, square: **192×192** and **512×512** with purpose `any` are what browsers require to offer an install;
add a **512×512 `maskable`** one (the picture within the middle 80%, the rest background) for Android's shaped icons.
Apple devices use the touch icon (180×180 is their size; the 512 one is scaled).

The page must be served over HTTPS (or from `localhost`). Chromium browsers then offer to install it from the address
bar; Safari adds it with Add to Dock or Add to Home Screen. In its own window the browser keeps no tabs, so more of the
chords a browser keeps for itself in a tab (Ctrl+T, Ctrl+N, Ctrl+Tab and others) reach the app's keymap; which ones is
the browser's own choice and changes between browsers, so try the chords the app relies on in the installed window.

## Push notifications (`everyui.push`)

`Push` subscribes the app to push notifications and hears them. A notification that arrives while the app is open is
the app's (`Push.received`, to show its own way; the system shows nothing); one that arrives while it is not is shown
by the system, and a tap on it opens the app at the message's path. `Push.opened` hears a tap while the app runs, and
the app then goes to the path (`ScreenManager.go`); a tap that opens the app starts it at the path, which its screens
answer as a link.

```scala
import everyui.push.{Push, PushAnswer}

Push.received.attach(m => Toast.show(m.title + ": " + m.body))
Push.tokens.attach(server.registerPush)                    // a token the platform replaced
if (Push.supported) Push.subscribe(vapidPublicKey).map {   // from a click the first time: it asks for the permission
  case PushAnswer.Subscribed(token) => server.registerPush(token)
  case PushAnswer.PushDenied => ()                         // the person said no
  case other => scribe.warn(other.toString)
}.start()
```

`Push.permission` (a `Task[everyui.Permission]`: `Granted`, `Refused`, `NotAsked`) and `Push.permit()` ask on their own;
`subscribe` asks first where it has to. A `PushToken` is what the server needs, sent as it is: `service` `"webpush"`
with `value` the browser's subscription as JSON (`endpoint` and `keys`, for a Web Push library with the app's VAPID
key pair), or `"fcm"` with the device's registration token. Subscribe on every start (it answers the same subscription
while it lasts) and send it, so the server always has the current one. `Push.unsubscribe()` lets it go.

**The payload** is JSON with three keys, all optional:

```json
{"title": "Build done", "body": "voidcraft#42 passed", "path": "/runs/42"}
```

`path` is the app's own path, where a tap takes the person; one off the app's origin opens the app at its start.

- **Web**: the Push API through the service worker `WebApp.write` installs ([An installable web app](#an-installable-web-app)),
  which shows a push as a notification when no window of the app is in sight, hands it to the page otherwise, focuses
  a window of the app (or opens one at the path) when a notification is tapped, and subscribes again when the browser
  renews a subscription (heard on `Push.tokens`). The server sends the payload above, encrypted with the VAPID key
  pair, as any Web Push library does. A page without the worker answers `PushFailed`. Chromium, Firefox and Safari (on
  iOS only for an app added to the home screen) have it; the page must be served over HTTPS (or `localhost`). Touch
  `Push` as the app starts (attach to `received` and `opened`), so it hears what the worker hands the page.
- **Android**: Firebase Cloud Messaging. The backend compiles against it but does not bring it: an app with push adds
  `EveryUILibraries.firebase` and the rest of Firebase messaging's runtime (`firebase-messaging` and what it pulls in),
  its Firebase configuration as the resources the Google services plugin writes from `google-services.json`
  (`res/values/google-services.xml`: `google_app_id`, `gcm_defaultSenderId`, `google_api_key`, `project_id`), and in
  its manifest `POST_NOTIFICATIONS` and the service:

  ```xml
  <service android:name="everyui.render.PushService" android:exported="false">
    <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
  </service>
  ```

  with `-keep class everyui.render.PushService` in its shrinker rules. Firebase's own manifest entries (its component
  registrars, the Play services version) must be in the app's manifest too, since the Android packaging here does not
  merge archives' manifests or resources. The server sends a data message with the payload's three keys as `data`; it
  reaches the app whether or not it is in front, and the app shows it as a notification (channel "everyui.push") when it
  is not. A notification message the system shows itself; its `data.path` still reaches the app on a tap. Android 13
  and later ask the person first. Not on an Android TV. Without Firebase in the app, `Push.supported` is false.
- **Desktop, Roku, Apple TV**: none; `supported` is false and `subscribe` answers `PushUnsupported`. A desktop app
  hears its server over its own connection while it runs.

`demo=platform` allows notifications, subscribes (`vapid=<public key>`), and shows what arrives or is tapped.

## Location (`everyui.location`)

`Location.current()` answers where the device is, once, and `Location.watch(distance)` follows it as it moves until
the watch is stopped, each as a `LocationAnswer`: `Located(position)`, `LocationDenied`, `LocationUnavailable` (no
fix just now: no signal, location off, none in time; a watch goes on), `LocationUnsupported` or
`LocationFailed(message)`. A `Position` has `latitude`, `longitude` and `accuracy` (metres), and `altitude`, `heading`
and `speed` as Options, with the `time` it was fixed; `distanceTo` measures between two in metres.

```scala
import everyui.location.{Location, LocationAnswer}

Location.current().map {
  case LocationAnswer.Located(p) => map.centre(p.latitude, p.longitude)
  case other => Toast.show("No location: " + other)
}.start()

val watch = Location.watch(distance = 25.0) {      // metres from the last position heard; 0 for every one
  case LocationAnswer.Located(p) => track += p
  case _ => ()
}
// ...
watch.stop()
```

Both ask for the permission where it has not been given; `Location.permission` (a `Task[everyui.Permission]`) and
`Location.permit()` ask on their own, before a screen that needs it. `precise = false` settles for a rough, quicker fix
(the network's rather than the satellites'), and `current` gives up after `timeout` seconds (30 by default).

- **Web**: `navigator.geolocation`, over HTTPS or on `localhost`. The browser asks the person at the first request.
  A watch is `watchPosition`, whose positions `LocationWatch` filters by distance.
- **Android**: the LocationManager: the fused provider where the device has one, else satellites for a precise fix
  and the network for a rough one. The manifest declares both permissions, which are asked for together, so the
  person chooses precise or approximate:

  ```xml
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  ```

  A watch gives the platform its distance too. Location turned off in the settings answers `LocationUnavailable`.
- **Desktop, Roku, Apple TV**: none; `supported` is false and every request answers `LocationUnsupported`.

`demo=platform` finds where the device is and follows it 25 m at a time.

## Video on the web: HLS through hls.js

A `VideoView` (or an `AudioPlayer`) whose `source` is an HLS stream plays in every major browser. Where the browser
plays HLS itself AND lists its tracks (Safari, on a Mac, an iPhone or an iPad), it plays it itself. Everywhere else
with Media Source Extensions (Chrome, Edge, Firefox) hls.js plays it, loaded the first time a source needs it and
never before. It is decided by what the browser can do, not by its name:

- a source is HLS by its address (a path ending `.m3u8`, before any query), or, where the browser refuses a source
  whose address says nothing, by the type its server answers for it (`application/vnd.apple.mpegurl`,
  `application/x-mpegURL`): the server is asked once, with `headers`, and a playlist goes to hls.js;
- Chrome and Chromium (153 and 154 here) now play HLS themselves (`canPlayType` answers "maybe") but list no audio or
  subtitle tracks, so an `.m3u8` still goes through hls.js there, and `tracks` and the track choice work;
- with `headers` set, hls.js plays an HLS stream even where the browser has HLS of its own, since the browser's player
  sends no headers. hls.js sends them with every playlist, segment and key (its `xhrSetup` and `fetchSetup`). A plain
  file (an `.mp4`) is still played by the browser, which sends none: its token must be in its address.

Everything a `VideoView` does works through hls.js: `playing`, `seek`, `playhead`, `duration` (0 for a live stream),
`loop`, `muted`, `volume`, `rate`, the `ready`, `ended` and `error` events, `waiting` (on the web now for any source:
playing asked for and the element short of data), `bandwidth` (hls.js's estimate, once a segment has come),
`retrying` (a load hls.js is retrying), `diagnostics` (the level's codecs and size, the audio tracks, and the error
with no address), and `buffering` (`responsive` holds 10 s ahead, `balanced` hls.js's own 30 s, `slow` 60 s). hls.js
adapts the bitrate itself. `tracks` lists the stream's audio renditions (`audioTrack`), its subtitle renditions and
its closed captions (ids `cc<n>`, format "CC") as text tracks (`textTrack`, drawn by the browser); `languages(audio,
text)` chooses by language as the stream opens. Each source's hls.js is destroyed when the source changes, the view
is released, or it goes out of sight.

**Shipping it.** hls.js (1.7.3) comes in the everyui-web jar, under `everyui-hls/<version>/` (the script, the worker it
transmuxes in, its licence), and sbt-everyui's `everyuiWebGuests` copies it out beside the page with the other guests,
so an app's bundle that already does `IO.copyDirectory(everyuiWebGuests.value, out)` serves it with nothing more. It is
loaded by a `<script src>`, never from a CDN, so a Content-Security-Policy needs only:

```
script-src 'self'; worker-src 'self'; media-src 'self' blob: data: <the streams' origins>; connect-src <the streams' origins>
```

(`blob:` for the stream Media Source Extensions hands the element, `data:` for the empty subtitle tracks hls.js makes,
and `connect-src`, since hls.js fetches the playlists and segments itself). Served somewhere other than next to the
page, the web's `everyui.render.HlsScript.root` says where. A stream on another origin must answer CORS for the page,
and with `headers` for its preflight too (`Access-Control-Allow-Headers: Authorization`).

`demo=video` plays an HLS stream, one with alternate audio and subtitles, and an `.mp4`, with `header=` and `lang=`.

## A film's dialogue: the centre mix (`centerMix`)

A film keeps its dialogue in the centre channel of its 5.1 or 7.1, and on a television's own speakers the usual fold
to stereo sets it under the music and effects. `centerMix: Prop[Double]` on a `VideoView` (or an `AudioPlayer`) is
the centre's level in that fold, in dB over the usual, as Kodi's "Downmix: Center Mix Level" is: 0 is the usual fold,
+3 and +6 bring the voices up over the rest, and it goes from -12 to +12. It is heard at once, as it plays.

```scala
video.centerMix @= 6.0
settings.visible := video.centerMixSupported()   // hide the setting where it can do nothing
video.centerMixing.attach(on => ...)             // whether it is being heard now
```

The fold is ITU-R BS.775's (`everyui.audio.Downmix`): each side takes its own front channel whole, the centre at
-3 dB times 10^(dB/20), its own surrounds at -3 dB (7.1's side and back pairs alike), a 6.1's back centre at -6 dB to
both; the LFE is left out, as BS.775 and a Dolby decoder's Lo/Ro leave it. Nothing is normalised, so at 0 dB it is as
loud as the usual fold (a browser's own, exactly); what comes out is kept under full scale instead of clipping. It
applies only to a source with a centre among more than two channels: a stereo or mono source is untouched.

Where the output plays every channel itself (an AV receiver taking 5.1 or 7.1 as PCM) nothing is folded: the centre
is lifted in place by the same decibels (`Downmix.lift`, 10^(dB/20), a negative level lowering it), gliding over the
same 20 ms, and every other channel goes to the receiver exactly as it came, so its own bass management and room
correction work as ever. The limiter is the centre's alone, so a raised centre never ducks the music and effects
around it: it keeps the centre under -0.2 dBFS (or under what it came in at, where that was louder still) and gives
back over 0.2 s, and it only ever takes back what the centre was raised by. At 0 dB every sample passes bit for bit.
Which it is, fold or lift, is decided from what the output says it takes as PCM (`everyui.audio.CenterRoute`): two
channels folds, more (or an output that does not say) lifts.

- **Android**: the fold and the lift are done in Media3's audio path (`CenterMixer`, an AudioProcessor in the player's
  `DefaultAudioSink`, ahead of the spectrum tap, the player's volume and the loudness enhancer), on the decoded PCM in
  all its channels (16-bit or float) before Android would fold it. For the fold, a limiter the two sides share takes
  its gain down at once to what a peak needs to stay under -0.2 dBFS and gives it back over 0.2 s; a change of level
  glides over 20 ms. It folds where the sound goes out as two channels: speakers, headphones, Bluetooth, or an HDMI
  sink whose PCM profiles say two (a television; Android 13's route for media, else what is connected), and lifts for
  an HDMI sink that takes 5.1 or 7.1 as PCM (an AV receiver). A level change is heard after what the AudioTrack already
  holds.

  **Sound the output would take still encoded** (AC-3, E-AC-3, E-AC-3 with Atmos, DTS, TrueHD, passed through to a
  receiver or a television to decode) never reaches the player's audio path, so while `centerMix` is not 0 it is
  decoded here instead (`CenterGate`, the sink saying it cannot take the format while the level is not 0, so Media3
  picks the device's decoder), wherever the device has a decoder for it: AC-3 and E-AC-3 on most Android TV devices
  (E-AC-3 with Atmos as its 5.1 or 7.1 bed), AAC always, TrueHD and DTS only where a decoder is listed. The receiver
  then gets the decoded channels as multichannel PCM, the centre lifted (or, over a link that takes only two channels
  as PCM, such as ARC or optical, the fold). What is lost meanwhile is what only the receiver's own decoder makes of a
  bitstream: Atmos's height channels and objects and DTS:X's, which go as their bed, and the receiver's own Dolby or
  DTS processing; the receiver shows PCM. At 0 (the default) nothing is refused, and a receiver gets its bitstream
  exactly as before, Atmos and DTS:X untouched. A format with no decoder here stays passed through, and
  `centerMixSupported` is false for it (and for sound that can be neither decoded nor passed through).

  The switch happens where it is playing, both ways, the position kept: on the change from 0 the audio renderer
  (`CenterAudioRenderer`) takes the format it is playing as though it had just come, Media3's own way through a change
  of format mid-stream, so the next sample is decoded (or passed); the sink plays out what it holds and makes a new
  AudioTrack. Going to PCM leaves a gap of a fraction of a second; going back to the bitstream, when the level is set to
  0 again, about a second on the Google TV Streamer, as the output reopens for it (and the receiver or television
  locks onto the bitstream again). An app whose viewers step through 0 often may rather keep a small level than 0.

  It logs which: `media: centre mix: 5.1 (LRCElr) at 48000 Hz folded to stereo for HDMI` or `... lifted in 5.1 for
  HDMI of 8 channels`, `media: centre mix: decoding E-AC-3 in 6 channels here from now` and `... passing E-AC-3 in 6
  channels through as it is from now` at a switch, `media: centre mix: decoding E-AC-3 in 6 channels here for HDMI,
  rather than passing it through` or `... passed through to HDMI as it is, while the centre is at 0` (or `with no
  decoder for it here`) as each AudioTrack is made, and a second after each change `media: centre mix at +6.0 dB:
  centre in -15.0 dBFS, out left -12.1 right -12.1 dBFS, limited 0.0 dB` (lifted: `media: centre mix at +6.0 dB,
  lifted: centre in -15.0 dBFS, out -9.0 dBFS, limited 0.0 dB`).
- **Web**: a Web Audio graph (`CenterFold`): the element's `MediaElementSource`, made up to 5.1 (mono to the centre,
  stereo to the front pair), split, each channel into a side at its share, and through a soft limiter (a
  WaveShaperNode: as it is up to 0.8, then bent towards -0.2 dBFS, never reaching it) to the destination. Taking an
  element's sound into Web Audio cannot be undone, and Web Audio hears nothing of a cross-origin file that has not
  allowed the page (CORS): it plays on, silent. So nothing is taken until a level other than 0 is first asked for, and
  then only while the page may make sound, its output plays two channels, and the source is one Web Audio may hear: an
  HLS stream through hls.js (its `blob:` is the page's own) or a file from the page's origin. A file from another
  origin is not supported (`centerMixSupported` is false for it) and plays as ever. A centre heard alone for two
  seconds is taken to be mono made up and played whole on both sides; `centerMixing` turns on once a centre has been
  heard with another channel. One limit: a file from another origin that does not allow the page, played later on an
  element already taken, plays silent, and says so (`scribe.error`); an element that will play such files after a
  same-origin one is best left at 0.
- **Roku, Apple TV, desktop**: not supported; `centerMixSupported` is false and the device or JavaFX folds as it
  does.

`demo=centermix` plays a 5.1 file (`src=`, `centre51.mp4` beside the page by default) with buttons for -6, 0, +3, +6
and +12 dB; `db=` starts it there, `steps=1` walks 0, +3, +6 and 0 every four seconds, `then=` plays another
file twelve seconds in, and `muted=1` plays it muted (still measured where it is decoded here; a bitstream passed
through is not the player's to mute).

## Listening aids: voice clarity and night mode (`voiceClarity`, `nightMode`)

Two more aids for hearing what is said, beside the centre mix, and unlike it they work on any source, stereo and mono
as much as surround. Each is a level from 0 (off, as it starts) to 1 on a `VideoView` (or an `AudioPlayer`), heard at
once as it plays, gliding to a new level over 20 ms rather than clicking.

```scala
video.voiceClarity @= 0.5                           // the voices' band lifted, the rumble cut
video.nightMode @= 1.0                              // the loud brought down, the quiet up
settings.visible := video.voiceClaritySupported()   // and nightModeSupported: hide them where they do nothing
video.voiceClarityOn.attach(on => ...)              // and nightModeOn: whether each is being heard now
```

- **Voice clarity** (`everyui.audio.VoiceClarity`) is an equaliser of three of RBJ's filters on every channel but the
  LFE: a high-pass at 90 Hz (Butterworth, 12 dB an octave), a dip of 3 dB about 300 Hz (Q 1) and a lift of 6 dB
  about 2.8 kHz (Q 1, about an octave and a third wide), each in proportion to the level (at 0.5 the lift is 3 dB, the
  dip 1.5 and the high-pass at 45 Hz). A level rather than a number of dB, because the three go together.
- **Night mode** (`everyui.audio.NightMode`) is a compressor that hears every channel as one (the loudest of them, as
  a root mean square over 5 ms), so a burst on one side brings every channel down together and the picture of the
  sound does not move. The level sets its threshold (-20 to -30 dBFS) and ratio (1:1 to 4:1) together, with a 6 dB
  soft knee, an attack of 5 ms and a release of 200 ms, and a makeup gain that leaves what is at -20 dBFS as it was:
  at 1, dialogue at about -27 comes up 5 dB, a quiet passage at -45 up 7.5, and a full-scale burst down 15.

The order is fixed: the centre mix (folded or lifted) first, then voice clarity, then night mode, then a limiter that
keeps every sample under -0.2 dBFS (the centre mix's, for any number of channels: its gain falls at once to what a
peak needs and comes back over 0.2 s), since both can make a peak louder (`everyui.audio.ListeningAids`). With both at
0 nothing is touched: every sample passes as it came.

- **Android**: `ListeningProcessor`, an AudioProcessor in the player's `DefaultAudioSink` right after the centre mix's
  `CenterMixer` (and before the spectrum tap, the player's volume and the loudness enhancer), on the decoded PCM,
  16-bit or float, in all its channels: two where the centre mix folded, all of them where it did not. It is in the path
  whenever the sound is PCM, so either aid is heard as soon as it is asked for; at 0 it copies every byte through.
  Sound the output would take still encoded (AC-3, E-AC-3 to a receiver or a television) is decoded here instead while
  either aid is above 0, where there is a decoder for it, exactly as for a centre mix other than 0, and passed through
  again once all three are back at 0; where there is no decoder for it, `voiceClaritySupported` and
  `nightModeSupported` are false. A level change is heard after what the AudioTrack already holds. A second after each
  change it logs `media: listening aids at voice clarity 1.00, night mode 0.00: in -24.4 dBFS, out -18.5 dBFS, night
  mode +0.0 to +0.0 dB, limited 0.0 dB`, and again two seconds later, settled.
- **Web**: in the centre mix's Web Audio graph (`CenterFold`, one graph and one `MediaElementSource` for an element,
  whichever of the three asked first), between its fold and its limiter (`ListeningNodes`): voice clarity as three
  BiquadFilterNodes (the same RBJ filters) and night mode as a DynamicsCompressorNode set as close to the shared one as
  it goes, with a gain after it that makes up as the shared one does. The browser's compressor hears peaks rather than
  a root mean square, starts its knee at its threshold, looks 6 ms ahead (the sound comes 6 ms later while the graph is
  in) and quickens its release as it reduces, so its threshold is set 3 dB higher and half a knee lower: a steady tone
  settles within about 0.3 dB of the shared curve, and the timing differs a little. The rules for taking the element are
  the centre mix's: nothing is taken until a level other than 0 is first asked for, and then only while the page may
  make sound, its output plays two channels, and the source is one Web Audio may hear (an HLS stream through hls.js or
  a file from the page's origin); a file from another origin is not supported and plays as ever. A second after each
  change it logs `media: listening aids at voice clarity 1.00, night mode 0.00: in left -24.4 right -24.4 dBFS, out left
  -18.5 right -18.5 dBFS, night mode -0.4 dB and +0.0 made up, peak out -15.4 dBFS` (the browser's reduction, then the
  shared makeup).
- **Roku, Apple TV, desktop**: not supported; `voiceClaritySupported` and `nightModeSupported` are false.

`demo=listening` plays a stereo file (`src=`, `listening.mp4` beside the page by default) with buttons for each aid
off, at half and at full; `clarity=` and `night=` start it there, `db=` sets a centre mix ahead of them, `steps=1`
walks clarity on and off, then night mode, both, and neither, every four seconds, and `muted=1` plays it muted (still
measured where it is decoded here).

## Sound: players that take turns, and effects (`AudioPlayer`)

Every platform but one mixes as many `AudioPlayer`s as play at once. A Roku plays one Audio node at a time: a second
asked to play fails ("player: only one playing instance supported"). There they take turns
(`everyui.audio.PlayerStack`): the newest to play takes the player, and the one it took it from stops where it was and
waits; when the newcomer stops, ends or fails, the one under it plays on from where it stopped. One whose `playing` is
turned off while it waits only leaves the stack. Each hand-off is logged:

```
[ui] audio: uiMedia5 clip.mp3 takes the player from uiMedia1 questions2.mp3
[video] uiMedia1 stopped at 41 s, its place kept
[ui] audio: uiMedia1 questions2.mp3 back from uiMedia5 clip.mp3
```

On a Roku an Audio node's `playing @= false` stops it with its place kept, rather than pausing it (a paused one may
still hold the player), and plays on from there, to the second, when turned on again. A player that fails is reported
as an error nothing caught (`[error] audio: uiMedia5 failed: -5 player: … (https://…)`), so it counts in
`PerfSession.errors`; one refused the player it was just handed, the one before not yet having let it go, asks once
more a quarter of a second later before it is. Video nodes do not take turns.

**Effects** are the short sounds over everything else (a cheer, a groan, a click): `effect` set before the source.

```scala
val cheer = new AudioPlayer
cheer.effect @= true
cheer.volume @= 0.85
cheer.source @= Platform.resolvePath("/sounds/cheer.wav")   // plays now, once
cheer.source @= ""                                          // and again: through "" ...
cheer.source @= Platform.resolvePath("/sounds/cheer.wav")   // ... or `playing` off and on
```

An effect plays once each time its source is set (or `playing` is turned on), from its start, and says nothing of
its end; it never takes the player. On a Roku it is SceneGraph's SoundEffect node, which mixes with whatever an Audio
or Video node plays, and which plays **only a WAV on the device**: a file in the channel's package (`pkg:/`), or under
`tmp:/` or `cachefs:/`. Each source's node is kept loaded for the next play, the eight used last at most. Its volume is a
share of the TV's own menu volume (Settings, Audio), and where that is off an effect is silent. A Roku plays only a
few at once; one past them is logged and not played (`[video] effect … not played: too many sounding at once`).

An effect a Roku cannot mix (an MP3, an address) is reported once (`[error] audio: uiMedia3 effect https://…/fx.mp3
is not a WAV, which a SoundEffect cannot play: played through the player instead`) and played through the player in
its turn, stopping what plays there until it is done. Elsewhere it plays as any sound does, and the log says once that
a Roku could not mix it (`[ui] audio: effect … is not a WAV: a Roku plays it through its one player …`), so it is seen
while building on the desktop.

So an app ships its effects as WAVs in the Roku's package (16-bit PCM; mono at 22,050 Hz keeps a second of sound to
about 43 KB) and points its effects there on the Roku, where the other platforms may go on using the MP3s they had.
EveryUI converts nothing: the sources are only known as the app runs (often a server's files), and no decoder in the
build would see them; and the one pure-JVM MP3 decoder (JLayer, last repackaged in April 2024) is LGPL.

`demo=audio` plays a looping track, a clip that takes the player from it and gives it back, and two effects over both,
logging each step with who has the player; `unfit=1` adds an effect from an address.

## Captions: the device's setting (`everyui.text.Captions`)

`Captions.device` is the viewer's own caption setting, `Option[CaptionPreference(mode, language)]`: the mode
(`CaptionMode.CaptionsOn`, `CaptionsOff`, `CaptionsOnReplay`, `CaptionsWhenMuted`, `CaptionsAutomatic`) and the
language they are wanted in, two letters, where the device keeps one. `Captions.listen(p => ...)` hears it change
(it answers the function that stops it); `Captions.supported` says whether the platform tells at all.

| Platform | Where it comes from | Change heard | Writable |
| --- | --- | --- | --- |
| Roku | roDeviceInfo: `GetCaptionsMode()` (On, Off, Instant replay, When mute) and `GetPreferredCaptionLanguage()` | yes: Main's roDeviceInfoEvent `isCaptionModeChanged` | yes |
| Android | CaptioningManager: `isEnabled` (On or Off) and `getLocale` | yes: its CaptioningChangeListener | no |
| Apple TV | MediaAccessibility: `MACaptionAppearanceGetDisplayType` (Always On, Automatic, or off) and the first selected language | yes: its settings-changed notification (the Swift runtime is not compiled yet) | no |
| Web, desktop | none: a browser and JavaFX say nothing of it (`device` None) | never | no |

**Writing it.** `Captions.setDevice(mode): Boolean` writes the device's mode where the platform lets an app
(`Captions.writable`): only a Roku, whose certification (4.8) asks an app with captions for On, Off, On instant replay
and On mute (a Roku TV's) in its options menu. It answers false where it was not written (every other platform, and
`CaptionsAutomatic` on a Roku). What it writes is heard by `listen` as the viewer's change, and a video goes back to
the device's captions.

```scala
// the four rows of an options menu's Captions item, offered where Captions.writable
Captions.setDevice(CaptionMode.CaptionsOn)          // On
Captions.setDevice(CaptionMode.CaptionsOff)         // Off
Captions.setDevice(CaptionMode.CaptionsOnReplay)    // On instant replay
Captions.setDevice(CaptionMode.CaptionsWhenMuted)   // On mute (a Roku TV)
```

**A video's captions** are `captions: Prop[TextChoice]` on a `VideoView` (or an `AudioPlayer`):

- `TextChoice.TextFromDevice` (what it starts as): the device's setting decides, by `Captions.choose`: captions on,
  the track in its caption language, else the first; off, none. A text language asked for through `languages` that
  the stream has is the app's choice over it. Where the device says nothing (a browser, the desktop), none.
- `TextChoice.TextOff`: none, whatever the device says.
- `TextChoice.TextTrack(id)`: that track, whatever the device says.

A choice holds for every source the view plays after, until another is made; a change the viewer makes to the device's
setting while it plays is theirs, and puts it back to `TextFromDevice`.

```scala
video.captions @= TextChoice.TextTrack(track.id)
video.captions @= TextChoice.TextOff
video.captions @= TextChoice.TextFromDevice
```

`textTrack: Prop[Option[String]]` is the same choice as an id, kept for code written before `captions`, and mapped both
ways: **None is `TextFromDevice`** (the device decides, which may well show captions), `Some("")` is `TextOff`, and
`Some(id)` is `TextTrack(id)`. So `textTrack @= None` does not turn captions off: say `TextOff`.

- **Roku.** Captions are drawn by the device's caption mode, which a channel's `globalCaptionMode` writes and which
  outlives the channel. As Roku's closed-caption guidance and its certification (4.8) have it, the app's choice is
  that setting: `TextTrack` writes it On (and picks the track), `TextOff` writes it Off, and neither is put back
  afterwards; `TextFromDevice` leaves it as the viewer set it, On replay and When mute to the Video node. The choice is
  put to each source as it opens and again once it plays. Each is said on the console: `[captions] <video> the
  device's, mode On, language 'en', track 't2'` or `[captions] <video> chosen '<id>', device mode Off, ...`,
  `[captions] device mode Off written On`, `[captions] device mode set <mode> by the app: true`, and `[captions]
  device mode now <mode>` as the viewer changes it.
- **Android.** The choice is the app's alone (CaptioningManager cannot be written). With `TextFromDevice`, the device's
  caption language is told to the track selector (a track in it reads from the start) and, once the tracks are known,
  the first text track stands in where none is in it; with the device's captions off, a track the file flags as its
  default is not read for that alone (forced subtitles still are).
- **Web, desktop.** No device setting: `TextFromDevice` reads none.

`demo=captions` shows the device's setting as it changes, over a video whose first button steps `captions` through the
device's, none and each track, and whose second writes the device's mode where it may (`src=<stream>`); it logs
`captions device <mode> <language>`, the rule's choice once the video is ready, and `captions setDevice <mode>
<written>`.

## Web builds: JavaScript, WebAssembly, or both

The `sbt-everyui` plugin builds a web app as JavaScript, as WebAssembly, or as both, with a loader in the page that
runs the WebAssembly build where the browser can and the JavaScript one elsewhere. The WebAssembly build runs heavy
layout and data work 2.5 to 3.7 times faster (the DataWrangler dashboard, measured). It downloads about as many
bytes, so it does not open any sooner.

`project/plugins.sbt`, beside Scala.js:

```scala
addSbtPlugin("com.outr" % "sbt-everyui" % "0.1.0-SNAPSHOT")
```

(EveryUI's own build compiles the plugin's sources into its meta-build, `project/plugins.sbt`; an app takes the
published plugin.)

It is on every Scala.js project. The app chooses with `everyuiWebTarget`, and its bundle task writes what
`everyuiWebScripts` links for it, with the page, which loads the app with `<script src="main.js"></script>` (`/main.js`
too):

```scala
lazy val app = project.in(file("app"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    // ... as in sbt, above
    everyuiWebTarget := WebTarget.Both,
    webBundle := Def.uncached {
      val out = baseDirectory.value / "out"
      IO.delete(out)
      everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
      // ... fonts, icons
      out
    }
  )
```

| `everyuiWebTarget` | What `writeTo` writes | Where it runs |
|---|---|---|
| `WebTarget.Js` (the default) | `main.js`, loaded as it always was | every browser |
| `WebTarget.Wasm` | `wasm-<hash>/` and `everyui-loader.js` | a browser with WasmGC, exnref and the JS string builtins; elsewhere the console says what it lacks, and nothing runs |
| `WebTarget.Both` | `main.js`, `wasm-<hash>/` and `everyui-loader.js` | WebAssembly where the browser can, JavaScript elsewhere |

`sbt 'set app/everyuiWebTarget := WebTarget.Js' app/webBundle` builds one another way, without editing the build.

`everyuiWebStage` says how far both builds are linked: `WebStage.Full` (the default) is `fullLinkJS`, a release;
`WebStage.Fast` is `fastLinkJS` for main.js, and the WebAssembly build linked with `fastLinkJS`'s configuration, for
development: `sbt 'set app/everyuiWebStage := WebStage.Fast' app/webBundle`. A fast build is not minified (over the
wire, main.js a third larger and main.wasm twice the size, and both run slower), so the bundle task's log says which
it is: `[sbt-everyui] web bundle: fast link (development)` or `full link (release)`. On the example app the link
itself is 1.0 s fast against 1.4 s full for main.js, and 0.9 s against 1.3 s for main.wasm, out of about 6 s for a
bundle after a one-line change, so most of a web build's time is elsewhere.

`everyuiWebGuests` is the web guests' files out of their modules (`everyui-monaco/<version>/`,
`everyui-terminal/<version>/`, and everyui-web's own `everyui-hls/<version>/`), a directory to copy next to the page:
`IO.copyDirectory(everyuiWebGuests.value, out)`. `everyuiWebFonts` is EveryUI's fonts out of the everyui-web jar, with
the app's own (`everyuiAppFonts`), a directory the faces are taken from (below), or, for a page that writes its own
`@font-face` rules, to copy as `fonts/`. Both are `everyui.sbt.ModuleFiles`, which a build without the plugin can call
itself.

An app with a face of its own (`Faces.register`) names its font files in `everyuiAppFonts`, and `everyuiWebFonts` (and
`everyuiFonts`, for the other platforms) copies them in beside EveryUI's. A file with the name of one of EveryUI's
(`OpenSans-Regular.ttf`), or two of the app's with one name, fails the build, naming each, rather than one font quietly
taking another's place.

The JavaScript build is the project's own `fullLinkJS`, as its `scalaJSLinkerConfig` sets it (a script, or an ES
module). The WebAssembly build (`everyuiWasmLinkJS`) links the same code with that configuration made an ES2022
module with `useWebAssembly`, into `target/everyui-wasm`; `Both` links twice, so a bundle takes longer. Every script's
URL carries a hash of its contents, so a new bundle is a new URL no browser serves from its cache. `writeTo` answers a
hash naming the build, which `WebApp.write` takes as its `version`.

The loader (`everyui.sbt.WasmLoader`):

- runs the WebAssembly build where the browser has WasmGC, exception handling with exnref, and the JS string
  builtins (it tests the browser for each, not its name), and the JS build everywhere else;
- falls down to the JS build if the WebAssembly one fails to start, clearing what it put in `#app`;
- takes `?wasm=false` (or `?wasm=true`) in the page's URL to choose, whatever the browser has, after a hash route
  (`#/runs?wasm=false`) too;
- says in the console which build it loaded and why (`[everyui] loading the WebAssembly build v…: …`, then
  `[everyui] running the …`).

`Platform.build` (`"wasm"` or `"js"`) says which build runs, and so does `window.everyuiBuild` where the loader is in
the page.

The server must send `main.wasm` as `application/wasm` (spice does), and a Content-Security-Policy must allow
`'wasm-unsafe-eval'`. Source maps are not copied; a browser asks for them only with its developer tools open.

### The faces (`everyuiWebFaces`)

An app declares the faces it draws with in its build, EveryUI's and its own, and the build ships each the way a
browser fetches least and writes the page's `@font-face` rules and preloads for them. `everyuiWebFaceFiles` writes
them, once the scripts are written (it reads the icons out of them):

```scala
everyuiAppFonts := Seq("Light", "Regular", "Medium", "Bold").map(w => baseDirectory.value / "fonts" / s"Roboto-$w.ttf"),
everyuiWebFaces := Seq(
  WebFace("Roboto", 300, "Roboto-Light.ttf"), WebFace("Roboto", 400, "Roboto-Regular.ttf"),
  WebFace("Roboto", 500, "Roboto-Medium.ttf"), WebFace("Roboto", 700, "Roboto-Bold.ttf").later,
  WebFace.MaterialIcons, WebFace.RobotoMono),
webBundle := Def.uncached {
  val out = baseDirectory.value / "out"
  IO.delete(out)
  everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  everyuiWebFaceFiles.value.writeTo(out)   // fonts/*.woff2, and their rules and preloads in index.html
  // …
}
```

A `WebFace` is the family, weight and slant the app's `Face` names (`italic = true` for an italic one), and its file
among `everyuiWebFonts` (EveryUI's and `everyuiAppFonts`). EveryUI's are `WebFace.OpenSans`, `OpenSansSemiBold`,
`MaterialIcons` and `RobotoMono`; `everyuiWebFaces` is `WebFace.bundled` (all four) unless the app says otherwise.
Each is shipped as WOFF2, cut to what the app draws (`glyphs`, a `WebGlyphs`):

| `glyphs` | What is shipped | For |
|---|---|---|
| `WebGlyphs.Scripts` (the default) | every glyph, in slices by script (`<file>.latin.woff2`, `.latin-ext`, `.vietnamese`, `.cyrillic`, `.greek`, `.other`), each rule naming the characters its slice holds (`unicode-range`); the browser fetches only the slices the words on the page need | words |
| `WebGlyphs.Reached` | only the icons main.js or main.wasm names (the characters in Unicode's private use areas there; a `MaterialIcon` the app reaches is its codepoint), in one file; none at all, and no rule, where it names none | an icon face drawn by codepoint (`WebFace.MaterialIcons`) |
| `WebGlyphs.All` | every glyph, in one file (a CFF face, `.otf`, is always shipped so) | anything else |

A slice keeps every glyph's number and the face's layout tables, so kerning, ligatures and accents are the whole
face's within it; the combining accents are in every slice, so a letter and its accent are drawn from one file. A
pair of letters from two slices (Ł and ó in "Łódź") is not kerned, as with any face served in slices. On Linux, where
the browser hints faces itself (FreeType's autohinter), a slice or icon file is hinted from what it holds, so its
edges can differ by a shade from the whole face's; the outlines and advances are the face's own.

A face at the `start` (the default) is preloaded with the page (its Latin slice, or its one file) and waited for before
the first frame; one declared `.later` is fetched only when words on the page are drawn in it, and never waited for,
so a face the app declares but does not draw costs nothing. The runtime reads which is which from
`<meta name="everyui-fonts">`, which the build writes beside the rules. A face the app registers (`Faces.register`)
that the build declared is the page's already; one it did not is loaded from `fonts/<file>` as before, and the console
says so. A page that still writes an `@font-face` rule for a family the build declares fails the build, since that face
would be fetched twice: take the page's rules out.

Measured on NaboTV (six Roboto faces of its own, four at the start, with Material Icons; Open Sans, Roboto Black and
italic, and Roboto Mono later): the faces fetched at the start went from 643 KB (whole TrueType, brotli) to 139 KB
(Material Icons 16 KB for its 270 icons, a Roboto Latin slice 29 to 30 KB), and the first words on a cold load over
"Slow 4G" with the CPU four times slower from 22.5 s to 13.5 s (the median of five runs each).

To move a page that writes its own rules: take its `@font-face` lines out, name the app's faces in `everyuiWebFaces`
(and their files in `everyuiAppFonts`), and in the bundle task write `everyuiWebFaceFiles.value.writeTo(out)` after the
scripts in place of copying `everyuiWebFonts` to `out / "fonts"`. `everyui.sbt.WebFaces` is the same for a build
without the plugin: `WebFaces(fontsDirectory, faces).writeTo(out)`.

### Behind a CDN: `WebBundle.seal`

A bundle served through a CDN is sealed, so that everything but `index.html` can be cached for good and a new
release is never served stale. `WebBundle.seal(out)` is the bundle task's last step, once everything is in `out`:

```scala
webBundle := Def.uncached {
  val out = baseDirectory.value / "out"
  IO.delete(out)
  everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  everyuiWebFaceFiles.value.writeTo(out)
  IO.copyDirectory(everyuiWebGuests.value, out)
  // l10n, images, WebApp.write, …
  WebBundle.seal(out)
  out
}
```

- Every directory at the top of `out` (`fonts/`, `l10n/`, `images/`, `everyui-hls/…`, the WebAssembly build's
  `wasm-…/`) moves under `a/<hash>/`, the hash being of all the files in them. Dot directories (`.well-known`) stay,
  and so do those named in `seal(out, keep = Seq("docs"))`.
- The scripts the staging wrote are named by a hash of their bytes: `main.<hash>.js`, `everyui-loader.<hash>.js`,
  `webapp.<hash>.js`.
- What names them is rewritten: `index.html` (its script tag, `url(fonts/…)`, an icon's `href`), the loader, and
  `manifest.webmanifest`'s icons: a path straight after a quote or `url(`, with `./` or `/` before it or nothing.
- The page names the one base for the runtime, `<meta name="everyui-assets" content="a/<hash>/" data-dirs="…">`, and
  everything everyui-web loads by path goes through it (`everyui.render.WebAssets`): fonts, translations
  (`Strings.source`), pictures (`Platform.resolvePath`), hls.js, and the web guests' scripts (Monaco, the terminal).
  The main script's own bytes are not touched.
- `index.html` and the other files at the top (`sw.js`, `manifest.webmanifest`, a favicon, `robots.txt`) stay where
  they are, to be revalidated. The service worker's cache (`WebApp.write`) is named by the bundle's hash.
- Last, every text file is compressed beside itself, Brotli and gzip ([below](#compressed-once-at-the-build-webbundleprecompress)).
- `web-assets.json`, beside `index.html`, lists what may be cached for good, for the host's
  `Cache-Control: public, max-age=31536000, immutable`, and what is to be revalidated (`no-cache`):

```json
{
  "version": "5d0c3e8a41f2",
  "immutable": ["a/3f2a9c01be47/", "main.9b1e0d6c22a4.js", "everyui-loader.71c4a5e0f3d8.js"],
  "revalidate": ["index.html", "manifest.webmanifest", "sw.js", "web-assets.json"]
}
```

A path ending in `/` is a prefix. `seal` answers the bundle's hash. Paths are relative to the page's base, so a
single-page host that answers a deep route (`/some/deep/path`) with `index.html` needs `<base href="/">` in it. A
bundle that is not sealed is staged exactly as before, and the runtime finds its files where it always did.

### Compressed once, at the build: `WebBundle.precompress`

`seal` ends by compressing the bundle: beside every text file (`js`, `mjs`, `wasm`, `css`, `html`, `arb`, `json`,
`svg`, `ttf`, `otf`, `txt`, `xml`, `webmanifest`) it writes `<file>.br` (Brotli at quality 11, its best) and
`<file>.gz` (gzip at level 9), each only where it is smaller. A bundle that is not sealed calls
`WebBundle.precompress(out)` itself as its last step; `seal(out, compress = false)` leaves it out, for a development
build, where Brotli's best over an unminified main.js takes seconds (NaboTV's 20 MB one, about 20). WOFF2 and pictures
are compressed already. Brotli is brotli4j (Google's encoder, natively; the plugin brings it).

A server compressing as it answers can afford only a fast setting: NaboTV's main.js is 1,305 KB at Brotli 11 against
1,892 KB at about 4 and 2,196 KB at gzip 9. So the host sends the precompressed file in the original's place to a
browser that takes it (browsers ask for `br` over HTTPS and from localhost), with `Content-Encoding: br` (or `gzip`),
the original's `Content-Type`, and `Vary: Accept-Encoding`; `web-assets.json`'s paths are the requests', and a file's
`.br` and `.gz` are cached as it is. Most hosts do this by configuration:

- nginx: `brotli_static on;` (the ngx_brotli module) and `gzip_static on;`.
- Caddy: `file_server { precompressed br gzip }`.
- Apache: rewrite to the `.br` where `Accept-Encoding` has `br`, with `Content-Encoding` set on `.br` files.
- A CDN in front of an origin: the origin serves the variants as above and the CDN keeps them by `Vary`; one that
  stores the files itself (S3 with CloudFront, say) is given each `.br` under the original's name with
  `Content-Encoding: br` in its metadata, or compresses on its own.
- Spice: EveryUI's own server (`server/`, `DirectoryPath`) sends a file's `.br`, else its `.gz`, where the browser takes
  it; an app's Spice server does the same with `Content.file(new File(file.getPath + ".br"), type)` and
  `setHeader("Content-Encoding", "br")`. Spice's Undertow leaves a response that says its encoding as it is, and
  gzips the rest as it answers.

### Size

The full link runs Scala.js's minifier on both builds: `everyuiWebMinify`, true by default (sbt-scalajs already
turns it on for `fullLinkJS`, so `withMinify(true)` in a build changes nothing, and `show` reports it on because it
is). `everyuiWebMinify := false` turns it off, for a readable release build. Closure (`withClosureCompilerIfAvailable`,
deprecated in Scala.js since 1.21 and to be removed) goes further on the JavaScript build only; the WebAssembly link
ignores it. Measured with Scala.js 1.22.0, NoModule, ES2021 (brotli -q 11, gzip -9):

| Build | DataWrangler dashboard: raw / gzip / brotli | EveryUI example app: raw / gzip / brotli |
|---|---|---|
| main.js, not minified | 5.32 MB / 634 KB / 412 KB | 7.03 MB / 805 KB / 514 KB |
| main.js, minified (the default) | 3.33 MB / 487 KB / 335 KB | 4.13 MB / 606 KB / 416 KB |
| main.js, Closure | 1.07 MB / 287 KB / 224 KB | 1.40 MB / 363 KB / 284 KB |
| main.wasm, not minified | 4.39 MB / 902 KB / 663 KB | 5.85 MB / 1.11 MB / 816 KB |
| main.wasm, minified (the default) | 1.58 MB / 473 KB / 353 KB | 1.99 MB / 597 KB / 447 KB |

The WebAssembly build's own main.js adds 48 KB (7 KB brotli) to 65 KB (10 KB). Closure takes 16 to 18 seconds to
link where the minified link takes one or two.

Keep the default. Serve the bundle compressed, Brotli where the browser takes it, from the files `seal` (or
`WebBundle.precompress`) wrote: that matters more than any of these. Closure's JavaScript is a third smaller again
over the wire, so an app that ships `WebTarget.Js` alone and counts every kilobyte may keep it on
`Compile / fullLinkJS / scalaJSLinkerConfig` while Scala.js still has it (with the minifier left on, which makes
Closure's output 2% smaller still); an app on `WebTarget.Both` sends most browsers the WebAssembly build, which
Closure never touches.

A minifier run over the linked main.js would take more off: esbuild took NaboTV's from 1,350 KB to 976 KB. None
runs inside sbt without a Node toolchain: esbuild and terser are Node's, and Closure Compiler (Java), given the
linked file, stops at the Unicode escapes Scala.js writes in identifiers (`_\uff3fself`), so it runs only as the
linker's own (`withClosureCompilerIfAvailable`, above), which takes NaboTV's main.js from 1,350 KB to 924 KB.

## The desktop, Android, the Roku and the Apple TV

Each platform is a project in the app's build that compiles the app's sources with that platform's backend artifact.
The `sbt-everyui` plugin (`project/plugins.sbt`, as for the web) does the rest: its tasks take what a build needs out
of the published artifacts, and its helpers package the app. Nothing comes from an EveryUI checkout.

```scala
addSbtPlugin("com.outr" % "sbt-everyui" % "0.1.0-SNAPSHOT")
```

`everyuiVersion` is the release the plugin belongs to, so the backends are named at it. Each backend carries the
network module with that platform's side of it (Spice over `java.net.http` on the desktop, over OkHttp on Android, the
device's own transfers on a Roku and an Apple TV), so none of them wants `everyui-network`. reactify comes as its
published jar: an app does not exclude `reactify_3`.

| Platform | The app depends on | and adds itself |
|---|---|---|
| desktop | `"com.outr" %% "everyui-desktop" % everyuiVersion.value` | `EveryUILibraries.javafx()` |
| Android | `"com.outr" %% "everyui-android" % everyuiVersion.value` | `EveryUILibraries.android`, from `EveryUILibraries.google`, and android.jar |
| Roku | `"com.outr" %% "everyui-roku" % everyuiVersion.value` | nothing |
| Apple TV | `"com.outr" %% "everyui-tvos" % everyuiVersion.value` | nothing |

JavaFX and the Android archives are `provided` in the backends, because only the app's build knows the form it needs:
JavaFX's jars are one per operating system (`javafx()` names base, graphics, controls and media for the machine the
build runs on; name others to add them, `javafx("base", "graphics", "controls", "media", "web")`), and an Android
archive is opened rather than put on a classpath (`Aars`).

The plugin's tasks, on every project:

| Key | What it is |
|---|---|
| `everyuiVersion` | the EveryUI release the plugin belongs to |
| `everyuiFonts` | EveryUI's fonts (and Material Icons' codepoints), out of the backend on the classpath, with the app's own (`everyuiAppFonts`): a directory to copy into the app's `fonts/` |
| `everyuiAppFonts` | the app's own font files (a face it registers with `Faces.register`), a setting, empty by default: copied in by `everyuiFonts` and `everyuiWebFonts`, and a name one of EveryUI's has fails the build |
| `everyuiAndroidRules` | the Android backend's shrinker rules, out of everyui-android, for `AndroidApk.build` |
| `everyuiCompiler` | the Roku and Swift compiler (everyui-compiler at `everyuiVersion`, fetched with the build's resolvers) and what it runs with |
| `everyuiEmitLibraries` | what the compiler compiles through with the app: everyui-roku or everyui-tvos, reactify, and Spice's durable client |
| `everyuiRokuRuntime` | the Roku runtime (BrightScript and SceneGraph), out of everyui-roku, for `RokuChannel.assemble` |
| `everyuiSwiftRuntime` | the Swift runtime and launcher, out of everyui-tvos, for `AppleApp.assemble` |
| `everyuiAppleInfoPlist` | the Info.plist an Apple app is packaged with: EveryUI's template unless the app sets its own |

And its helpers, plain objects an app's tasks call: `AndroidApk` (R8, aapt2, zipalign and apksigner, or bundletool for
an app bundle), `AndroidRuntime` (the classpath an APK is dexed from, with Scala 3's VarHandle lazy vals rewritten for
Android before 13, and the build failing if one is left), `Aars` (Android archives opened into jars and shrinker
rules), `RokuChannel` (emit, assemble, version, fallback fonts, the store package), `AppleApp` (emit and assemble) and
`EveryUILibraries`.

### Desktop

The desktop reads its fonts and images from `assets/` under the working directory (`--assets=dir` elsewhere):

```scala
lazy val desktop = project.in(file("desktop"))
  .settings(
    libraryDependencies += "com.outr" %% "everyui-desktop" % everyuiVersion.value,
    libraryDependencies ++= EveryUILibraries.javafx(),
    Compile / mainClass := Some("myapp.desktop.Main"),   // calls everyui.desktop.Desktop.launch
    Compile / run / fork := true,
    Compile / run / baseDirectory := baseDirectory.value / "out",
    Compile / run := (Compile / run).dependsOn(Def.task {
      IO.copyDirectory(everyuiFonts.value, baseDirectory.value / "out" / "assets" / "fonts")
    }).evaluated
  )
```

### Android

The backend is plain JVM bytecode compiled against android.jar, published as a jar rather than an Android archive (it
has no resources or manifest of its own), and the app's build dexes it with everything else. An app's activity extends
`everyui.activity.EveryUIActivity`.

```scala
lazy val androidSdk = file(sys.env.getOrElse("ANDROID_HOME", sys.props("user.home") + "/opt/android-sdk"))
lazy val androidJar = androidSdk / "platforms" / "android-37.0" / "android.jar"
@transient lazy val aars = taskKey[Seq[File]]("The Android archives' classes, as jars")
@transient lazy val apk = taskKey[File]("The app as a signed APK")

lazy val android = project.in(file("android"))
  .settings(
    libraryDependencies += "com.outr" %% "everyui-android" % everyuiVersion.value,
    resolvers += EveryUILibraries.google,
    libraryDependencies ++= EveryUILibraries.android,
    aars := Aars.open(streams.value.log.info(_), update.value.select(artifact = artifactFilter(extension = "aar")),
      Aars.room)._1,
    Compile / unmanagedJars ++= (aars.value :+ androidJar)
      .map(j => Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(j.toPath))),
    Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals"),
    apk := {
      val conv = fileConverter.value
      val assets = target.value / "apk-assets"
      IO.copyDirectory(everyuiFonts.value, assets / "fonts")
      AndroidApk.build(streams.value.log.info(_), androidSdk, "android-37.0", "37.0.0", "23", "37",
        AndroidRuntime.classpath((Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile)
          .filterNot(_.getName.endsWith(".aar")), "23", target.value / "android-runtime", streams.value.log.info(_)),
        baseDirectory.value / "AndroidManifest.xml", baseDirectory.value / "res", assets,
        Seq(everyuiAndroidRules.value, baseDirectory.value / "proguard-rules.pro"), target.value / "apk",
        baseDirectory.value / "out-apk" / "myapp.apk", version.value, 1, AndroidApk.debug)
    }
  )
```

The app's own `proguard-rules.pro` keeps its activity (`-keep class myapp.MainActivity { *; }`) and anything else the
manifest names; `everyuiAndroidRules` has the rest. A shipping build passes its release key as an
`AndroidApk.Signing`, a `Desugar` for the Java library on Android before 8, and `bundletool` for an app bundle.

### The Roku

The Roku runs no JVM: the app is compiled against everyui-roku (keeping its trees with `-Yretain-trees`), then
everyui-compiler translates its TASTy, with everyui-roku, reactify and Spice's durable client compiled through, into a
SceneGraph channel. Anything it cannot translate fails the build, each listed at its Scala source
(`-Deveryui.allowGaps=true` lets such a channel through to look at).

```scala
@transient lazy val emitRoku = taskKey[File]("The app as a Roku channel in out-roku/")

lazy val roku = project.in(file("roku"))
  .settings(
    libraryDependencies += "com.outr" %% "everyui-roku" % everyuiVersion.value,
    Compile / scalacOptions += "-Yretain-trees",
    emitRoku := {
      (Compile / compile).value
      val conv = fileConverter.value
      val out = RokuChannel.emit(streams.value.log.info(_), everyuiCompiler.value, (Compile / classDirectory).value,
        (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile), everyuiEmitLibraries.value,
        baseDirectory.value / "out-roku", baseDirectory.value, "MyApp")
      RokuChannel.assemble(out, everyuiRokuRuntime.value, Seq(baseDirectory.value / "images"),
        Seq(everyuiFonts.value), Nil, Some(baseDirectory.value / "channel-art"))
      RokuChannel.version(out, "1.0.0")
      out
    }
  )
```

`channel-art` holds the channel's icons and splash screens (`icon-fhd.png`, `icon-hd.png`, `icon-sd.png`,
`splash-fhd.png`, `splash-hd.png`, `splash-sd.png`). Lint the channel with brighterscript before sideloading it:
a channel the Roku refuses to compile wipes the dev channel and what it kept. `RokuChannel.packageChannel` makes the
store package on a keyed Roku.

### The Apple TV

The same compiler writes the app as Swift (`App.swift`), and `AppleApp.assemble` puts the runtime, the launcher, the
Info.plist, the icons and the assets beside it. The build itself is Xcode's (swiftc and actool, on a Mac); EveryUI's
`tools/tvos-run.sh` shows the commands.

```scala
@transient lazy val emitSwift = taskKey[File]("The app as Swift, with what an Apple TV build takes, in out-swift/")

lazy val tvos = project.in(file("tvos"))
  .settings(
    libraryDependencies += "com.outr" %% "everyui-tvos" % everyuiVersion.value,
    Compile / scalacOptions += "-Yretain-trees",
    everyuiAppleInfoPlist := baseDirectory.value / "Info.plist",   // or EveryUI's template, left unset
    emitSwift := {
      (Compile / compile).value
      val conv = fileConverter.value
      val out = AppleApp.emit(streams.value.log.info(_), everyuiCompiler.value, (Compile / classDirectory).value,
        (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile), everyuiEmitLibraries.value,
        baseDirectory.value / "out-swift", baseDirectory.value)
      AppleApp.assemble(out, everyuiSwiftRuntime.value, everyuiAppleInfoPlist.value,
        Some(baseDirectory.value / "Assets.xcassets"), Seq(baseDirectory.value / "images"), Seq(everyuiFonts.value))
    }
  )
```

## Launches (`Platform.launchArg`, `Platform.onLaunch`)

`Platform.launchArg(name)` is what the app was launched with, or "": `?name=value` in a page's URL, `--name=value` on
the desktop, `--es name value` on Android's intent, a Roku's launch parameter (`/launch/dev?name=value` over ECP, a
deep link's `contentId` and `mediaType`, or `pkg:/<name>.txt` in a test sideload).

A launch can also reach the app once it is running. `Platform.onLaunch` hears each, with what it was launched with,
and `launchArg` reads its values from then on (on a Roku, those it names replace those of the same name; on Android,
the new intent's extras are read). It returns what stops it being told. The launch the app started with is not one:
read it with `launchArg` as the app starts.

```scala
Platform.onLaunch(params => params.get("open").foreach(path => App.openLink(path)))
```

- **Roku**: ECP's `/launch/dev?…` (or `/input?…`, or a deep link from Roku Search) to the running channel, which comes
  as an `roInputEvent`, and the launch a suspended channel is brought back with (Instant Resume: `exit-app` and then a
  launch with parameters). What the device adds of its own (`source`, `splashTime`, `lastExitOrTerminationReason`,
  `source_ip_addr` and the like) is left out, and a plain return from the home screen, which says nothing else, is not
  a launch. The same launch heard both ways at once is told once. A deep link (`contentId`) also goes to
  `Platform.onDeepLink`, as before. The console says `[launch] arrived: open=/settings`.
- **Android**: an intent to the running activity (`onNewIntent`) with string extras (`am start --es open /settings`),
  a tap on a push notification's included. One with none (the launcher's icon again) is not one.
- **Web, desktop, Apple TV**: nothing launches a running app again, so it is never called.

`demo=launch` logs the launch it started with and each later one (`[ui] launch: later open=/settings | launchArg
open=/settings`).

## Away and back (`Platform.onAway`, `Platform.onResume`)

`Platform.onAway(f)` is told when nobody can see the app any more, and `Platform.onResume(f)` when somebody can again
after it was away; each returns what stops it being told. An app that hands the viewer to another app or site and asks
"did you watch it?" on the way back asks in `onResume`, and stops asking in `onAway`. Everywhere, away is told once on
leaving and resumed once on coming back, however many of the platform's signals say so (`everyui.app.Presence`), a
return is never told without a leaving before it, and the launch is not a return: the first time the app is seen
says nothing.

```scala
Platform.onAway(() => handedOver.foreach(_ => asking @= false))
Platform.onResume(() => handedOver.foreach(show => askWatched(show)))
```

- **Web**: away when the page is hidden (`visibilitychange`: another tab in front, the window minimised, a television's
  own home screen over the app on webOS and Tizen) or left (`pagehide`); resumed when it is shown again, or brought
  back from the back/forward cache (`pageshow` with `persisted`). A page left and loaded afresh on the way back is a
  launch, not a return: keep what the app needs to ask (the show handed over) in `everyui.store`, and ask from there as
  it starts. Focus is never used: devtools or another window in front of a page still in sight is not away.
- **Android**: away when the activity stops (another app has the screen, the device sleeps), not when it pauses (a
  dialog or picture-in-picture over it); resumed when it resumes after that.
- **Apple TV**: away when the application resigns active (another app opened over it with a URL, the TV's home screen
  or app switcher, the device sleeping) or goes into the background; resumed when it is active again.
- **Desktop**: away when the window is minimised, resumed when it is restored. Another window in front, or the focus
  lost, is neither.
- **Roku**: Instant Resume's resume handler is resumed, and the channel suspended away; a channel otherwise starts
  afresh each time.

`demo=lifecycle` logs each (`[ui] lifecycle: away 1`, `[ui] lifecycle: resumed 1`) and writes the counts on the screen.

## Sign-in, autofill and password managers

Every text field (`TextInput`, `TextArea`, and `InputView` beneath them) says what it asks for with `autofill`, an
`Autofill` from `everyui.component.types`. The browser's autofill, the platform's, and the password managers
(1Password, LastPass, Bitwarden, Google's, Apple's) fill a field by it and offer to save what was typed into it:

| `Autofill` | For |
|---|---|
| `Username`, `CurrentPassword`, `NewPassword`, `OneTimeCode` | signing in, signing up, changing a password, a code sent by text |
| `Email`, `Tel`, `Url` | contact details |
| `Name`, `GivenName`, `AdditionalName`, `FamilyName`, `Nickname`, `Organization` | a person |
| `StreetAddress`, `AddressLine1`, `AddressLine2`, `Locality`, `Region`, `PostalCode`, `Country` | an address |
| `CardName`, `CardNumber`, `CardExpiry`, `CardExpiryMonth`, `CardExpiryYear`, `CardCsc` | a payment card |
| `Birthday` | a date of birth |
| `Off` | none of these: a search box, notes, a message, a guest's name; the managers keep out of it |

A password hint hides what is typed, as `secure` does. Three more settings are rarely needed: `passkey` (a username
field that offers passkeys too), `section` (`"shipping"`, `"billing"`, or a name of the app's, when one form asks
for two addresses) and `fieldName` (the field's name to the managers when its hint alone does not keep it apart).

Put fields that belong together in an `everyui.form.Form`, with its `FormKind`, and tell it how it went:

```scala
import everyui.component.{Button, Column, Container, TextInput, TextView}
import everyui.component.types.Autofill
import everyui.form.{Form, FormKind}
import reactify.*

/** A sign-in: `signIn(email, password)(accepted)` asks the server, and `signedIn` shows what comes next. */
class SignIn(signIn: (String, String) => (Boolean => Unit) => Unit, signedIn: () => Unit) extends Container {
  private val error = Var("")
  private val form = new Form(FormKind.SignIn)
  private val email = new TextInput("Email") {
    autofill @= Some(Autofill.Username)   // the account: a username field, though it holds an email address
  }
  private val password = new TextInput("Password") {
    autofill @= Some(Autofill.CurrentPassword)
  }
  password.submitted.attach(_ => form.submit())
  form.submitted.attach { _ =>
    signIn(email.value(), password.value()) { accepted =>
      if (accepted) {
        form.succeeded()                  // the managers' cue to offer to save it
        signedIn()
      } else error @= "That email and password do not match"
    }
  }
  form.children += Column(12.0)(email, password, TextView.bound(error(), 14.0),
    new Button("Sign in", () => form.submit()))
  children += form
}
```

- **A sign-up** is `FormKind.SignUp` with `Autofill.Username` on the new account's email and `Autofill.NewPassword`
  on its password, so the managers offer a strong password and save it.
- **Changing a password** is `FormKind.ChangePassword`: `CurrentPassword`, then `NewPassword` (twice, for the
  repeat). Set the form's `hiddenUsername` to the account, so the new password is saved against it.
- **A password on a step of its own**, after the username on the step before: set `hiddenUsername` there too.
- **A contact or booking form** is `FormKind.Contact`: `Name`, `Email` and `Tel` on the booker's fields, `Off` on the
  message and on anyone else's details (a guest's name is not the user's).
- `submit()` sends the form once however the press arrives (the button, a field's Enter, the page's own sending).
  `abandoned()` says the user gave up; a form taken off the screen unsaid is abandoned.

On each platform:

- **Web.** The field's `autocomplete`, a `name` and an `id` to match, and the keyboard a phone shows (`inputmode`);
  `Off` also sets the markers 1Password, LastPass, Bitwarden and Dashlane keep out by. A `TextInput`'s field is
  named by its label (`aria-label`). A `Form` is a real `<form>`: Enter in a field, or a manager clicking its submit
  button, sends it (`submitted`; the page itself never goes anywhere), and `succeeded()` takes its fields out of the
  page, which the browser and the managers read as a sign-in done. A value a manager fills reaches the field's
  `value` (on `input` and `change`, on the field being left, and on Chrome's silent fill), and the field keeps its
  own colours under the autofill look. The keys a field types with are never kept from it, whatever the app makes of
  them; with a keyboard at hand a field being edited keeps the up and down arrows too, which work the autofill menus
  under it. Something the app did not make (a manager's menu, a frame) keeps its keys and its focus.
- **Android.** The field's `autofillHints`, its keyboard (`inputType`) and whether it is filled at all (Android 8 and
  later); `succeeded()` commits the autofill session, the save prompt, and `abandoned()` cancels it.
- **Apple TV.** Where the platform has no text field of its own a `TextInput` asks the platform's keyboard: a dialog
  whose field says what it asks for (`textContentType`), so a sign-in offers AutoFill from the Keychain and a nearby
  iPhone can fill it.
- **Desktop and Roku.** Nothing fills fields there. Sign in on another device (a code or a QR code), or hand the
  sign-in to the system browser.

### The site's side: web domains

A password saved on the site is offered in the app, and one saved in the app on the site, once each says it is the
other: Android's Digital Asset Links and Apple's associated domains. `everyui.sbt.AppDomains` (in `sbt-support/`)
writes both sides from the app's build, and `everyui.seo.WellKnown` answers the site's side from its server:

```scala
// the app's build
import everyui.sbt.AppDomains
val domains = AppDomains(Seq("example.com"),
  androidPackage = "com.example.app", androidFingerprints = Seq("AB:CD:..."),   // the store's signing key's SHA-256
  appleAppIds = Seq("TEAMID.com.example.app"),
  changePassword = "https://example.com/account/password")
AppDomains.write(webOut, domains)   // webOut/.well-known/: assetlinks.json, apple-app-site-association, change-password
IO.write(appleDir / "App.entitlements", domains.entitlements)   // webcredentials:example.com
IO.write(androidRes / "values" / "asset_statements.xml", domains.assetStatementsXml)  // with androidManifestMeta
```

```scala
// the app's server, before its other handlers
WellKnown.answer(webRoot, path) match {
  case Some(a) if a.location.nonEmpty => redirect(a.location)   // /.well-known/change-password
  case Some(a) => respond(a.status, a.contentType, a.body)       // JSON, never a redirect
  case None => ()                                               // on to the rest
}
```

NaboTV would name `nabo.tv` with the Play signing key's fingerprint and the Apple app's id; a site with no app
(matthicks.com) needs only `changePassword`, if it has such a page. The same association lets the Android app use the
site's passkeys (below).

### Passkeys (`everyui.passkey`)

A passkey is made and signed in with by the platform's own passkey manager; the server makes the options with its
WebAuthn library and verifies what comes back, and the app carries one to the other. Options are the JSON a server
library writes for the browser (`PublicKeyCredentialCreationOptionsJSON`, `PublicKeyCredentialRequestOptionsJSON`:
binary fields as base64url), bare or under `"publicKey"`. The answer is a `Task[PasskeyResult]`:
`Credential(json)`, the credential as the web's `PublicKeyCredential.toJSON()` writes it (what the server's library
verifies), `PasskeyCancelled`, `PasskeyUnsupported`, or `PasskeyFailed(message)`.

```scala
import everyui.passkey.{PasskeyResult, Passkeys}

if (Passkeys.supported) signInButton.event.click.attach { _ =>
  server.passkeyOptions().flatMap(options => Passkeys.get(options)).map {
    case PasskeyResult.Credential(json) => server.verifyPasskey(json)
    case PasskeyResult.PasskeyCancelled => ()
    case other => error @= other.toString
  }.start()
}
// registering one: Passkeys.create(server.registrationOptions()) the same way
```

`PasskeyCancelled` is the person closing the request or letting it time out; the web does not tell that apart from
having no passkey for the site, on purpose, and Android's "no credential" answers the same. `PasskeyFailed` carries the
platform's words, such as "InvalidStateError" for a passkey this authenticator already holds for the account (the
options' `excludeCredentials`). Start `create` and `get` from a click.

**Autofill.** On the web the site's passkeys can be offered in the username field's own autofill menu, beside saved
passwords: give the field `passkey @= true` (with `Autofill.Username`), and as the sign-in shows start
`Passkeys.autofill(options)`, which answers once the person picks a passkey there. `Passkeys.autofillable` says whether
the browser can (Chromium, Safari, Firefox). A page has one request at a time, so a `create` or `get` (a click on a
"Sign in with a passkey" button) ends the one waiting, which answers `PasskeyCancelled`; start `autofill` again if the
sign-in is still showing.

- **Web**: `navigator.credentials.create` and `get` (conditional mediation for `autofill`), over HTTPS or on
  `localhost`. The `rp.id` is the site's domain or one it is under.
- **Android**: Credential Manager (androidx.credentials, part of `EveryUILibraries.android`). Android 14 and later
  answer from the system's own, with nothing more to add. Before 14 an app that wants passkeys there adds Google Play
  services' provider itself, `"androidx.credentials" % "credentials-play-services-auth"` and what it pulls in; without
  it, `supported` is false there. The options' `rp.id` is the site's domain, and the site must say the app is its own:
  `AppDomains` (above) with the signing key's fingerprint, whose `assetlinks.json` also grants
  `delegate_permission/common.get_login_creds`. There is no autofill (`autofill` answers `PasskeyUnsupported`).
- **Desktop, Roku, Apple TV**: none; `supported` is false and every request answers `PasskeyUnsupported`. Sign in on
  another device, or in the system browser.

`demo=platform` makes a passkey and signs in with it, with options made on the page (`rp=<domain>` names the site),
logging the credential's JSON.

## Embeds

`everyui.component.Embed` is something the app does not draw itself, in a box the app lays out: a raw element the
platform lends (on the web the element is `embed.node.el`), or, given an `EmbedSite`, a web guest (a code editor, a
terminal) run from its files. On the web the guest runs in the app's own page; on Android and the desktop each embed is
a web view of its own, served from a private origin; where nothing can run it (Roku, tvOS, a desktop built without
javafx-web) the embed shows its `fallback` instead.

```scala
val site = EmbedSite("terminal", root = "xterm/5.5.0", page = "host.html", scripts = List("bridge.js"))
val term = new Embed("terminal", site, fallback = new CodeView(log()))
term.hear(text => handle(text))                  // what the guest says
term.send("""{"write": "hello"}""")              // said to it; waits for it to load
term.onSize((w, h) => term.send(s"""{"cols": ${(w / 9).toInt}}"""))
term.guestFocus = on => if (on) term.send("""{"focus": true}""")
term.exit @= Some(fileTree)                      // where Escape or a remote's Back hands the focus
```

The guest talks through `everyuiEmbed.channel(name)` (`send`, `onMessage`) and draws into
`everyuiEmbed.container(id)`, the embed's `id`. While the page's focus is in the guest every key is its own but the
chords the app keeps (`Keymap.embed(name, chords*)`); a key it does not want it gives back with `embed.handBack(key)`.

## WebView (`everyui.component.WebView`)

A page of the web drawn in place by the platform's own engine, for what is only ever published as a page (a YouTube
embed): Android's WebView, and on the web an `<iframe>` filling the box (`allow="autoplay; encrypted-media;
fullscreen"`, no border, out of the tab order). `Platform.webViews()` says whether there is an engine; where there is
none (the desktop for now, the Roku, the Apple TV) it is an empty box.

```scala
val page = new WebView("naboBridge")   // the page calls naboBridge.postMessage("...")
page.hear(message => handle(message))
page.source @= "/yt-embed?v=..."
page.run("player.playVideo()")         // waits on the web for the page to load
```

On the web how the two talk depends on the page's origin:

- **The app's own origin.** As on Android: `run` runs its script in the page (its window's `eval`), and the page's
  window is lent `<bridge>` with a `postMessage(string)` that the app hears. The bridge is put on the page's window as
  soon as it has one while it loads, and again as it finishes (each load is a new window), so a script run as the page
  is first read may be too early for it; say things from `load` on, or post to the parent (below). What `run` is given
  before the page has loaded is run once it has.
- **Another origin.** Nothing can be run in it, and nothing is lent. `run(script)` posts
  `{everyui: "run", script}` to the page, addressed to the origin of `source` alone, and the page decides what to do.
  What the page posts to its parent (`window.parent.postMessage(s, appOrigin)`) is heard, a string as it is and an
  object as its JSON; a message is heard only from this view's frame and the origin of its `source`.

A page of another origin must never run what it is sent: anything that can post to it could run script in it. It
should check `event.source === window.parent` and `event.origin` against the app's origin, and then act only on the
few commands it knows, matched exactly, refusing the rest:

```js
window.addEventListener('message', e => {
  if (e.source !== window.parent || e.origin !== 'https://app.example.com') return
  const m = e.data
  if (!m || m.everyui !== 'run' || typeof m.script !== 'string') return
  const seek = /^seekTo\((\d+(?:\.\d+)?),true\)$/.exec(m.script)
  if (m.script === 'player.playVideo()') player.playVideo()
  else if (seek) player.seekTo(Number(seek[1]), true)
})
```

`agent` does nothing on the web: the browser says who is asking. A WebView taken apart (`dispose`) lets its page go
(the frame is left on `about:blank`, so what it played stops) and stops listening to the app's window.
`demo=webview` shows `web/webview.html` (copied beside the build), or `src=<url>` for the same page served from another
origin.

`sandbox` (`Prop[Option[Set[String]]]`, `None` by default: no sandbox, as before) keeps a page apart from the app, as
an iframe's `sandbox` attribute does: `Some(flags)` allows only the iframe flags it names, `Some(Set())` nothing. Set
it before `source`; a change loads the page again, since an engine honours it only as a page loads. A sandboxed page
is neither told nor heard on any platform, whatever its flags: `run` does nothing, no bridge is lent, and what it posts
is not heard. For a file a user opened (HTML from a workspace), sandbox it with nothing, or with scripts alone, and
never with "allow-same-origin" beside "allow-scripts", which together let the page take its sandbox off:

```scala
val preview = new WebView("previewBridge")
preview.sandbox @= Some(if (scriptsOn) Set("allow-scripts") else Set.empty)
preview.source @= fileUrl
```

What each platform honours:

- **The web.** The iframe's `sandbox` attribute, exactly (the flags sorted, joined by spaces), set before the frame
  navigates and the frame sent to its `source` again as it changes. Without "allow-same-origin" the page has an origin
  of its own (`null`): it cannot reach its parent's document or origin, `document.cookie` and `localStorage` throw, and
  its requests are cross-origin to the app; without "allow-scripts" none of its script runs; forms, popups, top
  navigation, modals and the rest only as their flags allow, by the browser's rules.
- **Android.** Its WebView has no sandbox, so `WebSandbox` sets what comes nearest, again before the page loads and
  loading it again as it changes. Sandboxed at all: the bridge taken off the page's window; file and content access,
  geolocation and third-party cookies off; links that would leave for something other than a page (`intent:`,
  `market:`, `file:`, `content:`…) go nowhere, the rest staying in the view. By flag: JavaScript only with
  "allow-scripts"; DOM storage only with "allow-same-origin"; a form posted in place of the page refused (answered
  403, empty) without "allow-forms"; a new window (`window.open`, `target="_blank"`) opening nowhere without
  "allow-popups". It cannot give the page an origin of its own: the page keeps its address's, so that origin's
  cookies (one store for the whole app) still go with its requests, and with "allow-scripts" its script can read the
  ones not HttpOnly and call that origin as itself; a form sent by GET is a link to the WebView, and goes. Show a
  sandboxed page from an origin that holds nothing of the app's, or as a `data:` address.
- **The desktop, the Roku and the Apple TV.** No engine lends a page there (`Platform.webViews()` is false), so the
  view is an empty box and `sandbox` is ignored with it.

`demo=webview&sandbox=none` (or `scripts`, or flags by commas) shows `web/sandbox.html` sandboxed, a page that tries to
read its parent, the cookies and the storage and says what it found, on itself and posted to its parent; its Scripts
on and off button toggles "allow-scripts".

## Monaco (everyui-monaco)

`"com.outr" %% "everyui-monaco" % "0.1.0-SNAPSHOT"` (Scala.js) is Monaco, VS Code's editor, as components: a port of
flutter_monaco_editor with the same API in Scala. It brings Monaco 0.57.0's files as resources under
`everyui-monaco/0.57.0/`: fetched from npm with their integrity checked by the build, never committed.

```scala
import everyui.monaco.*
import everyui.monaco.Monaco.uiThread    // runs what follows a call where it completed, on the UI thread

val code = MonacoController(initialValue = source, language = "scala",
  options = MonacoEditorOptions(fontSize = Some(14.0), minimap = Some(MonacoMinimapOptions(enabled = Some(false)))))
val editor = new MonacoEditor(code)
editor.exit @= Some(fileTree)
code.onDidChangeContent.attach(text => changed(text))           // the user's edits; the text is kept here edit by edit
code.ready.foreach(_ => code.revealAndHighlight(MonacoRange(40, 1, 44, 1), lines = true))
code.setModelMarkers(List(MonacoMarker(MonacoRange(3, 5, 3, 9), MonacoMarkerSeverity.Error, "not found: value x")))
MonacoEditor.saveCommand(keymap) { write(code.value()); code.markSaved() }   // Primary+S, even inside the editor
MonacoThemes.followPalette()                                    // the app's palette, and follows Theme.palette
MonacoLanguages.registerDefinitionProvider("scala", MonacoDefinitionProvider(p => lookUp(p.value, p.position)))
```

`MonacoDiffEditor` / `MonacoDiffController` are the diff editor. `changes` (and `onDidUpdateDiff`) are its changed regions as
`MonacoLineChange`s, with their character changes; `checks` puts checkboxes in the modified gutter, a change's or a
line's, ticked as the app says, and `onGutterClick` hears a click on one with the change it belongs to;
`revertChange` and `acceptChange` put one side's lines of a change over the other's as one edit that can be undone:

```scala
val diff = MonacoDiffController(head, working, "scala")
diff.changes.attach(cs => diff.checks @= MonacoDiffCheck.forChanges(cs, c => !skipped(c), lines = true))
diff.onGutterClick.attach(click => click.change.foreach(toggle))
diff.revertChange(diff.changes().head)                          // roll one change back
```

What a tick means (a partial commit, say) is the app's.

Go to definition and a peek into another file need the app: `MonacoLanguages.registerModelResolver(uri => ...)` gives
Monaco a file's text and language when it needs its model (the peek's lines), and
`MonacoLanguages.registerEditorOpener((uri, range) => ...)` or the editor's `controller.onOpenLocation` opens the file
the app's way (its tab, `switchToModel`, the range revealed); with neither, Monaco stays where it is. A jump within
the file the editor shows is made by the bridge itself, and neither is asked (unless that jump fails). Every call answers as a `Future`; themes and
providers are global and reach every editor, including ones made later.

**Over a picture.** `MonacoThemes.followPalette(clear = true)` (or `MonacoTheme.transparent()`, with
`new MonacoEditor(controller, transparent = true)`) clears the editor's own ground: its background, the gutter, the
minimap and the overview ruler, in the editor and in both sides of the diff editor, so the picture shows behind the
code. What floats over the code stays solid, so no text ever shows through it: the hover, suggest, parameter hints,
find, the context menu and action list, the quick input, the sticky scroll (`followPalette` in the palette's
`surface`, the rest in its `raised`; `transparent()` in its base's own colours), a problem's peek and the peek view.
The diff editor's "hidden lines" bands stay clear, as nothing lies under them.

**Why the cursor moved.** `onDidChangeCursor` and `onDidChangeSelection` say what moved the cursor and why, beside
`onDidChangeCursorPosition` and `onDidChangeCursorSelection` (which still say only where): a `MonacoCursorChange` is
the position, the other cursors' (`secondaryPositions`), Monaco's `source` and its `reason` (`MonacoCursorReason`,
Monaco's `CursorChangeReason`); a `MonacoSelectionChange` the same of the selections. What Monaco says of each move:

| Move | `source` | `reason` |
|---|---|---|
| A click | `"mouse"` | `Explicit` |
| An arrow, Home, End, select all | `"keyboard"` | `Explicit` |
| Typing | `"keyboard"` | `NotSet` |
| A command's edit (Backspace) | its id (`"deleteLeft"`) | `NotSet` |
| Go to definition, Ctrl+Click, F12, a usage opened from a peek, within the file | `"code.jump"` | `NotSet` |
| The app's `setPosition`, `setSelection` | `"api"` | `NotSet` |
| Undo, redo | `"modelChange"` | `Undo`, `Redo` |
| The app's `setValue`, `executeEdits`; another editor's edit on a shared model, or one relayed into this copy | `"modelChange"` | `RecoverFromMarkers` |

`byUser` is true for the person's own moves (not `"api"`, nor a recovery or a flush), and `isJump` for a jump. A
jump within the file the editor shows (the model Monaco itself finds for the location's uri, however the uri is
written: an `@`, a space, a `%`, a `#`, a drive letter) is made by the bridge at once, the place selected and in the
middle as Monaco's code editor service would, under VS Code's name for it, `"code.jump"`; the editor opener
(`registerEditorOpener`, `onOpenLocation`) is asked only of another file, or should that jump fail, so a jump is
never dropped. On the
desktop and Android an edit made in another editor on a shared model is copied into this editor's own Monaco, and
moves its cursor as a recovery, never as the user's. A Ctrl+Click puts the cursor where it was clicked (`"mouse"`)
before it jumps, so the place to come back to is where the cursor was before the jump:

```scala
var here: Option[MonacoPosition] = None
code.onDidChangeCursor.attach { c =>
  if (c.isJump) here.foreach(p => history.push(file -> p))   // Navigate Back (the mouse's back button) comes back here
  here = Some(c.position)
}
```

**Shared models (an editor per tab, split views).** `MonacoModels` holds the documents by uri, as VS Code does: open a
file once and any number of editors show it, editing one text with one undo history and one dirty state, each editor
with its own cursor, selection, scroll and folding:

```scala
val main = MonacoModels.open("file:///src/Main.scala", source, "scala")   // open again: the same model, as it is
val left = MonacoEditor.of(main)                                          // or new MonacoEditor(MonacoController.forModel(main))
val right = MonacoEditor.of(main)                                         // a second view of the same document
main.dirty.attach(d => tab.marker @= (if (d) Some(dot) else None))       // also main.value, saved, onDidChangeContent
MonacoEditor.saveCommand(keymap) { write(main.value()); main.markSaved() }
main.setMarkers(compilerErrors, owner = "scalac")                         // on every editor showing it, now or later
left.controller.switchToModel(other.uri)                                  // and back: its cursor and scroll as they were
MonacoModels.close(main.uri)                                              // once its tabs have closed
```

`MonacoModels.get(uri)`, `dirty(uri)`, `all` (a `Val` of the open models by uri) and `onDidDispose` are there too, and a
model has `value`, `language`, `saved`, `dirty`, `onDidChangeContent` (the user's edits, from any editor),
`onDidChangeModelContent` (every edit), `setValue`, `setLanguage`, `markSaved`, `setMarkers` and `clearMarkers`. A
controller showing one says so (`shownModel`), and its `dirty`, `saved` and `markSaved` are the model's. A model lives
while the app holds it (from `open` to `close`) or any editor shows it: closed while one still does, it goes once the
last editor on it switches away or goes. Each editor keeps its own view of every model it has shown, whether shared or
made by `createModel`, and has it back as it shows that model again. `controller.disposeMonacoModel(uri)` on a shared
model is `MonacoModels.close(uri)`.

On the web every editor is one more editor in the page's one Monaco, on the one model: Monaco's code loads once
however many editors there are. On the desktop and Android each editor is a web view with a Monaco of its own (web
views share no objects), so each holds a copy of the model and an edit made in one is made in the others through the
app: the text, `dirty` and the markers are shared there too, but each copy keeps its own undo history, which holds
every edit whoever made it. Undo in one editor undoes the last edit in it, the other editor's included, and the others
follow; a redo after an undo made in another editor is not there, and Monaco's typing steps can differ from copy to copy.

**Serving it (web).** Copy the jar's `everyui-monaco/` directory next to the page (the gallery's `galleryBundle` task
in this build does it; `Monaco.site` points elsewhere, a CDN say). The bridge loads its own scripts with `<script src>`
and its styles with `<link>`: nothing inline. Monaco starts its workers from blobs, so a Content-Security-Policy needs:

```
script-src 'self'; worker-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:
```

(`'unsafe-inline'` for styles because Monaco sets style attributes itself.) Monaco runs under require.js, an AMD loader,
which defines the page's global `define` and `require`: a UMD library loaded after it registers itself as an AMD module
instead of a global. Link the bundle as `ModuleKind.NoModule` (as above), and load such libraries before the first
editor is made.

**Android and the desktop** compile it from source like the rest of their backends: add `monaco/src/main/scala`, fabric-io,
and the resources (`MonacoAssets.generate` in `project/`); on Android put `everyui-monaco/<version>/` under the APK's
assets. The desktop needs `javafx-web` and `desktop/web/src/main/scala` (FxWebGuest).

## Terminal (everyui-terminal)

`"com.outr" %% "everyui-terminal" % "0.1.0-SNAPSHOT"` (Scala.js) is a terminal as a component: xterm.js 6.0.0 with its
fit and web-links addons, over an `Embed` as Monaco is. It knows nothing of where the bytes come from: the app writes a
program's output in and sends what it gives out on, over whatever carries them (a socket to a pty on another machine,
say). xterm.js's files come as resources under `everyui-terminal/6.0.0/`, fetched from npm with their integrity checked
by the build, never committed. It needs nothing beyond everyui-web.

```scala
import everyui.terminal.*

val term = new Terminal(TerminalOptions(fontSize = 14.0, scrollback = 10000))
term.size.width := panel.size.width()
term.size.height := panel.size.height()
term.onInput.attach(data => socket.send(data))              // keys and pastes as a terminal sends them: Ctrl+C is \u0003
term.onResize.attach(s => socket.resize(s.cols, s.rows))   // the pty's window size, each time the grid changes
socket.onBytes(bytes => term.writeBytes(bytes))            // UTF-8, split anywhere; term.write(text) for text
term.title.attach(t => tab.label @= t)                     // what the program set
term.fontSize @= 15.0                                      // and term.theme, which follows Theme.palette until set
Terminal.keep(Chord("T", primary = true, shift = true))    // an app chord that still runs inside the terminal
```

It lays its grid out to its box (`dimensions`, `onResize`), keeps `scrollback` lines for the wheel or a finger, and
takes its colours from the palette (`TerminalTheme.of`: the text on the surface, the cursor in the accent, ANSI colours
for a dark or light ground). `selection` is the selected text; `copy()`, `paste(text)`, `selectAll()`, `clear()`,
`reset()`, `scrollToBottom()` and `scrollLines(n)` are there for a menu; `onBell`, `onLink` (a clicked address, opened
in the browser too while `openLinks`) and `onBinary` (old mouse reports) are heard.

**Keys.** While it has the focus every key is the program's, Tab, Escape and the arrows included, so no key of its own
takes the focus out. The app's shortcuts still run inside it exactly where they do inside a Monaco editor: a command
with `inEmbeds`, a chord left to terminals (`Terminal.keep`, which is `Keymap.embed("terminal", ...)`), a double-tap
of a modifier, and a remote's Back (to `exit`); those keys never reach the program. Copy is Ctrl+Shift+C (Cmd+C on a
Mac), through the platform's clipboard; paste is Ctrl+Shift+V (Cmd+V), the engine's own paste, bracketed where the
program asked for it.

**Serving it (web).** Copy the jar's `everyui-terminal/` next to the page: the sbt-everyui plugin's `everyuiWebGuests`
does it for every web guest on the classpath (Monaco too). The scripts load with `<script src>` and the styles with
`<link>`: nothing inline and nothing evaluated, so the policy needs only `script-src 'self'; style-src 'self'
'unsafe-inline'` (xterm.js sets style attributes). The scripts define their globals even where Monaco's require.js is
already in the page. The terminal is set in Roboto Mono, the page's own on the web (`fonts/`), and in the platform's
monospace face in a web view of its own.

**Android and the desktop** compile it from source like Monaco: add `terminal/src/main/scala` and the resources
(`TerminalAssets.generate` in `project/`); on Android put
`everyui-terminal/<version>/` under the APK's assets. The desktop needs `javafx-web` and `desktop/web/src/main/scala`.
Where no web engine runs (Roku, tvOS, a desktop without javafx-web) a line saying so shows in its place.
`terminal/demo` is its demo (`sbt terminalDemo/demoBundle`, `terminalDesktop/run`, `terminalAndroid/packageApk`).

## Logs (`everyui.log`)

A log as a terminal shows it, natively on every backend: `LogView` draws lines in their ANSI colours and styles, in
Roboto Mono, the newest at the bottom, from a few to tens of thousands of them (a `VirtualList` over the lines, which
makes only the rows in view). `LogPanel` is the same view with a toolbar: search, filter, level chips, wrap, follow
and clear. It is not a terminal (that is everyui-terminal): nothing is typed into it, and it runs where no web engine
does.

```scala
import everyui.log.*

val log = new LogView(LogOptions(maxLines = 50000, wrap = false, lineNumbers = true))
log.append(chunk)                          // a stream's pieces: a line shows once its newline comes
log.appendLine("done", at = System.currentTimeMillis(), level = Some(LogLevel.Info), source = "loader")
log.append(event)                          // a server's LogEvent: in seq order, each seq shown once
log.filter @= LogFilter(text = "timeout", levels = Set(LogLevel.Warn, LogLevel.Error))
log.search @= "socket"                     // log.searchCase, log.searchRegex; log.nextMatch(), log.previousMatch()
log.level @= (line => if (line.startsWith("!!")) Some(LogLevel.Error) else LogClassifier.classify(line))
log.matches(); log.matchIndex()            // "3 of 41": the count, and the match it is on
log.hidden()                               // lines the filter hides
log.clear()

val panel = new LogPanel(log)              // or the bare view
panel.size.width := screen.size.width()
panel.size.height := screen.size.height()
```

**What it reads** (`AnsiParser`, plain Scala, the Roku's too): SGR reset, bold, dim, italic, underline, strikethrough,
reverse and conceal and their undoing; the sixteen colours and their bright forms; xterm's 256 (`38;5;n`, `48;5;n`)
and colours of their own (`38;2;r;g;b`, and the `:` forms); the default colours (39, 49). The style carries on from
line to line as a terminal's does. Everything else is dropped: cursor moves and the other CSI sequences (private
markers among them), OSC ended by BEL or ST (an OSC 8 hyperlink becomes a link where the platform draws links), DCS,
charset designations, the control characters. A `\r` with more after it starts the line again, so a progress bar
drawn over itself shows its last state; tabs are set out to every eighth column. A line is read once its newline has
come, so an escape cut between two pieces of a stream is whole by then, and a line that never ends is cut at 65,536
characters. `AnsiParser.strip(text)` is a line's words alone.

**Colours.** `theme` follows `Theme.palette` (`LogTheme.of`): the words in the palette's text on its surface, the
sixteen tuned for a dark or a light ground, and any colour a program names (the 256 and its own too) taken towards the
text until it reads (4.5:1) — black on a dark ground, yellow on a light one. `LogTheme.of(palette, raw = true)` draws
xterm's colours as they are. Reverse, dim and conceal are drawn as a terminal draws them; a run's ground is a box
behind its columns.

**Lines.** `maxLines` (50,000; 5,000 on a Roku) is a ring: the oldest goes as a line comes. `lineNumbers`, `timestamps`
(where the app or the event gives a time) and `sources` fill a gutter; `wrap` wraps at the columns that fit (each line
as tall as the rows it takes), and without it long lines scroll sideways with the arrows, a sideways wheel or a finger.
What comes in is read a stretch a turn (8 ms; 25 on a Roku), so a backlog of thousands never holds the screen up;
`settle()` reads it all at once. Lines are chosen as a list's rows are, and Ctrl or Command+C copies their words,
without escapes (`selectedText`, `copySelected()`); the context menu copies the line under it, the lines chosen, or
chooses them all (`menuFor`).

**Following.** While the newest line is in view it stays there as lines arrive (`following`); scrolled up, a reader
keeps their place (the oldest going from above does not move it) and "Jump to latest" (with how many have come) takes
them back down, as reaching the bottom does.

**Search.** Matches are marked in the lines in view, the one it is on the strongest, and counted through every line
shown (`matches`, `matchIndex`, `searching` while it counts), a stretch a turn, so typing never waits on a long log.
Words in any case by default (`searchCase`), or a regular expression (`searchRegex`; one that does not compile is
`broken`). The words searched are the lines' own, escapes taken out. The first match from the top of the view on is
the one it is on; next and previous go round, scrolling the match into view (across too). In `LogPanel`, Enter and
Shift+Enter, F3 and Shift+F3 and the arrows beside the count.

**Filter and levels.** `filter` shows the lines holding some words (or a regular expression, in any case unless
`caseSensitive`) and of some `levels`; markers always show. A line's level is read by `level`, `LogClassifier.classify`
unless the app gives its own: the first word near the start of a line that names one, in capitals anywhere (scribe's
`2026.10.07 12:00:00 [main] INFO dw.Loader:87 - …` and its two-line layout, logback's, java.util.logging's `SEVERE:`)
or marked in any case (`[error]`, `error:`, `level=warn`, `"level":"warn"`), and logcat's letters (`E/Tag(…)`, and
the threadtime layout). With `continuations` (on by default) a line naming none takes the level of the one before it,
so a stack trace follows its error through a level filter. An event's level is its own.

**A server's log.** `LogEvent(seq, at, level, text, source)` is a line as a server sends it: plain fields (the level by
name, "" for none), so every backend reads it the same; its codecs are `LogWire`'s (`import everyui.log.LogWire.given`).
`LogFeed.connect(view, url, channel)` streams one into a view over a durable socket and returns the client to close:

```scala
val client = LogFeed.connect(panel.view, "wss://example.com/logs", "pipeline")
```

The socket's own sequencing does the work: an event comes with its seq, and a connection that drops and resumes has
what it missed replayed from the server's log. A session the server no longer holds (the first, or after a restart)
asks for what it lacks (`LogSince` its last seq, the socket's RPC), and shows it before what arrived meanwhile. Lines
the server no longer keeps show as a marker, "… 12 lines missed"; a server that started again (a new `epoch`) is
marked, and its log shown from its first line.

The server's side is `LogBacklog` in everyui-network (JVM): the newest `capacity` events of each channel, numbered as
they are sent, and the `EventLog` of the Spice `DurableSocketServer` that carries them, so replays come from it:

```scala
import everyui.log.{LogBacklog, LogEvent, LogLevel, LogSubscriber}
import everyui.log.LogWire.given

val logs = new LogBacklog(10000)
val sockets = new DurableSocketServer[String, LogEvent, LogSubscriber](eventLog = logs,
  resolveChannel = (_, who) => Task.pure(who.channel), onRequest = (who, json) => logs.answer(who.channel, json))
sockets.broadcast("pipeline", LogEvent.of(line, Some(LogLevel.Info), source = "loader"))
```

On the wire: the client connects with `LogSubscriber(channel)` as its info; events are `LogEvent`s whose seq is the
socket's; a new session asks `LogSince(after, channel)` and is answered `LogBatch(events, first, last, epoch)`, the
events after `after` still kept, the first and last seq kept, and the log's run.

**On a Roku and an Apple TV**, which draw no styled runs in a paragraph, a line is a piece per colour, set at its
columns (the face is monospaced), so the colours show there too; links do not. The example's `demo=logs` streams a
pipeline's log into a panel (`socket=ws://host:8731/logs` streams the example server's instead), and `probe=1` times
reading, searching and filtering a few thousand lines.

## Collections

Long lists and trees, in packages of their own (`everyui.list`, `everyui.tree`). Each takes the focus as one component
with a cursor among its rows, so the arrows, a remote's d-pad and the pointer all move the same cursor. At either end an
arrow leaves it as the focus layer says (`exitFocus` on a container around it).

### VirtualList (`everyui.list`)

Only the rows that show exist; as it scrolls, a row leaving one end is handed the item arriving at the other. The items
are a `Var[Vector[T]]` the app keeps, each with a key: the cursor (`current`), the selection and the scroll are kept by
key as items come and go. Rows are `rowHeight` tall, or `measuredRows = true` for rows as tall as each draws itself;
then `estimate` (an item to a height) guesses those not drawn yet, `rowHeight` unless set, so the rows made first are about
the ones that show and the scroll is about as long as it will be (a chat's transcript guesses its entries by their
length: `Transcript.estimate`). On the web such rows beyond the view are made after the next frame is drawn.

```scala
import everyui.list.{Decoration, VirtualList}

val files: Var[Vector[File]] = Var(Vector.empty)
val list = VirtualList.text(files, (f: File) => f.path, (f: File) => f.name,
  decorate = f => Decoration(icon = Some(MaterialIcon.Description), badge = f.status))
list.size.width @= 320.0
list.size.height := panel.size.height()
list.activated.attach(f => open(f))               // Enter, a double click (a tap on a touch screen)
list.menu.attach(p => showMenu(p.item, p.x, p.y))  // the menu key, Shift+F10, OK held
list.scrollTo(list.indexOf(path), align = 0.5)
list.selection.keys()                              // a Var[Set[String]] of keys, the app's to read and set
```

A row of its own extends `ListRow[T]` and binds what it shows to `item` (it is handed item after item):

```scala
val list = new VirtualList[Hit](hits, _.id, _.title, rowHeight = 44.0)(() => new ListRow[Hit] {
  children += TextView.bound(item().map(_.title).getOrElse(""))
})
```

Keys: Up, Down, Page Up, Page Down, Home and End move the cursor; with Shift they extend the selection, with the primary
modifier (Ctrl, Cmd on a Mac) only the cursor moves and Primary+Space toggles its row; Primary+A picks every row; a
letter jumps to the next row whose label starts with it. Clicks pick a row, Shift+click a range, Primary+click one more.
Where a remote drives the app (`Platform.remoteNavigation`), `selection.multiple` is off: one row is chosen and it follows
the cursor.

A row whose words are there to be read (a log line, a message) marks them `selectable`: on the web a drag across them
selects them, across rows, and Ctrl or Cmd+C copies them, while a plain click still picks the row, and a press on them
starts no drag of the row (`Pointer.onText`). The rows are put in the order they read as such a press goes down, so a
selection across rows takes in only those between; `list.selectText()` selects the words of every row drawn. What
answers a click inside (a button, a chip) stays unselectable. Other platforms select no text and ignore it. The
secondary button chooses the row under it and fires `menu`; the browser's own menu (Copy, Look Up) is kept away only
when a handler puts a `Menu` (or any dialog) up for it, or consumes the event, so a list with no menu for a row leaves
the browser's. Shift with the secondary button always gives the browser's, and a link's is always the browser's.

```scala
val line = new TextView { selectable @= true }     // or on a container: everything in it
```

Over a `source` (any `ListSource`, a server's pages or a log's lines) rows are `rowHeight` tall, unless the source
knows its rows' heights itself without drawing them (`sized`, with `top(index)` and `indexAt(y)`: a log's wrapped
lines). `dragAcross` reports a finger dragged across the rows, for a list whose rows scroll sideways themselves.

### Tree (`everyui.tree`)

A `TreeModel` holds the nodes by key and changes a node at a time; a `Tree` shows its open nodes as a `VirtualList`
and follows each change, keeping what is open (`expanded`), chosen (`selection`) and scrolled to. Changes made in
`model.batch { ... }` are heard once.

```scala
import everyui.tree.{Tree, TreeModel}

val model = new TreeModel[File](_.path)
val tree = new Tree(model, (f: File) => f.name)
tree.expandable @= (f => f.directory)
tree.loader @= Some(dir => Task.async(done => server.list(dir.path)(done)))   // children fetched as a node first opens
tree.decorate @= (f => Decoration(icon = Some(if (f.directory) MaterialIcon.Folder else MaterialIcon.Description),
  color = f.status.map(statusColour), badge = f.status.getOrElse("")))
tree.activated.attach(f => if (!f.directory) open(f))
tree.menu.attach(p => showMenu(p.item, p.x, p.y))

model.setChildren(None, roots)
model.insert(Some("/src"), 0, File("/src/Main.scala"))
model.update(file.copy(status = Some("M")))
model.move("/src/Main.scala", Some("/app"), 0)
model.remove("/tmp")
tree.reveal("/src/deep/File.scala")   // opens what is above it and scrolls to it
```

A row's label keeps its width, cut with an ellipsis only where it is wider than the row, and its badge has the room
left between the label (and a gap) and the end, in a list's rows as in a tree's: a badge too wide for it is cut where
`Decoration.badgeElide` says, from its start by default, so a file's folder keeps its end ("…/voidcraft/backend/app
+39 −7"), and one with no room for more than its ellipsis is not drawn. It never runs over the label.
`Decoration.badgeTail` is a part after the badge that is never cut, drawn at the end with a small gap before it: a
file's counts after its folder (`badge = "backend/app", badgeTail = "+1 −1"`), so a narrow row reads "…/backend/app
+1 −1" where one badge would have been cut to "…1 −1". The badge has the room between the label and the tail; with
too little, the badge goes first, and the tail too where even it does not fit beside the label. `demo=badge` shows a
commit window's changed files at 280 and 400 wide and wider.

A row can have a tip, `Decoration.tooltip` (None by default): words for more than it shows, a file's whole path
beside its name. It is a `Tooltip`, with its delay, look and place (under the row, centred on it, over it at the
foot of the screen), a long path wrapped within `Tooltip.widest` and broken where it must be. It shows as the pointer
rests on the row; as the cursor rests on it in a list with the focus, moved there by the keys, where the focus shows
tips (`Tooltip.onFocus`: not where a remote drives the app); and on a touch screen at once as a finger is held on the
row, for 2.5 s, unless that puts a menu up (`menu` answered with a `Menu`), when the menu is the hold's answer and no
tip shows: a tip flashed before it would only be covered by it (`list.holdTips` turns holding off or on). It goes as
the pointer leaves or presses, the cursor moves on, the list loses the focus, the rows scroll, the row is handed
another item and with the list; the words are read as it shows, so a row a long list hands item after item shows its
own item's, never one it showed before. They are also the row's description to a screen reader
(`Component.accessibleDescription`: the web's `aria-describedby`, TalkBack's hint). Rows are only watched for it once
they have had one, so a list without tips is as it was. `ListRow.tip` is what a row of its own sets for the same.

```scala
tree.decorate @= (f => Decoration(icon = Some(MaterialIcon.Description), badge = f.folder, tooltip = Some(f.path)))
```

`demo=rowtips` (app/full) shows a project's files in a tree, each with its path as its tip (`n=`, `menu=1`).

A node changed in the model (`model.update(entry.copy(status = "M"))`) is dressed again at once, on the row already
showing. Where the look comes from outside the node (a status map the app keeps), `tree.redecorate(key)` or
`tree.redecorateAll()` asks `decorate` again; lists and tables have the same.

Keys: Up and Down move the cursor, Right opens a node or goes to its first child, Left closes it or goes to its parent
(mirrored in a right-to-left language), Enter activates and opens or closes a folder. A click on the chevron opens or
closes it. Multi-select is the list's (Shift and Primary with clicks and arrows, Primary+A).

### Selection (lists and trees)

`list.selection` (and `tree.selection`) is a `Selection`: `keys` is a `Var[Set[String]]` the app reads and sets,
`anchor` is where a range starts, and `multiple` says whether more than one row may be chosen (off where a remote drives
the app, so one row is chosen and follows the cursor).

```scala
tree.selection.keys.attach(keys => status @= keys.size.toString + " selected")
tree.selection.keys @= Set("/src/A.scala", "/src/B.scala")   // chosen by the app
tree.selectedItems                                           // the chosen nodes, in the order they show
list.selectAll(); list.selectRange(10, 20); list.selection.clear()
list.selection.multiple @= false                             // one at a time, wherever it runs
```

Click picks a row, Shift+click the range from the anchor, Primary+click adds or removes one; Shift+arrows extend,
Primary+arrows move the cursor alone and Primary+Space toggles its row; Primary+A picks every row.

### Naming in place (lists and trees)

`edit(key)` puts a field over a row's label holding its name; `insertEditing` adds a row for a new item and puts the
field in it. Enter commits, Escape cancels, and leaving the field (the focus going elsewhere, a click away) commits.
The app's `commit` takes the name, changing its items or model to match, or refuses it with a message shown under the
field, which stays open; it is a `Task`, so the answer can come from a server. Afterwards the focus is back on the list,
on the renamed row or the new one. With `renames @= true`, F2 renames the row under the cursor. Where the platform has
no field of its own (a Roku, an Apple TV) its keyboard asks for the name, and a refusal is toasted.

```scala
tree.commit @= { e =>
  e.node match {
    case Some(file) => server.rename(file.path, e.text).map {   // a rename
      case Right(renamed) => model.remove(file.path); model.insert(e.parent, e.position, renamed); None
      case Left(error) => Some(error)                           // shown under the field
    }
    case None => server.create(e.parent, e.text).map {          // a new node, named at e.position under e.parent
      case Right(made) => model.insert(e.parent, e.position, made); None
      case Left(error) => Some(error)
    }
  }
}
keymap.add("file.rename", "Rename", Chord("F2")) { tree.current().foreach(tree.edit) }
keymap.add("file.new", "New file", Chord.parse("Primary+Alt+N")) { tree.insertEditing(tree.current().map(folderOf)) }

list.commit @= (e => Task.pure { if (taken(e.text)) Some("Already there") else { rename(e.item, e.text); None } })
list.insertEditing(3, File.blank)   // a stand-in for the new row until its name is typed; e.created is true
```

### Drag and drop (lists and trees)

With `draggable @= true`, rows are dragged out with the pointer (a chosen row takes all the chosen rows with it) and a
label follows the pointer. Over any list or tree, its `accepts` decides for each place whether they may land: `Before`
or `After` a row, or `Into` one that holds rows (a tree's folders). A line or a lit row shows where; the rows scroll near
an end; a closed folder held under the drag opens. Let go there, that list's `dropped` hears a `Drop` with where they came
from (`from`: the list or tree), their keys, the target's key and the position. Escape lets go, dropping nothing. Never
on a remote.

```scala
tree.draggable @= true
tree.accepts @= (d => d.target.forall(t => !tree.inside(t, d.keys)))   // nothing into itself or under itself
tree.dropped.attach { d =>
  val (parent, index) = d.position match {
    case DropPosition.Into => (d.target, Int.MaxValue)
    case p => (d.target.flatMap(model.parentOf), placeOf(d.target) + (if (p == DropPosition.After) 1 else 0))
  }
  if (d.from eq tree) model.batch(d.keys.foreach(k => model.move(k, parent, index)))
  else upload(d.keys, parent)
}
```

From the keyboard, `cut()` holds the chosen rows and `paste()` puts them into the row under the cursor (where it holds
rows) or after it, through the same `accepts` and `dropped`:

```scala
keymap.add("tree.cut", "Cut", Chord("X", primary = true)) { tree.cut() }
keymap.add("tree.paste", "Paste", Chord("V", primary = true)) { tree.paste() }
```

### GroupedList (`everyui.list`)

Results under collapsible headers, as a search finds them in files: each header shows its group's title and how many it
holds, each result a line with what matched lit (spans are `(start, end)` pairs, end excluded) and a quieter lead before
it (a line number). A long line starts a little before its first match. It is a `VirtualList` underneath (`list`), so
tens of thousands of results scroll as easily as ten.

```scala
import everyui.list.{GroupedList, ResultGroup}

case class Hit(file: String, row: Int, text: String, matches: Seq[(Int, Int)])

val groups: Var[Vector[ResultGroup[Hit]]] = Var(Vector.empty)
val results = new GroupedList[Hit](groups, h => h.file + ":" + h.row, _.text, _.matches, h => (h.row + 1).toString)
groups @= hits.groupBy(_.file).toVector.map((file, found) =>
  ResultGroup(file, file, found.toVector, icon = Some(MaterialIcon.Description)))
results.activated.attach(hit => openAt(hit.file, hit.row))
Keymap.global.add("search.next", "Next result", Chord("F4")) { results.nextResult().foreach(show) }
Keymap.global.add("search.previous", "Previous result", Chord("F4", shift = true)) { results.previousResult().foreach(show) }
```

The arrows go through headers and results alike, across groups; Left on a result goes to its header and on an open
header closes it; Right opens a header or goes to its first result; Enter or a click on a header opens or closes it;
Enter or a double click on a result activates it. `collapsed` holds the closed groups' keys; `expandAll`,
`collapseAll`.

### RichList (`everyui.list`)

The list down the side of a chat, mail or contacts app, virtualised (thousands scroll as ten do). Each item is drawn as
its `RichLook` says: an avatar (a `chat.Participant`: their picture, or their initial on their colour) or an icon, a
title and an optional second line, and at the end how long ago (`time`, epoch millis: "now", "5m", "3h", "2d", "9/16",
kept current while the list is on the screen, and said again the moment it is back: `Ticker`), a `count` badge ("99+") and a small `mark` (a pin, a lock); `unread` is bold with its time lit. `busy`
turns the busy ring around the avatar (an agent at work in it, unread or not), with `busyDetail` ("Running bash") said
by the avatar to a pointer and with the row to a screen reader; rows reused as the list scrolls start and stop it. Items
sit in `RichSection`s under headers (title and count) that open and close, the closed ones' keys in `collapsed`, which
the app may hold; a section with no title has no header.

`selected` is the app's current item, drawn chosen; Enter, a click or a tap activates an item, which chooses it and
fires `activated`. The arrows move through headers and items; Enter, a click, Right or Left on a header opens or closes
it; Left and Right on an item leave the list, as the focus layer says. `menu` gives each item's `MenuItem`s, opened by
the secondary button, a long press, the menu key (a Roku's options key), OK held, or the ⋮ that shows at the row's end
with the pointer over it or the cursor on it. `search = true` puts a field over it that narrows it by `query` (the app
can set it; `matches` says what a word finds), or the app filters its own `sections`. `reorders @= true` drags items
into another place: `accepts` (within their own section, by default) and `moved` (a `RichMove`: the keys, the section,
the item they go before). `rename(key)` types a title in place, for `commit`.

```scala
val chats = new RichList[Chat](sections, _.id, c => RichLook(c.title, Some(c.snippet), who = Some(c.persona),
  time = Some(c.updated), count = c.unread, unread = c.unread > 0, mark = if (c.locked) Some(MaterialIcon.Lock) else None),
  collapsed = app.collapsedSections, search = true)
sections @= Vector(RichSection("unread", "Unread", unread), RichSection("recent", "Recent", recent))
chats.menu @= (c => List(MenuItem("Rename…", () => chats.rename(c.id)),
  MenuItem.check("Lock title", c.locked, lock(c, _)), MenuItem("Delete", () => delete(c), danger = true)))
chats.commit @= ((c, title) => Task.pure { retitle(c, title); None })
chats.activated.attach(c => open(c))
chats.selected @= Some(current.id)
```

A long press opens the menu where the platform turns it into a context-menu request (Android, JavaFX, and browsers that
fire `contextmenu` on one). `demo=richlist` has 5000 in three sections.

A section can have actions of its own (a workspace, a folder, a label): `sectionMenu` (`RichSection[T] =>
List[MenuItem]`, empty by default) gives them, on its header. The secondary button opens them at the pointer (and only
then keeps the browser's own menu away, so a header without any leaves it); the menu key, Shift+F10 and OK held drop
them from the header; a ⋮ at the header's end, shown with the pointer over it or the cursor on it and always where there
is no pointer (a touch screen, a television), drops them from itself. A click or Enter on the header still opens and
closes it; a click on the ⋮ does not. The ⋮ is not a stop for the focus: the list is one stop, its cursor on the
header, and the keys reach the menu as they do an item's. Where any header has a menu (`headersHaveMenus`), every
header's count sits short of the ⋮, in one line down the list, whether it shows or not; in a list with none each
header is as it was. `RichSection.icon` puts an icon before the title, for a kind of group of its own. Right to left,
the ⋮ is at the left.

```scala
val sidebar = new RichList[Chat](Var(workspaces.map(w => RichSection(w.id, w.name, w.chats, icon = Some(MaterialIcon.Folder)))),
  _.id, look)
sidebar.sectionMenu @= (s => List(MenuItem("New chat here", () => create(s.key)), MenuItem("Rename…", () => rename(s.key)),
  MenuItem("Delete workspace", () => delete(s.key), danger = true)))
```

Sections can go under a level above them: `RichSection.group` (`Option[RichGroup]`, `None` by default) puts the sections
next to each other with the same `RichGroup(key, title, icon = None)` under one header ("Workspaces", "Personal"). The
group travels on its sections, so the list follows `sections` alone and needs no second Var kept in step with it. Its
header is bold in the text's colour, where a section's is muted, and the sections' headers under it are set in by
`RichRow.nest` (from the right, right to left); its count is the sum of what its sections' headers say. A click, Enter,
Right and Left open and close it as a section; the closed groups' keys are in `collapsedGroups` (a constructor
argument after `search`, a `Var` the app may hold, as `collapsed` is), apart from the sections', so a group and a
section of the same key never clash. A closed group hides its sections and their headers, and each keeps its own state
inside it. The words (`query`) open a group to what they find in it, with only the sections they find something in;
one they find nothing in is not shown. `groupMenu` (`RichGroup => List[MenuItem]`, empty by default) is its menu, asked
for and shown as a section's is, the ⋮ included. Sections in no group are drawn as they always were. A drag never goes
into a closed group; onto an open group's header is its first section's start, and before it the end of the section
above. `RichMove` still names sections, which the app maps to its groups.

```scala
val workspaces = RichGroup("workspaces", "Workspaces", Some(MaterialIcon.Workspaces))
sections @= app.workspaces.map(w => RichSection(w.id, w.name, w.chats, group = Some(workspaces))) ++
  app.personas.map(p => RichSection(p.id, p.name, p.chats, group = Some(RichGroup("personal", "Personal"))))
val sidebar = new RichList[Chat](sections, _.id, look, collapsedGroups = app.closedGroups)
sidebar.groupMenu @= (g => if (g.key == "workspaces") List(MenuItem("New workspace…", () => create())) else Nil)
```

RichList styles: `style` (`Var[RichStyle]`) says how the sections are drawn. `RichStyle.Tree`, the default, is all of the
above, unchanged. `RichStyle.Blocks` draws them as a sidebar's panels:

- a group's header is a plain caption over its sections ("WORKSPACES"): small, muted, in capitals, a little spaced, with
  no chevron, icon or count; a click or Enter (and Right and Left) still closes and opens the group, and a closed one
  shows a small ▸ at its end;
- each section is a block, a softly tinted panel with rounded corners (`RichRow.blockRadius`, 10) set in
  `RichRow.blockMargin` (8) from the list's sides, with `RichRow.blockPad` (6) inside it and `RichRow.blockGap` (8)
  between blocks. Its header is a bar across the block's top (`RichRow.blockHeader`, 36 tall): its icon and title, its
  count (always shown: a long title is cut short of it), its ⋮ where it has a menu, and a small ▾ or ▸ at its end (the
  left, right to left). The whole bar opens and closes the block: a click or a tap anywhere on it, Enter, Right and Left.
  Nothing is set in: the items run across the block inside its padding, at the width a tree gives them, and a section's
  header in a group starts where one in none does. A closed block is its header bar alone, still with its count;
- the item chosen, the cursor and the pointer are lit inside the block, rounded, rather than across the row.

Everything else is as in a tree: the cursor, the keys and a remote (headers and items are rows, Up and Down move through
them across blocks), `selected`, the search (what it finds shows in its blocks, opened), the menus and their ⋮, dragging
(within a block, and into another where `accepts` allows it), `collapsed` and `collapsedGroups`, right to left. The rows
stay virtual: each draws its own part of its block (the first with the block's top corners, the last with its bottom
ones and the gap after it, those between square), a few nodes a row, made the first time the list is drawn as blocks, so
the cost of scrolling is a tree's. The tint is the palette's `list.block` (`Theme.extra`: white at 5% in
`Palette.dark`, black at 4% in `Palette.light`), else the text's colour at 5%, so it shows on any ground.

```scala
sidebar.style @= RichStyle.Blocks
Theme.palette @= Theme.palette().copy(extras = Theme.palette().extras + ("list.block" -> Color.fromHex("7c4dff").withAlpha(0.06)))
```

A section can have one thing it does straight from its header ("New conversation"): `RichSection.action`
(`Option[RichAction]`, `None` by default), a `RichAction(icon, label, run)`. It is a small icon button in the header,
always shown, the ⋮'s size, lit as the ⋮ is under the pointer and following the theme: in a block just before the
count (the title cut short of it), in a tree at the header's end, short of the ⋮ where any header has a menu; where
any section has an action (`headersHaveActions`), every tree header's count sits short of it, in one line down the
list. Right to left, it is mirrored. `label` is its tooltip and what a screen reader calls it. A click or a tap on it
runs it and neither opens nor closes the section, whether open or closed (a click on the bar beside it still does).
The keys and a remote reach it from the header, the list still one stop for the focus: Right (Left, right to left) on
an open section's header puts the cursor on its action, ringed in the accent where the header was; Enter (OK) runs
it; Left (Right) takes the cursor back to the header, and Up or Down on as before. On a closed header Right opens it
first, as it always did; on a header with no action Right does what it did.

An open section that holds nothing can say so: `RichSection.empty` (`Option[String]`, `None` by default) is a line of
its own where its items would be ("No conversations yet"), muted, `RichRow.emptyHeight` (36) tall, inside the block in
`RichStyle.Blocks`. It is not an item: it is never chosen or lit, a click on it does nothing, the cursor passes over
it (sent onto it, it goes on the way it was going), and a drop onto it is into its section. Search shows only what
it finds, so a section the words find nothing in is hidden as before, and the words never find the line. Without
`empty`, an empty section is its header alone, as it was. Both are defaulted last parameters, so existing sections
are unchanged.

```scala
RichSection(w.id, w.name, w.chats, icon = Some(MaterialIcon.Workspaces), group = Some(workspaces),
  action = Some(RichAction(MaterialIcon.Add, "New conversation", () => create(w.id))),
  empty = Some("No conversations yet"))
```

An item can have one too: `RichLook.action` (`Option[RichAction]`, `None` by default) is a small icon button at its
row's end on the title's line, before the time, the mark and (in a short row) the badge, or before the ⋮ while that
shows in their place. It shows while the pointer or the cursor is on the row (on a touch screen, while it is the item
chosen), or always where `RichLook.actionShown` or the action's own `shown` (both false by default). A row with an action keeps its room whether it shows
or not, the wider of the ⋮'s and the time's, so its title is cut once, short of it, and neither the title nor the
button moves as the pointer comes and goes; a row without one is laid out as it was. A click or a tap on it runs it
without choosing, activating or moving the cursor to the item; the keys and a remote reach it as a header's: Right
(Left, right to left) from the row puts the cursor on it, ringed, Enter runs it, Left goes back, Up or Down move on.
Its label is its tooltip and its accessible name. It is the same in a tree and in blocks, and mirrored right to left.

```scala
look = c => RichLook(c.title, Some(c.snippet), action = Some(RichAction(
  if (c.starred) MaterialIcon.Star else MaterialIcon.StarBorder, if (c.starred) "Unstar" else "Star", () => star(c))),
  actionShown = c.starred)
```

An item can expand in place, to show all of itself and offer what can be done to it (a memory's whole text, with Pin
and Delete). `expansion` (`Var[T => Option[RichExpansion]]`, every item `None` by default) says what an item shows
expanded: `RichExpansion(text, actions, markdown = true)`, its whole text under its title's line, from where the
title starts to the row's end, wrapped and never cut short (as Markdown, through a `MarkdownView` set a little smaller
than a chat's, unless `markdown` is off: then as plain words, broken anywhere a word is wider than the row), and its
`actions` (`RichAction`s, in order) as buttons under it, each its glyph and its label in the accent, a `danger` one
(`RichAction.danger`, false by default) in the danger colour, the first's words in line with the text and as many in
a line as fit. Activating an item that has one (a click or a tap on its title's line, Enter, OK) opens it rather
than choosing it or firing `activated`, and again folds it; Escape folds it too, and does nothing on a folded row. A
click or a tap on its text or its buttons never folds it; a button runs its action at once. `expanded`
(`Var[Set[String]]`, by `keyOf`) holds the items open, the app's to keep and set, and `expandsOne` (true by default)
says whether opening one folds any other or several stay open; `expandItem`, `foldItem` and `toggleItem` do it from
the app. The row is as tall as all of it (the list measures each row), lit and ringed whole, and once it is drawn at
its new height it is kept in view: scrolled the least that shows it whole, or so its top is at the list's top where it
is taller than the list; folded, it is shown again where the fold left it above the view. Its text fades in as it
opens (160 ms) where the app runs a frame loop and Quality lets things glide, and is there at once otherwise. The keys
and a remote reach the buttons as a row's action: Right (Left, right to left) from the row puts the cursor on its
action where it has one, then on each button in turn, ringed; Enter runs the one it is on, without folding; Left goes
back the same way, and Up or Down move on. A screen reader hears whether the row is expanded (`Semantics.expanded`,
`aria-expanded` on the web). Search finds what it always did, by `matches` (the title and the detail, by default): an
expanded item that is found stays expanded, and words only in an item's text find it where the app's `matches` looks
there too. Grouping, sections, the menus and dragging are unchanged, and a list without an `expansion` is exactly as
it was. The same in a tree and in blocks, and mirrored right to left. `Button.danger` (`Var[Boolean]`) is the same
colour on any button: its words, an outlined one's edge and a filled one's plate in the danger colour.

```scala
memories.expansion @= (m => Some(RichExpansion(m.text, List(
  RichAction(MaterialIcon.PushPin, if (m.pinned) "Unpin" else "Pin", () => pin(m)),
  RichAction(MaterialIcon.Delete, "Delete", () => delete(m), danger = true)))))
memories.expandsOne @= true                               // the default: one open at a time
memories.matches @= ((m, words) => RichList.holds(look(m), words) || m.text.toLowerCase.contains(words.toLowerCase))
```

An item can be marked live, held somewhere else in the app apart from the list's own choice: a conversation open in a
tab, a track playing, a device connected, a document pinned to a pane. `RichLook.live` (false by default) draws it with
a faint tint of the accent behind it (in a block, inside the inset light the item chosen has) and a thin accent bar
(3 wide, 2 in from the row's start edge, the right right to left, stopping 9 short of its line's top and bottom): clear
of the cursor's ring in a tree, and in a block in the margin beside it, clear of an avatar's busy ring. `selected`
stays the stronger: the item chosen keeps its own tint, several times the live one's, and has no bar unless it is live
too, when it shows both, the chosen tint and the bar. The pointer over a live row and the cursor on it light it as any
row (the bar stays), and its busy ring, its action (★), its ⋮, its count and its expansion are as they were.
`RichLook.liveLabel` (`Option[String]`, `None` by default: nothing said) is said to a screen reader with the row while
it is live ("open in a tab"); say one where a live item should be heard apart. The tint is the palette's `list.live`
(`Theme.extra`), else the accent at 8%, and the bar its `list.liveBar`, else the accent. The bar is made the first time
a row shows a live item, so a list with none has the rows it had, and an item that is not live is drawn exactly as
before.

```scala
look = c => RichLook(c.title, Some(c.snippet), who = Some(c.persona), live = tabs().contains(c.id),
  liveLabel = Some("open in a tab"))
chats.selected @= Some(current.id)                         // stronger than live; one both shows both
Theme.palette @= Theme.palette().copy(extras = Theme.palette().extras + ("list.live" -> Color.fromHex("7c4dff").withAlpha(0.1)))
```

The field is a `SearchField` (`everyui.kit`, `list.field: Option[SearchField]`): a search box on one line, 34 px tall,
with a magnifier, "Search" (`Words.search`) faint inside it while it is empty, and a × once it holds something. The ×
and Escape empty it; an empty one leaves Escape to the screen (Back). Enter goes to the list, and so does Down (Up from
the list's top comes back). It is one stop for the focus, its edge lit; the × is the pointer's, Escape the keys'. Where
the platform has a field of its own (the web, the desktop, Android) the words are typed there; on a Roku and an Apple TV
OK on it asks the platform's keyboard (`Platform.editField`). It stands on its own anywhere a search box is wanted:

```scala
val search = new SearchField()                          // placeholder: Words.search
search.value.attach(words => results @= find(words))
search.exitFocus(Key.ArrowDown, results)
list.field.foreach(_.focus())                           // Ctrl+K
```

### Pointer buttons and modifiers

A `PointerEvent` carries its `button` (`PointerEvent.PrimaryButton`, `MiddleButton`, `SecondaryButton`; a finger is the
primary) and the modifiers held with it (`ctrl`, `meta`, `alt`, `shift`, and `primary` for the platform's shortcut
modifier). `click` is the primary button's everywhere; `event.pointer.menu` is the secondary's; `event.pointer.middle` is
a middle click, which closes a closable `TabItem`. A mouse's back and forward buttons are keys, not pointer
events ("BrowserBack", "BrowserForward": Keys, The mouse's back and forward buttons).

```scala
row.event.click.attach(e => if (e.shift) extend() else if (e.primary) toggle() else pick())
tab.event.pointer.middle.attach(_ => close())
```

A control smaller than a fingertip (a glyph button, a check box, a chip) answers a finger's tap a little outside it
(`Component.tapSlop`, `everyui.event.TapSlop`): anywhere in the 48-pixel square around its centre (Material's 48 dp,
which holds Apple's 44 pt), while it is drawn and laid out at its own size, so its neighbours stay where they are. Where
two reaches overlap the nearer centre takes the tap, and a tap on something else that answers a click (a field, a
button, a row) stays its own unless that holds the control, as a row holds its menu's glyph. A mouse's and a pen's
clicks land exactly where they did. `IconButton`, `SendButton`, `Button`, `DialogButton`, `Link`, `Checkbox`, `Switch`,
`Choice`, `SegmentChip`, `ColorSwatch`, a `TabItem`'s close, a `SearchField`'s clear, `ScaleControl`'s parts, a
`MenuCell` and the composer's suggestion chip have it; an app's own small control sets it. It is the web's, where a
click a finger made is taken to the control; Android finds the view a touch lands on itself, the desktop hears a touch
screen's taps as the mouse, and a Roku and an Apple TV have no fingers on the app.

```scala
val star = new Container { size.width @= 24.0; size.height @= 24.0; tapSlop @= true }
```

Lists, trees and grouped lists mirror in a right-to-left language (`Direction.set(true)`): indentation, chevrons,
icons and badges from the right, the scrollbar on the left, and a match line still reading left to right.

### Tick boxes on a tree

`checkable` says which nodes have a box (none by default; a change list's root headers, say, have none). `checked` is
a `Var[Set[String]]` of ticked keys the app reads and sets: ticking a folder ticks what is loaded under it, a folder is
ticked when all its boxes below are and shows mixed when only some are, and a folder whose children are not loaded yet
stands for them (they arrive ticked as it is). `checkToggled` hears what the person turned. Space turns the ticks of the
chosen nodes (all on, unless all are on); a click on a box turns that one. On a remote `checkOnEnter` is on, so OK on a
node with a box ticks it and Right still opens a folder.

```scala
tree.checkable @= (node => !node.isRoot)            // "Changes" and "Unversioned Files" have no box
tree.checked @= initiallyIncluded
tree.checkToggled.attach(t => println(t.keys.toString + " " + t.checked))
tree.checked.attach(keys => commitButton.enabled @= keys.exists(isFile))
tree.checkState("Changes/src")                        // Checked, Unchecked or Mixed
```

### DataTable (`everyui.table`)

A table of an app's rows of words that the person can sort, filter, choose from, copy and export; every Markdown table
is one too (above). It is laid out as a Markdown table is (`TableWidths`: columns as wide as their widest cell where
all fit, else sharing the width so cells wrap, no word ever cut, scrolling sideways only where the widest words alone
are too wide, a shade at the edge with more beyond). Cells wrap rather than being cut, so nothing is hidden behind a
tip. Taller than `maxHeight` (420 by default; 0 for none), the rows scroll under a header that stays, with room kept
for the page's scrollbar on the web. A table whose words read right to left has its first column at the right.

```scala
import everyui.table.{ColumnKind, DataColumn, DataTable}

val table = DataTable(Vector(DataColumn("Persona"), DataColumn("Conversations"),
  DataColumn("Last active", kind = Some(ColumnKind.Date))), personas.map(p => Vector(p.name, p.count, p.last)))
table.size.width := panel.size.width()
table.name @= "personas"                  // personas.csv, personas.md
// or from typed items:
DataTable.of(personas)(DataColumn("Persona") -> (_.name), DataColumn("Conversations") -> (_.count.toString))
```

- **Columns**: `DataColumn(title, kind, align, runs)`. `kind` (`ColumnKind.Text`, `Number`, `Date`) is how it sorts;
  None finds it from the cells (`CellValues.infer`: dates where every cell that says something is a date, numbers where
  every one is a number). `align` None puts numbers at the end and anything else at the start. `cells` (a
  `Var[Vector[Vector[DataCell]]]`; `DataCell(text, runs)`, the runs drawn where given) and `columns` can change: rows
  whose cells did not change keep what draws them. `DataTableStyle` sets the points, the cell padding, quiet words and
  the direction.
- **Sort**: a click or a tap on a header, or Enter (Space) on it, sorts up, then down, then back as the rows came, an
  arrow by the header and `aria-sort` on it. Numbers sort by value through thousands separators (either convention),
  currencies, signs, brackets for a loss, percentages, units and k, M, B, KB, MB, GB; dates as ISO's, with or without a
  time and zone, the slashed (month first unless it cannot be) and dotted (day first) orders, and English month names;
  words as the language orders them (the browser's collation, numbers in words by their value on the web; the
  platform's `Collator` on the desktop and Android). Cells with nothing (blank, a dash, "n/a") go last both ways; ties
  keep their order. `sort` (`Option[ColumnSort]`) is the app's to read and set; `sortBy(c)` cycles.
- **Filter**: past `filterAfter` rows (8) a search field over the table hides the rows without every word typed in it,
  in any cell, whatever the case, "12 of 60" beside it ("60 rows" while empty). `filter` is the app's to set; `shown` is
  the rows showing, in their order.
- **Choose and copy**: a click on a row chooses it (again, lets it go), Shift+click the rows from the last chosen, Ctrl
  or Command+click adds or takes away one; a tap on a touch screen adds or takes away. `selection` holds them by index
  in `cells`. Ctrl or Command+C copies the chosen rows (or, with none, the cell the keys are on) as tab-separated lines
  under the header, which paste into a spreadsheet (a cell with a tab, a line break or a leading quote is quoted, its
  quotes doubled). Where words are selected with the pointer (a drag across cells on the web) those are copied, as the
  browser copies them, and a click that ends such a drag chooses nothing.
- **Tools**: a ⋮ button, shown while the pointer is over the table or the focus is in it, and always on a touch screen,
  offers Copy table (the rows showing, sorted and filtered, as TSV), Copy as Markdown, Download CSV (RFC 4180, CR LF,
  with a UTF-8 byte-order mark for spreadsheets) and Download Markdown, and Copy N rows while rows are chosen. Downloads
  go through `Files.download`: the browser's downloads on the web, the save dialog on the desktop, Android's document
  creator. Where there is no clipboard the copies are not offered, and where nothing can be saved, the downloads.
  `tools @= false` hides it. `tsv()`, `csv()`, `markdown()` (of the rows showing, or of the rows given), `copy()`,
  `copyMarkdown()`, `copySelection()`, `exportCsv()` and `exportMarkdown()` are the app's too.
- **Keys**: the table is one stop for the focus (Tab goes on past it). The arrows move between its cells, Up from the
  first row to the header (Left and Right swap where the columns run from the right), Home and End to the first and last
  column (with Ctrl, row), Page Up and Down ten rows; Space or Enter chooses the row, or sorts on the header; Shift with
  Up or Down chooses as it goes; Ctrl or Command+A chooses every row showing; Escape lets them go. A ring marks the cell
  the keys are on while they move it. A screen reader hears a table of rows of cells and column headers, which rows are
  chosen (`aria-selected`) and the sort.
- **Long tables**: past 60 rows, the first 12 are made at once and the rest 16 a turn after the first page is drawn,
  and only the widest cells of each column (by their characters and their longest words) are measured for the
  columns' widths; a cell wider than those wraps. In headless Chromium a 500-row table took 80 to 110 ms to make, and
  narrowing its filter a key at a time no long task (all 500 back at once, about 65 ms).
- A Roku and an Apple TV have no `DataTable`: their Markdown tables are drawn plain, and the channel carries none of it.

### VirtualTable (`everyui.table`)

A table of any length on a `VirtualList`: headings over a row per item and a cell per column. Each row's cells are
made once (`GridColumn.cell`, a `GridCell` bound to its `item`) and reused as the rows scroll. `widths` (by column key)
changes as a pointer drags a heading's edge and can be kept and given back; `hidden` hides columns. A column with a
`sort` ordering is sorted by a click on its heading (up, down, then not at all) with an arrow on it; `sort` says which,
and with `sortsItself @= false` the app sorts `items` itself. Wider than the table, the columns scroll sideways (a
sideways wheel, the bar at the foot, Left and Right). Rows are chosen as a list's are (`selection`, multi optional), and
the whole table mirrors right to left.

```scala
import everyui.table.{GridCell, GridColumn, VirtualTable}

class SubjectCell extends GridCell[Commit] {
  private val refs = new Container {
    layout @= Some(HorizontalLayout(4.0, middle = true))
    children.bindMaking(item().toList.flatMap(_.refs))(r => new Tag(r.name, Tag.tone(r.kind), soft = true))
  }
  private val subject = TextView.bound(item().map(_.subject).getOrElse(""), 13.0)
  subject.position.left := refs.size.width() + 8.0
  children ++= List(refs, subject)
}

val log = new VirtualTable[Commit](commits, _.hash, _.subject, Vector(
  GridColumn[Commit]("graph", "", 80.0, () => new GraphCell),
  GridColumn[Commit]("subject", "Subject", 420.0, () => new SubjectCell),
  GridColumn.text[Commit]("author", "Author", 160.0, sorts = true)(_.author),
  GridColumn.text[Commit]("date", "Date", 140.0, sortBy = Some(Ordering.by(_.time)))(c => dateText(c.time))))
log.widths @= saved
log.widths.attach(save)
log.activated.attach(showCommit)
```

More a table can do, over items or paged rows alike:

```scala
table.columns @= viewColumns(view)            // a Var: change the set at runtime; rows keep the cells of columns kept
table.fills @= true                           // the columns fill the width, sharing it by their widths (weights)
table.checks @= true                          // a leading column of tick boxes; the heading's box chooses all or none
table.rowColor @= (e => if (e.managed) Some(Theme.success().withAlpha(0.12)) else None)   // under the selection's
table.hoverColor @= Theme.accent().withAlpha(0.08)    // the row under the pointer (the theme's text, faintly, unless set)
table.footer @= Some(statusLine)              // a line of the app's under the rows
table.chosenCount                             // how many are chosen, rows not loaded among them (reactive)
table.edit()                                  // F2: the current row's first cell that edits (a MenuCell's menu)
```

With `fills`, a column that `stretches` (the default) takes a share of the room the others leave, in proportion to its
width; dragging an edge moves room between that column and the next stretching one, and where the table is narrower than
the columns they keep their widths and scroll sideways. `GridColumn.align` puts a heading at the end over numbers.

A cell that edits: `GridCell.edits` and `edit()`. `MenuCell` is one, a small box with an arrow that drops a menu of the
app's entries; a click on it opens it without choosing the row, and F2 on the row does too. The entries do the edit —
confirm, post, and put the changed row back:

```scala
GridColumn[Entity]("status", "Status", 130.0, () => new MenuCell[Entity](_.status.label, e =>
  Status.all.map(s => MenuItem(s.label, () =>
    Dialogs.confirm("Change the status", s"Make ${e.name} ${s.label}?", "Change").map { yes =>
      if (yes) api.setStatus(e, s).map(saved => source.replace(saved)).start()
    }.start(), checked = Some(s == e.status)))), stretches = false)
```

### Paged rows (`everyui.paging`)

A server's rows a page at a time, for a `VirtualTable` (or a `VirtualList`, which takes any `ListSource`). Only the pages
near the view are asked for, and at most `maxPages` are kept, so 1.9 million rows cost what a few thousand do. A row not
come yet is a placeholder (a grey bar per cell) until its page comes.

```scala
import everyui.paging.{Page, PagedSource, Paging}

val source = new PagedSource[Entity](_.id, pageSize = 100)(request => Task.async { done =>
  // request: offset, limit, sort (Option[Sorting]: the heading clicked), cursor (the one the page before gave)
  api.list(filters(), request.offset, request.limit, request.sort)(
    r => done(Right(Page(r.entities, total = Some(r.total)))),
    e => done(Left(e.getMessage)))                     // the fetch must always complete
})
filters.attach(_ => source.invalidate())                 // a new query: from the first page, late answers dropped
val table = VirtualTable.paged(source, _.name, Vector(
  GridColumn.served[Entity]("name", "Name", 260.0)(_.name),                   // sorted on the server by its heading
  GridColumn.served[Entity]("records", "Records", 100.0, align = TextAlign.End)(_.records.toString),
  GridColumn.served[Entity]("id", "Id", 90.0, sortable = false)(_.id)))
status := (if (source.searching()) "Searching…" else source.total().map(n => s"$n results").getOrElse(""))
source.error()                                           // why a page failed; source.retry() asks again
```

- `Paging.Anywhere` (the default): any page by its offset; the list is as long as `total` from the first answer on
  (which may change with each answer). `Paging.InOrder`: load more, a page after another, each asked with the cursor
  the one before gave (`Page.next`; none on the last), or by offset where the server gives none (its rows end at a
  short page or at `total`); the list is what has come and a page of placeholders while there is more.
- `ahead` (half a page each way) is how near the view a page is fetched; `concurrent` (2) how many are asked at once,
  nearest first; a page the view left before its turn is never asked for.
- `sort` (the table's, from its headings) and `invalidate()` start again at the first page and scroll to the top;
  whatever still comes for the query before is dropped.
- `replace(row)` puts a row changed on the server back; `kept` and `here` say what is held.
- Selection over a paged table: clicks, Shift and Primary choose loaded rows by key as a list does; Primary+A or the
  heading's box chooses every row of the query, those never loaded among them: `selection.all` is on and
  `selection.keys` holds the rows taken out since (a toggle takes one out). The table cannot list those rows —
  `chosenCount` counts them, `selectedItems` gives only the chosen rows that are here, and the app acts on its query
  less those keys on the server. `all` stays on across a new sort or filter (it is every row of the query as it now
  is); an app whose filter change should not carry it over calls `table.selection.clear()`. A Shift range chooses the
  rows it spans that are loaded, and its two ends; a row between that was never loaded has no key and is not in it.
- Rows not come yet: a `VirtualList` over a source draws `placeholder` in their place (a `PlaceholderRow`, sized to
  the row, the cursor and the selection shown on it), never the app's row with no item, and swaps the two as pages come
  and the rows scroll. The default is a faint bar (`VirtualList.blank()`); `placeholder @= Some(() => mine)` gives
  another, and `None` hands the app's row `item` None as before (`ListRow.present` says whether it has one). A
  table's rows draw a grey bar per cell themselves (its `placeholder` is None) unless the app gives one. The cursor may
  stand on a placeholder: the arrows move on, Enter and the menu do nothing there, and it moves onto the row as it comes.
- `loadingView` and `emptyView` (both None by default, on the list and on the table) show in the middle of the list's
  box, under a table's headings: the first while the source looks for a query's first page (`searching`), in place of
  the rows, whose pages are still asked for; the second when there are no rows once it has answered. The list keeps the
  focus meanwhile, and the arrows go on past it as they do past an empty list.

```scala
table.loadingView @= Some(VirtualList.loading())     // a spinner in the accent
table.emptyView @= Some(VirtualList.empty())         // "No results" in the language in use: Words("noResults")
list.placeholder @= Some(() => new MyGhostRow)       // or None: the app's row, with no item
```

### TabGroups (`everyui.kit`)

Tabs in groups, as IntelliJ splits its editors: each group a `TabBar` over what its tab being shown shows, the groups in
`SplitPane`s whose handles drag, across and down. `new TabGroups[K](label, content, id, lead, allowsTwice)`: `content`
makes what a tab shows the first time it is shown, and it is kept while the tab is open, in whichever group it moves to,
then disposed of as it closes; `id` names a tab for the bars and the saved layout.

```scala
val editor = new TabGroups[File](_.name, f => new FileView(f), _.path, f => TabLead.Icon(iconOf(f)), allowsTwice = _.isFile)
editor.open(readme)                                  // in the focused group, or shown where it is open already
editor.tabMenu @= (f => List(MenuItem("Reveal in tree", () => tree.reveal(f))))
editor.addMenu @= Some(g => List(                   // each group's "+", its menu made for that group
  MenuItem("New file", () => editor.openIn(g, File.untitled())),
  MenuItem("Open…", () => pickFile(f => editor.openIn(g, f)))))
editor.commands(Keymap.global)                       // tabs.openRight, openDown, nextGroup, previousGroup, close, maximize
val saved: Var[TabLayout] = Stored("editor", "layout", TabLayout())(using TabLayout.codec)
editor.restore(saved(), path => files.get(path))
editor.layoutChanged.attach(l => saved @= l)
```

A tab's menu has Close, Close Others and Close All; Open Right and Open Down, which open it in a new group after its
group (by the reading direction: on the left, right to left) or below it; Move Right, Left, Down and Up, always on
the menu, to the group that way, or, where there is none, into a new group split off that way (`moveBeside(g, k,
vertical, after)`, Right being after by the reading direction as Open Right is); a group's only tab with no group that
way shows Move there but not enabled, as it would only make its own group again (`canMoveBeside`). A move never asks
`canClose` and takes the tab's content along (or is shown by the new group's view, for a key `sharesGroupContent`
picks), the focus following it. Unsplit puts the group's tabs into the group beside it; then come the app's
`tabMenu`. A tab dragged onto another group goes in among its tabs, and onto a group's edge (lit while it is over it)
splits the group there; Escape lets it go. A key shows in one group at a time, moved rather than opened again, unless
`allowsTwice` says it may show in two (a file in both halves of a split), each with its own content; a group's only
tab that may not show twice does not open right or down. A group left empty closes, the focus going to the one in its
place; the last stays, saying so. `focusedGroup` is the group the focus is in or was last; `open(k)` opens there, and
`openIn(g, k)` in `g`. `addMenu` (`Option[TabGroup[K] => List[MenuItem]]`, None by default) gives every group a "+"
after its last tab (`TabBar.addMenu`, above), groups made later by a split, a move or a drag too, its menu made for the
group whose "+" was pressed so the app opens its new tabs there with `openIn`; each "+" goes with its group, and
`addLabel` names them all. `contentBackground` (`Option[Color]`, None for the theme's background) fills the area under
every group's tabs, groups made later too: `Some(Color.Clear)` lets a picture behind the screen (a `Backdrop`) show
through what the tabs show, the strip of tabs keeping the theme's background; `barBackground` (the same, for every group's strip of
tabs) gives the strip one too, a colour with some alpha letting the picture through it, the tab shown keeping its mark.
`commands` puts the actions on a keymap, as Primary+\ (open right), Primary+Shift+\ (down), Primary+Alt+Right and Left
(the next and the previous group), Primary+W or Primary+F4 (close) and Primary+Shift+Enter (maximize, below), which
the app can `rebind`. Arrows keep to the focus layer's rules: Down from a bar goes into what it shows, Up comes back,
and a split's arrows cross it. `tabLayout` is how it all lies, a `TabLayout` tree (the groups' tabs by id and the one
shown; the splits' direction and share), plain enough for fabric (`given RW[TabLayout] = RW.gen`) and kept as JSON
without it (`TabLayout.codec`);
`layoutChanged` tells each change and `restore` lays a kept one out again, leaving out what it no longer finds.
A tab closes only once `canClose` (`K => Task[Boolean]`, always true by default) lets it, by the ×, the menu, the keys
and `closeAllTabs()`: the app asks as it likes (`Dialogs.saveChanges`) and a tab it answers false for stays. It is asked
for a key's last tab only, a second view of it closing at once. Close Others and Close All ask one tab at a time, in
order, and stop at the first that stays, those before it closed. A close asked again while its answer is awaited does
nothing. Disposing of the groups asks nothing (it cannot wait): an app closing its window asks `closeAllTabs()` first.
`tab(k)` finds a key's tabs in every group, and `dirty(k, on)` puts a dot after their label (`isDirty`, `dirtyKeys`), on
tabs opened later too.

One group can be shown alone over the whole area, as VS Code maximizes a group and IntelliJ hides the other splits:
`maximize(g)` hides every other group and the handles between them, closing nothing (their tabs, the tab each shows,
each content alive off the screen as a tab not shown is, and every split's ratio, nested ones too, kept as they were),
and `unmaximize()` lays them out again exactly as they were; `toggleMaximized()` does one or the other for the focused
group. `maximized` (`Val[Option[TabGroup[K]]]`) says which, for an app's own menu ("Restore") or a mark; nothing is
maximized while there is one group. The tab's menu has Hide Other Groups after Unsplit while there are two groups or
more, and Show All Groups while one is maximized (`TabGroups.maximizeWord`, `unmaximizeWord`, in EveryUI's eleven
languages); `commands` adds `tabs.maximize` on Primary+Shift+Enter, which toggles it. Whatever would land where the
person cannot see it lays every group out again first: `open` or `openIn` into a hidden group (or of a key open in
one already that may not show twice), a split (Open Right and Down, a drag onto the group's edge), a move to another
group (`move`, Move Right and the rest), Unsplit, `restore`, and the maximized group's last tab closing (it closes, and
the rest show); so does the hidden groups closing until it is the only one. A tab opened into the maximized group
itself, and one reordered among its tabs (by a drag, which lands only in the group that shows), leave it maximized.
While it is, the focus stays in it (it moves in as it is maximized), and the next and previous group go nowhere else.
Maximizing is not a change of layout: `tabLayout` stays the whole layout, as a saved one should, and `layoutChanged`
is not told either way; hear `maximized` instead.

```scala
editor.tabMenu @= (f => List(if (editor.maximized().nonEmpty) MenuItem("Restore", () => editor.unmaximize())
  else MenuItem("Focused", () => editor.groups().find(_.has(f)).foreach(editor.maximize))))   // after the groups' own
```

The keys `sharesGroupContent` picks show one view per group rather than a content each: `groupContent` makes a
`GroupContent[K]` for a group the first time it shows such a key, `show(k)` tells it which it shows now and `hide()`
when the group shows another kind of tab or none; it lives as long as the group and is disposed of with it. A key moved
in, or opened in a second group, is shown by that group's view. One editor per group, while terminals and
conversations keep a content each:

```scala
val editor = new TabGroups[Doc](_.name, d => new Terminal(d), _.id, sharesGroupContent = _.isFile,
  groupContent = _ => new GroupContent[Doc] { override def show(d: Doc): Unit = code.model @= modelOf(d) })
editor.canClose @= (d => if (!editor.isDirty(d)) Task.pure(true)
  else Dialogs.saveChanges("Save changes to " + d.name + "?", "").flatMap {
    case SaveChoice.Save => save(d).map(_ => true)
    case SaveChoice.Discard => Task.pure(true)
    case SaveChoice.Cancel => Task.pure(false)
  })
```

`demo=tabgroups` has four files, a terminal and a conversation in three groups: the files in one view per group, the
others a view each, each counting since it was made; two files hold unsaved work and ask before they close; each
group's "+" opens a new file, terminal or conversation in it; its layout kept; M or Primary+Shift+Enter shows the
focused group alone, or every group again (`maximized=<i>` starts with that group alone). Tabs are not dragged on a
television, which has no pointer; the menu and the keys do it all there.

### Tag (`everyui.kit`)

A word in a small coloured pill, for a cell or a row: solid (`new Tag("main", Theme.accent())`, the word white or near
black to read on it) or `soft` (a wash of the colour with an edge and the word in it). `Tag.tone("success")` names a
colour of the theme (accent, success, warning, danger, info, muted, text, or a palette extra). It takes no room while
its word is empty. `everyui.component.Chip` is the older fixed pill, which stays as it is.

## Voice audio (`everyui.audio`)

For a voice mode that sends what is said to a server's speech to text and plays back its text to speech: the
microphone as a stream of PCM chunks, and a player that plays PCM chunks as they arrive. Both are raw 16-bit mono
PCM, as `Array[Short]` (`chunk.bytes` and `Pcm.toBytes` give the little-endian bytes most services take, and
`Pcm.fromBytes` reads them back). Everything is heard on the thread that draws.

```scala
import everyui.audio.{CaptureEnd, Microphone, Pcm, PcmPlayer}

val player = new PcmPlayer(24000)                       // the rate the text to speech sends
player.drained.attach(_ => listening @= true)           // it has said everything queued
speech.onAudio(bytes => player.play(Pcm.fromBytes(bytes)))   // the app's own connection

val capture = Microphone.capture(sampleRate = 16000, frames = 2048)(
  chunk => {
    meter @= chunk.level                                // RMS, 0 to 1
    if (chunk.level > 0.05 && player.playing()) player.stop()   // barge-in: flushes at once, no drained
    transcriber.send(chunk.bytes)
  },
  why => if (why == CaptureEnd.Denied) Toast.show("The microphone is off"))
...
capture.stop()                                          // the rest comes as a shorter last chunk, then Stopped
```

- `Microphone.supported` and `PcmPlayer.supported` say whether this platform can at all: the web, the desktop and
  Android can; a Roku and an Apple TV cannot (a capture ends at once with `Unavailable`, and a chunk played is over
  at once, unheard).
- The platform records at its own rate (48 kHz, usually) and the chunks are resampled to `sampleRate`, averaged
  going down so little folds back as noise. Chunks are exactly `frames` long except the last.
- `ended` hears one of `Stopped`, `Denied`, `Unavailable` (no microphone), `Lost` (unplugged, or taken by the
  system) or `Failed(message)`, once, after the last chunk.
- Permission: the first capture asks. On the web that is the browser's prompt (the page must be served over HTTPS
  or from localhost). On Android the app's manifest must declare
  `<uses-permission android:name="android.permission.RECORD_AUDIO" />`, and for a television
  `<uses-feature android:name="android.hardware.microphone" android:required="false" />`; the runtime prompt is
  asked for then. A Mac asks the first time the desktop opens a line.
- Echo: `echoCancellation` (on by default) asks the platform to take the player's sound out of the microphone, so
  the person can talk over it. The web passes it to `getUserMedia` (with noise suppression and gain control);
  Android records from VOICE_COMMUNICATION and turns on an `AcousticEchoCanceler` where the device has one (off, it
  records from VOICE_RECOGNITION). Java Sound has none, so on the desktop use a headset or the sound server's echo
  cancelling (PipeWire's echo-cancel module).
- Autoplay: a browser makes no sound until the person has touched the page. EveryUI resumes the page's audio on the
  first pointer or key, so start voice from a tap; sound queued before one waits for it.
- Playback is gapless while chunks arrive faster than they play. `drained` fires once when the queue runs dry
  after the last chunk, including when the next chunk is merely late, so the app knows whether more is coming.
  `stop()` silences it at once and fires nothing; `volume` is 0 to 1; `close()` lets the platform's output go.
- The web records through an AudioWorklet whose code ships inside `everyui-web` (a Blob URL), falling back to a
  ScriptProcessor where there is no AudioWorklet; the desktop through `TargetDataLine` and `SourceDataLine`;
  Android through `AudioRecord` and a streaming `AudioTrack` used as an assistant's speech.
- `demo=voice` in the example app records with a level meter, plays back what it recorded (or, with Loopback,
  as it is heard), and streams a tone that Stop cuts off.
