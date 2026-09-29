package everyui.map

import com.sun.net.httpserver.HttpServer
import everyui.component.{Component, Container}
import everyui.focus.Focus
import everyui.map.MapFeature.{ClusterFeature, MarkerFeature, ShapeFeature}
import everyui.{Color, FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import javax.imageio.ImageIO

/** A map over tiles from a server of the spec's own, on this machine: which tiles it asks for and when, what stands in
  * for those still coming, what it lets go of; where it looks and fits; its markers, shapes and clusters, found under
  * the pointer and clicked with events sent to its own scene. */
class MapViewSpec extends FxSpec {
  // a tile server: every tile a 256-pixel PNG, each answer held back until `gate` opens, and every path asked for kept
  private var gate = new CountDownLatch(0)
  private val asked = new ConcurrentLinkedQueue[String]()
  private lazy val png: Array[Byte] = {
    val image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setColor(new java.awt.Color(160, 190, 140))
    g.fillRect(0, 0, 256, 256)
    g.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    out.toByteArray
  }
  private lazy val server: HttpServer = {
    val s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    s.setExecutor(java.util.concurrent.Executors.newCachedThreadPool())
    s.createContext("/", exchange => {
      asked.add(exchange.getRequestURI.getPath.stripPrefix("/"))
      gate.await(10, TimeUnit.SECONDS)
      exchange.getResponseHeaders.add("Content-Type", "image/png")
      exchange.sendResponseHeaders(200, png.length.toLong)
      exchange.getResponseBody.write(png)
      exchange.close()
    })
    s.start()
    s
  }
  // the source asked of each tile, and whether it was asked for the sharper picture
  private var sharpAsked = false
  private def source: TileSource = TileSource((t, sharp) => {
    if (sharp) sharpAsked = true
    "http://127.0.0.1:" + server.getAddress.getPort.toString + "/" + t.id + ".png"
  }, "Spec tiles")

  private def requests: List[String] = {
    var out: List[String] = Nil
    asked.forEach(p => out = p :: out)
    out.reverse
  }

  private def map(width: Double = 512.0, height: Double = 384.0): MapView = fx {
    val m = new MapView
    m.position.left @= 10.0
    m.position.top @= 20.0
    m.size.width @= width
    m.size.height @= height
    m.canvas.glide @= 0.0
    m
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false,
      false, true, false, false, false, false, true, null))
  }

  private def clickAt(m: MapView, x: Double, y: Double): Unit = {
    // the canvas hears the pointer at the root's points: the map sits at (10, 20)
    mouse(m.canvas, MouseEvent.MOUSE_PRESSED, x + 10.0, y + 20.0)
    mouse(m.canvas, MouseEvent.MOUSE_RELEASED, x + 10.0, y + 20.0)
    mouse(m.canvas, MouseEvent.MOUSE_CLICKED, x + 10.0, y + 20.0)
  }

  private def box(w: Double, h: Double): Component = new Container {
    size.width @= w
    size.height @= h
    backgroundColor @= Color.Red
  }

  override def afterAll(): Unit = {
    server.stop(0)
    super.afterAll()
  }

  "A map" should {
    "look where it is told, and say where that is" in {
      val m = map()
      onScreen(m) {
        fx(m.setView(LatLng(35.0, -97.0), 10.0))
        fx(m.zoom()) shouldBe 10.0 +- 1e-9
        fx(m.center().lat) shouldBe 35.0 +- 1e-9
        fx(m.center().lng) shouldBe -97.0 +- 1e-9
        // 512 pixels at zoom 10 is two tiles across: 360 / 512 of a degree each
        val b = fx(m.bounds())
        (b.east - b.west) shouldBe 360.0 / 1024.0 * 2.0 +- 1e-9
        b.contains(LatLng(35.0, -97.0)) shouldBe true
        val (x, y) = fx(m.toScreen(LatLng(35.0, -97.0)))
        x shouldBe 256.0 +- 1e-6
        y shouldBe 192.0 +- 1e-6
        fx(m.toLatLng(256.0, 192.0)).lat shouldBe 35.0 +- 1e-9
        // held within its zooms
        fx(m.maxZoom @= 15.0)
        fx(m.setView(LatLng(35.0, -97.0), 22.0))
        fx(m.zoom()) shouldBe 15.0 +- 1e-9
      }
    }
    "fit a box with room round it, even asked before it has a size" in {
      val m = fx(new MapView)
      val box = LatLngBounds(35.0, -98.0, 36.0, -97.0)
      fx(m.fitBounds(box, 20.0, animate = false))
      onScreen(m) {
        fx {
          m.size.width @= 400.0
          m.size.height @= 300.0
        }
        val shown = fx(m.bounds())
        shown.contains(box.southWest) shouldBe true
        shown.contains(box.northEast) shouldBe true
        // the box fills the view's narrower way but for the padding
        val (left, _) = fx(m.toScreen(LatLng(35.5, -98.0)))
        val (right, _) = fx(m.toScreen(LatLng(35.5, -97.0)))
        val (_, top) = fx(m.toScreen(LatLng(36.0, -97.5)))
        val (_, bottom) = fx(m.toScreen(LatLng(35.0, -97.5)))
        math.max((right - left) / 360.0, (bottom - top) / 260.0) shouldBe 1.0 +- 1e-6
        fx(m.center().lng) shouldBe -97.5 +- 1e-9
      }
    }
    "ask for the tiles in view and round it, nearest the middle first, a few at a time" in {
      gate = new CountDownLatch(1)
      asked.clear()
      val m = map()
      onScreen(m) {
        fx {
          m.setView(LatLng(0.0, 0.0), 3.0)
          m.tiles @= Some(source)
        }
        // (the queue is worked through once the turn is over; JavaFX fetches four at a time of the six)
        eventually(requests.length == 4) shouldBe true
        fx(m.tilesInFlight) shouldBe 6
        // 512 by 384 at zoom 3 and 128 pixels round it: four columns of four
        fx(m.tilesWaiting) shouldBe 10
        requests.toSet shouldBe Set("3/3/3.png", "3/4/3.png", "3/3/4.png", "3/4/4.png")
        sharpAsked shouldBe false
        // a move elsewhere, and closer, before the rest were asked for: they never are
        fx(m.setView(LatLng(60.0, 100.0), 4.0))
        gate.countDown()
        eventually(m.tilesInFlight + m.tilesWaiting == 0) shouldBe true
        val all = requests
        val old = Set("3/2/2", "3/3/2", "3/4/2", "3/5/2", "3/2/3", "3/5/3", "3/2/4", "3/5/4", "3/2/5", "3/3/5", "3/4/5",
          "3/5/5").map(_ + ".png")
        all.count(old.contains) should be <= 2
        all.length should be < 6 + 16
        eventually(m.tilesDrawn.length == 12) shouldBe true
        fx(m.tilesDrawn.forall(_.z == 4)) shouldBe true
      }
    }
    "show the tile above while the ones of a closer zoom come, and let go of the oldest past its cache" in {
      gate = new CountDownLatch(0)
      asked.clear()
      val m = map()
      onScreen(m) {
        fx {
          m.setView(LatLng(10.0, 10.0), 5.0)
          m.tiles @= Some(source)
        }
        eventually(m.tilesWaiting + m.tilesInFlight == 0 && m.tilesDrawn.nonEmpty) shouldBe true
        gate = new CountDownLatch(1)
        fx(m.setView(LatLng(10.0, 10.0), 6.0))
        // the zoom 6 tiles are on their way: zoom 5's are drawn, larger, in their place
        val standing = fx(m.tilesDrawn)
        standing.nonEmpty shouldBe true
        standing.forall(_.z == 5) shouldBe true
        gate.countDown()
        eventually(m.tilesWaiting + m.tilesInFlight == 0 && m.tilesDrawn.forall(_.z == 6)) shouldBe true
        // zooming out again, the tiles of 5 are still held: nothing more is asked for
        val before = requests.length
        fx(m.setView(LatLng(10.0, 10.0), 5.0))
        eventually(m.tilesDrawn.forall(_.z == 5)) shouldBe true
        requests.length shouldBe before
        // a cache of 20: panning round the world keeps no more than that, and what is drawn
        fx(m.tileCache @= 20)
        (0 until 8).foreach { i =>
          fx(m.setView(LatLng(10.0 + i * 5.0, 10.0 + i * 20.0), 6.0))
          eventually(m.tilesWaiting + m.tilesInFlight == 0) shouldBe true
        }
        fx(m.tilesHeld) should be <= math.max(20, fx(m.tilesDrawn.length))
      }
    }
    "keep its markers their own size at every zoom, their anchors at their places" in {
      val m = map()
      val at = LatLng(20.0, 30.0)
      fx(m.markers @= Vector(MapMarker("a", at, () => box(20.0, 10.0), MapAnchor.Bottom),
        MapMarker.dot("b", LatLng(21.0, 31.0))))
      onScreen(m) {
        List(4.0, 9.0, 14.5).foreach { z =>
          fx(m.setView(at, z))
          val c = fx(m.featureAt(256.0, 190.0))
          c.map(_.key) shouldBe Some("a")
          c.collect { case MarkerFeature(mk) => mk.key } shouldBe Some("a")
          // its bottom middle at the place, the middle of the map
          val (x, y) = fx(m.toScreen(at))
          fx(m.featureAt(x, y - 1.0)).map(_.key) shouldBe Some("a")
          fx(m.featureAt(x, y + 2.0)).map(_.key) shouldBe None
          fx(m.featureAt(x + 11.0, y - 5.0)).map(_.key) shouldBe None
        }
        // the dot, out of view, is not drawn
        fx(m.setView(at, 14.0))
        fx(m.shownMarkers) shouldBe 1
        fx(m.markersMade) shouldBe 2
      }
    }
    "find its shapes under the pointer, and draw each crisp at every zoom" in {
      val m = map()
      val ring = Vector(LatLng(1.0, 1.0), LatLng(1.0, 2.0), LatLng(0.0, 2.0), LatLng(0.0, 1.0))
      fx(m.shapes @= Vector(
        MapShape.polygon("square", ring, MapStyle(fill = Some(Color.Blue.withAlpha(0.3)), stroke = Some(Color.Blue))),
        MapShape.line("line", Vector(LatLng(0.5, 0.0), LatLng(0.5, 3.0)), MapStyle(stroke = Some(Color.Red), width = 3.0,
          dash = Some(MapDash(4.0, 2.0)))),
        MapShape.polygon("ghost", ring, MapStyle(stroke = Some(Color.Gray))).copy(hits = false),
        MapShape.polygon("close", ring, MapStyle(stroke = Some(Color.Gray))).copy(from = 12.0)))
      onScreen(m) {
        fx(m.setView(LatLng(0.5, 1.5), 8.0))
        fx(m.drawnShapes) shouldBe 3
        val (x, y) = fx(m.toScreen(LatLng(0.8, 1.5)))
        fx(m.featureAt(x, y)).map(_.key) shouldBe Some("square")
        // the line, within a few pixels of it, over the square
        val (lx, ly) = fx(m.toScreen(LatLng(0.5, 1.5)))
        fx(m.featureAt(lx, ly + 4.0)).map(_.key) shouldBe Some("line")
        fx(m.featureAt(lx, ly + 9.0)).map(_.key) shouldBe Some("square")
        val (ox, oy) = fx(m.toScreen(LatLng(0.8, 2.5)))
        fx(m.featureAt(ox, oy)) shouldBe None
        // close in, the shape from zoom 12 is drawn and found; its lines stay 3 pixels wide
        fx(m.setView(LatLng(0.5, 1.5), 13.0))
        fx(m.drawnShapes) shouldBe 4
        val (cx, cy) = fx(m.toScreen(LatLng(0.8, 1.5)))
        fx(m.featureAt(cx, cy)).map(_.key) shouldBe Some("close")
      }
    }
    "gather crowded markers into clusters, and glide in to one that is clicked" in {
      val m = map()
      var clicks: List[MapClick] = Nil
      fx(m.clicked.attach(c => clicks = c :: clicks))
      val crowd = (0 until 50).toVector.map(i => MapMarker.dot("w" + i.toString, LatLng(40.0 + (i % 7) * 0.01,
        -100.0 + (i / 7) * 0.01)))
      fx {
        m.markers @= crowd :+ MapMarker.dot("alone", LatLng(-20.0, 60.0)).copy(clusters = false)
        m.cluster @= Some(MapCluster(until = 12.0))
      }
      onScreen(m) {
        fx(m.setView(LatLng(40.03, -99.97), 5.0))
        fx(m.shownMarkers) shouldBe 1
        val found = fx(m.featureAt(256.0, 192.0))
        found.collect { case ClusterFeature(_, ms, _) => ms.length } shouldBe Some(50)
        clickAt(m, 256.0, 192.0)
        clicks.headOption.flatMap(_.feature).map(_.isInstanceOf[ClusterFeature]) shouldBe Some(true)
        // in to where they are drawn apart
        fx(m.zoom()) should be > 9.0
        fx(m.bounds().contains(LatLng(40.0, -100.0))) shouldBe true
        fx(m.setView(LatLng(40.03, -99.97), 12.0))
        fx(m.shownMarkers) shouldBe 50
        fx(m.cluster @= None)
        fx(m.setView(LatLng(40.03, -99.97), 5.0))
        fx(m.shownMarkers) shouldBe 50
      }
    }
    "hear a click on a marker or a shape, and on nothing, with the place" in {
      val m = map()
      var clicks: List[MapClick] = Nil
      fx(m.clicked.attach(c => clicks = c :: clicks))
      val ring = Vector(LatLng(1.0, -1.0), LatLng(1.0, 1.0), LatLng(-1.0, 1.0), LatLng(-1.0, -1.0))
      fx {
        m.shapes @= Vector(MapShape.polygon("land", ring, MapStyle(fill = Some(Color.Green)), label = "Land"))
        m.markers @= Vector(MapMarker.pin("pin", LatLng(0.5, 0.5), label = "Pin"))
      }
      onScreen(m) {
        fx(m.setView(LatLng(0.0, 0.0), 7.0))
        val (px, py) = fx(m.toScreen(LatLng(0.5, 0.5)))
        clickAt(m, px, py - 20.0)
        clicks.head.feature.map(_.key) shouldBe Some("pin")
        clickAt(m, 256.0, 192.0)
        clicks.head.feature.collect { case ShapeFeature(s) => s.label } shouldBe Some("Land")
        clicks.head.at.lat shouldBe 0.0 +- 1e-6
        clickAt(m, 20.0, 20.0)
        clicks.head.feature shouldBe None
        clicks.head.at.lat should be > 1.0
        // a drag is no click, and pans
        val before = fx(m.center())
        mouse(m.canvas, MouseEvent.MOUSE_PRESSED, 200.0, 200.0)
        mouse(m.canvas, MouseEvent.MOUSE_DRAGGED, 260.0, 230.0)
        mouse(m.canvas, MouseEvent.MOUSE_RELEASED, 260.0, 230.0)
        mouse(m.canvas, MouseEvent.MOUSE_CLICKED, 260.0, 230.0)
        clicks.length shouldBe 3
        fx(m.center().lng) should be < before.lng
        // hovering the pin says so
        mouse(m.canvas, MouseEvent.MOUSE_MOVED, 10.0 + fx(m.toScreen(LatLng(0.5, 0.5)))._1, 20.0 + fx(m.toScreen(LatLng(0.5, 0.5)))._2 - 20.0)
        fx(m.hovered()).map(_.key) shouldBe Some("pin")
      }
    }
    "zoom by its keys and its buttons, a whole zoom at a time" in {
      val m = map()
      onScreen(m) {
        fx(m.setView(LatLng(0.0, 0.0), 6.3))
        fx(m.zoomBy(1.0))
        fx(m.zoom()) shouldBe 7.0 +- 1e-9
        fx(m.canvas.focus())
        fx(javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "-", "-", KeyCode.MINUS, false, false,
          false, false)))
        fx(m.zoom()) should be < 7.0
        fx(m.center().lat) shouldBe 0.0 +- 1e-6
      }
    }
  }
}
