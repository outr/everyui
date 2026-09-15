package everyui.render

import javafx.animation.AnimationTimer
import javafx.geometry.{NodeOrientation, Pos, VPos}
import javafx.scene.image.{Image, ImageView}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.control.{PasswordField, TextField, TextInputControl, Tooltip}
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent, MouseEvent, ScrollEvent}
import javafx.scene.shape.Rectangle
import javafx.scene.layout.{Background, BackgroundFill, Border, BorderStroke, BorderStrokeStyle, BorderWidths, CornerRadii, Pane, Region}
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontSmoothingType, FontWeight, Text, TextAlignment}
import javafx.scene.{Node, Scene}
import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.event.PointerKind

import java.io.File
import scala.collection.mutable

/** **The one invention.** The abstract render node, JavaFX edition: a node in JavaFX's retained scene graph — a
  * `Text`, an `ImageView`, or a `Pane` holding other nodes.
  *
  * Signature-for-signature the same as its DOM, SceneGraph and Android twins. Units are JavaFX's pixels, which the OS
  * scales on a HiDPI display as a browser scales CSS pixels. A `Pane` never moves its children, so each node is placed
  * absolutely by its layout position, as on the other backends.
  */
class RenderNode(val fx: Node) {
  private var left: Double = 0.0
  private var top: Double = 0.0
  private var rotation: Double = 0.0
  private var explicitWidth: Double = -1.0
  private var explicitHeight: Double = -1.0
  private var face: Face = Faces.OpenSansRegular
  private var fontSize: Double = 32.0
  private var wrapping: Boolean = false
  private var textColor: String = "#1c1b1f"
  private var align: String = "start"

  def getText(): String = fx match {
    case t: Text => t.getText
    case _ => ""
  }

  def setText(text: String): Unit = fx match {
    case t: Text => t.setText(text)
    case _ => ()
  }

  def getX(): Double = left
  def setX(x: Double): Unit = {
    left = x
    fx.setLayoutX(x)
  }

  def getY(): Double = top
  def setY(y: Double): Unit = {
    top = y
    placeY()
  }

  /** Radians, clockwise, about the node's own centre — JavaFX's `rotate` is degrees, clockwise, about the centre of
    * the node's layout bounds. */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit = {
    rotation = radians
    fx.setRotate(math.toDegrees(radians))
  }

  def setFontSize(size: Double): Unit = fx match {
    case t: Text =>
      fontSize = size
      t.setFont(new Font(t.getFont.getName, size))
      placeY()
    case f: TextInputControl =>
      fontSize = size
      f.setFont(new Font(f.getFont.getName, size))
    case _ => ()
  }

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = fx match {
    case t: Text => t.setFill(Color.web(hex))
    case f: TextInputControl =>
      textColor = hex
      f.setStyle(RenderNode.fieldStyle(hex))
    case _ => ()
  }

  /** A bundled face (`assets/fonts/`): family, CSS weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = fx match {
    case t: Text =>
      face = Faces(family, weight)
      fontSize = size
      t.setFont(
        if (family.nonEmpty) Host.font(face, size)
        else Font.font("System", if (weight >= 600) FontWeight.SEMI_BOLD else FontWeight.NORMAL, size)
      )
      placeY()
    case f: TextInputControl =>
      face = Faces(family, weight)
      fontSize = size
      f.setFont(if (family.nonEmpty) Host.font(face, size) else Font.font("System", size))
    case _ => ()
  }

  /** The laid-out size in pixels. Text is measured with its real font (its advance width) and is one shared line
    * (everyui.font.Face) tall. */
  def getWidth(): Double =
    if (explicitWidth >= 0.0) explicitWidth
    else fx match {
      case t: Text => t.getLayoutBounds.getWidth
      case _ => 0.0
    }

  def getHeight(): Double =
    if (explicitHeight >= 0.0) explicitHeight
    else fx match {
      case t: Text => if (wrapping) math.ceil(t.getLayoutBounds.getHeight) else face.lineHeight(fontSize)
      case _ => 0.0
    }

