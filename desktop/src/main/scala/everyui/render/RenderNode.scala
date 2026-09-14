package everyui.render

import javafx.animation.AnimationTimer
import javafx.geometry.VPos
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.{Background, BackgroundFill, Pane}
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontSmoothingType, FontWeight, Text}
import javafx.scene.{Node, Scene}
import everyui.font.{Face, Faces}

import java.io.File
import scala.collection.mutable

/** **The one invention.** The abstract render node, JavaFX edition: a node in JavaFX's retained scene graph — a
  * `Text`, an `ImageView`, or a `Pane` holding other nodes.
  *
  * Signature-for-signature the same as its DOM, SceneGraph and Android twins. Units are JavaFX's pixels, which the OS
  * scales on a HiDPI display as a browser scales CSS pixels. A `Pane` never moves its children, so each node is placed
  * absolutely by its layout position, as on the other backends.
  */
class RenderNode(val fx: Node):
  private var left: Double = 0.0
  private var top: Double = 0.0
  private var rotation: Double = 0.0
  private var explicitWidth: Double = -1.0
  private var explicitHeight: Double = -1.0
  private var face: Face = Faces.OpenSansRegular
  private var fontSize: Double = 32.0

  def getText(): String = fx match
    case t: Text => t.getText
    case _ => ""

  def setText(text: String): Unit = fx match
    case t: Text => t.setText(text)
    case _ => ()

  def getX(): Double = left
  def setX(x: Double): Unit =
    left = x
    fx.setLayoutX(x)

  def getY(): Double = top
  def setY(y: Double): Unit =
    top = y
    placeY()

  /** Radians, clockwise, about the node's own centre — JavaFX's `rotate` is degrees, clockwise, about the centre of
    * the node's layout bounds. */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit =
    rotation = radians
    fx.setRotate(math.toDegrees(radians))

  def setFontSize(size: Double): Unit = fx match
    case t: Text =>
      fontSize = size
      t.setFont(new Font(t.getFont.getName, size))
      placeY()
    case _ => ()

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = fx match
    case t: Text => t.setFill(Color.web(hex))
    case _ => ()

  /** A bundled face (`assets/fonts/`): family, CSS weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = fx match
    case t: Text =>
      face = Faces(family, weight)
      fontSize = size
      t.setFont(
        if family.nonEmpty then Host.font(face, size)
        else Font.font("System", if weight >= 600 then FontWeight.SEMI_BOLD else FontWeight.NORMAL, size)
      )
      placeY()
    case _ => ()

  /** The laid-out size in pixels. Text is measured with its real font (its advance width) and is one shared line
    * (everyui.font.Face) tall. */
  def getWidth(): Double =
    if explicitWidth >= 0.0 then explicitWidth
    else fx match
      case t: Text => t.getLayoutBounds.getWidth
      case _ => 0.0

  def getHeight(): Double =
    if explicitHeight >= 0.0 then explicitHeight
    else fx match
      case _: Text => face.lineHeight(fontSize)
      case _ => 0.0

  def append(child: RenderNode): Unit = fx match
    case p: Pane => p.getChildren.add(child.fx)
    case _ => ()

  /** Detach from the parent, if attached. */
  def remove(): Unit = fx.getParent match
    case p: Pane => p.getChildren.remove(fx)
    case _ => ()

  def setWidth(width: Double): Unit =
    explicitWidth = width
    fx match
      case p: Pane =>
        p.setMinWidth(width)
        p.setPrefWidth(width)
        p.setMaxWidth(width)
      case i: ImageView => i.setFitWidth(width)
      case _ => ()

  def setHeight(height: Double): Unit =
    explicitHeight = height
    fx match
      case p: Pane =>
        p.setMinHeight(height)
        p.setPrefHeight(height)
        p.setMaxHeight(height)
      case i: ImageView => i.setFitHeight(height)
      case _ => ()

  /** `"#rrggbb"`, or `""` for transparent. */
  private var background: String = ""
  private var radius: Double = 0.0
  def setBackgroundColor(hex: String): Unit =
    background = hex
    paintBackground()
  def setRadius(r: Double): Unit =
    radius = r
    paintBackground()
  private def paintBackground(): Unit = fx match
    case p: Pane => p.setBackground(if background.isEmpty then null else
      new Background(new BackgroundFill(Color.web(background), if radius > 0.0 then new javafx.scene.layout.CornerRadii(radius) else null, null)))
    case _ => ()

  /** Every node is placed by its layout position, so there is no position type to set. */
  def setPositionType(name: String): Unit = ()

  /** Text nodes are single lines. */
  def setWhiteSpace(value: String): Unit = ()

  def setImageSource(uri: String): Unit = fx match
    case i: ImageView => if uri.nonEmpty then i.setImage(Host.image(uri))
    case _ => ()

  /** A `Text` is placed by its baseline: put that where the shared line box has it. */
  private def placeY(): Unit = fx match
    case _: Text => fx.setLayoutY(top + face.baseline(fontSize))
    case _ => fx.setLayoutY(top)

