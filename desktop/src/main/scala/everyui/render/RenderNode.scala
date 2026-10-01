package everyui.render

import javafx.animation.AnimationTimer
import javafx.geometry.{NodeOrientation, Pos, VPos}
import javafx.scene.image.{Image, ImageView}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.control.{PasswordField, TextArea, TextField, TextInputControl, Tooltip}
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent, MouseButton, MouseEvent, ScrollEvent}
import javafx.scene.shape.{Rectangle, StrokeLineJoin, StrokeType}
import javafx.scene.layout.{Background, BackgroundFill, Border, BorderStroke, BorderStrokeStyle, BorderWidths, CornerRadii, Pane, Region}
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Paint, Stop}
import javafx.scene.text.{Font, FontSmoothingType, Text, TextAlignment}
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
  // which way its words run (`setTextDirection`): "" the window's, "auto" their own, "ltr" or "rtl"
  private var textDirection: String = ""
  // what a video or an audio node plays (`Nodes.video`, `Nodes.audio`)
  private[render] var media: Option[DesktopMedia] = None

  def getText(): String = fx match {
    case t: Text => RtlText.words(t)
    case f: RichFlow => f.text
    case _ => ""
  }

  def setText(text: String): Unit = fx match {
    case t: Text =>
      RtlText.lay(t, text)
      if (textDirection == "auto") applyAlign()
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

  /** Drawn larger or smaller about its own centre, which is what JavaFX scales about. */
  def setScale(factor: Double): Unit = {
    fx.setScaleX(factor)
    fx.setScaleY(factor)
  }

  def setFontSize(size: Double): Unit = fx match {
    case f: RichFlow =>
      fontSize = size
      f.setSize(size)
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
    case f: RichFlow => f.setColour(hex)
    case t: Text => t.setFill(Color.web(hex))
    case f: TextInputControl =>
      textColor = hex
      f.setStyle(RenderNode.fieldStyle(hex))
    case _ => ()
  }

  /** A bundled face (`assets/fonts/`): family, CSS weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = fx match {
    case f: RichFlow =>
      face = Faces(family, weight)
      fontSize = size
      f.setBase(family, weight, size)
    case t: Text =>
      face = Faces(family, weight)
      fontSize = size
      fontFamily = family
      fontWeight = weight
      t.setFont(
        if (italic) RichFlow.font(family, weight, true, size)
        else if (family.nonEmpty) Host.font(face, size)
        else FontWeights("System", weight, false, size)
      )
      Oblique(t, italic)
      placeY()
    case f: TextInputControl =>
      face = Faces(family, weight)
      fontSize = size
      fontFamily = family
      fontWeight = weight
      f.setFont(if (italic) RichFlow.font(family, weight, true, size)
        else if (family.nonEmpty) Host.font(face, size) else FontWeights("System", weight, false, size))
    case _ => ()
  }

  /** The laid-out size in pixels. Text is measured with its real font (its advance width) and is one shared line
    * (everyui.font.Face) tall. */
  def getWidth(): Double =
    if (explicitWidth >= 0.0) explicitWidth
    else fx match {
      case t: Text => t.getLayoutBounds.getWidth + LetterSpacing.width(letterSpacing, t.getText)
      case f: RichFlow => f.across() + LetterSpacing.width(letterSpacing, runs)
      case _ => 0.0
    }

  def getHeight(): Double =
    if (explicitHeight >= 0.0) explicitHeight
    else fx match {
      case t: Text => if (wrapping) math.ceil(t.getLayoutBounds.getHeight) else face.lineHeight(fontSize)
      case f: RichFlow => f.down(if (wrapping && explicitWidth > 0.0) explicitWidth else -1.0)
      case _ => 0.0
    }

  def append(child: RenderNode): Unit = fx match {
    case p: Pane => p.getChildren.add(child.fx)
    case _ => ()
  }

  def insert(child: RenderNode, index: Int): Unit = fx match {
    case p: Pane =>
      p.getChildren.remove(child.fx)
      p.getChildren.add(math.max(0, math.min(index, p.getChildren.size)), child.fx)
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
  private var gradient: Option[Paint] = None
  /** A linear gradient over the box (`everyui.Gradient`'s encoding), or none for `""`. */
  def setGradient(angle: Double, stops: String): Unit = {
    gradient = RenderNode.linearGradient(angle, stops)
    paintBackground()
  }
  /** The letters painted with a gradient. */
  def setTextGradient(angle: Double, stops: String): Unit = fx match {
    case t: Text => RenderNode.linearGradient(angle, stops).foreach(t.setFill)
    case _ => ()
  }
  /** JavaFX's cached bitmap of the node. */
  def setCached(cached: Boolean): Unit = fx.setCache(cached)

  private var motionTracks: List[everyui.motion.Track] = Nil
  /** The motion's timelines, run only while the node is in a shown window and visible (Motion): made on first use. */
  private var motion: Motion = null

  /** What the motion is for, told when it is set (a Roku logs it for everyui_debug_motion); nothing here. */
  def setMotionOwner(owner: () => String): Unit = ()
  def setMotion(tracks: List[everyui.motion.Track]): Unit = {
    motionTracks = tracks
    if (tracks.nonEmpty || motion != null) {
      if (motion == null) motion = new Motion(fx, () => timelines())
      motion.set(tracks.nonEmpty)
    }
  }

  /** Each track a timeline of the node's own properties, repeating: a position is its translation, on top of the
    * layout position it is placed by. */
  private def timelines(): List[javafx.animation.Timeline] = {
    import javafx.animation.{Animation, Interpolator, KeyFrame, KeyValue, Timeline}
    import javafx.util.Duration
    import everyui.motion.Track
    motionTracks.map { track =>
      val frames = track.keys.map { k =>
        val at = Duration.seconds(k.at * track.seconds)
        def value(p: javafx.beans.value.WritableValue[Number], v: Double) = new KeyValue(p, Double.box(v), Interpolator.LINEAR)
        val values = track.property match {
          case Track.Position => List(value(fx.translateXProperty(), k.a), value(fx.translateYProperty(), k.b))
          case Track.Rotation => List(value(fx.rotateProperty(), math.toDegrees(k.a)))
          case Track.Opacity => List(value(fx.opacityProperty(), k.a))
          case _ => List(value(fx.scaleXProperty(), k.a), value(fx.scaleYProperty(), k.b))
        }
        new KeyFrame(at, values*)
      }
      val timeline = new Timeline(frames*)
      timeline.setCycleCount(Animation.INDEFINITE)
      timeline.setDelay(Duration.seconds(track.delay))
      timeline
    }
  }

  /** A soft shadow under the box, or none for `""`. */
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = {
    dropShadow = if (hex.isEmpty) None else Some(new javafx.scene.effect.DropShadow(blur, x, y, Color.web(hex)))
    paintEffect()
  }
  /** A gaussian blur, its kernel three times the spread wide (as far as JavaFX goes), over the shadow if there is one. */
  def setBlur(radius: Double): Unit = {
    blurRadius = radius
    paintEffect()
  }
  private var dropShadow: Option[javafx.scene.effect.DropShadow] = None
  private var blurRadius: Double = 0.0
  private def paintEffect(): Unit =
    if (blurRadius <= 0.0) fx.setEffect(dropShadow.orNull)
    else {
      val gaussian = new javafx.scene.effect.GaussianBlur(math.min(63.0, blurRadius * 3.0))
      dropShadow.foreach(s => gaussian.setInput(s))
      fx.setEffect(gaussian)
    }
  /** Lines of wrapped text keep the face's own spacing here. */
  def setLineHeight(px: Double): Unit = ()
  /** Extra space after each letter, in pixels: JavaFX spaces no letters, so the words take the spaced width and their
    * letters sit as the face sets them (`LetterSpacing`). */
  def setLetterSpacing(px: Double): Unit = letterSpacing = px
  private var letterSpacing: Double = 0.0
  private var runs: List[everyui.text.TextRun] = Nil
  /** Slanted: the family's registered italic, or the system's, where it has one (`RichFlow.font`), else its upright
    * letters sheared (`Oblique`); the measured width is the words' as drawn. A field in a family with no italic stays
    * upright. */
  def setItalic(on: Boolean): Unit = if (on != italic) {
    italic = on
    fx match {
      case f: RichFlow => f.setItalic(on)
      case _ => setFont(fontFamily, fontWeight, fontSize)
    }
  }
  private var italic: Boolean = false
  private var fontFamily: String = ""
  private var fontWeight: Int = 400
  /** A link is followed by its click; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** Styled runs: each in its own style in a `RichFlow` (`Nodes.richText`, a `RichText`'s node). A plain `Text` is one
    * style, so there they are drawn as the paragraph's own, their words run together, underlined where every word of
    * them is (a subtitle underlined whole), since a line under part of it cannot be. */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = {
    this.runs = runs
    fx match {
      case f: RichFlow =>
        f.setRuns(runs, follow)
        if (textDirection == "auto") applyAlign()
      case _ => setPlainRuns(runs)
    }
  }

  private def setPlainRuns(runs: List[everyui.text.TextRun]): Unit = {
    setText(runs.map(_.text).mkString)
    fx match {
      case t: Text => t.setUnderline(runs.exists(_.text.nonEmpty) && runs.forall(r => r.underline || r.text.isEmpty))
      case _ => ()
    }
  }

  private def paintBackground(): Unit = fx match {
    case p: Pane =>
      val corners = if (radius > 0.0) new CornerRadii(radius) else null
      val fills = List(
        Option.when(background.nonEmpty)(new BackgroundFill(Color.web(background), corners, null)),
        gradient.map(g => new BackgroundFill(g, corners, null))).flatten
      p.setBackground(if (fills.isEmpty) null else new Background(fills*))
      p.setBorder(if (borderColor.isEmpty || borderWidth <= 0.0) null
        else new Border(new BorderStroke(Color.web(borderColor), BorderStrokeStyle.SOLID, corners, new BorderWidths(borderWidth))))
    case _ => ()
  }

  /** Every node is placed by its layout position, so there is no position type to set. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", or "wheel" with its deltas) with its
    * place in the app's units: the scene's over the size the app is drawn at (`Host.unscaled`). */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = if (kind == PointerKind.Wheel)
    fx.addEventHandler(ScrollEvent.SCROLL, (e: ScrollEvent) => {
      Platform.pointed(0, e.isControlDown, e.isMetaDown, e.isAltDown, e.isShiftDown)
      f(-e.getDeltaX, -e.getDeltaY)
    })
  else if (kind == PointerKind.Menu) {
    fx.setPickOnBounds(true)
    fx.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
      (e: javafx.scene.input.ContextMenuEvent) => {
        Platform.pointed(2, Platform.modifiers().ctrl, Platform.modifiers().meta, Platform.modifiers().alt,
          Platform.modifiers().shift)
        f(Host.unscaled(e.getSceneX), Host.unscaled(e.getSceneY))
      })
  }
  else {
    val kinds = Map(PointerKind.Down -> MouseEvent.MOUSE_PRESSED, PointerKind.Up -> MouseEvent.MOUSE_RELEASED, PointerKind.Move -> MouseEvent.MOUSE_MOVED,
      PointerKind.Enter -> MouseEvent.MOUSE_ENTERED, PointerKind.Leave -> MouseEvent.MOUSE_EXITED, PointerKind.Click -> MouseEvent.MOUSE_CLICKED)
    fx.setPickOnBounds(true)   // a transparent Pane is not hit-tested unless told to be
    // a click is the primary button's, as a browser's is: the others have their own uses (a menu, a tab closed)
    fx.addEventHandler(kinds(kind), (e: MouseEvent) => if (kind != PointerKind.Click || e.getButton == MouseButton.PRIMARY) {
      pointed(e)
      f(Host.unscaled(e.getSceneX), Host.unscaled(e.getSceneY))
    })
    // a pointer moving with a button held is a drag, which JavaFX sends only to the node it went down on: a move all
    // the same, as a browser's pointermove is, so a split's handle follows it
    if (kind == PointerKind.Move) fx.addEventHandler(MouseEvent.MOUSE_DRAGGED, (e: MouseEvent) => {
      pointed(e)
      f(Host.unscaled(e.getSceneX), Host.unscaled(e.getSceneY))
    })
  }

  private def pointed(e: MouseEvent): Unit = {
    val button = if (e.getButton == MouseButton.MIDDLE) 1 else if (e.getButton == MouseButton.SECONDARY) 2 else 0
    Platform.pointed(button, e.isControlDown, e.isMetaDown, e.isAltDown, e.isShiftDown)
  }

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = ()
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer moves the content itself, so the node never scrolls on its own. */
  def onScrolled(f: () => Unit): Unit = ()
  /** A scroll that runs from its left in every language: the shared ScrollContainer counts it so here. */
  def setLeftToRight(): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  /** How far the node has scrolled its own overflow: never, here, where the ScrollContainer shifts the content. */
  def scrolled(horizontal: Boolean): Double = 0.0
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. */
  def setVisible(visible: Boolean): Unit = if (fx.isVisible != visible) {
    fx.setVisible(visible)
    // what moves in it, or is it, runs only while it can be seen (Motion)
    Motion.visibilityChanged()
  }
  def setOpacity(opacity: Double): Unit = fx.setOpacity(opacity)

  def setClip(width: Double, height: Double): Unit = fx.setClip(new Rectangle(width, height))
  def setTooltip(text: String): Unit = if (text.nonEmpty) Tooltip.install(fx, new Tooltip(text)) else Tooltip.uninstall(fx, null)
  /** A drawing's figures (everyui.draw.Drawing): an SVGPath each, stroked and filled as it says. */
  def setFigures(figures: List[everyui.draw.Figure]): Unit = fx match {
    case p: Pane =>
      p.getChildren.setAll(figures.map { f =>
        val shape = new javafx.scene.shape.SVGPath()
        shape.setContent(f.path.svg)
        shape.setFill(f.fill.map(RenderNode.paintOf).orNull)
        shape.setStroke(f.stroke.map(RenderNode.paintOf).orNull)
        shape.setStrokeWidth(f.width)
        shape.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
        shape.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND)
        shape.setMouseTransparent(true)
        shape: Node
      }*)
    case _ => ()
  }
  /** Only a web page scrolls under a finger that drags: nothing to do here. */
  def setTouchDrags(on: Boolean): Unit = ()
  /** Its words selectable: no text is selected with a pointer here (`Platform.selectsText`). */
  def setSelectable(on: Boolean): Unit = ()
  def selectText(): Unit = ()
  /** The pointer's shape over it, by its CSS name ("col-resize", "row-resize", "pointer", "text"); "" for the default. */
  def setCursor(name: String): Unit = fx.setCursor(name match {
    case "col-resize" | "ew-resize" => javafx.scene.Cursor.H_RESIZE
    case "row-resize" | "ns-resize" => javafx.scene.Cursor.V_RESIZE
    case "pointer" => javafx.scene.Cursor.HAND
    case "text" => javafx.scene.Cursor.TEXT
    case "move" => javafx.scene.Cursor.MOVE
    case _ => null
  })

  def setPositionType(name: String): Unit = ()

  /** `"normal"` (or `"pre-wrap"`, `"pre-line"`, which keep the words' own line breaks as a text node always does here)
    * wraps a text node at its width; anything else keeps it on one line. */
  def setWhiteSpace(value: String): Unit = fx match {
    case _: RichFlow => wrapping = value != "nowrap" && value != "pre"
    case t: Text =>
      wrapping = value == "normal" || value == "pre-wrap" || value == "pre-line"
      applyAlign()
    case _ => ()
  }

  /** "start", "center" or "end", resolved against the window's direction (`Platform.setDirection`). */
  /** A shadow behind the letters: not drawn here. */
  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** An edge round the letters, `width` each side, or none for "": the text's own stroke, laid outside the letters so
    * it never eats into them, as Android's is. */
  def setTextOutline(width: Double, hex: String): Unit = fx match {
    case t: Text =>
      if (hex.isEmpty) t.setStroke(null)
      else {
        t.setStroke(Color.web(hex))
        t.setStrokeWidth(width)
        t.setStrokeType(StrokeType.OUTSIDE)
        t.setStrokeLineJoin(StrokeLineJoin.ROUND)
      }
    case _ => ()
  }
  // no reader of roles or descriptions reaches this platform's nodes
  def setSemantics(role: String, level: Int, label: String): Unit = ()
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean]): Unit = ()
  /** The focus landed here: where a screen reader is on, it reads this out, and pressing there is `click`. */
  def accessibleFocus(click: () => Unit): Unit = ()
  def setAlt(text: String): Unit = ()

  def setTextAlign(value: String): Unit = {
    align = value
    applyAlign()
  }

  /** Which way the words run: "" the window's, "auto" their own (JavaFX has no such setting, so the first letter
    * with a direction is found here, again as the words change), "ltr" or "rtl". */
  def setTextDirection(value: String): Unit = {
    textDirection = value
    applyAlign()
  }

  /** A text node breaks a word too long for its line where it must already. */
  def setWrapAnywhere(on: Boolean): Unit = ()
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) are cut by measuring alone: nothing else holds them
    * here. */
  def setTextLimit(width: Double, lines: Int): Unit = ()
  /** Words measure as they will be drawn from the moment they are set. */
  def textMeasurable(ready: () => Unit): Boolean = true

  /** Text's alignment and wrap width, a field's alignment and orientation, for the alignment and the direction. A line
    * that is neither wrapped nor aligned away from the left keeps no wrap width, so it never wraps. */
  private[render] def applyAlign(): Unit = {
    val centred = align == "center"
    // the words' own direction, where they are given one: a text turned right to left takes its left as its start
    val rtl = everyui.text.Bidi.rightToLeft(textDirection, getText(), Host.rtl)
    val right = (align == "end") != rtl
    val turned = textDirection.nonEmpty && rtl
    val orientation = if (textDirection.isEmpty) NodeOrientation.INHERIT
      else if (rtl) NodeOrientation.RIGHT_TO_LEFT else NodeOrientation.LEFT_TO_RIGHT
    val side = if (centred) TextAlignment.CENTER else if (right != turned) TextAlignment.RIGHT else TextAlignment.LEFT
    fx match {
      case t: Text =>
        t.setNodeOrientation(orientation)
        t.setTextAlignment(side)
        t.setWrappingWidth(if ((wrapping || centred || right) && explicitWidth > 0.0) explicitWidth else 0.0)
      case f: RichFlow =>
        f.setNodeOrientation(orientation)
        f.setSide(side)
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
  // a desktop keyboard's Enter says nothing of what it does
  def setInputAction(action: String): Unit = ()
  // what a field asks for (`Autofill.token`): nothing here fills fields
  def setAutofill(token: String, section: String, name: String, passkey: Boolean, literal: Boolean): Unit = ()
  // a form (`everyui.form.Form`) is a container here: nothing sends it but the app, and nothing offers to save it
  def onSubmit(f: () => Unit): Unit = ()
  def formDone(saved: Boolean): Unit = ()
  def setHiddenUsername(username: String): Unit = ()
  /** Every edit of a text field, with its new contents. */
  def onValue(f: String => Unit): Unit = fx match {
    case field: TextInputControl => field.textProperty.addListener(new ChangeListener[String] {
      override def changed(o: ObservableValue[? <: String], was: String, now: String): Unit = f(now)
    })
    case _ => ()
  }
  /** A paste into a text field (Ctrl+V, Shift+Insert, the context menu's Paste), put to `f` with its words before they
    * land (`Pasting`). */
  def onPaste(f: String => everyui.component.types.PasteAction): Unit = fx match {
    case field: TextInputControl => Pasting.hook(field, f)
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
  /** Enter in a text field. */
  def onInputAction(f: () => Unit): Unit = fx match {
    case field: TextField => field.addEventHandler(javafx.event.ActionEvent.ACTION, (_: javafx.event.ActionEvent) => f())
    case _ => ()
  }
  /** All of what a text field holds chosen, so what is typed next replaces it. */
  def selectAllInput(): Unit = fx match {
    case field: TextInputControl => field.selectAll()
    case _ => ()
  }
  /** Where a text field's caret is, in characters from the start. */
  def getCaret(): Int = fx match {
    case field: TextInputControl => field.getCaretPosition
    case _ => 0
  }
  /** A text field's caret put at `at`, nothing chosen. */
  def setCaret(at: Int): Unit = fx match {
    case field: TextInputControl => field.positionCaret(at)
    case _ => ()
  }
  /** Out of use, or back in it: a text field keeps its words but takes no typing. Nothing reads the nodes' roles here
    * (`setSemantics`), so there is nothing to tell. */
  def setDisabled(disabled: Boolean): Unit = fx match {
    case field: TextInputControl =>
      fieldDisabled = disabled
      field.setEditable(!disabled && !fieldReadOnly)
    case _ => ()
  }
  /** Read-only, or editable again: a text field shows its words, which can be chosen and copied, but takes no typing
    * or paste, and looks as it did (not editable, which JavaFX tells a screen reader, rather than disabled). */
  def setReadOnly(on: Boolean): Unit = fx match {
    case field: TextInputControl =>
      fieldReadOnly = on
      field.setEditable(!on && !fieldDisabled)
    case _ => ()
  }
  private var fieldDisabled: Boolean = false
  private var fieldReadOnly: Boolean = false
  /** Give a text field the keyboard, or take it back to the window. */
  def focusInput(on: Boolean): Unit = fx match {
    case field: TextInputControl =>
      if (on && !field.isFocused) field.requestFocus()
      else if (!on && field.isFocused) Host.root.requestFocus()
    case _ => ()
  }

  /** The picture in one colour, its transparency kept (the colour blended over it, atop), or as it is for `""`. */
  def setTint(hex: String): Unit = fx.setEffect(
    if (hex.isEmpty) null
    else new javafx.scene.effect.Blend(javafx.scene.effect.BlendMode.SRC_ATOP, null,
      new javafx.scene.effect.ColorInput(0, 0, 8192, 8192, Color.web(hex))))

  /** The picture the view draws, or none (which lets the one it was drawing go). */
  def setImageSource(uri: String): Unit = fx match {
    case i: ImageView => i.setImage(if (uri.isEmpty) null else Host.image(uri))
    case _ => ()
  }

  // JavaFX Media takes no request headers: what it is given is an address and nothing else
  def setMediaHeaders(headers: Map[String, String]): Unit = ()
  def setMediaSource(url: String): Unit = media.foreach(_.setSource(url))
  def setMediaStart(seconds: Double): Unit = media.foreach(_.setStart(seconds))
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = ()
  def setMediaPlaying(playing: Boolean): Unit = media.foreach(_.setPlaying(playing))
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.setLoop(loop))
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.setMuted(muted))
  def setMediaVolume(volume: Double): Unit = media.foreach(_.setVolume(volume))
  def setMediaRate(rate: Double): Unit = media.foreach(_.setRate(rate))
  def mediaRates(): Boolean = true
  // neither what it holds nor what it waits for can be told here
  def setMediaBuffering(named: String): Unit = ()
  // nowhere of its own to keep a clip: every one is fetched as it plays
  def setMediaCached(cached: Boolean): Unit = ()
  // it does not say whether it is waiting; the playhead standing still is what tells a viewer
  def mediaBuffering(): Boolean = false
  // nothing here counts what the link is carrying
  def mediaBandwidth(): Long = 0L
  def mediaSize(): (Double, Double) = media.map(_.size()).getOrElse((0.0, 0.0))
  /** The words on screen: "" here, where the platform has no text to hand over. */
  def mediaCues(): String = ""
  def mediaCueList(): List[everyui.component.MediaCue] = Nil
  // JavaFX retries nothing it will say
  def mediaRetry(): Option[everyui.component.MediaRetry] = None
  // nothing here shows what is playing, or hears it
  def setNowPlaying(about: everyui.component.NowPlaying): Unit = ()
  def setMediaSpectrum(on: Boolean): Unit = ()
  def mediaBands(): List[Double] = Nil

  // ---- a page of the web ----
  def setWebSource(url: String): Unit = ()
  def setWebAgent(agent: String): Unit = ()
  def runWebScript(script: String): Unit = ()
  def onWebMessage(f: String => Unit): Unit = ()
  def releaseWeb(): Unit = ()

  // ---- an embed (everyui.component.Embed, Nodes.embed): a web guest in a web view of its own, where JavaFX's web
  // view is on the classpath (WebGuest) ----
  private[render] var guest: Option[WebGuest] = None
  def embedId(): String = guest.map(_.id).getOrElse("")
  def embedRuntime(): String = guest.map(_.id).getOrElse("")
  def embedSend(message: String): Unit = guest.foreach(_.send(message))
  def onEmbedMessage(f: String => Unit): Unit = guest.foreach(_.hear(f))
  def focusEmbed(on: Boolean): Unit = guest.foreach(_.focus(on))
  def onEmbedFocus(f: Boolean => Unit): Unit = guest.foreach(_.onFocus(f))
  def releaseEmbed(): Unit = {
    guest.foreach(_.release())
    guest = None
  }

  /** What to hear and read this in: nothing here, where the platform chooses its own tracks. */
  def setMediaLanguages(audio: String, text: String): Unit = ()

  def mediaTracks(): List[everyui.component.MediaTrack] = media.map(_.tracks()).getOrElse(Nil)
  /** What was decoded: nothing this player says. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics()
  // JavaFX lists its tracks but will not switch between them
  def selectMediaTrack(kind: String, id: String): Unit = ()
  def setMediaText(choice: everyui.component.TextChoice): Unit = ()
  def setMediaCover(cover: Boolean): Unit = media.foreach(_.setCover(cover))
  def setMediaDirect(direct: Boolean): Unit = ()
  def getMediaPosition(): Double = media.map(_.position()).getOrElse(0.0)
  def getMediaDuration(): Double = media.map(_.duration()).getOrElse(0.0)
  def seekMedia(seconds: Double): Unit = media.foreach(_.seek(seconds))
  def onMediaEvent(f: String => Unit): Unit = media.foreach(_.onEvent(f))
  def releaseMedia(): Unit = media.foreach(_.release())

  /** A `Text` is placed by its baseline: put that where the shared line box has it. */
  private def placeY(): Unit = fx match {
    case _: Text => fx.setLayoutY(top + face.baseline(fontSize))
    case _ => fx.setLayoutY(top)
  }
}

