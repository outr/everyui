package everyui.kit

import everyui.Color
import everyui.component.*
import everyui.event.Key
import everyui.focus.Focus
import everyui.menu.MenuItem
import everyui.theme.Theme
import reactify.*

/** One group of a `TabGroups`: a `TabBar` over what its tab being shown shows. Each tab's content is made the first
  * time it is shown and kept, the tabs not shown off the screen but alive, until the tab closes (disposed of) or moves
  * to another group (taken along). An empty group says so, and that is where the focus lands in it. */
class TabGroup[K](owner: TabGroups[K]) extends Container {
  /** Its tabs, in order. */
  val keys: Var[Vector[K]] = own(Var(Vector.empty[K]))
  /** The id of the tab being shown (the bar's `current`). */
  val shown: Var[String] = own(Var(""))
  /** The tab being shown. */
  val selected: Val[Option[K]] = own(Val(keys().find(k => owner.id(k) == shown())))
  val bar: TabBar = new TabBar(shown, size.width(), showOnFocus = false)
  /** Where the tab being shown shows its content. */
  val body: Container = new Container {
    size.width := TabGroup.this.size.width()
    size.height := math.max(0.0, TabGroup.this.size.height() - TabBar.height)
    position.top @= TabBar.height
  }
  body.size.width.attach(_ => body.node.setClip(body.size.width(), body.size.height()))
  body.size.height.attach(_ => body.node.setClip(body.size.width(), body.size.height()))
  private val empty: TextView = new TextView {
    content := TabGroups.noTabs
    font.size @= 14.0
    color := Theme.muted()
    focusable @= true
    visible := keys().isEmpty
  }
  empty.position.center := body.size.width() / 2.0
  empty.position.middle := body.size.height() / 2.0

  private var made: Map[K, Component] = Map.empty
  /** The one view this group shows its tabs that share one in (`TabGroups.sharesGroupContent`), once it has shown one,
    * and the tab it was last told it shows. */
  private var shared: Option[GroupContent[K]] = None
  private var told: Option[K] = None

  backgroundColor := (if (owner.contentBackground().isEmpty) Theme.background() else Color.Clear)
  // given its own, the area under the tabs takes it and the strip keeps the theme's, made once one is first given
  private var apart = false
  private def part(c: Option[Color]): Unit = if (c.nonEmpty && !apart) {
    apart = true
    bar.backgroundColor := Theme.background()
    body.backgroundColor := owner.contentBackground().getOrElse(Theme.background())
  }
  part(owner.contentBackground())
  follow(owner.contentBackground, (c: Option[Color]) => part(c))
  children ++= List(bar, body)
  body.children += empty
  bar.tabMenu := ((id: String) => keyOf(id).map(k => owner.menuFor(this, k)).getOrElse(Nil))
  bar.closing.attach(id => keyOf(id).foreach(k => owner.close(this, k)))
  // the groups' "+", its menu made for this group, and what it is called
  private def addFor(m: Option[TabGroup[K] => List[MenuItem]]): Unit = bar.addMenu @= m.map(f => () => f(this))
  addFor(owner.addMenu())
  follow(owner.addMenu, (m: Option[TabGroup[K] => List[MenuItem]]) => addFor(m))
  bar.addLabel @= owner.addLabel()
  follow(owner.addLabel, (w: String) => bar.addLabel @= w)
  // between the tabs and what the one shown shows, said rather than found by where they lie
  bar.exitAction(Key.ArrowDown, () => Focus.candidates(body).headOption.foreach(_.focus()))
  body.exitAction(Key.ArrowUp, () => focusTab())
  shown.attach(_ => show())
  keys.attach(_ => show())

  /** The tab with id `id`. */
  def keyOf(id: String): Option[K] = keys().find(k => owner.id(k) == id)

  def has(k: K): Boolean = keys().contains(k)

  /** `k` added at `at` (at the end where not given), with its content where it brings one along from another group. */
  def add(k: K, at: Int = -1, content: Option[Component] = None): Unit = if (!has(k)) {
    content.foreach { c =>
      fit(c)
      made = made + (k -> c)
    }
    val tab = bar.add(owner.id(k), owner.label(k), closable = true, at = at, lead = owner.lead(k))
    owner.draggable(this, k, tab)
    if (owner.isDirty(k)) tab.marker @= Some(TabGroups.dirtyDot())
    val all = keys() :+ k
    keys @= bar.tabs().flatMap(t => all.find(x => owner.id(x) == t.key)).toVector
  }

  /** `k` shown. */
  def select(k: K): Unit = if (has(k)) shown @= owner.id(k)

  /** `k` taken out, with its content where it was made, which is now the caller's (to dispose of, or to take along). */
  def take(k: K): Option[Component] = if (!has(k)) None else {
    val content = made.get(k)
    made = made - k
    content.foreach(c => body.children -= c)
    bar.remove(owner.id(k))
    keys @= keys().filterNot(_ == k)
    content
  }

  /** `k` moved to `at` among the tabs. */
  def reorder(k: K, at: Int): Unit = if (has(k)) {
    val content = made.get(k)
    val was = shown()
    made = made - k
    bar.remove(owner.id(k))
    keys @= keys().filterNot(_ == k)
    add(k, at, content)
    shown @= was
  }

  /** The view this group shows its tabs that share one in, once it has shown one. */
  def groupView: Option[GroupContent[K]] = shared

  /** The content made for `k` here, where it has been shown: none for a tab that shares the group's view. */
  def contentOf(k: K): Option[Component] = made.get(k)

  /** The focus on the tab being shown, or on the empty group's words. */
  def focusTab(): Unit = bar(shown()).map(_.focus()).getOrElse(empty.focus())

  // what the tab being shown shows, made the first time; the rest off the screen, alive
  private def show(): Unit = {
    // (read afresh: `selected` may not have heard the change yet)
    val on = keys().find(k => owner.id(k) == shown())
    val content: Option[Component] = on.map { k =>
      if (owner.sharesGroupContent(k)) {
        val view = shared.getOrElse {
          val fresh = owner.groupContent(this)
          fit(fresh)
          shared = Some(fresh)
          fresh
        }
        if (!told.contains(k)) {
          told = Some(k)
          view.show(k)
        }
        view
      }
      else made.getOrElse(k, {
        val c = owner.content(k)
        fit(c)
        made = made + (k -> c)
        c
      })
    }
    // the shared view told it shows none of its tabs now
    if (told.nonEmpty && !on.exists(owner.sharesGroupContent)) {
      told = None
      shared.foreach(_.hide())
    }
    val keep = Vector(empty) ++ content.toVector
    if (body.children().size != keep.size || !body.children().zip(keep).forall { case (a, b) => a eq b })
      body.children @= keep
    owner.changed()
  }

  private def fit(c: Component): Unit = {
    c.size.width := body.size.width()
    c.size.height := body.size.height()
  }

  /** Every content made here disposed of, as the group goes for good. */
  private[kit] def disposeContents(): Unit = {
    made.values.foreach(_.dispose())
    made = Map.empty
    shared.foreach(_.dispose())
    shared = None
    told = None
  }

  override def kind: String = "TabGroup"
}
