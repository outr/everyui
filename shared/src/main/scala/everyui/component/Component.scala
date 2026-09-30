package everyui.component

import reactify.*
import everyui.{Color, Gradient, Shadow}
import everyui.component.feature.{MeasuredFeature, PositionFeature, SizeFeature}
import everyui.component.types.{Prop, Semantics, WhiteSpace}
import everyui.event.{Events, Key, KeyEvent}
import everyui.focus.Focus
import everyui.motion.Track
import everyui.render.RenderNode

/** youi's `Component`, with the DOM element replaced by a [[RenderNode]] — which each backend supplies — so the
  * same Props and features run over a DOM element or a SceneGraph node. */
class Component(val node: RenderNode) {
  /** For diagnostics (a tree dump): what this is, and what it contains. */
  def kind: String = "Component"
  def childComponents: Vector[Component] = Vector.empty
  /** Done with for good: taken apart, so what it held can be let go. A binding to something that lives on (a theme's
    * colour, the language) holds the component it sets, and through each parent it held everything above it, and so
    * everything below that: a screensaver's scenes, each with its pictures, piled up until a Roku closed the channel
    * for its memory. Taken apart, such a binding holds only its own piece. A container takes its children apart too,
    * and a player lets its player go. Nothing is to be shown again once it is disposed of. */
  def dispose(): Unit = if (!takenApart) {
    takenApart = true
    val undo = cleanups
    cleanups = Nil
    undo.foreach(f => f())
    // what moves on its own stops: a Roku runs it on the node, which is not coming back
    if (ownMotion) node.setMotion(Nil)
    // each property bound to something (`color := Theme.text()`) stops listening to it, so what it is bound to no
    // longer holds it; and the size, which a screen binds to its host's
    owned.foreach(v => Component.unbind(v))
    Component.unbind(size.width)
    Component.unbind(size.height)
    // taken off the screen as it is taken apart: a focus on it moves on to what is there in its place
    parent().foreach { p =>
      leaves(p, p.childComponents.indexWhere(_ eq this))
      parent @= None
      Focus.offScreen()
    }
  }

  /** Whether this has been taken apart (`dispose`): taking it apart again does nothing. */
  private[everyui] var takenApart: Boolean = false

  /** What to undo when this is disposed of: a reaction it put on something that lives on, above all. */
  private var cleanups: List[() => Unit] = Nil
  def onDispose(f: () => Unit): Unit = cleanups = f :: cleanups

  /** `f` on each change of `to`, until this is disposed of: `to` usually lives on (the focus, the theme, the clock),
    * and a reaction left on it held this, and everything reachable from it, for as long as the app ran. */
  def follow[T](to: Reactive[T], f: T => Unit): Unit = {
    val reaction = to.attach(f)
    onDispose(() => to.reactions -= reaction)
  }

  /** The properties made for this component so far (a lazy one is made on first use), for `dispose` to unbind. */
  private var owned: List[Val[?]] = Nil
  def own[V <: Val[?]](v: V): V = {
    owned = v :: owned
    v
  }
  /** Ask for this to be laid out again: a container asks the layout pass (`LayoutPass`); anything else has nothing
    * to lay out. */
  def requestLayout(): Unit = ()

  /** The children of this changed (ContainerFeature): a container follows what came and what went. */
  private[everyui] def childListChanged(added: Vector[Component], removed: Vector[Component]): Unit = ()
  /** What the containers this is in follow of it. */
  private[component] var containerWatches: List[ChildWatch] = Nil
  /** A mark for a pass over lists of components (ContainerFeature), cleared by the pass that set it. */
  private[everyui] var listMark: Boolean = false

  lazy val measure: Trigger = Trigger()
  lazy val measured: MeasuredFeature = new MeasuredFeature(this)
  val size: SizeFeature = new SizeFeature(this)
  // told to the screen being made, if one is (Component.making), so it can take this apart with it
  Component.making().foreach(f => f(this))
  lazy val position: PositionFeature = new PositionFeature(this)

  /** Whether the platform draws this at all. A component that is not shown keeps its place and its size: it is out of
    * sight, not out of the layout. */
  lazy val visible: Prop[Boolean] = own(new Prop[Boolean](true, v => node.setVisible(v)))

