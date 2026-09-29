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
  one of a fixed set (on a Roku, list the cases, since its compiler does not read an enum's `values` yet), and
  `Codec.from(encode, decode)` covers anything else. An app with fabric keeps a case class as JSON through its `RW`:
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
network and keeps what it fetched, so the app still opens when the network is gone.

**Icons.** PNG, square: **192×192** and **512×512** with purpose `any` are what browsers require to offer an install;
add a **512×512 `maskable`** one (the picture within the middle 80%, the rest background) for Android's shaped icons.
Apple devices use the touch icon (180×180 is their size; the 512 one is scaled).

The page must be served over HTTPS (or from `localhost`). Chromium browsers then offer to install it from the address
bar; Safari adds it with Add to Dock or Add to Home Screen. In its own window the browser keeps no tabs, so more of the
chords a browser keeps for itself in a tab (Ctrl+T, Ctrl+N, Ctrl+Tab and others) reach the app's keymap; which ones is
the browser's own choice and changes between browsers, so try the chords the app relies on in the installed window.

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
the web guests' files out of their modules (`everyui-monaco/<version>/`, `everyui-terminal/<version>/`), a directory to
copy next to the page: `IO.copyDirectory(everyuiWebGuests.value, out)`. Both are `everyui.sbt.ModuleFiles`, which a
build without the plugin can call itself.

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
| `everyuiFonts` | EveryUI's fonts (and Material Icons' codepoints), out of the backend on the classpath: a directory to copy into the app's `fonts/` |
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
