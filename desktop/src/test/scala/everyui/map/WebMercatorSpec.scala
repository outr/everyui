package everyui.map

import everyui.spatial.Rect
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The projection and its tiles, fitting a box in a view, which tiles a view wants, tile addresses, the scale bar's
  * round distances, and the geometry that draws and finds shapes. Plain arithmetic: no window. */
class WebMercatorSpec extends AnyWordSpec with Matchers {
  // OpenStreetMap's own formula for a tile's numbers (its wiki's "Slippy map tilenames")
  private def osm(lat: Double, lng: Double, z: Int): (Int, Int) = {
    val n = math.pow(2.0, z)
    val r = math.toRadians(lat)
    ((((lng + 180.0) / 360.0) * n).floor.toInt,
      ((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n).floor.toInt)
  }

  private val places = List(LatLng(0.0, 0.0), LatLng(51.5074, -0.1278), LatLng(35.0844, -106.6504),
    LatLng(-33.8688, 151.2093), LatLng(64.1466, -21.9426), LatLng(-54.8019, -68.303), LatLng(35.4676, -97.5164),
    LatLng(85.0, 179.99), LatLng(-85.0, -179.99))

  "Web Mercator" should {
    "put the equator and the prime meridian in the middle of the world, and its corners at 85.05 degrees" in {
      WebMercator.toWorld(LatLng(0.0, 0.0)) shouldBe ((128.0, 128.0))
      WebMercator.y(WebMercator.maxLatitude) shouldBe 0.0 +- 1e-9
      WebMercator.y(-WebMercator.maxLatitude) shouldBe 256.0 +- 1e-9
      WebMercator.x(-180.0) shouldBe 0.0
      WebMercator.x(180.0) shouldBe 256.0
      // past the square, held at its edge
      WebMercator.y(89.9) shouldBe 0.0 +- 1e-9
    }
    "come back to the same place from the world" in {
      places.foreach { p =>
        val (x, y) = WebMercator.toWorld(p)
        val back = WebMercator.toLatLng(x, y)
        back.lat shouldBe p.lat +- 1e-9
        back.lng shouldBe p.lng +- 1e-9
      }
      // and the world from a place, anywhere in it
      for (x <- 0 to 256 by 16; y <- 0 to 256 by 16) {
        val (bx, by) = WebMercator.toWorld(WebMercator.toLatLng(x, y))
        bx shouldBe x.toDouble +- 1e-9
        by shouldBe y.toDouble +- 1e-7
      }
    }
    "number tiles as OpenStreetMap does, at every zoom" in {
      WebMercator.tileAt(LatLng(51.5074, -0.1278), 12) shouldBe MapTile(12, 2046, 1362)
      WebMercator.tileAt(LatLng(0.0, 0.0), 0) shouldBe MapTile(0, 0, 0)
      for (p <- places.take(7); z <- List(1, 3, 7, 10, 13, 16, 19)) {
        val (x, y) = osm(p.lat, p.lng, z)
        val t = WebMercator.tileAt(p, z)
        (t.x, t.y) shouldBe ((x, y))
        // and the tile's box holds the place
        WebMercator.tileBounds(t).contains(p) shouldBe true
      }
      WebMercator.across(19) shouldBe 524288
      WebMercator.tileBounds(MapTile(1, 0, 0)) match {
        case b =>
          (b.west, b.east, b.south) shouldBe ((-180.0, 0.0, 0.0))
          b.north shouldBe WebMercator.maxLatitude +- 1e-9
      }
      MapTile(5, 11, 7).parent shouldBe MapTile(4, 5, 3)
      MapTile(4, 5, 3).children should contain(MapTile(5, 11, 7))
    }
    "give a zoom that fits a box in a view, with padding, within the zooms allowed" in {
      val world = LatLngBounds(-WebMercator.maxLatitude, -180.0, WebMercator.maxLatitude, 180.0)
      WebMercator.zoomToFit(world, 256.0, 256.0, 0.0, 0.0, 19.0) shouldBe 0.0 +- 1e-9
      WebMercator.zoomToFit(world, 1024.0, 512.0, 0.0, 0.0, 19.0) shouldBe 1.0 +- 1e-9
      // a tile of zoom 10 fills a 256 view at 10, and with 64 pixels round it half as close (a 128 box)
      val tile = WebMercator.tileBounds(MapTile(10, 300, 400))
      WebMercator.zoomToFit(tile, 256.0, 256.0, 0.0, 0.0, 19.0) shouldBe 10.0 +- 1e-6
      WebMercator.zoomToFit(tile, 256.0, 256.0, 64.0, 0.0, 19.0) shouldBe 9.0 +- 1e-6
      // the narrower way decides
      WebMercator.zoomToFit(tile, 256.0, 1000.0, 0.0, 0.0, 19.0) shouldBe 10.0 +- 1e-6
      // a point is shown as close as allowed; nothing goes past the limits
      WebMercator.zoomToFit(LatLngBounds(10.0, 10.0, 10.0, 10.0), 400.0, 300.0, 20.0, 2.0, 17.0) shouldBe 17.0
      WebMercator.zoomToFit(world, 100.0, 100.0, 0.0, 2.0, 17.0) shouldBe 2.0
      WebMercator.zoomToFit(tile, 4096.0, 4096.0, 0.0, 0.0, 12.0) shouldBe 12.0
    }
    "measure the ground a pixel covers" in {
      WebMercator.metersPerPixel(0.0, 0.0) shouldBe 156543.034 +- 0.001
      WebMercator.metersPerPixel(60.0, 1.0) shouldBe 156543.034 / 4.0 +- 0.001
    }
  }

  "A box of places" should {
    "be found round them, and fit, hold and meet others" in {
      val b = LatLngBounds.around(List(LatLng(1.0, 2.0), LatLng(-3.0, 5.0), LatLng(0.0, -1.0))).get
      b shouldBe LatLngBounds(-3.0, -1.0, 1.0, 5.0)
      LatLngBounds.around(Nil) shouldBe None
      b.contains(LatLng(0.0, 0.0)) shouldBe true
      b.contains(LatLng(2.0, 0.0)) shouldBe false
      b.intersects(LatLngBounds(0.5, 4.0, 3.0, 9.0)) shouldBe true
      b.intersects(LatLngBounds(2.0, 4.0, 3.0, 9.0)) shouldBe false
      // its middle as the map draws it, nearer the pole than halfway between the latitudes
      val tall = LatLngBounds(0.0, 0.0, 80.0, 10.0)
      tall.center.lat should be > 40.0
      WebMercator.y(tall.center.lat) shouldBe (WebMercator.y(0.0) + WebMercator.y(80.0)) / 2.0 +- 1e-9
    }
  }

  "Tiles for a view" should {
    "be those that cover it, nearest the middle first, and none outside the world" in {
      val side = 256.0 / 16.0
      // a box across four tiles of zoom 4, and a little of their neighbours
      val box = Rect(side * 5.5, side * 7.5, side * 2.0, side * 2.0)
      val wanted = TileLayer.wanted(box, 4)
      wanted.toSet shouldBe (for (x <- 5 to 7; y <- 7 to 9) yield MapTile(4, x, y)).toSet
      wanted.head shouldBe MapTile(4, 6, 8)
      wanted.slice(1, 5).toSet shouldBe Set(MapTile(4, 6, 7), MapTile(4, 5, 8), MapTile(4, 7, 8), MapTile(4, 6, 9))
      TileLayer.wanted(Rect(-100.0, -100.0, 50.0, 50.0), 3) shouldBe Vector.empty
      TileLayer.wanted(Rect(-10.0, -10.0, 300.0, 300.0), 1).length shouldBe 4
      WebMercator.tilesIn(Rect(0.0, 0.0, 256.0, 256.0), 0) shouldBe Vector(MapTile(0, 0, 0))
    }
    "be of the nearest whole zoom the source has" in {
      val source = TileSource((_, _) => "", "", minZoom = 2, maxZoom = 18)
      TileLayer.levelFor(11.49, source) shouldBe 11
      TileLayer.levelFor(11.5, source) shouldBe 12
      TileLayer.levelFor(0.3, source) shouldBe 2
      TileLayer.levelFor(19.0, source) shouldBe 18
    }
  }

  "A tile source" should {
    "fill in a template's numbers, subdomains, rows from the south, and the sharper picture's suffix" in {
      val t = TileSource.template("https://{s}.tiles.test/{z}/{x}/{y}{r}.png", "Test")
      t.retina shouldBe true
      t.address(MapTile(3, 2, 5), false) shouldBe "https://b.tiles.test/3/2/5.png"
      t.address(MapTile(3, 2, 5), true) shouldBe "https://b.tiles.test/3/2/5@2x.png"
      t.address(MapTile(3, 3, 5), false) shouldBe "https://c.tiles.test/3/3/5.png"
      // the same subdomain for a tile every time
      t.address(MapTile(3, 2, 5), false) shouldBe t.address(MapTile(3, 2, 5), false)
      TileSource.template("http://x/{z}/{x}/{-y}.png", "T", Nil).address(MapTile(3, 2, 5), true) shouldBe "http://x/3/2/2.png"
      TileSource.openStreetMap.address(MapTile(12, 2046, 1362), true) shouldBe
        "https://tile.openstreetmap.org/12/2046/1362.png"
      TileSource.openStreetMap.retina shouldBe false
      TileSource.openStreetMap.attribution should include("OpenStreetMap")
    }
  }

  "The scale bar" should {
    "show the longest round distance that fits" in {
      ScaleBar.round(0.4) shouldBe 1.0
      ScaleBar.round(7.3) shouldBe 5.0
      ScaleBar.round(19.0) shouldBe 10.0
      ScaleBar.round(20.0) shouldBe 20.0
      ScaleBar.round(4999.0) shouldBe 2000.0
      // 30 metres a pixel: 3 km in 100 pixels is 2 km in 66.7
      val km = ScaleBar.pick(30.0, 100.0, imperial = false)
      km.label shouldBe "2 km"
      km.pixels shouldBe 2000.0 / 30.0 +- 1e-9
      ScaleBar.pick(3.0, 100.0, imperial = false).label shouldBe "200 m"
      ScaleBar.pick(3.0, 100.0, imperial = true).label shouldBe "500 ft"
      ScaleBar.pick(30.0, 100.0, imperial = true).label shouldBe "1 mi"
      ScaleBar.pick(0.4, 100.0, imperial = true).label shouldBe "100 ft"
      ScaleBar.pick(0.04, 100.0, imperial = true).label shouldBe "10 ft"
      ScaleBar.pick(1000.0, 100.0, imperial = false).label shouldBe "100 km"
      ScaleBar.pick(100000.0, 100.0, imperial = false).label shouldBe "10,000 km"
    }
  }

  "A shape's geometry" should {
    val ring = Vector(LatLng(10.0, 10.0), LatLng(10.0, 20.0), LatLng(0.0, 20.0), LatLng(0.0, 10.0))
    "find a point inside a polygon, filled or not, and near a line" in {
      val square = ShapeGeometry.of(MapShape.polygon("a", ring, MapStyle(stroke = Some(everyui.Color.Red))))
      val (x, y) = WebMercator.toWorld(LatLng(5.0, 15.0))
      square.hits(x, y, 0.0) shouldBe true
      val (ox, oy) = WebMercator.toWorld(LatLng(5.0, 25.0))
      square.hits(ox, oy, 0.0) shouldBe false
      val line = ShapeGeometry.of(MapShape.line("b", ring, MapStyle(stroke = Some(everyui.Color.Red))))
      line.hits(x, y, 0.1) shouldBe false
      // just off the line from (10, 10) to (10, 20), and off the closing edge a line does not have
      val (nx, ny) = WebMercator.toWorld(LatLng(10.0, 15.0))
      line.hits(nx, ny + 0.05, 0.1) shouldBe true
      val (cx, cy) = WebMercator.toWorld(LatLng(5.0, 10.0))
      line.hits(cx - 0.05, cy, 0.1) shouldBe false
      square.hits(cx - 0.05, cy, 0.1) shouldBe true
    }
    "take a hole out of a polygon wound the other way" in {
      val hole = Vector(LatLng(7.0, 13.0), LatLng(3.0, 13.0), LatLng(3.0, 17.0), LatLng(7.0, 17.0))
      val donut = ShapeGeometry.of(MapShape("d", Vector(ring, hole), closed = true, MapStyle(fill = Some(everyui.Color.Red))))
      val (x, y) = WebMercator.toWorld(LatLng(5.0, 15.0))
      donut.hits(x, y, 0.0) shouldBe false
      val (x2, y2) = WebMercator.toWorld(LatLng(8.5, 15.0))
      donut.hits(x2, y2, 0.0) shouldBe true
    }
    "be thinned to points a pixel apart, and cut to the frame" in {
      val frame = MapFrame(0.0, 0.0, 1.0, 100.0, 100.0)
      ShapeLayer.thin(Array(0.0, 0.0, 0.2, 0.2, 0.4, 0.1, 5.0, 5.0, 5.3, 5.1), frame, closed = false).toList shouldBe
        List(0.0, 0.0, 5.3, 5.1)
      ShapeLayer.thin(Array(0.0, 0.0, 0.2, 0.2, 10.0, 0.0, 10.0, 10.0), frame, closed = true).toList shouldBe
        List(0.0, 0.0, 10.0, 0.0, 10.0, 10.0)
      // a square twice the frame's size, clipped to it, is the frame
      val big = ShapeLayer.clip(Array(-50.0, -50.0, 150.0, -50.0, 150.0, 150.0, -50.0, 150.0), Rect(0.0, 0.0, 100.0, 100.0))
      big.grouped(2).map(p => (p(0), p(1))).toSet shouldBe Set((0.0, 0.0), (100.0, 0.0), (100.0, 100.0), (0.0, 100.0))
      // a line in and out of the frame and back in is two runs, each cut at its edges
      val runs = ShapeLayer.cutLine(Array(-50.0, 50.0, 50.0, 50.0, 50.0, 200.0, 80.0, 200.0, 80.0, 50.0), Rect(0.0, 0.0, 100.0, 100.0))
      runs.map(_.toList) shouldBe Vector(List(0.0, 50.0, 50.0, 50.0, 50.0, 100.0), List(80.0, 100.0, 80.0, 50.0))
      ShapeLayer.runs(Array(0.0, 0.0, 1.0, 1.0), None, closed = true) shouldBe Vector.empty
    }
  }
}
