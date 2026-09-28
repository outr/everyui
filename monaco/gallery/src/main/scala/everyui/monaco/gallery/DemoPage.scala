package everyui.monaco.gallery

import everyui.component.{Component, Container, MaterialIconView, Row, TextView}
import everyui.theme.Theme
import reactify.*

/** A demo's page: its title and blurb over `body`, with `actions` at the right of the title. `cleanup` runs as the page
  * is left. */
class DemoPage(demo: Demo, body: Component, actions: List[Component] = Nil, cleanup: () => Unit = () => ()) extends Container {
  private val headerHeight = 64.0
  private val icon = new MaterialIconView(demo.icon, 22.0) { color := Theme.text() }
  private val title = TextView(demo.label, 17.0)
  private val blurb = TextView(demo.blurb, 12.0, Theme.muted())
  private val tools = Row(10.0, middle = true)(actions*)
  private val rule = new Container {
    size.height @= 1.0
    backgroundColor := Theme.border()
  }

  icon.position.left @= 24.0
  icon.position.top @= 21.0
  title.position.left @= 58.0
  title.position.top @= 12.0
  blurb.position.left @= 58.0
  blurb.position.top @= 36.0
  // cut short before the actions rather than running under them
  blurb.maxWidth := math.max(0.0, tools.position.left() - 58.0 - 16.0)
  title.maxWidth := math.max(0.0, tools.position.left() - 58.0 - 16.0)
  tools.position.left := size.width() - tools.size.width() - 20.0
  tools.position.top := (headerHeight - tools.size.height()) / 2.0
  rule.position.top @= headerHeight
  rule.size.width := size.width()
  body.position.top @= headerHeight + 1.0
  body.size.width := size.width()
  body.size.height := size.height() - headerHeight - 1.0
  children ++= List(icon, title, blurb, tools, rule, body)

  override def dispose(): Unit = {
    cleanup()
    super.dispose()
  }
}