/** What the launcher (everyui.desktop.DesktopApp) hands the backend: the scene, its root, and how it was run. */
object Host:
  var scene: Scene = null
  var root: Pane = null
  var assets: File = new File("assets")
  var freeze: Double = -1.0
  var screenshot: String = ""
  /** Every `--name=value` on the command line. */
  var args: Map[String, String] = Map.empty

  private val fontNames = mutable.Map.empty[String, String]
  private val images = mutable.Map.empty[String, Image]

  /** A bundled face at `size`. JavaFX registers a font file by loading it once; after that it is found by name. */
  def font(face: Face, size: Double): Font =
    val name = fontNames.getOrElseUpdate(face.file, {
      val loaded = Font.loadFont(new File(assets, "fonts/" + face.file).toURI.toString, size)
      if loaded == null then throw new IllegalStateException("could not load font " + face.file)
      loaded.getName
    })
    new Font(name, size)

  def image(uri: String): Image = images.getOrElseUpdate(uri, new Image(uri))

  /** With `--screenshot`, save the frozen frame as a PNG and exit. Written by hand (no AWT/ImageIO), so the
    * desktop stays buildable as a GraalVM native image without the java.desktop module. */
  def frozen(): Unit =
    if screenshot.nonEmpty then
      javafx.application.Platform.runLater(() => {
        val image = scene.snapshot(null)
        val width = image.getWidth.toInt
        val height = image.getHeight.toInt
        val pixels = image.getPixelReader
        val rows = new java.io.ByteArrayOutputStream(width * height * 4 + height)
        var y = 0
        while y < height do
          rows.write(0) // filter: none
          var x = 0
          while x < width do
            val argb = pixels.getArgb(x, y)
            rows.write((argb >> 16) & 0xff); rows.write((argb >> 8) & 0xff); rows.write(argb & 0xff); rows.write((argb >>> 24) & 0xff)
            x += 1
          y += 1
        java.nio.file.Files.write(java.nio.file.Paths.get(screenshot), Png.encode(width, height, rows.toByteArray))
        javafx.application.Platform.exit()
      })

/** A minimal PNG encoder: 8-bit RGBA, one IDAT, zlib through java.util.zip. */
object Png:
  def encode(width: Int, height: Int, filteredRows: Array[Byte]): Array[Byte] =
    val out = new java.io.ByteArrayOutputStream()
    out.write(Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10))
    val header = java.nio.ByteBuffer.allocate(13)
    header.putInt(width).putInt(height).put(8.toByte).put(6.toByte).put(0.toByte).put(0.toByte).put(0.toByte)
    chunk(out, "IHDR", header.array())
    val deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED)
    deflater.setInput(filteredRows)
    deflater.finish()
    val compressed = new java.io.ByteArrayOutputStream(filteredRows.length / 4)
    val buffer = new Array[Byte](65536)
    while !deflater.finished() do
      val n = deflater.deflate(buffer)
      compressed.write(buffer, 0, n)
    chunk(out, "IDAT", compressed.toByteArray)
    chunk(out, "IEND", Array.empty[Byte])
    out.toByteArray

  private def chunk(out: java.io.ByteArrayOutputStream, kind: String, data: Array[Byte]): Unit =
    val length = java.nio.ByteBuffer.allocate(4).putInt(data.length).array()
    out.write(length)
    val typeBytes = kind.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
    out.write(typeBytes)
    out.write(data)
    val crc = new java.util.zip.CRC32()
    crc.update(typeBytes)
    crc.update(data)
    out.write(java.nio.ByteBuffer.allocate(4).putInt(crc.getValue.toInt).array())