  def append(child: RenderNode): Unit = fx match {
    case p: Pane => p.getChildren.add(child.fx)
    case _ => ()
  }

  /** Detach from the parent, if attached. */
  def remove(): Unit = fx.getParent match {
    case p: Pane => p.getChildren.remove(fx)
    case _ => ()
  }

  def setWidth(width: Double): Unit = {
    explicitWidth = width
    fx match {
      case r: Region =>
        r.setMinWidth(width)
        r.setPrefWidth(width)
        r.setMaxWidth(width)
      case _: Text => applyAlign()
      case i: ImageView => i.setFitWidth(width)
      case _ => ()
    }
  }

  def setHeight(height: Double): Unit = {
    explicitHeight = height
    fx match {
      case r: Region =>
        r.setMinHeight(height)
        r.setPrefHeight(height)
        r.setMaxHeight(height)
      case i: ImageView => i.setFitHeight(height)
      case _ => ()
    }
  }

  /** `"#rrggbb"`, or `""` for transparent. */
  private var background: String = ""
  private var radius: Double = 0.0
  private var borderWidth: Double = 0.0
  private var borderColor: String = ""
  def setBackgroundColor(hex: String): Unit = {
    background = hex
    paintBackground()
  }
  def setRadius(r: Double): Unit = {
    radius = r
    paintBackground()
  }
  /** An edge `width` pixels wide, inside the box. */
  def setBorder(width: Double, hex: String): Unit = {
    borderWidth = width
    borderColor = hex
    paintBackground()
  }
  private def paintBackground(): Unit = fx match {
    case p: Pane =>
      val corners = if (radius > 0.0) new CornerRadii(radius) else null
      p.setBackground(if (background.isEmpty) null else new Background(new BackgroundFill(Color.web(background), corners, null)))
      p.setBorder(if (borderColor.isEmpty || borderWidth <= 0.0) null
        else new Border(new BorderStroke(Color.web(borderColor), BorderStrokeStyle.SOLID, corners, new BorderWidths(borderWidth))))
    case _ => ()
  }

  /** Every node is placed by its layout position, so there is no position type to set. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", or "wheel" with its deltas) with its
    * scene coordinates. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = if (kind == PointerKind.Wheel)
    fx.addEventHandler(ScrollEvent.SCROLL, (e: ScrollEvent) => f(-e.getDeltaX, -e.getDeltaY))
  else {
    val kinds = Map(PointerKind.Down -> MouseEvent.MOUSE_PRESSED, PointerKind.Up -> MouseEvent.MOUSE_RELEASED, PointerKind.Move -> MouseEvent.MOUSE_MOVED,
      PointerKind.Enter -> MouseEvent.MOUSE_ENTERED, PointerKind.Leave -> MouseEvent.MOUSE_EXITED, PointerKind.Click -> MouseEvent.MOUSE_CLICKED)
    fx.setPickOnBounds(true)   // a transparent Pane is not hit-tested unless told to be
    fx.addEventHandler(kinds(kind), (e: MouseEvent) => f(e.getSceneX, e.getSceneY))
  }

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  def setClip(width: Double, height: Double): Unit = fx.setClip(new Rectangle(width, height))
  def setTooltip(text: String): Unit = if (text.nonEmpty) Tooltip.install(fx, new Tooltip(text)) else Tooltip.uninstall(fx, null)

  def setPositionType(name: String): Unit = ()

  /** `"normal"` wraps a text node at its width; anything else keeps it on one line. */
  def setWhiteSpace(value: String): Unit = fx match {
    case t: Text =>
      wrapping = value == "normal"
      applyAlign()
    case _ => ()
  }

  /** "start", "center" or "end", resolved against the window's direction (`Platform.setDirection`). */
  def setTextAlign(value: String): Unit = {
    align = value
    applyAlign()
  }

