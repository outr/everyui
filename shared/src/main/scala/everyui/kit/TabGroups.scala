package everyui.kit

import everyui.async.Task
import everyui.component.*
import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.i18n.{Direction, Words}
import everyui.keymap.{Chord, Command, Keymap}
import everyui.menu.MenuItem
import everyui.split.SplitPane
import everyui.theme.Theme
import everyui.{Shadow, ui}
import reactify.*

/** Tabs in groups, as an editor splits its files: one group or more, each a `TabBar` over what its tab being shown
  * shows, lying in splits whose handles drag (`SplitPane`), across and down. `content` makes what a tab shows the first
  * time it is shown; it is kept while the tab is open, in whichever group it moves to, and disposed of once it closes.
  * `id` names a tab, as the bars and a saved `layout` know it, and `label` and `lead` say how it looks.
  *
  * A tab's menu (the secondary button, the menu key, Shift+F10, OK held) has Close, Close Others and Close All; Open
  * Right and Open Down, which open it in a new group after the group (by the reading direction) or below it; Move
  * Right, Left, Down and Up, to the group that way, or into a new one split off that way where there is none (not a
  * group's only tab, which would only make its group again); and Unsplit, which puts the group's tabs into the group
  * beside it; then the app's own (`tabMenu`). A tab dragged onto another group goes in among its tabs, and onto a
  * group's edge (lit as it is over it) splits the group there. A key shows in one group at a time, moved rather than
  * opened again, unless `allowsTwice` says it may show in two (a file in both halves of a split), each with its own
  * content. A group left empty closes; the last one stays.
  *
  * `focusedGroup` is the group the focus is in, or was last; `open(k)` opens a tab there, or shows it where it is
  * open already, and `openIn(g, k)` in `g`. With an `addMenu` each group has a "+" after its last tab dropping the menu
  * it makes for that group, where the app opens its new tabs. `commands(keymap)` puts the actions on the keyboard.
  * `tabLayout` is how it all lies, to keep (`layoutChanged`) and `restore`.
  *
  * A tab closes only once `canClose` lets it (the app asking whether to save it), whichever way it is closed; Close
  * Others and Close All ask one tab at a time and stop at the first that stays. `tab(k)` finds a key's tabs (to change
  * their `label` or `lead` as what they show is renamed, `lead` answering the same for a tab made later), and
  * `dirty(k, on)` marks them with a dot. The keys `sharesGroupContent` says share one view per group show
  * `groupContent`'s for their group, told which of them it shows (`GroupContent.show`), rather than a content each: one
  * editor for a group's files, while a terminal keeps its own.
  *
  * {{{
  *   val editor = new TabGroups[File](_.name, f => new TerminalView(f), _.path, allowsTwice = _ => true,
  *     sharesGroupContent = _.isText, groupContent = _ => new EditorView)
  *   editor.canClose @= (f => if (!editor.isDirty(f)) Task.pure(true) else Dialogs.saveChanges("Save " + f.name + "?", "")
  *     .map(c => c != SaveChoice.Cancel))
  *   editor.open(readme)
  *   editor.tabMenu @= (f => List(MenuItem("Reveal in tree", () => tree.reveal(f))))
  *   editor.addMenu @= Some(g => List(MenuItem("New file", () => editor.openIn(g, File.untitled()))))   // each group's "+"
  *   editor.commands(Keymap.global)
  * }}} */
