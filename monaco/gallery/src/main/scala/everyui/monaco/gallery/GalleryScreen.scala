package everyui.monaco.gallery

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Column, Component, Container, Embed, ScrollContainer, MaterialIconView, TextView}
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.monaco.Monaco
import everyui.net.URLPath
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The gallery: the demos listed down the left, the one chosen filling the rest. */
object GalleryScreen extends UIScreen {
  override def manager: ScreenManager = Gallery
  override def path: URLPath = URLPath("/")

  private val sidebarWidth = 260.0
  private val chosen = Var(0)
  private var page: Option[DemoPage] = None

  private lazy val entries: List[Container] = Demo.all.zipWithIndex.map { case (demo, i) => entry(demo, i) }

  private lazy val sidebar: Container = new Container {
    size.width @= sidebarWidth
    backgroundColor := Theme.background()
    private val mark = new Container {
      size.width @= 28.0
      size.height @= 28.0
      radius @= 6.0
      backgroundColor := Theme.accent()
      position.left @= 20.0
      position.top @= 20.0
      private val icon = new MaterialIconView(MaterialIcon.Code, 18.0) {
        color := Theme.onAccent()
        position.left @= 5.0
        position.top @= 5.0
      }
      children += icon
    }
    private val title = TextView("everyui-monaco", 16.0)
    title.position.left @= 58.0
    title.position.top @= 22.0
    private val version = TextView("Monaco " + Monaco.version + "  •  gallery", 11.0, Theme.muted())
    version.position.left @= 20.0
    version.position.top @= 58.0
    // scrolled, where the screen is too short for all of them (a television at 720 lines), following the focus
    private val list = new ScrollContainer {
      this.content.children += Column(2.0)(entries*)
      position.top @= 90.0
      size.width @= sidebarWidth
    }
    list.size.height := size.height() - 90.0
    children ++= List(mark, title, version, list)
  }

  private lazy val content: Container = new Container

  private def entry(demo: Demo, i: Int): Container = new Container {
    focusable @= true
    size.width @= sidebarWidth
    size.height @= 58.0
    backgroundColor := (if (chosen() == i) Theme.accent().withAlpha(0.18) else if (focused() || event.pointer.over()) Theme.controlLit() else everyui.Color.Clear)
    private val icon = new MaterialIconView(demo.icon, 18.0) {
      color := (if (chosen() == i) Theme.accent() else Theme.muted())
      position.left @= 16.0
      position.top @= 11.0
    }
    private val label = TextView.bound(demo.label, 13.0, if (chosen() == i) Theme.accent() else Theme.text())
    label.position.left @= 46.0
    label.position.top @= 9.0
    private val blurb = TextView(if (demo.blurb.length > 38) demo.blurb.take(36) + "..." else demo.blurb, 11.0, Theme.muted())
    blurb.position.left @= 46.0
    blurb.position.top @= 30.0
    children ++= List(icon, label, blurb)
    event.click.attach { e =>
      e.consume()
      show(i)
    }
  }

  /** The demo at `i` in the list shown, once the screen is. */
  def choose(i: Int): Unit = Platform.after(0.5, () => show(i))

  private def show(i: Int): Unit = {
    chosen @= i
    page.foreach { p =>
      content.children -= p
      p.dispose()
    }
    val next = Demo.all(i).make(Demo.all(i))
    next.size.width := content.size.width()
    next.size.height := content.size.height()
    content.children += next
    page = Some(next)
    // Escape out of an editor (with nothing of its own to close) comes back to the demo's entry in the list
    embeds(next).foreach(_.exit @= Some(entries(i)))
    // and right from the list goes into its editor
    sidebar.exitFocus(Key.ArrowRight, embeds(next).headOption.getOrElse(next))
  }

  private def embeds(c: Component): List[Embed] = c match {
    case e: Embed => List(e)
    case _ => c.childComponents.toList.flatMap(embeds)
  }

  override def defaultFocus(): Component = entries.head

  override def createUI(): Task[Unit] = Task {
    sidebar.size.height := container.size.height()
    content.position.left @= sidebarWidth + 1.0
    content.size.width := container.size.width() - sidebarWidth - 1.0
    content.size.height := container.size.height()
    val rule = new Container {
      position.left @= sidebarWidth
      size.width @= 1.0
      backgroundColor := Theme.border()
    }
    rule.size.height := container.size.height()
    container.backgroundColor := Theme.surface()
    container.children ++= List(sidebar, rule, content)
    show(0)
  }
}