  /** Text's alignment and wrap width, a field's alignment and orientation, for the alignment and the direction. A line
    * that is neither wrapped nor aligned away from the left keeps no wrap width, so it never wraps. */
  private[render] def applyAlign(): Unit = {
    val centred = align == "center"
    val right = (align == "end") != Host.rtl
    fx match {
      case t: Text =>
        t.setTextAlignment(if (centred) TextAlignment.CENTER else if (right) TextAlignment.RIGHT else TextAlignment.LEFT)
        t.setWrappingWidth(if ((wrapping || centred || right) && explicitWidth > 0.0) explicitWidth else 0.0)
      case f: TextField =>
        // a right-to-left field is mirrored already, so its alignment stays logical: left is where it starts
        f.setAlignment(if (centred) Pos.CENTER else if (align == "end") Pos.CENTER_RIGHT else Pos.CENTER_LEFT)
        f.setNodeOrientation(if (Host.rtl) NodeOrientation.RIGHT_TO_LEFT else NodeOrientation.LEFT_TO_RIGHT)
      case _ => ()
    }
  }

  /** A text field's contents. */
  def getValue(): String = fx match {
    case f: TextInputControl => f.getText
    case _ => ""
  }
  def setValue(value: String): Unit = fx match {
    case f: TextInputControl =>
      f.setText(value)
      f.positionCaret(value.length)
    case _ => ()
  }
  def setPlaceholder(text: String): Unit = fx match {
    case f: TextInputControl => f.setPromptText(text)
    case _ => ()
  }
  /** Every edit of a text field, with its new contents. */
  def onValue(f: String => Unit): Unit = fx match {
    case field: TextInputControl => field.textProperty.addListener(new ChangeListener[String] {
      override def changed(o: ObservableValue[? <: String], was: String, now: String): Unit = f(now)
    })
    case _ => ()
  }
  /** A text field taking the keyboard focus itself (a click on it). */
  def onInputFocus(f: () => Unit): Unit = fx match {
    case field: TextInputControl => field.focusedProperty.addListener(new ChangeListener[java.lang.Boolean] {
      override def changed(o: ObservableValue[? <: java.lang.Boolean], was: java.lang.Boolean, now: java.lang.Boolean): Unit = if (now) f()
    })
    case _ => ()
  }
  /** A text field losing the keyboard. */
  def onInputDone(f: () => Unit): Unit = fx match {
    case field: TextInputControl => field.focusedProperty.addListener(new ChangeListener[java.lang.Boolean] {
      override def changed(o: ObservableValue[? <: java.lang.Boolean], was: java.lang.Boolean, now: java.lang.Boolean): Unit = if (!now) f()
    })
    case _ => ()
  }
  /** Give a text field the keyboard, or take it back to the window. */
  def focusInput(on: Boolean): Unit = fx match {
    case field: TextInputControl =>
      if (on && !field.isFocused) field.requestFocus()
      else if (!on && field.isFocused) Host.root.requestFocus()
    case _ => ()
  }

  def setImageSource(uri: String): Unit = fx match {
    case i: ImageView => if (uri.nonEmpty) i.setImage(Host.image(uri))
    case _ => ()
  }

  /** A `Text` is placed by its baseline: put that where the shared line box has it. */
  private def placeY(): Unit = fx match {
    case _: Text => fx.setLayoutY(top + face.baseline(fontSize))
    case _ => fx.setLayoutY(top)
  }
}

object RenderNode {
  /** A text field drawn by the component around it: no box of its own, the caret and the words in `hex`. */
  def fieldStyle(hex: String): String =
    "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-text-fill: " + hex +
      "; -fx-prompt-text-fill: derive(" + hex + ", -40%); -fx-highlight-fill: derive(" + hex + ", -60%); -fx-highlight-text-fill: " + hex + ";"
}

