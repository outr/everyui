package everyui.kit

import everyui.component.*
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.i18n.{Direction, Words}
import everyui.layout.HorizontalLayout
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A strip of tabs on a rule, `width` wide, scrolling along when there are more than fit: a page's sections, an
  * editor's open files. `current` is the key of the one shown, and closing it shows the one shown before it (`remove`).
  * With `showOnFocus` a tab shows itself as the focus lands on it, as moving along a TV's tab bar does; without, only a
  * click or Enter shows one.
  *
  * {{{
  *   val bar = TabBar(List("overview" -> "Overview", "cast" -> "Cast"), section, width)   // fixed sections
  *
  *   val files = new TabBar(openFile, width, showOnFocus = false)                            // open files
  *   val tab = files.add(path, name, closable = true)
  *   tab.marker @= Some(dot)                            // unsaved
  *   files.closing.attach(path => askThenClose(path))   // the close button, or the app's own Ctrl+W
  *   files.tabMenu @= (path => List(MenuItem("Reveal in tree", () => tree.reveal(path))))   // a right-click
  *   files.addMenu @= Some(() => List(MenuItem("New file", () => newFile())))   // a "+" after the last tab
  *
  *   files.add(path, name, closable = true, lead = TabLead.Icon(MaterialIcon.Description))   // led by an icon
  *   val chat = files.add("c-7", "Release plan", closable = true, lead = TabLead.Person(helper))
  *   chat.busy @= true                                  // a ring turns around its avatar while the agent works
  *   chat.busyDetail @= "Running bash"
  * }}} */
class TabBar(current: Var[String], width: => Double, showOnFocus: Boolean = true) extends Container {
  /** The tabs, in order. */
  val tabs: Var[List[TabItem]] = Var(Nil)
  /** Heard with a closable tab's key when its close button is pressed: the app decides (a file with unsaved work asks
    * first) and calls `remove`. */
  val closing: Channel[String] = Channel[String]
  /** Each tab's context menu, by its key, empty (none) by default: asked for with the secondary button, the menu key,
    * Shift+F10 or OK held on the tab (`TabItem.openMenu`). The browser's own menu is kept away only from a tab that has
    * one. */
  val tabMenu: Var[String => List[MenuItem]] = Var((_: String) => Nil)
  /** A "+" after the last tab that drops this menu, made afresh each time, as a browser's new-tab button: none (no
    * button, the bar as it is without one) by default. It stays at the bar's end while the tabs scroll before it. */
  val addMenu: Var[Option[() => List[MenuItem]]] = Var(None)
  /** What the "+" is called: its tooltip and its name to a screen reader. */
  val addLabel: Var[String] = Var(TabBar.addWord)

  private var plus: Option[TabAdd] = None
  // the keys shown, the latest first, however `current` came to change: where the tab shown goes, the one before it
  private var history: List[String] = List(current())
  current.attach(k => history = k :: history.filterNot(_ == k))
  // the way the tabs read on, and back: Right and Left, or the other way round in a right-to-left language
  private def onward: Key = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
  private def backward: Key = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft

  private val scroll = new ScrollContainer(horizontal = true) {
    // with a "+" as wide as the tabs, so it sits just after the last, and no wider than leaves it room at the end
    size.width := (if (addMenu().isEmpty) width else math.max(0.0, math.min(width - TabAdd.width, content.size.width())))
    position.left := (if (Direction.rtl()) width - size.width() else 0.0)
    size.height @= TabBar.height
    content.layout @= Some(HorizontalLayout(0.0))
    content.children := tabs().toVector

    // on past the last tab, the "+"
    override def focusExit(direction: Key): Option[Option[Component]] = plus match {
      case Some(p) if direction == onward && addMenu().nonEmpty => Some(Some(p))
      case _ => super.focusExit(direction)
    }
  }
  // the rule runs as far as the tabs (and the "+") do, from the right in a right-to-left language
  private val rule = new Container {
    size.width := math.min(width, scroll.content.size.width() + (if (addMenu().isEmpty) 0.0 else TabAdd.width))
    position.left := (if (Direction.rtl()) width - size.width() else 0.0)
    size.height @= 1.0
    position.top @= TabBar.height - 1.0
    backgroundColor := Theme.border()
  }

  size.width := width
  size.height @= TabBar.height
  children ++= List(rule, scroll)
  semantics @= Semantics("tablist")
  // arriving from elsewhere, the focus lands on the tab being shown rather than the nearest one: a tab can show itself
  // on focus, so the nearest one would be another page
  private def entry(key: String): Unit = apply(key).foreach(entryFocus)
  current.attach(entry)
  // the tab shown brought into view, once it is laid out, where there are more than fit
  current.attach(k => Platform.later(() => apply(k).filter(_.parent().nonEmpty).foreach(t => scroll.reveal(t))))
  tabs.attach(_ => entry(current()))
  addMenu.attach(_ => placeAdd())
  placeAdd()
  onDispose(() => plus.foreach(_.dispose()))