class TabGroups[K](val label: K => String, val content: K => Component, val id: K => String,
                   val lead: K => TabLead = (_: K) => TabLead.None,
                   val allowsTwice: K => Boolean = (_: K) => false,
                   val sharesGroupContent: K => Boolean = (_: K) => false,
                   val groupContent: TabGroup[K] => GroupContent[K] = (_: TabGroup[K]) => GroupContent.empty[K])
  extends Container {
  /** Whether `k` may close: asked before its last tab closes, by the ×, the menu, the keys and `closeAllTabs`, and
    * answered by the app as it likes (asking whether to save it); a tab it answers false for stays. Always, by default. */
  val canClose: Var[K => Task[Boolean]] = own(Var((_: K) => Task.pure(true)))
  /** The keys marked as holding unsaved work (`dirty`). */
  val dirtyKeys: Var[Set[K]] = own(Var(Set.empty[K]))
  /** The app's own entries on a tab's menu, after the groups' own. */
  val tabMenu: Var[K => List[MenuItem]] = own(Var((_: K) => Nil))
  /** A "+" after each group's last tab that drops this menu, made for the group whose "+" was pressed (where the app
    * opens its new tabs: `openIn`), as a browser's new-tab button: none (no button) by default. */
  val addMenu: Var[Option[TabGroup[K] => List[MenuItem]]] = own(Var(None))
  /** What each group's "+" is called: its tooltip and its name to a screen reader. */
  val addLabel: Var[String] = own(Var(TabBar.addWord))
  /** The groups, first to last. */
  val groups: Var[List[TabGroup[K]]] = own(Var(Nil))
  /** Heard with the layout each time it changes: a tab opened, closed, moved or shown, a split made or undone, a
    * handle dragged. */
  val layoutChanged: Channel[TabLayout] = Channel[TabLayout]

  private var tree: TabTree[K] = TabTree.Leaf(new TabGroup[K](this))
  /** The group the focus is in, or was in last. */
  val focusedGroup: Var[TabGroup[K]] = own(Var(TabTrees.groups(tree).head))
  private var root: Component = TabTrees.groups(tree).head
  private var quiet = false

  // what lights where a dragged tab would land
  private val zone: Container = new Container {
    backgroundColor := Theme.accent().withAlpha(0.22)
    borderColor := Theme.accent()
    borderWidth @= 2.0
    visible @= false
  }
  private val catcher: Container = new Container {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }

  groups @= TabTrees.groups(tree)
  place()
  follow(Focus.focused, (f: Option[Component]) => f.foreach { c =>
    groups().find(g => Focus.within(c, g)).foreach(g => if (!(focusedGroup() eq g)) focusedGroup @= g)
  })

  // ---- tabs ----

  /** `k` opened in the focused group and shown, the focus on its tab (with `focus`); or, where it shows already in
    * another group and may not show twice, shown there. */
  def open(k: K, focus: Boolean = true): Unit = openIn(focusedGroup(), k, focus)

  /** `k` opened in `g` and shown, as `open` opens it in the focused group: what a group's "+" menu (`addMenu`) does. */
  def openIn(g: TabGroup[K], k: K, focus: Boolean = true): Unit = {
    val target = if (g.has(k) || allowsTwice(k)) g else groups().find(_.has(k)).getOrElse(g)
    target.add(k)
    target.select(k)
    if (focus) target.focusTab()
    focusedGroup @= target
  }

  /** `k` closed in `g`, once `canClose` says it may be (asked where this is its last tab open), its content disposed
    * of; a group left empty closes, unless it is the last. Asked again while the answer is awaited, it does nothing. */
  def close(g: TabGroup[K], k: K): Unit = closeInTurn(g, List(k), _ => ())

  /** Every other tab of `g` closed, one at a time, in order, each asked of `canClose` in turn: the first it will not let
    * close stays, with those after it, and those before it are closed. */
  def closeOthers(g: TabGroup[K], k: K): Unit = closeInTurn(g, g.keys().toList.filterNot(_ == k), _ => ())

  /** Every tab of `g` closed, as `closeOthers` closes them, and `g` with them where none stays. */
  def closeAll(g: TabGroup[K]): Unit = closeInTurn(g, g.keys().toList, _ => ())

  /** Every tab of every group closed, group by group, as `closeAll` closes them: true once all have, false at the first
    * that stays. What an app asks before it lets its window go: disposing of this asks nothing, as it cannot wait. */
  def closeAllTabs(): Task[Boolean] = Task.async[Boolean] { done =>
    def next(rest: List[TabGroup[K]]): Unit = rest match {
      case Nil => done(true)
      case g :: more => closeInTurn(g, g.keys().toList, all => if (all) next(more) else done(false))
    }
    next(groups())
  }

  /** The tabs `ks` of `g` closed one after another, each once `canClose` lets it, stopping at the first it does not:
    * `done` hears whether all closed. One whose answer is still awaited stops it, and `done` is not told. */
  private def closeInTurn(g: TabGroup[K], ks: List[K], done: Boolean => Unit): Unit = ks match {
    case Nil => done(true)
    case k :: rest =>
      if (!g.has(k)) closeInTurn(g, rest, done)
      else if (!asking.exists(a => (a._1 eq g) && a._2 == k)) {
        val last = groups().forall(o => (o eq g) || !o.has(k))
        val answer = if (last) canClose()(k) else Task.pure(true)
        asking = (g, k) :: asking
        answer.map { ok =>
          asking = asking.filterNot(a => (a._1 eq g) && a._2 == k)
          if (ok) {
            discard(g, k)
            closeInTurn(g, rest, done)
          }
          else done(false)
        }.start()
      }
  }

  /** The tabs whose closing waits on `canClose`, by group. */
  private var asking: List[(TabGroup[K], K)] = Nil

  /** `k` taken out of `g` for good, its content disposed of; a group left empty closes, unless it is the last. */
  private def discard(g: TabGroup[K], k: K): Unit = if (g.has(k)) {
    val had = Focus.focused().exists(c => Focus.within(c, g))
    g.take(k).foreach(_.dispose())
    if (g.keys().isEmpty) closeGroup(g, had) else if (had) g.focusTab()
  }

  /** The tab of `k` in each group it shows in, first to last: one, unless it may show twice. */
  def tab(k: K): List[TabItem] = groups().flatMap(g => if (g.has(k)) g.bar(id(k)).toList else Nil)

  /** `k` marked as holding unsaved work, or not: a dot after its label on every tab of it, and on any opened after. */
  def dirty(k: K, on: Boolean): Unit = {
    dirtyKeys @= (if (on) dirtyKeys() + k else dirtyKeys() - k)
    tab(k).foreach(t => t.marker @= (if (on) Some(TabGroups.dirtyDot()) else None))
  }

  /** Whether `k` is marked as holding unsaved work (`dirty`). */
  def isDirty(k: K): Boolean = dirtyKeys().contains(k)

  /** `k` opened in a new group after `g` (`vertical`: below it): a second view of it where it may show twice, else
    * moved there. Nothing happens to a group's only tab that may not show twice. */
  def split(g: TabGroup[K], k: K, vertical: Boolean, after: Boolean = true): Unit =
    if (g.has(k) && (allowsTwice(k) || g.keys().size > 1)) {
      val made = splitOff(g, vertical, after)
      if (allowsTwice(k)) made.add(k) else made.add(k, content = g.take(k))
      made.select(k)
      made.focusTab()
    }

  def openRight(g: TabGroup[K], k: K): Unit = split(g, k, vertical = false)
  def openDown(g: TabGroup[K], k: K): Unit = split(g, k, vertical = true)

  /** `k` moved from `g` into `to` (at `at` among its tabs), with its content, and shown there. */
  def move(g: TabGroup[K], k: K, to: TabGroup[K], at: Int = -1): Unit = if (g.has(k)) {
    if (to eq g) {
      g.reorder(k, at)
      g.select(k)
    }
    else {
      val had = Focus.focused().exists(c => Focus.within(c, g))
      if (to.has(k)) g.take(k).foreach(_.dispose()) else to.add(k, at, g.take(k))
      to.select(k)
      to.focusTab()
      if (g.keys().isEmpty) closeGroup(g, had = false)
      else if (had && !Focus.focused().exists(c => Focus.within(c, to))) g.focusTab()
    }
  }

  /** `k` moved from `g` to the group that way (`vertical`: below, else after it in the reading direction; before it
    * where not `after`), or, where there is none, into a new group split off `g` that way, as IntelliJ's Split and Move
    * Right does. A group's only tab with nowhere that way stays: its group would only give its place to the new one. */
  def moveBeside(g: TabGroup[K], k: K, vertical: Boolean, after: Boolean): Unit = if (canMoveBeside(g, k, vertical, after)) {
    TabTrees.neighbour(tree, g, vertical, after) match {
      case Some(n) => move(g, k, n)
      case None => move(g, k, splitOff(g, vertical, after))
    }
  }

  /** Whether `moveBeside` would move `k`: there is a group that way, or `g` has another tab to keep. */
  def canMoveBeside(g: TabGroup[K], k: K, vertical: Boolean, after: Boolean): Boolean =
    g.has(k) && (g.keys().size > 1 || TabTrees.neighbour(tree, g, vertical, after).nonEmpty)

  /** `g`'s tabs put into the group beside it, and `g` gone. */
  def unsplit(g: TabGroup[K]): Unit = TabTrees.sibling(tree, g).foreach { to =>
    val was = g.keyOf(g.shown())
    g.keys().foreach(k => if (to.has(k)) g.take(k).foreach(_.dispose()) else to.add(k, content = g.take(k)))
    was.foreach(to.select)
    to.focusTab()
    closeGroup(g, had = false)
  }

  /** The focus on the next group's tab (the first after the last), or the one before with `back`. */
  def nextGroup(back: Boolean = false): Unit = {
    val all = groups()
    val i = all.indexWhere(_ eq focusedGroup())
    if (all.size > 1) all((i + (if (back) all.size - 1 else 1)) % all.size).focusTab()
  }

  /** The groups' own entries on `k`'s menu in `g`, and then the app's. */
  def menuFor(g: TabGroup[K], k: K): List[MenuItem] = {
    val many = groups().size > 1
    val movable = allowsTwice(k) || g.keys().size > 1
    def to(word: String, vertical: Boolean, after: Boolean): List[MenuItem] = List(MenuItem(word,
      () => moveBeside(g, k, vertical, after), enabled = canMoveBeside(g, k, vertical, after)))
    val own = List(
      MenuItem(TabGroups.closeWord, () => close(g, k)),
      MenuItem(TabGroups.closeOthersWord, () => closeOthers(g, k), enabled = g.keys().size > 1),
      MenuItem(TabGroups.closeAllWord, () => closeAll(g)),
      MenuItem.separator,
      MenuItem(TabGroups.openRightWord, () => openRight(g, k), enabled = movable),
      MenuItem(TabGroups.openDownWord, () => openDown(g, k), enabled = movable)) ++
      to(TabGroups.moveRightWord, vertical = false, after = true) ++ to(TabGroups.moveLeftWord, vertical = false, after = false) ++
      to(TabGroups.moveDownWord, vertical = true, after = true) ++ to(TabGroups.moveUpWord, vertical = true, after = false) ++
      (if (many) List(MenuItem(TabGroups.unsplitWord, () => unsplit(g))) else Nil)
    val app = tabMenu()(k)
    if (app.isEmpty) own else own ++ (MenuItem.separator :: app)
  }

  /** The actions on `keymap`, as `prefix`.openRight, .openDown, .nextGroup, .previousGroup and .close, on the tab shown
    * in the focused group, run by chords an app can `rebind`: Primary+\ to open right, Primary+Shift+\ down,
    * Primary+Alt+Right and Left to the next and the previous group, and Primary+W or Primary+F4 to close. */
  def commands(keymap: Keymap, prefix: String = "tabs"): List[Command] = {
    def shown(f: (TabGroup[K], K) => Unit): () => Unit = () => {
      val g = focusedGroup()
      g.keyOf(g.shown()).foreach(k => f(g, k))
    }
    List(
      new Command(prefix + ".openRight", TabGroups.openRightWord, List(Chord("\\", primary = true)), shown(openRight)),
      new Command(prefix + ".openDown", TabGroups.openDownWord,
        List(Chord("\\", primary = true, shift = true), Chord("|", primary = true, shift = true)), shown(openDown)),
      new Command(prefix + ".nextGroup", TabGroups.nextGroupWord, List(Chord("ArrowRight", primary = true, alt = true)),
        () => nextGroup()),
      new Command(prefix + ".previousGroup", TabGroups.previousGroupWord,
        List(Chord("ArrowLeft", primary = true, alt = true)), () => nextGroup(back = true)),
      new Command(prefix + ".close", TabGroups.closeWord, List(Chord("W", primary = true), Chord("F4", primary = true)),
        shown(close))).map(c => keymap.add(c))
  }

  // ---- the layout ----

  /** How it all lies now. */
  def tabLayout: TabLayout = snapshot(tree)

  /** It laid out as `l` says, each tab's id found by `find` (one it no longer finds is left out); what was open is
    * closed. A layout with no tabs at all leaves one empty group. */
  def restore(l: TabLayout, find: String => Option[K]): Unit = {
    quiet = true
    val old = groups()
    def build(t: TabLayout): Option[TabTree[K]] =
      if (t.isGroup) {
        val ks = t.tabs.flatMap(find)
        if (ks.isEmpty) None else {
          val g = new TabGroup[K](this)
          ks.foreach(k => g.add(k))
          ks.find(k => id(k) == t.selected).orElse(ks.headOption).foreach(g.select)
          Some(TabTree.Leaf(g))
        }
      }
      else t.parts.flatMap(build) match {
        case a :: b :: _ =>
          val ratio = Var(t.ratio)
          ratio.attach(_ => changed())
          Some(TabTree.Split(t.vertical, ratio, a, b))
        case one => one.headOption
      }
    tree = build(l).getOrElse(TabTree.Leaf(new TabGroup[K](this)))
    quiet = false
    place()
    old.foreach { g =>
      g.disposeContents()
      g.dispose()
    }
    focusedGroup @= groups().head
    changed()
  }

  /** The layout told, as it changes. */
  private[kit] def changed(): Unit = if (!quiet) layoutChanged @= tabLayout

  private def snapshot(t: TabTree[K]): TabLayout = t match {
    case TabTree.Leaf(g) => TabLayout(g.keys().map(id).toList, g.keyOf(g.shown()).map(id).getOrElse(""))
    case TabTree.Split(v, r, a, b) => TabLayout(vertical = v, ratio = r(), parts = List(snapshot(a), snapshot(b)))
  }

  // ---- the groups ----

  /** A new group split off beside `g`: after it (by the reading direction, or below with `vertical`), or before. */
  private def splitOff(g: TabGroup[K], vertical: Boolean, after: Boolean): TabGroup[K] = {
    val made = new TabGroup[K](this)
    val ratio = Var(0.5)
    ratio.attach(_ => changed())
    tree = TabTrees.replace(tree, g, leaf =>
      if (after) TabTree.Split(vertical, ratio, leaf, TabTree.Leaf(made)) else TabTree.Split(vertical, ratio, TabTree.Leaf(made), leaf))
    place()
    made
  }

  /** `g` gone, unless it is the last; the focus, where it was in it (`had`), on the group that takes its place. */
  private def closeGroup(g: TabGroup[K], had: Boolean): Unit = TabTrees.without(tree, g) match {
    case Some(rest) =>
      val next = TabTrees.sibling(tree, g)
      tree = rest
      place()
      g.disposeContents()
      g.dispose()
      if (focusedGroup() eq g) focusedGroup @= next.getOrElse(groups().head)
      if (had || Focus.focused().isEmpty) focusedGroup().focusTab()
    case None => if (had) g.focusTab()
  }

  /** The splits made again for the tree as it is, the groups (and the focus in them) kept. */
  private def place(): Unit = {
    val focus = Focus.focused()
    groups().foreach(g => g.parent().foreach {
      case c: Container => c.children -= g
      case _ => ()
    })
    root match {
      case s: SplitPane => s.dispose()
      case _ => ()
    }
    def render(t: TabTree[K]): Component = t match {
      case TabTree.Leaf(g) => g
      case TabTree.Split(v, r, a, b) => new SplitPane(render(a), render(b), vertical = v, ratio = r)
    }
    root = render(tree)
    root.size.width := size.width()
    root.size.height := size.height()
    groups @= TabTrees.groups(tree)
    children @= Vector(root, zone)
    focus.filter(c => Focus.showing(c)).foreach(c => if (!Focus.focused().contains(c)) c.focus())
    changed()
  }

  // ---- dragging a tab ----

  private var pressed: Option[(TabGroup[K], K, Double, Double)] = None
  private var dragging = false
  private var ghost: Option[Container] = None
  private var landing: Option[(TabGroup[K], TabDropZone, Int)] = None

  /** `tab` (`k` in `g`) dragged by the pointer, onto another group or a group's edge. */
  private[kit] def draggable(g: TabGroup[K], k: K, tab: TabItem): Unit = {
    tab.event.pointer.down.attach { e =>
      val onClose = tab.closeButton.exists(b => e.x >= b.absoluteLeft && e.x < b.absoluteLeft + b.size.width())
      if (e.button == PointerEvent.PrimaryButton && !onClose && pressed.isEmpty) {
        e.consume()
        press(g, k, e.x, e.y)
      }
    }
    // (JavaFX goes on telling the tab pressed, the sheet over it or not)
    tab.event.pointer.move.attach(e => if (pressed.exists(_._2 == k)) dragTo(e.x, e.y))
    tab.event.pointer.up.attach(e => if (pressed.exists(_._2 == k)) drop(e.x, e.y))
  }
  /** `k` in `g` pressed at `x`, `y`: dragged once the pointer goes far enough, else shown as it lets go. */
  private[kit] def press(g: TabGroup[K], k: K, x: Double, y: Double): Unit = {
    pressed = Some((g, k, x, y))
    dragging = false
    ui.children += catcher
  }
  catcher.event.pointer.move.attach(e => dragTo(e.x, e.y))
  catcher.event.pointer.up.attach(e => drop(e.x, e.y))

  /** The pointer at `x`, `y` with a tab held: once it has gone far enough, the tab is dragged and where it would land
    * is lit. */
  private[kit] def dragTo(x: Double, y: Double): Unit = pressed.foreach { case (_, k, x0, y0) =>
    if (!dragging && math.abs(x - x0) + math.abs(y - y0) > TabGroups.dragStart) {
      dragging = true
      val words = TextView.bound(label(k), 13.0, Theme.text())
      words.position.left @= 10.0
      words.position.top @= 5.0
      val tag = new Container {
        backgroundColor := Theme.raised()
        borderColor := Theme.accent()
        borderWidth @= 1.0
        radius @= 4.0
        shadow @= Some(Shadow(0.0, 2.0, 8.0, Theme.backdrop()))
        opacity @= 0.92
        size.width := words.size.width() + 20.0
        size.height := words.size.height() + 10.0
        children += words
      }
      ghost = Some(tag)
      ui.children += tag
    }
    if (dragging) {
      ghost.foreach { t =>
        t.position.left @= x + 14.0
        t.position.top @= y + 10.0
      }
      landing = landingAt(x, y)
      light()
    }
  }

  /** Let go at `x`, `y`: the tab lands where it was lit, or, never dragged, is shown as a click shows it. */
  private[kit] def drop(x: Double, y: Double): Unit = pressed.foreach { case (g, k, _, _) =>
    ui.children -= catcher
    if (dragging) {
      landingAt(x, y).foreach { case (to, where, at) =>
        if (where == TabDropZone.Into) move(g, k, to, at)
        else if (!(to eq g) || g.keys().size > 1) {
          val (vertical, after) = where match {
            case TabDropZone.Start => (false, false)
            case TabDropZone.End => (false, true)
            case TabDropZone.Top => (true, false)
            case _ => (true, true)
          }
          move(g, k, splitOff(to, vertical, after))
        }
      }
    }
    else if (g.has(k)) {
      g.select(k)
      g.bar(id(k)).foreach(_.focus())
    }
    cancelDrag()
  }

  private def cancelDrag(): Unit = {
    ghost.foreach(t => ui.children -= t)
    ghost = None
    pressed = None
    dragging = false
    landing = None
    zone.visible @= false
  }

  /** Where a tab let go at `x`, `y` lands: the group under it, into it (at a place among its tabs) or at an edge. */
  private[kit] def landingAt(x: Double, y: Double): Option[(TabGroup[K], TabDropZone, Int)] =
    groups().find(g => x >= g.absoluteLeft && x < g.absoluteLeft + g.size.width() && y >= g.absoluteTop &&
      y < g.absoluteTop + g.size.height()).map { g =>
      val across = (x - g.absoluteLeft) / math.max(1.0, g.size.width())
      val down = (y - g.absoluteTop - TabBar.height) / math.max(1.0, g.body.size.height())
      val rtl = Direction.rtl()
      val edges = List(
        (across, if (rtl) TabDropZone.End else TabDropZone.Start),
        (1.0 - across, if (rtl) TabDropZone.Start else TabDropZone.End),
        (down, TabDropZone.Top), (1.0 - down, TabDropZone.Bottom)).filter(_._1 < TabGroups.edge)
      if (y < g.absoluteTop + TabBar.height || edges.isEmpty) {
        // among the tabs: before the first whose middle is past the pointer, in the reading direction
        val tabs = g.bar.tabs()
        val before = tabs.indexWhere { t =>
          val middle = t.absoluteLeft + t.size.width() / 2.0
          if (rtl) x > middle else x < middle
        }
        (g, TabDropZone.Into, if (before < 0) tabs.size else before)
      }
      else (g, edges.sortBy(_._1).head._2, -1)
    }

  private def light(): Unit = landing match {
    case Some((g, where, _)) =>
      val left = g.absoluteLeft - absoluteLeft
      val top = g.absoluteTop - absoluteTop + TabBar.height
      val w = g.size.width()
      val h = g.body.size.height()
      val atLeft = (where == TabDropZone.Start) != Direction.rtl()
      val (l, t, ww, hh) = where match {
        case TabDropZone.Into => (left, top, w, h)
        case TabDropZone.Top => (left, top, w, h / 2.0)
        case TabDropZone.Bottom => (left, top + h / 2.0, w, h / 2.0)
        case _ => (if (atLeft) left else left + w / 2.0, top, w / 2.0, h)
      }
      zone.position.left @= l
      zone.position.top @= t
      zone.size.width @= ww
      zone.size.height @= hh
      zone.visible @= true
    case None => zone.visible @= false
  }

  // Escape lets a drag go without moving anything
  private val escape = Focus.onKey(k => if ((k == Key.Escape || k == Key.Back) && pressed.nonEmpty) {
    ui.children -= catcher
    cancelDrag()
    true
  } else false)
  onDispose(() => {
    escape()
    groups().foreach(_.disposeContents())
  })

  override def kind: String = "TabGroups"
}

