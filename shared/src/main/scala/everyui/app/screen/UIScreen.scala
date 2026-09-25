package everyui.app.screen

import reactify.*
import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.component.types.PositionType
import everyui.layout.LayoutPass

/** A screen with a container the size of its host, filled by `createUI` once, on its first showing. */
trait UIScreen extends Screen with PathActivation {
  /** The manager showing this screen; where its container goes. */
  def manager: ScreenManager
  /** This screen's own background image (a site-absolute path or a URL), in place of the theme's: a title's artwork, say. */
  val background: Var[Option[String]] = Var(None)
  /** Shown in place of `background` when that will not load: a picture addressed indirectly, say, which the server may
    * not have. */
  val backgroundFallback: Var[Option[String]] = Var(None)
  /** How the artwork behind it is treated while this screen is shown: where it sits, and the washes over it. Moving to
    * another screen blends one treatment into the next (`ScreenManager.washes`). */
  val treatment: Var[everyui.component.Treatment] = Var(everyui.component.Treatment.none)

  protected lazy val container: Container = {
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c
  }

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    container.size.width := manager.host.size.width
    container.size.height := manager.host.size.height
    // the whole UI laid out in one pass once it is built, rather than a container at a time as it is put together
    LayoutPass.batched(createUI())
  }

  /** Showing a screen swaps its container into the host, in front of the backdrop and its washes when those share the
    * host. */
  override protected def activate(): Task[Unit] = {
    manager.host.children @= manager.host.children().filter(c => (c eq manager.backdrop) || (c eq manager.washes)) :+
      container
    super.activate()
  }

  /** What has the focus when the screen activates. Every screen names one, so a remote always has somewhere to
    * start; returning to the screen with Back puts the focus back where it was instead. */
  def defaultFocus(): Component

  def createUI(): Task[Unit]
}
