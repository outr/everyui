package everyui.example.screen

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.example.ExampleApp
import everyui.example.ui.component.Header

/** youi's `UIExampleScreen`: a screen shown in the example app's content area, under its header. */
trait UIExampleScreen extends UIScreen {
  override def manager: ScreenManager = ExampleApp

  /** Shared app header (same for all example screens). */
  protected def header: Header = ExampleApp.appHeader

  override protected def activate(): Task[Unit] = {
    ExampleApp.title @= title
    container.backgroundColor := ExampleApp.bgColor()
    super.activate()
  }
}
