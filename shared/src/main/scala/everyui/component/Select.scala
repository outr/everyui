package everyui.component

import everyui.component.feature.FontFeature
import everyui.render.{Nodes, Platform}
import everyui.PlatformKind
import everyui.theme.Theme
import reactify.*

/** One of a list of `options`, chosen from a drop-down: the page's own `<select>` on the web (its keyboard, its list,
  * its accessibility); elsewhere the chosen option's words, a click, a tap or Enter moving to the next. `selected` is
  * the chosen option's index. Give it a size; it has no box of its own. */
class Select(options: => List[String]) extends Container {
  override def kind: String = "Select"

  val selected: Var[Int] = Var(0)
  private val choices: Val[List[String]] = Val(options)
  private val native = Platform.kind == PlatformKind.Web
  private val field: Component = if (native) new Component(Nodes.select()) else new TextView
  /** The words' font; their colour is `color`. */
  val font: FontFeature = new FontFeature(field)

  field.color := color()
  color @= Theme.text()
  field.size.width := size.width()
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
