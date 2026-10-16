package everyui.app

import everyui.component.{Container, Panel}
import everyui.layout.Overlaps
import everyui.list.VirtualList
import everyui.render.Platform
import everyui.text.TextArea
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A filter over a list of providers that fills the rest of the screen (`Panel`, `Component.flex`): where the window is
  * narrow the filter wraps to a second line, and the list starts below it, as tall as is left. The overlap check is on
  * (`Overlaps.warn`). With `bad=1` the list is placed by hand under the filter's one-line height instead, as an app
  * that guessed it would, and the check reports the two once the filter wraps. Logs `fill ok`, then both boxes:
  *
  *   [ui] fill filter 16,16 388x56 list 16,80 388x704
  */
object FillDemo {
  def run(): Unit = {
    Theme.family @= "Open Sans"
    ui.backgroundColor := Theme.background()
    Overlaps.warn @= true
    val filter = new TextArea("Filter providers", grow = true, minHeight = 38.0)
    filter.value @= "openai google mistral cohere groq together fireworks deepseek xai perplexity"
    val list = VirtualList.text(Var((1 to 200).toVector.map(i => "Provider " + i.toString)), (s: String) => s, (s: String) => s)
    val panel =
      if (Platform.launchArg("bad") == "1") {
        val placed = new Container
        placed.children ++= List(filter, list)
        filter.position.left @= 16.0
        filter.position.top @= 16.0
        list.position.left @= 16.0
        list.position.top @= 16.0 + 38.0 + 8.0
        list.size.height := placed.size.height() - list.position.top() - 16.0
        placed
      }
      else Panel(filter, list, spacing = 8.0, padding = 16.0)
    panel.size.width := ui.size.width()
    panel.size.height := ui.size.height()
    filter.size.width := panel.size.width() - 32.0
    list.size.width := panel.size.width() - 32.0
    ui.children += panel
    Platform.log("fill ok")
    Platform.after(1.5, () => Platform.log("fill filter " + box(filter.position.left(), filter.position.top(),
      filter.size.width(), filter.size.height()) + " list " + box(list.position.left(), list.position.top(),
      list.size.width(), list.size.height())))
  }

  private def box(left: Double, top: Double, width: Double, height: Double): String =
    math.round(left).toString + "," + math.round(top).toString + " " + math.round(width).toString + "x" +
      math.round(height).toString
}