object RenderNode {
  /** An EveryUI colour as JavaFX paints with it. */
  def paintOf(c: everyui.Color): Color = Color.rgb(c.redInt, c.greenInt, c.blueInt, c.alpha)

  /** The one thread that does [[Platform.offThread]]'s work, so what it does never holds up the FX thread. */
  lazy val work: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor(r => {
      val thread = new Thread(r, "everyui-work")
      thread.setDaemon(true)
      thread
    })

  /** A JavaFX paint for `everyui.Gradient`'s encoding, across the box by a CSS angle; none for `""`. */
  def linearGradient(angle: Double, stops: String): Option[Paint] = if (stops.isEmpty) None else {
    val radians = math.toRadians(angle)
    val dx = math.sin(radians) / 2.0
    val dy = -math.cos(radians) / 2.0
    val list = everyui.Gradient.decode(stops).map { case (hex, at) => new Stop(at, Color.web(hex)) }
    Some(new LinearGradient(0.5 - dx, 0.5 - dy, 0.5 + dx, 0.5 + dy, true, CycleMethod.NO_CYCLE, list*))
  }

  /** What a box of lines draws inside itself, taken away as a field's own box is (`fieldStyle` reaches only the
    * control): the white page under the words, and the padding around them, which the component keeps itself. */
  lazy val areaSheet: String = "data:text/css;base64," + java.util.Base64.getEncoder.encodeToString((
    ".text-area .content, .text-area .scroll-pane, .text-area .scroll-pane .viewport, .text-area:readonly .content " +
      "{ -fx-background-color: transparent; } " +
      ".text-area .content, .text-area .scroll-pane { -fx-padding: 0; -fx-background-insets: 0; } " +
      ".text-area .scroll-bar, .text-area .corner { -fx-background-color: transparent; -fx-pref-width: 8; } " +
      ".text-area .scroll-bar .thumb { -fx-background-color: rgba(128, 128, 128, 0.45); -fx-background-radius: 4; } " +
      ".text-area .increment-button, .text-area .decrement-button, .text-area .increment-arrow, " +
      ".text-area .decrement-arrow { -fx-padding: 0; -fx-background-color: transparent; }").getBytes("UTF-8"))

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
  /** The pictures loaded so far, the ones wanted longest ago first, and how much they take between them. */
  private val images = mutable.LinkedHashMap.empty[String, Image]
  private var imageBytes = 0L
  private val imageBudget = 192L * 1024L * 1024L

