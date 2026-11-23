package everyui.tooltip

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.event.{Key, Pointer}
import everyui.focus.Focus
import everyui.list.VirtualList
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, PlatformKind, Shadow, ui}
import reactify.*

/** The one host of every tip: beside a component for as long as the pointer rests on it or the focus is drawn on it
  * (`Component.focusShown`: the keys brought it there), or once a finger is held on it: words, or anything the app
  * makes (`Component.tipView`, a `RichTip`, a key's chord beside its name, a preview). It comes after `delay` seconds,
  * so a pointer passing over does not raise it, and goes as the pointer leaves, presses, the focus moves on, Escape is
  * pressed, what it is in scrolls, or what it belongs to is taken apart. It sits under the component, centred on it,
  * or over it where under has no room (`TipContext.side`), never off the screen, its content no wider than
  * `maxWidth`, on the theme's raised surface with its edge and a shadow. One shows at a time, and it never takes the
  * focus.
  *
  * A finger's tap is never taken for a tip: a finger held on the component opens it at once (where that opens no menu
  * of the app's), and it goes `held` seconds later, or, where it is interactive, at the next press outside it. An
  * interactive tip (`Component.tipInteractive`) stays open as the pointer moves from the component into it, within
  * `grace` seconds, and what is in it can be clicked and focused; it closes itself through `TipContext.close`.
  *
  * On a platform driven by a remote it shows for the focus only where the app asks (`onFocus`): there the focus moves
  * with every press, and a tip following it would cover the next thing along. Where the app's own view cannot be drawn
  * (a Roku, an Apple TV: `drawsViews`), a `tipView`'s place is taken by the words of the component's `tooltip`.
  *
  * {{{
  *   Tooltip(saveButton, "Save (" + Chord("S", primary = true).label + ")")
  *   tile.tipView @= Some(tip => RichTip("Sigil", lines = List(RichTipLine(MaterialIcon.Mail, "5 unread"))))
  * }}} */
object Tooltip {
  /** How long the pointer rests, or the focus stays, before a tip shows, in seconds. */
  val delay: Var[Double] = Var(0.6)
  /** Whether the focus shows a tip as well as the pointer: not where a remote moves the focus, unless an app says. */
  val onFocus: Var[Boolean] = Var(!Platform.remoteNavigation())
  /** How wide words in a tip wrap at. */
  val widest: Var[Double] = Var(320.0)
  /** How wide a tip's content may be: a `RichTip` lays itself out within it, and what is wider is cut to it. */
  val maxWidth: Var[Double] = Var(360.0)
  /** How long the pointer has, leaving an interactive tip's component or the tip, to come onto the other, in seconds. */
  val grace: Var[Double] = Var(0.35)
  /** How long a tip a finger held up stays, in seconds, as Android's own does. */
  val held: Double = 2.5

  /** Whether the app's own tip views are drawn here: not on a Roku or an Apple TV, where a `tipView` shows the words of
    * its component's `tooltip` instead. */
  def drawsViews: Boolean = {
    val kind = Platform.kind
    kind != PlatformKind.Roku && kind != PlatformKind.TvOS && kind != PlatformKind.IOS
  }

  // what is showing (its component and its plate), its context, and what undoes its hooks (Escape, the scrolls, a
  // press outside)
  private var showing: Option[(Component, Container)] = None
  private var context: Option[TipContext] = None
  private var unhooks: List[() => Unit] = Nil
  private var asked = 0
  private var leaving = 0
  // whose tip is still to come: asked for and not yet shown, none once it shows or is called off
  private var pending: Option[Component] = None

  /** `target`'s tip, the words `text` says as it shows. */
  def apply(target: Component, text: => String): Unit = rich(target)(() => words(text))

  /** `target`'s tip, made by `make` each time it shows. */
  def rich(target: Component)(make: () => Component): Unit = listen(target, _ => Some(make()), () => false)

  /** `target`'s own tip (`Component.tipView`) shown as its pointer, its focus and a finger held on it ask: told once,
    * as its view is first set. */
  private[everyui] def watch(target: Component): Unit = listen(target, own(target), () => target.tipInteractive())

  /** `target`'s own tip (`Component.tipView`, or its `tooltip`'s words) shown now, without waiting, where it is on the
    * screen: for an app's own gesture, and a check. */
  def open(target: Component): Unit = {
    asked += 1
    pending = None
    show(target, own(target), target.tipInteractive(), finger = false)
  }

