package everyui.component

import everyui.component.feature.FontFeature
import everyui.render.{Nodes, Platform}
import everyui.PlatformKind
import everyui.theme.Theme
import reactify.*

/** One of a list of `options`, chosen from a drop-down: the page's own `<select>` on the web (its keyboard, its list,
  * its accessibility); elsewhere the chosen option's words, a click, a tap or Enter moving to the next. `selected` is
  * the chosen option's index. Give it a size: it is drawn in a box of its own (`boxed`), its words set in from the box's
  * edge, so it is seen to be something to choose from. */
class Select(options: => List[String]) extends Container {
  override def kind: String = "Select"

  val selected: Var[Int] = own(Var(0))
  private val choices: Val[List[String]] = own(Val(options))
  private val native = Platform.kind == PlatformKind.Web
  private val field: Component = if (native) new Component(Nodes.select()) else new TextView
  /** The words' font; their colour is `color`. */
  val font: FontFeature = new FontFeature(field)

  field.color := color()
  // the theme's words, following it when it changes (a dark list on the web follows them)
  color := Theme.text()
  /** Whether it is drawn in a box of its own: an edge, rounded, on the control colour, its words set in from it. On,
    * the default; off, its words alone, for a box of the app's. */
  val boxed: Var[Boolean] = own(Var(true))
  private val inset: Val[Double] = own(Val(if (boxed()) Select.inset else 0.0))
  borderWidth := (if (boxed()) 1.0 else 0.0)
  borderColor := Theme.border()
  radius := (if (boxed()) Select.radius else 0.0)
  backgroundColor := (if (boxed()) Theme.control() else everyui.Color.Clear)
  field.position.left := inset()
  field.size.width := math.max(0.0, size.width() - inset() * 2.0)
  if (native) {
    field.size.height := size.height()
    choices.attachAndFire { labels =>
      field.node.setOptions(labels)
      field.node.setValue(selected().toString)
    }
    selected.attach(i => if (field.node.getValue() != i.toString) field.node.setValue(i.toString))
    field.node.onValue(v => v.toIntOption.foreach(i => if (selected() != i) selected @= i))
  } else {
    field match {
      case words: TextView => words.content := choices().lift(selected()).getOrElse("")
      case _ => ()
    }
    field.position.middle := size.middle()
    focusable @= true
    event.click.attach(_ => if (choices().nonEmpty) selected @= (selected() + 1) % choices().size)
  }
  children += field
}

object Select {
  /** How far a boxed select's words are set in from its edge, and how round its corners are. */
  val inset: Double = 8.0
  val radius: Double = 8.0
}
