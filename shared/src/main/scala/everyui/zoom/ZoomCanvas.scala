package everyui.zoom

import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent, PointerEvent}
import everyui.list.Pointers
import everyui.render.{Gestures, Platform}
import everyui.spatial.Rect
import everyui.task.TaskInstance
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A surface that pans and zooms over a world of items (`items`, each a [[ZoomItem]]): a map, a diagram, a project
  * seen from its sections down to its symbols. Only what is in view, and wide enough on the screen to be shown, is
  * made and drawn; the rest costs a box in a list, so a world of thousands moves as a screenful does.
  *
  * The view (`view`: the world's point at the top left, and the scale) moves by
  *   - a drag with the primary button, or one finger, and the wheel (sideways with Shift, or a trackpad's two fingers);
  *   - the wheel with Ctrl (Command on a Mac), a trackpad's pinch, or two fingers pinching, zooming about where they
  *     are, within `minScale` and `maxScale`;
  *   - the keys, while it has the focus: the arrows pan (an arrow with nothing more of the world that way leaves the
  *     canvas, as the focus layer says), + and - zoom, 0 fits everything in; on a remote Fast Forward and Rewind zoom,
  *     Replay fits, and OK clicks what is in the middle of the view, which a small cross marks there;
  *   - the app: `zoomTo` a box, `zoomToItem` an item by its keys, `fit` everything, animated or at once, or `view`
  *     set.
  *
  * A click (not a drag) is `clicked`, with the item under it; `hovered` follows the pointer. `hit` answers what is at
  * any point, down to the figure of a drawing (`ZoomHit.figure`), so a drawing's shapes can be clicked. `toWorld` and
  * `toScreen` convert between the screen (the canvas's own pixels) and the world.
  *
  * {{{
  *   val map = new ZoomCanvas
  *   map.size.width @= 800.0
  *   map.size.height @= 600.0
  *   map.items @= sections.map(s => ZoomItem(s.key, s.x, s.y, s.w, s.h, () => SectionCard(s), childrenFrom = 300.0,
  *     children = () => s.modules.map(m => ZoomItem(m.key, m.x, m.y, m.w, m.h, () => ModuleCard(m), scales = false))))
  *   map.clicked.attach(p => p.hit.foreach(h => open(h.path)))
  *   map.zoomToItem("core", "network")
  * }}}
  *
  * The web, the desktop and Android pan, zoom and pinch (`Gestures.pinches`: a desktop's trackpad pinch, and two
  * fingers on a touch screen). A Roku and an Apple TV move by the remote's keys alone; what they draw of a drawing is
  * what rectangles can (`everyui.draw.Block`). */
class ZoomCanvas extends Container {
  override def kind: String = "ZoomCanvas"

  /** What is on the canvas, in the world's units. */
  val items: Var[Vector[ZoomItem]] = own(Var(Vector.empty))
  /** Where the canvas looks. */
  val view: Var[ZoomView] = own(Var(ZoomView(0.0, 0.0, 1.0)))
  val minScale: Var[Double] = own(Var(0.02))
  val maxScale: Var[Double] = own(Var(16.0))
  /** Room kept round what `fit` and `zoomTo` show, in pixels of the screen. */
  val margin: Var[Double] = own(Var(24.0))
  /** How much beyond the view is made ahead of a pan, as a share of the view's larger side. */
  val overscan: Var[Double] = own(Var(0.25))
  /** How many components that have left the view are kept, ready to come back, before the oldest are taken apart. */
  val keep: Var[Int] = own(Var(200))
  /** Whether everything is fitted in the first time there is both a size and something to show. */
  val autoFit: Var[Boolean] = own(Var(true))
  /** How long `zoomTo` and `fit` take to get there, in seconds. */
  val glide: Var[Double] = own(Var(0.35))

  /** A click on the canvas, or OK on it: where, and what is there. */
  val clicked: Channel[ZoomPoint] = Channel[ZoomPoint]
  /** What the pointer is over. */
  val hovered: Var[Option[ZoomHit]] = own(Var(None))

  /** Keys a canvas built on this one answers first (a graph's arrows): the action for a press, if it takes it. */
  var keys: KeyEvent => Option[() => Unit] = _ => None

  /** What a press with the primary button, or a finger, at the world's point takes hold of in place of a pan (a
    * graph's node, dragged), if anything. A press that does not move is still a click.
    * {{{
    *   canvas.grab = (x, y) => layout.nodeAt(x, y).map(k => ZoomGrab((mx, my) => layout.drag(k, mx, my), () => layout.drop(k)))
    * }}} */
  var grab: (Double, Double) => Option[ZoomGrab] = (_, _) => None

  /** The box everything at the top of the canvas takes up; None while there is nothing. */
  def contentBounds: Option[Rect] = bounds

  /** The world the arrows pan across and `fit` shows, where it is not what the items take up: a map's whole Earth,
    * which has no items of its own. */
  val extent: Var[Option[Rect]] = own(Var(None))

  /** What a canvas built on this one draws itself, over the items and under the focus ring, placed in the canvas's
    * own pixels as the view moves: a map's tiles, shapes and markers. */
  val layer: Container = everyui.layout.Overlaps.allow(new Container)

  // what scales, laid out in the world's units under one transform; and what is laid out at its size on the screen
  private val world = everyui.layout.Overlaps.allow(new Container)
  private val flat = everyui.layout.Overlaps.allow(new Container)
  private val ring = new Container {
    borderWidth @= 2.0
    borderColor := Theme.accent()
    visible @= false
  }
  private val reticle = new everyui.draw.Drawing

  private var roots: Vector[ZoomPlaced] = Vector.empty
  private var bounds: Option[Rect] = None
  private var shown: Vector[ZoomPlaced] = Vector.empty
  // the components made, by their items' ids, and those out of view, oldest first; and which pass this is
  private var cache: Map[String, Component] = Map.empty
  private var idle: Vector[ZoomPlaced] = Vector.empty
  private var generation = 0
  private var moving: Option[TaskInstance] = None
  private var fitted = false
  private var replanted = false

  // a drag: where the pointer went down and last was (None to take it up again after a pinch), and whether it has
  // moved far enough to be no click
  private var dragging = false
  private var pressX = 0.0
  private var pressY = 0.0
  private var last: Option[(Double, Double)] = None
  private var moved = false
  private var pinching = false
  private var pinchAt: Option[(Double, Double)] = None
  private var pointerAt: Option[(Double, Double)] = None
  private var grabbed: Option[ZoomGrab] = None

  focusable @= true
  clickFocus @= true
  touchDrags @= true
  cursor @= "default"
  world.size.width := size.width()
  world.size.height := size.height()
  // its items lie where the world puts them, scaled, past the canvas as often as in it: their words are not cut to it
  world.textRunsOn = true
  // (no size of its own, so on the web it covers nothing of the world under it: only its items take the pointer)
  flat.size.width @= 0.0
  flat.size.height @= 0.0
  ring.size.width := size.width()
  ring.size.height := size.height()
  reticle.size.width @= 17.0
  reticle.size.height @= 17.0
  reticle.position.left := (size.width() - 17.0) / 2.0
  reticle.position.top := (size.height() - 17.0) / 2.0
  reticle.figures := List(everyui.draw.Figure(everyui.draw.Path.line(8.5, 0.0, 8.5, 17.0).moveTo(0.0, 8.5)
    .lineTo(17.0, 8.5), stroke = Some(Theme.accent()), width = 2.0))
  reticle.visible @= false
  layer.size.width @= 0.0
  layer.size.height @= 0.0
  children ++= List(world, flat, layer, ring, reticle)

  // ---- the world and the view ----

  /** The world's point at `x`, `y` of the canvas (its own pixels, from its top left). */
  def toWorld(x: Double, y: Double): (Double, Double) = {
    val v = view()
    (v.x + x / v.scale, v.y + y / v.scale)
  }

  /** Where the world's point `x`, `y` is on the canvas, in its own pixels. */
  def toScreen(x: Double, y: Double): (Double, Double) = {
    val v = view()
    ((x - v.x) * v.scale, (y - v.y) * v.scale)
  }

  /** What is at `x`, `y` of the canvas (its own pixels): the topmost item shown there that `hits`. */
  def hit(x: Double, y: Double): Option[ZoomHit] = {
    val (wx, wy) = toWorld(x, y)
    shown.reverse.find(p => p.item.hits && p.rect.contains(wx, wy)).map(p => hitOn(p, wx, wy))
  }

  /** What is under a pointer event (its point is the render root's). */
  def hitAt(e: PointerEvent): Option[ZoomHit] = hit(e.x - absoluteLeft, e.y - absoluteTop)

  /** The component made for the item at `path` (its keys from the top down), where it is in view. */
  def componentAt(path: String*): Option[Component] = find(path.toList).flatMap(_.component)

  /** The box the item at `path` takes in the world. */
  def boxOf(path: String*): Option[Rect] = find(path.toList).map(_.rect)

  /** How many items are drawn now. */
  def showing: Int = shown.length

  /** Whether a press is being followed: a drag, a pinch, something grabbed. */
  def pressing: Boolean = dragging || pinching

  // how many components are made, shown or waiting
  private[everyui] def made: Int = cache.size

  /** The item at `path` made again from its `make`, the next time it shows: what it draws changed. */
  def remake(path: String*): Unit = find(path.toList).foreach { p =>
    idle = idle.filterNot(_.id == p.id)
    p.component.foreach(_.dispose())
    cache = cache - p.id
    p.component = None
    p.placed = None
    refresh()
  }

  /** The view moved to `to` (held within the scales allowed), gliding there with `animate`. */
  def moveTo(to: ZoomView, animate: Boolean = false): Unit = {
    stop()
    val held = to.copy(scale = clamp(to.scale))
    if (!animate || glide() <= 0.0) view @= held
    else moving = Some(new ZoomMove(this, held, glide()).start(ui))
  }

  /** The view fitted round `box` (in the world), with `margin` to spare, centred. */
  def zoomTo(box: Rect, animate: Boolean = true): Unit = {
    val w = size.width()
    val h = size.height()
    if (w > 0.0 && h > 0.0) {
      val room = margin()
      val scale = clamp(math.min((w - room * 2.0) / math.max(box.width, 1e-9),
        (h - room * 2.0) / math.max(box.height, 1e-9)))
      moveTo(ZoomView(box.centerX - w / 2.0 / scale, box.centerY - h / 2.0 / scale, scale), animate)
    }
  }

  /** The view fitted round the item at `path`, its keys from the top down: a section, a module in it, a file in that. */
  def zoomToItem(path: String*): Unit = find(path.toList).foreach(p => zoomTo(p.rect))

  /** Everything fitted in. */
  def fit(animate: Boolean = true): Unit = whole.foreach(b => zoomTo(b, animate))

  /** Zoomed by `factor` (above 1 closer) about `x`, `y` of the canvas, which stays where it is. */
  def zoomBy(factor: Double, x: Double, y: Double): Unit = {
    val v = view()
    val scale = clamp(v.scale * factor)
    view @= ZoomView(v.x + x / v.scale - x / scale, v.y + y / v.scale - y / scale, scale)
  }

  /** The view moved `dx`, `dy` pixels of the screen: what it shows moves the other way. */
  def panBy(dx: Double, dy: Double): Unit = {
    val v = view()
    view @= v.copy(x = v.x + dx / v.scale, y = v.y + dy / v.scale)
  }

  // what `fit` and the arrows go by
  private def whole: Option[Rect] = extent().orElse(bounds)

  private def clamp(scale: Double): Double = math.max(minScale(), math.min(maxScale(), scale))

  private def stop(): Unit = {
    moving.foreach(_.cancel())
    moving = None
  }

  // the item at `path`, placing children on the way down as needed
  private def find(path: List[String]): Option[ZoomPlaced] = {
    var at: Option[ZoomPlaced] = None
    var level = roots
    var rest = path
    var lost = path.isEmpty
    while (!lost && rest.nonEmpty) {
      val key = rest.head
      at = level.find(_.item.key == key)
      if (at.isEmpty) lost = true
      else {
        rest = rest.tail
        if (rest.nonEmpty) level = at.get.children
      }
    }
    if (lost) None else at
  }

  private def hitOn(p: ZoomPlaced, wx: Double, wy: Double): ZoomHit = {
    val scale = view().scale
    val component = p.component
    if (!p.item.scales) ZoomHit(p.path, p.item, component, (wx - p.rect.x) * scale, (wy - p.rect.y) * scale, 1.0)
    else p.item.design match {
      case Some(d) =>
        val k = fitting(p)
        ZoomHit(p.path, p.item, component, (wx - p.rect.x) / k, (wy - p.rect.y) / k, 1.0 / (k * scale))
      case None => ZoomHit(p.path, p.item, component, wx - p.rect.x, wy - p.rect.y, 1.0 / scale)
    }
  }

  // how far an item laid out at its design size is scaled to fit its box
  private def fitting(p: ZoomPlaced): Double = p.item.design match {
    case Some(d) => math.min(p.rect.width / math.max(d.width, 1e-9), p.rect.height / math.max(d.height, 1e-9))
    case None => 1.0
  }

  // ---- what is shown ----

  private def replant(): Unit = {
    roots = items().map(ZoomPlaced.root)
    bounds = Rect.around(roots.map(_.rect))
    // what waits out of view goes, as what it was made for may have changed; what shows is found again by its id, and
    // goes to wait as it leaves, if its item is gone
    idle.foreach(letGo)
    idle = Vector.empty
    replanted = true
    if (autoFit() && !fitted && bounds.nonEmpty && size.width() > 0.0 && size.height() > 0.0) {
      fitted = true
      fit(animate = false)
    }
    refresh()
  }

  /** Make and place what is in view now, and put away what is not. */
  private def refresh(): Unit = {
    val w = size.width()
    val h = size.height()
    if (w > 0.0 && h > 0.0) {
      val v = view()
      val s = v.scale
      // the world laid out in its own units, scaled about its centre (as every platform scales), and shifted so the
      // point (v.x, v.y) is at the top left
      world.scale @= s
      world.position.left @= -v.x * s + (s - 1.0) * w / 2.0
      world.position.top @= -v.y * s + (s - 1.0) * h / 2.0
      val spare = overscan() * math.max(w, h) / s
      val sight = Rect(v.x - spare, v.y - spare, w / s + spare * 2.0, h / s + spare * 2.0)
      var order: Vector[ZoomPlaced] = Vector.empty
      var pending: List[ZoomPlaced] = roots.toList
      while (pending.nonEmpty) {
        val p = pending.head
        pending = pending.tail
        if (p.rect.intersects(sight)) {
          val wide = p.rect.width * s
          if (wide >= p.item.from && p.item.until.forall(wide < _)) order = order :+ p
          if (wide >= p.item.childrenFrom) pending = p.children.toList ++ pending
        }
      }
      // each made, if it is not yet, and put in its layer before it is sized and placed, so what measures itself
      // against its size (words cut short to fit) measures on the screen
      generation += 1
      var back: Set[String] = Set.empty
      val parts = order.map { p =>
        if (p.seen != generation - 1 && idle.nonEmpty) back = back + p.id
        p.seen = generation
        p.component.getOrElse {
          val c = cache.getOrElse(p.id, {
            val fresh = p.item.make()
            cache = cache + (p.id -> fresh)
            fresh
          })
          p.component = Some(c)
          c
        }
      }
      hold(world, order.indices.filter(i => order(i).item.scales).map(i => parts(i)).toVector)
      hold(flat, order.indices.filterNot(i => order(i).item.scales).map(i => parts(i)).toVector)
      order.indices.foreach { i =>
        val p = order(i)
        val c = parts(i)
        if (!p.item.scales) {
          c.position.left @= (p.rect.x - v.x) * s
          c.position.top @= (p.rect.y - v.y) * s
          c.size.width @= p.rect.width * s
          c.size.height @= p.rect.height * s
        }
        else if (!p.placed.contains(p.rect)) {
          p.placed = Some(p.rect)
          p.item.design match {
            case Some(d) =>
              val k = fitting(p)
              c.size.width @= d.width
              c.size.height @= d.height
              c.scale @= k
              c.position.left @= p.rect.x - (1.0 - k) * d.width / 2.0
              c.position.top @= p.rect.y - (1.0 - k) * d.height / 2.0
            case None =>
              c.size.width @= p.rect.width
              c.size.height @= p.rect.height
              c.position.left @= p.rect.x
              c.position.top @= p.rect.y
          }
        }
      }
      val before = shown
      shown = order
      putAway(before, back)
    }
  }

  // a layer given `parts`, where they are not what it holds already (a pan that brings nothing new changes nothing)
  private def hold(layer: Container, parts: Vector[Component]): Unit = {
    val now = layer.children()
    var same = now.length == parts.length
    var i = 0
    while (same && i < parts.length) {
      same = now(i) eq parts(i)
      i += 1
    }
    if (!same) layer.children @= parts
  }

  // what is shown again leaves the wait; what is no longer shown joins it, oldest first; and past `keep` of them the
  // oldest are taken apart. Only what came and went is looked at, by the pass that last showed each.
  private def putAway(before: Vector[ZoomPlaced], back: Set[String]): Unit = {
    if (back.nonEmpty) idle = idle.filterNot(p => back.contains(p.id))
    val live: Set[String] = if (replanted) shown.map(_.id).toSet else Set.empty
    before.foreach(p => if (p.seen != generation && !live.contains(p.id)) idle = idle :+ p)
    replanted = false
    val over = idle.length - math.max(0, keep())
    if (over > 0) {
      idle.take(over).foreach(letGo)
      idle = idle.drop(over)
    }
  }

  // an item's component taken apart, unless it is on the screen for another (the same id, placed again)
  private def letGo(p: ZoomPlaced): Unit = {
    p.component.foreach(c => if (c.parent().isEmpty) {
      c.dispose()
      cache = cache - p.id
    })
    p.component = None
    p.placed = None
  }

  // ---- the pointer ----

  private def local(e: PointerEvent): (Double, Double) = (e.x - absoluteLeft, e.y - absoluteTop)

  private def dragTo(e: PointerEvent): Unit = if (dragging && !pinching) {
    val far = math.abs(e.x - pressX) + math.abs(e.y - pressY) > 6.0
    grabbed match {
      case Some(g) =>
        // what was grabbed follows the pointer once it is a drag, not a click
        if (far || moved) {
          val (lx, ly) = local(e)
          val (wx, wy) = toWorld(lx, ly)
          g.move(wx, wy)
        }
      case None => last match {
        case Some((lx, ly)) => if (e.x != lx || e.y != ly) panBy(lx - e.x, ly - e.y)
        case None => ()
      }
    }
    last = Some((e.x, e.y))
    if (far) moved = true
  }

  private def release(): Unit = {
    dragging = false
    last = None
    grabbed.foreach(_.drop())
    grabbed = None
  }

  private def pinch(factor: Double, x: Double, y: Double): Unit = {
    stop()
    pinching = true
    moved = true
    val here = (x - absoluteLeft, y - absoluteTop)
    pinchAt.foreach { case (px, py) => panBy(px - here._1, py - here._2) }
    zoomBy(factor, here._1, here._2)
    pinchAt = Some(here)
  }

  private def pinchEnded(): Unit = {
    pinching = false
    pinchAt = None
    // the finger still down takes the drag up again from wherever it next is
    last = None
  }

  // a pinch is heard first, so the moves of its fingers are not also a drag
  Gestures.take(node, (f, x, y) => pinch(f, x, y), () => pinchEnded())

  event.pointer.down.attach { e =>
    if (e.button == PointerEvent.PrimaryButton) {
      stop()
      dragging = true
      moved = false
      pressX = e.x
      pressY = e.y
      last = Some((e.x, e.y))
      grabbed.foreach(_.drop())
      val (lx, ly) = local(e)
      val (wx, wy) = toWorld(lx, ly)
      grabbed = grab(wx, wy)
      // followed at the root too, which hears a page's moves wherever the pointer goes
      Pointers.track(m => dragTo(m), _ => release())
    }
  }
  event.pointer.move.attach { e =>
    pointerAt = Some(local(e))
    if (dragging) dragTo(e)
    else {
      val now = hitAt(e)
      if (now.map(_.path) != hovered().map(_.path)) hovered @= now
    }
  }
  event.pointer.up.attach(_ => release())
  event.pointer.leave.attach(_ => if (!dragging) hovered @= None)
  event.pointer.wheel.attach { e =>
    // (not consumed: a wheel's notches, or a trackpad's steps, come closer together than a consumed event holds its
    // equals off (Pointer.hold), and the next the same as it would be lost; the backend keeps the wheel from what the
    // canvas is in, Gestures.take)
    stop()
    val (x, y) = pointerAt.getOrElse((size.width() / 2.0, size.height() / 2.0))
    // a notch of a mouse wheel (100 on the web, 40 on the desktop) a third or a tenth closer; a trackpad pinch,
    // many small steps; a browser that ran behind hands several notches over in one
    if (e.primary || e.ctrl) zoomBy(math.pow(2.0, -math.max(-1000.0, math.min(1000.0, e.y)) / 250.0), x, y)
    else if (e.shift && e.x == 0.0) panBy(e.y, 0.0)
    else panBy(e.x, e.y)
  }
  event.click.attach { e =>
    if (!moved) {
      val (x, y) = local(e)
      val (wx, wy) = toWorld(x, y)
      clicked @= ZoomPoint(wx, wy, hit(x, y), e.modifiers)
    }
    moved = false
  }

  // ---- the keys ----

  /** Whether the view can move `dx`, `dy` and still have more of the world that way. */
  private def more(dx: Double, dy: Double): Boolean = whole.exists { b =>
    val v = view()
    val edge = 0.5 / v.scale
    (dx < 0.0 && b.x < v.x - edge) || (dx > 0.0 && b.right > v.x + size.width() / v.scale + edge) ||
      (dy < 0.0 && b.y < v.y - edge) || (dy > 0.0 && b.bottom > v.y + size.height() / v.scale + edge)
  }

  /** Whether the arrows pan the view: not on a canvas built on this one that moves among its items with them. */
  protected def arrowsPan: Boolean = true

  // an arrow pans while there is more of the world its way, and otherwise leaves the canvas
  private def panning(dx: Double, dy: Double): Option[() => Unit] =
    if (arrowsPan && more(dx, dy)) Some(() => {
      stop()
      panBy(dx, dy)
    })
    else None

  private def action(e: KeyEvent): Option[() => Unit] = keys(e).orElse {
    val step = math.min(size.width(), size.height()) / 6.0
    val middleX = size.width() / 2.0
    val middleY = size.height() / 2.0
    val plain = !e.ctrl && !e.meta && !e.alt
    e.key match {
      case Key.ArrowLeft => panning(-step, 0.0)
      case Key.ArrowRight => panning(step, 0.0)
      case Key.ArrowUp => panning(0.0, -step)
      case Key.ArrowDown => panning(0.0, step)
      case Key.MediaFastForward => Some(() => zoomBy(1.25, middleX, middleY))
      case Key.MediaRewind => Some(() => zoomBy(0.8, middleX, middleY))
      case Key.MediaReplay => Some(() => fit())
      case _ =>
        if (plain && (e.name == "+" || e.name == "=")) Some(() => zoomBy(1.25, middleX, middleY))
        else if (plain && (e.name == "-" || e.name == "_")) Some(() => zoomBy(0.8, middleX, middleY))
        else if (plain && e.name == "0") Some(() => fit())
        else None
    }
  }

  override def keepsEvent(e: KeyEvent): Boolean = action(e).nonEmpty
  event.key.down.attach(e => action(e).foreach(f => f()))

  focused.attach { on =>
    ring.visible @= on && Platform.remoteNavigation()
    reticle.visible @= on && Platform.remoteNavigation()
  }

  view.attach(_ => refresh())
  items.attach(_ => replant())
  size.width.attach { _ =>
    node.setClip(size.width(), size.height())
    replant()
  }
  size.height.attach { _ =>
    node.setClip(size.width(), size.height())
    replant()
  }
}

object ZoomCanvas {
  /** Whether a pinch can come here: a trackpad's or two fingers' (the web, the desktop, Android with a touch screen). */
  def pinches: Boolean = Gestures.pinches()
}
