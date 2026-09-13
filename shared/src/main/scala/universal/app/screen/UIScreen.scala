package universal.app.screen

import reactify.*
import universal.async.Task
import universal.component.Container
import universal.component.types.PositionType
import universal.ui

trait UIScreen extends Screen with PathActivation:
  protected lazy val container: Container =
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    container.size.width := ui.size.width
    container.size.height := ui.size.height
    ui.children += container
    createUI()
  }

  def createUI(): Task[Unit]
