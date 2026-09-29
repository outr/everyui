package everyui.map

/** One square of a tiled map: at zoom `z` the world is 2^z tiles across and down, `x` counted from the antimeridian
  * eastwards and `y` from the north, as OpenStreetMap numbers them (the "XYZ" or "slippy map" scheme). */
case class MapTile(z: Int, x: Int, y: Int) {
  /** How many tiles the world is across (and down) at this tile's zoom. */
  def across: Int = WebMercator.across(z)

  /** The tile at the zoom above that this one is a quarter of. */
  def parent: MapTile = MapTile(z - 1, x / 2, y / 2)

  /** The four tiles of the zoom below that make this one. */
  def children: List[MapTile] = List(MapTile(z + 1, x * 2, y * 2), MapTile(z + 1, x * 2 + 1, y * 2),
    MapTile(z + 1, x * 2, y * 2 + 1), MapTile(z + 1, x * 2 + 1, y * 2 + 1))

  /** A key no other tile shares. */
  def id: String = z.toString + "/" + x.toString + "/" + y.toString
}
