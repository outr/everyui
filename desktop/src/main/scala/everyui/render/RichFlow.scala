package everyui.render

import everyui.font.{Face, Faces}
import everyui.text.{Bidi, TextRun}
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontPosture, FontSmoothingType, FontWeight, Text, TextAlignment, TextFlow}

/** A paragraph of styled runs (`everyui.component.RichText`, `Nodes.richText`): a `Text` for each run in a `TextFlow`,
  * so a run keeps its own face, weight, slant, size, colour, strike and underline, and a link's run is followed by a
  * click. What no run names is the paragraph's own (`base`, `colour`). JavaFX draws no background behind a run, so a
  * marked span (code) is told by its face and colour alone. A run's `title` is the platform's tooltip over it. */
class RichFlow extends TextFlow {
  private var runs: List[TextRun] = Nil
  private var follow: String => Unit = _ => ()
  private var family: String = ""
  private var weight: Int = 600
  private var size: Double = 32.0
  private var colour: String = "#1c1b1f"
  private var italic: Boolean = false

  /** The words of every run. */
  def text: String = runs.map(_.text).mkString

  def setRuns(value: List[TextRun], followed: String => Unit): Unit = {
    runs = value
    follow = followed
    draw()
  }

  /** The paragraph's own face and size, for the runs that name none. */
  def setBase(family: String, weight: Int, size: Double): Unit = {
    this.family = family
    this.weight = weight
    this.size = size
    draw()
  }

  /** Every run slanted, as the paragraph's own style: a run marked italic is slanted either way. */
  def setItalic(on: Boolean): Unit = {
    italic = on
    draw()
  }

  def setSize(size: Double): Unit = {
    this.size = size
    draw()
  }

  def setColour(hex: String): Unit = {
    colour = hex
    draw()
  }

  /** Where each line sits: "left", "center" or "right". */
  def setSide(side: TextAlignment): Unit = setTextAlignment(side)

  /** How wide the runs are on one line, and how tall at `width` (or on their own lines, for -1). */
  def across(): Double = math.ceil(prefWidth(-1.0))
  def down(width: Double): Double = math.ceil(prefHeight(width))

  // words with right-to-left letters are drawn in a face that has them and broken into lines here, at the width it is
  // given, as JavaFX cannot draw them in order in a fallback face nor wrap them (`RtlText`)
  prefWidthProperty.addListener((_, _, _) => if (Bidi.anyRightToLeft(text)) draw())

  // and where JavaFX must wrap them all the same, at a width narrower than a letter or one they were not broken for, its
  // layout throws: they are laid out on the lines they have
  override protected def computePrefHeight(width: Double): Double =
    try super.computePrefHeight(width) catch { case _: ArrayIndexOutOfBoundsException => super.computePrefHeight(-1.0) }
  override protected def layoutChildren(): Unit =
    try super.layoutChildren() catch { case _: ArrayIndexOutOfBoundsException => () }

  private def draw(): Unit = {
    val shown = runs.filter(_.text.nonEmpty)
    val rtl = Bidi.anyRightToLeft(text)
    val fonts = shown.map(run => face(run, rtl))
    val width = getPrefWidth
    val words = if (rtl && width > 0.0) RtlText.lines(shown.map(_.text).zip(fonts), width) else shown.map(_.text)
    val now = shown.zip(words).zip(fonts).map(p => (p._1._1, p._1._2, p._2))
    // the nodes of the runs that begin as the last did kept, only those from the first that changed made again: code
    // that grows as an answer streams in draws its last lines, not the whole block; a run drawn as the one in its place
    // in every way but its words keeps its node too, its words written into it (a line whose time moves on each second)
    var kept = 0
    if (colour == drawnColour && italic == drawnItalic && getChildren.size == drawn.size) {
      var was = drawn
      var next = now
      while (was.nonEmpty && next.nonEmpty && (was.head == next.head || RichFlow.rewordable(was.head, next.head))) {
        if (was.head != next.head) getChildren.get(kept).asInstanceOf[Text].setText(next.head._2)
        kept += 1
        was = was.tail
        next = next.tail
      }
    }
    val made = now.drop(kept).map(p => if (p._1.image.nonEmpty) picture(p._1, p._3) else piece(p._1, p._2, p._3))
    if (kept == 0) getChildren.setAll(made*)
    else {
      getChildren.remove(kept, getChildren.size)
      getChildren.addAll(made*)
    }
    drawn = now
    drawnColour = colour
    drawnItalic = italic
  }
  // what the nodes draw (`draw`): each run with its words and face, and the paragraph's colour and slant
  private var drawn: List[(TextRun, String, Font)] = Nil
  private var drawnColour = ""
  private var drawnItalic = false