  /** A bundled face at `size`. JavaFX registers a font file by loading it once; after that it is found by name. */
  def font(face: Face, size: Double): Font = {
    val name = fontNames.getOrElseUpdate(face.file, {
      val loaded = Font.loadFont(new File(assets, "fonts/" + face.file).toURI.toString, size)
      if (loaded == null) throw new IllegalStateException("could not load font " + face.file)
      FontWeights.forget()
      loaded.getName
    })
    new Font(name, size)
  }

  /** An image by URI, cached (the longest-unwanted let go of once they come to more than the budget: an app that
    * shows thousands of them over an evening kept every one), and one from the network loads in the background. What
    * a view is drawing stays whole — letting go here only drops this hold on it. */
  def image(uri: String): Image = images.remove(uri) match {
    case Some(had) =>
      images.put(uri, had)
      had
    case None =>
      val loaded = new Image(uri, uri.startsWith("http"))
      images.put(uri, loaded)
      imageBytes += bytesOf(loaded)
      while (imageBytes > imageBudget && images.size > 1) {
        val oldest = images.head
        images.remove(oldest._1)
        imageBytes -= bytesOf(oldest._2)
      }
      loaded
  }

  // as it will be held once it has loaded (nothing yet while it is on its way)
  private def bytesOf(image: Image): Long = math.round(image.getWidth * image.getHeight * 4.0)

