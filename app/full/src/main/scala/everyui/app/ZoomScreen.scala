package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Component, Container, Row, TextView}
import everyui.component.types.FontWeight
import everyui.render.Platform
import everyui.net.*
import everyui.theme.Theme
import everyui.zoom.{ZoomCanvas, ZoomItem, ZoomPoint}
import reactify.*

/** The zoom demo's one screen (ZoomDemo): a bar with the map's name, what was last clicked, and Fit and Topology, over
  * the canvas. */
class ZoomScreen extends UIScreen {
  override def manager: ScreenManager = ZoomDemo
  override def title: String = "Project map"
  override def path: URLPath = path"/zoom"

  private val sections = List("Core", "Network", "Storage", "Interface", "Tooling", "Topology")
  private val hues = List(210.0, 160.0, 35.0, 280.0, 340.0, 190.0)

  private val picked = Var[Option[Int]](None)
  private val said = Var("Drag, wheel or pinch to move; Ctrl and the wheel, or + and -, to zoom; a click goes in")
  private val canvas = new ZoomCanvas

  override def defaultFocus(): Component = canvas

  override def createUI(): Task[Unit] = Task {
    canvas.position.top @= 56.0
    canvas.size.width := container.size.width()
    canvas.size.height := container.size.height() - 56.0
    canvas.backgroundColor := Theme.background()
    canvas.maxScale @= 40.0
    canvas.clicked.attach(p => said @= clicked(p))

    val title = TextView.line("Project map", 18.0, Theme.text(), FontWeight.SemiBold)
    val status = TextView.line(said(), 12.0, Theme.muted(), widest = math.max(80.0, container.size.width() - 420.0))
    val fit = new Button("Fit", () => canvas.fit(), style = ButtonStyle.Outlined)
    val topology = new Button("Topology", () => canvas.zoomToItem("Topology", "map"), style = ButtonStyle.Outlined)
    val buttons = Row(8.0, middle = true)(fit, topology)
    title.position.left @= 16.0
    title.position.top := (56.0 - title.size.height()) / 2.0
    status.position.left := title.position.left() + title.size.width() + 16.0
    status.position.top := (56.0 - status.size.height()) / 2.0
    status.visible := container.size.width() >= 560.0
    buttons.position.left := container.size.width() - buttons.size.width() - 12.0
    buttons.position.top := (56.0 - buttons.size.height()) / 2.0
    container.children ++= List(canvas, title, status, buttons)
    Platform.after(0.5, () =>
      Platform.log("zoom ok " + count(canvas.items()).toString + " items, " + canvas.showing.toString + " drawn"))
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  // the world given once the screen is showing: a name cut short to fit its box is measured on the page (the web
  // measures nothing that is not on it)
  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    if (canvas.items().isEmpty)
      canvas.items @= sections.zipWithIndex.toVector.flatMap { case (name, i) => section(name, i) }
  }

  // every item, however deep, counted
  private def count(items: Vector[ZoomItem]): Int = {
    var n = 0
    var pending = items.toList
    while (pending.nonEmpty) {
      n += 1
      pending = pending.head.children().toList ++ pending.tail
    }
    n
  }

  private def clicked(p: ZoomPoint): String = p.hit match {
    case Some(hit) if hit.key == "map" =>
      val figure = hit.figure(Topology.figures(None))
      picked @= figure
      Platform.log("zoom clicked map " + figure.map(_.toString).getOrElse("none"))
      figure.map(i => Topology.name(i)).getOrElse("Nothing there: a router or a link is picked by a click")
    case Some(hit) =>
      Platform.log("zoom clicked " + hit.path.mkString("/"))
      canvas.zoomToItem(hit.path*)
      hit.path.mkString("  ›  ")
    case None => "Nothing there"
  }

  private def tint(i: Int, strength: Double): Color =
    Color.fromHSB(hues(i) / 360.0, 0.55, 0.85).blend(Theme.surface(), 1.0 - strength)