  /** How opaque it is drawn, with all it holds: 1.0 is solid, 0.0 unseen (but still there, unlike `visible`). */
  lazy val opacity: Prop[Double] = own(new Prop[Double](1.0, o => node.setOpacity(o)))

  /** Drawn larger or smaller about its own centre, its place and its size unchanged — what a card does under the
    * focus. 1.0 is its own size. */
  lazy val scale: Prop[Double] = own(new Prop[Double](1.0, s => node.setScale(s)))

  /** Drawn once into a picture of its own and reused while it does not change: what sits over something that moves
    * (particles) is then not drawn again every frame. Where the platform keeps no such pictures, nothing changes. */
  lazy val cached: Prop[Boolean] = own(new Prop[Boolean](false, c => node.setCached(c)))

  /** Properties moving on their own, run by the platform (see everyui.motion.Track): setting them replaces what was
    * moving, and Nil stops it. */
  lazy val motion: Prop[List[Track]] = own(new Prop[List[Track]](Nil, t => {
    ownMotion = t.nonEmpty
    node.setMotionOwner(() => Component.lineage(this))
    node.setMotion(t)
  }))
  private var ownMotion = false

  lazy val color: Prop[Color] = own(new Prop[Color](Color.Black, c => node.setColor(c.toHex)))
  lazy val backgroundColor: Prop[Color] =
    own(new Prop[Color](Color.Clear, c => node.setBackgroundColor(Component.css(c))))
  /** A gradient over the whole box, on top of the background colour; a backend that draws none fills with its first
    * stop. */
  lazy val gradient: Prop[Option[Gradient]] = own(new Prop[Option[Gradient]](None, g => g match {
    case Some(value) => node.setGradient(value.angle, value.encoded)
    case None => node.setGradient(0.0, "")
  }))
  /** How far it is blurred: a gaussian's deviation in pixels, as the web's `filter: blur()` takes it; 0 is sharp. The
    * web, the desktop and Android draw it (before Android 12 only on a container or a picture); elsewhere it is drawn
    * sharp. */
  lazy val blur: Prop[Double] = own(new Prop[Double](0.0, r => node.setBlur(r)))
  /** A soft shadow under the box (the web and the desktop). */
  lazy val shadow: Prop[Option[Shadow]] = own(new Prop[Option[Shadow]](None, s => s match {
    case Some(value) => node.setShadow(value.x, value.y, value.blur, Component.css(value.color))
    case None => node.setShadow(0.0, 0.0, 0.0, "")
  }))
  /** An edge `borderWidth` pixels wide in `borderColor`, drawn inside the box and following its corners, so a border
    * never changes a size. (A Roku Rectangle has no edge to draw.) */
  lazy val borderColor: Prop[Color] =
    own(new Prop[Color](Color.Clear, c => node.setBorder(borderWidth(), Component.css(c))))
  lazy val borderWidth: Prop[Double] = own(new Prop[Double](0.0, w => node.setBorder(w, Component.css(borderColor()))))
  /** Rounded corners, in pixels (the Roku's Rectangle has none). */
  lazy val radius: Prop[Double] = own(new Prop[Double](0.0, r => node.setRadius(r)))
  /** In turns, as youi has it (1.0 is a full revolution). */
  lazy val rotation: Prop[Double] = own(new Prop[Double](0.0, turns => node.setRotation(turns * 2.0 * math.Pi)))
  lazy val whiteSpace: Prop[WhiteSpace] =
    own(new Prop[WhiteSpace](WhiteSpace.NoWrap, ws => node.setWhiteSpace(ws.value), () => measure.trigger()))

  lazy val effectiveWidth: Val[Double] = own(Val(size.width()))
  lazy val effectiveHeight: Val[Double] = own(Val(size.height()))

  /** The container this is in, kept by ContainerFeature. */
  val parent: Var[Option[Component]] = own(Var(None))
  /** The container this was last taken out of, and where in it: where the focus goes when it was on this as it went
    * (`Focus.successor`), to what took its place. */
  private[everyui] var leftParent: Option[Component] = None
  private[everyui] var leftIndex: Int = -1

