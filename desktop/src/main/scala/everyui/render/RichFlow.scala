package everyui.render

import everyui.font.{Face, Faces}
import everyui.text.TextRun
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontPosture, FontSmoothingType, FontWeight, Text, TextAlignment, TextFlow}

/** A paragraph of styled runs (`everyui.component.RichText`, `Nodes.richText`): a `Text` for each run in a `TextFlow`,
  * so a run keeps its own face, weight, slant, size, colour, strike and underline, and a link's run is followed by a
  * click. What no run names is the paragraph's own (`base`, `colour`). JavaFX draws no background behind a run, so a
  * marked span (code) is told by its face and colour alone. */
class RichFlow extends TextFlow {
  private var runs: List[TextRun] = Nil
  private var follow: String => Unit = _ => ()
  private var family: String = ""
  private var weight: Int = 600
  private var size: Double = 32.0
  private var colour: String = "#1c1b1f"

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

  private def draw(): Unit = {
    val texts = runs.filter(_.text.nonEmpty).map(piece)
    getChildren.setAll(texts*)
  }

  private def piece(run: TextRun): Text = {
    val t = new Text(run.text)
    val named = if (run.family.nonEmpty) run.family else family
    val heavy = if (run.weight > 0) run.weight else weight
    val points = if (run.size > 0.0) run.size else size
    t.setFontSmoothingType(FontSmoothingType.GRAY)
    t.setFont(RichFlow.font(named, heavy, run.italic, points))
    t.setFill(Color.web(if (run.color.nonEmpty) run.color else colour))
    t.setStrikethrough(run.strike)
    t.setUnderline(run.underline)
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
  /** A bundled face (its own italic where it has one) at `size`, or the system's where `family` is none. */
  def font(family: String, weight: Int, italic: Boolean, size: Double): Font =
    if (family.isEmpty)
      Font.font("System", if (weight >= 700) FontWeight.BOLD else if (weight >= 600) FontWeight.SEMI_BOLD else FontWeight.NORMAL,
        if (italic) FontPosture.ITALIC else FontPosture.REGULAR, size)
    else {
      val face: Face = (if (italic) Faces.italic(family, weight) else None).getOrElse(Faces(family, weight))
      val base = Host.font(face, size)
      // a face with no italic of its own, slanted by the system where it can be
      if (italic && !face.file.toLowerCase.contains("italic"))
        Font.font(base.getFamily, if (weight >= 600) FontWeight.BOLD else FontWeight.NORMAL, FontPosture.ITALIC, size)
      else base
    }
}