  /** A pointer's place in the scene in the app's units: over the size the app was drawn at as the event began
    * (everyui.theme.UiScale), so one that changes the size as it is heard (a click on a + that turns it up) is the
    * same place to every node it climbs through, and the one that consumed it is still known for it. */
  def unscaled(v: Double): Double = v / heardAt
  private var heardAt: Double = 1.0

  /** The root drawn `factor` times its size from its top left, and laid out in the scene over it, so its edges are the
    * window's whether larger or smaller: the scene sizes its root to itself, and is put right whenever it does. */
  def scaleRoot(factor: Double): Unit = if (root != null && scene != null) {
    if (!root.getTransforms.contains(rootScale)) {
      root.getTransforms.add(rootScale)
      // the size each input event begins at, before any node hears it
      scene.addEventFilter(javafx.scene.input.InputEvent.ANY, (_: javafx.scene.input.InputEvent) =>
        heardAt = everyui.theme.UiScale())
      root.widthProperty.addListener((_: javafx.beans.Observable) => fitRoot())
      root.heightProperty.addListener((_: javafx.beans.Observable) => fitRoot())
      scene.widthProperty.addListener((_: javafx.beans.Observable) => fitRoot())
      scene.heightProperty.addListener((_: javafx.beans.Observable) => fitRoot())
    }
    rootScale.setX(factor)
    rootScale.setY(factor)
    fitRoot()
  }
  private lazy val rootScale = new javafx.scene.transform.Scale(1.0, 1.0, 0.0, 0.0)
  private def fitRoot(): Unit = {
    val w = scene.getWidth / rootScale.getX
    val h = scene.getHeight / rootScale.getY
    if (math.abs(root.getWidth - w) > 0.01 || math.abs(root.getHeight - h) > 0.01) root.resize(w, h)
  }

