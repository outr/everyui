package everyui.app.screen

import everyui.async.Task
import everyui.component.{Container, ScrollContainer}
import everyui.layout.VerticalLayout
import reactify.*

/** A screen whose `body` stacks whatever it shows, `inset` pixels in from the edges, and scrolls when there is more
  * than fits. `heading` is what an app bar says while it shows. */
trait ScrollScreen extends UIScreen {
  def heading: Val[String] = Val(title)
  protected def inset: Double = 16.0

  protected val scroll: ScrollContainer = new ScrollContainer
  protected val body: Container = new Container {
    layout @= Some(VerticalLayout(16.0))
    size.height := contentHeight
  }

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    body.position.left @= inset
    body.position.top @= inset
    scroll.size.width := container.size.width
    scroll.size.height := container.size.height
    body.size.width := scroll.size.width() - inset * 2.0
    scroll.content.size.height := body.contentHeight + inset * 2.0
    scroll.content.children += body
    container.children += scroll
    Task.unit
  }
}