  // the view `target` makes for its tip, or its words where views are not drawn here
  private def own(target: Component)(tip: TipContext): Option[Component] =
    if (drawsViews) target.tipView().map(_(tip))
    else Some(target.tooltip()).filter(_.nonEmpty).map(words)

  private def listen(target: Component, make: TipContext => Option[Component], open: () => Boolean): Unit = {
    val over = target.event.pointer.over   // following the pointer from now, before the focus asks
    // (a finger coming onto it is a tap, not a pointer resting: a finger held shows its tip)
    target.event.pointer.enter.attach(e => if (!e.finger) ask(target, make, open))
    // (a finger lifted leaves it too, on the web: a tip it held up stays its while)
    target.event.pointer.leave.attach { e =>
      if (!e.finger) {
        if (open() && shownFor.exists(_ eq target)) linger(target) else hideFor(target)
      }
    }
    target.event.pointer.down.attach(_ => hideFor(target))
    // a finger held, where nothing put a menu up for it (this, or what it is in, as the press climbs)
    target.event.pointer.menu.attach { e =>
      if (e.finger) {
        val dialogs = Modal.opened
        Platform.later(() => if (Modal.opened == dialogs && !Pointer.menuAnswered) {
          asked += 1
          pending = None
          show(target, make, open(), finger = true)
        })
      }
    }
    // the focus drawn, not merely there: a button a click or a screen focused raises no tip, a key arriving at it does
    target.focusShown.attach { on =>
      if (on && onFocus() && !over()) ask(target, make, open)
      else if (!on && !over() && !focusInTip) hideFor(target)
    }
    target.onDispose(() => hideFor(target))
  }

  /** `target`'s tip asked for by what owns it rather than by its own pointer and focus (a list's row as the pointer or
    * the cursor rests on it): the words `text` gives, read as it shows, and nothing shown where they are None by then.
    * After `delay`, or at once with `now` (a finger held on it, which has waited already). */
  def askFor(target: Component, text: () => Option[String], now: Boolean = false): Unit = {
    asked += 1
    val mine = asked
    pending = Some(target)
    val make = (_: TipContext) => text().map(words)
    if (now) showIf(target, make, () => false, mine)
    else Platform.after(delay(), () => showIf(target, make, () => false, mine))
  }

  /** The tip showing, taken away, and any still to come, whoever's. */
  def hide(): Unit = {
    asked += 1
    pending = None
    drop()
  }

  /** Whether a tip is showing, and for what. */
  def shownFor: Option[Component] = showing.map(_._1)

  /** The tip showing: its plate, its content inside it. */
  def plate: Option[Container] = showing.map(_._2)

  /** `target`'s tip taken away, the one showing or the one still to come for it, where it is `target`'s; another's,
    * showing or to come, is left as it is (a paragraph taken apart as a reply streams calls off none but its own). */
  def hideFor(target: Component): Unit = {
    if (pending.exists(_ eq target)) {
      asked += 1
      pending = None
    }
    if (showing.exists(_._1 eq target)) drop()
  }

  /** Whether a tip is still to come, and for what. */
  def pendingFor: Option[Component] = pending

  private def ask(target: Component, make: TipContext => Option[Component], open: () => Boolean): Unit = {
    asked += 1
    val mine = asked
    pending = Some(target)
    Platform.after(delay(), () => showIf(target, make, open, mine))
  }

  private def showIf(target: Component, make: TipContext => Option[Component], open: () => Boolean, mine: Int): Unit =
    if (mine == asked) {
      pending = None
      show(target, make, open(), finger = false)
    }

  // whether what has the focus is in the tip showing (an interactive one's link, clicked)
  private def focusInTip: Boolean = showing.exists(s => Focus.focused().exists(f => Focus.within(f, s._2)))

  // the pointer gone off an interactive tip's component, or off the tip: closed unless it is on the other by `grace`
  private def linger(target: Component): Unit = {
    leaving += 1
    val mine = leaving
    Platform.after(grace(), () => if (mine == leaving) showing.foreach { s =>
      if ((s._1 eq target) && !s._2.event.pointer.over() && !target.event.pointer.over() && !focusInTip) drop()
    })
  }

  // the tip showing taken away for good: off the screen, and taken apart, as nothing shows it again
  private def drop(): Unit = {
    val undo = unhooks
    unhooks = Nil
    undo.foreach(f => f())
    showing.foreach { s =>
      ui.children -= s._2
      s._2.dispose()
    }
    showing = None
    context = None
  }

