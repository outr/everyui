package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Component, Container, Row, TextView}
import everyui.component.types.FontWeight
import everyui.map.*
import everyui.map.MapFeature.{ClusterFeature, MarkerFeature, ShapeFeature}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The map demo's one screen (MapDemo): a bar with the map's name, what was last clicked, and Fit, Clusters and Units,
  * over the map. */
class MapScreen extends UIScreen {
  override def manager: ScreenManager = MapDemo
  override def title: String = "Map"
  override def path: URLPath = path"/map"

  private val palette = List("26A69A", "42A5F5", "AB47BC", "EF5350", "FFA726", "66BB6A", "5C6BC0", "EC407A")
    .map(h => Color.fromHex(h + "FF"))
  private val owners = List("Allen", "Baker", "Carter", "Dunn", "Ellis", "Foster", "Grant", "Hayes")

  // the survey's north west corner, and a section's side (a mile) in degrees there
  private val north = 35.62
  private val west = -97.78
  private val tall = 1609.344 / 111320.0
  private val wide = tall / math.cos(35.5 * math.Pi / 180.0)

  private val said = Var("Drag, wheel or pinch to move; hover for names; a click says what is there")
  private val map = new MapView

  override def defaultFocus(): Component = map.canvas

  override def createUI(): Task[Unit] = Task {
    val sections = arg("sections", 300)
    val dots = arg("markers", 1000)
    map.position.top @= 56.0
    map.size.width := container.size.width()
    map.size.height := container.size.height() - 56.0
    map.tiles @= Some(source)
    map.cluster @= (if (Platform.launchArg("cluster") == "0") None else Some(MapCluster()))
    map.shapes @= survey(sections) ++ Vector(countyLine(sections), road)
    map.markers @= wells(dots) ++ numbers(sections) ++ pins
    map.legend @= Some(legend)
    map.legendCorner @= MapCorner.BottomRight
    map.clicked.attach(c => said @= clicked(c))
    if (Platform.launchArg("trace") == "1") Val((map.zoom(), map.center())).attach(_ => Platform.log("map view zoom " + round(map.zoom(), 3) +
      " at " + round(map.center().lat, 5) + ", " + round(map.center().lng, 5)))
    map.fitBounds(extent(sections), 24.0, animate = false)

    val title = TextView.line("Map", 18.0, Theme.text(), FontWeight.SemiBold)
    val status = TextView.line(said(), 12.0, Theme.muted(), widest = math.max(80.0, container.size.width() - 420.0))
    val fit = new Button("Fit", () => map.fitBounds(extent(sections), 24.0), style = ButtonStyle.Outlined)
    val clusters = new Button("Clusters", () => map.cluster @= (if (map.cluster().isEmpty) Some(MapCluster()) else None),
      style = ButtonStyle.Outlined)
    val units = new Button("Units", () => map.imperial @= !map.imperial(), style = ButtonStyle.Outlined)
    val buttons = Row(8.0, middle = true)(fit, clusters, units)
    title.position.left @= 16.0
    title.position.top := (56.0 - title.size.height()) / 2.0
    status.position.left := title.position.left() + title.size.width() + 16.0
    status.position.top := (56.0 - status.size.height()) / 2.0
    status.visible := container.size.width() >= 560.0
    buttons.position.left := container.size.width() - buttons.size.width() - 12.0
    buttons.position.top := (56.0 - buttons.size.height()) / 2.0
    container.children ++= List(map, title, status, buttons)
    Platform.after(0.5, () => Platform.log("map ok " + map.shapes().length.toString + " shapes, " +
      map.markers().length.toString + " markers, zoom " + round(map.zoom(), 2)))
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def round(d: Double, places: Int): String = {
    val k = math.pow(10.0, places)
    (math.floor(d * k + 0.5) / k).toString
  }

  private def arg(name: String, fallback: Int): Int = {
    val asked = Platform.launchArg(name)
    if (asked.nonEmpty && asked.forall(_.isDigit)) asked.toInt else fallback
  }

  // the tiles the launch asks for: drawn here, a server's template, or OpenStreetMap's own
  private def source: TileSource = Platform.launchArg("tiles") match {
    case "osm" => TileSource.openStreetMap
    case t if t.contains("{z}") => TileSource.template(t, "Local test tiles", Nil)
    case _ => MapTiles.drawn
  }

  private def clicked(c: MapClick): String = {
    val where = (math.floor(c.at.lat * 10000.0) / 10000.0).toString + ", " +
      (math.floor(c.at.lng * 10000.0) / 10000.0).toString
    c.feature match {
      case Some(ShapeFeature(s)) =>
        Platform.log("map clicked shape " + s.key)
        s.label
      case Some(MarkerFeature(m)) =>
        Platform.log("map clicked marker " + m.key)
        m.label
      case Some(ClusterFeature(key, ms, _)) =>
        Platform.log("map clicked cluster " + key + " of " + ms.length.toString)
        ms.length.toString + " wells: zooming in"
      case None =>
        Platform.log("map clicked nothing at " + where)
        "Nothing at " + where
    }
  }

  private def across(n: Int): Int = math.max(1, math.min(20, n))

  // the whole survey, a little round it
  private def extent(n: Int): LatLngBounds = {
    val down = (n + across(n) - 1) / across(n)
    LatLngBounds(north - down * tall - tall / 2.0, west - wide / 2.0, north + tall / 2.0, west + across(n) * wide + wide / 2.0)
  }

  private def corner(row: Int, col: Int): LatLng = LatLng(north - row * tall, west + col * wide)

  // sections of a mile, in rows of twenty, each an owner's colour (every seventh unowned)
  private def survey(n: Int): Vector[MapShape] = (0 until n).toVector.filter(_ % 7 != 3).map { i =>
    val row = i / across(n)
    val col = i % across(n)
    val owner = (row * 3 + col / 2) % owners.length
    val colour = palette(owner)
    val ring = Vector(corner(row, col), corner(row, col + 1), corner(row + 1, col + 1), corner(row + 1, col))
    MapShape.polygon("section-" + (i + 1).toString, ring,
      MapStyle(fill = Some(colour.withAlpha(0.35)), stroke = Some(colour.withAlpha(0.8)), width = 1.5),
      label = "Section " + (i % 36 + 1).toString + ", " + owners(owner) + " (" + ((i * 7) % 23 + 1).toString + " records)")
  }

  private def countyLine(n: Int): MapShape = {
    val b = extent(n)
    MapShape("county", Vector(Vector(LatLng(b.north, b.west), LatLng(b.north, b.east), LatLng(b.south, b.east),
      LatLng(b.south, b.west))), closed = true, MapStyle(stroke = Some(Theme.text().withAlpha(0.6)), width = 2.0,
      dash = Some(MapDash(8.0, 5.0))), label = "County line", hits = false)
  }

  private def road: MapShape = MapShape.line("road", Vector(LatLng(north + 0.06, west - 0.05), LatLng(north - 0.05, west + 0.08),
    LatLng(north - 0.09, west + 0.2), LatLng(north - 0.2, west + 0.26), LatLng(north - 0.3, west + 0.42)),
    MapStyle(stroke = Some(Color.fromHex("E65100FF")), width = 3.0), label = "State Highway 3")

  private def pins: Vector[MapMarker] = Vector(("Office", 2, 3), ("Yard", 6, 14), ("North well pad", 1, 17),
    ("Tank battery", 11, 8), ("Survey marker", 13, 1)).map { case (name, row, col) =>
    MapMarker.pin(name.toLowerCase.replace(" ", "-"), LatLng(north - (row + 0.5) * tall, west + (col + 0.5) * wide),
      Color.fromHex("D32F2FFF"), label = name).copy(clusters = false)
  }

  // wells scattered over the survey and round it, the same every time
  private def wells(n: Int): Vector[MapMarker] = {
    var seed = 12345L
    val next: () => Double = () => {
      seed = (seed * 1103515245L + 12345L) % 2147483648L
      seed.toDouble / 2147483648.0
    }
    (0 until n).toVector.map { i =>
      val at = LatLng(north + 0.05 - next() * 0.32, west - 0.05 + next() * 0.46)
      MapMarker.dot("well-" + (i + 1).toString, at, palette(i % palette.length), 5.0, label = "Well " + (i + 1).toString)
    }
  }

  // each section's number, from zoom 13 in, for reading only
  private def numbers(n: Int): Vector[MapMarker] = (0 until n).toVector.filter(_ % 7 != 3).map { i =>
    val row = i / across(n)
    val col = i % across(n)
    MapMarker.text("number-" + (i + 1).toString, LatLng(north - (row + 0.5) * tall, west + (col + 0.5) * wide),
      (i % 36 + 1).toString).copy(hits = false, clusters = false, from = 13.0)
  }

  private def legend: Component = {
    val rows = owners.take(4).zipWithIndex.map { case (name, i) =>
      val swatch = new Container {
        size.width @= 12.0
        size.height @= 12.0
        radius @= 2.0
        backgroundColor @= palette(i).withAlpha(0.6)
        borderWidth @= 1.0
        borderColor @= palette(i)
      }
      val words = TextView(name, 12.0, Theme.text())
      Row(6.0, middle = true)(swatch, words)
    }
    val heading = TextView("Owners", 12.0, Theme.muted())
    heading.font.weight @= FontWeight.SemiBold
    val column = everyui.component.Column(4.0)((heading :: rows)*)
    column.position.left @= 10.0
    column.position.top @= 8.0
    new Container {
      size.width := column.size.width() + 20.0
      size.height := column.size.height() + 16.0
      radius @= 6.0
      backgroundColor := Theme.surface().withAlpha(0.92)
      borderWidth @= 1.0
      borderColor := Theme.border()
      children += column
    }
  }
}
