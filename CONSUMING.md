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
kept and shown again. A screen made for one showing (`oneShowing`) is taken apart by its manager once nothing can go
back to it. Taking a large tree apart runs a slice a turn (`Component.disposeGradually(roots, done)`), so a screen of
thousands of components never outlasts a Roku's watchdog; disposing twice does nothing.

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
- the clock: while anything reads `Clock.now` (`Format.elapsed`) or listens to `Clock.tick`, a frame a second.

Motion (`Component.motion`) is run by the platform and needs no frames of the app's. Timers (`Platform.after`,
`Platform.later`) are not frames either.

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

The bundle expects an element with the id `app` (it makes one if there is none) and the bundled faces in `fonts/`
next to the page. Copy `assets/fonts/*.ttf` from this repository, and add `assets/images` if the app uses them. A
minimal `index.html`:

```html
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    @font-face { font-family: 'Open Sans'; font-weight: 400; src: url(fonts/OpenSans-Regular.ttf) format('truetype'); }
    @font-face { font-family: 'Open Sans'; font-weight: 600; src: url(fonts/OpenSans-SemiBold.ttf) format('truetype'); }
    @font-face { font-family: 'Material Icons'; font-weight: 400; src: url(fonts/MaterialIcons-Regular.ttf) format('truetype'); }
    @font-face { font-family: 'Roboto Mono'; font-weight: 400; src: url(fonts/RobotoMono-Regular.ttf) format('truetype'); }
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

## General components

Beyond the core components (`Button`, `Dialog`, `Table`, `ScrollContainer`, and so on), `everyui.kit` has these:

- `TabBar` and `TabItem`: a strip of tabs, fixed (`TabBar(List(key -> label), current, width)`) or open-ended
  (`add`, `remove`, closable tabs, the `closing` channel), with `icon` and `marker` slots beside each label. `add(key, label, closable,
  at = i)` puts a tab at a place and `addAfter(key0, key, label)` beside another (a diff by its file); closing the tab
  shown shows the one now in its place, else the one before; a middle click closes a closable tab.
- `Toast.show(words, seconds, top, margin)`: one short message at a time.
- `AsyncPress`: a press that runs slow work, with a delayed spinner, and failures toasted.
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
caller places the panel), which is how menus sit where they were opened.

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
import everyui.picker.Segment

new FilterPicker[Model](q => models.find(q), _.id, _.name, _.providerName,
  segments = List(Segment.all("All"), Segment("local", "Local"), Segment("anthropic", "Anthropic"),
    Segment("openai", "OpenAI"), Segment("fireworks", "Fireworks.ai")),
  groupOf = _.provider).open()
```

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
  uses too), scroll sideways rather than wrap, and have a copy button where the platform has a clipboard. Tables
  scroll sideways too. Each takes the focus only when there is somewhere to scroll.
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
`max-height` of their lines). `demo=textcut` shows rows of them made off the screen.

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
  are left out. Faces are carried from the page's `@font-face` rules and the faces the app registered; a style sheet of
  another origin is read again where it allows that, and a face that cannot be read falls back to the system's.
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

`everyuiWebFonts` is EveryUI's fonts out of the everyui-web jar, a directory to copy as `fonts/`, and `everyuiWebGuests`
the web guests' files out of their modules (`everyui-monaco/<version>/`, `everyui-terminal/<version>/`, and
everyui-web's own `everyui-hls/<version>/`), a directory to copy next to the page:
`IO.copyDirectory(everyuiWebGuests.value, out)`. Both are `everyui.sbt.ModuleFiles`, which a
build without the plugin can call itself.

An app with a face of its own (`Faces.register`) names its font files in `everyuiAppFonts`, and `everyuiWebFonts` (and
`everyuiFonts`, for the other platforms) copies them in beside EveryUI's, so the bundle task copies one directory as
before. A file with the name of one of EveryUI's (`OpenSans-Regular.ttf`), or two of the app's with one name, fails
the build, naming each, rather than one font quietly taking another's place.

```scala
everyuiAppFonts := Seq(baseDirectory.value / "fonts" / "Brand-Regular.ttf", baseDirectory.value / "fonts" / "Brand-Bold.ttf"),
webBundle := Def.uncached {
  // …
  IO.copyDirectory(everyuiWebFonts.value, out / "fonts")   // EveryUI's fonts and Brand-Regular.ttf, Brand-Bold.ttf
}
```

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

Keep the default. Serve the bundle compressed (brotli where the browser takes it): that matters more than any of
these. Closure's JavaScript is a third smaller again over the wire, so an app that ships `WebTarget.Js` alone and
counts every kilobyte may keep it on `Compile / fullLinkJS / scalaJSLinkerConfig` while Scala.js still has it (with the
minifier left on, which makes Closure's output 2% smaller still); an app on `WebTarget.Both` sends most browsers the WebAssembly build, which Closure never
touches.

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
the app's way (its tab, `switchToModel`, the range revealed); with neither, Monaco stays where it is. Every call answers as a `Future`; themes and
providers are global and reach every editor, including ones made later.

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

## Collections

Long lists and trees, in packages of their own (`everyui.list`, `everyui.tree`). Each takes the focus as one component
with a cursor among its rows, so the arrows, a remote's d-pad and the pointer all move the same cursor. At either end an
arrow leaves it as the focus layer says (`exitFocus` on a container around it).

### VirtualList (`everyui.list`)

Only the rows that show exist; as it scrolls, a row leaving one end is handed the item arriving at the other. The items
are a `Var[Vector[T]]` the app keeps, each with a key: the cursor (`current`), the selection and the scroll are kept by
key as items come and go. Rows are `rowHeight` tall, or `measuredRows = true` for rows as tall as each draws itself.

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
kept current), a `count` badge ("99+") and a small `mark` (a pin, a lock); `unread` is bold with its time lit. Items
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

### Pointer buttons and modifiers

A `PointerEvent` carries its `button` (`PointerEvent.PrimaryButton`, `MiddleButton`, `SecondaryButton`; a finger is the
primary) and the modifiers held with it (`ctrl`, `meta`, `alt`, `shift`, and `primary` for the platform's shortcut
modifier). `click` is the primary button's everywhere; `event.pointer.menu` is the secondary's; `event.pointer.middle` is
a middle click, which closes a closable `TabItem`.

```scala
row.event.click.attach(e => if (e.shift) extend() else if (e.primary) toggle() else pick())
tab.event.pointer.middle.attach(_ => close())
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