  lazy val settings: java.util.prefs.Preferences = java.util.prefs.Preferences.userRoot().node("everyui/" + app)

  /** The embed a key sent to `target` is meant for, "" for none: the name marked on it or the nearest node round it
    * (`embedKey`). */
  def embedOf(target: javafx.event.EventTarget): String = {
    var at: javafx.scene.Node = target match {
      case n: javafx.scene.Node => n
      case _ => null
    }
    var name = ""
    while (name.isEmpty && at != null) {
      val marked = at.getProperties.get(Host.embedKey)
      if (marked != null) name = marked.toString
      at = at.getParent
    }
    name
  }

  /** The node property that marks an embed (a web view, an editor): its value is the embed's name, and keys the window
    * sends to it or anything inside it are left to it (`Platform.keyEmbed`). */
  val embedKey: String = "everyui-embed"

  /** A JavaFX key by the web's name: the remote's keys by their names, Alt+Left as the browser's back, anything typed
    * by its text. */
  def keyName(e: KeyEvent): String = {
    val named = Map(KeyCode.ENTER -> "Enter", KeyCode.ESCAPE -> "Escape", KeyCode.UP -> "ArrowUp", KeyCode.DOWN -> "ArrowDown",
      KeyCode.LEFT -> "ArrowLeft", KeyCode.RIGHT -> "ArrowRight", KeyCode.TAB -> "Tab", KeyCode.BACK_SPACE -> "Backspace", KeyCode.SPACE -> " ",
      KeyCode.FAST_FWD -> "MediaFastForward", KeyCode.REWIND -> "MediaRewind", KeyCode.TRACK_NEXT -> "MediaTrackNext",
      KeyCode.TRACK_PREV -> "MediaTrackPrevious", KeyCode.PAGE_UP -> "PageUp", KeyCode.PAGE_DOWN -> "PageDown",
      KeyCode.HOME -> "Home", KeyCode.END -> "End", KeyCode.CONTEXT_MENU -> "ContextMenu", KeyCode.DELETE -> "Delete",
      // the modifiers on their own, as the web names them (a double-tap is one pressed twice)
      KeyCode.CONTROL -> "Control", KeyCode.SHIFT -> "Shift", KeyCode.ALT -> "Alt", KeyCode.META -> "Meta",
      KeyCode.COMMAND -> "Meta", KeyCode.WINDOWS -> "Meta")
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
  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** A page of the web: nothing here, where the platform has no engine of its own to lend. */
  def web(bridge: String): RenderNode = container()

  /** An embed's box (everyui.component.Embed): a pane marked with `Host.embedKey`, so the keys are the guest's while
    * the focus is inside it, and, for a `site`, holding a web view that runs it (WebGuest) where one can be had. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = {
    val node = container()
    node.fx.getProperties.put(Host.embedKey, name)
    node.fx.asInstanceOf[Pane].setPickOnBounds(true)
    if (site != null) node.guest = WebGuest.make(node.fx.asInstanceOf[Pane], name, site)
    node
  }

  /** The scene's root pane. */
  def root(): RenderNode = new RenderNode(Host.root)

  /** A video: a container whose pane holds the player's view, clipped to it. */
  def video(): RenderNode = {
    val node = container()
    node.media = Some(new DesktopMedia(Some(node.fx.asInstanceOf[Pane])))
    node
  }

  /** Sound: a container with nothing to draw. */
  def audio(): RenderNode = {
    val node = container()
    node.media = Some(new DesktopMedia(None))
    node
  }

  /** A container — a `Pane` other nodes are appended to, sized 0x0 until given a size (a `div`'s default). */
  def container(): RenderNode = {
    val p = new Pane()
    p.setMinSize(0.0, 0.0)
    p.setPrefSize(0.0, 0.0)
    p.setMaxSize(0.0, 0.0)
    p.setPickOnBounds(false)
    new RenderNode(p)
  }

  /** A drawing (everyui.draw.Drawing): a pane of shapes. */
  def drawing(): RenderNode = container()

  /** A paragraph of styled runs (`everyui.component.RichText`): a `RichFlow`, each run a `Text` of its own. */
  def richText(): RenderNode = {
    val node = new RenderNode(new RichFlow)
    Host.aligned.add(node)
    node
  }

  /** A text node — a `Text`, positioned by its baseline. */
  def text(): RenderNode = {
    val t = new Text()
    t.setTextOrigin(VPos.BASELINE)
    t.setFontSmoothingType(FontSmoothingType.GRAY)
    t.setFill(Color.web("#1c1b1f"))
    t.setFont(FontWeights("System", 600, false, 32.0))
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
    val f = if (secure) new PastingPasswordField() else new PastingTextField()
    f.setStyle(RenderNode.fieldStyle("#1c1b1f"))
    f.setFocusTraversable(false)
    val node = new RenderNode(f)
    Host.aligned.add(node)
    node.applyAlign()
    node
  }

  /** A link: a container; its click follows it. */
  def link(): RenderNode = container()

  /** A drop-down draws itself here: its node is a line of text. */
  def select(): RenderNode = text()

  /** A box of lines to type in — a JavaFX `TextArea` — with no box of its own. */
  def textArea(): RenderNode = {
    val a = new PastingTextArea()
    a.setWrapText(true)
    a.setStyle(RenderNode.fieldStyle("#1c1b1f"))
    a.getStylesheets.add(RenderNode.areaSheet)
    // its scroll bar only once the words run well past the box, never a sliver of thumb for a line not yet grown into
    a.skinProperty.addListener((_, _, skin) => if (skin != null) javafx.application.Platform.runLater(() =>
      a.lookupAll(".scroll-bar").forEach {
        case bar: javafx.scene.control.ScrollBar if bar.getOrientation == javafx.geometry.Orientation.VERTICAL =>
          bar.opacityProperty.bind(
            javafx.beans.binding.Bindings.when(bar.visibleAmountProperty.lessThan(0.85)).`then`(1.0).otherwise(0.0))
        case _ => ()
      }))
    a.setFocusTraversable(false)
    val node = new RenderNode(a)
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
  /** Whether this device can go on playing in a corner of whatever the viewer does next, and doing it. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web): not yet here. */
  def webViews(): Boolean = false
  /** Whether a web guest runs in an embed here: where the app was built with JavaFX's web view (WebGuest). */
  def embeds(): Boolean = WebGuest.available

  def corners(): Boolean = false

  def toCorner(): Boolean = false

  /** What to do when the app is at the front again after being away. Nothing here says so yet, so nothing is ever
    * called: a caller must not depend on it as its only way of noticing somebody is back. */
  def onResume(f: () => Unit): () => Unit = {
    resumers = resumers :+ f
    () => resumers = resumers.filterNot(_ eq f)
  }

  def resumed(): Unit = resumers.foreach(f => f())

  private var resumers: List[() => Unit] = Nil

  /** What to do when the app goes away from the front: another app has the screen, or the device is going to sleep.
    * A player wants to know, because nobody is watching it now. Returns what stops it being told. */
  def onAway(f: () => Unit): () => Unit = {
    aways = aways :+ f
    () => aways = aways.filterNot(_ eq f)
  }

  /** Said by the platform when it happens. */
  def away(): Unit = aways.foreach(f => f())

  private var aways: List[() => Unit] = Nil

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
  /** A window has no page to leave: the browser opens `url`. */
  def navigate(url: String): Unit = openUrl(url)
  /** The window's title. */
  /** A page's description of itself (`everyui.seo.SEO`): only its title has a place here. */
  def setSEO(meta: everyui.seo.SEO): Unit = setTitle(meta.title)
  /** The first screen is drawn: nothing was standing in for it. */
  def drawn(): Unit = ()
  /** Whether a component's size is read in a batch before the frame is drawn (`MeasuredFeature`) rather than at once:
    * here it is read at once. */
  def measuresInBatches: Boolean = false
  def beforePaint(f: () => Unit): Unit = f()
  /** `f` once the page has been painted at least once: here, at once, there being no page painted before the app. */
  def afterFirstPaint(f: () => Unit): Unit = f()

  def setTitle(title: String): Unit = Option(Host.scene).flatMap(s => Option(s.getWindow)).foreach {
    case stage: javafx.stage.Stage => stage.setTitle(title)
    case _ => ()
  }
  /** Words for a screen reader to read out (a new message in a chat): no reader of roles or descriptions reaches this
    * platform's nodes (`setSemantics`), so nothing is said. */
  def announce(words: String): Unit = ()
  /** A desktop keeps its own screen timing. */
  def keepAwake(on: Boolean): Unit = ()
  /** Every colour on the screen scaled, channel by channel (1 each leaves it as it is): the root multiplied by a flat
    * colour of the three, or left alone. */
  def tint(red: Double, green: Double, blue: Double): Boolean = {
    if (Host.root != null) Host.root.setEffect(
      if (red >= 0.999 && green >= 0.999 && blue >= 0.999) null
      else new javafx.scene.effect.Blend(javafx.scene.effect.BlendMode.MULTIPLY, null,
        new javafx.scene.effect.ColorInput(0.0, 0.0, 16384.0, 16384.0, javafx.scene.paint.Color.color(
          math.max(0.0, math.min(1.0, red)), math.max(0.0, math.min(1.0, green)), math.max(0.0, math.min(1.0, blue))))))
    true
  }
  /** The app named (`NaboTV (tv.nabo.app)`) to the servers its pictures come from, as their User-Agent: a map's tile
    * server refuses a bare `Java/21`. Before the first picture loads (the JDK reads it once). */
  def identify(app: String): Unit = System.setProperty("http.agent", app)

  /** Whether copyText reaches a clipboard: it does. */
  def clipboard(): Boolean = true
  /** Whether people select text with a pointer (`Component.selectable`): not here. */
  def selectsText(): Boolean = false
  def selectedText(): String = ""
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

  /** The app's language: the platform chooses its own faces for the script, so there is nothing to do. */
  def setLanguage(code: String): Unit = ()

  /** The window's reading direction: every text node and field re-aligns. */
  def setDirection(rtl: Boolean): Unit = {
    Host.rtl = rtl
    Host.aligned.forEach(_.applyAlign())
  }

  /** Run `f` on the JavaFX thread, soon: how a reply from another thread reaches the components. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  /** `f` on a thread that does not draw, for work a frame should not wait on (a large document parsed or written);
    * whatever it hands back goes by `later`. A failure is logged and goes no further. */
  def offThread(f: () => Unit): Unit = RenderNode.work.execute(() =>
    try f() catch { case t: Throwable => logError("Off the FX thread", t) })

  def later(f: () => Unit): Unit = javafx.application.Platform.runLater(() => f())
  /** `f` on the FX thread once `seconds` have passed, on a later turn of it (Timers). */
  def after(seconds: Double, f: () => Unit): Unit = Timers.after(seconds, f)

  /** A value kept across launches (the user's preferences store), or "" when there is none. */
  def setting(name: String): String = Host.settings.get(name, "")
  /** Keep `value` under `name`; "" forgets it. */
  // nowhere safer than the settings to keep a secret here
  def secret(name: String): String = setting(name)
  def saveSecret(name: String, value: String): Unit = saveSetting(name, value)

  def saveSetting(name: String, value: String): Unit = {
    if (value.isEmpty) Host.settings.remove(name) else Host.settings.put(name, value)
    Host.settings.flush()
  }

  /** The device's own store of documents (see [[everyui.store.Store]]). Files beside the app's own directory in
    * the home folder, rather than the preferences, which are meant for small values and have a limit on each. */
  private def storeSafe(name: String): String =
    name.map(c => if (c.isLetterOrDigit || c == '.' || c == '-') c else '_')
  private def storeDir(space: String): java.io.File = {
    val home = new java.io.File(System.getProperty("user.home"), "." + Host.app.toLowerCase)
    val dir = new java.io.File(new java.io.File(home, "store"), storeSafe(space))
    dir.mkdirs()
    dir
  }
  private def storeFile(space: String, key: String): java.io.File =
    new java.io.File(storeDir(space), storeSafe(key) + ".json")

  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = got(
    try {
      val file = storeFile(space, key)
      if (!file.isFile) None
      else {
        val text = new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
        if (text.isEmpty) None else Some(text)
      }
    } catch { case _: Throwable => None })

  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = {
    try java.nio.file.Files.write(storeFile(space, key).toPath, value.getBytes("UTF-8"))
    catch { case _: Throwable => () }
    done()
  }

  def storeDelete(space: String, key: String, done: () => Unit): Unit = {
    try storeFile(space, key).delete() catch { case _: Throwable => () }
    done()
  }

  def storeKeys(space: String, got: List[String] => Unit): Unit = got(
    Option(storeDir(space).listFiles()).getOrElse(Array.empty[java.io.File]).toList
      .map(_.getName).filter(_.endsWith(".json")).map(n => n.substring(0, n.length - 5)))

  def storeClear(space: String, done: () => Unit): Unit = {
    Option(storeDir(space).listFiles()).getOrElse(Array.empty[java.io.File])
      .foreach(f => try f.delete() catch { case _: Throwable => () })
    done()
  }

  /** What this machine calls itself. */
  /** What it is, as its maker names it; the same as its name where the platform knows no more than that. */
  def deviceModel(): String = deviceName()

  def launchesApps(): Boolean = false
  /** The hardware facts a performance report is read with: none this platform will say. */
  def deviceProfile(): List[(String, String)] = Nil

  /** What the app is showing, as a PNG in base64: nothing here can take one. */
  def screenshot(): String = ""

  // nothing here says which codecs it has, so none is ruled out: a wrong no costs a transcode nobody needed
  def decodes(codec: String): Boolean = true
  // an app here is updated by whatever put it here, never by itself
  def canInstallApps(): Boolean = false
  def openInstallSettings(): Unit = ()
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    done(Some("Updates install only on Android"))
  /** Something large was let go: nothing to do where the runtime collects on its own. */
  def collect(): Unit = ()
  /** Whether `blur` is drawn: it is. */
  def blurs(): Boolean = true
  /** Whether text can hold emoji: the system's fonts draw them. */
  def emoji(): Boolean = true
  // nothing here turns speech into words
  def dictation(): Boolean = false
  def dictate(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit = {
    ended()
    () => ()
  }
  def localAddress(): String = ""
  def deviceSerial(): String = ""

  /** Where the app came from, and which build it is: nothing this platform can say. */
  def installStore(): String = ""
  def appVersion(): Int = 0
  def appVersionName(): String = ""

  /** The device's own caption settings, where it has any: not here. */
  def captionStyle(): Option[everyui.text.CaptionStyle] = None

  def deviceName(): String =
    try java.net.InetAddress.getLocalHost.getHostName
    catch { case _: Exception => "Desktop" }

  /** A desktop has text fields of its own. */
  def nativeInput(): Boolean = true
  /** Only a platform without text fields asks its keyboard for a value. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()
  /** `editText` for a field asking for `autofill` (`Autofill.token`), which says nothing more here. */
  def editField(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit =
    editText(title, value, secure, done)

  /** A registered face loads from its file when first used. */
  def registerFont(face: Face): Unit = ()

  /** The window in the app's units: its size over the size the app is drawn at (everyui.theme.UiScale). */
  def screenWidth(): Double = Host.scene.getWidth / everyui.theme.UiScale()
  def screenHeight(): Double = Host.scene.getHeight / everyui.theme.UiScale()
  /** Screen pixels to a layout unit: the output scale of the screen the window is on (2 on a HiDPI display), times the
    * size the app is drawn at. */
  def pixelRatio(): Double =
    Option(Host.scene.getWindow).map(_.getOutputScaleX).filter(_ > 0.0).getOrElse(1.0) * everyui.theme.UiScale()
  /** The app drawn `factor` times its size (everyui.theme.UiScale): the root scaled from its top left. */
  def setUiScale(factor: Double): Unit = Host.scaleRoot(factor)

  /** `--freeze=<seconds>`: step the animation that far on a virtual clock and stop (tools/check.sh). Negative when
    * absent. */
  def freezeSeconds(): Double = Host.freeze

  /** No other apps to know of here. */
  def installedApps(ids: List[String]): List[String] = Nil

  /** No media servers to find from here (the JVM could ask as Android does, but nothing here wants it yet). */
  def findMediaServers(seconds: Double, found: List[everyui.MediaServer] => Unit): Unit = later(() => found(Nil))

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
  /** Where this app's server is, to resolve its relative URLs against: nothing here comes from a server, so what the app
    * set as the setting "everyui.origin" ("https://example.com"), else "". */
  def origin(): String = setting("everyui.origin")

  /** The frozen frame is complete: save it if asked to (`--screenshot`). */
  def frozen(): Unit = Host.frozen()

  /** Called whenever the window changes size. */
  /** Every key in the window, by the web's name, pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = {
    def hear(e: KeyEvent, pressed: Boolean): Unit = {
      held = everyui.event.Modifiers(e.isControlDown, e.isMetaDown, e.isAltDown, e.isShiftDown)
      embedded = Host.embedOf(e.getTarget)
      // JavaFX says nothing of a repeat: a press of a key already down is one
      repeating = if (pressed) !down.add(e.getCode) else { down.remove(e.getCode); false }
      try if (f(Host.keyName(e), pressed)) e.consume()
      // the embed is the one this key was meant for, and no other: a key sent later from anywhere else (a key given
      // back, one a test sends) is nobody's embed's, nor a repeat
      finally {
        embedded = ""
        repeating = false
      }
    }
    // a key let go while the window is not the one in front is never released here
    def watch(w: javafx.stage.Window): Unit = if (w != null) w.focusedProperty.addListener((_, _, now) => if (!now) down.clear())
    watch(Host.scene.getWindow)
    Host.scene.windowProperty.addListener((_, _, w) => watch(w))
    Host.scene.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => hear(e, true))
    Host.scene.addEventFilter(KeyEvent.KEY_RELEASED, (e: KeyEvent) => hear(e, false))
    // a mouse's back button is Back too
    Host.scene.addEventFilter(MouseEvent.MOUSE_PRESSED, (e: MouseEvent) => if (e.getButton == javafx.scene.input.MouseButton.BACK && f("BrowserBack", true)) e.consume())
    Host.scene.addEventFilter(MouseEvent.MOUSE_RELEASED, (e: MouseEvent) => if (e.getButton == javafx.scene.input.MouseButton.BACK && f("BrowserBack", false)) e.consume())
  }

  private var held = everyui.event.Modifiers(false, false, false, false)
  private var button = 0

  /** The button of the pointer event being reported: 0 the primary, 1 the middle, 2 the secondary. */
  def pointerButton(): Int = button

  /** A pointer event about to be heard: its button and the modifiers held with it. */
  private[render] def pointed(pressed: Int, ctrl: Boolean, meta: Boolean, alt: Boolean, shift: Boolean): Unit = {
    held = everyui.event.Modifiers(ctrl, meta, alt, shift)
    button = pressed
  }
  /** The modifiers held with the key being reported. */
  def modifiers(): everyui.event.Modifiers = held
  // the keys down now, as their presses and releases have told
  private val down = scala.collection.mutable.Set.empty[javafx.scene.input.KeyCode]
  private var repeating = false
  /** Whether the key being reported is one held down repeating: pressed again without being released. */
  def keyRepeat(): Boolean = repeating
  /** Whether the key being reported belongs to an input method composing words: never, since JavaFX hands those to the
    * field as input method events rather than keys. */
  def keyComposing(): Boolean = false
  private var embedded = ""
  /** The embed the key being reported is meant for, "" for none (`Host.embedOf`). */
  def keyEmbed(): String = embedded
  /** Whether shortcuts are made with Command rather than Ctrl here: on a Mac. */
  def commandKey(): Boolean = sys.props("os.name").toLowerCase.contains("mac")

  def onResize(f: () => Unit): Unit = {
    Host.scene.widthProperty.addListener((_: javafx.beans.Observable) => f())
    Host.scene.heightProperty.addListener((_: javafx.beans.Observable) => f())
  }

  /** A number from 0 up to 1, at random. */
  def random(): Double = math.random()
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
    else {
      // answered once, whichever comes first: the whole of it, or an error (which a download failing part-way through
      // raises without the progress ever reaching the end)
      var answered = false
      val answer: () => Unit = () => if (!answered) {
        answered = true
        if (image.isError) loaded(0.0, 0.0) else loaded(image.getWidth, image.getHeight)
      }
      image.progressProperty.addListener(new ChangeListener[Number] {
        override def changed(o: ObservableValue[? <: Number], was: Number, now: Number): Unit =
          if (now.doubleValue >= 1.0) {
            o.removeListener(this)
            answer()
          }
      })
      image.errorProperty.addListener(new ChangeListener[java.lang.Boolean] {
        override def changed(o: ObservableValue[? <: java.lang.Boolean], was: java.lang.Boolean,
                             now: java.lang.Boolean): Unit =
          if (now) {
            o.removeListener(this)
            answer()
          }
      })
    }
  }

  /** Bundled faces load synchronously from the assets directory. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    Host.font(Faces(family, weight), 24.0)
    loaded()
  }

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per JavaFX pulse. */
  /** Frames as the platform measured them: not here, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = false
  /** Frames on demand (`everyui.task.Frames`): the app asks for each frame it needs, and JavaFX pulses only when its
    * scene changes while it needs none. */
  def framesOnDemand(): Boolean = true
  /** `f` once, on the next JavaFX pulse. */
  def nextFrame(f: () => Unit): Unit = FrameTimer.next(f)
  def animate(tick: () => Unit): Unit = {
    val timer = new AnimationTimer {
      override def handle(now: Long): Unit = tick()
    }
    timer.start()
  }
}
