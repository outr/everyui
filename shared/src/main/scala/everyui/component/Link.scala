package everyui.component

import everyui.app.screen.ScreenManager
import everyui.render.{Nodes, Platform}
import everyui.component.types.Prop

/** Something to follow to `href`, holding whatever it shows in `children`: a path this app has a screen for is shown
  * in place, anything else is gone to (in a new tab with `newTab`). On the web it is a real `<a>`, so a middle click,
  * a modifier key, "copy link" and a new tab keep working as the browser has them; everywhere, a click, a tap or Enter
  * follows it. */
class Link(target: String = "", newTab: Boolean = false) extends Container(Nodes.link()) {
  override def kind: String = "Link(" + href() + ")"

  lazy val href: Prop[String] = new Prop[String](target, h => node.setHref(h, newTab))

  focusable @= true
  event.click.attach { e =>
    e.consume()
    Link.follow(href(), newTab)
  }
}

object Link {
  /** Show `href`'s screen if this app has one, or go there. */
  def follow(href: String, newTab: Boolean = false): Unit =
    if (newTab) Platform.openUrl(href)
    else if (!ScreenManager.navigate(href)) Platform.navigate(href)

  /** A link around `content`. */
  def apply(href: String, newTab: Boolean = false)(content: Component*): Link = {
    val link = new Link(href, newTab)
    link.children ++= content
    link.size.width := content.map(c => c.position.left() + c.size.width()).foldLeft(0.0)(math.max)
    link.size.height := content.map(c => c.position.top() + c.size.height()).foldLeft(0.0)(math.max)
    link
  }
}
