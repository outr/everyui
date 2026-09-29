package everyui.app

import everyui.map.{MapTile, TileSource}

/** Tiles drawn in the page, for the map demo and its checks: each a small picture (SVG, in a data: URL) of land in a
  * colour of its own, with a grid and its numbers, so nothing is asked of a tile server. What a browser draws; the
  * desktop and Android load only bitmaps, so there the demo takes a local server's (`tiles=`). */
object MapTiles {
  lazy val drawn: TileSource = TileSource((t, _) => "data:image/svg+xml;charset=utf-8," + escape(svg(t)),
    "Demo tiles, drawn in the page")

  /** A tile's picture. */
  def svg(t: MapTile): String = {
    // a colour for the tile from its numbers, pale greens and sands
    val mix = ((t.x * 73856093) ^ (t.y * 19349663) ^ (t.z * 83492791)) & 0xffff
    val hue = 70 + mix % 60
    val light = 82 + (mix / 60) % 8
    "<svg xmlns='http://www.w3.org/2000/svg' width='256' height='256'>" +
      "<rect width='256' height='256' fill='hsl(" + hue.toString + ",35%," + light.toString + "%)'/>" +
      "<path d='M64 0V256M128 0V256M192 0V256M0 64H256M0 128H256M0 192H256' stroke='rgba(0,0,0,0.06)'/>" +
      "<rect x='0.5' y='0.5' width='255' height='255' fill='none' stroke='rgba(0,0,0,0.18)'/>" +
      "<text x='8' y='20' font-family='sans-serif' font-size='12' fill='rgba(0,0,0,0.45)'>" + t.id + "</text></svg>"
  }

  // what a data: URL cannot carry as it is
  private def escape(s: String): String =
    s.replace("%", "%25").replace("#", "%23").replace("<", "%3C").replace(">", "%3E").replace(" ", "%20")
}
