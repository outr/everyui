package everyui.component

import reactify.*
import everyui.{Color, Gradient, Shadow}
import everyui.component.feature.{MeasuredFeature, PositionFeature, SizeFeature}
import everyui.component.types.{Live, Prop, Semantics, WhiteSpace}
import everyui.event.{Events, Key, KeyEvent}
import everyui.focus.Focus
import everyui.motion.Track
import everyui.perf.Quality
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
    // taken off the screen as it is taken apart: a focus on it moves on to what is there in its place, and the
    // containers it was in no longer remember the focus on it
    parent().foreach { p =>
      leaves(p, p.childComponents.indexWhere(_ eq this))
      everyui.component.feature.ContainerFeature.forget(p, this)
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
  /** Just before the node's size is read (MeasuredFeature): what could not be measured until now (words cut short to
    * fit, made off the page) is made ready to be. */
  private[everyui] def beforeRead(): Unit = ()
  /** The room `words`, a child of this, have in their box (`TextView.textOverflow`), telling them what to watch to
    * look again (`TextView.watchesRoom`). Only a container holds anything: 0, for no limit. */
  private[everyui] def roomOf(words: TextView): Double = 0.0
  /** This, where it is a container: what holds others (the Roku tells a class by its methods, not its type). */
  private[everyui] def asContainer: Option[Container] = None
  /** This was put in a container, or taken out of one (ContainerFeature), and its width measured again
    * (MeasuredFeature): words look at their box again then. */
  private[everyui] def containerChanged(): Unit = ()
  private[everyui] def widthRemeasured(): Unit = ()
  /** How much narrower this is drawn than it would be whole, for words in it cut short to their box: what comes before
    * it in a row is kept to the room it leaves whole, so the two are not each cut for the other. */
  private[everyui] def boxShrunk: Double = 0.0
  /** What the containers this is in follow of it. */
  private[component] var containerWatches: List[ChildWatch] = Nil
  /** A mark for a pass over lists of components (ContainerFeature), cleared by the pass that set it. */
  private[everyui] var listMark: Boolean = false

  lazy val measure: Trigger = Trigger()
  lazy val measured: MeasuredFeature = new MeasuredFeature(this)
  val size: SizeFeature = new SizeFeature(this)
  // told to the screen being made, if one is (Component.making), so it can take this apart with it
  Component.making().foreach(f => f(this))
  // and to a bound list rendering, if one is (ContainerFeature.bind), so it can take apart what its render made
  if (Component.rendering(0)) Component.rendered(0) = this :: Component.rendered(0)
  lazy val position: PositionFeature = new PositionFeature(this)

  // whether it cuts what it holds to a box of its own (`clipTo`, a container's `clipsToBox`): what runs past it is cut,
  // not past the screen (`Access`'s reflow)
  private[everyui] var clips: Boolean = false
  /** What it holds cut to `width` by `height` from its top left, as a scroll's or a list's box is. */
  private[everyui] def clipTo(width: Double, height: Double): Unit = {
    clips = true
    node.setClip(width, height)
  }

  // whether `visible` has been made: one never made is shown, and a look at whether it is need not make it
  private var visibleMade = false
  private[everyui] def notHidden: Boolean = !visibleMade || visible()
  /** Whether the platform draws this at all. A component that is not shown keeps its place and its size: it is out of
    * sight, not out of the layout. */
  lazy val visible: Prop[Boolean] = {
    visibleMade = true
    own(new Prop[Boolean](true, v => {
      node.setVisible(v)
      if (collapsesMade) roomChanged()
      everyui.access.LiveRegions.changed()
    }))
  }

  private var collapsesMade = false
  /** Whether, while it is hidden (`visible` false), it takes no room either: left out of its column, row or flow and
    * out of its container's size, as a child with no size is, and back in its place as it is shown again. Off by
    * default, so a hidden child keeps its place, as something that comes and goes (a spinner, a badge) wants; on, a
    * form's field shown only later leaves no gap until it is. */
  lazy val collapses: Var[Boolean] = {
    collapsesMade = true
    val v = own(Var(false))
    v.attach(_ => roomChanged())
    v
  }

  /** Whether it takes no room now: hidden, and collapsing while hidden. */
  private[everyui] def roomless: Boolean = collapsesMade && collapses() && !notHidden

  // the containers it is in told its room changed, as a change of size tells them
  private def roomChanged(): Unit = containerWatches.foreach(w => w.container.childWatched(w, true))

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
    // none at Minimal (`everyui.perf.Quality`)
    node.setMotion(if (Quality.moves) t else Nil)
  }))
  private var ownMotion = false
  /** Whether it has a motion of its own running (`motion`): for what looks at it without making it. */
  private[everyui] def hasMotion: Boolean = ownMotion
  // whether it has had a shadow or a blur, which a change of `Quality.level` draws again (`requality`)
  private var shadowed = false
  private var blurred = false

  // whether it draws a box: a background, a gradient over it, an edge (`Container.clipsToBox`)
  private var filled = false
  private var graded = false
  private var edged = false
  private[everyui] def drawsBox: Boolean = filled || graded || edged
  lazy val color: Prop[Color] = own(new Prop[Color](Component.unsetColour, c => {
    node.setColor(c.toHex)
    colored = true
  }))
  // whether its colour was set, rather than taken from what holds it (`everyui.access.Access`)
  private[everyui] var colored = false
  // whether it draws a background colour, or a gradient (`everyui.access.Access`: what is behind words)
  private[everyui] def drawsFill: Boolean = filled
  private[everyui] def drawsGradient: Boolean = graded
  lazy val backgroundColor: Prop[Color] =
    own(new Prop[Color](Color.Clear, c => {
      node.setBackgroundColor(Component.css(c))
      filled = c.alphaInt > 0
      boxDrawn()
    }))
  /** A gradient over the whole box, on top of the background colour, linear or radial (`Gradient.radial`, drawn again
    * as the box changes size); a backend that draws none fills with its first stop. */
  lazy val gradient: Prop[Option[Gradient]] = own(new Prop[Option[Gradient]](None, g => {
    if (g.exists(_.radial.nonEmpty)) sized()
    drawGradient()
    graded = g.nonEmpty
    boxDrawn()
  }))
  private def drawGradient(): Unit = gradient() match {
    case Some(value) => value.radial match {
      case Some(r) =>
        val (x, y, radius) = Component.circle(r, size.width(), size.height())
        node.setRadialGradient(x, y, radius, size.width(), size.height(), value.encoded)
      case None => node.setGradient(value.angle, value.encoded)
    }
    case None => node.setGradient(0.0, "")
  }
  /** How much of it shows, by the alpha of a gradient over it (`Gradient`, linear or radial): where the gradient is
    * opaque it is drawn, where it is clear nothing is, the box's children with it (CSS's `mask-image`): a field of
    * dots fading out from a point. Only the web masks; elsewhere it is drawn whole. */
  lazy val mask: Prop[Option[Gradient]] = own(new Prop[Option[Gradient]](None, g => {
    if (g.exists(_.radial.nonEmpty)) sized()
    drawMask()
  }))
  private def drawMask(): Unit = mask() match {
    case Some(value) => value.radial match {
      case Some(r) =>
        val (x, y, radius) = Component.circle(r, size.width(), size.height())
        node.setMask(0.0, x, y, radius, value.encoded)
      case None => node.setMask(value.angle, 0.0, 0.0, -1.0, value.encoded)
    }
    case None => node.setMask(0.0, 0.0, 0.0, -1.0, "")
  }
  // a radial gradient or mask is drawn in pixels: drawn again as the box changes size, once one is set
  private var sizing = false
  private def sized(): Unit = if (!sizing) {
    sizing = true
    val again = () => {
      if (gradient().exists(_.radial.nonEmpty)) drawGradient()
      if (mask().exists(_.radial.nonEmpty)) drawMask()
    }
    size.width.attach(_ => again())
    size.height.attach(_ => again())
  }
  /** What shows through it blurred, a gaussian's deviation in pixels, while its own content stays sharp: a pane of
    * frosted glass over what is behind it (CSS's `backdrop-filter`, Flutter's `BackdropFilter`). Its background should
    * be translucent for anything to show. Only the web blurs behind; elsewhere what is behind shows sharp, so a design
    * that needs the glass to read gives the pane a fill that reads without it. None below Full
    * (`everyui.perf.Quality`). */
  lazy val backdropBlur: Prop[Double] = own(new Prop[Double](0.0, _ => {
    blurred = true
    drawBlur()
  }))
  /** How far it is blurred: a gaussian's deviation in pixels, as the web's `filter: blur()` takes it; 0 is sharp. The
    * web, the desktop and Android draw it (before Android 12 only on a container or a picture); elsewhere it is drawn
    * sharp. */
  lazy val blur: Prop[Double] = own(new Prop[Double](0.0, r => {
    blurred = true
    drawBlur()
  }))
  /** A soft shadow under the box (the web and the desktop; Android's elevation). */
  lazy val shadow: Prop[Option[Shadow]] = own(new Prop[Option[Shadow]](None, _ => {
    shadowed = true
    drawShadow()
  }))
  /** More shadows under the box, layered under `shadow` (the first on top, as CSS lists them): a card's tight shadow
    * and its wide soft one, each with its own spread. The web draws every layer; elsewhere the first of `shadow` and
    * these is drawn, as `Shadow` says. */
  lazy val shadows: Prop[List[Shadow]] = own(new Prop[List[Shadow]](Nil, _ => {
    shadowed = true
    drawShadow()
  }))
  // none below Full (`everyui.perf.Quality`)
  private def drawBlur(): Unit = {
    node.setBlur(if (Quality.effects) blur() else 0.0)
    if (backdropBlur() > 0.0 || backdropBlurred) {
      backdropBlurred = true
      node.setBackdropBlur(if (Quality.effects) backdropBlur() else 0.0)
    }
  }
  private var backdropBlurred = false
  private def drawShadow(): Unit = {
    val all = if (Quality.effects) shadow().toList ++ shadows() else Nil
    all.headOption match {
      case Some(value) => node.setShadow(value.x, value.y, value.blur, Component.css(value.color))
      case None => node.setShadow(0.0, 0.0, 0.0, "")
    }
    // layers, or a spread: what only the web draws, over the first
    if (all.size > 1 || all.exists(_.spread != 0.0)) node.setShadowLayers(Shadow.encode(all))
  }
  /** The change of `Quality.level` it was last drawn at: one made before a change and put on the screen after it is
    * drawn again as it goes on (ContainerFeature). */
  private[everyui] var drawnAt: Int = Quality.changes
  /** Its shadow, blur and motion drawn again as `Quality.level` now has them (`Component.requality`). */
  private[everyui] def requality(): Unit = {
    if (blurred) drawBlur()
    if (shadowed) drawShadow()
    if (ownMotion) node.setMotion(if (Quality.moves) motion() else Nil)
  }
  /** An edge `borderWidth` pixels wide in `borderColor`, drawn inside the box and following its corners, so a border
    * never changes a size. (A Roku Rectangle has no edge to draw.) */
  lazy val borderColor: Prop[Color] =
    own(new Prop[Color](Color.Clear, c => paintEdge(borderWidth(), c)))
  lazy val borderWidth: Prop[Double] = own(new Prop[Double](0.0, w => paintEdge(w, borderColor())))
  private def paintEdge(width: Double, color: Color): Unit = {
    node.setBorder(width, Component.css(color))
    edged = width > 0.0 && color.alphaInt > 0
    boxDrawn()
  }
  /** It has drawn a box, or stopped: a container clips what it holds to one (`Container.clipsToBox`). */
  private[everyui] def boxDrawn(): Unit = ()
  /** Rounded corners, in pixels (the Roku's Rectangle has none). */
  lazy val radius: Prop[Double] = own(new Prop[Double](0.0, r => node.setRadius(r)))
  /** In turns, as youi has it (1.0 is a full revolution). */
  lazy val rotation: Prop[Double] = own(new Prop[Double](0.0, turns => node.setRotation(turns * 2.0 * math.Pi)))
  lazy val whiteSpace: Prop[WhiteSpace] =
    own(new Prop[WhiteSpace](WhiteSpace.NoWrap, ws => node.setWhiteSpace(ws.value), () => measure.trigger()))

  // whether `flex` has been made: one never made is 0, and a layout looking at every child's need not make it
  private var flexMade = false
  /** Its share of the room left in the row or column it is in (`HorizontalLayout`, `VerticalLayout`). At 0, the
    * default, it keeps its own size. Above 0, where the row's width or the column's height is its own (set, or bound to
    * what it sits in, rather than following what it holds), the others keep their sizes and this is given what they
    * leave, less the padding and the gaps: a body under a header, as tall as the panel has room for below it, that
    * shrinks as the header grows. Children with a `flex` share the room in proportion (1 and 2 take a third and two
    * thirds), and none is made smaller than nothing: where the others leave no room, it has none. Where the row or
    * column is sized to what it holds, there is nothing left over, and this keeps the size it has. The layout sets its
    * size along the row or column, replacing what it was bound to; across it, its size is its own as before. */
  lazy val flex: Var[Double] = {
    flexMade = true
    val share = own(Var(0.0))
    share.attach(_ => parent().flatMap(_.asContainer).foreach(_.layoutFor("flex")))
    share
  }
  /** Its `flex`, 0 where it has none, without making one. */
  private[everyui] def flexShare: Double = if (flexMade) flex() else 0.0
  /** Whether it is meant to lie over what is beside it (`everyui.layout.Overlaps.allow`): a dialog's sheet, a badge. */
  private[everyui] var overlays: Boolean = false
  /** Whether it draws its focus in a way of its own, where the focus layer would draw a ring round it
    * (`everyui.focus.Focus.ownRing`). */
  private[everyui] var drawsOwnFocus: Boolean = false
  /** Whether Tab reaches it before everything else in its tree, wherever it is (`everyui.kit.SkipLink`). */
  private[everyui] var tabFirst: Boolean = false
  /** Whether a click on it is heard (`event.click`). */
  private[everyui] var clickHeard: Boolean = false
  /** The rules it is marked as meeting in its own way (`everyui.access.Access.allow`). */
  private[everyui] var accessAllowed: List[everyui.access.AccessRule] = Nil

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
  /** What whatever reads the page hears of it after its name (a list row's tip): the web's `aria-describedby`, TalkBack's
    * hint; nothing where the platform has no reader of descriptions. */
  lazy val accessibleDescription: Prop[String] = own(new Prop[String]("", _ => describe()))
  // its description and, after it, what is wrong with it (`invalid`)
  private def describe(): Unit =
    node.setDescription(List(accessibleDescription(), invalid()).filter(_.nonEmpty).mkString(". "))
  /** The pointer's shape over it, by its CSS name ("col-resize", "row-resize", "pointer", "text"), where there is a
    * pointer to shape; "" leaves the platform's own. */
  lazy val cursor: Prop[String] = own(new Prop[String]("", c => node.setCursor(c)))
  /** Whether a finger dragged on it is its own to follow (a slider, a colour area), rather than scrolling or zooming the
    * page under it, which on the web would take the drag away part-way. */
  lazy val touchDrags: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setTouchDrags(d)))
  /** Whether a finger's tap a little outside it is still its own, where it is smaller than a fingertip
    * (`everyui.event.TapSlop`): a glyph button, a check box, a chip. It is drawn and laid out at its own size, a
    * mouse's clicks land where they did, and where two such reaches overlap the nearer centre takes the tap. On the
    * web; elsewhere the platform's own touch rules hold. */
  lazy val tapSlop: Prop[Boolean] = own(new Prop[Boolean](false, s => everyui.render.Gestures.slop(node, s)))
  /** Whether its words, and those of everything in it, are there to be read and copied: a drag across them selects
    * them (across lines and on into the next such component, as a chat's messages), Ctrl or Command+C copies them, and
    * a press there is not a list's row dragged (`Pointer.onText`). What answers a click inside it (a button, a chip)
    * stays unselectable, and a plain click does what it did. Where the platform lets people select text (the web,
    * `Platform.selectsText`); elsewhere it changes nothing. */
  lazy val selectable: Prop[Boolean] = own(new Prop[Boolean](false, s => node.setSelectable(s)))
  /** What this is when words selected across it are copied: an HTML block's tag — "p", "h1" to "h6", "pre",
    * "blockquote", "ul", "ol" ("ol 3" numbered from 3), "li", "hr", "table", "tr", "th" or "td" — or "none" to be left
    * out (a list's bullet, a code block's Copy button). Where a selection touches such components, the web copies it
    * as clean HTML of those blocks, with the bold, slant, code and links of their words and nothing of their look, and
    * as plain prose (`Clipboard.selection`), in place of the page's own styled copy; "" (the default) is a component
    * with nothing to say, whose words go as a paragraph of their own. Elsewhere no text is selected, and it changes
    * nothing. */
  lazy val copyAs: Prop[String] = own(new Prop[String]("", tag => node.setCopyAs(tag)))
  /** What this is to whatever reads the page rather than looking at it (`Semantics`): a heading, the main content, a
    * set of links. On the web it is the element's ARIA role, level and label, with a slider's value and a switch's
    * state; elsewhere, where the platform has one, its accessibility role. */
  lazy val semantics: Prop[Semantics] = own(new Prop[Semantics](Semantics.Plain, s => said(s)))
  private def said(s: Semantics): Unit = {
    node.setSemantics(if (s.role.isEmpty) impliedRole else s.role, s.level, s.label)
    node.setSemanticState(s.range, s.checked, s.expanded, s.selected, s.sort, s.current)
  }
  /** The role it has with no `semantics` of its own: a button, where it is an app's own (a plain `Container`, or a
    * kind of the app's), takes the focus, hears a click and takes no typing. EveryUI's own kinds say what they are. */
  private[everyui] def impliedRole: String =
    if (clickHeard && focusable() && !takesTyping && Component.appMade(this)) "button" else ""
  /** Its role said to the platform as it is now: for a kind whose role it has without being given one (a list). */
  private[everyui] def sayRole(): Unit = said(semantics())
  // whether the role is followed as `focusable` changes: once a click is heard
  private var implying = false
  /** A click is heard on it (`event.click`): it may be a button now, and is told again as it takes the focus or no
    * longer does. */
  private[everyui] def heardClick(): Unit = {
    clickHeard = true
    if (!implying && Component.appMade(this)) {
      implying = true
      focusable.attach(_ => said(semantics()))
      said(semantics())
    }
  }
  /** Out of use for now: whatever reads the page hears so (the web's `aria-disabled`, TalkBack for a field), and the
    * platform's own field takes no typing, though the focus still reaches it. How it looks is the component's to say. */
  lazy val disabled: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setDisabled(d)))
  /** What is wrong with what it holds ("Enter an email address"), "" while nothing is: whatever reads the page hears
    * that it is invalid, and why (the web's `aria-invalid` and its description, TalkBack's error, the desktop's help
    * text), and the words are read out at once as they come while the focus is in it. How it looks is the
    * component's to say (a `TextInput` draws its edge and its label in the theme's danger), and the words are to be
    * shown too, for those who see the screen: a `Notice` beside it, which reads them out wherever the focus is. */
  lazy val invalid: Prop[String] = own(new Prop[String]("", m => {
    node.setInvalid(m.nonEmpty)
    describe()
    if (m.nonEmpty && Focus.focused().exists(f => Focus.within(f, this)))
      everyui.access.LiveRegions.say(m, Live.Assertive)
  }))
  /** Being updated (a list being filled, a result being worked out): whatever reads the page is told it is not done
    * (the web's `aria-busy`, TalkBack's and the desktop's "busy"), and what it says as a live part (`liveRegion`) is
    * held until it is. */
  lazy val updating: Prop[Boolean] = own(new Prop[Boolean](false, u => {
    node.setUpdating(u)
    everyui.access.LiveRegions.changed()
  }))
  /** Whether what it says is read out as it changes, as a status line, a count of results or an error is (`Live`): its
    * name to a screen reader (its label, else the words inside it) as it comes on the screen with something to say,
    * and again each time that changes. Off by default. */
  lazy val liveRegion: Prop[Live] = own(new Prop[Live](Live.Off, l => everyui.access.LiveRegions.watch(this, l)))
  // what it said last as a live part (`LiveRegions`)
  private[everyui] var liveSaid: String = ""

  lazy val event: Events = new Events(this)
  /** Whether the focus can land here: by `focus()`, the arrows from a neighbour or Tab (everyui.focus.Focus), and a
    * click where a remote moves the focus or this takes it from a click (`clickFocus`). */
  lazy val focusable: Var[Boolean] = own(Var(false))
  /** Whether the focus is here: set by the focus layer as it moves (`Focus.listen`). */
  lazy val focused: Val[Boolean] = focusedNow
  private[everyui] lazy val focusedNow: Var[Boolean] = {
    val here = Var(false)
    here @= Focus.focused.get.exists(_ eq this) // read once, untracked: it follows the focus layer, not the focus
    here
  }
  /** Whether the focus here is to be drawn: always, where a remote moves it; elsewhere only while the keys are moving
    * it (`Focus.visible`), as a browser draws `:focus-visible`, so a button clicked is not left lit. A ring, a border
    * or a fill that shows the focus follows this; a field, whose caret shows however it was focused, follows
    * `focused`. */
  lazy val focusShown: Val[Boolean] = if (everyui.render.Platform.remoteNavigation()) focused else focusShownNow
  // set by the focus layer as the focus or the keys' use of it changes, once something has asked for it
  private[everyui] var showsFocus: Boolean = false
  private[everyui] lazy val focusShownNow: Var[Boolean] = {
    val here = Var(false)
    here @= Focus.focused.get.exists(_ eq this) && Focus.visible.get // read once, untracked, as `focusedNow` is
    showsFocus = true
    here
  }
  /** Whether a click or a tap gives this the focus where a pointer moves it (no remote): set by what is typed into or
    * worked by the keys (a field, an editor, a list, a table, a slider, a map). A button, a chip, a tab or a panel
    * clicked takes no focus, and the click takes it from where it was. A remote's platform focuses whatever is
    * clicked, as ever. */
  lazy val clickFocus: Var[Boolean] = own(Var(false))
  // whether a click on it leaves the focus where it is, rather than letting it go: a split's handle, clicked beside a
  // field being typed in, takes nothing from it
  private[everyui] var clickLeavesFocus: Boolean = false
  /** Whether this is typed into: a field, an editor. Where a pointer moves the focus, a screen starts with the focus
    * on its `defaultFocus` only when that is typed into, and keys pressed in it are not the keys moving the focus. */
  def takesTyping: Boolean = false
  /** Take the focus — unless a dialog is up and this is not in it: a screen that finishes loading behind a dialog, or
    * a reply that lands late, must not pull the focus out from under the question being asked. Nor where the focus is
    * confined and this is outside the region (`Focus.confine`), nor in a dialog waiting under a cover (Modal.covered),
    * which nobody can see. Asked again where it has the focus already, it takes the platform's focus again too
    * (`refocus`). */
  def focus(): Unit = if (Focus.admits(this) && !Modal.hides(this)) {
    if (Focus.focused().exists(_ eq this)) refocus() else Focus.focused @= Some(this)
  }

  /** The platform's own focus put back on this, which has the focus layer's (`Focus.focused`): a field's caret, lost
    * as its node was taken off the page and put back, or asked for again by `focus()`. Nothing, where the platform
    * keeps no focus of its own on it. */
  private[everyui] def refocus(): Unit = ()

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
  // a colour not yet set: black to whatever reads it, but not `Color.Black`, so setting black is a change and is drawn
  // (where the words would otherwise keep the colour they take from what holds them)
  private[everyui] val unsetColour: Color = new Color(0, 0, 0, 254)

  /** Whether `c` is an app's own: a plain `Container` it made, or a kind of its own; not one of EveryUI's kinds, which
    * say for themselves what they are. */
  def appMade(c: Component): Boolean = {
    val name = c.getClass.getName
    name == "everyui.component.Container" || !name.startsWith("everyui.")
  }

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

  // while a bound list renders (ContainerFeature.bind): the components made meanwhile, newest first. Arrays of one, not
  // a Var, whose previous value would hold what was made, nor a `var` here, which a Roku may not be able to set
  private[everyui] val rendering: Array[Boolean] = Array(false)
  private[everyui] val rendered: Array[List[Component]] = Array(Nil)

  /** What is under `root` (and `root` itself) drawn again as `Quality.level` now has it: its shadows, blurs and motion.
    * What was drawn at this level already is passed over, with what is under it. Walked with a list rather than by
    * recursion (a Roku's stack is small). */
  def requality(root: Component): Unit = {
    val now = Quality.changes
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = pending.tail
      if (at.drawnAt != now) {
        at.drawnAt = now
        at.requality()
        pending = at.childComponents.toList ::: pending
      }
    }
  }

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
  /** A radial shape in a box `width` by `height`: its centre and its radius, in pixels. */
  def circle(r: everyui.RadialShape, width: Double, height: Double): (Double, Double, Double) =
    (r.x * width, r.y * height, r.radius * math.min(width, height))

  def css(c: Color): String = if (c.alphaInt == 0) "" else if (c.alphaInt < 255) c.toHexAlpha else c.toHex
}