/** What the launcher (everyui.desktop.Desktop) hands the backend: the scene, its root, and how it was run. */
object Host {
  /** The app's name: the window's title, and where its settings are kept. */
  var app: String = "EveryUI"
  /** Whether the window reads right to left, and every text node that follows it (held weakly). */
  var rtl: Boolean = false
  val aligned: java.util.Set[RenderNode] = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[RenderNode, java.lang.Boolean]())

  /** The path the app is at: `--path=` to start somewhere, then wherever it last pushed. */
  var path: String = ""
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
  def font(face: Face, size: Double): Font = {
    val name = fontNames.getOrElseUpdate(face.file, {
      val loaded = Font.loadFont(new File(assets, "fonts/" + face.file).toURI.toString, size)
      if (loaded == null) throw new IllegalStateException("could not load font " + face.file)
      loaded.getName
    })
    new Font(name, size)
  }

  /** An image by URI, cached; one from the network loads in the background. */
  def image(uri: String): Image = images.getOrElseUpdate(uri, new Image(uri, uri.startsWith("http")))

  lazy val settings: java.util.prefs.Preferences = java.util.prefs.Preferences.userRoot().node("everyui/" + app)

  /** A JavaFX key by the web's name: the remote's keys by their names, Alt+Left as the browser's back, anything typed
    * by its text. */
  def keyName(e: KeyEvent): String = {
    val named = Map(KeyCode.ENTER -> "Enter", KeyCode.ESCAPE -> "Escape", KeyCode.UP -> "ArrowUp", KeyCode.DOWN -> "ArrowDown",
      KeyCode.LEFT -> "ArrowLeft", KeyCode.RIGHT -> "ArrowRight", KeyCode.TAB -> "Tab", KeyCode.BACK_SPACE -> "Backspace", KeyCode.SPACE -> " ")
    if (e.isAltDown && e.getCode == KeyCode.LEFT) "BrowserBack"
    else named.getOrElse(e.getCode, if (e.getText.nonEmpty) e.getText else e.getCode.getName)
  }

  /** With `--screenshot`, save the frozen frame as a PNG and exit. Written by hand (no AWT/ImageIO), so the
    * desktop stays buildable as a GraalVM native image without the java.desktop module. */
  def frozen(): Unit =
    if (screenshot.nonEmpty)
      javafx.application.Platform.runLater(() => {
        val image = scene.snapshot(null)
        val width = image.getWidth.toInt
        val height = image.getHeight.toInt
        val pixels = image.getPixelReader
        val rows = new java.io.ByteArrayOutputStream(width * height * 4 + height)
        var y = 0
        while (y < height) {
          rows.write(0) // filter: none
          var x = 0
          while (x < width) {
            val argb = pixels.getArgb(x, y)
            rows.write((argb >> 16) & 0xff); rows.write((argb >> 8) & 0xff); rows.write(argb & 0xff); rows.write((argb >>> 24) & 0xff)
            x += 1
          }
          y += 1
        }
        java.nio.file.Files.write(java.nio.file.Paths.get(screenshot), Png.encode(width, height, rows.toByteArray))
        javafx.application.Platform.exit()
      })
}

/** A minimal PNG encoder: 8-bit RGBA, one IDAT, zlib through java.util.zip. */
object Png {
  def encode(width: Int, height: Int, filteredRows: Array[Byte]): Array[Byte] = {
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
    while (!deflater.finished()) {
      val n = deflater.deflate(buffer)
      compressed.write(buffer, 0, n)
    }
    chunk(out, "IDAT", compressed.toByteArray)
    chunk(out, "IEND", Array.empty[Byte])
    out.toByteArray
  }

  private def chunk(out: java.io.ByteArrayOutputStream, kind: String, data: Array[Byte]): Unit = {
    val length = java.nio.ByteBuffer.allocate(4).putInt(data.length).array()
    out.write(length)
    val typeBytes = kind.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
    out.write(typeBytes)
    out.write(data)
    val crc = new java.util.zip.CRC32()
    crc.update(typeBytes)
    crc.update(data)
    out.write(java.nio.ByteBuffer.allocate(4).putInt(crc.getValue.toInt).array())
  }
}

