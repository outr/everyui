package universal.example.screen

import reactify.*
import universal.app.screen.UIScreen
import universal.async.Task
import universal.component.Container
import universal.component.types.PositionType
import universal.example.ExampleApp
import universal.example.ui.component.Header
import universal.ui

trait UIExampleScreen extends UIScreen:
  override protected lazy val container: Container =
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c

  /** Shared app header (same for all example screens). */
  protected def header: Header = ExampleApp.appHeader

  override protected def init(): Task[Unit] = super.init().flatMap { _ =>
    ui.children -= container
    container.size.height := ui.size.height() - ExampleApp.appHeader.size.height()
    Task.unit
  }

  override protected def activate(): Task[Unit] =
    ExampleApp.title @= title
    ExampleApp.contentContainer.children.clear()
    ExampleApp.contentContainer.children += container
    container.size.width := ExampleApp.contentContainer.size.width
    container.size.height := ExampleApp.contentContainer.size.height
    container.backgroundColor := ExampleApp.bgColor
    super.activate()