  /** Taken out of `from`, where it was at `index`. */
  private[everyui] def leaves(from: Component, index: Int): Unit = {
    leftParent = Some(from)
    leftIndex = index
  }
  /** The position in the render root: the parents' positions added up. */
  def absoluteLeft: Double = position.left() + parent().map(_.absoluteLeft).getOrElse(0.0)
  def absoluteTop: Double = position.top() + parent().map(_.absoluteTop).getOrElse(0.0)

  /** What a pointer resting here says; nothing where there is no pointer. */
  lazy val tooltip: Prop[String] = own(new Prop[String]("", t => node.setTooltip(t)))
  /** The pointer's shape over it, by its CSS name ("col-resize", "row-resize", "pointer", "text"), where there is a
    * pointer to shape; "" leaves the platform's own. */
  lazy val cursor: Prop[String] = own(new Prop[String]("", c => node.setCursor(c)))
  /** Whether a finger dragged on it is its own to follow (a slider, a colour area), rather than scrolling or zooming the
    * page under it, which on the web would take the drag away part-way. */
  lazy val touchDrags: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setTouchDrags(d)))
  /** What this is to whatever reads the page rather than looking at it (`Semantics`): a heading, the main content, a
    * set of links. On the web it is the element's ARIA role, level and label, with a slider's value and a switch's
    * state; elsewhere, where the platform has one, its accessibility role. */
  lazy val semantics: Prop[Semantics] =
    own(new Prop[Semantics](Semantics.Plain, s => {
      node.setSemantics(s.role, s.level, s.label)
      node.setSemanticState(s.range, s.checked)
    }))
  /** Out of use for now: whatever reads the page hears so (the web's `aria-disabled`, TalkBack for a field), and the
    * platform's own field takes no typing, though the focus still reaches it. How it looks is the component's to say. */
  lazy val disabled: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setDisabled(d)))

  lazy val event: Events = new Events(this)
  /** Whether the focus can land here: by a click, `focus()`, or the arrows from a neighbour (everyui.focus.Focus). */
  lazy val focusable: Var[Boolean] = own(Var(false))
  /** Whether the focus is here: set by the focus layer as it moves (`Focus.listen`). */
  lazy val focused: Val[Boolean] = focusedNow
  private[everyui] lazy val focusedNow: Var[Boolean] = {
    val here = Var(false)
    here @= Focus.focused.get.exists(_ eq this) // read once, untracked: it follows the focus layer, not the focus
    here
  }
  /** Take the focus — unless a dialog is up and this is not in it: a screen that finishes loading behind a dialog, or
    * a reply that lands late, must not pull the focus out from under the question being asked. Nor where this is in
    * a dialog waiting under a cover (Modal.covered), which nobody can see. */
  def focus(): Unit =
    if (Focus.dialog().forall(s => Focus.within(this, s)) && !Modal.hides(this)) Focus.focused @= Some(this)

  /** Whether this handles `key` itself while focused, so the focus layer leaves it be: a text field keeps Enter and
    * the arrows along its line. */
  def keepsKey(key: Key): Boolean = false

  /** `keepsKey` with the whole press to go on: the platform's name for the key (Home, End, a letter) and the modifiers
    * held with it, as a list needs for Shift+Down or Ctrl+A. Asked just before the press reaches `event.key.down`. */
  def keepsEvent(e: KeyEvent): Boolean = keepsKey(e.key)

  /** Whether `e` is a key the platform's own field under this types with or moves its caret by (Enter in a box of
    * lines, the arrows, Backspace), while it is being edited: it reaches `event.key.down` and then the platform, untouched,
    * and the focus layer makes no use of it. Where `keepsEvent` keeps a key, the platform never sees it. */
  def typesEvent(e: KeyEvent): Boolean = false
}

object Component {
  /** A component and those it sits in, as class names from it outward (`Spinner < Container < LoadingOverlay`), six at
    * most: what a platform's diagnostics name a node by. */
  def lineage(c: Component): String = {
    var names = List(c.getClass.getSimpleName)
    var at = c.parent()
    while (at.nonEmpty && names.size < 6) {
      names = names :+ at.get.getClass.getSimpleName
      at = at.get.parent()
    }
    names.mkString(" < ")
  }