  private def show(target: Component, make: TipContext => Option[Component], open: Boolean, finger: Boolean): Unit =
    if (Focus.showing(target)) {
      drop()
      val tip = new TipContext(target, maxWidth(), t => if (context.exists(_ eq t)) hide())
      make(tip).foreach(content => put(target, content, tip, open, finger))
    }

  private def put(target: Component, content: Component, tip: TipContext, open: Boolean, finger: Boolean): Unit = {
    val plate = new Container {
      size.width := math.min(content.size.width(), maxWidth()) + 20.0
      size.height := content.size.height() + 12.0
      backgroundColor := Theme.raised()
      borderWidth @= 1.0
      borderColor := Theme.border()
      radius @= 6.0
      shadow @= Some(Shadow(0.0, 3.0, 12.0, Color.Black.withAlpha(0.4)))
      content.position.left @= 10.0
      content.position.top @= 6.0
      children += content
    }
    val place = () => placed(target, plate, tip)
    place()
    plate.size.width.attach(_ => place())
    plate.size.height.attach(_ => place())
    plate.follow(ui.size.width, (_: Double) => place())
    plate.follow(ui.size.height, (_: Double) => place())
    showing = Some((target, plate))
    context = Some(tip)
    // a press outside an interactive tip a finger opened closes it, and is the tip's: a tap meant nothing else
    if (open && finger) {
      val outside = new Container {
        size.width := ui.size.width()
        size.height := ui.size.height()
      }
      outside.event.pointer.down.attach(_ => hideFor(target))
      ui.children += outside
      unhooks = (() => {
        ui.children -= outside
        outside.dispose()
      }) :: unhooks
    }
    ui.children += plate
    if (open) {
      plate.event.pointer.over   // (followed from now, for `linger`)
      plate.event.pointer.leave.attach(_ => linger(target))
    }
    else if (finger) {
      Platform.after(held, () => if (showing.exists(_._2 eq plate)) hideFor(target))
    }
    // Escape closes it, before a dialog under it; Back too, where a finger or the pointer's way into it opened it
    val back = (key: Key) => (key == Key.Escape || (key == Key.Back && (open || finger))) && {
      hideFor(target)
      true
    }
    unhooks = Focus.onBack(back) :: unhooks
    // and so does what it is in scrolling
    var at = target.parent()
    while (at.nonEmpty) {
      at.get match {
        case s: ScrollContainer => unhooks = closing(s.offset, target) :: unhooks
        case l: VirtualList[?] => unhooks = closing(l.offset, target) :: unhooks
        case _ => ()
      }
      at = at.get.parent()
    }
  }

  // `target`'s tip closed as `offset` moves, until undone
  private def closing(offset: Var[Double], target: Component): () => Unit = {
    val reaction = offset.attach(_ => hideFor(target))
    () => offset.reactions -= reaction
  }

  // the plate under its component, centred on it, or over it where under has less room than it needs and over has
  // more, never off the screen
  private def placed(target: Component, plate: Container, tip: TipContext): Unit = {
    val left = target.absoluteLeft
    val top = target.absoluteTop
    val width = plate.size.width()
    val height = plate.size.height()
    plate.position.left @= math.max(8.0, math.min(left + (target.size.width() - width) / 2.0,
      ui.size.width() - width - 8.0))
    val under = top + target.size.height() + 6.0
    val roomUnder = ui.size.height() - 8.0 - under
    val roomOver = top - 6.0 - 8.0
    val side = if (height <= roomUnder || roomUnder >= roomOver) TipSide.Below else TipSide.Above
    plate.position.top @= (
      if (side == TipSide.Below) math.max(8.0, math.min(under, ui.size.height() - 8.0 - height))
      else math.max(8.0, top - height - 6.0))
    if (tip.side() != side) tip.side @= side
  }

  /** Words as a tip: small, wrapping past `widest`, a path after its slashes where it can and a word longer than that
    * where it must. */
  private def words(text: String): Component = {
    val line = new TextView {
      font.size @= 12.5
      color := Theme.text()
      content @= text
    }
    if (line.size.width() <= widest()) line
    else {
      line.dispose()
      new TextView {
        whiteSpace @= WhiteSpace.Normal
        wrapAnywhere @= true
        size.width @= widest()
        font.size @= 12.5
        color := Theme.text()
        content @= text.replace("/", "/​")
      }
    }
  }
}
