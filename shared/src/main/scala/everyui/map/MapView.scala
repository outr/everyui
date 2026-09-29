package everyui.map

import everyui.{Color, Shadow}
import everyui.component.{Component, Container, IconButton, Link, TextView}
import everyui.font.MaterialIcon
import everyui.map.MapFeature.{ClusterFeature, ShapeFeature}
import everyui.render.Platform
import everyui.spatial.Rect
import everyui.theme.Theme
import everyui.zoom.{ZoomCanvas, ZoomView}
import reactify.*

/** A map that pans and zooms, as OpenStreetMap's does: tiles from a tile server (`tiles`), polygons and lines in
  * latitude and longitude over them (`shapes`), and markers that keep their own size at every zoom (`markers`),
  * gathered into clusters where they crowd (`cluster`). It is drawn in Web Mercator (`WebMercator`) at zooms from
  * `minZoom` to `maxZoom` (0, the whole Earth in one tile, to 19, a street's doors), on a [[ZoomCanvas]] (`canvas`),
  * which moves it:
  *   - a drag, or one finger, pans, and so does the wheel;
  *   - the wheel with Ctrl (Command on a Mac), a trackpad's pinch, two fingers, and the + and - buttons zoom, about
  *     where they are;
  *   - with the focus on it, the arrows pan and + and - zoom; on a remote Fast Forward and Rewind zoom and OK clicks
  *     the middle;
  *   - the app: `setView` a place and a zoom, `fitBounds` a box, gliding there with `animate`.
  *
  * Where it looks is `center`, `zoom` and `bounds`, which follow it. A click is `clicked`, with the place and the
  * feature there (a marker, a shape, a cluster), and `hovered` follows the pointer; a feature with a `label` shows it
  * in a tip as the pointer rests on it (`tips`). In its corners are the tiles' credit (their `attribution`, which
  * OpenStreetMap's licence requires), a scale bar (`scaleBar`, metric or `imperial`), the zoom buttons
  * (`zoomButtons`), and the app's `legend` in `legendCorner`.
  *
  * {{{
  *   val map = new MapView
  *   map.size.width := panel.size.width()
  *   map.size.height @= 420.0
  *   map.tiles @= Some(TileSource.openStreetMap)
  *   map.shapes @= parcels.map(p => MapShape.polygon(p.id, p.ring, MapStyle(fill = Some(teal.withAlpha(0.35)),
  *     stroke = Some(teal)), label = p.name))
  *   map.markers @= wells.map(w => MapMarker.pin(w.id, w.at, label = w.name))
  *   map.clicked.attach(c => c.feature.foreach(f => open(f.key)))
  *   LatLngBounds.around(parcels.flatMap(_.ring)).foreach(b => map.fitBounds(b, 32.0))
  * }}}
  *
  * Tiles load only for what is in view and a little round it, nearest the middle first and a few at a time; one not
  * yet come shows the tile above it drawn larger, and one scrolled past before it was asked for is never asked for.
  * Shapes are drawn in pixels of the screen for the view and some way round it, and markers are made only as they come
  * into view, so a pan within that only moves them.
  *
  * The web, the desktop and Android draw all of it. A Roku and an Apple TV draw the tiles, the markers that are
  * components (a `MapMarker.dot`, words, the app's own), and of the shapes and a pin only what rectangles can
  * (`everyui.draw.Block`), and move by the remote's keys. */
class MapView extends Container {
  override def kind: String = "MapView"

  /** The surface that pans and zooms, and takes the focus. */
  val canvas: ZoomCanvas = new ZoomCanvas

  /** Where the tiles come from; None draws none. */
  val tiles: Var[Option[TileSource]] = own(Var(None))
  /** The polygons and lines, drawn in order (the last on top). */
  val shapes: Var[Vector[MapShape]] = own(Var(Vector.empty))
  /** The markers, drawn in order over the shapes. */
  val markers: Var[Vector[MapMarker]] = own(Var(Vector.empty))
  /** How markers that crowd are gathered; None draws each. */
  val cluster: Var[Option[MapCluster]] = own(Var(None))
  /** What the app shows over a corner of the map (`legendCorner`): a key to its colours, a switch of layers. */
  val legend: Var[Option[Component]] = own(Var(None))
  val legendCorner: Var[MapCorner] = own(Var(MapCorner.TopLeft))
  /** Whether the + and - buttons show, in the top right corner. */
  val zoomButtons: Var[Boolean] = own(Var(true))
  /** Whether the scale bar shows, in the bottom left corner, and in feet and miles rather than metres. */
  val scaleBar: Var[Boolean] = own(Var(true))
  val imperial: Var[Boolean] = own(Var(false))
  /** Whether a feature's label shows as the pointer rests on it. */
  val tips: Var[Boolean] = own(Var(true))
  /** How far out and in the map goes. */
  val minZoom: Var[Double] = own(Var(0.0))
  val maxZoom: Var[Double] = own(Var(19.0))
  /** How far round the view tiles are loaded ahead of a pan, in pixels. */
  val tileMargin: Var[Double] = own(Var(128.0))
  /** How many tiles are on their way at once. */
  val tileLoads: Var[Int] = own(Var(6))
  /** How many tiles that came are kept, drawn or not, before the longest unused are let go. */
  val tileCache: Var[Int] = own(Var(256))
  /** How many markers' components that have left the view are kept, ready to come back. */
  val keep: Var[Int] = own(Var(300))
  /** How near a line the pointer finds it, in pixels of the screen. */
  val slop: Var[Double] = own(Var(4.0))

