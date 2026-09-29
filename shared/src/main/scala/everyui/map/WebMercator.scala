package everyui.map

import everyui.spatial.Rect

/** Web Mercator (EPSG:3857), the projection OpenStreetMap's tiles and nearly every other tile server are drawn in.
  *
  * A map lays the Earth out in a square world `size` (256) units across: `x` runs east from the antimeridian, `y`
  * south from 85.05° north (`maxLatitude`, where the square ends). At zoom `z` a unit of the world is 2^z pixels of the
  * screen, so the whole world is one 256-pixel tile at zoom 0 and 2^z tiles across at zoom `z` (`tilesIn`).
  *
  * {{{
  *   val (x, y) = WebMercator.toWorld(LatLng(51.5074, -0.1278))
  *   WebMercator.tileAt(LatLng(51.5074, -0.1278), 12)   // MapTile(12, 2046, 1362)
  * }}} */
object WebMercator {
  /** How many units of the world it is across and down: a tile's pixels at zoom 0. */
  val size: Double = 256.0
  /** How far north and south the square world reaches, in degrees: where the projection's y meets its x. */
  val maxLatitude: Double = 85.0511287798066
  /** The Earth's radius the projection takes, in metres (WGS 84's semi-major axis). */
  val earthRadius: Double = 6378137.0

  /** Where `p` lies in the world. A latitude past `maxLatitude` is held at it. */
  def toWorld(p: LatLng): (Double, Double) = (x(p.lng), y(p.lat))

  /** The place at the world's point (`x`, `y`). */
  def toLatLng(x: Double, y: Double): LatLng = LatLng(lat(y), lng(x))

  /** The world's x of longitude `lng`. */
  def x(lng: Double): Double = (lng + 180.0) / 360.0 * size

  /** The world's y of latitude `lat`. */
  def y(lat: Double): Double = {
    val held = math.max(-maxLatitude, math.min(maxLatitude, lat))
    val s = math.sin(held * math.Pi / 180.0)
    (0.5 - math.log((1.0 + s) / (1.0 - s)) / (4.0 * math.Pi)) * size
  }

  /** The longitude at the world's x. */
  def lng(x: Double): Double = x / size * 360.0 - 180.0

  /** The latitude at the world's y. */
  def lat(y: Double): Double = {
    val n = math.Pi * (1.0 - 2.0 * y / size)
    // atan(sinh(n)), in the functions every platform has
    math.atan((math.exp(n) - math.exp(-n)) / 2.0) * 180.0 / math.Pi
  }

  /** How many pixels of the screen a unit of the world is at `zoom`: 2^zoom. */
  def scaleOf(zoom: Double): Double = math.pow(2.0, zoom)

  /** The zoom at which a unit of the world is `scale` pixels. */
  def zoomOf(scale: Double): Double = math.log(scale) / math.log(2.0)

  /** How many tiles the world is across at zoom `z`: 2^z. */
  def across(z: Int): Int = {
    var n = 1
    var i = 0
    while (i < z) {
      n = n * 2
      i += 1
    }
    n
  }

  /** The tile at zoom `z` that `p` is in. */
  def tileAt(p: LatLng, z: Int): MapTile = {
    val n = across(z)
    val (wx, wy) = toWorld(p)
    MapTile(z, index(wx / size * n, n), index(wy / size * n, n))
  }

  /** The box `tile` takes in the world. */
  def tileBox(tile: MapTile): Rect = {
    val side = size / across(tile.z)
    Rect(tile.x * side, tile.y * side, side, side)
  }

  /** The box `tile` covers on the Earth. */
  def tileBounds(tile: MapTile): LatLngBounds = {
    val box = tileBox(tile)
    LatLngBounds(lat(box.bottom), lng(box.x), lat(box.y), lng(box.right))
  }

  /** The box `bounds` takes in the world. */
  def boxOf(bounds: LatLngBounds): Rect = {
    val left = x(bounds.west)
    val top = y(bounds.north)
    Rect(left, top, x(bounds.east) - left, y(bounds.south) - top)
  }

  /** The tiles of zoom `z` that cover `box` (in the world), row by row from the top left; none outside the world. */
  def tilesIn(box: Rect, z: Int): Vector[MapTile] = {
    val n = across(z)
    val side = size / n
    var out = Vector.empty[MapTile]
    if (box.right >= 0.0 && box.x < size && box.bottom >= 0.0 && box.y < size) {
      val left = index(box.x / side, n)
      val right = index(box.right / side, n)
      val top = index(box.y / side, n)
      val bottom = index(box.bottom / side, n)
      var ty = top
      while (ty <= bottom) {
        var tx = left
        while (tx <= right) {
          out = out :+ MapTile(z, tx, ty)
          tx += 1
        }
        ty += 1
      }
    }
    out
  }

  /** How many metres of the ground a pixel of the screen covers at latitude `lat` and `zoom`. */
  def metersPerPixel(lat: Double, zoom: Double): Double =
    2.0 * math.Pi * earthRadius * math.cos(lat * math.Pi / 180.0) / (size * scaleOf(zoom))

  /** The zoom that shows all of `bounds` in a view `width` by `height` pixels with `padding` pixels to spare on every
    * side, no less than `minZoom` and no more than `maxZoom`; a single point is shown at `maxZoom`. */
  def zoomToFit(bounds: LatLngBounds, width: Double, height: Double, padding: Double, minZoom: Double,
                maxZoom: Double): Double = {
    val box = boxOf(bounds)
    val roomX = math.max(1.0, width - padding * 2.0)
    val roomY = math.max(1.0, height - padding * 2.0)
    val zoom =
      if (box.width <= 0.0 && box.height <= 0.0) maxZoom
      else if (box.width <= 0.0) zoomOf(roomY / box.height)
      else if (box.height <= 0.0) zoomOf(roomX / box.width)
      else zoomOf(math.min(roomX / box.width, roomY / box.height))
    math.max(minZoom, math.min(maxZoom, zoom))
  }

  // the tile a fraction of the world's `n` tiles is in, within the world
  private def index(at: Double, n: Int): Int = math.max(0, math.min(n - 1, math.floor(at).toInt))
}