  // the "+" put on the bar while there is a menu for it, and taken off without one
  private def placeAdd(): Unit = addMenu() match {
    case Some(_) =>
      val p = plus.getOrElse {
        val made = new TabAdd(addLabel, () => addMenu().map(f => f()).getOrElse(Nil)) {
          // back to the last tab
          override def focusExit(direction: Key): Option[Option[Component]] =
            if (direction == backward && tabs().nonEmpty) Some(Some(tabs().last)) else super.focusExit(direction)
        }
        made.position.left := (if (Direction.rtl()) scroll.position.left() - TabAdd.width else scroll.size.width())
        plus = Some(made)
        made
      }
      if (!children().exists(_ eq p)) children += p
    case None => plus.foreach(p => if (children().exists(_ eq p)) children -= p)
  }

  /** A tab `label` for `key`, added at `at` among the tabs (at the end, where it is past the last or not given), or in
    * place of the one it replaces (moved to `at` where it is given). Neither the tab shown nor the focus moves: a tab
    * the focus was on that is replaced hands it to the new one. `lead` goes before the label (`TabLead`). */
  def add(key: String, label: String, closable: Boolean = false, at: Int = -1, lead: TabLead = TabLead.None): TabItem = {
    val tab = new TabItem(key, label, current, closable, k => closing @= k, showOnFocus, lead,
      k => tabMenu()(k))
    tab.event.key.down.attach(k => keyed(key, k))
    val replaced = apply(key)
    val was = tabs().indexWhere(_.key == key)
    val rest = tabs().filterNot(_.key == key)
    val place = if (at >= 0) math.min(at, rest.length) else if (was >= 0) was else rest.length
    tabs @= rest.patch(place, List(tab), 0)
    replaced.foreach(_.dispose())
    tab
  }

  // Home and End to the first and the last tab, and Alt+Shift with an arrow moving the tab along the bar
  private def keyed(key: String, k: KeyEvent): Unit = {
    val i = tabs().indexWhere(_.key == key)
    if (k.key == Key.Other && k.name == "Home") tabs().headOption.foreach(_.focus())
    else if (k.key == Key.Other && k.name == "End") tabs().lastOption.foreach(_.focus())
    else if (k.alt && k.shift && (k.key == onward || k.key == backward) && i >= 0) {
      val to = i + (if (k.key == onward) 1 else -1)
      if (to >= 0 && to < tabs().length) {
        move(key, to)
        apply(key).foreach(_.focus())
      }
    }
  }

  /** A tab added just after the tab for `after` (a diff opened beside the file it compares), else at the end. */
  def addAfter(after: String, key: String, label: String, closable: Boolean = false,
               lead: TabLead = TabLead.None): TabItem = {
    val i = tabs().filterNot(_.key == key).indexWhere(_.key == after)
    add(key, label, closable, if (i < 0) -1 else i + 1, lead)
  }

  /** The tab for `key` taken off the strip. Where it was the one shown, the one shown before it is shown again, as a
    * browser does; with none of those left (a bar laid out afresh), the tab now in its place, else the one before it.
    * The focus on it goes the same way. */
  def remove(key: String): Unit = {
    val gone = apply(key)
    val was = tabs().indexWhere(_.key == key)
    tabs @= tabs().filterNot(_.key == key)
    history = history.filterNot(_ == key)
    if (current() == key && tabs().nonEmpty) current @= history.find(k => apply(k).nonEmpty)
      .getOrElse(tabs()(math.min(math.max(0, was), tabs().length - 1)).key)
    gone.foreach(_.dispose())
  }

  /** The tab for `key` moved to `at` among the others (to the end, where it is past the last or not given), the same
    * tab, so what is shown and the order they were shown in stay as they were. */
  def move(key: String, at: Int = -1): Unit = apply(key).foreach { tab =>
    val rest = tabs().filterNot(_.key == key)
    tabs @= rest.patch(if (at >= 0) math.min(at, rest.length) else rest.length, List(tab), 0)
  }

  /** The keys of the tabs open, the one shown first and then the rest in the order they were last shown; a tab not
    * shown since the bar was made comes after, in its place along the strip. */
  def recent: List[String] = {
    val seen = history.filter(k => apply(k).nonEmpty)
    seen ++ tabs().map(_.key).filterNot(seen.contains)
  }

  /** The tab for `key`. */
  def apply(key: String): Option[TabItem] = tabs().find(_.key == key)

  /** The tab for `key`'s menu, dropped from it, where it has one. */
  def openMenu(key: String): Unit = apply(key).foreach(_.openMenu())

  /** The "+"'s menu, dropped from it, where it has one. */
  def openAddMenu(): Unit = plus.filter(p => children().exists(_ eq p)).foreach(_.open())

  /** The "+", while there is a menu for it. */
  def addButton: Option[TabAdd] = plus.filter(p => children().exists(_ eq p))
}

object TabBar {
  val height: Double = 44.0

  /** What the "+" is called unless the app says (`addLabel`). */
  def addWord: String = {
    val said = Words("tabs.add")
    if (said == "tabs.add") "New…" else said
  }

  /** A bar of fixed tabs, each a key and its label. */
  def apply(tabs: List[(String, String)], current: Var[String], width: => Double): TabBar = {
    val bar = new TabBar(current, width)
    tabs.foreach { case (key, label) => bar.add(key, label) }
    bar
  }
}
