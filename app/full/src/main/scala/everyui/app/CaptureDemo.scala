package everyui.app

import everyui.Color
import everyui.capture.{Snapshot, SnapshotImage}
import everyui.component.{Button, ButtonStyle, Column, Container, ImageView, MaterialIconView, Row, ScrollContainer, TextView}
import everyui.draw.{Drawing, Figure, Path}
import everyui.file.{Files, SaveResult}
import everyui.font.MaterialIcon
import everyui.layout.FlowLayout
import everyui.list.{Decoration, VirtualList}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Pictures the app takes of itself (`everyui.capture.Snapshot`): a panel of words, a picture, a chart (a `Drawing`)
  * and a list, taken as a PNG; the whole window; and the chart alone at three times its size, straight from its
  * figures. The last picture shows beside the panel with its size, and Save hands it to the person (`Files.save`).
  * Each is logged (`capture panel 480x540 51234 bytes`), and the panel's place once built (`capture at 16,64 480x540`).
  * `probe=1` takes each in turn by itself; `scale=2` takes the panel and the window at twice their size; `photo=<url>`
  * shows a picture of another origin beside the logo; `theme=light` shows the light palette. */
class CaptureDemo {
  private val narrow = ui.size.width() < 1040.0
  private val panelWidth = math.min(480.0, ui.size.width() - 32.0)
  private val shot: Var[Option[SnapshotImage]] = Var(None)
  private val times = Platform.launchArg("scale").toDoubleOption.getOrElse(1.0)
  private val said: Var[String] = Var(if (Snapshot.supported) "Nothing taken yet" else "No pictures on this platform")

  private val chart: Drawing = new Drawing {
    size.width @= panelWidth - 32.0
    size.height @= 140.0
  }
  chart.figures := figures(chart.size.width(), chart.size.height(), Theme.accent(), Theme.muted())

  private val words: Var[Vector[String]] =
    Var(Vector("Aurora", "Basalt", "Cinder", "Dune", "Ember", "Fjord", "Glacier", "Harbor", "Isle", "Juniper"))
  private val list: VirtualList[String] = VirtualList.text(words, (w: String) => w, (w: String) => w,
    decorate = (w: String) => Decoration(icon = Some(if (w.length % 2 == 0) MaterialIcon.Star else MaterialIcon.Home)))
  list.size.width @= panelWidth - 32.0
  list.size.height @= 132.0

  /** What is taken: words, a picture, a chart and a list, on a raised ground with rounded corners. */
  private val panel: Container = {
    val logo = ImageView("/images/logo.png", 48.0)
    // a picture of another origin, which is drawn only where it lets the page read it
    val photo = Platform.launchArg("photo")
    val pictures = if (photo.isEmpty) Row()(logo) else Row(8.0, middle = true)(logo, ImageView(photo, 48.0))
    val body = Column(12.0, padding = 16.0)(
      Row(12.0, middle = true)(pictures, Column(2.0)(TextView("Quarterly report", 20.0),
        TextView("Snapshot · naïve café", 13.0, Theme.muted()))),
      TextView.paragraph("A panel with words in two faces, a picture, a chart drawn from its figures, and a list " +
        "scrolled a row in: each is drawn into the picture as the screen shows it.", panelWidth - 32.0, 14.0),
      Row(8.0, middle = true)(new MaterialIconView(MaterialIcon.Info, 20.0) {
        color := Theme.accent()
      }, TextView("Material Icons, drawn as a face", 13.0)),
      new TextView {
        content @= "val shot = Snapshot.component(panel)"
        font.family @= "Roboto Mono"
        font.size @= 13.0
        color := Theme.accentLit()
      },
      chart,
      list)
    new Container {
      backgroundColor := Theme.raised()
      radius @= 12.0
      size.width @= panelWidth
      children += body
      size.height := body.size.height()
    }
  }

  private val preview = new ImageView
  preview.image.attach(i => fit(i.width, i.height))

  private val controls = Column(12.0)(
    TextView("Snapshots", 22.0),
    new Container {
      layout @= Some(FlowLayout(10.0, 10.0))
      size.width @= panelWidth
      children ++= List(
        new Button("Take the panel", () => take("panel", Snapshot.component(panel, times)), ButtonStyle.Filled),
        new Button("Take the window", () => take("window", Snapshot.window(times))),
        new Button("The chart at 3x", () => take("chart", Snapshot.drawing(chart, 3.0, Some(Theme.raised())))),
        new Button("Save", () => shot().foreach(i => i.save("snapshot.png").map(r => tell("save", saved(r))).start())))
    },
    TextView.paragraph(said(), panelWidth, 13.0, Theme.muted()),
    preview)