  /** A click on the map, or OK on it: the place, and what is there. */
  val clicked: Channel[MapClick] = Channel[MapClick]
  /** What the pointer is over. */
  val hovered: Var[Option[MapFeature]] = own(Var(None))

  /** The zoom the map is at (fractional between the whole ones). */
  val zoom: Val[Double] = own(Val(WebMercator.zoomOf(canvas.view().scale)))
  /** The place in the middle of the view. */
  val center: Val[LatLng] = own(Val {
    val v = canvas.view()
    WebMercator.toLatLng(v.x + size.width() / 2.0 / v.scale, v.y + size.height() / 2.0 / v.scale)
  })
  /** The box of the Earth in view. */
  val bounds: Val[LatLngBounds] = own(Val {
    val v = canvas.view()
    val nw = WebMercator.toLatLng(v.x, v.y)
    val se = WebMercator.toLatLng(v.x + size.width() / v.scale, v.y + size.height() / v.scale)
    LatLngBounds(se.lat, nw.lng, nw.lat, se.lng)
  })

  private val tileLayer = new TileLayer(this)
  private val shapeLayer = new ShapeLayer
  private val markerLayer = new MarkerLayer(this)
  private var frame: Option[MapFrame] = None
  // what the app asked for before the map had a size, done once it has one
  private var pending: Option[() => Unit] = None
  // the legend over the map now, and the link round the credit (a Roku keeps no Option of an Option, which `changes`
  // would hold)
  private var legendShown: Option[Component] = None
  private var creditLink: Option[Link] = None

  private val zoomIn = new IconButton(MaterialIcon.Add, "Zoom in", () => zoomBy(1.0))
  private val zoomOut = new IconButton(MaterialIcon.Remove, "Zoom out", () => zoomBy(-1.0))
  private val buttons = new Container {
    size.width @= 36.0
    size.height @= 68.0
    radius @= 6.0
    backgroundColor := Theme.surface()
    borderWidth @= 1.0
    borderColor := Theme.border()
    shadow @= Some(Shadow(0.0, 1.0, 4.0, Color.Black.withAlpha(0.25)))
  }
  private val credit = TextView.bound(tiles().map(_.attribution).getOrElse(""), 11.0, Theme.text())
  private val creditBox = new Container
  private val ruler = new ScaleBar
  private val tipWords = TextView("", 12.0, Theme.text())
  private val tip = new Container {
    radius @= 4.0
    backgroundColor := Theme.raised()
    shadow @= Some(Shadow(0.0, 2.0, 6.0, Color.Black.withAlpha(0.3)))
    visible @= false
  }

  canvas.autoFit @= false
  canvas.extent @= Some(Rect(0.0, 0.0, WebMercator.size, WebMercator.size))
  canvas.size.width := size.width()
  canvas.size.height := size.height()
  canvas.minScale := WebMercator.scaleOf(minZoom())
  canvas.maxScale := WebMercator.scaleOf(maxZoom())
  canvas.backgroundColor := Theme.surface()
  canvas.layer.children ++= List[Component](tileLayer.layer, shapeLayer.drawing, markerLayer.layer)

  zoomIn.position.left @= 2.0
  zoomIn.position.top @= 2.0
  zoomOut.position.left @= 2.0
  zoomOut.position.top @= 34.0
  buttons.children ++= List(zoomIn, zoomOut)
  buttons.position.left := size.width() - 46.0
  buttons.position.top @= 10.0
  buttons.visible := zoomButtons()