object TabGroups {
  /** How far a pressed tab moves before it is dragged. */
  val dragStart: Double = 6.0
  /** How near a group's edge, as a share of its size, a tab dragged over it splits it there. */
  val edge: Double = 0.25

  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }

  def closeWord: String = word("tabs.close", "Close")
  def closeOthersWord: String = word("tabs.closeOthers", "Close Others")
  def closeAllWord: String = word("tabs.closeAll", "Close All")
  def openRightWord: String = word("tabs.openRight", "Open Right")
  def openDownWord: String = word("tabs.openDown", "Open Down")
  def moveRightWord: String = word("tabs.moveRight", "Move Right")
  def moveLeftWord: String = word("tabs.moveLeft", "Move Left")
  def moveDownWord: String = word("tabs.moveDown", "Move Down")
  def moveUpWord: String = word("tabs.moveUp", "Move Up")
  def unsplitWord: String = word("tabs.unsplit", "Unsplit")
  def nextGroupWord: String = word("tabs.nextGroup", "Next Group")
  def previousGroupWord: String = word("tabs.previousGroup", "Previous Group")
  /** The dot after the label of a tab holding unsaved work (`TabGroups.dirty`). */
  def dirtyDot(): Component = new Container {
    size.width @= 8.0
    size.height @= 8.0
    radius @= 4.0
    backgroundColor := Theme.text()
  }

  /** What an empty group says. */
  def noTabs: String = word("tabs.none", "No tabs open")
}
