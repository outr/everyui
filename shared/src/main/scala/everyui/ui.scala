package everyui

import everyui.component.Container
import everyui.render.{Nodes, Platform}
import everyui.task.TaskSupport
import everyui.theme.UiScale

/** youi's `ui`: the root container, the size of the screen — re-measured whenever that changes. Its start is the focus
  * layer's too (`Focus.listen`). The screen is measured in the app's own units, over the size it is drawn at
  * (`UiScale`): drawn larger, the app has a smaller screen to lay out in. */
object ui extends Container(Nodes.root()) with TaskSupport {
  private def measureScreen(): Unit = {
    size.width @= Platform.screenWidth()
    size.height @= Platform.screenHeight()
  }

  Platform.setUiScale(UiScale())
  measureScreen()
  Platform.onResize(() => measureScreen())
  // drawn at the size asked for, taken into range where it is outside (set again, which comes back here)
  UiScale.factor.attach { f =>
    if (UiScale() != f) UiScale.factor @= UiScale()
    else {
      Platform.setUiScale(f)
      measureScreen()
    }
  }
  everyui.focus.Focus.listen()
}