/** Backend node factories. */
object Nodes:
  /** The scene's root pane. */
  def root(): RenderNode = new RenderNode(Host.root)

  /** A container — a `Pane` other nodes are appended to, sized 0x0 until given a size (a `div`'s default). */
  def container(): RenderNode =
    val p = new Pane()
    p.setMinSize(0.0, 0.0)
    p.setPrefSize(0.0, 0.0)
    p.setMaxSize(0.0, 0.0)
    p.setPickOnBounds(false)
    new RenderNode(p)

  /** A text node — a `Text`, positioned by its baseline. */
  def text(): RenderNode =
    val t = new Text()
    t.setTextOrigin(VPos.BASELINE)
    t.setFontSmoothingType(FontSmoothingType.GRAY)
    t.setFill(Color.web("#1c1b1f"))
    t.setFont(Font.font("System", FontWeight.SEMI_BOLD, 32.0))
    new RenderNode(t)

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode =
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node

  /** An image node — an `ImageView` stretched to its box. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    val i = new ImageView()
    i.setPreserveRatio(false)
    i.setSmooth(true)
    val node = new RenderNode(i)
    node.setWidth(width)
    node.setHeight(height)
    node.setImageSource(uri)
    node

/** The handful of non-render platform services the slice needs. */
object Platform:
  def log(message: String): Unit = println("[ui] " + message)

  /** A failure with its cause: the console gets the stack. */
  def logError(message: String, t: Throwable): Unit =
    println("[ui] ERROR " + message + ": " + t.toString)
    t.printStackTrace()

  def ticks(): Double = System.nanoTime() / 1.0e6

  def imagePath(name: String): String = resolvePath("/images/" + name)

  /** The window's content size. */
  def screenWidth(): Double = Host.scene.getWidth
  def screenHeight(): Double = Host.scene.getHeight

  /** `--freeze=<seconds>`: step the animation that far on a virtual clock and stop (tools/check.sh). Negative when
    * absent. */
  def freezeSeconds(): Double = Host.freeze

  /** `--name=value` on the launch, or "". */
  def launchArg(name: String): String = Host.args.getOrElse(name, "")

  /** Exit the process (a demo that is done). */
  def quit(): Unit =
    javafx.application.Platform.exit()
    System.exit(0)

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String =
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
      .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

  /** No page to be served from: an app names its server (`--live=`). */
  def webSocketOrigin(): String = ""

  /** The frozen frame is complete: save it if asked to (`--screenshot`). */
  def frozen(): Unit = Host.frozen()

  /** Called whenever the window changes size. */
  def onResize(f: () => Unit): Unit =
    Host.scene.widthProperty.addListener((_: javafx.beans.Observable) => f())
    Host.scene.heightProperty.addListener((_: javafx.beans.Observable) => f())

  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`), as a file URL under the assets directory. */
  def resolvePath(path: String): String = new File(Host.assets, path.stripPrefix("/")).toURI.toString

  /** Load `uri` and report its natural size. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    val image = Host.image(uri)
    loaded(image.getWidth, image.getHeight)

  /** Bundled faces load synchronously from the assets directory. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit =
    Host.font(Faces(family, weight), 24.0)
    loaded()

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per JavaFX pulse. */
  def animate(tick: () => Unit): Unit =
    val timer = new AnimationTimer:
      override def handle(now: Long): Unit = tick()
    timer.start()
