package everyui.map

import everyui.component.{Component, Container}
import everyui.map.MapFeature.{ClusterFeature, MarkerFeature}

/** A map's markers, each laid out in the world once and drawn at its own size on the frame: only those whose point is
  * on the frame (or near enough that they may reach into it) are made, each the first time it comes into view; those
  * that leave wait, up to `MapView.keep` of them, and are taken apart after that. Where the map clusters, the markers
  * of each square of the world (`MapCluster.cell` pixels at the whole zoom below the view's) that has enough of them
  * are drawn as one. */
private[map] class MarkerLayer(map: MapView) {
  val layer: Container = everyui.layout.Overlaps.allow(new Container)

  private var singles: Vector[MarkerSpot] = Vector.empty
  // the components made, by their spots' ids, those out of view (oldest first), and what the last frame drew
  private var made: Map[String, Component] = Map.empty
  private var idle: Vector[String] = Vector.empty
  private var current: Map[String, MarkerSpot] = Map.empty
  private var shown: Vector[MarkerSpot] = Vector.empty

  layer.size.width @= 0.0
  layer.size.height @= 0.0

  /** How many markers there are, and how many spots (markers and clusters) the last frame drew. */
  def count: Int = singles.length
  def showing: Int = shown.length
  /** How many components are made, drawn or waiting. */
  def madeCount: Int = made.size

  /** The markers, laid out in the world. What was made for them before is made again as it is next wanted. */
  def set(markers: Vector[MapMarker]): Unit = {
    singles = markers.map { m =>
      new MarkerSpot("m" + m.key.length.toString + ":" + m.key, WebMercator.x(m.at.lng), WebMercator.y(m.at.lat),
        MarkerFeature(m), m.anchor, m.make, m.hits)
    }
    made.foreach { case (_, c) => c.dispose() }
    made = Map.empty
    idle = Vector.empty
    current = Map.empty
    shown = Vector.empty
    if (layer.children().nonEmpty) layer.children @= Vector.empty
  }

  /** Drawn for `frame` at `zoom`. */
  def place(frame: MapFrame, zoom: Double): Unit = {
    val reach = 96.0
    var now: Vector[MarkerSpot] = Vector.empty
    gathered(zoom).foreach { s =>
      s.left = frame.left(s.x)
      s.top = frame.top(s.y)
      if (s.left >= -reach && s.top >= -reach && s.left <= frame.width + reach && s.top <= frame.height + reach)
        now = now :+ s
    }
    var ids: Set[String] = Set.empty
    val parts = now.map { s =>
      ids = ids + s.id
      val c = made.getOrElse(s.id, {
        val fresh = s.make()
        made = made + (s.id -> fresh)
        // what measures itself once it is on the screen (words) is placed again by its anchor as it does
        fresh.size.width.attach(_ => again(s.id))
        fresh.size.height.attach(_ => again(s.id))
        fresh
      })
      c
    }
    current = now.map(s => s.id -> s).toMap
    val before = shown
    shown = now
    val kids = layer.children()
    var same = kids.length == parts.length
    var i = 0
    while (same && i < parts.length) {
      same = kids(i) eq parts(i)
      i += 1
    }
    if (!same) layer.children @= parts
    now.foreach(s => made.get(s.id).foreach(c => put(c, s)))
    // what is drawn again leaves the wait, what is no longer drawn joins it, and past `keep` the oldest go
    idle = idle.filterNot(ids.contains)
    var waiting = idle.toSet
    before.foreach(s => if (!ids.contains(s.id) && made.contains(s.id) && !waiting.contains(s.id)) {
      idle = idle :+ s.id
      waiting = waiting + s.id
    })
    val over = idle.length - math.max(0, map.keep())
    if (over > 0) {
      idle.take(over).foreach { id =>
        made.get(id).foreach(_.dispose())
        made = made - id
      }
      idle = idle.drop(over)
    }
  }

  /** The topmost spot drawn whose component covers (`x`, `y`) of the frame, and that `hits`. */
  def hit(x: Double, y: Double): Option[MapFeature] = {
    var found: Option[MapFeature] = None
    var i = shown.length - 1
    while (found.isEmpty && i >= 0) {
      val s = shown(i)
      if (s.hits) made.get(s.id).foreach { c =>
        val left = c.position.left()
        val top = c.position.top()
        if (x >= left && y >= top && x <= left + c.size.width() && y <= top + c.size.height()) found = Some(s.feature)
      }
      i -= 1
    }
    found
  }

  // a component placed by its spot's anchor, on whole pixels
  private def put(c: Component, s: MarkerSpot): Unit = {
    c.position.left @= math.floor(s.left - s.anchor.x * c.size.width() + 0.5)
    c.position.top @= math.floor(s.top - s.anchor.y * c.size.height() + 0.5)
  }

  private def again(id: String): Unit = current.get(id).foreach(s => made.get(id).foreach(c => put(c, s)))

  // the markers drawn at `zoom`, those that crowd a square gathered into one where the map clusters
  private def gathered(zoom: Double): Vector[MarkerSpot] = {
    val live = singles.filter { s =>
      s.feature match {
        case MarkerFeature(m) => zoom >= m.from && m.until.forall(zoom < _)
        case _ => true
      }
    }
    map.cluster() match {
      case Some(c) if zoom < c.until =>
        val level = math.floor(zoom)
        val cell = c.cell / WebMercator.scaleOf(level)
        // each square's spots, and the squares in the order their first markers come
        var cells: Map[String, Vector[MarkerSpot]] = Map.empty
        var order: Vector[String] = Vector.empty
        live.foreach { s =>
          val clusters = s.feature match {
            case MarkerFeature(m) => m.clusters
            case _ => false
          }
          val key = if (clusters) math.floor(s.x / cell).toLong.toString + ":" + math.floor(s.y / cell).toLong.toString
            else s.id
          cells.get(key) match {
            case Some(had) => cells = cells + (key -> (had :+ s))
            case None =>
              cells = cells + (key -> Vector(s))
              order = order :+ key
          }
        }
        // a cluster's component is kept by its count (the k-th of its count this frame), not its square, so a zoom
        // that gathers the markers anew draws them with the components it has
        var counted: Map[Int, Int] = Map.empty
        order.flatMap { key =>
          val group = cells(key)
          if (group.length < math.max(2, c.least)) group
          else {
            val k = counted.getOrElse(group.length, 0)
            counted = counted + (group.length -> (k + 1))
            Vector(MarkerLayer.cluster(group, "c" + level.toInt.toString + ":" + key, "n" + group.length.toString + "#" +
              k.toString, c))
          }
        }
      case _ => live
    }
  }
}

private[map] object MarkerLayer {
  /** The spot for `group`, a square's markers (the square's `key`), drawn as one where they are on average, its
    * component kept by `id`. */
  def cluster(group: Vector[MarkerSpot], key: String, id: String, c: MapCluster): MarkerSpot = {
    var x = 0.0
    var y = 0.0
    var markers: Vector[MapMarker] = Vector.empty
    group.foreach { s =>
      x += s.x
      y += s.y
      s.feature match {
        case MarkerFeature(m) => markers = markers :+ m
        case _ => ()
      }
    }
    val n = group.length
    val bounds = LatLngBounds.around(markers.map(_.at)).getOrElse(LatLngBounds(0.0, 0.0, 0.0, 0.0))
    new MarkerSpot(id, x / n, y / n, ClusterFeature(key, markers, bounds), MapAnchor.Center, () => c.make(n), true)
  }
}
