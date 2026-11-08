package everyui.focus

import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent, Keys, PointerEvent, PointerKind}
import everyui.keymap.Keymap
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Which component has the focus, and how the keys reach it — the same navigation for a remote, a keyboard and a TV
  * browser, in one place rather than one per platform or per screen.
  *
  * A key that runs a command (`everyui.keymap.Keymap`) is taken before anything else. Every other key goes to the
  * focused component first, unless it types: a character, a space or Backspace where the focus is in something that
  * takes typing (`Container.typeInto`: a field with no platform field under it, an app's own keyboard) goes there. Enter
  * clicks it; Tab moves the focus on in tree order, Shift+Tab back; Back goes to the newest back handler (`onBack`: an
  * open dialog, then the screen history). An arrow with no modifier but Shift is answered by the layouts (with Alt,
  * Ctrl or Meta held it is the platform's, Back and Forward in a browser, unless a chord claims it):
  * from the focused component up through its containers, the first whose layout has a neighbour that way (a row's next
  * item, a column's next line, the next row of a flow) takes the focus there. Nothing is found by where things are on the
  * screen: a move is always one somebody decided, by a layout's order or by what a container states, never whatever
  * happens to sit nearest (which changes with the layout, and on a remote reads as random). A container without a
  * layout moves nothing by itself. A container can also state where an arrow leaving it goes (`Container.exitFocus`) or that it stops
  * there (`Container.stopFocus`), and where an arrow arriving from outside it lands (`Container.entryFocus`: the
  * selected tab, the card last left), however the arrow found its way in; an arrow nothing answers leaves the focus
  * where it is. So there is no zone to wire by hand, and no way for a key to be swallowed by a container that runs
  * the other way.
  *
  * The candidates are the focusable components under `ui` (or under an open dialog: `scope`) at the time of the key,
  * found by walking the tree: no registry, so a list rebuilt under the focus needs no bookkeeping. A container arrived at
  * is entered at its stated entry, else the part of it that last had the focus, else its first. The focused component
  * taken off the screen (a card gone from a row read again, a list built over, a dialog's sheet) hands the focus to
  * what took its place ([[successor]]); with nothing to take it, or nothing focused, it goes to the screen's
  * `defaultFocus` ([[home]]), or a dialog's first.
  *
  * A coach mark holds the focus in one part of the screen ([[confine]]): the arrows move only among what is in it,
  * and from outside it they land in it; and it can hold back OK, a click and a tap, or OK held and a long press, so
  * the viewer does just what its note says. A dialog put up over it has the focus while it is up.
  *
  * There are two models of the focus, one for each way of moving it. Where a remote moves it
  * (`Platform.remoteNavigation()`), all of the above holds as said: something always has the focus, it is always
  * drawn, a click focuses what it lands on, and a screen starts at its `defaultFocus`. Everywhere else a pointer moves
  * it as much as the keys do, and it behaves as it does on a web page. A click focuses only what is typed into or
  * worked by the keys (`Component.clickFocus`: a field, an editor, a list, a table), and otherwise lets the focus go
  * ([[clicked]]), as a press nothing took on what has no focus does ([[pointerUp]]). The focus is drawn only while the
  * keys are moving it ([[visible]], the browser's `:focus-visible`: `Component.focusShown`). A screen starts with
  * nothing focused unless it starts at a field ([[starts]]), and a focus put back (a screen returned to, a dialog
  * closed) is put back only where it was ([[restores]]). The focus taken off the screen goes nowhere, and with nothing
  * focused an arrow is left to the page; while the keys are moving the focus, it moves on and arrows start it, as on a
  * remote. */
object Focus {
  val focused: Var[Option[Component]] = Var(None)
  /** Where the focus may go: under `ui`, or under an open dialog alone, so the arrows never reach what it covers. */
  val scope: Var[Option[Component]] = Var(None)
  /** Where the focus goes when it has nowhere else: the showing screen's `defaultFocus`, as the screen manager says. */
  val home: Var[() => Option[Component]] = Var(() => None)
  /** The showing screen's own container, as the screen manager says: a focus whose place is gone looks no further out
    * than this for what took it, and goes to the screen's `defaultFocus` rather than to another part of the screen. */
  val page: Var[() => Option[Component]] = Var(() => None)
  /** How many times the app has been used — a key pressed, a pointer put down, moved or scrolled: what an app watches
    * to know somebody is there (a screensaver's idle time). */
  val activity: Var[Long] = Var(0L)
  /** Whether the keys are moving the focus, where a pointer is used (no remote): what the focus is drawn by
    * (`Component.focusShown`), as a browser's `:focus-visible`. A key that moves the focus or works what has it (Tab,
    * an arrow, Enter, Page Up and Down, Home and End, any key the focus moves on) sets it; a pointer put down clears
    * it. Where a remote moves the focus, it is always drawn, and this is not asked. */
  val visible: Var[Boolean] = Var(false)

  /** Somebody used the app just now. */
  def used(): Unit = {
    activity @= activity() + 1L
    everyui.task.Frames.wake()
  }

  /** Take the platform's keys: `ui` calls it as it starts, since the Roku compiler runs no statement in an object's
    * body. */
  def listen(): Unit = {
    // a key whose handling throws is told as that key's, on what had the focus, and taken (Errors.guard)
    Platform.onKey((name, pressed) => everyui.error.Errors.guard(
      "key " + name + (if (pressed) " down" else " up") + focused().map(c => " on " + named(c)).getOrElse(""), true)(
      key(name, pressed)))
    // only the component the focus leaves and the one it lands on hear of it, rather than every component on the
    // screen working out for itself whether it is the one (a home screen of rails is a hundred and more)
    // (the one it left kept here, rather than reactify's `changes`: its previous value is an Option of this Option,
    // which a Roku holds flattened, so the first focus of all, from none, went unheard there)
    focused.attach { now =>
      moves += 1
      marked.foreach { c =>
        c.focusedNow @= false
        if (c.showsFocus) c.focusShownNow @= false
        ring(c, false)
      }
      now.foreach { c =>
        c.focusedNow @= true
        if (c.showsFocus) c.focusShownNow @= visible()
        ring(c, visible())
      }
      marked = now
      now.foreach(remember)
      // a screen reader (TalkBack) reads out where the focus has landed, and can press it
      now.foreach { c =>
        val press = () => c.event.click @= PointerEvent(PointerKind.Click, centerX(c), centerY(c))
        c.node.accessibleFocus(press, () => everyui.access.Access.nameOf(c))
      }
    }
    if (Platform.remoteNavigation()) Platform.after(WatchSeconds, () => watch())
    else {
      visible.attach(v => marked.foreach { c =>
        if (c.showsFocus) c.focusShownNow @= v
        ring(c, v)
      })
      // every press heard at the root as well, where nothing under it listens (the page's empty space): on a turn of
      // its own, once `ui` is made
      Platform.later(() => {
        ui.event.pointer.down
        ui.event.pointer.up
      })
    }
  }

  /** `c`, marked as drawing its focus in a way of its own (on `focused`, or in a part of it), so no ring is drawn
    * round it for it (`ring`). */
  def ownRing[C <: Component](c: C): C = {
    c.drawsOwnFocus = true
    c
  }

  // a ring in the accent round what the keys focus, where it draws none of its own: an app's own component that
  // never asked whether its focus is shown (`focusShown`) and is not marked as drawing it (`ownRing`); EveryUI's own
  // kinds draw theirs. Where a remote moves the focus, the app draws it (a TV's screens are made for it).
  private def ring(c: Component, on: Boolean): Unit =
    if (!Platform.remoteNavigation() && !c.showsFocus && !c.drawsOwnFocus && !c.takesTyping && Component.appMade(c))
      c.node.setFocusRing(if (on) everyui.theme.Theme.accent().toHex else "", c.radius())

  // how many times the focus has moved, to know whether a press or a key moved it
  private var moves = 0

  /** Whether a click on `c` gives it the focus: wherever a remote moves the focus, and elsewhere where `c` takes it
    * from a click (`Component.clickFocus`). */
  def clickFocuses(c: Component): Boolean = Platform.remoteNavigation() || c.clickFocus()

  // the press going on, where a pointer is used: where it went down, how many moves the focus had made by then, and
  // whether a click took it (focused what it landed on, or let the focus go)
  private var pressing = false
  private var pressedX = 0.0
  private var pressedY = 0.0
  private var pressedMoves = 0
  private var pressTaken = false
  private var lastDown: Option[PointerEvent] = None
  private var lastDownAt = 0.0
  private var lastDownEvent = -1L

  /** A pointer went down (`PointerEvents`, once for each component it climbs through). Where a pointer is used, the
    * keys are no longer moving the focus (`visible`). */
  private[everyui] def pointerDown(e: PointerEvent): Unit = {
    // the same press, climbing through another component
    val again = lastDown.contains(e) && everyui.event.Pointer.same(lastDownEvent, lastDownAt)
    if (!Platform.remoteNavigation() && !again) {
      lastDown = Some(e)
      lastDownAt = Platform.ticks()
      lastDownEvent = Platform.pointerEvent()
      visible @= false
      pressing = true
      pressedX = e.x
      pressedY = e.y
      pressedMoves = moves
      pressTaken = false
    }
  }

  /** The pointer let go. Once its click has been heard, a press that no click took, that moved no focus, and that was
    * not on what has the focus (the page's empty space, words, a card that is no control) lets the focus go, as a
    * browser's does: a field so pressed away from no longer has the caret. Not under a coach mark, whose region holds
    * the focus. */
  private[everyui] def pointerUp(): Unit = if (pressing) {
    pressing = false
    Platform.later(() => if (!pressTaken && moves == pressedMoves && confined.isEmpty) focused().foreach { c =>
      val on = pressedX >= c.absoluteLeft && pressedX < c.absoluteLeft + c.size.width() &&
        pressedY >= c.absoluteTop && pressedY < c.absoluteTop + c.size.height()
      if (!on && showing(c)) focused @= None
    })
  }

  /** A click landed on `c`, which takes the focus from it (`clickFocuses`) or not: where it does not, the focus is let
    * go (unless it is on `c` already: a menu's row the pointer focused), so a button clicked is not left focused and a
    * field clicked away from loses the caret. What the click then does may put the focus somewhere itself (Send back
    * to the field written in). */
  private[everyui] def clicked(c: Component, takes: Boolean): Unit = {
    pressTaken = true
    if (!takes && !focused().exists(_ eq c) && confined.isEmpty) focused @= None
  }

  /** A press a drag took (`PointerDrag`, `Pointers.track`: a slider, a column's edge, a scrollbar): it lets no focus
    * go as it ends. */
  private[everyui] def dragging(): Unit = pressTaken = true

  /** Whether the focus is put back where it was (a screen returned to, a dialog closed): wherever a remote moves it,
    * and elsewhere while the keys are moving it (`visible`). */
  def restores: Boolean = Platform.remoteNavigation() || visible()

  /** Whether a screen shown starts with the focus on `c`, its `defaultFocus`: wherever a remote moves it; elsewhere
    * while the keys are moving it, or where `c` is typed into, or the screen asks (`always`). So a page opened with
    * the pointer has nothing lit, unless it starts at a field. */
  def starts(c: Component, always: Boolean): Boolean = restores || always || c.takesTyping

  // How often the focus is checked for, on a platform with only a remote to move it; and whether the last check
  // found it lost, since a screen that has just taken its rows down to build them again puts its own focus back a
  // moment later, and is not to be raced to it.
  private val WatchSeconds = 0.25
  private var lost = false
  // Each container the focus is in keeps the part of it that had the focus last, which is where an arrow coming back
  // into it lands (a row returned to at the card it was left on) when it states no entry of its own.
  private def remember(c: Component): Unit = {
    var at = c.parent()
    while (at.nonEmpty) {
      at.get match {
        case box: Container => box.remembered = Some(c)
        case _ => ()
      }
      at = at.flatMap(_.parent())
    }
  }

  /** On a platform navigated by a remote, something always has the focus: with nothing focused, nobody can see where
    * a press would go, and the only way back is a guess at an arrow. What takes the focus off the screen moves it on
    * itself (`offScreen`); this watch is the net under that, for a focus lost some other way. When it has been on
    * nothing, or on something no longer on the screen, for two checks in a row, it goes to what took the place of
    * what it was on, else to where the screen starts. */
  private def watch(): Unit = {
    if (focused().forall(c => !showing(c))) {
      if (lost) {
        recover()
        lost = false
      }
      else lost = true
    }
    else lost = false
    Platform.after(WatchSeconds, () => watch())
  }

  /** The focus onto what took the place of what it was on, else where the screen starts, else the first part of the
    * screen that can be seen: never left on nothing because the first choice happens to be out of sight. */
  private def recover(): Unit = {
    val was = focused()
    val found = was.flatMap(successor).orElse(start().filter(shown)).orElse(candidates(area()).find(shown))
    found.foreach { c =>
      // said, so a focus lost where nothing moved it on is found by reading the log rather than by a viewer stuck
      Platform.log("[focus] lost from " + was.map(_.kind).getOrElse("nothing") + ", now on " + c.kind)
      focused @= Some(c)
    }
  }

  /** The open dialog the focus is kept inside, while it is on the screen: a sheet taken off it by something else (a
    * screen shown in its place) holds the focus nowhere. */
  def dialog(): Option[Component] = scope().filter(showing)

  /** Where the focus may go now: inside the confined region, else inside the open dialog, else anywhere under `ui`. */
  def area(): Component = fence().map(_.region).orElse(dialog()).getOrElse(ui)

  // the regions the focus is held in, the newest first
  private var confined: List[Confinement] = Nil

  /** Hold the focus in `region` until the returned function is called: a coach mark's stop, where the viewer is to do
    * just what its note says. The arrows move only among what is in it, by its layouts and the exits it states (an
    * exit leading out of it goes nowhere, and an arrow with nowhere to go leaves the focus where it is); with the focus
    * outside it, any arrow lands in it (its entry, else the part last focused, else its first), and nothing else
    * reaches what has the focus there. Without `presses`, OK, a click and a tap in it do nothing; without `holds`, OK
    * held does not hold (OK presses at once, where presses are let), nor does a long press, the secondary button or
    * the menu key. Without `arrows`, no arrow (nor Tab) moves the focus inside it: it stays where it is, and what the
    * page itself focuses in it (a row built again) is taken as ever. A focus asked for outside it (`focus()`) is
    * refused, as a pointer at the parts of the screen that were up when it began is: one put up afterwards (the note
    * beside it) is pointed at as ever.
    *
    * The focused part taken out of it (a row built again, a card disposed) hands the focus to what took its place in
    * the region, else to the region's entry or first, a turn later: never to nothing, nor out of it. The region
    * itself taken off the screen holds nothing any more, and the focus moves on to what took its place, else to where
    * the screen starts; so confine a container that stays (a menu's rows' own parent) rather than a part the page builds again.
    *
    * A dialog put up over it has the focus and the keys while it is up, and gives the focus back inside it as it
    * closes; one confinement over another holds the focus until it is released, and the one under it then holds it
    * again. Back answers as ever. A Var set directly (`focused @= …`) is not checked: it is the focus layer's own. */
  def confine(region: Component, presses: Boolean = true, holds: Boolean = true,
              arrows: Boolean = true): () => Unit = {
    val held = new Confinement(region, presses, holds, arrows, ui.children())
    confined = held :: confined
    () => confined = confined.filterNot(_ eq held)
  }

  /** The confinement holding the focus now: the newest, while its region is on the screen and in where the focus may
    * go otherwise. A dialog put up elsewhere holds it instead. */
  private def fence(): Option[Confinement] =
    confined.headOption.filter(c => showing(c.region) && dialog().forall(d => within(c.region, d)))

  // whether the confinement holding the focus lets no arrow (nor Tab) move it
  private def stilled: Boolean = fence().exists(f => !f.arrows)

  /** Whether the focus may be put on `c`: inside the open dialog, if one is up, and inside the confined region. */
  def admits(c: Component): Boolean =
    dialog().forall(d => within(c, d)) && fence().forall(f => within(c, f.region))

  /** Whether a pointer's `kind` at `c` is heard: under a confinement, nothing on the screen it holds but what is in its
    * region is, and of that, neither a click (unless it lets presses) nor a menu asked for (unless it lets holds). The
    * pointer leaving and lifting are heard everywhere, so nothing is left thinking itself under it or held down. */
  def hears(c: Component, kind: PointerKind): Boolean = fence().forall { f =>
    kind == PointerKind.Leave || kind == PointerKind.Up || !f.layers.exists(_ eq layer(c)) ||
      (within(c, f.region) && (f.presses || kind != PointerKind.Click) && (f.holds || kind != PointerKind.Menu))
  }

  /** The part of the screen `c` is in: what it is in that sits on `ui` itself (a screen, a dialog's sheet, a note). */
  def layer(c: Component): Component = {
    var at = c
    while (at.parent().exists(p => !(p eq ui))) at = at.parent().get
    at
  }

  // the field that left the screen with the focus where a pointer is used, and the count of focus moves it left at
  private var parked: Option[(Component, Int)] = None

  // whether a look at a focus taken off the screen is waiting for its turn
  private val looking: Var[Boolean] = Var(false)

  /** Something was taken off the screen. If the focus was on it, or inside it, the focus moves on to what took its
    * place (`successor`). A turn later rather than at once: what is taken off is usually being replaced in the same
    * turn (a row read again, a list built over), and what takes its place is only there once that is done — or the
    * code doing it has put the focus back itself. With nothing in its place (a whole screen gone, the next on its
    * way), whatever shows next says where the focus starts, and the watch is there should nothing say. */
  def offScreen(): Unit = if (!looking() && focused().exists(c => !showing(c))) {
    looking @= true
    Platform.later(() => {
      looking @= false
      moveOn(false)
    })
  }

  /** A focus on something no longer on the screen, moved on at once: to what took its place, or with `anywhere`,
    * failing that, to where the screen starts. */
  private def moveOn(anywhere: Boolean): Unit = focused().filterNot(showing).foreach { gone =>
    // Where a pointer is used and the keys are not moving the focus, nothing takes its place, as on a web page: but a
    // field built again in its place, the same kind and typed into, is the same field to whoever is writing in it
    if (!restores && confined.isEmpty) {
      focused @= successor(gone).filter(s => gone.takesTyping && s.takesTyping && s.kind == gone.kind)
      // and a field written in that is only being moved (taken off and put back a turn or more later) has it back
      // (`placed`)
      parked = if (focused().isEmpty && gone.takesTyping) Some((gone, moves)) else None
    }
    // (under a confinement, where it starts at once, whether the region is still there or not: a tour never leaves it
    // on what is gone)
    else successor(gone).orElse(if (anywhere || confined.nonEmpty) start() else None) match {
      case Some(to) =>
        Platform.log("[focus] " + gone.kind + " left the screen, the focus goes to " + to.kind)
        focused @= Some(to)
      case None => ()
    }
  }

  /** Something was put on the screen. Where a pointer is used, a field the focus left the screen with, put back with
    * nothing focused since, has the focus again, and the platform's (`Component.refocus`): the field that moved, a
    * conversation's composer put into the view of another, is still the one being written in. */
  private[everyui] def placed(): Unit = parked.foreach { case (field, at) =>
    if (moves != at || field.takenApart) parked = None
    else if (showing(field)) {
      parked = None
      focused @= Some(field)
      field.refocus()
    }
  }

  /** The platform's focus left `c` for nothing, by the person's doing rather than the app's: a click on the page's
    * empty space, with the window still in front (the backend says so). Where the focus has to be on something (a
    * remote's platform) it stays, to be seen and pressed; elsewhere it is let go of too, as the page shows it, so the
    * field no longer looks focused and `focus()` focuses it again. A field taken off the page and put back, or a
    * window or a tab left, is not this: the one has its focus put back as it is placed (Component.refocus), and the
    * other has the platform give it back. */
  def dropped(c: Component): Unit =
    if (!Platform.remoteNavigation() && focused().exists(_ eq c) && showing(c)) focused @= None

  /** The focus back on `to`, as a dialog closing gives it back: or, where `to` has left the screen since, on what took
    * its place; or where the screen starts, with nothing to go back to. Where a pointer is used, never to where the
    * screen starts (nothing had the focus, and nothing has it), and to what took the place of `to` only while the keys
    * are moving the focus (`visible`); drawn as the focus is. */
  def restore(to: Option[Component]): Unit =
    if (!Platform.remoteNavigation())
      to.filter(c => showing(c) && admits(c)).orElse(if (visible()) to.flatMap(successor) else None).foreach(_.focus())
    else to.filter(c => showing(c) && admits(c)).orElse(to.flatMap(successor)).orElse(start()).foreach(_.focus())

  /** What the focus goes to when `gone` has left the screen with it: what took its place in the container it left —
    * the part now where it was, else the next, else the one before (it was the last) — else that container's entry;
    * and where the container left too, the same a level up. Decided by the tree alone, never by where anything is on
    * the screen, and only ever within where the focus may go (an open dialog). None when nothing it was in is left
    * short of the screen itself, which leaves it to where the screen starts. */
  def successor(gone: Component): Option[Component] = {
    val reach = area()
    var child = gone
    var found: Option[Component] = None
    var steps = 0
    // up through what went with it, then out from the first container still here, a level at a time
    while (found.isEmpty && steps < 64) {
      val box = child.parent().orElse(child.leftParent)
      if (box.isEmpty) steps = 64
      else {
        val index = if (child.parent().nonEmpty) box.get.childComponents.indexWhere(_ eq child) else child.leftIndex
        // at the top of where the focus may go (a screen, or the dialog), where it starts is the one to say, rather
        // than whichever part of the screen sits next in its tree
        // (a confined region is looked in, though: what took the place of a row of it rebuilt is the one to have it)
        if (fence().exists(f => (f.region eq box.get) && showing(box.get))) {
          found = around(box.get, index, child)
          steps = 64
        }
        else if ((box.get eq reach) || page()().exists(_ eq box.get) || box.get.parent().exists(_ eq ui)) steps = 64
        else if (showing(box.get) && within(box.get, reach)) found = around(box.get, index, child)
        child = box.get
      }
      steps += 1
    }
    found
  }

  // In `box`, where `gone` was at `index`: the part now there, else the next, else the one before; or the entry the
  // box states. Nothing when nothing in it takes the focus.
  private def around(box: Component, index: Int, gone: Component): Option[Component] = {
    val parts = box.childComponents.filterNot(_ eq gone)
    val at = math.max(0, math.min(index, parts.length))
    val order = parts.drop(at) ++ parts.take(at).reverse
    order.find(p => candidates(p).nonEmpty).flatMap(pick).orElse(entered(box).flatMap(pick))
  }

  /** Where the focus starts over: the confined region's entry or its first, else an open dialog's, else the screen's
    * `defaultFocus`, else the first of all. */
  private def start(): Option[Component] = fence() match {
    case Some(f) => pick(f.region)
    case None => dialog() match {
      case Some(sheet) => pick(sheet)
      case None => home()().filter(c => showing(c) && candidates(c).nonEmpty).flatMap(pick).orElse(pick(ui))
    }
  }

  /** Whether `c` is on the screen to be seen: under `ui`, and neither it nor anything it is inside hidden. */
  private def shown(c: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var seen = true
    while (seen && at.nonEmpty) {
      if (!at.get.visible()) seen = false
      at = at.flatMap(_.parent())
    }
    seen && showing(c) && c.size.width() > 0.0 && c.size.height() > 0.0
  }

  private var marked: Option[Component] = None

  /** Whether Enter is down and has already clicked: its repeats, until it is let go, are the same press. */
  private var enterHeld = false

  /** What OK went down on, where it waits for the release (`Events.hold`); whether the hold has fired; and which
    * press is which, so a late timer from an earlier one does nothing. */
  private var holding: Option[Component] = None
  private var held = false
  private var holds = 0
  private var heldFrom = 0.0

  private def hold(c: Component): Unit = {
    held = true
    c.event.hold @= (())
  }

  private var backs: List[Key => Boolean] = Nil

  /** Handle Back (or Escape) before anything registered earlier: the newest handler that takes it wins. A dialog
    * registers while it is open, the screen manager for as long as it runs. The returned function unregisters it. */
  def onBack(handler: Key => Boolean): () => Unit = {
    backs = handler :: backs
    () => backs = backs.filterNot(_ eq handler)
  }

  private var hooks: List[Key => Boolean] = Nil

  /** Handle a key the focused component did not take, before the focus layer makes its own use of it: a player's
    * transport keys, which mean what they say wherever the focus happens to be. The newest handler wins, and the
    * returned function unregisters it. Arrows and Enter reach a hook only where nothing focused wanted them. */
  def onKey(handler: Key => Boolean): () => Unit = {
    hooks = handler :: hooks
    () => hooks = hooks.filterNot(_ eq handler)
  }

  private def hooked(key: Key): Boolean = {
    var taken = false
    var left = hooks
    while (!taken && left.nonEmpty) {
      taken = left.head(key)
      left = left.tail
    }
    taken
  }

  /** Where `event` types, when it is a character, a space, Backspace or a phone app's Enter with no modifier but Shift:
    * the text entry of the focused component's nearest container that takes typing (`Container.typeInto`), it
    * included. */
  private def entryFor(target: Option[Component], event: KeyEvent): Option[TextEntry] =
    if (event.ctrl || event.meta || event.alt || !(event.key == Key.Backspace || event.key == Key.Space ||
      event.name == Keys.TypedEnter || (event.key == Key.Other && event.name.length == 1))) None
    else {
      var at = target
      var found: Option[TextEntry] = None
      while (found.isEmpty && at.nonEmpty) {
        at.get match {
          case box: Container => found = box.textEntry
          case _ => ()
        }
        at = at.flatMap(_.parent())
      }
      found
    }

  private def goBack(key: Key): Boolean = {
    var taken = false
    var left = backs
    while (!taken && left.nonEmpty) {
      taken = left.head(key)
      left = left.tail
    }
    // said, since a Back nothing on the screen answered is otherwise indistinguishable from a remote that did nothing
    if (!taken && key == Key.Back) Platform.log("[focus] Back left to the platform, the focus on " +
      focused().map(_.kind).getOrElse("nothing"))
    taken
  }

  /** A key by the web's name, pressed or released — what each backend's key hook reports, with the modifiers held
    * (`Platform.modifiers`). Whether the focus layer took it, so the platform leaves it be.
    *
    * A key that runs a command (`everyui.keymap.Keymap`: the showing screen's shortcuts, then the global ones) is taken
    * first, before the focused component hears of it. A key meant for an embed, something that handles its own keys
    * (`Platform.keyEmbed`: an editor in a web page), is the embed's, untouched — Escape too — unless it runs a command
    * made to win there or one on a chord the embed leaves to the app (`Keymap.embed`); the embed can give a key back
    * (`handBack`). Otherwise a key the focused component keeps (`keepsKey`) goes to it alone.
    *
    * A double-tap of a modifier (`Chord.doubleTap`) runs its command on the second release, inside an embed too, and
    * while one of Alt runs a command, Alt on its own is kept from the platform (`Keymap.keepsAlt`). */
  def key(name: String, pressed: Boolean): Boolean = {
    val key = Keys.of(name)
    val held = Platform.modifiers()
    val event = KeyEvent(key, name, held.ctrl, held.meta, held.alt, held.shift)
    val embed = Platform.keyEmbed()
    if (pressed) used() else everyui.task.Frames.wake()
    val was = moves
    if (pressed) keyed(event)
    // heard before anything else might take the key
    val tapped = Keymap.tap(event, pressed, Platform.keyRepeat())
    val taken =
      if (tapped) true
      else if (embed.nonEmpty) pressed && Keymap.run(event, embed)
      else if (pressed && Keymap.run(event)) true
      else focusKey(key, event, pressed)
    if (pressed) movedBy(was)
    taken || Keymap.keepsAlt(event)
  }

  // Where a pointer is used, a key that moves the focus or works what has it: the keys are moving the focus from now
  // (`visible`), so it is drawn. Tab from anywhere; the others where something not typed into has the focus (in a
  // field they move its caret).
  private def keyed(e: KeyEvent): Unit = if (!Platform.remoteNavigation() && !visible()) {
    val working = focused().exists(c => !c.takesTyping) && (Keys.isArrow(e.key) || e.key == Key.Enter ||
      e.key == Key.PageUp || e.key == Key.PageDown || e.name == "Home" || e.name == "End")
    if (e.key == Key.Tab || working) visible @= true
  }

  // and a key the focus moved on as it was handled (a shortcut to a list, Escape closing a dialog): the same
  private def movedBy(was: Int): Unit =
    if (!Platform.remoteNavigation() && moves != was && focused().nonEmpty && !visible()) visible @= true

  /** A key an embed had and gives back, pressed as though it had never been the embed's: an editor's Escape with no
    * popup of its own to close, which is then Back (or a command's). `name` is the web's name for it. Whether
    * anything took it. */
  def handBack(name: String, ctrl: Boolean = false, meta: Boolean = false, alt: Boolean = false,
               shift: Boolean = false): Boolean = {
    val event = KeyEvent(Keys.of(name), name, ctrl, meta, alt, shift)
    used()
    val was = moves
    val taken = Keymap.run(event) || focusKey(event.key, event, true)
    movedBy(was)
    // its release was the embed's: a press given back is a whole press
    enterHeld = false
    taken
  }

  // A key for the focus layer: to the focused component, then the screen's hooks, then Enter, the arrows, Tab and Back.
  private def focusKey(key: Key, event: KeyEvent, pressed: Boolean): Boolean = {
    if (key == Key.Enter && !pressed) enterHeld = false
    // a key never goes to something no longer on the screen: from there no arrow leads anywhere, and it answers nothing
    if (pressed) moveOn(true)
    // what had the focus when the key came: a component that moves the focus as it handles its own key is still the one
    // that kept it, and the key does not travel on from wherever the focus went
    val target = focused()
    // under a confinement, what the focused part does not hear: anything, while it is outside the region; OK, where
    // presses are held back; the menu key, where holds are. Nor do the screen's hooks hear anything (a player's keys)
    val fenced = fence()
    val outside = fenced.exists(f => target.exists(c => !within(c, f.region)))
    val deaf = outside || fenced.exists(f => (key == Key.Enter && !f.presses) || (key == Key.ContextMenu && !f.holds))
    // asked before it hears the key, which may change its mind (a scroll that reaches its end)
    val kept = pressed && !deaf && target.exists(_.keepsEvent(event))
    // and one the platform's own field types with or moves its caret by: heard, and then left to the platform
    val typed = pressed && !deaf && !kept && target.exists(_.typesEvent(event))
    // and one typed where the focus is in something that takes typing (Container.typeInto) is that's, in its place
    val entry = if (pressed && !deaf && !kept && !typed) entryFor(target, event) else None
    if (entry.isEmpty && !deaf) target.foreach { c =>
      if (pressed) c.event.key.down @= event else c.event.key.up @= event
    }
    // OK let go on what it went down on, which is waiting for the release to know whether it was a press or a hold: a
    // press, unless the hold already fired or presses are held back. Only there — a release whose press went somewhere
    // else is nobody's press.
    if (!pressed && key == Key.Enter && holding.nonEmpty) {
      val was = holding
      holding = None
      if (!held && !deaf) was.filter(c => target.exists(_ eq c)).foreach(c =>
        c.event.click @= PointerEvent(PointerKind.Click, centerX(c), centerY(c)))
      true
    }
    else if (!pressed) false
    // A kept key was consumed by the component it went to, so the platform must leave it alone. Saying otherwise
    // let both act on the same press: Back in the screensaver went to the screensaver, which returned to Home, AND
    // on to the Roku, which closed the channel — one press, two answers, and the app was gone.
    else if (kept) true
    // a key typed into a field is the field's: the platform types it, and the focus layer makes no use of it
    else if (typed) false
    else if (entry.nonEmpty) {
      val to = entry.get
      if (key == Key.Backspace) to.erase()
      else if (event.name == Keys.TypedEnter) to.enter()
      else to.typed(event.name)
      true
    }
    // what the screen itself claims, before the focus layer makes its own use of the key: not an arrow with Alt, Ctrl
    // or Meta held, which a hook (hearing the key alone) cannot tell from a plain one
    else if (fenced.isEmpty && !(Keys.isArrow(key) && modified(event)) && hooked(key)) true
    // one press, one click: a held OK is a single press however long it is held, where the remote's own repeat made
    // it a click every fiftieth of a second — and once the first had opened the next screen, the rest landed on
    // whatever had the focus there, which is usually Play. Held arrows still repeat; that is how a rail is run along.
    else if (key == Key.Enter && enterHeld) {
      // the remote's own repeat, well into a hold, is the hold as surely as the timer is: whichever comes first
      holding.filter(_ => !held && Platform.ticks() - heldFrom >= 400.0).foreach(hold)
      true
    }
    // something with a context menu waits to see how long OK is held (`Events.hold`), unless holds are held back
    else if (key == Key.Enter && target.exists(_.event.holdable) && !outside && fenced.forall(_.holds)) target.map { c =>
      enterHeld = true
      holding = Some(c)
      held = false
      heldFrom = Platform.ticks()
      holds += 1
      val mine = holds
      Platform.after(0.5, () => if (mine == holds && !held && holding.exists(_ eq c)) hold(c))
      true
    }.getOrElse(false)
    // held back by a confinement: the press is taken, and nothing hears it
    else if (key == Key.Enter && deaf) {
      enterHeld = true
      true
    }
    else if (key == Key.Enter) target.map { c =>
      enterHeld = true
      c.event.click @= PointerEvent(PointerKind.Click, centerX(c), centerY(c))
      true
    }.getOrElse(false)
    else if (Keys.isArrow(key)) !modified(event) && move(key)
    else if (key == Key.Tab && event.shift) previous()
    else if (key == Key.Tab) next()
    else if (key == Key.Back || key == Key.Escape) goBack(key)
    else false
  }

  // Alt, Ctrl or Meta held: an arrow the platform has a use for (Back and Forward in a browser, a word or a line along
  // in a field), so the focus layer leaves it be unless a chord took it first
  private def modified(e: KeyEvent): Boolean = e.alt || e.ctrl || e.meta

  /** Move the focus an arrow's way (see above). Whether it moved. */
  def move(direction: Key): Boolean = {
    val root = area()
    val all = candidates(root)
    focused() match {
      // where a pointer is used, with nothing focused and the keys not moving the focus, the arrow is the page's (or a
      // list's) to scroll by
      case None if !restores => false
      case None =>
        start().foreach(_.focus())
        all.nonEmpty
      case Some(was) if !within(was, root) =>
        start().foreach(_.focus())
        all.nonEmpty
      // held where it is by a confinement that lets no arrow move it
      case Some(_) if stilled => true
      case Some(was) =>
        travel(was, root, direction) match {
          case Some(next) =>
            next.focus()
            true
          case None =>
            // said, so a hand-off nobody has stated yet is found by using the app rather than by a viewer stuck
            Platform.log("[focus] no way " + (if (direction == Key.ArrowUp) "up" else if (direction == Key.ArrowDown) "down"
              else if (direction == Key.ArrowLeft) "left" else "right") + " from " + was.kind)
            false   // left to the platform: a page scrolls, say
        }
    }
  }

  /** From `was` up to `root`, the first container with somewhere to go `direction`'s way. */
  private def travel(was: Component, root: Component, direction: Key, dry: Boolean = false): Option[Component] = {
    var child: Component = was
    var found: Option[Component] = None
    var stopped = false
    // what the focused part states for itself comes first: a button that says where Down goes means it, though it holds
    // nothing to move among
    was match {
      case own: Container =>
        own.focusAction(direction).foreach { action =>
          if (!dry) action()
          stopped = true
        }
        if (!stopped) own.focusExit(direction) match {
          case Some(Some(target)) => found = enter(target, was)
          case Some(None) => stopped = true
          case None => ()
        }
      case _ => ()
    }
    while (found.isEmpty && !stopped && !(child eq root) && child.parent().nonEmpty) {
      val parent = child.parent().get
      parent match {
        case c: Container =>
          found = c.layout() match {
            case Some(layout) =>
              var next = layout.neighbour(c, c.children(), child, direction)
              var entered: Option[Component] = None
              var steps = 0
              // past a neighbour with nothing focusable in it, to the one beyond
              while (entered.isEmpty && next.nonEmpty && steps <= c.children().length) {
                entered = enter(next.get, was)
                if (entered.isEmpty) next = layout.neighbour(c, c.children(), next.get, direction)
                steps += 1
              }
              entered
            // a container without a layout moves nothing by itself: what lies which way is for it to state
            case None => None
          }
          // nothing inside this way: something the container does instead of moving, where it says so
          if (found.isEmpty) c.focusAction(direction).foreach { action =>
            if (!dry) action()
            stopped = true
          }
          // nothing inside this way: the container's own exit, if it states one
          if (found.isEmpty && !stopped) c.focusExit(direction) match {
            case Some(Some(target)) => found = enter(target, was)
            case Some(None) => stopped = true
            case None => ()
          }
        case _ => ()
      }
      child = parent
    }
    // an exit stated out of where the focus may go (a dialog, a confined region) goes nowhere
    found.filter(f => within(f, root))
  }

  /** The part of `target` the focus lands on, arriving from `from` (see [[pick]]). A container around it that states its
    * entry, being entered from outside, decides first: the outermost of them, since it is the one being entered. */
  private def enter(target: Component, from: Component): Option[Component] = pick(target).map(c => arrive(c, from))

  /** Where the focus lands, having picked `c` coming from `from`: `c` itself, unless it sits inside a container that
    * names its entry and the focus is arriving there from outside it — then that entry. A tab bar entered a level down
    * is entered at its selected tab, not at whichever of its tabs the move happened to reach. */
  private def arrive(c: Component, from: Component): Component = {
    val root = area()
    var at: Option[Component] = Some(c)
    var entry: Option[Component] = None
    while (at.nonEmpty && !at.exists(_ eq root)) {
      at.get match {
        case box: Container if !within(from, box) =>
          box.focusEntry.filter(e => showing(e) && candidates(e).nonEmpty).foreach(e => entry = Some(e))
        case _ => ()
      }
      at = at.flatMap(_.parent())
    }
    entry.flatMap(e => pick(e)).getOrElse(c)
  }

  /** Where the focus lands in `target`: itself if it takes the focus; else the entry it states; else the part of it
    * that last had the focus; else its first. Never by where anything is on the screen. */
  private def pick(target: Component): Option[Component] =
    if (target.focusable()) Some(target)
    else if (entered(target).nonEmpty) entered(target).flatMap(e => pick(e))
    else {
      val all = candidates(target)
      target match {
        case box: Container => box.remembered.filter(r => all.exists(_ eq r)).orElse(all.headOption)
        case _ => all.headOption
      }
    }

  // the part a container says an arrival lands on, when it has one to take the focus
  private def entered(target: Component): Option[Component] = target match {
    case c: Container => c.focusEntry.filter(e => candidates(e).nonEmpty)
    case _ => None
  }

  /** Tab: the next candidate in tree order, round to the first after the last. Whether anything was focused. */
  def next(): Boolean = {
    val all = candidates(area())
    if (all.isEmpty) false
    else if (stilled && focused().exists(c => within(c, area()))) true
    else {
      var at = -1
      all.zipWithIndex.foreach { case (c, i) => if (focused().exists(_ eq c)) at = i }
      all((at + 1) % all.length).focus()
      true
    }
  }

  /** Shift+Tab: the candidate before in tree order, round to the last before the first. Whether anything was
    * focused. */
  def previous(): Boolean = {
    val all = candidates(area())
    if (all.isEmpty) false
    else if (stilled && focused().exists(c => within(c, area()))) true
    else {
      var at = all.length
      all.zipWithIndex.foreach { case (c, i) => if (focused().exists(_ eq c)) at = i }
      all((at - 1 + all.length) % all.length).focus()
      true
    }
  }

  /** Whether `c` is `ancestor` or somewhere under it. */
  def within(c: Component, ancestor: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var found = false
    while (!found && at.nonEmpty) {
      if (at.exists(_ eq ancestor)) found = true
      at = at.flatMap(_.parent())
    }
    found
  }

  /** Where an arrow may have no stated way between parts of `root`: each container without a layout that holds more
    * than one part taking the focus and states no exit of its own, described. A tool for finding the hand-offs a screen
    * has not stated (the app run with `focusAudit`), not something the app acts on. */
  def unstated(root: Component): List[String] = {
    var found: List[String] = Nil
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = c.childComponents.toList ++ pending.tail
      c match {
        case box: Container if box.layout().isEmpty =>
          val parts = box.children().filter(child => candidates(child).nonEmpty)
          def states(c: Component): Boolean = c match {
            case b: Container => List(Key.ArrowUp, Key.ArrowDown, Key.ArrowLeft, Key.ArrowRight)
              .exists(d => b.focusExit(d).nonEmpty || b.focusAction(d).nonEmpty)
            case _ => false
          }
          // each part by what takes the focus in it first, and whether it says where its arrows go
          def said(part: Component): String =
            candidates(part).headOption.map(_.kind).getOrElse(part.kind) + (if (states(part)) " (states exits)" else "")
          if (parts.length > 1 && !states(box))
            found = found :+ (box.kind + " holds " + parts.map(said).mkString(" | "))
        case _ => ()
      }
    }
    found
  }

  /** What no arrow reaches: each focusable under `root` that the moves from where the screen starts never land on,
    * following every arrow from everything reached, as a viewer with a remote would. An arrow a container turns into
    * an action counts as going nowhere, since running it is not looking. Each is named by its kind and the first
    * words in it. */
  def unreached(root: Component): List[String] = {
    val all = candidates(root)
    var seen: Vector[Component] = start().filter(s => within(s, root)).orElse(all.headOption).toVector
    var pending: List[Component] = seen.toList
    while (pending.nonEmpty) {
      val at = pending.head
      pending = pending.tail
      List(Key.ArrowUp, Key.ArrowDown, Key.ArrowLeft, Key.ArrowRight).foreach { d =>
        travel(at, root, d, dry = true).foreach { next =>
          if (!seen.exists(_ eq next)) {
            seen = seen :+ next
            pending = pending :+ next
          }
        }
      }
    }
    all.filterNot(c => seen.exists(_ eq c)).filter(c => inView(c)).map(c => named(c)).toList
  }

  // on the screen, or in something that scrolls it there: a part parked off the edge (a bar waiting to slide in) is
  // not one a viewer is missing
  private def inView(c: Component): Boolean = {
    val across = c.absoluteLeft + c.size.width() > 0.0 && c.absoluteLeft < ui.size.width()
    val down = c.absoluteTop + c.size.height() > 0.0 && c.absoluteTop < ui.size.height()
    var scrolled = false
    var at = c.parent()
    while (!scrolled && at.nonEmpty) {
      if (at.get.kind == "ScrollContainer") scrolled = true
      at = at.get.parent()
    }
    (across && down) || scrolled
  }

  // a component as a person would point at it: the first words or icon shown in it, else the nearest ones around it
  private[everyui] def named(c: Component): String = {
    val own = shownIn(c)
    if (own.nonEmpty) c.kind + " " + own
    else {
      var near = ""
      var at = c.parent()
      while (near.isEmpty && at.nonEmpty) {
        near = shownIn(at.get)
        at = at.get.parent()
      }
      c.kind + (if (near.isEmpty) "" else " near " + near)
    }
  }

  private def shownIn(c: Component): String = {
    var found = ""
    var pending: List[Component] = List(c)
    while (found.isEmpty && pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ++ pending.tail
      val k = at.kind
      if (k.startsWith("TextView(") && k.length > 11) found = "\"" + k.substring(9, k.length - 1).take(40) + "\""
      else if (k.startsWith("MaterialIconView(")) found = "[" + k.substring(17, k.length - 1) + "]"
    }
    found
  }

  /** Whether `c` is on screen: under `ui`, focusable or not. */
  def showing(c: Component): Boolean = within(c, ui)

  /** The focusable components under `root`, in tree order. Walked with a list rather than by recursion: a Roku's
    * stack is small, and every level of a deep tree (a dialog on its sheet) costs it several frames. */
  def candidates(root: Component): Vector[Component] = {
    var found: Vector[Component] = Vector.empty
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = c.childComponents.toList ++ pending.tail
      if (c.focusable()) found = found :+ c
    }
    // what is marked to come first (a skip link, drawn last to be over the screen) before the rest
    if (found.exists(_.tabFirst)) found.filter(_.tabFirst) ++ found.filterNot(_.tabFirst) else found
  }

  private def centerX(c: Component): Double = c.absoluteLeft + c.size.center()
  private def centerY(c: Component): Double = c.absoluteTop + c.size.middle()
}
