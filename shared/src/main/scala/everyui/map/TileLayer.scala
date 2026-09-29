package everyui.map

import everyui.component.{Component, Container, ImageView}
import everyui.image.Image
import everyui.render.Platform
import everyui.spatial.Rect

/** A map's tiles: those of the zoom nearest the view's that cover it and a little more (`MapView.tileMargin`), asked
  * for nearest the middle first, a few at a time (`MapView.tileLoads`). One not yet come is stood in for by the nearest
  * tile above it that has (drawn larger), or else by those below it that have, so a zoom never shows a blank. One no
  * longer wanted before it was asked for (by the end of the turn it was wanted in, or while it waited behind others)
  * is not asked for at all, so a pan past a place loads nothing of it; one that came is kept, up to
  * `MapView.tileCache` of them, the longest unused let go first. */
private[map] class TileLayer(map: MapView) {
  /** What the tiles are drawn in, the lower zooms under the higher. */
  val layer: Container = new Container

  private var source: Option[TileSource] = None
  // every tile asked for, or to be, by its id; those waiting to be asked for, in order; how many are on their way
  private var entries: Map[String, TileEntry] = Map.empty
  private var queue: Vector[TileEntry] = Vector.empty
  private var loading = 0
  // whether the queue is to be worked through at the end of this turn
  private var due = false
  // whether tiles that came are to be drawn at the end of this turn
  private var arriving = false
  private var turn = 0
  // what the last pass was for, so a tile that comes can be placed at once
  private var last: Option[(MapFrame, Rect, Double)] = None

  layer.size.width @= 0.0
  layer.size.height @= 0.0

  /** How many tiles are known (come, failed, on their way or waiting), waiting to be asked for, and on their way. */
  def held: Int = entries.size
  def waiting: Int = queue.length
  def inFlight: Int = loading

  /** The tiles drawn now, lowest zoom first. */
  def drawn: Vector[MapTile] = layer.children().flatMap(c => entries.values.find(_.view.exists(_ eq c)).map(_.tile))

  /** Tiles for `sight` (the world's box in view) at `zoom`, placed on `frame`. */
  def update(frame: MapFrame, sight: Rect, zoom: Double): Unit = {
    last = Some((frame, sight, zoom))
    map.tiles() match {
      case None => clear()
      case Some(src) =>
        if (!source.contains(src)) {
          clear()
          source = Some(src)
        }
        turn += 1
        val z = TileLayer.levelFor(zoom, src)
        val margin = map.tileMargin() / frame.scale
        val wanted = TileLayer.wanted(sight.grow(margin), z)
        val sharp = src.retina && Platform.pixelRatio() > 1.0
        var show: Vector[TileEntry] = Vector.empty
        var showing: Set[String] = Set.empty
        var ask: Vector[TileEntry] = Vector.empty
        val add: TileEntry => Unit = e => if (!showing.contains(e.tile.id)) {
          showing = showing + e.tile.id
          show = show :+ e
          e.used = turn
        }
        wanted.foreach { t =>
          val e = entries.getOrElse(t.id, {
            val fresh = new TileEntry(t, src.address(t, sharp))
            entries = entries + (t.id -> fresh)
            fresh
          })
          e.used = turn
          if (e.loaded) add(e)
          else {
            if (!e.started && !e.failed) ask = ask :+ e
            cover(t).foreach(add)
          }
        }
        // what waited and is no longer wanted is never asked for
        queue.foreach(e => if (e.used != turn) entries = entries - e.tile.id)
        queue = ask
        soon()
        place(show.sortBy(_.tile.z), frame)
        evict()
    }
  }

  /** Placed again for the last pass: a tile came. */
  def again(): Unit = last.foreach { case (frame, sight, zoom) => update(frame, sight, zoom) }

  /** Every tile let go: the source changed, or the map is going. */
  def clear(): Unit = {
    entries.values.foreach(_.view.foreach(_.dispose()))
    entries = Map.empty
    queue = Vector.empty
    source = None
    if (layer.children().nonEmpty) layer.children @= Vector.empty
  }

  // the nearest tile above `t` that has come, up to six zooms up; or else those of the zoom below that have
  private def cover(t: MapTile): List[TileEntry] = {
    var up = t
    var found: Option[TileEntry] = None
    var i = 0
    while (found.isEmpty && i < 6 && up.z > 0) {
      up = up.parent
      found = entries.get(up.id).filter(_.loaded)
      i += 1
    }
    found match {
      case Some(e) => List(e)
      case None => t.children.flatMap(c => entries.get(c.id).filter(_.loaded).toList)
    }
  }

  // the queue worked through once the turn is over, so a view set and then set again at once (tiles given, then a
  // place to look) asks only for what the last wants
  private def soon(): Unit = if (!due) {
    due = true
    Platform.later(() => {
      due = false
      pump()
    })
  }

  // as many asked for as may be on their way at once
  private def pump(): Unit = while (loading < math.max(1, map.tileLoads()) && queue.nonEmpty) {
    val e = queue.head
    queue = queue.tail
    e.started = true
    loading += 1
    val uri = if (e.address.contains("://")) e.address else Platform.resolvePath(e.address)
    Platform.loadImage(uri, (w, h) => came(e, uri, w, h))
  }

  private def came(e: TileEntry, uri: String, w: Double, h: Double): Unit = {
    loading -= 1
    // (one let go while it was on its way is forgotten)
    if (entries.get(e.tile.id).exists(_ eq e)) {
      if (w > 0.0 && h > 0.0) {
        val view = new ImageView
        view.image @= new Image(uri, w, h)
        e.view = Some(view)
      }
      else e.failed = true
      pump()
      // drawn once the turn is over, with any others that came in it
      if (!arriving) {
        arriving = true
        Platform.later(() => {
          arriving = false
          again()
        })
      }
    }
    else pump()
  }

  // each drawn where its box falls on the frame, its edges on whole pixels so neighbours meet without a seam
  private def place(show: Vector[TileEntry], frame: MapFrame): Unit = {
    val views: Vector[Component] = show.flatMap { e =>
      e.view.toVector.map { view =>
        val box = WebMercator.tileBox(e.tile)
        val left = math.floor(frame.left(box.x) + 0.5)
        val top = math.floor(frame.top(box.y) + 0.5)
        val at = Rect(left, top, math.floor(frame.left(box.right) + 0.5) - left,
          math.floor(frame.top(box.bottom) + 0.5) - top)
        if (!e.placed.contains(at)) {
          e.placed = Some(at)
          view.position.left @= at.x
          view.position.top @= at.y
          view.size.width @= at.width
          view.size.height @= at.height
        }
        view
      }
    }
    val now = layer.children()
    var same = now.length == views.length
    var i = 0
    while (same && i < views.length) {
      same = now(i) eq views(i)
      i += 1
    }
    if (!same) layer.children @= views
  }

  // past the cache's size, the longest unused of those that came are let go (none drawn now)
  private def evict(): Unit = {
    val done = entries.values.filter(_.done).toVector
    val over = done.length - math.max(0, map.tileCache())
    if (over > 0) done.filter(_.used != turn).sortBy(_.used).take(over).foreach { e =>
      e.view.foreach(_.dispose())
      entries = entries - e.tile.id
    }
  }
}

object TileLayer {
  /** The zoom of the tiles drawn for a view at `zoom`: the nearest whole one the source has. */
  def levelFor(zoom: Double, source: TileSource): Int =
    math.max(source.minZoom, math.min(source.maxZoom, math.floor(zoom + 0.5).toInt))

  /** The tiles of zoom `z` that cover `box` (in the world), nearest its middle first. */
  def wanted(box: Rect, z: Int): Vector[MapTile] = {
    val side = WebMercator.size / WebMercator.across(z)
    WebMercator.tilesIn(box, z).sortBy { t =>
      val dx = (t.x + 0.5) * side - box.centerX
      val dy = (t.y + 0.5) * side - box.centerY
      dx * dx + dy * dy
    }
  }
}