  private val page = new Container {
    size.width := ui.size.width()
  }
  page.children += panel
  page.children += controls
  panel.position.left @= 16.0
  panel.position.top @= 16.0
  controls.position.left @= (if (narrow) 16.0 else 32.0 + panelWidth)
  controls.position.top := (if (narrow) 32.0 + panel.size.height() else 16.0)
  page.size.height := math.max(panel.size.height(), controls.position.top() + controls.size.height()) + 32.0

  private val scroll = new ScrollContainer {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }
  scroll.content.children += page
  ui.children += scroll

  // the list a row in, so the picture shows a scroll as it stands
  Platform.after(0.5, () => {
    list.scrollTo(1, align = 0.0)
    Platform.log("capture at 16,16 " + panelWidth.toInt.toString + "x" + panel.size.height().toInt.toString)
  })
  if (Platform.launchArg("probe") == "1") Platform.after(1.5, () => probe())
  if (Platform.freezeSeconds() >= 0.0) Platform.after(3.0, () => Platform.frozen())

  private def take(what: String, taking: everyui.async.Task[Option[SnapshotImage]]): Unit =
    taking.map {
      case Some(image) =>
        shot().foreach(old => Files.release(old.preview))
        shot @= Some(image)
        preview.image @= new everyui.image.Image(image.preview, image.width.toDouble, image.height.toDouble)
        tell(what, image.width.toString + "x" + image.height.toString + " " + image.png.length.toString + " bytes")
      case None => tell(what, "none taken")
    }.start()

  /** The panel, the window and the chart taken one after another. */
  private def probe(): Unit =
    Snapshot.component(panel, times).flatMap { p =>
      tell("panel", p.map(i => i.width.toString + "x" + i.height.toString + " " + i.png.length.toString + " bytes").getOrElse("none taken"))
      Snapshot.window(times)
    }.flatMap { w =>
      tell("window", w.map(i => i.width.toString + "x" + i.height.toString + " " + i.png.length.toString + " bytes").getOrElse("none taken"))
      Snapshot.drawing(chart, 3.0, Some(Theme.raised()))
    }.map { c =>
      c.foreach { image =>
        shot @= Some(image)
        preview.image @= new everyui.image.Image(image.preview, image.width.toDouble, image.height.toDouble)
      }
      tell("chart", c.map(i => i.width.toString + "x" + i.height.toString + " " + i.png.length.toString + " bytes").getOrElse("none taken"))
    }.start()

  /** The preview no wider than the panel, at the picture's own proportions. */
  private def fit(width: Double, height: Double): Unit = if (width > 0.0 && height > 0.0) {
    val across = math.min(panelWidth, width)
    preview.size.width @= across
    preview.size.height @= height * across / width
  }

  private def tell(what: String, words: String): Unit = {
    said @= what + ": " + words
    Platform.log("capture " + what + " " + words)
  }

  private def saved(r: SaveResult): String = r match {
    case SaveResult.Saved(name) => "Saved " + name.getOrElse("(to the browser's downloads)")
    case SaveResult.Cancelled => "Cancelled"
    case SaveResult.SaveFailed(why) => "Not saved: " + why
  }

  /** Bars of twelve months, a line through their tops and a dot on each, over faint rules. */
  private def figures(width: Double, height: Double, accent: Color, muted: Color): List[Figure] = {
    val values = List(12.0, 18.0, 15.0, 24.0, 30.0, 27.0, 35.0, 31.0, 40.0, 38.0, 46.0, 52.0)
    val step = width / values.size
    val top = 56.0
    def y(v: Double): Double = height - 4.0 - (height - 12.0) * v / top
    val rules = List(0.25, 0.5, 0.75).map(f => Figure(Path.line(0.0, height * f, width, height * f),
      stroke = Some(muted.withAlpha(0.35)), width = 1.0))
    val bars = values.zipWithIndex.map { case (v, i) =>
      Figure(Path.roundRect(i * step + step * 0.2, y(v), step * 0.6, height - 4.0 - y(v), 3.0), fill = Some(accent.withAlpha(0.35)))
    }
    val points = values.zipWithIndex.map { case (v, i) => (i * step + step / 2.0, y(v)) }
    val line = Figure(Path.polyline(points), stroke = Some(accent), width = 2.5)
    val dots = points.map { case (x, py) => Figure(Path.circle(x, py, 3.5), fill = Some(accent), stroke = Some(Color.White), width = 1.5) }
    rules ++ bars ++ (line :: dots)
  }
}

object CaptureDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    Theme.family @= "Open Sans"
    ui.backgroundColor := Theme.background()
    // the words measured in their own faces
    Platform.whenFontsReady(() => new CaptureDemo)
  }
}