  /** While a screen is being made (its constructor's turn), what to tell of each component made: the screen keeps
    * them, so one it holds but never put on the screen (an episode box on a film's page) is taken apart with it. */
  val making: Var[Option[Component => Unit]] = Var(None)

  /** `v` kept at its value and no longer listening to what it was bound to. */
  def unbind[T](v: Val[T]): Unit = v.option.foreach(x => v.static(x))

  /** How long a turn of `disposeGradually` takes apart components for, in milliseconds, before it leaves the rest for
    * the next turn. */
  val disposeSliceMs: Double = 12.0

  /** Take `roots` and everything under them apart (`dispose`), what is deepest first, a slice of about
    * `disposeSliceMs` a turn (`Platform.later`), then run `done`. A screen of thousands of components taken apart in
    * one turn could outlast a Roku's watchdog (Execution timeout), which stopped it part of the way through and left
    * the rest bound to the theme for good. One that fails is logged and the rest are taken apart all the same. `what`
    * names it on the console: `[dispose] <what> taken apart: <n> components in <ms> ms over <turns> turns` as it
    * finishes, and a line as it starts when it will take more than a turn.
    * {{{
    *   Component.disposeGradually(List(page), () => Platform.collect(), "SettingsPage")
    * }}} */
  def disposeGradually(roots: List[Component], done: () => Unit = () => (), what: String = ""): Unit = {
    // every component under the roots, once (a screen's parts are mostly under its container too), each before those
    // under it (an array grown as it fills: a list built a component at a time is a copy per component on a Roku)
    var order = new Array[Component](64)
    var count = 0
    var pending: List[Component] = roots
    while (pending.nonEmpty) {
      val c = pending.head
      pending = pending.tail
      if (!c.takenApart && !c.listMark) {
        c.listMark = true
        if (count == order.length) {
          val bigger = new Array[Component](order.length * 2)
          var i = 0
          while (i < count) {
            bigger(i) = order(i)
            i += 1
          }
          order = bigger
        }
        order(count) = c
        count += 1
        c.childComponents.foreach(child => pending = child :: pending)
      }
    }
    // (the marks are this pass's alone: a container reads them as it places its children)
    var i = 0
    while (i < count) {
      order(i).listMark = false
      i += 1
    }
    val named = if (what.nonEmpty) what else roots.headOption.map(_.kind).getOrElse("nothing")
    disposeSlice(order, count, done, Disposal(named, count, everyui.render.Platform.ticks(), 0))
  }

  // the last `left` of `all` taken apart, from the end, for a slice of a turn; the rest on the next
  private def disposeSlice(all: Array[Component], left: Int, done: () => Unit, run: Disposal): Unit = {
    val started = everyui.render.Platform.ticks()
    val turn = run.turns + 1
    var at = left
    while (at > 0 && everyui.render.Platform.ticks() - started < disposeSliceMs) {
      at -= 1
      val c = all(at)
      try c.dispose()
      catch {
        case t: Throwable => everyui.render.Platform.log("[dispose] " + c.kind + " failed to be taken apart: " + t.toString)
      }
    }
    val rest = at
    if (rest > 0) {
      if (turn == 1) everyui.render.Platform.log("[dispose] " + run.what + " being taken apart: " + run.count.toString +
        " components, a slice a turn")
      val next = run.copy(turns = turn)
      everyui.render.Platform.later(() => disposeSlice(all, rest, done, next))
    }
    else {
      everyui.render.Platform.log("[dispose] " + run.what + " taken apart: " + run.count.toString + " components in " +
        math.round(everyui.render.Platform.ticks() - run.started).toString + " ms over " + turn.toString + " turns")
      // and, once the focus has moved on from any of them (it looks where they were, on a turn of its own), each forgets
      // where it was: one kept by something (the focus last on it) held, through its last containers, all it was in
      everyui.render.Platform.later(() => {
        var i = 0
        while (i < run.count) {
          all(i).leftParent = None
          all(i) = null
          i += 1
        }
      })
      done()
    }
  }

  /** A colour as the seam takes it: `"#rrggbb"`, `"#rrggbbaa"` when translucent, or `""` for none. */
  def css(c: Color): String = if (c.alphaInt == 0) "" else if (c.alphaInt < 255) c.toHexAlpha else c.toHex
}