/** Backend node factories. */
object Nodes {
  /** The scene's root pane. */
  def root(): RenderNode = new RenderNode(Host.root)

  /** A container — a `Pane` other nodes are appended to, sized 0x0 until given a size (a `div`'s default). */
  def container(): RenderNode = {
    val p = new Pane()
    p.setMinSize(0.0, 0.0)
    p.setPrefSize(0.0, 0.0)
    p.setMaxSize(0.0, 0.0)
    p.setPickOnBounds(false)
    new RenderNode(p)
  }

  /** A text node — a `Text`, positioned by its baseline. */
  def text(): RenderNode = {
    val t = new Text()
    t.setTextOrigin(VPos.BASELINE)
    t.setFontSmoothingType(FontSmoothingType.GRAY)
    t.setFill(Color.web("#1c1b1f"))
    t.setFont(Font.font("System", FontWeight.SEMI_BOLD, 32.0))
    val node = new RenderNode(t)
    Host.aligned.add(node)
    node
  }

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode = {
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }

  /** A text field — a JavaFX `TextField`, or a `PasswordField` for a secret — with no box of its own. */
  def input(secure: Boolean): RenderNode = {
    val f = if (secure) new PasswordField() else new TextField()
    f.setStyle(RenderNode.fieldStyle("#1c1b1f"))
    f.setFocusTraversable(false)
    val node = new RenderNode(f)
    Host.aligned.add(node)
    node.applyAlign()
    node
  }

  /** An image node — an `ImageView` stretched to its box. */
  def image(uri: String, width: Double, height: Double): RenderNode = {
    val i = new ImageView()
    i.setPreserveRatio(false)
    i.setSmooth(true)
    val node = new RenderNode(i)
    node.setWidth(width)
    node.setHeight(height)
    node.setImageSource(uri)
    node
  }
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  def log(message: String): Unit = println("[ui] " + message)

  /** A failure with its cause: the console gets the stack. */
  def logError(message: String, t: Throwable): Unit = {
    println("[ui] ERROR " + message + ": " + t.toString)
    t.printStackTrace()
  }

  def ticks(): Double = System.nanoTime() / 1.0e6

  def imagePath(name: String): String = resolvePath("/images/" + name)

  /** The window's content size. */
  def kind: PlatformKind = PlatformKind.Desktop
  def pointerSupport(): Boolean = true
  def touchSupport(): Boolean = false
  /** A window has a pointer and a keyboard; `--remote=true` or `--keyboard=false` on the launch tries a TV's behaviour. */
  def remoteNavigation(): Boolean = Host.args.get("remote").contains("true")
  def keyboardSupport(): Boolean = !remoteNavigation() && !Host.args.get("keyboard").contains("false")

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  def openUrl(url: String): Unit = java.awt.Desktop.getDesktop.browse(new java.net.URI(url))
  def copyText(text: String): Unit = {
    val content = new ClipboardContent()
    content.putString(text)
    Clipboard.getSystemClipboard.setContent(content)
  }

  /** No address bar, but a path all the same: `--path=` to start at a screen, and whatever the app pushes after. */
  def path(): String = Host.path
  def pushPath(path: String): Unit = Host.path = path
  def onPath(f: String => Unit): Unit = ()

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** A text file under the assets (a translation), by the URI `resolvePath` makes, or "" if it is not there. */
  def readText(path: String, done: String => Unit): Unit = done(
    try java.nio.file.Files.readString(java.nio.file.Paths.get(new java.net.URI(path)))
    catch { case _: Exception => "" })

  /** The character at a Unicode code point, as a string. */
  def fromCodePoint(codePoint: Int): String = new String(Character.toChars(codePoint))

  /** The system's language, as a BCP 47 tag (`en-US`); `--language=` on the launch tries another. */
  def language(): String = Host.args.getOrElse("language", java.util.Locale.getDefault.toLanguageTag)

  /** The window's reading direction: every text node and field re-aligns. */
  def setDirection(rtl: Boolean): Unit = {
    Host.rtl = rtl
    Host.aligned.forEach(_.applyAlign())
  }

