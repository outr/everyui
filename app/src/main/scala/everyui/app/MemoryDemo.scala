package everyui.app

import everyui.component.{Button, Container, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{ListRow, RichAction, RichExpansion, RichGroup, RichList, RichLook, RichSection, RichStyle}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** An assistant's memories as a sidebar's blocks, grouped by the space that keeps them (You, Personas, Workspaces), each
  * a title and a line of summary that expands in place, on a click, a tap or Enter, to its whole text in Markdown (some
  * long: headings, lists, code, a quote, a link) with Pin and Delete (in the danger colour) under it. One opens at a time
  * unless `several=1`, and the pane beside the list (wide) switches between the two and draws the list as a tree;
  * narrow, the list fills the screen. `theme=light`, `rtl=1`, `style=tree`, `open=<keys>` to start with those expanded
  * and `find=<words>` to start narrowed. Logs `memory ok` once built, and each thing done. */
object MemoryDemo {
  private case class Memory(id: String, label: String, summary: String, text: String, by: String, pinned: Boolean = false)

  private val you: RichGroup = RichGroup("you", "You", Some(MaterialIcon.Person))
  private val personas: RichGroup = RichGroup("personas", "Personas", Some(MaterialIcon.Psychology))
  private val workspaces: RichGroup = RichGroup("workspaces", "Workspaces", Some(MaterialIcon.Workspaces))

  // (lines joined rather than a margin stripped: a Roku has no stripMargin)
  private val release: String = List("## Release checklist", "",
    "Before anything is published, every one of these holds:", "",
    "1. The desktop's specs pass after a clean, under `xvfb-run`.",
    "2. The web, Android and tvOS compile.",
    "3. The Roku channel emits **strict** and lints with no errors.",
    "4. NaboTV's channel emits against the snapshot through a private ivy.", "",
    "> A failed install wipes the dev channel and its sign-in, so the lint comes first.", "",
    "Publish from a clean, detached SHA:", "",
    "```", "git checkout --detach origin/master", "sbt publishLocal", "```", "",
    "Each app then builds against what was published, never the checkout.").mkString("\n")

  private val made: Vector[(String, Memory)] = Vector(
    "you" -> Memory("m-1", "Prefers curly braces", "Scala written with braces, never the indented syntax",
      "Matt prefers **curly braces** in Scala, not the braceless Scala 3 syntax. Convert any file that uses it with " +
        "`-rewrite -no-indent`, and keep new code in braces.", "Matt", pinned = true),
    "you" -> Memory("m-2", "Release checklist", "What must hold before anything is published", release, "Matt"),
    "you" -> Memory("m-3", "Coffee", "Black, no sugar", "Black, no sugar.", "Matt"),
    "ada" -> Memory("m-4", "Bernoulli numbers", "Notes on computing them with the engine",
      "### Note G\n\nThe engine computes the Bernoulli numbers in a loop of operations, each *variable* holding one " +
        "intermediate value:\n\n- `V1` holds the constant 1\n- `V2` holds 2\n- `V3` holds *n*\n\nThe table shows each " +
        "step's operation and the variables it reads and writes. A long word that must still fit: " +
        "Supercalifragilisticexpialidociously-long-identifier-without-any-breaks-at-all.", "Ada Lovelace"),
    "ada" -> Memory("m-5", "Likes long walks", "Mentioned twice", "Likes long walks along the river after a day's work.",
      "Ada Lovelace"),
    "archivist" -> Memory("m-6", "Citation style", "Chicago, author-date",
      "Cite in **Chicago author-date** style: (Author Year, page). See " +
        "[the quick guide](https://www.chicagomanualofstyle.org/tools_citationguide.html).", "Archivist"),
    "everyui" -> Memory("m-7", "No spatial focus", "Focus hand-off is explicit, never by position",
      "Focus moves only where a layout, a declared exit or a default says. **Never** to the nearest component by " +
        "position: that guesses wrong as soon as two rows are not the same height.\n\nThe arrows in a list move its " +
        "cursor; Right goes onto a row's action, then its buttons, and Left back.", "Matt", pinned = true),
    "everyui" -> Memory("m-8", "Text within its box", "Wraps or is cut to its box by default",
      "Text wraps or is cut to its box by default; an app may say otherwise. Text that fits is unchanged.", "Matt"),
    "everyui" -> Memory("m-9", "Roku limits", "2 MB of scripts, a launch timeout, name clashes",
      "- Scripts are capped at **2 MB**.\n- A channel that takes too long to launch is stopped.\n- Two functions of " +
        "the same name clash.\n- Options are flattened.\n\nAll of these fail only on the device.", "Matt"),
    "voidcraft" -> Memory("m-10", "Memory panel", "A tree of every space's memories, with search",
      "The Memory panel shows every space's memories as a tree: You, Personas, Workspaces, Rooms. A click opens a " +
        "memory to its whole text and offers to pin or delete it.", "Matt"),
    "voidcraft" -> Memory("m-11", "Sidebar width", "280 wide, a drawer on a phone",
      "The sidebar is **280** wide and keeps its width as the window is resized; on a phone it is a drawer.", "Matt"))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val memories: Var[Vector[(String, Memory)]] = Var(made)
    val sections: Var[Vector[RichSection[Memory]]] = Var(Vector.empty)
    def group(): Unit = {
      val all = memories()
      def in(space: String): Vector[Memory] = all.filter(_._1 == space).map(_._2)
      sections @= Vector(
        RichSection("you", "Matt", in("you"), icon = Some(MaterialIcon.Person), group = Some(you)),
        RichSection("ada", "Ada Lovelace", in("ada"), group = Some(personas)),
        RichSection("archivist", "Archivist", in("archivist"), group = Some(personas)),
        RichSection("everyui", "everyui", in("everyui"), icon = Some(MaterialIcon.Workspaces), group = Some(workspaces)),
        RichSection("voidcraft", "voidcraft", in("voidcraft"), icon = Some(MaterialIcon.Workspaces), group = Some(workspaces),
          empty = Some("Nothing remembered here yet")))
    }
    memories.attach(_ => group())
    group()

    val list = new RichList[Memory](sections, _.id, m => RichLook(m.label, Some(m.summary + " · saved by " + m.by),
      mark = if (m.pinned) Some(MaterialIcon.PushPin) else None), search = true)
    list.style @= (if (Platform.launchArg("style") == "tree") RichStyle.Tree else RichStyle.Blocks)
    list.expandsOne @= Platform.launchArg("several") != "1"
    def pin(m: Memory): RichAction = RichAction(MaterialIcon.PushPin, if (m.pinned) "Unpin" else "Pin", () => {
      memories @= memories().map(p => if (p._2.id == m.id) p._1 -> p._2.copy(pinned = !m.pinned) else p)
      Platform.log("memory " + (if (m.pinned) "unpinned " else "pinned ") + m.id)
    })
    def delete(m: Memory): RichAction = RichAction(MaterialIcon.Delete, "Delete", () => {
      memories @= memories().filterNot(_._2.id == m.id)
      Platform.log("memory deleted " + m.id)
    }, danger = true)
    list.expansion @= ((m: Memory) => Some(RichExpansion(m.text, List(pin(m), delete(m)))))
    // what the search finds: the whole text as well as the title and the summary
    list.matches @= ((m: Memory, words: String) => RichList.holds(RichLook(m.label, Some(m.summary)), words) ||
      m.text.toLowerCase.contains(words.toLowerCase))
    list.expanded.attach(k => Platform.log("memory expanded " + k.toList.sorted.mkString(",")))

    val heading = new TextView {
      content := (if (list.expandsOne()) "One memory open at a time" else "Several memories open")
      font.size @= 20.0
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
    }
    val mode = new Button("Open several", () => {
      list.expandsOne @= !list.expandsOne()
      Platform.log("memory one " + list.expandsOne().toString)
    })
    mode.label := (if (list.expandsOne()) "Open several" else "One at a time")
    val style = new Button("Draw as a tree", () => {
      list.style @= (if (list.style() == RichStyle.Blocks) RichStyle.Tree else RichStyle.Blocks)
      Platform.log("memory style " + list.style().toString)
    })
    style.label := (if (list.style() == RichStyle.Blocks) "Draw as a tree" else "Draw as blocks")
    val pane = new Container {
      backgroundColor := Theme.surface()
      children ++= List(heading, mode, style)
    }
    heading.position.left := ListRow.along(24.0, heading.size.width(), pane.size.width())
    heading.position.top @= 24.0
    mode.position.left := ListRow.along(24.0, mode.size.width(), pane.size.width())
    mode.position.top @= 72.0
    style.position.left := ListRow.along(36.0 + mode.size.width(), style.size.width(), pane.size.width())
    style.position.top @= 72.0
    val wide: Val[Boolean] = Val(ui.size.width() >= 640.0)
    list.size.width := (if (wide()) 280.0 else ui.size.width())
    list.size.height := ui.size.height()
    pane.visible := wide()
    list.position.left := (if (wide() && Direction.rtl()) ui.size.width() - 280.0 else 0.0)
    pane.position.left := (if (Direction.rtl()) 0.0 else list.size.width())
    pane.size.width := math.max(0.0, ui.size.width() - list.size.width())
    pane.size.height := ui.size.height()
    ui.children ++= List(list, pane)
    list.focus()
    val open = Platform.launchArg("open").split(",").filter(_.nonEmpty).toSet
    if (open.nonEmpty) list.expanded @= open
    if (Platform.launchArg("find").nonEmpty) list.query @= Platform.launchArg("find")
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
    Platform.log("memory ok " + sections().map(s => s.title + " " + s.items.size.toString).mkString(", "))
  }
}
