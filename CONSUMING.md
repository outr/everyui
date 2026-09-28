# Using EveryUI from another build

EveryUI is published locally for now: run `sbt publishLocal` in this repository, and the artifacts go to
`~/.ivy2/local/com.outr/`.

## Coordinates

| Module | What it is | Platforms |
|---|---|---|
| `"com.outr" %% "everyui-web" % "0.1.0-SNAPSHOT"` | the UI layer (components, layouts, focus, keymaps, screens, theme, i18n) over the DOM | Scala.js |
| `"com.outr" %% "everyui-network" % "0.1.0-SNAPSHOT"` | HTTP and DurableSocket, with Spice as the core | JVM and Scala.js |
| `"com.outr" %% "everyui-seo" % "0.1.0-SNAPSHOT"` | what an app's server writes for search and answer engines | JVM |

Everything is built with Scala **3.10.0-RC3** and Scala.js **1.22.0**, so a consumer needs Scala 3.10 (an RC or
later) to read it.

A backend is chosen when EveryUI is compiled: the shared layer names `everyui.render.RenderNode`, and each backend
supplies it. So there is no separate core artifact. The shared layer ships inside each backend module, and
`everyui-web` is the web one. The desktop, Android, Roku and tvOS backends are still compiled from source (see NaboTV's
`build.sbt`).

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

The browser never hands some chords to a page: Ctrl+W, Ctrl+T, Ctrl+Tab and Ctrl+N in a normal tab. They work when
the app runs as an installed app window. Choose defaults with that in mind.

### Embeds

An embed is something in the page that handles its own keys, such as a code editor. It is marked
`data-everyui-embed="<name>"` on the web, or with the node property `Host.embedKey` on the desktop. A field that the
app did not make counts as the embed `"field"`. While the page's focus is inside an embed, every key is the embed's,
Escape included. EveryUI does not move the focus and does not `preventDefault`. Only two kinds of command still run:

```scala
Keymap.global("file.new").foreach(_.inEmbeds @= true)       // wins inside every embed
Keymap.embed("editor", Chord("W", primary = true))           // the chords this embed leaves to the app
```

An embed gives back a key it did not want. For example, an editor's Escape with no popup to close becomes Back:
`Focus.handBack("Escape")`.

## General components

Beyond the core components (`Button`, `Dialog`, `Table`, `ScrollContainer`, and so on), `everyui.kit` has these:

- `TabBar` and `TabItem`: a strip of tabs, fixed (`TabBar(List(key -> label), current, width)`) or open-ended
  (`add`, `remove`, closable tabs, the `closing` channel), with `icon` and `marker` slots beside each label.
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
The first side is always the left (or top) one, in a right-to-left language too.

### Menu (`everyui.menu`)

A list of choices: a context menu at the pointer (`Menu.at`), a menu dropped from a button (`Menu.below`), or a panel
in the middle of the screen under a title (`Menu.show`, what a remote wants). A `MenuItem` has a label, an action, an
optional icon, a line of detail, a shortcut hint (a keymap `Chord`) or a note, and may be disabled, dangerous,
checkable or a submenu; `MenuItem.separator` is a rule. Up and Down move round the entries that can be chosen, OK or a
click chooses (every menu closes, then the action runs), Right opens a submenu and Left, Back or Escape closes it, and
the pointer resting on an entry focuses it. The focus goes back where it was. `Menu.context` wires a component's
secondary click (`event.pointer.menu`, the new `PointerKind.Menu`), the menu key and OK held on a remote.

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

## Monaco (everyui-monaco)

`"com.outr" %% "everyui-monaco" % "0.1.0-SNAPSHOT"` (Scala.js) is Monaco, VS Code's editor, as components: a port of
flutter_monaco_editor with the same API in Scala. It brings Monaco 0.57.0's files as resources under
`everyui-monaco/0.57.0/`: fetched from npm with their integrity checked by the build, never committed.

```scala
import everyui.monaco.*
import everyui.monaco.Monaco.ui          // runs what follows a call where it completed, on the UI thread

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

`MonacoDiffEditor` / `MonacoDiffController` are the diff editor. Every call answers as a `Future`; themes and
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

## Collections

Long lists and trees, in packages of their own (`everyui.list`, `everyui.tree`). Each takes the focus as one component
with a cursor among its rows, so the arrows, a remote's d-pad and the pointer all move the same cursor. At either end an
arrow leaves it as the focus layer says (`exitFocus` on a container around it).

### VirtualList (`everyui.list`)

Only the rows that show exist; as it scrolls, a row leaving one end is handed the item arriving at the other. The items
are a `Var[Vector[T]]` the app keeps, each with a key: the cursor (`current`), the selection and the scroll are kept by
key as items come and go. Rows are `rowHeight` tall, or `measured = true` for rows as tall as each draws itself.

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

Keys: Up and Down move the cursor, Right opens a node or goes to its first child, Left closes it or goes to its parent
(mirrored in a right-to-left language), Enter activates and opens or closes a folder. A click on the chevron opens or
closes it. Multi-select is the list's (Shift and Primary with clicks and arrows, Primary+A).
