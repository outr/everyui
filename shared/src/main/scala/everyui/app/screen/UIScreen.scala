package everyui.app.screen

import reactify.*
import everyui.async.Task
import everyui.component.Container
import everyui.component.types.PositionType
import everyui.ui

trait UIScreen extends Screen with PathActivation {
  protected lazy val container: Container = {
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c
  }

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    container.size.width := ui.size.width
    container.size.height := ui.size.height
    ui.children += container
    createUI()
  }

  def createUI(): Task[Unit]
}