  /** Run `f` on the JavaFX thread, soon: how a reply from another thread reaches the components. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  def later(f: () => Unit): Unit = javafx.application.Platform.runLater(() => f())

  /** A value kept across launches (the user's preferences store), or "" when there is none. */
  def setting(name: String): String = Host.settings.get(name, "")
  /** Keep `value` under `name`; "" forgets it. */
  def saveSetting(name: String, value: String): Unit = {
    if (value.isEmpty) Host.settings.remove(name) else Host.settings.put(name, value)
    Host.settings.flush()
  }

  /** What this machine calls itself. */
  def deviceName(): String =
    try java.net.InetAddress.getLocalHost.getHostName
    catch { case _: Exception => "Desktop" }

  /** A desktop has text fields of its own. */
  def nativeInput(): Boolean = true
  /** Only a platform without text fields asks its keyboard for a value. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()

  /** A registered face loads from its file when first used. */
  def registerFont(face: Face): Unit = ()

  def screenWidth(): Double = Host.scene.getWidth
  def screenHeight(): Double = Host.scene.getHeight

  /** `--freeze=<seconds>`: step the animation that far on a virtual clock and stop (tools/check.sh). Negative when
    * absent. */
  def freezeSeconds(): Double = Host.freeze

  /** `--name=value` on the launch, or "". */
  def launchArg(name: String): String = Host.args.getOrElse(name, "")

  /** Exit the process (a demo that is done). */
  def quit(): Unit = {
    javafx.application.Platform.exit()
    System.exit(0)
  }

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String =
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
      .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

  /** No page to be served from: an app names its server (`--live=`). */
  def webSocketOrigin(): String = ""

  /** The frozen frame is complete: save it if asked to (`--screenshot`). */
  def frozen(): Unit = Host.frozen()

  /** Called whenever the window changes size. */
  /** Every key in the window, by the web's name, pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = {
    Host.scene.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => if (f(Host.keyName(e), true)) e.consume())
    Host.scene.addEventFilter(KeyEvent.KEY_RELEASED, (e: KeyEvent) => if (f(Host.keyName(e), false)) e.consume())
    // a mouse's back button is Back too
    Host.scene.addEventFilter(MouseEvent.MOUSE_PRESSED, (e: MouseEvent) => if (e.getButton == javafx.scene.input.MouseButton.BACK && f("BrowserBack", true)) e.consume())
    Host.scene.addEventFilter(MouseEvent.MOUSE_RELEASED, (e: MouseEvent) => if (e.getButton == javafx.scene.input.MouseButton.BACK && f("BrowserBack", false)) e.consume())
  }

  def onResize(f: () => Unit): Unit = {
    Host.scene.widthProperty.addListener((_: javafx.beans.Observable) => f())
    Host.scene.heightProperty.addListener((_: javafx.beans.Observable) => f())
  }

  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`), as a file URL under the assets directory. */
  def resolvePath(path: String): String = new File(Host.assets, path.stripPrefix("/")).toURI.toString

  /** Load `uri` and report its natural size: at once from a file, once it arrives from the network (0 x 0 if it
    * cannot). */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit = {
    val image = Host.image(uri)
    if (image.getProgress >= 1.0 || image.isError) loaded(image.getWidth, image.getHeight)
    else image.progressProperty.addListener(new ChangeListener[Number] {
      override def changed(o: ObservableValue[? <: Number], was: Number, now: Number): Unit =
        if (now.doubleValue >= 1.0) {
          o.removeListener(this)
          loaded(image.getWidth, image.getHeight)
        }
    })
  }

  /** Bundled faces load synchronously from the assets directory. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    Host.font(Faces(family, weight), 24.0)
    loaded()
  }

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per JavaFX pulse. */
  def animate(tick: () => Unit): Unit = {
    val timer = new AnimationTimer {
      override def handle(now: Long): Unit = tick()
    }
    timer.start()
  }
}