  // a section: its box, part of the picture, and over it its name, laid out at the size the box has on the screen so
  // it can be read however far out the view is (a later item, and one that does not scale, is drawn over it)
  private def section(name: String, i: Int): Vector[ZoomItem] = {
    // three across, or two where the screen is narrow
    val across = if (container.size.width() < 700.0) 2 else 3
    val x = (i % across) * 1120.0
    val y = (i / across) * 820.0
    val inside = if (name == "Topology") () => Vector(ZoomItem("map", 0.03, 0.15, 0.94, 0.82, () => Topology(picked)))
      else () => (0 until 9).toVector.map(m => module(name, i, m))
    Vector(ZoomItem(name, x, y, 1000.0, 700.0, () => sectionBox(i), children = inside, childrenFrom = 300.0),
      ZoomItem(name + " name", x, y, 1000.0, 84.0, () => sectionName(name), scales = false, hits = false))
  }

  private def sectionBox(i: Int): Component = new Container {
    backgroundColor := tint(i, 0.18)
    borderColor := tint(i, 0.6)
    borderWidth @= 3.0
    radius @= 28.0
  }

  // the name, larger as the box is, from 12 to 30 pixels
  private def sectionName(name: String): Component = new Container {
    val heading: TextView = TextView(name, 12.0, Theme.text())
    heading.font.weight @= FontWeight.SemiBold
    heading.font.size := math.max(12.0, math.min(30.0, size.width() / 20.0))
    heading.position.left := math.max(6.0, size.width() * 0.035)
    heading.position.top := math.max(4.0, (size.height() - heading.size.height()) / 2.0)
    children += heading
  }

  private def module(section: String, i: Int, m: Int): ZoomItem = {
    val name = List("api", "model", "codec", "cache", "events", "io", "util", "config", "tests")(m)
    ZoomItem(name, 0.04 + (m % 3) * 0.32, 0.17 + (m / 3) * 0.28, 0.28, 0.24,
      () => card(name, "8 files", i, 0.3, 14.0, 10.0),
      children = () => (0 until 8).toVector.map(f => file(name, i, f)), childrenFrom = 420.0, scales = false)
  }

  private def file(module: String, i: Int, f: Int): ZoomItem = {
    val name = List("Reader", "Writer", "Parser", "Node", "Store", "Index", "Route", "Spec")(f) + ".scala"
    ZoomItem(name, 0.03 + (f % 4) * 0.245, 0.2 + (f / 4) * 0.4, 0.21, 0.34,
      () => card(name, "6 symbols", i, 0.45, 12.0, 8.0),
      children = () => (0 until 6).toVector.map(s => symbol(name, i, s)), childrenFrom = 220.0, scales = false)
  }

  private def symbol(file: String, i: Int, s: Int): ZoomItem = {
    val name = List("def read", "def write", "val size", "class Entry", "object Codec", "def close")(s)
    ZoomItem(name, 0.05 + (s % 2) * 0.47, 0.22 + (s / 2) * 0.26, 0.43, 0.2, () => card(name, "", i, 0.7, 11.0, 6.0),
      scales = false)
  }

  // a box laid out at its size on the screen: its name at the top, and what it holds under that where there is room,
  // each cut short to fit
  private def card(name: String, detail: String, i: Int, strength: Double, points: Double, pad: Double): Component =
    new Container {
      backgroundColor := tint(i, strength)
      borderColor := tint(i, math.min(1.0, strength + 0.3))
      borderWidth @= 1.0
      radius @= 6.0
      val label: TextView = TextView(name, points, Theme.text())
      label.maxWidth := math.max(0.0, size.width() - pad * 2.0)
      label.position.left @= pad
      label.position.top @= pad * 0.6
      label.visible := size.height() > points + pad
      val more: TextView = TextView(detail, points - 2.0, Theme.muted())
      more.maxWidth := math.max(0.0, size.width() - pad * 2.0)
      more.position.left @= pad
      more.position.top := label.position.top() + label.size.height() + 2.0
      more.visible := detail.nonEmpty && size.height() > more.position.top() + more.size.height() + pad
      children ++= List(label, more)
    }
}
