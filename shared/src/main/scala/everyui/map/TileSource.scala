package everyui.map

/** Where a map's tiles come from: `address` says where the picture of a tile is (asked for the sharper, twice-size
  * picture when `retina` and the screen has more than one pixel to a point), and `attribution` is the credit the map
  * shows for them in its corner, which OpenStreetMap's licence (and most others') requires; `link` is where a click on
  * it goes. The server has tiles from `minZoom` to `maxZoom`: closer than that, its closest are drawn larger.
  *
  * Most servers are a URL template (`TileSource.template`). A test, or an app that makes its own pictures, gives
  * `address` itself: a local server's, or anything `Image` loads.
  *
  * {{{
  *   map.tiles @= Some(TileSource.openStreetMap)
  *   map.tiles @= Some(TileSource.template("https://{s}.tile.example.com/{z}/{x}/{y}{r}.png", "© Example",
  *     subdomains = List("a", "b", "c")))
  *   map.tiles @= Some(TileSource((t, _) => "http://localhost:8123/" + t.id + ".png", "Test tiles"))
  * }}}
  *
  * OpenStreetMap's own tile server is for light use by people looking at a map (its tile usage policy): an app with
  * real traffic uses a commercial provider or its own server. */
case class TileSource(address: (MapTile, Boolean) => String, attribution: String, minZoom: Int = 0, maxZoom: Int = 19,
                      retina: Boolean = false, link: Option[String] = None)

object TileSource {
  /** Tiles from a URL template: `{z}`, `{x}` and `{y}` are the tile's (`{-y}` counts rows from the south, as TMS
    * does), `{s}` one of `subdomains` (the same one for a tile every time, so the browser's cache holds), and `{r}`
    * `@2x` where the screen is sharper than a pixel a point and nothing elsewhere (so a template with `{r}` is
    * `retina`). */
  def template(url: String, attribution: String, subdomains: List[String] = List("a", "b", "c"), minZoom: Int = 0,
               maxZoom: Int = 19, link: Option[String] = None): TileSource =
    TileSource((t, sharp) => fill(url, t, sharp, subdomains), attribution, minZoom, maxZoom, url.contains("{r}"), link)

  /** OpenStreetMap's standard tiles, from its own server: for light use (see its tile usage policy). */
  lazy val openStreetMap: TileSource = template("https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    "© OpenStreetMap contributors", Nil, maxZoom = 19, link = Some("https://www.openstreetmap.org/copyright"))

  /** `url` with `tile`'s numbers in it. */
  def fill(url: String, tile: MapTile, sharp: Boolean, subdomains: List[String]): String = {
    val sub = if (subdomains.isEmpty) "" else subdomains((tile.x + tile.y) % subdomains.length)
    url.replace("{z}", tile.z.toString).replace("{x}", tile.x.toString)
      .replace("{-y}", (tile.across - 1 - tile.y).toString).replace("{y}", tile.y.toString)
      .replace("{s}", sub).replace("{r}", if (sharp) "@2x" else "")
  }
}
