package everyui.kit

import everyui.component.*
import everyui.i18n.Direction
import everyui.layout.HorizontalLayout
import everyui.theme.Theme
import reactify.*

/** A strip of tabs on a rule, `width` wide, scrolling along when there are more than fit: a page's sections, an
  * editor's open files. `current` is the key of the one shown. With `showOnFocus` a tab shows itself as the focus
  * lands on it, as moving along a TV's tab bar does; without, only a click or Enter shows one.
  *
  * {{{
  *   val bar = TabBar(List("overview" -> "Overview", "cast" -> "Cast"), section, width)   // fixed sections
  *
  *   val files = new TabBar(openFile, width, showOnFocus = false)                            // open files
  *   val tab = files.add(path, name, closable = true)
  *   tab.marker @= Some(dot)                            // unsaved
  *   files.closing.attach(path => askThenClose(path))   // the close button, or the app's own Ctrl+W
  * }}} */
class TabBar(current: Var[String], width: => Double, showOnFocus: Boolean = true) extends Container {
  /** The tabs, in order. */
  val tabs: Var[List[TabItem]] = Var(Nil)
  /** Heard with a closable tab's key when its close button is pressed: the app decides (a file with unsaved work asks
    * first) and calls `remove`. */
  val closing: Channel[String] = Channel[String]

  private val scroll = new ScrollContainer(horizontal = true) {
    size.width := width
    size.height @= TabBar.height
    content.layout @= Some(HorizontalLayout(0.0))
    content.children := tabs().toVector
  }
  // the rule runs as far as the tabs do, from the right in a right-to-left language
  private val rule = new Container {
    size.width := math.min(width, scroll.content.size.width())
    position.left := (if (Direction.rtl()) width - size.width() else 0.0)
    size.height @= 1.0
    position.top @= TabBar.height - 1.0
    backgroundColor := Theme.border()
  }

  size.width := width
  size.height @= TabBar.height
  children ++= List(rule, scroll)
  // arriving from elsewhere, the focus lands on the tab being shown rather than the nearest one: a tab can show itself
  // on focus, so the nearest one would be another page
  private def entry(key: String): Unit = apply(key).foreach(entryFocus)
  current.attach(entry)
  tabs.attach(_ => entry(current()))

  /** A tab `label` for `key`, added at `at` among the tabs (at the end, where it is past the last or not given), or in
    * place of the one it replaces (moved to `at` where it is given). Neither the tab shown nor the focus moves: a tab
    * the focus was on that is replaced hands it to the new one. */
  def add(key: String, label: String, closable: Boolean = false, at: Int = -1): TabItem = {
    val tab = new TabItem(key, label, current, closable, k => closing @= k, showOnFocus)
    val replaced = apply(key)
    val was = tabs().indexWhere(_.key == key)
    val rest = tabs().filterNot(_.key == key)
    val place = if (at >= 0) math.min(at, rest.length) else if (was >= 0) was else rest.length
    tabs @= rest.patch(place, List(tab), 0)
    replaced.foreach(_.dispose())
    tab
  }

  /** A tab added just after the tab for `after` (a diff opened beside the file it compares), else at the end. */
  def addAfter(after: String, key: String, label: String, closable: Boolean = false): TabItem = {
    val i = tabs().filterNot(_.key == key).indexWhere(_.key == after)
    add(key, label, closable, if (i < 0) -1 else i + 1)
  }

  /** The tab for `key` taken off the strip. Where it was the one shown, the tab now in its place is shown, else the one
    * before it; the focus on it goes the same way. */
  def remove(key: String): Unit = {
    val gone = apply(key)
    val was = tabs().indexWhere(_.key == key)
    tabs @= tabs().filterNot(_.key == key)
    if (current() == key && tabs().nonEmpty) current @= tabs()(math.min(math.max(0, was), tabs().length - 1)).key
    gone.foreach(_.dispose())
  }

  /** The tab for `key`. */
  def apply(key: String): Option[TabItem] = tabs().find(_.key == key)
}

object TabBar {
  val height: Double = 44.0

  /** A bar of fixed tabs, each a key and its label. */
  def apply(tabs: List[(String, String)], current: Var[String], width: => Double): TabBar = {
    val bar = new TabBar(current, width)
    tabs.foreach { case (key, label) => bar.add(key, label) }
    bar
  }
}