  credit.position.left @= 6.0
  credit.position.top @= 3.0
  creditBox.size.width := credit.size.width() + 12.0
  creditBox.size.height := credit.size.height() + 6.0
  creditBox.backgroundColor := Theme.surface().withAlpha(0.8)
  creditBox.position.left := size.width() - creditBox.size.width()
  creditBox.position.top := size.height() - creditBox.size.height()
  creditBox.visible := tiles().exists(_.attribution.nonEmpty)
  tiles.attach { t =>
    // the words out of what held them before they go into what holds them now
    creditBox.children @= Vector.empty
    creditLink.foreach { l =>
      l.children -= credit
      l.dispose()
    }
    creditLink = t.flatMap(_.link).map(href => Link(href, newTab = true)(credit))
    creditBox.children @= Vector(creditLink.getOrElse(credit))
  }
  creditBox.children += credit

  ruler.position.left @= 10.0
  ruler.position.top := size.height() - ruler.size.height() - 10.0
  ruler.visible := scaleBar()

  tipWords.position.left @= 8.0
  tipWords.position.top @= 5.0
  tip.size.width := tipWords.size.width() + 16.0
  tip.size.height := tipWords.size.height() + 10.0
  tip.children += tipWords

  children ++= List[Component](canvas, ruler, creditBox, buttons, tip)

  // ---- where it looks ----

  /** The view moved to `at`, at `zoom` (held within `minZoom` and `maxZoom`), gliding there with `animate`. Asked
    * before the map has a size, it is done as soon as it has one. */
  def setView(at: LatLng, zoom: Double, animate: Boolean = false): Unit = whenSized { () =>
    val (x, y) = WebMercator.toWorld(at)
    lookAt(x, y, zoom, animate)
  }

  /** The view fitted round `box`, with `padding` pixels to spare on every side, as close as `maxZoom` allows (a single
    * place is shown at it). */
  def fitBounds(box: LatLngBounds, padding: Double = 24.0, animate: Boolean = true): Unit = whenSized { () =>
    val z = WebMercator.zoomToFit(box, size.width(), size.height(), padding, minZoom(), maxZoom())
    val world = WebMercator.boxOf(box)
    lookAt(world.centerX, world.centerY, z, animate)
  }

  /** Zoomed in (or, below 0, out) by `levels`, about the middle, to the nearest whole zoom. */
  def zoomBy(levels: Double): Unit =
    lookAt(canvas.view().x + size.width() / 2.0 / canvas.view().scale,
      canvas.view().y + size.height() / 2.0 / canvas.view().scale, math.floor(zoom() + levels + 0.5), animate = true)

  /** Where `p` is on the map, in its own pixels from its top left. */
  def toScreen(p: LatLng): (Double, Double) = {
    val (x, y) = WebMercator.toWorld(p)
    canvas.toScreen(x, y)
  }

  /** The place at `x`, `y` of the map (its own pixels). */
  def toLatLng(x: Double, y: Double): LatLng = {
    val (wx, wy) = canvas.toWorld(x, y)
    WebMercator.toLatLng(wx, wy)
  }

  /** What is at `x`, `y` of the map (its own pixels): the topmost marker or cluster there, or else the topmost shape
    * (inside a polygon, or within `slop` pixels of a line) that `hits`. */
  def featureAt(x: Double, y: Double): Option[MapFeature] = frame.flatMap { _ =>
    markerLayer.hit(x - canvas.layer.position.left(), y - canvas.layer.position.top()).orElse {
      val (wx, wy) = canvas.toWorld(x, y)
      shapeLayer.hit(wx, wy, 1.0 / canvas.view().scale, slop(), zoom()).map(s => ShapeFeature(s))
    }
  }

  // for a look (the specs): how many shapes the view draws, and markers and clusters it shows; how many tiles are known
  // (come, failed, on their way or waiting), on their way or waiting, and drawn now
  private[everyui] def drawnShapes: Int = shapeLayer.drawn
  private[everyui] def shownMarkers: Int = markerLayer.showing
  private[everyui] def tilesHeld: Int = tileLayer.held
  private[everyui] def tilesInFlight: Int = tileLayer.inFlight
  private[everyui] def tilesWaiting: Int = tileLayer.waiting
  private[everyui] def markersMade: Int = markerLayer.madeCount
  private[everyui] def tilesDrawn: Vector[MapTile] = tileLayer.drawn

  private def lookAt(x: Double, y: Double, z: Double, animate: Boolean): Unit = {
    val s = WebMercator.scaleOf(math.max(minZoom(), math.min(maxZoom(), z)))
    canvas.moveTo(ZoomView(x - size.width() / 2.0 / s, y - size.height() / 2.0 / s, s), animate)
  }

  private def whenSized(f: () => Unit): Unit =
    if (size.width() > 0.0 && size.height() > 0.0) f()
    else pending = Some(f)

  // ---- what is drawn ----