  // the face `run` is drawn in: its own, or the paragraph's; for right-to-left words, one that has their letters
  // unless it is the run's own (code)
  private def face(run: TextRun, rtl: Boolean): Font = {
    val named = if (run.family.nonEmpty) run.family else family
    val heavy = if (run.weight > 0) run.weight else weight
    val points = if (run.size > 0.0) run.size else size
    val font = RichFlow.font(named, heavy, run.italic || italic, points)
    if (rtl && run.family.isEmpty) RtlText.covering(font) else font
  }

  // a run's picture (`TextRun.image`): a square as tall as the letters, the picture fitted in it, set as low under the
  // line as the web sets it, so the lines are as tall before it loads as after; its words are what is read out and shown
  // on a hover
  private def picture(run: TextRun, font: Font): javafx.scene.Node = {
    val side = font.getSize * TextRun.pictureEm
    val view = new javafx.scene.image.ImageView(RichFlow.picture(run.image))
    view.setFitWidth(side)
    view.setFitHeight(side)
    view.setPreserveRatio(true)
    view.setSmooth(true)
    val box = new javafx.scene.layout.StackPane(view) {
      override def getBaselineOffset: Double = side - font.getSize * 0.3
    }
    box.setMinSize(side, side)
    box.setPrefSize(side, side)
    box.setMaxSize(side, side)
    box.setAccessibleText(run.text)
    javafx.scene.control.Tooltip.install(box,
      new javafx.scene.control.Tooltip(if (run.title.nonEmpty) run.title else run.text))
    if (run.href.nonEmpty) {
      box.setCursor(javafx.scene.Cursor.HAND)
      box.setOnMouseClicked(e => if (e.getButton == javafx.scene.input.MouseButton.PRIMARY) {
        e.consume()
        follow(run.href)
      })
    }
    box
  }

  private def piece(run: TextRun, words: String, font: Font): Text = {
    val t = new Text(words)
    t.setFontSmoothingType(FontSmoothingType.GRAY)
    t.setFont(font)
    if (run.italic || italic) Oblique(t, true)
    t.setFill(Color.web(if (run.color.nonEmpty) run.color else colour))
    t.setStrikethrough(run.strike)
    t.setUnderline(run.underline)
    // what it says on a hover (a link's by `Link.describe`): the platform's tooltip over its letters, and to a screen
    // reader after its words
    if (run.title.nonEmpty) {
      javafx.scene.control.Tooltip.install(t, new javafx.scene.control.Tooltip(run.title))
      t.setAccessibleHelp(run.title)
    }
    if (run.href.nonEmpty) {
      t.setCursor(javafx.scene.Cursor.HAND)
      t.setOnMouseClicked(e => if (e.getButton == javafx.scene.input.MouseButton.PRIMARY) {
        e.consume()
        follow(run.href)
      })
    }
    t
  }
}

object RichFlow {
  /** Whether `now` (a run, its words and its face) is drawn as `was` in every way but its words, neither a picture: its
    * node is kept, its words written into it. */
  def rewordable(was: (TextRun, String, Font), now: (TextRun, String, Font)): Boolean =
    was._1.image.isEmpty && now._1.image.isEmpty && was._3 == now._3 && was._1.copy(text = now._1.text) == now._1

  // the runs' pictures by address, loaded once, behind the drawing: a custom emoji is in many messages
  private val pictures = scala.collection.mutable.HashMap.empty[String, javafx.scene.image.Image]

  /** The picture at `uri`, loading in the background the first time it is wanted. */
  def picture(uri: String): javafx.scene.image.Image =
    pictures.getOrElseUpdate(uri, new javafx.scene.image.Image(uri, true))

  /** A bundled face (its own italic where it has one) at `size`, or the system's where `family` is none. */
  def font(family: String, weight: Int, italic: Boolean, size: Double): Font =
    if (family.isEmpty) FontWeights("System", weight, italic, size)
    else {
      val face: Face = (if (italic) Faces.italic(family, weight) else None).getOrElse(Faces(family, weight))
      val base = Host.font(face, size)
      // a face with no italic of its own, slanted by the system where it can be
      if (italic && !face.file.toLowerCase.contains("italic"))
        Font.font(base.getFamily, if (weight >= 600) FontWeight.BOLD else FontWeight.NORMAL, FontPosture.ITALIC, size)
      else base
    }
}
