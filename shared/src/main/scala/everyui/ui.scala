package everyui

import everyui.component.Container
import everyui.render.{Nodes, Platform}
import everyui.task.TaskSupport

/** youi's `ui`: the root container, the size of the screen — re-measured whenever that changes. Its start is the focus
  * layer's too (`Focus.listen`). */
object ui extends Container(Nodes.root()) with TaskSupport {
  private def measureScreen(): Unit = {
    size.width @= Platform.screenWidth()
    size.height @= Platform.screenHeight()
  }

  measureScreen()
  Platform.onResize(() => measureScreen())
  everyui.focus.Focus.listen()
}