  private def refresh(): Unit = {
    val w = size.width()
    val h = size.height()
    if (w > 0.0 && h > 0.0) {
      pending.foreach { f =>
        pending = None
        f()
      }
      val v = canvas.view()
      val s = v.scale
      val z = WebMercator.zoomOf(s)
      val sight = Rect(v.x, v.y, w / s, h / s)
      // a pan within the frame moves it; a zoom, or a pan past it, lays out a new one round the view
      val f = frame match {
        case Some(f) if f.scale == s && f.world.contains(sight.x, sight.y) &&
          f.world.contains(sight.right, sight.bottom) => f
        case _ =>
          val spare = math.max(w, h) / 2.0
          val fresh = MapFrame(v.x - spare / s, v.y - spare / s, s, w + spare * 2.0, h + spare * 2.0)
          frame = Some(fresh)
          shapeLayer.render(fresh, z)
          markerLayer.place(fresh, z)
          fresh
      }
      canvas.layer.position.left @= math.floor((f.x - v.x) * s + 0.5)
      canvas.layer.position.top @= math.floor((f.y - v.y) * s + 0.5)
      tileLayer.update(f, sight, z)
      val middle = WebMercator.lat(v.y + h / 2.0 / s)
      ruler.show(WebMercator.metersPerPixel(middle, z), imperial())
    }
  }

  private def redraw(): Unit = {
    frame = None
    refresh()
  }

  // the corner the legend sits in, clear of what else is there
  private def placeLegend(c: Component): Unit = {
    c.position.left := {
      if (legendCorner().right) size.width() - c.size.width() - 10.0 - (if (!legendCorner().bottom && zoomButtons()) 46.0 else 0.0)
      else 10.0
    }
    c.position.top := {
      if (legendCorner().bottom) size.height() - c.size.height() - 10.0 - (if (legendCorner().right) creditBox.size.height()
        else if (scaleBar()) ruler.size.height() + 6.0 else 0.0)
      else 10.0
    }
  }

  // ---- the pointer ----

  private def hover(x: Double, y: Double): Unit = {
    val now = featureAt(x, y)
    if (now.map(_.key) != hovered().map(_.key)) hovered @= now
    canvas.cursor @= (if (now.nonEmpty) "pointer" else "default")
    now.map(_.label).filter(_.nonEmpty) match {
      case Some(words) if tips() && !Platform.remoteNavigation() =>
        tipWords.content @= words
        tip.position.left @= math.max(0.0, math.min(size.width() - tip.size.width(), x + 14.0))
        tip.position.top @= (if (y + 20.0 + tip.size.height() > size.height()) y - tip.size.height() - 8.0 else y + 20.0)
        tip.visible @= true
      case _ => tip.visible @= false
    }
  }

  canvas.event.pointer.down.attach { _ =>
    // a press on the map gives it the keys, as a map on a page takes them
    if (!canvas.focused()) canvas.focus()
    tip.visible @= false
  }
  canvas.event.pointer.move.attach { e =>
    if (!canvas.pressing) hover(e.x - canvas.absoluteLeft, e.y - canvas.absoluteTop)
  }
  canvas.event.pointer.leave.attach { _ =>
    hovered @= None
    tip.visible @= false
  }
  canvas.clicked.attach { p =>
    val (x, y) = canvas.toScreen(p.x, p.y)
    val feature = featureAt(x, y)
    clicked @= MapClick(WebMercator.toLatLng(p.x, p.y), feature, p.modifiers)
    feature match {
      case Some(c: ClusterFeature) => cluster().filter(_.zoomsIn).foreach { settings =>
        val z = WebMercator.zoomToFit(c.bounds, size.width(), size.height(), 48.0, minZoom(),
          math.min(maxZoom(), settings.until))
        val world = WebMercator.boxOf(c.bounds)
        lookAt(world.centerX, world.centerY, math.max(z, zoom() + 1.0), animate = true)
      }
      case _ => ()
    }
  }

  canvas.view.attach(_ => refresh())
  size.width.attach(_ => redraw())
  size.height.attach(_ => redraw())
  tiles.attach(_ => refresh())
  shapes.attach { s =>
    shapeLayer.set(s)
    redraw()
  }
  markers.attach { m =>
    markerLayer.set(m)
    redraw()
  }
  cluster.attach { _ =>
    // what the clusters were made with may have changed
    markerLayer.set(markers())
    redraw()
  }
  imperial.attach(_ => refresh())
  legend.attach { now =>
    legendShown.foreach(c => children -= c)
    legendShown = now
    now.foreach { c =>
      placeLegend(c)
      children += c
    }
  }

  /** The tiles, and the markers' components, let go with it. */
  override def dispose(): Unit = {
    tileLayer.clear()
    markerLayer.set(Vector.empty)
    super.dispose()
  }
}
