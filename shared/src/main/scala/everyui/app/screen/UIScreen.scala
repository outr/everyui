package everyui.app.screen

import reactify.*
import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.component.types.PositionType

/** A screen with a container the size of its host, filled by `createUI` once, on its first showing. */
trait UIScreen extends Screen with PathActivation {
  /** The manager showing this screen; where its container goes. */
  def manager: ScreenManager

  protected lazy val container: Container = {
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c
  }

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    container.size.width := manager.host.size.width
    container.size.height := manager.host.size.height
    createUI()
  }

  /** Showing a screen swaps its container into the host. */
  override protected def activate(): Task[Unit] = {
    manager.host.children.clear()
    manager.host.children += container
    super.activate()
  }

  /** What has the focus when the screen activates. Every screen names one, so a remote always has somewhere to
    * start; returning to the screen with Back puts the focus back where it was instead. */
  def defaultFocus(): Component

  def createUI(): Task[Unit]
}
